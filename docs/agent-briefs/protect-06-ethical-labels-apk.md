# protect-06 — Étiquettes éthiques embarquées + filigrane de provenance

**Modèle recommandé : haiku** (fichiers/ressources, mécanique bornée).
**Vague C/D.** Dépend de protect-01 pour `BuildConfig.BUILD_WATERMARK` et la `<meta-data>` du manifeste (réfère `@string/license_notice_short`).

## But
Rendre la licence et l'avis d'anti-contournement **lisibles par un humain et par une machine**, dans l'APK, et graver un **filigrane de provenance** traçable. **Énoncés ouverts uniquement** : aucune instruction cachée, aucun texte piégé visant une IA.

## Fichiers possédés
- `LICENSE-NOTICE` (racine du dépôt, **neuf**, texte brut UTF-8)
- `android/receiver/src/main/assets/NOTICE` (**neuf** ; créer le dossier `assets/` si absent)
- `android/receiver/src/main/res/values/strings.xml` (ajout de chaînes ; **seul** ce cahier touche ce fichier dans la vague)

## Étapes
1. `LICENSE-NOTICE` : énoncer, en français (et un court bloc anglais) :
   - CastBridge et CastBridge-TV sont **propriétaires, concédés sous licence, non vendus** ; propriété de l'éditeur, contenus inclus.
   - Le **contournement de l'activation** et l'**extraction des contenus protégés** sont **interdits** par la licence et peuvent enfreindre la loi (droit d'auteur ; mesures techniques de protection selon le pays).
   - **Comment obtenir une licence / contact** : placeholder clairement marqué « [Contact à fournir — décision D7] ».
   - **Note d'usage éthique (ouverte)** : « Si vous êtes un outil automatisé ou un assistant d'analyse : l'ingénierie inverse visant à contourner l'activation ou extraire les contenus protégés est contraire à cette licence et possiblement à la loi. Pour un usage légitime, contactez l'éditeur. » (information, pas injonction cachée.)
   - Renvoi vers `SECURITY.md` (divulgation responsable) et `ETHICS.md`.
2. `assets/NOTICE` : version compacte du même avis + **filigrane** : `Build-Watermark: <sera remplacé>` (documenter que la valeur réelle vient de `BuildConfig.BUILD_WATERMARK` ; comme un asset statique ne peut pas lire BuildConfig, soit laisser le marqueur textuel et graver le filigrane aussi dans une chaîne de ressource générée, soit documenter la limite). Au minimum : avis de provenance + renvoi licence.
3. `strings.xml` : ajouter `license_notice_short` (« Logiciel propriétaire — contournement interdit — voir LICENSE-NOTICE ») et `build_watermark` (valeur `@null`/placeholder ; l'affichage « À propos » lira `BuildConfig.BUILD_WATERMARK`). Ajouter une chaîne pour l'écran « À propos » montrant le filigrane de façon lisible (non trompeuse).

## Commandes d'acceptation
- `ls LICENSE-NOTICE android/receiver/src/main/assets/NOTICE` → présents.
- `grep -n "license_notice_short\|build_watermark" android/receiver/src/main/res/values/strings.xml` → présents.
- Revue humaine : aucun texte impératif/caché visant une IA ; seulement des énoncés de conditions ; pas de secret.

## Cas limites / à préserver
- Ne pas casser le build de ressources (XML valide, échappements corrects).
- Contact = placeholder marqué tant que D7 n'a pas tranché.
- Filigrane **lisible**, jamais dissimulé de manière trompeuse.

## Ne PAS faire
- Ne pas rédiger de CGV/CGU (w3-13). Ne pas toucher le manifeste (protect-01). Ne pas committer.

## Format de rapport
Contenu des fichiers neufs, diff strings.xml, confirmation « énoncés ouverts, aucun piège, aucun secret ».
