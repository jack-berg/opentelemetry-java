/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.processcontext.internal;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.AutoConfigureListener;

/**
 * Publishes process context after declarative configuration.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class ProcessContextDeclarativeConfigListener
    implements DeclarativeConfigurationCustomizerProvider, AutoConfigureListener {

  private final ProcessContextAutoConfigureListener listener =
      new ProcessContextAutoConfigureListener();

  @Override
  public void customize(DeclarativeConfigurationCustomizer customizer) {}

  @Override
  public void afterAutoConfigure(OpenTelemetrySdk sdk) {
    listener.afterAutoConfigure(sdk);
  }
}
