/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

import io.opentelemetry.api.common.Attributes;

public interface LoggerBuilder {

  LoggerBuilder setSchemaUrl(String schemaUrl);

  LoggerBuilder setInstrumentationVersion(String instrumentationVersion);

  LoggerBuilder setAttributes(Attributes attributes);

  Logger build();
}
