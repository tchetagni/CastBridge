# syntax=docker/dockerfile:1
# castbridge-play : service de jeu en ligne (WebSocket + SSE + long-poll), JVM seule, au-dessus de :core.
# Contexte de construction = la RACINE du dépôt :   docker build -f server-play/Dockerfile -t castbridge-play .
# (filtre du contexte : server-play/Dockerfile.dockerignore). Ce fichier n'est ni construit ni lancé par l'agent w20-03 : à valider par w20-08.

# ---- build ----------------------------------------------------------------------------------------
FROM gradle:8.14.3-jdk21 AS build
WORKDIR /repo
COPY android/gradle.properties android/gradle.properties
COPY android/core android/core
COPY server-play server-play
# :core lit content/learn et content/langues pendant son build (ressources embarquées) ; le jar du service les écarte ensuite
COPY content/learn content/learn
COPY content/langues content/langues
# Racine Gradle minimale (sans le plugin Android, introuvable dans un conteneur) : :core + :server-play. Elle doit être un enfant direct de la racine du dépôt.
RUN mkdir .harness && cd .harness \
 && printf '%s\n' 'pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }' \
      'dependencyResolutionManagement { repositories { mavenCentral() } }' \
      'rootProject.name = "castbridge-play-harness"' 'include(":core", ":server-play")' \
      'project(":core").projectDir = file("../android/core")' 'project(":server-play").projectDir = file("../server-play")' > settings.gradle.kts \
 && printf '%s\n' 'plugins { kotlin("jvm") version "2.1.0" apply false }' > build.gradle.kts \
 && cp ../android/gradle.properties . \
 && gradle --no-daemon -q -PquizReserved=exclude :server-play:fatJar

# ---- runtime --------------------------------------------------------------------------------------
# JDK 25 (JEP 491) : un fil virtuel qui attend sous synchronized n'épingle plus son porteur ; le code reste compilé pour JDK 21 (build ci-dessus), il tourne sur 21 comme sur 25.
# Le tag eclipse-temurin:25-jre n'a pas pu être vérifié depuis ce poste (aucun accès au registre) : si le pull échoue, revenir à 21-jre (les verrous explicites suffisent déjà).
FROM eclipse-temurin:25-jre
ARG VCS_REF=unknown
LABEL org.opencontainers.image.title="castbridge-play" \
      org.opencontainers.image.description="CastBridge : salles de Quiz en ligne (WebSocket, SSE, long-poll)" \
      org.opencontainers.image.revision="${VCS_REF}"

# uid 10002 (l'API est en 10001) ; aucun secret, aucune base : le service ne lit que des CLES PUBLIQUES de tickets
RUN set -eux; \
    if ! command -v curl >/dev/null; then \
        apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*; \
    fi; \
    groupadd --system --gid 10002 castbridge-play; \
    useradd --system --uid 10002 --gid castbridge-play --home-dir /app --shell /usr/sbin/nologin castbridge-play; \
    mkdir -p /app /lots

WORKDIR /app
COPY --from=build /repo/server-play/build/libs/castbridge-play.jar /app/castbridge-play.jar

ENV TZ=Africa/Douala \
    CASTBRIDGE_PLAY_PORT=8080 \
    CASTBRIDGE_PLAY_LOTS_DIR=/lots

USER 10002:10002
EXPOSE 8080
# À lancer avec : read_only: true, tmpfs /tmp, cap_drop ALL, mem_limit 384m, port publié sur 127.0.0.1:7091 SEULEMENT (docker-compose : w20-08).
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://127.0.0.1:8080/play/health >/dev/null || exit 1

# -XX:-UsePerfData : pas d'écriture dans /tmp pour les compteurs de la JVM (système de fichiers en lecture seule) ; tas = 70 % de la limite du conteneur (384 Mo).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-Xss512k", "-XX:ReservedCodeCacheSize=48m", "-XX:MaxMetaspaceSize=128m", \
            "-XX:-UsePerfData", "-XX:+ExitOnOutOfMemoryError", "-Dfile.encoding=UTF-8", "-Duser.timezone=Africa/Douala", \
            "-jar", "/app/castbridge-play.jar"]
