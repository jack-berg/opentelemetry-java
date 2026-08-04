/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounterBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounterBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.state.BoundStorageHandle;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.List;
import javax.annotation.Nullable;

final class ExtendedSdkLongUpDownCounter extends SdkLongUpDownCounter
    implements ExtendedLongUpDownCounter, BoundLongUpDownCounter {

  @Nullable private final BoundStorageHandle boundHandle;
  @Nullable private final Attributes boundAttributes;
  private ExtendedSdkLongUpDownCounter(
      InstrumentDescriptor descriptor, SdkMeter sdkMeter, WriteableMetricStorage storage) {
    this(descriptor, sdkMeter, storage, null, null);
  }

  private ExtendedSdkLongUpDownCounter(
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
  public BoundLongUpDownCounter bind(Attributes attributes) {
    return new ExtendedSdkLongUpDownCounter(
        getDescriptor(), sdkMeter, storage, storage.bind(attributes), attributes);
  }

  @Override
  public void add(long value) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.isEnabled()) {
        return;
      }
      Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
      boundHandle.recordLong(value, boundAttributes, context);
    } else {
      super.add(value);
    }
  }

  @Override
  public void add(long value, Context context) {
    if (boundHandle != null && boundAttributes != null) {
      if (!storage.isEnabled()) {
        return;
      }
      boundHandle.recordLong(value, boundAttributes, context);
    } else {
      super.add(value, Attributes.empty(), context);
    }
  }

  static final class ExtendedSdkLongUpDownCounterBuilder extends SdkLongUpDownCounterBuilder
      implements ExtendedLongUpDownCounterBuilder {

    ExtendedSdkLongUpDownCounterBuilder(SdkMeter sdkMeter, String name) {
      super(sdkMeter, name);
    }

    @Override
    public ExtendedLongUpDownCounter build() {
      return builder.buildSynchronousInstrument(ExtendedSdkLongUpDownCounter::new);
    }

    @Override
    public ExtendedDoubleUpDownCounterBuilder ofDoubles() {
      return builder.swapBuilder(
          ExtendedSdkDoubleUpDownCounter.ExtendedSdkDoubleUpDownCounterBuilder::new);
    }

    @Override
    public ExtendedLongUpDownCounterBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
      builder.setAdviceAttributes(attributes);
      return this;
    }
  }
}
