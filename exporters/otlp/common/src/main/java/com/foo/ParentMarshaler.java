/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.foo;

import com.foo.internal.Parent;
import io.opentelemetry.exporter.internal.marshal.MarshalerContext;
import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.exporter.internal.marshal.StatelessMarshaler;
import java.io.IOException;

public class ParentMarshaler implements StatelessMarshaler<ParentData> {

  private static final ParentMarshaler INSTANCE = new ParentMarshaler();

  public static ParentMarshaler getInstance() {
    return INSTANCE;
  }

  @Override
  public int getBinarySerializedSize(MarshalerContext context, ParentData value) {
    int size = 0;

    int o1Size = MarshalerUtil.getUtf8Size(value.getO1());
    context.addSize(o1Size);
    size += o1Size;

    size += ChildMarshaler.getInstance().getBinarySerializedSize(context, value.getO2());

    int o3Size = MarshalerUtil.getUtf8Size(value.getO3());
    context.addSize(o3Size);
    size += o3Size;

    return size;
  }

  @Override
  public void writeTo(MarshalerContext context, Serializer output, ParentData value)
      throws IOException {
    output.serializeString(Parent.P1, value.getO1(), context.getSize());
    output.serializeMessage(Parent.P2, value.getO2(), context, ChildMarshaler.getInstance());
    output.serializeString(Parent.P3, value.getO3(), context.getSize());
  }
}
