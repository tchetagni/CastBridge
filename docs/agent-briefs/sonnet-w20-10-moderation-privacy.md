# w20-10 — Modération et données personnelles : signalements, bannissements, page admin minimale (jeton distinct), masquage de pseudonyme, page `/play/confidentialite`, `docs/QUIZ-EN-LIGNE.md`
<!-- routage Fable 2026-10-03 -->
> **Amendement 2026-10-04 — conception `docs/coordination/DESIGN-W20-AMENDEMENT-TV-SEULEMENT-2026-10-04.md` § 1, § 3, § 4.** Décisions du propriétaire : seule une TV activée connectée à Internet joue ; téléphones = joueurs locaux relayés ; aucune page web de jeu ; aucune délégation. **Changements de périmètre** : (1) signalements émis **par une TV** (au nom d'un de ses sièges : la TV relaie le `report` de son téléphone) ; (2) bannissements portés sur l'**identité de TV** (code d'appareil signé, haché) et, au besoin, sur un siège de cette TV ; plus de bannissement par `deviceHash` de navigateur ; (3) l'hôte peut retirer une **TV invitée** (`kick` d'un siège relais : ses sièges partent, fait par `sonnet-w20-04b`) ; (4) `/play/confidentialite` reste permise comme **page d'information** statique (pas de jeu, pas de formulaire) ; la case d'âge « 13 ans ou plus » de la page est **sans objet** ; la protection des mineurs reste sur la TV (`ParentalEngine`, `PlayRules.internetForProfile`) ; (5) données : aucune donnée de téléphone n'atteint le service (ni identifiant, ni adresse) ; la table § 3.1 de DESIGN-W20 perd la ligne « identifiant aléatoire du navigateur ». **Statut : phase 2, après w20-09.**
> **Modèle : sonnet** · escalade : audit Opus **échantillon** (jeton admin distinct, purge) · statut : PRÊT (après w20-09)
> **Groupe : W20-S2** (produit) · prérequis : w20-09 fusionné ; D-W20-11, D-W20-13 · porte : `gradle :play-server:test --tests 'castbridge.play.moderation.*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M, ≈ 1,5 j) · audit Opus : échantillon

**Vague 20 · Effort M · Modèle : sonnet.** Conception : `DESIGN-W20` § 3 (3.1-3.5), § 7 (D-W20-11, 13). Branche `claude/sonnet-w20-10`. Rapport : `docs/agent-reports/sonnet-w20-10.md`. Aucun `S/`, `R/`, `backend/src/`.

## Objectif
(1) Signalements : message `report{targetPlayerId, reason ∈ {pseudo, triche, autre}}` ⇒ `play_report(id, roomId, reporterHash, targetHash, targetName8, reason, at)` ; ≥ 3 appareils distincts en 24 h ⇒ pseudonyme masqué « Joueur N » dans la salle (`mute`) + `BotScore` +20. (2) Bannissements : `play_ban(deviceHash, until, reason, by, at)` ; `join`/`create` ⇒ `PLAY_BANNED` avec `retryAfterMs` et texte « jusqu'au <date> » ; **jamais** en local. (3) Page admin minimale `GET /play/admin` (HTML sobre, Thymeleaf ou chaîne), protégée par `CASTBRIDGE_PLAY_ADMIN_TOKEN` (**distinct** de `CASTBRIDGE_ADMIN_TOKEN`, en-tête `Authorization: Bearer`, jamais en URL), vue : signalements récents (pseudonyme à 8 hex + nom masqué), salles vivantes (compteurs), bannir 1 j / 7 j / 30 j / définitif, lever ; journal d'audit `play_audit` (action, cible, horodatage ; aucun pseudonyme en clair). (4) `GET /play/confidentialite` : texte français (inventaire § 3.1, durées D-W20-12, droit d'effacement, enfants § 3.2, contact du propriétaire **à remplir** : `{{CONTACT}}`). (5) `docs/QUIZ-EN-LIGNE.md` : le fonctionnement complet pour l'usager et le propriétaire (périmètres, signe, codes, droits, données, modération, limites honnêtes T-15), **marqué « textes juridiques à relire au 2026-12-31 »**. (6) Rétention des signalements 90 j et bannissements durée + 30 j (extension de `PlayRetentionJob`).

## Pourquoi (preuves)
- `B/config/SecurityConfig` + `AdminToken` : le jeton admin principal donne `ROLE_ADMIN` sur toute l'API : il ne doit **pas** entrer dans le service exposé (T-9).
- `docs/PARENTAL.md` § Sécurité : code jamais dans l'URL, verrouillage progressif : même règle pour le jeton admin de `play` (5 échecs/IP ⇒ 15 min).
- `docs/TELEMETRY.md` : « deviceName n'est jamais stocké », IP ≤ 30 j : le service `play` fait **plus court** (7 j, journaux seulement).
- Mémoire projet « Juridique reporté au 31/12/2026 » : les textes sont fonctionnels, pas contractuels.

## Fichiers possédés
- Service : `server-play/src/main/resources/db/migration/V2__moderation.sql`, `server-play/src/main/kotlin/castbridge/play/moderation/{ReportService, BanService, ModerationController, AdminTokenFilter, AuditLog}.kt`, `server-play/src/main/resources/templates/play-admin.html` (ou chaîne), `server-play/src/main/resources/static/play/confidentialite.html`, tests `…/moderation/{ReportServiceTest, BanServiceTest, ModerationControllerTest, AdminTokenFilterTest}.kt`.
- Docs : `docs/QUIZ-EN-LIGNE.md` (nouveau), zone additive `docs/QUIZ.md` (renvoi § 10).
- Zone additive : `PlayRetentionJob` (w20-09).
- Interdit : `backend/**`, `C/**` sauf lecture.

## Étapes
1. **Rouge** : `ReportServiceTest` (3 signalements même appareil ⇒ 1 compté ; 3 appareils ⇒ masqué), `BanServiceTest` (banni ⇒ `PLAY_BANNED` + `retryAfterMs` ; levée ; expiration), `AdminTokenFilterTest` (sans jeton 401 ; jeton en URL 400 ; 5 échecs ⇒ 429 15 min ; le jeton de l'API principale **ne marche pas**), `ModerationControllerTest` (page sans pseudonyme en clair : regex sur la réponse).
2. Services, page admin, audit.
3. Page confidentialité + `QUIZ-EN-LIGNE.md`.
4. **Vert**.

## Critères d'acceptation
- Aucune route admin sous `/api/`, aucune lecture de `CASTBRIDGE_ADMIN_TOKEN` (test de source).
- Un bannissement ne touche jamais une partie **locale** (le code TV ne lit pas `play_ban` : rappel dans la doc ; test : `LocalAuthority` n'a pas de notion de bannissement).
- `confidentialite.html` ≤ 20 Ko, français, sans script.

## Cas limites
- Signalement d'un joueur déjà parti ⇒ accepté (le `targetHash` est connu de la salle 24 h). Bannissement définitif ⇒ `until = null` ; `DELETE /play/me` d'un banni ⇒ supprime ses données **mais garde** la ligne `play_ban` (hachage seul) jusqu'à son terme : dit dans la page.

## À ne pas faire
- Pas de courriel, pas de formulaire de contact avec champ libre, pas de chat de modération. Pas de liste de pseudonymes en clair dans la page admin. Pas de bannissement par IP.

## Rapport
Format RAPPORT + `SYMBIOSE: cap=report · proto=play-v1 (message additif) · reason=PLAY_BANNED · deux écrans=—` + texte de la page de confidentialité en annexe du rapport.
