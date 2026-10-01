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
| activation-tools | tools/activation, bureau JVM, backend/ (routes admin), noyau console téléphone | claude/activation-tools | LANCÉ (format livré) | générateurs de jetons : bureau, téléphone propriétaire, serveur |
| license-admin | backend/ (module licences, migrations ≥ V50, admin) | claude/license-admin | TERMINÉ et FUSIONNÉ (3 tests de vecteurs rouges : `cba1` au lieu de `cbx1`, correctif lancé) | gestion robuste des licences en ligne ; clé serveur : ni transfert ni « tout ouvrir » |
| deferred-orders | core/.../policy*, backend/ (file d'ordres), protocole Bluetooth additif, tâche téléphone | claude/deferred-orders | LANCÉ | serveur → téléphone → TV : ordres différés signés, liste blanche d'actions |
| content-langues-w1 | castbridge-content uniquement | idem | LANCÉ | Langues vague 1 : A0–A2 des 7 langues (lots libres SA / lots réservés séparés) |
| owner-cli (local) | core/.../owner/OwnerCli.kt, docs/OWNER-CLI.md | integration/agents | LIVRÉ (coordinateur) | CLI d'activation Mac/Windows/Linux, 9 tests ; le reste de activation-tools bâtit dessus |
| naming-patterns | core/.../library/agent/NameParser*, SeriesClassifier, docs/NAMING-PATTERNS.md, content/naming | claude/naming-patterns | LANCÉ | catalogue exhaustif des expressions du moteur d'organisation ; corpus DEV-3 / GELÉ-3 |
| media-pipeline | tools/media-pipeline, docs/MEDIA-PIPELINE.md, registry/, legal/ (gabarits) | claude/media-pipeline | LANCÉ | chaîne multimédia Google : demandes, coûts estimés, registres, contrôles ; aucun accès Google en cloud |
| license-admin-cbx1 | backend/…/licenses (port Java de cbx1, portées de clé) | claude/license-admin-cbx1 | LANCÉ | aligner le serveur sur l'enveloppe cbx1 ; 127/130 tests verts avant |
| superadmin-login | core/.../owner/SuperAdminGate, :ownerlib, entrée cachée de MainActivity | integration/agents | LIVRÉ (coordinateur) ; bon mot de passe à essayer par le propriétaire | local seulement, aucun lien avec le serveur ; haché hors dépôt, injecté à la compilation |
| rental-lots | core/.../lots/Entitlement (Right.Rental), LotStore (balayage), tools/activation-desktop (--location), docs/RENTAL-LOTS.md | claude/rental-lots | LANCÉ | contenus d'apprentissage en location hors ligne : expiration cryptographique et suppression autonome ; ne touche pas à backend/ |
