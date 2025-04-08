plugins {
  id("otel.java-conventions")
  id("com.gradleup.shadow") version "8.3.5"
}

description = "TODO"
otelJava.moduleName.set("io.opentelemetry.integration.tests.tomcat")

dependencies {
  api("org.testcontainers:junit-jupiter")

  implementation("org.apache.tomcat.embed:tomcat-embed-core:10.0.0")

  implementation(project(":sdk:all"))
  implementation(project(":exporters:otlp:all"))
}

apply(plugin = "com.gradleup.shadow")

// Skip OWASP dependencyCheck task on test module
dependencyCheck {
  skip = true
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
  jvmArgs("--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED")
}

tasks {
  withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.release.set(11)
  }
}
