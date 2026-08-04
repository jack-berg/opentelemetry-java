/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.DoubleHistogramBuilder;
import io.opentelemetry.api.metrics.LongHistogramBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.internal.ThrottlingLogger;
import io.opentelemetry.sdk.metrics.internal.aggregator.ExplicitBucketHistogramUtils;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.AlwaysOffExemplarFilter;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

class SdkDoubleHistogram extends AbstractInstrument implements DoubleHistogram {
  private static final Logger logger = Logger.getLogger(SdkDoubleHistogram.class.getName());

  private final ThrottlingLogger throttlingLogger = new ThrottlingLogger(logger);
  final SdkMeter sdkMeter;
  final WriteableMetricStorage storage;
  final boolean exemplarsAlwaysOff;

  SdkDoubleHistogram(
      InstrumentDescriptor descriptor, SdkMeter sdkMeter, WriteableMetricStorage storage) {
    super(descriptor);
    this.sdkMeter = sdkMeter;
    this.storage = storage;
    this.exemplarsAlwaysOff =
        sdkMeter.getMeterProviderSharedState().getExemplarFilter() instanceof AlwaysOffExemplarFilter;
  }

  @Override
  public boolean isEnabled() {
    return sdkMeter.isMeterEnabled() && storage.isEnabled();
  }

  @Override
  public void record(double value, Attributes attributes, Context context) {
    if (!validateNonNegative(value)) {
      return;
    }
    if (!storage.shouldRecordDouble(value, attributes)) {
      return;
    }
    storage.recordDouble(value, attributes, context);
  }

  @Override
  public void record(double value, Attributes attributes) {
    if (!validateNonNegative(value)) {
      return;
    }
    if (!storage.shouldRecordDouble(value, attributes)) {
      return;
    }
    Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
    storage.recordDouble(value, attributes, context);
  }

  @Override
  public void record(double value) {
    if (!validateNonNegative(value)) {
      return;
    }
    if (!storage.shouldRecordDouble(value, Attributes.empty())) {
      return;
    }
    Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
    storage.recordDouble(value, Attributes.empty(), context);
  }

  /**
   * Returns true if {@code value} is non-negative, otherwise logs a warning and returns false.
   * Shared by the unbound and bound ({@link ExtendedSdkDoubleHistogram}) record paths.
   */
  boolean validateNonNegative(double value) {
    if (value < 0) {
      throttlingLogger.log(
          Level.WARNING,
          "Histograms can only record non-negative values. Instrument "
              + getDescriptor().getName()
              + " has recorded a negative value.");
      return false;
    }
    return true;
  }

  static class SdkDoubleHistogramBuilder implements DoubleHistogramBuilder {

    final InstrumentBuilder builder;

    SdkDoubleHistogramBuilder(SdkMeter sdkMeter, String name) {
      builder =
          new InstrumentBuilder(
              name, InstrumentType.HISTOGRAM, InstrumentValueType.DOUBLE, sdkMeter);
    }

    @Override
    public DoubleHistogramBuilder setDescription(String description) {
      builder.setDescription(description);
      return this;
    }

    @Override
    public DoubleHistogramBuilder setUnit(String unit) {
      builder.setUnit(unit);
      return this;
    }

    @Override
    public SdkDoubleHistogram build() {
      return builder.buildSynchronousInstrument(SdkDoubleHistogram::new);
    }

    @Override
    public LongHistogramBuilder ofLongs() {
      return builder.swapBuilder(SdkLongHistogram.SdkLongHistogramBuilder::new);
    }

    @Override
    public DoubleHistogramBuilder setExplicitBucketBoundariesAdvice(List<Double> bucketBoundaries) {
      try {
        Objects.requireNonNull(bucketBoundaries, "bucketBoundaries must not be null");
        ExplicitBucketHistogramUtils.validateBucketBoundaries(bucketBoundaries);
      } catch (IllegalArgumentException | NullPointerException e) {
        logger.log(Level.WARNING, "Error setting explicit bucket boundaries advice", e);
        return this;
      }
      builder.setExplicitBucketBoundaries(bucketBoundaries);
      return this;
    }

    @Override
    public String toString() {
      return builder.toStringHelper(getClass().getSimpleName());
    }
  }
}
