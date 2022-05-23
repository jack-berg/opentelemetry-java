/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

public interface LogEmitterProvider {

  default LogEmitter get(String instrumentationScopeName) {
    return loggerBuilder(instrumentationScopeName).build();
  }

  LogEmitterBuilder loggerBuilder(String instrumentationScopeName);

  static LogEmitterProvider noop() {
    return DefaultLogEmitterProvider.getInstance();
  }
}
