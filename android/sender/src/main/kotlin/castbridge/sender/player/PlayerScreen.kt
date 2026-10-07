@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package castbridge.sender.player

import castbridge.sender.cbv

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import castbridge.core.phone.Gestures
import castbridge.core.phone.MediaKind
import castbridge.core.phone.PhoneFormats
import castbridge.core.phone.Speeds
import castbridge.core.phone.SubtitleTypes
import castbridge.sender.fmtTime
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

/** Snapshot of the ExoPlayer that the Compose screen observes. */
@UnstableApi
class PlayerObserver(private val p: ExoPlayer) : Player.Listener {
    var playing by mutableStateOf(p.isPlaying)
    var state by mutableIntStateOf(p.playbackState)
    var index by mutableIntStateOf(p.currentMediaItemIndex)
    var count by mutableIntStateOf(p.mediaItemCount)
    var item by mutableStateOf<MediaItem?>(p.currentMediaItem)
    var videoSize by mutableStateOf(p.videoSize)
    var tracks by mutableStateOf(p.currentTracks)
    var error by mutableStateOf<PlaybackException?>(p.playerError)
    var speed by mutableFloatStateOf(p.playbackParameters.speed)
    var art by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)

    init { p.addListener(this); readArt() }
    fun detach() = p.removeListener(this)

    override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
    override fun onPlaybackStateChanged(s: Int) { state = s }
    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { item = mediaItem; index = p.currentMediaItemIndex; error = null; readArt() }
    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) { count = p.mediaItemCount; index = p.currentMediaItemIndex; item = p.currentMediaItem }
    override fun onVideoSizeChanged(v: VideoSize) { videoSize = v }
    override fun onTracksChanged(t: Tracks) { tracks = t }
    override fun onPlayerError(e: PlaybackException) { error = e }
    override fun onPlaybackParametersChanged(pp: androidx.media3.common.PlaybackParameters) { speed = pp.speed }
    override fun onMediaMetadataChanged(m: androidx.media3.common.MediaMetadata) { readArt() }

    private fun readArt() {
        art = p.mediaMetadata.artworkData?.let { runCatching { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }.getOrNull() }
    }
}

enum class Aspect(val label: String, val mode: Int) {
    FIT("Ajuster", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    FILL("Remplir (étirée)", AspectRatioFrameLayout.RESIZE_MODE_FILL),
    ZOOM("Zoom (rognée)", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
}

@UnstableApi
@Composable
fun VideoPlayerScreen(act: PlayerActivity, p: ExoPlayer) {
    val ctx = LocalContext.current
    val obs = remember(p) { PlayerObserver(p) }
    DisposableEffect(obs) { onDispose { obs.detach() } }
    val remote by CastSession.state.collectAsState()
    val queue by act.queue
    val current = queue.getOrNull(obs.index)
    // From the file type only: deciding on the tracks would tear the video surface down while a new item loads (black video).
    val isAudio = current?.kind == MediaKind.AUDIO
    val tvHasThis = remote?.let { it.tvHasIt && it.item.uri == current?.uri } == true
    val copyingThis = remote?.let { it.phase == Remote.Phase.COPYING && it.item.uri == current?.uri } == true

    var controls by remember { mutableStateOf(true) }
    var touchedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var locked by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf(Aspect.FIT) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var seekPreview by remember { mutableStateOf<Long?>(null) }
    var castOpen by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }         // "more", "speed", "aspect", "rotation", "tracks"
    var formatDismissed by remember(obs.index) { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(p.currentPosition) }
    var dur by remember { mutableLongStateOf(0L) }
    LaunchedEffect(p) { while (true) { pos = p.currentPosition; dur = p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0; delay(250) } }
    fun touch() { touchedAt = System.currentTimeMillis(); controls = true }
    LaunchedEffect(controls, touchedAt, obs.playing, menu, seekPreview, castOpen) {
        if (controls && obs.playing && menu == null && seekPreview == null && !castOpen) { delay(3500); controls = false }
    }

    // Window: full screen for videos, orientation from the video, screen kept on while playing.
    val landscape = if (obs.videoSize.width > 0) obs.videoSize.width * obs.videoSize.pixelWidthHeightRatio >= obs.videoSize.height else null
    // "Caster…" from the library: open the sheet once the screen has taken its final orientation.
    LaunchedEffect(act.openCast.value, landscape) {
        if (act.openCast.value && (landscape != null || isAudio)) { delay(700); castOpen = true; act.openCast.value = false }
    }
    LaunchedEffect(landscape, act.rotation.value, isAudio, tvHasThis) { act.applyRotation(if (isAudio || tvHasThis) null else landscape) }
    LaunchedEffect(controls, isAudio, tvHasThis) { act.showBars(isAudio || tvHasThis || controls) }
    LaunchedEffect(obs.playing, isAudio) { act.keepScreenOn(obs.playing && !isAudio); act.updatePip() }

    val subsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val cur = queue.getOrNull(p.currentMediaItemIndex) ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val n = nameOf(ctx, uri) ?: ""
        if (SubtitleTypes.mimeOf(n) == null) {
            android.widget.Toast.makeText(ctx, "Format de sous-titres non pris en charge (SRT, ASS/SSA, VTT, TTML).", android.widget.Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        val idx = p.currentMediaItemIndex; val at = p.currentPosition
        val upd = cur.copy(subtitles = listOf(uri) + cur.subtitles)
        act.queue.value = queue.toMutableList().also { it[idx] = upd }
        p.replaceMediaItem(idx, upd.toMediaItem(ctx))
        p.seekTo(idx, at)
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()
    }

    BackHandler(locked) { feedback = "Écran verrouillé : touchez le cadenas" }

    if (tvHasThis && remote != null) {
        RemoteControls(remote!!) { act.finish() }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (isAudio) AudioArt(current?.name?.substringBeforeLast('.') ?: "", obs.item?.mediaMetadata?.artist?.toString(), obs.art)
        // TextureView (res/layout/player_view.xml): keeps its image through rotation / system-bar changes, where a
        // SurfaceView is re-created and some decoders show nothing until the next key frame.
        else AndroidView(factory = { c ->
            android.view.LayoutInflater.from(c).inflate(castbridge.sender.R.layout.player_view, null) as PlayerView
        }, update = { v -> v.player = p; v.resizeMode = aspect.mode }, onRelease = { it.player = null }, modifier = Modifier.fillMaxSize())

        if (act.inPip.value) return@Box

        // Copy to the TV in progress: percentage and times, whatever the state of the (auto-hiding) controls.
        if (copyingThis && remote != null) {
            // below the player's own top bar (back arrow, title, cast): the banner used to be drawn over them
            Surface(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 68.dp, bottom = 12.dp).fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xCC0A0F1E)) {
                CopyBanner(remote!!, Modifier.padding(14.dp))
            }
        }

        // Gestures: tap = controls, double tap = ±10 s (middle: pause), horizontal = seek, vertical left = brightness, right = volume.
        Box(Modifier.fillMaxSize()
            .pointerInput(locked) {
                detectTapGestures(onTap = { if (controls) controls = false else touch() }, onDoubleTap = { o ->
                    if (locked) return@detectTapGestures
                    val d = Gestures.doubleTapSkip(o.x, size.width.toFloat())
                    if (d == 0L) { if (p.isPlaying) p.pause() else p.play() } else { p.seekTo((p.currentPosition + d).coerceAtLeast(0)); feedback = if (d > 0) "+10 s" else "−10 s" }
                })
            }
            .pointerInput(locked, isAudio) {
                if (locked) return@pointerInput
                var axis = 0; var acc = Offset.Zero; var base = 0L; var startLevel = 0f; var zone = Gestures.Zone.VOLUME
                detectDragGestures(
                    onDragStart = { o ->
                        axis = 0; acc = Offset.Zero; base = p.currentPosition
                        zone = Gestures.zone(o.x, size.width.toFloat())
                        startLevel = if (zone == Gestures.Zone.BRIGHTNESS) act.brightness() else act.volume()
                    },
                    onDragEnd = { seekPreview?.let { p.seekTo(it) }; seekPreview = null; feedback = null; axis = 0 },
                    onDragCancel = { seekPreview = null; feedback = null; axis = 0 },
                ) { change, d ->
                    change.consume(); acc += d
                    if (axis == 0 && acc.getDistance() > 28f) axis = if (abs(acc.x) > abs(acc.y)) 1 else 2
                    when (axis) {
                        1 -> if (dur > 0) {
                            val t = (base + Gestures.seekDelta(acc.x, size.width.toFloat(), dur)).coerceIn(0, dur)
                            seekPreview = t
                            val delta = (t - base) / 1000
                            feedback = "${if (delta >= 0) "+" else "−"}${fmtTime(abs(delta) * 1000)}  (${fmtTime(t)})"
                        }
                        2 -> {
                            val lv = Gestures.level(startLevel, acc.y, size.height.toFloat())
                            if (zone == Gestures.Zone.BRIGHTNESS && !isAudio) { act.setBrightness(lv); feedback = "Luminosité ${(lv * 100).toInt()} %" }
                            else { act.setVolume(lv); feedback = "Volume ${(lv * 100).toInt()} %" }
                        }
                    }
                }
            })

        feedback?.let { f ->
            LaunchedEffect(f) { if (seekPreview == null) { delay(900); if (feedback == f) feedback = null } }
            Text(f, Modifier.align(Alignment.Center).background(Color(0xAA000000), RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
                color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

        // Cannot play here: say why, offer the TV (libVLC reads nearly everything).
        val unsupported = remember(obs.tracks) { unsupportedCodecs(obs.tracks) }
        val fatal = obs.error != null
        val formatMsg = if (fatal && obs.error!!.errorCode in 2000..2999) "Lecture impossible : ${obs.error!!.errorCodeName.lowercase(Locale.ROOT).replace('_', ' ')} (lien ou réseau)."
            else PhoneFormats.explain(unsupported.first, unsupported.second, fatal, current?.name)
        if (formatMsg.isNotEmpty() && !formatDismissed) {
            Surface(Modifier.align(Alignment.TopCenter).padding(top = 72.dp, start = 16.dp, end = 16.dp).widthIn(max = 560.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f), contentColor = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.secondary); Spacer(Modifier.width(8.dp))
                        Text(formatMsg, style = MaterialTheme.typography.bodyMedium)
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        if (fatal) TextButton({ p.prepare(); p.play() }) { Text("Réessayer") }
                        TextButton({ formatDismissed = true }) { Text("Fermer") }
                        Button({ castOpen = true }) { Icon(cbv(castbridge.sender.R.drawable.ic_cb_caster), null); Spacer(Modifier.width(6.dp)); Text(castbridge.core.ux.SendWays.SHEET_TITLE) }
                    }
                }
            }
        }

        if (locked) {
            AnimatedVisibility(controls, Modifier.align(Alignment.CenterStart).padding(24.dp), enter = fadeIn(), exit = fadeOut()) {
                FilledIconButton({ locked = false; touch() }, Modifier.size(56.dp)) { Icon(Icons.Filled.Lock, "Déverrouiller") }
            }
            return@Box
        }

        AnimatedVisibility(controls || isAudio, Modifier.fillMaxSize(), enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // top bar
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ act.finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(current?.name ?: "", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        if (obs.count > 1) Text("${obs.index + 1} / ${obs.count}", color = Color(castbridge.core.brand.BrandTokens.Dark.TEXT_MEDIUM), style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton({ castOpen = true; touch() }) { Icon(cbv(castbridge.sender.R.drawable.ic_cb_caster), castbridge.core.ux.SendWays.SHEET_TITLE, tint = Color.White) }
                    IconButton({ menu = "tracks"; touch() }) { Icon(cbv(castbridge.sender.R.drawable.ic_cb_sous_titres), "Pistes audio et sous-titres", tint = Color.White) }
                    Box {
                        IconButton({ menu = "more"; touch() }) { Icon(Icons.Filled.MoreVert, "Plus", tint = Color.White) }
                        DropdownMenu(menu == "more", { menu = null }) {
                            DropdownMenuItem({ Text("Vitesse : ${Speeds.label(obs.speed)}") }, { menu = "speed" }, leadingIcon = { Icon(Icons.Filled.Speed, null) })
                            if (!isAudio) DropdownMenuItem({ Text("Format d'image : ${aspect.label}") }, { menu = "aspect" }, leadingIcon = { Icon(Icons.Filled.AspectRatio, null) })
                            if (!isAudio) DropdownMenuItem({ Text("Rotation : ${act.rotation.value.label}") }, { menu = "rotation" }, leadingIcon = { Icon(Icons.Filled.ScreenRotation, null) })
                            DropdownMenuItem({ Text("Pistes audio et sous-titres") }, { menu = "tracks" }, leadingIcon = { Icon(cbv(castbridge.sender.R.drawable.ic_cb_sous_titres), null) })
                            if (!isAudio) DropdownMenuItem({ Text("Charger des sous-titres…") }, { menu = null; subsPicker.launch(arrayOf("*/*")) }, leadingIcon = { Icon(Icons.Filled.FileOpen, null) })
                            var bg by remember { mutableStateOf(act.background) }
                            if (!isAudio) DropdownMenuItem({ Text("Lecture en arrière-plan") }, { bg = !bg; act.background = bg },
                                leadingIcon = { Icon(cbv(castbridge.sender.R.drawable.ic_cb_pistes_audio), null) }, trailingIcon = { Checkbox(bg, { bg = it; act.background = it }) })
                            if (!isAudio) DropdownMenuItem({ Text("Image dans l'image") }, { menu = null; act.enterPip() }, leadingIcon = { Icon(Icons.Filled.PictureInPicture, null) })
                        }
                        DropdownMenu(menu == "speed", { menu = null }) {
                            Speeds.ALL.forEach { s -> DropdownMenuItem({ Text(Speeds.label(s)) }, { p.setPlaybackSpeed(s); menu = null },
                                trailingIcon = { if (s == obs.speed) Icon(Icons.Filled.Check, null) }) }
                        }
                        DropdownMenu(menu == "aspect", { menu = null }) {
                            Aspect.entries.forEach { a -> DropdownMenuItem({ Text(a.label) }, { aspect = a; menu = null }, trailingIcon = { if (a == aspect) Icon(Icons.Filled.Check, null) }) }
                        }
                        DropdownMenu(menu == "rotation", { menu = null }) {
                            PlayerActivity.Rotation.entries.forEach { r -> DropdownMenuItem({ Text(r.label) }, { act.rotation.value = r; menu = null },
                                trailingIcon = { if (r == act.rotation.value) Icon(Icons.Filled.Check, null) }) }
                        }
                    }
                }

                // centre: previous, -10, play/pause, +10, next
                Row(Modifier.align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundIcon(Icons.Filled.SkipPrevious, "Précédent", enabled = obs.count > 1 && obs.index > 0) { p.seekToPreviousMediaItem(); touch() }
                    RoundIcon(cbv(castbridge.sender.R.drawable.ic_cb_recul_10s), "−10 s") { p.seekTo((p.currentPosition - 10_000).coerceAtLeast(0)); touch() }
                    FilledIconButton({ if (p.isPlaying) p.pause() else { if (p.playbackState == Player.STATE_ENDED) p.seekTo(0); p.play() }; touch() }, Modifier.size(72.dp)) {
                        if (obs.state == Player.STATE_BUFFERING && obs.playing) CircularProgressIndicator(Modifier.size(36.dp), strokeWidth = 3.dp)
                        else Icon(if (obs.playing) cbv(castbridge.sender.R.drawable.ic_cb_pause) else cbv(castbridge.sender.R.drawable.ic_cb_lecture), "Lecture / pause", Modifier.size(42.dp))
                    }
                    RoundIcon(cbv(castbridge.sender.R.drawable.ic_cb_avance_10s), "+10 s") { p.seekTo(p.currentPosition + 10_000); touch() }
                    RoundIcon(Icons.Filled.SkipNext, "Suivant", enabled = obs.index < obs.count - 1) { p.seekToNextMediaItem(); touch() }
                }

                // bottom: bar with time preview, lock / format / rotation / PiP
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    if (copyingThis) Text(remote?.message ?: "", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    if (act.resumedAt.longValue > 0 && pos < act.resumedAt.longValue + 15_000) {
                        AssistChip({ p.seekTo(0); act.resumedAt.longValue = 0 }, { Text("Reprise à ${fmtTime(act.resumedAt.longValue)} · Reprendre du début") },
                            leadingIcon = { Icon(Icons.Filled.Restore, null) }, colors = AssistChipDefaults.assistChipColors(labelColor = Color.White, leadingIconContentColor = Color.White))
                    }
                    var drag by remember { mutableStateOf<Float?>(null) }
                    val shown = drag?.toLong() ?: seekPreview ?: pos
                    Box(Modifier.fillMaxWidth().height(22.dp)) {
                        if (drag != null && dur > 0) {
                            val frac = (drag!! / dur).coerceIn(0f, 1f)
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                                Text(fmtTime(drag!!.toLong()), Modifier.offset(x = (maxWidth - 56.dp) * frac)
                                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                                    color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Slider(if (dur > 0) shown.toFloat().coerceIn(0f, dur.toFloat()) else 0f, { drag = it; touch() }, enabled = dur > 0,
                        valueRange = 0f..(if (dur > 0) dur.toFloat() else 1f), onValueChangeFinished = { drag?.let { p.seekTo(it.toLong()) }; drag = null })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${fmtTime(shown)} / ${if (dur > 0) fmtTime(dur) else "--:--"}", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        if (!isAudio) {
                            IconButton({ locked = true; touch() }) { Icon(Icons.Filled.LockOpen, "Verrouiller l'écran", tint = Color.White) }
                            IconButton({ aspect = Aspect.entries[(aspect.ordinal + 1) % Aspect.entries.size]; feedback = aspect.label; touch() }) {
                                Icon(Icons.Filled.AspectRatio, "Format d'image", tint = Color.White) }
                            IconButton({
                                val r = PlayerActivity.Rotation.entries; act.rotation.value = r[(act.rotation.value.ordinal + 1) % r.size]
                                feedback = act.rotation.value.label; touch()
                            }) { Icon(Icons.Filled.ScreenRotation, "Rotation", tint = Color.White) }
                            IconButton({ act.enterPip() }) { Icon(Icons.Filled.PictureInPicture, "Image dans l'image", tint = Color.White) }
                        }
                    }
                    val other = remote
                    if (other != null && other.item.uri != current?.uri) CastMiniBar()
                }
            }
        }
    }

    if (menu == "tracks") TracksDialog(p, obs.tracks, isAudio, onLoadSubs = { menu = null; subsPicker.launch(arrayOf("*/*")) }) { menu = null }
    if (castOpen && current != null) CastSheet(current, p.currentPosition, dur) { castOpen = false }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(52.dp).background(Color(0x55000000), RoundedCornerShape(26.dp)), enabled = enabled) {
        Icon(icon, desc, Modifier.size(30.dp), tint = if (enabled) Color.White else Color(0x66FFFFFF))
    }
}

/** Codec MIME types of the video/audio the phone cannot decode at all (every track of that type unsupported). */
@UnstableApi
private fun unsupportedCodecs(t: Tracks): Pair<List<String>, List<String>> {
    fun of(type: Int): List<String> {
        val groups = t.groups.filter { it.type == type }
        if (groups.isEmpty() || groups.any { it.isSupported }) return emptyList()
        return groups.flatMap { g -> (0 until g.length).mapNotNull { g.getTrackFormat(it).sampleMimeType } }.distinct()
    }
    return of(C.TRACK_TYPE_VIDEO) to of(C.TRACK_TYPE_AUDIO)
}

@UnstableApi
@Composable
private fun TracksDialog(p: ExoPlayer, tracks: Tracks, audioOnly: Boolean, onLoadSubs: () -> Unit, onDismiss: () -> Unit) {
    val textOff = p.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
    AlertDialog(onDismissRequest = onDismiss, confirmButton = { TextButton(onDismiss) { Text("OK") } },
        title = { Text(if (audioOnly) "Pistes audio" else "Audio et sous-titres") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                item { Text("AUDIO", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                items(audio.flatMap { g -> (0 until g.length).map { g to it } }) { (g, i) ->
                    TrackRow(label(g.getTrackFormat(i), "Piste"), g.isTrackSelected(i), g.isTrackSupported(i)) {
                        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                    }
                }
                if (audio.isEmpty()) item { Text("Aucune", style = MaterialTheme.typography.bodySmall) }
                if (!audioOnly) {
                    item { Spacer(Modifier.height(12.dp)); Text("SOUS-TITRES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                    item {
                        TrackRow("Désactivés", textOff || tracks.groups.none { it.type == C.TRACK_TYPE_TEXT && it.isSelected }, true) {
                            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        }
                    }
                    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                    items(text.flatMap { g -> (0 until g.length).map { g to it } }) { (g, i) ->
                        TrackRow(label(g.getTrackFormat(i), "Sous-titres"), !textOff && g.isTrackSelected(i), g.isTrackSupported(i)) {
                            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                        }
                    }
                    item {
                        TextButton(onLoadSubs) { Icon(Icons.Filled.FileOpen, null); Spacer(Modifier.width(8.dp)); Text("Charger un fichier (.srt, .ass, .vtt)…") }
                    }
                }
            }
        })
}

@Composable
private fun TrackRow(text: String, selected: Boolean, supported: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = supported, onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick, enabled = supported)
        Text(text + if (!supported) " (non lisible ici)" else "", style = MaterialTheme.typography.bodyMedium)
    }
}

@UnstableApi
private fun label(f: androidx.media3.common.Format, fallback: String): String {
    val lang = f.language?.takeIf { it != "und" && it.isNotBlank() }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.FRENCH).replaceFirstChar { c -> c.titlecase(Locale.FRENCH) } }
    val parts = listOfNotNull(f.label, lang, f.sampleMimeType?.let { PhoneFormats.codecName(it) }.takeIf { f.sampleMimeType?.startsWith("audio") == true },
        f.channelCount.takeIf { it > 0 }?.let { when (it) { 1 -> "mono"; 2 -> "stéréo"; 6 -> "5.1"; 8 -> "7.1"; else -> "$it canaux" } })
    return parts.distinct().joinToString(" · ").ifEmpty { fallback }
}
