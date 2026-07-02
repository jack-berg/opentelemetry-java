/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.common.internal;

import static io.opentelemetry.api.common.AttributeKey.booleanKey;
import static io.opentelemetry.api.common.AttributeKey.longKey;
import static io.opentelemetry.api.common.AttributeKey.stringKey;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import org.junit.jupiter.api.Test;

class ArrayBackedAttributesBuilderTest {

  @Test
  void asMap() {
    ArrayBackedAttributesBuilder attributes =
        ArrayBackedAttributesBuilder.create(2, Integer.MAX_VALUE);
    attributes.put(longKey("one"), 1L);
    attributes.put(longKey("two"), 2L);

    assertThat(attributes.asMap())
        .containsOnly(entry(longKey("one"), 1L), entry(longKey("two"), 2L));
  }

  @Test
  void sameNameDifferentType_lastValueWins() {
    ArrayBackedAttributesBuilder attributes =
        ArrayBackedAttributesBuilder.create(10, Integer.MAX_VALUE);
    attributes.put(stringKey("k"), "hello");
    attributes.put(booleanKey("k"), true);

    assertThat(attributes.size()).isEqualTo(1);
    assertThat(attributes.get(booleanKey("k"))).isEqualTo(true);
    assertThat(attributes.get(stringKey("k"))).isNull();
  }

  @Test
  void sameNameDifferentType_doesNotConsumeExtraCapacity() {
    ArrayBackedAttributesBuilder attributes =
        ArrayBackedAttributesBuilder.create(2, Integer.MAX_VALUE);
    attributes.put(stringKey("a"), "v1");
    attributes.put(booleanKey("a"), false); // overwrite - not a new capacity slot
    attributes.put(longKey("b"), 42L);

    assertThat(attributes.size()).isEqualTo(2);
    assertThat(attributes.get(booleanKey("a"))).isEqualTo(false);
    assertThat(attributes.get(longKey("b"))).isEqualTo(42L);
  }

  @Test
  void capacityDropsOverflow() {
    ArrayBackedAttributesBuilder attributes =
        ArrayBackedAttributesBuilder.create(2, Integer.MAX_VALUE);
    attributes.put(stringKey("a"), "v1");
    attributes.put(stringKey("b"), "v2");
    attributes.put(stringKey("c"), "v3"); // dropped

    assertThat(attributes.size()).isEqualTo(2);
    assertThat(attributes.getTotalAddedValues()).isEqualTo(3);
    assertThat(attributes.get(stringKey("c"))).isNull();
  }

  @Test
  void lengthLimitTruncatesStringValues() {
    ArrayBackedAttributesBuilder attributes = ArrayBackedAttributesBuilder.create(10, 3);
    attributes.put(stringKey("k"), "hello");
    assertThat(attributes.get(stringKey("k"))).isEqualTo("hel");
  }

  @Test
  void immutableCopy_producesDedupedAttributes() {
    ArrayBackedAttributesBuilder attributes =
        ArrayBackedAttributesBuilder.create(10, Integer.MAX_VALUE);
    attributes.put(stringKey("k"), "hello");
    attributes.put(booleanKey("k"), true);

    assertThat(attributes.immutableCopy().size()).isEqualTo(1);
    assertThat(attributes.immutableCopy().get(booleanKey("k"))).isEqualTo(true);
  }
}
