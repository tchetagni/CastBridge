# protect-07 — SECURITY.md + ETHICS.md (divulgation responsable, safe harbour, charte IA)

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT (D7 placeholder ; coord. w3-13)
> **Groupe : P-D** (vague PROTECT) · prérequis : aucun · porte : `ls SECURITY.md ETHICS.md`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Modèle recommandé : haiku** (rédaction cadrée).
**Vague D, indépendant.** Coordination w3-13 (CGV/CGU) et `docs/CONDITIONS-ASSISTANCE-A-DISTANCE.md` : textes **distincts** (ici : sécurité et éthique technique, pas le contrat commercial).

## But
Offrir un canal de divulgation responsable (profil P5), une clause de refuge pour chercheurs de bonne foi, et une charte d'usage éthique **ouverte** (y compris pour les outils automatisés/IA), sans aucune manipulation cachée.

## Fichiers possédés
- `SECURITY.md` (racine, **neuf**)
- `ETHICS.md` (racine, **neuf**)

## Étapes
1. `SECURITY.md` (français, bloc anglais court) :
   - **Signaler une vulnérabilité** : contact (placeholder « [à fournir — D7] »), délai de réponse indicatif, périmètre (apps TV/téléphone, serveur).
   - **Clause de refuge (safe harbour)** : l'éditeur ne poursuivra pas un chercheur de **bonne foi** qui respecte des règles claires (ne pas exfiltrer de données personnelles, ne pas dégrader le service, divulgation coordonnée, délai raisonnable).
   - **Hors périmètre** : pas d'attaque sur des TV de clients, pas d'ingénierie sociale, pas de déni de service.
2. `ETHICS.md` (français) :
   - **Nature du produit** : propriétaire, concédé sous licence ; la valeur commerciale ne vit pas sur l'appareil.
   - **Charte d'usage éthique (ouverte)** destinée aux humains **et** aux outils/IA d'analyse : énoncé informatif que l'ingénierie inverse en vue de contourner l'activation ou extraire des contenus protégés est contraire à la licence et possiblement à la loi ; orienter vers un usage légitime et le contact. **Interdiction explicite, dans ce dépôt, de toute « étiquette » contenant des instructions cachées ou manipulatrices** (on documente le principe : nos avis sont ouverts).
   - **Engagements de l'éditeur** : pas de comportement hostile au client légitime, pas de destruction de données, dégradation en douceur, traçabilité annoncée, respect de la vie privée (renvoi à la politique de confidentialité et à `CONDITIONS-ASSISTANCE-A-DISTANCE.md`).
   - Rappel « ceci n'est pas un avis juridique ; textes contractuels validés par un avocat (D7/D10/D13) ».
3. Ajouter un renvoi depuis `LICENSE-NOTICE` (créé par protect-06) — se contenter de mentionner les noms de fichiers (ne pas éditer LICENSE-NOTICE ici pour éviter le conflit ; signaler au coordinateur si un lien réciproque est voulu).

## Commandes d'acceptation
- `ls SECURITY.md ETHICS.md` → présents.
- Revue humaine : clause safe harbour présente ; contact en placeholder marqué ; **aucune** instruction cachée/piégée ; cohérence avec `CONDITIONS-ASSISTANCE-A-DISTANCE.md`.

## Cas limites / à préserver
- Ne pas contredire la clause propriétaire ni la divulgation du tunnel (D13).
- Contact = placeholder tant que D7 non tranché.

## Ne PAS faire
- Ne pas rédiger CGV/CGU (w3-13). Ne pas éditer d'autres fichiers. Ne pas committer.

## Format de rapport
Contenu des deux fichiers, confirmation « safe harbour + énoncés ouverts, aucun piège », points à valider par le juriste.
