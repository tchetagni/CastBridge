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
        // -Pcastbridge.versionCode / -Pcastbridge.versionName: build a higher version to test the automatic updates
        versionCode = (project.findProperty("castbridge.versionCode") as String?)?.toInt() ?: 21
        versionName = (project.findProperty("castbridge.versionName") as String?) ?: "0.11.1"
        // For a LOCAL test server only (docs/API-SERVER.md, « Tester en local »): -Pcastbridge.serverUrl=http://10.0.2.2:7090 and
        // -Pcastbridge.extraUpdateKey=<its public key>. Both empty by default: the apps built for Esaie talk to
        // https://bridge.sti-cm.com and trust the production key alone (UpdateKeys).
        buildConfigField("String", "EXTRA_UPDATE_KEY", "\"${(project.findProperty("castbridge.extraUpdateKey") as String?) ?: ""}\"")
        buildConfigField("String", "DEFAULT_SERVER", "\"${(project.findProperty("castbridge.serverUrl") as String?) ?: ""}\"")
    }
    buildFeatures { buildConfig = true }
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
    // aria2c ships as jniLibs/<abi>/libaria2c.so and is executed from nativeLibraryDir: it must be extracted at install.
    packaging { jniLibs { useLegacyPackaging = true } }
    packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST", "META-INF/*.kotlin_module") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
// Optional: rebuilds aria2c from its official sources (NDK r26+ required). `gradle :receiver:buildAria2`
tasks.register<Exec>("buildAria2") {
    group = "castbridge"
    description = "Builds jniLibs/<abi>/libaria2c.so from the pinned official sources (tools/build-aria2-android.sh)"
    workingDir = rootProject.projectDir.parentFile
    commandLine("bash", "tools/build-aria2-android.sh")
}
dependencies {
    implementation(project(":core"))
    implementation(project(":sshd"))
    implementation("org.videolan.android:libvlc-all:3.6.5")
    // Library grid on the TV: only the visible cards exist (plain Views, no Compose: see docs/ADMIN.md, "Bibliothèque")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
