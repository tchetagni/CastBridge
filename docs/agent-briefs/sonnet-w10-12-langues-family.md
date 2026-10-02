# w10-12 — Famille Langues dans la boutique : lots libres toujours autorisés, pack de confort (achat définitif), textes « contenu libre », exécution serveur de l'achat

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (serveur : w5-06/w5-07 requis ; sinon cœur + textes seulement et `STATUT: PARTIEL`)
> **Groupe : W10d-2** (vague W10d) · prérequis : w10-02 ; w5-06/07 pour le serveur ; après w9-14 pour R/LanguesHub.kt · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.lots.EditionTest' --tests 'castbridge.core.shop.LanguesOfferTest'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui (droit d'achat émis par le serveur)

**Vague 10d · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (serveur : w5-06/w5-07 requis ; sinon cœur + textes seulement et `STATUT: PARTIEL`).** Conception : DESIGN-W10 § 2.2 (Langues), § 4.5, § 9 (D-W10-9), § 13. Branche `claude/sonnet-w10-12`. Rapport : `docs/agent-reports/sonnet-w10-12.md`. Dépend de w10-02 (`WorkItem.LanguesPack`).

## Objectif
Les packs **Langues** sont des produits de premier rang : le contenu **libre** (CC BY-SA 4.0) est gratuit, en clair, toujours autorisé sur une TV en production (et dans l'archive libre) ; le **pack de confort** `achat-pack-langues-<code>` est un **achat définitif** (`Right.Purchase`) qui ouvre les lots **réservés** jumeaux (`langmedia`, voix humaines) **et** le service « livraison + mises à jour 12 mois » ; l'écran dit clairement ce qui est libre ; **aucune mesure technique** sur le libre.

## Pourquoi (preuves)
- `C/lots/RentalPolicy.kt:4-7, 23-27` : libre jamais loué ; `docs/FREE-CONTENT.md` § 1, § 4.2, § 4.7 (contradiction « original = libre ou réservé » à trancher par le propriétaire ; mesure technique vs CC BY-SA § 2(a)(5)(C) : juriste).
- `C/lots/EditionPolicy.kt:41-44` : un lot `FULL` n'est autorisé que par un bouquet possédé ⇒ un lot **libre** `FULL` serait bloqué sans droit : à corriger (règle « famille FREE ⇒ toujours autorisé »).
- `docs/ACTIVATION-FORMAT.md` § 3.3 (`purchase|…`) ; `B/licenses/ActivationService.java:254` (`rightsOf` : `purchase`), w5-06 `issueWithRights` ; DESIGN-W5 § 3.3 (`shop_order.kind`).
- `docs/LANGUES.md` § 13 (deux familles), `tools/langues/gen_a0_pilot.py` (packs A0 pilotes).

## Fichiers possédés
`C/lots/EditionPolicy.kt` (additif : `allowedFull(access, bundles, families)` surcharge ⇒ `+ tous les lots FREE connus`), `C/lots/RentalPolicy.kt` (commentaire), `CT/lots/EditionTest.kt`, nouveaux `C/shop/LanguesOffer.kt`, `CT/shop/LanguesOfferTest.kt`, `S/langues/LanguesShopTexts.kt` (nouveau), `R/LanguesHub.kt` (lien « Télécharger les contenus libres » : additif) ; **si w5-06/07 fusionnés** : nouveaux `B/shop/order/PurchaseFulfilment.java`, `BT/shop/order/PurchaseFulfilmentTest.java`. **Hors zone** : `content/langues/**`, `tools/free-content/**`, `S/shop/**` (w10-11 affiche), `B/shop/rental/**`.

## Étapes
1. `EditionPolicy` : surcharge additive avec `families: LotFamilies` : un lot de famille `FREE` est **toujours** dans `allowedFull` (et `offered`) ; l'ancienne signature reste (comportement inchangé) ; `LotSync`/`planForTv` des appels existants **non modifiés ici** (w10-11/w5-12 passeront `families` quand ils y seront : noter au rapport).
2. `LanguesOffer` (pur) : pour une langue `<code>` : `free: List<LotId>` (lots libres, « Gratuit (CC BY-SA 4.0) »), `reserved: List<LotId>` (jumeaux `langmedia` réservés s'ils existent), `service: Boolean` (pack sans lot réservé = service seul : D-W10-9), `item = achat-pack-langues-<code>`, textes : « Le contenu de ce pack est libre (CC BY-SA 4.0) : vous pouvez le télécharger gratuitement. Le pack de confort ajoute [les voix enregistrées et] les mises à jour pendant 12 mois, livrées sur votre TV. » ; `state` : `OWNED` (achat déjà dans `Access.purchased`) / `PURCHASABLE` / `NO_PRICE`.
3. Serveur (si W5) : `PurchaseFulfilment implements` le mécanisme d'exécution de w5-07 pour `kind = PURCHASE` : vérifie que le bouquet `pack-langues-<code>` est de type `pack`, émet via `ActivationService.issueWithRights` une activation portant **une** ligne `purchase|achat-pack-langues-<code>|pack-langues-<code>|<date>` (durable : W4-B § 3 `lastingRights`), fenêtre 48 h, cible de la demande ; les lots **réservés** du pack sont servis par les routes de lots **avec droit** (preuve W6 ; jamais scellés par contrat : ce n'est pas une location) ; idempotence par `(licence, poste, article)`.
4. Textes TV (`LanguesHub`) : dans « Langues », ligne « Contenus libres : téléchargeables gratuitement (archive) » (lien vers l'écran existant de l'archive, `FreeContentExport`).
5. Tests : `EditionTest` (lot FREE sans droit ⇒ autorisé ; lot RESERVED sans droit ⇒ non), `LanguesOfferTest` (service seul, pack réservé, déjà possédé), serveur (achat ⇒ ligne `purchase` exacte, idempotent, refus d'un bouquet non `pack`).

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.lots.EditionTest' --tests 'castbridge.core.shop.LanguesOfferTest'   # vert
cd backend && ./mvnw -q -o test -Dtest='PurchaseFulfilmentTest' 2>/dev/null || echo "serveur : W5 absent, PARTIEL"
grep -rn 'seal\|RentalKeys' android/core/src/main/kotlin/castbridge/core/shop/LanguesOffer.kt   # 0 : jamais de scellement du libre
```

## Cas limites
- Langue avec contenu libre mais sans pack réservé ni prix : « Gratuit » seulement, pas de bouton d'achat.
- Achat d'un pack déjà possédé : refusé avant paiement (« déjà acquis »).
- Lot libre **non étiqueté** (FREE-CONTENT § 4.3) : famille inconnue ⇒ **pas** autorisé par la règle FREE (fail closed) : le propriétaire doit étiqueter.

## À ne pas faire
Pas de commit sur les branches partagées ; ne jamais sceller ni louer un lot libre ; ne pas modifier `content/langues` ; aucun montant ; pas d'affirmation juridique (textes « à valider par un juriste » dans le rapport, pas à l'écran).

## Rapport
`STATUT` (ou PARTIEL), appels existants à `allowedFull` à faire migrer (liste), ligne `purchase` exacte, questions (D-W10-9, FREE-CONTENT § 4.2).
