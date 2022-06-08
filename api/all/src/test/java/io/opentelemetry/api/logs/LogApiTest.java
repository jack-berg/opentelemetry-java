/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class LogApiTest {

  @Test
  void demo() {
    OpenTelemetry openTelemetry = OpenTelemetry.noop();

    // Obtain a logger
    Logger logger = openTelemetry.getLogger("my-logger-scope");

    // Obtain a logger with scope attributes indicating the event domain
    // Event names are expected to be unique within a domain
    logger =
        openTelemetry
            .loggerBuilder("my-logger-scope")
            .setAttributes(Attributes.builder().put("event.domain", "otel.jfr").build())
            .build();

    // Emit a simple event named my-event
    logger.eventBuilder("my-event").emit();

    // Emit an event with attributes
    logger
        .eventBuilder("jdk.execution_sample")
        .setAttributes(
            Attributes.builder()
                .put("thread.name", "sampled-thread-1")
                .put("thread.state", Thread.currentThread().getState().toString())
                .put(AttributeKey.stringArrayKey("stack.trace"), stackTrace())
                .build())
        .emit();

    // Emit a low level log record
    // NOTE: the API for emitting log records is only intended to be used by log appender adapters
    // to adapt logs from existing log frameworks (Log4j, Logback) to OpenTelemetry
    logger
        .logRecordBuilder()
        .setSeverity(Severity.DEBUG)
        .setBody("My application log message")
        .emit();
  }

  private static List<String> stackTrace() {
    return Stream.of(Thread.currentThread().getStackTrace())
        .map(
            stackTraceElement ->
                stackTraceElement.getClassName()
                    + "."
                    + stackTraceElement.getMethodName()
                    + " "
                    + stackTraceElement.getLineNumber())
        .collect(Collectors.toList());
  }
}
