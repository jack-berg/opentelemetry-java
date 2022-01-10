/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.logging.otlp;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader;
import java.time.Duration;
import org.junit.jupiter.api.Test;

public class DemoTest {

  @Test
  void oneSecondInterval() throws InterruptedException {
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(
                        OtlpJsonLoggingMetricExporter.create(AggregationTemporality.CUMULATIVE))
                    .setInterval(Duration.ofSeconds(1))
                    .newMetricReaderFactory())
            .build();

    LongCounter counter =
        meterProvider.get("instrumentation-name").counterBuilder("work_done").build();

    for (int i = 0; i < 15; i++) {
      Thread.sleep(2000);
      counter.add(1);
    }
  }

  @Test
  void twoSecondInterval() throws InterruptedException {
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(
                        OtlpJsonLoggingMetricExporter.create(AggregationTemporality.CUMULATIVE))
                    .setInterval(Duration.ofSeconds(2))
                    .newMetricReaderFactory())
            .build();

    LongCounter counter =
        meterProvider.get("instrumentation-name").counterBuilder("work_done").build();

    for (int i = 0; i < 15; i++) {
      Thread.sleep(2000);
      counter.add(1);
    }
  }

  @Test
  void fourSecondInterval() throws InterruptedException {
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(
                PeriodicMetricReader.builder(
                        OtlpJsonLoggingMetricExporter.create(AggregationTemporality.CUMULATIVE))
                    .setInterval(Duration.ofSeconds(4))
                    .newMetricReaderFactory())
            .build();

    LongCounter counter =
        meterProvider.get("instrumentation-name").counterBuilder("work_done").build();

    for (int i = 0; i < 15; i++) {
      Thread.sleep(2000);
      counter.add(1);
    }
  }


}
