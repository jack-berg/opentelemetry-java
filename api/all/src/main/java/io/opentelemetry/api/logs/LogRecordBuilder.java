/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public interface LogRecordBuilder {

  LogRecordBuilder setEpoch(long timestamp, TimeUnit unit);

  LogRecordBuilder setEpoch(Instant instant);

  LogRecordBuilder setContext(Context context);

  LogRecordBuilder setSeverity(Severity severity);

  LogRecordBuilder setSeverityText(String severityText);

  LogRecordBuilder setBody(String body);

  LogRecordBuilder setAttributes(Attributes attributes);

  void emit();
}
