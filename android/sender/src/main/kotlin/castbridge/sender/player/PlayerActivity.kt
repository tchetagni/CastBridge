package castbridge.sender.player

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import castbridge.core.phone.MediaKind
import castbridge.core.phone.ResumeBook
import castbridge.sender.CastTheme
import castbridge.sender.MoveHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The phone's own media player, reached straight from "Ouvrir avec" / "Partager" (Gallery, Files, WhatsApp, Telegram, the
 * browser) or from the "Sur le téléphone" tab. Video/music go through [PlaybackService] (Media3/ExoPlayer), photos through
 * [ImageViewer]. The cast button hands playback to a TV at the current position; the screen then becomes its remote.
 */
@UnstableApi
class PlayerActivity : ComponentActivity() {
    sealed class Mode {
        object Loading : Mode()
        data class Empty(val message: String) : Mode()
        object Video : Mode()
        data class Images(val items: List<PlayItem>, val start: Int) : Mode()
        /** Only the remote control of the cast in progress (mini controller tapped). */
        object RemoteOnly : Mode()
    }

    enum class Rotation(val label: String) { AUTO("Auto (selon la vidéo)"), SENSOR("Suivre le téléphone"), LANDSCAPE("Paysage"), PORTRAIT("Portrait") }

    val mode = mutableStateOf<Mode>(Mode.Loading)
    /** The queue shown by the video/music player (kept in step with the ExoPlayer playlist). */
    val queue = mutableStateOf<List<PlayItem>>(emptyList())
    val inPip = mutableStateOf(false)
    val rotation = mutableStateOf(Rotation.AUTO)
    /** Where playback resumed (for the "Reprendre du début" chip), 0 = from the start. */
    val resumedAt = mutableLongStateOf(0L)
    /** Open the "Diffuser sur" sheet as soon as the file is loaded (intent extra [Media.EXTRA_CAST]). */
    val openCast = mutableStateOf(false)
    var player: ExoPlayer? = null; private set
    private val prefs by lazy { getSharedPreferences("castbridge_player", MODE_PRIVATE) }
    var background: Boolean
        get() = prefs.getBoolean("background", false)
        set(v) { prefs.edit().putBoolean("background", v).apply() }
    private var pipReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        PlaybackService.start(this)
        setContent {
            CastTheme {
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    when (val m = mode.value) {
                        Mode.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                        is Mode.Empty -> Text(m.message, Modifier.align(Alignment.Center).padding(24.dp()), color = Color.White)
                        Mode.Video -> player?.let { VideoPlayerScreen(this@PlayerActivity, it) }
                        is Mode.Images -> ImageViewer(this@PlayerActivity, m.items, m.start)
                        Mode.RemoteOnly -> {
                            val r by CastSession.state.collectAsState()
                            val cur = r
                            if (cur == null) LaunchedEffect(Unit) { finish() } else RemoteControls(cur) { finish() }
                        }
                    }
                    MoveHandler()
                }
            }
        }
        handle(intent)
        val filter = IntentFilter(ACTION_PIP_TOGGLE)
        pipReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { player?.let { if (it.isPlaying) it.pause() else it.play() }; updatePip() }
        }.also { ContextCompat.registerReceiver(this, it, filter, ContextCompat.RECEIVER_NOT_EXPORTED) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.action == ACTION_REMOTE) {
            if (CastSession.state.value == null) finish() else { mode.value = Mode.RemoteOnly; showBars(true) }
            return
        }
        // Called from the launcher-less entry without a file (e.g. the media notification): show what plays.
        if (intent.data == null && intent.action != Intent.ACTION_SEND && intent.action != Intent.ACTION_SEND_MULTIPLE) {
            lifecycleScope.launch {
                val p = PlaybackService.player.filterNotNull().first()
                if (p.mediaItemCount > 0) { player = p; if (queue.value.isEmpty()) queue.value = restoreQueue(p); mode.value = Mode.Video }
                else if (mode.value == Mode.Loading) mode.value = Mode.Empty("Rien à lire.")
            }
            return
        }
        mode.value = Mode.Loading
        // « Ouvrir avec » / « Partager » from another app: the player used as a shortcut
        if ((intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_SEND || intent.action == Intent.ACTION_SEND_MULTIPLE) &&
            referrer?.host != packageName) castbridge.sender.PhoneConnect.feature("player", "shortcut")
        lifecycleScope.launch {
            val fromTv = intent.getBooleanExtra(EXTRA_FROM_TV, false)
            val given = withContext(Dispatchers.IO) { Media.fromIntent(this@PlayerActivity, intent) }.map { if (fromTv) it.copy(fromTv = true) else it }
            if (given.isEmpty()) { mode.value = Mode.Empty("Ce fichier n'est pas une vidéo, une musique ou une photo lisible."); return@launch }
            val kind = given[0].kind
            val same = given.filter { if (kind == MediaKind.IMAGE) it.kind == MediaKind.IMAGE else it.kind != MediaKind.IMAGE }
            val (list, idx) = if (same.size == 1) withContext(Dispatchers.IO) { Media.folder(this@PlayerActivity, same[0]) } else same to 0
            if (kind == MediaKind.IMAGE) {
                PlaybackService.player.value?.let { if (it.isPlaying && it.currentMediaItem?.mediaMetadata?.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC) it.pause() }
                mode.value = Mode.Images(list, idx); applyRotation(null); return@launch
            }
            val p = PlaybackService.player.filterNotNull().first()
            player = p
            val requested = intent.getLongExtra(Media.EXTRA_POS, -1)
            val cur = list[idx]
            val start = when {
                requested >= 0 -> requested
                cur.fromTv -> 0
                else -> ResumeTracker.book(this@PlayerActivity).get(ResumeBook.key(cur.name, cur.size, cur.uri.toString()))
            }
            resumedAt.longValue = if (requested < 0) start else 0
            queue.value = list
            // Opening the file again after its cast ended on the TV: that finished session is forgotten, play here.
            CastSession.state.value?.let { if (it.phase == Remote.Phase.ENDED || it.phase == Remote.Phase.FAILED) CastSession.dismiss(this@PlayerActivity) }
            val remote = CastSession.state.value?.let { it.item.uri == cur.uri && it.phase == Remote.Phase.PLAYING } == true
            p.setMediaItems(list.map { it.toMediaItem(this@PlayerActivity) }, idx, start)
            p.prepare()
            p.playWhenReady = !remote             // the TV already plays it: stay paused, show the remote
            openCast.value = intent.getBooleanExtra(Media.EXTRA_CAST, false)
            mode.value = Mode.Video
        }
    }

    /** Opened from the notification while the queue was built earlier: rebuild the screen's view of it. */
    private fun restoreQueue(p: ExoPlayer): List<PlayItem> = (0 until p.mediaItemCount).map { i ->
        val m = p.getMediaItemAt(i); val e = m.mediaMetadata.extras
        PlayItem(android.net.Uri.parse(m.mediaId), e?.getString(ResumeTracker.EXTRA_NAME) ?: m.mediaMetadata.title?.toString().orEmpty(), null,
            runCatching { MediaKind.valueOf(e?.getString(ResumeTracker.EXTRA_KIND) ?: "VIDEO") }.getOrDefault(MediaKind.VIDEO), e?.getLong(ResumeTracker.EXTRA_SIZE) ?: -1,
            subtitles = m.localConfiguration?.subtitleConfigurations?.map { it.uri }.orEmpty(), fromTv = e?.getBoolean(ResumeTracker.EXTRA_NO_RESUME) == true)
    }

    // ---------- window: rotation, immersive mode, brightness, volume ----------

    /** [videoLandscape]: null = not a video (photos, music). */
    fun applyRotation(videoLandscape: Boolean?) {
        requestedOrientation = when (rotation.value) {
            Rotation.AUTO -> when (videoLandscape) {
                true -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                false -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                null -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            }
            Rotation.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_FULL_USER
            Rotation.LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            Rotation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        }
    }

    fun showBars(show: Boolean) {
        val c = WindowCompat.getInsetsController(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (show) c.show(WindowInsetsCompat.Type.systemBars()) else c.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun keepScreenOn(on: Boolean) {
        if (on) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun brightness(): Float {
        val b = window.attributes.screenBrightness
        if (b >= 0) return b
        return runCatching { Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }.getOrDefault(0.5f)
    }

    fun setBrightness(level: Float) {
        window.attributes = window.attributes.apply { screenBrightness = level.coerceIn(0.01f, 1f) }
    }

    private val audio by lazy { getSystemService(AudioManager::class.java) }
    fun volume(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun setVolume(level: Float) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level * audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)).roundToInt(), 0)
    }

    // ---------- Picture-in-Picture and background ----------

    private fun currentIsVideo(): Boolean {
        val p = player ?: return false
        return mode.value == Mode.Video && p.currentMediaItem?.mediaMetadata?.mediaType != androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC &&
            p.videoSize.width > 0 && CastSession.state.value?.let { it.tvHasIt && it.item.uri.toString() == p.currentMediaItem?.mediaId } != true
    }

    private fun pipParams(): PictureInPictureParams {
        val v = player?.videoSize
        val ratio = if (v != null && v.width > 0 && v.height > 0) {
            val r = v.width.toFloat() * v.pixelWidthHeightRatio / v.height
            val clamped = r.coerceIn(1f / 2.39f, 2.39f)
            Rational((clamped * 1000).roundToInt(), 1000)
        } else Rational(16, 9)
        val playing = player?.isPlaying == true
        val pi = PendingIntent.getBroadcast(this, 0, Intent(ACTION_PIP_TOGGLE).setPackage(packageName), PendingIntent.FLAG_IMMUTABLE)
        val action = RemoteAction(Icon.createWithResource(this, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
            if (playing) "Pause" else "Lecture", if (playing) "Pause" else "Lecture", pi)
        val b = PictureInPictureParams.Builder().setAspectRatio(ratio).setActions(listOf(action))
        if (Build.VERSION.SDK_INT >= 31) b.setAutoEnterEnabled(playing && currentIsVideo()).setSeamlessResizeEnabled(true)
        return b.build()
    }

    fun updatePip() { if (packageManager.hasSystemFeature("android.software.picture_in_picture")) runCatching { setPictureInPictureParams(pipParams()) } }

    fun enterPip() {
        if (!packageManager.hasSystemFeature("android.software.picture_in_picture") || !currentIsVideo()) return
        runCatching { enterPictureInPictureMode(pipParams()) }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Home pressed while a video plays: continue in a small window (Android 12+ does it by itself through setAutoEnterEnabled).
        if (Build.VERSION.SDK_INT < 31 && player?.isPlaying == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip.value = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // A video stops when the screen is left (unless PiP or "lecture en arrière-plan"); music always goes on.
        val p = player ?: return
        if (!isInPictureInPictureMode && !isChangingConfigurations && !background && currentIsVideo()) p.pause()
    }

    override fun onDestroy() {
        pipReceiver?.let { runCatching { unregisterReceiver(it) } }
        // Leaving the player for good while nothing plays: release the queue so the notification goes away.
        val p = player
        if (isFinishing && p != null && !p.isPlaying && CastSession.state.value == null) { p.stop(); p.clearMediaItems() }
        super.onDestroy()
    }

    companion object {
        const val ACTION_REMOTE = "castbridge.player.REMOTE"
        const val EXTRA_FROM_TV = "castbridge.fromTv"
        private const val ACTION_PIP_TOGGLE = "castbridge.player.PIP_TOGGLE"
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
