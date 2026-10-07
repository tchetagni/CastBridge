package castbridge.core.tv

import castbridge.core.tv.UsbVolumeState as V
import castbridge.core.tv.activation.LineTone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * « Pouvoir lire la clé USB même si elle a été mal éjectée » (2026-10-07). Android lance `fsck` au branchement (état `checking`), puis monte (`mounted`) ou refuse
 * (`unmountable`) : une application ne peut ni réparer ni démonter un volume, elle peut dire ce qui se passe et quoi faire. Règle pure : l'état du volume et son
 * historique donnent la ligne française et l'action.
 */
class UsbVolumeStateTest {
    private val t0 = 1_000_000L
    private fun facts(state: MediaState, label: String = "Lexar", since: Long? = t0, afterCheck: Boolean = false, bad: Boolean = false,
                      cut: String? = null, writing: Int = 0, pull: Boolean = false, prepared: Boolean = false) =
        UsbFacts(label, state, since, afterCheck, bad, cut, writing, pull, prepared)
    private fun judge(f: UsbFacts, now: Long = t0) = V.judge(f, now)

    // ---- what Android says ----

    @Test fun `the states are read from Android's words, anything else is unknown`() {
        assertEquals(MediaState.CHECKING, MediaState.parse("checking"))
        assertEquals(MediaState.MOUNTED, MediaState.parse("mounted"))
        assertEquals(MediaState.MOUNTED_READ_ONLY, MediaState.parse("mounted_ro"))
        assertEquals(MediaState.UNMOUNTABLE, MediaState.parse("unmountable"))
        assertEquals(MediaState.BAD_REMOVAL, MediaState.parse("bad_removal"))
        assertEquals(MediaState.EJECTING, MediaState.parse("ejecting"))
        assertEquals(MediaState.UNMOUNTED, MediaState.parse("unmounted"))
        assertEquals(MediaState.REMOVED, MediaState.parse("removed"))
        assertEquals(MediaState.NOFS, MediaState.parse("nofs"))
        assertEquals(MediaState.SHARED, MediaState.parse("shared"))
        assertEquals(MediaState.UNKNOWN, MediaState.parse("unknown"))
        assertEquals(MediaState.UNKNOWN, MediaState.parse(null))
        assertEquals(MediaState.UNKNOWN, MediaState.parse("MOUNTED "), "no guessing: exactly Android's words")
        assertEquals(MediaState.UNKNOWN, MediaState.parse("mounted_rw"))
    }

    @Test fun `each media broadcast announces one state, a read-only mount is told apart`() {
        assertEquals(MediaState.CHECKING, MediaState.ofAction("android.intent.action.MEDIA_CHECKING"))
        assertEquals(MediaState.MOUNTED, MediaState.ofAction("android.intent.action.MEDIA_MOUNTED"))
        assertEquals(MediaState.MOUNTED_READ_ONLY, MediaState.ofAction("android.intent.action.MEDIA_MOUNTED", readOnly = true))
        assertEquals(MediaState.UNMOUNTABLE, MediaState.ofAction("android.intent.action.MEDIA_UNMOUNTABLE"))
        assertEquals(MediaState.BAD_REMOVAL, MediaState.ofAction("android.intent.action.MEDIA_BAD_REMOVAL"))
        assertEquals(MediaState.EJECTING, MediaState.ofAction("android.intent.action.MEDIA_EJECT"))
        assertEquals(MediaState.UNMOUNTED, MediaState.ofAction("android.intent.action.MEDIA_UNMOUNTED"))
        assertEquals(MediaState.REMOVED, MediaState.ofAction("android.intent.action.MEDIA_REMOVED"))
        assertEquals(MediaState.NOFS, MediaState.ofAction("android.intent.action.MEDIA_NOFS"))
        assertEquals(MediaState.SHARED, MediaState.ofAction("android.intent.action.MEDIA_SHARED"))
        assertNull(MediaState.ofAction("android.intent.action.BOOT_COMPLETED"))
        assertNull(MediaState.ofAction(null))
    }

    @Test fun `only a mounted volume can be read`() {
        for (s in MediaState.values()) assertEquals(s == MediaState.MOUNTED || s == MediaState.MOUNTED_READ_ONLY, s.mounted, "$s")
    }

    // ---- Android checks the key (fsck) ----

    @Test fun `a key under check says Android is checking it, why, and to wait`() {
        val v = judge(facts(MediaState.CHECKING, bad = true))
        assertEquals(UsbPhase.CHECKING, v.phase)
        assertEquals("Clé « Lexar » : vérification par Android (elle a été retirée sans éjection)… patientez", v.line)
        assertEquals(UsbAction.WAIT, v.action)
        assertEquals(LineTone.INFO, v.tone)
        assertFalse(v.readable, "nothing can be read while Android checks")
        assertTrue(v.notable, "the home says it: the videos of the key vanish from the library meanwhile")
    }

    @Test fun `the cause is only claimed when it is known`() {
        val unknown = judge(facts(MediaState.CHECKING, bad = false))
        assertEquals("Clé « Lexar » : vérification par Android… patientez", unknown.line)
        assertFalse("sans éjection" in unknown.line, "a check is also done at every plug-in of a clean key: no accusation without proof")
    }

    @Test fun `a long check is said with its duration and keeps the advice not to pull the key`() {
        val slow = judge(facts(MediaState.CHECKING), now = t0 + 35_000)
        assertTrue("depuis 35 s" in slow.line, slow.line)
        assertTrue("sans doute retirée sans éjection" in slow.line, "a slow check hints at an unclean removal, said as a guess: ${slow.line}")
        assertTrue("ne la retirez pas" in slow.line, slow.line)
        val known = judge(facts(MediaState.CHECKING, bad = true), now = t0 + 35_000)
        assertTrue("(elle a été retirée sans éjection)" in known.line && "sans doute" !in known.line, known.line)
        val long = judge(facts(MediaState.CHECKING), now = t0 + 190_000)
        assertTrue("depuis 3 min" in long.line, long.line)
        assertTrue("c'est long" in long.line && "ordinateur" in long.line, "after minutes: what to do if it never ends: ${long.line}")
        assertEquals(UsbAction.WAIT, long.action)
    }

    @Test fun `the check line stays short under twenty seconds`() {
        val v = judge(facts(MediaState.CHECKING), now = t0 + V.SLOW_CHECK_MS - 1)
        assertFalse("depuis" in v.line, v.line)
        assertTrue("depuis" in judge(facts(MediaState.CHECKING), now = t0 + V.SLOW_CHECK_MS).line)
        assertFalse("c'est long" in judge(facts(MediaState.CHECKING), now = t0 + V.LONG_CHECK_MS - 1).line)
        assertTrue("c'est long" in judge(facts(MediaState.CHECKING), now = t0 + V.LONG_CHECK_MS).line)
    }

    @Test fun `a key without a name is still named`() {
        assertEquals("Clé : vérification par Android… patientez", judge(facts(MediaState.CHECKING, label = "")).line)
        assertEquals("Clé : vérification par Android… patientez", judge(facts(MediaState.CHECKING, label = "  ")).line)
        assertEquals("Clé prête", judge(facts(MediaState.MOUNTED, label = "", afterCheck = true)).line)
    }

    // ---- the check ended ----

    @Test fun `out of the check the key is ready, said for a few seconds`() {
        val v = judge(facts(MediaState.MOUNTED, since = t0, afterCheck = true), now = t0 + 3_000)
        assertEquals(UsbPhase.JUST_READY, v.phase)
        assertEquals("Clé « Lexar » prête", v.line)
        assertEquals(LineTone.GOOD, v.tone)
        assertTrue(v.readable && v.notable)
        assertEquals(UsbAction.NONE, v.action)
        // then it goes quiet
        val later = judge(facts(MediaState.MOUNTED, since = t0, afterCheck = true), now = t0 + V.READY_NOTICE_MS)
        assertEquals(UsbPhase.READY, later.phase)
        assertEquals("Clé « Lexar » prête", later.line)
        assertFalse(later.notable, "a ready key does not nag the home")
        assertTrue(later.readable)
    }

    @Test fun `a key already mounted when the TV starts is ready without a fanfare`() {
        val v = judge(facts(MediaState.MOUNTED, since = null, afterCheck = false))
        assertEquals(UsbPhase.READY, v.phase)
        assertFalse(v.notable)
    }

    @Test fun `a write-protected key is readable but not writable, said plainly`() {
        val v = judge(facts(MediaState.MOUNTED_READ_ONLY))
        assertEquals(UsbPhase.READ_ONLY, v.phase)
        assertTrue(v.readable)
        assertEquals(LineTone.WARN, v.tone)
        assertTrue("lecture seule" in v.line && "Lexar" in v.line, v.line)
        assertFalse(v.notable, "quiet on the home: the library and the settings say it")
    }

    // ---- Android could not mount it ----

    @Test fun `an unmountable key gets the guide and the button to the storage settings`() {
        val v = judge(facts(MediaState.UNMOUNTABLE, label = ""))
        assertEquals(UsbPhase.DAMAGED, v.phase)
        assertEquals("Clé illisible : Android n'a pas pu la réparer. Sur un ordinateur : Mac › Utilitaire de disque › S.O.S ; Windows › clic droit › Propriétés › Outils › Vérifier ; " +
            "ou Réglages de la TV › Stockage › Réparer/Formater (le formatage efface tout)", v.line)
        assertEquals(UsbAction.OPEN_STORAGE_SETTINGS, v.action)
        assertEquals("Ouvrir les réglages de stockage", v.actionLabel)
        assertEquals(LineTone.WARN, v.tone)
        assertFalse(v.readable)
        assertTrue(v.notable)
    }

    @Test fun `a named unmountable key keeps its name`() {
        assertTrue(judge(facts(MediaState.UNMOUNTABLE)).line.startsWith("Clé « Lexar » illisible : Android n'a pas pu la réparer."), judge(facts(MediaState.UNMOUNTABLE)).line)
    }

    @Test fun `a key without a known file system says so, with the same way out`() {
        val v = judge(facts(MediaState.NOFS))
        assertEquals(UsbPhase.NO_FILESYSTEM, v.phase)
        assertTrue("format non reconnu" in v.line && "exFAT" in v.line && "efface tout" in v.line, v.line)
        assertEquals(UsbAction.OPEN_STORAGE_SETTINGS, v.action)
        assertFalse(v.readable)
    }

    // ---- pulled without ejecting ----

    @Test fun `a key pulled during a copy names the incomplete file and says it will be resumed`() {
        val v = judge(facts(MediaState.BAD_REMOVAL, cut = "Film 2024.mkv"))
        assertEquals(UsbPhase.REMOVED_BADLY, v.phase)
        assertEquals("Clé retirée pendant une copie : le fichier « Film 2024.mkv » est incomplet, il sera repris", v.line)
        assertEquals(LineTone.WARN, v.tone)
        assertTrue(v.notable)
        assertFalse(v.readable)
    }

    @Test fun `a key pulled without a copy points at the safe removal for next time`() {
        val v = judge(facts(MediaState.BAD_REMOVAL))
        assertEquals(UsbPhase.REMOVED_BADLY, v.phase)
        assertTrue("retirée sans éjection" in v.line && "Préparer le retrait de la clé USB" in v.line, v.line)
        assertFalse("pendant une copie" in v.line, "no copy was cut: never claim one")
    }

    @Test fun `a key pulled after the preparation is not an accident, nothing is lost, said in green for a minute`() {
        val v = judge(facts(MediaState.BAD_REMOVAL, prepared = true))
        assertEquals(UsbPhase.REMOVED_PREPARED, v.phase)
        assertTrue("elle était préparée" in v.line && "rien n'est perdu" in v.line && "Lexar" in v.line, v.line)
        assertTrue("Android la vérifiera" in v.line, "the next plug-in is still checked by Android: said, so that it is no surprise (${v.line})")
        assertEquals(LineTone.GOOD, v.tone)
        assertTrue(v.notable)
        assertFalse(v.readable)
        assertNull(V.note(v), "no orange pastille for a removal done the right way")
        assertEquals(UsbPhase.REMOVED_PREPARED, judge(facts(MediaState.BAD_REMOVAL, prepared = true), now = t0 + V.EJECTED_KEEP_MS - 1).phase)
        assertEquals(UsbPhase.ABSENT, judge(facts(MediaState.BAD_REMOVAL, prepared = true), now = t0 + V.EJECTED_KEEP_MS).phase, "a minute, not ten: nothing to worry about")
        // a copy was cut or not, the preparation says it all: nothing was running
        assertEquals(UsbPhase.REMOVED_PREPARED, judge(facts(MediaState.BAD_REMOVAL, prepared = true, cut = "a.mkv")).phase)
    }

    @Test fun `the pulled-key notice goes away after ten minutes`() {
        val v = judge(facts(MediaState.BAD_REMOVAL, cut = "a.mkv"), now = t0 + V.BAD_REMOVAL_KEEP_MS - 1)
        assertEquals(UsbPhase.REMOVED_BADLY, v.phase)
        val gone = judge(facts(MediaState.BAD_REMOVAL, cut = "a.mkv"), now = t0 + V.BAD_REMOVAL_KEEP_MS)
        assertEquals(UsbPhase.ABSENT, gone.phase)
        assertEquals("", gone.line)
        assertFalse(gone.notable)
    }

    @Test fun `a clean removal or an unknown state says nothing`() {
        for (s in listOf(MediaState.REMOVED, MediaState.UNKNOWN)) {
            val v = judge(facts(s))
            assertEquals(UsbPhase.ABSENT, v.phase, "$s")
            assertEquals("", v.line, "$s")
            assertFalse(v.notable || v.readable, "$s")
            assertEquals(UsbAction.NONE, v.action, "$s")
        }
    }

    // ---- ejecting properly ----

    @Test fun `an ejection under way asks to wait, an ejected key may be pulled`() {
        val e = judge(facts(MediaState.EJECTING))
        assertEquals(UsbPhase.EJECTING, e.phase)
        assertTrue("éjection en cours" in e.line && "ne la retirez pas" in e.line, e.line)
        val done = judge(facts(MediaState.UNMOUNTED), now = t0 + 5_000)
        assertEquals(UsbPhase.EJECTED, done.phase)
        assertTrue("vous pouvez la retirer" in done.line, done.line)
        assertEquals(LineTone.GOOD, done.tone)
        assertEquals(UsbPhase.ABSENT, judge(facts(MediaState.UNMOUNTED), now = t0 + V.EJECTED_KEEP_MS).phase, "the notice does not stay for ever")
    }

    @Test fun `a key shared with a computer cannot be read`() {
        val v = judge(facts(MediaState.SHARED))
        assertEquals(UsbPhase.SHARED, v.phase)
        assertFalse(v.readable)
        assertTrue("ordinateur" in v.line, v.line)
    }

    // ---- writing ----

    @Test fun `while a copy writes to the key the TV says not to pull it`() {
        val v = judge(facts(MediaState.MOUNTED, writing = 1))
        assertEquals(UsbPhase.WRITING, v.phase)
        assertEquals("Ne retirez pas la clé : copie en cours", v.line)
        assertEquals(LineTone.WARN, v.tone)
        assertTrue(v.notable && v.readable)
        assertEquals("Ne retirez pas la clé : 2 copies en cours", judge(facts(MediaState.MOUNTED, writing = 2)).line)
        assertEquals(UsbPhase.WRITING, judge(facts(MediaState.MOUNTED, writing = 1, afterCheck = true), now = t0 + 1).phase, "a copy outranks the « prête » notice")
    }

    @Test fun `no writes, no warning`() {
        assertEquals(UsbPhase.READY, judge(facts(MediaState.MOUNTED, writing = 0)).phase)
    }

    @Test fun `a key prepared for removal says it can be pulled, even if a copy is still counted`() {
        val v = judge(facts(MediaState.MOUNTED, pull = true))
        assertEquals(UsbPhase.PULL_READY, v.phase)
        assertTrue(v.line.startsWith("Vous pouvez retirer la clé « Lexar »"), v.line)
        assertTrue("en pause" in v.line, v.line)
        assertEquals(LineTone.GOOD, v.tone)
        assertTrue(v.notable)
        assertEquals(UsbPhase.PULL_READY, judge(facts(MediaState.MOUNTED, pull = true, writing = 1)).phase)
    }

    @Test fun `the preparation only counts while the key is mounted`() {
        assertEquals(UsbPhase.CHECKING, judge(facts(MediaState.CHECKING, pull = true)).phase)
        assertEquals(UsbPhase.DAMAGED, judge(facts(MediaState.UNMOUNTABLE, pull = true)).phase)
        assertEquals(UsbPhase.CHECKING, judge(facts(MediaState.CHECKING, writing = 3)).phase, "nothing is written while Android checks")
    }

    // ---- the line of the home ----

    private fun verdicts(vararg f: UsbFacts, now: Long = t0) = f.map { V.judge(it, now) }

    @Test fun `the home shows the most urgent key line, a writing warning first`() {
        val writing = V.judge(facts(MediaState.MOUNTED, writing = 1), t0)
        val checking = V.judge(facts(MediaState.CHECKING), t0)
        val damaged = V.judge(facts(MediaState.UNMOUNTABLE, label = "B"), t0)
        val justReady = V.judge(facts(MediaState.MOUNTED, afterCheck = true), t0)
        val bad = V.judge(facts(MediaState.BAD_REMOVAL), t0)
        assertEquals(writing, V.pick(listOf(justReady, checking, damaged, writing)))
        assertEquals(damaged, V.pick(listOf(justReady, checking, damaged)))
        assertEquals(checking, V.pick(listOf(justReady, bad, checking)))
        assertEquals(bad, V.pick(listOf(justReady, bad)))
        assertEquals(justReady, V.pick(listOf(justReady)))
        val quiet = V.judge(facts(MediaState.MOUNTED), t0)
        assertNull(V.pick(listOf(quiet)), "a ready key says nothing")
        assertNull(V.pick(emptyList()))
    }

    @Test fun `the chip line puts the pull warning in front of the reception line`() {
        val writing = verdicts(facts(MediaState.MOUNTED, writing = 1))
        assertEquals("Ne retirez pas la clé : copie en cours   ·   ⬇ Réception de Film : 42 %", V.chipLine("⬇ Réception de Film : 42 %", writing, null))
        assertEquals("Ne retirez pas la clé : copie en cours", V.chipLine(null, writing, null))
        val ready = verdicts(facts(MediaState.MOUNTED))
        assertEquals("⬇ Réception de Film : 42 %", V.chipLine("⬇ Réception de Film : 42 %", ready, null), "a copy to the internal memory needs no warning")
        assertNull(V.chipLine(null, ready, null))
    }

    @Test fun `an urgent key line outranks the activation line, a mild one does not`() {
        val activation = "Clé USB : activation trouvée pour cette TV › ouvrez « Passer en production » (Accueil)"
        val checking = verdicts(facts(MediaState.CHECKING))
        assertEquals(checking[0].line, V.chipLine(null, checking, activation))
        val justReady = verdicts(facts(MediaState.MOUNTED, afterCheck = true))
        assertEquals(activation, V.chipLine(null, justReady, activation))
        assertEquals(justReady[0].line, V.chipLine(null, justReady, null))
        assertEquals(activation, V.chipLine(null, emptyList(), activation))
        assertNull(V.chipLine(null, emptyList(), null))
    }

    @Test fun `the chip carries a short version of a long guide, the screens keep the whole of it`() {
        val damaged = verdicts(facts(MediaState.UNMOUNTABLE))
        val chip = V.chipLine(null, damaged, null)!!
        assertTrue(chip.length < 120, "two lines of the chip at most: $chip")
        assertTrue(chip.startsWith("Clé « Lexar » illisible : Android n'a pas pu la réparer") && "MENU › Clé USB" in chip, chip)
        assertTrue("Utilitaire de disque" in damaged[0].line && "Utilitaire de disque" !in chip, "the guide is whole on the library and explorer screens")
        val nofs = V.chipLine(null, verdicts(facts(MediaState.NOFS)), null)!!
        assertTrue(nofs.length < 120 && "format non reconnu" in nofs && "MENU › Clé USB" in nofs, nofs)
        // a very long check too
        val long = V.chipLine(null, listOf(V.judge(facts(MediaState.CHECKING), t0 + 5 * 60_000)), null)!!
        assertTrue(long.length < 120 && "depuis 5 min" in long, long)
        assertTrue("ordinateur" in V.judge(facts(MediaState.CHECKING), t0 + 5 * 60_000).line, "the whole advice stays on the screens")
        // what is short already is the same
        val checking = verdicts(facts(MediaState.CHECKING))
        assertEquals(checking[0].line, checking[0].short)
        assertEquals(checking[0].line, V.chipLine(null, checking, null))
    }

    @Test fun `the notices send people to a line that exists in the menus`() {
        // « MENU > Clé USB » (the short guide, the orange dot) leads to the line of the Clé USB menu; « MENU › Préparer le retrait… » to the removal screen
        assertTrue(V.GUIDE_LABEL.startsWith("Clé USB"), V.GUIDE_LABEL)
        assertEquals("Préparer le retrait de la clé USB", V.PREPARE_LABEL)
        val damaged = judge(facts(MediaState.UNMOUNTABLE))
        assertTrue("MENU › Clé USB" in damaged.short, damaged.short)
        assertTrue("MENU > Clé USB" in V.note(damaged)!!.action!!, V.note(damaged)!!.action!!)
        assertTrue("MENU › ${V.PREPARE_LABEL}" in judge(facts(MediaState.BAD_REMOVAL)).line)
        assertTrue("MENU > ${V.PREPARE_LABEL}" in V.note(judge(facts(MediaState.BAD_REMOVAL)))!!.action!!)
    }

    @Test fun `a transfer line is never replaced by a mild key line`() {
        val justReady = verdicts(facts(MediaState.MOUNTED, afterCheck = true))
        assertEquals("⬇ Réception de A : 1 %", V.chipLine("⬇ Réception de A : 1 %", justReady, "x"))
        val damaged = verdicts(facts(MediaState.UNMOUNTABLE))
        assertEquals("⬇ Réception de A : 1 %", V.chipLine("⬇ Réception de A : 1 %", damaged, null), "the reception line stays; the pastille carries the key's problem")
    }

    // ---- the pastille ----

    @Test fun `the pastille only reacts to what needs attention`() {
        assertEquals(castbridge.core.ux.SignalLevel.ORANGE, V.note(judge(facts(MediaState.CHECKING)))!!.level)
        assertEquals(castbridge.core.ux.SignalLevel.ORANGE, V.note(judge(facts(MediaState.UNMOUNTABLE)))!!.level)
        assertEquals(castbridge.core.ux.SignalLevel.ORANGE, V.note(judge(facts(MediaState.NOFS)))!!.level)
        assertEquals(castbridge.core.ux.SignalLevel.ORANGE, V.note(judge(facts(MediaState.BAD_REMOVAL)))!!.level)
        assertEquals(castbridge.core.ux.SignalLevel.GREEN, V.note(judge(facts(MediaState.MOUNTED, pull = true)))!!.level)
        assertNull(V.note(judge(facts(MediaState.MOUNTED, writing = 1))), "every copy to the key would turn the pastille orange: the chip line says it instead")
        assertNull(V.note(judge(facts(MediaState.MOUNTED))))
        assertNull(V.note(judge(facts(MediaState.REMOVED))))
        assertNull(V.note(judge(facts(MediaState.MOUNTED, afterCheck = true))), "a key that just became ready is good news, not a signal")
    }

    @Test fun `the pastille takes the most urgent key that has something to say`() {
        val writing = V.judge(facts(MediaState.MOUNTED, writing = 1), t0)
        val checking = V.judge(facts(MediaState.CHECKING), t0)
        val damaged = V.judge(facts(MediaState.UNMOUNTABLE), t0)
        val pull = V.judge(facts(MediaState.MOUNTED, pull = true), t0)
        assertEquals(V.note(checking), V.signal(listOf(writing, checking)), "a copy to one key does not hide the check of the other")
        assertEquals(V.note(damaged), V.signal(listOf(checking, damaged)), "a key that cannot be read comes first")
        assertEquals(V.note(checking), V.signal(listOf(pull, checking)), "attention before good news")
        assertEquals(V.note(pull), V.signal(listOf(writing, pull)))
        assertNull(V.signal(listOf(writing)))
        assertNull(V.signal(emptyList()))
    }

    @Test fun `the pastille action says what to do`() {
        assertTrue("Patientez" in V.note(judge(facts(MediaState.CHECKING)))!!.action!!)
        assertTrue("Clé USB" in V.note(judge(facts(MediaState.UNMOUNTABLE)))!!.action!!)
        assertTrue("Préparer le retrait" in V.note(judge(facts(MediaState.BAD_REMOVAL)))!!.action!!)
    }

    // ---- text hygiene ----

    @Test fun `no line carries a path or an internal name`() {
        val all = MediaState.values().flatMap { s -> listOf(judge(facts(s)), judge(facts(s, afterCheck = true)), judge(facts(s, cut = "a.mkv")), judge(facts(s, writing = 2)), judge(facts(s, pull = true))) }
        for (v in all) {
            assertFalse("/storage" in v.line || "content://" in v.line || "file://" in v.line, v.line)
            assertFalse("sender" in v.line.lowercase() || "receiver" in v.line.lowercase(), v.line)
        }
    }

    @Test fun `a phase that is not readable never claims the key is ready`() {
        for (s in MediaState.values()) {
            val v = judge(facts(s))
            if (!s.mounted) assertFalse(v.readable, "$s")
            if (!v.readable) assertFalse("prête" in v.line, "$s : ${v.line}")
        }
    }
}
