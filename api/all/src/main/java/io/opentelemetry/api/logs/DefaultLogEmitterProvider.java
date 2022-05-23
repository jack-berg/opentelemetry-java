/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

import io.opentelemetry.api.common.Attributes;

class DefaultLogEmitterProvider implements LogEmitterProvider {

  private static final LogEmitterProvider INSTANCE = new DefaultLogEmitterProvider();
  private static final LogEmitterBuilder NOOP_BUILDER = new NoopLogEmitterBuilder();

  static LogEmitterProvider getInstance() {
    return INSTANCE;
  }

  @Override
  public LogEmitterBuilder loggerBuilder(String instrumentationScopeName) {
    return NOOP_BUILDER;
  }

  private static class NoopLogEmitterBuilder implements LogEmitterBuilder {

    @Override
    public LogEmitterBuilder setSchemaUrl(String schemaUrl) {
      return this;
    }

    @Override
    public LogEmitterBuilder setInstrumentationVersion(String instrumentationVersion) {
      return this;
    }

    @Override
    public LogEmitterBuilder setAttributes(Attributes attributes) {
      return this;
    }

    @Override
    public LogEmitter build() {
      return DefaultLogEmitter.getInstance();
    }
  }
}
