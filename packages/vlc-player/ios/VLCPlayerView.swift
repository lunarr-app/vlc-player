import UIKit
#if os(tvOS)
import TVVLCKit
#else
import MobileVLCKit
#endif
import AVFoundation
import MediaPlayer

/// Swift core hosting a libvlc `VLCMediaPlayer`.
///
/// The Fabric `VLCPlayerComponentView` (Objective-C++) is the host view that
/// forwards props/commands in and converts the `onEvent` closure out to the
/// codegen'd event emitter. All player logic lives here.
@objcMembers
public final class VLCPlayerView: UIView, VLCMediaPlayerDelegate, VLCMediaDelegate {

    /// Bridge to the Objective-C++ Fabric wrapper.
    public var onEvent: ((String, [String: Any]) -> Void)?

    private var player: VLCMediaPlayer?
    private var source: [String: Any]?
    private var isPaused: Bool = false
    private var autoAspectRatio: Bool = false
    private var isRepeat: Bool = false
    private var preVolume: Int = 100
    private var progressIntervalMs: Int = 0
    private var progressTimer: Timer?
    private var audioOnly: Bool = false
    private var savedVideoTrackIndex: Int32 = -1
    private var continueAudioInBackground: Bool = true
    private var shouldResumePlaying = false
    private var nowPlayingOverride: [String: Any]?
    private var showNowPlaying: Bool = true
    private var controlsRegistered = false
    private var nextTrackEnabled = false
    private var previousTrackEnabled = false
    private var equalizer: VLCAudioEqualizer?
    private var resizeMode = "contain"
    // Retained so the C pointer passed to MobileVLCKit's videoAspectRatio stays
    // valid for the lifetime of the value (the setter does not necessarily copy).
    private var stretchAspect: NSString?

    override public init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .black
        setupLifecycleObservers()
        if showNowPlaying {
            setupNowPlayingControls()
        }
    }

    required public init?(coder: NSCoder) {
        super.init(coder: coder)
        backgroundColor = .black
        setupLifecycleObservers()
        if showNowPlaying {
            setupNowPlayingControls()
        }
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
        releasePlayer()
    }

    // MARK: - Lifecycle

    private func setupLifecycleObservers() {
        let center = NotificationCenter.default
        center.addObserver(
            self, selector: #selector(willResignActive),
            name: UIApplication.willResignActiveNotification, object: nil
        )
        center.addObserver(
            self, selector: #selector(willEnterForeground),
            name: UIApplication.willEnterForegroundNotification, object: nil
        )
    }

    @objc private func willResignActive(_ note: Notification) {
        // Pause on resign only when the host opts out of background playback, so
        // playback keeps running in the background by default regardless of
        // media type, matching the official app.
        if !continueAudioInBackground, !isPaused {
            setPaused(true)
            shouldResumePlaying = true
        }
    }

    @objc private func willEnterForeground(_ note: Notification) {
        // Resume only if we paused the playback for backgrounding, so a
        // user-initiated pause is never overridden.
        if shouldResumePlaying {
            shouldResumePlaying = false
            play()
        }
    }

    override public func layoutSubviews() {
        super.layoutSubviews()
        if autoAspectRatio, let p = player, bounds.size.width > 0, bounds.size.height > 0 {
            applyAspectRatio("\(Int(bounds.size.width)):\(Int(bounds.size.height))")
        }
        if ["cover", "fill", "stretch"].contains(resizeMode),
           player != nil, bounds.size.width > 0, bounds.size.height > 0 {
            applyResizeMode()
        }
    }

    // MARK: - Source / setup

    public func setSource(_ src: [String: Any]) {
        if player != nil {
            releasePlayer()
        }
        source = src
        buildPlayerAndMedia(src: src)
    }

    private func buildPlayerAndMedia(src: [String: Any]) {
        guard let uri = src["uri"] as? String, !uri.isEmpty else { return }

        let initOptions = src["initOptions"] as? [String] ?? []
        let autoplay = (src["autoplay"] as? Bool) ?? false
        let mediaOptions = src["mediaOptions"] as? [String] ?? []
        // Hardware decoding mode: -1 automatic (let VLCKit choose), 0 disabled,
        // >= 1 force VideoToolbox. iOS collapses decode-only (1) and full (2)
        // because VLCKit has no separate decode-only analog (that's the Android
        // `:no-mediacodec-dr` mode). Force is implicit when enabled.
        let hwEnabled = (src["hwDecoderEnabled"] as? NSNumber)?.intValue ?? -1

        let newPlayer = VLCMediaPlayer(options: initOptions)
        if !audioOnly {
            newPlayer.drawable = self
        }
        newPlayer.delegate = self
        newPlayer.scaleFactor = 0

        let media: VLCMedia
        if let url = URL(string: uri), let scheme = url.scheme, !scheme.isEmpty, scheme != "file" {
            // Real remote MRLs (http/https/rtsp/...) open by URL.
            media = VLCMedia(url: url)
        } else if let fileURL = URL(string: uri), fileURL.isFileURL {
            // Strip the file:// prefix or libvlc cannot open local audio.
            media = VLCMedia(path: fileURL.path)
        } else {
            // Bare filesystem path (e.g. /private/var/...).
            media = VLCMedia(path: uri)
        }
        media.delegate = self

        for option in mediaOptions {
            media.addOption(option)
        }

        if hwEnabled >= 1 {
            media.addOption(":avcodec-hw=videotoolbox")
        } else if hwEnabled == 0 {
            media.addOption(":avcodec-hw=none")
        }

        media.parse()
        newPlayer.media = media
        player = newPlayer
        applyResizeMode()

        if autoplay {
            play()
        }
        onEvent?("LoadStart", [:])
    }

    // MARK: - Playback controls

    public func play() {
        player?.play()
        isPaused = false
        startProgressTimer()
    }

    public func pause() {
        player?.pause()
        isPaused = true
        stopProgressTimer()
    }

    public func setPaused(_ paused: Bool) {
        if player != nil {
            if paused { pause() } else { play() }
        } else if let src = source {
            buildPlayerAndMedia(src: src)
            if !paused {
                play()
            }
        }
    }

    public func seek(to seconds: Double) {
        guard let p = player else { return }
        let ms = Int64(seconds * 1000)
        p.time = VLCTime(int: Int32(ms))
        onEvent?("Seek", [
            "currentTime": secondsFromTime(p.time),
            "duration": secondsFromTime(p.media?.length),
        ])
    }

    public func setRate(_ value: Double) {
        player?.rate = Float(value)
    }

    public func setProgressUpdateInterval(_ ms: Int) {
        progressIntervalMs = ms
        if ms > 0, player?.isPlaying == true {
            startProgressTimer()
        } else if ms <= 0 {
            stopProgressTimer()
        }
    }

    // MARK: - Tracks

    public func getTracks() {
        guard let p = player else { return }

        var audio: [[String: Any]] = []
        let audioIndexes = p.audioTrackIndexes ?? []
        let audioNames = p.audioTrackNames ?? []
        for (i, idx) in audioIndexes.enumerated() {
            audio.append([
                "id": (idx as? NSNumber)?.intValue ?? Int(i),
                "name": i < audioNames.count ? (audioNames[i] as? String ?? "") : "",
            ])
        }

        var subtitle: [[String: Any]] = []
        let subIndexes = p.videoSubTitlesIndexes ?? []
        let subNames = p.videoSubTitlesNames ?? []
        for (i, idx) in subIndexes.enumerated() {
            subtitle.append([
                "id": (idx as? NSNumber)?.intValue ?? Int(i),
                "name": i < subNames.count ? (subNames[i] as? String ?? "") : "",
            ])
        }

        onEvent?("Tracks", [
            "audio": jsonString(audio),
            "audioIndex": Int(p.currentAudioTrackIndex),
            "subtitle": jsonString(subtitle),
            "subtitleIndex": Int(p.currentVideoSubTitleIndex),
        ])
    }

    public func selectAudioTrack(_ index: Int) {
        player?.currentAudioTrackIndex = Int32(index)
    }

    public func selectSubtitleTrack(_ index: Int) {
        player?.currentVideoSubTitleIndex = Int32(index)
    }

    public func setSubtitleFile(_ path: String) {
        guard let p = player else { return }
        let slave: URL
        if let url = URL(string: path), url.scheme != nil {
            // Already a URL (file:// from expo-file-system, or remote).
            slave = url
        } else {
            // Bare filesystem path.
            slave = URL(fileURLWithPath: path)
        }
        _ = p.addPlaybackSlave(slave, type: .subtitle, enforce: true)
    }

    public func setAudioDelay(_ micros: Int64) {
        player?.currentAudioPlaybackDelay = NSInteger(micros)
    }

    public func setSubtitleDelay(_ micros: Int64) {
        player?.currentVideoSubTitleDelay = NSInteger(micros)
    }

    public func setAudioOnly(_ enabled: Bool) {
        guard audioOnly != enabled else { return }
        audioOnly = enabled
        guard let p = player else { return }
        if enabled {
            // Turning off video: drop the drawable and disable the video track
            // so video output (and rendering) actually stops. `drawable` alone
            // does not tear down the running renderer. Save the active track
            // so it can be restored; it may be -1 (auto) while playing.
            savedVideoTrackIndex = p.currentVideoTrackIndex
            p.currentVideoTrackIndex = -1
            p.drawable = nil
        } else {
            // Restore video: re-apply the drawable and re-select a video track.
            // If there was no explicit selection (auto), pick the first track.
            p.drawable = self
            p.currentVideoTrackIndex = savedVideoTrackIndex >= 0 ? savedVideoTrackIndex : 0
            savedVideoTrackIndex = 0
        }
    }

    public func setContinueAudioInBackground(_ enabled: Bool) {
        continueAudioInBackground = enabled
        shouldResumePlaying = false
    }

    public func setNowPlayingMetadata(_ metadata: [String: Any]?) {
        nowPlayingOverride = metadata
        if player != nil {
            updateNowPlayingInfo()
        }
    }

    public func setShowNowPlaying(_ enabled: Bool) {
        guard showNowPlaying != enabled else { return }
        showNowPlaying = enabled
        if enabled {
            setupNowPlayingControls()
            updateNowPlayingInfo()
        } else {
            clearNowPlayingInfo()
        }
    }

    /// Enables the system next-track command when the host app handles
    /// `onRequestNext`. When either navigation handler is set, the skip
    /// (30s) buttons are hidden so iOS shows next/previous, matching the official
    /// app's behavior for a media list.
    public func setNextTrackEnabled(_ enabled: Bool) {
        guard nextTrackEnabled != enabled else { return }
        nextTrackEnabled = enabled
        updateNowPlayingCommandAvailability()
    }

    public func setPreviousTrackEnabled(_ enabled: Bool) {
        guard previousTrackEnabled != enabled else { return }
        previousTrackEnabled = enabled
        updateNowPlayingCommandAvailability()
    }

    private func updateNowPlayingCommandAvailability() {
        let cc = MPRemoteCommandCenter.shared()
        let navigationEnabled = nextTrackEnabled || previousTrackEnabled
        cc.skipForwardCommand.isEnabled = !navigationEnabled
        cc.skipBackwardCommand.isEnabled = !navigationEnabled
        cc.nextTrackCommand.isEnabled = nextTrackEnabled
        cc.previousTrackCommand.isEnabled = previousTrackEnabled
    }

    // MARK: - Equalizer

    public func setEqualizerEnabled(_ enabled: Bool) {
        if enabled {
            if equalizer == nil {
                equalizer = VLCAudioEqualizer()
            }
            player?.equalizer = equalizer
        } else {
            player?.equalizer = nil
        }
    }

    public func setEqualizerPreset(_ index: Int) {
        let presets = VLCAudioEqualizer.presets
        guard index >= 0, index < presets.count else { return }
        let eq = VLCAudioEqualizer(preset: presets[index])
        equalizer = eq
        player?.equalizer = eq
    }

    public func setEqualizerPreamp(_ value: Double) {
        let eq = equalizer ?? VLCAudioEqualizer()
        equalizer = eq
        eq.preAmplification = Float(value)
        player?.equalizer = eq
    }

    public func setEqualizerBandGains(_ gains: String) {
        let eq = equalizer ?? VLCAudioEqualizer()
        equalizer = eq
        let values = gains.split(separator: ",").compactMap { Float($0.trimmingCharacters(in: .whitespaces)) }
        for (i, band) in eq.bands.enumerated() where i < values.count {
            band.amplification = values[i]
        }
        player?.equalizer = eq
    }

    public func setVolume(_ value: Int) {
        if value >= 0 {
            player?.audio?.volume = Int32(value)
        }
    }

    public func setMuted(_ muted: Bool) {
        if muted {
            preVolume = Int(player?.audio?.volume ?? 0)
            player?.audio?.volume = 0
        } else {
            player?.audio?.volume = Int32(preVolume)
        }
    }

    public func setRepeat(_ repeatValue: Bool) {
        isRepeat = repeatValue
    }

    public func setAutoAspectRatio(_ auto: Bool) {
        autoAspectRatio = auto
        setNeedsLayout()
    }

    public func setVideoAspectRatio(_ ratio: String?) {
        if !autoAspectRatio, let r = ratio, !r.isEmpty {
            applyAspectRatio(r)
        }
    }

    /// Controls how the video is framed inside the view, mirroring `resizeMode`.
    /// Implemented the way the upstream VLC iOS player does it: `scaleFactor`
    /// zooms to cover the view, `videoAspectRatio` forces a ratio (stretch).
    public func setResizeMode(_ mode: String?) {
        resizeMode = mode ?? "contain"
        applyResizeMode()
        // MobileVLCKit applies these asynchronously, so re-assert on the next
        // runloop tick to ensure the first change always lands.
        DispatchQueue.main.async { [weak self] in
            self?.applyResizeMode()
        }
    }

    private func applyResizeMode() {
        guard let p = player else { return }
        switch resizeMode {
        case "cover", "fill":
            // Zoom to fill the view (matching VLC's "fill to screen").
            p.scaleFactor = coverScaleFactor(viewSize: bounds.size, videoSize: p.videoSize)
            p.videoAspectRatio = nil
            p.videoCropGeometry = nil
        case "stretch":
            // Distort to fill by forcing the container aspect on the video.
            stretchAspect = aspectRatioString(bounds.size) as NSString
            p.scaleFactor = 0
            p.videoAspectRatio = UnsafeMutablePointer(mutating: stretchAspect!.utf8String)
            p.videoCropGeometry = nil
        default:
            // contain / fit / center / original: natural aspect, fitted.
            p.scaleFactor = 0
            p.videoAspectRatio = nil
            p.videoCropGeometry = nil
        }
    }

    private func coverScaleFactor(viewSize: CGSize, videoSize: CGSize) -> Float {
        guard viewSize.width > 0, viewSize.height > 0,
              videoSize.width > 0, videoSize.height > 0 else { return 0 }
        let videoAR = videoSize.width / videoSize.height
        let viewAR = viewSize.width / viewSize.height
        let scale: CGFloat = viewAR >= videoAR
            ? viewSize.width / videoSize.width
            : viewSize.height / videoSize.height
        return Float(scale * UIScreen.main.scale)
    }

    /// VLC crop/aspect geometry wants a `W:H` ratio (e.g. "16:9"), not a bare
    /// decimal like "0.562", which its parser silently ignores.
    private func aspectRatioString(_ size: CGSize) -> String {
        guard size.width > 0, size.height > 0 else { return "16:9" }
        return "\(max(1, Int(size.width.rounded()))):\(max(1, Int(size.height.rounded())))"
    }

    public func snapshot(to path: String) {
        guard let p = player else { onEvent?("Snapshot", ["isSuccess": 0]); return }
        // Accept a `file://` URI (e.g. from expo-file-system) and write to the
        // real filesystem path.
        let filePath = path.hasPrefix("file://")
            ? (URL(string: path)?.path ?? path)
            : path
        // `saveVideoSnapshot` writes asynchronously and its return value does
        // not reliably indicate success, so poll for the file like the
        // official VLC iOS app does (40 attempts x 50 ms) before reporting.
        p.saveVideoSnapshot(at: filePath, withWidth: 0, andHeight: 0)
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let fileManager = FileManager.default
            let maxAttempts = 40
            var attempts = 0
            while !fileManager.fileExists(atPath: filePath), attempts < maxAttempts {
                Thread.sleep(forTimeInterval: 0.05)
                attempts += 1
            }
            let success = fileManager.fileExists(atPath: filePath)
            DispatchQueue.main.async {
                self?.onEvent?("Snapshot", ["isSuccess": success ? 1 : 0])
            }
        }
    }

    public func getMetadata() {
        guard let meta = player?.media?.metaData else { return }

        func s(_ v: String?) -> String { v ?? "" }
        func u(_ v: UInt32) -> String { v > 0 ? String(v) : "" }

        let payload: [String: Any] = [
            "type": "Metadata",
            "title": s(meta.title),
            "artist": s(meta.artist),
            "genre": s(meta.genre),
            "copyright": s(meta.copyright),
            "album": s(meta.album),
            "tracknumber": u(meta.trackNumber),
            "description": s(meta.metaDescription),
            "rating": s(meta.rating),
            "date": s(meta.date),
            "language": s(meta.language),
            "publisher": s(meta.publisher),
            "encodedby": s(meta.encodedBy),
            "trackid": u(meta.trackID),
            "tracktotal": u(meta.trackTotal),
            "director": s(meta.director),
            "season": u(meta.season),
            "episode": u(meta.episode),
            "showname": s(meta.showName),
            "albumartist": s(meta.albumArtist),
            "discnumber": u(meta.discNumber),
            "artwork": meta.artworkURL?.absoluteString ?? "",
        ]
        onEvent?("Metadata", payload)
    }

    // MARK: - VLCMediaPlayerDelegate

    public func mediaPlayerStateChanged(_ aNotification: Notification) {
        guard let p = player else { return }
        switch p.state {
        case .opening:
            onEvent?("Opening", [
                "type": "Opening",
                "currentTime": secondsFromTime(p.time),
                "duration": secondsFromTime(p.media?.length),
            ])
        case .paused:
            isPaused = true
            stopProgressTimer()
            updateNowPlayingInfo()
            onEvent?("Paused", [
                "type": "Paused",
                "currentTime": secondsFromTime(p.time),
                "duration": secondsFromTime(p.media?.length),
            ])
        case .stopped:
            stopProgressTimer()
            clearNowPlayingInfo()
            onEvent?("Stopped", [
                "type": "Stopped",
                "currentTime": secondsFromTime(p.time),
                "duration": secondsFromTime(p.media?.length),
            ])
        case .buffering:
            onEvent?("Buffering", [
                "type": "Buffering",
                "currentTime": secondsFromTime(p.time),
                "duration": secondsFromTime(p.media?.length),
                "isBuffering": !p.isPlaying,
            ])
        case .playing:
            isPaused = false
            startProgressTimer()
            updateNowPlayingInfo()
            onEvent?("Playing", [
                "type": "Playing",
                "currentTime": secondsFromTime(p.time),
                "duration": secondsFromTime(p.media?.length),
            ])
        case .ended:
            if isRepeat {
                p.time = VLCTime(int: 0)
                p.play()
            } else {
                stopProgressTimer()
                updateNowPlayingInfo()
                onEvent?("Ended", [
                    "type": "Ended",
                    "currentTime": secondsFromTime(p.time),
                    "duration": secondsFromTime(p.media?.length),
                ])
            }
        case .error:
            onEvent?("Error", [
                "type": "Error",
                "currentTime": secondsFromTime(p.time),
                "duration": secondsFromTime(p.media?.length),
            ])
            releasePlayer()
        default:
            break
        }
    }

    public func mediaPlayerTimeChanged(_ aNotification: Notification) {
        guard let p = player else { return }
        if progressIntervalMs <= 0 {
            emitProgress()
        }
    }

    // MARK: - VLCMediaDelegate

    public func mediaMetaDataDidChange(_ aMedia: VLCMedia) {
        updateNowPlayingInfo()
    }

    public func mediaDidFinishParsing(_ aMedia: VLCMedia) {
        updateNowPlayingInfo()
    }

    // MARK: - Now playing / background audio

    /// Register the lock-screen and Control Center remote commands and ask the
    /// system for continuous audio (the host app must declare `audio` in
    /// `UIBackgroundModes` for background playback to survive backgrounding).
    private func setupNowPlayingControls() {
        // Register the remote-command targets only once so re-enabling
        // `showNowPlaying` mid-playback does not stack duplicate handlers.
        guard !controlsRegistered else { return }
        controlsRegistered = true

        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)

        let cc = MPRemoteCommandCenter.shared()
        cc.playCommand.addTarget { [weak self] _ in
            self?.play()
            return .success
        }
        cc.pauseCommand.addTarget { [weak self] _ in
            self?.pause()
            return .success
        }
        cc.togglePlayPauseCommand.addTarget { [weak self] _ in
            guard let self = self else { return .commandFailed }
            if self.isPaused { self.play() } else { self.pause() }
            return .success
        }
        cc.skipForwardCommand.preferredIntervals = [30]
        cc.skipForwardCommand.addTarget { [weak self] _ in
            self?.seekRelative(30)
            return .success
        }
        cc.skipBackwardCommand.preferredIntervals = [30]
        cc.skipBackwardCommand.addTarget { [weak self] _ in
            self?.seekRelative(-30)
            return .success
        }
        cc.nextTrackCommand.addTarget { [weak self] _ in
            self?.onEvent?("RequestNext", [:])
            return .success
        }
        cc.previousTrackCommand.addTarget { [weak self] _ in
            self?.onEvent?("RequestPrevious", [:])
            return .success
        }
        cc.changePlaybackPositionCommand.addTarget { [weak self] event in
            guard let e = event as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            self?.seek(to: e.positionTime)
            return .success
        }

        updateNowPlayingCommandAvailability()
    }

    private func seekRelative(_ seconds: Double) {
        guard let p = player else { return }
        let target = Double(secondsFromTime(p.time)) + seconds
        seek(to: target)
    }

    private func updateNowPlayingInfo() {
        guard showNowPlaying, let p = player else { return }
        var info: [String: Any] = [:]
        info[MPNowPlayingInfoPropertyPlaybackRate] = p.isPlaying ? (p.rate == 0 ? 1.0 : Double(p.rate)) : 0.0

        let duration = secondsFromTime(p.media?.length)
        if duration > 0 {
            info[MPMediaItemPropertyPlaybackDuration] = duration
        }
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = secondsFromTime(p.time)

        if let override = nowPlayingOverride {
            let oTitle = stringValue(override["title"])
            let oArtist = stringValue(override["artist"])
            let oAlbum = stringValue(override["album"])
            let oAlbumArtist = stringValue(override["albumArtist"])
            let oGenre = stringValue(override["genre"])
            let oTrack = numberInt(override["trackNumber"])
            let oDisc = numberInt(override["discNumber"])
            let oArtwork = stringValue(override["artwork"])
            if let title = oTitle, !title.isEmpty {
                info[MPMediaItemPropertyTitle] = title
            }
            if let artist = oArtist, !artist.isEmpty {
                info[MPMediaItemPropertyArtist] = artist
            }
            if let album = oAlbum, !album.isEmpty {
                info[MPMediaItemPropertyAlbumTitle] = album
            }
            if let albumArtist = oAlbumArtist, !albumArtist.isEmpty {
                info[MPMediaItemPropertyAlbumArtist] = albumArtist
            }
            if let genre = oGenre, !genre.isEmpty {
                info[MPMediaItemPropertyGenre] = genre
            }
            if oTrack > 0 {
                info[MPMediaItemPropertyAlbumTrackNumber] = oTrack
            }
            if oDisc > 0 {
                info[MPMediaItemPropertyDiscNumber] = oDisc
            }
            if let artwork = oArtwork, !artwork.isEmpty {
                loadOverrideArtwork(artwork) { artworkObj in
                    if let artworkObj = artworkObj {
                        var info = MPNowPlayingInfoCenter.default().nowPlayingInfo ?? [:]
                        info[MPMediaItemPropertyArtwork] = artworkObj
                        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
                    }
                }
            }
        }

        if let meta = p.media?.metaData {
            if info[MPMediaItemPropertyTitle] == nil, let title = meta.title, !title.isEmpty {
                info[MPMediaItemPropertyTitle] = title
            }
            if info[MPMediaItemPropertyArtist] == nil, let artist = meta.artist, !artist.isEmpty {
                info[MPMediaItemPropertyArtist] = artist
            }
            if info[MPMediaItemPropertyAlbumTitle] == nil, let album = meta.album, !album.isEmpty {
                info[MPMediaItemPropertyAlbumTitle] = album
            }
            if info[MPMediaItemPropertyAlbumArtist] == nil, let albumArtist = meta.albumArtist, !albumArtist.isEmpty {
                info[MPMediaItemPropertyAlbumArtist] = albumArtist
            }
            if info[MPMediaItemPropertyGenre] == nil, let genre = meta.genre, !genre.isEmpty {
                info[MPMediaItemPropertyGenre] = genre
            }
            if info[MPMediaItemPropertyAlbumTrackNumber] == nil, meta.trackNumber > 0 {
                info[MPMediaItemPropertyAlbumTrackNumber] = meta.trackNumber
            }
            if info[MPMediaItemPropertyDiscNumber] == nil, meta.discNumber > 0 {
                info[MPMediaItemPropertyDiscNumber] = meta.discNumber
            }
            if nowPlayingOverride?["artwork"] == nil, let artwork = loadArtwork(from: meta.artworkURL) {
                info[MPMediaItemPropertyArtwork] = artwork
            }
        }
        if info[MPMediaItemPropertyTitle] == nil {
            info[MPMediaItemPropertyTitle] = (player?.media?.url?.lastPathComponent ?? "Now Playing")
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func stringValue(_ value: Any?) -> String? {
        if let s = value as? String, !s.isEmpty { return s }
        return nil
    }

    private func numberInt(_ value: Any?) -> Int {
        if let n = value as? NSNumber { return n.intValue }
        return 0
    }

    private func loadOverrideArtwork(_ artwork: String, completion: @escaping (MPMediaItemArtwork?) -> Void) {
        if artwork.hasPrefix("http://") || artwork.hasPrefix("https://") {
            guard let url = URL(string: artwork) else { completion(nil); return }
            URLSession.shared.dataTask(with: url) { data, _, _ in
                guard let data = data, let image = UIImage(data: data) else {
                    DispatchQueue.main.async { completion(nil) }
                    return
                }
                let artworkObj = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
                DispatchQueue.main.async { completion(artworkObj) }
            }.resume()
        } else if artwork.hasPrefix("data:") {
            guard let comma = artwork.range(of: ","),
                  let data = Data(base64Encoded: String(artwork.suffix(from: comma.upperBound))),
                  let image = UIImage(data: data) else {
                completion(nil)
                return
            }
            completion(MPMediaItemArtwork(boundsSize: image.size) { _ in image })
        } else {
            let url = artwork.hasPrefix("file://") ? URL(string: artwork) : URL(fileURLWithPath: artwork)
            completion(loadArtwork(from: url))
        }
    }

    private func loadArtwork(from url: URL?) -> MPMediaItemArtwork? {
        guard let url = url else { return nil }
        let fileURL: URL? = url.isFileURL ? url : nil
        guard let path = fileURL?.path ?? (url.scheme == nil ? url.path : nil) else { return nil }
        guard let image = UIImage(contentsOfFile: path) else { return nil }
        return MPMediaItemArtwork(boundsSize: image.size) { _ in image }
    }

    private func clearNowPlayingInfo() {
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    // MARK: - Helpers

    private func startProgressTimer() {
        stopProgressTimer()
        guard progressIntervalMs > 0 else { return }
        let interval = TimeInterval(progressIntervalMs) / 1000.0
        let timer = Timer(timeInterval: interval, repeats: true) { [weak self] _ in
            self?.emitProgress()
        }
        RunLoop.main.add(timer, forMode: .common)
        progressTimer = timer
    }

    private func stopProgressTimer() {
        progressTimer?.invalidate()
        progressTimer = nil
    }

    private func emitProgress() {
        guard let p = player else { return }
        let current = secondsFromTime(p.time)
        let duration = secondsFromTime(p.media?.length)
        // Emit even when the duration is unknown (e.g. live streams), matching
        // Android. The original `current < duration` guard dropped all progress
        // for streams with `duration == 0`.
        if current >= 0 {
            onEvent?("Progress", ["currentTime": current, "duration": duration])
        }
        updateNowPlayingInfo()
    }

    /// MobileVLCKit reports times in milliseconds via `VLCTime.intValue`.
    private func secondsFromTime(_ time: VLCTime?) -> Int {
        Int(time?.intValue ?? 0) / 1000
    }

    private func jsonString(_ items: [[String: Any]]) -> String {
        guard let data = try? JSONSerialization.data(withJSONObject: items) else { return "[]" }
        return String(data: data, encoding: .utf8) ?? "[]"
    }

    /// MobileVLCKit expects a C string for the video aspect ratio, the setter
    /// copies the value, so a transient buffer is safe here (mirrors the ObjC
    /// `cStringUsingEncoding` usage).
    private func applyAspectRatio(_ ratio: String) {
        let cString = Array(ratio.utf8CString)
        cString.withUnsafeBufferPointer { buf in
            player?.videoAspectRatio = UnsafeMutablePointer(mutating: buf.baseAddress)
        }
    }

    public func releasePlayer() {
        stopProgressTimer()
        clearNowPlayingInfo()
        if let p = player {
            p.stop()
            player = nil
        }
    }
}
