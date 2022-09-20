/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class SdkSchemaTest {

  private static final YamlJsonSchemaValidator validator =
      new YamlJsonSchemaValidator(
          new File(System.getProperty("otel.sdk-schema-dir")),
          "https://opentelemetry.io/schemas/sdkconfig",
          new File(System.getProperty("otel.sdk-schema-dir") + "/sdk.json"),
          "/sdk-schema/sdk");

  @Test
  void kitchenSink() {
    assertThat(validator.validate("kitchen-sink.yaml")).isEmpty();
  }

  @Test
  void simple() {
    assertThat(validator.validate("simple.yaml")).isEmpty();
  }

}
