import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

plugins { kotlin("jvm") }  // pure-logic module shared by the Android apps
dependencies {
    api("org.nanohttpd:nanohttpd:2.3.1")
    // bcrypt (Apache-2.0) : la porte « Super administration » vérifie le mot de passe contre un haché injecté à la compilation (docs/OWNER-CONSOLE.md)
    implementation("at.favre.lib:bcrypt:0.10.2")
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

// ---- Quiz embarqué : questions RÉSERVABLES (docs/agent-reports/quiz-toutes-les-questions.md) ----
// -PquizReserved=include (défaut, phase d'essai) : embedded-reserved/ est dans l'APK ; =exclude (version de production) : le dossier est retiré du build.
val quizReserved = (project.findProperty("quizReserved") as String?) ?: "include"
require(quizReserved == "include" || quizReserved == "exclude") { "quizReserved doit valoir include ou exclude (reçu : $quizReserved)" }
tasks.processResources { if (quizReserved == "exclude") exclude("castbridge/quiz/embedded-reserved/**") }

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

// ---- « Langues » (docs/LANGUES.md § 14): text lots of content/langues + the free starter bundled in the TV APK ----
val languesContent: File = rootProject.projectDir.parentFile.resolve("content/langues")
val languesEmbedded = layout.buildDirectory.dir("generated/langues-embedded")

// Copies the packs listed in content/langues/embedded.txt (langue.json + media.json) and writes catalog.json (ids + versions)
val embedLanguesPacks by tasks.registering {
    group = "castbridge"
    description = "Copies the embedded « Langues » packs (content/langues/embedded.txt) into the core resources"
    inputs.dir(languesContent).optional()
    outputs.dir(languesEmbedded)
    doLast {
        val root = languesEmbedded.get().asFile.resolve("castbridge/langues/embedded")
        languesEmbedded.get().asFile.deleteRecursively(); root.mkdirs()
        val list = languesContent.resolve("embedded.txt").takeIf { it.isFile }?.readLines().orEmpty().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        val reg = languesContent.resolve("lots.json").takeIf { it.isFile }?.readText().orEmpty()
        fun regList(k: String) = Regex("\"$k\"\\s*:\\s*\\[([^\\]]*)]").find(reg)?.groupValues?.get(1)?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toSet() } ?: emptySet()
        val regFree = regList("free"); val regReserved = regList("reserved")
        val items = list.map { id ->
            val dir = languesContent.resolve(id)
            require(dir.resolve("langue.json").isFile) { "embedded.txt : pack « $id » introuvable dans content/langues" }
            for (n in listOf("langue.json", "media.json")) dir.resolve(n).takeIf { it.isFile }?.copyTo(root.resolve("$id/$n"), overwrite = true)
            val v = Regex("\"version\"\\s*:\\s*(\\d+)").find(dir.resolve("langue.json").readText())?.groupValues?.get(1) ?: "1"
            // licence tag = the explicit registry content/langues/lots.json (free = CC BY-SA 4.0, reserved = sealed/rented); in neither = untagged (never exported)
            val tag = when { regFree.contains("langues:$id") -> ",\"license\":\"CC-BY-SA-4.0\",\"family\":\"free\""; regReserved.contains("langues:$id") -> ",\"family\":\"reserved\""; else -> "" }
            "{\"id\":\"$id\",\"version\":$v$tag}"
        }
        root.resolve("catalog.json").writeText("{\"format\":1,\"packs\":[${items.joinToString(",")}]}\n")
    }
}
sourceSets.main { resources.srcDir(languesEmbedded) }
tasks.processResources { dependsOn(embedLanguesPacks) }

// Builds the `langues` text lots + lots-catalog.json (UNSIGNED) into build/langues-lots. -Pupdate bumps changed lots and rewrites content/langues/lots.json
tasks.register<JavaExec>("buildLangLots") {
    group = "castbridge"
    description = "Builds every « Langues » text lot + lots-catalog.json (unsigned) into build/langues-lots"
    dependsOn(tasks.named("compileKotlin"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.langues.LangLotBuilder")
    args(listOf(languesContent.absolutePath, layout.buildDirectory.dir("langues-lots").get().asFile.absolutePath) + (if (project.hasProperty("update")) listOf("--update") else emptyList()))
}

// Transfer bench (docs/TRANSFER.md): gradle :core:transferBench -Pargs="--tv http://IP:8765 --pin 123456" (or tools/transfer-bench/run.sh)
tasks.register<JavaExec>("transferBench") {
    group = "castbridge"
    description = "Measures phone -> TV throughput for 1, 2, 4, 8 connections (needs --tv, or --simulate)"
    dependsOn(tasks.named("classes"))
    classpath = learnToolClasspath
    mainClass.set("castbridge.core.xfer.TransferBench")
    args(((project.findProperty("args") as String?) ?: "--help").split(" ").filter { it.isNotEmpty() })
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
    // Bounded time: JUnit 4 has no global per-test timeout, so no test may hang the suite (ByteRelayTest.noServerMeansRefused once did, for an hour).
    // (1) the whole task gives up after 40 minutes (the full core suite takes ~5); (2) a watchdog kills the test worker when ONE test runs longer than
    // 60 s (-PtestTimeoutMs=… to change) and names that test: Gradle then reports the worker crash. Slow tests must be fixed, not waited for.
    timeout.set(Duration.ofMinutes(40))
    val perTestMs = (project.findProperty("testTimeoutMs") as String?)?.toLong() ?: 60_000L
    val watch = AtomicReference<ScheduledExecutorService?>(null)
    val pending = AtomicReference<ScheduledFuture<*>?>(null)
    val log = logger
    doFirst { watch.set(Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "test-watchdog").apply { isDaemon = true } }) }
    doLast { watch.getAndSet(null)?.shutdownNow() }
    addTestListener(object : TestListener {
        override fun beforeSuite(suite: TestDescriptor) {}
        override fun afterSuite(suite: TestDescriptor, result: TestResult) {}
        override fun beforeTest(test: TestDescriptor) {
            val name = "${test.className}.${test.name}"
            pending.set(watch.get()?.schedule({
                log.error("TEST TROP LONG (plus de $perTestMs ms) : $name : le processus de test est tué")
                ProcessHandle.current().descendants()
                    .filter { it.info().commandLine().orElse("").contains("Gradle Test Executor") }
                    .forEach { it.destroyForcibly() }
            }, perTestMs, TimeUnit.MILLISECONDS))
        }
        override fun afterTest(test: TestDescriptor, result: TestResult) { pending.getAndSet(null)?.cancel(false) }
    })
    environment("LC_ALL", "C.UTF-8")
    // No JDK keep-alive pool in tests: every test starts its own server on a port the OS picks (port 0), and a later server can get a port an earlier one had;
    // the pooled idle connection to the dead server then fails the first request of the new one (« Unexpected end of file », « Connection reset »: 1 in
    // ~1500 servers measured, w15-07). The keep-alive behaviour of the server is covered with raw sockets (RemoteHttpTest, HttpRemoteTransport).
    systemProperty("http.keepAlive", "false")
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

// ---- « castbridge-owner » : the desk activation tool, one runnable jar for Mac / Windows / Linux (Java 17+) ----
tasks.register<Jar>("ownerToolJar") {
    group = "castbridge"
    description = "Builds build/libs/castbridge-owner.jar (java -jar castbridge-owner.jar help)"
    archiveBaseName.set("castbridge-owner"); archiveVersion.set("")
    manifest { attributes["Main-Class"] = "castbridge.core.owner.OwnerCli" }
    dependsOn(tasks.named("compileKotlin"))
    from(sourceSets.main.get().output.classesDirs)
    from({ configurations.runtimeClasspath.get().filter { it.name.endsWith(".jar") }.map { zipTree(it) } })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
}
