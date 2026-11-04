import AVKit
import SwiftUI

/// Apple's own player, so the Siri Remote, AirPlay, picture in picture, HDR and frame-rate
/// matching all work as people expect. Progress is saved every 10 seconds and on the way out,
/// with the same "watched" rule as the Android app (the core decides).
struct PlayerScreen: UIViewControllerRepresentable {
    let request: PlayRequest
    let model: AppModel

    func makeCoordinator() -> Coordinator { Coordinator(model: model, item: request.item) }

    func makeUIViewController(context: Context) -> AVPlayerViewController {
        let options: [String: Any]? = request.headers.isEmpty
            ? nil
            : ["AVURLAssetHTTPHeaderFieldsKey": Dictionary(request.headers.map { ($0.name, $0.value) }, uniquingKeysWith: { a, _ in a })]
        let asset = AVURLAsset(url: request.url, options: options)
        let playerItem = AVPlayerItem(asset: asset)
        #if os(tvOS)
        playerItem.externalMetadata = [Self.meta(.commonIdentifierTitle, request.item.title)]
        #endif
        let player = AVPlayer(playerItem: playerItem)
        let vc = AVPlayerViewController()
        vc.player = player
        #if os(tvOS)
        vc.appliesPreferredDisplayCriteriaAutomatically = true
        #else
        vc.allowsPictureInPicturePlayback = true
        #endif
        context.coordinator.attach(player)
        if request.startMs > 0 {
            player.seek(to: CMTime(value: CMTimeValue(request.startMs), timescale: 1000), toleranceBefore: .zero, toleranceAfter: .positiveInfinity)
        }
        player.play()
        return vc
    }

    func updateUIViewController(_ vc: AVPlayerViewController, context: Context) {}

    static func dismantleUIViewController(_ vc: AVPlayerViewController, coordinator: Coordinator) {
        coordinator.finish()
        vc.player?.pause()
        vc.player = nil
    }

    private static func meta(_ id: AVMetadataIdentifier, _ value: String) -> AVMetadataItem {
        let m = AVMutableMetadataItem()
        m.identifier = id
        m.value = value as NSString
        m.extendedLanguageTag = "und"
        return m
    }

    final class Coordinator {
        let model: AppModel
        let item: PlayItem
        private weak var player: AVPlayer?
        private var observer: Any?
        private var endObserver: NSObjectProtocol?

        init(model: AppModel, item: PlayItem) {
            self.model = model
            self.item = item
        }

        func attach(_ player: AVPlayer) {
            self.player = player
            observer = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 10, preferredTimescale: 1), queue: .main) { [weak self] _ in
                self?.save()
            }
            endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: player.currentItem, queue: .main) { [weak self] _ in
                self?.save(atEnd: true)
            }
        }

        func finish() {
            save()
            if let observer, let player { player.removeTimeObserver(observer) }
            if let endObserver { NotificationCenter.default.removeObserver(endObserver) }
            observer = nil
            endObserver = nil
        }

        private func save(atEnd: Bool = false) {
            guard let player, let current = player.currentItem else { return }
            let duration = current.duration.seconds
            guard duration.isFinite, duration > 0 else { return }
            let position = atEnd ? duration : player.currentTime().seconds
            guard position.isFinite, position > 0 else { return }
            let model = self.model
            let item = self.item
            let pos = UInt64(position * 1000)
            let dur = UInt64(duration * 1000)
            Task { @MainActor in model.saveProgress(item, positionMs: pos, durationMs: dur) }
        }
    }
}
