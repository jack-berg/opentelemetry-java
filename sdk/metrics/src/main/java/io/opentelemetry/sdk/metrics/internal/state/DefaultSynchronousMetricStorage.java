/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import static io.opentelemetry.sdk.common.export.MemoryMode.IMMUTABLE_DATA;
import static io.opentelemetry.sdk.common.export.MemoryMode.REUSABLE_DATA;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.common.export.MemoryMode;
import io.opentelemetry.sdk.internal.ThrottlingLogger;
import io.opentelemetry.sdk.metrics.data.AggregationTemporality;
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
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores aggregated {@link MetricData} for synchronous instruments.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class DefaultSynchronousMetricStorage<T extends PointData>
    implements SynchronousMetricStorage {

  private static final Logger internalLogger =
      Logger.getLogger(DefaultSynchronousMetricStorage.class.getName());

  private final ThrottlingLogger logger = new ThrottlingLogger(internalLogger);
  private final RegisteredReader registeredReader;
  private final MetricDescriptor metricDescriptor;
  private final AggregationTemporality aggregationTemporality;
  private final Aggregator<T> aggregator;
  private volatile ConcurrentHashMap<Attributes, AggregatorHandle<T>> collectionAggregatorHandles = new ConcurrentHashMap<>();
  private final StripedLock stripedLock = new StripedLock();
  private final AttributesProcessor attributesProcessor;

  private final MemoryMode memoryMode;

  // Only populated if memoryMode == REUSABLE_DATA
  private final ArrayList<T> reusableResultList = new ArrayList<>();

  // Only populated if memoryMode == REUSABLE_DATA and
  // aggregationTemporality is DELTA
  private volatile ConcurrentHashMap<Attributes, AggregatorHandle<T>>
      previousCollectionAggregatorHandles = new ConcurrentHashMap<>();

  /**
   * This field is set to 1 less than the actual intended cardinality limit, allowing the last slot
   * to be filled by the {@link MetricStorage#CARDINALITY_OVERFLOW} series.
   */
  private final int maxCardinality;

  private final ConcurrentLinkedQueue<AggregatorHandle<T>> aggregatorHandlePool =
      new ConcurrentLinkedQueue<>();

  private volatile boolean enabled;
  private final boolean requiresSynchronization;

  DefaultSynchronousMetricStorage(
      RegisteredReader registeredReader,
      MetricDescriptor metricDescriptor,
      Aggregator<T> aggregator,
      AttributesProcessor attributesProcessor,
      int maxCardinality,
      boolean enabled) {
    this.registeredReader = registeredReader;
    this.metricDescriptor = metricDescriptor;
    this.aggregationTemporality =
        registeredReader
            .getReader()
            .getAggregationTemporality(metricDescriptor.getSourceInstrument().getType());
    this.aggregator = aggregator;
    this.attributesProcessor = attributesProcessor;
    this.maxCardinality = maxCardinality - 1;
    this.memoryMode = registeredReader.getReader().getMemoryMode();
    this.enabled = enabled;
    this.requiresSynchronization = aggregationTemporality == AggregationTemporality.DELTA || aggregator.requiresSynchronization();
  }

  // Visible for testing
  Queue<AggregatorHandle<T>> getAggregatorHandlePool() {
    return aggregatorHandlePool;
  }

  @Override
  public void recordLong(long value, Attributes attributes, Context context) {
    if (!enabled) {
      return;
    }
    if (requiresSynchronization) {
      ReentrantLock forThread = stripedLock.getForThread();
      forThread.lock();
      try {
        AggregatorHandle<T> handle =
            getAggregatorHandle(this.collectionAggregatorHandles, attributes, context);
        handle.recordLong(value, attributes, context);
      } finally {
        forThread.unlock();
      }
    } else {
      AggregatorHandle<T> handle =
          getAggregatorHandle(this.collectionAggregatorHandles, attributes, context);
      handle.recordLong(value, attributes, context);
    }
  }

  @Override
  public void recordDouble(double value, Attributes attributes, Context context) {
    if (!enabled) {
      return;
    }
    if (Double.isNaN(value)) {
      logger.log(
          Level.FINE,
          "Instrument "
              + metricDescriptor.getSourceInstrument().getName()
              + " has recorded measurement Not-a-Number (NaN) value with attributes "
              + attributes
              + ". Dropping measurement.");
      return;
    }
    if (requiresSynchronization) {
      ReentrantLock forThread = stripedLock.getForThread();
      forThread.lock();
      try {
        AggregatorHandle<T> handle =
            getAggregatorHandle(this.collectionAggregatorHandles, attributes, context);
        handle.recordDouble(value, attributes, context);
      } finally {
        forThread.unlock();
      }
    } else {
      AggregatorHandle<T> handle =
          getAggregatorHandle(this.collectionAggregatorHandles, attributes, context);
      handle.recordDouble(value, attributes, context);
    }
  }

  @Override
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  private AggregatorHandle<T> getAggregatorHandle(
      ConcurrentHashMap<Attributes, AggregatorHandle<T>> aggregatorHandles,
      Attributes attributes,
      Context context) {
    Objects.requireNonNull(attributes, "attributes");
    attributes = attributesProcessor.process(attributes, context);
    AggregatorHandle<T> handle = aggregatorHandles.get(attributes);
    if (handle != null) {
      return handle;
    }
    if (aggregatorHandles.size() >= maxCardinality) {
      logger.log(
          Level.WARNING,
          "Instrument "
              + metricDescriptor.getSourceInstrument().getName()
              + " has exceeded the maximum allowed cardinality ("
              + maxCardinality
              + ").");
      // Return handle for overflow series, first checking if a handle already exists for it
      attributes = MetricStorage.CARDINALITY_OVERFLOW;
      handle = aggregatorHandles.get(attributes);
      if (handle != null) {
        return handle;
      }
    }
    // Get handle from pool if available, else create a new one.
    AggregatorHandle<T> newHandle = aggregatorHandlePool.poll();
    if (newHandle == null) {
      newHandle = aggregator.createHandle();
    }
    handle = aggregatorHandles.putIfAbsent(attributes, newHandle);
    return handle != null ? handle : newHandle;
  }

  @Override
  public MetricData collect(
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      long startEpochNanos,
      long epochNanos) {
    if (aggregationTemporality == AggregationTemporality.DELTA) {
      return collectDelta(resource, instrumentationScopeInfo, startEpochNanos, epochNanos);
    } else {
      return collectCumulative(resource, instrumentationScopeInfo, startEpochNanos, epochNanos);
    }
  }

  public MetricData collectDelta(
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      long startEpochNanos,
      long epochNanos) {
    boolean reset = true;
    long start = registeredReader.getLastCollectEpochNanos();

    stripedLock.lockAll();
    try {
      ConcurrentHashMap<Attributes, AggregatorHandle<T>> aggregatorHandles =
          this.collectionAggregatorHandles;
      this.collectionAggregatorHandles = memoryMode == REUSABLE_DATA ? previousCollectionAggregatorHandles : new ConcurrentHashMap<>();

      List<T> points;
      if (memoryMode == REUSABLE_DATA) {
        reusableResultList.clear();
        points = reusableResultList;
      } else {
        points = new ArrayList<>(aggregatorHandles.size());
      }

      // In DELTA aggregation temporality each Attributes is reset to 0
      // every time we perform a collection (by definition of DELTA).
      // In IMMUTABLE_DATA MemoryMode, this is accomplished by removing all aggregator handles
      // (into which the values are recorded) effectively starting from 0
      // for each recorded Attributes.
      // In REUSABLE_DATA MemoryMode, we strive for zero allocations. Since even removing
      // a key-value from a map and putting it again on next recording will cost an allocation,
      // we are keeping the aggregator handles in their map, and only reset their value once
      // we finish collecting the aggregated value from each one.
      // The SDK must adhere to keeping no more than maxCardinality unique Attributes in memory,
      // hence during collect(), when the map is at full capacity, we try to clear away unused
      // aggregator handles, so on next recording cycle using this map, there will be room for newly
      // recorded Attributes. This comes at the expanse of memory allocations. This can be avoided
      // if the user chooses to increase the maxCardinality.
      if (memoryMode == REUSABLE_DATA) {
        if (aggregatorHandles.size() >= maxCardinality) {
          aggregatorHandles.forEach(
              (attribute, handle) -> {
                if (!handle.hasRecordedValues()) {
                  aggregatorHandles.remove(attribute);
                }
              });
        }
      }

      // Grab aggregated points.
      aggregatorHandles.forEach(
          (attributes, handle) -> {
            if (!handle.hasRecordedValues()) {
              return;
            }
            T point = handle.aggregateThenMaybeReset(start, epochNanos, attributes, reset);

            if (memoryMode == IMMUTABLE_DATA) {
              // Return the aggregator to the pool.
              // The pool is only used in DELTA temporality (since in CUMULATIVE the handler is
              // always used as it is the place accumulating the values and never resets)
              // AND only in IMMUTABLE_DATA memory mode since in REUSABLE_DATA we avoid
              // using the pool since it allocates memory internally on each put() or remove()
              aggregatorHandlePool.offer(handle);
            }

            if (point != null) {
              points.add(point);
            }
          });

      // Trim pool down if needed. pool.size() will only exceed maxCardinality if new handles are
      // created during collection.
      int toDelete = aggregatorHandlePool.size() - (maxCardinality + 1);
      for (int i = 0; i < toDelete; i++) {
        aggregatorHandlePool.poll();
      }

      if (memoryMode == REUSABLE_DATA) {
        previousCollectionAggregatorHandles = aggregatorHandles;
      }

      if (points.isEmpty() || !enabled) {
        return EmptyMetricData.getInstance();
      }

      return aggregator.toMetricData(
          resource, instrumentationScopeInfo, metricDescriptor, points, aggregationTemporality);
    } finally {
      stripedLock.unlockAll();
    }
  }

  public MetricData collectCumulative(
      Resource resource,
      InstrumentationScopeInfo instrumentationScopeInfo,
      long startEpochNanos,
      long epochNanos) {
    boolean reset = false;
    long start = startEpochNanos;

    if (requiresSynchronization) {
      stripedLock.lockAll();
    }
    ConcurrentHashMap<Attributes, AggregatorHandle<T>> aggregatorHandles =
        this.collectionAggregatorHandles;

    List<T> points;
    if (memoryMode == REUSABLE_DATA) {
      reusableResultList.clear();
      points = reusableResultList;
    } else {
      points = new ArrayList<>(aggregatorHandles.size());
    }

    // Grab aggregated points.
    aggregatorHandles.forEach(
        (attributes, handle) -> {
          if (!handle.hasRecordedValues()) {
            return;
          }
          T point = handle.aggregateThenMaybeReset(start, epochNanos, attributes, reset);

          if (point != null) {
            points.add(point);
          }
        });

    // Trim pool down if needed. pool.size() will only exceed maxCardinality if new handles are
    // created during collection.
    int toDelete = aggregatorHandlePool.size() - (maxCardinality + 1);
    for (int i = 0; i < toDelete; i++) {
      aggregatorHandlePool.poll();
    }

    if (points.isEmpty() || !enabled) {
      return EmptyMetricData.getInstance();
    }

    MetricData metricData = aggregator.toMetricData(
        resource, instrumentationScopeInfo, metricDescriptor, points, aggregationTemporality);

    if (requiresSynchronization) {
      stripedLock.unlockAll();
    }

    return metricData;
  }

  @Override
  public MetricDescriptor getMetricDescriptor() {
    return metricDescriptor;
  }

  private static class StripedLock {
    private final ReentrantLock[] locks;

    private StripedLock() {
      locks = new ReentrantLock[Runtime.getRuntime().availableProcessors()];
      for (int i = 0; i < locks.length; i++) {
        locks[i] = new ReentrantLock();
      }
    }

    private ReentrantLock getForThread() {
      return locks[((int) Thread.currentThread().getId()) % locks.length];
    }

    private void lockAll() {
      for (ReentrantLock lock : locks) {
        lock.lock();
      }
    }

    private void unlockAll() {
      for (ReentrantLock lock : locks) {
        lock.unlock();
      }
    }
  }
}
