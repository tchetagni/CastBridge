# Cahier wtv-01 — Options multimédia du lecteur de CastBridge-TV

**Modèle** : sonnet · **Groupe** : lecteur TV (après la barre de boutons 0.14.30) · **Jauge** : 600 k entrée / 30 k sortie · **Audit Opus** : échantillon (aucune donnée sensible ; risque = performance et régressions de lecture)

## 0. Contexte et décision
Demande du propriétaire (2026-10-04) : « tu devrais ajouter plein d'options multimédia comme avec la majorité des lecteurs » ; la liste complète ci-dessous est **acceptée** (« garde la liste complète »). Exception au gel des écrans : accordée pour le lecteur.
Le lecteur sait déjà : pistes audio et sous-titres, fichiers de sous-titres externes, décalages audio/sous-titres, taille des sous-titres, vitesse, format d'image, chapitres, titres, décodeur matériel/logiciel, égaliseur à préréglages, répétition, file de lecture, affichage (Remplir/Ajusté/Étirer/Natif), contrôle à distance par commandes (`PlayerCommand`). Ne rien dupliquer : étendre.
**Prérequis** : la livraison de l'agent de la barre de boutons (boutons Audio · Sous-titres · Affichage · Infos, ligne de diagnostic, légende des icônes) est fusionnée ; partir de `integration/agents` à jour (`git reset --hard integration/agents` si le worktree part d'un vieux commit).

## 1. Matériel visé
TV de référence GaiaOS 32 bits, 720p, armeabi-v7a, télécommande à 5 touches (pas de MENU/INFO garantis) ; **toutes les TV** sont visées. Chaque filtre d'image coûte du processeur : **désactivé par défaut**, jamais d'effet dégradant au repos, et une garde de performance (si le décodeur perd des images, le filtre se coupe seul et le dit).

## 2. Fonctions à livrer (toutes accessibles à la télécommande à 5 touches et au contrôle à distance)
1. **Reprise de lecture** : mémoriser la position par fichier ; au lancement proposer « Reprendre à 55:04 / Recommencer » (choix par défaut : reprendre ; ignorer sous 30 s et au-delà de 95 %).
2. **Sauts** : gauche/droite = ±10 s, appui long ou deux appuis rapides = ±30 s ; réglage du pas (10/20/30 s) ; vidéo **suivante/précédente** de la file.
3. **Minuteur d'arrêt** : 15, 30, 60, 90 min, fin de la vidéo ; compte à rebours discret ; à l'échéance : fondu du son puis pause (jamais d'extinction de la TV).
4. **Boucle A–B** et **marque-pages** (jusqu'à 20 par fichier, mémorisés).
5. **Image** (hors défaut) : luminosité, contraste, saturation, gamma ; zoom et déplacement (1×–3×) ; désentrelacement (auto/off) ; rotation 90°. Un « Réinitialiser l'image ». Garde de performance décrite au § 1.
6. **Son** : normalisation du volume « mode nuit » (compression de dynamique), amplification jusqu'à 200 % avec avertissement, vitesse sans changement de hauteur.
7. **Sous-titres** : couleur, contour/ombre, position verticale, encodage des caractères (auto, UTF-8, Latin-1, Windows-1252, etc.), police de repli ; aperçu.
8. **Lecture automatique de l'épisode suivant** d'un même dossier (compte à rebours de 8 s annulable), désactivable.
9. **Sous-titres du dossier** : recherche des fichiers `.srt/.ass/.sub/.vtt` du dossier du fichier (même nom de base ou un seul fichier), et **mémorisation de tous les réglages par fichier** (clé stable, comme `fm=` déjà utilisée), avec « Comme le réglage par défaut ».
10. **Écran « Réglages du lecteur »** regroupant tout, par rubriques (Lecture · Image · Son · Sous-titres · Affichage), navigable aux flèches, chaque ligne avec libellé français, valeur courante et état « par défaut ».

## 3. Règles
- Tests d'abord, RED par assertion collé dans le rapport (W15 R1) pour toute logique **pure** (en `android/core/.../tv/` : états, bornes, mémorisation par fichier, minuteur, boucle A–B, règles de reprise, choix de l'épisode suivant, garde de performance) ; l'affichage Android n'est vérifié que par compilation : le dire.
- Aucune régression : la lecture, la pause, la reprise de copie en cours (« lecture pendant la copie »), le contrôle à distance et la logique d'affichage `VideoFit` restent identiques ; les suites `castbridge.core.tv.*` et `castbridge.receiver` compilent.
- Chaque nouvel indicateur d'écran suit la règle du lecteur : libellé texte, icône standard, contraste ≥ 4,5:1, zone de sécurité de 5 %.
- Aucun secret, aucun réseau nouveau, aucune télémétrie nouvelle ; réglages stockés localement.
- Commandes à distance : étendre `PlayerCommand`/`PlayerTracks` de façon **additive** (anciens téléphones et page web non cassés ; clés JSON nouvelles ignorées par les anciens).
- Mutations : ≥ 6 mutations appliquées puis retirées, toutes détectées (reprise à 100 %, minuteur à 0, bornes de luminosité, boucle A>B, épisode suivant hors du dossier, mémorisation par fichier croisée entre deux fichiers).

## 4. Fichiers possédés
`android/core/src/main/kotlin/castbridge/core/tv/` (nouveaux fichiers : `ResumePolicy`, `SleepTimer`, `LoopAB`, `Bookmarks`, `PictureTuning`, `AudioTuning`, `SubtitleStyle`, `NextEpisode`, `PlayerPrefs`) ; leurs tests ; `android/receiver/src/main/kotlin/castbridge/receiver/` (`PlayerActivity` et ses extras : câblage uniquement, le plus petit changement possible) ; `docs/TV-PLAYER.md` ; `docs/test-plans/PARCOURS-CRITIQUES.md` (nouveau parcours) ; ligne de l'index.

## 5. Critères d'acceptation
Tests purs verts ; `:core:test --tests 'castbridge.core.tv.*' :receiver:compileDebugKotlin` sortie 0 ; suite complète `:core:test` verte ; documentation du lecteur à jour avec la table « touche → action » pour la télécommande à 5 touches ; liste de ce que seule une vraie TV peut confirmer (performance des filtres sur 32 bits, désentrelacement, normalisation).
