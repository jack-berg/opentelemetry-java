/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.foo;

import java.util.List;

public class ChildData {

  private final List<GrandchildData> c1;
  private final int c2;

  public ChildData(List<GrandchildData> c1, int c2) {
    this.c1 = c1;
    this.c2 = c2;
  }

  public List<GrandchildData> getC1() {
    return c1;
  }

  public int getC2() {
    return c2;
  }
}
