import de.undercouch.gradle.tasks.download.Download

plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")

  id("otel.jmh-conventions")
  id("otel.animalsniffer-conventions")

  id("de.undercouch.download")
  id("org.jsonschema2pojo")
}

// SDK modules that are still being developed.

description = "OpenTelemetry SDK Incubator"
otelJava.moduleName.set("io.opentelemetry.sdk.extension.incubator")

dependencies {
  api(project(":sdk:all"))

  compileOnly(project(":sdk:trace-shaded-deps"))

  annotationProcessor("com.google.auto.value:auto-value")

  // io.opentelemetry.sdk.extension.incubator.metric.viewconfig
  implementation(project(":sdk-extensions:autoconfigure-spi"))
  implementation("org.snakeyaml:snakeyaml-engine")

  // io.opentelemetry.sdk.extension.incubator.trace.zpages
  implementation(project(":semconv"))
  compileOnly("com.sun.net.httpserver:http")

  // io.opentelemetry.sdk.extension.incubator.fileconfig
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml")

  testImplementation(project(":sdk:testing"))
  testImplementation(project(":sdk-extensions:autoconfigure"))

  testImplementation("com.google.guava:guava-testlib")
}

val configurationRef = "d018613a1bbac1ca5bbd6edbc7e6effeab3a71d0"
val configurationRepoZip = "https://github.com/open-telemetry/opentelemetry-configuration/archive/$configurationRef.zip"

val downloadConfigurationSchema by tasks.registering(Download::class) {
  src(configurationRepoZip)
  dest("$buildDir/configuration/opentelemetry-configuration.zip")
  overwrite(false)
}

val downloadAndUnzipConfigurationSchema by tasks.registering(Copy::class) {
  dependsOn("downloadConfigurationSchema")
  from(zipTree(downloadConfigurationSchema.get().dest))
  eachFile(closureOf<FileCopyDetails> {
    // Remove the top level folder "/opentelemetry-configuration-$configurationRef"
    val pathParts = path.split("/")
    path = pathParts.subList(1, pathParts.size).joinToString("/")
  })
  into("$buildDir/configuration/")
}

jsonSchema2Pojo {
  sourceFiles = setOf(file("$buildDir/configuration/schema"))
  targetDirectory = file("$buildDir/generated/sources/js2p/java/main")
  targetPackage = "io.opentelemetry.sdk.extension.incubator.fileconfig.internal.model"
  includeSetters = true
  removeOldOutput = true
}

tasks.getByName("generateJsonSchema2Pojo").dependsOn(downloadAndUnzipConfigurationSchema)
tasks.getByName("sourcesJar").dependsOn("generateJsonSchema2Pojo")

// Exclude jsonschema2pojo generated sources from checkstyle
tasks.named<Checkstyle>("checkstyleMain") {
  exclude("**/fileconfig/internal/model/**")
}

tasks {
  withType<Test>().configureEach {
    environment(
      mapOf(
        // Expose the kitchen sink example file to tests
        "CONFIG_EXAMPLE_FILE" to "$buildDir/configuration/kitchen-sink-example.yaml"
      )
    )
  }
}
