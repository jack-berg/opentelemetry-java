/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.http;

import io.opentelemetry.sdk.common.CompletableResultCode;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;

public interface HttpSender {

  static HttpSender create(
      String endpoint,
      boolean compressionEnabled,
      Supplier<Map<String, String>> headerSupplier,
      @Nullable RetryPolicyCopy retryPolicyCopy,
      @Nullable SSLSocketFactory socketFactory,
      @Nullable X509TrustManager trustManager) {
    return new JdkHttpSender(
        endpoint, compressionEnabled, headerSupplier, retryPolicyCopy, socketFactory, trustManager);
  }

  void send(
      Consumer<OutputStream> marshaler,
      int contentLength,
      Consumer<Throwable> onFailure,
      Consumer<HttpResponse> onResponse);

  CompletableResultCode shutdown();

  interface HttpResponse {

    int statusCode();

    String statusMessage();

    byte[] responseBody() throws IOException;
  }
}
