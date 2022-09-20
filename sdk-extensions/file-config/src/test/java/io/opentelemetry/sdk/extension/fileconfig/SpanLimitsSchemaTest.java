/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.fileconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;

import org.junit.jupiter.api.Test;

class SpanLimitsSchemaTest {

  private static final YamlJsonSchemaValidator validator =
      new YamlJsonSchemaValidator(
          new File(System.getProperty("otel.sdk-schema-dir")),
          "https://opentelemetry.io/schemas/sdkconfig",
          new File(System.getProperty("otel.sdk-schema-dir") + "/span_limits.json"),
          "/span_limits");

  @Test
  void allFields() {
    assertThat(validator.validate("all-fields.yaml")).isEmpty();
  }

  @Test
  void invalidTypes() {
    assertThat(validator.validate("invalid-types.yaml"))
        .containsExactlyInAnyOrder(
            "$.attribute_count_limit: string found, integer expected",
            "$.attribute_value_length_limit: string found, integer expected",
            "$.attribute_count_per_event_limit: string found, integer expected",
            "$.attribute_count_per_link_limit: string found, integer expected",
            "$.event_count_limit: string found, integer expected",
            "$.link_count_limit: string found, integer expected");
  }
}
