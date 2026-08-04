/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogramBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.internal.descriptor.Advice;
import io.opentelemetry.sdk.metrics.internal.descriptor.InstrumentDescriptor;
import io.opentelemetry.sdk.metrics.internal.state.BoundStorageHandle;
import io.opentelemetry.sdk.metrics.internal.state.WriteableMetricStorage;
import java.util.List;
import javax.annotation.Nullable;

final class ExtendedSdkLongHistogram extends SdkLongHistogram
    implements ExtendedLongHistogram, BoundLongHistogram {

  // Non-null only when this is a bound instance returned from bind(); null for the instrument
  // itself. When set, the record() methods record straight to this handle instead of resolving the
  // series from the storage on each call.
  @Nullable private final BoundStorageHandle boundHandle;
  // Per-binding original attributes, passed to the handle on every record so exemplar sampling
  // sees the caller's original attributes even when distinct bindings collapse to the same
  // underlying handle. Non-null iff boundHandle is non-null.
  @Nullable private final Attributes boundAttributes;
  private ExtendedSdkLongHistogram(
      InstrumentDescriptor descriptor, SdkMeter sdkMeter, WriteableMetricStorage storage) {
    this(descriptor, sdkMeter, storage, null, null);
  }

  private ExtendedSdkLongHistogram(
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
  public BoundLongHistogram bind(Attributes attributes) {
    return new ExtendedSdkLongHistogram(
        getDescriptor(), sdkMeter, storage, storage.bind(attributes), attributes);
  }

  @Override
  public void record(long value) {
    if (boundHandle != null && boundAttributes != null) {
      if (!validateNonNegative(value)) {
        return;
      }
      if (!storage.isEnabled()) {
        return;
      }
      Context context = exemplarsAlwaysOff ? Context.root() : Context.current();
      boundHandle.recordLong(value, boundAttributes, context);
    } else {
      super.record(value);
    }
  }

  @Override
  public void record(long value, Context context) {
    if (boundHandle != null && boundAttributes != null) {
      if (!validateNonNegative(value)) {
        return;
      }
      if (!storage.isEnabled()) {
        return;
      }
      boundHandle.recordLong(value, boundAttributes, context);
    } else {
      super.record(value, Attributes.empty(), context);
    }
  }

  static final class ExtendedSdkLongHistogramBuilder extends SdkLongHistogramBuilder
      implements ExtendedLongHistogramBuilder {

    ExtendedSdkLongHistogramBuilder(
        SdkMeter sdkMeter,
        String name,
        String description,
        String unit,
        Advice.AdviceBuilder adviceBuilder) {
      super(sdkMeter, name, description, unit, adviceBuilder);
    }

    @Override
    public ExtendedSdkLongHistogram build() {
      return builder.buildSynchronousInstrument(ExtendedSdkLongHistogram::new);
    }

    @Override
    public ExtendedLongHistogramBuilder setAttributesAdvice(List<AttributeKey<?>> attributes) {
      builder.setAdviceAttributes(attributes);
      return this;
    }
  }
}
