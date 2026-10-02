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
        // Activation requirement (docs/TRIAL-EDITION.md § Interrupteur de déploiement): OFF unless -PrequireActivation=true. An install that PREDATES the lock (firstInstallTime < LOCK_GRACE_START_MS: the owner's TV, beta testers) gets
        // grace until LOCK_GRACE_START_MS + ACTIVATION_GRACE_DAYS (absolute); any later install is locked at once. TRUSTED_KEYS = the PUBLIC keys that may sign activations, read from a file OUTSIDE the repository
        // (~/.castbridge-signing/activation-trusted-keys.txt, one « kid=… pub=… scopes=… » line per tool): empty => nobody can activate (the build then refuses to enable the lock).
        val requireActivation = (project.findProperty("requireActivation") as String?) == "true"
        val trustedFile = (project.findProperty("trustedKeysFile") as String?)?.let { File(it) } ?: File(System.getProperty("user.home"), ".castbridge-signing/activation-trusted-keys.txt")      // -PtrustedKeysFile=… : essais avec une clé jetable
        val trustedLines = if (trustedFile.isFile) trustedFile.readLines().map { it.trim() }.filter { it.startsWith("kid=") && " pub=" in it } else emptyList()
        // A line without « scopes= » is accepted by the app but grants NO scope (fail closed): warn at build so a mistyped line is noticed (the TV could not accept anything signed by that key).
        trustedLines.filter { l -> l.split(' ', '\t').none { it.startsWith("scopes=") && it.length > "scopes=".length } }.forEach { project.logger.warn("ATTENTION clé de confiance sans scopes= (aucune portée accordée, la TV ne l'acceptera pour rien) : ${it.substringBefore(" pub=")}") }
        val trustedKeys = trustedLines.joinToString("\\n")
        if (requireActivation && trustedKeys.isEmpty()) throw GradleException("requireActivation=true mais aucune clé publique de confiance : créez ~/.castbridge-signing/activation-trusted-keys.txt (lignes « kid=… pub=… scopes=… » données par « Clé publique » de la console ou « cle » de l'outil de bureau). Sans elle, personne ne pourrait activer la TV.")
        buildConfigField("boolean", "REQUIRE_ACTIVATION", requireActivation.toString())
        buildConfigField("int", "ACTIVATION_GRACE_DAYS", ((project.findProperty("castbridge.graceDays") as String?) ?: "30"))
        // ABSOLUTE start of the grace (version.properties « lock.graceStartMs »): only an install whose firstInstallTime is EARLIER gets the grace, which ends at start + graceDays and never restarts.
        buildConfigField("long", "LOCK_GRACE_START_MS", "${(project.findProperty("castbridge.lockGraceStartMs") as String?) ?: ver("lock.graceStartMs")}L")
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
