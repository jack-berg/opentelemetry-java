/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.otlp.testing.internal;

import io.opentelemetry.exporter.internal.auth.Authenticator;
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporterBuilder;
import io.opentelemetry.sdk.common.export.RetryPolicy;
import io.opentelemetry.sdk.metrics.data.MetricData;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLContext;
import javax.net.ssl.X509TrustManager;

/** Wrapper of {@link OtlpHttpMetricExporterBuilder} for use in integration tests. */
public class HttpMetricExporterBuilderWrapper implements TelemetryExporterBuilder<MetricData> {
  private final OtlpHttpMetricExporterBuilder builder;

  public HttpMetricExporterBuilderWrapper(OtlpHttpMetricExporterBuilder builder) {
    this.builder = builder;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setEndpoint(String endpoint) {
    builder.setEndpoint(endpoint);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setTimeout(long timeout, TimeUnit unit) {
    builder.setTimeout(timeout, unit);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setTimeout(Duration timeout) {
    builder.setTimeout(timeout);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setCompression(String compression) {
    builder.setCompression(compression);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> addHeader(String key, String value) {
    builder.addHeader(key, value);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setAuthenticator(Authenticator authenticator) {
    Authenticator.setAuthenticatorOnDelegate(builder, authenticator);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setTrustedCertificates(byte[] certificates) {
    builder.setTrustedCertificates(certificates);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setSslContext(
      SSLContext sslContext, X509TrustManager trustManager) {
    builder.setSslContext(sslContext, trustManager);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setClientTls(
      byte[] privateKeyPem, byte[] certificatePem) {
    builder.setClientTls(privateKeyPem, certificatePem);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setRetryPolicy(RetryPolicy retryPolicy) {
    builder.setRetryPolicy(retryPolicy);
    return this;
  }

  @Override
  public TelemetryExporterBuilder<MetricData> setChannel(io.grpc.ManagedChannel channel) {
    throw new UnsupportedOperationException("Not implemented");
  }

  @Override
  public TelemetryExporter<MetricData> build() {
    return TelemetryExporter.wrap(builder.build());
  }
}
