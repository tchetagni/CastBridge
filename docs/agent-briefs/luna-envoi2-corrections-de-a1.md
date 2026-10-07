# À coller tel quel chez Luna (GPT 6) — contrôle outillé du lot `de-a1-ma-famille-fr` (envoi 1) → envoi 2 attendu

Luna, merci pour le lot complet : 9 leçons, 108 items (ton annonce est exacte), 66,7 % de réemploi (exact), 245 identifiants uniques, 85 références de médias toutes déclarées, un identifiant = un texte, **0 écart** sur les articles et majuscules des noms, aucun secret. Ce contrôle est **outillé** (le vrai lecteur du cœur `LangPackJson`/`LangValidator` et l'outil `mp.py`) : il ne porte aucune appréciation pédagogique, celle-ci sera l'audit de Gemini, après ton envoi 2.

**Verdict : NON CHARGEABLE par la TV** (3 défauts mécaniques), plus des écarts de format et de cahier. Livre un **envoi 2 = lot complet corrigé** : `langue.json` + `media.json` seulement (ne fournis plus `media-requests.jsonl` : l'outil le génère depuis `langue.json` et fait foi), même mode (déclaratif, aucun média produit, `state: "review"`, aucun mot réservé « validé / natif / naturel »).

## A. Bloquants (rien ne se charge tant qu'ils restent)
1. **Thème sans tiret.** Le lecteur découpe l'identifiant en 4 segments `<cible>-<niveau>-<thème>-<départ>`, thème `[a-z0-9]{1,16}`. `de-a1-ma-famille-fr` a 5 segments. ⇒ `$.id` = `de-a1-mafamille-fr`, `$.theme` = `mafamille`, dossier identique, et le préfixe des 245 identifiants (`units`, `vocab`, `dialogues`, `grammar`, `exercises`, `cards`, `stories`).
2. **`media.json` : `file` et `bytes` obligatoires** (91 entrées à `file: null`, sans `bytes`). ⇒ `"file": "audio/<id>.opus"`, `"bytes"` estimé > 0 (16 kbit/s pour un mot, 24 kbit/s pour une phrase ou un dialogue) et `"durationMs"` estimé ; `mp.py receive` remplacera par les mesures des fichiers acceptés.
3. **`prerequisites` = unités du même paquet seulement.** `$.units[0].prerequisites` = `["de-a0-nombres-fr", "de-a0-salut-fr-u1"]` est refusé. ⇒ `[]` pour l'unité 1 (les unités 2 à 9 sont bonnes) ; les liens vers l'A0 et le graphe vont dans le champ additif `prereq` (ignoré du lecteur, lu par l'outillage).
Essai hors dépôt avec ces 3 corrections : `parse OK, validate : 0 problème`.

## B. Format du lecteur (le contenu existe mais n'est pas lu, ou pas accepté)
4. `wrongWhy` : **liste alignée sur `choices`** (`"—"` sur la bonne réponse), pas une chaîne (37 exercices).
5. `order` (`…-x3`, 9 fois) : `words` = **les mots mélangés d'une vraie phrase**, `answers` = `["la phrase exacte"]`. Aujourd'hui `answers` = 3 morceaux (`saluer`, `donner une information`, `répondre ou relancer`) : la correction compare la saisie à chaque élément **entier**, aucun arrangement n'est accepté.
6. Histoire L9 (`$.units[8].stories[0]`) : le lecteur lit `paragraphs` = liste de `{"who": "", "text": "…", "reading": null, "tr": "…", "audio": "m:…"}` ; tes clés `text`/`translation`/`questions` sont ignorées : l'histoire s'affiche **vide**. Les 5 questions deviennent des exercices (`mcq`/`truefalse`) placés après l'histoire.
7. `dialogues[*].audio` absent dans les 9 dialogues : la TV joue l'audio **d'ensemble** du dialogue ⇒ `"audio": "m:de-u1-d1"` sur chaque dialogue, média déclaré (`voiceClass` A+B).
8. **Clip court de la leçon** (le propriétaire y tient) : il se rattache au dialogue par `"video": "m:de-u1-d1-clip"`, déclaré dans `media.json` en `"kind": "video"` (estimation, `status: draft-not-authorized`) ; le scénario reste dans `scenario-clip.md`, **un par leçon** (9) ; production dès l'accord sur le moteur vidéo.
9. `vocab[*].reading` : affiché tel quel par la TV ⇒ `/ˈhaloː/` sans le préfixe `IPA ` (ou omets le champ).
10. **Identifiants de médias en ASCII translittéré** : ö→oe, ü→ue, ä→ae, ß→ss (`de-moegen`, `de-derschueler`, `de-derfussball`, `de-dergrossvater`, `de-diegrossmutter`, `de-ich-heisse-amina-u1-a`, `de-wie-heisst-du-u2-a`, `de-ich-bin-zwoelf-jahre-alt-u1-b`…) : 14 identifiants ont **perdu** leurs lettres au lieu de les translittérer (collisions possibles).
11. **Audio d'exercice** : tout exercice qui a `audio` porte le champ additif `"text": "<mots dits exactement>"`, et cet audio a **son propre identifiant** (`m:de-hallo-x1`, `m:de-bitte-paire`…), jamais celui d'un mot du vocabulaire (9 conflits sur `de-hallo` ; 16 demandes bloquées « texte non déterminable » : les 10 questions orales `de-a1-oral-q01…q10` et les 6 `…-contrast`).
12. Questions orales L9 (`…-u9-l01…l10`) : clés `repeat`/`text` inventées ⇒ `"kind": "speak"`, `prompt` = « Écoute la question et réponds à voix haute », `audio` = la question, `text` = son texte (additif), `model` = une réponse modèle.
13. Les 6 audios du **second** mot des paires (`de-biete-contrast`, `de-staat-contrast`…) sont déclarés mais **aucun exercice ne les référence** : emploie-les (point 17) ou retire-les.
14. `media.json` `$.comment` : retire le mot « validé » (réservé, même en formule négative).
15. `figures.json` : rattache chaque figure à un item (`vocab[].image` = `m:…`, média `"kind": "image"` déclaré) ou retire-la ; aujourd'hui aucune n'est reliée à `langue.json`.

## C. Cahier A1 (instruction § 2-3)
16. **Mots réellement nouveaux : 33** (41 distincts − 8 déjà en A0) pour une cible de **50-70** ; items 108 pour 110-130. Le réemploi (66,7 %) est tenu, mais **toujours par les 8 mêmes ancres** (`Hallo`, `Danke`, `Bitte`, `eins`, `zehn`, `die Familie`, `der Vater`, `wohnen` : 72 des 108 occurrences) et **les 4 items propres d'une leçon ne reviennent jamais dans la suivante** ⇒ 6 à 8 mots nouveaux par leçon (L2-L9), chaque leçon réemploie d'abord les items propres de la précédente, les ancres tournent.
17. **Paires minimales** : le trait distinctif doit être **dans `explanation`** (il n'est que dans `plan-unite.md`) ; au moins un mot de la paire connu de l'apprenant à ce stade, sinon « (son seul) » dans le prompt ; `kommen/können` = 3 lettres d'écart : pas minimale ⇒ p. ex. `schon/schön` ; L2=L5, L3=L8, L4=L9 identiques ⇒ 9 paires distinctes, ou la reprise fait entendre **l'autre** mot (ce qui emploie les 6 audios du point 13).
18. **Exercices identiques dans les 9 leçons** (x1 « Hallo » et x3 `order`, mêmes textes, mêmes audios) : chaque exercice porte sur les items **de sa leçon**.
19. Un même texte sous plusieurs identifiants (`Ich bin zwölf Jahre alt.` ×2, `Wie alt bist du?` ×3, `Wie heißt du?` ×2) ⇒ un identifiant par texte exact, réutilisé partout.

## D. Ce qui est bon : à garder tel quel
`format` 1, `type` « langue », `version` 1, `level` « A1 », `state` « review » ; 12 items par leçon ; dialogues de 3-4 répliques ; cartes rattachées au vocabulaire ; 64 exercices tous avec `explanation` ; QCM à index valides ; licence `CASTBRIDGE-ORIGINAL` ; variété de-DE, registre lent, voix A/B des répliques : 0 écart avec l'outil ; articles et majuscules des noms : 0 écart.

Suite : envoi 2 → même contrôle outillé → audit pédagogique par Gemini. Ton audit du lot chinois de Gemini commencera quand ses 8 leçons au schéma réel seront reçues (un seul message de ma part).
