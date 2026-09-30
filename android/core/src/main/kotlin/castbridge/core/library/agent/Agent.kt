package castbridge.core.library.agent

enum class Phase { READ, UNDERSTAND, FINGERPRINT, AI, PLAN, DONE }

data class Progress(val phase: Phase, val done: Int = 0, val total: Int = 0, val message: String = "") {
    /** 0..1 over the whole analysis (each phase has its share). */
    val fraction: Float get() {
        val w = when (phase) { Phase.READ -> 0f; Phase.UNDERSTAND -> 0.05f; Phase.FINGERPRINT -> 0.45f; Phase.AI -> 0.8f; Phase.PLAN -> 0.92f; Phase.DONE -> 1f }
        val span = when (phase) { Phase.UNDERSTAND -> 0.4f; Phase.FINGERPRINT -> 0.35f; Phase.AI -> 0.12f; else -> 0f }
        return if (total <= 0) w else w + span * (done.toFloat() / total).coerceIn(0f, 1f)
    }
}

/**
 * The library agent: reads a snapshot, understands every name with the local rules (plus, only if the user allowed it, a
 * model for the ambiguous ones), and PROPOSES a [Plan]. It never changes anything by itself: the [Executor] does, for the
 * changes the user ticked.
 */
class LibraryAgent(
    private val ctx: AgentContext,
    private val learned: LearnedRules? = null,
    private val model: NamingModel? = null,
    private val fingerprinter: Fingerprinter? = null,
    private val maxFingerprints: Int = 60,
) {
    private val labels = Labels(ctx.uiLang)

    fun analyze(snapshot: LibrarySnapshot, progress: (Progress) -> Unit = {}, cancelled: () -> Boolean = { false }): Analysis {
        val files = snapshot.files
        val notes = ArrayList<String>()
        progress(Progress(Phase.UNDERSTAND, 0, files.size))

        // ---- understand every name (local, deterministic)
        val parsed = HashMap<String, Parsed>(files.size * 2)
        files.forEachIndexed { n, f ->
            if (cancelled()) return@forEachIndexed
            parsed[f.key] = NameParser.parse(f.name, f.folder, f.durationMs, ctx.currentYear)
            if (n % 50 == 0) progress(Progress(Phase.UNDERSTAND, n, files.size))
        }
        fun parse(f: FileRef) = parsed[f.key] ?: NameParser.parse(f.name, f.folder, f.durationMs, ctx.currentYear)
        val habits = if (ctx.habits === Habits.NONE) Habits.from(files, ::parse, ctx.zone) else ctx.habits

        // ---- what the agent must not touch
        val skipped = ArrayList<Skipped>()
        val plannable = ArrayList<FileRef>()
        for (f in files) {
            val p = parse(f)
            when {
                ctx.guard.childProfileActive -> skipped += Skipped(f, "profil enfant actif : aucune modification")
                ctx.guard.isProtected(f) -> skipped += Skipped(f, "protégé par le contrôle parental")
                f.playing -> skipped += Skipped(f, "en cours de lecture")
                learned?.isIgnored(p.titleKey) == true || learned?.isIgnored(f.name.lowercase()) == true -> skipped += Skipped(f, "vous avez demandé de ne plus y toucher")
                else -> plannable += f
            }
        }
        if (ctx.guard.childProfileActive) notes += "Un profil enfant est actif : l'assistant ne propose que des conseils, il ne modifie rien."

        // ---- optional model for the ambiguous names
        var aiUsed = 0
        if (ctx.aiAllowed && model != null && !ctx.guard.childProfileActive && !cancelled()) {
            val plannableSet = plannable.map { it.key }.toSet()
            val pool = files.filter { it.key in plannableSet }.map { f -> ItemInfo(f, parse(f), Proposal(f.name, f.folder, ""), Source.RULES) }
            val picks = AiApply.select(pool, ctx.guard)
            if (picks.isNotEmpty()) {
                progress(Progress(Phase.AI, 0, picks.size, "Aide de l'IA (${if (model.remote) "serveur" else "sur le téléphone"})"))
                try {
                    picks.chunked(AiApply.BATCH).forEachIndexed { b, chunk ->
                        if (cancelled()) return@forEachIndexed
                        val req = SuggestRequest(ctx.uiLang, chunk.mapIndexed { k, (_, it) -> it.copy(i = k) })
                        val resp = model.suggest(req)
                        for (s in resp.suggestions) {
                            val (info, _) = chunk.getOrNull(s.i) ?: continue
                            parsed[info.file.key] = AiApply.apply(info.parsed, s); aiUsed++
                            aiKeys += info.file.key
                        }
                        progress(Progress(Phase.AI, (b + 1) * AiApply.BATCH, picks.size))
                    }
                } catch (e: ModelUnavailable) {
                    notes += "L'aide de l'IA n'a pas répondu (${e.message}) : les règles locales ont été utilisées seules."
                }
            }
        }

        // ---- fingerprints of same-size candidates (local reads only)
        val fps = HashMap<String, String>()
        val fpr = fingerprinter
        if (fpr != null && !cancelled()) {
            val cands = Duplicates.candidates(plannable).take(maxFingerprints)
            cands.forEachIndexed { n, f ->
                if (cancelled()) return@forEachIndexed
                progress(Progress(Phase.FINGERPRINT, n, cands.size, f.name))
                runCatching { fpr.fingerprint(f) }.getOrNull()?.let { fps[f.key] = it }
            }
        }

        // ---- proposals
        progress(Progress(Phase.PLAN, 0, 0))
        val removable = snapshot.volumes.filter { it.removable }.map { it.id }.toSet()
        val infos = files.map { f ->
            val p = parse(f)
            val src = if (f.key in aiKeys) Source.AI else Source.RULES
            ItemInfo(f, p, Namer.propose(p, f, labels, habits.defaultAudio, learned, dateOf(f.mtime).takeIf { p.date == null }), src)
        }
        aiKeys.clear()
        val plannableKeys = plannable.map { it.key }.toSet()
        val groups = Duplicates.find(plannable, ::parse, fps, removable, habits)
        val planItems = infos.filter { it.file.key in plannableKeys }
        val plan = Planner(ctx, learned).plan(snapshot, planItems, groups, skipped).let { it.copy(notes = notes + it.notes) }

        val renames = plan.renames.count { it.toName != null }
        val dupExtras = groups.filter { it.kind != DupKind.VERSION }
        val stats = Stats(files.size, files.size - renames - infos.count { it.parsed.kind == Kind.UNKNOWN }, renames, infos.count { it.parsed.kind == Kind.UNKNOWN },
            dupExtras.size, dupExtras.sumOf { it.bytes }, aiUsed)
        progress(Progress(Phase.DONE))
        return Analysis(snapshot, infos, plan, insights(snapshot, plan, groups), stats)
    }

    private val aiKeys = HashSet<String>()

    private fun dateOf(ms: Long): String? = if (ms <= 0) null else java.time.Instant.ofEpochMilli(ms).atZone(ctx.zone).toLocalDate().toString()

    /** Discreet advice computed from a finished analysis (also used alone, without fingerprints, for the library banner). */
    fun insights(snapshot: LibrarySnapshot, plan: Plan, groups: List<DupGroup>): List<Insight> {
        val out = ArrayList<Insight>()
        val n = plan.renames.count { it.toName != null }
        if (n > 0) out += Insight("names", Insight.NOTICE, if (n == 1) "1 fichier mal nommé" else "$n fichiers mal nommés")
        val dups = groups.filter { it.kind != DupKind.VERSION }
        if (dups.isNotEmpty()) {
            val count = dups.sumOf { it.extras.size }
            out += Insight("dups", Insight.NOTICE, (if (count == 1) "1 doublon" else "$count doublons") + " = " + Text.size(dups.sumOf { it.bytes }), dups.sumOf { it.bytes })
        }
        val versions = groups.filter { it.kind == DupKind.VERSION }
        if (versions.isNotEmpty()) out += Insight("versions", Insight.INFO, "${versions.sumOf { it.extras.size }} fichier(s) en double qualité = " + Text.size(versions.sumOf { it.bytes }), versions.sumOf { it.bytes })
        for (v in snapshot.volumes.filter { it.total > 0 && it.free >= 0 }) {
            val pct = Math.round(v.usedRatio * 100).toInt()
            val what = when (v.kind) { "usb" -> "La clé « ${v.label} »"; "internal" -> "La mémoire interne"; else -> "« ${v.label} »" }
            when {
                v.usedRatio >= 0.9 -> out += Insight("full:${v.id}", Insight.WARNING, "$what est pleine à $pct %")
                v.free < ctx.lowSpaceBytes -> out += Insight("low:${v.id}", Insight.WARNING, "Il ne reste que ${Text.size(v.free)} sur ${what.replaceFirstChar { it.lowercase() }}")
            }
        }
        val old = plan.trash.filter { it.why == TrashWhy.WATCHED_OLD }
        if (old.isNotEmpty()) out += Insight("old", Insight.INFO, "${old.size} fichier(s) déjà vus depuis longtemps occupent " + Text.size(old.sumOf { it.bytes }), old.sumOf { it.bytes })
        val mv = plan.moves
        if (mv.isNotEmpty()) out += Insight("move", Insight.INFO, "${mv.size} fichier(s) peuvent aller sur la clé pour libérer " + Text.size(mv.sumOf { it.bytes }), mv.sumOf { it.bytes })
        return out
    }
}

/** Advice the user snoozed or dismissed stays hidden for a while; nothing is ever pushed as a notification. */
object InsightFilter {
    fun visible(all: List<Insight>, hiddenUntil: Map<String, Long>, now: Long): List<Insight> = all.filter { (hiddenUntil[it.id] ?: 0L) <= now }.sortedByDescending { it.severity }
}
