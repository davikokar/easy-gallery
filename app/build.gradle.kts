import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Fails the build when a face model differs from the bytes SHA256SUMS names (ADR-0023 §1).
abstract class VerifyFaceModelChecksums : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val faceAssets: DirectoryProperty

    @get:OutputFile
    abstract val verifiedMarker: RegularFileProperty

    @TaskAction
    fun verify() {
        val directory = faceAssets.get().asFile
        val expected = File(directory, "SHA256SUMS").readLines()
            .filter { it.isNotBlank() }
            .associate { line ->
                val parts = line.trim().split(Regex("\\s+"), limit = 2)
                require(parts.size == 2) { "Malformed line in SHA256SUMS" }
                parts[1].trimStart('*') to parts[0].lowercase()
            }
        val present = directory.listFiles { file -> file.extension == "onnx" }.orEmpty().map { it.name }.toSet()
        val unlisted = present - expected.keys
        check(unlisted.isEmpty()) { "Model files with no checksum in SHA256SUMS: $unlisted" }

        for ((name, hash) in expected) {
            val file = File(directory, name)
            check(file.isFile) { "Model file listed in SHA256SUMS is missing: $name" }
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual == hash) { "SHA-256 mismatch for $name: expected $hash but the file is $actual" }
        }
        verifiedMarker.get().asFile.writeText("verified\n")
    }
}

val verifyFaceModelChecksums = tasks.register<VerifyFaceModelChecksums>("verifyFaceModelChecksums") {
    faceAssets.set(layout.projectDirectory.dir("src/main/assets/face"))
    verifiedMarker.set(layout.buildDirectory.file("face-models/verified"))
}

tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(verifyFaceModelChecksums)
    }
}

android {
    namespace = "com.davide.seddio.easygallery"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.davide.seddio.easygallery"
        minSdk = 28
        targetSdk = 36
        versionCode = 9
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                keepRules {
                    files.add(file("proguard-face.pro"))
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    bundle {
        // The in-app "Change language" feature overrides the locale independently of the
        // device's configured languages, so Play must not strip per-language config splits:
        // otherwise a locale the device wasn't already using has no resources to switch to.
        language {
            enableSplit = false
        }
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            // The app ships OpenCV's arm64-v8a build only (the AAR has four ABIs). Left out by packaging, never by an
            // app-wide abiFilters: that would make Play stop offering the app to every other ABI (ADR-0023 §2).
            // The libc++_shared.so excludes apply app-wide: a future dependency's copy under these ABIs is dropped too.
            excludes += listOf("armeabi-v7a", "x86", "x86_64").flatMap { abi ->
                listOf("lib/$abi/libopencv_java4.so", "lib/$abi/libc++_shared.so")
            }
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.0")
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-reflect:2.1.0")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.exifinterface)
    // Declared only to override the outdated fragment version appcompat pulls in via Coil.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.video)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.billing.ktx)
    implementation(libs.opencv)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.lifecycle.runtime.testing)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.mockkAndroid)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}