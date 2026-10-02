# w5-09 — Serveur : console `/admin/shop/**` (commandes, paiements, contrats, bons, jetons, reçus, remboursements, anomalies, CSV)

**Vague 5b · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w5-06, w5-07, w5-08).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 4.4 (dernière ligne), § 5.4, § 6.5, § 10. Branche `claude/sonnet-w5-09`. Rapport : `docs/agent-reports/sonnet-w5-09.md`.

## Objectif
Le propriétaire voit et agit : **Commandes** (liste, filtres par état/canal/mois, recherche par code de commande, de reçu, d'appareil ; détail ; **Rembourser** (TOTP, motif : marque `REFUNDED`, `OwnerGrantPayment` négatif, pour une location : contrat `REVOKED` comptablement, la TV n'est pas touchée ; pour des jetons : `ADJUST` négatif si le solde le permet, sinon refus « jetons déjà dépensés »)), **Contrats** (actifs/terminés, réémissions, « Réémettre la boîte » (TOTP) si le compteur est épuisé), **Bons** (importer un lot signé, liste par lot : émis/vendus/redimés/révoqués/expirés, taux par agent, **Révoquer un lot / un bon** (TOTP, motif), export CSV du stock), **Jetons** (solde par licence, grand livre, **Ajuster** (TOTP, motif, ±), « Rétablir l'hors ligne » pour une installation marquée), **Reçus** (recherche, réimpression), **Anomalies** (liste, « Clore », « Rouvrir ») ; tableau de bord (ventes du mois par article et par canal, en XAF ; jetons crédités/dépensés ; contrats actifs ; bons en circulation).

## Pourquoi (preuves)
- `B/licenses/LicenseWebController.java` + `templates/admin/lic-*.html`, `lic-nav.html:5-13` (navigation, style, rôles `OWNER/SUPPORT/READONLY`, `actor.require(..., props.requireTotp())`) ; `B/licenses/LicenseCsv.java` (export) ; `B/licenses/AuditLog.java` (journal chaîné : chaque action d'ici y écrit) ; w4-16 `/admin/agents` (modèle et liens croisés : une confirmation d'agent renvoie à sa fiche) ; w5-06 `AnomalySink`.

## Fichiers possédés
Nouveaux `B/shop/admin/AdminShopController.java`, `AdminShopService.java`, `ShopCsv.java`, `B/shop/ShopAnomalies.java` (implémente `AnomalySink` de w5-06, table `shop_anomaly` si absente du schéma : **vérifier** ; sinon réutiliser `agent_anomaly` avec `agent_kid NULL` : choisir et le dire), `backend/src/main/resources/templates/admin/shop*.html` (`shop-orders`, `shop-order`, `shop-contracts`, `shop-vouchers`, `shop-tokens`, `shop-receipts`, `shop-anomalies`, `shop-dashboard`), `BT/shop/admin/**` ; modifié `templates/admin/lic-nav.html` (un lien « Boutique »). **Hors zone** : `B/shop/{order,rental,tokens,wire}/**` (appeler ; demander dans le rapport toute méthode manquante), `B/agents/**`, `B/licenses/**`, Android, docs.

## Étapes
1. Routes `/admin/shop/**` (session web, CSRF, rôles : `OWNER` écrit, `SUPPORT` lit et clôt les anomalies, `READONLY` lit) ; TOTP sur : rembourser, révoquer, ajuster, réémettre, rétablir l'hors ligne.
2. Pages Thymeleaf dans le style existant (tables, filtres GET, pagination 50, messages flash FR) ; aucune valeur d'exemple ; montants affichés en XAF entiers.
3. `ShopAnomalies` : `record(kind, licenseId?, deviceCode?, orderRef?, agentKid?, detail)` ; dédoublonnage 24 h par `(kind, cible)` ; états `open/closed`.
4. CSV : commandes par mois (ref, date, canal, article, montant, moyen, agent, état), bons par lot (serial, état, agent, dates), jetons par licence.
5. `AuditLog.record(actor, action, target, reason)` pour chaque action d'écriture.
6. Tests MockMvc : accès par rôle ; remboursement d'une location (état, audit) ; remboursement de jetons refusé si dépensés ; import de lot (bon/mauvais) ; révocation ; ajustement avec/sans TOTP ; CSV non vide ; lien dans la navigation.

## Critères d'acceptation
```sh
cd backend && ./mvnw -q -o test -Dtest='AdminShopControllerTest,ShopAnomaliesTest,ShopCsvTest'   # vert
cd backend && ./mvnw -q -o test   # suite complète verte
grep -n 'shop' backend/src/main/resources/templates/admin/lic-nav.html   # 1 lien
grep -rn 'XAF\s*[1-9]' backend/src/main/resources/templates/admin/shop*.html   # 0 hit (aucun montant en dur)
```

## Cas limites
- Remboursement d'une commande `DELIVERED` depuis plus de 7 jours : autorisé pour `OWNER` avec motif (politique CGV = décision commerciale, l'outil ne l'empêche pas, il le signale « hors délai CGV »).
- Révocation d'un lot avec des bons déjà `REDEEMED` : ceux-là restent redimés ; la page le dit.
- Module éteint : `/admin/shop/**` ⇒ 404 (comme `LicenseFeature`).

## À ne pas faire
Pas de déploiement ; pas de commit sur les branches partagées ; aucun montant réel ; aucune action destructrice sans TOTP et motif ; ne pas écrire dans `token_ledger`/`shop_order` sans passer par les services de w5-07/08 ; ne jamais afficher un code de bon en clair (le serveur ne l'a pas).

## Rapport
`STATUT`, pages livrées, actions TOTP, table d'anomalies retenue, méthodes manquantes demandées.
