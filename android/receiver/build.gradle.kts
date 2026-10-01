import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    val versions = Properties().apply { File(rootDir.parentFile, "version.properties").takeIf { it.isFile }?.reader()?.use { load(it) } }
    fun ver(key: String) = versions.getProperty(key) ?: throw GradleException("version.properties : « $key » manquant")
    val locked = (project.findProperty("requireActivation") as String?) == "true"
    namespace = "castbridge.receiver"
    compileSdk = 35
    defaultConfig {
        applicationId = "castbridge.receiver"
        minSdk = 26
        targetSdk = 35
        // -Pcastbridge.versionCode / -Pcastbridge.versionName: build a higher version to test the automatic updates
        versionCode = (project.findProperty("castbridge.versionCode") as String?)?.toInt() ?: (ver("tv.versionCode").toInt() + if (locked) 1 else 0)
        versionName = (project.findProperty("castbridge.versionName") as String?) ?: (ver("tv.versionName") + if (locked) "-verrouillee" else "")
        // For a LOCAL test server only (docs/API-SERVER.md, « Tester en local »): -Pcastbridge.serverUrl=http://10.0.2.2:7090 and
        // -Pcastbridge.extraUpdateKey=<its public key>. Both empty by default: the apps built for Esaie talk to
        // https://bridge.sti-cm.com and trust the production key alone (UpdateKeys).
        buildConfigField("String", "EXTRA_UPDATE_KEY", "\"${(project.findProperty("castbridge.extraUpdateKey") as String?) ?: ""}\"")
        // Activation requirement (docs/TRIAL-EDITION.md § Interrupteur de déploiement): OFF unless -PrequireActivation=true. An UPDATED install (the owner's TV, beta testers) gets
        // ACTIVATION_GRACE_DAYS of grace; a fresh install is locked at once. TRUSTED_KEYS = the PUBLIC keys that may sign activations, read from a file OUTSIDE the repository
        // (~/.castbridge-signing/activation-trusted-keys.txt, one « kid=… pub=… scopes=… » line per tool): empty => nobody can activate (the build then refuses to enable the lock).
        val requireActivation = (project.findProperty("requireActivation") as String?) == "true"
        val trustedFile = (project.findProperty("trustedKeysFile") as String?)?.let { File(it) } ?: File(System.getProperty("user.home"), ".castbridge-signing/activation-trusted-keys.txt")      // -PtrustedKeysFile=… : essais avec une clé jetable
        val trustedKeys = if (trustedFile.isFile) trustedFile.readLines().map { it.trim() }.filter { it.startsWith("kid=") && " pub=" in it }.joinToString("\\n") else ""
        if (requireActivation && trustedKeys.isEmpty()) throw GradleException("requireActivation=true mais aucune clé publique de confiance : créez ~/.castbridge-signing/activation-trusted-keys.txt (lignes « kid=… pub=… scopes=… » données par « Clé publique » de la console ou « cle » de l'outil de bureau). Sans elle, personne ne pourrait activer la TV.")
        buildConfigField("boolean", "REQUIRE_ACTIVATION", requireActivation.toString())
        buildConfigField("int", "ACTIVATION_GRACE_DAYS", ((project.findProperty("castbridge.graceDays") as String?) ?: "30"))
        buildConfigField("String", "TRUSTED_KEYS", "\"$trustedKeys\"")
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
