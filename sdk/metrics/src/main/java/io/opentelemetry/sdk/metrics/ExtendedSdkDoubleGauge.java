/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGaugeBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGaugeBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.state.BoundStorageHandle;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.List;
import javax.annotation.Nullable;

final class ExtendedSdkDoubleGauge extends SdkDoubleGauge
    implements ExtendedDoubleGauge, BoundDoubleGauge {

  @Nullable private final BoundStorageHandle boundHandle;
  @Nullable private final Attributes boundAttributes;
  private ExtendedSdkDoubleGauge(
      InstrumentDescriptor descriptor, SdkMeter sdkMeter, WriteableMetricStorage storage) {
    this(descriptor, sdkMeter, storage, null, null);
  }

  private ExtendedSdkDoubleGauge(
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
  public BoundDoubleGauge bind(Attributes attributes) {
    return new ExtendedSdkDoubleGauge(
        getDescriptor(), sdkMeter, storage, storage.bind(attributes), attributes);
  }

  @Override
  public void set(double value) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.shouldRecordDouble(value, boundAttributes)) {
        return;
      }
      Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
      boundHandle.recordDouble(value, boundAttributes, context);
    } else {
      super.set(value);
    }
  }

  @Override
  public void set(double value, Context context) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.shouldRecordDouble(value, boundAttributes)) {
        return;
      }
      boundHandle.recordDouble(value, boundAttributes, context);
    } else {
      super.set(value, Attributes.empty(), context);
    }
  }

  static final class ExtendedSdkDoubleGaugeBuilder extends SdkDoubleGaugeBuilder
      implements ExtendedDoubleGaugeBuilder {
    ExtendedSdkDoubleGaugeBuilder(SdkMeter sdkMeter, String name) {
      super(sdkMeter, name);
    }

    @Override
    public ExtendedSdkDoubleGauge build() {
      return builder.buildSynchronousInstrument(ExtendedSdkDoubleGauge::new);
    }

    @Override
    public ExtendedDoubleGaugeBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
      builder.setAdviceAttributes(attributes);
      return this;
    }

    @Override
    public ExtendedLongGaugeBuilder ofLongs() {
      return builder.swapBuilder(ExtendedSdkLongGauge.ExtendedSdkLongGaugeBuilder::new);
    }
  }
}
