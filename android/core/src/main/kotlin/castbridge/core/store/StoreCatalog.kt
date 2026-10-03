package castbridge.core.store

import castbridge.core.langues.LangLevel
import castbridge.core.lots.Bundle
import castbridge.core.lots.BundleCatalog
import castbridge.core.lots.Edition
import castbridge.core.lots.LotEditions
import castbridge.core.lots.LotFamilies
import castbridge.core.lots.LotFamily
import castbridge.core.lots.LotId
import castbridge.core.lots.LotManifest
import castbridge.core.lots.LotMeta
import castbridge.core.lots.LotNames
import castbridge.core.lots.SignedBundleCatalog

/**
 * Cœur de la Boutique (w17-01) : fusionne le catalogue de lots signé (`castbridge-lot-catalog-v1`, [LotManifest]) et le catalogue
 * des bouquets signé (`castbridge-bundle-catalog-v1`, [SignedBundleCatalog]) en [StoreItem], les articles que le téléphone et la
 * TV dessinent. Fonction pure : aucune I/O, aucune horloge (les dates affichées sont celles des catalogues), aucun prix, aucun état
 * (sur la TV, loué… : c'est le travail de la vue d'état, w17-02).
 *
 * Règles : un article par bouquet ; un article par lot complet qu'aucun bouquet ne cite (« orphelin ») ; un lot d'essai (`-trial`)
 * est rangé dans l'article de son jumeau complet et ne forme jamais un article seul ; sans catalogue des bouquets, un article par
 * lot complet et [Store.degraded]. Le rayon vient du `type` du bouquet quand il est connu (`classe`, `langues`, `quiz`, `oeuvres`),
 * sinon de la fonction de ses lots (`learn`, `langues`, `quiz`), sinon « Autres ».
 *
 * Les documents arrivent déjà vérifiés par l'appelant (signature, clés, retour en arrière) : ce cœur ne vérifie PAS de signature.
 */
object StoreCatalog {
    /** Plafond du document des lots (taille réelle attendue : quelques dizaines de Ko). */
    const val MAX_LOTS_CATALOG_BYTES = 256L shl 10
    /** Plafond du document des bouquets. */
    const val MAX_BUNDLES_CATALOG_BYTES = 64L shl 10

    private const val SECTION_OTHER = "Autres"
    private const val SECTION_CULTURE = "Culture générale"
    /** Sous-rayons dans l'ordre d'affichage : niveaux scolaires, culture générale (Quiz), niveaux CEFR (Langues), puis « Autres ». */
    private val SECTIONS = listOf("Primaire", "Secondaire", "Supérieur", SECTION_CULTURE) + LangLevel.entries.map { it.name } + SECTION_OTHER
    private val PRIMAIRE = Regex("^(cp|ce1|ce2|cm1|cm2|class-[1-6])$")
    private val SECONDAIRE = Regex("^(6e|5e|4e|3e|(2nde|2de|1ere|1re)(-.*)?|tle.*|form-.*|(lower|upper)-sixth.*)$")
    private val SUPERIEUR = Regex("^(droit-.*|gce-.*|.*-l[1-3])$")
    private val CULTURE = Regex("^(culture-.*|geo-.*|monde|afrique|general)$")
    private val LEVELLED = setOf(Shelf.APPRENDRE, Shelf.QUIZ, Shelf.LANGUES)
    private val INDEXED = Regex("^(?:class|form)-(\\d)")
    private val UPPER_LEVEL = Regex("-l([1-3])$")

    /** Document refusé ; le message est en français et destiné à l'écran. */
    class Refused(message: String) : Exception(message)

    /** Rayon de la Boutique, dans l'ordre d'affichage. */
    enum class Shelf { APPRENDRE, LANGUES, QUIZ, OEUVRES, AUTRES }

    /**
     * Un article : [id] = identifiant du bouquet, ou `lot:<feature>:<scope>` pour un lot orphelin ; [lots] = lots complets
     * résolus (dernière version, triés) ; [trialLots] = leurs échantillons ; [family] = null si inconnue ou mélange avec une
     * famille inconnue (refus par précaution) ; [bytes] = `rawBytes` du bouquet, sinon somme des lots ; [alias] = code court.
     */
    data class StoreItem(val id: String, val title: String, val shelf: Shelf, val section: String, val lots: List<LotMeta>, val trialLots: List<LotMeta>,
                         val family: LotFamily?, val bytes: Long, val bundle: Bundle?, val alias: String)

    /** Résultat : [items] triés ; [warnings] = anomalies non bloquantes des catalogues ; [degraded] = pas de catalogue des bouquets. */
    data class Store(val items: List<StoreItem>, val catalogAtLots: String?, val catalogAtBundles: String?, val degraded: Boolean, val warnings: List<String>) {
        /** Le plus ancien des deux `generatedAt` connus (format ISO, comparable en texte), ou null. */
        val catalogAt: String? get() = listOfNotNull(catalogAtLots, catalogAtBundles).minOrNull()
        /** « Catalogue du JJ/MM », ou vide si aucune date exploitable. */
        val catalogLabel: String get() = catalogAt?.takeIf { DATE.containsMatchIn(it) }?.let { "Catalogue du ${it.substring(8, 10)}/${it.substring(5, 7)}" }.orEmpty()
    }

    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}")

    /** Phrase de refus (français) si [json] dépasse [maxBytes] octets UTF-8, sinon null. */
    fun checkSize(json: String, maxBytes: Long): String? {
        val size = json.toByteArray(Charsets.UTF_8).size.toLong()
        return if (size <= maxBytes) null else "Document trop volumineux (${(size + 1023) / 1024} Ko, au plus ${maxBytes / 1024} Ko) : refusé"
    }

    /**
     * Lit les deux documents (déjà vérifiés par l'appelant), contrôle leur taille puis fusionne. [bundlesJson] null = mode dégradé.
     * @throws Refused document trop gros ou illisible.
     */
    fun fromJson(lotsJson: String, bundlesJson: String?, families: LotFamilies): Store {
        guard("Catalogue des lots", lotsJson, MAX_LOTS_CATALOG_BYTES)
        val manifest = try { LotManifest.parse(lotsJson) } catch (e: Exception) { throw Refused("Catalogue des lots illisible : ${e.message}") }
        var bundles: BundleCatalog? = null
        var bundlesAt: String? = null
        if (bundlesJson != null) {
            guard("Catalogue des bouquets", bundlesJson, MAX_BUNDLES_CATALOG_BYTES)
            bundles = try { BundleCatalog.parse(bundlesJson) } catch (e: Exception) { throw Refused("Catalogue des bouquets illisible : ${e.message}") }
            bundlesAt = SignedBundleCatalog.generatedAtOf(bundlesJson)
        }
        return build(manifest.lots, bundles, families, manifest.generatedAt, bundlesAt)
    }

    private fun guard(what: String, json: String, max: Long) {
        checkSize(json, max)?.let { throw Refused("$what : ${it.replaceFirstChar { c -> c.lowercase() }}") }
    }

    /** Fusion pure. [bundles] null = mode dégradé (un article par lot complet). Aucune exception : un catalogue vide donne un magasin vide. */
    fun build(lots: List<LotMeta>, bundles: BundleCatalog?, families: LotFamilies, lotsAt: String? = null, bundlesAt: String? = null): Store {
        val warnings = ArrayList<String>()
        val latest = lots.groupBy { it.id }.mapValues { (_, v) -> v.maxByOrNull { it.version }!! }
        val fulls = latest.values.filter { it.edition == Edition.FULL }.associateBy { it.id }
        val trialsOfFull = latest.values.filter { it.edition == Edition.TRIAL }.associateBy { LotEditions.fullOf(it.id) }
        val attachedTrials = HashSet<LotId>()

        class Draft(val id: String, val title: String, val type: String?, val ids: List<LotId>, val lots: List<LotMeta>, val rawBytes: Long, val bundle: Bundle?)

        val drafts = ArrayList<Draft>()
        val cited = HashSet<LotId>()
        bundles?.bundles?.forEach { b ->
            val ids = ArrayList<LotId>()
            for (key in b.lots.sorted()) {
                val id = LotNames.parseKey(key)
                if (id == null) { warnings += "Bouquet ${b.id} : lot « $key » illisible (ignoré)"; continue }
                cited += id
                ids += id
                if (id !in fulls) warnings += "Bouquet ${b.id} : lot $key absent du catalogue des lots (ignoré)"
            }
            drafts += Draft(b.id, b.title.ifBlank { b.id }, b.type, ids, ids.mapNotNull { fulls[it] }.sortedWith(ORDER), b.rawBytes, b)
        }
        fulls.values.filter { it.id !in cited }.sortedBy { LotNames.key(it.id) }.forEach { m ->
            drafts += Draft("lot:${LotNames.key(m.id)}", m.title.ifBlank { LotNames.key(m.id) }, null, listOf(m.id), listOf(m), 0L, null)
        }

        val taken = HashSet<String>()
        val items = drafts.map { d ->
            val alias = StoreAlias.of(d.title, d.id, taken).also { taken += it }
            val trials = d.ids.mapNotNull { trialsOfFull[it] }.sortedWith(ORDER).also { t -> t.forEach { attachedTrials += LotEditions.fullOf(it.id) } }
            val scope = classScope(d.ids)
            val shelf = shelfOf(d.type, d.ids)
            val section = sectionOf(shelf, d.ids, scope)
            if (section == SECTION_OTHER && shelf in LEVELLED) warnings += "Article ${d.id} : niveau inconnu (rangé dans Autres)"
            val fam = d.lots.map { families.of(it.id) }
            val family = when {
                fam.isEmpty() || fam.any { it == null } -> null
                fam.all { it == LotFamily.FREE } -> LotFamily.FREE
                else -> LotFamily.RESERVED
            }
            Sorted(StoreItem(d.id, d.title, shelf, section, d.lots, trials, family,
                if (d.rawBytes > 0) d.rawBytes else d.lots.sumOf { it.bytes }, d.bundle, alias), scope)
        }
        trialsOfFull.filterKeys { it !in attachedTrials }.values.sortedWith(ORDER).forEach {
            warnings += "Lot d'essai ${LotNames.key(it.id)} sans lot complet connu (ignoré)"
        }
        val sorted = items.sortedWith(compareBy<Sorted>({ it.item.shelf.ordinal }, { SECTIONS.indexOf(it.item.section).let { i -> if (i < 0) SECTIONS.size else i } },
            { rank(it.scope) }, { it.scope }, { it.item.id })).map { it.item }
        return Store(sorted, lotsAt, bundlesAt, bundles == null, warnings)
    }

    private class Sorted(val item: StoreItem, val scope: String)

    private val ORDER = compareBy<LotMeta>({ it.id.feature }, { it.id.scope })

    /** La « classe » de l'article : portée du premier lot `learn`, à défaut du premier lot (ordre alphabétique). */
    private fun classScope(ids: List<LotId>): String {
        val sorted = ids.sortedWith(compareBy({ it.feature }, { it.scope }))
        return (sorted.firstOrNull { it.feature == "learn" } ?: sorted.firstOrNull())?.scope.orEmpty()
    }

    private fun schoolSection(scope: String): String = when {
        PRIMAIRE.matches(scope) -> "Primaire"
        SECONDAIRE.matches(scope) -> "Secondaire"
        SUPERIEUR.matches(scope) -> "Supérieur"
        else -> SECTION_OTHER
    }

    /** Niveau CEFR d'un lot de langue : second segment de la portée (`zh-a0-famille-fr` -> A0), null si inconnu. */
    private fun langLevel(scope: String): LangLevel? = LangLevel.of(scope.split('-').getOrNull(1))

    private fun sectionOf(shelf: Shelf, ids: List<LotId>, scope: String): String = when (shelf) {
        Shelf.APPRENDRE -> schoolSection(scope)
        Shelf.QUIZ -> if (CULTURE.matches(scope)) SECTION_CULTURE else schoolSection(scope)
        Shelf.LANGUES -> {
            val levels = ids.filter { it.feature == "langues" }.map { langLevel(it.scope) }
            (if (levels.isEmpty() || levels.any { it == null }) null else levels.filterNotNull().min())?.name ?: SECTION_OTHER
        }
        else -> ""
    }

    /** Rang du niveau dans son sous-rayon (CP->CM2, 6e->Tle, L1->L3) ; à rang égal, la portée départage. */
    private fun rank(scope: String): Int {
        val n = INDEXED.find(scope)?.groupValues?.get(1)?.toInt() ?: 0
        return when {
            scope == "cp" -> 1
            scope == "ce1" -> 2
            scope == "ce2" -> 3
            scope == "cm1" -> 4
            scope == "cm2" -> 5
            scope.startsWith("class-") -> n
            scope.startsWith("form-") -> 5 + n
            scope.length == 2 && scope[1] == 'e' && scope[0] in '3'..'6' -> 12 - scope[0].digitToInt()
            scope.startsWith("2nde") || scope.startsWith("2de") -> 10
            scope.startsWith("1re") || scope.startsWith("1ere") || scope.startsWith("lower-sixth") -> 11
            scope.startsWith("tle") || scope.startsWith("upper-sixth") -> 12
            else -> UPPER_LEVEL.find(scope)?.groupValues?.get(1)?.toInt() ?: 50
        }
    }

    private fun shelfOf(type: String?, ids: List<LotId>): Shelf {
        when (type?.trim()?.lowercase()) {
            "classe" -> return Shelf.APPRENDRE
            "langues" -> return Shelf.LANGUES
            "quiz" -> return Shelf.QUIZ
            "oeuvres", "œuvres" -> return Shelf.OEUVRES
        }
        val features = ids.map { it.feature }.toSet()
        return when {
            "learn" in features -> Shelf.APPRENDRE
            features == setOf("langues") -> Shelf.LANGUES
            features == setOf("quiz") -> Shelf.QUIZ
            features == setOf("oeuvres") -> Shelf.OEUVRES
            else -> Shelf.AUTRES
        }
    }
}
