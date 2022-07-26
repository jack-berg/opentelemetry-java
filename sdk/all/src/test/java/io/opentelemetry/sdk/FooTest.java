/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.LogEmitter;
import io.opentelemetry.sdk.logs.LogProcessor;
import io.opentelemetry.sdk.logs.LogRecordBuilder;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.SdkLogEmitterProvider;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.logs.export.LogExporter;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.ReadWriteSpan;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.SpanProcessor;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

public class FooTest {

  @Test
  void span() {
    SpanExporter spanExporter =
        new SpanExporter() {
          @Override
          public CompletableResultCode export(Collection<SpanData> spans) {
            spans.forEach(
                span -> {
                  Attributes attributes = span.getAttributes();
                  String name = span.getName();
                  long startEpochNanos = span.getStartEpochNanos();
                  long endEpochNanos = span.getEndEpochNanos();
                  List<EventData> events = span.getEvents();
                  List<LinkData> links = span.getLinks();
                  SpanKind kind = span.getKind();
                  InstrumentationScopeInfo instrumentationScopeInfo =
                      span.getInstrumentationScopeInfo();
                  SpanContext parentSpanContext = span.getParentSpanContext();
                  SpanContext spanContext = span.getSpanContext();
                  Resource resource = span.getResource();
                  int totalAttributeCount = span.getTotalAttributeCount();
                  int totalRecordedEvents = span.getTotalRecordedEvents();
                  int totalRecordedLinks = span.getTotalRecordedLinks();
                });
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }
        };

    SpanProcessor spanProcessor =
        new SpanProcessor() {
          @Override
          public void onStart(Context parentContext, ReadWriteSpan span) {
            span.setStatus(StatusCode.ERROR);
            span.addEvent("event");
            span.updateName("new name");
            span.setAttribute("foo", "bar");
            span.end();
          }

          @Override
          public boolean isStartRequired() {
            return true;
          }

          @Override
          public void onEnd(ReadableSpan span) {
            spanExporter.export(Collections.singletonList(span.toSpanData()));
          }

          @Override
          public boolean isEndRequired() {
            return true;
          }
        };

    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder().addSpanProcessor(spanProcessor).build();
    Tracer tracer = tracerProvider.get("tracer");
    SpanBuilder spanBuilder = tracer.spanBuilder("span");
    spanBuilder.addLink(
        SpanContext.create("", "", TraceFlags.getDefault(), TraceState.getDefault()));
    spanBuilder.setAttribute("foo", "bar");
    spanBuilder.setParent(Context.root());
    spanBuilder.setSpanKind(SpanKind.CLIENT);
    spanBuilder.setStartTimestamp(Instant.now());
    Span span = spanBuilder.startSpan();
    span.setStatus(StatusCode.ERROR);
    span.addEvent("event");
    span.updateName("new name");
    span.setAttribute("foo", "bar");
    span.end();
  }

  @Test
  void log() {
    LogExporter logExporter =
        new LogExporter() {
          @Override
          public CompletableResultCode export(Collection<LogData> logs) {
            logs.forEach(
                log -> {
                  Attributes attributes = log.getAttributes();
                  Body body = log.getBody();
                  long epochNanos = log.getEpochNanos();
                  InstrumentationScopeInfo instrumentationScopeInfo =
                      log.getInstrumentationScopeInfo();
                  Resource resource = log.getResource();
                  Severity severity = log.getSeverity();
                  SpanContext spanContext = log.getSpanContext();
                });
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
          }

          @Override
          public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
          }
        };

    LogProcessor logProcessor =
        new LogProcessor() {
          @Override
          public void onEmit(ReadWriteLogRecord logRecord) {
            logRecord.setAttribute(AttributeKey.stringKey("baz"), "qux");
            logExporter.export(Collections.singletonList(logRecord.toLogData()));
          }
        };

    SdkLogEmitterProvider logEmitterProvider =
        SdkLogEmitterProvider.builder().addLogProcessor(logProcessor).build();
    LogEmitter emitter = logEmitterProvider.get("emitter");
    LogRecordBuilder logRecordBuilder = emitter.logRecordBuilder();
    logRecordBuilder.setBody("body");
    logRecordBuilder.setContext(Context.root());
    logRecordBuilder.setSeverityText("foo");
    logRecordBuilder.setSeverity(Severity.DEBUG);
    logRecordBuilder.setEpoch(Instant.now());
    logRecordBuilder.setAttributes(Attributes.builder().put("key", "value").build());
    logRecordBuilder.emit();
  }
}
