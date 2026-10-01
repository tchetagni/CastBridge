package castbridge.core.library.agent

/** An in-memory library for the executor tests: volumes, folders (optional), a trash, playing files, and failure / crash injection. */
class FakeOps(override val folders: Boolean = false, val flat: Boolean = !folders) : LibraryOps {
    class Crash : RuntimeException("simulated power cut")

    val vols = LinkedHashMap<String, VolumeInfo>()
    /** (volume, folder, name) lower-cased -> (real name, size) */
    private val files = LinkedHashMap<Triple<String, String, String>, Pair<String, Long>>()
    val trashed = LinkedHashMap<String, Pair<Loc, Long>>()
    val playing = HashSet<String>()
    val log = ArrayList<String>()
    var crashAfter: String? = null          // op name ("rename", "moveToVolume", "trash") that mutates then throws
    var failOn: (String, Loc) -> String? = { _, _ -> null }
    var dirs = HashSet<String>()
    private var trashSeq = 0

    private fun k(l: Loc) = Triple(l.volume, l.folder.lowercase(), l.name.lowercase())

    fun add(volume: String, name: String, size: Long, folder: String = "") { files[Triple(volume, folder.lowercase(), name.lowercase())] = name to size; vol(volume)?.let { vols[volume] = it.copy(free = it.free - size) } }
    fun vol(id: String) = vols[id]
    fun addVolume(v: VolumeInfo) { vols[v.id] = v }
    fun names(volume: String? = null): List<String> = files.filter { volume == null || it.key.first == volume }.map { (if (it.key.second.isEmpty()) "" else files[it.key]!!.first.let { _ -> "" }) + it.value.first }.sorted()
    fun paths(): List<String> = files.map { (k, v) -> k.first + ":" + (if (k.second.isEmpty()) "" else k.second + "/") + v.first }.sorted()
    fun has(volume: String, name: String, folder: String = "") = files.containsKey(Triple(volume, folder.lowercase(), name.lowercase()))

    override fun volumes() = vols.values.toList()
    override fun stat(loc: Loc): Stat? = files[k(loc)]?.let { Stat(it.second) }
    override fun nameTaken(loc: Loc): Boolean = if (flat) files.keys.any { it.third == loc.name.lowercase() } else stat(loc) != null
    override fun isPlaying(loc: Loc) = loc.name in playing
    override fun mkdirs(volume: String, folder: String): OpResult { dirs += "$volume:$folder"; return OpResult.Ok(Loc(volume, folder, "")) }

    private fun fail(op: String, l: Loc): OpResult.Fail? = failOn(op, l)?.let { OpResult.Fail(it) }
    private fun maybeCrash(op: String) { if (crashAfter == op) { crashAfter = null; throw Crash() } }

    override fun rename(loc: Loc, newName: String): OpResult {
        fail("rename", loc)?.let { return it }
        log += "rename ${loc.name} -> $newName"
        val cur = files[k(loc)] ?: return OpResult.Fail("introuvable")
        if (nameTaken(loc.copy(name = newName))) return OpResult.Fail("le nom existe")
        files.remove(k(loc)); files[k(loc.copy(name = newName))] = newName to cur.second
        maybeCrash("rename")
        return OpResult.Ok(loc.copy(name = newName))
    }

    override fun moveToFolder(loc: Loc, folder: String): OpResult {
        fail("moveToFolder", loc)?.let { return it }
        log += "moveToFolder ${loc.name} -> $folder"
        val cur = files[k(loc)] ?: return OpResult.Fail("introuvable")
        val to = loc.copy(folder = folder)
        if (stat(to) != null) return OpResult.Fail("le nom existe")
        files.remove(k(loc)); files[k(to)] = cur
        maybeCrash("moveToFolder")
        return OpResult.Ok(to)
    }

    override fun moveToVolume(loc: Loc, toVolume: String, onProgress: (Long, Long) -> Unit, cancelled: () -> Boolean): OpResult {
        fail("moveToVolume", loc)?.let { return it }
        log += "moveToVolume ${loc.name} -> $toVolume"
        val cur = files[k(loc)] ?: return OpResult.Fail("introuvable")
        val to = loc.copy(volume = toVolume)
        if (stat(to) != null) return OpResult.Fail("existe déjà")
        files.remove(k(loc)); files[k(to)] = cur
        vols[loc.volume]?.let { vols[loc.volume] = it.copy(free = it.free + cur.second) }
        vols[toVolume]?.let { vols[toVolume] = it.copy(free = it.free - cur.second) }
        onProgress(cur.second, cur.second)
        maybeCrash("moveToVolume")
        return OpResult.Ok(to)
    }

    override fun trash(loc: Loc): OpResult {
        fail("trash", loc)?.let { return it }
        log += "trash ${loc.name}"
        val cur = files[k(loc)] ?: return OpResult.Fail("introuvable")
        val id = "t${++trashSeq}"
        files.remove(k(loc)); trashed[id] = loc to cur.second
        maybeCrash("trash")
        return OpResult.Ok(loc, id)
    }

    override fun restore(trashId: String, original: Loc): OpResult {
        val (loc, size) = trashed[trashId] ?: return OpResult.Fail("introuvable dans la corbeille")
        var target = loc
        var n = 2
        while (nameTaken(target)) { target = loc.copy(name = loc.name.substringBeforeLast('.') + " (restauré $n)." + loc.name.substringAfterLast('.')); n++ }
        files[k(target)] = target.name to size
        trashed.remove(trashId)
        log += "restore ${target.name}"
        return OpResult.Ok(target)
    }

    override fun findInTrash(original: Loc): String? = trashed.entries.firstOrNull { it.value.first == original }?.key
}
