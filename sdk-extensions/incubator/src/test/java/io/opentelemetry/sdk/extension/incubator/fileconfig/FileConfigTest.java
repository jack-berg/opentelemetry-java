/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanLimits;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class FileConfigTest {

  @Test
  void foo() throws FileNotFoundException {
    Yaml yaml = new Yaml();
    Object load = yaml.load(new FileInputStream(
        "/Users/jberg/code/open-telemetry/opentelemetry-java/sdk-extensions/incubator/file-config-natural.yaml"));
    System.out.println(load);
  }


  @Test
  void fileConfig() {
    Resource resource =
        Resource.builder()
            .put("key1", "value")
            .put("key2", 5)
            .setSchemaUrl("http://schema.com")
            .build();

    OpenTelemetrySdk.builder()
        .setTracerProvider(
            SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(SimpleSpanProcessor.create(LoggingSpanExporter.create()))
                .addSpanProcessor(
                    BatchSpanProcessor.builder(
                            OtlpGrpcSpanExporter.builder()
                                .setEndpoint("grpc")
                                .setEndpoint("http://localhost:4317")
                                .addHeader("api-key", System.getenv("API_KEY"))
                                .setCompression("gzip")
                                .setTimeout(Duration.ofMillis(30_000))
                                .build())
                        .setMaxQueueSize(100)
                        .setScheduleDelay(Duration.ofMillis(1_000))
                        .setExporterTimeout(Duration.ofMillis(30_000))
                        .setMaxExportBatchSize(200)
                        .build())
                .setSpanLimits(
                    SpanLimits.builder()
                        .setMaxNumberOfAttributes(10)
                        .setMaxAttributeValueLength(100)
                        .setMaxNumberOfAttributesPerEvent(5)
                        .setMaxNumberOfAttributesPerLink(5)
                        .setMaxNumberOfEvents(10)
                        .setMaxNumberOfLinks(4)
                        .build())
                .build())
        .setMeterProvider(SdkMeterProvider.builder().build())
        .setLogEmitterProvider(SdkLogEmitterProvider.builder().build())
        .setPropagators(
            ContextPropagators.create(
                TextMapPropagator.composite(
                    W3CBaggagePropagator.getInstance(),
                    W3CTraceContextPropagator.getInstance(),
                    new FooPropagator("bar", "qux"))));
  }

  private static final class FooPropagator implements TextMapPropagator {

    private final String foo;
    private final String baz;

    private FooPropagator(String foo, String baz) {
      this.foo = foo;
      this.baz = baz;
    }

    @Override
    public Collection<String> fields() {
      return Arrays.asList(foo, baz);
    }

    @Override
    public <C> void inject(Context context, @Nullable C carrier, TextMapSetter<C> setter) {
      // Do nothing
    }

    @Override
    public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
      return context;
    }
  }
}
