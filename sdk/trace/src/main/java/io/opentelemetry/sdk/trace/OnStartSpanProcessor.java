/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.trace;

/** A SpanProcessor that only handles onStart(). */
@FunctionalInterface
public interface OnStartSpanProcessor extends SpanProcessor {

  static <T extends OnStartSpanProcessor> T of(T processor) {
    return processor;
  }

  @Override
  default boolean isStartRequired() {
    return true;
  }

  @Override
  default void onEnd(ReadableSpan span) {
    // nop
  }

  @Override
  default boolean isEndRequired() {
    return false;
  }
}
