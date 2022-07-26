/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.data.Severity;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
final class DefaultLogEmitter implements LogEmitter {

  private static final LogEmitter INSTANCE = new DefaultLogEmitter();
  private static final LogRecordBuilder NOOP_LOG_RECORD_BUILDER = new NoopLogRecordBuilder();

  static LogEmitter getInstance() {
    return INSTANCE;
  }

  private DefaultLogEmitter() {}

  @Override
  public LogRecordBuilder logRecordBuilder() {
    return NOOP_LOG_RECORD_BUILDER;
  }

  private static final class NoopLogRecordBuilder implements LogRecordBuilder {

    private static final LogRecord NOOP_LOG_RECORD = new NoopLogRecord();

    private NoopLogRecordBuilder() {}

    @Override
    public LogRecordBuilder setEpoch(long timestamp, TimeUnit unit) {
      return this;
    }

    @Override
    public LogRecordBuilder setEpoch(Instant instant) {
      return this;
    }

    @Override
    public LogRecordBuilder setContext(Context context) {
      return this;
    }

    @Override
    public LogRecordBuilder setSeverity(Severity severity) {
      return this;
    }

    @Override
    public LogRecordBuilder setSeverityText(String severityText) {
      return this;
    }

    @Override
    public LogRecordBuilder setBody(String body) {
      return this;
    }

    @Override
    public LogRecordBuilder setAttributes(Attributes attributes) {
      return this;
    }

    @Override
    public LogRecord emit() {
      return NOOP_LOG_RECORD;
    }
  }

  private static final class NoopLogRecord implements LogRecord {

    private NoopLogRecord() {}

    @Override
    public <T> LogRecord setAttribute(AttributeKey<T> key, T value) {
      return this;
    }
  }
}
