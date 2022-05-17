/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.export;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.io.Closeable;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * {@code MetricExporter} is the interface that all "push based" metric libraries should use to
 * export metrics to the OpenTelemetry exporters.
 *
 * <p>All OpenTelemetry exporters should allow access to a {@code MetricExporter} instance.
 */
public interface MetricExporter
    extends AggregationTemporalitySelector, DefaultAggregationSelector, Closeable {

  /**
   * Return the default aggregation for the {@link InstrumentType}.
   *
   * @see DefaultAggregationSelector#getDefaultAggregation(InstrumentType)
   */
  @Override
  default Aggregation getDefaultAggregation(InstrumentType instrumentType) {
    return Aggregation.defaultAggregation();
  }

  /**
   * Exports the collection of given {@link MetricData}. Note that export operations can be
   * performed simultaneously depending on the type of metric reader being used. However, the caller
   * MUST ensure that only one export can occur at a time.
   *
   * @param metrics the collection of {@link MetricData} to be exported.
   * @return the result of the export, which is often an asynchronous operation.
   */
  CompletableResultCode export(Collection<MetricData> metrics);

  /**
   * Exports the collection of {@link MetricData} that have not yet been exported. Note that flush
   * operations can be performed simultaneously depending on the type of metric reader being used.
   * However, the caller MUST ensure that only one export can occur at a time.
   *
   * @return the result of the flush, which is often an asynchronous operation.
   */
  CompletableResultCode flush();

  /**
   * Called when the associated IntervalMetricReader is shutdown.
   *
   * @return a {@link CompletableResultCode} which is completed when shutdown completes.
   */
  CompletableResultCode shutdown();

  /** Closes this {@link MetricExporter}, releasing any resources. */
  @Override
  default void close() {
    shutdown().join(10, TimeUnit.SECONDS);
  }
}
