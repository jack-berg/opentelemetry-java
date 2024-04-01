/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.exporter.internal.marshal;

import java.io.IOException;
import java.io.OutputStream;

public interface StatelessMarshaler<T> {

  /** Marshals into the {@link OutputStream} in proto binary format. */
  default void writeBinaryTo(MarshalerContext context, OutputStream output, T value)
      throws IOException {
    try (Serializer serializer = new ProtoSerializer(output)) {
      writeTo(context, serializer, value);
    }
  }

  /** Marshals into the {@link OutputStream} in proto JSON format. */
  default void writeJsonTo(MarshalerContext context, OutputStream output, T value)
      throws IOException {
    try (JsonSerializer serializer = new JsonSerializer(output)) {
      serializer.writeMessageValue(context, this, value);
    }
  }

  int getBinarySerializedSize(MarshalerContext context, T value);

  void writeTo(MarshalerContext context, Serializer output, T value) throws IOException;
}
