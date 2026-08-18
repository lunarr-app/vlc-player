import type { StyleProp, ViewStyle } from 'react-native';
import type {
  VLCProgressEvent,
  VLCStateChangeEvent,
  VLCMetadataEvent,
  VLCSnapshotEvent,
  VLCMetaEvent,
  VLCRequestNavigationEvent,
} from '../specs/VLCPlayerNativeComponent';

export type {
  VLCProgressEvent,
  VLCStateChangeEvent,
  VLCMetadataEvent,
  VLCSnapshotEvent,
  VLCMetaEvent,
  VLCTracksEvent,
  VLCRequestNavigationEvent,
} from '../specs/VLCPlayerNativeComponent';

/** Hardware decoding mode (matches the official app's single `hardware_acceleration`
 * setting). Force is always applied when HW decoding is enabled. */
export const VLCHardwareDecoder = {
  /** Let libvlc pick (default). */
  Automatic: -1,
  /** Force software decoding. */
  Disabled: 0,
  /** Decode in hardware, skip the GPU render pass (Android-only distinction). */
  DecodeOnly: 1,
  /** Hardware decode + GPU-render the video. */
  Full: 2,
} as const;

export type VLCHardwareDecoder =
  (typeof VLCHardwareDecoder)[keyof typeof VLCHardwareDecoder];

export interface VLCPlayerSource {
  uri?: string;
  /** libvlc media options ("option=value"), passed straight to libvlc. Can
   * style subtitles, e.g. `[':freetype-rel-fontsize=24', ':freetype-bold',
   * ':freetype-color=0xFFFFFF']` (size is relative, default 16). */
  mediaOptions?: string[];
  /** libvlc / MobileVLCKit initialization options (applied to the player
   * instance, not per-media). */
  initOptions?: string[];
  isNetwork?: boolean;
  autoplay?: boolean;
  /** Hardware decoding mode, see [VLCHardwareDecoder]. */
  hwDecoderEnabled?: VLCHardwareDecoder;
}

/** A single audio or subtitle track. */
export interface VLCTrack {
  /** Opaque track id, pass it back to `selectAudioTrack` / `selectSubtitleTrack`. */
  id: number;
  /** Display name (usually includes language / description). */
  name: string;
}

/** Parsed `onTracks` payload. */
export interface VLCPlayerTracks {
  audio: VLCTrack[];
  /** id of the currently selected audio track (-1 if none). */
  audioIndex: number;
  subtitle: VLCTrack[];
  /** id of the currently selected subtitle track (-1 if disabled). */
  subtitleIndex: number;
}

/** How the video is framed inside the view. */
export type VLCResizeMode = 'contain' | 'cover' | 'stretch' | 'center' | 'none';

export type VLCPlayerProps = {
  source?: VLCPlayerSource;
  style?: StyleProp<ViewStyle>;
  autoplay?: boolean;
  paused?: boolean;
  muted?: boolean;
  repeat?: boolean;
  rate?: number;
  /**
   * Video framing: `contain` (fit, letterbox), `cover` (zoom, fill/crop),
   * `stretch` (fill, distort), `center` / `none` (original size).
   */
  resizeMode?: VLCResizeMode;
  /** Volume: 0-200 (100 = normal, >100 boosts up to 2x). */
  volume?: number;
  autoAspectRatio?: boolean;
  videoAspectRatio?: string;
  /** ms between `onProgress` emissions while playing (0 uses libvlc cadence). */
  progressUpdateInterval?: number;
  /** Render audio without the video surface. On Android, switching from
   * audio-only back to video reloads the media (playback restarts). */
  audioOnly?: boolean;
  /** Keep playback running when the app is backgrounded. Default true,
   * regardless of media type. When false, playback pauses on background and
   * auto-resumes when the app returns to the foreground. */
  continueAudioInBackground?: boolean;
  /** Whether to publish a system now-playing entry (media controls /
   * notification). Default true. Set false to hide the now-playing UI while
   * keeping the `continueAudioInBackground` behavior. */
  showNowPlaying?: boolean;
  /** Override the metadata shown in the system now-playing UI. Individual
   * fields, any omitted falls back to the media's embedded tags. `artwork`
   * is an http(s) URL, a local file path, or a `data:` base64 URI. */
  nowPlayingMetadata?: {
    title?: string;
    artist?: string;
    album?: string;
    albumArtist?: string;
    genre?: string;
    trackNumber?: number;
    discNumber?: number;
    artwork?: string;
  };

  onLoad?: (event: VLCProgressEvent) => void;
  onLoadStart?: (event: VLCMetaEvent) => void;
  onProgress?: (event: VLCProgressEvent) => void;
  onSeek?: (event: VLCProgressEvent) => void;
  onPlaying?: (event: VLCStateChangeEvent) => void;
  onPaused?: (event: VLCStateChangeEvent) => void;
  onEnd?: (event: VLCStateChangeEvent) => void;
  onError?: (event: VLCStateChangeEvent) => void;
  onBuffer?: (event: VLCStateChangeEvent) => void;
  onMetadata?: (event: VLCMetadataEvent) => void;
  onStopped?: (event: VLCStateChangeEvent) => void;
  onSnapshot?: (event: VLCSnapshotEvent) => void;
  onTracks?: (event: VLCPlayerTracks) => void;
  /** Called when the user taps the next-track command in system now-playing
   * controls. The host app owns advancing its queue (e.g. swap `source`).
   * Providing this enables the next-track button. Omitting it hides it. */
  onRequestNext?: (event: VLCRequestNavigationEvent) => void;
  /** Called when the user taps the previous-track command in system
   * now-playing controls. The host app owns going back in its queue.
   * Providing this enables the previous-track button. Omitting it hides it. */
  onRequestPrevious?: (event: VLCRequestNavigationEvent) => void;
};

export interface VLCPlayerRef {
  play(): void;
  pause(): void;
  /** Seek to a position in seconds (fractional values accepted). */
  seek(timeSeconds: number): void;
  snapshot(path: string): void;
  getMetadata(): void;
  /** Release the player and its media resources (the player is no longer
   * usable: provide a new source to play again). */
  release(): void;
  changeVideoAspectRatio(ratio: string): void;
  /** Emit `onTracks` with the current audio/subtitle track lists. */
  getTracks(): void;
  /** Select an audio track by id from `onTracks` (-1 to disable). */
  selectAudioTrack(index: number): void;
  /** Select a subtitle track by id from `onTracks` (-1 to disable). */
  selectSubtitleTrack(index: number): void;
  /** Load an external subtitle file (e.g. .srt / .vtt). */
  setSubtitleFile(path: string): void;
  /** Audio delay in microseconds. */
  setAudioDelay(micros: number): void;
  /** Subtitle delay in microseconds. */
  setSubtitleDelay(micros: number): void;
  setEqualizerEnabled(enabled: boolean): void;
  setEqualizerPreset(index: number): void;
  /** Pre-amp gain in the -20..20 dB range. */
  setEqualizerPreamp(value: number): void;
  /** Comma-separated band gains (-20..20 dB), e.g. "0,-2,3,0". */
  setEqualizerBandGains(gains: string): void;
}
