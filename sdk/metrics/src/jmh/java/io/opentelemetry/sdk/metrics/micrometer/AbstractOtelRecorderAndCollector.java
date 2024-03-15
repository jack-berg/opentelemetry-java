/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.micrometer;

import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.SdkMeterProviderBuilder;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.util.concurrent.TimeUnit;

abstract class AbstractOtelRecorderAndCollector implements RecorderAndCollector {
  protected final SdkMeterProvider sdkMeterProvider;

  protected AbstractOtelRecorderAndCollector(MemoryMode memoryMode, Aggregation aggregation) {
    InMemoryMetricReader reader =
        InMemoryMetricReader.builder()
            .setMemoryMode(memoryMode)
            .setDefaultAggregationSelector(unused -> aggregation)
            .build();
    SdkMeterProviderBuilder builder = SdkMeterProvider.builder();
    SdkMeterProviderUtil.setExemplarFilter(builder, ExemplarFilter.alwaysOff());
    sdkMeterProvider = builder.registerMetricReader(reader).build();
  }

  protected AbstractOtelRecorderAndCollector(MemoryMode memoryMode) {
    this(memoryMode, Aggregation.defaultAggregation());
  }

  @Override
  public void collect() {
    sdkMeterProvider.forceFlush().join(10, TimeUnit.SECONDS);
  }

  @Override
  public void shutdown() {
    sdkMeterProvider.close();
  }
}
