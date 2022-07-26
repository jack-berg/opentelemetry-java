/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.internal.AttributeUtil;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.Severity;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/** SDK implementation of {@link LogRecordBuilder}. */
final class SdkLogRecordBuilder implements LogRecordBuilder {

  private final InstrumentationScopeInfo instrumentationScopeInfo;
  private final LogEmitterSharedState logEmitterSharedState;
  private final LogLimits logLimits;

  private long epochNanos;
  private SpanContext spanContext = SpanContext.getInvalid();
  private Severity severity = Severity.UNDEFINED_SEVERITY_NUMBER;
  @Nullable private String severityText;
  private Body body = Body.empty();
  private Attributes attributes = Attributes.empty();

  SdkLogRecordBuilder(
      LogEmitterSharedState logEmitterSharedState,
      InstrumentationScopeInfo instrumentationScopeInfo) {
    this.instrumentationScopeInfo = instrumentationScopeInfo;
    this.logEmitterSharedState = logEmitterSharedState;
    this.logLimits = logEmitterSharedState.getLogLimits();
  }

  @Override
  public LogRecordBuilder setEpoch(long timestamp, TimeUnit unit) {
    this.epochNanos = unit.toNanos(timestamp);
    return this;
  }

  @Override
  public LogRecordBuilder setEpoch(Instant instant) {
    this.epochNanos = TimeUnit.SECONDS.toNanos(instant.getEpochSecond()) + instant.getNano();
    return this;
  }

  @Override
  public LogRecordBuilder setContext(Context context) {
    this.spanContext = Span.fromContext(context).getSpanContext();
    return this;
  }

  @Override
  public LogRecordBuilder setSeverity(Severity severity) {
    this.severity = severity;
    return this;
  }

  @Override
  public LogRecordBuilder setSeverityText(String severityText) {
    this.severityText = severityText;
    return this;
  }

  @Override
  public LogRecordBuilder setBody(String body) {
    this.body = Body.string(body);
    return this;
  }

  @Override
  public LogRecordBuilder setAttributes(Attributes attributes) {
    this.attributes =
        AttributeUtil.applyAttributesLimit(
            attributes,
            logLimits.getMaxNumberOfAttributes(),
            logLimits.getMaxAttributeValueLength());
    return this;
  }

  @Override
  public LogRecord emit() {
    long epochNanos =
        this.epochNanos == 0 ? this.logEmitterSharedState.getClock().now() : this.epochNanos;
    return SdkLogRecord.emitLogRecord(
        logEmitterSharedState.getLogProcessor(),
        logEmitterSharedState.getResource(),
        instrumentationScopeInfo,
        logLimits,
        epochNanos,
        spanContext,
        severity,
        severityText,
        body,
        attributes);
  }
}
