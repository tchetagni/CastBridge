# w11-13 — Mesure sans télémétrie : script « touches jusqu'à la fonction » (émulateur), liste de contrôle, protocole de test avec 5 usagers, campagne § W11
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus en audit seulement (jamais en exécution) · statut : PRÊT
> **Groupe : W11-e** (vague W11) · prérequis : aucun · porte : `bash -n tools/ux/taps.sh`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : non

**Vague 11e (mesure) · Effort S (≈ 1 j) · Modèle : sonnet (écriture d'un script et d'un protocole : hors forme mécanique Haiku, ROUTAGE § 4.7) · Statut PRÊT (utilisable avant et après 11c/11d).** Conception : `DESIGN-W11-NAVIGATION-ALLEGEE-2026-10-02.md` § 10. Branche `claude/sonnet-w11-13`. Rapport : `docs/agent-reports/sonnet-w11-13.md`.

## Objectif
Trois livrables qui permettent de **prouver** l'allègement sans aucune télémétrie : (1) `tools/ux/taps.sh` + `tools/ux/tasks.tsv` qui rejouent sur un émulateur (téléphone et TV) une séquence de touches/appuis par tâche et comptent ; (2) la même liste comme liste de contrôle manuelle (`docs/UX-MESURES.md`) avec colonnes avant/après ; (3) `docs/UX-TEST-PROTOCOLE.md` : protocole des 5 usagers (recrutement, 8 tâches, mesures, seuils, fiche de compte rendu anonyme) ; et le § W11 de `docs/TEST-CAMPAIGN.md` (si le fichier existe ; sinon le créer avec ce seul paragraphe).

## Pourquoi (preuves)
- Conception § 10.4 : cibles chiffrées (téléphone 11 → 6 destinations, 34 → ≤ 12 cibles, ≈ 190 → ≤ 40 mots ; TV 18 → 6 tuiles, 9,5 → ≤ 3 appuis, 22-24 → ≤ 12 lignes, badge ≤ 5 mots).
- `docs/TELEMETRY.md` : les statistiques d'usage sont soumises au consentement ; la mesure UX ne doit pas en dépendre.
- Aucun outil UX n'existe (`tools/` : release, activation, content…).

## Fichiers possédés
- Nouveaux : `tools/ux/taps.sh`, `tools/ux/tasks.tsv`, `tools/ux/README.md`, `docs/UX-MESURES.md`, `docs/UX-TEST-PROTOCOLE.md`.
- Modifié : `docs/TEST-CAMPAIGN.md` (ajout d'un § « W11 — navigation allégée » seulement, s'il existe).
- Hors zone : tout code Kotlin, les autres docs (w11-14).

## Étapes
1. `tasks.tsv` (tabulations) : `app	id	tâche (phrase usager)	séquence	attendu	seuil` ; `app` ∈ {phone, tv} ; `séquence` = suite d'actions `tap:x,y` (dp, convertis en px par le script d'après `wm size`/`wm density`), `key:KEYCODE_DPAD_RIGHT`, `text:...`, `wait:ms` ; `attendu` = sous-chaîne à trouver dans `uiautomator dump` ou nom d'activité (`dumpsys activity top`) ; `seuil` = 3 (téléphone) / 5 (TV). 14 tâches : envoyer une vidéo, télécommande, une leçon, une langue, un quiz, boutique (si présente), activer la TV, données hors ligne, contrôle parental, réglages, dépannage/diagnostic, téléchargements TV, bibliothèque TV, contenus libres ; côté TV : Regarder, Apprendre, Jeux, Téléphone (code), Parents, Plus › Mises à jour, Plus › Clé USB.
2. `taps.sh` : `taps.sh phone|tv [--serial S] [--only id]` ; pour chaque tâche : `am force-stop`, `am start` (`castbridge.sender/.MainActivity` ou `castbridge.receiver/.PlayerActivity` — vérifier les noms de paquets dans les manifestes), rejoue la séquence, compte les `tap`/`key` (hors `wait`), vérifie `attendu`, imprime `id	touches	seuil	OK/KO/NON-VÉRIFIÉ` ; code de sortie 1 si un KO ; `--dry-run` imprime seulement les comptes (sans appareil). Les coordonnées de la version actuelle **et** de `NAV_V2` sont deux fichiers : `tasks-legacy.tsv`, `tasks.tsv` (le script prend `--tasks`).
3. `docs/UX-MESURES.md` : tableau des 14 + 7 tâches avec colonnes « avant (compté dans le code) · avant (observé) · après (cible) · après (observé) » pré-rempli avec les chiffres de la conception § 1 et § 10.4 ; plus un tableau des densités (cibles, mots, tuiles, lignes) et la méthode de comptage des mots (`uiautomator dump` + `grep -o 'text="[^"]*"'`).
4. `docs/UX-TEST-PROTOCOLE.md` : § 10.3 de la conception développé : recrutement (5, dont ≥ 1 peu lettré, ≥ 1 > 60 ans, ≥ 1 novice), consentement oral, 8 tâches formulées sans le vocabulaire de l'app (liste de la conception), mesures (réussite 1er essai sans aide, touches/appuis, temps, hésitations, Retour inattendu), seuils (≥ 90 % de réussite, ≤ 3 touches / ≤ 5 appuis, 0 abandon), deux sessions (version actuelle puis `NAV_V2` à une semaine d'écart, mêmes personnes), fiche de compte rendu anonyme (modèle de tableau), lieu du rapport `docs/agent-reports/ux-w11-usagers.md`.
5. `TEST-CAMPAIGN.md` § W11 : 12 étapes (6 téléphone, 6 TV) qui reprennent les « Observable » de w11-07/08/10/11/12.

## Critères d'acceptation
```sh
bash -n tools/ux/taps.sh                         # syntaxe
tools/ux/taps.sh phone --dry-run | wc -l        # 14 lignes (téléphone)
tools/ux/taps.sh tv --dry-run | wc -l           # 7 lignes
awk -F'\t' 'NF!=6{bad++} END{exit bad>0}' tools/ux/tasks.tsv   # 6 colonnes partout
grep -c '^| ' docs/UX-MESURES.md                 # ≥ 21
```
Observable : sur un émulateur, `tools/ux/taps.sh phone --tasks tools/ux/tasks-legacy.tsv` rejoue au moins les tâches « télécommande » et « réglages » avec succès (les coordonnées des autres sont fournies au mieux et marquées `NON-VÉRIFIÉ` si l'émulateur manque).

## Cas limites
Pas d'`adb` ⇒ `--dry-run` seul, message clair. Densité inconnue ⇒ 1 dp = 1 px avec avertissement. Tâche dépendant d'une TV reliée (envoyer) ⇒ `NON-VÉRIFIÉ` sans échec.

## À ne pas faire
Aucune télémétrie, aucun envoi réseau, aucun nom d'usager dans les docs. Ne pas modifier le code des apps. Ne pas inventer de coordonnées pour `NAV_V2` tant que w11-07/10 ne sont pas fusionnés : laisser `tasks.tsv` avec les séquences en `key:` quand c'est possible et un `# TODO coordonnées` sinon.

## Rapport
`STATUT`, sortie de `--dry-run` des deux apps, tâches réellement rejouées sur émulateur, chemin des docs.
