/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogramBuilder;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.state.BoundStorageHandle;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.List;
import javax.annotation.Nullable;

final class ExtendedSdkDoubleHistogram extends SdkDoubleHistogram
    implements ExtendedDoubleHistogram, BoundDoubleHistogram {

  @Nullable private final BoundStorageHandle boundHandle;
  @Nullable private final Attributes boundAttributes;
  ExtendedSdkDoubleHistogram(
      InstrumentDescriptor descriptor, SdkMeter sdkMeter, WriteableMetricStorage storage) {
    this(descriptor, sdkMeter, storage, null, null);
  }

  private ExtendedSdkDoubleHistogram(
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
  public BoundDoubleHistogram bind(Attributes attributes) {
    return new ExtendedSdkDoubleHistogram(
        getDescriptor(), sdkMeter, storage, storage.bind(attributes), attributes);
  }

  @Override
  public void record(double value) {
    if (boundHandle != null && boundAttributes != null) {
      if (!validateNonNegative(value)) {
        return;
      }
      if (!storage.shouldRecordDouble(value, boundAttributes)) {
        return;
      }
      Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
      boundHandle.recordDouble(value, boundAttributes, context);
    } else {
      super.record(value);
    }
  }

  @Override
  public void record(double value, Context context) {
    if (boundHandle != null && boundAttributes != null) {
      if (!validateNonNegative(value)) {
        return;
      }
      if (!storage.shouldRecordDouble(value, boundAttributes)) {
        return;
      }
      boundHandle.recordDouble(value, boundAttributes, context);
    } else {
      super.record(value, Attributes.empty(), context);
    }
  }

  static final class ExtendedSdkDoubleHistogramBuilder extends SdkDoubleHistogramBuilder
      implements ExtendedDoubleHistogramBuilder {

    ExtendedSdkDoubleHistogramBuilder(SdkMeter sdkMeter, String name) {
      super(sdkMeter, name);
    }

    @Override
    public ExtendedSdkDoubleHistogram build() {
      return builder.buildSynchronousInstrument(ExtendedSdkDoubleHistogram::new);
    }

    @Override
    public ExtendedLongHistogramBuilder ofLongs() {
      return builder.swapBuilder(ExtendedSdkLongHistogram.ExtendedSdkLongHistogramBuilder::new);
    }

    @Override
    public ExtendedDoubleHistogramBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
      builder.setAdviceAttributes(attributes);
      return this;
    }
  }
}
