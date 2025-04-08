plugins {
  id("otel.java-conventions")
  application
}

description = "TODO"
otelJava.moduleName.set("io.opentelemetry.integration.tests.tomcat")

dependencies {
  implementation("org.apache.tomcat.embed:tomcat-embed-core:10.1.39")
}

// Skip OWASP dependencyCheck task on test module
dependencyCheck {
  skip = true
}

application {
  mainClass = "io.opentelemetry.integrationtests.tomcat.Tomcat"
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
