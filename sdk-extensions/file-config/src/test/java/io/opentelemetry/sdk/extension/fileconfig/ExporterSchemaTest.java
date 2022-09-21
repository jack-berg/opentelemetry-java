/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.fileconfig;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import org.junit.jupiter.api.Test;

class ExporterSchemaTest {

  private static final YamlJsonSchemaValidator validator =
      new YamlJsonSchemaValidator(
          new File(System.getProperty("otel.sdk-schema-dir")),
          "https://opentelemetry.io/schemas/sdkconfig",
          new File(System.getProperty("otel.sdk-schema-dir") + "/exporter.json"),
          "/exporter");

  @Test
  void otlpAllFields() {
    assertThat(validator.validate("otlp-all-fields.yaml")).isEmpty();
  }

  @Test
  void otlpInvalidTypes() {
    assertThat(validator.validate("otlp-invalid-types.yaml"))
        .containsExactlyInAnyOrder(
            "$.args.endpoint: integer found, string expected",
            "$.args.insecure: integer found, boolean expected",
            "$.args.certificate: integer found, string expected",
            "$.args.client_key: integer found, string expected",
            "$.args.client_certificate: integer found, string expected",
            "$.args.headers: integer found, object expected",
            "$.args.compression: integer found, string expected",
            "$.args.timeout: integer found, string expected",
            "$.args.protocol: integer found, string expected");
  }

  @Test
  void zipkinAllFields() {
    assertThat(validator.validate("zipkin-all-fields.yaml")).isEmpty();
  }

  @Test
  void zipkinInvalidTypes() {
    assertThat(validator.validate("zipkin-invalid-types.yaml"))
        .containsExactlyInAnyOrder(
            "$.args.endpoint: integer found, string expected",
            "$.args.timeout: integer found, string expected");
  }

  @Test
  void jaegerAllFields() {
    assertThat(validator.validate("jaeger-all-fields.yaml")).isEmpty();
  }

  @Test
  void jaegerInvalidTypes() {
    assertThat(validator.validate("jaeger-invalid-types.yaml"))
        .containsExactlyInAnyOrder(
            "$.args.protocol: integer found, string expected",
            "$.args.endpoint: integer found, string expected",
            "$.args.timeout: integer found, string expected",
            "$.args.user: integer found, string expected",
            "$.args.agent_host: integer found, string expected",
            "$.args.agent_port: integer found, string expected");
  }
}
