# w6-14 — CastBridge-TV : écran de consentement du détenteur, page « Téléphones des parents », indicateur permanent (adulte / enfant), « À propos : contrôle parental et rapports »

<!-- routage Fable 2026-10-02 -->
> **Modèle : sonnet** · escalade : opus audit si diff sécurité/crypto/argent · statut : PRÊT (après w6-06)
> **Groupe : W6c-1** (vague W6c) · prérequis : w6-06 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:compileDebugKotlin`
> **Jauge : ≈ 400 k jetons entrée / 20 k sortie** (effort M) · audit Opus : non

**Vague 6c · Effort M (≈ 2 j) · Modèle : sonnet · Statut PRÊT (après w6-06 fusionné ; w6-12 fournit `ActivationCenter.identityLine()` à afficher si disponible).** Conception : `DESIGN-W6-PARENTAL-PHONE-GATE.md` § 2.1, § 2.6, § 6. Branche `claude/sonnet-w6-14`. Rapport : `docs/agent-reports/sonnet-w6-14.md`.

## Objectif
(1) `HolderConsentActivity` (D-pad, `exported=false`, plein écran) : texte `ParentalPrivacy.consentText(phoneName)`, « ce qui est collecté / jamais », boutons **« J'ai informé le foyer, continuer »** (OK) et **« Annuler »** ; appelé quand un détenteur passe `PENDING` (désignation depuis la TV **ou** depuis le téléphone : la TV l'affiche au prochain `onResume` de l'accueil, et le rappelle une fois par jour tant que c'est en attente) ; valide par `Holders.consentGiven` ; (2) `ParentalActivity` : la page « Rapports vers le téléphone du parent » devient **« Téléphones des parents »** (liste : nom, état `en attente / actif`, dernier retrait, « Retirer » avec code parental, « Valider le consentement » pour un `PENDING`), options `shareTitles` et `lateUseAlert` par profil ; (3) **indicateur permanent** : `KeyBadgeOverlay` ajoute la ligne `ParentalPrivacy.adultIndicator(n)` quand `n > 0`, remplacée par `kidIndicator` en mode enfant ; accueil : icône près de la tuile « Contrôle parental » (`HomeScreen`/`PlayerActivity`) avec la même phrase ; option « réduire en icône » (jamais « masquer ») ; (4) « À propos » (fichier à localiser : `grep -rn "À propos" R/`) : section « Contrôle parental et rapports » (`ParentalPrivacy.aboutText(holders)` + « Les rapports ne quittent pas la maison » + ligne d'identité de w6-12 si présente).

## Pourquoi (preuves)
- `R/ParentalActivity.kt:152-187` (`rootPage`, `:179` ligne « Rapports vers le téléphone du parent »), `:116-134` (pages intro/gate : réutiliser `Page`/`row`/`section`), `R/ParentalUi.kt` (clavier, dialogues) ; `R/KeyBadgeOverlay.kt` (bandeau permanent, w2-04 : lisible à 3 m) ; `R/PlayerActivity.kt:539-541` (`homeTools`), `R/HomeScreen.kt:199` (tuiles) ; `R/ParentalHub.kt:268` (`kidHomeActive`), `:286` (`tileStatus`) ; `C/parental/Holders.kt`, `C/parental/ParentalPrivacy.kt` (w6-06) ; manifeste TV (`ParentalActivity` `exported=false` : même schéma).

## Fichiers possédés
`R/ParentalActivity.kt`, `R/ParentalUi.kt`, nouveau `R/HolderConsentActivity.kt`, `R/KeyBadgeOverlay.kt`, `R/HomeScreen.kt`, `R/PlayerActivity.kt`, le fichier « À propos » (nommé dans le rapport ; si c'est `PlayerActivity.kt`, déjà possédé), `android/receiver/src/main/AndroidManifest.xml` (une activité), `android/receiver/src/main/res/drawable/ic_t_parental_shared.xml`. **Hors zone** : `R/ParentalHub.kt`, `R/TvService.kt` (w6-13), `R/BtServer.kt` (w6-15), `R/ActivationCenter.kt` (w6-12), `C/**`.

## Étapes
1. `HolderConsentActivity.show(ctx, phoneId, phoneName)` ; retour `OK` ⇒ `ParentalHub.engine`… **non** : via `ParentalHub.holders.consentGiven(phoneId)` (exposé par w6-13 ; sinon `ParentalHub.reports.recipients` + `Holders` instancié localement : le dire) ; `Annuler` ⇒ le détenteur reste `PENDING` (sera oublié après 7 j, w6-06) ; BACK = Annuler ; aucune interception de HOME.
2. Rappel : `PlayerActivity.onResume` ⇒ si un `PENDING` existe et pas rappelé aujourd'hui ⇒ ouvrir l'écran (réutiliser le mécanisme `dailyPrompt` de `ActivationCenter` **sans** l'éditer : une clé de prefs locale).
3. `ParentalActivity` : page « Téléphones des parents » ; textes : « Un téléphone reçoit les rapports seulement s'il est de confiance, désigné avec votre code, et si vous validez ici que le foyer est informé. » ; options par profil.
4. Indicateur : `KeyBadgeOverlay` (ligne additionnelle, même style) ; accueil : icône + infobulle ; mode enfant : phrase enfant ; **aucun** réglage pour le supprimer.
5. « À propos ».
6. Captures d'émulateur 1280×720 : consentement, page, bandeau adulte, bandeau enfant, « À propos » → `docs/img/parental/w6-*.png`.

## Critères d'acceptation
```sh
cd android && gradle --offline :receiver:compileDebugKotlin   # compile
grep -n 'HolderConsentActivity' android/receiver/src/main/AndroidManifest.xml   # exported="false"
grep -rn 'adultIndicator\|kidIndicator' android/receiver/src/main/kotlin/castbridge/receiver/KeyBadgeOverlay.kt   # ≥ 2
grep -rn 'masquer l.indicateur\|hideIndicator' android/receiver/src/main/kotlin   # 0 hit
```
Observable : désigner depuis le téléphone ⇒ à l'accueil de la TV, l'écran de consentement apparaît ; « Annuler » ⇒ aucun rapport ne part (vérifier « Recevoir maintenant » sur le téléphone : `consent: pending`) ; « Continuer » ⇒ bandeau « Rapports d'usage partagés avec 1 téléphone » ; profil enfant actif ⇒ « Tes parents reçoivent un résumé de ce que fait la TV ».

## Cas limites
TV sans code parental : la désignation est impossible (PIN requis) donc pas de consentement à afficher ; 3 détenteurs : « Retirez-en un d'abord » ; nom de téléphone vide ⇒ « un téléphone » ; télécommande sans OK long : tout se fait avec OK/RETOUR.

## À ne pas faire
Pas de consentement par HTTP/Bluetooth ; pas de bouton « ne plus afficher » pour l'indicateur ; pas d'édition de `ParentalHub.kt` (demander à w6-13 par le rapport si un accès manque) ; français simple.

## Rapport
`STATUT`, captures, fichier « À propos », accès demandés à w6-13.
