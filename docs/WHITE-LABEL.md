# Marque blanche (white label)

Conception du 2026-10-24. Premier client : Hassan, produit **EduPlay** de **TechnoHub Enterprise** (branche `client/hassan-eduplay`).

## Décisions du propriétaire (2026-10-24)

| # | Décision |
|---|---|
| WL-1 | Le mécanisme de marque est fait **une seule fois sur le tronc** (branche `claude/white-label`) ; chaque client a **sa branche exclusive** `client/<client>-<marque>` qui ne contient que ses fichiers de marque (`branding/clients/<marque>/`) et suit le tronc par simple fusion, sans conflit. |
| WL-2 | Les apps d'un client sont **séparées** : identifiants d'app propres, clé de signature propre, installables à côté de CastBridge sur le même appareil. |
| WL-3 | Serveur : **notre serveur** (bridge.sti-cm.com) avec un **espace séparé** par client (codes d'activation, APK, guide). Phase 2. |
| WL-4 | Les éléments de marque sont **fournis par le client** ; nous n'en redessinons aucun (charte EduPlay § 3). |

## Principe

Une propriété Gradle `-Pbrand=<marque>` choisit la marque. Sans elle (ou `-Pbrand=castbridge`), la construction est **identique à aujourd'hui** : mêmes tâches, mêmes noms d'APK, mêmes identifiants, même rendu.

Avec `-Pbrand=eduplay`, Gradle lit `branding/clients/eduplay/brand.properties` et :

1. remplace `applicationId` (téléphone, TV) ;
2. expose la marque au code : `BuildConfig.BRAND_ID`, `BRAND_NAME`, `BRAND_TV_NAME`, `BRAND_OWNER`, `BRAND_TAGLINE`, `SUPPORT_PHONE`, et `DEFAULT_SERVER` si `server.url` est rempli ;
3. superpose les ressources `branding/clients/eduplay/android/<module>/res` **par le jeu de sources du type de build** (`debug`, `release`), qui a priorité sur `main` sans renommer aucune tâche : couleurs, icônes, logos, polices, `app_name` ;
4. nomme les APK `<marque>-…` au lieu de `castbridge-…` ;
5. lit les clés d'activation de confiance dans `~/.castbridge-signing/<marque>/activation-trusted-keys.txt` (jamais celles de CastBridge : une TV EduPlay ne s'active pas avec un code CastBridge, et inversement).

## Ce qui change de nom, ce qui n'en change pas

- **Change** : tout texte vu par l'utilisateur (« CastBridge », « CastBridge-TV », libellés du manifeste, notifications, guide intégré, écran « À propos »), icônes, logos, couleurs, polices.
- **Ne change pas** : paquets Kotlin (`castbridge.*`), noms internes d'actions d'intent, clés de préférences, format des fichiers. Ce sont des identifiants, pas de la marque.
- **À trancher (phase 2)** : identifiants de découverte (nom de service Bluetooth, Wi-Fi Direct, mDNS). Proposition : les préfixer par `BRAND_ID` pour qu'un téléphone EduPlay ne voie que des TV EduPlay. Le recensement est fait en phase 1, le changement attend la décision.

## Fichiers d'une marque

```
branding/clients/<marque>/
  brand.properties        identité, identifiants, serveur, support
  CHARTE.md               charte reçue du client (référence)
  source/                 fichiers maîtres reçus du client, intouchés
  android/sender/res/     superposition téléphone
  android/receiver/res/   superposition TV
```

## Phases

1. **Mécanisme** (tronc) : propriété `brand`, superposition des ressources, textes visibles tirés de `BuildConfig`/ressources, CastBridge inchangé (vérifié par comparaison des APK avant/après).
2. **EduPlay** (branche client) : `brand.properties`, couleurs et polices (Poppins, Inter), icônes et logos à partir des fichiers fournis, construction signée avec la clé EduPlay.
3. **Serveur** : espace `/eduplay/` (activation, mises à jour, guide), outil d'activation avec clé propre.
4. **Découverte isolée** par marque (après décision).

## Bloquants côté client (EduPlay)

- Logo **vectoriel** (SVG ou PDF) : seuls des JPG ont été reçus ; l'icône d'app et la bannière TV ne peuvent être produites fidèlement qu'à partir du fichier maître.
- Version **compacte** / icône d'application validée par le client (la charte la demande, elle n'existe pas encore).
- Numéro et canal de support EduPlay.
- Identifiants d'app définitifs (proposés : `com.technohub.eduplay` et `com.technohub.eduplay.tv`).
