/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.logs.data.Severity;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Used to construct and emit logs from a {@link LogEmitter}.
 *
 * <p>Obtain a {@link LogEmitter#logRecordBuilder()}, add properties using the setters, and emit the
 * log to downstream {@link LogProcessor}(s) by calling {@link #emit()}.
 */
public interface LogRecordBuilder {

  /** Set the epoch timestamp using the timestamp and unit. */
  LogRecordBuilder setEpoch(long timestamp, TimeUnit unit);

  /** Set the epoch timestamp using the instant. */
  LogRecordBuilder setEpoch(Instant instant);

  /** Set the context. */
  LogRecordBuilder setContext(Context context);

  /** Set the severity. */
  LogRecordBuilder setSeverity(Severity severity);

  /** Set the severity text. */
  LogRecordBuilder setSeverityText(String severityText);

  /** Set the body string. */
  LogRecordBuilder setBody(String body);

  /** Set the attributes. */
  LogRecordBuilder setAttributes(Attributes attributes);

  /** Emit the log to downstream {@link LogProcessor}(s). */
  LogRecord emit();
}
