# w6-20 — Documentation W6 : PARENTAL (détenteurs, v2, Wi-Fi, confidentialité), nouveau PHONE-GATE (matrice, messages, preuve), ACTIVATION-FORMAT (type `proof`), OWNER-CONSOLE (session, perte), TRIAL-EDITION § 14 amendé, HANDOFF

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après 6a-6d)
> **Groupe : W6e-1** (vague W6e) · prérequis : w6-01, w6-02, w6-03, w6-04, w6-05, w6-06, w6-07, w6-08, w6-09, w6-10, w6-11, w6-12, w6-13, w6-14, w6-15, w6-16, w6-17, w6-18, w6-19 · porte : `ls docs/PHONE-GATE.md`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non
> **Amendement (architecte, 2026-10-02)** : lire d'abord `docs/coordination/ADDENDUM-W6-PREUVE-TV-LIEN-CLE-2026-10-02.md` (entier). Il **prévaut**. À documenter en plus : ACTIVATION-FORMAT § 3.3 (ligne `install=<kid>` en position 4, facultative, émise seulement si la demande porte `sign=` ; TV anciennes), § 3.5 `proof` (liaison avant épinglage, `seq` strict, contrat du `NonceBook`, `compact=1`), § 4.1 (« la clé compacte ne prouve jamais la production » ; retrait de la compacte de production au coucher si P-L2 est acceptée), § 5.2 (`sign=`), § 11 (vecteurs additifs) ; PHONE-GATE (confiance liée / non liée, cache 3 j / 14 j, transition et coucher `BIND_SUNSET_MS` 2027-01-01, trois messages ajoutés) ; OWNER-CONSOLE (réémission gratuite après réinstallation ou clé compacte, article `reemission-cle|0`, déléguée aux points focaux) ; API-SERVER (deux formes de `X-CB-TV-Proof`, anomalies). Risques résiduels : recopier § 8 de l'addendum tels quels.

**Vague 6e · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après fusion de 6a-6d ; lire tous les rapports `docs/agent-reports/sonnet-w6-*.md`).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` (entier). Branche `claude/sonnet-w6-20`. Rapport : `docs/agent-reports/sonnet-w6-20.md`.

## Objectif
Mettre la documentation au niveau du code fusionné, en français, sans secret : (1) `docs/PARENTAL.md` : « téléphone détenteur » (trois conditions, consentement, révocation, indicateur), § « Ce que la TV rapporte / jamais » (copier `ParentalPrivacy`), format v2 (schéma du rapport de w6-07), signature Ed25519 + HMAC, magasin chiffré, rétentions, Wi-Fi local (routes `holder/*`), CBTP v2, **retirer** la limite « Bluetooth seulement » et la phrase « le HTTP ne sait pas quel téléphone », sections téléphone (§ 2.8), limites honnêtes ; (2) nouveau `docs/PHONE-GATE.md` : règle, liste minimale, **matrice** § 3.7 (recopiée depuis `PhoneMatrixTest.EXPECTED_MATRIX`), machine d'états et **catalogue** § 3.8 (recopié depuis `PhoneGateTexts`), preuve, cache, interrupteur `REQUIRE_TV_PROOF`, grâce, procédure d'allumage, plusieurs TV, enfant, interactions (boutique, lots, tunnel, protect) ; (3) `docs/ACTIVATION-FORMAT.md` : § 3.5 type `proof` (champs exacts du rapport de w6-03), vecteurs `proof-vectors.json` dans § 11 ; (4) `docs/OWNER-CONSOLE.md` : session super (§ 4.2-4.4), contrôle de publication, procédure de perte (§ 4.5, texte de w6-19) ; (5) `docs/TRIAL-EDITION.md` § 14 : paragraphe daté « 2026-10-02 : décision remplacée par W6 (mode minimal du téléphone) » ; (6) `docs/API-SERVER.md` : `X-CB-TV-Proof` (w6-09) ; `docs/TELEMETRY.md` : ligne « aucune donnée parentale » ; `docs/REMOTE-TUNNEL-TV.md` : routes parentales interdites ; (7) `docs/HANDOFF.md` : état W6, ce qui reste à vérifier sur la TV de référence ; (8) `docs/COORDINATION.md` : rien sauf si une commande change.

## Pourquoi (preuves)
- Mémoire du propriétaire : « Always write a handoff » ; `docs/PARENTAL.md` (sections à amender : « Rapports envoyés… », « Limites », « Onglet Parental ») ; `docs/TRIAL-EDITION.md:219-220` (§ 14) ; `docs/OWNER-CONSOLE.md:113-117` ; `docs/ACTIVATION-FORMAT.md:75-101` (§ 3.3 : modèle de rédaction d'un type).

## Fichiers possédés
`docs/{PARENTAL,ACTIVATION-FORMAT,OWNER-CONSOLE,TRIAL-EDITION,API-SERVER,TELEMETRY,REMOTE-TUNNEL-TV,HANDOFF}.md`, nouveau `docs/PHONE-GATE.md`. **Hors zone** : `docs/legal/**` (w6-21), `docs/TEST-CAMPAIGN.md` (w6-22), `docs/COORDINATION.md` (w6-23), le code.

## Étapes
1. Lire les 19 rapports ; relever les écarts entre conception et code (les documenter comme tels, ne pas « corriger » la conception).
2. Rédiger ; tableaux copiés depuis le code (matrice, catalogue, listes `COLLECTED`/`NEVER`) : indiquer la source.
3. HANDOFF : versions, interrupteurs (`REQUIRE_TV_PROOF` éteint), ce que le coordinateur doit tester sur la TV de référence (32 bits) et sur le téléphone.

## Critères d'acceptation
```sh
test -f docs/PHONE-GATE.md && grep -c 'M-' docs/PHONE-GATE.md   # ≥ 20
grep -n 'Bluetooth uniquement\|ne sait pas, d.après le jeton' docs/PARENTAL.md   # 0 hit (limite retirée)
grep -n 'type `proof`\|### 3.5' docs/ACTIVATION-FORMAT.md   # ≥ 1
grep -n 'W6' docs/HANDOFF.md docs/TRIAL-EDITION.md   # ≥ 2
grep -rn 'BEGIN PRIVATE\|\$2a\$' docs/ | wc -l   # 0 (aucun secret)
```

## Cas limites
Un cahier non fusionné : documenter « prévu (w6-NN, non fusionné) » plutôt que de décrire une fonction absente.

## À ne pas faire
Pas de texte juridique ici (w6-21) ; pas d'invention de comportement ; pas de capture recopiée depuis un journal.

## Rapport
`STATUT`, liste des fichiers touchés, écarts conception/code relevés.
