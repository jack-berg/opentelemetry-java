/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.aggregator;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.DoubleExemplarData;
import io.opentelemetry.sdk.metrics.data.ExemplarData;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.MetricDataType;
import io.opentelemetry.sdk.metrics.internal.descriptor.MetricDescriptor;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Map;
import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;

/**
 * Aggregator represents the abstract class for all the available aggregations that can be computed
 * during the accumulation phase for all the instrument.
 *
 * <p>The synchronous instruments will create an {@link AggregatorHandle} to record individual
 * measurements synchronously, and for asynchronous the {@link #accumulateDoubleMeasurement} or
 * {@link #accumulateLongMeasurement} will be used when reading values from the instrument
 * callbacks.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@Immutable
public interface Aggregator<T, U extends ExemplarData> {
  /** Returns the drop aggregator, an aggregator that drops measurements. */
  static Aggregator<Object, DoubleExemplarData> drop() {
    return DropAggregator.INSTANCE;
  }

  /**
   * Returns a new {@link AggregatorHandle}. This MUST by used by the synchronous to aggregate
   * recorded measurements during the collection cycle.
   *
   * @return a new {@link AggregatorHandle}.
   */
  AggregatorHandle<T, U> createHandle();

  /**
   * Returns a new {@code Accumulation} for the given value. This MUST be used by the asynchronous
   * instruments to create {@code Accumulation} that are passed to the processor.
   *
   * @param value the given value to be used to create the {@code Accumulation}.
   * @return a new {@code Accumulation} for the given value, or {@code null} if there are no
   *     recordings.
   */
  @Nullable
  default T accumulateLongMeasurement(long value, Attributes attributes, Context context) {
    AggregatorHandle<T, U> handle = createHandle();
    handle.recordLong(value, attributes, context);
    return handle.accumulateThenReset(attributes, /* reset= */ true);
  }

  /**
   * Returns a new {@code Accumulation} for the given value. This MUST be used by the asynchronous
   * instruments to create {@code Accumulation} that are passed to the processor.
   *
   * @param value the given value to be used to create the {@code Accumulation}.
   * @return a new {@code Accumulation} for the given value, or {@code null} if there are no
   *     recordings.
   */
  @Nullable
  default T accumulateDoubleMeasurement(double value, Attributes attributes, Context context) {
    AggregatorHandle<T, U> handle = createHandle();
    handle.recordDouble(value, attributes, context);
    return handle.accumulateThenReset(attributes, /* reset= */ true);
  }

  /**
   * Returns a new DELTA aggregation by comparing two cumulative measurements.
   *
   * <p>Aggregators MUST implement diff if it can be used with asynchronous instruments.
   *
   * @param previousCumulative the previously captured accumulation.
   * @param currentCumulative the newly captured (cumulative) accumulation.
   * @return The resulting delta accumulation.
   */
  default T diff(T previousCumulative, T currentCumulative) {
    throw new UnsupportedOperationException("This aggregator does not support diff.");
  }

  /**
   * Returns the {@link MetricData} that this {@code Aggregation} will produce.
   *
   * @param resource the resource producing the metric.
   * @param instrumentationScopeInfo the scope that instrumented the metric.
   * @param metricDescriptor the name, description and unit of the metric.
   * @param accumulationByLabels the map of Labels to Accumulation.
   * @param temporality the temporality of the accumulation.
   * @param startEpochNanos the startEpochNanos for the {@code Point}.
   * @param epochNanos the epochNanos for the {@code Point}.
   * @return the {@link MetricDataType} that this {@code Aggregation} will produce.
   */
  MetricData toMetricData(
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      MetricDescriptor metricDescriptor,
      Map<Attributes, T> accumulationByLabels,
      AggregationTemporality temporality,
      long startEpochNanos,
      long lastCollectionEpoch,
      long epochNanos);
}
