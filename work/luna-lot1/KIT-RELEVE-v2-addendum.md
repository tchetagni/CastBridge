# Kit de relève — addendum v2 au kit v1

Ce fichier complète le kit v1 sans le remplacer. Le `work/releve/KIT-RELEVE.md` annoncé par le propriétaire n’est pas présent dans le workspace de production consulté ; transmettre cet addendum à l’orchestrateur pour fusion dans le kit canonique.

## Règles tranchées à réutiliser

- **Règle — texte exact / média :** un identifiant média pointe vers un seul texte prononcé, ponctuation comprise ; si le texte diffère, créer un nouvel identifiant stable. Pour le mandarin, `zh-nihao` porte le mot `你好` ; `zh-d1-a` porte la réplique `你好！`. Garder six demandes distinctes prévues par l’arbitrage du propriétaire ; ne pas en déduire une autorisation de production.
- **Règle — japonais, première rencontre :** dire `はじめまして。`, échanger les noms, dire `よろしくお願いします。`, puis `さようなら。` au moment de prendre congé. À faire confirmer en relecture native ; ne pas inverser l’ordre.
- **Règle — chinois A0 :** `很高兴认识你` n’est pas une formule de production active A0. L’omettre ou la cantonner à une activité explicite de reconnaissance ; pour un clip, préférer `再见` ou `谢谢`, sous réserve de décision/autorisation clips.
- **Règle — état média :** tant que le moteur/voix ne figure pas comme approuvé et qu’une `AUTORISATION.md` du propriétaire n’accompagne pas le lot, toutes les demandes sont `draft-not-authorized`, aucune génération ni publication.
- **Règle — intégration :** conserver le schéma réellement utilisé par les packs (`level: "a0"`, `theme: "salut"`, ID `<cible>-a0-salut-fr`) ; le graphe de prérequis se confirme avec l’orchestrateur.

## Exercices d’imitation

Exercice n° 1 : allemand A0 « Nombres 0-20 », produit par Relève-Langues. Le compte rendu disponible rapporte 6 leçons, 87 items, 60,9 % de réemploi, QA auto 2,8/4, et les règles d’identifiant/thème/champs. Cette relève doit être notée sur les artefacts eux-mêmes avant d’enregistrer une note de Luna : le présent espace ne contient pas ces fichiers. Ne pas présenter l’auto-évaluation comme validation indépendante.

## Auto-contrôle des lots

- [ ] Schéma `langue.json` respecté ; 6–12 leçons ; champs identifiants stables.
- [ ] Objectifs observables A0 ; aucun objectif productif au-delà du niveau.
- [ ] 12–15 items ciblés par unité ; réemploi ≥ 60 % mesuré entre unités.
- [ ] Exercices variés, consignes enfantines et corrigés exacts.
- [ ] Écritures et conventions : pinyin tonal / kana + rōmaji / API selon la langue.
- [ ] Un texte exact par ID média ; identifiants distincts lorsque la ponctuation change.
- [ ] Toute demande non autorisée reste en brouillon ; moteurs/voix non inventés.
- [ ] Relecture native consignée, ou statut « bêta : non validé ».
- [ ] Transcriptions et textes alternatifs prévus ; maquette lisible à 3 m en 720p.
- [ ] Licence et provenance présentes pour chaque média livré ; aucun actif sans source.

## Portée langues — lot A0 salutations

Pour rendre « toutes les langues » opérable au regard des 12 paires annoncées, ce lot couvre : chinois, japonais, allemand, anglais, italien et espagnol avec explications en français ou anglais ; français avec explications en anglais. Soit les 12 paquets suivants : zh-fr, zh-en, ja-fr, ja-en, de-fr, de-en, en-fr, it-fr, it-en, es-fr, es-en, fr-en. Cette interprétation reste à confirmer par l’orchestrateur si les 12 paires officielles diffèrent.
