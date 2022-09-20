plugins {
  id("otel.java-conventions")

  id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry SDK File Config"
otelJava.moduleName.set("io.opentelemetry.sdk.extension.fileconfig")

dependencies {
  implementation("org.yaml:snakeyaml")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.10.1")
  implementation("com.networknt:json-schema-validator:1.0.72")

  testImplementation("com.google.guava:guava-testlib")
}

tasks {
  test {
    System.out.println(project.projectDir)
    systemProperty("otel.sdk-schema-dir", project.projectDir.toString() + "/sdk-schema")
  }
}
