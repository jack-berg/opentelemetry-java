package io.opentelemetry.sdk.trace;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ShutdownTest {

  @Test
  void test() {
    TestExporter exporter = new TestExporter();
    exporter.exportBlockingMillis.set(5);
    exporter.shutdownBlockingMillis.set(5);

    BatchSpanProcessor batchSpanProcessor = BatchSpanProcessor.builder(exporter).setExportUnsampledSpans(true).setScheduleDelay(
        Duration.ofSeconds(1000)).build();

    SdkSpan span = SdkSpan.startSpan(SpanContext.getInvalid(), "name",
        InstrumentationScopeInfo.empty(), SpanKind.INTERNAL,
        Span.getInvalid(), Context.root(), SpanLimits.getDefault(), batchSpanProcessor,
        Clock.getDefault(), Resource.getDefault(), null, Collections.emptyList(), 0, 1);
    span.end();

    System.out.println("processor#shutdown - started");
    batchSpanProcessor.shutdown().join(10, TimeUnit.SECONDS);
    System.out.println("processor#shutdown - completed");
  }

  private static final class TestExporter implements SpanExporter {

    private final AtomicLong exportBlockingMillis = new AtomicLong();
    private final AtomicLong shutdownBlockingMillis = new AtomicLong();

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
      System.out.println("exporter#export - started");
      sleepSafe(exportBlockingMillis.get());
      System.out.println("exporter#export - complete");
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode flush() {
      return CompletableResultCode.ofSuccess();
    }

    @Override
    public CompletableResultCode shutdown() {
      System.out.println("exporter#shutdown - started");
      sleepSafe(shutdownBlockingMillis.get());
      System.out.println("exporter#shutdown - complete");
      return CompletableResultCode.ofSuccess();
    }
  }

  private static void sleepSafe(long sleepMillis) {
    try {
      Thread.sleep(sleepMillis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
