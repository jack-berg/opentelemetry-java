plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
}

description = "OpenTelemetry - ProcessContext SDK"
otelJava.moduleName.set("io.opentelemetry.sdk.processcontext")
otelJava.osgiOptionalPackages.set(listOf("io.opentelemetry.sdk.autoconfigure.declarativeconfig"))
otelJava.osgiServiceLoaderProvides.set(listOf(
  "io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider",
  "io.opentelemetry.sdk.autoconfigure.declarativeconfig.DeclarativeConfigurationCustomizerProvider",
))

dependencies {
  api(project(":sdk:all"))
  api(project(":exporters:common")) // MessageWriter

  annotationProcessor("com.google.auto.value:auto-value")

  implementation(project(":exporters:otlp:common"))
  implementation(project(":api:incubator")) // ExtendedOpenTelemetry (parent of ExtendedOpenTelemetrySdk)

  compileOnly(project(":sdk-extensions:declarative-config"))
  compileOnly(project(":sdk-extensions:autoconfigure-spi"))

  testImplementation(project(":sdk:testing"))
  testImplementation(project(":sdk-extensions:autoconfigure"))
  testImplementation("io.opentelemetry.proto:opentelemetry-proto")
  testImplementation("com.google.protobuf:protobuf-java-util")
}

java {
  sourceSets {
    create("java25") {
      java {
        srcDir("src/main/java25")
      }
      compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    }
  }
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(25))
  }
}

tasks.named<JavaCompile>("compileJava") {
  options.release.set(8)
}

tasks.named<JavaCompile>("compileJava25Java") {
  options.release.set(25)
}

val testJavaVersion = project.findProperty("testJavaVersion") as String?

testing {
  suites {
    register<JvmTestSuite>("java25Test") {
      sources {
        java {
          setSrcDirs(listOf("src/test/java25"))
        }
      }
      dependencies {
        implementation(project())
        implementation(project(":sdk:testing"))
        implementation(project(":sdk-extensions:autoconfigure"))
        implementation(project(":sdk-extensions:declarative-config"))
        implementation(sourceSets["java25"].output)
      }
      targets {
        all {
          testTask {
            enabled = (testJavaVersion != null && Integer.valueOf(testJavaVersion) >= 25)
            // Enables the Panama publisher so autoconfigure end-to-end tests can observe the
            // OTEL_CTX mapping. Must be set at JVM start because the factory singleton reads it
            // once at class load.
            systemProperty("otel.experimental.process.context.enabled", "true")
          }
        }
      }
    }
  }
}

tasks.named<JavaCompile>("compileJava25TestJava") {
  options.release.set(25)
}

// Default `test` runs OS/JDK-agnostic tests. The java25Test suite covers everything that
// requires Linux + JDK 25 (Panama FFM, /proc/<pid>/maps).

tasks.check {
  dependsOn(testing.suites)
}

tasks.named<Jar>("jar") {
  manifest {
    attributes["Multi-Release"] = "true"
  }
  from(sourceSets.named("java25").get().output) {
    into("META-INF/versions/25")
  }
}
