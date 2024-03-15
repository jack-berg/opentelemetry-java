package io.opentelemetry.sdk.metrics.micrometer;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Measurement(iterations = 5, batchSize = 100)
@Warmup(iterations = 2, batchSize = 10)
@Fork(1)
public class MicrometerBenchmark {

  private static final int cardinality = 100;
  private static final int measurementsPerSeries = 10_000;

  @State(Scope.Benchmark)
  public static class ThreadState {

    @Param private Scenario scenario;

    private RecorderAndCollector recorderAndCollector;
    private Random random;
    private List<Attributes> attributesList;
    private List<List<Tag>> tagsList;

    @Setup
    public void setup() {
      recorderAndCollector = scenario.recorderAndCollector;
      random = new Random();
      attributesList = new ArrayList<>(cardinality);
      tagsList = new ArrayList<>(cardinality);
      String last = "aaaaaaaaaaaaaaaaaaaaaaaaaa";
      for (int i = 0; i < cardinality; i++) {
        char[] chars = last.toCharArray();
        chars[random.nextInt(last.length())] = (char) (random.nextInt(26) + 'a');
        last = new String(chars);
        attributesList.add(Attributes.builder().put("key", last).build());
        tagsList.add(Collections.singletonList(Tag.of("key", last)));
      }
    }

    @TearDown
    public void teardown() {
      recorderAndCollector.shutdown();
    }
  }

  public enum Scenario {
    OTEL_SDK_WITH_DEFAULT_HISTOGRAM(new RecorderAndCollector() {
      private final InMemoryMetricReader reader = InMemoryMetricReader.create();
      private final SdkMeterProvider sdkMeterProvider = SdkMeterProvider.builder().registerMetricReader(reader).build();
      private final LongHistogram longHistogram = sdkMeterProvider.get("meter").histogramBuilder("histogram").ofLongs()
          .build();

      @Override
      public void record(ThreadState threadState, long value, int attributesIndex) {
        longHistogram.record(value, threadState.attributesList.get(attributesIndex));
      }

      @Override
      public void collect() {
        reader.collectAllMetrics();
      }

      @Override
      public void shutdown() {
        reader.shutdown().join(10, TimeUnit.SECONDS);
      }
    }),
    MICROMETER_WITH_DEFAULT_HISTOGRAM(new RecorderAndCollector() {
      private final MeterRegistry registry = registry();

      private MeterRegistry registry() {
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        meterRegistry.config()
            .meterFilter(new MeterFilter() {
              @Override
              public DistributionStatisticConfig configure(Meter.Id id,
                  DistributionStatisticConfig config) {
                return DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(Double.MIN_VALUE, 5d, 10d, 25d, 50d, 75d, 100d, 250d, 500d, 750d, 1_000d, 2_500d, 5_000d, 7_500d,
                        10_000d)
                    .build();
              }
            });
        return meterRegistry;
      }

      @Override
      public void record(ThreadState threadState, long value, int attributesIndex) {
        registry.summary("histogram", threadState.tagsList.get(attributesIndex));
      }

      @Override
      public void collect() {
        registry.getMeters();
      }

      @Override
      public void shutdown() {
        registry.close();
      }
    });

    private RecorderAndCollector recorderAndCollector;

    Scenario(RecorderAndCollector recorderAndCollector) {
      this.recorderAndCollector = recorderAndCollector;
    }
  }

  interface RecorderAndCollector {
    void record(ThreadState threadState, long value, int attributesIndex);

    void collect();

    void shutdown();
  }

  @Benchmark
  public void recordAndCollect(ThreadState threadState) {
    record(threadState);
    threadState.recorderAndCollector.collect();
  }

  @Benchmark
  @Threads(1)
  public void recordOneThread(ThreadState threadState) {
    record(threadState);
  }

  @Benchmark
  @Threads(8)
  public void recordEightThreads(ThreadState threadState) {
    record(threadState);
  }

  private void record(ThreadState threadState) {
    for (int j = 0; j < measurementsPerSeries; j++) {
      for (int i = 0; i < threadState.attributesList.size(); i++) {
        int value = threadState.random.nextInt(10_000);
        threadState.recorderAndCollector.record(threadState, value, i);
      }
    }
  }

}
