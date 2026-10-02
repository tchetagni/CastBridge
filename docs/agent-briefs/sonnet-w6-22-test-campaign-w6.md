# w6-22 — Campagne de test W6 : `docs/TEST-CAMPAIGN.md` § W6 (TV de référence 32 bits + téléphone), scénarios émulateur, liste de contrôle du coordinateur

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT (après 6c, 6d)
> **Groupe : W6e-1** (vague W6e) · prérequis : w6-12, w6-13, w6-14, w6-15, w6-16, w6-17, w6-18, w6-19 · porte : `grep -c '^- \[ \]' docs/TEST-CAMPAIGN.md`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 6e · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT (après fusion de 6c et 6d).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.7, § 3.3, § 3.7, § 3.8, § 4.2. Branche `claude/sonnet-w6-22`. Rapport : `docs/agent-reports/sonnet-w6-22.md`.

## Objectif
Une section « W6 » de `docs/TEST-CAMPAIGN.md` (créer le fichier s'il n'existe pas, même style que les sections W5 de w5-22) : ≈ 45 étapes numérotées, chacune avec **précondition, action, résultat attendu, où regarder** (écran, `adb logcat` filtre, fichier), en trois blocs : (A) rapports parentaux « toute la TV » (consentement, indicateur adulte/enfant, tranches, écran, Sudoku, connexions, Wi-Fi vs Bluetooth, trous, révocation, chiffrement du magasin, tunnel refusé) ; (B) mode minimal (une étape par **cellule notable** de la matrice § 3.7 : A, B, C, D, E, F, G, H pour `SEND_FILES_TO_TV`, `PHONE_LIBRARY_PLAYER`, `LOTS_SYNC`, `SHOP_ORDER`, `PARENTAL_DASHBOARD`, `CAST_TO_LINKED_TV`, `FREE_CONTENT_DOWNLOAD` ; essai + production appariées ; preuve expirée par avance d'horloge **du téléphone** ; identité de TV changée ; `-PrequireTvProof` éteint = aucun changement) ; (C) session super (ouverture, bandeau, verrou 2 min, fermeture, horloge, `check_no_superadmin.sh` sur une build ordinaire et une build propriétaire).

## Pourquoi (preuves)
- `docs/agent-briefs/sonnet-w5-22-shop-test-campaign.md` (format), `docs/TEST-CAMPAIGN.md` (s'il existe), rapports `sonnet-w6-12…19` (captures, points observables), `tools/shop-test/` (fumée : ne pas dupliquer).

## Fichiers possédés
`docs/TEST-CAMPAIGN.md` (section W6 seulement ; créer si absent). **Hors zone** : tout le reste.

## Étapes
1. Lire les cahiers 6c/6d et leurs rapports ; relever chaque « Observable ».
2. Écrire les étapes ; numéroter `W6-A-01…`, `W6-B-01…`, `W6-C-01…` ; mentionner les commandes `adb` utiles (`adb shell appops set <pkg> GET_USAGE_STATS allow`, `adb shell date` **sur le téléphone seulement** pour l'expiration, jamais sur la TV de production).
3. Liste de contrôle finale « avant d'allumer `REQUIRE_TV_PROOF` » (§ 3.4, trois temps) et « avant de distribuer la fonction parentale » (D-W6-4).

## Critères d'acceptation
```sh
grep -c '^| W6-' docs/TEST-CAMPAIGN.md   # ≥ 45
grep -n 'requireTvProof' docs/TEST-CAMPAIGN.md   # ≥ 2
```

## Cas limites
Une étape impossible sur l'émulateur (Bluetooth) est marquée « TV réelle seulement ».

## À ne pas faire
Ne rien exécuter sur la TV de production ni sur le serveur ; pas de code.

## Rapport
`STATUT`, nombre d'étapes, étapes « TV réelle seulement ».
