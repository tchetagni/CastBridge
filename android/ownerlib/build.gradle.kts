plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// Console propriétaire partagée (docs/OWNER-CONSOLE.md) : utilisée par l'application « CastBridge Propriétaire » (:owner) et, derrière l'entrée cachée
// protégée par mot de passe, par l'application du téléphone (:sender). Aucune permission réseau.
//
// Le haché bcrypt du mot de passe superadmin est lu À LA COMPILATION dans ~/.castbridge-signing/superadmin.bcrypt (hors dépôt, droits 600) :
// absent => l'entrée cachée n'existe pas dans ce build. -Pcastbridge.noSuperAdmin=true force un build sans entrée (APK à diffuser sans elle).
val superAdminHash: String = run {
    if ((project.findProperty("castbridge.noSuperAdmin") as String?) == "true") return@run ""
    val f = File(System.getProperty("user.home"), ".castbridge-signing/superadmin.bcrypt")
    val h = if (f.isFile) f.readText().trim() else ""
    if (Regex("^\\$2[abxy]\\$\\d{2}\\$[./A-Za-z0-9]{53}$").matches(h)) h else ""
}
android {
    namespace = "castbridge.ownerlib"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        buildConfigField("String", "SUPERADMIN_BCRYPT", "\"$superAdminHash\"")
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true; buildConfig = true }
}
dependencies {
    api(project(":core"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
}
