/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.processcontext.data;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.resources.Resource;
import javax.annotation.concurrent.Immutable;

/**
 * Describes a ProcessContext.
 *
 * @see <a
 *     href="https://github.com/open-telemetry/opentelemetry-specification/blob/main/oteps/profiles/4719-process-ctx.md#payload-format">
 *     OTEP-4719 Process Context : Payload</a>
 */
@Immutable
@AutoValue
public abstract class ProcessContextData {

  /**
   * Returns a new ProcessContextData encapsulating the given Resource and supplemental Attributes.
   *
   * @return a new ProcessContextData.
   */
  @SuppressWarnings("AutoValueSubclassLeaked")
  public static ProcessContextData create(Resource resource, Attributes attributes) {
    return new AutoValue_ProcessContextData(resource, attributes);
  }

  ProcessContextData() {}

  /** Returns the resource of this process. */
  public abstract Resource getResource();

  /** Additional attributes that are not part of the Resource. */
  public abstract Attributes getAttributes();
}
