/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.testing.logs;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.sdk.common.InstrumentationScopeInfo;
import io.opentelemetry.sdk.logs.data.Body;
import io.opentelemetry.sdk.logs.data.LogData;
import io.opentelemetry.sdk.logs.data.Severity;
import io.opentelemetry.sdk.resources.Resource;
import java.util.concurrent.TimeUnit;
import javax.annotation.concurrent.Immutable;

/**
 * Immutable representation of all data collected by the {@link io.opentelemetry.sdk.logs.LogRecord}
 * class.
 */
@Immutable
@AutoValue
public abstract class TestLogData implements LogData {

  /**
   * Creates a new Builder for creating an SpanData instance.
   *
   * @return a new Builder.
   */
  public static Builder builder() {
    return new AutoValue_TestLogData.Builder()
        .setResource(Resource.empty())
        .setInstrumentationScopeInfo(InstrumentationScopeInfo.empty())
        .setEpoch(0, TimeUnit.NANOSECONDS)
        .setSpanContext(SpanContext.getInvalid())
        .setSeverity(Severity.UNDEFINED_SEVERITY_NUMBER)
        .setBody("")
        .setAttributes(Attributes.empty());
  }

  TestLogData() {}

  /** A {@code Builder} class for {@link TestLogData}. */
  @AutoValue.Builder
  public abstract static class Builder {

    abstract TestLogData autoBuild();

    /**
     * Create a new SpanData instance from the data in this.
     *
     * @return a new SpanData instance
     */
    public TestLogData build() {
      return autoBuild();
    }

    /** Set the {@link Resource} associated with this log record. Must not be null. */
    public abstract Builder setResource(Resource resource);

    /**
     * Sets the instrumentation scope of the log emitter which created this log record. Must not be
     * null.
     */
    public abstract Builder setInstrumentationScopeInfo(
        InstrumentationScopeInfo instrumentationScopeInfo);

    /** Set the epoch timestamp. */
    public Builder setEpoch(long timestamp, TimeUnit unit) {
      return setEpochNanos(unit.toNanos(timestamp));
    }

    /** Set the epoch timestamp in nanos. */
    abstract Builder setEpochNanos(long epochNanos);

    /** Set the span context. */
    public abstract Builder setSpanContext(SpanContext spanContext);

    /** Set the severity. */
    public abstract Builder setSeverity(Severity severity);

    /** Set the severity text. */
    public abstract Builder setSeverityText(String severityText);

    /** Set the body string. */
    public Builder setBody(String body) {
      return setBody(Body.string(body));
    }

    /** Set the body. */
    abstract Builder setBody(Body body);

    /** Set the attributes. */
    public abstract Builder setAttributes(Attributes attributes);
  }
}
