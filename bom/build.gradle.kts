plugins {
  id("otel.bom-conventions")
}

description = "OpenTelemetry Bill of Materials"
group = "io.opentelemetry"
base.archivesName.set("opentelemetry-bom")

otelBom.projectFilter.set { !it.hasProperty("otel.release") }

javaPlatform.allowDependencies()

dependencies {
  // Add dependency on opentelemetry-bom to ensure synchronization between alpha and stable artifacts
  api(platform("org.springframework.boot:spring-boot-dependencies:3.5.3"))
}
