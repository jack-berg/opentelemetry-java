/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.foo;

import com.foo.internal.GrandChild;
import io.opentelemetry.exporter.internal.marshal.MarshalerContext;
import io.opentelemetry.exporter.internal.marshal.MarshalerUtil;
import io.opentelemetry.exporter.internal.marshal.Serializer;
import io.opentelemetry.exporter.internal.marshal.StatelessMarshaler;
import java.io.IOException;

public class GrandchildMarshaler implements StatelessMarshaler<GrandchildData> {

  private static final GrandchildMarshaler INSTANCE = new GrandchildMarshaler();

  public static GrandchildMarshaler getInstance() {
    return INSTANCE;
  }

  @Override
  public int getBinarySerializedSize(MarshalerContext context, GrandchildData value) {
    return MarshalerUtil.sizeRepeatedFixed64(GrandChild.G1, value.getG1().length);
  }

  @Override
  public void writeTo(MarshalerContext context, Serializer output, GrandchildData value)
      throws IOException {
    output.serializeRepeatedFixed64(GrandChild.G1, value.getG1());
  }
}
