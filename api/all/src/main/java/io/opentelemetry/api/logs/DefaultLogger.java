/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

class DefaultLogger implements Logger {

  private static final Logger INSTANCE = new DefaultLogger();

  private static final EventBuilder NOOP_EVENT_BUILDER = new NoopEventBuilder();

  static Logger getInstance() {
    return INSTANCE;
  }

  @Override
  public EventBuilder eventBuilder(String name) {
    return NOOP_EVENT_BUILDER;
  }

  @Override
  public LogRecordBuilder logRecordBuilder() {
    return NOOP_EVENT_BUILDER;
  }

  private static final class NoopEventBuilder implements EventBuilder {

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
    public void emit() {}
  }
}
