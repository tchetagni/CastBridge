# w14-04 — Cœur : parcours J-19 (« Ouvrir avec » dans chaque état réel), J-20 (activation vue du téléphone), compatibilité des formats persistés (fixtures) et règle de rétrogradation pure
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus en échantillon (`UpdateRules` touche la mise à jour) · statut : PRÊT
> **Groupe : W14-b** (vague W14, tranche S1) · prérequis : w14-01 fusionné · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.journey.OpenWithJourneyTest' --tests 'castbridge.core.journey.ActivationJourneyTest' --tests 'castbridge.core.journey.PersistenceCompatTest' --tests 'castbridge.core.update.UpdateRulesTest'`
> **Jauge : ≈ 400 k jetons entrée / 25 k sortie** (effort M) · audit Opus : échantillon

**Vague 14b (cœur) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W14` § 3.2 (J-19, J-20), § 4.2 (fixtures), § 4.3 (rétrogradation). Parcours P-22…P-25, P-28…P-30. Branche `claude/sonnet-w14-04`. Rapport : `docs/agent-reports/sonnet-w14-04.md`.

## Objectif
(1) Rejouer « Ouvrir avec CastBridge » depuis **huit états réels** fabriqués par le harnais (plus de `SendFacts` écrits à la main) ; (2) prouver que l'état d'activation de la TV est vu par le téléphone en une lecture ; (3) figer les **formats de fichiers persistés** (téléphones de confiance, magasin de liaison, index `.filing`, coffre de location, PIN) par des fixtures **jamais modifiées** lues par les classes actuelles ; (4) extraire la règle de rétrogradation en fonction pure `UpdateRules`.

## Pourquoi (preuves)
- R-02 : `CT/SendChoiceTest.kt` teste `SendChoices.decide` sur des faits écrits à la main ; le vrai défaut venait de **faits mal collectés** (`S/OpenWithActivity.kt:68` ignorait le chemin PIN). Seul un état fabriqué par la vraie boucle le prouve.
- Activation : `C/owner/ActivationScreenState.kt` (`view(info, why, nowMs)` sur les champs de `GET /api/activation`) ; R-05 « activation non propagée ».
- Formats : `C/trust/TrustFiles.kt` (`trusted_phones.txt`), `C/trust/LinkDriver.kt`/`LinkStore`, `C/tv/Filing`/`FiledIndex` (`.filing`, `docs/STORAGE.md` § 10), `C/lots/RentalVault` ; R-03 a montré qu'une réinstallation (pas une mise à jour) perd tout : il faut au moins garantir qu'une **mise à jour** relit tout.
- Rétrogradation : `R/UpdateInstaller.kt` `val canForce = force && BuildConfig.DEBUG`, `if (a.code < installedCode() && !canForce) return err(409, …)` : règle Android non testable ; `C/update/UpdateClient.kt:73` (`m.versionCode <= me.versionCode -> UpToDate`).

## Fichiers possédés
Nouveaux : `CT/journey/OpenWithJourneyTest.kt`, `CT/journey/ActivationJourneyTest.kt`, `CT/journey/PersistenceCompatTest.kt`, `android/core/src/test/resources/fixtures/formats/v2026-10-02/{trusted_phones.txt,link_store.json,filing.index,rental_vault.bin,pins.properties}` (contenus **de test** : adresses `AA:BB:…`, jetons factices, aucun secret réel), `android/core/src/test/resources/fixtures/formats/README.md` ; `C/update/UpdateRules.kt`, `CT/update/UpdateRulesTest.kt`. **Hors zone** : `R/UpdateInstaller.kt` (câblé par w14-06), harnais, autres `C/**`.

## Signatures
```kotlin
package castbridge.core.update
object UpdateRules {
    sealed class Verdict { object Install : Verdict(); data class Refuse(val http: Int, val reason: String) : Verdict() }
    /** Même règle que R/UpdateInstaller.kt : code < installé ⇒ 409 sauf force en debug ; même signataire exigé ailleurs. */
    fun mayInstall(installedCode: Int, candidateCode: Int, force: Boolean, debugBuild: Boolean): Verdict
    const val DOWNGRADE_REFUSED = "Version plus ancienne : refusée (rétrogradation impossible sur une TV distribuée)"
}
```

## Étapes
1. J-19 : table de 8 états fabriqués : (a) aucune TV ; (b) confiance connectée ; (c) confiance en reconnexion (`tv.stop()`) ; (d) confiance Bluetooth seul ; (e) **TV à code connectée, registre vide** (`enterPin(ok)` sans `pairWithTv`) ; (f) TV à code, code refusé ; (g) TV à code, verrou ; (h) deux TV sans défaut. Pour chaque : `SendChoices.decide(phone.sendFacts())` ⇒ attendu (`route`, `copyEnabled`, `moveEnabled`, `status` ne contient `NO_TV` que pour (a)) ; **et** compter les requêtes `/api/info` déclenchées par `sendFacts()` : ≤ 1. Pour (b) et (e) : enchaîner `send(small)` ⇒ fichier reçu (P-23).
2. J-20 : `tv.activate(trialKey)` (clé de TEST des vecteurs : voir `CT/owner/ActivationVectorsTest` pour l'obtenir) ; `ActivationScreenState.view(TvClient(tv.base, pin).activation(), null, clock.now())` ⇒ libellé ESSAI ; `tv.activate(productionKey)` ⇒ PRODUCTION ; clé inconnue ⇒ vue avec **raison** non vide.
3. Fixtures : générer une fois chaque fichier **avec les classes actuelles** (exécution unique, fichiers committés) ; `PersistenceCompatTest` relit chaque fixture avec la classe actuelle et vérifie des valeurs attendues (2 téléphones, 1 jeton, 3 fichiers rangés, 1 location, 2 PIN sous 4 clés). README : « ces fichiers ne changent **jamais** ; un nouveau format = un nouveau dossier `vNNNN-MM-JJ` + chemin de migration testé ».
4. `UpdateRules` + test : table (installé 64, candidat 63 ⇒ Refuse 409 ; 64/64 ⇒ Install (règle actuelle `<` strict) ; 64/65 ⇒ Install ; 64/63 force+debug ⇒ Install ; force sans debug ⇒ Refuse).

## Critères d'acceptation (hors ligne)
Porte verte ; `grep -c 'SendFacts(' CT/journey/OpenWithJourneyTest.kt` ⇒ 0 ; `ls android/core/src/test/resources/fixtures/formats/v2026-10-02 | wc -l` ⇒ 5 ; `grep -rn 'import android' android/core/src/main/kotlin/castbridge/core/update/UpdateRules.kt | wc -l` ⇒ 0 ; `:core:test` complet vert.

## Cas limites
Si un format persisté n'a pas de lecteur pur (par ex. PIN dans `SharedPreferences` Android) : fixture au format texte attendu + note « lecteur Android, non testable ici » dans README et rapport. État (g) : construire le verrou avec un `TvSim` dédié.

## À ne pas faire
Aucun secret ni clé réelle dans les fixtures ; ne pas modifier `SendChoice.kt` ni `ActivationScreenState.kt` (si un faux positif apparaît : `@Ignore` + REGRESSIONS, pas un correctif ici) ; ne pas câbler `UpdateInstaller`.

## Rapport
`STATUT`, table des 8 états → résultat, formats sans lecteur pur, écarts de `UpdateRules` vs `UpdateInstaller` (lecture seule), lignes REGRESSIONS proposées.
