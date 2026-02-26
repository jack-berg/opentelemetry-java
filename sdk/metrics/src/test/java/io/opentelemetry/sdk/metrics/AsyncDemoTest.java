/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AsyncDemoTest {

  @Test
  void asyncInstrumentsManageState() {
    InMemoryMetricReader metricReader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(metricReader).build();
    Meter meter = meterProvider.get("test");

    AtomicInteger counter = new AtomicInteger(1);

    meter
        .counterBuilder("testCounter")
        .buildWithCallback(
            measurement -> {
              int value = counter.getAndDecrement();
              if (value > 0) {
                measurement.record(value);
              }
            });

    // First collect, our testCounter metric is recorded since counter.getAndDecrement() returns 1.
    assertThat(metricReader.collectAllMetrics().size()).isEqualTo(1);
    // Second collector, our testCounter metric is not recorded since counter.getAndDecrement()
    // returns 0.
    assertThat(metricReader.collectAllMetrics().size()).isEqualTo(0);
  }
}
