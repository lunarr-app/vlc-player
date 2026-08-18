import type React from 'react';
import type { CodegenTypes, HostComponent, ViewProps } from 'react-native';
import { codegenNativeCommands, codegenNativeComponent } from 'react-native';

type Double = CodegenTypes.Double;
type Int32 = CodegenTypes.Int32;

export interface VLCPlayerSource {
  uri?: string;
  mediaOptions?: Array<string>;
  initOptions?: Array<string>;
  isNetwork?: boolean;
  autoplay?: boolean;
  hwDecoderEnabled?: Int32;
}

export interface VLCStateChangeEvent {
  type: string;
  currentTime?: Double;
  duration?: Double;
  isPlaying?: boolean;
  hasVideoOut?: boolean;
  isBuffering?: boolean;
  bufferRate?: Double;
}

export interface VLCProgressEvent {
  currentTime: Double;
  duration: Double;
}

export interface VLCMetadataEvent {
  type: string;
  title?: string;
  artist?: string;
  genre?: string;
  copyright?: string;
  album?: string;
  tracknumber?: string;
  description?: string;
  rating?: string;
  date?: string;
  language?: string;
  publisher?: string;
  encodedby?: string;
  artwork?: string;
  trackid?: string;
  tracktotal?: string;
  director?: string;
  season?: string;
  episode?: string;
  showname?: string;
  albumartist?: string;
  discnumber?: string;
}

export interface VLCSnapshotEvent {
  isSuccess?: Int32;
}

export interface NowPlayingMetadata {
  title?: string;
  artist?: string;
  album?: string;
  albumArtist?: string;
  genre?: string;
  trackNumber?: Int32;
  discNumber?: Int32;
  artwork?: string;
}

export interface VLCMetaEvent {
  type?: string;
  target?: Int32;
}

/** Empty payload emitted when the user taps next/previous in the system
 * now-playing controls. The host app owns queue navigation. */
export interface VLCRequestNavigationEvent {}

/** Payload for `onTracks`, emitted when track info becomes available or via `getTracks()`. */
export interface VLCTracksEvent {
  /** JSON-encoded `[{ "id": number, "name": string }, ...]` audio tracks. */
  audio: string;
  /** id of the currently selected audio track (-1 if none). */
  audioIndex: Int32;
  /** JSON-encoded `[{ "id": number, "name": string }, ...]` subtitle tracks. */
  subtitle: string;
  /** id of the currently selected subtitle track (-1 if disabled). */
  subtitleIndex: Int32;
}

export interface NativeProps extends ViewProps {
  source?: VLCPlayerSource;
  paused?: boolean;
  muted?: boolean;
  repeat?: boolean;
  rate?: Double;
  resizeMode?: string;
  volume?: Double;
  autoAspectRatio?: boolean;
  videoAspectRatio?: string;
  progressUpdateInterval?: Int32;
  continueAudioInBackground?: boolean;
  showNowPlaying?: boolean;
  nowPlayingMetadata?: NowPlayingMetadata;
  /** True when the host provides `onRequestNext`. Enables the system
   * next-track button and hides the 30s skip buttons. */
  nextTrackEnabled?: boolean;
  /** True when the host provides `onRequestPrevious`. Enables the system
   * previous-track button and hides the 30s skip buttons. */
  previousTrackEnabled?: boolean;

  onLoad?: CodegenTypes.DirectEventHandler<VLCProgressEvent> | null;
  onLoadStart?: CodegenTypes.DirectEventHandler<VLCMetaEvent> | null;
  onProgress?: CodegenTypes.DirectEventHandler<VLCProgressEvent> | null;
  onSeek?: CodegenTypes.DirectEventHandler<VLCProgressEvent> | null;
  onPlaying?: CodegenTypes.DirectEventHandler<VLCStateChangeEvent> | null;
  onPaused?: CodegenTypes.DirectEventHandler<VLCStateChangeEvent> | null;
  onEnd?: CodegenTypes.DirectEventHandler<VLCStateChangeEvent> | null;
  onError?: CodegenTypes.DirectEventHandler<VLCStateChangeEvent> | null;
  onBuffer?: CodegenTypes.DirectEventHandler<VLCStateChangeEvent> | null;
  onMetadata?: CodegenTypes.DirectEventHandler<VLCMetadataEvent> | null;
  onStopped?: CodegenTypes.DirectEventHandler<VLCStateChangeEvent> | null;
  onSnapshot?: CodegenTypes.DirectEventHandler<VLCSnapshotEvent> | null;
  onTracks?: CodegenTypes.DirectEventHandler<VLCTracksEvent> | null;
  /** Emitted when the user taps the next-track command in system now-playing
   * controls. The host app is responsible for advancing its own queue. No-op
   * if the app does nothing. */
  onRequestNext?: CodegenTypes.DirectEventHandler<VLCRequestNavigationEvent> | null;
  /** Emitted when the user taps the previous-track command in system
   * now-playing controls. The host app is responsible for going back in its
   * own queue. No-op if the app does nothing. */
  onRequestPrevious?: CodegenTypes.DirectEventHandler<VLCRequestNavigationEvent> | null;
}

export interface NativeCommands {
  play: (ref: React.ComponentRef<HostComponent<NativeProps>>) => void;
  pause: (ref: React.ComponentRef<HostComponent<NativeProps>>) => void;
  seekTo: (ref: React.ComponentRef<HostComponent<NativeProps>>, timeSeconds: Double) => void;
  snapshot: (ref: React.ComponentRef<HostComponent<NativeProps>>, path: string) => void;
  getMetadata: (ref: React.ComponentRef<HostComponent<NativeProps>>) => void;
  clear: (ref: React.ComponentRef<HostComponent<NativeProps>>) => void;
  changeVideoAspectRatio: (ref: React.ComponentRef<HostComponent<NativeProps>>, ratio: string) => void;
  getTracks: (ref: React.ComponentRef<HostComponent<NativeProps>>) => void;
  selectAudioTrack: (ref: React.ComponentRef<HostComponent<NativeProps>>, index: Int32) => void;
  selectSubtitleTrack: (ref: React.ComponentRef<HostComponent<NativeProps>>, index: Int32) => void;
  setSubtitleFile: (ref: React.ComponentRef<HostComponent<NativeProps>>, path: string) => void;
  setAudioDelay: (ref: React.ComponentRef<HostComponent<NativeProps>>, micros: Double) => void;
  setSubtitleDelay: (ref: React.ComponentRef<HostComponent<NativeProps>>, micros: Double) => void;
  setEqualizerEnabled: (ref: React.ComponentRef<HostComponent<NativeProps>>, enabled: boolean) => void;
  setEqualizerPreset: (ref: React.ComponentRef<HostComponent<NativeProps>>, index: Int32) => void;
  setEqualizerPreamp: (ref: React.ComponentRef<HostComponent<NativeProps>>, value: Double) => void;
  setEqualizerBandGains: (ref: React.ComponentRef<HostComponent<NativeProps>>, gains: string) => void;
}

export const Commands: NativeCommands = codegenNativeCommands<NativeCommands>({
  supportedCommands: [
    'play',
    'pause',
    'seekTo',
    'snapshot',
    'getMetadata',
    'clear',
    'changeVideoAspectRatio',
    'getTracks',
    'selectAudioTrack',
    'selectSubtitleTrack',
    'setSubtitleFile',
    'setAudioDelay',
    'setSubtitleDelay',
    'setEqualizerEnabled',
    'setEqualizerPreset',
    'setEqualizerPreamp',
    'setEqualizerBandGains',
  ],
});

export default codegenNativeComponent<NativeProps>('VLCPlayer') as HostComponent<NativeProps>;
