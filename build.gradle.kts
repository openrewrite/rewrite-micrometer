plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("org.openrewrite.build.moderne-source-available-license") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Micrometer Migration"

recipeDependencies {
    parserClasspath("io.micrometer:micrometer-commons:1.11.3")
    parserClasspath("io.micrometer:micrometer-core:1.11.3")
    parserClasspath("io.micrometer:micrometer-observation:1.11.3")
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
val micrometerVersion = "1.12.+"
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:latest.integration")
    implementation("org.openrewrite:rewrite-templating:latest.integration")
    compileOnly("com.google.errorprone:error_prone_core:2.+") {
        exclude("com.google.auto.service", "auto-service-annotations")
        exclude("io.github.eisop","dataflow-errorprone")
    }

    implementation("io.dropwizard.metrics:metrics-core:4.2.23")

    compileOnly("io.micrometer:micrometer-core:${micrometerVersion}")
    compileOnly("io.prometheus:simpleclient:latest.release")

    testImplementation("org.openrewrite:rewrite-java-21")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-kotlin")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.14.2")

    testImplementation("io.micrometer:micrometer-registry-prometheus:${micrometerVersion}")
    testImplementation("com.google.guava:guava:latest.release")

    testRuntimeOnly("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    testRuntimeOnly("com.squareup.misk:misk-metrics:2023.09.27.194750-c3aa143")
}
