/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.CUMULATIVE;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.internal.ThrottlingLogger;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.internal.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.internal.descriptor.MetricDescriptor;
import io.opentelemetry.sdk.metrics.internal.export.RegisteredReader;
import io.opentelemetry.sdk.metrics.internal.view.AttributesProcessor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores aggregated {@link MetricData} for synchronous instruments.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public abstract class DefaultSynchronousMetricStorage<T extends PointData>
    implements SynchronousMetricStorage {

  private static final Logger internalLogger =
      Logger.getLogger(DefaultSynchronousMetricStorage.class.getName());

  final ThrottlingLogger logger = new ThrottlingLogger(internalLogger);
  final AttributesProcessor attributesProcessor;
  protected final Clock clock;
  protected final MetricDescriptor metricDescriptor;
  protected final Aggregator<T> aggregator;

  /**
   * This field is set to 1 less than the actual intended cardinality limit, allowing the last slot
   * to be filled by the {@link MetricStorage#CARDINALITY_OVERFLOW} series.
   */
  protected final int maxCardinality;

  protected volatile boolean enabled;

  DefaultSynchronousMetricStorage(
      MetricDescriptor metricDescriptor,
      Aggregator<T> aggregator,
      AttributesProcessor attributesProcessor,
      Clock clock,
      int maxCardinality,
      boolean enabled) {
    this.metricDescriptor = metricDescriptor;
    this.aggregator = aggregator;
    this.attributesProcessor = attributesProcessor;
    this.clock = clock;
    this.maxCardinality = maxCardinality - 1;
    this.enabled = enabled;
  }

  static <T extends PointData> DefaultSynchronousMetricStorage<T> create(
      RegisteredReader reader,
      MetricDescriptor descriptor,
      Aggregator<T> aggregator,
      AttributesProcessor processor,
      int maxCardinality,
      Clock clock,
      boolean enabled) {
    AggregationTemporality aggregationTemporality =
        reader.getReader().getAggregationTemporality(descriptor.getSourceInstrument().getType());
    return aggregationTemporality == CUMULATIVE
        ? new CumulativeSynchronousMetricStorage<>(
            descriptor,
            aggregator,
            processor,
            clock,
            maxCardinality,
            enabled,
            reader.getReader().getMemoryMode())
        : new DeltaSynchronousMetricStorage<>(
            reader, descriptor, aggregator, processor, clock, maxCardinality, enabled);
  }

  /**
   * Records a long measurement. Caller is expected to gate on {@link #isEnabled()} before
   * invoking; this method skips the check as a fast-path optimization for the direct SDK caller
   * path. Aggregators reached via {@link SdkMeter}'s multi-storage iteration path re-check
   * per-storage.
   */
  @Override
  public abstract void recordLong(long value, Attributes attributes, Context context);

  /**
   * Records a double measurement. Caller is expected to gate on {@link #shouldRecordDouble} before
   * invoking; this method skips the check.
   */
  @Override
  public abstract void recordDouble(double value, Attributes attributes, Context context);

  /**
   * Returns true if a double {@code value} should be recorded. Returns false (dropping the
   * measurement) when recording is disabled, or when {@code value} is NaN, logging in the latter
   * case. Shared by the unbound and bound record paths.
   */
  @Override
  public final boolean shouldRecordDouble(double value, Attributes attributes) {
    if (!enabled) {
      return false;
    }
    if (Double.isNaN(value)) {
      logger.log(
          Level.FINE,
          "Instrument "
              + metricDescriptor.getSourceInstrument().getName()
              + " has recorded measurement Not-a-Number (NaN) value with attributes "
              + attributes
              + ". Dropping measurement.");
      return false;
    }
    return true;
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public MetricDescriptor getMetricDescriptor() {
    return metricDescriptor;
  }
}
