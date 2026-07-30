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
import io.opentelemetry.sdk.metrics.internal.concurrent.AdderUtil;
import io.opentelemetry.sdk.metrics.internal.concurrent.DoubleAdder;
import io.opentelemetry.sdk.metrics.internal.concurrent.LongAdder;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;
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
   * Lock-free histogram handle inspired by the Prometheus Java client's classic histogram.
   *
   * <p>Bucket counts and running sum use {@link LongAdder} / {@link DoubleAdder}; min and max use
   * CAS loops on volatile long bit patterns with a fast-exit for non-extremes. Total count is
   * derived from bucket counts at collect.
   *
   * <p>A thread-striped {@link AtomicLong} array coordinates record and collect. Its sign bit
   * signals "collect in progress"; recorders back out and spin when they observe it. This prevents
   * an observation's writes from being split across a delta reset boundary, which would produce
   * count/sum inconsistencies at collect points.
   *
   * <p>Within {@link #doRecordDouble} the bucket increment is the last write. Its internal volatile
   * write publishes the prior sum/min/max writes, so the collector's wait for {@code
   * sum(bucketCounts) >= expected} doubles as a barrier for the whole observation.
   *
   * <p>Wedge trade-off: a recorder that has committed its stripe reservation (successful CAS) is
   * guaranteed to publish its bucket increment via a {@code try/finally} in {@link
   * #doRecordDouble}, even if the sum/min/max writes throw ({@code OutOfMemoryError} from a
   * Striped64 cell allocation, thread death, etc.). This keeps Phase 2's wait bounded by recorder
   * progress rather than requiring a timeout. The trade-off is that the failing observation may
   * have partial sum/min/max (bucket count includes it, but sum may be behind by one value) — a
   * single-observation blip rather than a permanent skew.
   */
  static final class Handle extends AggregatorHandle<HistogramPointData> {
    private static final long COLLECT_BIT = 1L << 63;
    private static final long MIN_INIT_BITS = Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
    private static final long MAX_INIT_BITS = Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY);

    private final List<Double> boundaryList;
    private final double[] boundaries;
    private final boolean recordMinMax;

    private final LongAdder[] bucketCounts;
    private final DoubleAdder sum = AdderUtil.createDoubleAdder();

    // Min / max as raw double bits so they can be CAS-updated via AtomicLongFieldUpdater. Updated
    // via CAS loops that fast-exit when the observation isn't a new extreme — the common
    // steady-state case has no memory write.
    @SuppressWarnings("UnusedVariable")
    private volatile long minBits = MIN_INIT_BITS;

    @SuppressWarnings("UnusedVariable")
    private volatile long maxBits = MAX_INIT_BITS;

    private static final AtomicLongFieldUpdater<Handle> MIN_BITS =
        AtomicLongFieldUpdater.newUpdater(Handle.class, "minBits");
    private static final AtomicLongFieldUpdater<Handle> MAX_BITS =
        AtomicLongFieldUpdater.newUpdater(Handle.class, "maxBits");

    // Power-of-2 length so the stripe probe is a bitwise AND with stripeMask.
    private final AtomicLong[] stripedStartedCounter;
    private final int stripeMask;

    // Sum of bucket counts drained in previous delta resets. Added to bucketSumTotal when
    // comparing against the cumulative started counter so the Phase 2 wait works for both
    // cumulative (offset stays 0, buckets never reset) and delta (offset accumulates, buckets
    // reset each cycle). Collector-only.
    private long bucketResetOffset;

    private final long[] countsScratch;

    // Non-null only when MemoryMode == REUSABLE_DATA.
    @Nullable private final MutableHistogramPointData reusablePoint;

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
      this.bucketCounts = new LongAdder[bucketCount];
      for (int i = 0; i < bucketCount; i++) {
        this.bucketCounts[i] = AdderUtil.createLongAdder();
      }
      // Fixed at 4 (power of 2 so the probe mask compiles to a bitwise AND). Small enough to
      // keep per-handle memory footprint low at high cardinality where per-handle contention is
      // naturally minimal (many threads spread across many handles); large enough to distribute
      // 4-way single-handle contention with acceptable collision rates.
      int stripes = 4;
      this.stripedStartedCounter = new AtomicLong[stripes];
      for (int i = 0; i < stripes; i++) {
        this.stripedStartedCounter[i] = new AtomicLong();
      }
      this.stripeMask = stripes - 1;
      this.countsScratch = new long[bucketCount];
      if (memoryMode == MemoryMode.REUSABLE_DATA) {
        this.reusablePoint = new MutableHistogramPointData(bucketCount);
      } else {
        this.reusablePoint = null;
      }
    }

    @Override
    public void recordLong(long value, Attributes attributes, Context context) {
      // There is no LongExplicitBucketHistogramAggregator; redirect to recordDouble so
      // LongHistogram measurements route through this handle.
      super.recordDouble((double) value, attributes, context);
    }

    @Override
    @SuppressWarnings("ThreadPriorityCheck")
    protected void doRecordDouble(double value) {
      // Compute the bucket index before reserving on the stripe so that a failure here can't
      // leave a stranded reservation.
      int bucketIndex = ExplicitBucketHistogramUtils.findBucketIndex(this.boundaries, value);

      // Reserve a pre-flip slot on our stripe via CAS. Increment only when the bit is clear at
      // the time of the CAS; otherwise spin until the collector's Phase 4 clears the bit and
      // retry. This avoids the inc-then-dec back-out pattern, which could leave a transient +1
      // on the stripe visible to a later Phase 1 if the recorder was preempted between the inc
      // and dec across a collect cycle boundary.
      AtomicLong stripe =
          stripedStartedCounter[System.identityHashCode(Thread.currentThread()) & stripeMask];
      while (true) {
        long current = stripe.get();
        if ((current & COLLECT_BIT) != 0) {
          while ((stripe.get() & COLLECT_BIT) != 0) {
            Thread.yield();
          }
          continue;
        }
        if (stripe.compareAndSet(current, current + 1)) {
          break;
        }
        // CAS lost the race (either the bit was just set or another recorder incremented);
        // loop and reevaluate.
      }

      // Bucket increment in a finally so it happens even on exception. This makes bucket publish
      // a guaranteed post-condition of a successful reservation, so Phase 2's wait can always
      // progress. On the failure path the observation has partial sum/min/max but a consistent
      // count.
      try {
        sum.add(value);
        if (recordMinMax) {
          updateMin(value);
          updateMax(value);
        }
      } finally {
        bucketCounts[bucketIndex].increment();
      }
    }

    /** Fast-exits without a CAS when {@code value} is not smaller than the current min. */
    private void updateMin(double value) {
      long newBits = Double.doubleToRawLongBits(value);
      long cur;
      do {
        cur = minBits;
        if (value >= Double.longBitsToDouble(cur)) {
          return;
        }
      } while (!MIN_BITS.compareAndSet(this, cur, newBits));
    }

    /** Fast-exits without a CAS when {@code value} is not larger than the current max. */
    private void updateMax(double value) {
      long newBits = Double.doubleToRawLongBits(value);
      long cur;
      do {
        cur = maxBits;
        if (value <= Double.longBitsToDouble(cur)) {
          return;
        }
      } while (!MAX_BITS.compareAndSet(this, cur, newBits));
    }

    @Override
    @SuppressWarnings("ThreadPriorityCheck")
    protected HistogramPointData doAggregateThenMaybeResetDoubles(
        long startEpochNanos,
        long epochNanos,
        Attributes attributes,
        List<DoubleExemplarData> exemplars,
        boolean reset) {
      // Phase 1: set the collect bit on every stripe; capture the pre-flip cumulative count.
      long cumulativeStarted = 0;
      for (AtomicLong stripe : stripedStartedCounter) {
        cumulativeStarted += stripe.getAndAdd(COLLECT_BIT) & ~COLLECT_BIT;
      }

      // Phase 2: wait for pre-flip recorders to publish their bucket increments. Post-flip
      // recorders are spinning on the collect bit, so nothing new arrives. Because
      // doRecordDouble publishes the bucket increment in a finally block, every successful
      // stripe reservation is guaranteed to eventually reach the bucket — the wait is bounded by
      // recorder progress, not clock time.
      while (bucketSumTotal() + bucketResetOffset < cumulativeStarted) {
        Thread.yield();
      }

      // Phase 3: snapshot (and reset if delta) with recorders quiescent.
      long totalCount = 0;
      for (int i = 0; i < bucketCounts.length; i++) {
        long c = reset ? bucketCounts[i].sumThenReset() : bucketCounts[i].sum();
        countsScratch[i] = c;
        totalCount += c;
      }
      if (reset) {
        bucketResetOffset += totalCount;
      }
      double totalSum = reset ? sum.sumThenReset() : sum.sum();

      double snapshotMin = Double.POSITIVE_INFINITY;
      double snapshotMax = Double.NEGATIVE_INFINITY;
      if (recordMinMax) {
        long minSnapshot = reset ? MIN_BITS.getAndSet(this, MIN_INIT_BITS) : minBits;
        long maxSnapshot = reset ? MAX_BITS.getAndSet(this, MAX_INIT_BITS) : maxBits;
        snapshotMin = Double.longBitsToDouble(minSnapshot);
        snapshotMax = Double.longBitsToDouble(maxSnapshot);
      }

      // Phase 4: clear the collect bit. addAndGet(COLLECT_BIT) toggles the sign bit off via
      // two's-complement overflow. Spinning recorders resume.
      for (AtomicLong stripe : stripedStartedCounter) {
        stripe.addAndGet(COLLECT_BIT);
      }

      HistogramPointData pointData;
      if (reusablePoint == null) {
        pointData =
            ImmutableHistogramPointData.create(
                startEpochNanos,
                epochNanos,
                attributes,
                totalSum,
                recordMinMax && totalCount > 0,
                recordMinMax ? snapshotMin : 0,
                recordMinMax && totalCount > 0,
                recordMinMax ? snapshotMax : 0,
                boundaryList,
                PrimitiveLongList.wrap(Arrays.copyOf(countsScratch, countsScratch.length)),
                exemplars);
      } else /* REUSABLE_DATA */ {
        pointData =
            reusablePoint.set(
                startEpochNanos,
                epochNanos,
                attributes,
                totalSum,
                recordMinMax && totalCount > 0,
                recordMinMax ? snapshotMin : 0,
                recordMinMax && totalCount > 0,
                recordMinMax ? snapshotMax : 0,
                boundaryList,
                countsScratch,
                exemplars);
      }
      return pointData;
    }

    private long bucketSumTotal() {
      long total = 0;
      for (LongAdder adder : bucketCounts) {
        total += adder.sum();
      }
      return total;
    }
  }
}
