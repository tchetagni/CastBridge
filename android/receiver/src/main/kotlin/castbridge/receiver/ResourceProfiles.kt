package castbridge.receiver

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import castbridge.core.device.ResourceProfile

/**
 * Lit ActivityManager / Runtime UNE fois et en tire le [ResourceProfile] de cette TV (décision pure dans le noyau, docs/TV-RESSOURCES-FAIBLES.md).
 * Aucune permission, aucun `largeHeap`. Toute lecture qui échoue = valeur inconnue (0) : le profil normal reste le comportement d'avant.
 */
object ResourceProfiles {
    @Volatile private var cached: ResourceProfile? = null

    fun of(ctx: Context): ResourceProfile = cached ?: synchronized(this) {
        cached ?: read(ctx.applicationContext).also { cached = it }
    }

    /** Sans contexte (déjà lu une fois) : le profil connu, sinon normal. */
    fun peek(): ResourceProfile = cached ?: ResourceProfile.NORMAL

    private fun read(ctx: Context): ResourceProfile {
        val am = runCatching { ctx.getSystemService(ActivityManager::class.java) }.getOrNull()
        val ram = runCatching { ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem }.getOrDefault(0L)
        return ResourceProfile.decide(
            ramTotalBytes = ram,
            memoryClassMb = runCatching { am?.memoryClass ?: 0 }.getOrDefault(0),
            isLowRamDevice = runCatching { am?.isLowRamDevice == true }.getOrDefault(false),
            cores = Runtime.getRuntime().availableProcessors(),
            is64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
            heapBytes = Runtime.getRuntime().maxMemory(),
        )
    }
}
