/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import static io.opentelemetry.api.common.AttributeType.STRING;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.extension.incubator.trace.data.DelegatingSpanData;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

public class FilterSpanExporter {

  @Test
  void testEnrichAndFilter() {
    InMemorySpanExporter inMemorySpanExporter = InMemorySpanExporter.create();
    Predicate<AttributeKey<?>> filterAttributePredicate = key -> !key.getType().equals(STRING);
    FilteringSpanExporter filteringSpanExporter =
        new FilteringSpanExporter(inMemorySpanExporter, filterAttributePredicate);

    SdkTracerProvider sdkTracerProvider =
        SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(filteringSpanExporter))
            .build();

    for (int i = 0; i < 100; i++) {
      sdkTracerProvider
          .get("test")
          .spanBuilder("span " + i)
          .setAttribute("strkey", "value")
          .setAttribute("boolkey", true)
          .setAttribute("longkey", 125)
          .startSpan()
          .end();
    }

    List<SpanData> spans = inMemorySpanExporter.getFinishedSpanItems();
    assertThat(spans)
        .allSatisfy(
            spanData -> {
              assertThat(spanData.getAttributes().get(AttributeKey.stringKey("strkey")))
                  .isNotNull();
              assertThat(spanData.getAttributes().get(AttributeKey.stringKey("boolkey"))).isNull();
              assertThat(spanData.getAttributes().get(AttributeKey.stringKey("longkey"))).isNull();
            });
  }

  private static class FilteringSpanExporter implements SpanExporter {

    private final SpanExporter spanExporter;
    private final Predicate<AttributeKey<?>> filterAttributePredicate;

    private FilteringSpanExporter(
        SpanExporter spanExporter, Predicate<AttributeKey<?>> filterAttributePredicate) {
      this.spanExporter = spanExporter;
      this.filterAttributePredicate = filterAttributePredicate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      Collection<SpanData> filteredSpans = new ArrayList<>(spans.size());
      for (SpanData spanData : spans) {
        filteredSpans.add(new FilteredSpanData(spanData, filterAttributePredicate));
      }
      return spanExporter.export(filteredSpans);
    }

    @Override
    public CompletableResultCode flush() {
      return spanExporter.flush();
    }

    @Override
    public CompletableResultCode shutdown() {
      return spanExporter.shutdown();
    }
  }

  private static class FilteredSpanData extends DelegatingSpanData {

    private final Predicate<AttributeKey<?>> filterAttributePredicate;

    private FilteredSpanData(
        SpanData delegate, Predicate<AttributeKey<?>> filterAttributePredicate) {
      super(delegate);
      this.filterAttributePredicate = filterAttributePredicate;
    }

    @Override
    public Attributes getAttributes() {
      Attributes attributes = super.getAttributes();
      AttributesBuilder filteredAttributes = Attributes.builder();
      for (Map.Entry<AttributeKey<?>, Object> entry : attributes.asMap().entrySet()) {
        AttributeKey<?> key = entry.getKey();
        if (!filterAttributePredicate.test(key)) {
          addAttribute(filteredAttributes, key, entry.getValue());
        }
      }
      return filteredAttributes.build();
    }

    @SuppressWarnings("unchecked")
    private static void addAttribute(AttributesBuilder builder, AttributeKey<?> key, Object value) {
      switch (key.getType()) {
        case LONG:
          builder.put((AttributeKey<Long>) key, (long) value);
          break;
        case DOUBLE:
          builder.put((AttributeKey<Double>) key, (double) value);
          break;
        case STRING:
          builder.put((AttributeKey<String>) key, (String) value);
          break;
        case BOOLEAN:
          builder.put((AttributeKey<Boolean>) key, (boolean) value);
          break;
        case LONG_ARRAY:
          builder.put((AttributeKey<List<Long>>) key, (List<Long>) value);
          break;
        case DOUBLE_ARRAY:
          builder.put((AttributeKey<List<Double>>) key, (List<Double>) value);
          break;
        case STRING_ARRAY:
          builder.put((AttributeKey<List<String>>) key, (List<String>) value);
          break;
        case BOOLEAN_ARRAY:
          builder.put((AttributeKey<List<Boolean>>) key, (List<Boolean>) value);
          break;
      }
    }
  }
}
