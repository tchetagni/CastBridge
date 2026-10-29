# Consigne du propriétaire à Astra — mini-vidéos chinois A0 (2026-10-29)

« Il fallait aussi des mini-vidéos générées par Astra. » Suite de ton travail sur les quatre packs chinois A0 francophones (`content/langues/zh-a0-{salut,phonetique,nombres,famille}-fr`, voir `work/astra-zh-a0/PASSATION.md` et `AUDIT.md`).

## Ce que tu produis
4 à 6 mini-vidéos pédagogiques, une au moins par pack, par exemple :
- **Salutations** : une scène courte de rencontre (arrivée, salut de la main, sourire, au revoir), cohérente avec ton illustration `zh-a0-salut-astra-rencontre`.
- **Phonétique** : les 4 tons + ton neutre : une courbe qui se trace sur l'échelle de hauteur, ton par ton (rendu vectoriel déterministe, exact, pas d'image générée approximative).
- **Nombres** : compter de 1 à 5 (puis 6 à 10) avec des objets qui apparaissent un par un (cahiers, mangues…), et éventuellement les gestes chinois de la main pour 6-10 seulement si tu peux les rendre justes.
- **Famille** : présentation des membres un par un (爸爸, 妈妈, 哥哥, 姐姐, 弟弟, 妹妹) dans le style de ton illustration de famille.

## Méthode
- Génère les images clés avec ton outil d'images (style cohérent avec tes 3 illustrations), et/ou dessine en vectoriel (SVG → PNG) ce qui doit être exact (courbes de tons, chiffres arabes, objets à compter).
- Assemble avec `ffmpeg` (installé : /opt/homebrew/bin/ffmpeg, libx264) : fondus, apparitions successives, léger zoom ; aucune animation qui déforme les personnages.
- **Aucun caractère chinois ni pinyin dessiné par le générateur d'images.** Si une vidéo doit montrer 你好, 三, mā…, rends le texte toi-même par une police système qui contient les caractères (vérifie le rendu, ex. PingFang SC sur macOS : `fc-list :lang=zh` ou `/System/Library/Fonts/`) et les signes diacritiques du pinyin (ǎ, ǚ…) ; sinon pas de texte dans la vidéo.
- Vidéos **muettes** (aucun outil de voix disponible ; pas d'audio factice, pas de voix macOS) : prévois le minutage pour qu'un audio de prononciation puisse s'y caler plus tard, et note-le.

## Format (impératif)
MP4 H.264 (`-pix_fmt yuv420p -movflags +faststart`), ≤ 480p (854×480 ou 640×360), ≤ 20 s chacune, 24 ou 25 i/s, ≤ 1,5 Mo chacune, ≤ 6 Mo au total. Lisible sur TV 720p à distance : gros éléments, contrastes forts, rien d'essentiel près des bords.

## Où écrire (impératif)
Uniquement sous `work/astra-zh-a0-video/` : `out/*.mp4`, les sources (images clés, SVG, scripts de montage reproductibles), `PROMPTS.md`, et `PASSATION.md`. **Ne modifie PAS** `content/`, les `media.json`, ni le code : un autre agent travaille dessus en ce moment. Dans la passation, propose pour chaque vidéo : identifiant `m:…`, pack et unité où l'accrocher (vocabulaire, dialogue ou explication), `alt`, durée, taille, provenance « synthétique (Astra, image_gen + ffmpeg) », statut « bêta : non validé ». Ajoute 3 vignettes PNG (une image représentative de chaque vidéo) dans `work/astra-zh-a0-video/apercus/` pour que le propriétaire juge vite. Termine par un auto-bilan honnête.
