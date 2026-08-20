import org.gradle.api.JavaVersion

plugins {
  id("otel.java-conventions")
  // id("otel.publish-conventions")
}

description = "OpenTelemetry - ProcessContext SDK"
otelJava.moduleName.set("io.opentelemetry.sdk.processcontext")
otelJava.minJavaVersionSupported.set(JavaVersion.VERSION_25)

dependencies {
  api(project(":sdk:common"))
  api(project(":exporters:common")) // MessageWriter

  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":exporters:otlp:common"))

  testImplementation("io.opentelemetry.proto:opentelemetry-proto")
  testImplementation("com.google.protobuf:protobuf-java-util")
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }
}
