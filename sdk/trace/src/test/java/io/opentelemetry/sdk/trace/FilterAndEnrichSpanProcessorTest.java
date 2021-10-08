/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

public class FilterAndEnrichSpanProcessorTest {

  private static final Random RANDOM = new Random();

  @Test
  void testEnrichAndFilter() {
    Predicate<ReadableSpan> shouldFilter =
        span ->
            Optional.ofNullable(span.getAttribute(AttributeKey.booleanKey("filter"))).orElse(true);
    BiConsumer<Context, ReadWriteSpan> enricher =
        (parentContext, span) -> {
          span.setAttribute("foo", "bar");
          span.setAttribute("filter", RANDOM.nextBoolean());
        };
    InMemorySpanExporter inMemorySpanExporter = InMemorySpanExporter.create();
    FilteringSpanProcessor filteringSpanProcessor =
        new FilteringSpanProcessor(shouldFilter, SimpleSpanProcessor.create(inMemorySpanExporter));
    EnrichingSpanProcessor enrichingSpanProcessor =
        new EnrichingSpanProcessor(enricher, filteringSpanProcessor);

    SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(enrichingSpanProcessor).build();

    for (int i = 0; i < 100; i++) {
      sdkTracerProvider.get("test").spanBuilder("span " + i).startSpan().end();
    }

    List<SpanData> spans = inMemorySpanExporter.getFinishedSpanItems();
    assertThat(spans.size()).isGreaterThan(0).isLessThan(100);
    assertThat(spans)
        .allMatch(
            spanData ->
                "bar".equals(spanData.getAttributes().get(AttributeKey.stringKey("foo")))
                    && !Optional.ofNullable(
                            spanData.getAttributes().get(AttributeKey.booleanKey("filter")))
                        .orElse(true));
  }

  private static class EnrichingSpanProcessor implements SpanProcessor {

    BiConsumer<Context, ReadWriteSpan> enricher;
    private final SpanProcessor nextProcessor;

    private EnrichingSpanProcessor(
        BiConsumer<Context, ReadWriteSpan> enricher, SpanProcessor nextProcessor) {
      this.enricher = enricher;
      this.nextProcessor = nextProcessor;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
      enricher.accept(parentContext, span);
      nextProcessor.onStart(parentContext, span);
    }

    @Override
    public boolean isStartRequired() {
      return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
      nextProcessor.onEnd(span);
    }

    @Override
    public boolean isEndRequired() {
      return true;
    }
  }

  private static class FilteringSpanProcessor implements SpanProcessor {

    private final Predicate<ReadableSpan> shouldFilter;
    private final SpanProcessor nextProcessor;

    private FilteringSpanProcessor(
        Predicate<ReadableSpan> shouldFilter, SpanProcessor nextProcessor) {
      this.shouldFilter = shouldFilter;
      this.nextProcessor = nextProcessor;
    }

    @Override
    public void onStart(Context parentContext, ReadWriteSpan span) {
      nextProcessor.onStart(parentContext, span);
    }

    @Override
    public boolean isStartRequired() {
      return true;
    }

    @Override
    public void onEnd(ReadableSpan span) {
      if (shouldFilter.test(span)) {
        return;
      }
      nextProcessor.onEnd(span);
    }

    @Override
    public boolean isEndRequired() {
      return true;
    }
  }
}
