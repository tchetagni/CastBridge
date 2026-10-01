import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// « CastBridge Propriétaire » : console de génération des activations (docs/OWNER-CONSOLE.md). Application SÉPARÉE, jamais publiée sur le canal de mise à jour,
// sans aucune permission réseau (outil hors ligne), avec sa propre clé de signature (une clé par outil).
android {
    val versions = Properties().apply { File(rootDir.parentFile, "version.properties").takeIf { it.isFile }?.reader()?.use { load(it) } }
    fun ver(key: String) = versions.getProperty(key) ?: throw GradleException("version.properties : « $key » manquant")
    namespace = "castbridge.owner"
    compileSdk = 35
    defaultConfig {
        applicationId = "castbridge.owner"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("castbridge.ownerVersionCode") as String?)?.toInt() ?: ver("owner.versionCode").toInt()
        versionName = (project.findProperty("castbridge.ownerVersionName") as String?) ?: ver("owner.versionName")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}
dependencies {
    implementation(project(":ownerlib"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}
