plugins { kotlin("jvm") }  // pure-logic module shared by the Android apps
dependencies {
    api("org.nanohttpd:nanohttpd:2.3.1")
    testImplementation(kotlin("test"))
}

// ---- « Apprendre » content packs (docs/LEARN.md) ----
// Sources: <repo>/content/learn/<pack id>/ ; the packs listed in content/learn/embedded.txt are zipped into the app
// resources (small socle), all packs are built by `gradle :core:buildLearnPacks` (or tools/build-learn-packs).
val learnContent: File = rootProject.projectDir.parentFile.resolve("content/learn")
val learnEmbedded = layout.buildDirectory.dir("generated/learn-embedded")
// The tool runs from the compiled classes only (not the resources: they depend on it)
val learnToolClasspath = files(sourceSets.main.get().output.classesDirs, configurations.runtimeClasspath)

val embedLearnPacks by tasks.registering(JavaExec::class) {
    group = "castbridge"
    description = "Zips the embedded « Apprendre » packs (content/learn/embedded.txt) into the core resources"
    dependsOn(tasks.named("compileKotlin"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.learn.LearnTool")
    val out = learnEmbedded.map { it.dir("castbridge/learn/embedded") }
    inputs.dir(learnContent).optional()
    outputs.dir(learnEmbedded)
    doFirst { learnEmbedded.get().asFile.deleteRecursively() }
    argumentProviders.add(CommandLineArgumentProvider { listOf("embed", learnContent.absolutePath, out.get().asFile.absolutePath) })
}
sourceSets.main { resources.srcDir(learnEmbedded) }
tasks.processResources { dependsOn(embedLearnPacks) }

tasks.register<JavaExec>("buildLearnPacks") {
    group = "castbridge"
    description = "Builds every « Apprendre » pack zip + catalog.json into build/learn-packs (to copy to a USB drive or the server)"
    dependsOn(tasks.named("compileKotlin"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.learn.LearnTool")
    args("build", learnContent.absolutePath, layout.buildDirectory.dir("learn-packs").get().asFile.absolutePath)
}

// Lots (docs/LEARN.md § Lots): one zip per class + lots-catalog.json (the LotMeta list for the server's publish endpoint).
// -Pupdate bumps the version of the lots whose content changed and rewrites content/learn/lots.json; fails above 3 MB per lot.
tasks.register<JavaExec>("buildLearnLots") {
    group = "castbridge"
    description = "Builds every « Apprendre » lot (one per class) + lots-catalog.json into build/learn-lots and prints the size report"
    dependsOn(tasks.named("compileKotlin"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.learn.LearnTool")
    args(listOf("lots", learnContent.absolutePath, layout.buildDirectory.dir("learn-lots").get().asFile.absolutePath) + (if (project.hasProperty("update")) listOf("--update") else emptyList()))
}

tasks.register<JavaExec>("reviewLearn") {
    group = "castbridge"
    description = "Writes docs/LEARN-REVIEW.md: per lot, what the teachers must verify"
    dependsOn(tasks.named("compileKotlin"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.learn.LearnTool")
    args("review", learnContent.absolutePath, rootProject.projectDir.parentFile.resolve("docs/LEARN-REVIEW.md").absolutePath)
}

tasks.register<JavaExec>("checkLearnContent") {
    group = "castbridge"
    description = "Validates the « Apprendre » sources and prints the coverage table"
    dependsOn(tasks.named("compileKotlin"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.learn.LearnTool")
    args(listOf("check", learnContent.absolutePath) + ((project.findProperty("packs") as String?)?.split(",") ?: emptyList()))
}

// ---- Skill graph (docs/CONTENT-ARCHITECTURE.md): content/graph/*.json ----
tasks.register<JavaExec>("checkContentGraph") {
    group = "castbridge"
    description = "Validates the curriculum skill graph (content/graph) and the content that names a skill"
    dependsOn(tasks.named("compileKotlin"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.curriculum.GraphTool")
    args(listOf("check", rootProject.projectDir.parentFile.resolve("content").absolutePath) + (if (project.hasProperty("requireContent")) listOf("--require-content") else emptyList()))
}

// UTF-8 file names in tests, as on Android (CI/containers often have no locale set)
tasks.test {
    environment("LC_ALL", "C.UTF-8")
    // « Apprendre »: the tests validate every pack source of the repository (docs/LEARN.md)
    systemProperty("learn.content", learnContent.absolutePath)
    // Skill graph + scopes (content/graph): the tests validate them
    systemProperty("graph.content", rootProject.projectDir.parentFile.resolve("content").absolutePath)
    // Charte graphique: the tests read branding/design-tokens.json (contrasts, generated Kotlin in sync)
    systemProperty("branding.dir", rootProject.projectDir.parentFile.resolve("branding").absolutePath)
    inputs.dir(rootProject.projectDir.parentFile.resolve("branding")).withPropertyName("branding").optional()
    // Question packs built by tools/quiz-bank (docs/QUIZ.md): the tests check their integrity
    systemProperty("quiz.dist", rootProject.projectDir.parentFile.resolve("content/quiz/dist").absolutePath)
    inputs.dir(learnContent).withPropertyName("learnContent").optional()
    // Animations (docs/LEARN.md § Animations): examples generated by tools/anim, PNG contact sheets written for reviewers
    val animDir = rootProject.projectDir.parentFile.resolve("tools/anim")
    systemProperty("anim.dir", animDir.absolutePath)
    systemProperty("anim.sheets", (project.findProperty("animSheets") as String?) ?: layout.buildDirectory.dir("anim-sheets").get().asFile.absolutePath)
    inputs.dir(animDir.resolve("examples")).withPropertyName("animExamples").optional()
}

// ---- Lots (docs/LOTS.md): the starter data bundled in the TV APK counts in the TV's 10 MB budget ----
// Runs on the built classes + resources (embedded Apprendre packs + quiz banks); fails the build above LotBudget.TV_MAX_BYTES.
val checkStarterBudget by tasks.registering(JavaExec::class) {
    group = "castbridge"
    description = "Prints the size of the Learn+Quiz starter data bundled in the TV APK and fails above the 10 MB budget"
    dependsOn(tasks.named("classes"))
    classpath = files(sourceSets.main.get().output.classesDirs, sourceSets.main.get().output.resourcesDir, configurations.runtimeClasspath)
    mainClass.set("castbridge.core.lots.StarterBudget")
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8")
}
tasks.named("check") { dependsOn(checkStarterBudget) }
