/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.foo;

import io.opentelemetry.exporter.internal.marshal.MarshalerContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DummyTest {

  @Test
  void serde() throws IOException {
    ParentData data =
        new ParentData(
            "value1",
            new ChildData(
                Arrays.asList(
                    new GrandchildData(new long[] {1, 2}), new GrandchildData(new long[] {3, 4})),
                1),
            "value2");

    MarshalerContext context = new MarshalerContext();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ParentMarshaler.getInstance().writeJsonTo(context, baos, data);
    System.out.println(new String(baos.toByteArray(), StandardCharsets.UTF_8));
  }
}
