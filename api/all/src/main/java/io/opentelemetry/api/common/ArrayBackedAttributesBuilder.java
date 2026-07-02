/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.api.common;

import static io.opentelemetry.api.common.AttributeKey.booleanArrayKey;
import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.doubleArrayKey;
import static io.opentelemetry.api.common.AttributeKey.doubleKey;
import static io.opentelemetry.api.common.AttributeKey.longArrayKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringArrayKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;

import io.opentelemetry.api.internal.AttributeLengthLimits;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * The default {@link AttributesBuilder} implementation.
 *
 * <p>Storage is a flat {@link List} of alternating {@link AttributeKey}/value pairs. In the default
 * unlimited configuration, {@link #put} appends without dedup and defers dedup/sort to {@link
 * #build()}. When constructed with capacity and length limits (used by the SDK to record span/log
 * attributes), {@link #put} enforces last-value-wins by key <em>name</em> (regardless of type) on
 * the fly, truncates over-length values, and drops entries beyond the capacity.
 *
 * <p>Also implements {@link Attributes} so that a limits-configured instance can be exposed as a
 * live, mutable {@link Attributes} view without allocating a snapshot. Callers using the class as
 * the default {@link AttributesBuilder} never see this: the returned type is {@link
 * AttributesBuilder}.
 *
 * <p>Yes, a class implementing both a builder interface and its result-immutable interface is
 * unusual. It exists to let the SDK copy of this class (see {@code :sdk:common}'s Gradle build)
 * serve as {@code AttributesMap}'s replacement without allocating a separate {@link Attributes}
 * view on every read. The api-side (unlimited) users of this class only reference it through the
 * {@link AttributesBuilder} interface and never see the {@link Attributes} methods.
 */
@SuppressWarnings("BuilderReturnThis") // Also implements Attributes; not all methods return this.
class ArrayBackedAttributesBuilder implements AttributesBuilder, Attributes {
  private final List<Object> data;

  /** Max number of unique entries. {@link Integer#MAX_VALUE} means unlimited. */
  private final int capacity;

  /**
   * Max length of string / string-array values. {@link Integer#MAX_VALUE} means unlimited. Only
   * consulted on the limited put path.
   */
  private final int lengthLimit;

  /** Count of put-with-non-null-value attempts (only incremented on the limited put path). */
  private int totalAddedValues;

  /** Count of non-null pairs currently stored. */
  private int size;

  ArrayBackedAttributesBuilder() {
    this(new ArrayList<>(), Integer.MAX_VALUE, Integer.MAX_VALUE, 0);
  }

  ArrayBackedAttributesBuilder(List<Object> data) {
    this(data, Integer.MAX_VALUE, Integer.MAX_VALUE, data.size() / 2);
  }

  private ArrayBackedAttributesBuilder(
      List<Object> data, int capacity, int lengthLimit, int initialSize) {
    this.data = data;
    this.capacity = capacity;
    this.lengthLimit = lengthLimit;
    this.size = initialSize;
  }

  /**
   * Create a limits-enforcing builder. The resulting instance also implements {@link Attributes}
   * and can be exposed as a live view.
   *
   * @param capacity max number of unique attributes; further additions are dropped
   * @param lengthLimit max length of string / string-array values; longer values are truncated
   */
  // NOTE: public so that the copy of this class in sdk-common (see :sdk:common's Gradle build)
  // exposes it across packages. Safe here because this class is package-private in api.common,
  // so external api users cannot reach this factory.
  public static ArrayBackedAttributesBuilder create(long capacity, int lengthLimit) {
    int cap = (int) Math.min(capacity, (long) Integer.MAX_VALUE);
    return new ArrayBackedAttributesBuilder(new ArrayList<>(), cap, lengthLimit, 0);
  }

  private boolean isLimited() {
    return capacity != Integer.MAX_VALUE || lengthLimit != Integer.MAX_VALUE;
  }

  @Override
  public Attributes build() {
    // If only one key-value pair AND the entry hasn't been set to null (by #remove(AttributeKey<T>)
    // or #removeIf(Predicate<AttributeKey<?>>)), then we can bypass sorting and filtering
    if (data.size() == 2 && data.get(0) != null) {
      return new ArrayBackedAttributes(data.toArray());
    }
    return ArrayBackedAttributes.sortAndFilterToAttributes(data.toArray());
  }

  @Override
  public <T> AttributesBuilder put(AttributeKey<Long> key, int value) {
    return put(key, (long) value);
  }

  @Override
  public <T> AttributesBuilder put(AttributeKey<T> key, @Nullable T value) {
    if (key == null || key.getKey().isEmpty() || value == null) {
      return this;
    }
    if (key.getType() == AttributeType.VALUE && value instanceof Value) {
      putValue(key, (Value<?>) value);
      return this;
    }
    addPair(key, value);
    return this;
  }

  @SuppressWarnings("unchecked")
  private void putValue(AttributeKey<?> key, Value<?> valueObj) {
    // Convert VALUE type to narrower type when possible
    String keyName = key.getKey();
    switch (valueObj.getType()) {
      case STRING:
        put(stringKey(keyName), ((Value<String>) valueObj).getValue());
        return;
      case LONG:
        put(longKey(keyName), ((Value<Long>) valueObj).getValue());
        return;
      case DOUBLE:
        put(doubleKey(keyName), ((Value<Double>) valueObj).getValue());
        return;
      case BOOLEAN:
        put(booleanKey(keyName), ((Value<Boolean>) valueObj).getValue());
        return;
      case ARRAY:
        List<Value<?>> arrayValues = (List<Value<?>>) valueObj.getValue();
        AttributeType attributeType = attributeType(arrayValues);
        switch (attributeType) {
          case STRING_ARRAY:
            List<String> strings = new ArrayList<>(arrayValues.size());
            for (Value<?> v : arrayValues) {
              strings.add((String) v.getValue());
            }
            put(stringArrayKey(keyName), strings);
            return;
          case LONG_ARRAY:
            List<Long> longs = new ArrayList<>(arrayValues.size());
            for (Value<?> v : arrayValues) {
              longs.add((Long) v.getValue());
            }
            put(longArrayKey(keyName), longs);
            return;
          case DOUBLE_ARRAY:
            List<Double> doubles = new ArrayList<>(arrayValues.size());
            for (Value<?> v : arrayValues) {
              doubles.add((Double) v.getValue());
            }
            put(doubleArrayKey(keyName), doubles);
            return;
          case BOOLEAN_ARRAY:
            List<Boolean> booleans = new ArrayList<>(arrayValues.size());
            for (Value<?> v : arrayValues) {
              booleans.add((Boolean) v.getValue());
            }
            put(booleanArrayKey(keyName), booleans);
            return;
          case VALUE:
            // Not coercible (empty, non-homogeneous, or unsupported element type)
            addPair(key, valueObj);
            return;
          default:
            throw new IllegalArgumentException("Unexpected array attribute type: " + attributeType);
        }
      case KEY_VALUE_LIST:
      case BYTES:
      case EMPTY:
        // Keep as VALUE type
        addPair(key, valueObj);
    }
  }

  /**
   * Store the given key/value pair. In unlimited mode: append. In limited mode: dedup by name,
   * truncate over-length values, and enforce capacity.
   */
  private void addPair(AttributeKey<?> key, Object value) {
    if (!isLimited()) {
      data.add(key);
      data.add(value);
      size++;
      return;
    }
    totalAddedValues++;
    Object limited =
        lengthLimit == Integer.MAX_VALUE
            ? value
            : AttributeLengthLimits.applyAttributeLengthLimit(value, lengthLimit);
    String name = key.getKey();
    int emptySlot = -1;
    for (int i = 0; i < data.size(); i += 2) {
      Object existing = data.get(i);
      if (existing == null) {
        if (emptySlot < 0) {
          emptySlot = i;
        }
        continue;
      }
      if (((AttributeKey<?>) existing).getKey().equals(name)) {
        data.set(i, key);
        data.set(i + 1, limited);
        return;
      }
    }
    if (size >= capacity) {
      return;
    }
    if (emptySlot >= 0) {
      data.set(emptySlot, key);
      data.set(emptySlot + 1, limited);
    } else {
      data.add(key);
      data.add(limited);
    }
    size++;
  }

  /**
   * Returns the AttributeType for a homogeneous array (STRING_ARRAY, LONG_ARRAY, DOUBLE_ARRAY, or
   * BOOLEAN_ARRAY), or VALUE if the array is empty, non-homogeneous, or contains unsupported
   * element types.
   */
  private static AttributeType attributeType(List<Value<?>> arrayValues) {
    if (arrayValues.isEmpty()) {
      return AttributeType.VALUE;
    }
    ValueType elementType = arrayValues.get(0).getType();
    for (Value<?> v : arrayValues) {
      if (v.getType() != elementType) {
        return AttributeType.VALUE;
      }
    }
    switch (elementType) {
      case STRING:
        return AttributeType.STRING_ARRAY;
      case LONG:
        return AttributeType.LONG_ARRAY;
      case DOUBLE:
        return AttributeType.DOUBLE_ARRAY;
      case BOOLEAN:
        return AttributeType.BOOLEAN_ARRAY;
      case ARRAY:
      case KEY_VALUE_LIST:
      case BYTES:
      case EMPTY:
        return AttributeType.VALUE;
    }
    throw new IllegalArgumentException("Unsupported element type: " + elementType);
  }

  @Override
  @SuppressWarnings({"unchecked", "rawtypes"})
  // Safe: Attributes guarantees iteration over matching AttributeKey<T> / value pairs.
  public AttributesBuilder putAll(Attributes attributes) {
    if (attributes == null) {
      return this;
    }
    attributes.forEach((key, value) -> put((AttributeKey) key, value));
    return this;
  }

  @Override
  public <T> AttributesBuilder remove(AttributeKey<T> key) {
    if (key == null || key.getKey().isEmpty()) {
      return this;
    }
    return removeIf(
        entryKey ->
            key.getKey().equals(entryKey.getKey()) && key.getType().equals(entryKey.getType()));
  }

  @Override
  public AttributesBuilder removeIf(Predicate<AttributeKey<?>> predicate) {
    if (predicate == null) {
      return this;
    }
    for (int i = 0; i < data.size() - 1; i += 2) {
      Object entry = data.get(i);
      if (entry instanceof AttributeKey && predicate.test((AttributeKey<?>) entry)) {
        // null items are filtered out in ArrayBackedAttributes
        data.set(i, null);
        data.set(i + 1, null);
        size--;
      }
    }
    return this;
  }

  // ---- SDK-facing helpers (used by the copied class in sdk/common). Public so the copy exposes
  // them across packages; the api-side class is package-private so these do not leak. ----

  /** Count of {@link #put} attempts with a non-null value, including those dropped by capacity. */
  public int getTotalAddedValues() {
    return totalAddedValues;
  }

  /** Typed put convenience for callers with an already-typed {@link AttributeKey}. */
  public <T> void putIfCapacity(AttributeKey<T> key, @Nullable T value) {
    put(key, value);
  }

  /** Snapshot the current state to an immutable {@link Attributes}. */
  public Attributes immutableCopy() {
    return build();
  }

  // ---- Attributes implementation (only meaningful in limited/live mode) ----

  @SuppressWarnings("unchecked")
  @Override
  @Nullable
  public <T> T get(AttributeKey<T> key) {
    if (key == null) {
      return null;
    }
    for (int i = 0; i < data.size(); i += 2) {
      Object entryKey = data.get(i);
      if (key.equals(entryKey)) {
        return (T) data.get(i + 1);
      }
    }
    return null;
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public boolean isEmpty() {
    return size == 0;
  }

  @Override
  public void forEach(BiConsumer<? super AttributeKey<?>, ? super Object> consumer) {
    if (consumer == null) {
      return;
    }
    for (int i = 0; i < data.size(); i += 2) {
      Object entryKey = data.get(i);
      if (entryKey != null) {
        consumer.accept((AttributeKey<?>) entryKey, data.get(i + 1));
      }
    }
  }

  @Override
  public Map<AttributeKey<?>, Object> asMap() {
    if (size == 0) {
      return Collections.emptyMap();
    }
    Map<AttributeKey<?>, Object> snapshot = new HashMap<>(size);
    for (int i = 0; i < data.size(); i += 2) {
      Object entryKey = data.get(i);
      if (entryKey != null) {
        snapshot.put((AttributeKey<?>) entryKey, data.get(i + 1));
      }
    }
    return Collections.unmodifiableMap(snapshot);
  }

  @Override
  public AttributesBuilder toBuilder() {
    // Always returns an unlimited builder. Limits do not carry over to the returned builder.
    return Attributes.builder().putAll(this);
  }

  @Override
  public boolean equals(@Nullable Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Attributes)) {
      return false;
    }
    Attributes other = (Attributes) o;
    if (other.size() != size) {
      return false;
    }
    return asMap().equals(other.asMap());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("ArrayBackedAttributesBuilder{data={");
    boolean first = true;
    for (int i = 0; i < data.size(); i += 2) {
      Object entryKey = data.get(i);
      if (entryKey == null) {
        continue;
      }
      if (!first) {
        sb.append(", ");
      }
      first = false;
      sb.append(entryKey).append('=').append(data.get(i + 1));
    }
    sb.append("}, capacity=")
        .append(capacity)
        .append(", totalAddedValues=")
        .append(totalAddedValues)
        .append('}');
    return sb.toString();
  }

  @Override
  public int hashCode() {
    // Consistent with Map.hashCode semantics (matches AbstractMap-based Attributes impls).
    int h = 0;
    for (int i = 0; i < data.size(); i += 2) {
      Object entryKey = data.get(i);
      if (entryKey != null) {
        h += entryKey.hashCode() ^ data.get(i + 1).hashCode();
      }
    }
    return h;
  }

  static List<Double> toList(double... values) {
    Double[] boxed = new Double[values.length];
    for (int i = 0; i < values.length; i++) {
      boxed[i] = values[i];
    }
    return Arrays.asList(boxed);
  }

  static List<Long> toList(long... values) {
    Long[] boxed = new Long[values.length];
    for (int i = 0; i < values.length; i++) {
      boxed[i] = values[i];
    }
    return Arrays.asList(boxed);
  }

  static List<Boolean> toList(boolean... values) {
    Boolean[] boxed = new Boolean[values.length];
    for (int i = 0; i < values.length; i++) {
      boxed[i] = values[i];
    }
    return Arrays.asList(boxed);
  }
}
