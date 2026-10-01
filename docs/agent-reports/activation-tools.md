STATUT: TERMINÉ
ORDRES-TRAITES: 7

- 2026-10-01 | départ (ORDRE 7, branche claude/activation-tools depuis integration/agents) ; plan : tools/activation-desktop (CLI Kotlin puis GUI Swing, bibliothèque ActivationIssuer du cœur), noyau console téléphone ensuite
- 2026-10-01 16:15 | CLI de bureau livrée (tools/activation-desktop : cle-creer, emettre, registre, journal, QR, autotest) ; 11 tests verts dont les vecteurs communs | commit ebab273

Résumé (ordre 7, branche claude/activation-tools) :
- Livré : outil de bureau tools/activation-desktop (CLI en français : cle-creer, cle, faire-confiance, appareil, licence, emettre, cle-saisissable, commande, verifier, journal, registre, autotest, gui ; fenêtre Swing ; JAR exécutable ; QR ; fichier « activation » pour la clé USB) ; flux partagé core/.../owner/LicensedIssuer.kt (postes, ré-activation gratuite, refus sans poste) ; noyau PhoneConsole (déverrouillage avec compteur d'essais, session verrouillable, trame Bluetooth ACTIVATION, audit chaîné) ; docs/ACTIVATION-TOOLS.md (guide, risques, rotation/révocation) ; flux .github/workflows/activation-desktop.yml (manuel, installateurs non signés).
- Tests : activation-desktop 11/11 (RFC 7914 scrypt, 46 vecteurs communs : mêmes octets, bout en bout : clé→émission→vérification, mauvais code, aucun secret écrit) ; core PhoneConsoleTest 7/7. Fenêtre vérifiée sous écran virtuel Linux (capture).
- Vérificateur côté TV : déjà livré par trial-edition (ActivationVerifier, vecteurs) : rien refait.
- NON fait / à valider : écran Android « Générer un jeton » (à compiler sur le téléphone) ; Mac, Windows et jpackage jamais essayés ; transfert/révocation de poste non exposés par l'outil ; serveur = license-admin. Pas de compteur d'essais sur le bureau (scrypt seul protège).
- Hors chantier : :core:test échoue 10 tests LearnLotsTest sur integration/agents dans mon environnement (hash des lots dépendant de la locale) ; HotRemovalTest instable (1 échec sur 2).
