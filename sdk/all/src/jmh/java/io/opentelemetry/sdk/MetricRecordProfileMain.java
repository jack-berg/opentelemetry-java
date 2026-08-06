/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk;

import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Standalone launcher for a single {@link MetricRecordBenchmark} scenario without JMH. Reuses the
 * benchmark's {@link MetricRecordBenchmark.BenchmarkState} and {@link
 * MetricRecordBenchmark.ThreadState} setup, then drives {@link MetricRecordBenchmark#record} in a
 * hot loop from configured worker threads.
 *
 * <p>Intended for IDE profiler attach (IntelliJ's built-in profiler, async-profiler, etc.) or
 * running with {@code -XX:+PrintInlining} to inspect JIT decisions on the record path. Edit the
 * constants at the top to switch scenarios. Ops/s is printed periodically as a sanity check that
 * throughput matches the JMH result for the same params.
 */
@SuppressWarnings("SystemOut")
public final class MetricRecordProfileMain {

  // ─── Scenario configuration ─────────────────────────────────────────────────────────────────

  private static final MetricRecordBenchmark.InstrumentTypeAndAggregation INSTRUMENT_TYPE =
      MetricRecordBenchmark.InstrumentTypeAndAggregation.HISTOGRAM_EXPLICIT;
  private static final AggregationTemporality TEMPORALITY = AggregationTemporality.CUMULATIVE;
  private static final int CARDINALITY = 1;
  private static final boolean BOUND = false;
  private static final boolean PROMETHEUS = false;
  private static final int THREADS = 1;

  // ─── Run configuration ──────────────────────────────────────────────────────────────────────

  /** Seconds to sleep before starting recording. Attach profiler during this window if needed. */
  private static final int STARTUP_DELAY_SECONDS = 5;

  /**
   * Seconds of warmup before reporting steady-state ops/s. Workers run during warmup too; the
   * reporter just doesn't count these seconds.
   */
  private static final int WARMUP_SECONDS = 10;

  /** Seconds of measurement. Set to 0 for indefinite (Ctrl+C to stop). */
  private static final int MEASUREMENT_SECONDS = 60;

  /** How often to print a rolling ops/s update during measurement. */
  private static final int REPORT_EVERY_SECONDS = 5;

  // ─── ─────────────────────────────────────────────────────────────────────────────────────────

  private MetricRecordProfileMain() {}

  public static void main(String[] args) throws Exception {
    System.out.printf(
        "Scenario: %s, %s, card=%d, bound=%s, prometheus=%s, threads=%d%n",
        INSTRUMENT_TYPE, TEMPORALITY, CARDINALITY, BOUND, PROMETHEUS, THREADS);
    System.out.printf(
        "Timings: startup=%ds, warmup=%ds, measurement=%s%n",
        STARTUP_DELAY_SECONDS,
        WARMUP_SECONDS,
        MEASUREMENT_SECONDS > 0 ? MEASUREMENT_SECONDS + "s" : "indefinite");

    MetricRecordBenchmark.BenchmarkState state = new MetricRecordBenchmark.BenchmarkState();
    state.instrumentTypeAndAggregation = INSTRUMENT_TYPE;
    state.aggregationTemporality = TEMPORALITY;
    state.cardinality = CARDINALITY;
    state.bound = BOUND;
    state.prometheus = PROMETHEUS;
    state.setup();

    System.out.printf(
        "Setup complete. Sleeping %ds before starting workers...%n", STARTUP_DELAY_SECONDS);
    Thread.sleep(TimeUnit.SECONDS.toMillis(STARTUP_DELAY_SECONDS));

    AtomicBoolean stop = new AtomicBoolean(false);
    AtomicLong totalRecords = new AtomicLong();
    CountDownLatch startLatch = new CountDownLatch(1);
    Thread[] workers = new Thread[THREADS];
    for (int t = 0; t < THREADS; t++) {
      MetricRecordBenchmark.ThreadState threadState = new MetricRecordBenchmark.ThreadState();
      threadState.setup(state);
      workers[t] =
          new Thread(
              () -> {
                try {
                  startLatch.await();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                long localRecords = 0;
                while (!stop.get()) {
                  MetricRecordBenchmark.record(state, threadState);
                  localRecords += BenchmarkUtils.RECORDS_PER_INVOCATION;
                  // Publish counter periodically to avoid heavy contention on the shared counter.
                  if ((localRecords & 0xFFFFF) == 0) {
                    totalRecords.addAndGet(localRecords);
                    localRecords = 0;
                  }
                }
                totalRecords.addAndGet(localRecords);
              },
              "record-worker-" + t);
      workers[t].setDaemon(true);
      workers[t].start();
    }
    startLatch.countDown();

    System.out.printf("Warming up for %ds...%n", WARMUP_SECONDS);
    Thread.sleep(TimeUnit.SECONDS.toMillis(WARMUP_SECONDS));

    long baseline = totalRecords.get();
    long measurementStartNanos = System.nanoTime();
    System.out.println("Measurement begins.");

    long endNanos =
        MEASUREMENT_SECONDS > 0
            ? measurementStartNanos + TimeUnit.SECONDS.toNanos(MEASUREMENT_SECONDS)
            : Long.MAX_VALUE;

    long lastReportNanos = measurementStartNanos;
    long lastReportRecords = baseline;
    while (System.nanoTime() < endNanos) {
      Thread.sleep(TimeUnit.SECONDS.toMillis(REPORT_EVERY_SECONDS));
      long nowNanos = System.nanoTime();
      long nowRecords = totalRecords.get();
      double intervalOpsPerSec =
          (nowRecords - lastReportRecords) / ((nowNanos - lastReportNanos) / 1e9);
      double cumulativeOpsPerSec =
          (nowRecords - baseline) / ((nowNanos - measurementStartNanos) / 1e9);
      System.out.printf(
          "  interval=%,.0f ops/s   cumulative=%,.0f ops/s   elapsed=%.1fs%n",
          intervalOpsPerSec, cumulativeOpsPerSec, (nowNanos - measurementStartNanos) / 1e9);
      lastReportNanos = nowNanos;
      lastReportRecords = nowRecords;
    }

    stop.set(true);
    for (Thread w : workers) {
      w.join(5000);
    }
    state.tearDown();
    System.out.println("Done.");
  }
}
