plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Micrometer Migration"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
val micrometerVersion = "1.12.+"
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:latest.integration")
    implementation("org.openrewrite:rewrite-templating:latest.integration")
    compileOnly("com.google.errorprone:error_prone_core:2.19.1:with-dependencies") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }

    implementation("io.dropwizard.metrics:metrics-core:4.2.23")

    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0")
    compileOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    compileOnly("io.micrometer:micrometer-core:${micrometerVersion}")
    compileOnly("io.prometheus:simpleclient:latest.release")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("org.openrewrite:rewrite-kotlin:latest.release")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("io.micrometer:micrometer-registry-prometheus:${micrometerVersion}")
    testImplementation("com.google.guava:guava:latest.release")

    testRuntimeOnly("org.jetbrains.kotlin:kotlin-stdlib-common:1.9.0")
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    testRuntimeOnly("com.squareup.misk:misk-metrics:2023.09.27.194750-c3aa143")
}
