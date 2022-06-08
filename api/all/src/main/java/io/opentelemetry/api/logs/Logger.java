/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.logs;

public interface Logger {

  EventBuilder eventBuilder(String name);

  LogRecordBuilder logRecordBuilder();
}
