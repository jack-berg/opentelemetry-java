/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.context.Context;

/**
 * A record target for a single timeseries, resolved once via {@link
 * WriteableMetricStorage#bind(io.opentelemetry.api.common.Attributes)}.
 *
 * <p>Records bypass the per-recording attribute processing and series lookup that {@link
 * WriteableMetricStorage#recordLong} / {@link WriteableMetricStorage#recordDouble} perform, since
 * the series is resolved at bind time. Backs bound instruments.
 *
 * <p>{@code attributes} is passed on every record because distinct bindings can collapse to the
 * same underlying handle (via attribute processor collapse or cardinality overflow); each binding
 * supplies its own original attributes for exemplar sampling. The bound instrument is expected to
 * check {@link WriteableMetricStorage#isEnabled()} (and NaN for doubles) before invoking.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface BoundStorageHandle {

  /** Records a long measurement against the bound series. */
  void recordLong(long value, Attributes attributes, Context context);

  /** Records a double measurement against the bound series. */
  void recordDouble(double value, Attributes attributes, Context context);
}
