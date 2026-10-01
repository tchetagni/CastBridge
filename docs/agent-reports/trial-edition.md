STATUT: TERMINÉ
RÉSUMÉ (10 lignes) :
1. Livré : outil `tools/trial-edition` (sélection déterministe, `check`, `build`, 17 tests), modèle d'édition (lots `-trial`, `LotMeta.edition` additif, `NOT_ENTITLED`), droits (essai + achat + abonnement, bouquets, rétrogradation annoncée), **format filaire `docs/ACTIVATION-FORMAT.md` + 109 vecteurs + vérificateur Python indépendant (117 contrôles)**, **enveloppe signée générique `cbx1`** (activation / commande / révocation / ordre différé, `kid`, séquence par clé, portées dont `policy` et `reactivate`), `ActivationIssuer`, licences / postes / ré-activation / transfert / registre signé / révocation, **option A** (état verrouillé à liste blanche testée, mode porteur, `REQUIRE_ACTIVATION` éteint par défaut, parc existant avec grâce), console propriétaire (défi de la TV, séquence cachée, lots chiffrés par TV, coffre, audit, canal Bluetooth `…04`).
2. Essai sur le contenu actuel (Apprendre + Quiz) : 29,9 Mo sur 100 Mo, 59 lots ; **l'outil échoue volontairement sur « Langues »** (aucun contenu sur cette base) ; budget restant ≈ 70 Mo.
3. Tests : lots 64 → 96 ; `owner.*` 72+ ; `:core:test` complet (banc core seul) : seuls échecs = 10 `LearnLotsTest` (lot `maternelle` : `build-learn-lots --update`), préexistants ; Python 17/17 et 117/117.
4. Défaut corrigé : nom de fichier d'un lot à portée avec tiret ambigu ; une fonction = un seul mot (`langmedia`).
5. Non compilé : `:sender`, `:receiver` (plugin Android introuvable), backend (rien écrit, production non touchée) ; `REQUIRE_ACTIVATION` non relié aux `BuildConfig` ; Argon2id = interface + repli PBKDF2.
6. À valider sur matériel : facteurs réels de la TV de référence, service Bluetooth propriétaire + panneau caché, branchement Apprendre/Quiz sur `-trial` (`QuizLotFormat expectScope`), débit des vidéos courtes.
7. Hypothèses à confirmer : N0–N4 = difficultés Quiz 1–5 ; `UNLOCK` ≤ 30 jours ; part max. de l'essai 35 % ; `bundleQuizAliases` ; achat par matière impossible (lots par classe) ; sort des bêta-testeurs (grand-père ?) ; mise à jour impossible en état verrouillé (ajouter `UPDATES` à la liste blanche ?).
8. Docs : `docs/TRIAL-EDITION.md`, `docs/OWNER-CONSOLE.md`, `docs/ACTIVATION-FORMAT.md`, `docs/HANDOFF.md`. Aucun prix, aucun prestataire, aucun secret ; le code du propriétaire n'est nulle part.
9. Branche `claude/trial-edition`, pas de PR.
JALONS :
- 2026-10-01 | départ : cahier + COORDINATION lus, branche claude/trial-edition créée depuis integration/agents | commit (voir git log)
- 2026-10-01 | outil tools/trial-edition (sélection, vérification, build) + 17 tests Python OK | commit 64749d6+
- 2026-10-01 | modèle d'édition (Edition, LotMeta.edition additif, lots <lot>-trial), Entitlement 3 droits, EditionPolicy, TrialBudget + 32 tests JVM verts (96 lots.*) ; bug corrigé : nom de fichier d'un lot à portée avec tiret (feature = un seul mot) | commit f546ffc+
- 2026-10-01 | docs TRIAL-EDITION.md, OWNER-CONSOLE.md, HANDOFF, rapport final | voir git log
- 2026-10-01 | PRIORITÉ coordinateur : docs/ACTIVATION-FORMAT.md + tools/activation/test-vectors.json (65 vecteurs) + verify_vectors.py indépendant (70 contrôles verts) + ActivationIssuer/ActivationVerifier (kid, portées de clé, sujet tv/phone, licence, poste, droit « tout ouvert »), 50 tests owner verts ; reste : licences/transfert/registre, option A (verrou) | voir git log
- 2026-10-01 | enveloppe générique cbx1 (ordres différés, séquence par clé, portées policy/reactivate), licences/transfert/registre, option A, 109 vecteurs, rapport final | voir git log
