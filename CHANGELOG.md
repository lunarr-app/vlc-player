# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [2.1.0] - 2026-08-19

### Added

- Add now playing next and previous track controls
- Handle iOS audio interruptions and route changes (pause on interruption, resume when the system allows)

### Changed

- Mirror official now-playing lifecycle, now playing controls register when playback starts
- Removed `audioOnly` prop to match official app behavior. For audio only you can still use `mediaOptions: [":no-video"]` in the source

### Fixed

- Fix file:// URIs on iOS local audio

## [2.0.0] - 2026-08-17

See the [migration guide](MIGRATING_TO_V2.md) for upgrading from v1.x.

### Changed

- Rewrite for the New Architecture (Fabric + TurboModule via codegen, RN >= 0.82)
- Add tvOS and Android TV support
- Add now playing support for Android and iOS
- No Expo dependency, works in bare React Native and Expo apps
- `mediaOptions` is now an array of `"key=value"` strings
- Events emit time values in seconds directly from the native side
- Imperative calls now go through a ref instead of class methods

## [1.1.0] - 2026-06-13

### Added

- Add TypeScript declarations
- Wire missing native APIs and fix platform playback bugs

### Changed

- Upgrade to MobileVLCKit 3.7.3 and libvlc-all 3.6.5
- Remove prop-types dependency
- Fix VLCPlayer TypeScript export for valid JSX component typing

## [1.0.5] - 2023-03-23

### Changed

- Revert mediaOptions fix on Android

## [1.0.4] - 2023-02-23

### Added

- Fix mediaOptions on Android and indexing error on option arrays

### Changed

- Update prop-types
- Upgrade to MobileVLCKit 3.3.17

## [1.0.3] - 2021-03-29

### Changed

- Fix package name and update defaults
- Upgrade to MobileVLCKit 3.3.16
- Update resolver and build

## [1.0.2] - 2020-04-20

### Added

- Emit metadata and seek events
- Handle seeking and video progress

### Changed

- Upgrade to VLC 3.3.10

## [1.0.1] - 2020-04-19

### Added

- Add readme
- Update props and event values

### Changed

- Remove onIsPlaying and fix onVideoProgress
- Update VLC player

## [1.0.0] - 2020-04-19

### Added

- Initial release