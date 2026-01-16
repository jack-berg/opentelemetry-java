/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.sender.internal;

import io.opentelemetry.exporter.sender.GrpcSenderConfig;
import javax.annotation.Nullable;

/**
 * Extended {@link GrpcSenderConfig} with internal / experimental APIs.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public interface ExtendedGrpcSenderConfig extends GrpcSenderConfig {

  @Nullable
  Object getMangedChannel();
}
