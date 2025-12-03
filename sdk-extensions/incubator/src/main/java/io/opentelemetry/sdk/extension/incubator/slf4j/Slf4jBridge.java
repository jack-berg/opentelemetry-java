/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.slf4j;

import io.opentelemetry.api.common.Value;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.Loopback;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

public final class Slf4jBridge implements LogRecordProcessor {
  private Slf4jBridge() {}

  public static Slf4jBridge create() {
    return new Slf4jBridge();
  }

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    // Ignore scope version, schemaUrl, attributes.
    // Ignore a variety of fields which exist for briding purposes. Here we're trying to bridge logs
    // recorded directly in the OpenTelemetry log API to SLF4J:
    // - logRecord.getSeverityText()
    // - logRecord.getTimestampEpochNanos()
    // - logRecord.getObservedTimestampEpochNanos()

    InstrumentationScopeInfo scopeInfo = logRecord.getInstrumentationScopeInfo();

    Logger logger = LoggerFactory.getLogger(scopeInfo.getName());

    Level level = toSlf4jLevel(logRecord.getSeverity());

    if (!logger.isEnabledForLevel(level)) {
      return;
    }

    LoggingEventBuilder builder = logger.atLevel(level);

    Value<?> bodyValue = logRecord.getBodyValue();
    if (bodyValue != null) {
      builder.setMessage(logRecord.getBodyValue().asString());
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

    // Set loopbackk context and log
    MDC.put(Loopback.loopbackAttribute.getKey(), Boolean.toString(true));
    try (Scope scope = Loopback.withLoopback(context).makeCurrent()) {
      builder.log();
    }
  }

  private static Level toSlf4jLevel(Severity severity) {
    switch (severity) {
      case TRACE:
        return Level.TRACE;
      case TRACE2:
        return Level.TRACE;
      case TRACE3:
        return Level.TRACE;
      case TRACE4:
        return Level.TRACE;
      case DEBUG:
        return Level.DEBUG;
      case DEBUG2:
        return Level.DEBUG;
      case DEBUG3:
        return Level.DEBUG;
      case DEBUG4:
        return Level.DEBUG;
      case INFO:
        return Level.INFO;
      case INFO2:
        return Level.INFO;
      case INFO3:
        return Level.INFO;
      case INFO4:
        return Level.INFO;
      case WARN:
        return Level.WARN;
      case WARN2:
        return Level.WARN;
      case WARN3:
        return Level.WARN;
      case WARN4:
        return Level.WARN;
      case ERROR:
        return Level.ERROR;
      case ERROR2:
        return Level.ERROR;
      case ERROR3:
        return Level.ERROR;
      case ERROR4:
        return Level.ERROR;
      case FATAL:
        return Level.ERROR;
      case FATAL2:
        return Level.ERROR;
      case FATAL3:
        return Level.ERROR;
      case FATAL4:
        return Level.ERROR;
      // TODO: Review
      case UNDEFINED_SEVERITY_NUMBER:
      default:
        return Level.INFO;
    }
  }
}
