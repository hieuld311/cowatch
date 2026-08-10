# CoWatch UI component and typography inventory

> Source snapshot: `extended` worktree, 2026-08-10. This is a source inventory, not a rendered-device specification. Pixel (`px`) values converted with `LocalDensity.toDp()` are density-dependent at runtime; they are intentionally retained as `px` below.

## 1. Scope and screen ownership

| APK / flavor | Screen | Root composable / Activity | Included UI |
|---|---|---|---|
| CID | Library | `cid/ui/VideoLibraryActivity` → `cid/ui/library/VideoLibraryScreen` | Vertical video rail, focused title, PiP, Explorer background |
| CID | Player | `cid/ui/FrontPlayerActivity` → `cid/ui/player/FrontPlayerScreen` | Local video surface, common close control, common playback controls |
| PID | Library | `pid/ui/VideoLibraryActivity` → `common/ui/pidlibrary/VideoLibraryScreen` | Horizontal rail, rail progress, focused title, PiP, Explorer background |
| PID | Player | `pid/ui/FrontPlayerActivity` → `pid/ui/player/FrontPlayerScreen` | Fanout video surface, common controls, broadcast control, share dialog, notification |
| Rear Left / Rear Right | Library | `rear/ui/VideoLibraryActivity` → `common/ui/pidlibrary/VideoLibraryScreen` | Same visual library as PID; can overlay the receiver dialog |
| Rear Left / Rear Right | Local player | `rear/ui/FrontPlayerActivity` | Local surface, common controls and title, receiver dialog when requested |
| Rear Left / Rear Right | Shared player | `rear/ui/FrontPlayerActivity` | Fanout surface, passive/read-only common control bar, independent five-second overlay timer |
| Rear Left / Rear Right | Cold-start consent | `rear/ui/ReceiverConsentActivity` | Receiver dialog only |

Excluded: Android permission dialogs, Android status/navigation bars, Toast rendering, and video/image pixels supplied by external media assets. Their visual style is controlled by Android/SystemUI or bitmap assets, not Compose text/style declarations.

## 2. Typography tokens

`CoWatchTheme` starts from Material 3 `Typography`, uses `FontFamily.Monospace` for every style, makes `headlineSmall` and `bodySmall` bold, and otherwise preserves Material 3 defaults.

| Token used by UI | Font | Weight | Size / line height | Used for |
|---|---|---:|---:|---|
| `headlineSmall` | Monospace | Bold | 24sp / 32sp | Video title, player title, dialog heading, receiver title, receiver actions |
| `titleMedium` | Monospace | Medium | 16sp / 24sp | PID target row, PID dialog actions, receiver countdown |
| `bodyLarge` | Monospace | Normal | 16sp / 24sp | PID no-display state |
| `bodySmall` | Monospace | Bold | 12sp / 16sp | Unfocused PID/Rear rail-card title |
| `labelLarge` | Monospace | Medium | 14sp / 20sp | PID broadcast notification |
| `labelMedium` | Monospace | Medium | 12sp / 16sp | Seek-preview timestamp |
| `labelSmall` | Monospace | Medium | 11sp / 16sp | Playback elapsed/total time |

No visible Compose text uses a literal `fontSize`; all visible Compose text resolves through the tokens above.

## 3. Text-color tokens

`CoWatchTheme` selects the following palette from system dark mode. The table records all semantic colors used by visible text.

| Semantic token | Dark mode | Light mode | Visible text using it |
|---|---:|---:|---|
| `contentPrimary` | `#E6EDF3` | `#17212B` | Player title, seek preview time, dialog heading, unfocused PID/Rear card title |
| `contentPrimary` at 70% alpha | `#E6EDF3B3` | `#17212BB3` | Playback elapsed and total time |
| `libraryTitle` | `#FFFFFFFF` | `#17212B` | CID/PID/Rear focused-video title |
| `dialogAccent` | `#00F9EC` | `#00A86B` | Selected PID display row; Rear broadcast video title |
| `dialogNormalText` | `#C7CADA` | `#3F4A55` | Unselected PID display row; Rear countdown |
| `dialogDisabledText` | `#838497` | `#77808A` | Disabled PID target row; no-secondary-display message; disabled Broadcast action |
| `dialogActionContent` | `#FFFFFFFF` | `#FFFFFFFF` | PID Cancel/Broadcast and Rear Dismiss/Accept action labels |
| `broadcastNotificationText` | `#DCE8F2` | `#15382A` | PID host broadcast notification |

All other apparent text-like marks are bitmap icons: speed (`1X`, `1.5X`, `2X`), transport glyphs, checkbox artwork and broadcast artwork. Their colors are embedded in the corresponding drawable, not declared as Compose text color.

## 4. Shared visual primitives

| Component | Dimensions / layout | Colors / assets | Corner radius / border | Text |
|---|---|---|---|---|
| `ExplorerBackground` | Fills its parent | Dynamic selected-video thumbnail plus `img_media_background_list_video` (CID) or `img_passenger_launcher_background` (PID/Rear); placeholder `libraryPlaceholder` (`#70879B` dark / `#B7C8D6` light) | None declared | None |
| `VideoThumbnail` | Fills caller area; crop by default | Dynamic decoded thumbnail; same placeholder token while loading | Inherited from caller | No visible text; content description is dynamic video title when supplied |
| `PrimaryPlaybackButton` | Default 124dp square; default glyph 50dp; actual click target is glyph area | `img_button_play_background_[n/p/d]`, play/pause icon assets | Asset-defined | No visible text; a11y: `Play video` or `Pause video` |
| `PressStateIconButton` | Default 124dp layout; default icon/click target 40dp | Normal/pressed/disabled icon assets; 90ms crossfade, 0.98 press scale | Asset-defined | No visible text; a11y supplied per use |
| Library PiP | Caller-owned video rectangle; center play; Expand and Close at lower corners, 40dp icon/layout with 12dp inset | Video surface plus common icon assets | No shape declared | No visible text; a11y: `Expand player`, `Close PiP player` |
| Player close | 64dp layout, 40dp glyph, top-right 12dp inset | `ico_general_close_[n/p]` | Asset-defined | No visible text; a11y: `Close player` |

## 5. CID Library

Source: `app/src/cid/java/com/ivi/cid/ui/library/VideoLibraryScreen.kt` and `VerticalVideoRail.kt`.

| Component | Dimensions / placement | Colors / assets | Bo góc / border | Visible text |
|---|---|---|---|---|
| Screen background | Full screen | `ExplorerBackground`; overlay asset `img_media_background_list_video` | None | None |
| Vertical focused card | `604px × 304px`; left-aligned in rail | Dynamic thumbnail | 8dp; 1dp `focusedVideoOutline` (`#05F0EC` dark / `#00A86B` light) | None |
| Vertical side cards | `398px × 224px`; interpolated while dragging | Dynamic thumbnail | 8dp; no border | None |
| Focused-card play | Bottom-start, offset `x=20dp`, `y=-20dp` | Shared primary play control | Bitmap-defined | None |
| Focused-video title | Bottom-end; `end=28dp`, `bottom=36dp`; max 2 lines | `libraryTitle` | None | Dynamic `video.title`; `headlineSmall`, 24sp bold |
| In-app PiP | Top-end; `top=56dp`, `end=28dp`; `398px × 224px` | Live `CIDPlayerSurface` and shared PiP controls | Surface shape not declared | No visible text |

CID Library intentionally does not render titles for side cards.

## 6. PID and Rear Library

Source: `app/src/main/java/com/ivi/common/ui/pidlibrary/VideoLibraryScreen.kt`, `VideoRail.kt`, `VideoRailCard.kt`.

| Component | Dimensions / placement | Colors / assets | Bo góc / border | Visible text |
|---|---|---|---|---|
| Screen background | Full screen | `ExplorerBackground`; overlay asset `img_passenger_launcher_background` | None | None |
| Focused-video title | Above rail; horizontal padding `55px` converted to dp; bottom 12dp; one line | `libraryTitle` | None | Dynamic `video.title`; `headlineSmall`, 24sp bold |
| Horizontal focused card | `604px × 340px`; rail top padding 12dp | Dynamic thumbnail | 8dp; 1dp `focusedVideoOutline` when focused | None |
| Horizontal side card | `396px × 223px`; 16dp card gap | Dynamic thumbnail | 8dp; no border | Dynamic `video.title`; `contentPrimary`; `bodySmall`, 12sp bold; top inset 8dp; max one line |
| Focused-card play | Bottom-start inside selected card | Shared primary play control | Bitmap-defined | None |
| Rail gesture / motion | Horizontal drag; 320ms focus settle | N/A | N/A | None |
| Rail progress | Bottom of screen; side insets `55px`; height 6dp; 420ms animation | `img_general_progress_bar_track` + `img_general_progress_bar_filled_track` | Asset-defined | None |
| In-app PiP | Top-end; `top=56dp`, `end=28dp`; `396px × 223px` | PID: fanout surface; Rear: Media3 surface; shared PiP controls | Surface shape not declared | None |

Rear Left and Rear Right use this exact Library UI source. PID differs only in its PiP surface implementation.

## 7. Common local-player controls

Source: `app/src/main/java/com/ivi/common/ui/player/PlayerScreenFrame.kt`, `PlaybackControlBar.kt`, `VideoSeekBar.kt`, and `SeekFramePreview.kt`.

| Component | Dimensions / placement | Colors / assets | Bo góc / border | Visible text |
|---|---|---|---|---|
| Player frame | Full screen, video beneath overlays | `playerCanvas`: `#000000` in both modes | None | None |
| Close control | Top-end, 12dp inset; 64dp layout / 40dp glyph | Common close assets | Asset-defined | None |
| Control-bar container | Bottom; total height 154dp; background layer height 134dp | CID/Rear: `img_media_control_background`; PID: `img_media_passenger_control_background` | Asset-defined | None |
| Timeline overlay | At top of control-bar container; height 48dp | Track/filled-track assets | Asset-defined | None |
| Seek track | Visual height 40dp; track 6dp | `img_general_progress_bar_track`, `img_general_progress_bar_filled_track` | Asset-defined | None |
| Seek handle | 40dp | `img_general_slider_handle_[n/p]` | Asset-defined | None |
| Seek interaction strip | 16dp high, centered on track | Transparent interaction area | None | None |
| Transport layout | Fixed 124dp slots; center Play stays geometrically centered; non-primary glyphs 40dp | Speed, previous, next and collapse bitmap assets | Asset-defined | No visible text |
| Elapsed / total time | Timeline bottom; horizontal inset 28dp, bottom 6dp; 52dp text width each | `contentPrimary` at 70% alpha | None | Dynamic `HH:MM` / `HH:MM:SS`; `labelSmall`, 11sp |
| Player video title | Only when `showVideoTitle=true`; top-start; all-side 80dp padding; one line | `contentPrimary` | None | Dynamic media title; `headlineSmall`, 24sp bold |
| Seek-frame preview | 240dp × 135dp when frame exists; 88dp × 44dp fallback; above controls | Black `playerCanvas`; 1dp `seekPreviewOutline` (white 70%); timestamp scrim `playerScrim` (black 68%) | Preview 8dp; timestamp 4dp | Dynamic time; `contentPrimary`; `labelMedium`, 12sp |

Common transport a11y labels: `Playback speed 1X`, `Playback speed 1.5X`, `Playback speed 2X`, `Back`, `Forward`, `Collapse player`, `Play video`, `Pause video`, and `Close player`.

## 8. CID Player

Source: `app/src/cid/java/com/ivi/cid/ui/player/FrontPlayerScreen.kt`.

| Component | Behavior / visual delta from common player |
|---|---|
| Video surface | Local `CIDPlayerSurface` / Media3 `PlayerView` |
| Playback controls | Common `PlaybackControlBar`; no player title (`showVideoTitle=false`); no broadcast control |
| Screen mode | Immersive fullscreen |
| System text | Toast `Select a video first` when launched without a valid video; styling is Android system-controlled |

## 9. PID Player

Source: `app/src/pid/java/com/ivi/pid/ui/player/FrontPlayerScreen.kt`, `HostPlaybackControls.kt`, `BroadcastGlyph.kt`, and `ShareDisplaysDialog.kt`.

| Component | Dimensions / placement | Colors / assets | Bo góc / border | Visible text |
|---|---|---|---|---|
| Video surface | Full screen PID fanout output | Video content | None | None |
| Playback controls | Common bar with `img_media_passenger_control_background`; player title visible | Common controls | Asset-defined | Dynamic media title; `headlineSmall`, 24sp bold |
| Broadcast icon | Extra leading slot; 40dp clickable/glyph | `ico_media_boardcast_n/p/s`; 90ms crossfade; 0.98 press scale | `CircleShape` clip | No visible text; a11y `Broadcast` |
| Host broadcast notification | Top-center; top 10dp; horizontal 28dp / vertical 8dp padding | `broadcastNotificationSurface`: `#25364A` dark / `#E0F2E9` light; text token in section 3 | 4dp | Dynamic coordinator notification; `labelLarge`, 14sp |
| System text | Android Toast | System-controlled | System-controlled | `Select a video first`; `No rear screen available` |

### PID Share Displays dialog

| Component | Dimensions / placement | Colors / assets | Bo góc / border | Visible text |
|---|---|---|---|---|
| Dialog surface | `992px × 653px` | `dialogSurface`: `#25263B` dark / `#FBFCFE` light | Square container; no radius | None |
| Header | Height 110px, centered | `contentPrimary` | Bottom divider uses `dialogDivider` | `Accept video broadcast request?`; `headlineSmall`, 24sp bold |
| Empty state | Flexible content area, centered | `dialogDisabledText` | None | `No secondary display found`; `bodyLarge`, 16sp |
| Display row | Height 150px; list side padding 30px; content side padding 50px | Selected asset `btn_general_list_vertical_s`, fixed `150px × 151px`; divider `dialogDivider` | Bitmap-defined selected treatment | `${display.name} â€¢ ${display.state}`; `titleMedium`, 16sp; selected=`dialogAccent`, normal=`dialogNormalText`, disabled=`dialogDisabledText` |
| Checkbox | 48px | Off: `btn_general_checkbox_off_[n/p/d]`; On: `btn_general_checkbox_on_[s/p/d]` | Bitmap-defined | No visible text; a11y `Select <name>` / `Deselect <name>` |
| Action row | Horizontal side inset 50px; bottom 24px; gap 20px | Cancel `dialogAction`; Broadcast `broadcastAction`; disabled `dialogDisabledAction` | Buttons 4dp; height 84px | `Cancel`, `Broadcast`; `dialogActionContent`; `titleMedium`, 16sp; disabled text `dialogDisabledText` |

The current source literal for the row separator is `â€¢`, which is mojibake in the Kotlin file; the intended design character appears to be `•`.

## 10. Rear local and shared Player

Source: `app/src/rear/java/com/ivi/rear/ui/FrontPlayerActivity.kt`.

| Mode | Components | Text / interaction |
|---|---|---|
| Local player | Local Media3 surface; common playback bar with title enabled; common close; PiP action | Dynamic title uses `contentPrimary` / `headlineSmall`. Transport controls are interactive. |
| Shared player | PID fanout surface; `ReadOnlyPlaybackControlBar`; common close | Dynamic title and time use the same common text tokens and sizes. Timeline is disabled. Speed, previous, play/pause, next and collapse render forced pressed visuals and are non-interactive. A tap on this Rear screen alone shows the overlay for five seconds. |
| Invalid launch | Android Toast | `Select a video first`; Android system styling. |

## 11. Rear receiver dialog

Source: `app/src/rear/java/com/ivi/rear/ui/ReceiverBroadcastDialog.kt`. It can appear over Rear Library, Rear Local Player, or the cold-start consent Activity.

| Component | Dimensions / placement | Colors / assets | Bo góc / border | Visible text |
|---|---|---|---|---|
| Dialog surface | `888px × 412px` | `dialogSurface` | Square container; no radius | None |
| Header | Height 96px, centered | `contentPrimary`; divider `dialogDivider` | None | `Accept video broadcast request?`; `headlineSmall`, 24sp bold |
| Content block | Height 140px, centered | N/A | None | Dynamic broadcast `title`: `dialogAccent`, `headlineSmall`, 24sp bold; `Accepting the video broadcast in <n> seconds`: `dialogNormalText`, `titleMedium`, 16sp |
| Action row | Side padding 50px; gap 20px; controls vertically centered | `dialogAction`, text `dialogActionContent` | Buttons 4dp; height 84px | `Dismiss`, `Accept`; `headlineSmall`, 24sp bold |
| Dismiss behavior | Not a visual style | N/A | N/A | In a bootstrap consent Activity it releases the temporary receiver and process; in an already-open Rear app it closes only the request. |

## 12. Non-visual text and accessibility inventory

| Location | Text | Rendering |
|---|---|---|
| CID / PID / Rear player activity | `Select a video first` | Android Toast; no Compose size/color/radius in source |
| PID player activity | `No rear screen available` | Android Toast; no Compose size/color/radius in source |
| Rail thumbnails | Dynamic video title | Image content description only; not visible text |
| Seek preview image | `Seek preview` | Image content description only |
| PiP/Player/Transport controls | Labels listed in sections 4 and 7 | Image content descriptions only |

## 13. Source references

- Theme, semantic colors and typography: `app/src/main/java/com/ivi/common/ui/CoWatchTheme.kt`
- Shared library primitives: `app/src/main/java/com/ivi/common/ui/library/` and `app/src/main/java/com/ivi/common/ui/pidlibrary/`
- Shared player primitives: `app/src/main/java/com/ivi/common/ui/player/`
- CID-specific Library/Player: `app/src/cid/java/com/ivi/cid/ui/`
- PID-specific Player/Dialog: `app/src/pid/java/com/ivi/pid/ui/`
- Rear Player/receiver dialog: `app/src/rear/java/com/ivi/rear/ui/`
