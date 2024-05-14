/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.autoconfigure.spi.internal;

import static io.opentelemetry.api.internal.ConfigUtil.defaultIfNull;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurationException;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An interface for accessing structured configuration data.
 *
 * <p>An instance of {@link StructuredConfigProperties} is equivalent to a <a
 * href="https://yaml.org/spec/1.2.2/#3211-nodes">YAML mapping node</a>. It has accessors for
 * reading scalar properties, {@link #getStructured(String)} for reading children which are
 * themselves mappings, and {@link #getStructuredList(String)} for reading children which are
 * sequences of mappings.
 */
public interface StructuredConfigProperties {

  /**
   * Returns a {@link String} configuration property.
   *
   * @return null if the property has not been configured
   * @throws ConfigurationException if the property is not a valid scalar string
   */
  @Nullable
  String getString(String name);

  /**
   * Returns a {@link String} configuration property.
   *
   * @return a {@link String} configuration property or {@code defaultValue} if a property with
   *     {@code name} has not been configured
   * @throws ConfigurationException if the property is not a valid scalar string
   */
  default String getString(String name, String defaultValue) {
    return defaultIfNull(getString(name), defaultValue);
  }

  /**
   * Returns a {@link Boolean} configuration property. Implementations should use the same rules as
   * {@link Boolean#parseBoolean(String)} for handling the values.
   *
   * @return null if the property has not been configured
   * @throws ConfigurationException if the property is not a valid scalar boolean
   */
  @Nullable
  Boolean getBoolean(String name);

  /**
   * Returns a {@link Boolean} configuration property.
   *
   * @return a {@link Boolean} configuration property or {@code defaultValue} if a property with
   *     {@code name} has not been configured
   * @throws ConfigurationException if the property is not a valid scalar boolean
   */
  default boolean getBoolean(String name, boolean defaultValue) {
    return defaultIfNull(getBoolean(name), defaultValue);
  }

  /**
   * Returns a {@link Integer} configuration property.
   *
   * @return null if the property has not been configured
   * @throws ConfigurationException if the property is not a valid scalar integer
   */
  @Nullable
  Integer getInt(String name);

  /**
   * Returns a {@link Integer} configuration property.
   *
   * @return a {@link Integer} configuration property or {@code defaultValue} if a property with
   *     {@code name} has not been configured
   * @throws ConfigurationException if the property is not a valid scalar integer
   */
  default int getInt(String name, int defaultValue) {
    return defaultIfNull(getInt(name), defaultValue);
  }

  /**
   * Returns a {@link Long} configuration property.
   *
   * @return null if the property has not been configured
   * @throws ConfigurationException if the property is not a valid scalar long
   */
  @Nullable
  Long getLong(String name);

  /**
   * Returns a {@link Long} configuration property.
   *
   * @return a {@link Long} configuration property or {@code defaultValue} if a property with {@code
   *     name} has not been configured
   * @throws ConfigurationException if the property is not a valid scalar long
   */
  default long getLong(String name, long defaultValue) {
    return defaultIfNull(getLong(name), defaultValue);
  }

  /**
   * Returns a {@link Double} configuration property.
   *
   * @return null if the property has not been configured
   * @throws ConfigurationException if the property is not a valid scalar double
   */
  @Nullable
  Double getDouble(String name);

  /**
   * Returns a {@link Double} configuration property.
   *
   * @return a {@link Double} configuration property or {@code defaultValue} if a property with
   *     {@code name} has not been configured
   * @throws ConfigurationException if the property is not a valid scalar double
   */
  default double getDouble(String name, double defaultValue) {
    return defaultIfNull(getDouble(name), defaultValue);
  }

  /**
   * Returns a {@link List} configuration property. Empty values will be removed. Entries which are
   * not strings are converted to their string representation.
   *
   * @return a {@link List} configuration property, or null if the property has not been configured
   * @throws ConfigurationException if the property is not a valid sequence of scalars
   */
  @Nullable
  List<String> getScalarList(String name);

  /**
   * Returns a {@link List} configuration property. Entries which are not strings are converted to
   * their string representation.
   *
   * @see ConfigProperties#getList(String name)
   * @return a {@link List} configuration property or {@code defaultValue} if a property with {@code
   *     name} has not been configured
   * @throws ConfigurationException if the property is not a valid sequence of scalars
   */
  default List<String> getScalarList(String name, List<String> defaultValue) {
    return defaultIfNull(getScalarList(name), defaultValue);
  }

  /**
   * Returns a {@link StructuredConfigProperties} configuration property.
   *
   * @return a map-valued configuration property, or {@code null} if {@code name} has not been
   *     configured
   * @throws ConfigurationException if the property is not a mapping
   */
  @Nullable
  StructuredConfigProperties getStructured(String name);

  /**
   * Returns a list of {@link StructuredConfigProperties} configuration property.
   *
   * @return a list of map-valued configuration property, or {@code null} if {@code name} has not
   *     been configured
   * @throws ConfigurationException if the property is not a sequence of mappings
   */
  @Nullable
  List<StructuredConfigProperties> getStructuredList(String name);
}
