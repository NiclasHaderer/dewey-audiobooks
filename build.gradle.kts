import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.KtlintPlugin
import org.jlleitschuh.gradle.ktlint.tasks.KtLintFormatTask

val kotlinVersion = libs.versions.kotlin.get()
val ktlintVersion = libs.versions.ktlint.get()

plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint)
}

subprojects {
    group = "io.dewey"
    version = "0.0.1"

    apply<KtlintPlugin>()
    configure<KtlintExtension> {
        version.set(ktlintVersion)
    }
    // Regenerated code repeats the unformatted input of the last run, which Gradle would take as up to date
    tasks.withType<KtLintFormatTask>().configureEach {
        outputs.upToDateWhen { false }
    }

    plugins.withType<JavaPlugin> {
        configure<JavaPluginExtension> {
            toolchain { languageVersion.set(JavaLanguageVersion.of(25)) }
            sourceCompatibility = JavaVersion.VERSION_25
            targetCompatibility = JavaVersion.VERSION_25
        }
        dependencies {
            "testImplementation"(libs.kotlin.test.junit5)
            "testImplementation"(platform(libs.junit.bom))
            "testRuntimeOnly"(libs.junit.platform.launcher)
        }
    }
    // Koin, Exposed's TransactionManager and the pipeline threads are all JVM-global, so classes may only
    // run side by side in separate JVMs
    tasks.withType<Test>().configureEach {
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        // Tests tagged "live" call third-party services, which rate limit or block CI runners
        useJUnitPlatform {
            if (providers.gradleProperty("skipLiveTests").isPresent) excludeTags("live")
        }
    }
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
            freeCompilerArgs.add("-Xcontext-parameters")
            freeCompilerArgs.add("-Xmulti-dollar-interpolation")
            jvmTarget.set(JvmTarget.JVM_25)
            apiVersion.set(KotlinVersion.fromVersion(kotlinVersion.substringBeforeLast('.')))
            languageVersion.set(KotlinVersion.fromVersion(kotlinVersion.substringBeforeLast('.')))
            optIn.add("kotlin.RequiresOptIn")
        }
    }
}
