package io.opentelemetry.integrationtests.tomcat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class TestServlet extends HttpServlet {
  OpenTelemetrySdk sdk;
  Tracer tracer;

  TestServlet() {
    sdk = OpenTelemetrySdk.builder()
        .setTracerProvider(SdkTracerProvider.builder()
            .setSampler(Sampler.alwaysOn())
            .addSpanProcessor(BatchSpanProcessor.builder(OtlpHttpSpanExporter.builder()
                    .setEndpoint("http://localhost:4318/v1/traces")
                    .build())
                .build())
            .build())
        .build();

    tracer = sdk.getTracer("tracer");
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    Span span = tracer.spanBuilder("span").startSpan();
    resp.setStatus(200);
    resp.getWriter().println("Hello world!");
    span.end();
  }

  @Override
  public void destroy() {
    sdk.shutdown().join(10, TimeUnit.SECONDS);
  }
}
