plugins {
  id("otel.java-conventions")
  war
}

description = "TODO"
otelJava.moduleName.set("io.opentelemetry.integration.tests.tomcat")

dependencies {
  implementation("org.apache.tomcat:tomcat-jsp-api:10.1.39")
  implementation("org.apache.tomcat:tomcat-jasper:10.1.39")
  implementation(project(":sdk:all"))
  implementation(project(":exporters:otlp:all"))
}

// Skip OWASP dependencyCheck task on test module
dependencyCheck {
  skip = true
}

tasks {
  withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.release.set(11)
  }
}
