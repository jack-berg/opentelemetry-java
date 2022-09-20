/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.fileconfig;

import org.junit.jupiter.api.Test;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

class ViewEntrySchemaTest {

  private static final YamlJsonSchemaValidator validator =
      new YamlJsonSchemaValidator(
          new File(System.getProperty("otel.sdk-schema-dir")),
          "https://opentelemetry.io/schemas/sdkconfig",
          new File(System.getProperty("otel.sdk-schema-dir") + "/view_entry.json"),
              "/view_entry");

  @Test
  void allFields() {
    assertThat(validator.validate("all-fields.yaml")).isEmpty();
  }

  @Test
  void invalidTypes() {
    assertThat(validator.validate("invalid-types.yaml"))
        .containsExactlyInAnyOrder(
            "$.selector.instrument_name: integer found, string expected",
            "$.selector.instrument_type: integer found, string expected",
            "$.selector.meter_name: integer found, string expected",
            "$.selector.meter_version: integer found, string expected",
            "$.selector.meter_schema_url: integer found, string expected",
            "$.view.name: integer found, string expected",
            "$.view.description: integer found, string expected",
            "$.view.aggregation.name: integer found, string expected",
            "$.view.aggregation.args: integer found, object expected");
  }
}
