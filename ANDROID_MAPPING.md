# Android dance-pad mapping

## Device and connection

- Controller: **Microntek USB Joystick**, USB ID **`0079:0006`**.
- App: **Dance Pad 1.3**, package `dev.paul.dancepad`.
- Tablet tested: **Xiaomi Pad 7**, Android 16 / HyperOS `OS3.0.304.0.WOZEUXM`.
- Working setup: **mat → USB hub → USB-C data/OTG adapter → tablet**.
- Enable **Settings → More connection options → OTG** before connecting. German: **Einstellungen → Mehr Verbindungsoptionen → OTG**. Xiaomi says OTG switches off after 10 idle minutes.
- For physical USB input, leave **PC bridge** and **Mock pad** off; stop any demo.

See [USB hub troubleshooting](README.md#xiaomi-pad-7-usb-hub-workaround) for connection failures and recovery steps. A hub is a tested compatibility workaround on this tablet, not a universal Android requirement.

## Saved Android bindings

These bindings were read from the app's saved preferences on the Xiaomi and match the confirmed Windows pad layout.

| Physical area | App signal | Android key label / axis |
| --- | --- | --- |
| Left arrow | `B1` | `KEYCODE_BUTTON_1` |
| Down arrow | `B2` | `KEYCODE_BUTTON_2` |
| Up arrow | `B3` | `KEYCODE_BUTTON_3` |
| Right arrow | `B4` | `KEYCODE_BUTTON_4` |
| Square | `B5` | `KEYCODE_BUTTON_5` |
| Triangle | `B6` | `KEYCODE_BUTTON_6` |
| Cross | `B7` | `KEYCODE_BUTTON_7` |
| Circle | `B8` | `KEYCODE_BUTTON_8` |
| Select | `B9` | `KEYCODE_BUTTON_9` |
| Start | `B10` | `KEYCODE_BUTTON_10` |
| Center | `Y+` | Positive `AXIS_Y`, value greater than `0.5` |

`B11` and `B12` have no assigned physical areas. The controller also exposes hat axes, but the saved center binding is specifically `Y+`.

On the tested Xiaomi, `/system/usr/keylayout/Generic.kl` maps Linux joystick scan codes **288–299** to Android **BUTTON_1–BUTTON_12**. The app converts those Android key codes to `B1`–`B12`. The Windows button numbering therefore matches this Android configuration; it should not be assumed for every controller or Android key-layout file.

## Where mappings are saved

Android uses app-private SharedPreferences named **`pad_mapping`**:

```text
/data/user/0/dev.paul.dancepad/shared_prefs/pad_mapping.xml
```

The area names are stored as string sets, for example:

```xml
<set name="LEFT">
    <string>B1</string>
</set>
<set name="CENTER">
    <string>Y+</string>
</set>
```

The same preferences file also stores options such as `pc_bridge`. Android mappings are **separate from `pad_mapping.json`**, which belongs to the Windows viewer/bridge. This Markdown file is a reference, not an importable configuration file.

The installed debug build allows inspection using an authorized ADB connection:

```sh
adb devices -l
adb -s TABLET_SERIAL shell run-as dev.paul.dancepad cat shared_prefs/pad_mapping.xml
```

Use the tablet's current serial from `adb devices -l`; a Wi-Fi debugging IP/port can change after restarting. `run-as` requires a debuggable build and is not guaranteed for release APKs. Do not edit live preferences while the app is running.

## Remapping in the app

1. Open the pad viewer and stop the demo.
2. Tap **Map real controller**.
3. Tap the area to assign.
4. Press that area on the physical mat.
5. The binding saves automatically. Repeat for another area if needed.

Learning a signal removes that signal from other areas to avoid duplicate assignments. Existing bindings already match the confirmed layout, so no remapping was needed during inspection.

## Verification and limits

- Physical mat operation in Dance Pad on the Xiaomi was confirmed by the user with the hub attached.
- All saved area bindings were inspected over ADB and matched the table above.
- Android's generic joystick key labels were inspected and matched the app's `B1`–`B12` conversion.
- Each physical area was **not** individually retested during that mapping inspection.
- USB enumeration must succeed before mappings can work. If no Microntek controller appears, remapping cannot repair that USB connection failure.
