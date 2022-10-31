/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongUpDownCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.junit.jupiter.api.Test;

public class DropAttributeAsyncTest {

  @Test
  void test() {
    InMemoryMetricReader reader = InMemoryMetricReader.create();
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(reader)
            .registerView(
                InstrumentSelector.builder().setName("my-instrument").build(),
                View.builder().setAttributeFilter(attributeKey -> false).build())
            .build();

    Meter myMeter = meterProvider.get("my-meter");

    LongUpDownCounterBuilder myInstrument = myMeter.upDownCounterBuilder("my-instrument");
    myInstrument.buildWithCallback(
        observable -> {
          observable.record(10, Attributes.builder().put("key", "value1").build());
          observable.record(10, Attributes.builder().put("key", "value2").build());
        });

    OpenTelemetryAssertions.assertThat(reader.collectAllMetrics())
        .satisfiesExactly(
            metricData ->
                OpenTelemetryAssertions.assertThat(metricData)
                    .hasLongSumSatisfying(
                        sum ->
                            sum.hasPointsSatisfying(
                                point ->
                                    point
                                        // Should aggregate the two 10 values together but instead takes the last reported measurement
                                        .hasValue(10)
                                        .hasAttributes(
                                            Attributes.empty()))));
  }
}
