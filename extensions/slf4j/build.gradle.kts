plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")

  id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry Extension: SLF4J"
otelJava.moduleName.set("io.opentelemetry.extension.slf4j")

dependencies {
  api(project(":api:all"))

  compileOnly("org.slf4j:slf4j-api:2.0.17")
}
