# CoWatch Multi-Display Media Concepts

Tài liệu này gom 5 nhóm khái niệm cần nắm để thiết kế app CoWatch theo hướng: một màn host là player chính, các màn phụ chỉ hiển thị video theo trạng thái do host phát ra. Nguồn chính là tài liệu Android/Media3 official; với các claim kỹ thuật lõi, nên ưu tiên official docs hơn Medium hoặc StackOverflow vì API media/display thay đổi theo version Android và Media3.

## 1. Android multi-display: Display, Activity trên màn phụ, Presentation

### Khái niệm chính

Multi-display là khả năng Android quản lý nhiều màn hình cùng lúc. Một màn có thể là màn chính, màn HDMI, màn trong xe, màn phụ do hệ thống tạo, hoặc virtual display tùy thiết bị. Trong app CoWatch, multi-display không có nghĩa là clone UI mặc định; app cần quyết định sẽ mở màn phụ bằng `Activity`, `Presentation`, hay để hệ thống mirror.

`DisplayManager` là API để biết các display đang gắn vào thiết bị. Tài liệu Android mô tả nó là lớp quản lý thuộc tính của các display đang attached. Khi thiết kế CoWatch, lớp kiểu `DisplayRepository` nên gom logic quét display, loại bỏ màn host, và trả về danh sách màn có thể share.

`Display` là đối tượng đại diện cho từng màn. Mỗi display có `displayId`, tên, mode, flags, kích thước, density. Trong CoWatch, `displayId` là danh tính runtime để biết receiver nào thuộc màn nào. Không nên coi `displayId` là dữ liệu bền vững qua mọi lần chạy app; nó là id do hệ thống cung cấp trong phiên hiện tại.

`ActivityOptions.setLaunchDisplayId(displayId)` cho phép app yêu cầu mở một `Activity` trên display cụ thể. Đây là hướng CoWatch đang dùng: host ở `FrontPlayerActivity`, còn mỗi màn phụ được mở bằng một `ReceiverActivity` riêng.

`Presentation` là một dạng dialog đặc biệt để trình bày nội dung trên display khác. Nó hợp khi activity chính vẫn quản lý UI, còn màn phụ chỉ là output phụ thuộc activity chính. Android docs cũng nhắc rằng `Presentation` có context riêng, cần inflate layout bằng context của nó để asset/density đúng display. Presentation tự bị cancel khi display gắn với nó bị remove hoặc task bị remove.

### Các mô hình triển khai

Mô hình A: mở `ReceiverActivity` riêng trên màn phụ.

```text
FrontPlayerActivity
  -> ActivityOptions.setLaunchDisplayId(displayId)
  -> ReceiverActivity on display X
```

Ưu điểm:
- Tách lifecycle từng màn rõ ràng.
- Receiver có thể có layout riêng.
- Phù hợp với thiết kế receiver video-only.
- Dễ đóng từng receiver khi session thay đổi.

Nhược điểm:
- Mỗi receiver thường có player riêng nếu dùng Media3 đơn giản.
- Cần tự quản session và display removal.

Mô hình B: dùng `Presentation`.

```text
Host Activity
  -> Presentation(display)
  -> View riêng trên secondary display
```

Ưu điểm:
- Màn phụ phụ thuộc rõ vào activity chính.
- Hợp với UI phụ đơn giản.

Nhược điểm:
- Vẫn phải tự render nội dung lên presentation.
- Nếu muốn phát video bằng ExoPlayer riêng trên presentation thì vẫn gặp bài toán sync tương tự.
- Nếu muốn mirror frame thật mượt thì presentation không tự giải quyết được buffer/video pipeline.

Mô hình C: mirror hệ thống/OEM.

```text
System display policy
  -> mirror host display to receiver display
```

Ưu điểm:
- Timeline hình ảnh giống host vì là mirror.
- App ít phải sync.

Nhược điểm:
- Có thể mirror cả controller/UI host, không chỉ video.
- Phụ thuộc thiết bị, ROM, AAOS/OEM policy.
- App thường không kiểm soát portable được trên mọi máy.

### Áp dụng vào CoWatch

CoWatch hiện hợp với mô hình A: host mở `ReceiverActivity` riêng trên màn phụ. Đây là lựa chọn thực tế vì app muốn receiver chỉ render video, không có controller, không phát audio, không được ghi command playback ngược lại.

Điểm cần quyết định sớm:
- Receiver nên là `Activity` riêng hay `Presentation`.
- Receiver có player riêng hay chỉ nhận frame/texture từ host.
- Có cần mirror nguyên host UI không.
- Khi display bị remove, session đóng receiver nào.
- Khi host pause/stop/destroy, receiver nên giữ hay finish.

### Nguồn tham khảo

- Android Developers: `DisplayManager` API - https://developer.android.com/reference/android/hardware/display/DisplayManager
- Android Developers: `ActivityOptions.setLaunchDisplayId` - https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)
- Android Developers: `Presentation` API - https://developer.android.com/reference/android/app/Presentation

## 2. Media3/ExoPlayer pipeline: Player, PlayerView, Surface, tracks

### Khái niệm chính

Media3 là bộ thư viện media hiện đại của Android. `ExoPlayer` là implementation phổ biến của interface `Player`. Trong CoWatch, `ExoPlayer` là engine phát media, còn `PlayerView` là view UI giúp gắn player vào layout.

Một pipeline phát video có thể nhìn đơn giản như sau:

```text
MediaItem
  -> DataSource / Extractor
  -> Decoder
  -> Video renderer -> Surface trong PlayerView
  -> Audio renderer -> Audio output
```

`MediaItem` là mô tả source media. Nó có thể là file local, raw resource, URI mạng, DASH/HLS, v.v.

`ExoPlayer.Builder(context).build()` tạo instance player. Android docs nhấn mạnh player nên được access từ một application thread nhất quán, thường là main thread, đặc biệt khi dùng UI components.

`PlayerView` là UI component có sẵn trong `media3-ui`. Theo docs, `PlayerView` chứa `PlayerControlView`, `SubtitleView`, và một `Surface` để render video. Vì vậy `PlayerView` không chỉ là "khung hiển thị"; nó còn có controller UI nếu bật.

`Surface` là nơi video frame được render. Đây là lý do một player không tự nhiên render sạch sang nhiều view độc lập theo API thông thường. Nếu muốn một decode path render ra nhiều surface, thiết kế sẽ đi xuống tầng custom renderer/GL/EGL, không còn là dùng `PlayerView` đơn giản.

`TrackSelectionParameters` điều khiển chọn track. Track có thể là video, audio, text/subtitle. Với receiver video-only, app có thể disable audio track:

```kotlin
player.trackSelectionParameters = player.trackSelectionParameters
    .buildUpon()
    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
    .build()
```

`volume = 0f` khác với disable audio track. `volume = 0f` mute output; audio track vẫn có thể được chọn và decode. Disable audio track nói với player không chọn track audio cho media items.

### Áp dụng vào CoWatch

Host player:
- Có audio và video.
- Có `PlayerView` controller.
- Nhận thao tác người dùng: play, pause, seek, speed.
- Ghi intent playback vào state chung.

Receiver player:
- Có thể vẫn dùng `ExoPlayer` riêng để render video.
- Tắt controller bằng `app:use_controller="false"`.
- Tắt audio bằng `volume = 0f` và disable audio track.
- Chỉ apply state từ host, không phát command ngược.

Mô hình này decode video nhiều lần, nhưng code đơn giản, dùng đúng API public của Media3, dễ debug hơn custom buffer fan-out.

### Edge cases cần nhớ

- Video network có thể buffer khác nhau giữa host và receiver.
- Receiver prepare chậm hơn host thì cần cơ chế ready/anchor.
- Nếu media có nhiều audio/subtitle track, receiver cần policy rõ: disable audio, subtitle có hiển thị không.
- Nếu dùng DRM/HDR/codec đặc biệt, mỗi display/player có thể có hạn chế khác nhau.
- Nếu release player không đúng lifecycle, có thể giữ decoder/surface/audio resource quá lâu.

### Nguồn tham khảo

- Android Developers: Media3 ExoPlayer getting started - https://developer.android.com/media/media3/exoplayer/hello-world
- Android Developers: `PlayerView` trong Media3 UI - https://developer.android.com/media/media3/exoplayer/hello-world#attach-the-player-to-a-view
- Android Developers: Media3 track selection - https://developer.android.com/media/media3/exoplayer/track-selection

## 3. Player authority và shared playback state

### Khái niệm chính

Player authority là quyết định kiến trúc: thành phần nào có quyền ra lệnh playback, thành phần nào chỉ nghe và render. Đây không phải một API Android có sẵn; đây là rule của app.

Với CoWatch, rule nên là:

```text
Host = authority
Receiver = passive renderer
PlaybackManager = source of truth cho playback intent/state
```

Host có quyền:
- play
- pause
- seek
- đổi speed
- bật/tắt broadcast
- quyết định media source

Receiver không có quyền:
- không hiện controller
- không nhận input playback từ user
- không gọi `PlaybackManager.playAt()`, `pauseAt()`, `seekTo()`
- không phát audio

Shared playback state là dữ liệu nhỏ mô tả ý định playback, không phải video buffer. Ví dụ:

```kotlin
data class SharedPlaybackState(
    val mediaUrl: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val playbackSpeed: Float,
    val updatedAtElapsedMs: Long,
    val startAtElapsedRealtimeMs: Long?
)
```

State này trả lời các câu hỏi:
- Đang phát media nào.
- Đang play hay pause.
- Mốc timeline quan trọng là gì.
- Playback speed là bao nhiêu.
- Có scheduled start không.

Trong kiến trúc Android, đây gần với tư duy UDF: state đi xuống UI, event đi ngược lên state holder. Android docs mô tả UDF là mô hình state flows down, events flow up. Với CoWatch, chỉ host được phép gửi event playback lên state holder. Receiver chỉ consume state đi xuống.

Single source of truth nghĩa là trạng thái playback chung phải có một nguồn đáng tin. Android architecture docs dùng khái niệm source of truth cho repository/data. Trong CoWatch, `PlaybackManager` đang đóng vai trò source of truth in-memory cho playback session demo. Khi app lớn hơn, có thể đổi nó thành repository hoặc service, nhưng nguyên tắc vẫn là một nguồn chính.

### Vì sao không để receiver điều khiển

Nếu receiver cũng có quyền ghi command, app sẽ gặp các race:
- Host vừa pause, receiver vừa play.
- Receiver seek vì người dùng chạm nhầm, host vẫn nghĩ timeline cũ.
- Nhiều receiver cùng phát command, không biết command nào thắng.
- UI host không phản ánh đúng authority thực tế.

Vì vậy receiver nên chỉ có local player operation để render:

```text
SharedPlaybackState -> ReceiverActivity.applySharedState() -> local ExoPlayer
```

Các operation local như `player.play()`, `player.pause()`, `player.seekTo()` trong receiver không nên được hiểu là receiver có quyền điều khiển session. Chúng chỉ là cách receiver render state đã được host quyết định.

### Áp dụng vào code CoWatch

Thiết kế nên giữ ranh giới:

```text
FrontPlayerActivity
  -> FrontPlayerViewModel
  -> PlaybackManager

ReceiverActivity
  -> ReceiverPlayerViewModel
  -> observe PlaybackManager.state
```

Nếu sau này thêm nút trên receiver, cần phân loại:
- Nút UI local như hide overlay: được phép.
- Nút playback như play/pause/seek: không được phép, trừ khi đổi mô hình authority.

### Nguồn tham khảo

- Android Developers: UI layer architecture và UDF - https://developer.android.com/topic/architecture/ui-layer
- Android Developers: state holders and UI state - https://developer.android.com/topic/architecture/ui-layer/stateholders
- Android Developers: source of truth trong data layer - https://developer.android.com/topic/architecture/data-layer#source-of-truth

## 4. Timeline sync: anchor, scheduled start, seek event, drift correction

### Khái niệm chính

Timeline sync là làm các player cùng phát ở cùng mốc thời gian media, ví dụ cùng ở giây 35. Frame sync là cùng hiển thị đúng cùng frame tại cùng thời điểm vật lý. Hai khái niệm này khác nhau.

```text
Timeline sync:
  Host position = 35_000ms
  Receiver position = 35_000ms

Frame-perfect sync:
  Host và receiver cùng scan/render đúng frame đó cùng lúc
```

Với nhiều `ExoPlayer` riêng, CoWatch chủ yếu đạt timeline sync tương đối. Frame-perfect sync cần pipeline sâu hơn như single decoder, shared texture, GL/EGL fan-out, hoặc hỗ trợ hệ thống/OEM.

`anchorPositionMs` là mốc ban đầu khi bắt đầu share. Nếu host đang ở `35_000ms`, receiver phải prepare xong rồi seek tới `35_000ms` trước khi báo ready. Nếu không có anchor, receiver dễ bắt đầu từ `0ms` trong khi host đang ở giữa video.

`startAtElapsedRealtimeMs` là mốc thời gian hệ thống để start đồng loạt trong tương lai. Thay vì host play ngay khi receiver vừa ready, session có thể đặt lịch:

```text
T0: tất cả receiver ready
T0 + 1500ms: host và receiver cùng play local
```

Mục tiêu là giảm lệch do receiver A ready trước receiver B.

Seek event là sự kiện người dùng tua trên host. Media3 docs nói `Player.seekTo` tạo callback `onPositionDiscontinuity` với reason `DISCONTINUITY_REASON_SEEK`. Với CoWatch, nếu muốn app nhẹ nhưng vẫn đúng khi tua, hướng hợp lý là chỉ publish seek khi host thật sự seek, không publish position mỗi 500ms.

Drift correction là cơ chế định kỳ đo lệch và sửa. Ví dụ cũ:

```kotlin
if (abs(player.currentPosition - state.positionMs) > 250L) {
    player.seekTo(state.positionMs)
}
```

Nó giúp receiver kéo về gần host nếu lệch lâu dần, nhưng tốn state emission, tốn check, và có thể gây giật nếu seek quá thường xuyên. Với app nhẹ, có thể tạm bỏ correction định kỳ và chỉ giữ:
- initial anchor
- scheduled start
- event-based play/pause/seek/speed

### Các cấp độ sync

Cấp 1: sync lúc bắt đầu.

```text
Start sharing -> receiver seek to anchor -> scheduled start
```

Nhẹ nhất, nhưng nếu tua trong lúc đang share mà không publish seek event thì receiver không theo.

Cấp 2: event-based sync.

```text
Host play/pause/seek/speed -> publish event/state -> receiver apply once
```

Đây là điểm cân bằng tốt cho CoWatch hiện tại.

Cấp 3: periodic correction.

```text
Host publish position định kỳ -> receiver đo lệch -> seek nếu vượt tolerance
```

Đúng timeline hơn, nhưng nặng hơn và dễ gây giật nếu làm thô.

Cấp 4: frame sync/custom renderer.

```text
Host decode one stream -> render/copy texture to multiple surfaces
```

Đây là hướng phức tạp, không nên triển khai nếu mục tiêu chỉ là demo hoặc app nhẹ.

### Áp dụng vào CoWatch sau khi bỏ tolerance

Sau khi bỏ `SYNC_SEEK_TOLERANCE_MS`, app nhẹ hơn vì không còn ticker position định kỳ và không còn check lệch liên tục. Nhưng cần hiểu tradeoff:

- Receiver vẫn bắt đầu đúng anchor khi share.
- Scheduled start vẫn giúp các màn cùng bắt đầu.
- Receiver không tự sửa drift theo thời gian.
- Nếu chưa thêm event-based seek, thao tác tua host sau khi share có thể không kéo receiver theo.

Thiết kế tiếp theo nên là: thêm seek event nhẹ nếu cần. Nghĩa là host chỉ gọi `PlaybackManager.seekTo(positionMs)` khi user seek thật, receiver nhận state seek đó và `player.seekTo(positionMs)` một lần. Không cần quay lại ticker 500ms.

### Nguồn tham khảo

- Android Developers: Media3 player events - https://developer.android.com/media/media3/exoplayer/listening-to-player-events
- Android Developers: seeking callback behavior - https://developer.android.com/media/media3/exoplayer/listening-to-player-events#seeking
- Android Developers: ExoPlayer threading note - https://developer.android.com/media/media3/exoplayer/hello-world#create-the-player

## 5. Lifecycle, resource ownership, performance và failure cases

### Khái niệm chính

Multi-display media app không chỉ là phát video. Nó là quản lý nhiều resource sống theo lifecycle khác nhau:

```text
Activity lifecycle
Display lifecycle
Player lifecycle
Session lifecycle
Surface lifecycle
Coroutine/Flow collection lifecycle
```

Activity lifecycle có các callback chính: `onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`. Android docs nhấn mạnh lifecycle đúng giúp tránh crash, tránh tiêu hao resource khi app không active, và giữ progress khi user rời app rồi quay lại.

`onCreate` thường setup view, ViewModel, player. `onStop` có thể lưu state nhẹ như current position. `onDestroy` phải release player nếu player thuộc activity đó. Trong CoWatch, `ReceiverActivity.onDestroy()` release receiver player là đúng vì receiver sở hữu player local.

Display lifecycle là chuyện màn phụ có thể xuất hiện, thay đổi, hoặc bị remove. `DisplayManager.DisplayListener` có callback cho display added/changed/removed. Nếu display bị remove, receiver liên quan nên đóng hoặc session manager phải loại display đó khỏi session.

Session lifecycle là vòng đời broadcast:

```text
No session
  -> preparing share
  -> receiver launched
  -> receiver ready
  -> playing shared
  -> stop sharing / display removed / host destroyed
  -> no session
```

Player lifecycle là vòng đời decoder/surface/audio output. Mỗi `ExoPlayer` giữ resource tương đối nặng. Nếu mỗi màn phụ có một player riêng, app cần release chắc chắn khi receiver finish. Nếu không, app có thể giữ decoder, memory, surface, hoặc audio resource không cần thiết.

Flow/coroutine lifecycle là chuyện observe state. Nếu receiver collect state khi activity đã destroy hoặc không visible, có thể gây leak hoặc update UI/player sai thời điểm. Với View-based Activity, `lifecycleScope` tự cancel khi lifecycle destroy, nhưng nếu muốn tiết kiệm hơn khi stopped/started thì cân nhắc `repeatOnLifecycle`.

### Performance checklist

CPU/GPU:
- Mỗi receiver player decode video riêng.
- Nếu video độ phân giải cao và nhiều màn phụ, thiết bị yếu có thể drop frame.
- Disable audio track trên receiver để giảm việc không cần thiết.

Memory:
- Mỗi player có buffer riêng.
- Video source network có thể cache/buffer riêng.
- Release player trong `onDestroy`.

Battery/thermal:
- Multi-display video decode liên tục có thể nóng máy.
- Ticker position định kỳ làm state update nhiều hơn, đã bỏ để nhẹ hơn.

Latency:
- Receiver prepare và buffer chậm hơn host.
- Scheduled start giảm lệch ban đầu nhưng không giải quyết mọi drift.
- Network source dễ lệch hơn local resource.

Robustness:
- Không assume luôn có màn phụ.
- Không assume launch receiver luôn thành công.
- Không assume displayId còn valid sau khi display thay đổi.
- Không để receiver ghi playback state nếu authority là host.

### Failure cases nên thiết kế trước

Không có màn phụ:
- Tắt broadcast switch.
- Hiện message ngắn.

Một trong nhiều receiver launch fail:
- Chỉ giữ các display launch thành công.
- Nếu không display nào launch được thì hủy session.

Receiver prepare chậm:
- Session ở trạng thái preparing.
- Host pause tại anchor.
- Chỉ scheduled start khi receiver ready.

Display bị remove:
- Receiver finish.
- Session manager loại display đó.
- Nếu không còn receiver thì stop sharing.

Host bị stop/destroy:
- Lưu state hiện tại nếu cần.
- Stop sharing nếu app không muốn receiver tiếp tục.
- Release host player đúng lifecycle.

User tua khi đang share:
- Nếu muốn nhẹ nhưng đúng, publish event seek một lần.
- Receiver apply seek một lần.
- Không cần position ticker định kỳ.

### Nguồn tham khảo

- Android Developers: Activity lifecycle - https://developer.android.com/guide/components/activities/activity-lifecycle
- Android Developers: `Presentation` lifecycle/display removal notes - https://developer.android.com/reference/android/app/Presentation
- Android Developers: Lifecycle-aware coroutines - https://developer.android.com/topic/libraries/architecture/coroutines

