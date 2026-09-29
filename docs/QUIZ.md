# Quiz sur la TV — « Le Quiz des Millions » (POC, version 0.5-quiz)

Jeu de questions façon « Qui veut gagner des millions ? » sur CastBridge TV. On y joue **seul à la télécommande** (sans
téléphone ni réseau) ou **à plusieurs** : chacun répond sur son téléphone, sans rien installer (QR code affiché sur la TV).

- Ouvrir : touche **MENU** de la télécommande > « Quiz culture générale », ou onglet **Quiz** de l'app téléphone CastBridge.
- Télécommande : flèches = se déplacer, **OK** = valider, **RETOUR** = pause (reprendre / abandonner / quitter, toujours avec
  confirmation).

## 1. Choisir sa partie (écran de la TV)

**Mode → Parcours → Niveau → Filière → Façon de jouer**

| Mode | Principe |
|---|---|
| Compétition entre amis | gratuit, pour le plaisir |
| Compétition avec mise | mise en **jetons virtuels sans valeur** (voir § 5), en Duel seulement |
| Entraînement | sans enjeu ni chrono, l'explication de chaque réponse s'affiche, une erreur n'arrête pas la partie |

| Parcours | Contenu |
|---|---|
| Culture générale | **70 % Cameroun, 20 % Afrique, 10 % Monde** (± 1 question) |
| Primaire | SIL, CP, CE1, CE2, CM1, CM2 ; Class 1 à 6 |
| Secondaire | 6e à Tle ; Form 1 à 5, Lower / Upper Sixth |
| Supérieur | L1, L2, L3, par filière : droit, économie, mathématiques, physique, psychologie, géographie, littérature, histoire, informatique, chimie, biologie, philosophie, sociologie |

Un niveau ou une filière sans questions s'affiche « bientôt » : il suffit d'ajouter des questions avec ce `level` / `field`
pour l'ouvrir (§ 6). Dans le POC : culture générale, **CM2, 3e, Tle, L1 droit, L1 économie, L1 mathématiques**.

| Façon de jouer | Qui joue | Réseau |
|---|---|---|
| **Seul, à la télécommande** | la TV | aucun |
| Millionnaire avec le public | un candidat (TV ou téléphone) + public et amis sur téléphones | Wi-Fi |
| Duel | 1 à 8 joueurs, tous sur téléphone | Wi-Fi |

## 2. Règles

### Millionnaire
- 15 questions de difficulté croissante (3 par niveau 1 → 5). Échelle de gains **fictifs** en FCFA, de 10 000 à
  100 000 000 ; **paliers de sécurité** à la 5e (100 000) et à la 10e question (3 000 000).
- On choisit une réponse, puis « **C'est votre dernier mot ?** » (Oui / Non). Réponse verrouillée en orange, suspense ~1,8 s,
  puis révélation : vert ✓ / rouge ✗ (jamais la couleur seule) et une courte explication.
- Erreur = on repart avec le dernier palier atteint. **S'arrêter** = on garde le gain de la dernière bonne réponse.
- Chrono : 30 s (questions 1-5), 45 s (6-10), aucun ensuite ; temps écoulé = comme une erreur. Pas de chrono en Entraînement.
- Jokers, **une fois chacun** par partie :
  - **50:50** retire deux mauvaises réponses (jamais la bonne) ;
  - **Avis du public** : vote réel des autres joueurs connectés (15 s) ; sans joueur, vote simulé plausible (d'autant plus
    juste que la question est facile) — la TV indique « simulé » ;
  - **Appel à un ami** : le candidat choisit un joueur connecté, qui voit la question 30 s sur son téléphone et suggère une
    réponse ; sans joueur (ou sans réponse à temps) un « ami virtuel » répond avec un degré de certitude.
  Le chrono est en pause pendant le public et l'appel.
- Le candidat peut jouer sur son téléphone ; la télécommande garde toujours la main (utile si ce téléphone se déconnecte).

### Duel
- 10 questions ; tout le monde répond en même temps sur son téléphone (20 s, 40 s en Entraînement).
- Bonne réponse : **1000 points si instantanée, jusqu'à 500 au gong** ; mauvaise ou pas de réponse : 0.
  La rapidité est mesurée avec l'horloge **de la TV** (moment où la réponse arrive), jamais celle du téléphone.
- La question se ferme quand tout le monde a répondu ou à la fin du temps ; révélation (répartition des réponses, qui a
  juste), puis **classement animé** ; OK sur la télécommande passe l'attente.
- Première réponse définitive (renvoyer la même est sans effet, en changer est refusé).

### Meilleurs scores (solo)
Les parties Millionnaire (gain) et Entraînement (bonnes réponses) inscrivent leur score dans un tableau gardé sur la TV, par
façon de jouer et parcours (10 par tableau). « Nouveau record » est célébré ; menu > « Meilleurs scores ».

## 3. Rejoindre depuis un téléphone

- **Sans app** : scanner le QR code de la salle d'attente (appareil photo), ou ouvrir `http://<ip de la TV>:8765/quiz`,
  puis saisir le **code de salle à 4 chiffres** affiché sur la TV et un pseudo. Même Wi-Fi que la TV.
- **App CastBridge** (onglet Quiz) : trouve la TV, peut ouvrir le quiz sur la TV (PIN déjà connu de l'app), récupère le code
  toute seule et rejoint.
- Reconnexion : la page garde un jeton ; en rouvrant la page, on retrouve sa place (et son score).

## 4. Réseau et sécurité (pour les curieux)

- Routes `/quiz/*` sur le serveur existant de la TV (port 8765), **séparées de l'admin** : elles n'utilisent pas le PIN et
  ne donnent accès à rien d'autre. Le code de salle sert seulement à rejoindre ; ensuite un **jeton aléatoire par joueur**
  (128 bits). Le PIN n'est jamais donné aux joueurs.
- État en direct par **Server-Sent Events** (`/quiz/api/events`), repli automatique en long-poll 25 s (`/quiz/api/state`).
  Commandes POST idempotentes (`/quiz/api/act`, avec l'id de la question).
- **La bonne réponse n'est jamais envoyée avant la clôture de la question** (testé).
- Limites : 8 joueurs, 30 requêtes en rafale puis 10/s par adresse IP, 10 codes faux par IP / 5 min, 12 flux d'événements,
  2 par joueur. La salle se ferme en quittant le quiz ou après 10 min sans activité.
- Côté PIN (app téléphone) : `GET /api/quiz` (salle ouverte ? code) et `POST /api/quiz/open` (ouvre le quiz sur la TV).

## 5. « Mise payante » : jetons virtuels seulement

**Dans ce POC il n'y a aucun paiement ni argent réel** : pas d'intégration de paiement, aucune donnée bancaire ni Mobile
Money. La mise est en **jetons virtuels sans valeur** (1 000 offerts à chaque téléphone, remis à zéro au redémarrage de
l'app), affichés « Jetons virtuels — démo ». Mises égales ; la cagnotte est partagée selon le classement (1 joueur 100 % ;
2 : 70/30 ; 3 et plus : 60/30/10 ; ex æquo à parts égales ; 0 point = rien) ; partie abandonnée = mises rendues.

Tout passe par l'interface `WalletProvider` (`core/.../quiz/Wallet.kt`) ; l'implémentation actuelle est `VirtualWallet`.
**Avant de brancher un vrai prestataire**, vérifier le cadre légal camerounais sur les jeux d'argent et concours (jeu
d'adresse ou de hasard, licence éventuelle, âge minimum, identification KYC, fiscalité), puis implémenter `WalletProvider`
côté serveur (jamais de secret de paiement sur la TV).

## 6. Banque de questions — ajouter vos questions

Fichiers : `android/core/src/main/resources/castbridge/quiz/questions.json` (culture générale) et `questions-school.json`
(parcours scolaires). Tout fichier ajouté à `EmbeddedQuestionSource.DEFAULT_RESOURCES` est fusionné. Une question par ligne :

```json
{"id":"cm-geo-001","track":"general","level":null,"field":null,"region":"CM","category":"Géographie","difficulty":1,
 "question":"Quelle est la capitale politique du Cameroun ?","choices":["Douala","Yaoundé","Garoua","Bamenda"],"answer":1,
 "explanation":"Yaoundé est la capitale politique ; Douala est la capitale économique.","source":"Constitution",
 "review":false,"lang":"fr"}
```

| Champ | Règle |
|---|---|
| `id` | unique et stable (slug ou UUID) ; ne jamais le réutiliser pour une autre question |
| `track` | `general` (défaut), `primary`, `secondary`, `higher` |
| `level` / `field` | niveau (`CM2`, `3e`, `Tle`, `L1`, `Form 5`…) et filière (`droit`, `economie`…) — voir `QuizCatalog` |
| `region` | `CM`, `AF`, `WORLD` (la règle 70/20/10 ne s'applique qu'à `general`) |
| `difficulty` | 1 (facile) à 5 (expert), relatif au niveau pour les parcours scolaires |
| `choices` / `answer` | exactement 4 choix distincts ; `answer` = index 0-3. L'ordre est **mélangé à chaque partie** : pas de « toutes ces réponses » |
| `explanation`, `source` | obligatoires (courtes) |
| `review` | `true` = pas sûr : **exclue du jeu** |

Contrôles : `gradle :core:test` vérifie la banque (4 choix distincts, index valide, pas de doublon d'id ni de question,
champs requis, régions, niveaux connus, assez de questions par région et difficulté). Conseils : faits stables et vérifiables,
pas de chiffres qui changent (population…) ni de dirigeants en poste sans date (« en 2024 »), pas de sujets clivants,
mauvaises réponses plausibles mais clairement fausses.

### Questions marquées `review` (exclues du jeu)
- `cm-sym-007` : établissement et année de composition de l'hymne (École normale de Foulassi, 1928) — à confirmer.
- `cm-sym-008` : premier vers exact de la version anglaise de l'hymne.
- `sup-l1-droit-010` : mode de scrutin présidentiel (« à un tour » vient du Code électoral, pas du texte constitutionnel).

À relire en priorité (non marquées, mais à confirmer par un humain) : `cm-geo-022` (chutes d'Ekom-Nkam près de Melong),
`cm-nat-011` (Debundscha), `cm-sym-009` (balance des armoiries), `cm-lang-008` (« a-ka-u-ku »), `sup-l1-droit-002`
(formulée sur la loi de révision du 18 janvier 1996).

Contenu du POC : 200 questions de culture générale (140 CM, 40 AF, 20 Monde ; 28-29 par difficulté pour le Cameroun, 8
pour l'Afrique, 4 pour le Monde), 120 questions scolaires (CM2, 3e, Tle, L1 droit / économie / mathématiques, 20 chacune).

## 7. Format d'échange avec le futur serveur de questions

La TV ne dépend d'aucun serveur : la banque embarquée suffit et reste le **repli hors ligne**. L'abstraction
`QuestionSource` (`core/.../quiz/QuestionSource.kt`) prévoit la suite :

- `EmbeddedQuestionSource` : fichiers de l'APK (actuel) ;
- `CachedQuestionSource` : banque embarquée **+** un seul fichier cache (`files/quiz/questions-cache.json`, **2 Mo max**)
  rempli par le futur client serveur via `update(json)` ; le cache est validé (taille, version, chaque question), écrit
  atomiquement ; une question du serveur remplace celle de même `id` ; cache absent, trop gros ou corrompu = banque
  embarquée seule ;
- `RemoteQuestionApi` : interface du client à écrire (`fetch(since, page)`).

Document échangé (version **2**, un lecteur accepte sa version et les précédentes) :

```json
{"version":2,"generatedAt":"2026-09-30T10:00:00Z","page":1,"pages":3,
 "questions":[{"id":"6f1c…-uuid","lang":"fr","track":"secondary","level":"3e","field":null,"region":"CM",
   "category":"Histoire","difficulty":3,"question":"…?","choices":["…","…","…","…"],"answer":2,
   "explanation":"…","source":"…","status":"approved","updatedAt":"2026-09-30T09:12:00Z"}]}
```

- `status` : `approved` (jouable) ; toute autre valeur (`draft`, `review`, `rejected`) = exclue, comme `review:true`.
- `updatedAt` (ISO 8601) permet la synchronisation incrémentale ; `lang` prépare d'autres langues (anglais pour les
  parcours anglophones).
- API suggérée : `GET /v1/questions?since=<ISO>&track=&level=&field=&page=` (pagination), éventuellement
  `POST /v1/draws` pour un tirage côté serveur ; la TV garde le tirage local (70/20/10, difficulté croissante, pas de
  répétition dans la session) pour fonctionner hors ligne.

## 8. Technique et ressources

- `:core` (JVM, testé) : `QuizBank` (tirage), `QuizGame` (machine à états Millionnaire, sérialisable en JSON),
  `QuizDuel`, `QuizRoom` (salle), `QuizHttp` (routes), `QrCode` (encodeur QR en Kotlin pur, ~250 lignes, vérifié contre
  la bibliothèque Python `qrcode` ; pas de ZXing), `Wallet`, `HighScores`, `Json` (mini-lecteur, pas de dépendance).
- `:receiver` : `QuizActivity` (vues Android classiques, Canvas et ValueAnimator, aucun moteur de jeu, aucune image),
  `QuizViews`, `QuizSound` (sons **synthétisés** au premier lancement en petits WAV dans le cache, ~200 ko, joués par
  SoundPool ; aucun fichier audio dans l'APK), `QuizHub`.
- `:sender` : `QuizScreen` (Compose) : découverte, ouverture, code automatique, puis la page web `/quiz` dans une WebView.
- APK TV armeabi-v7a : debug 42 773 056 → 43 131 070 octets (+350 ko), release non signé 40 757 700 → 40 864 074
  (+104 ko). Mémoire mesurée sur émulateur TV (arm64, debug) pendant une partie : PSS ≈ 62 Mo.

## 9. Limites du POC et reste à valider sur la vraie TV

Testé : tests JVM (tirage, banque, règles, jokers, duel, salle, HTTP de bout en bout, QR, jetons, scores), parcours complet
sur **émulateur Android TV 1080p** (et 720p) à la télécommande : réglages, salle d'attente, Millionnaire solo, Duel avec 3
joueurs simulés, fin de partie, record ; le QR des captures d'écran est décodé par OpenCV.

À valider sur la TV GaiaOS / Amlogic 32 bits : rendu et fluidité (fond animé, animations), focus D-pad avec la vraie
télécommande, **lisibilité du QR à 2-3 m** avec plusieurs téléphones, latence du multijoueur sur le Wi-Fi réel (SSE à
travers le routeur), sons (SoundPool / sortie HDMI), mémoire réelle (~300 Mo disponibles), et la page web sur des
téléphones variés (iPhone/Safari, Android ancien). L'app téléphone n'a pas été essayée sur un vrai téléphone.

Pistes : questions en anglais (parcours anglophones), plus de niveaux et de filières, serveur de questions, relecture
humaine des questions, sons plus riches, écran « podium » avec confettis, mode équipes, historique des scores par joueur.
