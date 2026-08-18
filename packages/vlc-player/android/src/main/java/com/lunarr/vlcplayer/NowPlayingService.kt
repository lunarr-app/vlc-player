package com.lunarr.vlcplayer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle

/**
 * Hosts the media session and the persistent "now playing" notification so
 * playback keeps running and is controllable when the app is in the background.
 *
 * The host app must declare the foreground-service permissions and request
 * `POST_NOTIFICATIONS` on Android 13+ for the notification to be visible.
 */
class NowPlayingService : Service() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var manager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        instance = this
        manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()

        mediaSession = MediaSessionCompat(this, "VLCPlayer").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    listener?.onPlay()
                }
                override fun onPause() {
                    listener?.onPause()
                }
                override fun onSeekTo(pos: Long) {
                    listener?.onSeekTo(pos)
                }
                override fun onSkipToNext() {
                    listener?.onRequestNext()
                }
                override fun onSkipToPrevious() {
                    listener?.onRequestPrevious()
                }
            })
        }
        mediaSession.isActive = true
    }

    /**
     * Push the latest playback state and metadata into the session and the
     * notification. Called from the player view on state / progress changes.
     */
    fun update(
        playing: Boolean,
        positionMs: Long,
        speed: Float,
        title: String?,
        artist: String?,
        album: String?,
        albumArtist: String?,
        genre: String?,
        trackNumber: Int,
        discNumber: Int,
        artworkPath: String?,
        durationMs: Long,
        onNotificationTap: Intent?,
    ) {
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        val state = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                positionMs,
                speed,
            )
            .build()
        mediaSession.setPlaybackState(state)
        mediaSession.isActive = playing || positionMs > 0

        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title ?: "Unknown")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, albumArtist)
            .putString(MediaMetadataCompat.METADATA_KEY_GENRE, genre)
            .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, trackNumber.toLong())
            .putLong(MediaMetadataCompat.METADATA_KEY_DISC_NUMBER, discNumber.toLong())
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
        artworkPath?.let { path ->
            if (!path.isEmpty()) {
                try {
                    BitmapFactory.decodeFile(path)?.let { meta.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, it) }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not load artwork", e)
                }
            }
        }
        mediaSession.setMetadata(meta.build())

        notify(playing, title, artist, onNotificationTap)
    }

    private fun notify(playing: Boolean, title: String?, artist: String?, onNotificationTap: Intent?) {
        val playPauseAction = if (playing) {
            NotificationCompat.Action(R.drawable.ic_vlc_now_playing, "Pause", pausePendingIntent())
        } else {
            NotificationCompat.Action(R.drawable.ic_vlc_now_playing, "Play", playPendingIntent())
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_vlc_now_playing)
            .setContentTitle(title ?: "Now Playing")
            .setContentText(artist ?: "")
            .setContentIntent(contentPendingIntent(onNotificationTap))
            .setStyle(MediaStyle().setMediaSession(mediaSession.sessionToken))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .setOnlyAlertOnce(true)
            .build()

        if (playing) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            stopForeground(STOP_FOREGROUND_DETACH)
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun contentPendingIntent(onNotificationTap: Intent?): PendingIntent {
        val intent = onNotificationTap ?: packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(this, this::class.java)
        intent?.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        return PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun playPendingIntent(): PendingIntent = pendingIntentByName("PLAY")
    private fun pausePendingIntent(): PendingIntent = pendingIntentByName("PAUSE")

    private fun pendingIntentByName(action: String): PendingIntent {
        val intent = Intent(this, NowPlayingService::class.java).setAction(action)
        return PendingIntent.getService(
            this, action.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY" -> listener?.onPlay()
            "PAUSE" -> listener?.onPause()
        }
        return START_NOT_STICKY
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Now Playing", NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        instance = null
        mediaSession.isActive = false
        mediaSession.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "NowPlayingService"
        private const val CHANNEL_ID = "vlc_player_now_playing"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var instance: NowPlayingService? = null

        var listener: NowPlayingListener? = null

        fun start(context: Context) {
            try {
                val intent = Intent(context, NowPlayingService::class.java)
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Could not start now playing service", e)
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, NowPlayingService::class.java))
            } catch (e: Exception) {
                Log.w(TAG, "Could not stop now playing service", e)
            }
        }

        /** Push state into the running service, starting it on first use. */
        fun update(
            context: Context,
            playing: Boolean,
            positionMs: Long,
            speed: Float,
            title: String?,
            artist: String?,
            album: String?,
            albumArtist: String?,
            genre: String?,
            trackNumber: Int,
            discNumber: Int,
            artworkPath: String?,
            durationMs: Long,
            onNotificationTap: Intent?,
        ) {
            if (instance == null) {
                start(context)
            }
            instance?.update(
                playing, positionMs, speed, title, artist, album, albumArtist, genre,
                trackNumber, discNumber, artworkPath, durationMs, onNotificationTap
            )
        }
    }
}

/** Routes media-session transport commands back to the player view. */
interface NowPlayingListener {
    fun onPlay()
    fun onPause()
    fun onSeekTo(positionMs: Long)
    /** User tapped next in the system now-playing controls. */
    fun onRequestNext()
    /** User tapped previous in the system now-playing controls. */
    fun onRequestPrevious()
}
