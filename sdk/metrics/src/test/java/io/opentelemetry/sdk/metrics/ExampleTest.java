/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.data.HistogramPointData;
import io.opentelemetry.sdk.metrics.export.AggregationTemporalitySelector;
import io.opentelemetry.sdk.metrics.export.CollectionRegistration;
import io.opentelemetry.sdk.metrics.export.MetricReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ExampleTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void example() {
    SdkMeterProvider meterProvider =
        SdkMeterProvider.builder()
            .registerMetricReader(
                new MetricReader() {
                  AtomicReference<CollectionRegistration> ref =
                      new AtomicReference<>(CollectionRegistration.noop());

                  @Override
                  public void register(CollectionRegistration registration) {
                    ref.set(registration);
                  }

                  @Override
                  public CompletableResultCode forceFlush() {
                    System.out.format("\nFlush\n");
                    ref.get()
                        .collectAllMetrics()
                        .forEach(
                            metricData -> {
                              System.out.println(metricData.getName() + ":");
                              metricData
                                  .getHistogramData()
                                  .getPoints()
                                  .forEach(
                                      point -> {
                                        try {
                                          System.out.println(
                                              "  attributes: "
                                                  + objectMapper.writeValueAsString(
                                                      point
                                                          .getAttributes()
                                                          .asMap()
                                                          .entrySet()
                                                          .stream()
                                                          .collect(
                                                              Collectors.toMap(
                                                                  entry -> entry.getKey().getKey(),
                                                                  Map.Entry::getValue))));
                                          System.out.println(
                                              "  value: "
                                                  + objectMapper.writeValueAsString(
                                                      new HistogramPoint(point)));
                                          System.out.println();
                                        } catch (JsonProcessingException e) {
                                          e.printStackTrace();
                                        }
                                      });
                              System.out.println();
                            });
                    System.out.println();
                    return CompletableResultCode.ofSuccess();
                  }

                  @Override
                  public CompletableResultCode shutdown() {
                    return CompletableResultCode.ofSuccess();
                  }

                  @Override
                  public AggregationTemporality getAggregationTemporality(
                      InstrumentType instrumentType) {
                    return AggregationTemporalitySelector.deltaPreferred()
                        .getAggregationTemporality(instrumentType);
                  }
                })
            .build();

    DoubleHistogram histogram =
        meterProvider
            .get("meter")
            .histogramBuilder("http.server.request.duration")
            .setUnit("s")
            .setExplicitBucketBoundariesAdvice(Arrays.asList(1.0, 5.0, 10.0))
            .build();

    histogram.record(22.0, httpAttributes("GET", "/users", 200));
    histogram.record(7.0, httpAttributes("GET", "/users/{id}", 200));
    histogram.record(11.0, httpAttributes("GET", "/users/{id}", 200));
    histogram.record(4.0, httpAttributes("GET", "/users/{id}", 200));
    histogram.record(6.0, httpAttributes("GET", "/users/{id}", 404));
    histogram.record(6.2, httpAttributes("PUT", "/users/{id}", 200));
    histogram.record(7.2, httpAttributes("PUT", "/users/{id}", 200));

    meterProvider.forceFlush().join(10, TimeUnit.SECONDS);
  }

  private static Attributes httpAttributes(String method, String route, int responseStatusCode) {
    return Attributes.builder()
        .put("http.request.method", method)
        .put("http.route", route)
        .put("http.response.status_code", responseStatusCode)
        .build();
  }

  private static class HistogramPoint {
    private final HistogramPointData point;

    HistogramPoint(HistogramPointData point) {
      this.point = point;
    }

    public long getCount() {
      return point.getCount();
    }

    public double getSum() {
      return point.getSum();
    }

    public double getMin() {
      return point.getMin();
    }

    public double getMax() {
      return point.getMax();
    }

    public List<List<Number>> getBuckets() {
      List<List<Number>> buckets = new ArrayList<>();
      for (int i = 0; i < point.getBoundaries().size(); i++) {
        buckets.add(Arrays.asList(point.getBoundaries().get(i), point.getCounts().get(i)));
      }
      return buckets;
    }
  }
}
