/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import static io.opentelemetry.sdk.common.export.MemoryMode.REUSABLE_DATA;
import static io.opentelemetry.sdk.metrics.data.AggregationTemporality.DELTA;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.metrics.data.MetricData;
import io.opentelemetry.sdk.metrics.data.PointData;
import io.opentelemetry.sdk.metrics.internal.aggregator.Aggregator;
import io.opentelemetry.sdk.metrics.internal.aggregator.AggregatorHandle;
import io.opentelemetry.sdk.metrics.internal.aggregator.EmptyMetricData;
import io.opentelemetry.sdk.metrics.internal.descriptor.MetricDescriptor;
import io.opentelemetry.sdk.metrics.internal.export.RegisteredReader;
import io.opentelemetry.sdk.metrics.internal.view.AttributesProcessor;
import io.opentelemetry.sdk.resources.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

class DeltaSynchronousMetricStorage<T extends PointData>
    extends DefaultSynchronousMetricStorage<T> {
  private final long instrumentCreationEpochNanos;
  private final RegisteredReader registeredReader;
  private final MemoryMode memoryMode;

  private final ConcurrentHashMap<Attributes, DeltaAggregatorHandle<T>> deltaHandles =
      new ConcurrentHashMap<>();
  // Only populated if memoryMode == REUSABLE_DATA
  private final ArrayList<T> reusableResultList = new ArrayList<>();

  DeltaSynchronousMetricStorage(
      RegisteredReader registeredReader,
      MetricDescriptor metricDescriptor,
      Aggregator<T> aggregator,
      AttributesProcessor attributesProcessor,
      Clock clock,
      int maxCardinality,
      boolean enabled) {
    super(metricDescriptor, aggregator, attributesProcessor, clock, maxCardinality, enabled);
    this.instrumentCreationEpochNanos = clock.now();
    this.registeredReader = registeredReader;
    this.memoryMode = registeredReader.getReader().getMemoryMode();
  }

  @Override
  void doRecordLong(long value, Attributes attributes, Context context) {
    while (true) {
      DeltaAggregatorHandle<T> deltaHandle = getDeltaAggregatorHandle(attributes, context);
      int count = deltaHandle.activeRecordingThreads.addAndGet(2);
      if (count % 2 == 0) {
        try {
          deltaHandle.handle.recordLong(value, attributes, context);
        } finally {
          deltaHandle.activeRecordingThreads.addAndGet(-2);
        }
        return;
      }
      // Handle is being collected; release and re-read the map to retry
      deltaHandle.activeRecordingThreads.addAndGet(-2);
    }
  }

  @Override
  void doRecordDouble(double value, Attributes attributes, Context context) {
    while (true) {
      DeltaAggregatorHandle<T> deltaHandle = getDeltaAggregatorHandle(attributes, context);
      int count = deltaHandle.activeRecordingThreads.addAndGet(2);
      if (count % 2 == 0) {
        try {
          deltaHandle.handle.recordDouble(value, attributes, context);
        } finally {
          deltaHandle.activeRecordingThreads.addAndGet(-2);
        }
        return;
      }
      deltaHandle.activeRecordingThreads.addAndGet(-2);
    }
  }

  @Override
  public MetricData collect(
      Resource resource, InstrumentationScopeInfo instrumentationScopeInfo, long epochNanos) {
    // Snapshot the handles to process this cycle. Handles added after this snapshot
    // were not yet recording when collection began and will be picked up next cycle.
    List<Map.Entry<Attributes, DeltaAggregatorHandle<T>>> entries =
        new ArrayList<>(deltaHandles.entrySet());

    // Pass 1: signal all handles that collection is starting by making activeRecordingThreads odd.
    // Recorders that see an odd count know to release and retry.
    for (Map.Entry<Attributes, DeltaAggregatorHandle<T>> entry : entries) {
      entry.getValue().activeRecordingThreads.addAndGet(1);
    }

    List<T> points;
    if (memoryMode == REUSABLE_DATA) {
      reusableResultList.clear();
      points = reusableResultList;
    } else {
      points = new ArrayList<>(entries.size());
    }

    long startEpochNanos =
        registeredReader.getLastCollectEpochNanosOrDefault(instrumentCreationEpochNanos);
    boolean atCapacity = deltaHandles.size() >= maxCardinality;

    // Pass 2: for each locked handle, wait for in-flight recordings to finish, then collect.
    for (Map.Entry<Attributes, DeltaAggregatorHandle<T>> entry : entries) {
      Attributes attrs = entry.getKey();
      DeltaAggregatorHandle<T> deltaHandle = entry.getValue();

      // Wait until all active recordings on this handle complete (count drops to 1 = lock bit only)
      while (deltaHandle.activeRecordingThreads.get() > 1) {
        // spin
      }

      // When at capacity, evict handles that recorded no values this cycle to make room for new
      // series next cycle. Handles removed here will be recreated on their next recording.
      if (atCapacity && !deltaHandle.handle.hasRecordedValues()) {
        deltaHandles.remove(attrs, deltaHandle);
        deltaHandle.activeRecordingThreads.addAndGet(-1);
        continue;
      }

      if (!deltaHandle.handle.hasRecordedValues()) {
        deltaHandle.activeRecordingThreads.addAndGet(-1);
        continue;
      }

      T point =
          deltaHandle.handle.aggregateThenMaybeReset(
              startEpochNanos, epochNanos, attrs, /* reset= */ true);
      deltaHandle.activeRecordingThreads.addAndGet(-1);

      if (point != null) {
        points.add(point);
      }
    }

    if (points.isEmpty() || !enabled) {
      return EmptyMetricData.getInstance();
    }

    return aggregator.toMetricData(
        resource, instrumentationScopeInfo, metricDescriptor, points, DELTA);
  }

  private DeltaAggregatorHandle<T> getDeltaAggregatorHandle(
      Attributes attributes, Context context) {
    Objects.requireNonNull(attributes, "attributes");
    attributes = attributesProcessor.process(attributes, context);
    DeltaAggregatorHandle<T> handle = deltaHandles.get(attributes);
    if (handle != null) {
      return handle;
    }
    if (deltaHandles.size() >= maxCardinality) {
      // Try to evict a stale series (reset after last collection, not yet re-recorded) to make
      // room for this new one. The overflow series is never evicted — only regular series.
      boolean evicted = false;
      for (Map.Entry<Attributes, DeltaAggregatorHandle<T>> e : deltaHandles.entrySet()) {
        if (e.getKey().equals(MetricStorage.CARDINALITY_OVERFLOW)) {
          continue;
        }
        DeltaAggregatorHandle<T> candidate = e.getValue();
        if (!candidate.handle.hasRecordedValues()
            && candidate.activeRecordingThreads.get() == 0
            && deltaHandles.remove(e.getKey(), candidate)) {
          evicted = true;
          break;
        }
      }
      if (!evicted) {
        logger.log(
            Level.WARNING,
            "Instrument "
                + metricDescriptor.getSourceInstrument().getName()
                + " has exceeded the maximum allowed cardinality ("
                + maxCardinality
                + ").");
        attributes = MetricStorage.CARDINALITY_OVERFLOW;
        handle = deltaHandles.get(attributes);
        if (handle != null) {
          return handle;
        }
      }
    }
    DeltaAggregatorHandle<T> newHandle =
        new DeltaAggregatorHandle<>(aggregator.createHandle(clock.now()));
    DeltaAggregatorHandle<T> existing = deltaHandles.putIfAbsent(attributes, newHandle);
    return existing != null ? existing : newHandle;
  }

  private static final class DeltaAggregatorHandle<T extends PointData> {
    final AggregatorHandle<T> handle;
    // Uses the same even/odd protocol as the former AggregatorHolder.activeRecordingThreads,
    // but scoped to a single series instead of the entire map:
    //   - Recording threads increment by 2 before recording, decrement by 2 when done.
    //   - The collect thread increments by 1 (making the count odd) as a signal that this
    //     handle is being collected; recorders that observe an odd count release and retry.
    //   - Once all in-flight recordings finish the count returns to 1, and the collect
    //     thread decrements by 1 to restore it to even for the next cycle.
    final AtomicInteger activeRecordingThreads = new AtomicInteger(0);

    DeltaAggregatorHandle(AggregatorHandle<T> handle) {
      this.handle = handle;
    }
  }
}
