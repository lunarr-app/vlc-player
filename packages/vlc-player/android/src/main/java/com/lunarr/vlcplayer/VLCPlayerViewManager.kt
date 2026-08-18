package com.lunarr.vlcplayer

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.VLCPlayerManagerDelegate
import com.facebook.react.viewmanagers.VLCPlayerManagerInterface

class VLCPlayerViewManager(private val reactContext: ReactApplicationContext) :
    SimpleViewManager<VLCPlayerView>(),
    VLCPlayerManagerInterface<VLCPlayerView> {

    private val delegate: ViewManagerDelegate<VLCPlayerView> =
        VLCPlayerManagerDelegate<VLCPlayerView, VLCPlayerViewManager>(this)

    override fun getName(): String = VLCPlayerView.REACT_CLASS

    override fun createViewInstance(context: ThemedReactContext): VLCPlayerView =
        VLCPlayerView(context)

    override fun getDelegate(): ViewManagerDelegate<VLCPlayerView> = delegate

    // ---- Props ----

    override fun setSource(view: VLCPlayerView, value: ReadableMap?) {
        if (value != null) {
            view.setSource(value)
        }
    }

    override fun setPaused(view: VLCPlayerView, value: Boolean) {
        view.setPaused(value)
    }

    override fun setMuted(view: VLCPlayerView, value: Boolean) {
        view.setMuted(value)
    }

    override fun setRepeat(view: VLCPlayerView, value: Boolean) {
        view.setRepeat(value)
    }

    override fun setRate(view: VLCPlayerView, value: Double) {
        view.setRate(value.toFloat())
    }

    override fun setResizeMode(view: VLCPlayerView, value: String?) {
        view.setResizeMode(value)
    }

    override fun setVolume(view: VLCPlayerView, value: Double) {
        view.setVolume(value.toInt())
    }

    override fun setAutoAspectRatio(view: VLCPlayerView, value: Boolean) {
        view.setAutoAspectRatio(value)
    }

    override fun setVideoAspectRatio(view: VLCPlayerView, value: String?) {
        view.setAspectRatio(value)
    }

    override fun setProgressUpdateInterval(view: VLCPlayerView, value: Int) {
        view.setProgressUpdateInterval(value.toLong())
    }

    override fun setAudioOnly(view: VLCPlayerView, value: Boolean) {
        view.setAudioOnly(value)
    }

    override fun setContinueAudioInBackground(view: VLCPlayerView, value: Boolean) {
        view.setContinueAudioInBackground(value)
    }

    override fun setShowNowPlaying(view: VLCPlayerView, value: Boolean) {
        view.setShowNowPlaying(value)
    }

    override fun setNextTrackEnabled(view: VLCPlayerView, value: Boolean) {
        view.setNextTrackEnabled(value)
    }

    override fun setPreviousTrackEnabled(view: VLCPlayerView, value: Boolean) {
        view.setPreviousTrackEnabled(value)
    }

    override fun setNowPlayingMetadata(view: VLCPlayerView, value: ReadableMap?) {
        view.setNowPlayingMetadata(value)
    }

    // ---- Commands ----

    override fun play(view: VLCPlayerView) {
        view.play()
    }

    override fun pause(view: VLCPlayerView) {
        view.pause()
    }

    override fun seekTo(view: VLCPlayerView, timeSeconds: Double) {
        view.seekTo(Math.round(timeSeconds * 1000f).toLong())
    }

    override fun snapshot(view: VLCPlayerView, path: String) {
        view.doSnapshot(path)
    }

    override fun getMetadata(view: VLCPlayerView) {
        view.getMetadata()
    }

    override fun clear(view: VLCPlayerView) {
        view.releasePlayer()
    }

    override fun changeVideoAspectRatio(view: VLCPlayerView, ratio: String) {
        view.setAspectRatio(ratio)
    }

    override fun getTracks(view: VLCPlayerView) {
        view.getTracks()
    }

    override fun selectAudioTrack(view: VLCPlayerView, index: Int) {
        view.selectAudioTrack(index)
    }

    override fun selectSubtitleTrack(view: VLCPlayerView, index: Int) {
        view.selectSubtitleTrack(index)
    }

    override fun setSubtitleFile(view: VLCPlayerView, path: String) {
        view.setSubtitleFile(path)
    }

    override fun setAudioDelay(view: VLCPlayerView, micros: Double) {
        view.setAudioDelay(Math.round(micros).toLong())
    }

    override fun setSubtitleDelay(view: VLCPlayerView, micros: Double) {
        view.setSubtitleDelay(Math.round(micros).toLong())
    }

    override fun setEqualizerEnabled(view: VLCPlayerView, enabled: Boolean) {
        view.setEqualizerEnabled(enabled)
    }

    override fun setEqualizerPreset(view: VLCPlayerView, index: Int) {
        view.setEqualizerPreset(index)
    }

    override fun setEqualizerPreamp(view: VLCPlayerView, value: Double) {
        view.setEqualizerPreamp(value)
    }

    override fun setEqualizerBandGains(view: VLCPlayerView, gains: String) {
        view.setEqualizerBandGains(gains)
    }
}
