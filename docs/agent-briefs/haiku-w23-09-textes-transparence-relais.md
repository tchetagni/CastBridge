# w23-09 — Textes de transparence du relais téléphone ↔ TV (consentement versionné, TV et téléphone)
<!-- routage architecte 2026-10-04 (W23-B) -->
> **Modèle : haiku** · escalade : relecture par le coordinateur (texte affiché) · statut : **ATTEND la décision D-W23B-12** et w23-07 ; juridique reporté au 2027-01-01
> **Groupe : W23-B** (ordre 5) · porte : tests unitaires des textes existants + `:sender:testDebugUnitTest` + `:receiver:assembleDebug -PrequireActivation=true`
> **Jauge : ≈ 120 k jetons entrée / 6 k sortie** (effort S, ≈ 0,3 j)

**Conception** : `DESIGN-W23B-…` § 9.6 a. Branche `claude/w23-09-transparence`. Rapport : `docs/agent-reports/haiku-w23-09.md`.

## Objet
Ajouter, **mot pour mot** (après validation du propriétaire), la ligne du téléphone dans son texte d'information/consentement (`ConsentText.kt` du téléphone) et la ligne de la TV dans l'avis d'usage du premier lancement et dans « À propos > Politiques appliquées » ; **incrémenter la version** du consentement (une version nouvelle redemande l'accord au prochain lancement, mécanique existante).

## Fichiers possédés
Les fichiers de textes de consentement existants du téléphone et de la TV (zone additive : une ligne chacun, la constante de version) et leurs tests.

## Interdits
Toute autre modification de texte ; toute logique ; tout écran nouveau.

## Critères d'acceptation
Les deux textes présents à l'identique de la conception validée ; version incrémentée ; tests des textes mis à jour (aucune assertion supprimée).
