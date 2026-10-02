# w10-01 — Cœur : modèle d'œuvre (`work.json`), catalogue des œuvres signé, budgets, index du magasin, vecteurs

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT
> **Groupe : W10a-1** (vague W10a) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.works.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui (format signé : échantillon)

**Vague 10a · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W10-BOUTIQUE-PRODUCTEURS-LOCAUX-2026-10-02.md` § 2.2, § 3.2, § 4.3, § 5.3, § 7 (lire en entier). Branche `claude/sonnet-w10-01`. Rapport : `docs/agent-reports/sonnet-w10-01.md`. **Premier cahier de la vague** : w10-02, w10-03, w10-04, w10-05, w10-08, w10-09, w10-10, w10-11 codent contre ses API et son format.

## Objectif
Le cœur (JVM pur, sans `android.*`) sait : (1) lire et valider un `work.json` (une œuvre ou son aperçu) ; (2) vérifier un **catalogue des œuvres signé** `castbridge-works-catalog-v1` (même patron que `SignedBundleCatalog`) ; (3) exposer les **budgets** des œuvres ; (4) décrire ce que la TV sait d'une œuvre installée ; (5) relier la classification d'une œuvre au `Rating` parental ; (6) figer tout cela par des vecteurs.

## Pourquoi (preuves)
- `C/lots/LotApi.kt:4` : `LotId(feature, scope)` ; `C/lots/LotManifest.kt:28-31` : une fonction est un mot `[a-z0-9]{1,32}`, un périmètre `[a-z0-9][a-z0-9-]{0,31}` : `LotId("oeuvre", "<id>")` et `"<id>-trial"` sont valides si `<id>` ≤ 26 caractères.
- `C/lots/SignedBundleCatalog.kt:19-65` : modèle à copier (texte canonique reconstruit, Ed25519 `castbridge.core.update.Ed25519`, anti-retour `notOlderThan`, messages français).
- `C/parental/ParentalModel.kt:6-9` : `Rating(all, 12, 16, 18)` ; `AgeBand.allows`.
- `C/lots/LotApi.kt:30-33` : `LotBudget` (10 Mo / 100 Mo) : les œuvres ont **leurs** budgets, séparés.
- `grep -rn 'oeuvre\|WorksCatalog' android/core/src/main/kotlin` → 0 (rien n'existe).

## Fichiers possédés
Nouveaux : `C/works/Work.kt`, `C/works/WorksCatalog.kt`, `C/works/WorkBudget.kt`, `C/works/WorkStoreIndex.kt`, `C/works/WorkVectors.kt`, `CT/works/**`, `tools/activation/works-vectors.json`. **Hors zone** : `C/lots/**`, `C/shop/**` (w10-02), `C/parental/**` (utiliser), `R/**`, `S/**`, `backend/`, `tools/**` sauf le fichier de vecteurs.

## Étapes
1. `Work.kt` : `enum WorkKind { SKETCH, COURT, MUSIQUE, CONTE, COURS, RADIO, AUTRE }` (codes `sketch|court|musique|conte|cours|radio|autre`) ; `enum WorkLicense { RESERVED, CC_BY_SA_4 }` (`"RESERVED"`, `"CC-BY-SA-4.0"`) ; `data class WorkMedia(path, mime, sha256, bytes)` ; `data class Work(id, producer, title, kind, langs: List<String>, durationS, synopsis, ratingProducer: Rating, rating: Rating? /* plateforme, null avant publication */, year, credits, license, media, cover: WorkMedia?, teaserOf: String?, suggestedPriceXaf: Int?, rightsDeclaration: String?, fingerprintSha256: String?, chromaprint: String?)` ; `Work.parse(json): Work` (lève `WorkError(messageFr)`), `Work.validate(w): List<String>` (règles § 3.2 : `id` `[a-z0-9][a-z0-9-]{0,26}` préfixé par `producer + "-"`, titre 1-120, synopsis ≤ 400, `durationS` 1..7200, langs non vide, `rating` ≠ `ADULT` **toujours refusé**, `teaserOf != null ⇒ durationS ≤ 60 ∧ license == RESERVED`, `license == CC_BY_SA_4 ⇒ credits non vide`, `media.mime ∈ {audio/ogg, video/mp4}`), `lotId(): LotId` (`oeuvre:<id>` ou `oeuvre:<teaserOf>-trial`), `isTeaser`, `toJson()` canonique (clés triées).
2. `WorksCatalog.kt` : `data class ProducerInfo(slug, name, bio, zone)` ; `data class WorkEntry(id, producer, kind, langs, durationS, rating, license, lotVersion, teaserVersion: Int?, title, synopsis, coverSha256: String?)` ; `class WorksCatalog(producers, works)` avec `find`, `byProducer`, `byKind` ; `object SignedWorksCatalog { const FORMAT = "castbridge-works-catalog-v1"; fun canonicalPayload(generatedAt, producers, works): String ; fun verify(json, publicKeys, notOlderThan): Verified ; class Refused(message) }`. Texte canonique : `FORMAT`, `generatedAt=`, puis `producer=<slug>|<sha256(name)>|<sha256(bio)>|<zone>` triés, puis `work=<id>|<producer>|<kind>|<langs ,>|<durationS>|<rating>|<license>|<lotVersion>|<teaserVersion ou 0>|<sha256(title)>|<sha256(synopsis)>|<coverSha256 ou ->` triés par id. Refus en français : non signé, altéré, vide, id en double, producteur inconnu d'une œuvre, plus ancien que le gardé.
3. `WorkBudget.kt` : `TV_MAX_BYTES = 200L shl 20`, `PHONE_MAX_BYTES = 300L shl 20`, `MAX_WORK_BYTES = 25L shl 20`, `MAX_TEASER_BYTES = 2L shl 20`, `MAX_TEASER_SECONDS = 60`, `MEMORY_PLAY_MAX_BYTES = 16L shl 20` (au-delà : repli fichier privé sur TV 32 bits) ; KDoc : pourquoi séparé de `LotBudget`.
4. `WorkStoreIndex.kt` : `data class InstalledWork(id, version, edition: Edition, rating: Rating, bytes, durationS, kind, producer, contract: String?, installedAt)` ; `WorkStoreIndex.toJson/parse` (liste) ; `manifest(maxBytes, used)` texte pour `GET /api/oeuvres`.
5. Classification : `Work.rating ?: ratingProducer` → `Rating` existant ; fonction pure `WorkAccess.visibleTo(work, band: AgeBand?): Boolean` (band null = adulte/sans profil).
6. `works-vectors.json` (`castbridge-works-vectors-v1`, clés de test **copiées** de `tools/activation/test-vectors.json`) : ≥ 20 cas : `work-json` (valide audio, valide vidéo, aperçu valide, id trop long, id sans préfixe producteur, classification adulte, aperçu > 60 s, libre sans crédits, mime inconnu), `works-catalog` (signé OK → champs, altéré, non signé, plus ancien, œuvre d'un producteur inconnu, ordre canonique), `visible-to` (table tranche × classification). Générateur `CASTBRIDGE_WRITE_VECTORS=1`.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.works.*'            # vert
python3 -c "import json;print(len(json.load(open('tools/activation/works-vectors.json'))['cases']))"   # ≥ 20
grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/works          # 0
git diff --quiet tools/activation/test-vectors.json tools/activation/rental-vectors.json && echo intacts
```

## Cas limites
- `work.json` avec des clés inconnues : ignorées (format additif) ; clé obligatoire absente : erreur nommant la clé.
- Catalogue sans section `producers` : refusé (une œuvre a toujours un producteur).
- `rating` plateforme absent (œuvre non encore publiée) : `visibleTo` utilise `ratingProducer` **mais** `WorkEntry` du catalogue exige `rating` (le catalogue ne liste que du publié).

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant réel ; pas d'`import android` ; ne pas toucher `C/lots/**` ni `SignedBundleCatalog` ; pas de HTTP ; ne pas définir la taxonomie de la boutique (w10-02) ; textes utilisateur en français.

## Rapport
`STATUT`, signatures publiques (pour w10-02…11), nombre de vecteurs, décisions de détail (longueurs, codes), questions.
