/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

public final class Loopback {

  /**
   * When this bit is 1, indicates that a log record's source was the SDK (i.e. `Slf4jBridge`).
   * OpenTelemetry log appenders should skip processing these logs.
   */
  private static final int LOOPBACK_OTEL_SDK = 0b1;

  /**
   * When this bit is 1, indicates that a log record's source was an OpenTelemetry log appender
   * (i.e. io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender).
   * OpenTelemetry SDK sources (i.e. `Slf4jBridge`) should skip processing these logs.
   */
  private static final int LOOPBACK_OTEL_APPENDER = 0b10;

  private Loopback() {}

  public static final ContextKey<Long> loopbackContextKey = ContextKey.named("otel.loopback");

  public static long withLoopbackOtelSdk() {
    return LOOPBACK_OTEL_SDK;
  }

  public static long withLoopbackOtelAppender() {
    return LOOPBACK_OTEL_APPENDER;
  }

  public static boolean isLoopbackOtelSdk(@Nullable Long loopback) {
    return loopback != null && (loopback & LOOPBACK_OTEL_SDK) != 0;
  }

  public static boolean isLoopbackOtelAppender(@Nullable Long loopback) {
    return loopback != null && (loopback & LOOPBACK_OTEL_APPENDER) != 0;
  }
}
