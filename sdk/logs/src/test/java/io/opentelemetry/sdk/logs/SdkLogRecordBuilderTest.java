/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.resources.Resource;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SdkLogRecordBuilderTest {

  @Test
  void buildAndEmit() {
    Resource resource = Resource.builder().put("key", "value").build();
    InstrumentationScopeInfo scopeInfo = InstrumentationScopeInfo.create("test-scope");
    Instant now = Instant.now();
    String bodyStr = "body";
    String sevText = "sevText";
    Severity severity = Severity.DEBUG3;
    Attributes attrs = Attributes.empty();
    AtomicReference<ReadWriteLogRecord> seenLog = new AtomicReference<>();
    LogProcessor logProcessor = seenLog::set;

    LogEmitterSharedState state = mock(LogEmitterSharedState.class);

    when(state.getResource()).thenReturn(resource);
    when(state.getLogLimits()).thenReturn(LogLimits.getDefault());
    when(state.getLogProcessor()).thenReturn(logProcessor);

    new SdkLogRecordBuilder(state, scopeInfo)
        .setBody(bodyStr)
        .setEpoch(123, TimeUnit.SECONDS)
        .setEpoch(now)
        .setAttributes(attrs)
        // TODO
        // .setContext(context)
        .setSeverity(severity)
        .setSeverity(severity)
        .setSeverityText(sevText)
        .emit();
    LogData logData = seenLog.get().toLogData();
    assertThat(logData.getResource()).isEqualTo(resource);
    assertThat(logData.getInstrumentationScopeInfo()).isEqualTo(scopeInfo);
    assertThat(logData.getEpochNanos())
        .isEqualTo(TimeUnit.SECONDS.toNanos(now.getEpochSecond()) + now.getNano());
    // TODO
    // assertThat(logData.getSpanContext()).isEqualTo()
    assertThat(logData.getSeverity()).isEqualTo(severity);
    assertThat(logData.getSeverityText()).isEqualTo(sevText);
    assertThat(logData.getBody().asString()).isEqualTo(bodyStr);
    assertThat(logData.getAttributes()).isEqualTo(attrs);
  }
}
