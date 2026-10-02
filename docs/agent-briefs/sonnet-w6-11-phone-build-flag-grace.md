# w6-11 — Téléphone : interrupteur `REQUIRE_TV_PROOF`, grâce absolue 14 j, `TRUSTED_KEYS` du téléphone, règles de sauvegarde (preuves, session super)

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT
> **Groupe : W6b-1** (vague W6b) · prérequis : aucun · porte : `python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py' && cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin -PrequireTvProof=true`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 6b · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 3.4 (interrupteur, grâce), § 4.2 (session scellée), § 1 (Sauvegardes). Branche `claude/sonnet-w6-11`. Rapport : `docs/agent-reports/sonnet-w6-11.md`.

## Objectif
(1) `android/sender/build.gradle.kts` : `buildConfigField("boolean","REQUIRE_TV_PROOF", …)` depuis `-PrequireTvProof=true` (défaut **false**), `LOCK_GRACE_START_MS`/`PHONE_GRACE_DAYS` depuis `version.properties` (`phone.lock.graceStartMs`, `phone.lock.graceDays=14`), `TRUSTED_KEYS` (clés **publiques** du propriétaire, même mécanique que `android/receiver/build.gradle.kts:24-37`) **si** w4-13 ne l'a pas déjà fait ; le build **refuse** `requireTvProof=true` sans aucune clé de confiance (même garde-fou que la TV) ; (2) `version.properties` : les deux lignes `phone.lock.*` commentées comme `lock.*` ; (3) `backup_rules.xml` et `data_extraction_rules.xml` du téléphone : exclure `files/proof/` (preuves, clé épinglée, `clock.txt`), `files/super-session.txt`, `files/sync/` ; (4) test Python des règles XML étendu.

## Pourquoi (preuves)
- `android/receiver/build.gradle.kts:24-37` (`TRUSTED_KEYS`, refus sans clé), `android/sender/build.gradle.kts` (`grep -n 'TRUSTED_KEYS\|requireActivation'` : absent au 2026-10-02), `android/ownerlib/build.gradle.kts:9-23` (modèle de propriété Gradle) ; `version.properties` (`lock.graceStartMs`, `lock.graceDays`) ; `android/sender/src/main/res/xml/backup_rules.xml` (liste actuelle) ; `tools/tests/test_backup_rules.py` (w1-01).

## Fichiers possédés
`android/sender/build.gradle.kts`, `version.properties`, `android/sender/src/main/res/xml/{backup_rules,data_extraction_rules}.xml`, `tools/tests/test_backup_rules.py`, `docs/RELEASES.md` (§ « Téléphone : interrupteur de preuve »). **Hors zone** : tout `.kt`.

## Étapes
1. Gradle : propriétés, `buildConfigField` (`REQUIRE_TV_PROOF`, `PHONE_LOCK_GRACE_START_MS`, `PHONE_GRACE_DAYS`, `TRUSTED_KEYS`), garde-fou, `-Pcastbridge.phoneGraceDays=N` surcharge.
2. `version.properties` : `phone.lock.graceStartMs=<ms UTC du jour de la première publication verrouillée du téléphone : laisser 0 = « pas encore fixé »>` ; `phone.lock.graceDays=14` ; commentaire D-W6-5.
3. XML : exclusions ; test Python : les deux fichiers listent `proof/`, `super-session.txt`, `sync/`.
4. RELEASES.md : procédure en trois temps (§ 3.4) pour allumer `requireTvProof`.

## Critères d'acceptation
```sh
cd android && gradle --offline :sender:compileDebugKotlin   # compile (défaut : interrupteur éteint)
cd android && gradle --offline :sender:compileDebugKotlin -PrequireTvProof=true -PtrustedKeysFile=/dev/null 2>&1 | grep -i 'aucune clé'   # le build refuse
python3 -m unittest discover -s tools/tests -p 'test_backup_rules.py'   # vert
grep -n 'phone.lock.graceDays' version.properties   # ≥ 1
```

## Cas limites
`phone.lock.graceStartMs=0` ⇒ `FleetMigration.of(firstInstall, 0)` ne donne jamais de grâce (installation « neuve ») : documenter que la valeur doit être fixée **avant** la première publication verrouillée ; w4-13 déjà fusionné ⇒ ne pas dupliquer `TRUSTED_KEYS`.

## À ne pas faire
Ne pas allumer l'interrupteur par défaut ; ne pas toucher les `.kt` ; ne pas toucher `~/.castbridge-signing`.

## Rapport
`STATUT`, noms exacts des champs `BuildConfig` (pour w6-16, w6-19).
