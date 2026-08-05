/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk;

import static io.opentelemetry.sdk.metrics.InstrumentType.COUNTER;
import static io.opentelemetry.sdk.metrics.InstrumentType.GAUGE;
import static io.opentelemetry.sdk.metrics.InstrumentType.HISTOGRAM;
import static io.opentelemetry.sdk.metrics.InstrumentType.UP_DOWN_COUNTER;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.incubator.metrics.BoundDoubleCounter;
import io.opentelemetry.api.incubator.metrics.BoundDoubleGauge;
import io.opentelemetry.api.incubator.metrics.BoundDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.BoundDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.BoundLongCounter;
import io.opentelemetry.api.incubator.metrics.BoundLongGauge;
import io.opentelemetry.api.incubator.metrics.BoundLongHistogram;
import io.opentelemetry.api.incubator.metrics.BoundLongUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedDoubleUpDownCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongCounter;
import io.opentelemetry.api.incubator.metrics.ExtendedLongGauge;
import io.opentelemetry.api.incubator.metrics.ExtendedLongHistogram;
import io.opentelemetry.api.incubator.metrics.ExtendedLongUpDownCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.Aggregation;
import io.opentelemetry.sdk.metrics.Base2ExponentialHistogramOptions;
import io.opentelemetry.sdk.metrics.ExemplarFilter;
import io.opentelemetry.sdk.metrics.ExplicitBucketHistogramOptions;
import io.opentelemetry.sdk.metrics.InstrumentType;
import io.opentelemetry.sdk.metrics.InstrumentValueType;
import io.opentelemetry.sdk.metrics.SdkMeterProvider;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
import io.opentelemetry.sdk.metrics.export.DefaultAggregationSelector;
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;
import io.prometheus.metrics.core.datapoints.CounterDataPoint;
import io.prometheus.metrics.core.datapoints.DistributionDataPoint;
import io.prometheus.metrics.core.datapoints.GaugeDataPoint;
import io.prometheus.metrics.core.metrics.Counter;
import io.prometheus.metrics.core.metrics.Gauge;
import io.prometheus.metrics.core.metrics.Histogram;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * This benchmark measures the performance of recording metrics. It includes the following
 * dimensions:
 *
 * <ul>
 *   <li>{@link BenchmarkState#instrumentTypeAndAggregation} composite of {@link InstrumentType} and
 *       {@link Aggregation}, including all relevant combinations for synchronous instruments.
 *   <li>{@link BenchmarkState#aggregationTemporality}
 *   <li>{@link BenchmarkState#cardinality}
 *   <li>{@link BenchmarkState#bound} whether recording goes through bound instruments ({@code
 *       Extended*#bind(Attributes)}) or unbound instruments.
 *   <li>thread count
 *   <li>{@link BenchmarkState#instrumentValueType}, {@link BenchmarkState#memoryMode}, and {@link
 *       BenchmarkState#exemplars} are disabled to reduce combinatorial explosion.
 * </ul>
 *
 * <p>Each operation consists of recording {@link MetricRecordBenchmark#RECORDS_PER_INVOCATION}
 * measurements.
 *
 * <p>The cardinality and thread count dimensions partially overlap. Cardinality dictates how many
 * unique attribute sets (i.e. series) are recorded to, and thread count dictates how many threads
 * are simultaneously recording to those series. For unbound instruments, the record path looks up
 * an aggregation handle for the series corresponding to the measurement's {@link Attributes} in a
 * {@link java.util.concurrent.ConcurrentHashMap}; for bound instruments ({@link
 * BenchmarkState#bound}) that handle is resolved once at bind time, so the record path skips the
 * lookup and attribute processing entirely. The cardinality dictates the size of this map, which
 * has some impact on performance. However, by far the dominant bottleneck is contention. That is,
 * the number of threads simultaneously trying to record to the same series. Increasing the threads
 * increases contention. Increasing cardinality decreases contention, as the threads are now
 * spreading their record activities over more distinct series. The highest contention scenario is
 * cardinality=1, threads=4. Any scenario with threads=1 has zero contention.
 *
 * <p>It's useful to characterize the performance of the metrics system under contention, as some
 * high-performance applications may have many threads trying to record to the same series. It's
 * also useful to characterize the performance of the metrics system under low contention, as some
 * high-performance applications may not frequently be trying to concurrently record to the same
 * series yet still care about the overhead of each record operation.
 *
 * <p>{@link AggregationTemporality} can impact performance because additional concurrency controls
 * are needed to ensure there are no duplicate, partial, or lost writes while resetting the set of
 * timeseries each collection.
 */
public class MetricRecordBenchmark {

  private static final int INITIAL_SEED = 513423236;
  private static final int MAX_THREADS = 4;
  private static final int RECORDS_PER_INVOCATION = BenchmarkUtils.RECORDS_PER_INVOCATION;

  @State(Scope.Benchmark)
  public static class BenchmarkState {

    @Param InstrumentTypeAndAggregation instrumentTypeAndAggregation;

    @Param AggregationTemporality aggregationTemporality;

    @Param({"1", "4", "32", "128"})
    int cardinality;

    // Whether to record through bound instruments (Extended*#bind(Attributes)), which resolve the
    // timeseries once up front, or unbound instruments, which look up the timeseries by Attributes
    // on every record. Uncomment to evaluate.
    @Param({"false", "true"})
    boolean bound;

    // Exploratory: when true, record through the equivalent Prometheus Java client instrument
    // using the same benchmark harness (measurements, cardinality, threads, bound-vs-unbound).
    // Not intended for merge; aggregationTemporality is a no-op for Prometheus (it's always
    // cumulative). Use to compare the OTel and Prometheus record paths under identical
    // workload shapes.
    @Param({"false", "true"})
    boolean prometheus;

    // The following parameters are excluded from the benchmark to reduce combinatorial explosion
    // but can optionally be enabled for adhoc evaluation.

    // InstrumentValueType doesn't materially impact performance. Uncomment to evaluate.
    // @Param
    // InstrumentValueType instrumentValueType;
    InstrumentValueType instrumentValueType = InstrumentValueType.LONG;

    // MemoryMode almost exclusively impacts collect from a performance standpoint. Uncomment to
    // evaluate.
    // @Param
    // MemoryMode memoryMode;
    MemoryMode memoryMode = MemoryMode.REUSABLE_DATA;

    // Exemplars can impact performance, but we skip evaluation to limit test cases. Uncomment to
    // evaluate.
    // @Param({"true", "false"})
    // boolean exemplars;
    boolean exemplars = false;

    OpenTelemetrySdk openTelemetry;
    // Populated when bound == false && !prometheus.
    private Instrument instrument;
    // Populated when bound == false && prometheus.
    private PrometheusInstrument prometheusInstrument;
    // Populated when bound == true; parallel to attributesList (one bound instrument per series).
    // Used for both OTel and Prometheus.
    private List<BoundInstrument> boundInstruments;
    List<Long> measurements;
    List<Attributes> attributesList;
    // Parallel to attributesList when prometheus == true; the single label value per series.
    List<String> labelValues;
    Span span;
    io.opentelemetry.context.Scope contextScope;
    // Hands out a distinct seed to each recording thread's ThreadState so threads traverse the
    // series in independent orders (see ThreadState).
    final AtomicInteger threadSeedSequence = new AtomicInteger();

    @Setup
    @SuppressWarnings("MustBeClosedChecker")
    public void setup() {
      // Prometheus is always cumulative; the DELTA row duplicates the CUMULATIVE row. Skip via
      // JMH's setup-exception mechanism so invalid combos don't appear in results.
      if (prometheus && aggregationTemporality == AggregationTemporality.DELTA) {
        throw new SkipInvalidCombo(
            "Prometheus is cumulative-only; skipping duplicate DELTA combo");
      }
      InstrumentType instrumentType = instrumentTypeAndAggregation.instrumentType;
      Aggregation aggregation = instrumentTypeAndAggregation.aggregation;

      openTelemetry =
          OpenTelemetrySdk.builder()
              .setTracerProvider(SdkTracerProvider.builder().setSampler(Sampler.alwaysOn()).build())
              .setMeterProvider(
                  SdkMeterProvider.builder()
                      .registerMetricReader(
                          InMemoryMetricReader.builder()
                              .setAggregationTemporalitySelector(unused -> aggregationTemporality)
                              .setDefaultAggregationSelector(
                                  DefaultAggregationSelector.getDefault()
                                      .with(instrumentType, aggregation))
                              .setMemoryMode(memoryMode)
                              .build())
                      .setExemplarFilter(
                          exemplars ? ExemplarFilter.traceBased() : ExemplarFilter.alwaysOff())
                      .build())
              .build();

      Meter meter = openTelemetry.getMeter("benchmark");
      Tracer tracer = openTelemetry.getTracer("benchmark");
      span = tracer.spanBuilder("benchmark").startSpan();
      // We suppress warnings on closing here, as we rely on tests to make sure context is closed.
      contextScope = span.makeCurrent();

      Random random = new Random(INITIAL_SEED);
      attributesList = new ArrayList<>(cardinality);
      labelValues = new ArrayList<>(cardinality);
      AttributeKey<String> key = AttributeKey.stringKey("key");
      String last = "aaaaaaaaaaaaaaaaaaaaaaaaaa";
      for (int i = 0; i < cardinality; i++) {
        char[] chars = last.toCharArray();
        chars[random.nextInt(last.length())] = (char) (random.nextInt(26) + 'a');
        last = new String(chars);
        attributesList.add(Attributes.of(key, last));
        labelValues.add(last);
      }
      // Shuffle both lists identically so labelValues[i] matches the label of attributesList[i].
      Random shuffleRandom = new Random(INITIAL_SEED);
      Collections.shuffle(attributesList, shuffleRandom);
      Collections.shuffle(labelValues, new Random(INITIAL_SEED));

      if (prometheus) {
        if (bound) {
          boundInstruments = bindPrometheusInstruments(instrumentType, labelValues);
        } else {
          prometheusInstrument = getPrometheusInstrument(instrumentType);
        }
      } else {
        if (bound) {
          boundInstruments =
              bindInstruments(meter, instrumentType, instrumentValueType, attributesList);
        } else {
          instrument = getInstrument(meter, instrumentType, instrumentValueType);
        }
      }

      measurements = new ArrayList<>(RECORDS_PER_INVOCATION);
      for (int i = 0; i < RECORDS_PER_INVOCATION; i++) {
        measurements.add((long) random.nextInt(2000));
      }
      Collections.shuffle(measurements);
    }

    @TearDown
    public void tearDown() {
      contextScope.close();
      span.end();
      openTelemetry.shutdown();
    }
  }

  /**
   * Per-thread series traversal order. Each recording thread shuffles {@code [0, cardinality)} with
   * a distinct seed, so that at any given record the threads are recording to <em>different</em>
   * series rather than marching through the same series in lockstep. Without this, the shared
   * sequential {@code i % cardinality} index plus contention's self-synchronizing effect collapses
   * the high-cardinality, multi-thread cases into a single rotating hotspot (effectively
   * cardinality=1 contention), which does not reflect real-world recording where independent
   * threads touch arbitrary series.
   */
  @State(Scope.Thread)
  public static class ThreadState {
    int[] order;

    @Setup
    public void setup(BenchmarkState benchmarkState) {
      int cardinality = benchmarkState.cardinality;
      order = new int[cardinality];
      for (int i = 0; i < cardinality; i++) {
        order[i] = i;
      }
      // Distinct seed per thread => independent permutations => no cross-thread lockstep.
      Random random =
          new Random(INITIAL_SEED + benchmarkState.threadSeedSequence.getAndIncrement());
      for (int i = cardinality - 1; i > 0; i--) {
        int j = random.nextInt(i + 1);
        int tmp = order[i];
        order[i] = order[j];
        order[j] = tmp;
      }
    }
  }

  @Benchmark
  @Group("threads1")
  @GroupThreads(1)
  @Fork(3)
  @Warmup(iterations = 3, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OperationsPerInvocation(RECORDS_PER_INVOCATION)
  public void record_SingleThread(BenchmarkState benchmarkState, ThreadState threadState) {
    record(benchmarkState, threadState);
  }

  @Benchmark
  @Group("threads" + MAX_THREADS)
  @GroupThreads(MAX_THREADS)
  @Fork(3)
  @Warmup(iterations = 3, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OperationsPerInvocation(RECORDS_PER_INVOCATION)
  public void record_MultipleThreads(BenchmarkState benchmarkState, ThreadState threadState) {
    record(benchmarkState, threadState);
  }

  static void record(BenchmarkState benchmarkState, ThreadState threadState) {
    // Per-thread series order: at a given i, different threads hit different series (no lockstep).
    int[] order = threadState.order;
    if (benchmarkState.bound) {
      // Bound path is shared between OTel and Prometheus: both resolve the series once up front
      // and record straight to the pre-resolved handle.
      List<BoundInstrument> boundInstruments = benchmarkState.boundInstruments;
      for (int i = 0; i < RECORDS_PER_INVOCATION; i++) {
        long value = benchmarkState.measurements.get(i % benchmarkState.measurements.size());
        boundInstruments.get(order[i % order.length]).record(value);
      }
    } else if (benchmarkState.prometheus) {
      PrometheusInstrument prometheusInstrument = benchmarkState.prometheusInstrument;
      for (int i = 0; i < RECORDS_PER_INVOCATION; i++) {
        String label = benchmarkState.labelValues.get(order[i % order.length]);
        long value = benchmarkState.measurements.get(i % benchmarkState.measurements.size());
        prometheusInstrument.record(value, label);
      }
    } else {
      for (int i = 0; i < RECORDS_PER_INVOCATION; i++) {
        Attributes attributes = benchmarkState.attributesList.get(order[i % order.length]);
        long value = benchmarkState.measurements.get(i % benchmarkState.measurements.size());
        benchmarkState.instrument.record(value, attributes);
      }
    }
  }

  @SuppressWarnings("ImmutableEnumChecker")
  public enum InstrumentTypeAndAggregation {
    COUNTER_SUM(COUNTER, Aggregation.sum()),
    UP_DOWN_COUNTER_SUM(UP_DOWN_COUNTER, Aggregation.sum()),
    GAUGE_LAST_VALUE(GAUGE, Aggregation.lastValue()),
    HISTOGRAM_EXPLICIT(
        HISTOGRAM,
        Aggregation.explicitBucketHistogram(
            ExplicitBucketHistogramOptions.builder().setRecordMinMax(false).build())),
    HISTOGRAM_BASE2_EXPONENTIAL(
        HISTOGRAM,
        Aggregation.base2ExponentialBucketHistogram(
            Base2ExponentialHistogramOptions.builder().setRecordMinMax(false).build()));

    InstrumentTypeAndAggregation(InstrumentType instrumentType, Aggregation aggregation) {
      this.instrumentType = instrumentType;
      this.aggregation = aggregation;
    }

    private final InstrumentType instrumentType;
    private final Aggregation aggregation;
  }

  private interface Instrument {
    void record(long value, Attributes attributes);
  }

  private interface PrometheusInstrument {
    void record(long value, String labelValue);
  }

  private static Instrument getInstrument(
      Meter meter, InstrumentType instrumentType, InstrumentValueType instrumentValueType) {
    String name = "instrument";
    switch (instrumentType) {
      case COUNTER:
        return instrumentValueType == InstrumentValueType.DOUBLE
            ? meter.counterBuilder(name).ofDoubles().build()::add
            : meter.counterBuilder(name).build()::add;
      case UP_DOWN_COUNTER:
        return instrumentValueType == InstrumentValueType.DOUBLE
            ? meter.upDownCounterBuilder(name).ofDoubles().build()::add
            : meter.upDownCounterBuilder(name).build()::add;
      case HISTOGRAM:
        return instrumentValueType == InstrumentValueType.DOUBLE
            ? meter.histogramBuilder(name).build()::record
            : meter.histogramBuilder(name).ofLongs().build()::record;
      case GAUGE:
        return instrumentValueType == InstrumentValueType.DOUBLE
            ? meter.gaugeBuilder(name).build()::set
            : meter.gaugeBuilder(name).ofLongs().build()::set;
      case OBSERVABLE_COUNTER:
      case OBSERVABLE_UP_DOWN_COUNTER:
      case OBSERVABLE_GAUGE:
    }
    throw new IllegalArgumentException();
  }

  @FunctionalInterface
  private interface BoundInstrument {
    void record(long value);
  }

  /**
   * Prometheus counterpart to {@link #getInstrument}. Uses the newer {@code
   * io.prometheus.metrics.core.metrics.*} API. Selects the Prometheus family instrument based on
   * the OTel instrument type; each record does a {@code labelValues(...)} lookup per call
   * (equivalent to OTel unbound).
   */
  @SuppressWarnings("LongDoubleConversion")
  private static PrometheusInstrument getPrometheusInstrument(InstrumentType instrumentType) {
    String name = "instrument";
    switch (instrumentType) {
      case COUNTER:
        {
          Counter counter = Counter.builder().name(name).help(name).labelNames("key").build();
          return (value, label) -> counter.labelValues(label).inc(value);
        }
      case UP_DOWN_COUNTER:
        {
          Gauge gauge = Gauge.builder().name(name).help(name).labelNames("key").build();
          return (value, label) -> gauge.labelValues(label).inc(value);
        }
      case GAUGE:
        {
          Gauge gauge = Gauge.builder().name(name).help(name).labelNames("key").build();
          return (value, label) -> gauge.labelValues(label).set(value);
        }
      case HISTOGRAM:
        {
          // Default to classic histogram (matches OTel HISTOGRAM_EXPLICIT). The exponential
          // variant would require .nativeOnly() but the OTel benchmark's default is explicit
          // buckets. Both aggregations of the OTel benchmark share this path for simplicity.
          Histogram histogram =
              Histogram.builder().name(name).help(name).labelNames("key").classicOnly().build();
          return (value, label) -> histogram.labelValues(label).observe(value);
        }
      case OBSERVABLE_COUNTER:
      case OBSERVABLE_UP_DOWN_COUNTER:
      case OBSERVABLE_GAUGE:
    }
    throw new IllegalArgumentException();
  }

  /**
   * Prometheus counterpart to {@link #bindInstruments}. Pre-resolves the {@code labelValues(...)}
   * DataPoint for each label so the record loop can call {@code observe/inc/set} directly.
   */
  @SuppressWarnings("LongDoubleConversion")
  private static List<BoundInstrument> bindPrometheusInstruments(
      InstrumentType instrumentType, List<String> labelValues) {
    String name = "instrument";
    List<BoundInstrument> result = new ArrayList<>(labelValues.size());
    switch (instrumentType) {
      case COUNTER:
        {
          Counter counter = Counter.builder().name(name).help(name).labelNames("key").build();
          for (String label : labelValues) {
            CounterDataPoint dp = counter.labelValues(label);
            result.add(dp::inc);
          }
          return result;
        }
      case UP_DOWN_COUNTER:
        {
          Gauge gauge = Gauge.builder().name(name).help(name).labelNames("key").build();
          for (String label : labelValues) {
            GaugeDataPoint dp = gauge.labelValues(label);
            result.add(dp::inc);
          }
          return result;
        }
      case GAUGE:
        {
          Gauge gauge = Gauge.builder().name(name).help(name).labelNames("key").build();
          for (String label : labelValues) {
            GaugeDataPoint dp = gauge.labelValues(label);
            result.add(dp::set);
          }
          return result;
        }
      case HISTOGRAM:
        {
          Histogram histogram =
              Histogram.builder().name(name).help(name).labelNames("key").classicOnly().build();
          for (String label : labelValues) {
            DistributionDataPoint dp = histogram.labelValues(label);
            result.add(dp::observe);
          }
          return result;
        }
      case OBSERVABLE_COUNTER:
      case OBSERVABLE_UP_DOWN_COUNTER:
      case OBSERVABLE_GAUGE:
    }
    throw new IllegalArgumentException();
  }

  /**
   * Builds the instrument, then binds one {@link BoundInstrument} per series in {@code
   * attributesList}, returned in the same order so the record loop can index it in lockstep.
   */
  private static List<BoundInstrument> bindInstruments(
      Meter meter,
      InstrumentType instrumentType,
      InstrumentValueType instrumentValueType,
      List<Attributes> attributesList) {
    boolean isDouble = instrumentValueType == InstrumentValueType.DOUBLE;
    String name = "instrument";
    List<BoundInstrument> result = new ArrayList<>(attributesList.size());
    switch (instrumentType) {
      case COUNTER:
        if (isDouble) {
          ExtendedDoubleCounter instrument =
              (ExtendedDoubleCounter) meter.counterBuilder(name).ofDoubles().build();
          for (Attributes attributes : attributesList) {
            BoundDoubleCounter bound = instrument.bind(attributes);
            result.add(bound::add);
          }
        } else {
          ExtendedLongCounter instrument = (ExtendedLongCounter) meter.counterBuilder(name).build();
          for (Attributes attributes : attributesList) {
            BoundLongCounter bound = instrument.bind(attributes);
            result.add(bound::add);
          }
        }
        return result;
      case UP_DOWN_COUNTER:
        if (isDouble) {
          ExtendedDoubleUpDownCounter instrument =
              (ExtendedDoubleUpDownCounter) meter.upDownCounterBuilder(name).ofDoubles().build();
          for (Attributes attributes : attributesList) {
            BoundDoubleUpDownCounter bound = instrument.bind(attributes);
            result.add(bound::add);
          }
        } else {
          ExtendedLongUpDownCounter instrument =
              (ExtendedLongUpDownCounter) meter.upDownCounterBuilder(name).build();
          for (Attributes attributes : attributesList) {
            BoundLongUpDownCounter bound = instrument.bind(attributes);
            result.add(bound::add);
          }
        }
        return result;
      case HISTOGRAM:
        if (isDouble) {
          ExtendedDoubleHistogram instrument =
              (ExtendedDoubleHistogram) meter.histogramBuilder(name).build();
          for (Attributes attributes : attributesList) {
            BoundDoubleHistogram bound = instrument.bind(attributes);
            result.add(bound::record);
          }
        } else {
          ExtendedLongHistogram instrument =
              (ExtendedLongHistogram) meter.histogramBuilder(name).ofLongs().build();
          for (Attributes attributes : attributesList) {
            BoundLongHistogram bound = instrument.bind(attributes);
            result.add(bound::record);
          }
        }
        return result;
      case GAUGE:
        if (isDouble) {
          ExtendedDoubleGauge instrument = (ExtendedDoubleGauge) meter.gaugeBuilder(name).build();
          for (Attributes attributes : attributesList) {
            BoundDoubleGauge bound = instrument.bind(attributes);
            result.add(bound::set);
          }
        } else {
          ExtendedLongGauge instrument =
              (ExtendedLongGauge) meter.gaugeBuilder(name).ofLongs().build();
          for (Attributes attributes : attributesList) {
            BoundLongGauge bound = instrument.bind(attributes);
            result.add(bound::set);
          }
        }
        return result;
      case OBSERVABLE_COUNTER:
      case OBSERVABLE_UP_DOWN_COUNTER:
      case OBSERVABLE_GAUGE:
    }
    throw new IllegalArgumentException();
  }

  /**
   * Thrown from {@link BenchmarkState#setup} to skip param combinations that are invalid or
   * duplicate for a given backend (e.g. Prometheus with {@code aggregationTemporality=DELTA}).
   * JMH treats a setup exception as a failed trial and omits it from aggregated results, which
   * is the desired effect here.
   */
  static final class SkipInvalidCombo extends RuntimeException {
    private static final long serialVersionUID = 1L;

    SkipInvalidCombo(String message) {
      super(message);
    }
  }
}
