/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.baggage.Baggage;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

class BaggageDemoTest {

  @Test
  void test() {
    SdkMeterProviderBuilder builder = SdkMeterProvider.builder();
    ViewBuilder viewBuilder = View.builder();
    SdkMeterProviderUtil.appendFilteredBaggageAttributes(
        viewBuilder, name -> name.equals("my-key"));
    builder.registerView(InstrumentSelector.builder().setName("*").build(), viewBuilder.build());

    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider = builder.registerMetricReader(reader).build();

    LongCounter counter = meterProvider.get("meter").counterBuilder("counter").build();

    try (Scope unused =
        Baggage.builder()
            .put("my-key", "my-value")
            .put("another-key", "another-value")
            .build()
            .storeInContext(Context.current())
            .makeCurrent()) {
      counter.add(1);
    }

    assertThat(reader.collectAllMetrics())
        .satisfiesExactly(
            metricData -> {
              assertThat(metricData)
                  .hasName("counter")
                  .hasLongSumSatisfying(
                      sum ->
                          sum.hasPointsSatisfying(
                              point ->
                                  point
                                      .hasValue(1)
                                      .hasAttributes(
                                          Attributes.builder().put("my-key", "my-value").build())));
            });
  }
}
