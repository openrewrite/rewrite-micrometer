plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
}

group = "org.openrewrite.recipe"
description = "Micrometer Migration"

tasks.getByName<JavaCompile>("compileJava") {
    // minimum required for misk use in refaster-style templates
    options.release.set(11)
}

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))
    implementation("org.openrewrite:rewrite-java")
    implementation("org.openrewrite.recipe:rewrite-java-dependencies:$rewriteVersion")

    annotationProcessor("org.openrewrite:rewrite-templating:latest.integration")
    compileOnly("com.google.errorprone:error_prone_core:2.19.1:with-dependencies") {
        exclude("com.google.auto.service", "auto-service-annotations")
    }

    // TODO compileOnly?
    implementation("org.jetbrains.kotlin:kotlin-stlib-common:1.9.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.9.0")
    implementation("com.squareup.misk:misk-metrics:2023.09.27.194750-c3aa143")
    implementation("io.micrometer:micrometer-core:latest.release")
    implementation("io.prometheus:simpleclient:latest.release")

    testImplementation("org.openrewrite:rewrite-java-17")
    testImplementation("org.openrewrite:rewrite-test")
    testImplementation("org.openrewrite:rewrite-gradle")
    testImplementation("org.openrewrite:rewrite-maven")

    testImplementation("org.openrewrite:rewrite-kotlin:latest.release")

    testImplementation("org.junit.jupiter:junit-jupiter-engine:latest.release")

    testImplementation("io.micrometer:micrometer-registry-prometheus:latest.release")
    testImplementation("com.google.guava:guava:latest.release")
}
