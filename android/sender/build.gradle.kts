plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "castbridge.sender"
    compileSdk = 35
    defaultConfig {
        applicationId = "castbridge.sender"
        minSdk = 26
        targetSdk = 35
        // -Pcastbridge.versionCode / -Pcastbridge.versionName: build a higher version to test the automatic updates
        versionCode = (project.findProperty("castbridge.versionCode") as String?)?.toInt() ?: 15
        versionName = (project.findProperty("castbridge.versionName") as String?) ?: "1.2.5-beta"
        // For a LOCAL test server only (docs/API-SERVER.md, « Tester en local »): -Pcastbridge.serverUrl=http://10.0.2.2:7090 and
        // -Pcastbridge.extraUpdateKey=<its public key>. Both empty by default: production server and production key only (UpdateKeys).
        buildConfigField("String", "EXTRA_UPDATE_KEY", "\"${(project.findProperty("castbridge.extraUpdateKey") as String?) ?: ""}\"")
        buildConfigField("String", "DEFAULT_SERVER", "\"${(project.findProperty("castbridge.serverUrl") as String?) ?: ""}\"")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    implementation(project(":core"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    // Phone player ("Ouvrir avec"): Media3/ExoPlayer, see docs/PHONE-PLAYER.md for the choice and the formats covered.
    val media3 = "1.5.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.media3:media3-session:$media3")
}
