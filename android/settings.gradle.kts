pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "castbridge"
include(":core", ":sshd", ":sender", ":receiver", ":owner", ":ownerlib", ":devbridge")
// Outil de bureau des activations (docs/ACTIVATION-TOOLS.md) : sources dans tools/, au-dessus de :core
include(":activation-desktop")
project(":activation-desktop").projectDir = file("../tools/activation-desktop")
// Service de jeu en ligne castbridge-play (docs/PLAY-PROTOCOL.md, DESIGN-W20 § 2) : JVM pur, au-dessus de :core, sources dans server-play/
include(":server-play")
project(":server-play").projectDir = file("../server-play")
