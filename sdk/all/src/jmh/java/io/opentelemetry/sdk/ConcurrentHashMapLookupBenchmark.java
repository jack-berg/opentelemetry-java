/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.internal.ImmutableKeyValuePairs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Group;
import org.openjdk.jmh.annotations.GroupThreads;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Benchmarks {@link ConcurrentHashMap#get} in isolation to characterize the cost of the series
 * lookup that occurs on every metric record operation. Three dimensions are varied:
 *
 * <ul>
 *   <li>{@link KeyType} — key implementation and {@code hashCode} strategy.
 *   <li>{@link KeySize} — number of attributes per key (1 / 10 / 100), proportional to key object
 *       size. For {@link KeyType#ATTRIBUTES_UNCACHED} this directly increases {@code hashCode} cost
 *       since {@code Arrays.hashCode(data)} must iterate the full data array on every call.
 *   <li>{@code cardinality} — number of distinct keys in the map (1 / 128 / 1024).
 * </ul>
 *
 * <p>Keys are generated with the same seed and strategy as {@link MetricRecordBenchmark}.
 *
 * <h3>Key types</h3>
 *
 * <ul>
 *   <li>{@link KeyType#STRING}: {@link String} lazily caches {@code hashCode}, serving as a
 *       baseline. For {@link KeySize#MEDIUM}/{@link KeySize#LARGE} the string is proportionally
 *       longer (260 / 2600 chars), but this only affects the first (pre-cache) computation.
 *   <li>{@link KeyType#ATTRIBUTES_CACHED}: the real {@link Attributes} implementation
 *       ({@code ArrayBackedAttributes} / {@code ImmutableKeyValuePairs}), which lazily caches its
 *       hash in a plain {@code int} field. At steady state (after warmup) the cost is a single
 *       field read regardless of key size.
 *   <li>{@link KeyType#ATTRIBUTES_UNCACHED}: structurally identical to {@code
 *       ImmutableKeyValuePairs} but recomputes {@code Arrays.hashCode(data)} on every call. Cost
 *       grows linearly with key size.
 * </ul>
 */
public class ConcurrentHashMapLookupBenchmark {

  private static final int INITIAL_SEED = 513423236;
  private static final int MAX_THREADS = 4;
  private static final int RECORDS_PER_INVOCATION = BenchmarkUtils.RECORDS_PER_INVOCATION;
  // 26-char string used as both the seed and fixed padding for multi-attribute keys.
  private static final String FIXED_VALUE = "aaaaaaaaaaaaaaaaaaaaaaaaaa";

  /**
   * Key type used for {@link ConcurrentHashMap} lookups.
   *
   * @see ConcurrentHashMapLookupBenchmark class-level javadoc for descriptions of each variant.
   */
  public enum KeyType {
    STRING,
    ATTRIBUTES_CACHED,
    ATTRIBUTES_UNCACHED
  }

  /**
   * Number of attributes (key-value pairs) per key, governing key object size.
   *
   * <ul>
   *   <li>{@link #SMALL}: 1 attribute / ~26 chars — baseline
   *   <li>{@link #MEDIUM}: 10 attributes / ~260 chars — ~10x
   *   <li>{@link #LARGE}: 100 attributes / ~2600 chars — ~100x
   * </ul>
   *
   * <p>Only the first attribute's value varies across cardinality slots; the remaining attributes
   * use {@link ConcurrentHashMapLookupBenchmark#FIXED_VALUE}. This keeps keys distinct while
   * exercising the full data array traversal in {@code Arrays.hashCode}.
   */
  public enum KeySize {
    SMALL(1),
    MEDIUM(10),
    LARGE(100);

    final int numAttributes;

    KeySize(int numAttributes) {
      this.numAttributes = numAttributes;
    }
  }

  /**
   * Mirrors the structure, {@code hashCode}, and {@code equals} of {@code ImmutableKeyValuePairs}
   * (the superclass of {@code ArrayBackedAttributes}) but omits the lazy-cache field. {@code
   * hashCode} recomputes {@code Arrays.hashCode(data)} on every invocation.
   */
  static final class UncachedAttributesKey {
    private final Object[] data;

    UncachedAttributesKey(Object[] data) {
      this.data = data;
    }

    @Override
    public int hashCode() {
      // Identical formula to ImmutableKeyValuePairs.hashCode(), without the cache field.
      int result = 1;
      result *= 1000003;
      result ^= Arrays.hashCode(data);
      return result;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof UncachedAttributesKey)) {
        return false;
      }
      return Arrays.equals(data, ((UncachedAttributesKey) o).data);
    }
  }

  @State(Scope.Benchmark)
  public static class BenchmarkState {

    @Param KeyType keyType;

    @Param KeySize keySize;

    @Param({"1", "128", "1024"})
    int cardinality;

    ConcurrentHashMap<Object, Object> map;
    Object[] keys;

    @Setup
    public void setup() {
      Random random = new Random(INITIAL_SEED);
      int n = keySize.numAttributes;
      Object sentinel = new Object();

      // Pre-build the attribute keys shared across all cardinality slots.
      List<AttributeKey<String>> attrKeys = new ArrayList<>(n);
      for (int j = 0; j < n; j++) {
        attrKeys.add(AttributeKey.stringKey("key" + j));
      }

      // Generate keys using the same approach as MetricRecordBenchmark so results are comparable.
      // Only the first attribute value varies to produce distinct keys; the remaining n-1 attributes
      // use FIXED_VALUE. For STRING, the key is a single concatenated string of n*26 chars.
      List<Object> keyList = new ArrayList<>(cardinality);
      String last = FIXED_VALUE;
      for (int i = 0; i < cardinality; i++) {
        char[] chars = last.toCharArray();
        chars[random.nextInt(last.length())] = (char) (random.nextInt(26) + 'a');
        last = new String(chars);

        switch (keyType) {
          case STRING: {
            StringBuilder sb = new StringBuilder(n * FIXED_VALUE.length());
            sb.append(last);
            for (int j = 1; j < n; j++) {
              sb.append(FIXED_VALUE);
            }
            keyList.add(sb.toString());
            break;
          }
          case ATTRIBUTES_CACHED: {
            AttributesBuilder builder = Attributes.builder();
            builder.put(attrKeys.get(0), last);
            for (int j = 1; j < n; j++) {
              builder.put(attrKeys.get(j), FIXED_VALUE);
            }
            keyList.add(builder.build());
            break;
          }
          case ATTRIBUTES_UNCACHED: {
            // Build via the real Attributes machinery to get the identical sorted data layout, then
            // extract the backing array and hand it to UncachedAttributesKey.
            AttributesBuilder builder = Attributes.builder();
            builder.put(attrKeys.get(0), last);
            for (int j = 1; j < n; j++) {
              builder.put(attrKeys.get(j), FIXED_VALUE);
            }
            Object[] data = ((ImmutableKeyValuePairs<?, ?>) builder.build()).getData();
            keyList.add(new UncachedAttributesKey(data));
            break;
          }
        }
      }
      Collections.shuffle(keyList, random);

      map = new ConcurrentHashMap<>(cardinality);
      for (Object key : keyList) {
        map.put(key, sentinel);
      }
      keys = keyList.toArray(new Object[0]);
    }
  }

  @Benchmark
  @Group("threads1")
  @GroupThreads(1)
  @Fork(3)
  @Warmup(iterations = 3, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OperationsPerInvocation(RECORDS_PER_INVOCATION)
  public void lookup_SingleThread(BenchmarkState state) {
    lookup(state);
  }

  @Benchmark
  @Group("threads" + MAX_THREADS)
  @GroupThreads(MAX_THREADS)
  @Fork(3)
  @Warmup(iterations = 3, time = 1)
  @Measurement(iterations = 10, time = 1)
  @OperationsPerInvocation(RECORDS_PER_INVOCATION)
  public void lookup_MultipleThreads(BenchmarkState state) {
    lookup(state);
  }

  private static void lookup(BenchmarkState state) {
    for (int i = 0; i < RECORDS_PER_INVOCATION; i++) {
      state.map.get(state.keys[i % state.cardinality]);
    }
  }
}
