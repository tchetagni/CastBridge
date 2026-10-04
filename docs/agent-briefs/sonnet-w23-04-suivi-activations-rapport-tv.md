# w23-04 — Suivi des activations : rapport d'activation de CastBridge-TV au serveur (direct, puis par le téléphone coursier W21), cadence, coupure par le serveur
<!-- routage architecte 2026-10-04 (W23) -->
> **Modèle : sonnet (4.6)** · escalade : audit Opus **échantillon** (aucun secret envoyé hors des jetons d'activation, jamais pendant une partie Internet, coupure par le serveur, gel) · statut : **ATTEND w23-01 et l'exception de gel D-W23-7** (voie coursier : attend aussi w21-01b et w21-07 fusionnés, sinon livrer la voie directe seule et le dire)
> **Groupe : W23** (ordre 2 ter) · porte : `cd android && gradle :core:test --tests 'castbridge.core.activation.ActivationReport*' :receiver:assembleDebug -PrequireActivation=true`
> **Jauge : ≈ 300 k jetons entrée / 15 k sortie** (effort S, ≈ 0,75 j) · exécutant le moins cher compétent : sonnet 4.6

**Conception** : `docs/coordination/DESIGN-W23-SUIVI-ACTIVATIONS-CONSOLE-2026-10-04.md` (§ 3.3, § 6.5, § 7.3, D-W23-4, D-W23-5, D-W23-6, D-W23-7). Branche `claude/w23-04-rapport-tv`. Rapport : `docs/agent-reports/sonnet-w23-04.md`.

## Objectif (autonome)
Que le serveur sache **quelle TV porte quelle activation**, quelles commandes du propriétaire y sont actives et quelle version de CastBridge-TV y tourne, sans écran nouveau et sans gêner le jeu.

## Fichiers possédés
- **Nouveaux** : `android/core/src/main/kotlin/castbridge/core/activation/ActivationReport.kt` (pur : construit le corps du § 3.3 depuis l'état d'activation, décide **quand** envoyer : changement, 24 h ± 1 h, ≥ 10 min depuis le dernier envoi, `next` rendu par le serveur, 0 = coupé ; jamais pendant une partie Internet ni dans les 30 s qui suivent) + `android/core/src/test/kotlin/castbridge/core/activation/ActivationReportTest.kt` ; `android/receiver/src/main/kotlin/castbridge/receiver/ActivationReporter.kt` (câblage : jeton d'appareil de `DeviceClient`, envoi `POST /api/v1/activations/report`, repli sur la file scellée W21 si présente).
- **Zone additive** : l'endroit du récepteur qui accepte une activation ou une commande (un appel `reporter.changed()`), et le planificateur existant (un réveil quotidien) : **le cahier lit `TvService`/`TvConnect` et choisit le point d'accroche le plus petit, puis le dit** ; drapeau compilé `act.report` (vrai dans les builds verrouillés, coupable par le serveur).
- **Interdit** : tout écran ; `backend/**` ; les formats d'activation ; la porte de consentement W21 (le rapport n'y passe pas, D-W23-5) ; tout envoi par la passerelle Bluetooth pendant une partie.

## Spécification
1. Corps exact du § 3.3 (≤ 4 jetons installés, le courant en premier ; `compact` seulement si la TV garde le texte de la clé compacte : **à vérifier**, sinon champ omis et le dire ; `installedAt` par empreinte si la TV connaît la date d'acceptation, sinon omis) ; jamais le jeton d'appareil dans le corps, jamais de donnée de contenu.
2. Voie directe si la TV a Internet ; par la passerelle Bluetooth : permise hors partie (≤ 4 Ko / jour, D-W23-6) ; sinon entrée `act.report` dans `SealedOutbox` (W21) remise au téléphone coursier, si ce code est fusionné.
3. Réponse : `next` (heures) appliqué ; `revocations` transmise au mécanisme existant de révocation (sans le modifier).
4. Échec réseau : réessai au prochain réveil, jamais en boucle ; au plus 1 rapport / 10 min.

## Critères d'acceptation (mutations au rapport)
- `ActivationReportTest` : changement ⇒ envoi ; même état ⇒ rien avant 24 h ; partie Internet en cours ou terminée depuis < 30 s ⇒ rien (mutation : retirer cette garde ⇒ échec) ; `next = 0` ⇒ plus aucun envoi ; corps ≤ 16 Ko avec 4 jetons de taille maximale.
- Aucun secret : le corps ne contient ni jeton d'appareil, ni code PIN, ni clé de lot (test sur un état complet de démonstration).
- Build verrouillé `-PrequireActivation=true` compilé ; APK copié dans le `Download` de la clé USB par le coordinateur (pas par ce cahier).

## À ne pas faire
- Ajouter un écran ; envoyer pendant une partie ; passer par la porte de consentement W21 ; envoyer quoi que ce soit d'autre que l'état d'activation et la version.
