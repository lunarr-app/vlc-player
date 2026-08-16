import type React from 'react';
import { forwardRef, useImperativeHandle, useRef } from 'react';
import type { NativeSyntheticEvent } from 'react-native';
import RCTVLCPLayer, { Commands } from '../specs/VLCPlayerNativeComponent';
import type {
  VLCMetaEvent,
  VLCMetadataEvent,
  VLCPlayerProps,
  VLCPlayerRef,
  VLCProgressEvent,
  VLCSnapshotEvent,
  VLCStateChangeEvent,
  VLCTracksEvent,
  VLCTrack,
  VLCPlayerSource,
} from './types';

type NativeComponentRef = React.ComponentRef<typeof RCTVLCPLayer>;

function toNativeEvent<T>(
  handler: ((event: T) => void) | undefined,
): ((event: NativeSyntheticEvent<T>) => void) | undefined {
  if (!handler) {
    return undefined;
  }
  return (event: NativeSyntheticEvent<T>) => handler(event.nativeEvent);
}

function parseTracks(json: string): VLCTrack[] {
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export default forwardRef<VLCPlayerRef, VLCPlayerProps>(function VLCPlayer(props, ref) {
  const nativeRef = useRef<NativeComponentRef | null>(null);

  useImperativeHandle(ref, () => ({
    play() {
      if (nativeRef.current) {
        Commands.play(nativeRef.current);
      }
    },
    pause() {
      if (nativeRef.current) {
        Commands.pause(nativeRef.current);
      }
    },
    seek(timeSeconds: number) {
      if (nativeRef.current) {
        Commands.seekTo(nativeRef.current, timeSeconds);
      }
    },
    snapshot(path: string) {
      if (nativeRef.current) {
        Commands.snapshot(nativeRef.current, path);
      }
    },
    getMetadata() {
      if (nativeRef.current) {
        Commands.getMetadata(nativeRef.current);
      }
    },
    release() {
      if (nativeRef.current) {
        Commands.clear(nativeRef.current);
      }
    },
    changeVideoAspectRatio(ratio: string) {
      if (nativeRef.current) {
        Commands.changeVideoAspectRatio(nativeRef.current, ratio);
      }
    },
    getTracks() {
      if (nativeRef.current) {
        Commands.getTracks(nativeRef.current);
      }
    },
    selectAudioTrack(index: number) {
      if (nativeRef.current) {
        Commands.selectAudioTrack(nativeRef.current, index);
      }
    },
    selectSubtitleTrack(index: number) {
      if (nativeRef.current) {
        Commands.selectSubtitleTrack(nativeRef.current, index);
      }
    },
    setSubtitleFile(path: string) {
      if (nativeRef.current) {
        Commands.setSubtitleFile(nativeRef.current, path);
      }
    },
    setAudioDelay(micros: number) {
      if (nativeRef.current) {
        Commands.setAudioDelay(nativeRef.current, micros);
      }
    },
    setSubtitleDelay(micros: number) {
      if (nativeRef.current) {
        Commands.setSubtitleDelay(nativeRef.current, micros);
      }
    },
    setEqualizerEnabled(enabled: boolean) {
      if (nativeRef.current) {
        Commands.setEqualizerEnabled(nativeRef.current, enabled);
      }
    },
    setEqualizerPreset(index: number) {
      if (nativeRef.current) {
        Commands.setEqualizerPreset(nativeRef.current, index);
      }
    },
    setEqualizerPreamp(value: number) {
      if (nativeRef.current) {
        Commands.setEqualizerPreamp(nativeRef.current, value);
      }
    },
    setEqualizerBandGains(gains: string) {
      if (nativeRef.current) {
        Commands.setEqualizerBandGains(nativeRef.current, gains);
      }
    },
  }));

  const { source, autoplay, audioOnly, onLoad, onLoadStart, onProgress, onSeek, onPlaying, onPaused, onEnd, onError, onBuffer, onMetadata, onStopped, onSnapshot, onTracks, ...rest } = props;
  const nativeSource: VLCPlayerSource = { ...source };

  if (autoplay !== undefined && nativeSource.autoplay === undefined) {
    nativeSource.autoplay = autoplay;
  }

  return (
    <RCTVLCPLayer
      ref={nativeRef}
      source={nativeSource}
      onLoad={toNativeEvent<VLCProgressEvent>(onLoad)}
      onLoadStart={toNativeEvent<VLCMetaEvent>(onLoadStart)}
      onProgress={toNativeEvent<VLCProgressEvent>(onProgress)}
      onSeek={toNativeEvent<VLCProgressEvent>(onSeek)}
      onPlaying={toNativeEvent<VLCStateChangeEvent>(onPlaying)}
      onPaused={toNativeEvent<VLCStateChangeEvent>(onPaused)}
      onEnd={toNativeEvent<VLCStateChangeEvent>(onEnd)}
      onError={toNativeEvent<VLCStateChangeEvent>(onError)}
      onBuffer={toNativeEvent<VLCStateChangeEvent>(onBuffer)}
      onMetadata={toNativeEvent<VLCMetadataEvent>(onMetadata)}
      onStopped={toNativeEvent<VLCStateChangeEvent>(onStopped)}
      onSnapshot={toNativeEvent<VLCSnapshotEvent>(onSnapshot)}
      onTracks={
        onTracks
          ? (event: NativeSyntheticEvent<VLCTracksEvent>) =>
              onTracks({
                audio: parseTracks(event.nativeEvent.audio),
                audioIndex: event.nativeEvent.audioIndex,
                subtitle: parseTracks(event.nativeEvent.subtitle),
                subtitleIndex: event.nativeEvent.subtitleIndex,
              })
          : undefined
      }
      audioOnly={audioOnly}
      {...rest}
    />
  );
});
