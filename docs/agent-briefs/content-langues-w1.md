# Brief : Langues, vague 1 (A0 à A2 des 7 langues) — session `castbridge-content`

Dans `castbridge-content` (branche de travail autorisée, jamais `main`), comme `content-phase2.md`. Protocole `docs/COORDINATION.md` : rapport vivant `docs/agent-reports/content-langues-w1.md` dans `castbridge-content`. Français, aucun secret, rien sur le serveur de production, rien dans le code des applications.
Lire en premier : **`docs/LANGUES.md` (branche `integration/agents`, §§ 1–13)**, `content/langues/` (budget, paquet d'exemple), `content/graph/langue-*.json`, `tools/content-budget/langues_budget.py`, `tools/langues/` (synthèse vocale).

## Mission
Produire la **vague 1 : niveaux A0, A1, A2** des **7 langues** (chinois mandarin, anglais, allemand, français, italien, espagnol, japonais) : **texte, figures légères, vocabulaire, dialogues**, exercices, auto-contrôles, selon le modèle de contenu de `LANGUES.md`. Langue de départ de l'apprenant : **français** et **anglais** (les deux paires pour chaque langue cible quand cela a un sens). Priorité d'ordre : anglais, français, espagnol, allemand, italien, chinois, japonais (ajuste si le budget l'impose, dis pourquoi).

## Décisions du propriétaire à respecter (voir `LANGUES.md` § 13)
- **Deux familles de lots, jamais mélangées** : **lots libres** (tout ce qui est dérivé d'une source CC BY-SA : licence **CC BY-SA 4.0**, attribution, manifeste de licences par fichier, **non chiffrés**, inclus dans l'**archive publique téléchargeable séparément**) et **lots réservés** (contenu **original** écrit par nous, ou domaine public / CC0). Chaque fichier déclare sa famille et sa licence ; l'outil de budget **échoue** si un lot mélange les deux ou si une source n'a pas de licence.
- **Médias** : quota du téléphone pour les Langues = **500 Mo** (réglable) ; lots média ≤ 100 Mo, lots texte de la TV ≤ 3 Mo. **Voix : synthèse libre, clairement marquée « voix synthétique »** (phase actuelle, petit budget) ; si `espeak-ng` ou un modèle libre est disponible dans ta session, produis l'audio court des mots et des phrases clés (Opus bas débit), sinon **n'en promets pas** et laisse les références audio vides dans le manifeste ; pas de vidéo de personnes.
- **Chinois et japonais** : écriture + pinyin / kana + romaji, tons, ordre des traits en **figures vectorielles légères** (≤ 8 Ko) ; rappelle dans le rapport que le **rendu sur la TV 32 bits n'est pas vérifié** (polices CJK).
- Tout est « **bêta : non validé** » ; aucun identifiant réutilisé ou renommé ; points douteux listés par paquet pour les vérificateurs.

## Contrôles à chaque lot
`content-budget` (étendu aux langues), les validateurs Python du dépôt, `LanguesTest` côté CastBridge n'étant pas exécutable chez toi : dis ce qui n'a pas été exécuté. Un commit par langue et par niveau, jalon dans le rapport. Budget de la vague 1 : à fixer d'après `budget.json` (rapporte les tailles réelles).

## Priorité
Cette vague passe **avant** la suite de `content-phase2.md` (Quiz N2/N4 et autres classes) : termine le jalon en cours puis commence les Langues ; reprends ensuite la phase 2.

## Coordination
Relis la section ci-dessous à chaque jalon.

## Réponses du coordinateur
(aucune pour l'instant)
