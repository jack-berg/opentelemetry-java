/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import static io.opentelemetry.sdk.common.export.MemoryMode.REUSABLE_DATA;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.internal.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.internal.aggregator.AggregatorHandle;
import io.opentelemetry.sdk.metrics.internal.aggregator.EmptyMetricData;
import io.opentelemetry.sdk.metrics.internal.descriptor.MetricDescriptor;
import io.opentelemetry.sdk.metrics.internal.view.AttributesProcessor;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

class CumulativeSynchronousMetricStorage<T extends PointData>
    extends DefaultSynchronousMetricStorage<T> {
  private final MemoryMode memoryMode;
  private final ConcurrentHashMap<Attributes, AggregatorHandle<T>> aggregatorHandles =
      new ConcurrentHashMap<>();
  // Only populated if memoryMode == REUSABLE_DATA
  private final ArrayList<T> reusableResultList = new ArrayList<>();

  CumulativeSynchronousMetricStorage(
      MetricDescriptor metricDescriptor,
      Aggregator<T> aggregator,
      AttributesProcessor attributesProcessor,
      Clock clock,
      int maxCardinality,
      boolean enabled,
      MemoryMode memoryMode) {
    super(metricDescriptor, aggregator, attributesProcessor, clock, maxCardinality, enabled);
    this.memoryMode = memoryMode;
  }

  @Override
  public void recordLong(long value, Attributes attributes, Context context) {
    getAggregatorHandle(attributes, context).recordLong(value, attributes, context);
  }

  @Override
  public void recordDouble(double value, Attributes attributes, Context context) {
    getAggregatorHandle(attributes, context).recordDouble(value, attributes, context);
  }

  @Override
  public BoundStorageHandle bind(Attributes attributes) {
    // Cumulative handles are stable for the instrument's lifetime (the map is never swapped and
    // handles are never reset/pooled), so resolve the handle once here and let the bound
    // instrument record straight onto it. Callers are expected to gate on
    // {@link #isEnabled()} / {@link #shouldRecordDouble} before invoking.
    return getAggregatorHandle(attributes, Context.current());
  }

  private AggregatorHandle<T> getAggregatorHandle(Attributes attributes, Context context) {
    Objects.requireNonNull(attributes, "attributes");
    attributes = attributesProcessor.process(attributes, context);
    AggregatorHandle<T> handle = aggregatorHandles.get(attributes);
    if (handle != null) {
      return handle;
    }
    if (aggregatorHandles.size() >= maxCardinality) {
      logger.log(
          Level.WARNING,
          "Instrument "
              + metricDescriptor.getSourceInstrument().getName()
              + " has exceeded the maximum allowed cardinality ("
              + maxCardinality
              + ").");
      // Return handle for overflow series, first checking if a handle already exists for it
      attributes = MetricStorage.CARDINALITY_OVERFLOW;
      handle = aggregatorHandles.get(attributes);
      if (handle != null) {
        return handle;
      }
    }
    AggregatorHandle<T> newHandle = aggregator.createHandle(clock.now());
    handle = aggregatorHandles.putIfAbsent(attributes, newHandle);
    return handle != null ? handle : newHandle;
  }

  @Override
  public MetricData collect(
      Resource resource, InstrumentationScopeInfo instrumentationScopeInfo, long epochNanos) {
    List<T> points;
    if (memoryMode == REUSABLE_DATA) {
      reusableResultList.clear();
      points = reusableResultList;
    } else {
      points = new ArrayList<>(aggregatorHandles.size());
    }

    // Grab aggregated points.
    aggregatorHandles.forEach(
        (attributes, handle) -> {
          if (!handle.hasRecordedValues()) {
            return;
          }
          // Start time for cumulative synchronous instruments is the time the first series
          // measurement was recorded. I.e. the time the AggregatorHandle was created.
          T point =
              handle.aggregateThenMaybeReset(
                  handle.getCreationEpochNanos(), epochNanos, attributes, /* reset= */ false);

          if (point != null) {
            points.add(point);
          }
        });

    if (points.isEmpty() || !enabled) {
      return EmptyMetricData.getInstance();
    }

    return aggregator.toMetricData(
        resource, instrumentationScopeInfo, metricDescriptor, points, CUMULATIVE);
  }
}
