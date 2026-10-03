# w18-12 — Docs : `docs/WIFI-DIRECT.md` (la TV est le point d'accès : premier contact, zéro geste, routes, sécurité, diagnostic, limites), renvois ADMIN/TRANSFER/BT-PLUG-AND-PLAY/HANDOFF/REGRESSIONS

<!-- routage Fable 2026-10-03 -->
> **Modèle : haiku** · escalade : aucune · statut : PRÊT (après w18-02)
> **Groupe : W18a-4** (vague W18a, docs) · prérequis : w18-01, 02 fusionnés (pour citer les vraies classes) · porte : `python3 tools/tests/test_docs_links.py 2>/dev/null || true ; grep -c "" docs/WIFI-DIRECT.md`
> **Jauge : ≈ 120 k jetons entrée / 12 k sortie** (effort S, ≈ 0,5 j) · audit Opus : non

**Vague 18a · Effort S · Modèle : haiku · Statut PRÊT.** Conception : `docs/coordination/DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` (tout) ; `docs/agent-reports/auto-wifi-direct.md` (R-14). Branche `claude/sonnet-w18-12`. Rapport : `docs/agent-reports/sonnet-w18-12.md`. Règle : documentation **de ce qui existe** après 18a (le cœur) et de ce qui est prévu (18b/18c) **marqué comme tel** ; aucun secret, aucune adresse complète hors 192.168.49.1 ; français ; « CastBridge » / « CastBridge-TV ».

## Objectif
Un document de référence `docs/WIFI-DIRECT.md` (≤ 180 lignes) : (1) pourquoi (décision du propriétaire, 85 % sans point d'accès, matériel neuf exclu) ; (2) le modèle : la TV est le point d'accès, secret persistant par TV, premier contact (Bluetooth + PIN, ou QR), reconnexion sans geste, routes `LAN > WD ≥ BT` par usage (table § 5.1) ; (3) ce qui passe par le groupe (copie, cast, télécommande, Quiz, lots, parental) ; (4) cycle de vie et ligne d'état (§ 5.3, § 5.4) ; (5) sécurité (§ 6, sémantique du PIN inchangée) ; (6) diagnostic (`/api/diag/wifi-direct`, écran TV, test terrain) ; (7) Android 10-12 et téléphones anciens ; (8) le secours Bluetooth et ses limites honnêtes (§ 9) ; (9) « non vérifié » (§ 12). Renvois : `docs/ADMIN.md` § Wi-Fi Direct (le MENU, le QR, la rotation, la route de diagnostic), `docs/TRANSFER.md` § 4 (`WifiDirectLane` : note que la voie WD est la `WifiLane` sur 192.168.49.1, pas une voie séparée), `docs/BT-PLUG-AND-PLAY.md` (§ Sécurité : « Autoriser » **ou** PIN ; § Routes), `docs/HANDOFF.md` § 0 (état W18, test terrain d'abord), `docs/REGRESSIONS.md` (R-14 : « suite : W18 », lien).

## Pourquoi (preuves)
- `docs/BT-PLUG-AND-PLAY.md:82` (« Routes : Wi-Fi commun → Wi-Fi Direct → Bluetooth seul ») et `:22` (deux validations) : à amender ; `docs/TRANSFER.md:31` (`WifiDirectLane` expérimentale) ; `docs/ADMIN.md:295-322` (CBTN, MENU Wi-Fi Direct, mot de passe affiché) ; `docs/REGRESSIONS.md` R-14 (branche) ; mémoire « Always write a handoff ».

## Fichiers possédés
Nouveau `docs/WIFI-DIRECT.md` ; une section/ligne dans `docs/ADMIN.md`, `docs/TRANSFER.md`, `docs/BT-PLUG-AND-PLAY.md`, `docs/HANDOFF.md` (§ 0 et § 9 dans le même commit), `docs/REGRESSIONS.md` (colonne « Corrigé en » de R-14 : « cœur W18a ; câblage W18b après test terrain »). **Hors zone** : tout code ; `docs/coordination/**` ; `docs/agent-briefs/**`.

## Étapes
1. Lire la conception et les rapports de w18-01/02 ; citer les classes réelles (`WdCredentials`, `WdPolicy`, `WdSession`, `WdLine`, `TrustByPin` si fusionné, sinon « prévu w18-03 »).
2. Écrire `WIFI-DIRECT.md` avec les 9 sections ; tableaux copiés de la conception **réduits** (pas de doublon intégral) ; un schéma ASCII (premier contact / zéro geste).
3. Renvois minimaux (une ligne chacun, lien vers le nouveau document) ; `HANDOFF.md` : « Test terrain Wi-Fi Direct : à faire par le propriétaire, kit `tools/wd/` » dans § 0.
4. Relecture : aucun mot de passe, aucun chiffre présenté comme mesuré s'il ne l'est pas (« attendu », « à mesurer »).

## Critères d'acceptation
`docs/WIFI-DIRECT.md` ≤ 180 lignes, 9 sections ; `grep -n -i "hotspot du téléphone\|routeur de voyage\|adaptateur" docs/WIFI-DIRECT.md` ne montre que la phrase « écartés par décision du propriétaire » ; `grep -n "Mo/s" docs/WIFI-DIRECT.md` : chaque occurrence qualifiée (« attendu » ou « mesuré le … ») ; `HANDOFF.md` § 0 et § 9 modifiés dans le même commit.

## Cas limites
w18-03 non fusionné au moment de la rédaction ⇒ section « premier contact » dit « deux validations aujourd'hui, une seule prévue (w18-03) » ; verdict terrain déjà connu ⇒ l'inscrire avec la date.

## À ne pas faire
Pas de code ; pas de promesse de débit ; ne pas réécrire `BT-PLUG-AND-PLAY.md` (renvoi) ; pas de secret.

## Rapport
`STATUT`, plan du document, lignes changées dans chaque fichier, phrases marquées « prévu ».
