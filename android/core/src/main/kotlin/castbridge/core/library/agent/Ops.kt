package castbridge.core.library.agent

data class Stat(val size: Long, val mtime: Long = 0)

sealed class OpResult {
    data class Ok(val loc: Loc, val trashId: String? = null) : OpResult()
    data class Fail(val reason: String) : OpResult()
}

/**
 * What the executor needs from a library: the TV (through its HTTP routes, PIN protected) or the phone (system file picker).
 * All calls are blocking; the UI runs them on a background thread. Implementations must never overwrite an existing file and
 * must refuse anything outside the library.
 */
interface LibraryOps {
    /** Folders exist (phone) or not (the TV library is flat). */
    val folders: Boolean

    /** Live volumes with their current free space. */
    fun volumes(): List<VolumeInfo>

    /** Size of the file at [loc], or null when it is not there. Names are compared the way the file system does (FAT is case-insensitive). */
    fun stat(loc: Loc): Stat?

    /** Would [loc] collide with something when a file gets this name? (TV: any volume, the TV library has one name space.) */
    fun nameTaken(loc: Loc): Boolean = stat(loc) != null

    fun isPlaying(loc: Loc): Boolean

    fun mkdirs(volume: String, folder: String): OpResult

    /** Renames in place. Must fail (not overwrite) if the name is taken. */
    fun rename(loc: Loc, newName: String): OpResult

    /** Moves into [folder] of the same volume (phone). */
    fun moveToFolder(loc: Loc, folder: String): OpResult

    /** Copies to another volume, verifies, then removes the source (TV: /api/storage/move). Blocking; [cancelled] stops it cleanly. */
    fun moveToVolume(loc: Loc, toVolume: String, onProgress: (done: Long, total: Long) -> Unit, cancelled: () -> Boolean): OpResult

    /** Puts the file in the "Corbeille CastBridge" (recoverable, never a real delete). */
    fun trash(loc: Loc): OpResult

    /** Takes an item out of the trash back to [original] (another name if that one is taken). */
    fun restore(trashId: String, original: Loc): OpResult

    /** After a crash: the trash id of a file that was being trashed, if it got there. */
    fun findInTrash(original: Loc): String?
}
