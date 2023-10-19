/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

import io.opentelemetry.context.Context;

/** A SpanProcessor implementation that is only capable of processing spans when they end. */
@FunctionalInterface
public interface OnEndSpanProcessor extends SpanProcessor {

  static <T extends OnEndSpanProcessor> T of(T processor) {
    return processor;
  }

  @Override
  default boolean isEndRequired() {
    return true;
  }

  @Override
  default void onStart(Context parentContext, ReadWriteSpan span) {
    // nop
  }

  @Override
  default boolean isStartRequired() {
    return false;
  }
}
