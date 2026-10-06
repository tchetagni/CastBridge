# Publier le guide utilisateur

Le serveur sert le guide en page libre (sans authentification) : `https://bridge.sti-cm.com/guide/`
(`/guide` redirige en 301 vers `/guide/`). Le dossier est `${castbridge.guide.dir}` (défaut `/data/apk/guide`, dans le volume
Docker `castbridge-apk`, comme `/dl/`). Seul `index.html` est requis ; le guide est un seul HTML, CSS en ligne, images en `data:`, sans script.
Fichiers annexes possibles (extensions html, css, js, png, jpg, webp, svg, json, txt) : `/guide/<fichier>`.

## Publier ou mettre à jour

```sh
scp docs/guide-utilisateur/guide-utilisateur.html ubuntu@bridge.sti-cm.com:/tmp/guide.html
# puis sur le serveur :
sudo docker exec castbridge-api mkdir -p /data/apk/guide
sudo docker cp /tmp/guide.html castbridge-api:/data/apk/guide/index.html
sudo docker exec castbridge-api ls -l /data/apk/guide/
rm /tmp/guide.html
```

Pièges connus : le conteneur tourne sous l'uid 10001 sans capacités root. Créer le dossier AVEC l'utilisateur du conteneur
(`docker exec … mkdir`, pas sur le disque de l'hôte), puis y déposer le fichier par `docker cp` (même méthode que `/data/apk/lots/`,
voir `docs/DEPLOIEMENT-PREMIERE-EXPERIENCE.md`). Si le fichier copié n'est pas lisible (`ls -l`) :
`sudo docker exec -u 0 castbridge-api chown 10001:10001 /data/apk/guide/index.html`. Pas de redémarrage : le fichier est relu à
chaque requête (cache navigateur 5 min pour `index.html`, 24 h pour le reste).

## Vérifier

```sh
curl -I https://bridge.sti-cm.com/guide/    # 200, text/html;charset=UTF-8, Cache-Control: public, max-age=300, ETag
curl -sI https://bridge.sti-cm.com/guide    # 301 vers /guide/
```

Sans fichier publié : 404 « Guide non publié ». La route est limitée par IP comme `/api` (`RateLimitFilter`).
