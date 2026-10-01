package castbridge.core.lots

/** A lot = a homogeneous, versioned, signed bundle of the data of ONE feature for ONE scope (e.g. all Apprendre content of one class). */
data class LotId(val feature: String, val scope: String)   // feature: "learn" | "quiz"; scope: e.g. "cm2", "3e", "tle-c", "droit-l1"

/** What the server/phone/TV know about a lot version. [bytes] = size on disk once installed; [sha256] of the lot file. */
data class LotMeta(val id: LotId, val version: Int, val bytes: Long, val sha256: String, val title: String, val minAppVersion: Int = 0)

/** Where lots come from: the server (phone side), or the phone (TV side). */
interface LotSource {
    fun catalog(): List<LotMeta>
    fun open(id: LotId, version: Int): java.io.InputStream?
}

/** A feature that consumes lots (Apprendre, Quiz): installs a verified lot file, removes it, lists what is installed. */
interface LotConsumer {
    val feature: String
    fun install(meta: LotMeta, data: java.io.File): Boolean
    fun remove(id: LotId)
    fun installed(): List<LotMeta>
}

object LotBudget {
    const val TV_MAX_BYTES = 10L shl 20      // Apprendre + Quiz together on the TV, bundled starter data included
    const val PHONE_MAX_BYTES = 100L shl 20  // all lots on the phone
}
