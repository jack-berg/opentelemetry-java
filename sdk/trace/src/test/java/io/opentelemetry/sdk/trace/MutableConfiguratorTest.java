/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;

import io.opentelemetry.api.incubator.trace.ExtendedTracer;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.internal.ScopeConfigurator;
import io.opentelemetry.sdk.trace.internal.TracerConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MutableConfiguratorTest {

  @Test
  void demo() {
    MapBasedConfigurator<TracerConfig> configurator =
        new MapBasedConfigurator<>(TracerConfig.disabled());

    SdkTracerProvider tracerProvider =
        SdkTracerProvider.builder().setTracerConfigurator(configurator).build();

    ExtendedTracer tracer1 = (ExtendedTracer) tracerProvider.get("tracer1");
    ExtendedTracer tracer2 = (ExtendedTracer) tracerProvider.get("tracer2");

    // Assert that tracer1 and tracer2 are disabled as dictated by configurator
    assertThat(tracer1.isEnabled()).isFalse();
    assertThat(tracer2.isEnabled()).isFalse();

    // ...later, update the configurator to enable the tracer1
    configurator.put(InstrumentationScopeInfo.create("tracer1"), TracerConfig.enabled());
    tracerProvider.updateTracerConfigurator(configurator);

    // Assert that tracer1 is enabled and tracer2 is disabled, reflecting the updated configurator
    assertThat(tracer1.isEnabled()).isTrue();
    assertThat(tracer2.isEnabled()).isFalse();
  }

  private static class MapBasedConfigurator<T> implements ScopeConfigurator<T> {

    private final Map<InstrumentationScopeInfo, T> map = new LinkedHashMap<>();
    private final T defaultConfig;

    private MapBasedConfigurator(T defaultConfig) {
      this.defaultConfig = defaultConfig;
    }

    public T put(InstrumentationScopeInfo scope, T config) {
      return map.put(scope, config);
    }

    @Override
    public T apply(InstrumentationScopeInfo scope) {
      return map.getOrDefault(scope, defaultConfig);
    }
  }
}
