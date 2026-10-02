# w5-19 — Conditions de vente de la boutique (acceptation versionnée sur TV et téléphone) et magasin local des reçus

**Vague 5d · Effort S/M (≈ 1 j) · Modèle : haiku · Statut PRÊT (après w5-01 ; le texte des CGV vient de w5-21 : d'ici là, version « 0 » = texte d'attente clairement marqué « projet, à valider »).** Conception : `DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 9 (tunnel / conditions), § 11. Branche `claude/sonnet-w5-19`. Rapport : `docs/agent-reports/sonnet-w5-19.md`.

## Objectif
(1) `ShopTerms` (cœur) : texte versionné (`version`, `date`, `text`), `accepted(store): Boolean`, `accept(store, version, nowMs)` ; stocké sur la TV (`files/shop/terms.txt`) et sur le téléphone (`files/shop/terms.txt`), **par appareil** ; la première commande / le premier bon exige l'acceptation ; `shop_order.terms_version` est renseigné par le client (champ de `ShopRequest` ? **non** : paramètre de `ShopApi.order/redeem` ; w5-01 l'a prévu sous `termsVersion` ; sinon le demander). (2) `ReceiptStore` (cœur) : `receipts.jsonl` (code, date, article, montant, texte, signature), `add`, `all`, `find(code)`, borné à 200 ; utilisé par `ShopStore` (TV, w5-16) et `ShopRuntime` (téléphone, w5-12). (3) Vues : `ShopTermsView` (TV : texte défilant au D-pad, « J'accepte » / « Plus tard »), `ShopTermsScreen` (téléphone, Compose).

## Pourquoi (preuves)
- `docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md` (« faire accepter, texte versionné, date conservée ») et `R/ActivationActivity.kt` (bloc `TunnelTerms` + case à cocher : **même mécanique**, texte différent) ; `C/owner/SafeFile.kt` ; w5-01 `ShopOrder`, `Receipt` v2.

## Fichiers possédés
Nouveaux `C/shop/ShopTerms.kt`, `C/shop/ReceiptStore.kt`, `R/shop/ShopTermsView.kt`, `S/shop/ShopTermsScreen.kt`, `CT/shop/{ShopTermsTest,ReceiptStoreTest}.kt`. **Hors zone** : `R/shop/ShopActivity.kt` (w5-15 : appelle `ShopTermsView.ensureAccepted(activity) { … }`), `S/shop/ShopPayScreen.kt` (w5-11 : appelle `ShopTermsScreen`), `docs/legal/**` (w5-21 : le texte final est **copié** ici par le coordinateur à la fusion : ce cahier ne rédige pas de CGV).

## Étapes
1. `ShopTerms.CURRENT = Terms(version = "0", date = "2026-10-02", text = "PROJET DE CONDITIONS — à valider par un juriste. … (10 lignes : objet, prix en XAF, espèces et bons, durée fixe des locations, jetons = commodités sans valeur, mineurs, réclamation par code de reçu)")` ; `accepted(file)`, `accept(file, nowMs)` (ligne `version|at`) ; une nouvelle version ⇒ nouvelle acceptation.
2. `ReceiptStore(file)` : JSONL, `add` idempotent par code, `all()` du plus récent au plus ancien, `find`, 200 max (les plus anciens partent), `SafeFile`.
3. `ShopTermsView` (TV) : texte en 30 px, défilement ↑↓, boutons ; `ensureAccepted(activity, onAccepted)` ; `ShopTermsScreen` (téléphone) : même contenu, bouton « J'accepte », lien « Lire plus tard ».
4. Tests : acceptation, nouvelle version, reçu dédoublonné, borne 200.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.shop.ShopTermsTest' --tests 'castbridge.core.shop.ReceiptStoreTest'   # vert
cd android && gradle --offline :receiver:compileDebugKotlin && gradle --offline :sender:compileDebugKotlin   # compile (SDK)
grep -n 'PROJET DE CONDITIONS' android/core/src/main/kotlin/castbridge/core/shop/ShopTerms.kt   # 1 (version 0 marquée)
```

## Cas limites
- Fichier d'acceptation illisible : considéré non accepté (on redemande ; rien de grave).
- Reçu reçu deux fois (relais téléphone + TV en ligne) : une seule entrée.

## À ne pas faire
Pas de commit sur les branches partagées ; ne pas rédiger de texte juridique définitif ; aucun montant ; pas d'`import android` dans `C/shop/**` ; français.

## Rapport
`STATUT`, API (pour w5-11, w5-15, w5-12, w5-16), emplacement du texte à remplacer par w5-21.
