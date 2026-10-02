# w8-19 — Documentation W8 : `TRANSFER.md` v2, nouveau `TRANSPORT-CBX.md` (trames, chiffrement), `BT-PLUG-AND-PLAY.md` (service de vrac), `HANDOFF.md`

**Vague 8d · Effort S (≈ 1 j) · Modèle : haiku · Statut PRÊT (après fusion 8a-8c).** Branche `claude/sonnet-w8-19`. Rapport : `docs/agent-reports/sonnet-w8-19.md`.

## Objectif
Que la documentation dise exactement ce qui est livré (pas la conception) : principe, voies, limites physiques, formats, sécurité, diagnostic, mesures **réelles** si elles existent (sinon « aucune mesure »), et que `HANDOFF.md` reçoive une entrée datée.

## Sources
`docs/coordination/DESIGN-W8-TRANSPORT-MULTIVOIE.md` ; les rapports `docs/agent-reports/sonnet-w8-01..18.md` ; le code fusionné (`core/xfer/**`, `ReceiverServer.kt` § transfert, `BtBulkBridge.kt`, `sender/xfer/**`).

## Fichiers possédés
`docs/{TRANSFER,BT-PLUG-AND-PLAY,HANDOFF}.md`, nouveau `docs/TRANSPORT-CBX.md`. **Hors zone** : tout code, `docs/TEST-CAMPAIGN.md` (w8-20), `docs/ADMIN.md` (w8-11).

## Étapes
1. `TRANSFER.md` : réécrire § 2-7 à partir du code livré : voies (LAN, Direct alternatif, Bluetooth de vrac, lien USB si w8-12 l'a rendu utile), « instantané puis accéléré », garde « jamais plus lent », veille Bluetooth, ordre progressif, part équitable, plafond, réglages ; § « ce qui borne » conservé ; § mesures : **copier le tableau du banc** s'il existe dans un rapport, sinon « Aucune mesure réelle : lancer `run.sh --scenario` » ; compatibilité (tableau § 8.2 de la conception, vérifié).
2. `TRANSPORT-CBX.md` (nouveau) : trame 24 o (table), types, drapeaux, payloads de contrôle, enregistrements AEAD, dérivation des clés (honnête : pas de confidentialité persistante en v1, X25519 après W4), nonces, rejeu, choix du chiffre mesuré, mode dégradé visible, CRC32C, vecteurs (`tools/activation/xfer-vectors.json`), limites.
3. `BT-PLUG-AND-PLAY.md` : § « Service CastBridge Bulk » (UUID …0005, règle d'admission identique, chien de garde, diagnostic `bulk` dans `/api/bluetooth`), et une ligne dans le tableau des fichiers pour `BulkStream`.
4. `HANDOFF.md` : entrée datée « W8 transport multivoie » : livré, non compilé/non essayé (d'après les rapports), à valider sur matériel, décisions ouvertes (D-W8-1, D-W8-5).
5. Relecture : aucun chiffre non sourcé (chaque Mo/s renvoie à un rapport ou est marqué « ordre de grandeur, conception § 2 »).

## Critères d'acceptation
```sh
test -f docs/TRANSPORT-CBX.md && grep -c "CBXF" docs/TRANSPORT-CBX.md   # ≥ 1
grep -n "W8" docs/HANDOFF.md | head -1   # une entrée
grep -n "cb0000000005" docs/BT-PLUG-AND-PLAY.md | wc -l   # ≥ 1
grep -rn "cbk_[0-9a-f]\{8,\}" docs/TRANSFER.md docs/TRANSPORT-CBX.md | wc -l   # 0 (aucun jeton d'exemple réaliste)
```

## À ne pas faire
Ne pas documenter ce qui n'est pas fusionné comme s'il l'était ; pas de promesse de débit ; pas de secret ; ne pas toucher à la conception (`docs/coordination/`).

## Rapport
`STATUT`, liste des sections réécrites, chiffres sourcés / absents.
