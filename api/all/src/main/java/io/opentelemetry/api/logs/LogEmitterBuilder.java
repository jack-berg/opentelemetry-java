/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

import io.opentelemetry.api.common.Attributes;

public interface LogEmitterBuilder {

  LogEmitterBuilder setSchemaUrl(String schemaUrl);

  LogEmitterBuilder setInstrumentationVersion(String instrumentationVersion);

  LogEmitterBuilder setAttributes(Attributes attributes);

  LogEmitter build();
}
