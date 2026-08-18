package com.lunarr.vlcplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.TextureView
import android.view.View
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.LifecycleEventListener
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.Event
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IVLCVout
import java.io.FileOutputStream
import java.io.File
import java.io.IOException
import java.util.ArrayList

/**
 * Fabric native view hosting a libvlc [MediaPlayer].
 *
 * Events are dispatched to the New Architecture via [UIManagerHelper]'s
 * [com.facebook.react.uimanager.events.EventDispatcher], matching the event
 * names declared in the codegen spec (onLoad, onProgress, etc.).
 */
@SuppressLint("ViewConstructor")
class VLCPlayerView(context: ThemedReactContext) :
    TextureView(context), TextureView.SurfaceTextureListener, AudioManager.OnAudioFocusChangeListener,
    LifecycleEventListener,
    NowPlayingListener {

    private val themedReactContext: ThemedReactContext = context
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var libvlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null

    private var srcMap: ReadableMap? = null

    private var videoWidth = 0
    private var videoHeight = 0
    private var frameWidth = 0
    private var frameHeight = 0
    private var preVolume = 100
    private var autoAspectRatio = false
    private var repeat = false
    private var resizeMode = "contain"
    private var currentViewId = -1
    private var progressIntervalMs: Long = 0
    private var equalizer: MediaPlayer.Equalizer? = null
    private var audioFocusGranted = false
    private var resumeAfterFocusLoss = false
    private var duckedVolume = -1
    private var nowPlayingTitle: String? = null
    private var nowPlayingArtist: String? = null
    private var nowPlayingAlbum: String? = null
    private var nowPlayingAlbumArtist: String? = null
    private var nowPlayingGenre: String? = null
    private var nowPlayingTrackNumber: Int = 0
    private var nowPlayingDiscNumber: Int = 0
    private var nowPlayingArtwork: String? = null
    private var continueAudioInBackground = true
    private var showNowPlaying = true
    private var nowPlayingActive = false
    private var shouldResumePlaying = false
    private var nextTrackEnabled = false
    private var previousTrackEnabled = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (mediaPlayer?.isPlaying == true) {
                progressChanged()
            }
            if (progressIntervalMs > 0) {
                mainHandler.postDelayed(this, progressIntervalMs)
            }
        }
    }

    init {
        surfaceTextureListener = this
        setSurfaceTextureListener(this)
        themedReactContext.addLifecycleEventListener(this)
    }

    // TextureView cannot display a background (its setBackground throws an
    // UnsupportedOperationException). Swallow both entry points so neither this
    // class nor a style backgroundColor can crash at mount time.
    override fun setBackground(background: Drawable?) {
        return
    }

    override fun setBackgroundColor(color: Int) {
        return
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        addOnLayoutChangeListener(onLayoutChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeOnLayoutChangeListener(onLayoutChangeListener)
        releasePlayer()
    }

    override fun setId(id: Int) {
        super.setId(id)
        currentViewId = id
    }

    // ---- Event emission (Fabric) ----

    private fun emit(name: String, data: WritableMap) {
        if (currentViewId <= 0) return
        val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(themedReactContext, currentViewId)
            ?: return
        dispatcher.dispatchEvent(
            VLCPlayerEvent(UIManagerHelper.getSurfaceId(themedReactContext), currentViewId, name, data)
        )
    }

    private fun progressChanged() {
        val player = mediaPlayer ?: return
        Arguments.createMap().apply {
            // Android reports milliseconds, iOS reports seconds, so normalise.
            putDouble("currentTime", player.time / 1000.0)
            putDouble("duration", player.length / 1000.0)
            emit("onProgress", this)
        }
        pushNowPlaying()
    }

    // ---- Layout / aspect ratio ----

    private val onLayoutChangeListener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
        if (view.width > 0 && view.height > 0) {
            videoWidth = view.width
            videoHeight = view.height
            mediaPlayer?.let { player ->
                player.vlcVout.setWindowSize(videoWidth, videoHeight)
                if (autoAspectRatio) {
                    player.aspectRatio = "$videoWidth:$videoHeight"
                }
                // cover / center depend on the resolved surface size.
                applyResizeMode()
            }
        }
    }

    // ---- libvlc callbacks ----

    private val eventListener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.TimeChanged -> if (progressIntervalMs <= 0) progressChanged()
            MediaPlayer.Event.EndReached -> {
                if (repeat) {
                    mediaPlayer?.let { it.time = 0L; it.play() }
                } else {
                    stopProgress()
                    pushNowPlaying()
                    emitState("onEnd", "Ended")
                }
            }
            MediaPlayer.Event.Playing -> {
                requestAudioFocus()
                scheduleProgress()
                updateFrameSizeFromMedia()
                pushNowPlaying()
                emitState("onPlaying", "Playing")
            }
            MediaPlayer.Event.Opening -> {
                val player = mediaPlayer
                if (player == null) {
                    emit("onLoad", Arguments.createMap())
                } else {
                    Arguments.createMap().apply {
                        putDouble("currentTime", player.time / 1000.0)
                        putDouble("duration", player.length / 1000.0)
                        emit("onLoad", this)
                    }
                }
            }
            MediaPlayer.Event.Paused -> {
                stopProgress()
                pushNowPlaying()
                emitState("onPaused", "Paused")
            }
            MediaPlayer.Event.Buffering -> {
                // Use a safe call: a Buffering event can arrive after release (unmount,
                // source switch), and with no player we must not force-unwrap.
                val isBuffering = mediaPlayer?.isPlaying != true
                Arguments.createMap().apply {
                    putString("type", "Buffering")
                    putDouble("bufferRate", event.buffering.toDouble())
                    putBoolean("isBuffering", isBuffering)
                    emit("onBuffer", this)
                }
            }
            MediaPlayer.Event.Stopped -> {
                stopProgress()
                emitState("onStopped", "Stopped")
            }
            MediaPlayer.Event.EncounteredError -> {
                stopProgress()
                emitState("onError", "Error")
            }
        }
    }

    private val newVideoLayoutListener = IVLCVout.OnNewVideoLayoutListener { _, width, height, visibleWidth, visibleHeight, sarNum, sarDen ->
        if (width * height != 0) {
            frameWidth = width
            frameHeight = height
            // The decoded frame size is known now, so cover / center can apply.
            applyResizeMode()
        }
    }

    private fun emitState(name: String, type: String) {
        val player = mediaPlayer
        if (player == null) {
            Arguments.createMap().apply {
                putString("type", type)
                emit(name, this)
            }
            return
        }
        Arguments.createMap().apply {
            putString("type", type)
            putDouble("currentTime", player.time / 1000.0)
            putDouble("duration", player.length / 1000.0)
            putBoolean("isPlaying", player.isPlaying)
            putBoolean("isBuffering", !player.isPlaying)
            emit(name, this)
        }
    }

    // ---- Player lifecycle ----

    fun setSource(src: ReadableMap) {
        srcMap = src
        val autoplay = if (src.hasKey("autoplay")) src.getBoolean("autoplay") else true
        createPlayer(autoplay = autoplay, isResume = false)
    }

    private fun createPlayer(autoplay: Boolean, isResume: Boolean) {
        releasePlayer()
        val surfaceTexture = surfaceTexture ?: return
        val src = srcMap ?: return
        try {
            val uriString = if (src.hasKey("uri")) src.getString("uri") else null
            if (uriString == null || uriString.isEmpty()) return
            val isNetwork = if (src.hasKey("isNetwork")) src.getBoolean("isNetwork") else false
            val mediaOptions: ReadableArray? = if (src.hasKey("mediaOptions")) src.getArray("mediaOptions") else null
            val initOptions: ReadableArray? = if (src.hasKey("initOptions")) src.getArray("initOptions") else null
            // Hardware decoding mode, matching the official app's single
            // `hardware_acceleration` setting: -1 automatic, 0 disabled,
            // 1 decode-only, 2 full (decode + render-accelerated). As in the
            // official app, force is always applied when HW decode is enabled.
            val hwEnabled = optInt(src, "hwDecoderEnabled", -1)

            val opts = ArrayList<String>()
            initOptions?.let { optsArr ->
                for (i in 0 until optsArr.size()) {
                    optsArr.getString(i)?.let { opts.add(it) }
                }
            }

            // Apply init options to the LibVLC instance when provided (mirrors
            // iOS, which passes them to VLCMediaPlayer(options:)).
            libvlc = if (opts.isEmpty()) LibVLC(context) else LibVLC(context, opts)
            val player = MediaPlayer(libvlc)
            mediaPlayer = player
            player.setEventListener(eventListener)

            val vout = player.vlcVout
            if (videoWidth > 0 && videoHeight > 0) {
                vout.setWindowSize(videoWidth, videoHeight)
                if (autoAspectRatio) {
                    player.aspectRatio = "$videoWidth:$videoHeight"
                }
            }

            val media: Media =
                if (isNetwork || uriString.contains("://")) {
                    // Has a scheme, so treat it as a URL MRL and let the Uri
                    // carry the scheme (https, rtsp, blob, ...). Without this
                    // libvlc opens the whole string as a local file path.
                    Media(libvlc, Uri.parse(uriString))
                } else {
                    Media(libvlc, uriString)
                }

            if (hwEnabled >= 2) {
                // Full: hardware decode and GPU-render the video.
                media.setHWDecoderEnabled(true, true)
            } else if (hwEnabled == 1) {
                // Decode-only: decode in hardware but skip the display-render
                // accelerations (`:no-mediacodec-dr` / `:no-omxil-dr`).
                media.setHWDecoderEnabled(true, true)
                media.addOption(":no-mediacodec-dr")
                media.addOption(":no-omxil-dr")
            } else if (hwEnabled == 0) {
                media.setHWDecoderEnabled(false, false)
            }
            // hwEnabled < 0 => automatic, leave libvlc to pick its default.

            mediaOptions?.let { optsArr ->
                for (i in 0 until optsArr.size()) {
                    optsArr.getString(i)?.let { media.addOption(it) }
                }
            }

            player.media = media
            player.scale = 0f

            if (!vout.areViewsAttached()) {
                vout.setVideoSurface(surfaceTexture)
                vout.attachViews(newVideoLayoutListener)
            }

            // Apply the resize mode only once the vout views exist, matching how
            // the official app recomputes the surface geometry on layout.
            applyResizeMode()

            if (isResume) {
                if (autoplay) {
                    player.play()
                }
            } else if (autoplay) {
                player.play()
            }

            emit("onLoadStart", Arguments.createMap())
        } catch (e: Exception) {
            Log.e(TAG, "Error creating player", e)
        }
    }

    fun releasePlayer() {
        stopProgress()
        abandonAudioFocus()
        stopNowPlaying()
        val lib = libvlc ?: return
        mediaPlayer?.let {
            it.stop()
            it.vlcVout.detachViews()
        }
        mediaPlayer = null
        lib.release()
        libvlc = null
    }

    // ---- Public API (called from the manager) ----

    fun play() {
        mediaPlayer?.play()
        requestAudioFocus()
        scheduleProgress()
    }

    fun pause() {
        mediaPlayer?.pause()
        abandonAudioFocus()
        stopProgress()
    }

    fun setPaused(paused: Boolean) {
        val player = mediaPlayer
        if (player != null) {
            if (paused) {
                makeBufferNow(player)
            } else {
                player.play()
                scheduleProgress()
            }
        } else {
            createPlayer(autoplay = !paused, isResume = false)
        }
    }

    private fun makeBufferNow(player: MediaPlayer) {
        abandonAudioFocus()
        stopProgress()
        player.pause()
    }

    fun setProgressUpdateInterval(interval: Long) {
        progressIntervalMs = interval
        if (interval > 0) {
            if (mediaPlayer?.isPlaying == true) {
                scheduleProgress()
            }
        } else {
            stopProgress()
        }
    }

    private fun scheduleProgress() {
        mainHandler.removeCallbacks(progressRunnable)
        if (progressIntervalMs > 0) {
            mainHandler.postDelayed(progressRunnable, progressIntervalMs)
        }
    }

    private fun stopProgress() {
        mainHandler.removeCallbacks(progressRunnable)
    }

    fun seekTo(timeMs: Long) {
        mediaPlayer?.let { player ->
            player.time = timeMs
            Arguments.createMap().apply {
                putDouble("currentTime", player.time / 1000.0)
                putDouble("duration", player.length / 1000.0)
                emit("onSeek", this)
            }
        }
    }

    fun setRate(rate: Float) {
        mediaPlayer?.rate = rate
    }

    fun setVolume(volume: Int) {
        mediaPlayer?.let { if (volume >= 0) it.volume = volume }
    }

    fun setMuted(muted: Boolean) {
        mediaPlayer?.let {
            if (muted) {
                preVolume = it.volume
                it.volume = 0
            } else {
                it.volume = preVolume
            }
        }
    }

    fun setRepeat(repeat: Boolean) {
        this.repeat = repeat
    }

    fun setAspectRatio(ratio: String?) {
        if (!autoAspectRatio && !ratio.isNullOrEmpty()) {
            mediaPlayer?.aspectRatio = ratio
        }
    }

    fun setAutoAspectRatio(auto: Boolean) {
        autoAspectRatio = auto
    }

    fun setResizeMode(mode: String?) {
        resizeMode = mode ?: "contain"
        applyResizeMode()
    }

    // The raw SurfaceTexture vout never reports a new video layout, so read the
    // decoded frame size from the media's video track to drive cover / center.
    private fun updateFrameSizeFromMedia() {
        val player = mediaPlayer ?: return
        val media = player.media ?: return
        val count = media.trackCount
        if (count <= 0) return
        for (i in 0 until count) {
            val t = media.getTrack(i) ?: continue
            if (t is IMedia.VideoTrack) {
                if (t.width > 0 && t.height > 0) {
                    frameWidth = t.width
                    frameHeight = t.height
                    applyResizeMode()
                }
                break
            }
        }
    }

    private fun applyResizeMode() {
        val player = mediaPlayer ?: return
        when (resizeMode) {
            // Zoom the video to cover the view and let the TextureView clip the
            // overflow. `setVideoScale` only works with a VLC-managed child, so
            // framing is driven through the vout scale instead (as on iOS).
            "cover", "fill" -> {
                player.aspectRatio = ""
                if (frameWidth > 0 && frameHeight > 0 && videoWidth > 0 && videoHeight > 0) {
                    player.scale = Math.max(
                        videoWidth.toFloat() / frameWidth,
                        videoHeight.toFloat() / frameHeight
                    )
                } else {
                    player.scale = 0f
                }
            }
            // Distort to fill by forcing the view aspect onto the video.
            "stretch" -> {
                player.scale = 0f
                player.aspectRatio = if (videoWidth > 0 && videoHeight > 0) "$videoWidth:$videoHeight" else ""
            }
            // Render at the natural pixel size (1:1).
            "center", "none", "original" -> {
                player.scale = 1f
                player.aspectRatio = ""
            }
            // contain / fit: default auto-fit, letterboxed.
            else -> {
                player.scale = 0f
                player.aspectRatio = ""
            }
        }
    }

    fun doSnapshot(path: String) {
        if (path.isEmpty()) {
            emitSnapshot(0)
            return
        }
        // Accept a `file://` URI (e.g. from expo-file-system) and write to the
        // real filesystem path.
        val target = if (path.contains("://")) (Uri.parse(path).path ?: path) else path
        mainHandler.post {
            val w = if (videoWidth > 0) videoWidth else width
            val h = if (videoHeight > 0) videoHeight else height
            if (w <= 0 || h <= 0) {
                emitSnapshot(0)
                return@post
            }
            val bitmap = getBitmap(w, h)
            if (bitmap == null) {
                emitSnapshot(0)
                return@post
            }
            try {
                FileOutputStream(target).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                emitSnapshot(1)
            } catch (e: IOException) {
                emitSnapshot(0)
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun emitSnapshot(result: Int) {
        Arguments.createMap().apply {
            putInt("isSuccess", result)
            emit("onSnapshot", this)
        }
    }

    fun getMetadata() {
        val player = mediaPlayer ?: return
        Arguments.createMap().apply {
            putString("type", "Metadata")
            player.media?.let { m ->
                putString("title", m.getMeta(IMedia.Meta.Title))
                putString("artist", m.getMeta(IMedia.Meta.Artist))
                putString("genre", m.getMeta(IMedia.Meta.Genre))
                putString("copyright", m.getMeta(IMedia.Meta.Copyright))
                putString("album", m.getMeta(IMedia.Meta.Album))
                putString("tracknumber", m.getMeta(IMedia.Meta.TrackNumber))
                putString("description", m.getMeta(IMedia.Meta.Description))
                putString("rating", m.getMeta(IMedia.Meta.Rating))
                putString("date", m.getMeta(IMedia.Meta.Date))
                putString("language", m.getMeta(IMedia.Meta.Language))
                putString("publisher", m.getMeta(IMedia.Meta.Publisher))
                putString("encodedby", m.getMeta(IMedia.Meta.EncodedBy))
                m.getMeta(IMedia.Meta.ArtworkURL)?.let { putString("artwork", it) }
                putString("trackid", m.getMeta(IMedia.Meta.TrackID))
                putString("tracktotal", m.getMeta(IMedia.Meta.TrackTotal))
                putString("director", m.getMeta(IMedia.Meta.Director))
                putString("season", m.getMeta(IMedia.Meta.Season))
                putString("episode", m.getMeta(IMedia.Meta.Episode))
                putString("showname", m.getMeta(IMedia.Meta.ShowName))
                putString("albumartist", m.getMeta(IMedia.Meta.AlbumArtist))
                putString("discnumber", m.getMeta(IMedia.Meta.DiscNumber))
            }
            emit("onMetadata", this)
        }
    }

    // ---- Now playing / background audio ----

    fun setNowPlayingMetadata(map: ReadableMap?) {
        if (map == null) {
            nowPlayingTitle = null
            nowPlayingArtist = null
            nowPlayingAlbum = null
            nowPlayingAlbumArtist = null
            nowPlayingGenre = null
            nowPlayingTrackNumber = 0
            nowPlayingDiscNumber = 0
            nowPlayingArtwork = null
        } else {
            nowPlayingTitle = map.getString("title")?.takeIf { it.isNotEmpty() }
            nowPlayingArtist = map.getString("artist")?.takeIf { it.isNotEmpty() }
            nowPlayingAlbum = map.getString("album")?.takeIf { it.isNotEmpty() }
            nowPlayingAlbumArtist = map.getString("albumArtist")?.takeIf { it.isNotEmpty() }
            nowPlayingGenre = map.getString("genre")?.takeIf { it.isNotEmpty() }
            nowPlayingTrackNumber = optInt(map, "trackNumber")
            nowPlayingDiscNumber = optInt(map, "discNumber")
            nowPlayingArtwork = map.getString("artwork")?.takeIf { it.isNotEmpty() }
        }
        if (mediaPlayer != null) {
            pushNowPlaying()
        }
    }

    /// Read a numeric now-playing field tolerantly. JS numbers in a nested
    /// object prop always arrive as doubles, and the key may be omitted
    /// entirely, so a strict `getInt` would throw on both cases.
    private fun optInt(map: ReadableMap, key: String, default: Int = 0): Int =
        if (map.hasKey(key)) map.getDouble(key).toInt() else default

    private fun pushNowPlaying() {
        if (!showNowPlaying) {
            stopNowPlaying()
            return
        }
        val player = mediaPlayer ?: return
        val media = player.media
        val title = nowPlayingTitle ?: media?.getMeta(IMedia.Meta.Title) ?: ""
        val artist = nowPlayingArtist ?: media?.getMeta(IMedia.Meta.Artist) ?: ""
        val album = nowPlayingAlbum ?: media?.getMeta(IMedia.Meta.Album) ?: ""
        val albumArtist = nowPlayingAlbumArtist
            ?: media?.getMeta(IMedia.Meta.AlbumArtist) ?: ""
        val genre = nowPlayingGenre ?: media?.getMeta(IMedia.Meta.Genre) ?: ""
        val trackNumber = if (nowPlayingTrackNumber > 0) nowPlayingTrackNumber
            else media?.getMeta(IMedia.Meta.TrackNumber)?.toIntOrNull() ?: 0
        val discNumber = if (nowPlayingDiscNumber > 0) nowPlayingDiscNumber
            else media?.getMeta(IMedia.Meta.DiscNumber)?.toIntOrNull() ?: 0

        val embeddedArtwork = (media?.getMeta(IMedia.Meta.ArtworkURL))
            ?.takeIf { it.startsWith("file:") }
            ?.let { Uri.parse(it).path }
        val artworkOverride = nowPlayingArtwork?.let { localArtworkPath(it) }

        NowPlayingService.listener = this
        NowPlayingService.update(
            themedReactContext,
            playing = player.isPlaying,
            positionMs = player.time,
            speed = player.rate,
            title = title.ifEmpty { null },
            artist = artist.ifEmpty { null },
            album = album.ifEmpty { null },
            albumArtist = albumArtist.ifEmpty { null },
            genre = genre.ifEmpty { null },
            trackNumber = trackNumber,
            discNumber = discNumber,
            artworkPath = artworkOverride ?: embeddedArtwork,
            durationMs = player.length,
            onNotificationTap = null,
        )
        nowPlayingActive = true
    }

    /** Resolve an override artwork value to a local path. Remote URLs are
     * downloaded to the cache directory asynchronously, then re-push. */
    private fun localArtworkPath(value: String): String? {
        return when {
            value.startsWith("file://") -> Uri.parse(value).path
            value.startsWith("http://") || value.startsWith("https://") -> {
                downloadArtworkAsync(value)
                null
            }
            value.startsWith("data:") -> decodeDataArtwork(value)
            else -> value
        }
    }

    private fun decodeDataArtwork(dataUri: String): String? {
        return try {
            val comma = dataUri.indexOf(',')
            if (comma < 0) return null
            val bytes = Base64.decode(dataUri.substring(comma + 1), Base64.DEFAULT)
            val file = File(themedReactContext.cacheDir, "now_playing_art_${System.currentTimeMillis()}.jpg")
            file.writeBytes(bytes)
            file.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Could not decode artwork", e)
            null
        }
    }

    private fun downloadArtworkAsync(url: String) {
        Thread {
            try {
                val bytes = java.net.URL(url).readBytes()
                val file = File(themedReactContext.cacheDir, "now_playing_art_${System.currentTimeMillis()}.jpg")
                file.writeBytes(bytes)
                mainHandler.post {
                    nowPlayingArtwork = file.absolutePath
                    pushNowPlaying()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not fetch artwork", e)
            }
        }.start()
    }

    override fun onPlay() {
        play()
    }

    override fun onPause() {
        pause()
    }

    override fun onSeekTo(positionMs: Long) {
        seekTo(positionMs)
    }

    override fun onRequestNext() {
        if (nextTrackEnabled) emit("onRequestNext", Arguments.createMap())
        else seekBy(30000L)
    }

    override fun onRequestPrevious() {
        if (previousTrackEnabled) emit("onRequestPrevious", Arguments.createMap())
        else seekBy(-30000L)
    }

    private fun seekBy(deltaMs: Long) {
        mediaPlayer?.let { seekTo(it.time + deltaMs) }
    }

    private fun stopNowPlaying() {
        if (!nowPlayingActive) return
        nowPlayingActive = false
        NowPlayingService.listener = null
        NowPlayingService.stop(themedReactContext)
    }

    // ---- Tracks / audio / subtitles / equalizer ----

    fun getTracks() {
        val player = mediaPlayer ?: return
        val audioJson = org.json.JSONArray()
        player.getAudioTracks()?.forEach { t ->
            audioJson.put(org.json.JSONObject().put("id", t.id).put("name", t.name ?: ""))
        }
        val subJson = org.json.JSONArray()
        player.getSpuTracks()?.forEach { t ->
            subJson.put(org.json.JSONObject().put("id", t.id).put("name", t.name ?: ""))
        }
        Arguments.createMap().apply {
            putString("audio", audioJson.toString())
            putString("subtitle", subJson.toString())
            putInt("audioIndex", player.getAudioTrack())
            putInt("subtitleIndex", player.getSpuTrack())
            emit("onTracks", this)
        }
    }

    fun selectAudioTrack(index: Int) {
        mediaPlayer?.setAudioTrack(index)
    }

    fun selectSubtitleTrack(index: Int) {
        mediaPlayer?.setSpuTrack(index)
    }

    fun setSubtitleFile(path: String) {
        if (path.isEmpty()) return
        val uri = if (path.contains("://")) Uri.parse(path) else Uri.fromFile(java.io.File(path))
        mediaPlayer?.addSlave(IMedia.Slave.Type.Subtitle, uri, true)
    }

    fun setAudioDelay(micros: Long) {
        mediaPlayer?.setAudioDelay(micros)
    }

    fun setSubtitleDelay(micros: Long) {
        mediaPlayer?.setSpuDelay(micros)
    }

    fun setContinueAudioInBackground(enabled: Boolean) {
        continueAudioInBackground = enabled
        shouldResumePlaying = false
    }

    fun setShowNowPlaying(enabled: Boolean) {
        showNowPlaying = enabled
        if (!enabled) {
            stopNowPlaying()
        } else {
            pushNowPlaying()
        }
    }

    fun setNextTrackEnabled(enabled: Boolean) {
        nextTrackEnabled = enabled
    }

    fun setPreviousTrackEnabled(enabled: Boolean) {
        previousTrackEnabled = enabled
    }

    override fun onHostDestroy() {
        themedReactContext.removeLifecycleEventListener(this)
    }

    override fun onHostPause() {
        if (!continueAudioInBackground && mediaPlayer?.isPlaying == true) {
            pause()
            shouldResumePlaying = true
        }
    }

    override fun onHostResume() {
        if (shouldResumePlaying) {
            shouldResumePlaying = false
            play()
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        if (enabled) {
            val eq = equalizer ?: MediaPlayer.Equalizer.create().also { equalizer = it }
            mediaPlayer?.setEqualizer(eq)
        } else {
            equalizer = null
            mediaPlayer?.setEqualizer(null)
        }
    }

    fun setEqualizerPreset(index: Int) {
        // Guard against out-of-range presets, which would otherwise leave the
        // equalizer in a broken/disabled state (matches the iOS range check).
        if (index < 0 || index >= MediaPlayer.Equalizer.getPresetCount()) return
        val eq = MediaPlayer.Equalizer.createFromPreset(index)
        equalizer = eq
        mediaPlayer?.setEqualizer(eq)
    }

    fun setEqualizerPreamp(value: Double) {
        val eq = equalizer ?: MediaPlayer.Equalizer.create().also { equalizer = it }
        eq.setPreAmp(value.toFloat())
        mediaPlayer?.setEqualizer(eq)
    }

    fun setEqualizerBandGains(gains: String) {
        val eq = equalizer ?: MediaPlayer.Equalizer.create().also { equalizer = it }
        val values = gains.split(",").mapNotNull { it.trim().toFloatOrNull() }
        val bandCount = MediaPlayer.Equalizer.getBandCount()
        values.forEachIndexed { i, g -> if (i < bandCount) eq.setAmp(i, g) }
        mediaPlayer?.setEqualizer(eq)
    }

    // ---- SurfaceTextureListener ----

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        videoWidth = width
        videoHeight = height
        val player = mediaPlayer
        if (player != null) {
            // A backgrounded view's surface is destroyed but the player (and
            // its position) survives, so re-attach the new texture instead of
            // rebuilding from 0. Audio-only media keeps no video output.
            val vout = player.vlcVout
            if (!vout.areViewsAttached()) {
                vout.setVideoSurface(surface)
                vout.attachViews(newVideoLayoutListener)
            }
            applyResizeMode()
            return
        }
        createPlayer(autoplay = true, isResume = false)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    // ---- AudioManager.OnAudioFocusChangeListener ----

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Another app needs audio briefly (call, notification).
                resumeAfterFocusLoss = mediaPlayer?.isPlaying == true
                if (mediaPlayer?.isPlaying == true) {
                    makeBufferNow(mediaPlayer!!)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Permanent loss (another player started): pause, do not resume.
                resumeAfterFocusLoss = false
                if (mediaPlayer?.isPlaying == true) {
                    makeBufferNow(mediaPlayer!!)
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Duck: lower the stream volume to a third, restore on regain.
                if (duckedVolume < 0) {
                    duckedVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (duckedVolume / 3), 0)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Restore ducking and resume if we paused for a transient loss.
                if (duckedVolume >= 0) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, duckedVolume, 0)
                    duckedVolume = -1
                }
                if (resumeAfterFocusLoss) {
                    resumeAfterFocusLoss = false
                    play()
                }
            }
            else -> Unit
        }
    }

    private fun requestAudioFocus() {
        if (audioFocusGranted) return
        val result = audioManager.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
        audioFocusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        if (audioFocusGranted) {
            audioManager.abandonAudioFocus(this)
            audioFocusGranted = false
        }
    }

    companion object {
        const val REACT_CLASS = "VLCPlayer"
        private const val TAG = "VLCPlayerView"
    }
}

/** Fabric event carrying a raw payload to the JS `nativeEvent`. */
private class VLCPlayerEvent(
    surfaceId: Int,
    viewTag: Int,
    private val eventType: String,
    private val data: WritableMap,
) : Event<VLCPlayerEvent>(surfaceId, viewTag) {

    override fun getEventName(): String = eventType

    override fun getEventData(): WritableMap = data
}
