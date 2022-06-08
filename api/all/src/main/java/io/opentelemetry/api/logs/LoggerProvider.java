/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

public interface LoggerProvider {

  default Logger get(String instrumentationScopeName) {
    return loggerBuilder(instrumentationScopeName).build();
  }

  LoggerBuilder loggerBuilder(String instrumentationScopeName);

  static LoggerProvider noop() {
    return DefaultLoggerProvider.getInstance();
  }
}
