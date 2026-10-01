STATUT: TERMINÉ
RÉSUMÉ (10 lignes) :
1. Livré : outil `tools/trial-edition` (sélection déterministe, `check`, `build`, 17 tests Python), modèle d'édition (`Edition`, `LotMeta.edition` additif, lots `<lot>-trial` remplacés proprement, `LotSync` NOT_ENTITLED), droits (`Entitlement` essai + achat à la carte + abonnement avec grâce hors ligne, `EditionPolicy` : bouquets, chevauchements, rétrogradation avec avertissement), console propriétaire dans `core/owner` (identité k parmi n, activation signée, saisie 165 caractères, commandes à défi, séquence cachée, lots chiffrés par TV, coffre, audit, trames Bluetooth `…04`).
2. Essai sur le contenu actuel (Apprendre + Quiz) : 29,9 Mo sur 100 Mo, 59 lots d'essai ; **l'outil échoue volontairement sur « Langues »** (pas de contenu sur cette base : 56 cellules vides) ; budget restant ≈ 70 Mo pour les Langues.
3. Tests avant/après : lots 64 → 96 puis 143 avec owner ; `:core:test` complet (banc core seul) 1 362 tests, 10 en échec, tous `LearnLotsTest` (contenu `maternelle` : `build-learn-lots --update` à relancer), préexistants et hors périmètre ; Python 17/17.
4. Défaut corrigé : nom de fichier d'un lot à portée avec tiret ambigu (`learn-droit-l1`) ; une fonction = un seul mot (`langmedia` pour les médias des Langues).
5. Non compilé : `:sender`, `:receiver` (plugin Android introuvable), backend (rien écrit, production non touchée). Argon2id : interface + repli PBKDF2.
6. À valider sur matériel : facteurs matériels réels de la TV de référence, service Bluetooth propriétaire + panneau caché, branchement Apprendre/Quiz sur les lots `-trial` (`QuizLotFormat expectScope`), débit des vidéos courtes de l'essai.
7. Hypothèses à confirmer par le propriétaire : N0–N4 = difficultés Quiz 1–5 ; pouvoir `UNLOCK` ≤ 30 jours ; part maximale de l'essai par sous-catégorie 35 % (`maxShareOfFull`) ; table `bundleQuizAliases` ; achat par matière impossible tant que les lots sont par classe.
8. Docs : `docs/TRIAL-EDITION.md`, `docs/OWNER-CONSOLE.md`, `docs/HANDOFF.md`. Aucun prix, aucun prestataire, aucun secret ; le code de déverrouillage du propriétaire n'est nulle part.
9. Branche `claude/trial-edition`, pas de PR (voir le dernier commit ci-dessous).
JALONS :
- 2026-10-01 | départ : cahier + COORDINATION lus, branche claude/trial-edition créée depuis integration/agents | commit (voir git log)
- 2026-10-01 | outil tools/trial-edition (sélection, vérification, build) + 17 tests Python OK | commit 64749d6+
- 2026-10-01 | modèle d'édition (Edition, LotMeta.edition additif, lots <lot>-trial), Entitlement 3 droits, EditionPolicy, TrialBudget + 32 tests JVM verts (96 lots.*) ; bug corrigé : nom de fichier d'un lot à portée avec tiret (feature = un seul mot) | commit f546ffc+
- 2026-10-01 | docs TRIAL-EDITION.md, OWNER-CONSOLE.md, HANDOFF, rapport final | voir git log
- 2026-10-01 | PRIORITÉ coordinateur : docs/ACTIVATION-FORMAT.md + tools/activation/test-vectors.json (65 vecteurs) + verify_vectors.py indépendant (70 contrôles verts) + ActivationIssuer/ActivationVerifier (kid, portées de clé, sujet tv/phone, licence, poste, droit « tout ouvert »), 50 tests owner verts ; reste : licences/transfert/registre, option A (verrou) | voir git log
