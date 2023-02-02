/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData;
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData;
import io.opentelemetry.sdk.metrics.internal.descriptor.MetricDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarReservoir;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Aggregator that generates explicit bucket histograms.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DoubleExplicitBucketHistogramAggregator
    implements Aggregator<ExplicitBucketHistogramAccumulation, DoubleExemplarData> {
  private final double[] boundaries;

  // a cache for converting to MetricData
  private final List<Double> boundaryList;

  private final Supplier<ExemplarReservoir<DoubleExemplarData>> reservoirSupplier;

  /**
   * Constructs an explicit bucket histogram aggregator.
   *
   * @param boundaries Bucket boundaries, in-order.
   * @param reservoirSupplier Supplier of exemplar reservoirs per-stream.
   */
  public DoubleExplicitBucketHistogramAggregator(
      double[] boundaries, Supplier<ExemplarReservoir<DoubleExemplarData>> reservoirSupplier) {
    this.boundaries = boundaries;

    List<Double> boundaryList = new ArrayList<>(this.boundaries.length);
    for (double v : this.boundaries) {
      boundaryList.add(v);
    }
    this.boundaryList = Collections.unmodifiableList(boundaryList);
    this.reservoirSupplier = reservoirSupplier;
  }

  @Override
  public AggregatorHandle<ExplicitBucketHistogramAccumulation, DoubleExemplarData> createHandle() {
    return new Handle(this.boundaries, reservoirSupplier.get());
  }

  @Override
  public MetricData toMetricData(
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      MetricDescriptor metricDescriptor,
      Map<Attributes, ExplicitBucketHistogramAccumulation> accumulationByLabels,
      AggregationTemporality temporality,
      long startEpochNanos,
      long lastCollectionEpoch,
      long epochNanos) {
    return ImmutableMetricData.createDoubleHistogram(
        resource,
        instrumentationScopeInfo,
        metricDescriptor.getName(),
        metricDescriptor.getDescription(),
        metricDescriptor.getSourceInstrument().getUnit(),
        ImmutableHistogramData.create(
            temporality,
            MetricDataUtils.toExplicitBucketHistogramPointList(
                accumulationByLabels,
                (temporality == AggregationTemporality.CUMULATIVE)
                    ? startEpochNanos
                    : lastCollectionEpoch,
                epochNanos,
                boundaryList)));
  }

  static final class Handle
      extends AggregatorHandle<ExplicitBucketHistogramAccumulation, DoubleExemplarData> {
    // read-only
    private final double[] boundaries;

    @GuardedBy("lock")
    private double sum;

    @GuardedBy("lock")
    private double min;

    @GuardedBy("lock")
    private double max;

    @GuardedBy("lock")
    private long count;

    @GuardedBy("lock")
    private final long[] counts;

    private final ReentrantLock lock = new ReentrantLock();

    Handle(double[] boundaries, ExemplarReservoir<DoubleExemplarData> reservoir) {
      super(reservoir);
      this.boundaries = boundaries;
      this.counts = new long[this.boundaries.length + 1];
      this.sum = 0;
      this.min = Double.MAX_VALUE;
      this.max = -1;
      this.count = 0;
    }

    @Override
    protected ExplicitBucketHistogramAccumulation doAccumulateThenMaybeReset(
        List<DoubleExemplarData> exemplars, boolean reset) {
      lock.lock();
      try {
        ExplicitBucketHistogramAccumulation acc =
            ExplicitBucketHistogramAccumulation.create(
                sum,
                this.count > 0,
                this.count > 0 ? this.min : -1,
                this.count > 0 ? this.max : -1,
                Arrays.copyOf(counts, counts.length),
                exemplars);
        if (reset) {
          this.sum = 0;
          this.min = Double.MAX_VALUE;
          this.max = -1;
          this.count = 0;
          Arrays.fill(this.counts, 0);
        }
        return acc;
      } finally {
        lock.unlock();
      }
    }

    @Override
    protected void doRecordDouble(double value) {
      int bucketIndex = ExplicitBucketHistogramUtils.findBucketIndex(this.boundaries, value);

      lock.lock();
      try {
        this.sum += value;
        this.min = Math.min(this.min, value);
        this.max = Math.max(this.max, value);
        this.count++;
        this.counts[bucketIndex]++;
      } finally {
        lock.unlock();
      }
    }

    @Override
    protected void doRecordLong(long value) {
      doRecordDouble((double) value);
    }
  }
}
