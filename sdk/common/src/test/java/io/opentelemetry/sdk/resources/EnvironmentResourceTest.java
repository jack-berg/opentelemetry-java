/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.resources;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;

import com.google.common.collect.ImmutableMap;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.internal.DefaultConfigProperties;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.semconv.resource.attributes.ResourceAttributes;
import org.junit.jupiter.api.Test;

class EnvironmentResourceTest {

  @Test
  void resourceFromConfig_empty() {
    Attributes attributes =
        EnvironmentResource.getAttributes(DefaultConfigProperties.createForTest(emptyMap()));

    OpenTelemetryAssertions.assertThat(attributes).isEmpty();
  }

  @Test
  void resourceFromConfig() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                singletonMap(
                    EnvironmentResource.ATTRIBUTE_PROPERTY,
                    "service.name=myService,appName=MyApp")));

    OpenTelemetryAssertions.assertThat(attributes)
        .hasSize(2)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "myService")
        .containsEntry("appName", "MyApp");
  }

  @Test
  void serviceName() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                singletonMap(EnvironmentResource.SERVICE_NAME_PROPERTY, "myService")));

    OpenTelemetryAssertions.assertThat(attributes)
        .hasSize(1)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "myService");
  }

  @Test
  void resourceFromConfig_overrideServiceName() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                ImmutableMap.of(
                    EnvironmentResource.ATTRIBUTE_PROPERTY,
                    "service.name=myService,appName=MyApp",
                    EnvironmentResource.SERVICE_NAME_PROPERTY,
                    "ReallyMyService")));

    OpenTelemetryAssertions.assertThat(attributes)
        .hasSize(2)
        .containsEntry(ResourceAttributes.SERVICE_NAME, "ReallyMyService")
        .containsEntry("appName", "MyApp");
  }

  @Test
  void resourceFromConfig_emptyEnvVar() {
    Attributes attributes =
        EnvironmentResource.getAttributes(
            DefaultConfigProperties.createForTest(
                singletonMap(EnvironmentResource.ATTRIBUTE_PROPERTY, "")));

    OpenTelemetryAssertions.assertThat(attributes).isEmpty();
  }
}
