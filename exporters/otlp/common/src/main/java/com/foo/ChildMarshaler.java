/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.foo;

import com.foo.internal.Child;
import io.opentelemetry.exporter.internal.marshal.MarshalerContext;
import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.exporter.internal.marshal.StatelessMarshaler;
import java.io.IOException;

public class ChildMarshaler implements StatelessMarshaler<ChildData> {

  private static final ChildMarshaler INSTANCE = new ChildMarshaler();

  public static ChildMarshaler getInstance() {
    return INSTANCE;
  }

  @Override
  public int getBinarySerializedSize(MarshalerContext context, ChildData value) {
    int size = 0;

    size +=
        MarshalerUtil.sizeRepeatedMessage(
            context, Child.C1, GrandchildMarshaler.getInstance(), value.getC1());
    size += MarshalerUtil.sizeInt32(Child.C2, value.getC2());

    return size;
  }

  @Override
  public void writeTo(MarshalerContext context, Serializer output, ChildData value)
      throws IOException {
    output.serializeRepeatedMessage(
        Child.C1, value.getC1(), context, GrandchildMarshaler.getInstance());
    output.serializeInt32(Child.C2, value.getC2());
  }
}
