plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "castbridge.receiver"
    compileSdk = 35
    defaultConfig {
        applicationId = "castbridge.receiver"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.3"
    }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }
    buildTypes {
        release {
            // Smaller dex/resources on a small TV. libVLC is reached through JNI: its classes must be kept.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST", "META-INF/*.kotlin_module") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core"))
    implementation(project(":sshd"))
    implementation("org.videolan.android:libvlc-all:3.6.5")
}
