/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.logs;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import javax.annotation.Nullable;

public final class Loopback {

  private static final String loopbackKey = "otel.loopback";

  private Loopback() {}

  public static final AttributeKey<Boolean> loopbackAttribute =
      AttributeKey.booleanKey(loopbackKey);
  public static final ContextKey<Boolean> loopbackContextKey = ContextKey.named(loopbackKey);

  public static Context withLoopback(Context context) {
    return context.with(loopbackContextKey, true);
  }

  public static AttributesBuilder withLoopback(AttributesBuilder attributes) {
    return attributes.put(loopbackAttribute, true);
  }

  public static boolean isLoopback(@Nullable Attributes attributes) {
    if (attributes == null) {
      return false;
    }
    Boolean value = attributes.get(loopbackAttribute);
    return value != null && value;
  }

  public static boolean isLoopback(Context context) {
    Boolean value = context.get(loopbackContextKey);
    return value != null && value;
  }
}
