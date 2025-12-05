/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.slf4j;

import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Loopback;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

public final class Slf4jBridge implements LogRecordProcessor {
  private Slf4jBridge() {}

  public static Slf4jBridge create() {
    return new Slf4jBridge();
  }

  @SuppressWarnings("CheckReturnValue")
  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    if (Loopback.isLoopbackOtelAppender(context.get(Loopback.loopbackContextKey))) {
      return;
    }

    InstrumentationScopeInfo scopeInfo = logRecord.getInstrumentationScopeInfo();
    Logger logger = LoggerFactory.getLogger(scopeInfo.getName());
    Level level = toSlf4jLevel(logRecord.getSeverity());
    if (!logger.isEnabledForLevel(level)) {
      return;
    }

    LoggingEventBuilder builder = logger.atLevel(level);

    Value<?> bodyValue = logRecord.getBodyValue();
    if (bodyValue != null) {
      builder.setMessage(bodyValue.asString());
    }

    logRecord
        .getAttributes()
        .forEach(
            (key, value) -> {
              builder.addKeyValue(key.getKey(), value);
            });

    // append event_name last to take priority over attributes
    String eventName = logRecord.getEventName();
    if (eventName != null) {
      builder.addKeyValue("event_name", eventName);
    }

    try (Scope scope =
        context.with(Loopback.loopbackContextKey, Loopback.withLoopbackOtelSdk()).makeCurrent()) {
      builder.log();
    }
  }

  private static Level toSlf4jLevel(Severity severity) {
    switch (severity) {
      case TRACE:
      case TRACE2:
      case TRACE3:
      case TRACE4:
        return Level.TRACE;
      case DEBUG:
      case DEBUG2:
      case DEBUG3:
      case DEBUG4:
        return Level.DEBUG;
      case INFO:
      case INFO2:
      case INFO3:
      case INFO4:
        return Level.INFO;
      case WARN:
      case WARN2:
      case WARN3:
      case WARN4:
        return Level.WARN;
      case ERROR:
      case ERROR2:
      case ERROR3:
      case ERROR4:
      case FATAL:
      case FATAL2:
      case FATAL3:
      case FATAL4:
        return Level.ERROR;
      case UNDEFINED_SEVERITY_NUMBER:
        return Level.INFO;
    }
    throw new IllegalArgumentException("Unknown severity: " + severity);
  }
}
