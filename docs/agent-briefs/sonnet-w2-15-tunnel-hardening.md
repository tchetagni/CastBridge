# w2-15 — Tunnel SSH inverse : enrôlement réservé à la production, portée dédiée aux experts, divulgation

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (D13 = oui ; client TV reste hors cahier)
> **Groupe : W2-B** (vague W2) · prérequis : aucun · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :core:test --tests '*Tunnel*' && cd backend && tools/agents/gradle-lock.sh ./mvnw -q -o test -Dtest='Tunnel*Test'`
> **Jauge : ≈ 150 k jetons entrée / 8 k sortie** (effort S) · audit Opus : oui

**Vague 2 · Effort S (≈ 1 j) · Statut BLOQUÉ PARTIEL** — le **client TV** reste suspendu à D13 (ne pas le construire) ; les durcissements serveur/cœur ci-dessous sont faisables maintenant. **Rien n'est déployé ; le module reste `enabled=false`.** Branche `claude/sonnet-w2-15`. Rapport : `docs/agent-reports/sonnet-w2-15.md`.

## Objectif
1. Seules les activations `kind=production` peuvent enrôler un tunnel (option de configuration ; essai refusé 403 avec message).
2. La liste d'experts est vérifiée avec une **portée dédiée** `EXPERTS` (nouvelle valeur de `KeyScope`), et non `REGISTRY` (la clé qui émet les activations ne doit pas, à elle seule, ouvrir l'accès SSH distant au parc).
3. `notAfter` obligatoire et borné (≤ 180 j) pour chaque expert.
4. Texte de divulgation (français) prêt pour l'écran d'activation et les CGU, dans `docs/REMOTE-TUNNEL.md` (brouillon, à valider par un juriste).
5. Le module serveur et sa migration sont **commités** sur la branche de l'agent (ils sont aujourd'hui non suivis dans l'arbre du coordinateur : `backend/src/main/java/castbridge/server/tunnel/`, `V61__tunnel.sql`, tests).

## Pourquoi (preuves)
- `backend/src/main/java/castbridge/server/tunnel/TunnelService.java:181-186` : activations d'**essai** acceptées ; `docs/REMOTE-TUNNEL.md:45,114` le reconnaît ; tunnel ouvert après expiration jusqu'à révocation manuelle.
- `C/tunnel/ExpertsList.kt:105-111` : `verify` exige une clé **avec `KeyScope.REGISTRY`** ; or la clé de bureau porte `REGISTRY` et toutes les portées d'émission (`docs/OWNER-CONSOLE.md` § 4bis) : fuite = parc entier + accès SSH distant.
- `docs/REMOTE-TUNNEL.md:107-109` : divulgation, finalité, durée des journaux, engagement des experts « à faire relire par un juriste » ; aucun texte proposé.
- `git status` : `backend/.../tunnel/`, `V61__tunnel.sql`, `templates/admin/tunnels.html`, `backend/src/test/.../tunnel/`, `ops/`, `docs/REMOTE-TUNNEL.md` non suivis.
- Audit : SE-12, LE-2, R1-R4.

## Fichiers possédés
`backend/src/main/java/castbridge/server/tunnel/**`, `backend/src/main/resources/db/migration/V61__tunnel.sql` (**commit tel quel** ; si V61 a été pris entre-temps par une autre migration fusionnée, renuméroter au plus haut + 1 et le dire), `backend/src/main/resources/templates/admin/tunnels.html`, `backend/src/test/java/castbridge/server/tunnel/**`, `ops/tunnel/**`, `C/tunnel/ExpertsList.kt`, `C/owner/Keys.kt` (**ajout** `KeyScope.EXPERTS` seulement), `tools/activation-desktop/src/main/kotlin/castbridge/desktop/ExpertsStore.kt`, `android/core/src/test/kotlin/castbridge/core/tunnel/**`, `docs/REMOTE-TUNNEL.md`. **Hors zone** : client TV (D13), `TvSshServer` (w2-02), `backend/licenses/**`.

## Étapes
1. `KeyScope.EXPERTS` ajouté (cœur) ; `ExpertsList.verify` exige `EXPERTS` ; `ExpertsStore` (bureau) signe avec une clé qui a cette portée (la ligne publique `kid=… scopes=…` l'inclura **si** le propriétaire la génère ; aucune clé n'est créée ici) ; `notAfter > 0` et `≤ generatedAt + 180 j` exigés ; tests cœur mis à jour (`ExpertsListTest`).
2. Serveur : `TunnelProperties.allowTrial=false` ; `enroll` refuse `kind != PRODUCTION` (403 « Le tunnel de maintenance est réservé aux TV en version complète ») ; `TrustedKeys` du serveur : la liste d'experts exige `EXPERTS` ; tâche de sondage : révoquer automatiquement un tunnel dont l'activation est expirée **si** `castbridge.tunnel.auto-revoke-expired=true` (défaut `true`) — utiliser `EnvelopeVerifier` sur l'activation stockée (hachée ? si seul le haché est stocké, stocker `expiresAt`/`usage` à l'enrôlement) ; tests MockMvc.
3. `docs/REMOTE-TUNNEL.md` : § 4 complété par un **brouillon** de divulgation : « CastBridge-TV maintient, lorsqu'elle a Internet, une liaison de maintenance sortante vers le serveur de CastBridge. Elle permet au vendeur, et aux techniciens qu'il a désignés par écrit, d'accéder à la TV pour le diagnostic et l'assistance. Aucun contenu personnel n'est consulté sans votre accord. Vous pouvez demander la fermeture de cette liaison à tout moment. » + tableau finalité / durée des journaux (proposer 90 j) / personnes habilitées / engagement écrit des experts ; marqué « à valider par un juriste ».
4. Vérifier que `V61__tunnel.sql` ne touche aucune table existante.

## Critères d'acceptation
```sh
cd android && gradle --offline :core:test --tests 'castbridge.core.tunnel.*' --tests 'castbridge.core.owner.*'   # vert
cd backend && ./mvnw -q -o test -Dtest='Tunnel*'                                                              # vert (essai → 403, expiré → révoqué)
grep -n 'EXPERTS' android/core/src/main/kotlin/castbridge/core/tunnel/ExpertsList.kt android/core/src/main/kotlin/castbridge/core/owner/Keys.kt   # présent
git ls-files backend/src/main/java/castbridge/server/tunnel | wc -l                                          # > 0 sur la branche de l'agent
```

## Cas limites
- Une liste d'experts déjà signée avec `REGISTRY` (aucune en production) devient invalide : acceptable, documenté.
- Les vecteurs d'activation ne changent pas (nouvelle portée = nouvelle valeur d'énumération, ordre de sérialisation à vérifier dans `Keys.kt` : si les portées sont sérialisées par nom, aucun impact ; si par ordinal, **ajouter en fin**).

## À ne pas faire
Pas de déploiement, pas de commit sur les branches partagées, pas de secret, pas de client TV, pas de modification du module licences ; textes en français.

## Rapport
`STATUT: BLOQUÉ` (client TV, D13) + ce qui est livré ; numéro de migration ; texte de divulgation proposé.
