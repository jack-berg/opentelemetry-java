import io.opentelemetry.gradle.OtelVersionClassPlugin

plugins {
  id("otel.java-conventions")
  id("otel.publish-conventions")
  id("otel.animalsniffer-conventions")
  id("otel.jmh-conventions")
}
apply<OtelVersionClassPlugin>()

description = "OpenTelemetry SDK Common"
otelJava.moduleName.set("io.opentelemetry.sdk.common")
otelJava.osgiOptionalPackages.set(listOf("io.opentelemetry.api.incubator"))

dependencies {
  api(project(":api:all"))
  compileOnly(project(":api:incubator"))

  annotationProcessor("com.google.auto.value:auto-value")

  testAnnotationProcessor("com.google.auto.value:auto-value")

  testImplementation(project(":sdk:testing"))
  testImplementation("com.google.guava:guava-testlib")
  testImplementation("io.opentelemetry.semconv:opentelemetry-semconv-incubating")
}

// Copies ArrayBackedAttributesBuilder.java and ArrayBackedAttributes.java from the api:all module
// into a generated source set, rewriting only the package header (and injecting the api:all
// imports the original classes didn't need) and promoting each class to public. The SDK uses the
// copied ArrayBackedAttributesBuilder as its mutable, capacity-bounded Attributes implementation
// (formerly AttributesMap). The originals stay package-private in api:all so nothing new leaks
// into the api's public surface.
val generatedAttributesDir =
  layout.buildDirectory.dir("generated/sources/attributes/java/main")
val copyAttributesSources = tasks.register<Copy>("copyAttributesSources") {
  from(
    project(":api:all").file(
      "src/main/java/io/opentelemetry/api/common/ArrayBackedAttributesBuilder.java"
    ),
    project(":api:all").file(
      "src/main/java/io/opentelemetry/api/common/ArrayBackedAttributes.java"
    )
  )
  into(generatedAttributesDir.map { it.dir("io/opentelemetry/sdk/common/internal") })
  filter { line: String ->
    line
      .replace(
        "package io.opentelemetry.api.common;",
        "package io.opentelemetry.sdk.common.internal;\n\nimport io.opentelemetry.api.common.AttributeKey;\nimport io.opentelemetry.api.common.AttributeType;\nimport io.opentelemetry.api.common.Attributes;\nimport io.opentelemetry.api.common.AttributesBuilder;\nimport io.opentelemetry.api.common.Value;\nimport io.opentelemetry.api.common.ValueType;"
      )
      // The source classes are package-private (kept internal to api:all). The SDK copies need to
      // be referenced from sdk:trace and sdk:logs, so promote them to public.
      .replace("final class ArrayBackedAttributes ", "public final class ArrayBackedAttributes ")
      .replace(
        "class ArrayBackedAttributesBuilder ",
        "public class ArrayBackedAttributesBuilder "
      )
  }
}

sourceSets {
  main {
    java {
      srcDir(copyAttributesSources)
    }
  }
}

// Import order in the copied sources does not match the project's checkstyle rules (imports are
// injected right after the package header). Excluding the generated files is simpler than
// producing correctly-ordered imports from the copy filter.
// The copied sources are checkstyle-clean apart from import order (imports are injected right
// after the package header rather than merged into the STATIC group). The originals with the same
// filenames live in api:all and are not part of this task's source set, so a filename-scoped
// exclude here won't hide checkstyle issues in real sdk-common files.
tasks.named<Checkstyle>("checkstyleMain") {
  exclude("**/ArrayBackedAttributes.java", "**/ArrayBackedAttributesBuilder.java")
}

tasks {
  test {
    // For checking version number included in Resource.
    systemProperty("otel.test.project-version", project.version.toString())
  }
}
