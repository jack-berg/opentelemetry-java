/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.metrics.internal.state;

import java.util.concurrent.atomic.AtomicInteger;

public class RecordCollectLock {
  private final AtomicInteger[] recordLock;

  public RecordCollectLock() {
    recordLock = new AtomicInteger[Runtime.getRuntime().availableProcessors()];
    for (int i = 0; i < recordLock.length; i++) {
      recordLock[i] = new AtomicInteger(0);
    }
  }

  public boolean readyToRecord() {
    return lockForThread().addAndGet(2) % 2 == 0;
  }

  public void awaitReadyToRecord() {
    while (!readyToRecord()) {
      releaseForRecord();
      Thread.yield();
    }
  }

  public void releaseForRecord() {
    lockForThread().addAndGet(-2);
  }

  public void awaitReadyToCollect() {
    for (int i = 0; i < recordLock.length; i++) {
      recordLock[i].addAndGet(1);
    }
    while (!allReadyToCollect()) {
      Thread.yield();
    }
  }

  private boolean allReadyToCollect() {
    for (int i = 0; i < recordLock.length; i++) {
      if (recordLock[i].get() != 1) {
        return false;
      }
    }
    return true;
  }

  public void releaseForCollect() {
    for (int i = 0; i < recordLock.length; i++) {
      recordLock[i].addAndGet(-1);
    }
  }

  private AtomicInteger lockForThread() {
    return recordLock[((int) Thread.currentThread().getId()) % recordLock.length];
  }
}
