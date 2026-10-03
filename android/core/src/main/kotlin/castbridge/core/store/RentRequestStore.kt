package castbridge.core.store

import castbridge.core.lots.QueueStore
import castbridge.core.net.JsonLite
import castbridge.core.owner.SafeFile
import java.io.File
import java.io.IOException

/** Un [QueueStore] qui sait si le dernier [load] a été servi par la copie `.bak` : la file des demandes en tient compte (état dégradé, aucune demande en attente reprise). */
interface BackupAwareStore : QueueStore {
    val loadedFromBackup: Boolean
}

/** Persistance de `files/store/requests.json` (écriture atomique avec `.bak`, comme la file de livraison) ; garde de quel fichier le dernier [load] est venu. */
class FileRentRequestStore(private val file: File) : BackupAwareStore {
    @Volatile override var loadedFromBackup: Boolean = false
        private set

    private fun valid(text: String) = runCatching { JsonLite.obj(text) }.isSuccess

    override fun load(): String? {
        val read = SafeFile.read(file, ::valid)
        loadedFromBackup = read?.fromBackup == true
        return read?.text
    }

    override fun save(json: String) {
        try { SafeFile.write(file, json, ::valid) } catch (e: IOException) { throw IOException("file des demandes non écrite", e) }
    }
}
