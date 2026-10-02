# w12-11 — Docs : `docs/REGLAGES.md` (nouveau) et mise à jour de `ACTIVATION-FORMAT.md` (type `settings`), `API-SERVER.md`, `TELEMETRY.md`, `ORDRES.md`, `ADMIN.md`, `HANDOFF.md` depuis les rapports des cahiers w12-01…10
<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet si un rapport manque ou se contredit (ne pas inventer) · statut : PRÊT (à lancer **après** la fusion de la première tranche ; seconde passe après w12-09/10)
> **Groupe : W12-d** (vague W12) · prérequis : rapports `docs/agent-reports/sonnet-w12-0{1,3,4,6,7,8}.md` présents · porte : `ls docs/REGLAGES.md && grep -c 'type=settings' docs/ACTIVATION-FORMAT.md && grep -c 'settings_applied' docs/TELEMETRY.md`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Vague 12d (docs) · Effort S (≈ 0,5 j) · Modèle : haiku · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` (§ 1 à § 7 : source des tableaux à recopier). Branche `claude/sonnet-w12-11`. Rapport : `docs/agent-reports/sonnet-w12-11.md`. Règle d'or : **recopier ce que les rapports disent avoir fait** ; ce qui n'est pas fait est écrit « conception cible non réalisée » ; **aucun secret, aucun montant, aucun texte juridique** (reporté au 2026-12-31).

## Objectif (édition mécanique, forme avant/après)
1. **Créer `docs/REGLAGES.md`** avec ces sections, dans cet ordre : `# Réglages signés (document « settings ») et phase de teasing` ; `## 1. Principe` (12 lignes : copier § 0 lignes 2-4 de la conception, au présent, sans « sera ») ; `## 2. Inventaire` (copier les tableaux § 1.1, § 1.2, § 1.3 **tels quels**, en remplaçant la colonne « Tranche » par « Consommateur branché : oui/non » d'après les rapports) ; `## 3. Format` (copier § 2.2 et la liste d'étapes § 2.3) ; `## 4. Livraison` (schéma § 2.7 + les chemins réellement branchés : Wi-Fi, USB, Bluetooth si w12-09 est fusionné) ; `## 5. Expériences` (§ 3.1-3.3, et la méthode D-W12-5) ; `## 6. Console` (§ 4.1-4.2 : seules les routes livrées par w12-03/04/10) ; `## 7. Diagnostic et UX` (§ 5) ; `## 8. Limites honnêtes` (§ 6.1 : colonne « Résiduel ») ; `## 9. Secours hors ligne` (commandes `tools/settings/*.py` du rapport w12-05, si fusionné).
2. **`docs/ACTIVATION-FORMAT.md`** : après § 3.4 (ligne `## 4. Codages de transport`), insérer `### 3.5 Type \`settings\` (réglages signés)` : corps § 2.2, ordre de vérification § 2.3 (12 étapes), portée `POLICY`, `target=any`, séquence propre ; ajouter une ligne au tableau § 11.1 (fichier `settings-vectors.json`, qui rejoue quoi : d'après les rapports w12-01/03/05).
3. **`docs/API-SERVER.md`** : section `## Réglages signés` avec les routes de § 4.2 **livrées** (rapport w12-03) et l'interrupteur `CASTBRIDGE_SETTINGS_ENABLED`.
4. **`docs/TELEMETRY.md`** : § 4 : si w12-02/10 ne l'ont pas déjà fait, ligne `settings_applied` (essentiel) et note « propriété `exp` sur les événements d'usage seulement » ; § 5 : indicateur « Parc par version de réglages » et « Expériences ».
5. **`docs/ORDRES.md`** : § 2 tableau : ligne « Réglages : document `settings` distinct, cf. REGLAGES.md ; priorité `budget.set`/`lots.tvBudgetMb` = plus récent » ; § 13 : cocher les points réalisés par w12-09 (d'après son rapport).
6. **`docs/ADMIN.md`** : entrée « `/admin/settings` ».
7. **`docs/HANDOFF.md`** : paragraphe « Réglages signés (W12) : état, première tranche fusionnée ou non, commandes, ce qui reste » (**jamais de secret**, mémoire du propriétaire : le handoff est toujours à jour).

## Fichiers possédés
`docs/REGLAGES.md` (nouveau), `docs/ACTIVATION-FORMAT.md`, `docs/API-SERVER.md`, `docs/TELEMETRY.md` (§ 4-5), `docs/ORDRES.md`, `docs/ADMIN.md`, `docs/HANDOFF.md`. Hors zone : `docs/OWNER-CONSOLE.md`, `docs/ACTIVATION-TOOLS.md` (w12-08), `docs/TEST-CAMPAIGN.md`, `docs/COORDINATION.md` (w12-12), tout code.

## Critères d'acceptation
- Porte verte ; aucun chiffre qui ne vienne de la conception ou d'un rapport ; chaque section de `REGLAGES.md` cite le rapport source entre parenthèses ; `grep -n 'BLOQUÉ\|non réalisée' docs/REGLAGES.md` liste ce qui manque.
