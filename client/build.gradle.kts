plugins {
    kotlin("jvm")
    id("maven-publish")
    kotlin("plugin.serialization")
}

afterEvaluate {
    publishing {
        publications {
            // publish to jitpack
            create<MavenPublication>("maven") {
                groupId = "com.github.niclashaderer"
                artifactId = "client"
                version = "0.0.1"
                from(components["java"])
            }
        }
    }
}

// The sources in io/dewey/client/gen are written by the server's generateClients task
tasks.named("compileKotlin") {
    dependsOn(":server:generateClients")
}

dependencies {
    api(project(":openapi-models"))
    api(libs.arrow.core)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlin.reflect)
}
