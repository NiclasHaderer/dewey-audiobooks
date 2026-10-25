plugins {
    kotlin("jvm")
    id("maven-publish")
    kotlin("plugin.serialization")
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.kotlinx.datetime)
}

afterEvaluate {
    publishing {
        publications {
            // publish to jitpack
            create<MavenPublication>("maven") {
                groupId = "com.github.niclashaderer"
                artifactId = "openapi-models"
                version = "0.0.1"
                from(components["java"])
            }
        }
    }
}
