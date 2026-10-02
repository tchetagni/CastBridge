# w12-05 — Outils : miroir Python du document `settings` (`verify_vectors.py`), signeur de secours hors ligne `tools/settings/sign_settings.py` (clé maîtresse du bureau), export clé USB / QR, lecteur `read_settings.py`
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (signature hors ligne) · statut : PRÊT
> **Groupe : W12-b** (vague W12) · prérequis : w12-01 fusionné (vecteurs, `schema.json`) · porte : `python3 tools/activation/verify_vectors.py && python3 -m unittest discover -s tools/settings -p 'test_*.py'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : **oui**

**Vague 12b (outils) · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 2.2-2.3, § 2.7, § 4.4. Branche `claude/sonnet-w12-05`. Rapport : `docs/agent-reports/sonnet-w12-05.md`.

## Objectif
(1) `verify_vectors.py` rejoue `tools/activation/settings-vectors.json` (section `settings` et `build-settings`) : **mêmes octets** que Kotlin/Java, acceptations et refus ; (2) `tools/settings/sign_settings.py` : construit et signe un document `settings` **hors ligne** avec une clé portant `POLICY` (fichier PEM/graine de l'outil de bureau, `KeyFile` ; **jamais** la clé du téléphone), bornes vérifiées contre `schema.json` **avant** signature (refus avec message par clé), `--seq` explicite (l'outil garde le dernier seq dans `~/.castbridge-activation/settings-seq.txt`), `--valid-days 90`, `--based-on`, `--reset`, sorties : jeton texte, `--usb <dossier>` (écrit `<dossier>/Download/CastBridge/settings` = jeton + LF), `--qr settings.png` (si `qrcode` est installé, sinon message clair) ; (3) `tools/settings/read_settings.py <jeton|fichier|URL>` : vérifie (clés de confiance données en `--trust`), affiche les valeurs effectives et la version (pour `releve.py` de W10 et les scripts du pilote) ; (4) tests `unittest` hermétiques avec des clés de test dérivées de textes publics.

## Pourquoi (preuves)
- `tools/activation/verify_vectors.py` est l'implémentation de référence indépendante (`ACTIVATION-FORMAT.md:3,197-209`, dépendance `cryptography`) ; tout nouveau type **doit** y être rejoué (`§ 11.1`).
- Clés par outil : « bureau = toutes » les portées (`ACTIVATION-FORMAT.md` § 2) ; « téléphone propriétaire : jamais `POLICY` » (`docs/OWNER-CONSOLE.md:102`, `OL/OwnerStore.kt:54`).
- Chemin USB : la TV lit `Download/CastBridge/` (`ACTIVATION-FORMAT.md` § 4 ; veilleur `R/ActivationCenter.kt:145`) ; QR : `DK/Qr.kt:11-22` (Kotlin) — ici un équivalent Python pour le propriétaire sans Gradle.
- Scripts voisins à imiter (style, `unittest`) : `tools/prices/sign_prices.py` (**conçu, non fusionné** : ne pas le créer ici), `tools/trial-edition/trial_edition.py sign-catalog` (`:790-812`), `tools/rental-test/rental_test.py`.

## Fichiers possédés
- Nouveaux : `tools/settings/sign_settings.py`, `tools/settings/read_settings.py`, `tools/settings/settingslib.py` (canonique, bornes, enveloppe : partagé par les deux), `tools/settings/test_sign_settings.py`, `tools/settings/test_read_settings.py`, `tools/settings/README.md` (10 lignes : usage, **aucun secret**).
- Existants : `tools/activation/verify_vectors.py` (section `settings`), `tools/tests/test_verify_vectors.py` (le comptage inclut les nouveaux cas).
- Hors zone : `tools/settings/schema.json` (w12-01 : lecture seule), `tools/requirements-dev.txt` et CI (**w12-12**).

## Étapes
1. `settingslib.py` : grammaire § 2.2 (tri, bornes, `NEVER`), enveloppe `cbx1` type `settings` (réutiliser les fonctions d'enveloppe déjà présentes dans `verify_vectors.py` : **ne pas dupliquer** ; extraire si nécessaire dans `settingslib` et importer depuis `verify_vectors.py`, en gardant `verify_vectors.py` autonome en ligne de commande).
2. `verify_vectors.py` : rejouer les cas ; affichage du nombre de contrôles par fichier inchangé dans sa forme.
3. `sign_settings.py` : options ci-dessus ; **refus** avant signature : clé hors schéma, hors bornes, `NEVER`, `validDays` hors 1 h–366 j, `seq` ≤ dernier connu (sauf `--force-seq` avec avertissement) ; sortie du résumé « 3 valeurs changent (2 €) » ; `--usb` crée les dossiers ; `--qr` optionnel.
4. `read_settings.py` : vérification complète (mêmes 12 étapes), valeurs effectives sans expérience (les cohortes sont côté appareil), `--json`.
5. Tests : octets identiques aux vecteurs `build-settings` ; refus ; `--usb` écrit le bon chemin ; `read_settings` refuse une signature fausse et un `target=device`.

## Critères d'acceptation (hors ligne)
- Porte verte ; `verify_vectors.py` affiche le total augmenté ; aucun secret ni clé réelle dans le dépôt ; rapport : commandes exactes pour le propriétaire (secours) et ce que l'audit Opus doit relire (signature, `seq`).
