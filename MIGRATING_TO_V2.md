# Migration Guide: v1.x → v2.x

This guide walks you through upgrading `@lunarr/vlc-player` from the legacy 1.x
(React Native bridged) version to 2.x, which is built entirely on the **New
Architecture** (Fabric native component + TurboModule via codegen).

## Requirements that changed

| Item | v1.x | v2.x |
| --- | --- | --- |
| React Native | any supported RN | **>= 0.82** |
| Architecture | bridged (legacy) + new | **New Architecture only** |
| Expo | optional (bare) | optional (bare / prebuild only, not Expo Go) |
| libvlc-all (Android) | older | 3.7.5 |
| MobileVLCKit (iOS) | older | 3.7.3 (latest stable) |

v2 drops all legacy bridge code. There is no mixmode, so `newArchEnabled=false`
and `RCT_NEW_ARCH_ENABLED=0` are ignored on RN 0.82 and newer.

## 1. Upgrade your app

1. Bump React Native to **>= 0.82** and rebuild with the New Architecture (this
   is the only option on RN 0.82+).
2. Reinstall pods so the Fabric codegen bindings link:
   ```sh
   cd ios && pod install --repo-update
   ```
3. iOS still requires **Bitcode disabled** (`ENABLE_BITCODE = NO`) in your
   app's build settings, as with the previous release.
4. Android needs no extra Maven repository, `libvlc-all` resolves from Maven
   Central. Run `./gradlew clean` after upgrading.

## 2. Move options into `source`

In v1, playback options such as `mediaOptions`, `initType`, `initOptions`, and
`hwDecoderEnabled` were top-level props. In v2 they are fields of the `source`
object.

Before:

```tsx
<VLCPlayer
  source={{ uri }}
  mediaOptions={{ ":network-caching" : 300 }}
  initType={2}
  initOptions={["--no-color"]}
/>
```

After:

```tsx
<VLCPlayer
  source={{
    uri,
    mediaOptions: [":network-caching=300"],
    initOptions: ["--no-color"],
  }}
/>
```

> `mediaOptions` changed from a key/value object to an **array of `"key=value"`
> strings** on every platform (previously iOS took an object and Android
> converted internally).

`initType` is **removed in v2** — `initOptions` are now always applied to the
libvlc / MobileVLCKit instance when provided (previously Android only applied
them with `initType={2}`, which is iOS's behavior all along).

`hwDecoderEnabled` moved into `source` and now takes the exported
`VLCHardwareDecoder` enum instead of a raw number (`Automatic`, `Disabled`,
`DecodeOnly`, or `Full`). Omit it to keep the default or pass
`VLCHardwareDecoder.Automatic` explicitly. Most apps leave it unset.

## 3. Move imperative calls to a ref

v1 bundled methods on a class component (using `setNativeProps` under the
hood). v2 exposes an imperative handle via `useRef` backed by Fabric
`dispatchCommand`.

Before:

```tsx
const player = useRef<VLCPlayerInstance>(null);
// ...
player.current?.seek(30);
player.current?.pause();
```

After:

```tsx
const player = useRef<VLCPlayerRef>(null);
// ...
player.current?.seek(30);   // time is in seconds
player.current?.pause();
```

### Method mapping

| v1.x | v2.x |
| --- | --- |
| `seek(timeSec)` | `seek(timeSeconds)` (unified, both platforms) |
| `play(paused)` | `play()` (start / resume from the current position) |
| `pause()` | `pause()` |
| `resume(isResume)` | **removed**, use `play()` (resume) |
| `clear()` | `release()` (renamed, releases the player / media resources) |
| `getMetadata()` | `getMetadata()` |
| `snapshot(path)` | `snapshot(path)` |
| `changeVideoAspectRatio(ratio)` | `changeVideoAspectRatio(ratio)` |
| `autoAspectRatio(isAuto)` | **removed as a method**, use the `autoAspectRatio` prop |
| `position(position)` | **removed**, use `seek(timeSeconds)` |
| `setNativeProps(...)` | **removed**, use props or methods |

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
      onProgress={({ currentTime, duration }) => {
        console.log(currentTime, duration);
      }}
      onEnd={() => console.log("ended")}
      onError={(e) => console.warn(e)}
    />
  );
}
```

## 4. Event times are already in seconds

v1 normalized `currentTime` and `duration` to seconds inside its JS wrapper. v2
emits them in **seconds** directly from the native side, so no manual
`/ 1000` conversion is needed.

## 5. Removed props and handlers

The following are not part of the v2 API:

- `controls`
- `volumeUp`, `volumeDown`
- `seek`, `resume`, `position` (v1 props moved to methods / ref API, `resume`
  is **not** exposed in v2, use `play()` to resume)
- `onOpen`, `onAudioBecomingNoisy`

> Note: v1's `progressUpdateInterval` existed in 1.x but was not honored. In
> v2 it is fully implemented. Set it (in ms) to control how often `onProgress`
> fires while playing. A value of `0` keeps libvlc's own reporting cadence.

## 6. Types

All types are now exported from the package entrypoint:

```tsx
import Video, {
  type VLCPlayerSource,
  type VLCPlayerProps,
  type VLCPlayerRef,
  type VLCProgressEvent,
  type VLCStateChangeEvent,
  type VLCMetadataEvent,
} from "@lunarr/vlc-player";
```

The old `index.d.ts` was replaced by `src/types.ts` re-exporting the codegen
spec types, so the JS package and the native component share a single source of
truth.

## 7. New TurboModule

v2 adds `VLCPlayerModule`, reachable as `NativeVlcPlayerModule`. Use
`supportsHardwareCodecs()` to advertise direct-play support:

```ts
import { NativeModules } from "react-native";
const { VLCPlayerModule } = NativeModules;
if (VLCPlayerModule?.supportsHardwareCodecs?.()) {
  // advertise hardware decoding support
}
```

## 8. Now playing & background audio (new)

v2 automatically publishes now playing info (title, artist, album, artwork,
duration, position) to the OS media controls and keeps audio running in the
background. It needs a small host change compared to v1 (which had neither):

- **iOS**: add `audio` to `UIBackgroundModes` in `Info.plist` for controls and
  background audio. The library manages `AVAudioSession` itself.
- **Android**: the library runs a foreground media service and a media
  notification by itself. On Android 13+ request `POST_NOTIFICATIONS` at
  runtime so the controls are visible (background audio keeps working without
  it).

The OS play, pause, toggle, seek and skip (+/- 30s) commands map back to the
player automatically.

## Reference

- Props: `source`, `autoplay`, `paused`, `muted`, `repeat`, `rate`,
  `resizeMode`, `volume` (0 to 200), `autoAspectRatio`, `videoAspectRatio`,
  `progressUpdateInterval` (ms, 0 = libvlc cadence), `audioOnly`,
  `continueAudioInBackground`, `nowPlayingMetadata`.
- Handlers: `onLoad`, `onLoadStart`, `onProgress`, `onSeek`, `onPlaying`,
  `onPaused`, `onEnd`, `onError`, `onBuffer`, `onMetadata`, `onStopped`,
  `onSnapshot`, `onTracks`.
- Imperative (new in 2.x, via ref): `getTracks`, `selectAudioTrack`,
  `selectSubtitleTrack`, `setSubtitleFile`, `setAudioDelay`, `setSubtitleDelay`,
  `setEqualizerEnabled`, `setEqualizerPreset`, `setEqualizerPreamp`,
  `setEqualizerBandGains`.
- `source`: `uri`, `mediaOptions` (`"key=value"` array), `initOptions`,
  `autoplay`, `isNetwork`, `hwDecoderEnabled`.

See `README.md` for the full API reference.
