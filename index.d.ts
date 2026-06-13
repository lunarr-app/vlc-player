import type { PureComponent } from "react";
import type { ImageSourcePropType, ViewProps } from "react-native";

export interface VLCProgressEvent {
  currentTime: number;
  duration: number;
}

export interface VLCBufferEvent {
  type: "Buffering";
  isBuffering?: boolean;
  bufferRate?: number;
  [key: string]: unknown;
}

export interface VLCMetadataEvent {
  type: "Metadata";
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
  [key: string]: unknown;
}

export type VLCStateChangeType =
  | "Opening"
  | "Playing"
  | "Paused"
  | "Stopped"
  | "Ended"
  | "Error"
  | "Buffering"
  | "Metadata"
  | "ESAdded"
  | "onNewVideoLayout"
  | string;

export interface VLCStateChangeEvent {
  type: VLCStateChangeType;
  currentTime?: number;
  duration?: number;
  isPlaying?: boolean;
  hasVideoOut?: boolean;
  isBuffering?: boolean;
  [key: string]: unknown;
}

export interface VLCSnapshotEvent {
  target?: number;
  success?: number;
  isSuccess?: number;
  [key: string]: unknown;
}

export interface VLCPlayerProps extends ViewProps {
  source: ImageSourcePropType | { uri: string };
  autoplay?: boolean;
  initType?: 1 | 2;
  initOptions?: string[];
  /** iOS: key-value object. Android: converted to `key=value` strings internally. */
  mediaOptions?: Record<string, string | number>;
  paused?: boolean;
  muted?: boolean;
  repeat?: boolean;
  rate?: number;
  seek?: number;
  resume?: boolean;
  position?: number;
  snapshotPath?: string;
  autoAspectRatio?: boolean;
  videoAspectRatio?: string;
  /** Volume: 0–200 */
  volume?: number;
  volumeUp?: number;
  volumeDown?: number;
  hwDecoderEnabled?: number;
  hwDecoderForced?: number;
  onLoad?: (event: VLCProgressEvent) => void;
  onLoadStart?: (event: Record<string, unknown>) => void;
  onProgress?: (event: VLCProgressEvent) => void;
  onSeek?: (event: VLCProgressEvent) => void;
  onEnd?: (event: VLCStateChangeEvent) => void;
  onError?: (event: VLCStateChangeEvent) => void;
  onBuffer?: (event: VLCBufferEvent) => void;
  onMetadata?: (event: VLCMetadataEvent) => void;
  onStopped?: (event: VLCStateChangeEvent) => void;
  onSnapshot?: (event: VLCSnapshotEvent) => void;
  onVideoStateChange?: (event: VLCStateChangeEvent) => void;
  onOpen?: (event: Record<string, unknown>) => void;
}

declare class VLCPlayer extends PureComponent<VLCPlayerProps> {
  setNativeProps(nativeProps: Record<string, unknown>): void;
  getMetadata(): void;
  clear(): void;
  seek(timeSec: number): void;
  autoAspectRatio(isAuto: boolean): void;
  changeVideoAspectRatio(ratio: string): void;
  play(paused: boolean): void;
  position(position: number): void;
  resume(isResume: boolean): void;
  snapshot(path: string): void;
}

export default VLCPlayer;
