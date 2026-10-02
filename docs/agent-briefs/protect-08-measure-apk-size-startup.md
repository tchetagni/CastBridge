# protect-08 — Mesure taille APK + démarrage à froid (v7a + arm64 ; TV de référence = pire cas 32 bits)

<!-- routage Fable 2026-10-02 -->
> **Modèle : haiku** · escalade : sonnet au 2e échec ou fichier sensible · statut : PRÊT (après A, idéalement B ; SDK local)
> **Groupe : P-E** (vague PROTECT) · prérequis : protect-01 · porte : `cd android && tools/agents/gradle-lock.sh gradle --offline :receiver:assembleRelease -PrequireActivation=true`
> **Jauge : ≈ 60 k jetons entrée / 5 k sortie** (effort S) · audit Opus : non

**Modèle recommandé : haiku** (build + mesure + rapport ; pas de modification de source).
**Vague E.** Dépend de protect-01 (idéalement après B). Coordination w3-12 (affinage ProGuard) et A6-6 (budget mémoire des lots).

## But
Établir une **baseline** chiffrée de l'impact taille/performance des couches de protection. Le parc cible = **tous** les OS de smart TV (Android TV, Google TV, Fire TV, GaiaOS, autres) ; la TV de référence 32 bits / 1 Go / 720p est le **pire cas de budget** (contrainte de performance), pas la seule cible. Confirmer la §7 du document de conception (impact négligeable, aucune lib native ajoutée — PD1).

## Fichiers possédés
- `docs/agent-reports/protect-mesure-apk.md` (**neuf** ; créer `docs/agent-reports/` si absent)

## Étapes
1. Construire les APK `release` : `./gradlew :receiver:assembleRelease` (splits v7a **et** arm64). Noter la taille de chaque `.apk`. Si l'environnement ne permet pas le build Android, documenter la procédure et marquer les chiffres « à relever sur la machine de build ».
2. Mesurer **avant/après** les couches de protection si deux points de comparaison existent (branche de base vs branche courante) ; sinon, relever l'état courant comme baseline.
3. Sur la TV de référence (pire cas 32 bits) et, si disponible, sur une TV/box 64 bits (Android TV/Google TV/Fire TV) ou l'émulateur TV à défaut : mesurer le **démarrage à froid** (temps jusqu'au premier écran) sur 3-5 essais (`adb shell am start -W ...`), noter moyenne/médiane.
4. Vérifier qu'**aucune `.so` supplémentaire** n'a été ajoutée : `unzip -l app-armeabi-v7a-release.apk | grep '\.so'` → seulement `libaria2c.so` + libVLC attendues.
5. Rédiger le rapport : tailles, temps de démarrage, liste des `.so`, écart vs baseline, recommandation (OK / à surveiller).

## Commandes d'acceptation
- `ls docs/agent-reports/protect-mesure-apk.md` → présent, chiffré (ou procédure + emplacements des chiffres si build impossible ici).
- Le rapport confirme : pas de nouvelle `.so`, impact taille documenté.

## Cas limites / à préserver
- Si le toolchain Android n'est pas disponible dans l'environnement du sous-agent : livrer la procédure exacte + les commandes, et signaler que les chiffres doivent être relevés sur la machine de build du propriétaire (ne pas inventer de chiffres).
- Ne modifier aucune source ni config de build.

## Ne PAS faire
- Ne pas committer. Ne pas changer `proguard-rules.pro` (w3-12). Ne pas inventer de mesures.

## Format de rapport
Chemin du rapport, chiffres clés (ou mention « à relever »), confirmation « aucune .so ajoutée ».
