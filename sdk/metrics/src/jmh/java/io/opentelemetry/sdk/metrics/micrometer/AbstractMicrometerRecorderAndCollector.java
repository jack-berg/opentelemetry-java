/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

abstract class AbstractMicrometerRecorderAndCollector implements RecorderAndCollector {
  protected final MeterRegistry registry = micrometerRegistry();

  @Override
  public void collect() {
    registry.getMeters();
  }

  @Override
  public void shutdown() {
    registry.close();
  }

  static MeterRegistry micrometerRegistry() {
    MeterRegistry meterRegistry = new SimpleMeterRegistry();
    meterRegistry
        .config()
        .meterFilter(
            new MeterFilter() {
              @Override
              public DistributionStatisticConfig configure(
                  Meter.Id id, DistributionStatisticConfig config) {
                return DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(
                        Double.MIN_VALUE,
                        5d,
                        10d,
                        25d,
                        50d,
                        75d,
                        100d,
                        250d,
                        500d,
                        750d,
                        1_000d,
                        2_500d,
                        5_000d,
                        7_500d,
                        10_000d)
                    .build();
              }
            });
    return meterRegistry;
  }
}
