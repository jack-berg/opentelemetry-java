/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.micrometer;

import io.prometheus.metrics.model.registry.PrometheusRegistry;

abstract class AbstractPrometheusRecorderAndCollector implements RecorderAndCollector {

  protected PrometheusRegistry prometheusRegistry;

  protected AbstractPrometheusRecorderAndCollector() {}

  @Override
  public void setup(MicrometerBenchmark.ThreadState threadState) {
    prometheusRegistry = new PrometheusRegistry();
  }

  @Override
  public void collect() {
    prometheusRegistry.scrape();
  }
}
