# w4-15 — CastBridge-TV : accepter une activation « avec ticket », mémoriser les délégations, afficher le point focal

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : audit Opus obligatoire (diff sensible) · statut : PRÊT (après w4-11, w2-01) ; refuse toute ligne rental d'agent
> **Groupe : W4c-2** (vague W4c) · prérequis : w4-11, w2-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin :core:test --tests '*Delegat*'`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : oui

**Vague 4c · Effort M (≈ 1,5 j) · Statut PRÊT (après w4-11 ; w2-01 fusionné pour la révocation persistée).** Conception : `docs/coordination/DESIGN-W4-VENTE-TERRAIN.md` § 2, § 3. Branche `claude/sonnet-w4-15`. Rapport : `docs/agent-reports/sonnet-w4-15.md`.

## Objectif
La TV accepte, par les trois canaux (Bluetooth, fichier USB, saisie/Wi-Fi), une ligne `<délégation>|<activation>` : vérifie la délégation contre ses clés compilées et sa liste de révocation, vérifie l'activation avec la clé déléguée et ses contraintes, garde la délégation pour revérifier au rechargement, et dit à l'écran « Clé délivrée par le point focal <nom> ». L'écran d'activation montre la demande d'appareil complète en QR (si une bibliothèque QR est disponible) pour que l'agent la scanne.

## Pourquoi (preuves)
- `R/ActivationCenter.kt:107` `receiver()` (`ActivationReceiver(ring, trusted, fp, Subject.TV)`), `:118-131` `accept`, `:205-210` `reload` (revérification « à la date d'installation »), `:170-203` `activations.txt` (une ligne par activation, TAB, sans LF dans le texte : la ligne ticket est une seule ligne ASCII).
- `R/RentalHub.kt:69-90` `ActivationInstallApi` (`POST /api/activation/install`), `R/OwnerBtHost.kt` (trame `ACTIVATION`), `R/ActivationActivity.kt` (code en grand ; pas de QR : `grep -rn zxing android/receiver` à vérifier).
- `C/owner/FeatureGate.kt:87-95` `ActivationReceiver.receive` (FILE : première ligne non vide ; MANUAL : `cbx1.` ou texte groupé ou compacte) : la ligne ticket commence par `cbx1.` mais contient `|` ⇒ `Envelope.decode` échoue ⇒ `MALFORMED` aujourd'hui.

## Fichiers possédés
`R/ActivationCenter.kt`, nouveau `R/DelegationStore.kt`, `R/ActivationActivity.kt`, `R/RentalHub.kt` (**`ActivationInstallApi` seulement**), `R/OwnerBtHost.kt` (si la trame doit tolérer 2 jetons : non, une ligne suffit ; vérifier `MAX_PAYLOAD = 4096` : une délégation ≈ 700 o + activation ≈ 900 o : OK ; sinon ajuster la trame **est hors zone** (cœur) : le signaler). **Hors zone** : `C/**` (w4-11 : utiliser `TicketedActivation`, `DelegatedVerifier`), `R/TvService.kt`, `R/PlayerActivity.kt`, `R/KeyBadgeOverlay.kt`, docs.

## Étapes
1. `ActivationCenter.accept` / `stage` / `scanFiles` : si `TicketedActivation.isTicketed(line)` ⇒ `DelegatedVerifier(ring, revocations (w2-01), Subject.TV).verify(line, fp, now())` ; sinon chemin existant. En cas d'acceptation : `persist(line)` tel quel (une ligne), `DelegationStore.remember(delegation)`, `lastIssuer = "Point focal : <name>"`.
2. `DelegationStore` (`delegations.txt`, `SafeFile`, une ligne par délégation acceptée, remplacée par une plus récente du même agent) ; `reload` revérifie chaque ligne stockée d'`activations.txt` « à la date d'installation » (le ticket contient la délégation : pas besoin de la retrouver ; le store sert à l'affichage et à la révocation : `revokedAgents` = clés de la liste `cbr1` persistée par w2-01 ∩ agents connus ⇒ journal).
3. Révocation : quand une liste `cbr1` arrive (w2-01), si `key=<agent kid>` : les activations **déjà installées** restent (limite honnête documentée), mais `DelegationStore.markRevoked(kid)` et toute **nouvelle** ligne de cet agent est refusée (`REVOKED_KEY` : déjà le comportement de `ActivationVerifier` avec `revocations.keys`).
4. `ActivationActivity` : sous le code d'appareil, « Clé délivrée par le point focal <name> le JJ/MM » quand `lastIssuer` existe ; en mode « Passer en production »/« Renouveler » : QR de `requestText()` si une bibliothèque QR est **déjà** disponible dans `:receiver` (sinon, texte « Le point focal lit votre TV par Bluetooth ou Wi-Fi » et rien d'autre : **ne pas ajouter de dépendance**).
5. `ActivationInstallApi` : corps ticket accepté (même `accept`), réponse `{"installed":true,"label":…,"issuer":"<name>"}`.
6. Messages de refus (français) : « Ce point focal n'est pas autorisé à délivrer ceci », « Mandat du point focal expiré : demandez-lui de le renouveler », « Point focal révoqué : contactez CastBridge » (`contact` = `BuildConfig.OWNER_CONTACT` de w2-03 si présent).

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # si SDK
grep -n 'TicketedActivation\|DelegatedVerifier' android/receiver/src/main/kotlin/castbridge/receiver/ActivationCenter.kt   # ≥ 2
grep -n 'delegations.txt' android/receiver/src/main/kotlin/castbridge/receiver/DelegationStore.kt   # 1
```
Observable (émulateur TV, ticket de test produit par l'outil de bureau de w4-12 avec une clé de test déléguée) : fichier `activation` contenant la ligne ticket ⇒ TV activée, écran « Point focal : test » ; ticket avec clé illimitée ⇒ refus ; après `cbr1` révoquant l'agent (poussé par fichier) ⇒ nouveau ticket refusé, ancien toujours installé ; redémarrage ⇒ activation toujours là.

## Cas limites
- Ligne ticket dans `Download/CastBridge/activation` avec CRLF : nettoyage existant (`:125,138`).
- Délégation valide mais TV hors fenêtre d'installation 48 h de l'activation : `WINDOW_CLOSED` (comme toute activation).
- Une TV **sans** w2-01 (révocation non persistée) : la révocation de l'agent n'agit pas hors ligne ; le dire dans le rapport.

## À ne pas faire
Pas de commit sur les branches partagées ; pas de nouvelle dépendance ; ne pas toucher `C/` ; ne pas accepter une activation d'agent sans ticket (jamais de clé d'agent dans l'anneau compilé) ; français ; « CastBridge-TV ».

## Rapport
`STATUT`, canaux testés, présence ou non d'une bibliothèque QR, interaction constatée avec w2-01.
