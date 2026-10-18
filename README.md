# Dance Pad

Windows and Android viewers for Paul's USB dance mat, plus a bridge that sends the real mat's signals from the PC to the Xiaomi Pad 7. The bridge works with the tablet's existing USB cable; no OTG adapter is needed.

Project location: `C:\Users\paul\Projects\dancepad`.

## Use the real mat on the tablet through the PC

1. Plug the dance mat into the PC by USB.
2. Connect the Xiaomi tablet to the PC by USB. USB debugging must be enabled and this PC authorized on the tablet.
3. Open PowerShell and run:

   ```powershell
   cd C:\Users\paul\Projects\dancepad
   .\start_pad_bridge.ps1
   ```

4. Open **Dance Pad** on the tablet and turn on **PC bridge**. Leave **Mock pad** off to view only real input.
5. Step on the mat. Corresponding areas and raw button indicators turn green on the tablet.

Keep both USB cables connected. The launcher starts the PC relay in the background and sets up `adb reverse` for the tablet. It reuses an already running relay. Run the launcher again after a reboot or reconnecting USB if the connection is lost.

The launcher defaults to the Xiaomi's USB serial `4c637b9e`. For another authorized Android device:

```powershell
adb devices -l
.\start_pad_bridge.ps1 -DeviceSerial YOUR_DEVICE_SERIAL
```

The relay reads the mat at 120 Hz, sends changes immediately, and listens only on `127.0.0.1:8765`. The tablet reconnects automatically if the relay restarts. Signals feed the Dance Pad viewer; system-wide Android gamepad emulation is not implemented.

## Windows viewer

```powershell
python pad_monitor.py
```

The layout matches the mat. Pressed areas turn green; the lower panel shows 12 buttons, axes, POV, and the last press/release. Click an area, release all controls, then press the physical area to map it. Esc cancels mapping. **Reset mapping** restores the confirmed defaults below. **Rescan** detects newly connected controllers.

Windows mappings save automatically in `pad_mapping.json` beside the Python scripts. The viewer and bridge use the standard Windows joystick API. Python with Tkinter is required; no extra Python packages are needed.

## Android app and mock mode

**Dance Pad 1.3** is already installed on the Xiaomi Pad 7. `DancePad.apk` is a copy of the installable debug APK. To reinstall it:

```powershell
adb -s 4c637b9e install -r .\DancePad.apk
```

- **Mock pad**: hold areas on the touchscreen to simulate signals, including simultaneous touches.
- **Run demo / Stop demo**: cycle through simulated presses automatically.
- **PC bridge**: display signals forwarded from the real mat on the PC.
- **Map real controller**: tap this, choose an area, then press its control on a directly connected Android controller. Android mappings save privately on the tablet, separately from the Windows JSON.

When an OTG/data adapter is available, the mat can also be tested directly on Android. Direct USB compatibility and Android's button codes still need physical testing. The PC bridge uses the confirmed Windows button assignments. Mock signals only affect this viewer.

## Arrow practice mode

Tap **Play arrows · rhythm practice**, then **Start / Restart**. A three-second countdown leads into a 32-step pattern. Yellow arrows rise toward the four fixed targets at the top. Step on the matching mat arrow as they meet, or tap the corresponding lane on the screen.

- Tempo: 60–120 BPM; slider changes apply to the next round.
- Excellent: within 70 ms, worth 100 points.
- Good: within 150 ms, worth 60 points.
- Miss: an unhit note more than 150 ms past its target; resets the combo.
- Each press can score one note. Holding a control does not repeatedly score.
- Pause / Resume preserves the round. Backgrounding the app pauses automatically.
- Optional **Beat click** plays a quiet metronome using the tablet's media volume. No songs are bundled.
- **Pad viewer** or Android Back returns to the diagnostic viewer.

The practice screen uses the same bridged or directly connected controller input as the viewer. Touch controls are always available in practice. Synchronized PC detection timestamps are used for bridged input once clock synchronization is ready. Local input and the synchronization warmup use tablet arrival timing.

## Input timestamps and clock synchronization

The PC relay records a high-resolution timestamp when it detects a changed controller state. On this Windows setup it uses Python's `perf_counter_ns()` (Windows QueryPerformanceCounter). These are PC detection times; the mat exposes no hardware press timestamp through the Windows API.

Each bridge state packet includes protocol version 2, a sequence number, `pc_detected_ns`, and `pc_sent_ns`. A heartbeat repeats the same sequence number and detection timestamp, with a fresh send timestamp. A heartbeat therefore cannot replay a press. The PC log records the sequence and detection timestamp for changed states.

The app sends periodic synchronization requests. The PC replies with its receive/send timestamps, and the tablet records its own send/receive times. After three valid samples, the app uses the lowest-round-trip sample in a rolling window to estimate the difference between PC and Android monotonic clocks. Synchronization continues during use and resets on reconnect.

The viewer displays synchronization status, round-trip time (RTT), estimated one-way transit delay, recent transit jitter (max minus min over up to 32 packets), and an estimated clock uncertainty. One-way delay is an estimate: unequal send/return delays can bias clock alignment. The uncertainty display includes half the measured RTT plus 1 ms for Android timer resolution; it is not a guarantee of physical press accuracy.

Rhythm scoring converts the PC detection timestamp into the tablet's game clock. Misses wait an additional **250 ms** for in-flight bridge events; this does not widen the 70 ms Excellent or 150 ms Good judgment windows. An event's detection time must still fall inside the original judgment window. Events more than 250 ms old, implausibly future-dated events, and packets predating the current round/resume cannot score. The first state after connection establishes a held-button baseline and does not score. An old relay or incomplete synchronization uses arrival timing, with that fallback shown in the viewer.

After a successful hit, practice mode briefly shows its signed timing error (negative = early, positive = late) separately from the input delivery delay. Clock synchronization cannot remove USB polling/sensor delay before the PC detects a change.

Tests include clock-origin conversion, high-RTT outliers, drift refresh, protocol heartbeats, malformed requests, delayed event scoring, stale/future/duplicate rejection, and pause/restart boundaries. To run the end-to-end check on the connected tablet:

```powershell
python tests\verify_timestamped_scoring.py
```

That check temporarily uses a clearly labelled PC TEST source, delivers a timestamped press 200 ms late, verifies its scoring on the tablet, then restores the real relay connection.

Timing logic tests are in `android-pad/engine-tests/RhythmEngineTest.java` and can run with a standard JDK, without an emulator. They cover judgment boundaries, duplicate hits, wrong lanes, missed notes, pause/resume, tempo changes, round completion, and restart.

## Confirmed mat mapping

| Area | Windows signal |
| --- | --- |
| Left | B1 |
| Down | B2 |
| Up | B3 |
| Right | B4 |
| Square | B5 |
| Triangle | B6 |
| Cross | B7 |
| Circle | B8 |
| Select | B9 |
| Start | B10 |
| Center | Y+ / POV down |

USB identifier: `VID_0079 / PID_0006`. Windows exposes 12 raw buttons; B11 and B12 have no assigned pad areas.

## Project files

| File | Purpose |
| --- | --- |
| `pad_monitor.py` | Windows Tkinter viewer and joystick-reading functions |
| `pad_mapping.json` | Saved Windows area mappings |
| `pc_pad_bridge.py` | Windows-to-Android signal relay; imports `pad_monitor.py` |
| `start_pad_bridge.ps1` | Start the relay and configure USB forwarding |
| `DancePad.apk` | Installable Android app, version 1.3 |
| `android-pad/` | Complete Android source and Gradle wrapper |
| `pad_bridge.log` | Relay connection and signal diagnostics |
| `extract_patterns.py` | Extract the real ITGmania SSC step charts into JSON |
| `patterns/` | 251 extracted charts, originals, indexes, and format documentation |

The [extracted patterns](patterns/README.md) include 175 four-arrow charts and a separate index of 70 beginner/easy options. They preserve note sequences and timing, independently of the Android app's current generated practice pattern. Recreate the export with `python extract_patterns.py --fetch-repo`.

## Build Android from source

Requires JDK 17 or newer and an Android SDK with platform 36. The app supports Android 11 and newer. This PC's SDK path is recorded in `android-pad/local.properties`; adjust it on another machine.

```powershell
cd C:\Users\paul\Projects\dancepad\android-pad
.\gradlew.bat :app:assembleDebug
```

The output is `app\build\outputs\apk\debug\app-debug.apk`. Copy that file over the root `DancePad.apk` after making changes if you want the distribution copy updated too.

## Diagnostics

```powershell
python pad_monitor.py --check
python pad_monitor.py --smoke-test
adb -s 4c637b9e reverse --list
Get-Content .\pad_bridge.log -Tail 20
```

If the tablet says reconnecting, check USB debugging, the USB cable, and rerun the launcher. If the PC detects no mat, reconnect it and run the Python check. To run the relay in the foreground instead of the background, stop the existing relay process using the PID printed by the launcher, then run `python pc_pad_bridge.py`; Ctrl+C stops that foreground relay.

The original ITGmania game installation remains at `%LOCALAPPDATA%\Programs\itgmania`; its desktop shortcut and game settings are independent of this project.
