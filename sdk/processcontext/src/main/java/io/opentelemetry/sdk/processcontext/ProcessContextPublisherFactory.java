/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.processcontext;

import io.opentelemetry.common.impl.ConfigUtil;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Factory for obtaining the process-wide {@link ProcessContextPublisher}.
 *
 * <p>The supplied publisher will, depending on the Java version and OpenTelemetry configuration, be
 * either a no-op or an implementation using Linux libc via Panama FFM.
 */
final class ProcessContextPublisherFactory {

  private static final Logger logger =
      Logger.getLogger(ProcessContextPublisherFactory.class.getName());

  private static final ProcessContextPublisherFactory INSTANCE =
      new ProcessContextPublisherFactory();

  private final ProcessContextPublisher processContextPublisher;

  private ProcessContextPublisherFactory() {
    processContextPublisher = selectPublisher();
  }

  static ProcessContextPublisherFactory getInstance() {
    return INSTANCE;
  }

  ProcessContextPublisher create() {
    return processContextPublisher;
  }

  private static ProcessContextPublisher selectPublisher() {
    // Off by default until stable.
    // Spec in progress: OTEL_EXPERIMENTAL_PROCESS_CONTEXT_ENABLED
    // https://github.com/open-telemetry/opentelemetry-specification/pull/5341
    boolean enabled =
        "true"
            .equalsIgnoreCase(
                ConfigUtil.getString("otel.experimental.process.context.enabled", "false"));

    if (!enabled) {
      return new NoopProcessContextPublisher();
    }

    // Panama publisher uses Linux-specific syscalls and requires JDK 25+.
    boolean available =
        "Linux".equalsIgnoreCase(System.getProperty("os.name"))
            && isJava25Plus(System.getProperty("java.specification.version"));

    if (!available) {
      logger.log(
          Level.WARNING,
          "Process context publication requested, but no suitable implementation available for this environment");
      return new NoopProcessContextPublisher();
    }

    try {
      Method create =
          Class.forName("io.opentelemetry.sdk.processcontext.PanamaProcessContextPublisher")
              .getDeclaredMethod("create");
      create.setAccessible(true);
      return (ProcessContextPublisher) create.invoke(null);
    } catch (ReflectiveOperationException | LinkageError e) {
      logger.log(Level.WARNING, "Failed to init ProcessContextPublisher", e);
      return new NoopProcessContextPublisher();
    }
  }

  // java.specification.version is a bare integer from Java 9 onwards ("9", "11", "25", ...) and
  // "1.N" on older releases, which are never >= 25. Pre-Java 9 strings fail parseInt and we treat
  // anything unparseable as unsupported.
  private static boolean isJava25Plus(String version) {
    try {
      return Integer.parseInt(version) >= 25;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
