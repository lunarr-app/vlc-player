import { useRef, useState } from 'react';
import { Button, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { Directory, File, Paths } from 'expo-file-system';
import Video, {
  type VLCProgressEvent,
  type VLCStateChangeEvent,
  type VLCMetaEvent,
  type VLCMetadataEvent,
  type VLCPlayerRef,
  type VLCPlayerSource,
  type VLCResizeMode,
  type VLCTrack,
} from '@lunarr/vlc-player';

type QueueItem = {
  uri: string;
  title: string;
  artist: string;
  album: string;
};

const QUEUE: QueueItem[] = [
  {
    uri: 'https://www.papytane.com/mp4/airelles.mp4',
    title: 'Airelles',
    artist: 'Lunarr Artists',
    album: 'Field Recordings Vol. 1',
  },
  {
    uri: 'https://www.papytane.com/mp4/bonjour.mp4',
    title: 'Bonjour',
    artist: 'Le Chœur',
    album: 'Morning Sessions',
  },
  {
    uri: 'https://www.papytane.com/mp4/ecureuil.mp4',
    title: 'L’Écureuil',
    artist: 'Gaspard',
    album: 'Animaux',
  },
  {
    uri: 'https://www.papytane.com/mp4/marais.mp4',
    title: 'Le Marais',
    artist: 'Les Arts',
    album: 'City Songs',
  },
  {
    uri: 'https://www.papytane.com/mp4/cygne.mp4',
    title: 'Le Cygne',
    artist: 'Florent',
    album: 'Animaux',
  },
];

const resizeModes: VLCResizeMode[] = ['contain', 'cover', 'stretch', 'center'];
const RESIZE_LABEL: Record<VLCResizeMode, string> = {
  contain: 'Fit',
  cover: 'Fill',
  stretch: 'Stretch',
  center: 'Center',
  none: 'None',
};

function fmt(sec: number): string {
  if (!isFinite(sec) || sec < 0) return '00:00';
  const m = Math.floor(sec / 60);
  const s = Math.floor(sec % 60);
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

export default function App() {
  const ref = useRef<VLCPlayerRef>(null);
  const [uri, setUri] = useState(QUEUE[0].uri);
  const [source, setSource] = useState<VLCPlayerSource>({ uri: QUEUE[0].uri });
  const [queueIndex, setQueueIndex] = useState(0);
  const [audioOnly, setAudioOnly] = useState(false);
  const [showNowPlaying, setShowNowPlaying] = useState(true);
  const [continueAudioInBackground, setContinueAudioInBackground] = useState(true);
  const nowPlayingMetadata = {
    title: QUEUE[queueIndex].title,
    artist: QUEUE[queueIndex].artist,
    album: QUEUE[queueIndex].album,
    artwork: undefined as string | undefined,
  };
  const [log, setLog] = useState<string[]>([]);
  const [tracks, setTracks] = useState<{ audio: VLCTrack[]; subtitle: VLCTrack[] }>({ audio: [], subtitle: [] });
  const [resizeMode, setResizeMode] = useState<VLCResizeMode>('contain');
  const [playing, setPlaying] = useState(true);
  const [muted, setMuted] = useState(false);
  const [pos, setPos] = useState(0);
  const [dur, setDur] = useState(0);
  const [fullscreen, setFullscreen] = useState(false);

  const goTo = (idx: number) => {
    if (idx < 0 || idx >= QUEUE.length) return;
    setQueueIndex(idx);
    setSource({ uri: QUEUE[idx].uri });
  };
  const onRequestNext = () => goTo(queueIndex + 1);
  const onRequestPrevious = () => goTo(queueIndex - 1);

  const push = (line: string) =>
    setLog((prev) => [line, ...prev].slice(0, 60));

  const load = () => setSource({ uri: uri || QUEUE[0].uri });

  const togglePlay = () => {
    if (playing) {
      ref.current?.pause();
      setPlaying(false);
    } else {
      ref.current?.play();
      setPlaying(true);
    }
  };

  const seek = (d: number) => {
    const next = Math.max(0, pos + d);
    ref.current?.seek(next);
    setPos(next);
  };

  const setSubtitleFile = () => {
    const doc = new Directory(Paths.document, 'vlc');
    doc.create({ idempotent: true, intermediates: true });
    const f = new File(doc, 'example.srt');
    f.create({ overwrite: true });
    f.write('1\n00:00:00,000 --> 00:01:00,000\nExample subtitle');
    ref.current?.setSubtitleFile(f.uri);
    push(`setSubtitleFile -> ${f.uri}`);
  };

  const snapshot = () => {
    const doc = new Directory(Paths.document, 'vlc');
    doc.create({ idempotent: true, intermediates: true });
    const f = new File(doc, `snapshot-${Date.now()}.png`);
    ref.current?.snapshot(f.uri);
    push(`snapshot -> ${f.uri}`);
  };

  const syncPlaying = (e: VLCStateChangeEvent) => {
    const t = e.type || '';
    if (t.includes('Playing')) setPlaying(true);
    else if (t.includes('Pause') || t.includes('End') || t.includes('Stop')) setPlaying(false);
  };

  const progress = dur > 0 ? Math.min(1, pos / dur) : 0;

  return (
    <View style={[styles.root, fullscreen && styles.rootFullscreen]}>
      {!fullscreen && <Text style={styles.title}>@lunarr/vlc-player example</Text>}

      <View style={styles.player}>
        <Video
          ref={ref}
          source={source}
          style={StyleSheet.absoluteFill}
          autoplay
          paused={!playing}
          muted={muted}
          audioOnly={audioOnly}
          showNowPlaying={showNowPlaying}
          continueAudioInBackground={continueAudioInBackground}
          nowPlayingMetadata={nowPlayingMetadata}
          repeat={false}
          rate={1}
          volume={100}
          resizeMode={resizeMode}
          progressUpdateInterval={500}
          onLoad={(e: VLCProgressEvent) => push(`onLoad ${JSON.stringify(e)}`)}
          onLoadStart={(e: VLCMetaEvent) => push(`onLoadStart ${JSON.stringify(e)}`)}
          onProgress={(e: VLCProgressEvent) => {
            setPos(e.currentTime);
            setDur(e.duration);
          }}
          onSeek={(e: VLCProgressEvent) => {
            setPos(e.currentTime);
            push(`onSeek ${JSON.stringify(e)}`);
          }}
          onEnd={(e: VLCStateChangeEvent) => push(`onEnd ${JSON.stringify(e)}`)}
          onError={(e: VLCStateChangeEvent) => push(`onError ${JSON.stringify(e)}`)}
          onBuffer={(e: VLCStateChangeEvent) => push(`onBuffer ${JSON.stringify(e)}`)}
          onMetadata={(e: VLCMetadataEvent) => push(`onMetadata ${JSON.stringify(e)}`)}
          onStopped={(e: VLCStateChangeEvent) => push(`onStopped ${JSON.stringify(e)}`)}
          onSnapshot={(e) => push(`onSnapshot ${JSON.stringify(e)}`)}
          onPlaying={(e: VLCStateChangeEvent) => {
            syncPlaying(e);
            push(`onPlaying ${JSON.stringify(e)}`);
          }}
          onPaused={(e: VLCStateChangeEvent) => {
            syncPlaying(e);
            push(`onPaused ${JSON.stringify(e)}`);
          }}
          onTracks={(e) => {
            setTracks({ audio: e.audio, subtitle: e.subtitle });
            push(`onTracks audio=${e.audio.length} subtitle=${e.subtitle.length}`);
          }}
          onRequestNext={() => { push('RequestNext'); onRequestNext(); }}
          onRequestPrevious={() => { push('RequestPrevious'); onRequestPrevious(); }}
        />

        <View style={styles.overlay} pointerEvents="box-none">
          <View style={styles.progressTrack}>
            <View style={[styles.progressFill, { width: `${progress * 100}%` }]} />
          </View>
          {fullscreen && (
            <View style={styles.resizeRow}>
              <Text style={styles.time}>Zoom</Text>
              {resizeModes.map((m) => (
                <Button
                  key={m}
                  title={RESIZE_LABEL[m]}
                  color={resizeMode === m ? '#1e90ff' : '#767676'}
                  onPress={() => setResizeMode(m)}
                />
              ))}
            </View>
          )}
          <View style={styles.transportRow}>
            <Text style={styles.time}>{fmt(pos)}</Text>
            <Button title="«" onPress={onRequestPrevious} />
            <Button title={playing ? 'Pause' : 'Play'} onPress={togglePlay} />
            <Button title="»" onPress={onRequestNext} />
            <Button title="-10s" onPress={() => seek(-10)} />
            <Button title="+10s" onPress={() => seek(10)} />
            <Button
              title={fullscreen ? 'Exit' : 'Full'}
              onPress={() => setFullscreen(!fullscreen)}
            />
            <Text style={styles.time}>{fmt(dur)}</Text>
          </View>
        </View>
      </View>

      {!fullscreen && (
        <ScrollView style={styles.controls} contentContainerStyle={styles.controlsContent}>
          <TextInput
            style={styles.input}
            value={uri}
            onChangeText={setUri}
            placeholder="Media URI"
            autoCapitalize="none"
            autoCorrect={false}
          />
          <Text style={styles.sectionHeader}>Source & transport</Text>
          <View style={styles.row}>
            <Button title="Load" onPress={load} />
            <Button title="play" onPress={() => ref.current?.play()} />
            <Button title="pause" onPress={() => ref.current?.pause()} />
            <Button title="seek 5s" onPress={() => ref.current?.seek(5)} />
            <Button title="release" onPress={() => ref.current?.release()} />
          </View>
          <Text style={styles.sectionHeader}>Playback & audio props</Text>
          <View style={styles.row}>
            <Button title="prev" onPress={onRequestPrevious} />
            <Button title={`${queueIndex + 1}/${QUEUE.length} ${QUEUE[queueIndex].title}`} onPress={() => {}} />
            <Button title="next" onPress={onRequestNext} />
          </View>
          <View style={styles.row}>
            <Button title="mute" onPress={() => setMuted(!muted)} />
            <Button
              title={`now playing ${showNowPlaying ? 'on' : 'off'}`}
              onPress={() => setShowNowPlaying(!showNowPlaying)}
            />
            <Button
              title={`audioOnly ${audioOnly ? 'on' : 'off'}`}
              onPress={() => setAudioOnly(!audioOnly)}
            />
            <Button
              title={`bg audio ${continueAudioInBackground ? 'on' : 'off'}`}
              onPress={() => setContinueAudioInBackground(!continueAudioInBackground)}
            />
            <Button title="metadata" onPress={() => ref.current?.getMetadata()} />
            <Button title="tracks" onPress={() => ref.current?.getTracks()} />
            <Button title="snapshot" onPress={snapshot} />
            <Button title="ext sub" onPress={setSubtitleFile} />
          </View>
          <Text style={styles.sectionHeader}>Audio & subtitle delays</Text>
          <View style={styles.row}>
            <Button title="aud delay 500ms" onPress={() => ref.current?.setAudioDelay(500000)} />
            <Button title="sub delay 500ms" onPress={() => ref.current?.setSubtitleDelay(500000)} />
            <Button title="subs off" onPress={() => ref.current?.selectSubtitleTrack(-1)} />
          </View>
          <Text style={styles.sectionHeader}>Equalizer</Text>
          <View style={styles.row}>
            <Button title="eq preset 1" onPress={() => ref.current?.setEqualizerPreset(1)} />
            <Button title="eq on" onPress={() => ref.current?.setEqualizerEnabled(true)} />
            <Button title="eq off" onPress={() => ref.current?.setEqualizerEnabled(false)} />
            <Button title="eq preamp 2" onPress={() => ref.current?.setEqualizerPreamp(2)} />
            <Button title="eq bands" onPress={() => ref.current?.setEqualizerBandGains('0,-2,3,0')} />
          </View>

          <Text style={styles.sectionHeader}>Resize mode</Text>
          <View style={styles.row}>
            <Button title="Fit" onPress={() => setResizeMode('contain')} />
            <Button title="Fill" onPress={() => setResizeMode('cover')} />
            <Button title="Stretch" onPress={() => setResizeMode('stretch')} />
            <Button title="Center" onPress={() => setResizeMode('center')} />
          </View>

          <Text style={styles.section}>Audio tracks</Text>
          {tracks.audio.map((t) => (
            <Button key={`a${t.id}`} title={`${t.id} - ${t.name}`} onPress={() => ref.current?.selectAudioTrack(t.id)} />
          ))}
          <Text style={styles.section}>Subtitle tracks</Text>
          {tracks.subtitle.map((t) => (
            <Button key={`s${t.id}`} title={`${t.id} - ${t.name}`} onPress={() => ref.current?.selectSubtitleTrack(t.id)} />
          ))}

          <Text style={styles.section}>Event log</Text>
          {log.map((l, i) => (
            <Text key={i} style={styles.logLine}>{l}</Text>
          ))}
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#111', paddingTop: 60 },
  rootFullscreen: { paddingTop: 0 },
  title: { color: '#fff', fontSize: 16, fontWeight: '700', textAlign: 'center', marginBottom: 8 },
  player: { flex: 1, backgroundColor: '#000' },
  controls: { flex: 1.2, backgroundColor: '#1c1c1e' },
  controlsContent: { padding: 8 },
  input: { backgroundColor: '#2c2c2e', color: '#fff', borderRadius: 6, padding: 10, marginBottom: 8 },
  row: { flexDirection: 'row', flexWrap: 'wrap', gap: 6, marginBottom: 8 },
  section: { color: '#8e8e93', fontWeight: '700', marginTop: 4, marginBottom: 4 },
  sectionHeader: { color: '#64b5f6', fontWeight: '700', marginTop: 10, marginBottom: 4, fontSize: 13 },
  logLine: { color: '#0f0', fontSize: 11, marginBottom: 2 },
  overlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    justifyContent: 'flex-end',
  },
  progressTrack: {
    height: 3,
    backgroundColor: 'rgba(255,255,255,0.25)',
  },
  progressFill: {
    height: 3,
    backgroundColor: '#fff',
  },
  resizeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 10,
    paddingVertical: 6,
    backgroundColor: 'rgba(0,0,0,0.35)',
  },
  transportRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 10,
    paddingVertical: 8,
    backgroundColor: 'rgba(0,0,0,0.4)',
  },
  time: { color: '#fff', fontVariant: ['tabular-nums'], fontSize: 12 },
});
