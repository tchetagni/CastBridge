package castbridge.sender.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import castbridge.core.phone.ResumeBook
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the phone's ExoPlayer and its MediaSession, so that playback goes on with the screen off or the app in the
 * background (music, or a video with "lecture en arrière-plan"), with the standard media notification, lock-screen and
 * Bluetooth controls. It is a MediaLibraryService so Android Auto can list and drive the current queue (basic support).
 *
 * The player lives in this process: the screen uses [player] directly (same thread, the main one).
 */
@UnstableApi
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private lateinit var resume: ResumeTracker

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setUserAgent("CastBridge")
            .setConnectTimeoutMs(10_000).setReadTimeoutMs(20_000)
        // The TV's /stream/ route wants its PIN: added only for the TV hosts registered in TvStreamAuth.
        val upstream = ResolvingDataSource.Factory(DefaultDataSource.Factory(this, http)) { spec: DataSpec ->
            val pin = spec.uri.host?.let { TvStreamAuth.pins[it] }
            if (pin != null) spec.withAdditionalHeaders(mapOf("X-CB-Pin" to pin)) else spec
        }
        val p = ExoPlayer.Builder(this)
            .setRenderersFactory(DefaultRenderersFactory(this).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(upstream))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)          // headphones unplugged / Bluetooth lost: pause
            .setSeekBackIncrementMs(10_000).setSeekForwardIncrementMs(10_000)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        resume = ResumeTracker(this, p)
        val open = PendingIntent.getActivity(this, 0, Intent(this, PlayerActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val s = MediaLibrarySession.Builder(this, p, LibraryCallback()).setSessionActivity(open).build()
        session = s
        addSession(s)                                   // notification + foreground handled by Media3 from now on
        _player.value = p
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Swiped away from the recents while nothing plays: stop the service (music that plays keeps going). */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) { p?.let { resume.save() }; stopSelf() }
    }

    override fun onDestroy() {
        _player.value = null
        session?.let { s -> resume.save(); resume.detach(); s.player.release(); s.release() }
        session = null
        super.onDestroy()
    }

    /** Android Auto / Bluetooth browsers: one folder, the current queue. */
    private inner class LibraryCallback : MediaLibrarySession.Callback {
        private fun root() = MediaItem.Builder().setMediaId(ROOT).setMediaMetadata(
            MediaMetadata.Builder().setTitle("CastBridge").setIsBrowsable(true).setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED).build()).build()

        private fun queue(): List<MediaItem> {
            val p = session?.player ?: return emptyList()
            return (0 until p.mediaItemCount).map { p.getMediaItemAt(it) }
        }

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?)
            : ListenableFuture<LibraryResult<MediaItem>> = Futures.immediateFuture(LibraryResult.ofItem(root(), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String, page: Int,
                                   pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = if (parentId == ROOT) queue().drop(page * pageSize).take(pageSize) else emptyList()
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(items), params))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, mediaId: String)
            : ListenableFuture<LibraryResult<MediaItem>> {
            val item = if (mediaId == ROOT) root() else queue().firstOrNull { it.mediaId == mediaId }
            return Futures.immediateFuture(if (item != null) LibraryResult.ofItem(item, null) else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
        }

        /** A controller picked an item by id (Android Auto): give back the full item of the queue (with its URI). */
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo, mediaItems: MutableList<MediaItem>)
            : ListenableFuture<MutableList<MediaItem>> {
            val q = queue()
            return Futures.immediateFuture(mediaItems.mapNotNull { m ->
                q.firstOrNull { it.mediaId == m.mediaId } ?: m.takeIf { it.localConfiguration != null }
            }.toMutableList())
        }
    }

    companion object {
        private const val ROOT = "root"
        private val _player = MutableStateFlow<ExoPlayer?>(null)
        /** The phone's player, once the service is up (main thread only). */
        val player: StateFlow<ExoPlayer?> = _player

        fun start(ctx: Context) { runCatching { ctx.startService(Intent(ctx, PlaybackService::class.java)) } }
    }
}

/** PINs of the CastBridge TVs whose /stream/ the phone player reads (back to the phone after a move). Host -> PIN. */
object TvStreamAuth { val pins = ConcurrentHashMap<String, String>() }

/**
 * Remembers where each file stopped (see [ResumeBook]) and resumes there when it is played again, including the next items
 * of a folder playlist and in the background.
 */
@UnstableApi
class ResumeTracker(ctx: Context, private val p: ExoPlayer) : Player.Listener {
    private val sp = ctx.getSharedPreferences("castbridge_resume", Context.MODE_PRIVATE)
    private var lastSave = 0L
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() { if (p.isPlaying) record(); handler.postDelayed(this, 5_000) }
    }

    init { p.addListener(this); handler.postDelayed(tick, 5_000) }

    fun detach() { p.removeListener(this); handler.removeCallbacks(tick) }

    private fun keyOf(item: MediaItem?): String? {
        val e = item?.mediaMetadata?.extras ?: return null
        if (e.getBoolean(EXTRA_NO_RESUME)) return null
        return ResumeBook.key(e.getString(EXTRA_NAME), e.getLong(EXTRA_SIZE, -1), item.mediaId)
    }

    private fun record() {
        val k = keyOf(p.currentMediaItem) ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0
        book(sp).update(k, p.currentPosition, dur)
        if (System.currentTimeMillis() - lastSave > 10_000) save()
    }

    fun save() { lastSave = System.currentTimeMillis(); sp.edit().putString("book", book(sp).serialize()).apply() }

    override fun onIsPlayingChanged(isPlaying: Boolean) { if (!isPlaying) { record(); save() } }

    override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
        // Leaving an item (next/previous, end): remember where the previous one stopped.
        if (old.mediaItemIndex != new.mediaItemIndex) old.mediaItem?.let { m ->
            keyOf(m)?.let { k -> book(sp).update(k, old.positionMs, if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) old.positionMs else 0) }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        // A new item of the queue starts: go to where it was left last time (the first one is positioned by the screen).
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return
        val k = keyOf(mediaItem) ?: return
        val at = book(sp).startAt(k, 0)
        if (at > 0 && p.currentPosition < 1_000) p.seekTo(at)
    }

    companion object {
        const val EXTRA_NAME = "cb_name"
        const val EXTRA_SIZE = "cb_size"
        const val EXTRA_KIND = "cb_kind"
        const val EXTRA_NO_RESUME = "cb_noresume"
        @Volatile private var cached: ResumeBook? = null
        fun book(sp: android.content.SharedPreferences): ResumeBook = cached ?: synchronized(this) {
            cached ?: ResumeBook.parse(sp.getString("book", null)).also { cached = it }
        }
        fun book(ctx: Context) = book(ctx.getSharedPreferences("castbridge_resume", Context.MODE_PRIVATE))
        fun extras(name: String?, size: Long, kind: String, noResume: Boolean = false) = Bundle().apply {
            putString(EXTRA_NAME, name); putLong(EXTRA_SIZE, size); putString(EXTRA_KIND, kind); putBoolean(EXTRA_NO_RESUME, noResume)
        }
    }
}
