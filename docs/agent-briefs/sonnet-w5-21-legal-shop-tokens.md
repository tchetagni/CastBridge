# w5-21 — Brouillons juridiques de la boutique : CGV boutique, conditions des bons de recharge, registre des traitements, indicateurs « jetons et mineurs » (pas un avis juridique)

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : BLOQUÉ partiel (juriste, D7, montants en `[…]`)
> **Groupe : W5e-1** (vague W5e) · prérequis : aucun · porte : `grep -L 'pas un avis juridique' docs/legal/*.md | wc -l`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 5e · Effort M (≈ 2 j) · Modèle : sonnet · Statut BLOQUÉ partiel (validation par un juriste ; D7 pour le nom commercial et le contact ; D-W5-1/D9-bis pour les montants : laissés en `[…]`).** Conception : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 5, § 6.1, § 6.6, § 9, § 11. Branche `claude/sonnet-w5-21`. Rapport : `docs/agent-reports/sonnet-w5-21.md`. Si `sonnet-w3-13` a été fusionné : même dossier, même ton, renvois croisés ; sinon créer `docs/legal/README.md` (« projets, à valider par un avocat de chaque marché »).

## Objectif
Quatre documents de travail en français, prêts pour relecture par un avocat (Cameroun / zone OHADA ; mention des règles des magasins d'applications pour plus tard) :
1. `docs/legal/CGV-boutique.md` : parties (éditeur = propriétaire, D7 ; client = titulaire de la licence, majeur) ; objet (location de contenus pédagogiques à **durée fixe** fixée au catalogue, ≤ 60 j, non prolongeable au-delà, renouvelable ; **jetons du Quiz** = droit d'usage prépayé de commodités de jeu, **sans valeur monétaire, non convertibles, non transférables, sans expiration** sauf mention de la grille ; clés d'activation) ; prix TTC en XAF affichés dans l'application (grille signée) ; **moyens de paiement : espèces auprès d'un point focal mandataire d'encaissement, bons de recharge** (aucun autre) ; commande, code de commande, validité 72 h, confirmation, livraison « en ligne » (téléphone ou TV connectée), accusé ; droit de rétractation / remboursement (location commencée : non sauf défaut ; paquet de jetons non entamé : 7 j ; bon de recharge : non remboursable en espèces) ; obligations techniques (clé d'installation : réinstallation = réémission gratuite ≤ 3 ; horloge ; budget de la TV) ; mineurs (contrôle parental, responsabilité du titulaire, blocage par défaut) ; interdictions (contournement, revente de contenus) ; réclamations (code de reçu, délai, contact D7) ; données (renvoi registre) ; droit applicable, règlement des litiges ; **clauses à surveiller** en encadré pour le juriste (jeux d'argent : exclusion explicite ; clauses abusives ; information précontractuelle).
2. `docs/legal/BON-DE-RECHARGE.md` : texte court à imprimer au dos (validité jusqu'au …, usage unique, à saisir dans la Boutique de CastBridge ou CastBridge-TV, article crédité, perte/vol = non remplacé, pas de remboursement en espèces, contact) + conditions longues (révocation d'un lot volé : le client de bonne foi muni d'un reçu de vente est servi).
3. `docs/legal/REGISTRE-TRAITEMENTS-boutique.md` : finalités, données (code d'appareil, identifiant de licence, clé publique d'installation, commandes, montants, moyen, serial de bon, journal de dépenses de jetons, reçus, journaux d'agents), bases, durées (5 ans comptable ; 12 mois journaux de dépenses ; purge avec l'appareil), destinataires (propriétaire, points focaux : données minimales), sécurité (chiffrement des clés de contrat, empreintes des bons, audit chaîné), droits des personnes, **ce qui ne part jamais** (profils enfants, codes parentaux).
4. `docs/legal/JETONS-MINEURS-INDICATEURS.md` : analyse de risque **non juridique** : (a) jeux de hasard / loteries (Cameroun : réglementation des jeux de divertissement, d'argent et de hasard ; critères : mise, hasard, gain) ⇒ pourquoi la conception exclut la mise redistribuée en jetons achetés et n'offre que des commodités à effet certain ; (b) monnaie virtuelle / prépayé ; (c) mineurs (incapacité, sollicitation, Google Play « Families », CNIL-like) ; (d) protection du consommateur OHADA/Cameroun (information, prix, confirmation) ; (e) magasins d'applications (facturation obligatoire des biens numériques : non applicable en distribution directe, à revoir avant Play Store) ; (f) fiscalité et facturation (reçu) ; **questions pour l'avocat** (liste numérotée).

## Fichiers possédés
Nouveaux `docs/legal/CGV-boutique.md`, `docs/legal/BON-DE-RECHARGE.md`, `docs/legal/REGISTRE-TRAITEMENTS-boutique.md`, `docs/legal/JETONS-MINEURS-INDICATEURS.md`, `docs/legal/README.md` (si absent). **Hors zone** : code, `C/shop/ShopTerms.kt` (le coordinateur y copie le texte validé), autres docs.

## Critères d'acceptation
```sh
ls docs/legal/CGV-boutique.md docs/legal/BON-DE-RECHARGE.md docs/legal/REGISTRE-TRAITEMENTS-boutique.md docs/legal/JETONS-MINEURS-INDICATEURS.md
grep -c 'Ce n.est pas un avis juridique' docs/legal/CGV-boutique.md docs/legal/JETONS-MINEURS-INDICATEURS.md   # ≥ 1 chacun
grep -rn 'mobile money\|MoMo\|Orange Money' docs/legal/CGV-boutique.md   # 0 hit (P5)
grep -rn 'XAF [1-9]' docs/legal   # 0 hit (montants en […])
```

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant, nom, numéro ; ne pas présenter le texte comme validé ; ne pas inventer de référence légale précise sans la marquer « à vérifier par le juriste » ; français.

## Rapport
`STATUT: BLOQUÉ` (juriste, D7) + livrables ; liste des questions pour l'avocat ; points où la conception devrait changer si le juriste le demande (par ex. expiration des jetons).
