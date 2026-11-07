plugins {
    kotlin("jvm")
}

// CI drops every target it builds into src/main/resources/native/<target>/; locally only the host is built.
val hostTarget =
    run {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val osName =
            when {
                os.startsWith("mac") || os.startsWith("darwin") -> "macos"
                os.startsWith("win") -> "windows"
                os.startsWith("linux") -> "linux"
                else -> throw GradleException("Unsupported OS for the taglib native build: $os")
            }
        val archName =
            when (arch) {
                "aarch64", "arm64" -> "arm64"
                "x86_64", "amd64" -> "x64"
                else -> throw GradleException("Unsupported architecture for the taglib native build: $arch")
            }
        "$osName-$archName"
    }

val nativeSourceDir = layout.projectDirectory.dir("native")
val nativeBuildDir = layout.buildDirectory.dir("native/$hostTarget")
val nativeResourceDir = layout.buildDirectory.dir("nativeResources")
val cmakeExecutable = providers.gradleProperty("cmake").orElse("cmake")

val cmakeConfigureNativeHost =
    tasks.register<Exec>("cmakeConfigureNativeHost") {
        description = "Configures the CMake build of the bundled TagLib fork for the host platform."
        group = "native"
        inputs.file(nativeSourceDir.file("CMakeLists.txt"))
        outputs.dir(nativeBuildDir)
        executable = cmakeExecutable.get()
        args(
            "-S",
            nativeSourceDir.asFile.absolutePath,
            "-B",
            nativeBuildDir.get().asFile.absolutePath,
            "-DCMAKE_BUILD_TYPE=Release",
        )
        doFirst {
            if (!nativeSourceDir
                    .dir("taglib")
                    .file("CMakeLists.txt")
                    .asFile
                    .exists()
            ) {
                throw GradleException(
                    "TagLib submodule is missing. Run: git submodule update --init --recursive",
                )
            }
        }
    }

val buildNativeHost =
    tasks.register<Exec>("buildNativeHost") {
        description = "Builds libtag_c for the host platform into the module's resources."
        group = "native"
        dependsOn(cmakeConfigureNativeHost)
        inputs.dir(nativeSourceDir.dir("taglib").dir("taglib"))
        inputs.dir(nativeSourceDir.dir("taglib").dir("bindings"))
        outputs.dir(nativeResourceDir)
        executable = cmakeExecutable.get()
        args("--build", nativeBuildDir.get().asFile.absolutePath, "--config", "Release", "--parallel")
        doLast {
            val built =
                nativeBuildDir.get().asFile.walkTopDown().firstOrNull {
                    it.isFile && it.name.matches(Regex("""(lib)?tag_c\.(dylib|so|dll)"""))
                } ?: throw GradleException("CMake build produced no tag_c shared library in ${nativeBuildDir.get()}")
            copy {
                from(built)
                into(nativeResourceDir.get().dir("native/$hostTarget"))
            }
        }
    }

// Not wired into the build: the output is committed, so only a C API change needs jextract.
tasks.register<Exec>("jextract") {
    description = "Regenerates the committed FFM bindings from the TagLib fork's tag_c.h."
    group = "native"
    val generatedDir = layout.projectDirectory.dir("src/main/java")
    executable = providers.gradleProperty("jextract").orElse("jextract").get()
    args(
        "--output",
        generatedDir.asFile.absolutePath,
        "@${nativeSourceDir.file("jextract.args").asFile.absolutePath}",
        nativeSourceDir
            .dir("taglib")
            .dir("bindings/c")
            .file("tag_c.h")
            .asFile.absolutePath,
    )
    doFirst { delete(generatedDir) }
    // The bindings are committed once and shipped to every platform, but jextract hardcodes the
    // host's data model for C long, whose layout is OfInt on Windows and OfLong elsewhere. The
    // declaration is unused, so widening the type keeps the file loadable everywhere.
    doLast {
        val shared = generatedDir.file("io/dewey/taglib/ffi/TagLibC\$shared.java").asFile
        shared.writeText(
            shared.readText().replace(
                "ValueLayout.OfLong C_LONG = (ValueLayout.OfLong)",
                "ValueLayout C_LONG = (ValueLayout)",
            ),
        )
    }
}

sourceSets.main {
    resources.srcDir(nativeResourceDir)
}

tasks.processResources { dependsOn(buildNativeHost) }

tasks.test {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
