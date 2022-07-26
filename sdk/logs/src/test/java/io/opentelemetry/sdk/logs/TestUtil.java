/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.resources.Resource;
import java.util.concurrent.TimeUnit;

public final class TestUtil {

  public static ReadWriteLogRecord emitLogRecord(
      LogProcessor logProcessor, Severity severity, String message) {
    return SdkLogRecord.emitLogRecord(
        logProcessor,
        Resource.create(Attributes.builder().put("testKey", "testValue").build()),
        InstrumentationScopeInfo.create("instrumentation", "1", null),
        LogLimits.getDefault(),
        TimeUnit.MILLISECONDS.toNanos(System.currentTimeMillis()),
        SpanContext.getInvalid(),
        severity,
        "really severe",
        Body.string(message),
        Attributes.builder().put("animal", "cat").build());
  }

  private TestUtil() {}
}
