# w5-23 — Fiche du point focal v2 et `VENTE-TERRAIN.md` : clés, bons de recharge (stock, serial, vente, perte), confirmation de commandes en espèces, versements ; plus de location

**Vague 5e · Effort S (≈ 1 j) · Modèle : haiku · Statut BLOQUÉ partiel (D7 nom/contact, D-W5-2 dénominations, D9-bis montants : laissés en `[…]`).** Conception : `docs/coordination/DESIGN-W5-BOUTIQUE-LOCATIONS-JETONS.md` § 0 (P1, P5), § 5 ; `SONNET-WAVE5-INDEX.md` « Changements aux cahiers w4 » (w4-18). Branche `claude/sonnet-w5-23`. Rapport : `docs/agent-reports/sonnet-w5-23.md`. Dépend de w4-18 (fichiers existants) et du rapport de w5-13 (écrans réels).

## Objectif
`docs/FICHE-POINT-FOCAL.md` (une page imprimable, phrases courtes, français simple) : ce que je vends (clés d'essai gratuites pour la démonstration ; clés de production de [30/90/365] jours ; **bons de recharge** : « jetons du Quiz » et « location de leçons » ; je **confirme** les commandes passées dans la Boutique) ; ce que je **ne** fais **pas** (je ne crée pas de location moi-même, je ne touche pas aux lots, je ne promets pas de remboursement) ; **mon stock de bons** (je reçois des cartes avec un serial visible ; je vends, j'inscris la vente dans l'app (serial) ; je remets la carte ; perte ou vol : je le signale dans l'app le jour même) ; **confirmer une commande** (le client me montre `CB-XXXX-XX` et le montant ; je vérifie dans l'app si j'ai du réseau ; j'encaisse ; je confirme ; sans réseau : la confirmation part quand je synchronise ; je préviens le client) ; **reçus** (je partage toujours le reçu) ; **argent** (ce que je dois = ce que j'ai encaissé − mes versements confirmés ; échéance [D7]) ; **problèmes fréquents** (bon refusé « déjà utilisé » : vérifier le serial sur le reçu de vente ; commande « expirée » : le client recommande ; TV « clé terminée » : vendre une clé ou un bon de clé) ; contact [D7]. `docs/VENTE-TERRAIN.md` : mise à jour des sections « rôles », « flux de vente », « journal » (items `bon|…`, `commande|…`), « serveur » (anomalies bons/commandes), suppression des passages sur le scellement et le maître.

## Fichiers possédés
`docs/FICHE-POINT-FOCAL.md`, `docs/VENTE-TERRAIN.md`. **Hors zone** : tout le reste (SHOP.md : w5-20).

## Critères d'acceptation
```sh
grep -rn 'maître\|sceller\|scellement' docs/FICHE-POINT-FOCAL.md   # 0 hit
grep -c 'bon' docs/FICHE-POINT-FOCAL.md   # ≥ 5
grep -rn 'XAF [1-9]\|WhatsApp : [0-9]' docs/FICHE-POINT-FOCAL.md docs/VENTE-TERRAIN.md   # 0 hit (D7, D9-bis en […])
grep -n 'location' docs/VENTE-TERRAIN.md | grep -vi 'ne .*plus\|boutique\|serveur' | head   # aucune phrase ne dit que l'agent loue
```

## À ne pas faire
Pas de commit sur les branches partagées ; aucun montant, nom, numéro ; ne pas décrire d'écran qui n'existe pas (lire le rapport de w5-13) ; français simple.

## Rapport
`STATUT: BLOQUÉ` (D7, D-W5-2) + livrables ; phrases à valider par le propriétaire.
