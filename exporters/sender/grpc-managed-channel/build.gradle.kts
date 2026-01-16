plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")

  id("otel.animalsniffer-conventions")
}

description = "OpenTelemetry gRPC Upstream Sender"
otelJava.moduleName.set("io.opentelemetry.exporter.sender.grpc.managedchannel.internal")

dependencies {
  implementation(project(":exporters:sender:api"))

  implementation("io.grpc:grpc-stub")
}
