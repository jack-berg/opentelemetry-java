/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.internal.PrimitiveLongList;
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
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.concurrent.atomic.LongAdder;
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
  private final MemoryMode memoryMode;

  // a cache for converting to MetricData
  private final List<Double> boundaryList;

  private final ExemplarReservoirFactory reservoirFactory;

  /**
   * Constructs an explicit bucket histogram aggregator.
   *
   * @param boundaries Bucket boundaries, in-order.
   * @param reservoirFactory Supplier of exemplar reservoirs per-stream.
   * @param memoryMode The {@link MemoryMode} to use in this aggregator.
   */
  public DoubleExplicitBucketHistogramAggregator(
      double[] boundaries, ExemplarReservoirFactory reservoirFactory, MemoryMode memoryMode) {
    this.boundaries = boundaries;
    this.memoryMode = memoryMode;

    List<Double> boundaryList = new ArrayList<>(this.boundaries.length);
    for (double v : this.boundaries) {
      boundaryList.add(v);
    }
    this.boundaryList = Collections.unmodifiableList(boundaryList);
    this.reservoirFactory = reservoirFactory;
  }

  @Override
  public AggregatorHandle<HistogramPointData> createHandle() {
    return new Handle(boundaryList, boundaries, reservoirFactory, memoryMode);
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

  static final class Handle extends AggregatorHandle<HistogramPointData> {
    // read-only
    private final List<Double> boundaryList;
    // read-only
    private final double[] boundaries;

    private final DoubleAdder sum = new DoubleAdder();
    private final DoubleAccumulator min = new DoubleAccumulator(Math::min, Double.MAX_VALUE);
    private final DoubleAccumulator max = new DoubleAccumulator(Math::max, -1);
    private final java.util.concurrent.atomic.LongAdder[] counts;
    private final long[] countsArr;

    // Used only when MemoryMode = REUSABLE_DATA
    @Nullable private final MutableHistogramPointData reusablePoint;

    Handle(
        List<Double> boundaryList,
        double[] boundaries,
        ExemplarReservoirFactory reservoirFactory,
        MemoryMode memoryMode) {
      super(reservoirFactory, /* isDoubleType= */ true);
      this.boundaryList = boundaryList;
      this.boundaries = boundaries;
      this.counts = new java.util.concurrent.atomic.LongAdder[this.boundaries.length + 1];
      this.countsArr = new long[this.boundaries.length + 1];
      for (int i = 0; i < counts.length; i++) {
        counts[i] = new LongAdder();
      }
      if (memoryMode == MemoryMode.REUSABLE_DATA) {
        this.reusablePoint = new MutableHistogramPointData(counts.length);
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
    protected HistogramPointData doAggregateThenMaybeResetDoubles(
        long startEpochNanos,
        long epochNanos,
        Attributes attributes,
        List<DoubleExemplarData> exemplars,
        boolean reset) {
      // TODO: if the cumulative path isn't going to provide concurrency controls, then this code needs adjustment to avoid partial writes.
      HistogramPointData pointData;
      long currentCount = 0;
      for (int i = 0; i < counts.length; i++) {
        long bucketCount = counts[i].sum();
        countsArr[i] = bucketCount;
        currentCount += bucketCount;
      }
      if (reusablePoint == null) {
        pointData =
            ImmutableHistogramPointData.create(
                startEpochNanos,
                epochNanos,
                attributes,
                sum.sum(),
                currentCount > 0,
                this.min.get(),
                currentCount > 0,
                this.max.get(),
                boundaryList,
                PrimitiveLongList.wrap(Arrays.copyOf(countsArr, countsArr.length)),
                exemplars);
      } else /* REUSABLE_DATA */ {
        pointData =
            reusablePoint.set(
                startEpochNanos,
                epochNanos,
                attributes,
                sum.sum(),
                currentCount > 0,
                this.min.get(),
                currentCount > 0,
                this.max.get(),
                boundaryList,
                countsArr,
                exemplars);
      }
      if (reset) {
        this.sum.reset();
        this.min.reset();
        this.max.reset();
        for (int i = 0; i < counts.length; i++) {
          counts[i].reset();
        }
        Arrays.fill(this.countsArr, 0);
      }
      return pointData;
    }

    @Override
    protected void doRecordDouble(double value) {
      int bucketIndex = ExplicitBucketHistogramUtils.findBucketIndex(this.boundaries, value);

      this.sum.add(value);
      this.min.accumulate(value);
      this.max.accumulate(value);
      this.counts[bucketIndex].increment();
    }
  }
}
