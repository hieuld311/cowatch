# CoWatch Real-Device Manual Test Plan

Last updated: 2026-07-22
Architecture reference: `COWATCH_ARCHITECTURE_REPORT.md`

## 1. Purpose and coverage definition

This document is the manual acceptance suite for the current four-APK source:

- CID
- PID
- Rear Left
- Rear Right

“100% coverage” in this document means every current functional use case, state transition, lifecycle path and observable failure branch has at least one manual test. It does not mean 100% Kotlin bytecode/line coverage; that requires automated JVM and instrumentation coverage tools.

The user has already confirmed that the basic playback-control functions pass on all APKs. Cases marked `BASELINE PASS` are retained for traceability and require only one final release smoke pass, not full repetition during this cycle.

## 2. Status and priority

Use these values:

- `PASS`: expected behavior observed.
- `FAIL`: behavior differs from Expected Result.
- `BLOCKED`: environment prevents execution.
- `NOT RUN`: not executed yet.
- `N/A`: explicitly not applicable to the target configuration.
- `BASELINE PASS`: user-confirmed basic playback behavior.

Priorities:

- `P0`: release blocker, crash, black screen, wrong screen, resource exhaustion or broken sharing.
- `P1`: required behavior or visible UI defect.
- `P2`: resilience, performance or lower-frequency edge case.

## 3. Test environment record

Fill this before every complete run.

| Field | Value |
|---|---|
| Test date/time | |
| Tester | |
| Vehicle/head-unit model | |
| Android/AAOS build fingerprint | |
| SoC/GPU | |
| PID screen resolution/density/display ID | |
| CID screen resolution/density/display ID | |
| Rear Left resolution/density/display ID | |
| Rear Right resolution/density/display ID | |
| CID APK version | |
| PID APK version | |
| Rear Left APK version | |
| Rear Right APK version | |
| Share protocol version | `4` expected |
| Video asset set/hash | |
| Log file path | |

## 4. Package and activity reference

| APK | Package | Launcher activity |
|---|---|---|
| CID | `com.hieuld.cowatch.cid` | `com.ivi.cid.ui.VideoLibraryActivity` |
| PID | `com.hieuld.cowatch.pid` | `com.ivi.pid.ui.VideoLibraryActivity` |
| Rear Left | `com.hieuld.cowatch.rear.left` | `com.ivi.rear.ui.VideoLibraryActivity` |
| Rear Right | `com.hieuld.cowatch.rear.right` | `com.ivi.rear.ui.VideoLibraryActivity` |

Useful PowerShell/ADB commands:

```powershell
adb devices -l
adb shell pm list packages | Select-String "com.hieuld.cowatch"
adb shell dumpsys display | Select-String "mDisplayId|DisplayDeviceInfo"
adb shell dumpsys activity activities | Select-String "cowatch|mDisplayId"
adb logcat -c
adb logcat -v threadtime | Tee-Object -FilePath .\cowatch-runtime.log
```

Focused log capture:

```powershell
adb logcat -v threadtime -s PIDFrontPlayer PidShareCoordinator PidFrameFanout RearShareClient AndroidRuntime MediaCodec CCodec
```

Resource snapshots:

```powershell
adb shell dumpsys media.resource_manager > .\media-resource-manager.txt
adb shell dumpsys media_session > .\media-session.txt
adb shell dumpsys meminfo com.hieuld.cowatch.pid > .\pid-meminfo.txt
adb shell dumpsys gfxinfo com.hieuld.cowatch.pid > .\pid-gfxinfo.txt
```

Launch an APK on a specific runtime display when the device permits shell multi-display launch:

```powershell
adb shell am start --display <CID_ID> -n com.hieuld.cowatch.cid/com.ivi.cid.ui.VideoLibraryActivity
adb shell am start --display <PID_ID> -n com.hieuld.cowatch.pid/com.ivi.pid.ui.VideoLibraryActivity
adb shell am start --display <REAR_LEFT_ID> -n com.hieuld.cowatch.rear.left/com.ivi.rear.ui.VideoLibraryActivity
adb shell am start --display <REAR_RIGHT_ID> -n com.hieuld.cowatch.rear.right/com.ivi.rear.ui.VideoLibraryActivity
```

## 5. Shared test data

Run media matrix cases against every asset:

| Data ID | Asset |
|---|---|
| V0 | `blender_video.mp4` |
| V1 | `blender_video_1.mp4` |
| V2 | `blender_video_2.mp4` |
| V3 | `blender_video_3.mp4` |
| V4 | `blender_video_4.mp4` |
| V5 | `blender_video_5.mp4` |
| V6 | `blender_video_6.mp4` |

Every video currently has a matching `seekPreview/<video-name>/index.txt` and storyboard frames.

## 6. Basic playback baseline — already passed

These cases are recorded as `BASELINE PASS` for CID, PID, Rear Left and Rear Right standalone playback.

| ID | Function | Expected result | Status |
|---|---|---|---|
| BAS-01 | Play/Pause tap | State and icon switch exactly once | BASELINE PASS |
| BAS-02 | Play/Pause press-hold-release | Pressed visual appears while held; release inside executes once | BASELINE PASS |
| BAS-03 | Previous/back | Position moves back 5 seconds and clamps at zero | BASELINE PASS |
| BAS-04 | Next/forward | Position moves forward 5 seconds and clamps at duration | BASELINE PASS |
| BAS-05 | Speed | Cycles `1X -> 1.5X -> 2X -> 1X` | BASELINE PASS |
| BAS-06 | Seek tap | Player seeks to the tapped timeline position | BASELINE PASS |
| BAS-07 | Seek drag | Slider tracks drag and seeks on release | BASELINE PASS |
| BAS-08 | PiP/collapse | Fullscreen moves to in-app PiP | BASELINE PASS |
| BAS-09 | Expand | PiP returns to fullscreen | BASELINE PASS |
| BAS-10 | Close | Correct normal/pressed close artwork; closes expected session | BASELINE PASS |
| BAS-11 | Icon-only hit area | Tapping empty slot/background does not execute a button | BASELINE PASS |
| BAS-12 | Release outside | Cancels action and returns to normal artwork | BASELINE PASS |

Final-release smoke: execute BAS-01, BAS-06, BAS-08 and BAS-10 once per APK after all remaining cases pass.

## 7. Installation, identity and startup

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| INS-01 | P0 | Install all four APKs from the same build; list packages | All four package IDs coexist | NOT RUN |
| INS-02 | P1 | Inspect launcher icons | All use only `ico_general_media_s`; no extra icon background | NOT RUN |
| INS-03 | P1 | Inspect launcher labels | All four APKs use the single shared `@string/app_name` label | NOT RUN |
| INS-04 | P0 | Cold-launch every APK after force-stop | Library opens; no Hilt/activity/ClassNotFound crash | NOT RUN |
| INS-05 | P1 | Warm-launch every APK from Recents | Existing state resumes without duplicate Activity | NOT RUN |
| INS-06 | P0 | Reboot device, then launch every APK | All start normally; no stale Binder/session state | NOT RUN |
| INS-07 | P1 | Use `pidof`/`ps` for all packages | PID, CID, Rear Left and Rear Right are separate processes | NOT RUN |
| INS-08 | P0 | Install PID and both Rear APKs from the same build | Protocol v4 binds without mismatch log | NOT RUN |
| INS-09 | P2 | Clear data for one Rear APK only | Other three APKs retain their state and still launch | NOT RUN |
| INS-10 | P0 | Uninstall/reinstall one Rear APK while no sharing is active | PID and remaining Rear continue; reinstalled APK registers after launch | NOT RUN |

## 8. Runtime display and role mapping

The APK role is authoritative. A runtime display number must never define Left versus Right.

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| DSP-01 | P0 | Record all runtime display IDs from `dumpsys display` | IDs are recorded in the environment table | NOT RUN |
| DSP-02 | P0 | Launch Rear Left APK on assigned left display | PID records `REAR_LEFT -> actual left displayId` | NOT RUN |
| DSP-03 | P0 | Launch Rear Right APK on assigned right display | PID records `REAR_RIGHT -> actual right displayId` | NOT RUN |
| DSP-04 | P0 | Use a device/config where Rear IDs are not `2` and `3` | Both targets remain available using reported runtime IDs | NOT RUN |
| DSP-05 | P0 | Reconnect/re-enumerate displays so numeric IDs change | Role mapping follows APK registration, not old IDs | NOT RUN |
| DSP-06 | P0 | Start Right before Left | Targets do not swap based on registration/display order | NOT RUN |
| DSP-07 | P1 | Run only Rear Left | PID shows Left ready and Right unavailable/disconnected | NOT RUN |
| DSP-08 | P1 | Run only Rear Right | PID shows Right ready and Left unavailable/disconnected | NOT RUN |
| DSP-09 | P1 | Move/relaunch a Rear Activity on another permitted display | New registration replaces its previous display ID | NOT RUN |
| DSP-10 | P0 | Register a replacement Rear process after killing the old one | Late death callback from old Binder does not remove new receiver | NOT RUN |

The source uses live `ReceiverRegistration.role -> displayId` mapping. DSP-04, DSP-05 and DSP-09 are mandatory regression cases proving that no fixed display number or stale previous registration remains.

## 9. Shared asset catalog, thumbnails and seek-preview data

| ID | P | Target | Steps | Expected result | Status |
|---|---:|---|---|---|---|
| CAT-01 | P0 | All APKs | Open each library | Exactly seven videos appear | NOT RUN |
| CAT-02 | P1 | All APKs | Compare ordering/titles | Same catalog and case-insensitive title order | NOT RUN |
| CAT-03 | P1 | All APKs | Scroll through every video | Correct thumbnail for each video; no blank/reused wrong image | NOT RUN |
| CAT-04 | P1 | CID/PID/Rear | Focus every item | Background follows committed focus and is not corrupted during preview animation | NOT RUN |
| CAT-05 | P2 | PID/Rear | Reopen library after 1 second and after 30 seconds | Persistent cache warms without visible UI freeze | NOT RUN |
| CAT-06 | P0 | All APKs | Play V0–V6 | Every packaged asset opens; no missing asset/URI error | NOT RUN |
| CAT-07 | P1 | All APKs | Drag seek on V0–V6 | Each video shows its own storyboard, never another video's frames | NOT RUN |
| CAT-08 | P1 | All APKs | Drag rapidly across V0–V6 | Preview updates without queue buildup, stale frame or crash | NOT RUN |
| CAT-09 | P2 | All APKs | Seek at 0%, 50%, 100% of every video | Preview frame/time corresponds to requested region | NOT RUN |
| CAT-10 | P2 | Build/install after changing one video asset | Changed storyboard updates; unchanged video previews still work | NOT RUN |

## 10. CID library UI and focus behavior

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| CID-01 | P1 | Open CID library | Vertical rail is left-aligned; no PID broadcast UI | NOT RUN |
| CID-02 | P1 | Inspect focused and side cards | Focused card has cyan border and correct CID geometry | NOT RUN |
| CID-03 | P1 | Drag up/down slowly | Preview focus follows finger smoothly | NOT RUN |
| CID-04 | P1 | Release before/after settle threshold | Focus stays or moves to expected nearest item | NOT RUN |
| CID-05 | P1 | Drag beyond first/last item | Circular navigation wraps without blank slots | NOT RUN |
| CID-06 | P0 | Tap a non-focused card | Card becomes focused; playback does not start | NOT RUN |
| CID-07 | P0 | Tap already-focused card body | Playback does not start | NOT RUN |
| CID-08 | P0 | Tap focused Play | Selected video opens and starts | NOT RUN |
| CID-09 | P1 | Return to library and focus another item | Focused title/background reflect selected item | NOT RUN |
| CID-10 | P1 | Start PiP, then navigate focus | PiP session and library focus remain independent | NOT RUN |

## 11. PID and Rear horizontal library UI

Execute every `LIB-*` case on PID, Rear Left and Rear Right unless specified.

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| LIB-01 | P1 | Open library | No solid video-tray background; shared background fills screen | NOT RUN |
| LIB-02 | P1 | Inspect focused card | `604x340` physical pixels and cyan border | NOT RUN |
| LIB-03 | P1 | Inspect non-focused cards | `396x223` physical pixels | NOT RUN |
| LIB-04 | P1 | Open PiP | PiP video uses `396x223` geometry | NOT RUN |
| LIB-05 | P1 | Measure rail margins | Left/right padding is 55 physical pixels | NOT RUN |
| LIB-06 | P1 | Measure rail location | Rail bottom padding is 40dp-equivalent layout spacing | NOT RUN |
| LIB-07 | P1 | Inspect focused title | Title and focused card share the same left column | NOT RUN |
| LIB-08 | P1 | Focus each item | Title changes smoothly and does not clip unexpectedly | NOT RUN |
| LIB-09 | P1 | Inspect page-progress bar | Bar is at bottom of full library screen, not directly under the rail | NOT RUN |
| LIB-10 | P1 | Measure progress bar | Height is 6dp | NOT RUN |
| LIB-11 | P1 | Inspect gradient | Intentional direction is left cyan `0xFF00E7FF` to right purple `0xFF8D3DFF` | NOT RUN |
| LIB-12 | P1 | Move focus one and multiple items | Progress animates smoothly in about 420ms without jumping | NOT RUN |
| LIB-13 | P1 | Wrap last-to-first/first-to-last | Progress and focus use the same circular destination | NOT RUN |
| LIB-14 | P0 | Tap non-focused card and focused card body | Focus-only; neither starts playback | NOT RUN |
| LIB-15 | P0 | Tap focused Play | New session starts; active-session Play toggles existing session | NOT RUN |
| LIB-16 | P2 | Rapidly swipe/click during settle animation | No duplicate navigation, crash or stuck focus state | NOT RUN |

## 12. Advanced local playback and media lifecycle

Apply the media matrix to CID, PID, Rear Left and Rear Right in standalone mode.

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| MED-01 | P0 | Play V0–V6 on CID | Video and audio start without seek workaround | NOT RUN |
| MED-02 | P0 | Play V0–V6 on PID | Video and audio start without seek workaround | NOT RUN |
| MED-03 | P0 | Play V0–V6 on Rear Left | Video and audio start without seek workaround | NOT RUN |
| MED-04 | P0 | Play V0–V6 on Rear Right | Video and audio start without seek workaround | NOT RUN |
| MED-05 | P0 | Watch startup logs | No fatal MediaCodec/AudioTrack error; no repeated prepare loop | NOT RUN |
| MED-06 | P1 | Pause for 30s, then resume | Same position/source resumes normally | NOT RUN |
| MED-07 | P1 | Seek while playing | Playback pauses during drag and resumes after release | NOT RUN |
| MED-08 | P1 | Seek while paused | Player remains paused after release | NOT RUN |
| MED-09 | P1 | Drag at extreme left/right edges | Handle reaches track endpoints; no overrun or clipping | NOT RUN |
| MED-10 | P1 | Wait for controls to hide, tap screen | Controls reappear and timer restarts | NOT RUN |
| MED-11 | P1 | Interact at 4.5s after showing controls | Controls remain for another complete five-second idle period | NOT RUN |
| MED-12 | P1 | Hold seek longer than 5s | Controls and preview remain visible while dragging | NOT RUN |
| MED-13 | P1 | Inspect timeline | Timeline is full-width on top of bar; top of handle is not clipped | NOT RUN |
| MED-14 | P1 | Inspect control spacing | Play is exactly centered; adjacent slots are visually even | NOT RUN |
| MED-15 | P1 | PID/Rear: show controls | Video title appears at top-left with 80dp padding and hides with controls | NOT RUN |
| MED-16 | P1 | Let every video reach end | No crash/black-loop; play state becomes stopped/paused consistently | NOT RUN |
| MED-17 | P1 | Close player, immediately play another video | Old media/Surface is cleared; new video starts normally | NOT RUN |
| MED-18 | P2 | Open same source repeatedly through `singleTop` | Position/session is not duplicated or restarted unexpectedly | NOT RUN |
| MED-19 | P2 | Press Home/Recents and return | Surface reattaches; video is not permanently black | NOT RUN |
| MED-20 | P2 | Turn affected display off/on during local playback | Activity/player recovers or exits cleanly; no orphan audio | NOT RUN |

## 13. In-app PiP and return paths

Run PIP-01 through PIP-10 on CID, PID, Rear Left and Rear Right in standalone mode.

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| PIP-01 | P0 | Enter PiP while playing | Playback/audio/position continue without restart | NOT RUN |
| PIP-02 | P0 | Enter PiP while paused | PiP stays paused at same position | NOT RUN |
| PIP-03 | P0 | Expand same source | Fullscreen returns at same position with no black frame | NOT RUN |
| PIP-04 | P1 | Toggle play in PiP | Same session toggles; fullscreen later reflects state | NOT RUN |
| PIP-05 | P0 | Close PiP | Playback stops, PiP disappears and media state clears | NOT RUN |
| PIP-06 | P0 | Start a different focused video while PiP exists | New source replaces old session cleanly | NOT RUN |
| PIP-07 | P1 | Rapid expand/collapse 20 times | No black screen, duplicate audio, crash or leaked window | NOT RUN |
| PIP-08 | P2 | Home/return while PiP is visible | PiP Surface reattaches and session remains consistent | NOT RUN |
| PIP-09 | P2 | Rotate/recreate if device configuration permits | Stale Surface generation cannot remove current PID PiP output | NOT RUN |
| PIP-10 | P1 | Inspect PiP hit areas | Only icon regions trigger play, expand and close | NOT RUN |
| PIP-11 | P0 | PID: close library PiP during active sharing | Sharing stops for receivers and PID media clears | NOT RUN |
| PIP-12 | P1 | PID: enter PiP during active sharing | Host PiP and accepted Rear outputs continue from one PID stream | NOT RUN |

## 14. PID broadcast target dialog

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| BDG-01 | P0 | While PID plays, tap Broadcast | PID pauses immediately and opens dialog | NOT RUN |
| BDG-02 | P0 | While PID is paused, tap Broadcast | PID remains paused and opens dialog | NOT RUN |
| BDG-03 | P1 | Measure dialog screenshot | Dialog is `992x653` physical pixels | NOT RUN |
| BDG-04 | P1 | Inspect unselected row and press-hold | Checkbox transitions `_off_n -> _off_p` | NOT RUN |
| BDG-05 | P1 | Select ready target and press-hold again | Selected artwork uses `_on_s -> _on_p` | NOT RUN |
| BDG-06 | P1 | Inspect unavailable target | Disabled artwork uses `_off_d`; row cannot toggle | NOT RUN |
| BDG-07 | P1 | Select then make target unavailable | Disabled selected state uses `_on_d` | NOT RUN |
| BDG-08 | P1 | Inspect selected-row artwork | `btn_general_list_vertical_s` remains original `150x151`, not stretched | NOT RUN |
| BDG-09 | P0 | Select no target | Broadcast action is disabled | NOT RUN |
| BDG-10 | P0 | Select Left, Right, then both | Selected role set exactly matches visible checkboxes | NOT RUN |
| BDG-11 | P0 | Cancel while PID was playing | Dialog closes and PID resumes | NOT RUN |
| BDG-12 | P0 | Cancel while PID was paused | Dialog closes and PID remains paused | NOT RUN |
| BDG-13 | P0 | Tap outside/back if supported by dialog behavior | No accidental broadcast; dismissal follows defined Cancel path | NOT RUN |
| BDG-14 | P1 | No Rear target available, tap Broadcast control | Dialog does not open; “No rear screen available” appears | NOT RUN |
| BDG-15 | P1 | Inspect target label and state separator | The UTF-8 bullet `•` renders correctly between target name and state | NOT RUN |

## 15. Rear receiver request dialog

Run RXD cases on Rear Left and Rear Right independently and together.

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| RXD-01 | P0 | PID sends request to ready Rear | Dialog appears on correct Rear screen | NOT RUN |
| RXD-02 | P1 | Measure screenshot | Dialog is `888x412` physical pixels | NOT RUN |
| RXD-03 | P1 | Inspect content | Correct video title and countdown appear without clipping | NOT RUN |
| RXD-04 | P0 | Press Accept before countdown ends | Local playback suspends and receiver enters shared player | NOT RUN |
| RXD-05 | P0 | Press Dismiss | Dialog closes; local playback remains unchanged; PID receives denial | NOT RUN |
| RXD-06 | P0 | Do nothing for 10 seconds | Countdown reaches zero and auto-accepts once | NOT RUN |
| RXD-07 | P1 | Press Back or outside | Dialog cannot be accidentally dismissed | NOT RUN |
| RXD-08 | P1 | Send a duplicate request for same session | Only one dialog/countdown exists | NOT RUN |
| RXD-09 | P0 | PID cancels while dialog is open | Dialog disappears; no delayed auto-accept occurs | NOT RUN |
| RXD-10 | P0 | PID dies while dialog is open | Dialog clears; Rear remains/restores standalone mode | NOT RUN |
| RXD-11 | P1 | Request arrives over Rear library | Dialog overlays library on that display | NOT RUN |
| RXD-12 | P1 | Request arrives over Rear fullscreen player | Dialog overlays player without switching source before acceptance | NOT RUN |

## 16. Sharing functional matrix

For SHARE-01 through SHARE-08, verify PID, selected Rear screens and unselected Rear screens simultaneously.

| ID | P | Scenario/steps | Expected result | Status |
|---|---:|---|---|---|
| SHR-01 | P0 | PID playing; Left selected; Left accepts | Scheduled synchronized sharing starts on PID+Left | NOT RUN |
| SHR-02 | P0 | PID playing; Right selected; Right accepts | Scheduled synchronized sharing starts on PID+Right | NOT RUN |
| SHR-03 | P0 | PID playing; both selected; both accept | PID+both Rear render same stream | NOT RUN |
| SHR-04 | P0 | PID paused; both accept | All screens show same paused frame; timeline does not advance | NOT RUN |
| SHR-05 | P0 | Both selected; Left accepts, Right dismisses | Sharing continues only on Left; Right local state remains intact | NOT RUN |
| SHR-06 | P0 | Both selected; both dismiss | Sharing does not start; previously playing PID resumes | NOT RUN |
| SHR-07 | P0 | One selected Rear auto-accepts at 10s | Sharing starts once after Surface becomes ready | NOT RUN |
| SHR-08 | P0 | Both selected; acceptance occurs at different times | Start waits for final response/accepted Surface, then synchronizes accepted roles | NOT RUN |
| SHR-09 | P0 | Selected Rear was local fullscreen-playing | Acceptance releases local decoder; end restores fullscreen source/position/play state | NOT RUN |
| SHR-10 | P0 | Selected Rear was local fullscreen-paused | End restores fullscreen paused at saved position | NOT RUN |
| SHR-11 | P0 | Selected Rear was library-PiP-playing | End restores library PiP and continues from saved state | NOT RUN |
| SHR-12 | P0 | Selected Rear was library-PiP-paused | End restores library PiP paused | NOT RUN |
| SHR-13 | P0 | Selected Rear was library-idle | End returns to idle library without phantom PiP/audio | NOT RUN |
| SHR-14 | P0 | Unselected Rear is locally playing | Its independent playback continues; selected Rear alone switches to shared mode | NOT RUN |
| SHR-15 | P0 | Start sharing, then choose another PID video | Old sharing ends before new media starts | NOT RUN |
| SHR-16 | P0 | Start a new share session while an old one exists | Old session completes once; new session owns all outputs | NOT RUN |
| SHR-17 | P1 | PID had speed 1.5X/2X before start | Shared snapshots and progress use same speed | NOT RUN |
| SHR-18 | P1 | Share each V0–V6 to both Rear screens | Correct source/title/frames appear for every asset | NOT RUN |

## 17. Shared playback synchronization and passive Rear UI

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| SYN-01 | P0 | Record all screens at start | Visible start skew is within acceptance tolerance; no Rear starts early | NOT RUN |
| SYN-02 | P0 | PID Play/Pause repeatedly | Both accepted Rear screens follow state without owning playback authority | NOT RUN |
| SYN-03 | P0 | PID seek to several positions | Rear frames/progress reconcile to PID; no stale seek replay | NOT RUN |
| SYN-04 | P1 | PID back/forward 5s | Both Rear timelines follow host destination | NOT RUN |
| SYN-05 | P1 | PID cycle speed | Rear progress rate and displayed speed icon follow PID | NOT RUN |
| SYN-06 | P1 | Pause for 10s | Rear timeline remains fixed because `isPlaying=false` | NOT RUN |
| SYN-07 | P1 | Observe for more than 2s while playing | Reconciliation snapshots prevent accumulating drift | NOT RUN |
| SYN-08 | P1 | Tap Rear Left only | Only Left overlay appears; Right visibility/timer is unchanged | NOT RUN |
| SYN-09 | P1 | Tap Rear Right 2s later | Right owns a separate five-second timer | NOT RUN |
| SYN-10 | P1 | Wait 5 seconds idle on each Rear | Each overlay hides approximately 5s after its own last tap | NOT RUN |
| SYN-11 | P1 | Inspect shared Rear bar | Timeline/background/title/typography match PID host geometry | NOT RUN |
| SYN-12 | P1 | Inspect shared Rear transport buttons | Buttons permanently show pressed artwork | NOT RUN |
| SYN-13 | P0 | Tap every shared Rear transport button | No play, pause, seek, speed, PiP or navigation command executes | NOT RUN |
| SYN-14 | P0 | Drag/tap shared Rear timeline | No seek occurs and PID position is unchanged | NOT RUN |
| SYN-15 | P1 | Tap blank Rear video surface | Only overlay visibility changes | NOT RUN |
| SYN-16 | P1 | Inspect title while overlay visible | Correct source title appears top-left and hides with bar | NOT RUN |
| SYN-17 | P0 | Listen during both-Rear fanout | No duplicated/echoed Rear audio; PID remains intended audio authority | NOT RUN |
| SYN-18 | P2 | Run for 30 minutes | Audio/video remain synchronized; drift does not grow visibly | NOT RUN |

## 18. Host and receiver completion paths

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| END-01 | P0 | PID toggles Broadcast off during active share | Both selected Rear sessions stop and restore local destinations | NOT RUN |
| END-02 | P0 | PID closes player during active share | Both Rear sessions stop; no orphan shared frame/audio | NOT RUN |
| END-03 | P0 | Rear Left closes shared player while both share | Left returns to library/PiP; Right and PID continue | NOT RUN |
| END-04 | P0 | Final Rear closes shared player | Session ends once; PID receives ended state | NOT RUN |
| END-05 | P0 | Rear user closes shared player with prior local source | Rear returns to library with restored local PiP | NOT RUN |
| END-06 | P0 | Rear user closes with no prior source | Rear returns to idle library | NOT RUN |
| END-07 | P1 | PID stops before sharing reaches `SHARING` | Previously playing PID resumes | NOT RUN |
| END-08 | P1 | PID stops before sharing while originally paused | PID stays paused | NOT RUN |
| END-09 | P0 | Stop session repeatedly/rapidly | Completion is idempotent; no double restore/crash | NOT RUN |
| END-10 | P0 | After end, start local playback on both Rear APKs | Local decoders and controls work normally | NOT RUN |

## 19. PID host notifications

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| NTF-01 | P1 | At least one target accepts and starts | “Video broadcast accepted” appears on PID | NOT RUN |
| NTF-02 | P1 | All targets deny/timeout | “Video broadcast denied” appears on PID | NOT RUN |
| NTF-03 | P1 | Active sharing ends | “Video broadcast ended” appears on PID | NOT RUN |
| NTF-04 | P1 | Measure each notification | Visible for about two seconds, then fades | NOT RUN |
| NTF-05 | P2 | Cause two notifications rapidly | Latest notification replaces previous cleanly | NOT RUN |
| NTF-06 | P1 | Enter PID library PiP around a session event | Required host event is still observable and no crash occurs | NOT RUN |

## 20. Timeout, disconnect and Surface recovery

Some cases require force-stop, screen off/on, display disconnect or a debug build that can delay a callback. Mark `BLOCKED` with the exact unavailable control if the production device cannot create the condition.

| ID | P | Fault injection/steps | Expected result | Status |
|---|---:|---|---|---|
| REC-01 | P0 | Selected Rear never becomes ready for 8s | Target fails; session continues with other ready target or denies if none | NOT RUN |
| REC-02 | P0 | Receiver does not respond for 12s | Target response times out and is stopped | NOT RUN |
| REC-03 | P0 | Accept but never provide Surface for 5s | Target fails with Surface timeout; no host hang | NOT RUN |
| REC-04 | P0 | Destroy/recreate Surface within 2s | Output recovers and sharing continues | NOT RUN |
| REC-05 | P0 | Keep Surface unavailable beyond 2s | Only affected target is removed; final target removal ends session | NOT RUN |
| REC-06 | P0 | Force-stop Rear Left during both-target sharing | PID detects Binder death; Right continues | NOT RUN |
| REC-07 | P0 | Force-stop final Rear receiver | PID ends session and releases remote output | NOT RUN |
| REC-08 | P0 | Force-stop PID during active sharing | Both Rear clients clear shared mode and restore local destinations | NOT RUN |
| REC-09 | P0 | Force-stop PID while receiver dialog is visible | Dialog clears; no delayed transition to shared mode | NOT RUN |
| REC-10 | P0 | Restart PID after REC-08 | Rear clients reconnect/register; a new share works | NOT RUN |
| REC-11 | P0 | Rapidly recreate Rear Activity/Surface 20 times | Stale destroy generation never removes the current output | NOT RUN |
| REC-12 | P0 | Disconnect/reconnect physical Rear display | No permanent black frame, invalid Surface loop or orphan decoder | NOT RUN |
| REC-13 | P1 | PID output reports invalid Surface/EGL swap failure | Coordinator removes affected role and reports completion correctly | NOT RUN |
| REC-14 | P1 | Kill old Rear then immediately start replacement process | Old Binder death cannot remove replacement registration | NOT RUN |
| REC-15 | P1 | Send status/session callback from stale session if test hook exists | PID/Rear ignore stale session ID and sequence | NOT RUN |
| REC-16 | P1 | Register older Surface generation after newer one if test hook exists | Old Surface is rejected/released; new output remains | NOT RUN |
| REC-17 | P2 | Repeatedly toggle display power during 30-minute share | Recovery remains bounded; no increasing output/resource count | NOT RUN |

## 21. Decoder, GPU, memory and performance

| ID | P | Steps/evidence | Expected result | Status |
|---|---:|---|---|---|
| RES-01 | P0 | Capture `dumpsys media.resource_manager` with PID+both Rear sharing | One PID video decoder owns the shared stream; selected Rear decoders are released | NOT RUN |
| RES-02 | P0 | Compare before acceptance and after acceptance | Each accepted Rear local decoder disappears | NOT RUN |
| RES-03 | P1 | Keep one unselected Rear playing locally | PID fanout plus that independent Rear may own separate decoders; selected Rear does not | NOT RUN |
| RES-04 | P0 | Inspect logcat during both-target start | No `NO_MEMORY`, codec allocation failure or fatal EGL error | NOT RUN |
| RES-05 | P1 | Capture `gfxinfo` during library focus animation | No sustained severe jank; progress animation is smooth | NOT RUN |
| RES-06 | P1 | Rapid seek-preview drag for 60s | UI remains responsive; frame requests do not cause continuous memory growth | NOT RUN |
| RES-07 | P1 | Record CPU/GPU/memory with one and two Rear outputs | Second output increases render work but not decoder count | NOT RUN |
| RES-08 | P2 | Share both screens for 2 hours | No crash, ANR, black screen, drift or unbounded memory growth | NOT RUN |
| RES-09 | P2 | Execute 50 share start/stop cycles | Output count returns to baseline after every cycle | NOT RUN |
| RES-10 | P2 | Execute 50 PiP expand/collapse cycles on PID | No leaked Surface/EGL output or black host | NOT RUN |
| RES-11 | P1 | Play all seven videos and inspect audio | Sound is present unless one-time AudioTrack fallback is genuinely triggered | NOT RUN |
| RES-12 | P1 | If fallback triggers, inspect logs and behavior | Audio is disabled only once for that source attempt; video restarts at same position/state | NOT RUN |

## 22. Security, protocol and exported-component behavior

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| SEC-01 | P0 | Bind to PID service from an unsigned test APK | Bind is denied by signature permission | NOT RUN |
| SEC-02 | P0 | Install mismatched old Rear build if available | Protocol mismatch is logged; no crash or undefined sharing | NOT RUN |
| SEC-03 | P1 | Try external launch of flavor `FrontPlayerActivity` | Launch is rejected because Activity is not exported | NOT RUN |
| SEC-04 | P1 | External-launch each library Activity | Only intended launcher Activity opens | NOT RUN |
| SEC-05 | P1 | Send invalid role through signed test hook if available | PID rejects/does not expose an unintended render target | NOT RUN |
| SEC-06 | P1 | Send wrong session ID/generation through test hook | PID releases rejected Surface and keeps active session intact | NOT RUN |
| SEC-07 | P2 | Update only PID while Rear processes are alive | Old connection dies cleanly; mismatch does not crash either side | NOT RUN |
| SEC-08 | P2 | Reinstall all protocol-v4 APKs and retry | Normal binding/sharing is restored | NOT RUN |

## 23. Lifecycle, concurrency and stress

| ID | P | Steps | Expected result | Status |
|---|---:|---|---|---|
| LIF-01 | P0 | Run local video independently on both Rear APKs | Both operate independently before sharing | NOT RUN |
| LIF-02 | P0 | Accept sharing on both while both local videos run | Both local sessions suspend before PID fanout starts | NOT RUN |
| LIF-03 | P0 | End LIF-02 | Each Rear restores its own source/position/state, not the other's | NOT RUN |
| LIF-04 | P1 | Background/foreground CID repeatedly | No effect on PID/Rear sessions | NOT RUN |
| LIF-05 | P1 | Background/foreground PID during sharing | Surfaces/session recover; no duplicate coordinator | NOT RUN |
| LIF-06 | P1 | Background/foreground one Rear during sharing | Other Rear timer/rendering remains independent | NOT RUN |
| LIF-07 | P0 | Close PID and immediately relaunch/play | Stable player/listener state; no missing callbacks or duplicate session | NOT RUN |
| LIF-08 | P0 | Close Rear shared screen and immediately launch local video | Restore/navigation completes once; no black-but-playing state | NOT RUN |
| LIF-09 | P2 | Rapidly tap Broadcast/Cancel 30 times | PID pause/resume state remains correct; one dialog maximum | NOT RUN |
| LIF-10 | P2 | Rapidly Accept/Dismiss near countdown zero | Exactly one final response; no double transition | NOT RUN |
| LIF-11 | P2 | Simultaneously close both Rear shared players | PID finishes once; no concurrent modification/crash | NOT RUN |
| LIF-12 | P2 | Reboot after a session was active | All APKs start standalone with no persisted phantom session | NOT RUN |

## 24. Visual regression captures

Capture full-resolution screenshots for every row.

| ID | Required capture | Status |
|---|---|---|
| VIS-01 | CID library with first/middle/last focus | NOT RUN |
| VIS-02 | PID library with focus/progress positions 0%, middle, 100% | NOT RUN |
| VIS-03 | Rear Left and Rear Right libraries | NOT RUN |
| VIS-04 | CID/PID/Rear local playback bar with handle at 0%, 50%, 100% | NOT RUN |
| VIS-05 | PID player title and controls visible/hidden | NOT RUN |
| VIS-06 | PID target dialog: off, pressed, selected, disabled states | NOT RUN |
| VIS-07 | Receiver dialog at 10s, 5s and 0s | NOT RUN |
| VIS-08 | Both Rear shared overlays visible with pressed disabled controls | NOT RUN |
| VIS-09 | Rear overlays independently hidden/visible | NOT RUN |
| VIS-10 | PID accepted/denied/ended notifications | NOT RUN |

## 25. Release acceptance gates

Release is accepted only when:

- Every P0 case is `PASS`; no P0 case may be waived without product-owner approval.
- Every P1 case is `PASS` or has an approved defect/waiver.
- No reproducible crash, ANR, permanent black screen, orphan audio or decoder `NO_MEMORY` remains.
- Dynamic display mapping works without assuming display IDs 2 and 3.
- Both-Rear sharing owns one PID decoded video stream.
- Receiver local playback restoration passes for fullscreen, PiP and idle destinations.
- Independent Rear auto-hide and non-interactive pressed controls pass.
- All seven videos pass catalog, playback, audio and seek-preview matrices.
- Final BAS smoke cases pass once on all four APKs.

## 26. Defect record template

```text
Test ID:
Build/version:
Device/build fingerprint:
Display IDs and role assignment:
Precondition:
Exact steps:
Expected:
Actual:
Frequency:
First failure timestamp:
Screenshot/video:
Logcat file and timestamp range:
dumpsys media.resource_manager:
dumpsys activity/display:
Recovery required:
```

## 27. Source-to-test traceability

| Current source owner | Manual coverage |
|---|---|
| App manifests, flavors, Hilt startup | INS, SEC |
| `AssetVideoRepository`, thumbnail/cache | CAT, CID, LIB |
| `Media3PlaybackController` | BAS, MED, PIP, SHR, END, LIF |
| Playback controls, timeline, seek frames | BAS, CAT, MED, VIS |
| CID activities/ViewModels/UI | CID, MED, PIP, LIF |
| PID activities/ViewModels/UI | LIB, MED, PIP, BDG, NTF |
| Runtime Rear receiver registration and target mapping | DSP |
| `PidShareCoordinator` | BDG, SHR, SYN, END, NTF, REC |
| `RearShareClient` | RXD, SHR, SYN, END, REC, LIF |
| AIDL protocol/signature permission | INS, DSP, REC, SEC |
| `PidRenderFanout`, EGL engine | PIP, SHR, SYN, REC, RES |
| Rear local/shared activities | LIB, MED, PIP, RXD, SYN, END, LIF |
| Build-time storyboard pipeline | CAT |

This traceability table covers every production package in the current single-module `app/src` architecture. Generated AIDL/Hilt code and internal GLES shader statements are validated indirectly through IPC, Surface, visual output, recovery and resource tests rather than as separate manual line-level cases.
