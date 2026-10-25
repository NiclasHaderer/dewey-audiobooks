plugins {
    kotlin("jvm")
    id("maven-publish")
    kotlin("plugin.serialization")
}

dependencies {
    api(project(":openapi-models"))
}

afterEvaluate {
    publishing {
        publications {
            // publish to jitpack
            create<MavenPublication>("maven") {
                groupId = "com.github.niclashaderer"
                artifactId = "auth-models"
                version = "0.0.1"
                from(components["java"])
            }
        }
    }
}
