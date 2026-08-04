/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.internal.descriptor.Advice;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.state.BoundStorageHandle;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.List;
import javax.annotation.Nullable;

final class ExtendedSdkDoubleUpDownCounter extends SdkDoubleUpDownCounter
    implements ExtendedDoubleUpDownCounter, BoundDoubleUpDownCounter {

  @Nullable private final BoundStorageHandle boundHandle;
  @Nullable private final Attributes boundAttributes;
  private ExtendedSdkDoubleUpDownCounter(
      InstrumentDescriptor descriptor, SdkMeter sdkMeter, WriteableMetricStorage storage) {
    this(descriptor, sdkMeter, storage, null, null);
  }

  private ExtendedSdkDoubleUpDownCounter(
      InstrumentDescriptor descriptor,
      SdkMeter sdkMeter,
      WriteableMetricStorage storage,
      @Nullable BoundStorageHandle boundHandle,
      @Nullable Attributes boundAttributes) {
    super(descriptor, sdkMeter, storage);
    this.boundHandle = boundHandle;
    this.boundAttributes = boundAttributes;
  }

  @Override
  public BoundDoubleUpDownCounter bind(Attributes attributes) {
    return new ExtendedSdkDoubleUpDownCounter(
        getDescriptor(), sdkMeter, storage, storage.bind(attributes), attributes);
  }

  @Override
  public void add(double value) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.shouldRecordDouble(value, boundAttributes)) {
        return;
      }
      Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
      boundHandle.recordDouble(value, boundAttributes, context);
    } else {
      super.add(value);
    }
  }

  @Override
  public void add(double value, Context context) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.shouldRecordDouble(value, boundAttributes)) {
        return;
      }
      boundHandle.recordDouble(value, boundAttributes, context);
    } else {
      super.add(value, Attributes.empty(), context);
    }
  }

  static final class ExtendedSdkDoubleUpDownCounterBuilder extends SdkDoubleUpDownCounterBuilder
      implements ExtendedDoubleUpDownCounterBuilder {

    ExtendedSdkDoubleUpDownCounterBuilder(
        SdkMeter sdkMeter,
        String name,
        String description,
        String unit,
        Advice.AdviceBuilder adviceBuilder) {
      super(sdkMeter, name, description, unit, adviceBuilder);
    }

    @Override
    public ExtendedDoubleUpDownCounter build() {
      return builder.buildSynchronousInstrument(ExtendedSdkDoubleUpDownCounter::new);
    }

    @Override
    public ExtendedDoubleUpDownCounterBuilder setAttributesAdvice(
        List<AttributeKey<?>> attributes) {
      builder.setAdviceAttributes(attributes);
      return this;
    }
  }
}
