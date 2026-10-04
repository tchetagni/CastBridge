# wios-10 — Plan de test humain sur iPhone (P-IOS-1…12), guide Apple du propriétaire (signature, TestFlight), notes au relecteur App Review
<!-- routage architecte 2026-10-04 (vague iOS, ordre 6) -->
> **Modèle : haiku** · escalade : aucune (documentation) · statut : **ATTEND wios-06…09** (pour citer les écrans réels)
> **Groupe : WIOS** (ordre 6, en parallèle de wios-11) · porte : relecture ; `grep` de secrets vide
> **Jauge : ≈ 150 k jetons entrée / 15 k sortie** (effort S, ≈ 0,5 j) · exécutant le moins cher compétent : haiku

**Conception** : `docs/coordination/DESIGN-IOS-TELEPHONE-2026-10-04.md` (§ 4.3, § 6.2, § 6.3, § 6.5, § 7). Branche `claude/wios-10-docs`. Rapport : `docs/agent-reports/sonnet-wios-10.md`.

## Objectif (autonome)
Écrire, en français simple, ce que le **propriétaire** fait seul (aucun agent ne signe, n'installe ni ne téléverse ; aucun identifiant Apple n'est jamais demandé ni écrit), et les essais à faire avec un vrai iPhone et la TV.

## Fichiers possédés
- **Nouveaux** : `ios/docs/GUIDE-PROPRIETAIRE-APPLE.md`, `ios/docs/TEST-HUMAIN.md`, `ios/docs/NOTES-APP-REVIEW.md`, `ios/docs/CONFIDENTIALITE.md` (réponses proposées aux étiquettes de confidentialité et à la classification par âge).
- **Interdit** : tout autre fichier ; toute valeur réelle d'équipe, d'identifiant Apple, de PIN, de mot de passe Wi-Fi.

## Contenu
1. `GUIDE-PROPRIETAIRE-APPLE.md` : les 8 étapes de la conception § 6.2 (Xcode › Comptes ; signature automatique pour les 3 cibles ; capacités App Groups + Hotspot Configuration ; iPhone en « Mode développeur » ; fiche App Store Connect `com.sti-cm.castbridge`, français ; Product › Archive › Distribute › Upload ; `ITSAppUsesNonExemptEncryption = NO` déjà dans l'Info.plist ; TestFlight interne, 90 jours par build ; externe = revue bêta) ; quoi faire si le nom « CastBridge » est pris ; où voir la disponibilité par pays (Cameroun) ; rappel : renouvellement annuel du programme.
2. `TEST-HUMAIN.md` : parcours **P-IOS-1** même box (Bonjour, invite réseau local « Autoriser ») ; **P-IOS-2** QR + groupe de la TV (invite « Rejoindre », temps mesuré) ; **P-IOS-3** iPhone sur le groupe : Internet par données mobiles ? avertissement d'iOS ? reste associé 10 min ? ; **P-IOS-4** refus du réseau local puis réparation ; **P-IOS-5** copie 2 Go écran allumé (débit) ; **P-IOS-6** passage en fond pendant une copie (ce qui continue, reprise au retour) ; **P-IOS-7** photo iCloud optimisée (avertissement données mobiles) ; **P-IOS-8** HEIC ⇒ JPEG lu par la TV ; **P-IOS-9** vidéo HEVC d'iPhone lue par la TV ? (fluide ? saccades ?) ; **P-IOS-10** Live Activity sur l'écran verrouillé ; **P-IOS-11** partage d'une vidéo de 2 Go depuis Photos ; **P-IOS-12** Quiz à 2 iPhone + 9e téléphone refusé. Chaque parcours : préconditions, gestes, résultat attendu, case OK/KO, ce qu'il faut copier (jamais un PIN ni un mot de passe). 3 séances d'≈ 1 h proposées.
3. `NOTES-APP-REVIEW.md` : brouillon des notes au relecteur (l'app est le compagnon de CastBridge-TV, app Android TV séparée ; utiliser « Essayer sans TV » ; ce que fait chaque autorisation ; aucune collecte ; aucun achat) + liste des plans de la vidéo de démonstration à tourner par le propriétaire.
4. `CONFIDENTIALITE.md` : « Données non collectées » (v1), pas de pistage, justification des API à raison déclarée, classification par âge proposée (la plus basse) avec les réponses au questionnaire.

## Critères d'acceptation
- Chaque étape est exécutable par une personne non développeuse ; aucune commande qui signe depuis un agent.
- `grep -RniE "team|[A-Z0-9]{10}\)|motdepasse|pin *[:=] *[0-9]{4,8}" ios/docs` ne trouve aucune valeur réelle (le rapport montre la commande).

## À ne pas faire
- Promettre une date de revue Apple ; écrire une valeur réelle ; décrire une fonction absente de la v1.
