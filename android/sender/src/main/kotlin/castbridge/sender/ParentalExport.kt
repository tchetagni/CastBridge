package castbridge.sender

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import castbridge.core.parental.tab.DocLine
import castbridge.core.parental.tab.LineStyle
import castbridge.core.parental.tab.PdfLayout
import java.io.File

/**
 * Sharing of a summary / the detailed events (docs/PARENTAL.md, « Exports »). Nothing is uploaded: the file is written in the app's cache and
 * handed to the Android share sheet ONLY when the parent taps a share button; the parent chooses where it goes. The cache copy is replaced
 * by the next export (and is never in a backup).
 */
object ParentalExport {
    private const val DIR = "parental_exports"

    fun shareText(ctx: Context, subject: String, text: String) {
        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text)
        ctx.startActivity(Intent.createChooser(i, "Partager le résumé").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun shareCsv(ctx: Context, name: String, csv: String) = shareFile(ctx, name, "text/csv", csv.toByteArray(Charsets.UTF_8).let { "﻿".toByteArray(Charsets.UTF_8) + it })

    fun sharePdf(ctx: Context, name: String, lines: List<DocLine>) = shareFile(ctx, name, "application/pdf", pdf(lines))

    private fun shareFile(ctx: Context, name: String, mime: String, bytes: ByteArray) {
        val dir = File(ctx.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, name).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".parentalshare", f)
        val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Partager").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A4 pages (595 x 842 pt) painted from the tested layout of [PdfLayout]. */
    private fun pdf(lines: List<DocLine>): ByteArray {
        val doc = PdfDocument()
        val title = Paint().apply { textSize = 18f; typeface = Typeface.DEFAULT_BOLD }
        val head = Paint().apply { textSize = 14f; typeface = Typeface.DEFAULT_BOLD }
        val body = Paint().apply { textSize = 11f }
        val small = Paint().apply { textSize = 9f; color = 0xFF555555.toInt() }
        PdfLayout.paginate(lines).forEachIndexed { n, page ->
            val p = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, n + 1).create())
            var y = 50f
            for (l in page.lines) {
                p.canvas.drawText(l.text, 40f, y, when (l.style) { LineStyle.TITLE -> title; LineStyle.HEADING -> head; LineStyle.BODY -> body; LineStyle.SMALL -> small })
                y += 16f
            }
            doc.finishPage(p)
        }
        val out = java.io.ByteArrayOutputStream()
        doc.writeTo(out); doc.close()
        return out.toByteArray()
    }
}
