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
import java.util.Map;
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

  JdkHttpSender(
      String endpoint,
      boolean compressionEnabled,
      Supplier<Map<String, String>> headerSupplier,
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

  @Override
  public void send(
      Consumer<OutputStream> marshaler,
      int contentLength,
      Consumer<Throwable> onFailure,
      Consumer<HttpResponse> onResponse) {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);
    headerSupplier.get().forEach(requestBuilder::setHeader);

    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    if (compressionEnabled) {
      try {
        GZIPOutputStream gzos = new GZIPOutputStream(baos);
        marshaler.accept(gzos);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

    } else {
      marshaler.accept(baos);
    }

    requestBuilder.POST(HttpRequest.BodyPublishers.ofByteArray(baos.toByteArray()));
    requestBuilder.header("Content-Type", "application/x-protobuf");

    // TODO: avoid byte baos?

    client
        .sendAsync(requestBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofByteArray())
        .whenComplete(
            (httpResponse, throwable) -> {
              if (throwable != null) {
                onFailure.accept(throwable);
                return;
              }

              onResponse.accept(
                  new HttpResponse() {
                    @Override
                    public int statusCode() {
                      return httpResponse.statusCode();
                    }

                    @Override
                    public String statusMessage() {
                      return String.valueOf(httpResponse.statusCode());
                    }

                    @Override
                    public byte[] responseBody() {
                      return httpResponse.body();
                    }
                  });
            });
  }

  @Override
  public CompletableResultCode shutdown() {
    // TODO:
    return CompletableResultCode.ofSuccess();
  }
}
