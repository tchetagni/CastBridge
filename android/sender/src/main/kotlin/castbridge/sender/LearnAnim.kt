package castbridge.sender

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import castbridge.core.learn.AnimPlayer
import castbridge.core.learn.AnimSettings
import castbridge.core.learn.AnimatedFigure
import castbridge.core.learn.Block

/** « Réduire les animations »: the app switch (kept here) and Android's « remove animations » (animator scale 0). */
internal object PhoneAnimPrefs {
    private const val FILE = "learn_anim"
    fun load(ctx: Context) {
        AnimSettings.appReduce = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("reduce", false)
        AnimSettings.systemReduce = runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    fun setApp(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("reduce", v).apply()
        AnimSettings.appReduce = v
    }
}

/**
 * Animated illustration on the phone: the core [AnimPlayer] drives the timeline, frames are drawn by the same [SceneDraw] as the
 * static figures. Play / pause, replay, previous / next step and a scrubber; paused when the screen leaves or the figure scrolls
 * out of view; with « Réduire les animations » only the stop frames are shown (no tweening) with the step list as text.
 */
@Composable
internal fun PhoneAnimation(b: Block.Illustration, anim: AnimatedFigure) {
    val ctx = LocalContext.current
    var reduce by remember { PhoneAnimPrefs.load(ctx); mutableStateOf(AnimSettings.reduce) }
    val player = remember(anim, reduce) { AnimPlayer(anim, reduce) }
    var scene by remember(player) { mutableStateOf(player.scene()) }
    var kick by remember(player) { mutableIntStateOf(0) }
    var step by remember(player) { mutableIntStateOf(player.stepIndex) }
    var playing by remember(player) { mutableStateOf(player.playing) }
    var atEnd by remember(player) { mutableStateOf(player.finished) }
    var pos by remember(player) { mutableFloatStateOf(0f) }
    var inView by remember { mutableStateOf(true) }
    val viewH = LocalView.current.height

    fun sync() {
        scene = player.scene(); step = player.stepIndex; playing = player.playing; atEnd = player.finished
        pos = if (anim.duration > 0) (player.t / anim.duration).toFloat() else 0f
    }
    fun refresh() { sync(); kick++ }

    LaunchedEffect(player) { if (!anim.stepMode && !player.reduced) player.play(); refresh() }
    LaunchedEffect(inView) { if (inView) kick++ }
    LaunchedEffect(player, kick) {
        // one frame callback per vsync while playing; AnimPlayer.advance drops the ticks beyond 30 fps
        while (player.playing && inView) withFrameNanos { n -> if (player.advance(n)) sync() }
        sync()
    }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, player) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) { player.pause(); sync() } }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); player.pause() }
    }

    val caption = anim.stops.getOrNull(step)?.say
    val alt = b.alt + if (caption != null) ". Étape ${step + 1} sur ${anim.stops.size} : $caption" else ""
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio((anim.w / anim.h).toFloat().coerceIn(0.4f, 4f)).background(Color(0xFFFDFCF7), RoundedCornerShape(10.dp))
                .semantics { contentDescription = alt }
                .onGloballyPositioned { c -> val r = c.boundsInWindow(); inView = viewH == 0 || (r.bottom > 0f && r.top < viewH) },
        ) {
            val sc = scene
            val s = minOf(size.width / sc.w.toFloat(), size.height / sc.h.toFloat())
            drawIntoCanvas { c ->
                val nc = c.nativeCanvas; nc.save()
                nc.translate((size.width - sc.w.toFloat() * s) / 2, (size.height - sc.h.toFloat() * s) / 2)
                SceneDraw.draw(nc, sc, s); nc.restore()
            }
        }
        if (caption != null) Text(
            (if (anim.stops.size > 1) "Étape ${step + 1}/${anim.stops.size} : " else "") + caption,
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
        )
        if (anim.stops.isNotEmpty() || !reduce) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { player.replay(); refresh() }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.semantics { contentDescription = "Rejouer l'animation" }) { Text("↺") }
            if (anim.stops.isNotEmpty()) OutlinedButton(onClick = { player.prev(); refresh() }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.semantics { contentDescription = "Étape précédente" }) { Text("◀") }
            Button(onClick = { player.toggle(); refresh() }, Modifier.weight(1f)) {
                Text(when { anim.stepMode || reduce -> if (atEnd) "Rejouer" else if (playing) "Passer" else "Étape suivante"; playing -> "⏸ Pause"; else -> "▶ Lecture" })
            }
            if (anim.stops.isNotEmpty() && !anim.stepMode) OutlinedButton(onClick = { player.next(); refresh() }, contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.semantics { contentDescription = "Étape suivante" }) { Text("▶") }
        }
        if (!reduce) Slider(
            value = pos, onValueChange = { player.pause(); player.seek(it * anim.duration); sync() },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Position dans l'animation" },
        )
        if (reduce && anim.stops.isNotEmpty()) Column {
            anim.stops.forEachIndexed { i, s -> Text("${i + 1}. ${s.say}", style = MaterialTheme.typography.bodySmall, fontWeight = if (i == step) FontWeight.Bold else FontWeight.Normal) }
        }
        TextButton(onClick = { PhoneAnimPrefs.setApp(ctx, !AnimSettings.appReduce); reduce = AnimSettings.reduce }) {
            Text(when { AnimSettings.systemReduce -> "Animations réduites (réglage Android)"; AnimSettings.appReduce -> "Animations réduites : activer les animations"; else -> "Réduire les animations" }, style = MaterialTheme.typography.labelSmall)
        }
    }
}
