# Vague iOS — CastBridge sur iPhone (compagnon Wi-Fi de CastBridge-TV)

<!-- routage architecte 2026-10-04 -->

**Source** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md`. Branche de référence `integration/agents` (HEAD `3484fbb1`). Exécution sur ordre du coordinateur seulement.

**Demande du propriétaire (2026-10-04, verbatim)** : « peux-tu me produire une version iOS de l'application phone ».

**Règles de la vague** : **I1** Swift natif dans le répertoire neuf `ios/` (paquet `CastBridgeKit` + app SwiftUI + extensions), aucune dépendance externe ; **I2** parité filaire prouvée par les vecteurs `tools/ios-vectors/*.json` écrits par le cœur Kotlin et par des tests contre la **vraie** `ReceiverServer` (TV factice sur 127.0.0.1) ; **I3** aucun build signé, aucune installation sur appareil, aucun téléversement par un agent (`CODE_SIGNING_ALLOWED=NO`) : le propriétaire signe dans Xcode ; aucun identifiant Apple demandé ni écrit ; **I4** aucun appel Internet dans l'app v1 ; jamais le service de jeu ni le portefeuille (W20 I-2, W22 J4) ; **I5** aucune activation propre de l'iPhone, aucun prix, aucun achat, aucune clé (D-IOS-2, règle App Store 3.1.1) ; **I6** côté TV : additif, versionné, rétrocompatible, builds **verrouillés** seulement (`-PrequireActivation=true`), APK copiés par le coordinateur dans le `Download` de la clé USB ; **I7** tout `gradle`, `swift test`, `xcodebuild` passe par `tools/agents/gradle-lock.sh` (un seul build lourd à la fois sur le Mac ; jusqu'à 3 agents écrivent en parallèle) ; **I8** noms : « CastBridge » (téléphone, iPhone compris) et « CastBridge-TV », jamais sender/receiver dans un texte visible ; **I9** audit Opus obligatoire sur la confiance, le PIN, les clés, les identifiants Wi-Fi et le chemin de perte de données.

## Niveau v1 — première TestFlight interne

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| wios-tv-01 | `sonnet-wios-tv-01-vecteurs-partages-tv-factice.md` | vecteurs Kotlin → Swift (transfert, API TV, QR, textes) ; `FakeTvMain` + `tools/ios/fake-tv.sh` | cœur (tests) : permis | M | sonnet | non | 400 / 20 | PRÊT (ordre 1) | — |
| wios-01 | `sonnet-wios-01-squelette-ios-paquet-projet.md` | `ios/` : paquet SwiftPM, projet 3 cibles, chargeur de vecteurs, porte minimale | hors gel | S | sonnet | non | 200 / 15 | PRÊT (ordre 1) | — |
| wios-02 | `sonnet-wios-02-client-api-tv-swift.md` | client Swift de l'API HTTP de la TV, identifiants, raisons | hors gel | M | sonnet | échantillon | 400 / 25 | ATTEND (ordre 2) | 01, tv-01 |
| wios-03 | `sonnet-wios-03-transfert-blocs-reprise-swift.md` | moteur de copie : blocs SHA-256, reprise, envoi simple, réessais bornés, 4 états finaux | hors gel | L | sonnet | **oui** | 550 / 30 | ATTEND (ordre 2) | 01, tv-01 |
| wios-tv-02 | `sonnet-wios-tv-02-appairage-http-pin-cle-appareil.md` | TV : `pin-http-v1` (PIN une fois, clé Ed25519, jeton, défi), `hello` additif, 8 au plus | cœur pur + 1 ligne : permis | M | sonnet | **oui** | 450 / 22 | PRÊT (ordre 2) | — (w19-02 souhaitable) |
| wios-04 | `sonnet-wios-04-decouverte-qr-groupe-tv.md` | Bonjour, réseau local, QR, `NEHotspotConfiguration` (groupe de la TV) | hors gel | M | sonnet | échantillon | 400 / 22 | ATTEND (ordre 3) | 02, tv-01 |
| wios-05 | `sonnet-wios-05-appairage-pin-trousseau-swift.md` | appairage par PIN, clé dans le trousseau, fiche par TV, renouvellement | hors gel | M | sonnet | **oui** | 400 / 22 | ATTEND (ordre 3) | 02, tv-02 |
| wios-06 | `sonnet-wios-06-app-ecrans-plist-manifeste-demo.md` | écrans de liaison, Info.plist, manifeste, français, **mode démonstration** | hors gel | L | sonnet | non | 600 / 35 | ATTEND (ordre 4) | 04, 05 |
| wios-07 | `sonnet-wios-07-envoi-file-live-activity-fond.md` | envoi, file, HEIC → JPEG, Live Activity, notifications, fond | hors gel | L | sonnet | échantillon | 600 / 35 | ATTEND (ordre 5) | 03, 06 |
| wios-08 | `sonnet-wios-08-extension-partage.md` | extension de partage « CastBridge-TV » | hors gel | M | sonnet | non | 400 / 22 | ATTEND (ordre 5) | 06 (+ API de file de 07) |
| wios-09 | `sonnet-wios-09-telecommande-bibliotheque-quiz.md` | télécommande, bibliothèque de la TV, Quiz par `/quiz` | hors gel | M | sonnet | non | 400 / 22 | ATTEND (ordre 5) | 02, 06 |
| wios-tv-03 | `sonnet-wios-tv-03-tv-qr-iphone-telephones-mdns.md` | TV : « Ajouter un iPhone » (QR), iPhones dans « Téléphones », TXT mDNS, groupe gardé | `R/` : après gel ou exception | M | sonnet | échantillon | 450 / 22 | ATTEND (ordre 5) | tv-02, **w18-07** |
| wios-10 | `sonnet-wios-10-plan-humain-testflight-app-review.md` | guide Apple du propriétaire, P-IOS-1…12, notes App Review, confidentialité | docs | S | **haiku** | non | 150 / 15 | ATTEND (ordre 6) | 06-09 |
| wios-11 | `sonnet-wios-11-porte-ios-secrets-manifeste.md` | `tools/ios/check.sh` : tests, simulateur, secrets, aucune URL Internet, manifeste | outil | S | **haiku** | non | 150 / 12 | ATTEND (ordre 6) | 06-09 |

## Niveau v2 — conditionnel

| id | Cahier | Objet | Effort | Modèle | Audit Opus | Jauge | Statut |
|---|---|---|---|---|---|---|---|
| wios-tv-04 | `sonnet-wios-tv-04-service-ble-remise-identifiants.md` | sonde BLE de la TV puis service GATT (X25519 + PIN, remise chiffrée, allumage du groupe) | L (sonde : S) | sonnet | **oui** | 550 / 30 | CONDITIONNEL (sonde d'abord) |
| wios-tv-05 | `sonnet-wios-tv-05-activation-http-tv-verrouillee.md` | activation `cbx1` par HTTP en état verrouillé (mode porteur iPhone) | S | sonnet | **oui** | 250 / 12 | BLOQUÉ : décision du propriétaire |

Les cahiers iPhone de v2/v3 (« Lire en direct », téléchargement TV → iPhone, pistes, BLE côté iPhone, export H.264, lots libres, parental, coursier) seront écrits après les séances P-IOS et la décision de poursuivre.

## Ordre et parallélisme

```
 ordre 1 :  wios-tv-01  ∥  wios-01
 ordre 2 :  wios-02  ∥  wios-03  ∥  wios-tv-02
 ordre 3 :  wios-04  ∥  wios-05
 ordre 4 :  wios-06
 ordre 5 :  wios-07  ∥  wios-08  ∥  wios-09  ∥  wios-tv-03 (si w18-07 fusionné et gel levé/exception)
 ordre 6 :  wios-10  ∥  wios-11
 ordre 7 :  PROPRIÉTAIRE : signature automatique (Xcode), archive, TestFlight interne, P-IOS-1…12 (3 séances d'≈ 1 h)
 prérequis TV de la situation « sans point d'accès » : test terrain W18 § 7, w18-01, w18-07
```

- **Charge du Mac** (12 cœurs, 32 Go ; le commentaire « 8 Go » de `gradle-lock.sh` est périmé) : au plus **3 agents** en même temps ; **un seul** build lourd (`gradle`, `xcodebuild test`, `swift test` du paquet complet) à la fois, par le verrou ; un agent qui attend le verrou continue d'écrire.
- **Fichiers partagés par zones** (fusionner dans cet ordre) : `ios/CastBridge/Info.plist` (01 → 04 → 06 → 07 → 08), `ios/CastBridge/App/Tabs.swift` (06 → 07 → 09), `ios/CastBridge/CastBridge.entitlements` (01 → 04), `ios/CastBridge/PrivacyInfo.xcprivacy` (06 → 11). Tout le reste est possédé par un seul cahier.
- **Chemin critique** : tv-01/01 → 02 → 04/05 → 06 → 07 → 11 → propriétaire ≈ **8-10 jours ouvrés**.

## Coût

Prix des index précédents, **non vérifiés** (sonnet 2/10 $ par million de jetons, opus 4/20 $, haiku 0,8/4 $).

| Poste | Calcul | Montant |
|---|---|---|
| sonnet v1 | 1 S (0,6) + 8 M (≈ 1,0-1,1 chacun) + 3 L (1,4-1,6) | ≈ 13,5 $ |
| haiku v1 | 2 S | ≈ 0,4 $ |
| audits Opus v1 | 3 obligatoires (03, 05, tv-02) × 0,8 + 4 échantillons (02, 04, 07, tv-03) × 0,4 | ≈ 4,0 $ |
| **total v1 avant reprises** | | **≈ 17,9 $** |
| reprises | 15 % (habituel) à 45 % (Swift/Xcode nouveaux dans ce dépôt) | + 2,7 à 8 $ |
| **v1** | **≈ 24 agent·jours** | **≈ 21-26 $** |
| v2 conditionnel (TV) | tv-04 (1,4 + 0,8) + tv-05 (0,6 + 0,8) | ≈ 3,6 $ |

Hors agents : programme Apple Developer déjà payé (renouvellement annuel) ; temps du propriétaire ≈ 2 h de mise en place Apple + 3 séances de test d'≈ 1 h ; un iPhone (celui du propriétaire : existence et version **non vérifiées**).

## Décisions à trancher par le propriétaire avant l'ordre 4

- **D-IOS-2** : iPhone sans activation propre, débloqué par la TV activée (recommandé : oui).
- **D-IOS-6** : iOS 16.0 minimum (recommandé) ou 17.
- **D-IOS-7** : `com.sti-cm.castbridge`, nom « CastBridge » (recommandé).
- **D-IOS-8** : TestFlight interne ⇒ externe ⇒ App Store non listée (recommandé).
- Pour v2 : **wios-tv-05** (élargir la surface verrouillée de la TV au réseau local) : oui / non.
