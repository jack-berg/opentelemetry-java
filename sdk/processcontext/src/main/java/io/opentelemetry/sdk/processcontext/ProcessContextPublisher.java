/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.processcontext;

import io.opentelemetry.sdk.common.Clock;
import io.opentelemetry.sdk.processcontext.data.ProcessContextData;
import java.io.IOException;

/**
 * Exposes process context information to external readers using the shared memory mechanism
 * specified in OTEP-4719.
 *
 * <p>Access the singleton via {@link #getInstance()}. The implementation returned is selected at
 * class load time based on the running JVM and the {@code
 * otel.experimental.process.context.enabled} property; it may be a no-op.
 *
 * <p>Note that publication is a process-wide singleton per OTEP-4719. If multiple {@link
 * io.opentelemetry.sdk.OpenTelemetrySdk} instances exist in the same JVM, each subsequent {@link
 * #publish} call overwrites the previous publication (last-writer-wins).
 *
 * @see <a
 *     href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/oteps/profiles/4719-process-ctx.md">OTEP
 *     4719</a>
 */
public interface ProcessContextPublisher extends AutoCloseable {

  /** Returns the process-wide {@link ProcessContextPublisher} instance. */
  static ProcessContextPublisher getInstance() {
    return ProcessContextPublisherFactory.getInstance().create();
  }

  /**
   * Called to publish a {@code ProcessContextData}s.
   *
   * @param processContextData the process Resource descriptor and optional additional metadata.
   * @param clock the time source for the publication.
   */
  void publish(ProcessContextData processContextData, Clock clock) throws IOException;

  @Override
  void close() throws IOException;
}
