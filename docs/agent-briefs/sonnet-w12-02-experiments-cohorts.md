# w12-02 — Cœur : expériences et cohortes (`Experiments`, `Cohort`), propriété `exp` de la télémétrie, événement essentiel `settings_applied`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (vie privée : aucun identifiant nouveau) · statut : PRÊT
> **Groupe : W12-a** (vague W12) · prérequis : w12-01 fusionné (types `ExperimentLine`, `SettingsState`) ; `C/telemetry/Telemetry.kt` **après** w11-04 / w11-14 / w10-07 s'ils sont lancés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.settings.*' --tests '*Telemetry*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : échantillon

**Vague 12a (cœur, JVM, testé) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.8, § 3 (tout), § 5.4, § 9 (D-W12-5). Branche `claude/sonnet-w12-02`. Rapport : `docs/agent-reports/sonnet-w12-02.md`.

## Objectif
(1) `Cohort.arm(exp, subject)` : affectation **déterministe** d'un bras sans identifiant nouveau (`SHA-256("castbridge-cohort-v1|" + sel + "|" + sujet)[0] mod n`) ; (2) `Experiments.effective(state, subject, now)` : la valeur **effective** d'une clé compte tenu des expériences en cours (fenêtre, bras) — c'est ce que `Settings.int` renvoie quand une `exp=` vise la clé ; (3) `Subject.resolve(licenseId?, agentKid?, localSalt)` : la règle licence → agent → local (§ 3.2) ; (4) télémétrie : propriété **`exp`** (`"<nom>:<bras>"`, ≤ 64 car., liste blanche) ajoutée **uniquement** aux événements soumis au consentement « statistiques » ; événement **essentiel** `settings_applied{seq, kid, schema, stale}` ; (5) `Experiments.summaryLine()` pour le diagnostic exportable (jamais pour l'écran utilisateur).

## Pourquoi (preuves)
- Consentement à deux niveaux et liste des clés interdites : `docs/TELEMETRY.md:19-32,52-58` ; les propriétés sont une **liste blanche par événement** (`:52`) ; aucun identifiant de licence n'existe en télémétrie (rapport d'inventaire : `installId`, `androidIdHash` seulement, `:10-13`) : **ne pas en ajouter**. `exp` est à faible cardinalité (≤ 8 expériences × ≤ 4 bras).
- `Telemetry.hashForCounting(valeur, sel)` (`TELEMETRY.md:59-60`) montre l'usage d'un sel local jamais envoyé : même esprit pour le sujet « local ».
- L'identifiant de licence est connu de la TV et du téléphone par l'activation installée (`license=` du corps, `ACTIVATION-FORMAT.md` § 3.3 ; `license=trial` pour l'essai).
- Avec 15-25 foyers (`DESIGN-W10` § 10.6) : décision D-W12-5 (séquentiel, par point focal) ; le code doit **permettre** les trois sujets, la console choisit.

## Fichiers possédés
- Nouveaux : `C/settings/Experiments.kt`, `C/settings/Cohort.kt`, `C/settings/Subject.kt`, `CT/settings/{ExperimentsTest,CohortTest,SubjectTest}.kt`.
- Existants (zones précises) : `C/telemetry/Telemetry.kt` (ajout de la propriété `exp` à la liste blanche des événements **d'usage** et de l'événement `settings_applied` **essentiel** ; rien d'autre), `C/settings/Settings.kt` (**une seule** méthode ajoutée : `bindSubject(() -> String)` pour que `int/bool/id` passent par `Experiments.effective`), `docs/TELEMETRY.md` § 4 (deux lignes de tableau : `settings_applied` essentiel, propriété `exp`).
- Hors zone : `B/telemetry/EventCatalog.java` (**w12-10**), tout écran, `SettingsEngine`.

## Signatures à respecter
```kotlin
package castbridge.core.settings
object Cohort { fun arm(exp: ExperimentLine, subject: String): Pair<String, String> /* (bras, valeur) */ ; fun index(salt: String, subject: String, n: Int): Int }
object Subject { fun resolve(licenseId: String?, agentKid: String?, localSalt: () -> String): String   /* "lic:<id>" | "agent:<kid>" | "local:<sel>" ; "trial" n'est jamais un sujet */ }
object Experiments {
    fun running(state: SettingsState, nowMs: Long): List<ExperimentLine>
    fun effective(state: SettingsState, key: String, subject: String, nowMs: Long): String?   // null = pas d'expérience sur la clé ⇒ Settings lit values/défaut
    fun arms(state: SettingsState, subject: String, nowMs: Long): Map<String, String>        // "<nom>" -> "<bras>" (pour GET /api/settings cohorts= et la propriété exp)
    fun expProp(state: SettingsState, subject: String, nowMs: Long): String?                  // "nom:bras[,nom:bras]" ≤ 64 car., ou null
    fun summaryLine(state: SettingsState, subject: String, nowMs: Long): String                // diagnostic FR
}
```

## Étapes
1. `Cohort` : fonction de hachage, répartition testée (10 000 sujets, 2/3/4 bras : chaque bras entre 20 % et 55 % pour 2 bras, etc.) ; **stabilité** : même sel + même sujet ⇒ même bras ; sel différent ⇒ réassignation.
2. `Subject` : ordre licence → agent → local ; `trial` ignoré ; jamais de code d'appareil.
3. `Experiments` : fenêtre `[début, fin]` sur le temps **de confiance** fourni par l'appelant ; hors fenêtre ⇒ bras `a` **sans** effet (la valeur `set=` ou le défaut) ; bras hors bornes ⇒ expérience ignorée (déjà filtrée par w12-01 : test de défense en profondeur).
4. `Telemetry.kt` : `exp` dans la liste blanche des événements d'usage ; `settings_applied` essentiel avec `seq` (nombre), `kid` (16 hex), `schema`, `stale` (bool) ; **un test** prouve qu'un événement essentiel **ne porte jamais** `exp` même si l'appelant le fournit.
5. `Settings.bindSubject` ; `Settings.int` renvoie la valeur effective ; test : avec une `exp=` sur `trial.defaultDays` à bras `a:30,b:14`, deux sujets différents obtiennent 30 et 14, et un même foyer (même licence) obtient la même valeur sur « TV » et « téléphone ».
6. `docs/TELEMETRY.md` § 4 : deux lignes.

## Critères d'acceptation (hors ligne)
- Porte verte ; ≥ 14 tests ; `exp` absent des événements essentiels ; aucune clé interdite ajoutée (`TELEMETRY.md:55-58`).
- Rapport : la répartition mesurée, la règle de sujet, ce que w12-06/07/10 doivent appeler (`Experiments.arms`, `expProp`).
