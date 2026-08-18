# @lunarr/vlc-player

VLC media player for React Native, built on the **New Architecture** (Fabric
native component + TurboModule via codegen).

- **iOS / tvOS** — MobileVLCKit 3.7.3 / TVVLCKit 3.7.3
- **Android / Android TV** — libvlc-all 3.7.5
- React Native **>= 0.82** (New Architecture only)

> **2.0.0 is a breaking release.** The package is New Architecture **only** and
> no longer ships legacy bridge code. Upgrading from 1.x? See the
> [Migration Guide](https://github.com/lunarr-app/vlc-player/blob/main/MIGRATING_TO_V2.md).

## Install

```sh
npm install @lunarr/vlc-player
# or
pnpm add @lunarr/vlc-player
```

**iOS**: reinstall pods so the Fabric codegen bindings link, and disable
Bitcode (`ENABLE_BITCODE = NO`):

```sh
cd ios && pod install --repo-update
```

**Android**: no extra Maven repository is needed, `libvlc-all` resolves from
Maven Central. Run `./gradlew clean` after upgrading.

> Expo is supported via the bare / prebuild workflow (a development build), but
> being a native module the library cannot run in **Expo Go**.

## Quick start

```tsx
import { useRef } from "react";
import Video, { type VLCPlayerRef } from "@lunarr/vlc-player";

export default function Player() {
  const ref = useRef<VLCPlayerRef>(null);

  return (
    <Video
      ref={ref}
      source={{ uri: "https://example.com/video.mkv" }}
      style={{ flex: 1 }}
      autoplay
      onProgress={({ currentTime, duration }) =>
        console.log(currentTime, duration)
      }
      onEnd={() => console.log("ended")}
      onError={(e) => console.warn(e)}
    />
  );
}
```

## API at a glance

**Props:** `source`, `autoplay`, `paused`, `muted`, `repeat`, `rate`,
`resizeMode`, `volume` (0–200, above 100 is boost), `autoAspectRatio`,
`videoAspectRatio`, `progressUpdateInterval` (ms, `0` = libvlc cadence),
`continueAudioInBackground`, `showNowPlaying`,
`nowPlayingMetadata`.

**Events:** `onLoad`, `onLoadStart`, `onProgress`, `onSeek`, `onPlaying`,
`onPaused`, `onEnd`, `onError`, `onBuffer`, `onMetadata`, `onStopped`,
`onSnapshot`, `onTracks`. State events carry the same
`{ type, currentTime, duration, isPlaying, isBuffering }` payload.

**Imperative (via `ref`):** `play`, `pause`, `seek(timeSeconds)`,
`snapshot(path)`, `getMetadata`, `changeVideoAspectRatio(ratio)`, `getTracks`,
`selectAudioTrack(index)`, `selectSubtitleTrack(index)`, `setSubtitleFile(path)`,
`setAudioDelay(micros)`, `setSubtitleDelay(micros)`, `setEqualizerEnabled`,
`setEqualizerPreset`, `setEqualizerPreamp`, `setEqualizerBandGains`, `release`.

**`source`:** `uri`, `mediaOptions` (`["key=value", ...]`), `initOptions`,
`autoplay`, `isNetwork`, `hwDecoderEnabled` (`VLCHardwareDecoder` enum).

**TurboModule:** `VLCPlayerModule.supportsHardwareCodecs()` reports whether the
device can hardware-decode typical profiles.

## Docs

- [Full guide — installation, API reference, subtitles & audio, now playing
  background audio, TV support](https://github.com/lunarr-app/vlc-player#readme)
- [Migration Guide: v1.x → v2.x](https://github.com/lunarr-app/vlc-player/blob/main/MIGRATING_TO_V2.md)

## License

MIT
