import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// « CastBridge Dev » : outil de DÉVELOPPEMENT, jamais distribué. Application séparée de CastBridge-TV (elle survit à sa désinstallation) : un serveur SSH permanent
// par clé (réseau local seulement) et des commandes `cbdev` pour installer / désinstaller des APK. Les clés publiques autorisées sont lues, au build, dans un fichier
// HORS dépôt (par défaut ~/.ssh/id_ed25519.pub ; -PdevKeysFile=...). Voir docs/DEV-BRIDGE.md.
android {
    val versions = Properties().apply { File(rootDir.parentFile, "version.properties").takeIf { it.isFile }?.reader()?.use { load(it) } }
    fun ver(key: String) = versions.getProperty(key) ?: throw GradleException("version.properties : « $key » manquant")
    namespace = "castbridge.dev"
    compileSdk = 35
    defaultConfig {
        applicationId = "castbridge.dev"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("castbridge.devVersionCode") as String?)?.toInt() ?: ver("dev.versionCode").toInt()
        versionName = (project.findProperty("castbridge.devVersionName") as String?) ?: ver("dev.versionName")
        val keysFile = (project.findProperty("devKeysFile") as String?)?.let { File(it) } ?: File(System.getProperty("user.home"), ".ssh/id_ed25519.pub")
        val keys = if (keysFile.isFile) keysFile.readLines().map { it.trim() }.filter { it.startsWith("ssh-") }.joinToString("\\n") else ""
        buildConfigField("String", "DEV_SSH_KEYS", "\"$keys\"")
    }
    buildFeatures { buildConfig = true }
    buildTypes { release { isMinifyEnabled = false } }
    packaging { resources { excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/INDEX.LIST", "META-INF/*.kotlin_module", "META-INF/versions/**") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":sshd"))
}
