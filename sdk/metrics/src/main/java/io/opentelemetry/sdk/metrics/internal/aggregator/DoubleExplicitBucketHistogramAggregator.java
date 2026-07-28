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
   * <p>The record path is fully lock-free: per-bucket counts and the running sum use {@link
   * LongAdder} / {@link DoubleAdder} (Striped64-backed, so they scale with contention); min and max
   * use CAS loops on volatile long fields holding the bit patterns. Total count is derived from
   * bucket counts at collect time, saving a per-record atomic op.
   *
   * <p>Record and collect are coordinated via a thread-striped {@link AtomicLong} array whose sign
   * bit signals "collect in progress". Recorders increment their stripe's counter and, if the bit
   * is set, back out and spin until the collector clears it. This prevents an observation from
   * being split across two collection cycles at a delta reset boundary (bucket increment counted in
   * one cycle, sum contribution counted in the next), which would otherwise produce count/sum
   * inconsistencies that break downstream avg/ratio calculations.
   *
   * <p>Ordering trick that avoids a separate completion counter: within {@link #doRecordDouble} the
   * bucket increment is the last write. The internal volatile write inside {@code LongAdder.add}
   * publishes all prior writes (sum, min, max) via Java's happens-before, so the collector's
   * observation of {@code sum(bucketCounts) >= expected} is sufficient to conclude that all
   * pre-flip recorders have completed every field write.
   *
   * <p>Under a single recorder the fast path is: one CAS on the striped started counter, one CAS on
   * the sum adder base, one CAS on the bucket adder base, plus (in steady state where min/max have
   * settled) two volatile reads for the min/max fast exits. Under multi-threaded recording each
   * component scales independently via its own Striped64 backing; the striped started counter
   * distributes recorders across {@code NCPUS} cache lines.
   */
  static final class Handle extends AggregatorHandle<HistogramPointData> {
    private static final long MIN_INIT_BITS = Double.doubleToRawLongBits(Double.POSITIVE_INFINITY);
    private static final long MAX_INIT_BITS = Double.doubleToRawLongBits(Double.NEGATIVE_INFINITY);

    // Sign bit of a stripedStartedCounter entry; set by the collector while collect is in progress.
    // Adding it toggles the bit (via two's-complement overflow) each time.
    private static final long COLLECT_BIT = 1L << 63;

    // read-only
    private final List<Double> boundaryList;
    // read-only
    private final double[] boundaries;
    private final boolean recordMinMax;

    // One LongAdder per bucket. Uncontended add is one CAS on the adder's base; under contention
    // Striped64 allocates cells and scales.
    private final LongAdder[] bucketCounts;
    // Running sum. DoubleAdder is Striped64-backed just like LongAdder.
    private final DoubleAdder sum = AdderUtil.createDoubleAdder();

    // Min / max stored as raw long bits of the observed doubles. Updated via CAS loops that
    // fast-exit when the observation isn't a new extreme, which is the common steady-state case.
    // Not touched on the record path when recordMinMax is false.
    @SuppressWarnings("UnusedVariable")
    private volatile long minBits = MIN_INIT_BITS;

    @SuppressWarnings("UnusedVariable")
    private volatile long maxBits = MAX_INIT_BITS;

    private static final AtomicLongFieldUpdater<Handle> MIN_BITS =
        AtomicLongFieldUpdater.newUpdater(Handle.class, "minBits");
    private static final AtomicLongFieldUpdater<Handle> MAX_BITS =
        AtomicLongFieldUpdater.newUpdater(Handle.class, "maxBits");

    // Thread-striped counters used to coordinate record/collect. Low 63 bits are a monotonic
    // observation count for the stripe; sign bit is set by the collector while collect is in
    // progress. Recorders that observe the bit set back out and spin. Cumulative across cycles;
    // the collector diffs against #lastCumulativeStarted to get the per-cycle expected count.
    private final AtomicLong[] stripedStartedCounter;

    // Sum of stripedStartedCounter low bits captured at the end of the previous collect. Used to
    // derive the current cycle's expected observation count. Only touched by the collector.
    private long lastCumulativeStarted;

    // Scratch buffer used during collection to accumulate per-bucket counts. Not touched on the
    // record path.
    private final long[] countsScratch;

    // Used only when MemoryMode = REUSABLE_DATA
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
      int stripes = Runtime.getRuntime().availableProcessors();
      this.stripedStartedCounter = new AtomicLong[stripes];
      for (int i = 0; i < stripes; i++) {
        this.stripedStartedCounter[i] = new AtomicLong();
      }
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
    @SuppressWarnings("ThreadPriorityCheck")
    protected void doRecordDouble(double value) {
      // Acquire a "pre-flip" slot on our stripe. If collect is in progress (sign bit set), back
      // out and spin until it finishes.
      AtomicLong stripe =
          stripedStartedCounter[
              (int) (Thread.currentThread().getId() % stripedStartedCounter.length)];
      while (true) {
        long c = stripe.incrementAndGet();
        if ((c & COLLECT_BIT) == 0) {
          break;
        }
        stripe.decrementAndGet();
        while ((stripe.get() & COLLECT_BIT) != 0) {
          Thread.yield();
        }
      }

      int bucketIndex = ExplicitBucketHistogramUtils.findBucketIndex(this.boundaries, value);
      // Ordered writes: sum, min, max, then bucket increment last. The internal volatile write
      // inside LongAdder.increment() publishes the prior writes to any thread that later observes
      // this bucket's incremented value, so the collector's "sum(bucketCounts) >= expected" wait
      // is a valid barrier for the whole observation.
      sum.add(value);
      if (recordMinMax) {
        updateMin(value);
        updateMax(value);
      }
      bucketCounts[bucketIndex].increment();
    }

    /**
     * CAS-loop min update. Fast-exits without touching memory when {@code value} is not smaller.
     */
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

    /** CAS-loop max update. Fast-exits without touching memory when {@code value} is not larger. */
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
      // Phase 1: flip the collect bit on every stripe and sum the pre-flip low bits. This gives
      // the cumulative count of observations that started before collect began.
      long cumulativeStarted = 0;
      for (AtomicLong stripe : stripedStartedCounter) {
        cumulativeStarted += stripe.getAndAdd(COLLECT_BIT) & ~COLLECT_BIT;
      }
      long expectedThisCycle = cumulativeStarted - lastCumulativeStarted;

      // Phase 2: wait for the completion signal. Because bucketCounts.increment() is the last
      // write in doRecordDouble, once the current cycle's bucket sum reaches expectedThisCycle,
      // all pre-flip recorders have finished writing sum, min, max, and their bucket. Post-flip
      // recorders are spinning on the collect bit, so no new writes arrive during Phase 3.
      //
      // The bucketCounts adders are per-cycle in delta mode (reset at the end of the previous
      // Phase 3) and cumulative in cumulative mode. In cumulative mode, expectedThisCycle is
      // still just the count of observations since the previous collect, since
      // lastCumulativeStarted
      // is subtracted; the bucket sum grows to at least match without ever being reset.
      while (bucketSumTotal() < expectedThisCycle) {
        Thread.yield();
      }

      // Phase 3: snapshot (and reset if delta). Recorders are quiescent, so this is atomic from
      // the recorder's perspective.
      long totalCount = 0;
      for (int i = 0; i < bucketCounts.length; i++) {
        long c = reset ? bucketCounts[i].sumThenReset() : bucketCounts[i].sum();
        countsScratch[i] = c;
        totalCount += c;
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

      // Phase 4: clear the collect bit on every stripe (via two's-complement overflow of
      // adding 1L << 63 to a value that already has the sign bit set). Spinning recorders resume.
      lastCumulativeStarted = cumulativeStarted;
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
