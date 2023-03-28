/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.http;

import static java.net.http.HttpResponse.BodyHandlers;

import io.opentelemetry.sdk.common.CompletableResultCode;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;
import javax.annotation.Nullable;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class JdkHttpSender implements HttpSender {

  private static final Set<Integer> retryableStatusCodes =
      Collections.unmodifiableSet(new HashSet<>(Arrays.asList(429, 502, 503, 504)));

  private final ExecutorService marshalerService = Executors.newFixedThreadPool(2);
  private final ExecutorService executorService = Executors.newFixedThreadPool(2);
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
    HttpClient.Builder builder = HttpClient.newBuilder().executor(executorService);
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

  @Override
  public CompletableFuture<Response> send(Consumer<OutputStream> marshaler, int contentLength) {
    return new ExportRequest(marshaler, contentLength).send().thenApply(JdkHttpSender::toHttpResponse);
  }

  private HttpRequest.Builder builder() {
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder().uri(uri);
    headerSupplier.get().forEach(requestBuilder::setHeader);
    requestBuilder.header("Content-Type", "application/x-protobuf");
    return requestBuilder;
  }

  private class ExportRequest {

    private final Consumer<OutputStream> marshaler;
    private final int contentLength;
    private final AtomicInteger attempt = new AtomicInteger();
    private final AtomicLong nextBackoffNanos =
        new AtomicLong(retryPolicyCopy.initialBackoff.toNanos());

    private ExportRequest(Consumer<OutputStream> marshaler, int contentLength) {
      this.marshaler = marshaler;
      this.contentLength = contentLength;
    }

    private CompletableFuture<HttpResponse<byte[]>> send() {
      HttpRequest.Builder requestBuilder = builder();
      // TODO: make sure to test with small pipe size
      PipedInputStream pis;
      OutputStream os;
      try {
        PipedOutputStream pos = new PipedOutputStream();
        pis = new PipedInputStream(pos);
        if (compressionEnabled) {
          requestBuilder.header("Content-Encoding", "gzip");
          os = new GZIPOutputStream(pos);
        } else {
          os = pos;
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }

      requestBuilder.POST(HttpRequest.BodyPublishers.ofInputStream(() -> pis));

      CompletableFuture<Void> marshalFuture =
          CompletableFuture.runAsync(
              () -> {
                marshaler.accept(os);
                try {
                  os.close();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
              },
              marshalerService);

      // TODO: timeout
      return client
          .sendAsync(requestBuilder.build(), BodyHandlers.ofByteArray())
          .thenCombine(marshalFuture, (httpResponse, unused) -> httpResponse)
          .handleAsync(
              (httpResponse, throwable) -> {
                try {
                  pis.close();
                } catch (IOException e) {
                  throw new RuntimeException(e);
                }
                // TODO: is throwable retryable?
                if (throwable != null) {
                  throw new RuntimeException(throwable);
                }

                int currentAttempt = attempt.incrementAndGet();
                if (currentAttempt >= retryPolicyCopy.maxAttempts
                    || !retryableStatusCodes.contains(httpResponse.statusCode())) {
                  return httpResponse;
                }

                // Compute and sleep for backoff
                long upperBoundNanos =
                    Math.min(nextBackoffNanos.get(), retryPolicyCopy.maxBackoff.toNanos());
                long backoffNanos = ThreadLocalRandom.current().nextLong(upperBoundNanos);
                nextBackoffNanos.set(
                    (long) (nextBackoffNanos.get() * retryPolicyCopy.backoffMultiplier));
                try {
                  TimeUnit.NANOSECONDS.sleep(backoffNanos);
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }

                return send().join();
              },
              executorService);
    }
  }

  private static Response toHttpResponse(HttpResponse<byte[]> response) {
    return new Response() {
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
    executorService.shutdown();
    marshalerService.shutdown();
    // TODO:
    return CompletableResultCode.ofSuccess();
  }
}
