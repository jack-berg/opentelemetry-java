plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")

  id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry Senders API"
otelJava.moduleName.set("io.opentelemetry.exporter.sender")

dependencies {
  api(project(":sdk:common"))
}
