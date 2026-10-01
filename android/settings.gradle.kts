pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositories { google(); mavenCentral() }
}
rootProject.name = "castbridge"
include(":core", ":sshd", ":sender", ":receiver", ":owner")
