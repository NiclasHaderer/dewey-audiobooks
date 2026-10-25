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

// The sources are written by the server's generateClients task, and formatted right after so a build leaves the
// committed client unchanged
val formatGeneratedClient =
    tasks.named("runKtlintFormatOverMainSourceSet") {
        dependsOn(":server:generateClients")
    }
tasks.named("compileKotlin") {
    dependsOn(formatGeneratedClient)
}
tasks.named("runKtlintCheckOverMainSourceSet") {
    mustRunAfter(formatGeneratedClient)
}

dependencies {
    api(project(":openapi-models"))
    api(libs.arrow.core)
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.kotlin.reflect)
}
