/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

public class StateManagement {

  @Test
  void demo() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder().registerMetricReader(reader).build();

    Meter meter = meterProvider.get("meter");

    AtomicLong value = new AtomicLong(1);

    meter
        .counterBuilder("async-counter")
        .buildWithCallback(
            observable -> {
              long curVal = value.get();
              if (curVal > 0) {
                observable.record(curVal);
              }
            });

    // value is > 0, so the callback reports a measurement and there should be metric exported
    assertThat(reader.collectAllMetrics()).hasSize(1);

    // value is == 0, so the callback DOES NOT report a measurement and there should NOT be a metric
    // exported
    value.set(0);
    assertThat(reader.collectAllMetrics()).hasSize(0);
  }
}
