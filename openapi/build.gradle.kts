plugins {
    kotlin("jvm")
    id("maven-publish")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":openapi-models"))
    implementation(libs.kotlin.logging)

    // Get type generators
    implementation(libs.classgraph)

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.data.conversion)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.websockets.jvm)

    // Openapi
    implementation(libs.swagger.models)
    implementation(libs.swagger.core)
    implementation(libs.swagger.ui)

    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    // Tests
    testImplementation(libs.ktor.server.test.host)
}

afterEvaluate {
    publishing {
        publications {
            // publish to jitpack
            create<MavenPublication>("maven") {
                groupId = "com.github.niclashaderer"
                artifactId = "openapi"
                version = "0.0.1"
                from(components["java"])
            }
        }
    }
}
