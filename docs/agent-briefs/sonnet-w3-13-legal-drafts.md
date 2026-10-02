> **REPORTÉ jusqu'au 31 décembre 2026 (décision du propriétaire, 2026-10-02 : tout le juridique est reporté) : ne pas lancer ce cahier avant cette date.**

# w3-13 — Brouillons juridiques et de vie privée (à faire valider par un juriste)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT partiel (brouillons marqués « à valider par un juriste »)
> **Groupe : W3-C** (vague W3) · prérequis : aucun · porte : `ls docs/legal/*.md && grep -L 'pas un avis juridique' docs/legal/*.md | wc -l`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 3 · Effort M (≈ 1,5 j) · Statut BLOQUÉ PARTIEL** — l'agent rédige des **brouillons** clairement marqués « à valider par un juriste ; ceci n'est pas un avis juridique » ; aucune mise en production de texte. Dépend des décisions D7 (contact), D10 (Langues libres), D13 (tunnel). Branche `claude/sonnet-w3-13`. Rapport : `docs/agent-reports/sonnet-w3-13.md`.

## Objectif
Quatre documents en français, prêts pour relecture : (1) CGV/CGU de vente et de location de contenus (essai unique, durée dès l'activation, suppression automatique à l'échéance, horloge fausse = suspension, perte en cas de réinitialisation, remboursements, données) ; (2) divulgation du lien de maintenance (tunnel) pour l'écran d'activation et les CGU ; (3) consentement télémétrie **par le parent** (téléphone, code parental) et registre des traitements (loi camerounaise 2024/017 : à vérifier) ; (4) crédits et licences (CC BY-SA pour Langues, archive libre, titularité du contenu IA, GPL aria2).

## Pourquoi (preuves)
- `docs/RENTAL-LOTS.md` § 5, § 11 ; `docs/TRIAL-EDITION.md` § 15 : règles commerciales sans CGV (audit Opus A9-3, audit LE-4).
- `docs/REMOTE-TUNNEL.md:107-109` : divulgation à écrire (LE-2).
- `docs/TELEMETRY.md:161-169` : loi 2024/017 « à valider » ; consentement donné sur la TV par n'importe qui ; `deviceName` (LE-3).
- `docs/LANGUES.md:440-442` vs `docs/TRIAL-EDITION.md:195-208` : contenu CC BY-SA « jamais verrouillé » vs « rien sans clé » (LE-1) ; 24 packs de droit sans avertissement (LE-5) ; `libaria2c.so` sans texte GPL (LE-6).

## Fichiers possédés
Nouveaux `docs/legal/README.md`, `docs/legal/CGV-location.md`, `docs/legal/DIVULGATION-tunnel.md`, `docs/legal/CONSENTEMENT-parent.md`, `docs/legal/CREDITS-licences.md`, `docs/TELEMETRY.md` (§ 7 : lien vers les brouillons). **Hors zone** : tout code, tout texte affiché dans les apps (w2-03/w2-15 les reprendront après validation).

## Étapes
1. `README.md` : statut de chaque document (brouillon / relu / validé), qui valide, date, et la mention obligatoire en tête de chaque fichier.
2. `CGV-location.md` : objet ; définitions (clé d'essai, clé complète, location, lot, bouquet) ; activation liée à la TV, 48 h pour installer, ré-activation gratuite même matériel, transfert (2/an) ; essai : une seule fois, 30 jours, 12 h de leçons ; location : durée fixée par le bouquet, **dès l'activation**, suppression automatique à l'échéance, suspension si l'heure de la TV est fausse, aucune restitution possible du contenu après l'échéance ; perte en cas de réinitialisation/désinstallation ; paiement (phase 0 : virement mobile money + référence ; confirmation sous N heures ouvrées) ; remboursement : avant émission de la clé seulement, sauf défaut ; données personnelles (renvoi) ; support (contact D7) ; droit applicable (Cameroun) — chaque point marqué « à confirmer ».
3. `DIVULGATION-tunnel.md` : version courte (écran, ≤ 400 caractères) et version longue (CGU) ; finalité, personnes habilitées, journalisation 90 j, droit de demander la fermeture, réservé à la version complète.
4. `CONSENTEMENT-parent.md` : texte des deux niveaux (essentiel / statistiques), recueilli **sur le téléphone du parent** après le code parental, version `2026-11` à comparer avec le texte actuel de `docs/TELEMETRY.md` ; registre des traitements (tableau : donnée, finalité, base, durée, destinataire, hébergement hors Cameroun) ; mention « à vérifier : formalités de la loi 2024/017 ».
5. `CREDITS-licences.md` : liste des composants tiers et licences (libVLC LGPL/GPL, aria2 GPLv2+, MINA Apache-2.0, NanoHTTPD BSD, polices OFL, données KanjiVG CC BY-SA 3.0…), texte de l'écran « Crédits » (TV et téléphone), politique CC BY-SA 4.0 de Langues (archive libre, attribution, pas de mesure technique sur la famille `free`), question de titularité du contenu produit par IA (à poser au juriste), avertissements droit/santé (texte repris par w3-08).

## Critères d'acceptation
```sh
ls docs/legal/*.md | wc -l                                    # 5
grep -L 'à valider par un juriste' docs/legal/*.md            # vide (chaque fichier porte la mention)
grep -n 'legal/' docs/TELEMETRY.md                            # lien présent
```

## Cas limites
- Ne jamais citer un numéro d'article de loi sans « à vérifier ».
- Pas de prix, pas de délais contractuels fermes : des emplacements `[N]`.

## À ne pas faire
Pas de commit sur les branches partagées, pas de texte dans les apps, pas d'affirmation juridique ferme, pas de secret ; dire « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT: BLOQUÉ` (validation juriste) + liste des questions ouvertes pour le juriste (≤ 15).
