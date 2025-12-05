/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.slf4j;

import io.opentelemetry.context.Context;
import io.opentelemetry.extension.slf4j.Slf4jBridge;
import io.opentelemetry.sdk.logs.LogRecordProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;

public final class Slf4jBridgeProcessor implements LogRecordProcessor {
  private Slf4jBridgeProcessor() {}

  public static Slf4jBridgeProcessor create() {
    return new Slf4jBridgeProcessor();
  }

  @Override
  public void onEmit(Context context, ReadWriteLogRecord logRecord) {
    Slf4jBridge.recordToSlf4j(
        context,
        logRecord.getInstrumentationScopeInfo().getName(),
        logRecord.getEventName(),
        logRecord.getBodyValue(),
        logRecord.getAttributes(),
        logRecord.getSeverity());
  }
}
