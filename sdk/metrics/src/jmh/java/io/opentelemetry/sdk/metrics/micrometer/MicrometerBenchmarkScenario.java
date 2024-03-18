/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.opentelemetry.api.metrics.DoubleCounter;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;

public enum MicrometerBenchmarkScenario {
  /**
   * Otel recording to histogram with default explicit bucket boundaries, and {@link
   * MemoryMode#REUSABLE_DATA}.
   */
  OTEL_SDK_DEFAULT_HISTOGRAM_REUSABLE_DATA(
      new AbstractOtelRecorderAndCollector(MemoryMode.REUSABLE_DATA) {
        private DoubleHistogram doubleHistogram;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          doubleHistogram = sdkMeterProvider.get("meter").histogramBuilder("histogram").build();
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          doubleHistogram.record(
              value, threadState.attributesList[attributesIndex], Context.root());
        }
      }),
  /**
   * Otel recording to histogram with default explicit bucket boundaries, and {@link
   * MemoryMode#IMMUTABLE_DATA}.
   */
  OTEL_SDK_DEFAULT_HISTOGRAM_IMMUTABLE_DATA(
      new AbstractOtelRecorderAndCollector(MemoryMode.IMMUTABLE_DATA) {
        private DoubleHistogram doubleHistogram;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          doubleHistogram = sdkMeterProvider.get("meter").histogramBuilder("histogram").build();
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          doubleHistogram.record(
              value, threadState.attributesList[attributesIndex], Context.root());
        }
      }),
  /**
   * Otel recording to histogram with base2 exponential aggregation, and {@link
   * MemoryMode#REUSABLE_DATA}.
   */
  OTEL_SDK_EXPONENTIAL_HISTOGRAM_REUSABLE_DATA(
      new AbstractOtelRecorderAndCollector(
          MemoryMode.REUSABLE_DATA, Aggregation.base2ExponentialBucketHistogram()) {
        private DoubleHistogram doubleHistogram;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          doubleHistogram = sdkMeterProvider.get("meter").histogramBuilder("histogram").build();
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          doubleHistogram.record(
              value, threadState.attributesList[attributesIndex], Context.root());
        }
      }),
  /**
   * Otel recording to histogram with base2 exponential aggregation, and {@link
   * MemoryMode#IMMUTABLE_DATA}.
   */
  OTEL_SDK_EXPONENTIAL_HISTOGRAM_IMMUTABLE_DATA(
      new AbstractOtelRecorderAndCollector(
          MemoryMode.IMMUTABLE_DATA, Aggregation.base2ExponentialBucketHistogram()) {
        private DoubleHistogram doubleHistogram;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          doubleHistogram = sdkMeterProvider.get("meter").histogramBuilder("histogram").build();
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          doubleHistogram.record(
              value, threadState.attributesList[attributesIndex], Context.root());
        }
      }),
  /** Otel recording to counter, and {@link MemoryMode#REUSABLE_DATA}. */
  OTEL_SDK_COUNTER_REUSABLE_DATA(
      new AbstractOtelRecorderAndCollector(MemoryMode.REUSABLE_DATA) {
        private DoubleCounter doubleCounter;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          doubleCounter =
              sdkMeterProvider.get("meter").counterBuilder("counter").ofDoubles().build();
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          doubleCounter.add(value, threadState.attributesList[attributesIndex], Context.root());
        }
      }),
  /** Otel recording to counter, and {@link MemoryMode#REUSABLE_DATA}. */
  OTEL_SDK_COUNTER_IMMUTABLE_DATA(
      new AbstractOtelRecorderAndCollector(MemoryMode.IMMUTABLE_DATA) {
        private DoubleCounter doubleCounter;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          doubleCounter =
              sdkMeterProvider.get("meter").counterBuilder("counter").ofDoubles().build();
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          doubleCounter.add(value, threadState.attributesList[attributesIndex], Context.root());
        }
      }),
  /**
   * Micrometer recording to summary distribution (i.e. otel histogram) with bucket boundaries
   * reflecting otel default explicit bucket boundaries, assuming tags ARE NOT known ahead of time
   * (i.e. typical http.server.request.duration). See {@link
   * {@link AbstractMicrometerRecorderAndCollector#setup(MicrometerBenchmark.ThreadState)} for configuration details.
   */
  MICROMETER_DEFAULT_HISTOGRAM_UNKNOWN_TAGS(
      new AbstractMicrometerRecorderAndCollector() {
        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          registry.summary("histogram", threadState.tagsList[attributesIndex]).record(value);
        }
      }),
  /**
   * Micrometer recording to summary distribution (i.e. otel histogram) with bucket boundaries
   * reflecting otel default explicit bucket boundaries, assuming tags ARE known ahead of time. See
   * {@link AbstractMicrometerRecorderAndCollector#setup(MicrometerBenchmark.ThreadState)} for
   * configuration details.
   */
  MICROMETER_DEFAULT_HISTOGRAM_KNOWN_TAGS(
      new AbstractMicrometerRecorderAndCollector() {
        private DistributionSummary[] summaries;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          summaries = new DistributionSummary[threadState.tagsList.length];
          for (int i = 0; i < threadState.tagsList.length; i++) {
            summaries[i] = registry.summary("histogram", threadState.tagsList[i]);
          }
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          summaries[attributesIndex].record(value);
        }
      }),
  /** Micrometer recording to counter, assuming tags ARE NOT known ahead of time. */
  MICROMETER_COUNTER_UNKNOWN_TAGS(
      new AbstractMicrometerRecorderAndCollector() {
        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          registry.counter("counter", threadState.tagsList[attributesIndex]).increment(value);
        }
      }),
  /** Micrometer recording to counter, assuming tags ARE known ahead of time. */
  MICROMETER_COUNTER_KNOWN_TAGS(
      new AbstractMicrometerRecorderAndCollector() {
        private Counter[] counters;

        @Override
        public void setup(MicrometerBenchmark.ThreadState threadState) {
          super.setup(threadState);
          counters = new Counter[threadState.tagsList.length];
          for (int i = 0; i < threadState.tagsList.length; i++) {
            counters[i] = registry.counter("counter", threadState.tagsList[i]);
          }
        }

        @Override
        public void record(
            MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex) {
          counters[attributesIndex].increment(value);
        }
      });

  private final RecorderAndCollector recorderAndCollector;

  MicrometerBenchmarkScenario(RecorderAndCollector recorderAndCollector) {
    this.recorderAndCollector = recorderAndCollector;
  }

  RecorderAndCollector getRecorderAndCollector() {
    return recorderAndCollector;
  }
}
