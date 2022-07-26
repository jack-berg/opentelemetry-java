/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.internal.GuardedBy;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.resources.Resource;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
class SdkLogRecord implements ReadWriteLogRecord {

  private final Resource resource;
  private final InstrumentationScopeInfo instrumentationScopeInfo;
  private final LogLimits logLimits;
  private final long epochNanos;
  private final SpanContext spanContext;
  private final Severity severity;
  @Nullable private final String severityText;
  private final Body body;
  private final Object lock = new Object();

  @GuardedBy("lock")
  private Attributes attributes;

  private SdkLogRecord(
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      LogLimits logLimits,
      long epochNanos,
      SpanContext spanContext,
      Severity severity,
      @Nullable String severityText,
      Body body,
      Attributes attributes) {
    this.resource = resource;
    this.instrumentationScopeInfo = instrumentationScopeInfo;
    this.logLimits = logLimits;
    this.epochNanos = epochNanos;
    this.spanContext = spanContext;
    this.severity = severity;
    this.severityText = severityText;
    this.body = body;
    this.attributes = attributes;
  }

  /** Create the log record with the given configuration and emit it to the {@code logProcessor}. */
  static SdkLogRecord emitLogRecord(
      LogProcessor logProcessor,
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      LogLimits logLimits,
      long epochNanos,
      SpanContext spanContext,
      Severity severity,
      @Nullable String severityText,
      Body body,
      Attributes attributes) {
    SdkLogRecord logRecord =
        new SdkLogRecord(
            resource,
            instrumentationScopeInfo,
            logLimits,
            epochNanos,
            spanContext,
            severity,
            severityText,
            body,
            attributes);
    logProcessor.onEmit(logRecord);
    return logRecord;
  }

  @Override
  public <T> LogRecord setAttribute(AttributeKey<T> key, T value) {
    synchronized (lock) {
      // TODO: apply LogLimits
      this.attributes = attributes.toBuilder().put(key, value).build();
    }
    return this;
  }

  @Override
  public LogData toLogData() {
    synchronized (lock) {
      return SdkLogData.create(
          resource,
          instrumentationScopeInfo,
          epochNanos,
          spanContext,
          severity,
          severityText,
          body,
          attributes);
    }
  }
}
