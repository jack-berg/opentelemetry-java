/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

/**
 * A combination of the write methods from {@link LogRecord} and the read methods from {@link
 * ReadableLogRecord}.
 */
public interface ReadWriteLogRecord extends LogRecord, ReadableLogRecord {}
