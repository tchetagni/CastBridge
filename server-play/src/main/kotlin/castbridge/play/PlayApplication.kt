package castbridge.play

/**
 * Point d'entrée : `java -jar castbridge-play.jar --server.port=8090`. Réglages par `CASTBRIDGE_PLAY_*` (voir README). Le service ne lit AUCUN secret : seulement
 * des clés PUBLIQUES de tickets. Il écoute sur la boucle locale du conteneur ; ne jamais publier le port hors de 127.0.0.1.
 * Il REFUSE de démarrer sans `CASTBRIDGE_PLAY_TRUSTED_PROXIES` (ou avec une entrée invalide), sauf `CASTBRIDGE_PLAY_DIRECT=1` (staging, tests).
 */
fun main(args: Array<String>) {
    val cfg = try { PlayConfig.fromEnv(args = args) } catch (e: IllegalStateException) {
        System.err.println("castbridge-play : configuration refusée : ${e.message}")
        kotlin.system.exitProcess(2)
    }
    val server = PlayServer(cfg).start()
    System.err.println("castbridge-play ${cfg.version} : écoute sur ${cfg.bind}:${server.port} (salles max ${cfg.maxRooms}, connexions max ${cfg.maxConnections})")
    Runtime.getRuntime().addShutdownHook(shutdownHook(server, 25_000))
    Thread.currentThread().join()
}

/** Crochet SIGTERM : maintenance annoncée aux salles, [graceMs] de grâce, puis arrêt (docker : `stop_grace_period: 30s` ; déployer HORS PARTIE). */
fun shutdownHook(server: PlayServer, graceMs: Long): Thread = Thread({ server.drain(graceMs) }, "play-shutdown")
