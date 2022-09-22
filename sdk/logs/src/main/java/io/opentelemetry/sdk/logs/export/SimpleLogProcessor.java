/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs.export;

import static java.util.Objects.requireNonNull;

import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.LogProcessor;
import io.opentelemetry.sdk.logs.ReadWriteLogRecord;
import io.opentelemetry.sdk.logs.data.LogData;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An implementation of the {@link LogProcessor} that passes {@link LogData} directly to the
 * configured exporter.
 *
 * <p>This processor will cause all logs to be exported directly as they finish, meaning each export
 * request will have a single log. Most backends will not perform well with a single log per request
 * so unless you know what you're doing, strongly consider using {@link BatchLogProcessor} instead,
 * including in special environments such as serverless runtimes. {@link SimpleLogProcessor} is
 * generally meant to for testing only.
 */
public final class SimpleLogProcessor implements LogProcessor {

  private static final Logger logger = Logger.getLogger(SimpleLogProcessor.class.getName());

  private final LogRecordExporter logRecordExporter;
  private final Set<CompletableResultCode> pendingExports =
      Collections.newSetFromMap(new ConcurrentHashMap<>());
  private final AtomicBoolean isShutdown = new AtomicBoolean(false);

  /**
   * Returns a new {@link SimpleLogProcessor} which exports logs to the {@link LogRecordExporter}
   * synchronously.
   *
   * <p>This processor will cause all logs to be exported directly as they finish, meaning each
   * export request will have a single log. Most backends will not perform well with a single log
   * per request so unless you know what you're doing, strongly consider using {@link
   * BatchLogProcessor} instead, including in special environments such as serverless runtimes.
   * {@link SimpleLogProcessor} is generally meant to for testing only.
   */
  public static LogProcessor create(LogRecordExporter exporter) {
    requireNonNull(exporter, "exporter");
    return new SimpleLogProcessor(exporter);
  }

  SimpleLogProcessor(LogRecordExporter logRecordExporter) {
    this.logRecordExporter = requireNonNull(logRecordExporter, "logRecordExporter");
  }

  @Override
  public void onEmit(ReadWriteLogRecord logRecord) {
    try {
      List<LogData> logs = Collections.singletonList(logRecord.toLogData());
      CompletableResultCode result = logRecordExporter.export(logs);
      pendingExports.add(result);
      result.whenComplete(
          () -> {
            pendingExports.remove(result);
            if (!result.isSuccess()) {
              logger.log(Level.FINE, "Exporter failed");
            }
          });
    } catch (RuntimeException e) {
      logger.log(Level.WARNING, "Exporter threw an Exception", e);
    }
  }

  @Override
  public CompletableResultCode shutdown() {
    if (isShutdown.getAndSet(true)) {
      return CompletableResultCode.ofSuccess();
    }
    CompletableResultCode result = new CompletableResultCode();

    CompletableResultCode flushResult = forceFlush();
    flushResult.whenComplete(
        () -> {
          CompletableResultCode shutdownResult = logRecordExporter.shutdown();
          shutdownResult.whenComplete(
              () -> {
                if (!flushResult.isSuccess() || !shutdownResult.isSuccess()) {
                  result.fail();
                } else {
                  result.succeed();
                }
              });
        });

    return result;
  }

  @Override
  public CompletableResultCode forceFlush() {
    return CompletableResultCode.ofAll(pendingExports);
  }
}
