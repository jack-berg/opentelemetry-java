/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reporter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.internal.SdkMeterProviderUtil;
import io.opentelemetry.sdk.metrics.internal.exemplar.ExemplarFilter;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DropwizardMetricsTest {
  private static final int cardinality = 100;
  private static final int measurementsPerSeries = 10_000;

  private final Random random = new Random();
  private MetricRegistry metricRegistry;
  private MeterRegistry registry;
  private SdkMeterProvider sdkMeterProvider;
  Attributes[] attributesList;
  private List<Tag>[] tagsList;
  private Counter[] counters;
  private DistributionSummary[] distributionSummaries;

  @BeforeEach
  void setup() {
    metricRegistry = new MetricRegistry();

    ConsoleReporter build = ConsoleReporter.forRegistry(metricRegistry).outputTo(System.out).build();
    build.report();

    tagsList = new List[cardinality];
    counters = new Counter[cardinality];
    attributesList = new Attributes[cardinality];
    distributionSummaries = new DistributionSummary[cardinality];
    String last = "aaaaaaaaaaaaaaaaaaaaaaaaaa";
    for (int i = 0; i < cardinality; i++) {
      char[] chars = last.toCharArray();
      chars[random.nextInt(last.length())] = (char) (random.nextInt(26) + 'a');
      last = new String(chars);
      attributesList[i] = Attributes.builder().put("key", last).build();
      tagsList[i] = Collections.singletonList(Tag.of("key", last));
      counters[i] = registry.counter("counter", tagsList[i]);
      distributionSummaries[i] = registry.summary("histogram", tagsList[i]);
    }
  }

  @Test
  void attributesKnown() {
    for (int j = 0; j < measurementsPerSeries; j++) {
      for (int i = 0; i < tagsList.length; i++) {
        int value = random.nextInt(10_000);
        distributionSummaries[i].record(value);
        counters[i].increment(value);
      }
    }

    registry
        .getMeters()
        .forEach(
            meter -> {
              System.out.format("%s: %s\n", meter.getId(), meter.measure());
            });
  }

  @Test
  void attributesUnknown() {
    for (int j = 0; j < measurementsPerSeries; j++) {
      for (int i = 0; i < tagsList.length; i++) {
        int value = random.nextInt(10_000);
        registry.summary("histogram", tagsList[i]).record(value);
        registry.counter("counter", tagsList[i]).increment(value);
      }
    }

    registry
        .getMeters()
        .forEach(
            meter -> {
              System.out.format("%s: %s\n", meter.getId(), meter.measure());
            });
  }

  @Test
  void otelExplicitBucket_MultiThreaded() {
    DoubleHistogram histogram = sdkMeterProvider.get("meter").histogramBuilder("histogram").build();
    runInMultipleThreads(
        4,
        () -> {
          while (true) {
            for (int i = 0; i < tagsList.length; i++) {
              int value = random.nextInt(10_000);
              histogram.record(value, attributesList[i], Context.root());
            }
            try {
              Thread.sleep(10);
            } catch (InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        });
  }

  void runInMultipleThreads(int threadCount, Runnable runnable) {
    List<Thread> threads = new ArrayList<>(threadCount);
    for (int i = 0; i < threadCount; i++) {
      Thread thread = new Thread(runnable);
      thread.setDaemon(true);
      thread.setName("test-" + i);
      thread.start();
      threads.add(thread);
    }
    threads.forEach(
        thread -> {
          try {
            thread.join();
          } catch (InterruptedException e) {
            throw new RuntimeException(e);
          }
        });
  }
}
