/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleUpDownCounter;
import io.opentelemetry.api.metrics.DoubleUpDownCounterBuilder;
import io.opentelemetry.api.metrics.ObservableDoubleMeasurement;
import io.opentelemetry.api.metrics.ObservableDoubleUpDownCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.internal.descriptor.Advice;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.exemplar.AlwaysOffExemplarFilter;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.function.Consumer;

class SdkDoubleUpDownCounter extends AbstractInstrument implements DoubleUpDownCounter {

  final SdkMeter sdkMeter;
  final WriteableMetricStorage storage;
  final boolean exemplarsAlwaysOff;

  SdkDoubleUpDownCounter(
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
  public void add(double increment, Attributes attributes, Context context) {
    if (!storage.shouldRecordDouble(increment, attributes)) {
      return;
    }
    storage.recordDouble(increment, attributes, context);
  }

  @Override
  public void add(double increment, Attributes attributes) {
    if (!storage.shouldRecordDouble(increment, attributes)) {
      return;
    }
    Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
    storage.recordDouble(increment, attributes, context);
  }

  @Override
  public void add(double increment) {
    if (!storage.shouldRecordDouble(increment, Attributes.empty())) {
      return;
    }
    Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
    storage.recordDouble(increment, Attributes.empty(), context);
  }

  static class SdkDoubleUpDownCounterBuilder implements DoubleUpDownCounterBuilder {

    final InstrumentBuilder builder;

    SdkDoubleUpDownCounterBuilder(
        SdkMeter sdkMeter,
        String name,
        String description,
        String unit,
        Advice.AdviceBuilder adviceBuilder) {
      this.builder =
          new InstrumentBuilder(
                  name, InstrumentType.UP_DOWN_COUNTER, InstrumentValueType.DOUBLE, sdkMeter)
              .setDescription(description)
              .setUnit(unit)
              .setAdviceBuilder(adviceBuilder);
    }

    @Override
    public DoubleUpDownCounterBuilder setDescription(String description) {
      builder.setDescription(description);
      return this;
    }

    @Override
    public DoubleUpDownCounterBuilder setUnit(String unit) {
      builder.setUnit(unit);
      return this;
    }

    @Override
    public DoubleUpDownCounter build() {
      return builder.buildSynchronousInstrument(SdkDoubleUpDownCounter::new);
    }

    @Override
    public ObservableDoubleUpDownCounter buildWithCallback(
        Consumer<ObservableDoubleMeasurement> callback) {
      return builder.buildDoubleAsynchronousInstrument(
          InstrumentType.OBSERVABLE_UP_DOWN_COUNTER, callback);
    }

    @Override
    public ObservableDoubleMeasurement buildObserver() {
      return builder.buildObservableMeasurement(InstrumentType.OBSERVABLE_UP_DOWN_COUNTER);
    }

    @Override
    public String toString() {
      return builder.toStringHelper(getClass().getSimpleName());
    }
  }
}
