STATUT: TERMINÉ (correctifs de l'audit Opus intégrés)
CAHIER: w16-01 · MODÈLE: sonnet · BRANCHE: claude/w16-01-rental-engine · COMMIT: voir git log (commit amendé, un seul)
FICHIERS: android/core/.../lots/RentalEngine.kt, lots/RentalLedger.kt (config passée à contracts()), lots/RentalVectors.kt (config explicite), owner/KeyBadge.kt, tests RentalUnitsTest.kt (neuf) et RentalTest.kt (config explicite + RentalLedgerUnitsTest), docs/RENTAL-LOTS.md, docs/agent-briefs/sonnet-w16-04-pilot-rules-issuing.md (amendement), ce rapport. Rien dans sender/receiver.

CORRECTIFS DE L'AUDIT
1. `RentalConfig.perUnitMessages` vaut TRUE par défaut (RentalHub passe RentalConfig() : le chemin réel est par unité). Vecteurs v1/v2 (RentalVectors) et RentalTest passent `perUnitMessages = false` explicitement. Le carnet passe sa config à `contracts()` (RentalLedger:76/93), donc clamp et notes suivent la config réelle. Chemin carnet testé : fin par usage à 5760 min (« Vos 96 heures d'utilisation sont épuisées… »), ouverture silencieuse d'1 h.
2. Une ligne d'unité différente de la première du contrat est IGNORÉE (n'étend pas la fin, n'efface pas le budget) ; le test l'affirme (6 h + « 5 j sans budget » : budget 360 conservé, fin inchangée ; et l'inverse). Raison en français dans `RentalStatus.notes`.
3. Excédent de 96 h conservé en clamp mais exposé : « 6 h non applicables : plafond de 96 h par location ». Amendement « W16-01 » ajouté à l'en-tête de sonnet-w16-04 : l'ÉMETTEUR doit refuser la prolongation > 96 h et le mélange d'unités.
4. Clamp sans drapeau, accepté et documenté (docs/RENTAL-LOTS.md § 2) : règle du moteur pour toute location horaire ; test qui le fixe (ligne unique de 6000 min coupée à 5760, note « 4 h »).
5. Alertes en jours : « 7 jours » seulement si durée ≥ 14 j, « dernières 24 h » seulement si durée > 2 j, « dernière heure » toujours (`daysWarning`). 1 j / 2 j / 3 j / 13 j / 14 j testés. HOURS : aucune alerte de date avant la date de sûreté (bandeau W16-10).
Mineurs : `hoursExpired` sans « (fin du test gratuit) » ; badge : contrat aux MOINS d'heures restantes (pas la date), aucune heure en GRACE (ligne « de tolérance »), jamais de conversion jours→heures (« 2 jour(s) restant(s) », pas « 30 h »), libellé « jour(s) » ; `defaultDate` en Africa/Douala.

ROUGE (code neutralisé : défaut false, mélange non ignoré, alertes jours non relatives, ancienne phrase, fuseau UTC, note du clamp coupée) : 12 des 16 tests rouges par assertion (3 RentalLedgerUnitsTest + 9 RentalUnitsTest). Les 4 autres (clamp, signature, essai, ancien format) décrivent un comportement inchangé.
VERT : porte `*Rental*`, `*KeyBadge*`, `*TrialWindow*` ; `:core:test` complet VERT (2552 tests, 0 échec) ; `:sender:compileDebugKotlin` et `:receiver:compileDebugKotlin` VERTS ; diff tools/activation vide, RentalLines inchangé, vecteurs JSON inchangés.

RISQUES RESTANTS : un contrat plafonné en usage émis avant W16 s'affiche désormais par unité sur la TV (accepté : licences éteintes) ; le badge affiche deux lignes « Location » si heures et jours coexistent ; une ligne de renouvellement d'unité différente est ignorée en silence côté TV hors statut (visible dans `notes` : à afficher par w16-10) ; seuils horaires 25 % / 60 min / 10 min à valider sur terrain.
AUTOCONTRÔLE: [x] zone [x] porte [x] suite [x] secrets [x] dépendances [x] FR [x] diff [x] un commit
