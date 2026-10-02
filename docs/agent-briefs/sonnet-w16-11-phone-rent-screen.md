# w16-11 — CastBridge (téléphone) : écran « Louer » (sélecteur à trois groupes : sans durée précise, jours, heures d'utilisation ; date de fin réelle), relevé d'usage automatique et partage, file hors ligne, client du module pilote

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : **audit Opus obligatoire** (livraison de contenu chiffré, file persistée) · statut : **ATTEND la sortie du gel** et w15-16 fusionné (`C/lots/DeliveryQueue.kt`, `S/LotsRuntime.kt`)
> **Groupe : W16d-2** (vague W16d, téléphone) · prérequis : w16-03, 04, 07 fusionnés ; w16-08 pour le mode serveur (sinon `FakePilotApi`) ; w15-16 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*PilotClient*' --tests '*RentalDelivery*' && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin`
> **Jauge : ≈ 600 k jetons entrée / 30 k sortie** (effort L, ≈ 3 j) · audit Opus : **oui**

**Vague 16d · Effort L · Modèle : sonnet · Statut ATTEND.** Conception : DESIGN-W16 § 1.1, § 1.3, § 3.3, § 5.2. Branche `claude/sonnet-w16-11`. Rapport : `docs/agent-reports/sonnet-w16-11.md`. Dire « CastBridge » (téléphone) / « CastBridge-TV ».

## Objectif
Depuis « Données hors ligne » (ou la Boutique W5 si elle existe), l'utilisateur choisit un bouquet louable, puis **un seul** sélecteur à trois groupes : « Sans durée précise : 30 jours (jusqu'au 11/11) » (présélectionné), « Jours : 1 · 3 · 7 · 14 », « Heures d'utilisation : 1 · 3 · 6 · 12 · 24 · 48 · 96 » avec l'aide d'une ligne par groupe et la **date de fin réelle** ; la commande part au serveur (prix 0) ou reste en file hors ligne ; l'activation et les lots scellés sont livrés à la TV par le flux existant ; à chaque contact avec la TV, le téléphone **relève** l'usage (`GET /api/rental/usage`) et le remet au serveur (ou le partage en fichier, tranche 1). « Locations sur la TV » affiche l'unité et le reste.

## Pourquoi (preuves)
- `S/RentalDeliveryActivity.kt:38-94` (écran existant : fichiers, contrat, `deliver`) ; `S/LotsScreen.kt:22-28` (`LotsEntry`, point d'entrée) ; `S/LotsRuntime.kt` (tâche périodique, zone `hasWork` w15-16).
- `C/lots/RentalDelivery.kt:96-119` (livraison), `TvRentalView` (w16-03), `RentalUsageReport`/`RentalReportStore` (w16-03), `PilotRules.Choice.label()` (w16-04), `C/lots/DeliveryQueue.kt`.
- Routes serveur § 3.3 (w16-08) ; `GET /api/v1/pilot/config` pour les paliers avant W12.
- Catalogue : `ServerBundleCatalog` (`C/lots/ServerBundleCatalog.kt`) ; lots libres ⇒ « Gratuit », pas de sélecteur (`RentalPolicy`).

## Fichiers possédés
Nouveaux `C/lots/PilotClient.kt` (requêtes/réponses pures, `FakePilotApi` de test), `CT/lots/PilotClientTest.kt`, `S/RentalPickerScreen.kt`, `S/RentalRuntime.kt` (file, relevé, remise) ; `S/RentalDeliveryActivity.kt` (zone affichage : unité, reste ; bouton « Partager le relevé ») ; `S/LotsScreen.kt` (**une** entrée « Louer »). **Hors zone** : `S/LotsRuntime.kt` (w15-16 ; `RentalRuntime` s'y branche par une ligne proposée au rapport), `S/shop/**` (W5), `C/lots/RentalDelivery.kt`.

## Étapes
1. **Rouge** : `PilotClientTest` : devis/commande/relevé contre `FakePilotApi` (idempotence, hors ligne ⇒ file, rejeu) ; vue du sélecteur pure (`PickerModel` : groupes, présélection, libellés exacts, date de fin par choix, désactivation après `pilot.end`, lots libres sans sélecteur).
2. `RentalPickerScreen` (Compose) : trois groupes, aide : « Les jours passent même quand la TV est éteinte. » / « Les heures comptent seulement pendant que le contenu est ouvert sur la TV. » ; confirmation : « Louer CM2 · 12 heures d'utilisation · à utiliser avant le 15/11 · gratuit pendant le test ».
3. `RentalRuntime` : commande ⇒ activation + lots ⇒ `DeliveryQueue` ⇒ `RentalDelivery.deliver` (activation d'abord par `POST /api/activation/install`) ; relevé à chaque liaison ; remise au serveur ou export (fichier `releve-<install>-<date>.txt`, partage système).
4. « Locations sur la TV » : lignes par unité (`TvRentalView.lines()`), « Partager le relevé ».
5. Vert : porte ; `compileDebugKotlin`.

## Critères d'acceptation
Porte verte ; 8 tests rouges puis verts ; aucune conversion heures ↔ jours à l'écran (relecture des chaînes) ; profil enfant (parental actif sur le téléphone, `S/ParentalScreen.kt` : lecture) ⇒ pas de bouton « Louer » ; lot libre ⇒ « Gratuit » ; hors ligne ⇒ « commande en attente, elle partira au prochain réseau Wi-Fi ».

## À ne pas faire
Pas de montant ; pas de mobile money ; ne pas réécrire `RentalDelivery` ; ne pas modifier `LotsRuntime` hors la ligne proposée.

## Rapport
`STATUT`, sorties rouge/vert, captures textuelles de l'écran, ligne à ajouter dans `LotsRuntime` (hors zone), question : l'entrée « Louer » doit-elle vivre dans la Boutique W5 quand elle sera là (recommandation : oui, même `PickerModel`).
