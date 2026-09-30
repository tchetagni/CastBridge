package castbridge.core.learn

import castbridge.core.quiz.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Content packs (docs/LEARN.md § Packs): one zip per exam × subject (or level × subject) =
 * `manifest.json` (id, version, exam/level, subject, size, sha256 of every file, optional Ed25519 signature)
 * + `pack.json` + `lessons/<name>.json` + optional `media/` (small webp/svg/ogg). Videos are never inside a pack.
 */
data class PackManifest(
    val id: String, val version: Int, val title: String, val lang: String, val cursus: String, val level: String,
    val subject: String, val exam: String?, val status: String, val size: Long, val files: List<FileEntry>,
    val createdAt: String?, val signature: SignatureInfo?, val format: Int = PackFormat.VERSION,
    /** Counts for catalogs (fiches, exercises, mock exams). */
    val lessons: Int = 0, val exercises: Int = 0, val mockExams: Int = 0,
) {
    data class FileEntry(val path: String, val size: Long, val sha256: String)
    data class SignatureInfo(val alg: String, val keyId: String, val value: String)

    /** The map that is serialized; [signed] leaves the signature out (the signed bytes). */
    fun toMap(signed: Boolean = true): LinkedHashMap<String, Any?> = linkedMapOf(
        "format" to format, "id" to id, "version" to version, "title" to title, "lang" to lang, "cursus" to cursus,
        "level" to level, "subject" to subject, "exam" to exam, "status" to status, "size" to size,
        "lessons" to lessons, "exercises" to exercises, "mockExams" to mockExams, "createdAt" to createdAt,
        "files" to files.map { linkedMapOf("path" to it.path, "size" to it.size, "sha256" to it.sha256) },
    ).also { m -> if (signed && signature != null) m["signature"] = linkedMapOf("alg" to signature.alg, "keyId" to signature.keyId, "value" to signature.value) }

    fun json(): String = Json.write(toMap())
    /** Exactly the bytes covered by the signature: the manifest without its "signature" field. */
    fun signedBytes(): ByteArray = Json.write(toMap(signed = false)).toByteArray(Charsets.UTF_8)
    val fileName: String get() = PackFormat.fileName(id, version)

    companion object {
        fun parse(json: String): PackManifest {
            val m = Json.obj(json)
            fun s(k: String) = m[k] as? String
            fun n(k: String) = (m[k] as? Number)?.toLong()
            val fmt = n("format")?.toInt() ?: 1
            if (fmt > PackFormat.VERSION) throw LessonJson.ParseError("manifest : format $fmt non pris en charge")
            @Suppress("UNCHECKED_CAST")
            val files = (m["files"] as? List<Any?> ?: throw LessonJson.ParseError("manifest : \"files\" manquant")).map { o ->
                val f = o as? Map<String, Any?> ?: throw LessonJson.ParseError("manifest : fichier invalide")
                FileEntry(f["path"] as? String ?: "", (f["size"] as? Number)?.toLong() ?: -1, (f["sha256"] as? String ?: "").lowercase())
            }
            @Suppress("UNCHECKED_CAST")
            val sig = (m["signature"] as? Map<String, Any?>)?.let { SignatureInfo(it["alg"] as? String ?: "", it["keyId"] as? String ?: "", it["value"] as? String ?: "") }
            return PackManifest(
                id = s("id") ?: throw LessonJson.ParseError("manifest : id manquant"), version = n("version")?.toInt() ?: throw LessonJson.ParseError("manifest : version manquante"),
                title = s("title") ?: "", lang = s("lang") ?: "fr", cursus = s("cursus") ?: "", level = s("level") ?: "", subject = s("subject") ?: "",
                exam = s("exam"), status = s("status") ?: "draft", size = n("size") ?: 0, files = files, createdAt = s("createdAt"), signature = sig, format = fmt,
                lessons = n("lessons")?.toInt() ?: 0, exercises = n("exercises")?.toInt() ?: 0, mockExams = n("mockExams")?.toInt() ?: 0,
            )
        }
    }
}

object PackFormat {
    const val VERSION = 1
    const val MANIFEST = "manifest.json"
    const val SUFFIX = ".learn.zip"
    /** Limits when reading a pack (a pack is text + small media: a few MB at most). */
    const val MAX_FILES = 2000
    const val MAX_UNCOMPRESSED = 64L shl 20
    const val MAX_FILE = 16L shl 20

    fun fileName(id: String, version: Int) = "$id-v$version$SUFFIX"

    fun sha256(b: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** A safe path inside a pack: relative, no «..», only manifest/pack/lessons/media. */
    fun safePath(p: String): Boolean =
        p.isNotEmpty() && !p.startsWith("/") && !p.contains('\\') && p.split('/').none { it == ".." || it == "." || it.isEmpty() } &&
            (p == MANIFEST || p == "pack.json" || p.startsWith("lessons/") || p.startsWith("media/"))
}

/** Ed25519 check of a manifest (keys = raw 32-byte public keys, base64). No trusted key configured = unsigned packs accepted. */
class PackSignatures(private val trusted: Map<String, String> = emptyMap()) {
    val required: Boolean get() = trusted.isNotEmpty()

    /** null = OK, else why the signature is refused. */
    fun check(m: PackManifest): String? {
        if (!required) return null
        val sig = m.signature ?: return "pack non signé (une signature est exigée)"
        if (sig.alg != "Ed25519") return "algorithme de signature ${sig.alg} non pris en charge"
        val key = trusted[sig.keyId] ?: return "clé de signature « ${sig.keyId} » inconnue"
        return try {
            val pub = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(X509_PREFIX + Base64.getDecoder().decode(key)))
            val v = Signature.getInstance("Ed25519"); v.initVerify(pub); v.update(m.signedBytes())
            if (v.verify(Base64.getDecoder().decode(sig.value))) null else "signature invalide"
        } catch (e: java.security.GeneralSecurityException) { "signature non vérifiable sur cet appareil (${e.javaClass.simpleName})" }
        catch (e: IllegalArgumentException) { "signature mal formée" }
    }

    companion object {
        /** DER prefix of an X.509 SubjectPublicKeyInfo for a raw Ed25519 key. */
        val X509_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)
    }
}

/** A verified pack: its manifest, its content and the raw files (media are served from here). */
class VerifiedPack(val manifest: PackManifest, val pack: Pack, val files: Map<String, ByteArray>, val warnings: List<String>)

object PackReader {
    class Refused(msg: String) : Exception(msg)

    /** Reads only the manifest (fast listing of a folder of packs; the pack is fully checked when opened). */
    fun manifest(input: InputStream): PackManifest {
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == PackFormat.MANIFEST) return PackManifest.parse(String(readLimited(z, 1L shl 20), Charsets.UTF_8))
            }
        }
        throw Refused("manifest.json absent")
    }

    fun manifest(f: File): PackManifest = f.inputStream().buffered().use { manifest(it) }

    /**
     * Opens and checks a pack: safe paths, size limits, every file of the manifest present with the right size and
     * sha256, no file outside the manifest, signature (when keys are configured), then the content itself (parse +
     * [LessonValidator]). Throws [Refused] with the reason: an altered pack is never activated.
     */
    fun read(bytes: ByteArray, signatures: PackSignatures = PackSignatures(), knownLessons: Set<String> = emptySet()): VerifiedPack =
        read(ByteArrayInputStream(bytes), signatures, knownLessons)

    fun read(input: InputStream, signatures: PackSignatures = PackSignatures(), knownLessons: Set<String> = emptySet()): VerifiedPack {
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                if (!PackFormat.safePath(e.name)) throw Refused("chemin interdit dans le pack : ${e.name}")
                if (files.containsKey(e.name)) throw Refused("fichier en double : ${e.name}")
                if (files.size >= PackFormat.MAX_FILES) throw Refused("trop de fichiers")
                val b = readLimited(z, PackFormat.MAX_FILE)
                total += b.size
                if (total > PackFormat.MAX_UNCOMPRESSED) throw Refused("pack trop gros une fois décompressé")
                files[e.name] = b
            }
        }
        val mBytes = files.remove(PackFormat.MANIFEST) ?: throw Refused("manifest.json absent")
        val m = try { PackManifest.parse(String(mBytes, Charsets.UTF_8)) } catch (e: IllegalArgumentException) { throw Refused("manifest invalide : ${e.message}") }
        val listed = m.files.associateBy { it.path }
        for (f in m.files) {
            val b = files[f.path] ?: throw Refused("fichier manquant : ${f.path}")
            if (b.size.toLong() != f.size) throw Refused("taille différente pour ${f.path} (pack altéré ?)")
            if (PackFormat.sha256(b) != f.sha256) throw Refused("empreinte sha256 différente pour ${f.path} (pack altéré)")
        }
        files.keys.firstOrNull { it !in listed }?.let { throw Refused("fichier non déclaré dans le manifest : $it") }
        signatures.check(m)?.let { throw Refused(it) }
        val texts = files.filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }
        val pack = try { LessonJson.parsePack(texts) } catch (e: IllegalArgumentException) { throw Refused("contenu illisible : ${e.message}") }
        if (pack.id != m.id || pack.version != m.version) throw Refused("le manifest (${m.id} v${m.version}) ne correspond pas au contenu (${pack.id} v${pack.version})")
        val rep = LessonValidator(knownLessons).validate(pack)
        if (!rep.ok) throw Refused("contenu invalide : ${rep.errors.take(3).joinToString("; ")}")
        return VerifiedPack(m, pack, files, rep.warnings)
    }

    private fun readLimited(input: InputStream, max: Long): ByteArray {
        val out = ByteArrayOutputStream(); val buf = ByteArray(16 shl 10); var n = 0L
        while (true) {
            val r = input.read(buf); if (r < 0) break
            n += r; if (n > max) throw Refused("fichier trop gros dans le pack")
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }
}

/**
 * Builds pack zips from the sources of the repository (content/learn/<pack id>/pack.json, lessons/, media/).
 * Deterministic: sorted entries, fixed dates, so the same sources always give the same sha256.
 */
object PackBuilder {
    class Built(val manifest: PackManifest, val bytes: ByteArray, val warnings: List<String>)

    /** Reads a source folder into path → bytes (only the allowed paths; .md notes and other files are ignored). */
    fun sources(dir: File): Map<String, ByteArray> = dir.walkTopDown().filter { it.isFile }.mapNotNull { f ->
        val rel = f.relativeTo(dir).invariantSeparatorsPath
        if (rel == "pack.json" || (rel.startsWith("lessons/") && rel.endsWith(".json")) || rel.startsWith("media/")) rel to f.readBytes() else null
    }.toMap().toSortedMap()

    fun build(files: Map<String, ByteArray>, createdAt: String? = null, signer: ((ByteArray) -> Pair<String, String>)? = null,
              knownLessons: Set<String> = emptySet()): Built {
        val texts = files.filterKeys { it.endsWith(".json") }.mapValues { String(it.value, Charsets.UTF_8) }
        val pack = LessonJson.parsePack(texts)
        val rep = LessonValidator(knownLessons).validate(pack)
        if (!rep.ok) throw PackReader.Refused("${pack.id} : ${rep.errors.size} erreur(s) : ${rep.errors.take(5).joinToString("; ")}")
        val entries = files.toSortedMap().map { (p, b) -> PackManifest.FileEntry(p, b.size.toLong(), PackFormat.sha256(b)) }
        var m = PackManifest(pack.id, pack.version, pack.title, pack.lang, pack.cursus, pack.level, pack.subject, pack.exam, pack.status.key,
            entries.sumOf { it.size }, entries, createdAt, null, lessons = pack.lessons.size, exercises = pack.exercises.size, mockExams = pack.mockExams.size)
        signer?.let { s -> val (keyId, sig) = s(m.signedBytes()); m = m.copy(signature = PackManifest.SignatureInfo("Ed25519", keyId, sig)) }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.setLevel(9)
            fun put(name: String, b: ByteArray) { val e = ZipEntry(name); e.time = FIXED_TIME; z.putNextEntry(e); z.write(b); z.closeEntry() }
            put(PackFormat.MANIFEST, m.json().toByteArray(Charsets.UTF_8))
            for ((p, b) in files.toSortedMap()) put(p, b)
        }
        return Built(m, out.toByteArray(), rep.warnings)
    }

    /** 2026-01-01 00:00 local: zip dates are fixed so that builds are reproducible. */
    private const val FIXED_TIME = 1767225600000L
}
