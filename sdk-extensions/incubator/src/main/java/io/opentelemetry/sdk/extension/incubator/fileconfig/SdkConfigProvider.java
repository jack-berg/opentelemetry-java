/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import io.opentelemetry.api.incubator.config.ConfigProvider;
import io.opentelemetry.api.incubator.config.DeclarativeConfigProperties;
import io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model.OpenTelemetryConfiguration;
import javax.annotation.Nullable;

/** SDK implementation of {@link ConfigProvider}. */
public final class SdkConfigProvider implements ConfigProvider {

  @Nullable private final DeclarativeConfigProperties instrumentationConfig;

  private SdkConfigProvider(OpenTelemetryConfiguration model) {
    DeclarativeConfigProperties configProperties =
        DeclarativeConfiguration.toConfigProperties(model);
    this.instrumentationConfig = configProperties.getStructured("instrumentation");
  }

  /**
   * Create a {@link SdkConfigProvider} from the {@code model}.
   *
   * @param model the configuration model
   * @return the {@link SdkConfigProvider}
   */
  public static SdkConfigProvider create(OpenTelemetryConfiguration model) {
    return new SdkConfigProvider(model);
  }

  @Nullable
  @Override
  public DeclarativeConfigProperties getInstrumentationConfig() {
    return instrumentationConfig;
  }
}
