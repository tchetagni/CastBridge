package castbridge.sender

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import castbridge.core.parental.tab.Heatmap
import castbridge.core.parental.tab.Quality

/*
 * Charts drawn with Canvas (no chart library). Rules: colours come from the theme (dark and light of the charte), every chart has a text
 * alternative (contentDescription AND the numbers written next to it by the caller), and a missing value is drawn as an empty dashed slot,
 * never as a zero-height bar (a gap is not a zero).
 */

/** One bar of a [BarChart]. [value] null = no data (gap); [extra] stacked on top = the best-effort part (other apps). */
data class Bar(val label: String, val value: Float?, val extra: Float? = null)

@Composable
fun BarChart(bars: List<Bar>, description: String, modifier: Modifier = Modifier, height: Int = 120) {
    val measured = MaterialTheme.colorScheme.primary
    val effort = MaterialTheme.colorScheme.tertiary
    val gap = MaterialTheme.colorScheme.outline
    val label = MaterialTheme.colorScheme.onSurfaceVariant
    val max = (bars.maxOfOrNull { (it.value ?: 0f) + (it.extra ?: 0f) } ?: 0f).coerceAtLeast(1f)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height.dp)) {
            if (bars.isEmpty()) return@Canvas
            val slot = size.width / bars.size
            val bw = (slot * 0.6f).coerceAtMost(48.dp.toPx())
            bars.forEachIndexed { i, b ->
                val x = i * slot + (slot - bw) / 2
                val v = b.value
                if (v == null) {
                    // gap: a dashed outline, so that « no report » is visible and is not read as zero
                    drawRect(gap, Offset(x, size.height - 24.dp.toPx()), Size(bw, 24.dp.toPx()), style = Stroke(1.5.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
                } else {
                    val h = v / max * size.height; val e = (b.extra ?: 0f) / max * size.height
                    drawRect(measured, Offset(x, size.height - h), Size(bw, h.coerceAtLeast(1f)))
                    if (e > 0f) drawRect(effort, Offset(x, size.height - h - e), Size(bw, e))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) { bars.forEach { Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = label, maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
    }
}

/** A line over the days; a missing day (null) breaks the line instead of dropping it to zero. */
@Composable
fun LineChart(values: List<Float?>, description: String, modifier: Modifier = Modifier, height: Int = 100) {
    val line = MaterialTheme.colorScheme.primary
    val axis = MaterialTheme.colorScheme.outlineVariant
    val max = (values.filterNotNull().maxOrNull() ?: 0f).coerceAtLeast(1f)
    Canvas(modifier.fillMaxWidth().height(height.dp).semantics { contentDescription = description }) {
        drawLine(axis, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (values.size < 2) return@Canvas
        val step = size.width / (values.size - 1)
        var path: Path? = null
        values.forEachIndexed { i, v ->
            if (v == null) { path?.let { drawPath(it, line, style = Stroke(2.5.dp.toPx())) }; path = null; return@forEachIndexed }
            val p = Offset(i * step, size.height - v / max * (size.height - 6.dp.toPx()) - 3.dp.toPx())
            drawCircle(line, 3.dp.toPx(), p)
            if (path == null) path = Path().apply { moveTo(p.x, p.y) } else path!!.lineTo(p.x, p.y)
        }
        path?.let { drawPath(it, line, style = Stroke(2.5.dp.toPx())) }
    }
}

/** Share of each part in a donut; the parts are also listed in text by the caller ([description]). */
@Composable
fun Donut(parts: List<Pair<Float, Color>>, description: String, modifier: Modifier = Modifier, size: Int = 110) {
    val total = parts.sumOf { it.first.toDouble() }.toFloat()
    val empty = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.size(size.dp).semantics { contentDescription = description }) {
        val stroke = 18.dp.toPx(); val d = this.size.minDimension - stroke
        val tl = Offset(stroke / 2, stroke / 2)
        if (total <= 0f) { drawArc(empty, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke)); return@Canvas }
        var start = -90f
        for ((v, c) in parts) { val sweep = v / total * 360f; drawArc(c, start, (sweep - 1f).coerceAtLeast(0.5f), false, tl, Size(d, d), style = Stroke(stroke)); start += sweep }
    }
}

/** Usage by weekday (rows, Monday first) and hour (columns): the darker the cell, the more minutes. Built from timed events of the TV only. */
@Composable
fun HeatmapView(h: Heatmap, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val max = h.max().coerceAtLeast(1)
    val peaks = h.peakHours(3).joinToString(", ") { "${it.first} h" }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(112.dp).semantics { contentDescription = if (h.hasData) "Carte d'utilisation par jour de la semaine et par heure. Heures de pointe : $peaks." else "Carte d'utilisation : aucune donnée chronométrée." }) {
            val cw = size.width / 24f; val ch = size.height / 7f
            for (d in 0 until 7) for (hr in 0 until 24) {
                val m = h.min(d, hr)
                drawRect(if (m <= 0) empty else base.copy(alpha = (0.2f + 0.8f * m / max).coerceAtMost(1f)), Offset(hr * cw + 0.5f, d * ch + 0.5f), Size(cw - 1f, ch - 1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { listOf("0 h", "6 h", "12 h", "18 h", "23 h").forEach { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        Text("Lignes : lundi → dimanche.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** « MESURÉ » / « MEILLEUR EFFORT » / « INDISPONIBLE »: a symbol AND a word (never colour alone). */
@Composable
fun QualityTag(q: Quality, modifier: Modifier = Modifier) {
    val (sym, color) = when (q) {
        Quality.MEASURED -> "●" to MaterialTheme.colorScheme.primary
        Quality.BEST_EFFORT -> "◐" to MaterialTheme.colorScheme.tertiary
        Quality.UNAVAILABLE -> "○" to MaterialTheme.colorScheme.outline
    }
    Row(modifier.border(1.dp, color, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp).semantics { contentDescription = "Qualité de la donnée : ${q.label}" }, verticalAlignment = Alignment.CenterVertically) {
        Text("$sym ${q.label}", style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}
