# Test de location de bout en bout (TEST uniquement)

1. `gradle :core:buildLearnLots` puis `python3 tools/rental-test/prepare_test_lots.py --out DIR --key test-lots.pem` : lots d'Apprendre + catalogue signé avec une clé de TEST.
2. Construire la TV avec `-PtrustedKeysFile=<clés réelles + clé de bureau de test (scope SUPER_UNLIMITED)>` et `-Pcastbridge.extraUpdateKey=<clé publique du catalogue de test>`.
3. `python3 tools/rental-test/rental_test.py --tv URL --pin PIN --desk DOSSIER --pass-file F --lots DIR [--days N] [--super]` rejoue : demande d'appareil, activation de location, installation, chiffrement et envoi de chaque lot, état. `--status` et `--sweep` seuls disponibles.

Sur l'émulateur (build debug) : `files/test-factors.txt` donne à la TV une identité de test. Avancer l'horloge : `adb shell "cmd alarm set-time <ms>"`, puis `--sweep`. Ne jamais utiliser ces clés ni ce build en production.
