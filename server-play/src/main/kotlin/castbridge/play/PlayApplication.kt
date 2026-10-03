package castbridge.play

/**
 * Point d'entrée : `java -jar castbridge-play.jar --server.port=8090`. Réglages par `CASTBRIDGE_PLAY_*` (voir README). Le service ne lit AUCUN secret : seulement
 * des clés PUBLIQUES de tickets. Il écoute sur la boucle locale du conteneur ; ne jamais publier le port hors de 127.0.0.1.
 */
fun main(args: Array<String>) {
    val cfg = PlayConfig.fromEnv(args = args)
    val server = PlayServer(cfg).start()
    System.err.println("castbridge-play ${cfg.version} : écoute sur ${cfg.bind}:${server.port} (salles max ${cfg.maxRooms}, connexions max ${cfg.maxConnections})")
    Runtime.getRuntime().addShutdownHook(Thread { server.close() })
    Thread.currentThread().join()
}
