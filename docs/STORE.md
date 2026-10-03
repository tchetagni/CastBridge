# La Boutique : vitrine de contenus en location sur le téléphone CastBridge et la TV CastBridge-TV

> **Statut : conception + cœur testé (JVM).** Exécution teléphone + TV : après la sortie du gel (W15 R3/R5) ou sur exception du propriétaire. Aucun prix, aucun paiement, aucune clé nouvelle. Code : `android/core/src/main/kotlin/castbridge/core/store/` (cœur pur, JVM) et TV `receiver/.../StoreActivity.kt`, téléphone `sender/.../StoreScreen.kt` (après gel). Rapports : `docs/agent-reports/w17-01…04-store*.md`. Conception détaillée : `docs/coordination/DESIGN-W17-STORE-TELEPHONE-ET-TV-2026-10-03.md`.

## 0. En bref

Une seule **Boutique**, deux rendus : le téléphone CastBridge pilote, la TV CastBridge-TV choisit à la télécommande. Aucun format signé nouveau ; deux catalogues signés existants (`castbridge-lot-catalog-v1` et `castbridge-bundle-catalog-v1`, plafonds 256 Ko / 64 Ko) descendent à la TV, vérifiés. États d'article : sur la TV / envoyé / à louer / loué (reste) / gratuit / échantillon / terminé / indisponible, calculés par une **fonction pure** (`StoreView.item(facts)`) à partir de faits que les deux appareils ont : catalogues, manifeste TV, carnet des locations, activation, essai, profil enfant. Aucun écran ne décide. Depuis la TV, « **Louer** » crée une **demande** (`castbridge-rent-request-v1`) que le téléphone relève, un adulte **confirme**, et l'émetteur exécute. Tous les droits existants (achat, abonnement, location, essai, « tout ouvert ») s'appliquent. Drapeau `store.enabled` (ordre signé `flag.set`, par défaut faux en release).

## 1. Sources de données

| Catalogue | Format | Signature | Taille | Où | TV |
|---|---|---|---|---|---|
| Lots d'Apprendre/Quiz | `castbridge-lot-catalog-v1` | Ed25519 (`UpdateKeys.PUBLIC_KEYS`) | ≤ 256 Ko | `files/store/lots-catalog.json` | Vérifiée à la réception (`generatedAt` anti-retour) |
| Bouquets (articles) | `castbridge-bundle-catalog-v1` | Ed25519 (idem) | ≤ 64 Ko | `files/store/bundles-catalog.json` | Idem |

Le téléphone pousse à chaque contact (hook dans `LotsRuntime.deliver`) si plus récent (`generatedAt`). Tous deux existent aujourd'hui (w17-01…04 intègrent le cœur). La vitrine affiche « Catalogue du JJ/MM » (date locale de `generatedAt`) ; > 30 j : « Catalogue ancien : rapprochez le téléphone » (jamais un blocage).

## 2. États d'un article (§ 2.3 de la conception)

| État | Faits | Phrase |
|---|---|---|
| `GRATUIT` | Tous les lots du bouquet sont `FREE` | « Gratuit · Télécharger / Envoyer à la TV » |
| `ECHANTILLON` | La TV n'a que les `-trial` du bouquet | « Sur la TV : échantillon » |
| `PAS_SUR_TV` | Aucun lot du bouquet dans le manifeste TV | « Pas sur la TV · Télécharger / À envoyer » |
| `ENVOYE` | `LotStage.SENT/SENDING/WAITING_TV` (téléphone) | « Envoi en cours 42 % » / « En attente de la TV » |
| `SUR_TV` | Lots complets + droit ( `Access.granted` ⊇ bouquet, ou location utilisable) | « Sur la TV » |
| `LOUE` | Contrat `usable` couvrant le bouquet | « Loué : il vous reste … » + `Prolonger` |
| `TERMINE` | Contrat `EXPIRED`/`DONE` < 30 j | « Location terminée le … · Relouer ? » |
| `A_LOUER` | Bouquet réservé, aucun droit | `Louer gratuitement` (téléphone) / « Louer » (TV) |
| `BLOQUE` | Voir §2.5 (TV éteinte, enfant, essai, quota) | Phrase d'erreur, bouton grisé |

Rayons : **Apprendre** (par classe : Primaire, Secondaire, Supérieur), **Langues** (« Gratuit », jamais de sélecteur), **Quiz**, **Ma TV** (locations + demandes en attente). Phrases françaises de `StoreTexts.kt` dans le cœur (w17-02).

## 3. Blocages et phrases (§ 2.5 de la conception)

| Raison | Téléphone | TV |
|---|---|---|
| TV hors de portée | « La TV n'est pas à portée : la demande partira dès qu'elle sera allumée à côté du téléphone. » | — |
| Aucune TV enregistrée | « Ajoutez d'abord votre TV (Accueil › Ajouter ma TV). » | — |
| TV d'essai | « Version d'essai : les locations demandent une clé de production. Voyez votre point focal. » | « Version complète nécessaire · Passer en production » |
| Profil enfant actif | « Un profil enfant est actif sur la TV : demandez à un parent. » | « Demandez à un parent » |
| Quota : 3 locations en cours | « 3 locations en cours : attendez la fin de l'une d'elles. » | « 3 locations en cours » |
| Même bouquet loué (autre unité) | « CM2 est déjà loué (7 jours) : prolongez-le, ou attendez la fin pour changer d'unité. » | Idem |
| Lot libre | « Gratuit : rien à louer. » | « Gratuit » |
| Famille inconnue | « Cet article n'est pas louable pour le moment. » | Idem |
| Pilote terminé, sans prix | « Le test gratuit est terminé ; tarifs bientôt disponibles. » | Idem |
| Place TV | « Il manque X Mo sur la TV. » | « Place insuffisante sur la TV (X Mo) » |
| Catalogue absent | « Connectez le téléphone à Internet puis actualisez la Boutique. » | « Boutique vide : rapprochez le téléphone » |

## 4. Routes TV — `/api/store*` (§ 4.3 de la conception)

| Route | Rôle | Essai |
|---|---|---|
| `GET /api/store` | `{enabled, catalogAt:{lots, bundles}, view: StoreView.json, kidActive, trial, pending: n}` | Ouvert (lecture) |
| `POST /api/store/catalog` | Corps JSON `{"lots": <json signé>, "bundles": <json signé>}` (≤ 512 Ko total) ; vérifie, anti-retour, écrit `files/store/*.json` | Ouvert |
| `GET /api/store/requests` | Demandes `PENDING` (+ 50 dernières acquittées) | Fermé |
| `POST /api/store/requests/ack?nonce=&state=ACCEPTED\|REFUSED` | Acquittement idempotent | Fermé |

Classification dans `tools/routes/routes.txt` (w17-04). `TrialPolicy` : `/api/store` et `/api/store/catalog` ouvertes en essai (la TV voit la vitrine), `/api/store/requests` et `/api/store/requests/ack` fermées (la TV ne peut pas demander).

## 5. La demande de location : `castbridge-rent-request-v1` (§ 4.1 de la conception)

Format texte, 8 champs triés par clé, parseur strict (w17-03) :
```
castbridge-rent-request-v1
at=<ms TV horloge>
bundle=<id du bouquet>
choice=defaut|<N>j|<H>h
kind=new|extend
nonce=<8 hex aléa>
origin=tv|phone
period=<ms contrat ou 0 pour new>
tv=<16 hex installId>
```

**Ce qu'une demande n'est pas** : un droit. Elle ne donne rien. Seul l'émetteur (outil bureau w16-05 ou serveur w16-08) décide, après confirmation d'un adulte sur le téléphone. La TV ne peut pas signer. États : `PENDING` → `ACCEPTED|REFUSED|EXPIRED` → `FULFILLED`. Refus du cœur (w17-03) : `TRIAL_TV`, `KID_PROFILE`, `FREE_BUNDLE`, `UNKNOWN_FAMILY`, `OVER_LIMIT(3)`, `SAME_BUNDLE_OTHER_UNIT`, `PILOT_ENDED`, `DUPLICATE`, `QUEUE_FULL(20)`.

**Code court** (secours, téléphone loin) : `<ALIAS>-<CHOIX>-<4 Crockford>` (ex. `CM2-12H-7K3Q`), mémo jamais preuve : l'adulte doit confirmer sur le téléphone.

## 6. Essai, profil enfant, modes

| Mode | Tuile | Vitrine | « Louer » |
|---|---|---|---|
| Production | Oui | Complète | Demande (§ 5) |
| **Essai** | Oui (pas dans `CLOSED_TILES`) | Chaque article « Version complète nécessaire » | **Refusé cœur** (`TRIAL_TV`) |
| **Profil enfant** | Oui (« Boutique » dans `KID_HOME`) | Complète | « Demandez à un parent » (refus cœur) |
| `LOCKED` | Aucune | — | — |
| Super illimité | Oui | Tout « Droit illimité » | Inutile |

Langues : article gratuit à rayonner, jamais louable (`RentalPolicy.refusal`).

## 7. Drapeau `store.enabled`

Ordre signé `flag.set store.enabled` (`PolicyActions.FLAGS`, liste close). Défaut : **faux en release**, vrai en debug (sans rebuild dès arrivée de l'ordre). Les écrans restent invisibles si le drapeau est faux (action « Locations » inchangée, pas de tuile TV). Sera clé W12 quand W12 existe ; en attendant : `StoreFlag.kt` lit le drapeau depuis `Settings`.

## 8. Tests

Classes : `StoreCatalogTest` (w17-01), `StoreViewTest`/`StoreFlagTest` (w17-02), `RentRequestTest`/`RentRequestsTest` (w17-03), `StoreApiTest` (w17-04).

Parcours J (w17-05, `StoreJourneyTest`) : J-S1 catalogues relayés une fois (anti-retour), J-S2 article « sur la TV » après livraison d'un lot, J-S3 demande TV → téléphone → ack ACCEPTED → contrat → FULFILLED, J-S4 TV d'essai (vitrine visible, demande refusée), J-S5 profil enfant (refus, aucune ligne), J-S6 téléphone absent 8 j (demande EXPIRED, location terminée).

Vecteurs : `tools/activation/store-vectors.json` (w17-03, demande canonique, code court, refus, nonce/ack, rien ne donne droit).

## 9. Limites honnêtes

- **La demande ne prouve rien** : sans l'adulte au téléphone, rien n'est livré.
- **LAN avec PIN = nuisance bornée** : une demande créée ainsi n'exécute que si l'émetteur l'accepte ; bornes (20 en attente, 1 par bouquet+choix, 50 acquittées 30 j).
- **TV jamais rejointe** = vitrine ancienne : > 30 j les catalogues ne se rafraîchissent plus, articles restent visibles, locations en cours jouables.
- **Identifiants réels des bouquets** (valeurs de `Bundle.type` : classe/langues/quiz) : `content/TRIAL-MANIFEST.json` absent du dépôt, non vérifiables (w17-01 lit les valeurs).
