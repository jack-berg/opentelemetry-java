package io.opentelemetry.sdk.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

class MicrometerTest {

  @Test
  void micrometerRecordCollect() {
    MeterRegistry registry = new SimpleMeterRegistry();
    registry.config()
        .meterFilter(new MeterFilter() {
          @Override
          public DistributionStatisticConfig configure(Meter.Id id,
              DistributionStatisticConfig config) {
            return DistributionStatisticConfig.builder()
                .serviceLevelObjectives(0.1, 0.5, 1.5, 2.0)
                .build();
          }
        });


    List<Tag> tags1 = Arrays.asList(Tag.of("key1", "valuea"), Tag.of("key2", "valuec"));
    List<Tag> tags2 = Arrays.asList(Tag.of("key1", "valueb"), Tag.of("key2", "valued"));
    registry.counter("counter", tags1).increment();
    registry.counter("counter", tags2).increment();

    registry.timer("timer", tags1).record(Duration.ofSeconds(1));
    registry.timer("timer", tags1).record(Duration.ofSeconds(2));
    registry.timer("timer", tags2).record(Duration.ofSeconds(1));


    registry.summary("summary", tags1).record(1.0);
    registry.summary("summary", tags2).record(1.0);

    registry.getMeters().forEach(meter -> {
      System.out.format("%s: %s\n", meter.getId(), meter.measure());
    });

  }
}
