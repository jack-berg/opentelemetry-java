/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.LogProcessor;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.logs.export.BatchLogProcessor;
import io.opentelemetry.sdk.logs.export.InMemoryLogExporter;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

public class ContextLogProcessorTest {

  private static final ContextKey<String> MY_CONTEXT_KEY = ContextKey.named("my-context-key");

  @Test
  void demoContextExtractingLogProcessor() {
    InMemoryLogExporter logExporter = InMemoryLogExporter.create();
    LogProcessor batchLogProcessor = BatchLogProcessor.builder(logExporter).build();
    Function<LogData, LogData> contextExtractor =
        logData ->
            new DelegatingLogData(
                logData,
                Attributes.builder()
                    .put(
                        "key",
                        Optional.ofNullable(Context.current().get(MY_CONTEXT_KEY)).orElse(""))
                    .build());
    LogProcessor delegatingLogProcessor =
        new DelegatingLogProcessor(batchLogProcessor, contextExtractor);

    SdkLogEmitterProvider logEmitterProvider =
        AutoConfiguredOpenTelemetrySdk.builder()
            .setResultAsGlobal(false)
            .addPropertiesSupplier(() -> ImmutableMap.of("OTEL_LOGS_EXPORTER", "none"))
            .addLogEmitterProviderCustomizer(
                (sdkLogEmitterProviderBuilder, configProperties) ->
                    sdkLogEmitterProviderBuilder.addLogProcessor(delegatingLogProcessor))
            .build()
            .getOpenTelemetrySdk()
            .getSdkLogEmitterProvider();

    try (Scope unused = Context.current().with(MY_CONTEXT_KEY, "value!").makeCurrent()) {
      logEmitterProvider.get("my-logger").logBuilder().setBody("message").emit();
    }

    batchLogProcessor.forceFlush().join(10, TimeUnit.SECONDS);

    assertThat(logExporter.getFinishedLogItems())
        .satisfiesExactly(
            logData -> {
              assertThat(logData.getBody().asString()).isEqualTo("message");
              assertThat(logData.getAttributes().get(AttributeKey.stringKey("key")))
                  .isEqualTo("value!");
            });
  }

  private static class DelegatingLogProcessor implements LogProcessor {

    private final LogProcessor delegate;
    private final Function<LogData, LogData> mapper;

    DelegatingLogProcessor(LogProcessor delegate, Function<LogData, LogData> mapper) {
      this.delegate = delegate;
      this.mapper = mapper;
    }

    @Override
    public void emit(LogData logData) {
      delegate.emit(mapper.apply(logData));
    }
  }

  private static class DelegatingLogData implements LogData {
    private final LogData delegate;
    private final Attributes attributes;

    DelegatingLogData(LogData delegate, Attributes attributes) {
      this.delegate = delegate;
      this.attributes = attributes;
    }

    @Override
    public Resource getResource() {
      return delegate.getResource();
    }

    @Override
    public InstrumentationScopeInfo getInstrumentationScopeInfo() {
      return delegate.getInstrumentationScopeInfo();
    }

    @Override
    public long getEpochNanos() {
      return delegate.getEpochNanos();
    }

    @Override
    public SpanContext getSpanContext() {
      return delegate.getSpanContext();
    }

    @Override
    public Severity getSeverity() {
      return delegate.getSeverity();
    }

    @Nullable
    @Override
    public String getSeverityText() {
      return delegate.getSeverityText();
    }

    @Override
    public Body getBody() {
      return delegate.getBody();
    }

    @Override
    public Attributes getAttributes() {
      return delegate.getAttributes().toBuilder().putAll(attributes).build();
    }
  }
}
