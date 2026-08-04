/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.internal.descriptor.Advice;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.state.BoundStorageHandle;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.List;
import javax.annotation.Nullable;

final class ExtendedSdkLongGauge extends SdkLongGauge implements ExtendedLongGauge, BoundLongGauge {

  @Nullable private final BoundStorageHandle boundHandle;
  @Nullable private final Attributes boundAttributes;
  private ExtendedSdkLongGauge(
      InstrumentDescriptor descriptor, SdkMeter sdkMeter, WriteableMetricStorage storage) {
    this(descriptor, sdkMeter, storage, null, null);
  }

  private ExtendedSdkLongGauge(
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
  public BoundLongGauge bind(Attributes attributes) {
    return new ExtendedSdkLongGauge(
        getDescriptor(), sdkMeter, storage, storage.bind(attributes), attributes);
  }

  @Override
  public void set(long value) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.isEnabled()) {
        return;
      }
      Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
      boundHandle.recordLong(value, boundAttributes, context);
    } else {
      super.set(value);
    }
  }

  @Override
  public void set(long value, Context context) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.isEnabled()) {
        return;
      }
      boundHandle.recordLong(value, boundAttributes, context);
    } else {
      super.set(value, Attributes.empty(), context);
    }
  }

  static final class ExtendedSdkLongGaugeBuilder extends SdkLongGaugeBuilder
      implements ExtendedLongGaugeBuilder {

    ExtendedSdkLongGaugeBuilder(
        SdkMeter sdkMeter,
        String name,
        String description,
        String unit,
        Advice.AdviceBuilder adviceBuilder) {
      super(sdkMeter, name, description, unit, adviceBuilder);
    }

    @Override
    public ExtendedSdkLongGauge build() {
      return builder.buildSynchronousInstrument(ExtendedSdkLongGauge::new);
    }

    @Override
    public ExtendedLongGaugeBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
      builder.setAdviceAttributes(attributes);
      return this;
    }
  }
}
