package castbridge.core.library.agent

/**
 * A simulated Storage Access Framework provider, in memory. It behaves like the real ones where they are awkward:
 *  - ids are PATHS (like ExternalStorageProvider), so every rename or move changes the id of the document;
 *  - names are case-insensitive (FAT / exFAT on an SD card);
 *  - [supportsMove] = false: `moveDocument` is refused (many cloud / third-party providers);
 *  - [silentRename]: a name already taken in the folder gets " (1)" appended instead of an error (what some providers do);
 *  - [mounted] = false: the volume was removed, nothing can be read or written;
 *  - [readOnly]: every write is refused.
 */
class FakeDocProvider(val label: String = "Carte SD", var supportsMove: Boolean = true, var silentRename: Boolean = false) : DocProvider {
    private class Node(var name: String, val dir: Boolean, var size: Long = 0, var mtime: Long = 0, var duration: Long = 0, val kids: MutableList<Node> = ArrayList())

    private val top = Node("", true)
    var mounted = true
    var readOnly = false
    var childrenCalls = 0
    var durationCalls = 0
    var free = 50L shl 30

    override val rootId = "root"
    private fun idOf(path: List<String>) = if (path.isEmpty()) "root" else "root/" + path.joinToString("/")
    private fun pathOf(id: String): List<String> = if (id == "root") emptyList() else id.removePrefix("root/").split('/')
    private fun nodeAt(path: List<String>): Node? { var n = top; for (s in path) n = n.kids.firstOrNull { it.name.equals(s, true) } ?: return null; return n }

    /** Test setup: creates folders as needed. */
    fun add(path: String, size: Long = 10L shl 20, mtime: Long = 1_700_000_000_000, duration: Long = 0) {
        val segs = path.split('/')
        var n = top
        for ((i, s) in segs.withIndex()) {
            val last = i == segs.lastIndex
            var k = n.kids.firstOrNull { it.name.equals(s, true) }
            if (k == null) { k = Node(s, !last, if (last) size else 0, mtime, if (last) duration else 0); n.kids += k }
            n = k
        }
    }

    fun exists(path: String) = nodeAt(path.split('/')) != null
    fun all(): List<String> { val out = ArrayList<String>(); fun go(n: Node, p: String) { for (k in n.kids) { val q = if (p.isEmpty()) k.name else "$p/${k.name}"; if (k.dir) go(k, q) else out += q } }; go(top, ""); return out.sorted() }
    fun sizeOf(path: String) = nodeAt(path.split('/'))?.size
    fun setSize(path: String, size: Long) { nodeAt(path.split('/'))!!.size = size }
    fun remove(path: String) { val p = path.split('/'); nodeAt(p.dropLast(1))!!.kids.removeAll { it.name.equals(p.last(), true) } }

    private fun entry(path: List<String>, n: Node) = DocEntry(idOf(path), n.name, n.dir, n.size, n.mtime)

    override fun children(parentId: String): List<DocEntry> {
        childrenCalls++
        if (!mounted) return emptyList()
        val p = pathOf(parentId)
        return nodeAt(p)?.kids?.map { entry(p + it.name, it) }.orEmpty()
    }

    override fun info(id: String): DocEntry? { if (!mounted) return null; val p = pathOf(id); return nodeAt(p)?.let { entry(p, it) } }

    override fun createDir(parentId: String, name: String): DocEntry? {
        if (!mounted || readOnly) return null
        val p = pathOf(parentId); val parent = nodeAt(p) ?: return null
        if (parent.kids.any { it.name.equals(name, true) }) return null
        val n = Node(name, true); parent.kids += n
        return entry(p + name, n)
    }

    override fun rename(id: String, newName: String): DocEntry? {
        if (!mounted || readOnly) return null
        val p = pathOf(id); val n = nodeAt(p) ?: return null
        val parent = nodeAt(p.dropLast(1))!!
        var finalName = newName
        if (parent.kids.any { it !== n && it.name.equals(newName, true) }) {
            if (!silentRename) return null
            var i = 1; while (parent.kids.any { it.name.equals(finalName, true) }) { i++; finalName = newName.substringBeforeLast('.') + " ($i)." + newName.substringAfterLast('.') }
        }
        n.name = finalName
        return entry(p.dropLast(1) + finalName, n)
    }

    override fun move(id: String, fromParentId: String, toParentId: String): DocEntry? {
        if (!mounted || readOnly || !supportsMove) return null
        val p = pathOf(id); val n = nodeAt(p) ?: return null
        val src = nodeAt(pathOf(fromParentId)) ?: return null
        val dstPath = pathOf(toParentId); val dst = nodeAt(dstPath) ?: return null
        if (dst.kids.any { it.name.equals(n.name, true) }) return null
        src.kids.remove(n); dst.kids += n
        return entry(dstPath + n.name, n)
    }

    override fun delete(id: String): Boolean {
        if (!mounted || readOnly) return false
        val p = pathOf(id); val parent = nodeAt(p.dropLast(1)) ?: return false
        return parent.kids.removeAll { it.name.equals(p.last(), true) }
    }

    override fun durationMs(id: String): Long { durationCalls++; return nodeAt(pathOf(id))?.duration ?: 0 }

    override fun volume() = VolumeInfo("phone", label, "phone", free, 64L shl 30, !readOnly)
}

class MapDurationCache : DurationCache {
    val map = HashMap<String, Long>()
    override fun get(key: String) = map[key]
    override fun put(key: String, durationMs: Long) { map[key] = durationMs }
}
