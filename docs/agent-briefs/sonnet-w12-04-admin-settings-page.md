# w12-04 — Serveur : page `/admin/settings` (éditeur avec bornes, aperçu du diff, signer et publier sous TOTP, historique, retour arrière, interrupteur, parc par version, expériences)
<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus seulement si le diff touche la signature ou le TOTP (il ne devrait pas) · statut : PRÊT
> **Groupe : W12-b** (vague W12) · prérequis : w12-03 fusionné (API et service) ; `TPL/lic-nav.html` **après** w5-09 / w10-06 s'ils sont lancés (un lien) · porte : `cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='AdminSettingsPage*Test'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 12b (serveur, pages) · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT.** Conception : `DESIGN-W12-REGLAGES-TEASING-2026-10-02.md` § 4.1, § 3.4, § 5.3, § 6.2. Branche `claude/sonnet-w12-04`. Rapport : `docs/agent-reports/sonnet-w12-04.md`.

## Objectif
Les six pages de § 4.1 en Thymeleaf, **sans JavaScript tiers** (CSP stricte comme `/admin/orders`), formulaires CSRF, rôle `OWNER`, TOTP demandé **sur la page** pour « Publier » et « Interrupteur » : (1) Réglages courants ; (2) Brouillon (champ par clé, bornes affichées, erreur par clé, sensibilité €/🔒/UX en pastille textuelle, zone expériences) ; (3) Aperçu du diff + estimation d'impact + bouton « Signer et publier » ; (4) Historique (seq, date, auteur, résumé, `basedOn`, « Rétablir cette version », « Télécharger le jeton » pour la clé USB / QR) ; (5) Parc par version (`settings_device`) ; (6) Grilles de prix déposées (affichage seul ; « aucune » tant que W4-C n'est pas fusionné). Textes en français.

## Pourquoi (preuves)
- Patron de page d'administration avec CSRF/CSP et TOTP : `B/orders/AdminOrdersPage.java:22-63`, `B/licenses/LicenseWebController.java:94-487` (`/admin/licenses/security` pour le TOTP), gabarits `TPL/*.html`, navigation `TPL/lic-nav.html`.
- Service et API : cahier w12-03 (contrat) ; schéma : `tools/settings/schema.json` (bornes, défauts, consommateurs, sensibilité).
- Décision : « une expérience sans garde-fou ni seuil écrit n'est pas publiable » (conception § 3.4) ; « jamais automatique » (le bouton « clore » propose un brouillon, il ne signe pas).

## Fichiers possédés
- Nouveaux : `B/settings/AdminSettingsPage.java`, `TPL/settings.html`, `TPL/settings-draft.html`, `TPL/settings-diff.html`, `TPL/settings-history.html`, `TPL/settings-fleet.html`, `TPL/settings-experiments.html`, `BT/settings/AdminSettingsPageTest.java`.
- Existants : `TPL/lic-nav.html` (**un** lien « Réglages »).
- Hors zone : `B/settings/{SettingsService,…}.java` (w12-03 : si une méthode manque, la **demander au rapport**, ne pas l'ajouter), `EventCatalog`, KPI (**w12-10**).

## Étapes
1. Page « Réglages courants » : tableau trié par famille ; colonne « publié » vide si égal au défaut ; lien vers le brouillon.
2. Brouillon : un champ par clé (type adapté : nombre, case, texte), aide « bornes : 1–365 · défaut 30 · lu par : émetteurs » ; message `settings.message` (≤ 280, sans lien : erreur sinon) ; expériences : nom, clé (liste), bras (≤ 4), sel (bouton « nouveau sel »), dates, garde-fou, hypothèse, métrique, seuils go/no-go (**obligatoires**).
3. Diff : ancien → nouveau par clé, ajoutées / retirées, compteurs € et 🔒, validité (défaut 90 j), résumé obligatoire (≤ 200) ; TOTP ; après publication : seq, lien « Télécharger le jeton » (texte `cbx1…` + LF, nom `settings`) et « QR » (SVG généré côté serveur via `B/licenses/QrSvg.java`, existant).
4. Historique : liste, « Rétablir » (pré-remplit le brouillon avec `basedOn`), « Interrupteur » (brouillon `reset=all`, TOTP), « Vérifier l'audit ».
5. Parc : nombre d'appareils par seq, périmés, jamais vus (jointure `settings_device` / `device`) ; aucune donnée personnelle (étiquette admin existante seulement).
6. Tests MockMvc : accès refusé sans rôle ; bornes refusées avec message par clé ; publication sans TOTP refusée ; l'historique montre seq croissants ; rétablir crée un brouillon ; CSP sans `unsafe-inline`.

## Critères d'acceptation (hors ligne)
- Porte verte ; aucune ressource externe (CSP) ; tous les textes en français ; rapport avec captures textuelles des pages (`curl` sur le serveur de test) et la liste des méthodes de service manquantes s'il y en a.
