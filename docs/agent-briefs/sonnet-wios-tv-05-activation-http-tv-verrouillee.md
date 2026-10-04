# wios-tv-05 — TV (v2, sur décision du propriétaire) : recevoir une activation `cbx1` par HTTP en état verrouillé, pour le mode porteur d'un iPhone
<!-- routage architecte 2026-10-04 (vague iOS, v2) -->
> **Modèle : sonnet** · escalade : audit Opus **obligatoire** (surface de l'état verrouillé, liste blanche testée) · statut : **BLOQUÉ : décision du propriétaire** (élargir la surface verrouillée de la TV au réseau local)
> **Groupe : WIOS-TV v2** · porte : `tools/agents/gradle-lock.sh gradle --offline :core:test --tests 'castbridge.core.owner.*' --tests '*Locked*'`
> **Jauge : ≈ 250 k jetons entrée / 12 k sortie** (effort S, ≈ 0,5 j) · exécutant le moins cher compétent : sonnet

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 2 F24, § 3.3, D-IOS-2). Règles : `docs/TRIAL-EDITION.md` (état verrouillé, `Feature.lockedAllowed`, test `theLockedSurfaceIsExactlyTheActivationSurface`), `docs/ACTIVATION-FORMAT.md` § 3. Branche `claude/wios-tv-05-activation-http`. Rapport : `docs/agent-reports/sonnet-wios-tv-05.md`.

## Objectif (autonome)
Le mode porteur Android remet une activation à une TV **verrouillée** par la trame Bluetooth `ACTIVATION`. Un iPhone ne peut pas. Si le propriétaire l'accepte, la TV verrouillée accepte **une seule** route HTTP de plus : `POST /api/activation/install` (corps = jeton `cbx1` complet), sur une adresse du réseau local ou du groupe Wi-Fi Direct, **sans** PIN (la TV verrouillée n'a rien d'autre à protéger que sa propre activation ; le jeton est signé et lié à l'appareil), bornée.

## Fichiers possédés
- **Zones** : `C/owner/FeatureGate.kt` (ajout **volontaire** de la route à la liste verrouillée) ; le test `theLockedSurfaceIsExactlyTheActivationSurface` **mis à jour volontairement** avec un commentaire qui cite cette conception ; `C/tv/ReceiverServer.kt` (si le garde de route doit connaître l'état verrouillé : ≤ 5 lignes) ; nouveau test `CT/owner/LockedHttpActivationTest.kt`.
- **Interdit** : tout autre format, toute autre route, `R/`, `S/`, `ios/**`.

## Spécification
1. Seule `POST /api/activation/install` est ajoutée ; corps ≤ 8 Kio ; source = IP privée ou 192.168.49.0/24, sinon 403 ; 10 essais / 10 min par IP, puis 429 ; refus du vérificateur (`ActivationVerifier` existant) ⇒ code de refus existant, aucune information supplémentaire.
2. Un jeton d'activation **de téléphone** ou pour une autre TV ⇒ `WRONG_SUBJECT` (comportement existant).
3. Aucun autre accès en état verrouillé (lecteur, bibliothèque, transfert, SSH restent fermés) : test de surface.

## Critères d'acceptation
- `LockedHttpActivationTest` : TV verrouillée + jeton valide de test (`tools/activation/test-vectors.json`) ⇒ activée ; jeton altéré ⇒ refus ; 11e essai ⇒ 429 ; IP publique ⇒ 403 ; toute autre route ⇒ inchangée (verrouillée).
- Le test de surface échoue si l'on ajoute une deuxième route (mutation au rapport).

## À ne pas faire
- Exécuter ce cahier sans la décision écrite du propriétaire ; ouvrir une route d'administration ; accepter une clé compacte par HTTP (elle se saisit sur la TV).
