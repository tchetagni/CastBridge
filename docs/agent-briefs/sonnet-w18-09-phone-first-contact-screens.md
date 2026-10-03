# w18-09 — CastBridge (téléphone) : écrans du premier contact (« Ajouter ma TV » avec l'étape « Tapez le code de la TV », chemin QR), ligne d'état Wi-Fi Direct, réglage

<!-- routage Fable 2026-10-03 -->
> **Modèle : sonnet** · escalade : aucune · statut : **ATTEND** sortie du gel W15 (après w18-08)
> **Groupe : W18b-3** (vague W18b, téléphone, écrans) · prérequis : w18-03, 08 fusionnés · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :sender:compileDebugKotlin :core:test --tests 'castbridge.core.trust.PairFlowTest' --tests 'castbridge.core.link.WdLineTest' --tests 'castbridge.core.trust.PairScreenViewTest'`
> **Jauge : ≈ 450 k jetons entrée / 22 k sortie** (effort M, ≈ 2 j) · audit Opus : non

**Vague 18b · Effort M · Modèle : sonnet · Statut ATTEND.** Conception : `DESIGN-W18-WIFI-DIRECT-PRIMAIRE-2026-10-03.md` § 2.2 (table des écrans), § 5.4, § 8 (D-W18-4, D-W18-5). Branche `claude/sonnet-w18-09`. Rapport : `docs/agent-reports/sonnet-w18-09.md`. Règle W14/W17 R4 : aucune décision d'état dans un écran (les états viennent de `PairFlow`, `BootstrapPlan`, `WdLine`, `PairScreenView`).

## Objectif
Rendre, sans rien décider : (1) `S/TvPairScreen.kt` : les étapes de `PairFlow` **plus** `WaitingPin` (w18-03) : champ 6 chiffres, clavier numérique, phrases de `LinkText` (« Tapez le code affiché sur la TV », « Il est en bas de l'écran de la TV », « Code refusé. Il reste n essais. », « Trop d'essais : nouvel essai dans n s. ») ; bouton secondaire « Scanner le QR de la TV » selon `BootstrapPlan.Choice` ; (2) `S/link/QrAddTvStep.kt` : caméra (`ML Kit` **non** : aucune dépendance nouvelle ; utiliser l'intention `com.google.zxing.client.android.SCAN` si présente **sinon** le lecteur du système (`Intent.ACTION_VIEW` du lien `castbridge://tv` scanné par l'appareil photo d'Android) ; l'app reçoit le lien par l'`intent-filter` W7 (w7-19 ; ce cahier l'ajoute s'il manque) ⇒ `WdCredentials.fromDeepLinkParams` ⇒ `WdRuntime.learn(tv, creds)` ; (3) `S/TvHome.kt` : ligne d'état de `WdLine` ; le **bouton « Wi-Fi Direct »** de la fiche (brique `claude/wd-manual-button`, à rebaser) **reste** : il lit désormais `WdLine` (même état que la ligne) et garde sa mesure de débit et ses faits ; (niveau, texte, action unique) dans la carte de la TV et dans la carte de file ; (4) `S/ConnectScreens.kt` section Connexion : « Wi-Fi Direct » : « Automatique (recommandé) / Jamais » (remplace « si seul le Bluetooth est disponible » de R-14), « Oublier le mot de passe Wi-Fi Direct de cette TV ».

## Pourquoi (preuves)
- `S/TvPairScreen.kt` (affichage des `PairStep`, `docs/BT-PLUG-AND-PLAY.md` § États) ; `C/trust/PairFlow.kt` (`WaitingPin` w18-03) ; `C/trust/PairScreenView.kt` ; `C/link/{BootstrapPlan,WdLine}.kt` ; `S/ConnectScreens.kt` (réglage R-14 « Wi-Fi Direct automatique ») ; `S/TvHome.kt` (ligne de la carte de file R-14).
- Manifeste téléphone : aucun `intent-filter` `castbridge://` (vérifié) ; W7 w7-19 non exécuté.

## Fichiers possédés
`S/TvPairScreen.kt`, `S/ConnectScreens.kt` (section Connexion), `S/TvHome.kt` (ligne d'état), nouveaux `S/link/PinEntryStep.kt`, `S/link/QrAddTvStep.kt`, manifeste téléphone (`intent-filter` `castbridge://tv` sur `MainActivity`, ≤ 8 lignes). **Hors zone** : `S/TvLink.kt`, `S/link/WdRuntime.kt` (w18-08), `S/MainActivity.kt` hors manifeste, `C/**`, `R/**`.

## Étapes
1. **Rouge (JVM, cœur existant)** : si une phrase ou un état manque dans `PairScreenView`/`WdLine`, écrire d'abord le test rouge dans `CT/trust/PairScreenViewTest` ? **Non** (hors zone) : le dire au rapport et demander à w18-02/03 ; l'écran n'invente aucun texte.
2. `PinEntryStep` : 6 cases, validation à la 6ᵉ, masquage au bout de 2 s, « Afficher le code » ; aucune vérification locale du format autre que `Pin.isValidFormat` (règle `BT-PLUG-AND-PLAY.md` § Identifiants : jamais dans un écran de jeton) ; le PIN est remis à `PairFlow.run(tv, pin)` et **oublié** (il n'est pas stocké : la confiance suffit ; `PinBook` le garde seulement pour le chemin « code » existant).
3. `QrAddTvStep` : lien reçu ⇒ `DeepLinkTv` (w7-17 si présent, sinon `WdCredentials.fromDeepLinkParams` + `id`) ⇒ `SavedTvs.add` + `WdRuntime.learn` ⇒ écran « Liaison Wi-Fi avec la TV… » ⇒ `WdLine`.
4. `TvHome` : une ligne, une action, couleurs de `SignalColors` ; jamais deux lignes pour le même fait.
5. `ConnectScreens` : réglage et « Oublier le mot de passe Wi-Fi Direct ».
6. **Sur appareil (propriétaire)** : H-21 parcours J-WD-1 réel chronométré (≤ 45 s hors saisie) ; H-22 QR ; H-23 lisibilité à bout de bras (code 40 sp).
7. **Vert** : porte ; `compileDebugKotlin`.

## Critères d'acceptation
Porte verte ; `grep -n "isValidFormat\|X-CB-Pin" android/sender/src/main/kotlin/castbridge/sender/TvPairScreen.kt android/sender/src/main/kotlin/castbridge/sender/link/PinEntryStep.kt` ne montre que l'appel de format à la saisie ; test de source W14 (lint de pureté) vert ; aucun texte français nouveau hors `C/` (grep des guillemets français dans les fichiers possédés : seulement des libellés de boutons listés au rapport).

## Cas limites
TV ancienne (sans `HELLO_HAS_PIN`) ⇒ `WaitingOwner` affiché comme avant (le champ PIN n'apparaît pas : `BootstrapPlan` le dit) ; caméra absente ⇒ bouton QR caché ; lien `castbridge://` reçu alors qu'une TV du même `id` existe ⇒ mise à jour des identifiants, pas de doublon ; code verrouillé ⇒ compte à rebours visible, champ désactivé.

## À ne pas faire
Pas de décision dans l'écran ; pas de dépendance (ZXing, ML Kit) ; ne pas stocker le PIN tapé ici ; ne pas toucher `TvLink`/`WdRuntime`.

## Rapport
`STATUT`, captures décrites (texte), libellés de boutons ajoutés, H-21…H-23, question : « Afficher le code » par défaut (recommandé : masqué).
