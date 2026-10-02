# w4-06 — Documentation : enveloppe v2, clé d'installation, lots au repos, limites honnêtes

**Vague 4a · Effort S (≈ 0,5 j) · Statut PRÊT (après w4-01…w4-05 fusionnés, ou en parallèle avec les signatures du rapport de w4-01).** Conception : `docs/coordination/DESIGN-W4-ENVELOPPE-LOCATIONS.md` (source unique). Branche `claude/sonnet-w4-06`. Rapport : `docs/agent-reports/sonnet-w4-06.md`. Remplace **w1-13** (qui documentait l'acceptation de la faille : ne pas l'exécuter ; si déjà fusionné, réécrire ses paragraphes).

## Objectif
Les documents disent exactement ce que la v2 garantit et ne garantit pas, comment migrer, et comment réémettre après une réinstallation.

## Fichiers possédés
`docs/RENTAL-LOTS.md` (§ 3 réécrit, § 9 outils, § 10 port Java, § 11 limites, nouveau § 16 « Enveloppe v2 et lots au repos »), `docs/ACTIVATION-FORMAT.md` (§ 5.2 `DEVICE_INFO` v2, § 11 vecteurs v2), `docs/TRIAL-EDITION.md` (§ 6.3 demande d'appareil, § 6.5 réécrit, § 9, § 16), `docs/OWNER-CONSOLE.md` (option « Enveloppe v1 »), `docs/ACTIVATION-TOOLS.md` (§ 8 : `--enveloppe-v1`, `appareil`), `docs/LICENSE-ADMIN.md` (§ 4 : ligne `install=` tolérée), `docs/ADMIN.md` (état « clé d'installation » de la page d'administration), `docs/HANDOFF.md` (une entrée datée en § 0). **Hors zone** : tout code.

## Étapes
1. RENTAL-LOTS § 3 : dérivations v2 (copier le § 5 de la conception), format `install.key`, format `box` v2, lecture v1 bornée (`V1_BOX_SUNSET_MS`, date en clair), **flux de réémission** après réinstallation (même `period`, lots déjà scellés valables).
2. RENTAL-LOTS § 11 : remplacer « la clé du coffre ne résiste pas… » par la liste du § 2 de la conception (rootée, Keystore en repli, shell SSH, clé d'émission) ; retirer toute phrase affirmant qu'une copie du fichier d'activation suffit à ouvrir (c'était vrai en v1 : le dire au passé, avec la date de correction).
3. RENTAL-LOTS § 16 (nouveau) : lots au repos (`lot.enc`, `meta.json`, `enc = rental:|store`, cache 2 lots, coût 32 bits mesuré par w4-04, Quiz fait ou résiduel), `allowBackup=false` (w1-01).
4. ACTIVATION-FORMAT § 5.2 : `DEVICE_INFO` = `code=`, `k=`, `factor=…`, `install=x25519|<64 hex>` ; lignes inconnues ignorées ; § 11 : `rental-vectors-v2.json`.
5. TRIAL-EDITION § 6.5 : remplacer le paragraphe « clés d'enveloppe dérivées des facteurs matériels » et sa « correction d'une proposition précédente » par le nouveau modèle (clé d'installation Keystore + réémission) ; § 9 : nouvelle limite honnête ; § 16 : ligne « enveloppe v2 : corrigé (vague 4a) ».
6. HANDOFF § 0 : entrée « 2026-10-xx : enveloppe v2, clé d'installation, lots au repos ; à tester sur la TV GaiaOS : Keystore (protection affichée), réinstallation ⇒ réémission ».

## Critères d'acceptation
```sh
grep -n 'V1_BOX_SUNSET\|2027-01-01' docs/RENTAL-LOTS.md   # ≥ 1
grep -n 'install=x25519' docs/ACTIVATION-FORMAT.md docs/RENTAL-LOTS.md   # ≥ 2
grep -n 'en clair' docs/RENTAL-LOTS.md   # chaque occurrence est au passé ou décrit .in/ éphémère (relire à la main)
grep -n 'stay secret\|restent secrètes' docs/*.md   # 0 hit
```

## À ne pas faire
Pas de code ; pas de secret ; pas de promesse que le code ne tient pas (relire les rapports de w4-01…05 avant d'écrire « fait ») ; français.

## Rapport
`STATUT`, liste des sections modifiées, écarts constatés entre conception et code livré (à remonter au coordinateur).
