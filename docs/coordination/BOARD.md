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
