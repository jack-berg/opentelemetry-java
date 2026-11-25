/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.processcontext.internal;

import com.google.auto.value.AutoValue;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.sdk.processcontext.data.ProcessContextData;
import io.opentelemetry.sdk.resources.Resource;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;

/**
 * AutoValue implementation of {@link ProcessContextData}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
@Immutable
@AutoValue
public abstract class SdkProcessContextData implements ProcessContextData {

  /** Returns a new {@link ProcessContextData}. */
  public static ProcessContextData create(Resource resource, Attributes attributes) {
    Objects.requireNonNull(resource, "resource");
    Objects.requireNonNull(attributes, "attributes");
    return new AutoValue_SdkProcessContextData(resource, attributes);
  }

  SdkProcessContextData() {}
}
