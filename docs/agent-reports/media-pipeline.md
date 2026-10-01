STATUT: TERMINÉ
ORDRES-TRAITES: 8

- 2026-10-01 | départ (ORDRE 8, branche claude/media-pipeline depuis integration/agents) ; plan : tools/media-pipeline (Python 3 stdlib, ffmpeg pour les tests) : demandes, estimation/plafonds/reprise, registres et gabarits juridiques, manifeste, contrôles de réception, interface ASR, gabarits AGENTS.md/CLAUDE.md, docs

Résumé (ordre 8, cahier media-pipeline, branche claude/media-pipeline) :
- Livré : tools/media-pipeline (mp.py + paquet mp/) : demandes déterministes avec empreintes (mêmes paquets = mêmes octets, texte copié tel quel, aucune voix nommée, blocage de ce qui ne peut pas être déterminé, conflit signalé), estimation sans prix en dur (pricing.json vide : « coût inconnu », jamais zéro), plafonds (code 3 à l'arrêt, refus sans tarif), reprise sans double facturation, registres vides (moteurs, voix, mapping classe -> voix à choisir par le propriétaire), 4 gabarits juridiques « à valider par un juriste », manifeste de provenance additif, réception (audio ffprobe/ffmpeg : codec, fréquence d'entrée OpusHead, débit, durée, LUFS, silences, métadonnées ; image WebP ; vidéo ; plafonds ; secrets ; fichier non listé rejeté), interface ASR non implémentée (comparaison locale ; écart = rejet ; absence = ni accepté ni rejeté), gabarits AGENTS.md / .claude/CLAUDE.md / lot de travail / procédure d'arrêt, docs/MEDIA-PIPELINE.md.
- Copie dans castbridge-content : branche claude/media-pipeline (depuis claude/initial-import), commit ff84ba8 ; jamais main.
- Tests : 27 Python verts (8 utilisent ffmpeg, sautés s'il est absent), dans CastBridge et dans la copie. Non exécutable ici : tout appel Google (aucun accès, voulu).
- Constats : le paquet d'exemple zh-a0-salut-fr donne deux textes pour l'id zh-nihao (« 你好 » et « 你好！ ») : à trancher par l'auteur du contenu ; l'anglais et l'espagnol n'ont pas de variété par défaut (à choisir).
- À faire par le propriétaire : remplir pricing.json d'après la grille Google en vigueur, choisir moteurs et voix (registres), faire valider les textes juridiques, écrire l'AUTORISATION d'un lot et son plafond ; écoute native pour le chinois et le japonais.
