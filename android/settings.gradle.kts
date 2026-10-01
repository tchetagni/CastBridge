pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "castbridge"
include(":core", ":sshd", ":sender", ":receiver", ":owner", ":ownerlib")
// Outil de bureau des activations (docs/ACTIVATION-TOOLS.md) : sources dans tools/, au-dessus de :core
include(":activation-desktop")
project(":activation-desktop").projectDir = file("../tools/activation-desktop")
