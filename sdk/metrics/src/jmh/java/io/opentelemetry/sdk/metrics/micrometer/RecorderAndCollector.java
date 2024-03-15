/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.micrometer;

interface RecorderAndCollector {
  default void setup(MicrometerBenchmark.ThreadState threadState) {}

  void record(MicrometerBenchmark.ThreadState threadState, double value, int attributesIndex);

  void collect();

  void shutdown();
}
