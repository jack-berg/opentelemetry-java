/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.sdk.extension.incubator.fileconfig;

import static java.util.stream.Collectors.toSet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.yaml.snakeyaml.Yaml;

class YamlJsonSchemaValidator {

  private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());
  private static final Yaml YAML = new Yaml();

  private final JsonSchema jsonSchema;
  private final String resourceDir;

  YamlJsonSchemaValidator(
      File schemaDirectory, String baseUri, File schemaFile, String resourceDir) {
    Map<String, String> uriMapping = new HashMap<>();
    for (File schema : schemaDirectory.listFiles()) {
      uriMapping.put(baseUri + "/" + schema.getName().split("\\.")[0], schema.toURI().toASCIIString());
    }

    System.out.println(uriMapping);
    JsonSchemaFactory jsonSchemaFactory =
        JsonSchemaFactory.builder(
                // V202012 has a bug where items is not validated
                JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V201909))
            .objectMapper(MAPPER)
            .addUriMappings(uriMapping)
            .build();
    try {
      jsonSchema = jsonSchemaFactory.getSchema(Files.newInputStream(schemaFile.toPath()));
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to initialize validator", e);
    }
    this.resourceDir = resourceDir;
  }

  Set<String> validate(String resourceFile) {
    InputStream inputStream;
    try {
      URI uri = YamlJsonSchemaValidator.class.getResource(resourceDir + "/" + resourceFile).toURI();
      inputStream = Files.newInputStream(new File(uri).toPath());
    } catch (URISyntaxException | IOException e) {
      throw new IllegalArgumentException("Unable to load resource file");
    }
    return validate(inputStream);
  }

  Set<String> validate(InputStream yaml) {
    // Load yaml and write it as string to resolve anchors
    Object yamlObj = YAML.load(yaml);
    try {
      String yamlStr = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(yamlObj);
      return jsonSchema.validate(MAPPER.readTree(yamlStr)).stream()
          .map(ValidationMessage::toString)
          .collect(toSet());
    } catch (IOException e) {
      throw new IllegalStateException("Unable to parse yaml", e);
    }
  }
}
