package io.opentelemetry.sdk.metrics.internal.state;

import java.util.concurrent.atomic.AtomicInteger;

class RecordCollectLock {
  private final AtomicInteger recordLock = new AtomicInteger(0);

  boolean tryAcquireForRecord() {
    return recordLock.addAndGet(2) % 2 == 0;
  }

  void releaseForRecord() {
    recordLock.addAndGet(-2);
  }

  void awaitReadyToCollect() {
    while (recordLock.get() > 1) {
      Thread.yield();
    }
  }

  void acquireForCollect() {
    recordLock.addAndGet(1);
  }

  void releaseForCollect() {
    recordLock.addAndGet(-1);
  }
}
