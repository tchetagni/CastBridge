// Marque blanche (docs/WHITE-LABEL.md) : script appliqué par :sender et :receiver (apply(from = "../brand.gradle.kts")).
// -Pbrand=<id> (défaut « castbridge » = construction d'aujourd'hui, octet pour octet) lit branding/clients/<id>/brand.properties.
// -PbrandDir=<dossier> remplace branding/clients/<id> (essais). Le module reçoit : applicationId, champs BuildConfig, ressources superposées.
import java.util.Properties

val brandId = (findProperty("brand") as String?)?.trim().takeUnless { it.isNullOrEmpty() } ?: "castbridge"
val isDefaultBrand = brandId == "castbridge"
val brandDir = (findProperty("brandDir") as String?)?.let { File(it) } ?: File(rootDir.parentFile, "branding/clients/$brandId")
val brandProps = Properties()
if (!isDefaultBrand) {
    val f = File(brandDir, "brand.properties")
    if (!f.isFile) throw GradleException("Marque « $brandId » introuvable : $f n'existe pas. Créez branding/clients/$brandId/brand.properties (docs/WHITE-LABEL.md) ou retirez -Pbrand.")
    f.reader(Charsets.UTF_8).use { brandProps.load(it) }
    val declared = brandProps.getProperty("brand.id")?.trim()
    if (declared != brandId) throw GradleException("$f : brand.id=« ${declared ?: ""} » ne correspond pas à -Pbrand=$brandId.")
}
fun prop(key: String, default: String = ""): String = if (isDefaultBrand) default else (brandProps.getProperty(key)?.trim() ?: default)
fun need(key: String): String = prop(key).ifEmpty { throw GradleException("$brandDir/brand.properties : « $key » est obligatoire.") }
fun lit(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

// Valeurs de la marque ; celles de CastBridge sont exactement les textes d'aujourd'hui.
val brandName = if (isDefaultBrand) "CastBridge" else need("brand.name")
val brandTvName = if (isDefaultBrand) "CastBridge-TV" else need("brand.tvName")
val brandOwner = prop("brand.owner", "CastBridge")
val brandTagline = prop("brand.tagline")
val supportPhone = if (isDefaultBrand) "+237686031070" else prop("support.phone")
val serverUrl = prop("server.url")
// Couleurs : clés « color.<rôle> » (docs/WHITE-LABEL.md § Couleurs) transmises telles quelles à BrandPalette ; thème du téléphone par défaut.
val palette = if (isDefaultBrand) "" else brandProps.stringPropertyNames().filter { it.startsWith("color.") }.sorted()
    .joinToString(";") { it.removePrefix("color.") + "=" + brandProps.getProperty(it).trim() }
val phoneTheme = prop("theme.phone.default", "dark").uppercase()
if (phoneTheme !in setOf("DARK", "LIGHT", "SYSTEM")) throw GradleException("$brandDir/brand.properties : theme.phone.default doit valoir dark, light ou system.")
val module = project.name   // « sender » ou « receiver »
val appIdKey = if (module == "receiver") "tv.applicationId" else "phone.applicationId"

// Lus par les scripts des modules.
extra["brand.id"] = brandId
extra["brand.appId"] = if (isDefaultBrand) null else need(appIdKey)   // null = l'applicationId d'aujourd'hui, resté dans le module
extra["brand.isDefault"] = isDefaultBrand
extra["brand.serverUrl"] = serverUrl

// L'extension « android » n'est pas typée ici (AGP n'est pas sur le chemin de compilation d'un script d'appui) : appels dynamiques.
pluginManager.withPlugin("com.android.application") {
    extensions.getByName("android").withGroovyBuilder {
        "defaultConfig" {
            "buildConfigField"("String", "BRAND_ID", lit(brandId))
            "buildConfigField"("String", "BRAND_NAME", lit(brandName))
            "buildConfigField"("String", "BRAND_TV_NAME", lit(brandTvName))
            "buildConfigField"("String", "BRAND_OWNER", lit(brandOwner))
            "buildConfigField"("String", "BRAND_TAGLINE", lit(brandTagline))
            "buildConfigField"("String", "SUPPORT_PHONE", lit(supportPhone))
            "buildConfigField"("String", "BRAND_PALETTE", lit(palette))
            "buildConfigField"("String", "BRAND_PHONE_THEME", lit(phoneTheme))
        }
        if (!isDefaultBrand) {
            // Ressources de la marque : jeux de sources des types de build (priorité sur main, aucune tâche renommée).
            val res = File(brandDir, "android/$module/res")
            if (res.isDirectory) {
                "sourceSets" {
                    "getByName"("debug") { getProperty("res").withGroovyBuilder { "srcDir"(res) } }
                    "getByName"("release") { getProperty("res").withGroovyBuilder { "srcDir"(res) } }
                }
            }
        }
    }
    if (!isDefaultBrand) extensions.getByType<BasePluginExtension>().archivesName.set("$brandId-$module")
}

// Textes-ressources dérivés de la marque (libellés du manifeste, raccourci, service d'accessibilité) : générés, pas écrits à la main par le client.
// Toute <string> de src/main/res/values qui nomme « CastBridge » est refaite avec la marque (mêmes règles que Brand.t du cœur) ; xml/shortcuts.xml reçoit l'applicationId.
// Une chaîne que la superposition du client déclare elle-même n'est pas générée (le client garde la main).
if (!isDefaultBrand) {
    val brandAppId = need(appIdKey)
    val genDir = layout.buildDirectory.dir("brand/res")
    val overlayRes = File(brandDir, "android/$module/res")
    val gen = tasks.register("generateBrandResources") {
        group = "castbridge"
        description = "Textes-ressources et raccourcis de la marque $brandId (docs/WHITE-LABEL.md)"
        val mainRes = File(projectDir, "src/main/res")
        inputs.dir(mainRes); inputs.property("brand", listOf(brandId, brandName, brandTvName, brandAppId))
        if (overlayRes.isDirectory) inputs.dir(overlayRes)
        outputs.dir(genDir)
        doLast {
            val out = genDir.get().asFile; out.deleteRecursively()
            val tv = Regex("(?<![/\\w])CastBridge[- ]TV"); val ph = Regex("(?<![/\\w])CastBridge(?![\\w-])")
            fun brandText(t: String) = t.replace(tv, Regex.escapeReplacement(brandTvName)).replace(ph, Regex.escapeReplacement(brandName))
            val own = HashSet<String>()
            File(overlayRes, "values").listFiles { f -> f.extension == "xml" }?.forEach { f -> Regex("<string\\s+name=\"([^\"]+)\"").findAll(f.readText()).forEach { own += it.groupValues[1] } }
            val lines = ArrayList<String>()
            File(mainRes, "values").listFiles { f -> f.extension == "xml" }?.sortedBy { it.name }?.forEach { f ->
                Regex("(<string\\s+name=\"([^\"]+)\"[^>]*>)(.*?)(</string>)", RegexOption.DOT_MATCHES_ALL).findAll(f.readText()).forEach { m ->
                    if (m.groupValues[2] !in own && "CastBridge" in m.groupValues[3]) lines += "    " + m.groupValues[1] + brandText(m.groupValues[3]) + m.groupValues[4]
                }
            }
            File(out, "values").mkdirs()
            File(out, "values/brand_generated.xml").writeText("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<!-- GÉNÉRÉ par brand.gradle.kts pour la marque $brandId : ne pas modifier -->\n<resources>\n" + lines.joinToString("\n") + "\n</resources>\n")
            val sc = File(mainRes, "xml/shortcuts.xml")
            if (sc.isFile) {
                File(out, "xml").mkdirs()
                File(out, "xml/shortcuts.xml").writeText(sc.readText().replace("android:targetPackage=\"castbridge.sender\"", "android:targetPackage=\"" + brandAppId + "\""))
            }
        }
    }
    tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(gen) }      // avant toute fusion de ressources
    pluginManager.withPlugin("com.android.application") {
        extensions.getByName("android").withGroovyBuilder {
            "sourceSets" {
                listOf("debug", "release").forEach { n ->
                    "getByName"(n) { getProperty("res").withGroovyBuilder { "srcDir"(files(genDir).builtBy(gen)) } }
                }
            }
        }
    }
}

