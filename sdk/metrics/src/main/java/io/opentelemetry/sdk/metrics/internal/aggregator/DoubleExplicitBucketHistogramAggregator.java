/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.common.internal.PrimitiveLongList;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.data.MutableHistogramPointData;
import io.opentelemetry.sdk.metrics.internal.descriptor.MetricDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarReservoirFactory;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;

/**
 * Aggregator that generates explicit bucket histograms.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DoubleExplicitBucketHistogramAggregator
    implements Aggregator<HistogramPointData> {
  private final double[] boundaries;
  private final boolean recordMinMax;
  private final MemoryMode memoryMode;

  // a cache for converting to MetricData
  private final List<Double> boundaryList;

  private final ExemplarReservoirFactory reservoirFactory;

  /**
   * Constructs an explicit bucket histogram aggregator.
   *
   * @param boundaries Bucket boundaries, in-order.
   * @param recordMinMax whether to record min and max values
   * @param reservoirFactory Supplier of exemplar reservoirs per-stream.
   * @param memoryMode The {@link MemoryMode} to use in this aggregator.
   */
  public DoubleExplicitBucketHistogramAggregator(
      double[] boundaries,
      boolean recordMinMax,
      ExemplarReservoirFactory reservoirFactory,
      MemoryMode memoryMode) {
    this.boundaries = boundaries;
    this.recordMinMax = recordMinMax;
    this.memoryMode = memoryMode;

    List<Double> boundaryList = new ArrayList<>(this.boundaries.length);
    for (double v : this.boundaries) {
      boundaryList.add(v);
    }
    this.boundaryList = Collections.unmodifiableList(boundaryList);
    this.reservoirFactory = reservoirFactory;
  }

  @Override
  public AggregatorHandle<HistogramPointData> createHandle(long creationEpochNanos) {
    return new Handle(
        creationEpochNanos, boundaryList, boundaries, recordMinMax, reservoirFactory, memoryMode);
  }

  @Override
  public MetricData toMetricData(
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      MetricDescriptor metricDescriptor,
      Collection<HistogramPointData> pointData,
      AggregationTemporality temporality) {
    return ImmutableMetricData.createDoubleHistogram(
        resource,
        instrumentationScopeInfo,
        metricDescriptor.getName(),
        metricDescriptor.getDescription(),
        metricDescriptor.getSourceInstrument().getUnit(),
        ImmutableHistogramData.create(temporality, pointData));
  }

  /**
   * Adaptive-striping histogram handle.
   *
   * <p>Starts with a single {@link Cell} ({@link #base}) guarded by its own {@link ReentrantLock}.
   * The record path uses {@code tryLock()} on the base cell; the first time it fails (i.e., the
   * first observed contention), the handle allocates a fixed-size cells array (one slot per CPU)
   * and from then on routes recorders to a cell chosen by thread id. Each cell has its own lock, so
   * recorders can proceed in parallel across cells.
   *
   * <p>Uncontended workloads never allocate {@link #cells} and pay only one {@code tryLock} +
   * primitive field updates + unlock per record, on par with the previous {@code synchronized}
   * implementation. Contended workloads pay one probe + one cell lock acquire, with contention
   * distributed across {@code Runtime.getRuntime().availableProcessors()} cells.
   *
   * <p>Collection acquires base and all cell locks in turn for a consistent cross-cell snapshot and
   * then merges: sums are added, min/max are reduced, per-bucket counts are summed. Total {@code
   * count} is derived from the sum of the bucket counts, saving a per-record field write.
   */
  static final class Handle extends AggregatorHandle<HistogramPointData> {
    // read-only
    private final List<Double> boundaryList;
    // read-only
    private final double[] boundaries;
    private final boolean recordMinMax;

    // Always present. Serves both as the uncontended fast-path target and as the merge sink at
    // collect time.
    private final Cell base;

    // Null until the first tryLock() failure on base. Installed once via CAS and never resized or
    // cleared. Reads are volatile via the field declaration.
    @SuppressWarnings("UnusedVariable")
    @Nullable
    private volatile Cell[] cells;

    // Scratch buffer used during collection to accumulate merged bucket counts before wrapping
    // into a point. Not touched on the record path.
    private final long[] countsScratch;

    // Used only when MemoryMode = REUSABLE_DATA
    @Nullable private final MutableHistogramPointData reusablePoint;

    private static final AtomicReferenceFieldUpdater<Handle, Cell[]> CELLS =
        AtomicReferenceFieldUpdater.newUpdater(Handle.class, Cell[].class, "cells");

    Handle(
        long creationEpochNanos,
        List<Double> boundaryList,
        double[] boundaries,
        boolean recordMinMax,
        ExemplarReservoirFactory reservoirFactory,
        MemoryMode memoryMode) {
      super(creationEpochNanos, reservoirFactory, /* isDoubleType= */ true);
      this.boundaryList = boundaryList;
      this.boundaries = boundaries;
      this.recordMinMax = recordMinMax;
      int bucketCount = boundaries.length + 1;
      this.base = new Cell(bucketCount);
      this.countsScratch = new long[bucketCount];
      if (memoryMode == MemoryMode.REUSABLE_DATA) {
        this.reusablePoint = new MutableHistogramPointData(bucketCount);
      } else {
        this.reusablePoint = null;
      }
    }

    @Override
    public void recordLong(long value, Attributes attributes, Context context) {
      // Since there is no LongExplicitBucketHistogramAggregator and we need to support measurements
      // from LongHistogram, we redirect calls from #recordLong to #recordDouble. Without this, the
      // base AggregatorHandle implementation of #recordLong throws.
      super.recordDouble((double) value, attributes, context);
    }

    @Override
    protected void doRecordDouble(double value) {
      int bucketIndex = ExplicitBucketHistogramUtils.findBucketIndex(this.boundaries, value);
      Cell[] cs = cells;
      if (cs == null) {
        // Uncontended fast path: try base without blocking.
        if (base.lock.tryLock()) {
          try {
            updateCell(base, value, bucketIndex);
            return;
          } finally {
            base.lock.unlock();
          }
        }
        // tryLock() failed: another recorder holds base. Escalate to cells (once).
        cs = escalate();
      }
      // Post-escalation path: route by thread id and block on the chosen cell if necessary.
      Cell cell = cs[Math.abs((int) (Thread.currentThread().getId() % cs.length))];
      cell.lock.lock();
      try {
        updateCell(cell, value, bucketIndex);
      } finally {
        cell.lock.unlock();
      }
    }

    private void updateCell(Cell cell, double value, int bucketIndex) {
      cell.sum += value;
      if (recordMinMax) {
        if (value < cell.min) {
          cell.min = value;
        }
        if (value > cell.max) {
          cell.max = value;
        }
      }
      cell.counts[bucketIndex]++;
    }

    /**
     * Install a fresh cells array via CAS. One-shot: if this thread loses the race, it uses the
     * winning thread's array instead. Never grows or reclaims.
     */
    private Cell[] escalate() {
      Cell[] existing = cells;
      if (existing != null) {
        return existing;
      }
      int n = Runtime.getRuntime().availableProcessors();
      Cell[] fresh = new Cell[n];
      int bucketCount = boundaries.length + 1;
      for (int i = 0; i < n; i++) {
        fresh[i] = new Cell(bucketCount);
      }
      if (CELLS.compareAndSet(this, null, fresh)) {
        return fresh;
      }
      // Lost the race; a concurrent recorder installed cells first. The CAS only failed because
      // cells is now non-null, so requireNonNull is safe.
      return Objects.requireNonNull(cells, "cells");
    }

    @Override
    protected HistogramPointData doAggregateThenMaybeResetDoubles(
        long startEpochNanos,
        long epochNanos,
        Attributes attributes,
        List<DoubleExemplarData> exemplars,
        boolean reset) {
      // Acquire base + all cell locks for a consistent cross-cell snapshot. Recorders on this
      // series are paused for the duration of the merge; the merge is O(cells * buckets).
      base.lock.lock();
      Cell[] cs = cells;
      if (cs != null) {
        for (Cell c : cs) {
          c.lock.lock();
        }
      }
      try {
        Arrays.fill(countsScratch, 0);
        double sum = 0;
        long count = 0;
        double min = Double.MAX_VALUE;
        double max = -1;

        // Merge base.
        sum += base.sum;
        if (recordMinMax) {
          if (base.min < min) {
            min = base.min;
          }
          if (base.max > max) {
            max = base.max;
          }
        }
        for (int i = 0; i < base.counts.length; i++) {
          long c = base.counts[i];
          countsScratch[i] += c;
          count += c;
        }
        if (reset) {
          resetCell(base);
        }

        // Merge cells if escalated.
        if (cs != null) {
          for (Cell cell : cs) {
            sum += cell.sum;
            if (recordMinMax) {
              if (cell.min < min) {
                min = cell.min;
              }
              if (cell.max > max) {
                max = cell.max;
              }
            }
            for (int i = 0; i < cell.counts.length; i++) {
              long c = cell.counts[i];
              countsScratch[i] += c;
              count += c;
            }
            if (reset) {
              resetCell(cell);
            }
          }
        }

        HistogramPointData pointData;
        if (reusablePoint == null) {
          pointData =
              ImmutableHistogramPointData.create(
                  startEpochNanos,
                  epochNanos,
                  attributes,
                  sum,
                  recordMinMax && count > 0,
                  recordMinMax ? min : 0,
                  recordMinMax && count > 0,
                  recordMinMax ? max : 0,
                  boundaryList,
                  PrimitiveLongList.wrap(Arrays.copyOf(countsScratch, countsScratch.length)),
                  exemplars);
        } else /* REUSABLE_DATA */ {
          pointData =
              reusablePoint.set(
                  startEpochNanos,
                  epochNanos,
                  attributes,
                  sum,
                  recordMinMax && count > 0,
                  recordMinMax ? min : 0,
                  recordMinMax && count > 0,
                  recordMinMax ? max : 0,
                  boundaryList,
                  countsScratch,
                  exemplars);
        }
        return pointData;
      } finally {
        if (cs != null) {
          for (Cell c : cs) {
            c.lock.unlock();
          }
        }
        base.lock.unlock();
      }
    }

    private static void resetCell(Cell cell) {
      cell.sum = 0;
      cell.min = Double.MAX_VALUE;
      cell.max = -1;
      Arrays.fill(cell.counts, 0);
    }

    /**
     * One shard of a striped histogram. All mutable fields are guarded by {@link #lock}. Fields are
     * package-private so that {@link Handle} can read them directly during merge without incurring
     * accessor overhead.
     */
    private static final class Cell {
      final ReentrantLock lock = new ReentrantLock();
      final long[] counts;
      double sum;
      double min = Double.MAX_VALUE;
      double max = -1;

      Cell(int buckets) {
        this.counts = new long[buckets];
      }
    }
  }
}
