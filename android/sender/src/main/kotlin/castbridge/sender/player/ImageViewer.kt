package castbridge.sender.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import castbridge.core.phone.CastAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Photos: swipe between the folder's pictures, pinch/double-tap zoom, slideshow, cast. */
@OptIn(ExperimentalFoundationApi::class)
@UnstableApi
@Composable
fun ImageViewer(act: PlayerActivity, items: List<PlayItem>, start: Int) {
    val pager = rememberPagerState(initialPage = start) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var castOpen by remember { mutableStateOf(false) }
    val remote by CastSession.state.collectAsState()
    val ctx = LocalContext.current
    val current = items.getOrNull(pager.currentPage)

    LaunchedEffect(chrome) { act.showBars(chrome) }
    LaunchedEffect(slideshow) {
        act.keepScreenOn(slideshow)
        while (slideshow) { delay(4000); pager.animateScrollToPage((pager.currentPage + 1) % items.size.coerceAtLeast(1)) }
    }
    // A photo shown on a TV follows the swipe: the next one is sent to the same TV.
    LaunchedEffect(pager.currentPage) {
        val r = CastSession.state.value ?: return@LaunchedEffect
        val cur = items.getOrNull(pager.currentPage) ?: return@LaunchedEffect
        if (r.item.kind == castbridge.core.phone.MediaKind.IMAGE && r.action == CastAction.LIVE && r.item.uri != cur.uri &&
            r.phase != Remote.Phase.FAILED) CastSession.start(ctx, r.target, CastAction.LIVE, cur)
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = !zoomed, key = { items[it].uri.toString() }) { page ->
            ZoomableImage(items[page], onTap = { chrome = !chrome }, onZoomed = { if (page == pager.currentPage) zoomed = it })
        }
        if (chrome) {
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))).statusBarsPadding().padding(4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton({ act.finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(current?.name ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    if (items.size > 1) Text("${pager.currentPage + 1} / ${items.size}", color = Color(0xFFBBBBBB), style = MaterialTheme.typography.labelSmall)
                }
                IconButton({ castOpen = true }) { Icon(Icons.Filled.Cast, "Caster", tint = Color.White) }
                if (items.size > 1) IconButton({ slideshow = !slideshow; if (slideshow) chrome = false }) {
                    Icon(if (slideshow) Icons.Filled.Pause else Icons.Filled.Slideshow, "Diaporama", tint = Color.White)
                }
            }
        }
        remote?.let { r ->
            if (r.item.kind == castbridge.core.phone.MediaKind.IMAGE) Surface(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), contentColor = MaterialTheme.colorScheme.onSurface, shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CastConnected, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp))
                    Text(r.message ?: "Affiché sur ${r.target.name}", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyMedium)
                    TextButton({ if (r.tvHasIt || r.phase == Remote.Phase.STARTING) CastSession.stop(ctx) else CastSession.dismiss(ctx) }) { Text(if (r.phase == Remote.Phase.FAILED) "Fermer" else "Arrêter") }
                }
            } else Box(Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { CastMiniBar() }
        }
    }
    if (castOpen && current != null) CastSheet(current, 0, 0) { castOpen = false }
}

@Composable
private fun ZoomableImage(item: PlayItem, onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, item.uri) { value = withContext(Dispatchers.IO) { decode(ctx, item.uri, 2560) } }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize()
        .pointerInput(item.uri) {
            detectTapGestures(onTap = { onTap() }, onDoubleTap = { o ->
                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else { scale = 2.5f; offset = (Offset(size.width / 2f, size.height / 2f) - o) * 1.5f }
                onZoomed(scale > 1f)
            })
        }
        .pointerInput(item.uri) {
            // Only while zoomed or with two fingers: a one-finger swipe at 1x belongs to the pager.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                do {
                    val e = awaitPointerEvent()
                    if (e.changes.count { it.pressed } >= 2 || scale > 1f) {
                        val zoom = e.calculateZoom(); val pan = e.calculatePan()
                        scale = (scale * zoom).coerceIn(1f, 6f)
                        val maxX = size.width * (scale - 1) / 2; val maxY = size.height * (scale - 1) / 2
                        offset = if (scale <= 1f) Offset.Zero else Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
                        e.changes.forEach { if (it.positionChanged()) it.consume() }
                        onZoomed(scale > 1f)
                    }
                } while (e.changes.any { it.pressed })
            }
        }, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) CircularProgressIndicator()
        else Image(b, item.name, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            contentScale = ContentScale.Fit)
    }
}

/** Decodes a picture no bigger than [max] px on its long side (HEIF/AVIF through ImageDecoder on Android 9+). */
fun decode(ctx: Context, uri: Uri, max: Int): ImageBitmap? = runCatching {
    val bmp: Bitmap? = if (Build.VERSION.SDK_INT >= 28) {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
            val s = info.size; val long = maxOf(s.width, s.height)
            if (long > max) d.setTargetSize(s.width * max / long, s.height * max / long)
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } else {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / sample > max) sample *= 2
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }
    bmp?.asImageBitmap()
}.getOrNull()
