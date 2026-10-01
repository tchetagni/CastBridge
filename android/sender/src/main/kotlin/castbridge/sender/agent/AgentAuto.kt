package castbridge.sender.agent

import android.content.Context
import castbridge.core.library.agent.AutoRename
import castbridge.core.library.agent.Entry
import castbridge.core.library.agent.Loc
import castbridge.core.library.agent.Op
import castbridge.core.library.agent.State

/**
 * « Rangement automatique des nouveaux envois » (OFF by default, see the assistant's settings): a file sent to the TV by Wi-Fi gets
 * a clean name BEFORE it is sent, only when the rules are sure (series, films, WhatsApp / camera videos). The renaming is written in
 * the assistant's journal, so "Annuler un rangement" renames the file on the TV back to its original name.
 */
object AgentAuto {
    /** The name to use on the TV for [original]; [original] itself when the option is off or the rules are not sure. */
    fun nameFor(ctx: Context, original: String): String {
        return try {
            AgentStore.init(ctx)
            if (!AgentStore.settings.autoRename || AgentStore.guard.childProfileActive) return original
            val n = AutoRename.nameFor(original, learned = AgentStore.learned) ?: return original
            val j = AgentStore.journal
            // volume "" = wherever the TV stores it; DONE from the start: the rename IS the name the file is sent under
            j.append(Entry(j.nextSeq(), "tv-auto-" + java.lang.Long.toString(System.currentTimeMillis() / 60_000, 36), System.currentTimeMillis(), Op.RENAME, "auto:$original",
                Loc("", "", original), Loc("", "", n), State.DONE, "renommé à l'envoi"))
            n
        } catch (e: Exception) { original }
    }
}
