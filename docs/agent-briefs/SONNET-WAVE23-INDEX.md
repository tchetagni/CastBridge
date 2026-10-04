# Vague 23 — Suivi des codes d'activation (essai et production) : inventaire serveur, émission hors ligne déclarée, synchronisation avec la console du propriétaire, historique immuable

<!-- routage architecte 2026-10-04 -->

**Source** : `docs/coordination/DESIGN-W23-SUIVI-ACTIVATIONS-CONSOLE-2026-10-04.md`. Branche de référence `integration/agents` (HEAD `3484fbb1`). Exécution sur ordre du coordinateur seulement.

**Demande du propriétaire (2026-10-04, verbatim)** : « traque et synchronise tous les jetons activés avec la console propriétaire et garde l'historique de toutes les informations utiles dans le serveur » ; précision : « je parle des codes d'activation d'essai et de production ». **Aucun suivi de soldes NDEM/MBOKO** (lien avec W22 : conception § 11).

**Pourquoi « W23 » et pas « w21b »** : le sujet n'est ni la télémétrie (W21) ni le grand livre (W22) ; des cahiers `w21-01b`, `w21-02b`, `w21-03b` existent déjà ; ce chantier **n'attend pas** le grand livre (il ne dépend que des tables du module des licences V50-V52, des appareils V2 et de la sécurité d'administration, fusionnées).

**Règles de la vague** : **A1** le serveur fait foi ; la console lit, les outils déclarent, les TV rapportent ; **A2** jamais un jeton `cbx1`, une clé compacte, un défi complet, un jeton d'appareil ou d'API en clair dans une table, un journal ou un export (empreinte SHA-256 + étiquette de 8 hex) ; **A3** historique immuable chaîné, jamais purgé (archive froide après 24 mois sur ordre du propriétaire) ; **A4** toute lecture d'administration auditée ; **A5** alertes douces, jamais de révocation automatique ; **A6** aucune écriture dans les tables `lic_*` ; **A7** aucune permission ajoutée à l'application propriétaire tant que D-W23-3 = B ; **A8** migration « plus haut numéro existant + 1 » AU MOMENT DE LA FUSION (rédigée V65 ; aucun numéro réservé : V62 = grand livre W22 aujourd'hui) ; **A9** audit Opus sur le contrôle d'accès et le journal d'audit.

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Jauge (k entrée / sortie) | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|---|
| w23-01 | `sonnet-w23-01-suivi-activations-api-serveur-v65.md` | module `activations`, V65, journal `cbx1 type=journal`, rapport TV, réconciliation, alertes, API en lecture, flux de changements, audit des lectures, instantanés, points de contrôle, export, effacement | serveur | L | sonnet 4.6 | **oui** | 600 / 30 | **PRÊT** (ordre 1) | — |
| w23-02 | `sonnet-w23-02-suivi-activations-pages-admin.md` | pages `/admin/activations/**` adaptées au téléphone, interrogation longue | hors gel (non distribué, D-W23-7) | M | sonnet 4.6 | **oui** | 400 / 25 | ATTEND 01 (ordre 2) | 01 |
| w23-03 | `sonnet-w23-03-suivi-activations-telephone-proprietaire.md` | journal signé des outils (cœur, application propriétaire, bureau), export fichier/partage/QR, « Ouvrir le suivi » ; option en ligne en annexe | hors gel (application non publiée, D-W23-7) ; `ownerlib` : crochet neutre | M (+0,5 j si option A) | sonnet 4.6 | **oui** | 400 / 20 | ATTEND 01 (vecteurs) (ordre 2 bis) | 01 |
| w23-04 | `sonnet-w23-04-suivi-activations-rapport-tv.md` | rapport d'activation de la TV (direct, puis coursier W21) | **exception de gel** demandée (aucun écran, D-W23-7) | S | sonnet 4.6 | échantillon | 300 / 15 | ATTEND 01 + D-W23-7 (ordre 2 ter) ; coursier : w21-01b, w21-07 | 01 |

### W23-B — notification des activations, enregistrement licence/poste, portefeuille à l'activation, voie descendante (ajout du 2026-10-04)
Source : `docs/coordination/DESIGN-W23B-NOTIFICATION-ACTIVATION-LICENCE-PORTEFEUILLE-2026-10-04.md`. Règles en plus de A1-A9 : **B1** seul le module des licences écrit `lic_*` (`ReportedActivationRegistrar`) ; **B2** préproduction identique à la production (aucun drapeau de test) ; **B3** aucun montant calculé par la TV ; **B4** liste blanche fermée pour tout objet descendant, interrupteurs serveur et TV, transparence écrite.

| id | Cahier | Objet | Gel | Effort | Modèle | Audit Opus | Statut | Dépend de |
|---|---|---|---|---|---|---|---|---|
| w23-05 | `sonnet-w23-05-enregistrement-licence-par-notification-et-rattrapage.md` | avis vérifié ⇒ licence + poste ; correctif `end_at` ; identité de portefeuille à la notification ; rattrapage borné ; w23-05a (appel depuis `wallet/sync`) | serveur | L | sonnet 4.6 | **oui** | ATTEND w23-01 corrigé et fusionné | 01 |
| w23-06 | `sonnet-w23-06-avis-activation-scelle-recu-signe.md` | types `actnotice`, `receipt`, boîte à clé publique, route `/api/v1/activations/relay`, rapport v2, vecteurs | serveur + cœur | M | sonnet 4.6 | **oui** | ATTEND 05 (faux possibles) | 05 |
| w23-04 | (amendé) | avis signé, voie directe v2, file `act`, reçus, états | **exception de gel** | S → M | sonnet 4.6 | **oui** | ATTEND 06 | 06 |
| w21-07 | (amendé) | relais `act` montant, budget, remise des objets descendants à `OrdersRuntime` | — | +0,3 j | sonnet | échantillon | ATTEND w21-01b, 06 | 06 |
| w23-07 | `sonnet-w23-07-voie-descendante-relais-par-historique.md` | clé des ordres distincte, relais par historique, objets descendants, accusés signés, actions nouvelles, interrupteur | serveur + téléphone | M | sonnet 4.6 | **oui** | ATTEND 06 | 06 |
| w23-08 | `sonnet-w23-08-tv-ordres-accuses-portefeuille-attente.md` | câblage `PolicyHub`, accusés signés, `downlink.pause`, états du portefeuille | exception (D-W22-13 + insigne) | M | sonnet 4.6 | **oui** | ATTEND 07, w22-07 | 07 |
| w23-09 | `haiku-w23-09-textes-transparence-relais.md` | lignes de transparence versionnées TV et téléphone | textes | S | haiku | — | ATTEND D-W23B-12, 07 | 07 |

**Ordre W23-B** : 05 → 06 → (04 ∥ w21-07 ∥ 07) → 08 → 09 ; **≈ 13-17 $**, ≈ 9 agent·jours, ≈ 6-7 jours ouvrés (estimé, prix non vérifiés). **Sans code, tout de suite** : procédure provisoire du § 8 de la conception (licences des TV actuelles par l'API d'administration). Décisions D-W23B-1 à 13 : § 12 de la conception.

**Chemin critique** : `w23-01` → `w23-02 ∥ w23-03 ∥ w23-04` → actes du propriétaire (module des licences allumé, clés publiques des outils dans `CASTBRIDGE_LICENSES_TRUSTED_KEYS`, `act-ref.key` créée, `server-1.x` avec `CASTBRIDGE_ACTIVATIONS_ENABLED=1`, premier import du registre et des journaux, APK TV verrouillés copiés dans le `Download` de la clé USB) ⇒ **≈ 4 jours ouvrés** (estimé).

**Première valeur** : après w23-01 + w23-02 (≈ 4 j), le propriétaire voit au serveur tout ce que le registre, les émissions du serveur et les journaux téléversés à la main disent, avec historique et alertes ; w23-03 rend les journaux du téléphone et du bureau complets (clés compactes, commandes, remises) ; w23-04 ajoute le constat « sur quelle TV ».

**Coût** (prix des index précédents, **non vérifiés** : sonnet 2/10 $/M, opus 4/20 $/M) : sonnet 1 × L 1,6 $ + 2 × M 2,0 $ + 1 × S 0,5 $ ≈ 4,1 $ ; audits Opus 3 × 0,8 $ + 1 échantillon 0,4 $ ≈ 2,8 $ ; reprises 15 % ≈ 1,0 $ ⇒ **≈ 8 $**, ≈ **6,5 agent·jours**. Option A du téléphone : ≈ +0,5 $.

## Décisions du propriétaire
**DÉCIDÉ** : D-W23-1 (« jetons activés » = codes d'activation d'essai et de production, correction du 2026-10-04).
**OUVERTES** (recommandations au § 10 de la conception) : D-W23-2 (journal signé des outils : oui), D-W23-3 (téléphone sans réseau maintenant, en ligne plus tard), D-W23-4 (rapport des TV : oui), D-W23-5 (hors consentement des statistiques : oui), D-W23-6 (passerelle permise hors partie : oui), D-W23-7 (gel : pas pour l'administration ni l'application propriétaire ; exception pour le rapport TV), D-W23-8 (conservation), D-W23-9 (point de contrôle signé), D-W23-10 (pas de règle des deux personnes), D-W23-11 (alertes douces), D-W23-12 (lectures auditées), D-W23-13 (points focaux au niveau 2), D-W23-14 (délais). **Aucune ne bloque w23-01** ; D-W23-7 bloque w23-04.

## Faits vérifiés le 2026-10-04
- Le serveur garde déjà émissions (`lic_issuance`, `source SERVER|IMPORT`, empreinte du jeton seulement), registre signé (`lic_event`), conflits, révocations, journal d'audit chaîné (`lic_audit`) : `V51`, `V52`, `B/licenses/AuditLog.java`.
- `GET /api/v1/entitlements/me` existe (éteinte par défaut) ; **aucun appelant** dans `android/` ni `tools/`.
- L'application « CastBridge Propriétaire » (`castbridge.owner`) n'a **aucune permission** ; `ownerlib` est embarqué dans l'APK grand public de CastBridge (entrée super-administrateur éteinte sans `-Pcastbridge.superAdmin=true`).
- `LicensedIssuer` écrit un événement de registre `issue` pour toute émission, essai compris ; la clé compacte du bureau n'en écrit pas ; les commandes du propriétaire ne sont que dans `AuditChain` (local).
- Fenêtre d'installation d'une activation : **48 h** (`docs/ACTIVATION-FORMAT.md` § 3.2) ; « jusqu'à 1 an » = phase hors ligne.
