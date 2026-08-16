package com.lunarr.vlcplayer

import com.facebook.react.bridge.ReactApplicationContext
import com.lunarr.vlcplayer.NativeVlcPlayerModuleSpec

class VLCPlayerModule(reactContext: ReactApplicationContext) :
    NativeVlcPlayerModuleSpec(reactContext) {

    /**
     * libvlc always decodes via its own stack with hardware acceleration where
     * available. We advertise true so direct-play advertising works on device.
     */
    override fun supportsHardwareCodecs(): Boolean = true

    companion object {
        const val NAME = "VLCPlayerModule"
    }
}
