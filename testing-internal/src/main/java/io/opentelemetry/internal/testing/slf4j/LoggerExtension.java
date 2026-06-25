/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.internal.testing.slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit5 extension to enable the {@link SuppressLogger} annotation which can suppress output of log
 * messages during test execution, i.e. when the log messages are expected.
 *
 * <p>Class-level {@link SuppressLogger} annotations suppress logging for the entire test class
 * lifecycle (including {@code @BeforeAll} setup). Method-level annotations suppress logging only
 * during that test method's execution.
 */
public final class LoggerExtension
    implements BeforeAllCallback,
        AfterAllCallback,
        BeforeTestExecutionCallback,
        AfterTestExecutionCallback {

  private static final ExtensionContext.Namespace NAMESPACE_CLASS =
      ExtensionContext.Namespace.create(LoggerExtension.class, "class");
  private static final ExtensionContext.Namespace NAMESPACE_METHOD =
      ExtensionContext.Namespace.create(LoggerExtension.class, "method");

  @Override
  public void beforeAll(ExtensionContext context) {
    // Walk the class hierarchy so that @SuppressLogger on an abstract base class is inherited.
    List<SuppressLogger> suppressLoggers = new ArrayList<>();
    Class<?> testClass = context.getRequiredTestClass();
    while (testClass != null) {
      suppressLoggers.addAll(
          Arrays.asList(testClass.getDeclaredAnnotationsByType(SuppressLogger.class)));
      testClass = testClass.getSuperclass();
    }

    List<Logger> loggers = toLoggers(suppressLoggers);
    if (loggers.isEmpty()) {
      return;
    }

    loggers.forEach(logger -> logger.setUseParentHandlers(false));
    context
        .getStore(NAMESPACE_CLASS)
        .put(
            LoggerExtension.class,
            (Runnable) () -> loggers.forEach(logger -> logger.setUseParentHandlers(true)));
  }

  @Override
  public void afterAll(ExtensionContext context) {
    Runnable restore = context.getStore(NAMESPACE_CLASS).get(LoggerExtension.class, Runnable.class);
    if (restore != null) {
      restore.run();
    }
  }

  @Override
  public void beforeTestExecution(ExtensionContext context) {
    List<SuppressLogger> suppressLoggers =
        new ArrayList<>(
            Arrays.asList(
                context.getRequiredTestMethod().getAnnotationsByType(SuppressLogger.class)));

    List<Logger> loggers = toLoggers(suppressLoggers);
    if (loggers.isEmpty()) {
      return;
    }

    loggers.forEach(logger -> logger.setUseParentHandlers(false));
    context
        .getStore(NAMESPACE_METHOD)
        .put(
            LoggerExtension.class,
            (Runnable) () -> loggers.forEach(logger -> logger.setUseParentHandlers(true)));
  }

  @Override
  public void afterTestExecution(ExtensionContext context) {
    Runnable restore =
        context.getStore(NAMESPACE_METHOD).get(LoggerExtension.class, Runnable.class);
    if (restore != null) {
      restore.run();
    }
  }

  private static List<Logger> toLoggers(List<SuppressLogger> suppressLoggers) {
    List<Logger> loggers = new ArrayList<>();
    for (SuppressLogger suppression : suppressLoggers) {
      if (!suppression.value().equals(Void.class)) {
        loggers.add(Logger.getLogger(suppression.value().getName()));
      }
      if (!suppression.loggerName().isEmpty()) {
        loggers.add(Logger.getLogger(suppression.loggerName()));
      }
    }
    return loggers;
  }
}
