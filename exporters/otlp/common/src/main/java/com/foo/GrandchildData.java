/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.foo;

public class GrandchildData {

  private final long[] g1;

  public GrandchildData(long[] g1) {
    this.g1 = g1;
  }

  public long[] getG1() {
    return g1;
  }
}
