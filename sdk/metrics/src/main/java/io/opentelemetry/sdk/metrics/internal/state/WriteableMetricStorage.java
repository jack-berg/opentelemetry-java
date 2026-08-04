/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;
import io.opentelemetry.sdk.metrics.data.MetricData;

/**
 * Stores {@link MetricData} and allows synchronous writes of measurements.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface WriteableMetricStorage {

  /** Records a measurement. */
  void recordLong(long value, Attributes attributes, Context context);

  /** Records a measurement. */
  void recordDouble(double value, Attributes attributes, Context context);

  /**
   * Binds the given {@code attributes}, returning a {@link BoundStorageHandle} that records to the
   * corresponding timeseries directly. The series is resolved once here, so subsequent records via
   * the returned handle skip per-recording attribute processing and series lookup.
   */
  BoundStorageHandle bind(Attributes attributes);

  /**
   * Returns {@code true} if the storage is actively recording measurements, and {@code false}
   * otherwise (i.e. noop / empty metric storage is installed).
   */
  boolean isEnabled();

  /**
   * Returns {@code true} if the storage should record the given double measurement, {@code false}
   * to drop it. Combines {@link #isEnabled()} with a NaN check (logged). Used by bound-instrument
   * record paths to gate before invoking a {@link BoundStorageHandle}.
   */
  boolean shouldRecordDouble(double value, Attributes attributes);
}
