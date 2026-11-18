plugins {
    id("com.android.application")
}

android {
    namespace = "com.davide.seddio.facespike"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Deliberately not com.davide.seddio.easygallery: installing the harness must never replace or wipe the app.
        applicationId = "com.davide.seddio.facespike"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "spike"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild { cmake { arguments += "-DCMAKE_BUILD_TYPE=Release" } }
    }

    ndkVersion = "27.1.12297006"
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    // Apache-2.0. Carries libopencv_java4.so for four ABIs; abiFilters keeps arm64 only, the one the phones use.
    implementation("org.opencv:opencv:4.13.0")
}
