/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.foo;

public class ParentData {

  private final String o1;
  private final ChildData o2;
  private final String o3;

  public ParentData(String o1, ChildData o2, String o3) {
    this.o1 = o1;
    this.o2 = o2;
    this.o3 = o3;
  }

  public String getO1() {
    return o1;
  }

  public ChildData getO2() {
    return o2;
  }

  public String getO3() {
    return o3;
  }
}
