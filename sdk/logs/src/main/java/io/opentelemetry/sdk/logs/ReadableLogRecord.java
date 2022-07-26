/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.sdk.logs.data.LogData;

/** A log record that can be read. */
public interface ReadableLogRecord {

  /** Return an immutable {@link LogData} instance representing this log record. */
  LogData toLogData();

  // TODO: add additional log record accessors as needed. All fields can be accessed indirectly via
  // #toLogData() with at additional expense.

}
