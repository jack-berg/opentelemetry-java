/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.http;

import io.opentelemetry.sdk.common.CompletableResultCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class JdkHttpSender implements HttpSender {

  private final HttpClient client;
  private final URI uri;
  private final boolean compressionEnabled;
  private final Supplier<Map<String, String>> headerSupplier;
  private final RetryPolicyCopy retryPolicyCopy;

  JdkHttpSender(
      String endpoint,
      boolean compressionEnabled,
      Supplier<Map<String, String>> headerSupplier,
      @Nullable RetryPolicyCopy retryPolicyCopy,
      @Nullable SSLSocketFactory socketFactory,
      @Nullable X509TrustManager trustManager) {
    HttpClient.Builder builder = HttpClient.newBuilder();
    maybeConfigSSL(builder, socketFactory, trustManager);
    this.client = builder.build();
    try {
      this.uri = new URI(endpoint);
    } catch (URISyntaxException e) {
      throw new RuntimeException(e);
    }
    this.compressionEnabled = compressionEnabled;
    this.headerSupplier = headerSupplier;
    this.retryPolicyCopy =
        retryPolicyCopy == null
            ? new RetryPolicyCopy(1, Duration.ZERO, Duration.ZERO, 0)
            : retryPolicyCopy;
  }

  private static void maybeConfigSSL(
      HttpClient.Builder builder,
      @Nullable SSLSocketFactory socketFactory,
      @Nullable X509TrustManager trustManager) {
    if (socketFactory == null || trustManager == null) {
      return;
    }
    SSLContext context;
    try {
      // TODO: address
      context = SSLContext.getInstance("TLSv1.2");
      context.init(null, new TrustManager[] {trustManager}, null);
    } catch (NoSuchAlgorithmException | KeyManagementException e) {
      throw new RuntimeException(e);
    }
    builder.sslContext(context);
  }

  private static final Set<Integer> retryableStatusCodes =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(429, 502, 503, 504)));

  @Override
  public void send(
      Consumer<OutputStream> marshaler,
      int contentLength,
      Consumer<Throwable> onFailure,
      Consumer<HttpResponse> onResponse) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);
    headerSupplier.get().forEach(requestBuilder::setHeader);

    // todo: timeout
    // TODO: avoid byte baos?
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    if (compressionEnabled) {
      requestBuilder.header("Content-Encoding", "gzip");
      try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
        marshaler.accept(gzos);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

    } else {
      marshaler.accept(baos);
    }

    requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()));
    requestBuilder.header("Content-Type", "application/x-protobuf");

    HttpResponse response = null;
    Exception exception = null;
    int attempt = 0;
    long nextBackoffNanos = retryPolicyCopy.initialBackoff.toNanos();
    while (attempt < retryPolicyCopy.maxAttempts) {
      // Compute timeout
      // TODO: sleep for backoff if needed
      try {
        response = send(requestBuilder);
      } catch (Exception e) {
        exception = e;
      }
      if (response != null && !retryableStatusCodes.contains(response.statusCode())) {
        onResponse.accept(response);
        return;
      }
      // TODO: determine which exceptions are retryable
      if (exception != null) {
        onFailure.accept(exception);
        return;
      }
      attempt++;
    }

    if (response != null) {
      onResponse.accept(response);
    } else {
      onFailure.accept(exception);
    }
  }

  private HttpResponse send(HttpRequest.Builder requestBuilder) throws Exception {
    java.net.http.HttpResponse<byte[]> send =
        client.send(requestBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray());
    return toHttpResponse(send);
  }

  private static HttpResponse toHttpResponse(java.net.http.HttpResponse<byte[]> response) {
    return new HttpResponse() {
      @Override
      public int statusCode() {
        return response.statusCode();
      }

      @Override
      public String statusMessage() {
        return String.valueOf(response.statusCode());
      }

      @Override
      public byte[] responseBody() {
        return response.body();
      }
    };
  }

  @Override
  public CompletableResultCode shutdown() {
    // TODO:
    return CompletableResultCode.ofSuccess();
  }
}
