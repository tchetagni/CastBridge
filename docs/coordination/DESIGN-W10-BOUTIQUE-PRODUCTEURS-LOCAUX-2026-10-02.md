# Conception W10 : boutique à trois familles (Apprendre, Langues, Œuvres locales) et marché des producteurs de contenus locaux — avec plan d'expérimentation

> **Statut : conception (Fable, architecte, 2026-10-02). Rien n'est implémenté par ce document.** Exécution : cahiers `docs/agent-briefs/sonnet-w10-NN-*.md` (index `SONNET-WAVE10-INDEX.md`), sur ordre explicite du coordinateur. Branche de référence : `integration/agents`. Tous les fichiers et lignes cités ont été relus le 2026-10-02 ; les faits non vérifiables sont marqués **BLOQUÉ**. Aucun montant réel, aucun numéro, aucun secret : les montants XAF de ce document sont des **hypothèses d'expérience**, pas des prix.
> Chemins : `C/` = `android/core/src/main/kotlin/castbridge/core/`, `CT/` = tests cœur, `R/` = `android/receiver/src/main/kotlin/castbridge/receiver/`, `S/` = `android/sender/src/main/kotlin/castbridge/sender/`, `B/` = `backend/src/main/java/castbridge/server/`, `BT/` = tests serveur, `DK/` = `tools/activation-desktop/src/main/kotlin/castbridge/desktop/`.
>
> **Demande du propriétaire (2026-10-02)** : « expérimente la boutique qui inclura les producteurs de contenus locaux (vidéos, audios de sketch par exemple, …) ». **Précision du coordinateur** : la boutique vend d'abord les packs d'**Apprendre** (réservés, en location) et de **Langues** (libres CC BY-SA, achat de confort ou de mise à jour) ; les producteurs locaux sont une **troisième famille** ; même flux que W5 (location scellée par TV) ; l'expérience pilote teste les trois familles ensemble.

## 0. En douze lignes

1. **Trois familles de produits** dans la même boutique (W5, inchangée dans ses fondations) : **Apprendre** (bouquets de classe, réservés, location 30 j), **Langues** (lots libres CC BY-SA **jamais scellés ni loués**, vendus comme **pack de confort** : médias réservés à voix humaine + service de livraison/mises à jour, achat définitif), **Œuvres locales** (sketches audio, courts métrages, musique, contes, cours en langue locale, feuilletons radio : location 7/30 j par titre ou par **chaîne** de producteur, aperçu gratuit).
2. **Une œuvre est un lot** : fonction `oeuvre`, périmètre = identifiant de l'œuvre, lot ZIP (`work.json` + média + couverture), **jumeau d'aperçu `<id>-trial`** (30-60 s, en clair, visible même en essai) : le cadre existant des éditions (`LotEditions`, `-trial`), des catalogues signés et des **locations scellées par TV** (`RentalKeys`, `RentalApi`, `RentalEngine`) s'applique **sans nouveau format cryptographique**.
3. **Mais pas dans les 10 Mo** : les œuvres vivent dans un **second magasin** de la TV (`WorkStore`, même classe `TvLotStore`, autre dossier, autre budget : 200 Mo par défaut sur le volume vidéo) et dans un second stock du téléphone ; une œuvre ≤ 25 Mo au pilote (audio 3-6 min ≈ 2-4 Mo ; vidéo 3 min 360p ≈ 8-12 Mo). Les longs formats (≥ 25 Mo, 720p) attendent le **conteneur par morceaux** `castbridge-work-v1` (§ 5.6), hors pilote.
4. **La plateforme scelle et signe tout ; les producteurs n'ont aucune clé** : catalogue des œuvres signé hors ligne par le propriétaire (`castbridge-works-catalog-v1`, même patron que `SignedBundleCatalog`), lots signés dans le catalogue de lots, scellés par le serveur pour chaque contrat (W5 § 4.3). Un **Studio producteur** (script de bureau `tools/producer-studio/`) transcode, contrôle, empaquette et prépare la **soumission** ; la publication reste au propriétaire (console).
5. **Modération avant publication** (liste de contrôle : droits, musique tierce, haine/diffamation, sensibilité politique, classification d'âge, langue), **classification producteur + plafond plateforme** reprise par le **contrôle parental existant** (`Rating` tous publics/-12/-16/adulte, `ParentalModel.kt:6-9`) ; **aucune œuvre « adulte » au pilote** (décision).
6. **Argent** : espèces et bons seulement (P5). Partage recommandé **60 % producteur / 25 % plateforme / 15 % point focal** (hypothèse à tester), **relevé mensuel signé** par le serveur, **versement en espèces** contre reçu (ou en bons à revendre), **seuil de versement** 5 000 XAF (hypothèse). Litiges et remboursements : règles W5 (geste du propriétaire, TOTP), répercutés en `REVERSAL` sur le relevé.
7. **Retrait** : plus de nouvelle location dès le retrait ; les locations livrées **continuent jusqu'à leur fin** sauf **ordre signé `work.takedown`** (ordres différés existants, portée `POLICY`) appliqué par la TV **à son prochain contact** (limite honnête d'une TV hors ligne).
8. **Pas de filigrane forensique** par TV au pilote (irréaliste sur une TV 32 bits, coûteux côté serveur) : dissuasion = scellement par installation (W4-A), aperçu **visiblement** marqué, registre des locations (qui a loué quoi, quand), contrat producteur/client. Mesure du piratage = hypothèse H6 du pilote.
9. **Vie privée** : un producteur voit ses **ventes** (toujours) et des **lectures agrégées** venant des seules TV ayant consenti aux « statistiques d'usage » (événement `work_play{work, pct}`, jamais de code d'appareil) ; pas d'avis publics (modération, diffamation) ; un « pouce » local optionnel, agrégé.
10. **Expérience (§ 10)** : 3-5 producteurs, ≈ 20 sketches/contes audio + 5 courtes vidéos + 3 bouquets Apprendre + 1 pack Langues, 15-25 foyers, 2 points focaux, **4 semaines de vente** sur 7 semaines ; **pilote papier** exécutable avec les outils d'aujourd'hui (outil de bureau `emettre --location` + `lot-chiffrer` + `rental_test.py` généralisé), **pilote technique minimal** = 7 cahiers (≈ 12 j) indépendants de W5 ; seuils go/no-go chiffrés ; budget ≈ 270 000 XAF (hypothèses).
11. **Juridique** : droit d'auteur (loi camerounaise 2000/011 et Accord de Bangui/OAPI, **à vérifier**), sociétés de gestion collective (musique, audiovisuel : **noms et périmètres à vérifier**), consommation, fiscalité des versements, régulation des contenus, mineurs : **liste de contrôle pour un juriste** (§ 11) ; **ce document n'est pas un avis juridique**.
12. **Effort** : 17 cahiers, 5 sous-vagues, ≈ 31 agent·jours ; W10 s'empile sur W4 (4a), W5 (boutique, locations en ligne) et W6 (preuve de TV) **sans les modifier** ; ce qui change dans les cahiers W5 est listé au § 13. **Vagues conçues en parallèle le même jour** (fichiers non suivis par git au moment de la relecture : W7 synchronisation plug-and-play, W8 transport multivoie, W9 lots multimédias génératifs, W11 navigation allégée) : les recouvrements de fichiers et l'ordre à respecter sont au **§ 13 bis** ; W9 (w9-14 : lots média sur le volume de la bibliothèque) est le **voisin le plus proche** de W10 et W10 s'y aligne.

## 1. État des lieux vérifié (à ne pas refaire)

| Brique | Où (vérifié) | Ce qui existe et compte pour W10 |
|---|---|---|
| Lots, catalogue signé, budgets | `C/lots/LotApi.kt:3-33` (`LotId(feature, scope)`, `LotConsumer`, `LotBudget.TV_MAX_BYTES = 10 Mo`, `PHONE_MAX_BYTES = 100 Mo`) ; `C/lots/LotManifest.kt:29-31` (une **fonction est un seul mot** `[a-z0-9]{1,32}`, le périmètre peut porter des tirets) ; `docs/LOTS.md` § 2-6 | une œuvre = `LotId("oeuvre", "<id>")` est **déjà un nom valide** ; le budget de 10 Mo est un paramètre de `TvLotStore` (`C/lots/TvLotStore.kt:62-69`, `maxBytes`), pas une constante câblée |
| Éditions essai / complet | `C/lots/EditionPolicy.kt:41-56` ; `TRIAL-EDITION.md` § 1 (`-trial` = édition d'essai, signée dans le catalogue, jamais confondue avec le complet) | l'**aperçu** d'une œuvre = lot `oeuvre:<id>-trial` : toujours autorisé, en clair, remplacé par le complet sans doublon |
| Familles libre / réservé | `C/lots/RentalPolicy.kt:4-19, 23-27` (`LotFamily.FREE` jamais loué, famille inconnue refusée) ; `docs/FREE-CONTENT.md` § 1 (archive = étiquette **explicite** seulement) | Langues libres **jamais** scellées ; une œuvre CC BY-SA entre dans `free` + étiquette ⇒ archive |
| Location scellée par TV | `C/lots/RentalKeys.kt:35-54` (`lotKey`, `seal`/`open` AES-GCM, déterministe) ; `C/lots/RentalApi.kt:22-40` (`/api/rental/install` ouvre avec la clé du contrat puis confie au `TvLotStore`) ; `RentalEngine`, `RentalLedger`, `RentalSweeper` (`docs/RENTAL-LOTS.md` § 2-6) ; boîte v2 W4-A ; locations en ligne W5 § 4 | **réutilisé tel quel** ; seul ajout : `RentalApi` doit **router** un lot `oeuvre` vers le second magasin |
| Serveur lots | `B/lots/LotService.java:46-49` (`FEATURES = {learn, quiz}`, `MAX_LOT_BYTES = 10 Mo`, validateur par fonction `LotValidator`) ; `B/lots/BundleCatalogController.java:20-27` (relais d'un fichier signé hors ligne) | ajouter `oeuvre` (et `langues`, `langmedia`), plafond par fonction, `WorkLotValidator`, relais du catalogue des œuvres |
| Catalogue des bouquets | `C/lots/SignedBundleCatalog.kt:19-40` ; `tools/trial-edition/trial_edition.py:556` (`bundles`), `:797` (`sign_catalog`) ; `content/bundles-rental.json` (30 j par défaut) | les bouquets `oeuvre-<id>`, `chaine-<producteur>` s'y ajoutent avec leur `rentalDays` |
| Chaîne de construction | `tools/content-lots/build_lots.py` (lots déterministes, `lot.json`, catalogue signé), `tools/content-lib/lotlib.py`, `tools/publish-content.sh` (règle A : rien n'est produit sur le serveur) | le Studio produit des lots **au même format** ; `build_lots.py` les ramasse depuis `content/oeuvres/dist/` |
| Médias : règles et contrôles | `docs/MEDIA-POLICY.md` § 1 (**jamais d'audio ni de clip dans un lot TV ≤ 3 Mo** : règle faite pour Apprendre) ; `tools/media-pipeline/mp.py receive` (LUFS, silence, codecs, EXIF, secrets) ; `C/transcode/Transcode.kt:4-9` (profils `dlna-480` 1 200 kbit/s, `dlna-720` 3 500 kbit/s : profils de **diffusion**, pas de stockage) | W10 **n'abroge pas** la règle 3 Mo d'Apprendre : les œuvres sont une autre fonction, un autre magasin, un autre budget ; les contrôles de `mp.py` sont réutilisés par le Studio |
| Lecteur TV | `C/tv/ReceiverServer.kt:97-114` (`streamUrl` loopback + jeton de run), `:277-283` (garde : seul le lecteur de la TV lit `/stream/` sans PIN), `:1131` (`/stream/<name>` avec `Range`) ; libVLC ; `docs/STORAGE.md` (volumes, clé USB, débits) | une œuvre déchiffrée **en mémoire** est servie au lecteur par le même mécanisme (`/stream/oeuvre/<id>`) : aucun clair durable sur disque |
| Outils du propriétaire | `tools/rental-test/rental_test.py:53` (`emettre --location`), `:66` (`lot-chiffrer` : **l'outil de bureau scelle déjà un lot**), `:74` (`/api/rental/install`) ; `docs/ACTIVATION-TOOLS.md` § 8 | le **pilote papier** livre des œuvres **aujourd'hui** avec ces commandes (§ 10.4) |
| Boutique W5 | `DESIGN-W5` § 3.1 (articles `loc-<bouquet>\|<jours>`, grille signée), § 4.5 (séquence louer), § 5 (bons, commande en espèces), § 6.6 (`Category.PURCHASES`, enfants), § 9 | W10 ajoute des **articles** et des **catégories** ; aucun nouveau moyen de paiement |
| Contrôle parental | `C/parental/ParentalModel.kt:6-9` (`Rating` all/12/16/18), `:36-46` (`Category`) ; `docs/PARENTAL.md` (classement des vidéos, profils, mode enfant) | la classification d'une œuvre est **portée par le catalogue** (plus « non classée = adulte » comme pour une vidéo du client) |
| Ordres différés | `B/orders/PolicyCatalog.java:15` (`ACTIONS` liste fermée), `C/policy/*` (`OrderTransport`, `PolicyEngine`) | nouvelle action `work.takedown` |
| Télémétrie | `B/telemetry/EventCatalog.java:81` (`playback_end` : `pct`, `ms`), consentement « statistiques » (`docs/TELEMETRY.md` § 2) | nouvel événement `work_play` |
| Preuve de TV (W6) | `DESIGN-W6` § 0.7 (`X-CB-TV-Proof` exigé par le serveur pour les lots complets, locations, jetons) | s'applique aux œuvres sans rien de plus |
| Téléchargements TV, transfert | `docs/DOWNLOADS.md` (la TV télécharge elle-même quand elle a Internet) ; `docs/TRANSFER.md` (multivoie) | voies de livraison **futures** du conteneur long format (§ 5.6) ; hors pilote |
| Lots média W9 (conçu en parallèle, non fusionné) | `docs/agent-briefs/sonnet-w9-14-tv-media-twin-badge.md` : consommateurs `langmedia` / `learn:<classe>-media` enregistrés dans `LotsHub`, fichiers sur le **volume de la bibliothèque** (`Download/CastBridge/Medias/lots/<feature>/<scope>/`), **jamais** dans les 10 Mo ; lecture `.opus`/`.m4a`/`.mp4` par `MediaPlayer` ; `SONNET-WAVE9-INDEX.md` C6 (lot W9 ≤ 8 Mio, vidéo 854×480 CRF 28 ≤ 1,5 Mo / 30 s) | W10 **adopte le même emplacement** (`…/lots/oeuvre/<id>/`) et le même patron (consommateur par fonction) ; différences assumées : budget propre de 200 Mo compté par `WorkStore`, lecture par **loopback chiffré** (œuvres louées : jamais en clair sur disque), presets vidéo ≤ 25 Mo (§ 3.3) |

**Constat clé.** Le dépôt est **à 90 % prêt** pour vendre des œuvres de tiers comme des locations ordinaires : identifiants, éditions, familles, scellement, horloge, balayage, catalogues signés, outils d'émission existent. Les manques réels sont : (A) un **magasin et un budget séparés** pour des médias de plusieurs Mo, (B) un **consommateur et un lecteur** d'œuvres sur la TV, (C) un **catalogue d'œuvres** riche (producteur, synopsis, classification, langue, genre, durée) signé, (D) la **chaîne producteur** (Studio, modération, console, relevés, retrait) côté serveur et outils, (E) des **textes** (accord producteur, charte, CGV).

## 2. Modèle de marché

### 2.1 Rôles

| Rôle | Qui | Ce qu'il fait | Ce qu'il détient | Ne fait jamais |
|---|---|---|---|---|
| **Plateforme** (propriétaire, une personne) | CastBridge | modère, classe, fixe les prix (grille signée), signe les catalogues, publie, scelle (serveur), encaisse via les points focaux, établit et paie les relevés, retire | clés de signature (hors ligne), KEK serveur, TOTP, registre | ne cède aucune clé ; ne modifie pas une œuvre sans accord |
| **Producteur** | humoriste, conteur, musicien, enseignant en langue locale, troupe radio | crée, déclare ses droits, transcode et empaquette avec le Studio (ou le fait faire par la plateforme au pilote), soumet, fixe un **prix proposé**, reçoit relevés et versements | ses fichiers sources, son contrat signé, ses relevés | ne signe rien, ne scelle rien, n'a pas de compte serveur au pilote |
| **Point focal** (agent de terrain, W4-C/W5) | agent délégué | vend clés, bons, confirme les commandes en espèces, **fait découvrir** (aperçus sur sa TV/téléphone), remonte les retours terrain (fiche), remet les espèces | sa clé d'agent, sa délégation, son journal chaîné | n'émet pas de location (P1), ne touche pas aux contenus |
| **Acheteur** (titulaire de la licence, adulte) | foyer | parcourt, écoute l'aperçu, loue (par titre ou par chaîne), paie, reçoit, lit hors ligne | sa TV, son téléphone, ses reçus | ne redistribue pas (CGV) |
| **Profil enfant** | enfant du foyer | voit **seulement** les œuvres autorisées par sa tranche d'âge, lit les locations du foyer | — | n'achète jamais (W5 § 6.6) |
| **Juriste** (externe) | avocat | valide accord, charte, CGV, registre | — | — |

### 2.2 Taxonomie des produits (trois familles)

| Famille | Identifiants | Droit délivré | Durée | Aperçu | Famille de lot |
|---|---|---|---|---|---|
| **Apprendre** (propre) | bouquets existants `classe-<scope>`, `quiz-<lot>` ; article `loc-classe-cm2\|30` | location (W5) | `rentalDays` du catalogue (30 j défaut, ≤ 60 j en ligne) | lots `-trial` déjà sur la TV | réservé |
| **Langues** (propre, libre) | `pack-langues-<code>` (ex. `pack-langues-zh`) ; article `achat-pack-langues-<code>` | **achat définitif** (`Right.Purchase`, format existant `docs/ACTIVATION-FORMAT.md` § 3.3) couvrant les lots **réservés** jumeaux (médias à voix humaine, `langmedia`) **et** le service « livraison + mises à jour signées » ; les lots **libres** CC BY-SA restent gratuits, en clair, dans l'archive (`FreeContentController`) et dans la boutique (« Gratuit ») | sans durée (achat) ; le « confort » = mises à jour pendant 12 mois (CGV) | le libre **est** l'aperçu | libre + réservé (deux lots distincts, LANGUES.md § 13) |
| **Œuvres locales** (tiers) | `oeuvre-<id>` (bouquet d'un lot), `chaine-<producteur>` (bouquet de tous les lots publiés d'un producteur, **ouvert** : un ajout = nouveau catalogue, la location en cours couvre les ajouts jusqu'à sa fin) ; articles `loc-oeuvre-<id>\|30`, `loc-oeuvre-<id>-7j\|7` (second bouquet pour la variante 7 j), `loc-chaine-<p>\|30` | location (W5) | 30 j par défaut ; 7 j pour les courts formats (test H2) | lot `oeuvre:<id>-trial` (30-60 s, marqué « Aperçu CastBridge », en clair, visible en essai) | réservé ; **libre** si le producteur choisit CC BY-SA (§ 4.5) |

**Genres** (`work.json.kind`, liste fermée, extensible par le propriétaire) : `sketch` (audio ou vidéo), `court` (court métrage), `musique` (titre, clip), `conte`, `cours` (leçon en langue locale, santé, agriculture…), `radio` (feuilleton, chronique), `autre`. **Langues** : code ISO 639 + variantes locales déclarées (`fr`, `en`, `pidgin`, `ewondo`, `duala`, `fulfulde`, `bassa`, `ghomala`, `medumba`… liste ouverte, validée par le propriétaire). **Durée** : `durationS`. **Longueur** affichée : court (< 5 min), moyen (5-15), long (> 15).

### 2.3 Prix et modèles (grille signée hors ligne, W4-C § 6 / W5 § 3.1 : format inchangé, lignes en plus)

```
price=loc-oeuvre-<id>|30|<xaf>            (par titre, 30 j)
price=loc-oeuvre-<id>-7j|7|<xaf>          (variante 7 j, bouquet distinct)
price=loc-chaine-<producteur>|30|<xaf>    (chaîne : toutes les œuvres publiées du producteur)
price=achat-pack-langues-<code>|0|<xaf>   (achat définitif ; 0 = sans durée)
set=works.shareProducer|60  set=works.shareAgent|15   (parts en %, le reste = plateforme ; informatives pour l'affichage, le serveur fait foi)
set=works.minPayoutXaf|5000
```
Un producteur **propose** un prix (dans `work.json.suggestedPriceXaf`), la plateforme **fixe** (grille). **Hypothèses de prix du pilote** (§ 10.6) : sketch 30 j 100-200 XAF ; vidéo courte 300 ; chaîne 500 ; classe Apprendre 30 j selon D9-bis (**BLOQUÉ**, hypothèse pilote 1 000) ; pack Langues 500. Aucune de ces valeurs n'entre dans le code ni dans une grille réelle sans décision du propriétaire.

### 2.4 Partage de revenus, versements, relevés

- **Base** : montant TTC de la grille encaissé (`shop_order.amount_xaf`) à l'état `FULFILLED` ; une commande remboursée (`REFUNDED`, W5) crée un `REVERSAL`.
- **Répartition** (hypothèse recommandée, décision **D-W10-3**) : producteur 60 %, point focal 15 % (commission de vente, s'ajoute ou se substitue au régime W4-C selon D7 : **à aligner**), plateforme 25 % (hébergement, scellement, modération, assistance, impayés). Chaîne : la part producteur est **au prorata du nombre d'œuvres publiées** du producteur (une chaîne n'a qu'un producteur : 100 %). Bouquets mixtes multi-producteurs : **exclus** (simplicité ; renversable).
- **Relevé mensuel** `castbridge-producer-statement-v1` (texte signé par la clé serveur, code `P-XXXX-XXXX`) : par œuvre : locations, renouvellements, remboursements, montant brut, part ; total ; cumul non versé ; **jamais** de code d'appareil ni de nom d'acheteur (seulement des comptes). Remis en main propre / WhatsApp PDF par le propriétaire ; vérifiable `GET /api/v1/producers/statements/{code}` (public par code, comme les reçus).
- **Versement** : **espèces** contre reçu signé (le propriétaire ou un point focal mandaté ; même journal chaîné `REMIT` W4-C à l'envers : `PAYOUT item=producteur|<id>|<montant>`), **ou** en **bons de recharge** (le producteur revend ou offre : un bon = sa valeur faciale) si le producteur le préfère ; **seuil** 5 000 XAF (report au mois suivant en dessous) ; **jamais** de mobile money (P5).
- **Litiges** : réclamation client → propriétaire (code de reçu) ; défaut technique avéré (lot illisible, aperçu trompeur) ⇒ remboursement W5 et `REVERSAL` ; plainte sur le **contenu** (droits, diffamation) ⇒ **suspension immédiate** (`SUSPENDED`), examen sous 72 h, retrait ou rétablissement, information du producteur ; **contestation d'un relevé** par le producteur : le propriétaire fournit l'extrait anonymisé des commandes (date, article, montant) sous 15 jours.

### 2.5 Obligations

| Plateforme | Producteur |
|---|---|
| publier ou refuser sous 10 jours ouvrés avec motif ; relevé mensuel ; versement sous 30 jours après seuil ; garder les fichiers 24 mois après retrait pour les relevés ; ne pas altérer l'œuvre (hors transcodage et aperçu convenus) ; retirer sous 72 h sur demande du producteur (hors locations en cours) ; informer de toute plainte ; protéger (scellement) sans garantir l'impossibilité de copie | détenir **tous** les droits (texte, musique, voix, image des personnes filmées : autorisations), ne pas inclure de musique tierce non libérée, respecter la charte (haine, diffamation, politique partisane, mineurs), classification sincère, répondre aux demandes de preuve de droits sous 7 jours, accepter le retrait en cas de plainte sérieuse, ne pas vendre la **même version scellée** ailleurs (non-exclusivité **sinon**) |

## 3. Intégration producteur et ingestion

### 3.1 Identité et KYC-léger (économie d'espèces)

Niveau **P0** (pilote) : nom d'artiste + nom civil + pièce d'identité **vue** par le propriétaire (numéro **non** stocké ; seulement `kyc_level=P0`, date, agent vérificateur), téléphone de contact (haché au serveur, en clair dans le classeur hors ligne du propriétaire), quartier, **accord signé papier** (2 exemplaires, scan dans le classeur hors ligne, `contract_version` + `signed_at` en base). Niveau **P1** (après 3 relevés payés ou > 50 000 XAF cumulés) : copie de pièce conservée hors ligne, attestation d'adhésion à une société de gestion collective si musique (§ 11). Aucune donnée bancaire (espèces). Registre des traitements : § 11.

### 3.2 Studio producteur (`tools/producer-studio/studio.py`, Python 3 + `ffmpeg`/`ffprobe`, bibliothèque standard ; pas d'application web)

```
studio.py inspecter  <fichier>                              # codec, durée, LUFS, résolution, verdict « à transcoder »
studio.py transcoder <fichier> --preset audio|video360|video480|video720 --out DIR    # presets § 3.3
studio.py apercu     <fichier> --debut 12 --duree 45 --marque "Aperçu CastBridge"     # extrait + marque visible (vidéo) / jingle court (audio)
studio.py empreinte  <fichier>                              # SHA-256 du média + empreinte perceptuelle (fpcalc/chromaprint si présent, sinon « non vérifiable »)
studio.py emballer   <dossier-oeuvre>                       # work.json validé + lots oeuvre:<id> et oeuvre:<id>-trial (ZIP déterministes, lotlib) + couverture WebP ≤ 60 Ko
studio.py verifier   <lot.lot>                              # relit tout (tailles, empreintes, classification, licence, champs obligatoires)
studio.py soumettre  <dossier-oeuvre> --vers <dossier-depot>   # dossier de soumission (lots + work.json + déclaration de droits signée scan + empreintes) ; AUCUN envoi réseau
```
`work.json` (dans le lot, champs figés par `C/works/Work.kt`) :
```json
{"format":1,"id":"ndolo-kwata-01","producer":"ndolo","title":"Kwata au marché","kind":"sketch","lang":["fr","pidgin"],"durationS":252,
 "synopsis":"…(≤ 400 car.)","ratingProducer":"all","year":2026,"credits":["Jean N. (voix)"],"license":"RESERVED",
 "media":{"path":"media/kwata.opus","mime":"audio/ogg","sha256":"…","bytes":1980000},"cover":{"path":"cover.webp","sha256":"…"},
 "teaserOf":null,"suggestedPriceXaf":150,"rightsDeclaration":"…(texte, version)","fingerprint":{"sha256":"…","chromaprint":"…|null"}}
```
Le jumeau d'aperçu porte `"teaserOf":"ndolo-kwata-01"`, `durationS ≤ 60`, même `producer`, `kind`, `ratingProducer`. Identifiant : `[a-z0-9][a-z0-9-]{0,26}` (le périmètre d'un lot est ≤ 32 caractères, `-trial` compris : `C/lots/LotManifest.kt:28`), préfixé par le **slug du producteur**. **Le Studio est utilisable par le propriétaire pour le compte du producteur** (cas normal au pilote : les producteurs apportent un fichier WhatsApp/USB).

### 3.3 Presets de transcodage (TV bas de gamme, 32 bits, 720p ; valeurs à confirmer sur la TV de référence, cahier w10-03)

| Preset | Vidéo | Audio | Conteneur | Taille indicative |
|---|---|---|---|---|
| `audio` | — | Opus 48 kbit/s mono (voix) / 64 stéréo (musique), 48 kHz, **−16 LUFS** (comme `mp.py`), pic −1,5 dBTP, silence coupé en tête/queue | OGG | 3-6 min ≈ 1,1-2,9 Mo |
| `video360` (pilote) | H.264 Baseline/Main **3.0**, 640×360, ≤ 400 kbit/s, 25 i/s, GOP 2 s | AAC-LC 64 kbit/s | MP4 `faststart` | 3 min ≈ 10 Mo |
| `video480` | H.264 Main 3.1, 854×480, ≤ 700 kbit/s | AAC 96 | MP4 | 3 min ≈ 18 Mo |
| `video720` (hors pilote, conteneur § 5.6) | H.264 High 3.1, 1280×720, ≤ 1 200 kbit/s | AAC 128 | MP4 | 10 min ≈ 100 Mo |
| `apercu` | preset de l'original ; vidéo : texte « Aperçu CastBridge » en bas (filtre `drawtext`), audio : jingle ≤ 1,5 s en tête | | | ≤ 2 Mo |

Profils H.264 **Baseline/Main** (pas High au pilote) : décodage matériel garanti sur GaiaOS 32 bits ; `Transcode.kt` (profils `dlna-*`, x264 `profile=high`) sert la **diffusion** depuis le téléphone, pas ce stockage : on ne le modifie pas. **Plafond pilote** : 25 Mo par œuvre (serveur, § 6.1), 2 Mo par aperçu.

### 3.4 Identifiant de contenu et doublons
- `fingerprint.sha256` = SHA-256 du **média transcodé** ; `chromaprint` si `fpcalc` est installé (sinon `null`, la console le signale « empreinte perceptuelle absente »). Table `producer_fingerprint (fp UNIQUE, work_id)` : une soumission dont le SHA-256 **ou** le chromaprint existe déjà chez un **autre** producteur est bloquée `DUPLICATE` en file de modération (jamais refusée en silence : les deux producteurs sont informés). Une œuvre déjà retirée pour plainte ne peut pas être re-soumise sous un autre identifiant (même empreinte).
- Les lots sont **déterministes** (`lotlib`, horodatage fixe) : même `work.json` + mêmes médias = mêmes octets ⇒ `sha256` du lot stable, catalogue stable.

### 3.5 Modération (file de revue, console `/admin/producers/works`)

États : `SUBMITTED → IN_REVIEW → CHANGES_REQUESTED | REJECTED | APPROVED → PRICED → PUBLISHED → SUSPENDED | TAKEN_DOWN` (+ `DUPLICATE` bloquant). Liste de contrôle (cochée, horodatée, signée par l'auditeur dans `producer_review.checklist_json`) :
1. **Droits** : déclaration signée présente ; auteurs/interprètes crédités ; musique : originale, libre (licence citée) ou libérée (preuve) ; personnes filmées : autorisation (adultes) / **aucun mineur identifiable sans autorisation parentale écrite** ;
2. **Contenu** : pas d'incitation à la haine, pas d'attaque nominative contre une personne réelle (diffamation), pas de contenu sexuel explicite, pas de promotion de violence ; **politique** : au pilote, **aucun contenu partisan ni visant une personnalité politique** (décision **D-W10-5**, renversable après avis juridique) ; satire sociale **admise** ;
3. **Classification** : proposition producteur ≤ plateforme ; critères : langage grossier répété ⇒ -12 ; sexualité suggérée / violence ⇒ -16 ; **adulte : refusé au pilote** ;
4. **Qualité** : audible (LUFS, pas de saturation), aperçu représentatif, synopsis non trompeur, langue(s) exactes, durée exacte ;
5. **Technique** : `studio.py verifier` vert, taille ≤ plafond, couverture lisible à 3 m.
Délai cible 10 jours ouvrés ; un seul auditeur au pilote (le propriétaire) ; **à deux** (propriétaire + relecteur) dès 20 œuvres/mois.

### 3.6 Contrat, licence, clés
- **Accord producteur** (esquisse : `docs/coordination/ACCORD-PRODUCTEUR-W10-ESQUISSE-2026-10-02.md`, à valider par un juriste) : licence **non exclusive**, mondiale, pour la **durée de l'accord** (12 mois, tacite reconduction, résiliable 30 j), de **reproduire, transcoder, extraire un aperçu, chiffrer, stocker, distribuer par location à durée fixe sur des téléviseurs activés**, afficher titre/couverture/synopsis ; **pas** de sous-licence à des tiers, pas de modification éditoriale, pas de redistribution hors CastBridge ; **retrait** à la demande (effet : plus de nouvelle location ; en cours : jusqu'à échéance ≤ 60 j) ; garanties du producteur ; partage et seuil ; données et relevés ; droit camerounais.
- **Clés** : **la plateforme signe et scelle ; les producteurs n'ont jamais la clé maître de location** (recommandation ferme : une clé entre les mains d'un tiers ne se révoque pas sans réémettre tout le parc ; W5 § 4.2 : « aucun secret maître partagé », clé aléatoire par contrat sous KEK serveur). Le Studio ne contient **aucune** clé. Preuve de provenance côté producteur : la plateforme lui remet, à la publication, un **reçu de dépôt** signé (`castbridge-deposit-v1` : id, version, sha256 des lots, date) : utile en cas de litige d'antériorité.
- **Versions** : une version de lot ne change jamais (`LotService.java:93-94`) ; une nouvelle coupe = version +1, catalogue re-signé ; pour un contrat en cours, le serveur **rescelle la nouvelle version** sous la même clé de contrat (`lotKey(rentalKey, lot, version)` est déterministe) et le téléphone la livre ; la TV garde une seule version (`TvLotStore`). L'aperçu suit (version +1).

### 3.7 Retrait et révocation (TV hors ligne)
- **Serveur** : `TAKEN_DOWN` ⇒ le lot est **retiré** (`LotService.revoke`, 410), le bouquet disparaît du catalogue signé suivant (le propriétaire re-signe), la grille perd la ligne ; **aucune nouvelle location ni renouvellement** (`/shop/quote` : « œuvre retirée »).
- **Locations livrées** : par défaut, elles **continuent jusqu'à leur fin** (≤ 60 j), car la TV est hors ligne et le client a payé ; relevé : pas de `REVERSAL`.
- **Retrait impératif** (plainte sérieuse, injonction) : ordre différé signé `work.takedown {work, reason, refund: bool}` (portée `POLICY`, `PolicyCatalog.ACTIONS` + `C/policy/PolicyActions.kt`), livré comme les révocations (battement de cœur de la TV en ligne, relais téléphone w3-09, fichier USB) : la TV **retire** le lot (`WorkStore.remove`), journalise, affiche « Cette œuvre a été retirée à la demande de son ayant droit ; CastBridge vous contacte pour la suite » ; si `refund`, commande `REFUNDED` par le propriétaire (TOTP). **Limite honnête** : une TV qui ne voit ni Internet ni téléphone garde l'œuvre jusqu'à la fin de la location.

## 4. Protection, confiance, familles

### 4.1 Ce qui est réutilisé (sans changement)
Scellement par contrat et par installation (W4-A boîte v2, W5 § 4.3), `LotCrypt` au repos (W4-A § 7 : le lot d'œuvre est chiffré au repos comme les autres), destruction de clé + balayage (`RentalSweeper`), horloge durcie, `X-CB-TV-Proof` (W6), anomalies serveur. Un **contenu de tiers ne change rien à la cryptographie** ; il change la **responsabilité** (§ 4.2).

### 4.2 Contenu propre vs contenu de tiers

| | Contenu propre (Apprendre, Langues) | Œuvre d'un producteur |
|---|---|---|
| Responsabilité éditoriale | plateforme | producteur (garanties) **et** plateforme (modération, hébergeur-éditeur : qualification **à faire trancher par le juriste**) |
| Retrait | rare (correction) | sur plainte ou demande du producteur ; ordre `work.takedown` |
| Classification | « tous publics » par construction | producteur + plafond plateforme, portée par le catalogue |
| Relevé | aucun | mensuel, part producteur |
| Preuve d'antériorité | dépôt git | reçu de dépôt signé + empreintes |
| Licence | réservé / CC BY-SA (LANGUES.md § 13) | réservé par défaut ; CC BY-SA explicite possible |

### 4.3 Contrôle parental
- `work.json.rating` = classification **plateforme** (`all|12|16`) → `LotMeta`-side : le `WorkConsumer` l'expose ; `ParentalHub` applique `AgeBand.allows(Rating)` (`ParentalModel.kt:14-18`) **comme pour une vidéo classée** : œuvre au-delà de la tranche = **masquée** pour un profil enfant (pas seulement verrouillée : une œuvre n'est pas un fichier du foyer), **code parental** pour un adulte si « PIN pour lire -16 » est activé (réglage existant des vidéos). La **boutique** (téléphone et TV) masque les œuvres > tranche sous profil enfant, et l'enfant n'achète jamais (W5 § 6.6).
- Lecture d'une œuvre = `UseKind.PLAY` (temps de lecture compté, heures autorisées) ; aucune nouvelle catégorie.
- **Plateforme > producteur** : la plateforme ne peut qu'**élever** la classification proposée.

### 4.4 Édition d'essai, mode réduit
- **Essai** : la tuile « Œuvres » est **visible avec les aperçus seulement** (comme Apprendre montre ses lots d'essai ; décision **D-W10-6**, recommandation OUI : l'aperçu est l'outil de conversion) ; `TrialPolicy` : `/api/oeuvres` (lecture, aperçus) ouvert, location refusée (« Passez en version complète » + point focal). Aucune commande en essai (W5).
- **Mode réduit** (W4-B) : locations en cours lisibles ; aperçus lisibles ; aucune nouvelle installation (`/api/rental/install` fermé, inchangé).
- **`LOCKED`** : rien.

### 4.5 Familles libre / réservé, CC BY-SA
- Toute œuvre payante : `oeuvre:<id>` dans la liste `reserved` de `content/oeuvres/lots.json` (format de `content/learn/lots.json`, listes explicites `free`/`reserved`) ; famille inconnue ⇒ refus de location (`RentalPolicy.refusal`, inchangé).
- **Producteur qui choisit le libre** : `work.json.license = "CC-BY-SA-4.0"` + auteur + source ⇒ l'id entre dans `free`, le lot est **livré en clair**, affiché « Gratuit (CC BY-SA 4.0) » dans la boutique, **et** inscrit dans `tools/free-content/license-tags.json` (étiquette **explicite**, FREE-CONTENT § 1) ⇒ archive libre. Jamais de lot `-trial` pour une œuvre libre (elle est son propre aperçu). Mélange interdit (libre + réservé pour le même id : refus de `studio.py verifier`).
- **Langues** : le texte CC BY-SA reste libre ; **aucune mesure technique** ne le touche (CC BY-SA 4.0 § 2(a)(5)(C), FREE-CONTENT § 4.7 : **à valider par un juriste**) ; ce qui est vendu est **réservé** (voix humaines, lot `langmedia` réservé) **ou** un **service** (livraison, mises à jour, assistance). L'écran dit : « Le contenu de ce pack est libre (CC BY-SA 4.0) : vous pouvez le télécharger gratuitement [bouton]. Le pack de confort ajoute les voix enregistrées et les mises à jour pendant 12 mois. »

## 5. Boutique : expérience utilisateur

### 5.1 Téléphone (CastBridge, onglet « Boutique » de W5, `S/shop/**`)
Trois onglets en tête : **Leçons** (Apprendre, par cycle puis classe ; filtre profil), **Langues** (par langue : « Gratuit » + « Pack de confort »), **Œuvres locales** (cartes : couverture, titre, producteur, genre, langue, durée, classification, prix 30 j ; filtres : genre, langue, longueur, producteur ; tri : nouveautés, « les plus loués » (comptes serveur), A-Z). **Page producteur « chaîne »** : photo/logo, présentation (≤ 600 car.), œuvres, « Louer toute la chaîne 30 j (X XAF) ». **Fiche œuvre** : aperçu (lecture sur le téléphone, ou « Lire l'aperçu sur la TV »), synopsis, crédits, « Louer 30 j (X XAF) » / « 7 j (Y) », « En location jusqu'au … (renouveler) », taille (« 9,8 Mo ; il manque X Mo sur la TV » d'après le budget `WorkStore`). Paiement : écran **« Payer »** de W5 inchangé (code de recharge / espèces chez le point focal). Hors ligne : catalogue en cache, aperçus déjà sur le téléphone lisibles.
**Textes** (FR) : « Œuvres locales : sketches, courts métrages, musique, contes, cours, radio, par des artistes d'ici » ; « Aperçu gratuit » ; « Louer 30 jours » ; « Cette œuvre est classée -12 : elle n'apparaît pas aux profils enfants de moins de 12 ans » ; « Le producteur reçoit la plus grande part de ce que vous payez » (si D-W10-3 = oui à l'affichage).

### 5.2 TV (CastBridge-TV, D-pad, 720p)
- **Tuile « Œuvres »** sur l'accueil (après « Apprendre », `homeTools`, icône `ic_t_works`) → `WorksActivity` : colonne gauche (Tout, Sketches, Vidéos, Musique, Contes, Cours, Radio, **Mes locations**), grille de cartes (couverture, titre, producteur, durée, badge « Aperçu » / « Loué · 12 j restants » / « Louer »), fiche : OK = **lire** (aperçu ou œuvre), touche verte « Louer » (ouvre `ShopActivity` W5 sur l'article ; sans W5 : écran « Pour louer : CastBridge sur votre téléphone ou votre point focal, code TV XXXX »), jaune « Chaîne », bleue « Aide ». Compte à rebours = phrases de `RentalEngine` (inchangées). Profil enfant : filtre par classification, pas de « Louer ».
- **Boutique TV** (W5 § 8) : la colonne gauche gagne **« Œuvres locales »** et **« Langues »** ; cartes et flux de paiement identiques.
- **Lecture** : `WorkHub.play(id)` ⇒ l'œuvre est ouverte **en mémoire** (`LotOpener`, cache de 2 lots de W4-A : porter la limite à **1 œuvre ≤ 25 Mo** pour ce magasin), servie à libVLC par `/stream/oeuvre/<id>?t=<jeton de run>` (même garde loopback que `ReceiverServer.kt:277-283`, `Range` pris en charge) ; l'audio affiche la couverture plein écran + titre + progression ; reprise de lecture (position) comme la bibliothèque. Pas de copie claire sur disque ; **repli** si mémoire insuffisante (ActivityManager `isLowRamDevice` ou œuvre > 16 Mo sur TV 32 bits) : fichier temporaire **privé** `files/.play/<id>` effacé à l'arrêt (résiduel documenté, comme RENTAL-LOTS § 11).
- Accessibilité : textes ≥ 28 px, focus visible, `contentDescription`, jamais la couleur seule (W5 § 6.6).

### 5.3 Catalogue hors ligne signé
- **Catalogue des œuvres** `castbridge-works-catalog-v1` (JSON + signature Ed25519 de la clé des mises à jour, texte canonique ligne par ligne comme `SignedBundleCatalog.canonicalPayload`, `SignedBundleCatalog.kt:34-40`) : par œuvre : `id|producer|kind|langs|durationS|rating|license|lotVersion|teaserVersion|sha256(title)|sha256(synopsis)|coverSha256`, plus la section `producers` (`slug|sha256(name)|sha256(bio)`) ; titres, synopsis, bios **dans le JSON** (vérifiés par empreinte). Produit par `trial_edition.py sign-works-catalog` (même clé que le catalogue des bouquets), déposé sur le serveur (`castbridge.catalog.works-file`), relayé par `GET /api/v1/catalog/works` (copie de `BundleCatalogController`), gardé par le téléphone (anti-retour `generatedAt`), relayé à la TV par `POST /api/shop/catalog` (W5) **ou** par `POST /api/oeuvres/catalog` (sans W5), vérifié avec `UpdateKeys.PUBLIC_KEYS`.
- **Couvertures** : dans le lot d'aperçu (donc sur la TV hors ligne dès qu'un aperçu est livré) ; la boutique sans aperçu montre une couverture générique.
- **Bouquets et prix** : catalogue des bouquets (bouquets `oeuvre-*`, `chaine-*`, `pack-langues-*`) et grille de prix signés (W5), inchangés dans leur format.

### 5.4 Achat ⇒ location scellée ⇒ TV (réutilise W5 § 4.5 et W4)
1. Vitrine (catalogues en cache) → 2. lecture de la TV (demande v2 + preuve + `GET /api/rental` **+ `GET /api/oeuvres`** : manifeste du `WorkStore` : budget restant, œuvres déjà là) → 3. `POST /shop/quote` (`loc-oeuvre-<id>|30`) → 4. payer (bon / espèces) → 5. attente / notification → 6. récupérer : activation de location + lot **scellé** par `GET /shop/rentals/{contrat}/lots/oeuvre/<id>/<v>` (route W5, `Range`, Wi-Fi seulement : un lot d'œuvre peut faire 25 Mo) dans le **stock d'œuvres** du téléphone (`files/works/sealed/`, quota `WorkBudget.PHONE_MAX_BYTES` 300 Mo, **distinct** des 100 Mo de lots) → 7. livrer : `POST /api/activation/install` puis `/api/lots/upload` (morceaux de 512 Ko, reprise) + `POST /api/rental/install?name=castbridge-lot-oeuvre-<id>-v<n>.lot&contract=…` : `RentalApi` reconnaît la fonction `oeuvre` et confie le lot au `WorkStore` ; le `WorkConsumer` installe (chiffré au repos, `LotSealing.Rental`) ; `markRented` ⇒ balayage à l'échéance → 8. accusé, reçu.
**Aperçus** : lots `-trial` ordinaires : `LotSync` les télécharge (`feature=oeuvre`, édition `trial`), `DeliveryQueue` les livre dans le `WorkStore` (priorité basse, après les lots d'Apprendre du profil), ou le point focal les apporte sur **clé USB** (adoption des paires lot + preuve au branchement, `TvLotStore.adoptFrom`, LOTS.md § 6 : à étendre au second magasin).
**Vente assistée par le point focal** : le client dit « je veux la chaîne de Ndolo », l'agent tape la commande **sur le téléphone du client** (ou le client sur la TV), le code `CB-XXXX-XX` est confirmé par l'agent (W5 § 5.2 b) ; **ou** bon `loc-chaine-ndolo|30` vendu d'avance (W5 § 5.1 a). Un bon **par œuvre** est trop fin : les bons du pilote couvrent **les chaînes** et **les jetons** ; les titres isolés passent par la commande en espèces (b).

### 5.5 Analyses pour les producteurs (consentement et vie privée)
- **Toujours** (données comptables, sans consentement nécessaire) : locations par œuvre et par mois, renouvellements, remboursements, zone (quartier du point focal, jamais l'appareil).
- **Avec consentement « statistiques d'usage »** (TELEMETRY.md § 2) : événement `work_play{work, pct, ms, teaser: bool}` (jamais de code d'appareil, `work` = identifiant public de l'œuvre) ⇒ `producer_play_stat (work_id, day, plays, completions, devices)` ; affiché au producteur avec la mention « d'après N téléviseurs ayant accepté les statistiques ».
- **Jamais** : qui a loué, quand précisément, le foyer, les profils, les autres applications.
- **Pouce** (optionnel, décision **D-W10-7**, recommandation : **non au pilote**) : si oui, un « J'aime » local, agrégé dans `work_play.liked`, jamais affiché publiquement avant 20 votes.

### 5.6 Évolution hors pilote : conteneur long format `castbridge-work-v1`
Pour les œuvres > 25 Mo (courts métrages 720p, feuilletons) : fichier `castbridge-work-v1` = en-tête JSON signé (id, version, `chunkSize` 1 Mio, `sha256` du clair, codec, durée, classification) ‖ morceaux `AES-256-GCM(kChunk_i, nonce_i, aad = id|version|i, clair_i)` avec `kChunk_i = HKDF(lotKey, "chunk|i")` : **lecture avec recherche** (le lecteur demande un `Range`, la TV déchiffre les morceaux touchés en flux), aucune mise en mémoire entière ; stocké dans le **coffre vidéo** (`files/works/vault/` ou clé USB), livré par le **transfert multivoie** (`/api/transfer/*`, TRANSFER.md) ou par **téléchargement direct de la TV** (`DownloadManager`, DOWNLOADS.md) si elle a Internet (W5 § 4.7 : à la demande). Deux cahiers (cœur + TV) après le pilote, si H1/H3 sont vérifiées. Pas de deuxième format cryptographique au pilote.

## 6. Serveur (additif ; module `castbridge.producers.enabled=false` par défaut)

### 6.1 Lots : fonctions et plafonds
`LotService.FEATURES` → `{learn, quiz, langues, langmedia, oeuvre}` ; plafond **par fonction** : `learn`/`quiz` 10 Mo (inchangé), `langues` 3 Mo, `langmedia` 100 Mo, `oeuvre` **25 Mo** (`castbridge.lots.max-bytes.oeuvre`, pilote) ; `WorkLotValidator implements LotValidator` (feature `oeuvre`) : ZIP avec un seul `work.json` conforme, un média dont le `sha256` correspond, couverture WebP ≤ 60 Ko, aucun autre fichier, aperçu (`-trial`) ≤ 2 Mo et `durationS ≤ 60`, classification ∈ {all,12,16}. La route de téléchargement `GET /api/v1/lots/oeuvre/{scope}/{v}` **ne sert que les aperçus** (`-trial`) : un lot complet d'œuvre n'est servi **que scellé** par `/shop/rentals/...` (W5) ; un lot `oeuvre` **complet** non scellé n'est jamais servi (test).

### 6.2 Entités (migration `V<plus haut + 1>__producers.sql` ; W5 prend « plus haut + 1 » à son tour : le cahier vérifie `git log --all -- backend/src/main/resources/db/migration`)
```
producer            (id PK, slug VARCHAR(24) UNIQUE, display_name, legal_name_hash CHAR(64), phone_hash CHAR(64), zone VARCHAR(64), kyc_level ENUM(P0,P1),
                     contract_version VARCHAR(16), signed_at, status ENUM(ACTIVE, SUSPENDED, CLOSED), payout_pref ENUM(CASH, VOUCHERS), notes TEXT, created_at)
producer_work       (id PK, producer_id FK, work_id VARCHAR(32) UNIQUE, kind, title, langs VARCHAR(64), genre, duration_s INT, rating_producer, rating_platform,
                     synopsis TEXT, cover_sha256, license ENUM(RESERVED, CC_BY_SA_4), suggested_price_xaf INT NULL, state ENUM(SUBMITTED, IN_REVIEW, CHANGES_REQUESTED, REJECTED,
                     DUPLICATE, APPROVED, PRICED, PUBLISHED, SUSPENDED, TAKEN_DOWN), submitted_at, reviewed_at, reviewed_by, published_at, taken_down_at, takedown_reason)
producer_work_version (work_id FK, version INT, lot_id FK lots NULL, teaser_lot_id FK NULL, sha256_media CHAR(64), bytes BIGINT, chromaprint TEXT NULL, deposit_code CHAR(11) UNIQUE,
                     deposit_signed TEXT, created_at, PK(work_id, version))
producer_fingerprint (fp VARCHAR(128) PK, kind ENUM(SHA256, CHROMAPRINT), work_id FK)
producer_review     (id PK, work_id FK, version INT, checklist_json TEXT, decision ENUM(APPROVE, CHANGES, REJECT), notes TEXT, by VARCHAR(64), at)
producer_takedown   (id PK, work_id FK, requester ENUM(PRODUCER, COMPLAINT, OWNER), reason TEXT, refund BOOL, order_seq BIGINT NULL, state ENUM(OPEN, ORDERED, DONE), at)
producer_sale       (id PK, order_id FK shop_order UNIQUE, work_id FK NULL, channel_slug NULL, contract_id FK NULL, amount_xaf INT, share_producer_xaf INT, share_agent_xaf INT,
                     share_platform_xaf INT, agent_kid NULL, at, reversed_at NULL, statement_id FK NULL)
producer_statement  (id PK, code CHAR(11) UNIQUE 'P-XXXX-XXXX', producer_id FK, period CHAR(7), lines_json TEXT, gross_xaf INT, share_xaf INT, carried_xaf INT, total_due_xaf INT,
                     state ENUM(DRAFT, ISSUED, PAID, CARRIED), signed_text TEXT, issued_at, paid_at NULL, paid_via ENUM(CASH_OWNER, CASH_AGENT, VOUCHERS) NULL, receipt_ref NULL)
producer_play_stat  (work_id, day DATE, plays INT, completions INT, devices INT, teaser_plays INT, PK(work_id, day))
```
`producer_sale` est rempli par un **écouteur additif** d'`OrderService` (W5 w5-07) à `FULFILLED`/`REFUNDED` pour tout article `loc-oeuvre-*`, `loc-chaine-*` (interface `SaleListener` définie par W10, branchée en une ligne dans w5-07 s'il est fusionné ; sinon un **import CSV** des ventes papier du pilote, § 10.4). Parts calculées avec les `set=works.*` de la **grille signée** (le serveur relit la grille publiée ; un changement de parts ne s'applique qu'aux ventes suivantes).

### 6.3 Routes
| Route | Rôle |
|---|---|
| `GET /api/v1/catalog/works` | relais du catalogue des œuvres signé hors ligne (ETag, ≤ 2 Mio) |
| `GET /api/v1/producers/statements/{code}` | relevé signé, public par code (comme les reçus) |
| `/admin/producers` | liste, fiche (KYC, contrat, œuvres, cumul dû), suspension (TOTP) |
| `/admin/producers/works` | **file de revue** : soumissions importées depuis un dossier de dépôt (`POST /api/v1/admin/producers/works/import`, multipart : lots + `work.json`), liste de contrôle, décision, classification plateforme, **prix proposé → ligne de grille à signer** (export d'un fragment `price=` pour `tools/prices/sign_prices.py`), publication = `LotService.publish` des deux lots + rappel « re-signer catalogue des œuvres et des bouquets » |
| `/admin/producers/takedowns` | ouvrir, émettre l'ordre `work.takedown` (TOTP), suivre |
| `/admin/producers/statements` | générer les brouillons du mois, émettre (signature serveur), marquer payé (TOTP, mode, référence du reçu), CSV |
| `/admin/producers/stats` | lectures agrégées (consentement), ventes par œuvre |
Audit : `AuditLog` chaîné existant (chaque décision, prix, retrait, versement). Limites : ≤ 50 œuvres par producteur, ≤ 200 au total au pilote ; relevés : 24 mois ; import ≤ 30 Mo par soumission.

### 6.4 Console du bureau (`tools/activation-desktop`) : rien de nouveau au pilote (la revue se fait sur la console web) ; `tools/producer-studio/livrer.py` et `releve.py` sont les outils hors ligne (§ 10.4).

## 7. Modèle de données côté client (cœur, JVM pur)

`C/works/Work.kt` (`Work`, `WorkKind`, `WorkRating` ↔ `parental.Rating`, `WorkLicense`, `parse/validate(work.json)`), `C/works/WorksCatalog.kt` (`SignedWorksCatalog.verify(json, keys, notOlderThan)`, `Producer`, `canonicalPayload`), `C/works/WorkBudget.kt` (`TV_MAX_BYTES = 200 Mo`, `PHONE_MAX_BYTES = 300 Mo`, `MAX_WORK_BYTES = 25 Mo`, `MAX_TEASER_BYTES = 2 Mo`), `C/works/WorkItems.kt` (article `loc-oeuvre-<id>|<j>`, `loc-chaine-<p>|<j>`, `achat-pack-langues-<code>` : extension de `ShopItem` de w5-01 si présent, sinon analyseur local), `C/works/WorkStoreIndex.kt` (ce que la TV sait d'une œuvre installée : id, version, édition, classification, taille, contrat), `C/shop/ShopTaxonomy.kt` (trois familles, catégories, filtres, tri ; pur). Vecteurs : `tools/activation/works-vectors.json` (`castbridge-works-vectors-v1` : catalogue signé OK / altéré / plus ancien ; `work.json` valide / id trop long / classification adulte refusée / aperçu > 60 s / libre + réservé ; articles).

## 8. Séquences

**S1 — Du producteur à la boutique**
```
Producteur ──fichiers (USB/WhatsApp)──► Propriétaire : studio.py inspecter → transcoder → apercu → emballer → verifier → soumettre
                                        ──► console /admin/producers/works/import (lots + work.json) → revue (liste) → APPROVED → prix proposé
                                        ──► hors ligne : sign_prices.py (grille), trial_edition.py select + sign-catalog + sign-works-catalog
                                        ──► dépôt serveur (catalogues) + publish des lots → PUBLISHED → reçu de dépôt au producteur
```
**S2 — Achat et livraison** : § 5.4 (W5 § 4.5 + `WorkStore`).
**S3 — Fin de location** : `RentalEngine` ⇒ `RentalSweeper` : clé détruite, `WorkStore.remove` (place libérée sur le volume vidéo), message « Location terminée : … Reprendre la location ? ».
**S4 — Relevé et versement** : 1er du mois : brouillons ⇒ propriétaire vérifie ⇒ `ISSUED` (signé) ⇒ remise au producteur ⇒ espèces contre reçu (`PAYOUT` dans un journal chaîné) ⇒ `PAID` (TOTP) ; < seuil ⇒ `CARRIED`.
**S5 — Retrait** : plainte ⇒ `SUSPENDED` (plus de vente) ⇒ examen ≤ 72 h ⇒ `TAKEN_DOWN` + ordre `work.takedown` si impératif ⇒ TV : retrait au prochain contact ⇒ `REVERSAL` si remboursement.

## 9. Décisions prises par l'architecte (renversables) et décisions du propriétaire

**Prises ici** : une œuvre = un lot `oeuvre:<id>` (≤ 25 Mo) + aperçu `-trial` ; second magasin TV 200 Mo sur le volume vidéo, stock téléphone 300 Mo ; lecture en mémoire via loopback ; aucun nouveau format cryptographique au pilote (conteneur par morceaux après) ; plateforme signe/scelle, producteurs sans clé ; aucun filigrane forensique ; classification plateforme ≥ producteur, pas d'adulte ; aperçus visibles en essai ; locations livrées continuent après retrait sauf ordre ; pas d'avis publics ; pas de bouquets multi-producteurs ; chaîne = 100 % au producteur ; relevé mensuel signé, seuil 5 000 XAF (hypothèse) ; versement espèces ou bons ; Langues = libre gratuit + pack de confort (réservé/service) sans mesure technique sur le libre ; module serveur éteint par défaut.

| # | Question au propriétaire | Bloque | Recommandation |
|---|---|---|---|
| **D-W10-1** | Lancer le **pilote papier** maintenant (outils d'aujourd'hui, § 10.4 A) **ou** attendre le pilote technique minimal (7 cahiers) ? | calendrier | **Les deux** : papier dès S0 (recrutement, production, revue), technique minimal en parallèle (≈ 2,5 semaines), vente en S3 avec le minimal si prêt, sinon papier |
| **D-W10-2** | Prix d'hypothèse du pilote (sketch, vidéo, chaîne, classe Apprendre, pack Langues) | grille du pilote, affiches | 150 / 300 / 500 / 1 000 (D9-bis) / 500 XAF, 30 j ; tester 7 j à 50 XAF sur 2 sketches en semaine 4 |
| **D-W10-3** | Parts : producteur / point focal / plateforme ; afficher la part producteur au client ? | accord producteur, relevés | 60 / 15 / 25 ; oui (« la plus grande part va à l'artiste ») |
| **D-W10-4** | Versement : espèces par le propriétaire seul, ou aussi par un point focal mandaté ; bons acceptés ? | S4, fiche agent | propriétaire seul au pilote ; bons acceptés si le producteur le demande |
| **D-W10-5** | Contenu politique partisan / personnalités : exclu au pilote ? | charte | **exclu** jusqu'à avis juridique ; satire sociale admise |
| **D-W10-6** | Aperçus visibles en édition d'essai ? | `TrialPolicy` | oui |
| **D-W10-7** | « Pouce » / avis ? | TV, serveur | non au pilote |
| **D-W10-8** | Budget TV des œuvres (200 Mo ?) et volume (interne ou clé USB d'abord ?) | `WorkBudget` | 200 Mo, **clé USB d'abord** si présente (STORAGE.md), sinon interne |
| **D-W10-9** | Pack Langues : vendre un **service** (livraison + mises à jour 12 mois) tant qu'il n'existe pas de médias réservés à voix humaine ? | w10-12, CGV | oui, à 500 XAF (hypothèse), texte « contenu libre » explicite ; **à valider par un juriste** |
| **D7** (W4) | nom commercial, contact | textes | inchangé |
| **D9-bis** (W4) | prix Apprendre | grille | inchangé (**BLOQUÉ**) |

## 10. L'EXPÉRIENCE : pilote « trois familles » sur 7 semaines

### 10.1 Objectif et questions
Savoir, avec de vrais producteurs et de vrais foyers, si des œuvres locales **louées par TV** à des prix de quelques centaines de XAF, vendues en espèces par des points focaux, **créent de la valeur** pour les trois parties (producteur, foyer, plateforme), **à côté** des leçons (Apprendre) et des langues ; et mesurer la charge (modération, assistance, piratage).

### 10.2 Hypothèses, métriques, seuils go/no-go

| # | Hypothèse | Métrique (comment on la mesure) | Seuil « go » | « no-go » |
|---|---|---|---|---|
| H1 | Les foyers paient pour des œuvres locales | % de foyers pilotes avec ≥ 1 location d'œuvre en 4 semaines (journal des ventes / `shop_order`) | ≥ 30 % | < 15 % |
| H2 | 30 j est la bonne durée ; 7 j a sa place | répartition 7 j / 30 j (semaine 4), renouvellements à J+30 | ≥ 25 % de renouvellement des chaînes | < 10 % |
| H3 | L'aperçu convertit | locations / aperçus lus (`work_play{teaser}` avec consentement ; sinon questionnaire) | ≥ 20 % | < 8 % |
| H4 | Prix acceptable | ventes par palier ; refus « trop cher » (fiche agent) ; test A/B 100 vs 200 XAF sur 2 sketches comparables | élasticité mesurée, prix médian accepté ≥ 150 XAF | majorité « trop cher » à 100 |
| H5 | Les producteurs reviennent | producteurs qui soumettent un **2ᵉ lot** d'œuvres avant S6 ; satisfaction (entretien) | ≥ 3/5 | ≤ 1/5 |
| H6 | Piratage contenu | copies trouvées (2 groupes WhatsApp de quartier suivis, clés USB des points focaux, demandes « donne-moi le fichier ») ; tentatives sur la TV (anomalies serveur) | 0 copie claire d'une œuvre payante | ≥ 2 copies |
| H7 | Charge d'assistance | tickets (fiche agent, WhatsApp) / foyer / semaine ; temps du propriétaire | ≤ 0,5 ticket/foyer/semaine ; ≤ 4 h/semaine | > 1,5 ; > 10 h |
| H8 | Charge de modération | minutes par œuvre (horodatage de la liste) ; taux de `CHANGES_REQUESTED` | ≤ 30 min/œuvre ; ≤ 30 % | > 60 min ; > 60 % |
| H9 | Les trois familles se renforcent | % de foyers ayant loué **2 familles** ; panier moyen | ≥ 20 % ; ≥ 1 500 XAF/foyer/mois | < 5 % |
| H10 | Langues : le confort se paie | achats du pack / foyers informés ; téléchargements de l'archive libre | ≥ 10 % | < 3 % |
| H11 | Encaissement fiable | écart caisse / journal chaîné des agents (W4-C `REMIT`) | 0 écart non expliqué | ≥ 2 écarts |

**Décision globale** : GO = H1, H5, H6, H8 vertes et aucune rouge ; CONTINUER-AJUSTER = une rouge hors H6 ; STOP = H1 rouge **ou** H6 rouge **ou** deux rouges.

### 10.3 Producteurs : sélection et approche
- **Critères** : (1) œuvres **propres** (pas de musique tierce ; sketches, contes, chansons originales) ; (2) un **public local** existant (radio de quartier, WhatsApp/Facebook ≥ 500 abonnés, spectacles) ; (3) **disponible** 4-6 semaines, à portée du propriétaire ; (4) accepte l'accord **non exclusif, révocable** et le partage ; (5) **diversité** : 2 humoristes (sketch audio 3-6 min), 1 conteur en langue locale, 1 musicien (3-4 titres + 1 clip 360p), 1 enseignant/animateur (cours courts : santé, agriculture, langue locale) ; un 6ᵉ de réserve.
- **Approche** : pitch d'une page (`PITCH-PRODUCTEURS-W10-2026-10-02.md`), démonstration sur la TV de référence (aperçu + location + compte à rebours), accord pilote signé, **avance de pilote** (hypothèse 25 000 XAF, déductible ou non des parts : **décision**), calendrier d'enregistrement ; le propriétaire **enregistre ou récupère** les fichiers et fait le Studio pour eux (le Studio seul est testé avec 1-2 producteurs à l'aise).
- **À produire** : par humoriste 6 sketches audio (dont 2 vidéo 360p ≤ 3 min) ; conteur 5 contes ; musicien 4 titres + 1 clip ; enseignant 5 cours de 5-8 min ⇒ **≈ 20 audios + 5 vidéos**, 25 aperçus ; côté propre : 3 bouquets Apprendre (CM2, 3e, Tle : D9-bis) + 1 pack Langues (zh-a0 ou ja-a0 pilotes, `tools/langues/gen_a0_pilot.py`).

### 10.4 Avec quoi on le fait : ce qui marche aujourd'hui vs ce qui est manuel

**A. Pilote papier (exécutable sans aucun nouveau code, dès D-W10-1)**
| Étape | Outil existant | Manuel / bricolé |
|---|---|---|
| Transcodage, contrôle | `ffmpeg`, contrôles de `tools/media-pipeline/mp.py receive` (LUFS, silence) | presets tapés à la main (§ 3.3) |
| Empaquetage | `tools/content-lib/lotlib.py` (ZIP déterministe), format `lot.json` de `build_lots.py` | `work.json` écrit à la main ; **la TV ne sait pas lire un lot `oeuvre`** ⇒ au papier, l'œuvre est livrée comme **vidéo/audio de la bibliothèque** (copie ordinaire téléphone → TV, en clair) : **aucune protection** ; acceptable 4 semaines **avec l'accord écrit des producteurs** (clause pilote) et des œuvres **non exclusives** ; le point focal supprime à l'échéance (fiche) |
| Catalogue, prix | affiches papier + liste WhatsApp | pas de boutique ; le client choisit sur une **fiche** chez le point focal |
| Vente, encaissement | journal des ventes **papier** (modèle `content/oeuvres/pilote/ventes.csv`), clés d'activation vendues par le point focal (W4-C si fusionné, sinon outil de bureau) | aucune commande serveur ; `releve.py` calcule les parts depuis le CSV |
| Location réelle (Apprendre) | **oui** : `emettre --location classe-cm2=…:30` + `lot-chiffrer` + `rental_test.py` (généralisé en `livrer.py`) sur le LAN de la TV ou via le relais téléphone (nc) | le propriétaire se déplace ou pilote à distance |
| Mesure | fiches agent, questionnaire, entretiens, `playback_end` (consentement) | pas de `work_play` |

**B. Pilote technique minimal** (vendre une œuvre **comme une location ordinaire dans le build actuel**, sans W5) : exactement les cahiers **w10-01, w10-04, w10-05 (partie lots), w10-08, w10-09, w10-10, w10-13** (≈ 12 j). Changements de code, précisément :
1. `C/works/{Work,WorksCatalog,WorkBudget,WorkStoreIndex}.kt` + tests (nouveau) — w10-01.
2. `content/oeuvres/{registry.json,lots.json,dist/}` ; `tools/trial-edition/trial_edition.py` (bouquets `oeuvre-*`/`chaine-*`, `rentalDays`, `sign-works-catalog`) ; `tools/content-lots/build_lots.py` (ramasser `content/oeuvres/dist/*.lot`) ; `content/bundles-rental.json` (7 j pour `-7j`) — w10-04.
3. `B/lots/LotService.java` (`FEATURES`, plafond par fonction), nouveau `B/lots/WorkLotValidator.java`, `B/lots/WorksCatalogController.java` (relais), `application.yml` — w10-05 (sans la migration producteurs, qui peut attendre).
4. `R/WorkHub.kt` (second `TvLotStore(File(volumeBibliothèque, "Medias/lots/oeuvre"), …, maxBytes = WorkBudget.TV_MAX_BYTES)` : même racine que les lots média de w9-14), `R/WorkConsumer.kt`, `C/lots/RentalApi.kt` (`storeFor(id)` : `oeuvre` ⇒ `WorkStore`), `R/LotsHub.kt` (routage de `/api/lots/*` par fonction, adoption USB), `C/tv/ReceiverServer.kt` (`/stream/oeuvre/<id>` loopback depuis une source mémoire), `TrialPolicy`/`DegradedPolicy` (`/api/oeuvres`), `tools/routes/routes.txt` — w10-08.
5. `R/WorksActivity.kt`, `R/WorksViews.kt`, tuile, icône, manifeste (activité `exported=false`) — w10-09.
6. `R/ParentalHub.kt` (classification des œuvres, filtre enfant) — w10-10.
7. `tools/producer-studio/livrer.py` (émission `--location oeuvre-<id>` ou `chaine-<p>` avec l'outil de bureau, scellement `lot-chiffrer`, envoi `/api/lots/upload` + `/api/rental/install`, via LAN ou relais téléphone) — w10-13.
**Non requis pour vendre** (mais requis pour l'échelle) : Studio complet (w10-03 : le propriétaire peut emballer à la main avec `lotlib` au pilote), console producteurs (w10-06), téléphone (w10-11 : le propriétaire livre), télémétrie (w10-07), Langues (w10-12).

### 10.5 Collecte des mesures (hors ligne, avec consentement)
- **Journal des ventes** : CSV par point focal (papier recopié le soir) **ou** journal chaîné W4-C ; champs : date, article, montant, foyer (code TV masqué `XXXX-…-XXXX`), mode (bon / espèces), agent.
- **Questionnaire foyer** (S3 et S6, 10 questions, 5 min, oral par l'agent) : a écouté l'aperçu ? a loué ? pourquoi / pourquoi pas ? prix ? durée ? préférence genre/langue ; **consentement oral enregistré sur la fiche** (pas de nom : numéro de fiche).
- **Entretien producteur** (S2, S6, 20 min) : effort du Studio, relevé compris ?, part acceptable ?, continuer ?
- **Télémétrie** : seulement `playback_end`/`work_play` des TV consentantes ; le pilote **demande le consentement « statistiques »** explicitement (écran existant) et le note.
- **Piratage** : veille passive sur 2 groupes WhatsApp (avec un membre volontaire), questions aux agents, anomalies serveur (`RENTAL_REISSUE_ABUSE` etc.).
- **Fiche d'incident** (assistance) : date, foyer, problème, durée de résolution.

### 10.6 Calendrier (7 semaines) et budget (XAF, **hypothèses**)

| Semaine | Quoi |
|---|---|
| S0 | décisions D-W10-1…9 ; sélection et signature de 5 producteurs ; accord pilote (juriste : relecture express ou clause « pilote » explicite) ; recrutement de 15-25 foyers (clients existants + nouveaux) et 2 points focaux ; impression des bons (chaînes, jetons) ; lancement w10-01/04/05/08/09/10/13 |
| S1-S2 | enregistrements, Studio (propriétaire), revue et classification, aperçus ; livraison des aperçus sur les TV pilotes (clé USB des agents) ; formation agents (fiche) ; APK TV de pilote avec `WorkHub` si prêt |
| S3-S6 | **vente** (4 semaines) : S3 prix de base ; S4 variante 7 j + A/B prix ; S5 2ᵉ lot d'œuvres des producteurs volontaires ; S6 questionnaires, relevés de fin de mois, versements |
| S7 | bilan : métriques vs seuils, entretiens, décision GO / AJUSTER / STOP, rapport `docs/coordination/BILAN-PILOTE-W10.md` |

| Poste | Hypothèse XAF |
|---|---|
| Avances de pilote 5 producteurs × 25 000 | 125 000 |
| Points focaux 2 × 30 000 (4 semaines, hors commission) | 60 000 |
| Impression bons + affiches + fiches | 15 000 |
| Transport, données mobiles, appels | 30 000 |
| Matériel d'enregistrement (micro USB prêté, bonnette) | 25 000 |
| Imprévus | 15 000 |
| **Total** | **≈ 270 000** (recettes attendues si H1/H9 : 20 foyers × 1 500 × 1 mois ≈ 30 000 : le pilote **ne s'autofinance pas**, c'est une mesure) |
Coût serveur : nul (VPS existant, ≤ 1 Go de lots). TV : 0 (foyers existants). Juriste : **BLOQUÉ** (tarif inconnu).

### 10.7 Risques et éthique
- **Œuvres en clair au pilote papier** (A) : exposition des producteurs ⇒ accord explicite, œuvres non exclusives, suppression à l'échéance par l'agent ; préférer B dès que possible.
- **Attentes des producteurs** (revenus) : dire la vérité (pilote, 20 foyers) ; avance claire ; relevé même à 0.
- **Mineurs** : aucune œuvre adulte ; classification ; profils enfant ; pas de sollicitation.
- **Vie privée** : pas de nom de foyer dans les CSV ; consentement télémétrie ; questionnaires anonymes.
- **Conflits locaux** (satire visant une personne du quartier) : charte, modération, suspension rapide.
- **Fraude agent** : journal chaîné, rapprochement (W4-C/W5), bons sérialisés.
- **Horloge / TV réinitialisée** : règles existantes (suspension, réémission gratuite ≤ 3).
- **Échec technique** : repli papier A à tout moment.

## 11. Juridique et réglementaire (Cameroun) : liste de contrôle pour un juriste — **ceci n'est pas un avis juridique**

| Thème | À vérifier avec l'avocat | Impact produit |
|---|---|---|
| Droit d'auteur et droits voisins | loi camerounaise n° 2000/011 (**à vérifier**), Accord de Bangui révisé (OAPI, annexe VII) : licence non exclusive écrite, droits des interprètes (voix, musiciens), œuvres de collaboration (troupes), durée, retrait | accord producteur ; crédits ; preuve de droits |
| Gestion collective | société(s) compétente(s) pour la **musique** et pour l'**audiovisuel / arts dramatiques** (noms, périmètres, obligations d'un diffuseur **à vérifier** ; la question ne vise pas une société en particulier) : un producteur membre peut-il licencier directement ? la plateforme doit-elle déclarer/payer des redevances de diffusion ? | musique : exiger attestation ou exclure les membres au pilote ; redevance éventuelle dans la part plateforme |
| Qualification de la plateforme | hébergeur, éditeur, distributeur ? régime de responsabilité pour les contenus de tiers, procédure de notification-retrait | charte, délais, ordre `work.takedown` |
| Régulation des contenus et de la communication | loi sur la communication sociale (1990, **à vérifier**), rôle du Conseil National de la Communication, contenus politiques, satire, diffamation (code pénal) | D-W10-5 ; modération |
| Commerce électronique et consommation | loi relative au commerce électronique (2010, **à vérifier**), loi-cadre de protection du consommateur (2011, **à vérifier**) : information précontractuelle, prix TTC, reçu, droit de rétractation sur contenus numériques, durée fixe non prolongeable, remboursement | CGV œuvres (complément de `CGV-boutique.md`) |
| Fiscalité | TVA sur les ventes (taux et seuil **à vérifier**), **retenue à la source** éventuelle sur les sommes versées à des personnes physiques (régime des revenus non commerciaux / prestations : **à vérifier**), obligations déclaratives, patente ; facture/relevé conforme | relevés, parts nettes, mentions |
| Données personnelles | loi n° 2024/017 (**à vérifier** ; registre, consentement, hébergement hors Cameroun du VPS) : données des producteurs (identité, contact), des acheteurs (reçus), télémétrie | registre des traitements producteurs/œuvres ; KYC-léger sans copie au pilote |
| Mineurs | incapacité contractuelle (acheteur = titulaire adulte), image des mineurs dans les œuvres (autorisation parentale), classification | charte ; profils enfant |
| Mesures techniques et CC BY-SA | compatibilité « lots libres jamais scellés » ; vente d'un **service** autour d'un contenu libre ; attribution | w10-12, FREE-CONTENT § 4.7 |
| Espèces et mandat | point focal mandataire d'encaissement et de **paiement** (versement aux producteurs) : mandat écrit, plafonds, reçus ; lutte anti-blanchiment : seuils d'espèces (**à vérifier**) | D-W10-4 |
| Marques, noms d'artiste | usage du nom et de l'image du producteur dans la boutique | clause de l'accord |

## 12. Effort, coût, risques techniques

| Bloc | Cahiers | Effort (agent·j) |
|---|---|---|
| 10a Cœur et outils (modèle, taxonomie, Studio, registre/catalogues) | w10-01…04 | ≈ 7 |
| 10b Serveur (lots/validateur/relais, producteurs/console/relevés/retrait, télémétrie) | w10-05…07 | ≈ 7,5 |
| 10c TV (magasin, consommateur, lecture, écran, parental) | w10-08…10 | ≈ 7 |
| 10d Téléphone, Langues, outils du pilote | w10-11…14 | ≈ 6 |
| 10e Docs, juridique, campagne/CI | w10-15…17 | ≈ 3,5 |
| **Total** | 17 | **≈ 31 j** ; pilote minimal ≈ 12 j |
Exploitation : disque serveur +≤ 1 Go (lots d'œuvres + cache scellé W5, LRU), CPU négligeable ; APK TV +≈ 200 Ko ; mémoire TV : 1 œuvre ≤ 25 Mo en tas pendant la lecture (**à mesurer sur la TV de référence**, repli fichier privé).
**Risques techniques** : (1) lecture libVLC depuis une source mémoire via loopback : à prouver sur GaiaOS (repli prévu) ; (2) 25 Mo en tas sur TV 1 Go : mesure ; (3) `TvLotStore` second magasin : `LotsHub` route aujourd'hui tout vers un seul magasin (`LotsHub.kt:31`) : le routage par fonction touche `RentalApi`, `LotsHub`, `TvLotApi` (lecture seule ailleurs) ; (4) `LotSync`/`DeliveryQueue` du téléphone sont génériques mais le **quota** de 100 Mo est une constante (`LotApi.kt:32`) : second `LotStore` pour les œuvres ; (5) dépendances : W5 pour la vente en ligne, W6 pour la preuve, W4 pour la boîte v2 (le pilote minimal fonctionne avec la boîte v1 et l'outil de bureau, comme `rental_test.py` l'a prouvé sur émulateur, HANDOFF 2026-10-02 nuit) ; (6) **fichiers partagés avec W7/W8/W9/W11** (§ 13 bis) : `R/PlayerActivity.kt`, `R/LotsHub.kt`, `R/RentalHub.kt`, `R/TvService.kt`, `ReceiverServer.kt`, `S/LotsRuntime.kt`, `R/LanguesHub.kt`, `Telemetry.kt`, `routes.txt` : conflits de fusion si les vagues tournent en même temps ⇒ ordre ou réservation par le coordinateur.

## 13. Ce que W10 change dans W5 (et W4/W6) — sans éditer leurs cahiers

| Cahier / conception | Changement | Repris par |
|---|---|---|
| W5 § 3.1 articles | ajoute `loc-oeuvre-<id>\|<j>`, `loc-chaine-<p>\|<j>`, `achat-pack-langues-<code>` ; **nouveau genre de commande `PURCHASE`** (`shop_order.kind`) pour l'achat définitif Langues | w10-02 (cœur), w10-12 (serveur : `issueWithRights` avec une ligne `purchase`) |
| w5-01 (`ShopItem`, `ShopCatalog`) | `ShopItem.Work`, `ShopItem.Channel`, `ShopItem.Purchase` ; `ShopCatalog.build` reçoit aussi le catalogue des œuvres et le manifeste du `WorkStore` (budget 200 Mo, pas 10) | w10-02 (si w5-01 fusionné : extension ; sinon analyseur local puis fusion) |
| w5-06 (serveur locations) | scelle des lots `oeuvre` **jusqu'à 25 Mo** (cache LRU inchangé) ; `TRIAL_LOTS` gratuit n'inclut jamais d'œuvre (`essai\|tout` couvre Apprendre seulement : vérifier que le bouquet `tout` **exclut** `oeuvre-*` et `chaine-*` : règle dans `trial_edition.py`) | w10-04, w10-05 |
| w5-07 (commandes) | `SaleListener` additif appelé à `FULFILLED`/`REFUNDED` ; `kind=PURCHASE` | w10-06 |
| w5-09 (console boutique) | lien « Producteurs » dans `lic-nav.html` | w10-06 |
| w5-11 / w5-15 (boutique téléphone / TV) | onglets « Langues » et « Œuvres locales », page chaîne, fiche œuvre, taille contre `WorkBudget` | w10-11, w10-09 |
| w5-12 (passerelle) | stock d'œuvres séparé (`files/works/sealed/`, 300 Mo), livraison vers `/api/rental/install` inchangée, aperçus par `LotSync` (`feature=oeuvre`) | w10-11 |
| w5-16 (`ShopHub`) | `POST /api/shop/catalog` accepte aussi le catalogue des œuvres | w10-08 |
| w5-18 (parental achats) | aucun changement ; w10-10 ajoute le filtre de classification des œuvres | w10-10 |
| w5-21 (CGV) | complément « Œuvres locales » + « Pack Langues » | w10-16 |
| W4-A `LotCrypt` / `LotOpener` | cache LRU de 2 lots : le `WorkStore` utilise un cache de **1** œuvre ≤ 25 Mo | w10-08 |
| W4-B `DegradedPolicy` | `/api/oeuvres` lecture ouverte en mode réduit | w10-08 |
| W6 (`X-CB-TV-Proof`) | s'applique aux routes d'œuvres scellées (déjà « lots complets ») | — |
| `docs/MEDIA-POLICY.md` § 1 | ajout d'une ligne : « œuvres locales : fonction `oeuvre`, magasin et budget propres, hors de la règle 3 Mo » | w10-15 |

## 13 bis. Coexistence avec W7, W8, W9, W11 (conçues en parallèle le 2026-10-02 ; relues après coup, aucune n'est fusionnée)

| Vague voisine | Ce qui touche W10 | Règle d'ordre / d'alignement |
|---|---|---|
| **W9** lots génératifs (`SONNET-WAVE9-INDEX.md`) | w9-14 : consommateurs de lots média sur le volume de la bibliothèque, possède `R/LanguesHub.kt`, `R/LearnHub.kt`, `R/LanguesActivity.kt` ; w9-16 : `docs/MEDIA-POLICY.md` § 5 ; w9-09 : `tools/content-gen/build_media_lots.py` (pas `build_lots.py`) ; w9-13 : registre des licences des moteurs | w10-08 place `WorkStore` au **même emplacement** (`…/Medias/lots/oeuvre/`) ; **w10-12 après w9-14** (une ligne dans `R/LanguesHub.kt`) ; w10-15 touche MEDIA-POLICY § 1, w9-16 le § 5 (séquentiel) ; les œuvres **humaines** ne portent jamais `synthetic: true` ; une œuvre générée par IA soumise par un producteur est **refusée au pilote** (charte, w10-16) |
| **W8** transport multivoie (`SONNET-WAVE8-INDEX.md`) | w8-10 : `C/tv/ReceiverServer.kt` (§ transfert), `tools/routes/routes.txt` ; w8-15 : `C/lots/LotPush.kt`, `S/LotsRuntime.kt` ; w8-21 : `C/tv/UsbImport.kt`, `R/UsbImporter.kt` (manifeste de clé USB) | **w10-08 après w8-10** (zones distinctes de `ReceiverServer` : `/stream/` vs `/api/transfer`, mais `routes.txt` partagé) ; **w10-11 après w8-15** (`S/LotsRuntime.kt`) ; l'adoption USB des lots d'œuvres (w10-08) s'appuie sur `TvLotStore.adoptFrom`, pas sur `UsbImporter` : si w8-21 ajoute un manifeste de clé, les œuvres y figurent comme des lots (une ligne, après fusion) ; le conteneur long format (§ 5.6) utilisera `BulkLotTransport` (w8-15) |
| **W7** synchronisation plug-and-play | w7-13 : `R/TvService.kt` (chaîne `ApiExtension`), crochets `LotsHub.adopt/install/remove`, `RentalHub.sweep/install` ; w7-14 : `ReceiverServer.kt` (`/api/hello`) ; w7-05 : domaine `shop` | **w10-08 après w7-13 et w7-14** ; le manifeste des œuvres (`GET /api/oeuvres`) devient une **source** du domaine de synchronisation (`lots` ou `shop`) : une ligne dans `SyncSources` après fusion, notée au rapport de w10-08 |
| **W11** navigation allégée | w11-04 / w11-10 / w11-11 / w11-12 : `R/PlayerActivity.kt` (`homeTools`, `navTiles`, `openTile`), `R/HomeScreen.kt`, `R/TvCards.kt`, `R/ParentalHub.kt` (`filterHome`), `C/parental/ParentalModel.kt` (`KID_HOME_IDS`), `C/telemetry/Telemetry.kt` (ids) ; w11-01/07 : `S/MainActivity.kt` | **w10-09 après w11-10** : la tuile « Œuvres » s'inscrit dans `HomeGrid`/`navTiles` (id `oeuvres`) au lieu de `homeTools` ; **w10-10 après w11-04** (`filterHome`, `KID_HOME_IDS` += `oeuvres` si une œuvre est autorisée) ; **w10-07 après w11-04/w11-14** (`Telemetry.kt` ids) ; w10-11 : entrée téléphone sans W5 via le `Shell` de w11-07 s'il existe |
| **Routage des exécutants** (`ROUTAGE-AGENTS-EXECUTION-2026-10-02.md`, `routing.json`) | en-tête de 3 lignes sur chaque cahier ; `routing.json` ne couvre que W1-W6 et protect | les 17 cahiers W10 portent l'en-tête ; les lignes `routing.json` proposées sont dans `SONNET-WAVE10-INDEX.md` § Routage (le coordinateur les ajoute : fichier non possédé par W10) |

Rien de W10 ne dépend fonctionnellement de W7, W8, W9 ou W11 ; seuls les **fichiers partagés** imposent l'ordre ci-dessus (ou une fusion entre les deux). Le pilote technique minimal (w10-01, 04, 05-lots, 08, 09, 10, 13) peut partir **avant** ces vagues si le coordinateur réserve `R/PlayerActivity.kt` (une tuile), `R/LotsHub.kt`, `R/RentalHub.kt`, `R/TvService.kt` (une ligne) et `ReceiverServer.kt` (zone `/stream/`) à w10-08/09 le temps de leur exécution.

## 14. Ancrages pour les exécutants (vérifiés)

| Besoin | Où | Ce que W10 y fait |
|---|---|---|
| Magasin TV | `C/lots/TvLotStore.kt:62-69` (`maxBytes` paramètre), `R/LotsHub.kt:23` (`register`), `:31` (instanciation) | second magasin `WorkStore` + routage par fonction |
| Installation d'un lot loué | `C/lots/RentalApi.kt:22-40` | `storeFor(id.feature)` |
| Lecteur loopback | `C/tv/ReceiverServer.kt:97-114`, `:277-283`, `:1131-1165` | `/stream/oeuvre/<id>` depuis `WorkHub.open(id)` (mémoire) |
| Éditions | `C/lots/LotEditions.kt`, `EditionPolicy.kt:41-56` | aperçu = `-trial` |
| Familles | `C/lots/RentalPolicy.kt:9-19` ; `content/learn/lots.json` (registre de versions, **sans** `free`/`reserved` aujourd'hui : FREE-CONTENT § 2) | `content/oeuvres/lots.json` avec listes explicites ; Apprendre/Langues : décision du propriétaire (FREE-CONTENT § 4.1-2) |
| Catalogue signé | `C/lots/SignedBundleCatalog.kt:34-40`, `tools/trial-edition/trial_edition.py:790-800`, `B/lots/BundleCatalogController.java` | `SignedWorksCatalog`, `sign-works-catalog`, `WorksCatalogController` |
| Serveur lots | `B/lots/LotService.java:46-49, 59-65, 111` | fonctions, plafond par fonction, `WorkLotValidator` |
| Ordres | `B/orders/PolicyCatalog.java:15, 51`, `C/policy/PolicyActions.kt` | `work.takedown {work, reason, refund}` |
| Télémétrie | `B/telemetry/EventCatalog.java:81`, `C/telemetry/Telemetry.kt` | `work_play` |
| Parental | `C/parental/ParentalModel.kt:6-18`, `R/ParentalHub.kt` (`guardTile`, filtre), `docs/PARENTAL.md` | filtre et PIN des œuvres |
| Essai / réduit | `C/owner/TrialPolicy.kt:11, 31-36`, `C/owner/DegradedPolicy.kt` (W4-B), `tools/routes/routes.txt` | `/api/oeuvres` |
| Outils du propriétaire | `tools/rental-test/rental_test.py:53-74` (`emettre --location`, `lot-chiffrer`, upload, install) | `tools/producer-studio/livrer.py` |
| Médias : contrôles | `tools/media-pipeline/mp.py` (`receive` : LUFS, silence, codecs) ; `tools/content-lib/lotlib.py` (`deflated`, `read_json`) | Studio |
| Archive libre | `tools/free-content/build_free_archive.py`, `license-tags.json` | œuvres CC BY-SA étiquetées |
| Migrations | `backend/src/main/resources/db/migration/README.md` (plus haut + 1 ; V62+ libre) | `V<n>__producers.sql` |
