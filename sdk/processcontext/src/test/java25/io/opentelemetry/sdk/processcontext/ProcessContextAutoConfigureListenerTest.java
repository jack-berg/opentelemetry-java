/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.processcontext;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.AutoConfiguredOpenTelemetrySdk;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfiguration;
import io.opentelemetry.sdk.autoconfigure.declarativeconfig.model.OpenTelemetryConfigurationModel;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.LINUX)
class ProcessContextAutoConfigureListenerTest {

  @AfterEach
  void cleanupPublishedMapping() throws IOException {
    // The publisher singleton is reused across tests; close it so the OTEL_CTX VMA doesn't leak
    // into unrelated tests in the same JVM.
    ProcessContextPublisher.getInstance().close();
  }

  @Test
  void autoConfigurePublishesContext() throws IOException {
    // Depends on otel.experimental.process.context.enabled=true being set at JVM start (see
    // build.gradle.kts java25Test suite) so the factory selects the Panama publisher.
    assertThat(ProcessContextPublisher.getInstance())
        .isNotInstanceOf(NoopProcessContextPublisher.class);

    assertThat(containsMappingWith("OTEL_CTX")).isFalse();

    Map<String, String> props = new HashMap<>();
    props.put("otel.traces.exporter", "none");
    props.put("otel.metrics.exporter", "none");
    props.put("otel.logs.exporter", "none");
    OpenTelemetrySdk sdk =
        AutoConfiguredOpenTelemetrySdk.builder()
            .addPropertiesSupplier(() -> props)
            .build()
            .getOpenTelemetrySdk();

    assertThat(sdk).isNotNull();
    assertThat(containsMappingWith("OTEL_CTX")).isTrue();
  }

  @Test
  void declarativeConfigPublishesContext() throws IOException {
    assertThat(containsMappingWith("OTEL_CTX")).isFalse();

    try (OpenTelemetrySdk sdk =
        DeclarativeConfiguration.create(new OpenTelemetryConfigurationModel().setFileFormat("1.2"))
            .getSdk()) {
      assertThat(sdk).isNotNull();
      assertThat(containsMappingWith("OTEL_CTX")).isTrue();
    }
  }

  private static boolean containsMappingWith(String value) throws IOException {
    String pid = ManagementFactory.getRuntimeMXBean().getName();
    pid = pid.substring(0, pid.indexOf('@'));
    List<String> lines = Files.readAllLines(Path.of("/proc/" + pid + "/maps"));
    for (String line : lines) {
      if (line.contains(value)) {
        return true;
      }
    }
    return false;
  }
}
