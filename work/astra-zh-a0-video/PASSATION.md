# Passation Astra → Claude — mini-vidéos chinois A0

29 octobre 2026. **Cinq vidéos livrées ; bêta : non validé.** Exclusivement sous `work/astra-zh-a0-video/`. Aucun changement des packs, manifestes, sources applicatives ou versions. Aucune compilation Gradle, aucun test long, émulateur, commit, push ou publication.

## Changements et propositions d’intégration

Les identifiants suivants sont proposés, pas enregistrés dans les manifestes. Les emplacements de vocabulaire sont des points d’accroche pédagogiques ; Claude doit vérifier le support réel des vidéos dans le format et l’interface avant de les raccorder.

| Identifiant proposé / fichier | Pack / unité / point d’accroche | Durée | Octets | Alt proposé |
|---|---|---:|---:|---|
| `m:zh-a0-salutations-astra` / `out/zh-a0-salutations-astra.mp4` | `zh-a0-salut-fr` / `zh-a0-salut-fr-u1` ; vocabulaire v1–v3 | 9 s | 64 686 | Une illustration de rencontre accompagne successivement 你好  谢谢 et 再见  avec pinyin et traduction française. |
| `m:zh-a0-tons-astra` / `out/zh-a0-tons-astra.mp4` | `zh-a0-phonetique-fr` / `zh-a0-phonetique-fr-u1` ; explication des quatre tons et du ton neutre  vocabulaire u1-v1–u1-v5 | 18 s | 93 295 | Les courbes des quatre tons se tracent sur une échelle de hauteur de 1 à 5  puis un écran explique le ton neutre contextuel. |
| `m:zh-a0-nombres-1-5-astra` / `out/zh-a0-nombres-1-5-astra.mp4` | `zh-a0-nombres-fr` / `zh-a0-nombres-fr-u1` ; vocabulaire v2–v6 | 15 s | 47 644 | Des cahiers apparaissent un par un de un à cinq  avec chiffre  caractère chinois et pinyin. |
| `m:zh-a0-nombres-6-10-astra` / `out/zh-a0-nombres-6-10-astra.mp4` | `zh-a0-nombres-fr` / `zh-a0-nombres-fr-u1` ; vocabulaire v7–v11 | 15 s | 52 337 | Six cahiers sont visibles  puis un cahier supplémentaire apparaît à chaque étape jusqu’à dix ; chiffre  caractère et pinyin accompagnent le comptage. |
| `m:zh-a0-famille-astra` / `out/zh-a0-famille-astra.mp4` | `zh-a0-famille-fr` / `zh-a0-famille-fr-u1` ; vocabulaire v2–v7 | 18 s | 128 856 | Six mots de famille défilent avec caractères  pinyin et traduction ; les frères et sœurs sont distingués selon leur âge par rapport à moi. Une illustration familiale sert de contexte. |

Toutes : MP4 H.264, **854×480, 24 i/s, yuv420p, faststart**, une seule piste vidéo, sans audio. Total : **386,818 octets** (moins de 0,4 Mo), contre 6 Mo autorisés ; chacune sous 1,5 Mo et 20 s.

Provenance de la livraison : **synthétique (Astra, image_gen + ffmpeg)**. Pour Salutations et Famille : illustrations image_gen du chantier précédent réutilisées, montage Astra avec ffmpeg. Pour Tons et les deux Nombres : provenance individuelle exacte **synthétique (Astra, dessin déterministe + ffmpeg)** ; aucune image générative dans ces trois vidéos. Statut individuel pour les cinq : **bêta : non validé**. Aucun nouvel appel image_gen.

## Minutage pour une future prononciation

Le fichier `sources/minutages.json` donne chaque écran, le texte, le pinyin lexical et une fenêtre vocale proposée. Ce ne sont pas des sous-titres synchronisés à une voix existante.

- Salutations : 0–3 s 你好 ; 3–6 s 谢谢 ; 6–9 s 再见.
- Tons : 0–3,6 s mā ; 3,6–7,2 s má ; 7,2–10,8 s mǎ ; 10,8–14,4 s mà ; 14,4–18 s nǐ hǎo ma. Tracé de chaque courbe entre début +0,4 s et début +2,05 s. La vitesse du tracé est pédagogique, pas une mesure de durée syllabique.
- Nombres 1–5 : un mot toutes les 3 s, yī / èr / sān / sì / wǔ. Nombres 6–10 : même cadence, liù / qī / bā / jiǔ / shí. La deuxième vidéo commence à six, en continuité avec la première.
- Famille : 0–3 s bàba ; 3–6 s māma ; 6–9 s gēge ; 9–12 s jiějie ; 12–15 s dìdi ; 15–18 s mèimei.

Proposer la voix entre début +0,6 s et fin −0,5 s de chaque écran, puis laisser une pause. Ajuster à l’enregistrement naturel ; ne pas étirer les tons pour remplir la fenêtre. nǐ hǎo est écrit lexicalement ; faire valider la réalisation ní hǎo. Garder les syllabes neutres brèves, 一 au premier ton dans l’énumération. Pour l’audio final, retirer les mentions « vidéo muette » / « écouter plus tard » du rendu et refaire les contrôles.

## Fichiers livrés

- `out/zh-a0-{salutations,tons,nombres-1-5,nombres-6-10,famille}-astra.mp4` : les cinq vidéos.
- `sources/render.py` : dessin géométrique, composition, encodage, contrôles techniques et extraction des vignettes ; commande reproductible dans `PROMPTS.md`.
- `sources/zh-a0-salut-astra-rencontre.webp`, `sources/zh-a0-famille-astra-scene.webp` : copies autonomes des images existantes, non modifiées.
- `sources/PROMPTS-ILLUSTRATIONS.md` : archive des prompts d’origine ; `PROMPTS.md` : choix du montage et provenance exacte.
- `sources/verification-polices.png` : caractères chinois et accents, y compris ǎ et ǚ ; `sources/minutages.json` : repères audio futurs.
- `apercus/01-salutations-famille.png`, `apercus/02-tons.png`, `apercus/03-nombres.png` : **trois planches PNG extraites des MP4**, couvrant les cinq vidéos. La demande de trois vignettes pour au moins quatre vidéos est résolue en regroupant plusieurs captures par planche.
- `VERIFICATIONS.json` : paramètres, tailles, SHA-256, contrôle faststart et décodage ; cette `PASSATION.md`.
- `CONSIGNE.md` préexistait et reste intacte.

## Contrôles réalisés

ffprobe : formats, dimensions, cadence, durées, absence de piste audio et budgets conformes. Décodage complet des cinq courts MP4 sans erreur ffmpeg. Vérification des atomes MP4 : `moov` avant `mdat`. Inspection visuelle des trois planches extraites et de la planche des polices : textes non tronqués, caractères et accents visibles, courbes distinctes, cahiers séparés et dénombrables. `git diff --check` exécuté. Aucune validation linguistique native ou sur appareil.

## Vérifications attendues de Claude

1. Relecture native enseignante : pinyin, neutres familiaux, contours schématiques 55/35/214/51, troisième ton isolé et sandhi de nǐ hǎo. Valider les modèles vocaux avant tout ajout audio.
2. Intégrer les cinq fichiers aux bons lots, créer les entrées et références supportées, préserver les anciens médias ; vérifier licences, alt, crédit synthétique, statut bêta, empreintes et budgets des lots. Les identifiants proposés ne sont pas encore actifs.
3. Contrôler la lecture, pause/reprise, retour et absence d’attente audio sur CastBridge (téléphone) et CastBridge-TV ; vérifier le comportement sans lot vidéo. Les tests et constructions nécessaires restent à la charge de Claude.
4. Sur un vrai écran TV 720p, vérifier à distance les caractères, diacritiques et textes secondaires, les zones de sécurité et la navigation à la télécommande. Les captures sur ordinateur ne certifient pas cette lisibilité.
5. Vérifier pédagogiquement le rythme de trois secondes ; proposer pause/relecture pour les débutants. Pour la future voix, revoir montage, cadence et mentions affichées ensemble.

## Auto-bilan honnête

Les cinq fichiers sont de vraies vidéos MP4, compactes et reproductibles. Les tons se tracent et les cahiers s’ajoutent réellement ; les autres séquences sont des cartes successives sur illustrations fixes avec fondu d’entrée. La rencontre ne joue pas physiquement une arrivée et un départ : le changement de formule apporte le contexte. La famille montre six **termes**, pas six personnages présentés individuellement ; l’illustration à quatre personnages ne prouve ni leur rôle ni leur aînesse. Les relations d’âge sont donc données par le texte. Ce choix évite d’inventer des liens familiaux ou de déformer les personnages, mais reste moins narratif qu’une animation dédiée.

Aucun geste de comptage chinois, aucune voix et aucune évaluation de prononciation. Le ton neutre est expliqué sans courbe universelle, et les contours isolés restent schématiques. La validation native, la lisibilité à distance et l’intégration restent ouvertes ; ces vidéos ne sont pas annoncées comme déjà accessibles dans l’application.
