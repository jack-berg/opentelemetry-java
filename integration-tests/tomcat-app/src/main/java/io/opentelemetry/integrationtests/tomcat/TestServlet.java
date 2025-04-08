/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

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
import java.util.logging.Logger;

public class TestServlet extends HttpServlet {
  private static final Logger logger = Logger.getLogger(TestServlet.class.getName());

  OpenTelemetrySdk sdk;
  Tracer tracer;

  public TestServlet() {
    sdk =
        OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .setSampler(Sampler.alwaysOn())
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(
                                OtlpHttpSpanExporter.builder()
                                    .setEndpoint("http://localhost:4318/v1/traces")
                                    .build())
                            .build())
                    .build())
            .build();

    tracer = sdk.getTracer("tracer");
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    logger.info("TestServlet#service");

    Span span = tracer.spanBuilder("span").startSpan();

    resp.getWriter().write("<h1>Hello, Jenkins with WAR!</h1>");

    span.end();
  }

  @Override
  public void destroy() {
    sdk.shutdown().join(10, TimeUnit.SECONDS);
  }
}
