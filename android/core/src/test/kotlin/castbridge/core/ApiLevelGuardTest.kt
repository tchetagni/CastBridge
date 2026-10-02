package castbridge.core

import java.io.File
import kotlin.test.*

/**
 * Durable guard against JDK calls that compile (compileSdk 35, JDK 17) but do not exist on the Android levels we ship to (minSdk 26; the reference TV is Android 9 = API 28).
 * A call to such a method crashes with NoSuchMethodError on the TV only, never in the JVM tests (e.g. `InputStream.readNBytes(int)`, Java 11 = API 33).
 *
 * It scans the Kotlin / Java sources of the Android-shipped modules (core, receiver, sender, owner, ownerlib, sshd, devbridge: `src/main` only) line by line,
 * comments and string literals removed, against [RULES]. Every hit fails with `file:line`, the rule and the replacement to use.
 *
 * Precision (a rule must not flag what the Kotlin stdlib provides): `String.isBlank()/lines()/repeat()/trim()`, `listOf/setOf/mapOf`, `Iterable.toList()`, `File.readText()`,
 * `InputStream.readBytes()/copyTo()` are Kotlin stdlib, FINE, and are never flagged. Rules for methods that Kotlin does not expose on its own types (`strip()`, `formatted()`, ...)
 * match everywhere; the rules for names that Kotlin ALSO provides (`isBlank`, `lines`, `repeat`) apply to `.java` files only. `.toList()` is flagged only after `.stream()`
 * on the same line (Java `Stream.toList()`, Java 16). Not covered (textually ambiguous, review by hand): `ByteArrayOutputStream.toString(Charset)` (Java 10), `Character.toString(int)` (Java 11).
 *
 * A legitimate use (code that only runs on a desktop JVM: an owner tool, a build task) goes into `src/test/resources/api-level-allowlist.txt`,
 * one line per file and rule: `path/relative/to/android|RULE_ID|justification` (a justification is mandatory; an entry that no longer matches anything fails: remove it).
 */
class ApiLevelGuardTest {
    class Rule(val id: String, val regex: Regex, val fix: String, val extensions: Set<String> = setOf("kt", "java"))

    companion object {
        private val ANY = setOf("kt", "java")
        val RULES: List<Rule> = listOf(
            Rule("READ_N_BYTES", Regex("""\.readNBytes\("""), "castbridge.core.util.BoundedRead.readAll(stream, max) (InputStream.readNBytes = API 33)"),
            Rule("READ_ALL_BYTES", Regex("""\.readAllBytes\("""), "stream.readBytes() (Kotlin) or BoundedRead.readAll(stream, max) (InputStream.readAllBytes = API 33)"),
            Rule("TRANSFER_TO", Regex("""\.transferTo\([^,()]*\)"""), "in.copyTo(out) (Kotlin) (InputStream.transferTo = API 33)"),
            Rule("COLLECTION_FACTORY", Regex("""\b(?:java\.util\.)?(?:List|Set|Map)\.(?:of|copyOf|ofEntries)\("""), "listOf / setOf / mapOf / x.toList() (Kotlin) (JDK 9/10 factories = API 30+)"),
            Rule("MAP_ENTRY", Regex("""\b(?:java\.util\.)?Map\.entry\("""), "key to value (Kotlin Pair) or AbstractMap.SimpleEntry (Map.entry = JDK 9)"),
            Rule("FILES_STRING", Regex("""\bFiles\.(?:readString|writeString)\("""), "file.readText() / file.writeText() (Kotlin) (Files.readString = API 33)"),
            Rule("PATH_OF", Regex("""\bPath\.of\("""), "java.nio.file.Paths.get(...) or File(...) (Path.of = API 33)"),
            Rule("OPTIONAL_JDK9_11", Regex("""\.orElseThrow\(\)|\bOptional\b.*\.(?:isEmpty\(\)|ifPresentOrElse\(|stream\(\)|or\()"""), "isPresent() / orElseThrow { Exception() } (no-arg orElseThrow, isEmpty, ifPresentOrElse = API 33)"),
            Rule("STREAM_TO_LIST", Regex("""\.stream\(\).*\.toList\(\)"""), ".collect(Collectors.toList()) or a Kotlin sequence (Stream.toList = API 34)"),
            Rule("COLLECTORS_UNMODIFIABLE", Regex("""\bCollectors\.toUnmodifiable(?:List|Set|Map)\("""), "Collectors.toList() wrapped in Collections.unmodifiableList (API 33)"),
            Rule("REQUIRE_NON_NULL_ELSE", Regex("""\brequireNonNullElse(?:Get)?\("""), "x ?: default (Kotlin) (Objects.requireNonNullElse = API 30)"),
            Rule("OBJECTS_CHECK_INDEX", Regex("""\bObjects\.check(?:Index|FromToIndex|FromIndexSize)\("""), "an explicit range check (Objects.checkIndex = API 30)"),
            Rule("BIGINTEGER_TWO", Regex("""\bBigInteger\.TWO\b"""), "BigInteger.valueOf(2) (BigInteger.TWO = API 33)"),
            Rule("DURATION_PARTS", Regex("""\.(?:toSeconds|toMillisPart|toSecondsPart|toMinutesPart|toHoursPart|toDaysPart|toNanosPart)\(\)"""), "toMillis() / toMinutes() and arithmetic (Duration.toSeconds and the *Part methods = API 31)"),
            Rule("STRING_STRIP", Regex("""\.(?:strip|stripLeading|stripTrailing)\(\)"""), "trim() / trimStart() / trimEnd() (Kotlin) (String.strip = API 33)"),
            Rule("STRING_JDK11_15", Regex("""\.(?:indent|translateEscapes|stripIndent|formatted)\("""), "String.format(...) / trimIndent() (Kotlin) (JDK 12-15 String methods = API 33+)"),
            Rule("STRING_JAVA_ONLY", Regex("""\.(?:isBlank\(\)|lines\(\)|repeat\()"""), "Kotlin String.isBlank() / lineSequence() / repeat() (these are only Kotlin stdlib in .kt files; Java String.isBlank/lines/repeat = API 33)", setOf("java")),
            Rule("NULL_STREAMS", Regex("""\.null(?:Input|Output)Stream\(\)|\.null(?:Reader|Writer)\("""), "an empty ByteArrayInputStream / a discarding stream of your own (JDK 11 = API 33)"),
            Rule("HEX_FORMAT", Regex("""\bHexFormat\b"""), "joinToString(\"\") { \"%02x\".format(it) } (HexFormat = JDK 17 = API 34)"),
            Rule("PROCESS_HANDLE", Regex("""\b(?:ProcessHandle|StackWalker)\b"""), "no equivalent below API 33: avoid on the TV (JDK 9)"),
            Rule("JAVA_NET_HTTP", Regex("""\bjava\.net\.http\."""), "HttpURLConnection (java.net.http does not exist on Android)"),
            Rule("MATH_9_PLUS", Regex("""\bMath\.(?:multiplyHigh|floorMod\(\s*\w+L\b|fma\()"""), "Math.floorMod(Long, Int) is Java 9 (API 31): use explicit arithmetic"),
        )

        /** Source roots, relative to `android/`: only what ships in an APK (the desktop-only modules, the tools folders and backend/ are out of scope). */
        val ROOTS = listOf("core", "receiver", "sender", "owner", "ownerlib", "sshd", "devbridge").map { "$it/src/main" }

        /** Code with comments and string literals blanked, one entry per source line (line numbers preserved). */
        fun code(text: String): List<String> {
            val out = ArrayList<String>(); var inBlock = false
            for (raw in text.split("\n")) {
                val sb = StringBuilder(); var i = 0; var inStr = false
                while (i < raw.length) {
                    val c = raw[i]; val n = raw.getOrNull(i + 1)
                    when {
                        inBlock -> { if (c == '*' && n == '/') { inBlock = false; i++ } }
                        inStr -> { if (c == '\\') i++ else if (c == '"') { inStr = false; sb.append('"') } }
                        c == '/' && n == '*' -> { inBlock = true; i++ }
                        c == '/' && n == '/' -> i = raw.length
                        c == '"' -> { inStr = true; sb.append('"') }
                        else -> sb.append(c)
                    }
                    i++
                }
                out += sb.toString()
            }
            return out
        }

        data class Hit(val path: String, val line: Int, val rule: Rule, val text: String)

        fun scan(path: String, text: String): List<Hit> {
            val ext = path.substringAfterLast('.')
            val hits = ArrayList<Hit>()
            code(text).forEachIndexed { i, l -> for (r in RULES) if (ext in r.extensions && r.regex.containsMatchIn(l)) hits += Hit(path, i + 1, r, l.trim()) }
            return hits
        }
    }

    private val android: File = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.firstOrNull { File(it, "settings.gradle.kts").isFile && File(it, "core").isDirectory }
        ?: error("dossier android/ introuvable depuis ${System.getProperty("user.dir")}")

    private fun allowlist(): Map<Pair<String, String>, String> {
        val f = File(android, "core/src/test/resources/api-level-allowlist.txt")
        val m = LinkedHashMap<Pair<String, String>, String>()
        if (!f.isFile) return m
        f.readLines().forEachIndexed { i, l ->
            if (l.isBlank() || l.trim().startsWith("#")) return@forEachIndexed
            val p = l.split("|", limit = 3).map { it.trim() }
            assertTrue(p.size == 3 && p[2].length >= 10, "api-level-allowlist.txt:${i + 1} : « chemin|RÈGLE|justification » (justification obligatoire)")
            assertTrue(RULES.any { it.id == p[1] }, "api-level-allowlist.txt:${i + 1} : règle inconnue ${p[1]}")
            m[p[0] to p[1]] = p[2]
        }
        return m
    }

    @Test fun noJdkApiAbsentFromTheMinimumAndroidLevelIsCalled() {
        val allow = allowlist(); val used = HashSet<Pair<String, String>>(); val bad = ArrayList<String>()
        var files = 0
        for (root in ROOTS) {
            val dir = File(android, root); if (!dir.isDirectory) continue
            dir.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") }.sortedBy { it.path }.forEach { f ->
                files++
                val rel = f.relativeTo(android).invariantSeparatorsPath
                for (h in scan(rel, f.readText())) {
                    val key = rel to h.rule.id
                    if (key in allow) used += key
                    else bad += "$rel:${h.line} [${h.rule.id}] ${h.text.take(110)}\n      -> utiliser : ${h.rule.fix}"
                }
            }
        }
        assertTrue(files > 100, "le garde n'a scanné que $files fichiers : chemins des sources à revoir")
        val stale = allow.keys - used
        assertTrue(stale.isEmpty(), "entrées d'allowlist qui ne correspondent plus à rien (à supprimer) : $stale")
        assertTrue(bad.isEmpty(), "API JDK absente d'Android 9 / API 26-28 (NoSuchMethodError sur la TV seulement) :\n" + bad.joinToString("\n"))
    }

    @Test fun theRulesFlagTheJdkOnlyCallsAndNothingTheKotlinStdlibProvides() {
        fun ids(line: String, ext: String = "kt") = scan("x.$ext", line).map { it.rule.id }
        assertEquals(listOf("READ_N_BYTES"), ids("val b = s.readNBytes(10)"))
        assertEquals(listOf("READ_ALL_BYTES"), ids("s.readAllBytes()"))
        assertEquals(listOf("TRANSFER_TO"), ids("i.transferTo(o)"))
        assertEquals(listOf("COLLECTION_FACTORY"), ids("val l = java.util.List.of(1, 2)"))
        assertEquals(listOf("COLLECTION_FACTORY"), ids("Set.copyOf(x)"))
        assertEquals(listOf("MAP_ENTRY"), ids("Map.entry(a, b)"))
        assertEquals(listOf("FILES_STRING"), ids("Files.readString(p)"))
        assertEquals(listOf("PATH_OF"), ids("Path.of(\"a\")"))
        assertEquals(listOf("OPTIONAL_JDK9_11"), ids("o.orElseThrow()"))
        assertEquals(listOf("OPTIONAL_JDK9_11"), ids("val o: Optional<String> = x; if (o.isEmpty()) {}"))
        assertEquals(listOf("STREAM_TO_LIST"), ids("list.stream().map { it }.toList()"))
        assertEquals(listOf("COLLECTORS_UNMODIFIABLE"), ids("Collectors.toUnmodifiableList()"))
        assertEquals(listOf("REQUIRE_NON_NULL_ELSE"), ids("Objects.requireNonNullElse(a, b)"))
        assertEquals(listOf("BIGINTEGER_TWO"), ids("BigInteger.TWO"))
        assertEquals(listOf("DURATION_PARTS"), ids("d.toMillisPart()"))
        assertEquals(listOf("DURATION_PARTS"), ids("d.toSeconds()"))
        assertEquals(listOf("STRING_STRIP"), ids("s.strip()"))
        assertEquals(listOf("STRING_JAVA_ONLY"), ids("s.isBlank()", "java"))
        // what the Kotlin stdlib (or an older JDK) provides is NOT flagged
        for (ok in listOf("s.isBlank()", "s.lines()", "\"ab\".repeat(3)", "listOf(1, 2)", "setOf(1)", "mapOf(1 to 2)", "ts.toList()", "seq.map { it }.toList()", "f.readText()", "s.readBytes()",
            "i.copyTo(o)", "ch.transferTo(pos, n, out)", "BigDecimal(x).stripTrailingZeros()", "unit.toSeconds(5)", "o.orElseThrow { E() }", "o.isPresent", "ByteArray(2).toString(Charsets.UTF_8)", "Paths.get(\"a\")", "mutableListOf<Int>()",
            "x.trim()", "Collections.unmodifiableList(l)", "Collectors.toList()", "d.toMillis()", "d.toMinutes()")) assertEquals(emptyList(), ids(ok), ok)
        // comments and strings are ignored
        for (ok in listOf("// s.readNBytes(1)", "val t = \"List.of(1)\"", "/* s.readAllBytes() */ val a = 1", "val t = \"a \\\" s.strip()\"")) assertEquals(emptyList(), ids(ok), ok)
        assertEquals(listOf(4), scan("x.kt", "/*\nreadNBytes(\n*/\nval a = s.readAllBytes()").map { it.line })
    }
}
