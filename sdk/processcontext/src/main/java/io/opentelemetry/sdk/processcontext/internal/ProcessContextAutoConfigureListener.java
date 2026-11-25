/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.processcontext.internal;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;
import io.opentelemetry.sdk.autoconfigure.spi.internal.AutoConfigureListener;
import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.internal.ExtendedOpenTelemetrySdk;
import io.opentelemetry.sdk.processcontext.ProcessContextPublisher;
import io.opentelemetry.sdk.processcontext.data.ProcessContextData;
import io.opentelemetry.sdk.resources.internal.SdkResourceProvider;
import java.io.IOException;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * SPI integration that publishes process context after {@link OpenTelemetrySdk} autoconfiguration.
 *
 * <p>Registered as an {@link AutoConfigurationCustomizerProvider} so autoconfigure discovers it via
 * {@link java.util.ServiceLoader}; {@link AutoConfigureListener} is piggybacked on the same load
 * (see {@code SpiHelper#maybeAddListener}).
 *
 * <p>If more than one {@link OpenTelemetrySdk} is autoconfigured in the same JVM, each invocation
 * of {@link #afterAutoConfigure} will publish on the shared process-wide {@link
 * ProcessContextPublisher} singleton. The last caller's resource wins (OTEP-4719 mandates a single
 * process context per process).
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
// TODO(jack-berg): should we have other mechanisms to detect / prevent publishing multiple process
// contexts?
public final class ProcessContextAutoConfigureListener
    implements AutoConfigurationCustomizerProvider, AutoConfigureListener {

  private static final Logger logger =
      Logger.getLogger(ProcessContextAutoConfigureListener.class.getName());

  @Override
  public void customize(AutoConfigurationCustomizer autoConfiguration) {
    // No customization needed; implemented only so autoconfigure discovers us via SPI and the
    // AutoConfigureListener piggyback can fire.
  }

  @Override
  public void afterAutoConfigure(OpenTelemetrySdk sdk) {
    Objects.requireNonNull(sdk, "sdk");

    if (!(sdk instanceof ExtendedOpenTelemetrySdk)) {
      throw new IllegalStateException(
          "Expected ExtendedOpenTelemetrySdk, got " + sdk.getClass().getName());
    }
    SdkResourceProvider sdkResourceProvider =
        ((ExtendedOpenTelemetrySdk) sdk).getSdkResourceProvider();

    ProcessContextData processContextData =
        SdkProcessContextData.create(sdkResourceProvider.getResource(), Attributes.empty());

    try {
      ProcessContextPublisher.getInstance().publish(processContextData, Clock.getDefault());
    } catch (IOException e) {
      logger.log(Level.WARNING, "Failed to publish process context", e);
    }
  }
}
