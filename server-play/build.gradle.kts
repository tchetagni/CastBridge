import java.time.Duration

plugins {
    kotlin("jvm")
    application
}

// Service castbridge-play (docs/PLAY-PROTOCOL.md) : serveur HTTP/WebSocket écrit sur le JDK seul (aucune dépendance hors :core),
// hébergeant ServerRoom du cœur. `gradle :server-play:test`, `gradle :server-play:fatJar` (build/libs/castbridge-play.jar).
dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("castbridge.play.PlayApplicationKt")
    applicationName = "castbridge-play"
}

// Un seul JAR exécutable : java -jar castbridge-play.jar --server.port=8090
// Les ressources du cœur inutiles au service en sont retirées : packs Apprendre et Langues, et le contenu Quiz par niveau (« embedded » et surtout
// « embedded-reserved », contenu réservable qui ne doit JAMAIS être servi ici avant w20-04). Seules les deux banques libres de base restent.
tasks.register<Jar>("fatJar") {
    group = "castbridge"
    description = "JAR exécutable du service castbridge-play (< 60 Mo)"
    archiveBaseName.set("castbridge-play")
    archiveVersion.set("")
    manifest { attributes["Main-Class"] = "castbridge.play.PlayApplicationKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
    exclude("castbridge/learn/**", "castbridge/langues/**", "castbridge/quiz/embedded/**", "castbridge/quiz/embedded-reserved/**", "castbridge/quiz/play.html")
}

tasks.test {
    environment("LC_ALL", "C.UTF-8")
    timeout.set(Duration.ofMinutes(15))
    // Les tests ouvrent de vraies sockets sur 127.0.0.1 (port aléatoire) : pas de réutilisation de connexions JDK entre serveurs de test.
    systemProperty("http.keepAlive", "false")
    maxHeapSize = "768m"
    // JEP 444 : un fil virtuel qui attend sous `synchronized` épingle son porteur ; la trace dit où (tests de charge de l'audit)
    jvmArgs("-Djdk.tracePinnedThreads=full")
}
