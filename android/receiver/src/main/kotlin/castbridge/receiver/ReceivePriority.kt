package castbridge.receiver

import android.os.Process

/**
 * « La lecture d'abord » on the TV (castbridge.core.xfer.PlaybackPriority): the HTTP thread that receives, hashes and writes a copy goes to
 * background priority while a video plays, so libVLC's decoder and the UI come first on the 4×A53; it is put back to default after every request.
 * Acts on the calling thread only; never throws.
 */
object ReceivePriority : castbridge.core.xfer.ThreadPriorityPort {
    override fun background() { runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) } }
    override fun normal() { runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT) } }
}
