# Dance Pad for Android

Native Android viewer matching the Windows pad monitor. No accounts or runtime permissions. PC bridge uses a local TCP connection through ADB's USB forwarding; it does not use an internet service.

- Hold any pad area to mock its signal; simultaneous touches work.
- Run demo cycles through the pad, including a left + right combination.
- Turn off Mock pad to use only live controller input.
- Connect a USB controller using a data/OTG adapter. Generic numbered buttons use the confirmed Windows layout: left B1, down B2, up B3, right B4, square B5, triangle B6, cross B7, circle B8, Select B9, Start B10; center positive Y.
- Android may assign different controller codes. Use Map real controller, tap an area, then press the physical pad area to learn its key or axis. Mappings persist in private Android SharedPreferences, independently of the Windows JSON file.
- Mock events only affect this viewer; they do not inject input into other apps.
- **Play arrows** opens a four-lane rhythm practice screen with rising notes, combo, score, adjustable 60–120 BPM tempo, and an optional beat click. Press Start / Restart for a 32-note round. Both real controller signals and touchscreen lane presses work.

Build: `./gradlew.bat :app:assembleDebug` with Android SDK 36 and JDK 17+. Install `app/build/outputs/apk/debug/app-debug.apk`.

USB hardware behavior cannot be verified until the adapter is available.

## Use the real mat through the PC (no OTG adapter needed)

1. Connect both the mat and the Xiaomi tablet to the PC by USB, with USB debugging authorized.
2. Run `./start_pad_bridge.ps1` from the parent folder. It starts a Python relay and forwards the tablet's local port 8765 to the PC using `adb reverse`.
3. Turn on **PC bridge** in the Android app. Mock mode turns off automatically. Your actual mat presses now highlight the tablet viewer.

The PC relay listens only on `127.0.0.1:8765`, reads the mat at 120 Hz, sends changes immediately, and sends heartbeats every half second. The app reconnects if the relay restarts and clears held signals if the stream times out. Run the launcher again after unplugging/reconnecting USB if forwarding was lost. The bridge affects this viewer only; it does not emulate a system-wide Android controller.

Version 1.3 adds PC detection timestamps, periodic two-way clock synchronization, and timestamp-aware practice scoring. The viewer reports clock sync, RTT, estimated transit delay, and jitter. A bounded 250 ms arrival grace accepts in-flight events while the original Excellent/Good judgment windows remain unchanged. The initial held state on connection does not score. See the parent README for the protocol, accuracy limits, fallback behavior, and device checks.

Relay diagnostics are in `../pad_bridge.log`. For a synthetic transport test, run `python pc_pad_bridge.py --demo` instead of the normal relay; the app labels this **PC TEST**.
