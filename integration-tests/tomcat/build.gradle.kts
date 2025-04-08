plugins {
  id("otel.java-conventions")
  application
}

description = "TODO"
otelJava.moduleName.set("io.opentelemetry.integration.tests.tomcat")

dependencies {
  api("org.testcontainers:junit-jupiter")

  implementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.39")
}

// Skip OWASP dependencyCheck task on test module
dependencyCheck {
  skip = true
}

tasks.withType<Test>().configureEach {
  jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
  jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED")
  jvmArgs("--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED")
  // jvmArgs("-Djava.util.logging.config.file=/Users/jberg/code/open-telemetry/opentelemetry-java/integration-tests/tomcat/logging.properties")

  // environment("CATALINA_OUT", "/Users/jberg/code/open-telemetry/opentelemetry-java/integration-tests/tomcat/base/logs")
}

application {
  mainClass = "io.opentelemetry.integrationtests.tomcat.MemoryLeakTest"
  applicationDefaultJvmArgs = listOf(
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.rmi/sun.rmi.transport=ALL-UNNAMED"
  )
}

tasks {
  withType<JavaCompile>().configureEach {
    sourceCompatibility = "11"
    targetCompatibility = "11"
    options.release.set(11)
  }
}
