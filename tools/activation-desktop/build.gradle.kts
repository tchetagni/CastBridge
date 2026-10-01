plugins {
    kotlin("jvm")
    application
}

// Outil de bureau des activations (docs/ACTIVATION-TOOLS.md) : ligne de commande + interface Swing, au-dessus de la bibliothèque émettrice du cœur
// (castbridge.core.owner.ActivationIssuer). Java 17+. `gradle :activation-desktop:run --args="aide"`, `gradle :activation-desktop:fatJar` (JAR exécutable).
dependencies {
    implementation(project(":core"))
    implementation("com.google.zxing:core:3.5.3")
    testImplementation(kotlin("test"))
}


application {
    mainClass.set("castbridge.desktop.MainKt")
    applicationName = "castbridge-activation"
}

// Un seul JAR exécutable pour Mac, Windows et Linux : java -jar castbridge-activation-desktop.jar [commande]
tasks.register<Jar>("fatJar") {
    group = "castbridge"
    description = "JAR exécutable multiplateforme (toutes les dépendances)"
    archiveBaseName.set("castbridge-activation-desktop")
    archiveVersion.set("")
    manifest { attributes["Main-Class"] = "castbridge.desktop.MainKt" }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) } })
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

tasks.test {
    environment("LC_ALL", "C.UTF-8")
    systemProperty("activation.vectors", rootProject.projectDir.parentFile.resolve("tools/activation/test-vectors.json").absolutePath)
}
