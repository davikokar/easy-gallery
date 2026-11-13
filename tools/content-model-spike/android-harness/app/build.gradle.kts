plugins {
    id("com.android.application")
}

android {
    namespace = "com.davide.seddio.contentspike"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        // Deliberately not com.davide.seddio.easygallery: installing the harness must never replace or wipe the app.
        applicationId = "com.davide.seddio.contentspike"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "spike"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.32.0")
}
