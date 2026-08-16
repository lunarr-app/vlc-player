# @lunarr/example

Expo (SDK 57) test app for `@lunarr/vlc-player`, exercising every prop,
imperative method, and event on iOS, Android, Apple TV (tvOS) and
Android TV.

> The library is a New Architecture native module, so it cannot run in **Expo
> Go**. Use a development build via `expo run:ios` / `expo run:android`, which
> prebuilds the native app and autolinks the library.

This project uses the
[`react-native-tvos`](https://github.com/react-native-tvos/react-native-tvos)
fork (via the `npm:react-native-tvos@0.86.2-0` alias) plus the
[`@react-native-tvos/config-tv`](https://docs.expo.dev/guides/building-for-tv/)
config plugin, so the same source builds for phone and TV. `EXPO_TV` is
switched on for TV native configurations and off for phone.

## Run

```sh
pnpm install            # from the monorepo root
pnpm example:ios        # mobile  -> expo run:ios
pnpm example:android    # mobile  -> expo run:android
pnpm example:tvos       # tvOS    -> EXPO_TV prebuild + expo run:ios
pnpm example:androidtv  # Android TV -> EXPO_TV prebuild + expo run:android
```

> `prebuild --clean` regenerates the native `android/` / `ios/` directories
> (they are git-ignored in this Expo CNG project). `@react-native-tvos/config-tv`
> injects the `LEANBACK_LAUNCHER` TV intent and the tvOS Podfile/Xcode target
> during `EXPO_TV=1` prebuild. It does **not** add an Android TV **banner**. The
> working tree ships one at
> `android/app/src/main/res/drawable-xhdpi/banner.png` (320 x 180) referenced from
> the manifest, so re-apply it after a clean prebuild if you customize resources.

## What it tests

- Playback props: `autoplay`, `paused`, `muted`, `repeat`, `rate`, `volume`,
  `audioOnly`, `continueAudioInBackground`, `resizeMode`, `autoAspectRatio`, `videoAspectRatio`,
  `progressUpdateInterval`, `nowPlayingMetadata`.
- Imperative API: `play`, `pause`, `seek`, `snapshot`, `release`,
  `getMetadata`, `getTracks`, `selectAudioTrack`, `selectSubtitleTrack`,
  `setSubtitleFile`, `setAudioDelay`, `setSubtitleDelay`, and the equalizer
  methods.
- Events: `onLoad`, `onLoadStart`, `onProgress`, `onSeek`, `onPlaying`,
  `onPaused`, `onEnd`, `onError`, `onBuffer`, `onMetadata`, `onStopped`,
  `onSnapshot`, `onTracks`.
- Subtitle track list / selection, external `.srt` loading, audio track
  selection.

Change the default `VIDEO URI` in the app or the `DEFAULT_URI` constant in
`App.tsx` to test your own media.
