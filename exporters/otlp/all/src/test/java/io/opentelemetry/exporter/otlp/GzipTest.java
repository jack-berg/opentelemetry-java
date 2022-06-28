/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.exporter.internal.otlp.traces.TraceRequestMarshaler;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.trace.TestSpanData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import okio.Buffer;
import okio.GzipSink;
import org.junit.jupiter.api.Test;

public class GzipTest {

  @Test
  void gzip() throws IOException {
    Map<String, Collection<SpanData>> testCases = new HashMap<>();

    Resource resource =
        Resource.create(Attributes.builder().put("service.name", "my-service").build());
    Attributes recordAttributes =
        Attributes.builder().put("otel.session_id", UUID.randomUUID().toString()).build();
    testCases.put("session id on span - 1 record", spanData(1, resource, recordAttributes));
    testCases.put("session id on span - 10 records", spanData(10, resource, recordAttributes));
    testCases.put("session id on span - 100 records", spanData(100, resource, recordAttributes));

    resource =
        Resource.create(
            Attributes.builder()
                .put("service.name", "my-service", "otel.session_id", UUID.randomUUID().toString())
                .build());
    recordAttributes = Attributes.empty();
    testCases.put("session id on resource - 1 record", spanData(1, resource, recordAttributes));
    testCases.put("session id on resource - 10 records", spanData(10, resource, recordAttributes));
    testCases.put(
        "session id on resource - 100 records", spanData(100, resource, recordAttributes));

    StringBuilder builder = new StringBuilder();

    builder
        .append(
            "| Scenario | JSON Size Raw | JSON Size Compressed (% reduction) | Binary Size Raw (% reduction) | Binary Size Compressed (% reduction) |")
        .append(System.lineSeparator());
    builder
        .append(
            "|----------|---------------|----------------------|-----------------|------------------------|")
        .append(System.lineSeparator());
    testCases.entrySet().stream()
        .sorted(Comparator.comparing(Map.Entry::getKey))
        .forEach(
            entry -> {
              Result result = computeSerializedSize(entry.getKey(), entry.getValue());
              builder.append(
                  String.format(
                      "| %s | %s | %s (%.2f) | %s (%.2f) | %s (%.2f) |%n",
                      entry.getKey(),
                      result.jsonRawSize,
                      result.jsonCompressedSize,
                      (result.jsonRawSize * 1.0 / result.jsonCompressedSize) * 100,
                      result.binaryRawSize,
                      (result.jsonRawSize * 1.0 / result.binaryRawSize) * 100,
                      result.binaryCompressedSize,
                      (result.jsonRawSize * 1.0 / result.binaryCompressedSize) * 100));
            });

    String result = builder.toString();

    System.out.println(result);
    try (FileWriter writer =
        new FileWriter(
            "/Users/jberg/code/open-telemetry/opentelemetry-java/exporters/otlp/compression_result.md")) {
      writer.write(result);
    }
  }

  private Result computeSerializedSize(String testCase, Collection<SpanData> spans) {
    try {
      TraceRequestMarshaler marshaler = TraceRequestMarshaler.create(spans);

      ByteArrayOutputStream json = new ByteArrayOutputStream();
      marshaler.writeJsonTo(json);

      try (FileOutputStream writer =
          new FileOutputStream(
              "/Users/jberg/code/open-telemetry/opentelemetry-java/exporters/otlp/"
                  + testCase.replace(" ", "_")
                  + ".json")) {
        marshaler.writeJsonTo(writer);
      }

      ByteArrayOutputStream binary = new ByteArrayOutputStream();
      marshaler.writeBinaryTo(binary);

      return new Result(
          json.size(),
          gzipCompressedSize(json.toByteArray()),
          binary.size(),
          gzipCompressedSize(binary.toByteArray()));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class Result {
    private final long jsonRawSize;
    private final long jsonCompressedSize;
    private final long binaryRawSize;
    private final long binaryCompressedSize;

    private Result(
        long jsonRawSize, long jsonCompressedSize, long binaryRawSize, long binaryCompressedSize) {
      this.jsonRawSize = jsonRawSize;
      this.jsonCompressedSize = jsonCompressedSize;
      this.binaryRawSize = binaryRawSize;
      this.binaryCompressedSize = binaryCompressedSize;
    }
  }

  private long gzipCompressedSize(byte[] bytes) throws IOException {
    try (Buffer sink = new Buffer()) {
      GzipSink gzipSink = new GzipSink(sink);
      gzipSink.write(new Buffer().write(bytes), bytes.length);
      gzipSink.close();
      return sink.size();
    }
  }

  private List<SpanData> spanData(int count, Resource resource, Attributes spanAttributes) {
    return IntStream.range(0, count)
        .mapToObj(
            i ->
                TestSpanData.builder()
                    .setResource(resource)
                    .setName("name")
                    .setKind(SpanKind.INTERNAL)
                    .setStatus(StatusData.ok())
                    .setStartEpochNanos(
                        TimeUnit.MICROSECONDS.toNanos(System.currentTimeMillis() - 1000))
                    .setEndEpochNanos(TimeUnit.MICROSECONDS.toNanos(System.currentTimeMillis()))
                    .setHasEnded(true)
                    .setAttributes(spanAttributes)
                    .build())
        .collect(Collectors.toList());
  }
}
