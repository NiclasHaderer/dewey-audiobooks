plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.shadow)
    application
    // Bundles third-party-licenses.json into the jar for the /api/licenses endpoint
    id("dewey.license-report")
}

application {
    // :taglib reaches TagLib through the FFM API, which needs native access granted.
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true", "--enable-native-access=ALL-UNNAMED")
    mainClass.set("io.dewey.server.ApplicationKt")
    tasks.run.get().workingDir = rootProject.projectDir
}

// Shadow task depends on Jar task, so these configs are reflected for Shadow as well
tasks.jar {
    manifest.attributes["Main-Class"] = "io.dewey.server.ApplicationKt"
    manifest.attributes["Enable-Native-Access"] = "ALL-UNNAMED"
}

tasks.shadowJar {
    // Hoplite's config parsers and Exposed's factories come from ServiceLoader, so their
    // META-INF/services entries must be concatenated, which needs the duplicates kept.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
}

dependencies {
    // Other projects
    implementation(project(":openapi"))
    implementation(project(":auth"))
    implementation(project(":auth-models"))

    // Metadata
    implementation(libs.jsoup)
    implementation(libs.caffeine)

    // Database
    implementation(libs.bundles.exposed)
    // Drivers
    implementation(libs.sqlite.jdbc)
    implementation(libs.hikaricp)
    // Migration
    implementation(libs.classgraph)

    // JWT
    implementation(libs.java.jwt)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.spring.security.core)
    implementation(libs.bundles.bouncycastle)

    // Config
    implementation(libs.bundles.hoplite)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Dependency Injection
    implementation(libs.bundles.koin)

    // Audio file processing
    implementation(project(":taglib"))
    // Folder watching
    implementation(libs.directory.watcher)
    // Search
    implementation(libs.fuzzywuzzy)
    // Logging
    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)
    implementation(libs.slf4j.jul.to.slf4j)
    // Scheduler
    implementation(libs.cron.utils)

    // Ktor
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.partial.content)
    implementation(libs.ktor.server.data.conversion)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.content.negotiation.jvm)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.core.jvm)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.sessions.jvm)
    implementation(libs.ktor.server.host.common.jvm)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.websockets.jvm)
    implementation(libs.ktor.server.netty.jvm)
    implementation(libs.ktor.client.core.jvm)
    implementation(libs.ktor.client.cio.jvm)

    // Openapi
    implementation(libs.swagger.models)
    // Tests
    testImplementation(libs.ktor.server.test.host)
    testImplementation(project(":client"))
}

// The client module compiles the output of this, so it has to run before anything consumes :client.
tasks.register<JavaExec>("generateClients") {
    group = "build"
    description = "Regenerates the Kotlin and TypeScript API clients from the current routes"
    mainClass.set("io.dewey.server.GenerateClientsKt")
    classpath = sourceSets["main"].runtimeClasspath
    workingDir = rootProject.projectDir
}
