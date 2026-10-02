> **REPORTÉ jusqu'au 31 décembre 2026 (décision du propriétaire, 2026-10-02 : tout le juridique est reporté) : ne pas lancer ce cahier avant cette date.**

# w10-16 — Brouillons juridiques : accord producteur, charte de modération, CGV œuvres et pack Langues, registre des traitements, liste de contrôle pour le juriste

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus sur échantillon · statut : BLOQUÉ partiel
> **Groupe : W10e-2** (vague W10e) · prérequis : aucun (juriste pour la validation) · porte : `grep -L 'à valider par un juriste' docs/legal/ACCORD-PRODUCTEUR.md docs/legal/CHARTE-MODERATION.md docs/legal/CGV-oeuvres-langues.md docs/legal/REGISTRE-TRAITEMENTS-producteurs.md docs/legal/CHECKLIST-JURISTE-producteurs.md`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 10e · Effort M (≈ 1,5 j) · Modèle : sonnet · Statut BLOQUÉ partiel** (validation par un juriste ; D7 contact ; D-W10-3 parts ; D-W10-5 politique ; D-W10-9 Langues). L'agent rédige des **brouillons** marqués « à valider par un juriste ; ceci n'est pas un avis juridique » ; rien n'entre dans les applications. Conception : DESIGN-W10 § 2.4, § 2.5, § 3.5, § 3.6, § 4.5, § 11 ; esquisses `docs/coordination/ACCORD-PRODUCTEUR-W10-ESQUISSE-2026-10-02.md` et `PITCH-PRODUCTEURS-W10-2026-10-02.md`. Branche `claude/sonnet-w10-16`. Rapport : `docs/agent-reports/sonnet-w10-16.md`. Peut partir le jour 1. Si `docs/legal/` existe (w3-13, w5-21) : même dossier, même `README.md` (ligne ajoutée par document).

## Objectif
Cinq documents en français, prêts pour relecture par un avocat : (1) **accord producteur** (licence non exclusive, garanties, partage, relevés, retrait, données, durée, droit applicable, clause pilote) ; (2) **charte de modération** (ce qui est refusé, classification, délais, recours) ; (3) **CGV complément** « Œuvres locales » et « Pack Langues » (location à durée fixe d'un contenu de tiers, aperçu, retrait et remboursement, contenu libre et service de confort) ; (4) **registre des traitements** producteurs / œuvres / ventes / statistiques ; (5) **liste de contrôle pour le juriste** (DESIGN § 11, une question par ligne, chaque référence de loi suivie de « à vérifier »).

## Pourquoi (preuves)
- `docs/agent-briefs/sonnet-w3-13-legal-drafts.md` (patron : mention obligatoire en tête, emplacements `[N]`, jamais de numéro d'article sans « à vérifier ») ; `docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md` (ton) ; DESIGN-W5 § 11 (CGV boutique).
- `docs/FREE-CONTENT.md` § 4.7 (mesure technique et CC BY-SA), `docs/MEDIA-PIPELINE.md` § 4 (gabarits « questions, pas réponses »).

## Fichiers possédés
Nouveaux `docs/legal/ACCORD-PRODUCTEUR.md`, `docs/legal/CHARTE-MODERATION.md`, `docs/legal/CGV-oeuvres-langues.md`, `docs/legal/REGISTRE-TRAITEMENTS-producteurs.md`, `docs/legal/CHECKLIST-JURISTE-producteurs.md` (+ `docs/legal/README.md` s'il n'existe pas ; sinon une ligne par document). **Hors zone** : tout code, tout texte des apps, `docs/coordination/**`.

## Étapes
1. `ACCORD-PRODUCTEUR.md` : développer l'esquisse (articles : parties, définitions, objet et licence, exclusivité (non), aperçu et transcodage, garanties et déclarations du producteur, modération et retrait, prix et partage `[60/15/25]`, relevés et versements (espèces/bons, seuil `[5 000]`, délais), données personnelles, durée et résiliation, responsabilité, litiges et droit applicable (Cameroun), **clause pilote** (durée 7 semaines, œuvres en clair possible en pilote papier avec accord exprès, avance `[25 000]` et son sort), signatures) ; chaque montant = emplacement `[…]`.
2. `CHARTE-MODERATION.md` : critères (DESIGN § 3.5), classification, politique (D-W10-5 : exclusion provisoire), mineurs, musique tierce et gestion collective (attestation), délais, recours du producteur, journal des décisions, transparence envers les clients (« contenu retiré »).
3. `CGV-oeuvres-langues.md` : complément de `CGV-boutique.md` (w5-21, référencé même s'il n'existe pas encore) : nature (location à durée fixe d'une œuvre d'un tiers, non prolongeable au-delà de 60 j, scellée à la TV), aperçu, classification et profils, retrait (locations en cours maintenues sauf retrait impératif avec remboursement), réclamation (code de reçu), **Langues** : contenu libre CC BY-SA 4.0 (attribution, lien archive, aucune restriction technique), pack de confort = service + médias réservés, mises à jour 12 mois, pas de remboursement après livraison sauf défaut.
4. `REGISTRE-TRAITEMENTS-producteurs.md` : tableau donnée / finalité / base / durée / destinataires / hébergement (VPS hors Cameroun : **à vérifier**) pour : identité du producteur (vue, hachée), contact, contrat, œuvres et empreintes, ventes et relevés, statistiques de lecture (consentement, agrégées), journaux d'audit ; droits des personnes ; loi 2024/017 « à vérifier ».
5. `CHECKLIST-JURISTE-producteurs.md` : reprendre DESIGN § 11 ligne par ligne + questions concrètes (≤ 25) : qualification de la plateforme, retenue à la source, TVA, gestion collective (noms à confirmer), seuils d'espèces, image des mineurs, satire, CC BY-SA et service payant, mandat des points focaux pour **verser**, conservation des œuvres retirées (24 mois).
6. `README.md` : statut de chaque document (brouillon / relu / validé), qui valide, date.

## Critères d'acceptation
```sh
ls docs/legal/*.md | wc -l                                           # ≥ 5 (+ README)
grep -L 'à valider par un juriste' docs/legal/ACCORD-PRODUCTEUR.md docs/legal/CHARTE-MODERATION.md docs/legal/CGV-oeuvres-langues.md docs/legal/REGISTRE-TRAITEMENTS-producteurs.md docs/legal/CHECKLIST-JURISTE-producteurs.md   # vide
grep -n 'loi\|Loi' docs/legal/CHECKLIST-JURISTE-producteurs.md | grep -v 'vérifier' ; echo "(toute loi citée est « à vérifier »)"
grep -rn '[0-9]\{3,\} XAF' docs/legal/ACCORD-PRODUCTEUR.md           # 0 (emplacements)
```

## Cas limites
- Producteur = troupe (plusieurs auteurs) : clause « représentant mandaté » + liste des membres en annexe.
- Producteur mineur : refusé (représentant légal signataire : question au juriste).

## À ne pas faire
Pas de commit sur les branches partagées ; aucune affirmation juridique ferme ; aucun montant réel ; pas de texte dans les apps ; pas de nom de société de gestion collective présenté comme certain ; dire « CastBridge » / « CastBridge-TV ».

## Rapport
`STATUT: BLOQUÉ` (juriste) + liste des questions ouvertes (≤ 25), emplacements à remplir par le propriétaire.
