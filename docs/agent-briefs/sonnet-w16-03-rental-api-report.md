# w16-03 — Cœur routes et vue : `GET /api/rental` additif (unité, usage), `GET /api/rental/usage` (relevé), vue téléphone, `RentalUsageReport` (analyse, fusion monotone, stockage téléphone)

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon (format de relevé) · statut : PRÊT (après w16-01, w16-02)
> **Groupe : W16a-3** (vague W16a, cœur, autorisé pendant le gel) · prérequis : w16-01, w16-02 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*RentalApi*' --tests '*RentalDelivery*' --tests '*RentalUsageReport*'`
> **Jauge : ≈ 350 k jetons entrée / 18 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 16a · Effort M · Modèle : sonnet · Statut PRÊT.** Conception : DESIGN-W16 § 2.5, § 3.2 (relevés), § 1.3 (phrases). Branche `claude/sonnet-w16-03`. Rapport : `docs/agent-reports/sonnet-w16-03.md`.

## Objectif
Le téléphone (et `louer.py`) peut lire sur la TV, pour chaque location : l'unité, les minutes utilisées, le budget, le reste, l'état et la raison ; il peut tirer le **relevé** texte ; le cœur sait l'analyser, le **fusionner** (maximum monotone par contrat) et le ranger sur le téléphone pour le remettre plus tard. Les lecteurs anciens de `GET /api/rental` ne voient rien casser.

## Pourquoi (preuves)
- `C/lots/RentalApi.kt:62-72` : `statusJson` n'expose que `remainingMs`, `message`, `endsAt`, `lots`, `keyInSafe` : aucune minute d'usage.
- `C/lots/RentalDelivery.kt:62-70` : `TvRentalView.Rental` idem ; `:79-91` `status()`.
- `C/owner/TrialPolicy.kt:33, 35` : `/api/rental` et son préfixe sont **déjà** ouverts en essai (une nouvelle route sous `/api/rental/` l'est aussi : à noter dans `tools/routes/routes.txt` si w1-06 est fusionné, **hors zone** ⇒ le signaler au rapport).
- `RentalLedger.usageReport` (w16-02) fournit le texte.

## Fichiers possédés
`C/lots/RentalApi.kt`, `C/lots/RentalDelivery.kt` (zones `TvRentalView`, `status()` ; `deliver` inchangé), nouveaux `C/lots/RentalUsageReport.kt`, `CT/lots/RentalUsageReportTest.kt` ; `CT/lots/RentalApiTest.kt`, `CT/lots/RentalDeliveryTest.kt` (tests ajoutés). **Hors zone** : `RentalEngine.kt`, `RentalLedger.kt`, `R/`, `S/`, `tools/routes/routes.txt`.

## Étapes
1. **Rouge** : `RentalApiTest.statusCarriesUnitAndUsage` (JSON contient `unit`, `usedMinutes`, `maxUsageMinutes`, `remainingUsageMinutes`, `reason`, `period`, `startsAt` ; les clés anciennes inchangées) ; `RentalApiTest.usageRouteReturnsTheReport` (`GET /api/rental/usage` ⇒ `200`, corps texte `castbridge-rental-usage-v1`) ; `RentalDeliveryTest.viewReadsUsageAndStaysCompatibleWithOldTv` (JSON sans les nouvelles clés ⇒ champs `null`, aucune exception) ; `RentalUsageReportTest` : analyse, ligne malformée ignorée et comptée, fusion monotone (`used` ne recule jamais, `state` le plus avancé gagne), stockage `RentalReportStore(dir)` (un fichier par `install`, `SafeFile`, `.bak`), rendu « lignes » en français pour l'écran.
2. `RentalApi` : `statusJson` additif ; nouvelle route `GET /api/rental/usage` (`handle`, texte brut, `Content-Type: text/plain; charset=utf-8` via `ApiReply` : vérifier comment `ApiReply` porte le type ; sinon JSON `{"report": "<texte>"}` et le dire).
3. `TvRentalView.Rental` : champs additifs nullable `unit: String?`, `usedMinutes: Long?`, `maxUsageMinutes: Long?`, `remainingUsageMinutes: Long?`, `reason: String?`, `period: Long?` ; `lines()` : pour `HOURS` « CM2 : 5 h 20 d'utilisation restante(s) sur 12 h · à utiliser avant le 15/11 » ; `DAYS` : ligne existante.
4. `RentalUsageReport` : `parse(text): Report` (`install`, `lines`, `ignored`), `merge(old, new)`, `format(report)`, `RentalReportStore.put(report)/all()/export(): String` (concaténation pour partage).
5. Crochet documenté (KDoc) : « signature du relevé par `InstallSigner` (W6, w6-12) : champ `sig=` en fin de relevé, ignoré s'il est absent ».
6. Vert : porte + `:core:test` complet.

## Critères d'acceptation
Porte verte ; 7 tests rouges puis verts ; `curl`-équivalent dans le test : JSON de `GET /api/rental` d'avant (fixture figée) toujours accepté par `RentalDelivery.status()` ; aucun identifiant de licence/poste/personne dans le relevé (test de chaînes).

## Cas limites
TV ancienne sans `/api/rental/usage` (404 ⇒ `null`, message « cette TV ne fournit pas de relevé : mettez CastBridge-TV à jour ») ; relevé d'une autre installation (en-tête `install=` différent ⇒ fichier séparé) ; relevé vide (aucun contrat).

## À ne pas faire
Ne pas changer `deliver()` ; ne pas renommer une clé JSON existante ; pas de HTTP réel dans les tests (transport factice comme `RentalDeliveryTest.Tv`).

## Rapport
`STATUT`, sorties rouge/vert, JSON d'exemple avant/après, type de contenu retenu pour le relevé, rappel : ajouter `/api/rental/usage` à `tools/routes/routes.txt` (hors zone) et à `TrialPolicy` (déjà couvert par le préfixe : vérifié ou non).
