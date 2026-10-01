# Tableau de suivi (tenu par le coordinateur)

| Chantier (id) | Zone de fichiers | Branche | Statut | Notes |
|---|---|---|---|---|
| content-integration | content/learn, content/quiz, tools/quiz-bank, tests de contenu | claude/content-integration | EN COURS | remet les tests au vert (17 échecs) |
| content-export | dépôt castbridge-content uniquement | claude/lucid-goldberg-6tz9ae (castbridge-content) | EN ATTENTE D'ACCORD | accès au dépôt CastBridge et branche à autoriser par le propriétaire |
| bt-tunnel-keepalive | android/core/.../tv/BtTunnel*, sender BtSshGatewayService, receiver BtApiControl | claude/bt-tunnel-keepalive | LANCÉ 13:41 UTC | Bluetooth seul : la télécommande hors app ne marche pas |
| smart-remote | android/core/.../remote/strategy* | claude/smart-remote | FUSIONNÉ (4 jalons) | |
| bt-remote | android/core/.../remote/vendor*, receiver RemoteHub | claude/bt-remote | FUSIONNÉ | relais Bluetooth → service du fabricant |
| usb-data | android/core/.../tv/Storage*, receiver UsbImporter | claude/usb-data | LIVRÉ, NON FUSIONNÉ | 1 commit seulement : à vérifier |
| net-architect | tools/content-*, docs/CONTENT-PUBLISH.md | claude/net-architect | LIVRÉ, NON FUSIONNÉ | outils de budget et de découpage |
| content-quiz-culture / superieur | tools/quiz-bank | claude/content-quiz-* | LIVRÉ, NON FUSIONNÉ (superieur fusionné) | |
| multipath-transfer | android/core/.../tv/*Transfer*, sender UploadService, receiver serveur d'envoi | claude/multipath-transfer | LANCÉ | copie rapide multivoie : mesurer d'abord |
| content-phase1 | castbridge-content uniquement | (branche autorisée dans castbridge-content) | TERMINÉ (vague 1 : 8 paquets N2/N4) | rapport lu |
| languages-architect | docs/LANGUES.md, core/.../learn/lang*, graph/langue-* | claude/languages-architect | LANCÉ | catégorie Langues (7 langues, 6 Go) : conception d'abord |
| content-phase2 | castbridge-content uniquement | idem | LANCÉ | Quiz N2/N4, autres classes/matières, animations ; session permanente des contenus d'apprentissage |
| trial-edition | tools/trial-edition, core/.../lots* (additif), docs/TRIAL-EDITION.md | claude/trial-edition | LANCÉ | essai 100 Mo couvrant tout le catalogue ; le contenu complet continue |
| activation-tools | tools/activation, bureau JVM, backend/ (routes admin), noyau console téléphone | claude/activation-tools | EN ATTENTE du format filaire de trial-edition | générateurs de jetons : bureau, téléphone propriétaire, serveur |
| license-admin | backend/ (module licences, migrations ≥ V50, admin) | claude/license-admin | LANCÉ | gestion robuste des licences en ligne ; clé serveur : ni transfert ni « tout ouvrir » |
| deferred-orders | core/.../policy*, backend/ (file d'ordres), protocole Bluetooth additif, tâche téléphone | claude/deferred-orders | EN ATTENTE du format filaire de trial-edition | serveur → téléphone → TV : ordres différés signés, liste blanche d'actions |
| content-langues-w1 | castbridge-content uniquement | idem | LANCÉ | Langues vague 1 : A0–A2 des 7 langues (lots libres SA / lots réservés séparés) |
