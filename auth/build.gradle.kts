plugins {
    kotlin("jvm")
    id("maven-publish")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":openapi"))
    implementation(project(":auth-models"))

    implementation(libs.kotlin.logging)

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.resources)
    implementation(libs.ktor.server.data.conversion)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jvm)
    implementation(libs.ktor.server.auth.jwt)

    // Security
    implementation(libs.spring.security.core)
    implementation(libs.java.jwt)
    implementation(libs.nimbus.jose.jwt)
    implementation(libs.bundles.bouncycastle)
}

afterEvaluate {
    publishing {
        publications {
            // publish to jitpack
            create<MavenPublication>("maven") {
                groupId = "com.github.niclashaderer"
                artifactId = "auth"
                version = "0.0.1"
                from(components["java"])
            }
        }
    }
}
