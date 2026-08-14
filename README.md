# Live2D in Minecraft

> [中文文档 (Chinese)](README_zh.md)

A Fabric client mod that brings [Live2D Cubism 3](www.live2d.com) characters into Minecraft as a companion overlay on your screen.

## Features

- Render Live2D models as a HUD.
- Ships with 3 built-in models: `kaguya`, `noir`, `yachi`.
- Load your own Live2D Cubism 3 (`*.moc3`) models with no extra setup.
- Head / eye tracking based on your camera yaw & pitch, with smooth spring physics and configurable sway.
- Automatic blinking, breathing and idle body movement.
- Expressions and motions loaded from the model's `model3.json` (including `.exp3.json` and `.motion3.json` files).
- Sound-reactive blinking: the character closes its eyes while the game plays sound above a configurable threshold.
- Event system: bind game events (chat, damage, weather, swimming, ...) to motions, expressions or parameter overrides.
- Masked drawable rendering (Live2D clip masks), additive / multiplicative blending.
- Chat drag to reposition, scroll wheel to resize (while the chat is open).
- Edit mode for precise arrow-key positioning and `+/-` resizing.
- Runtime preview texture fallback if a model fails to load.
- All settings persist to a JSON config file.


## Controls

| Key | Action |
| --- | --- |
| `F9` | Toggle the overlay on / off |
| `F10` | Cycle through available models |
| `F11` | Cycle expressions |
| `F12` | Toggle edit mode |
| `Arrows` | Move (edit mode) |
| `+` / `-` | Resize (edit mode) |

In-game, while the chat window is open you can **drag** the character to move it and use the **mouse wheel** to resize it.

All keys can be rebinded under *Options → Controls → Key Binds → MISC*.

## Commands

All commands are client-side and begin with `/live2d`.

| Command | Description |
| --- | --- |
| `/live2d toggle` | Toggle the overlay |
| `/live2d status` | Show current state (model, size, position, flags, errors) |
| `/live2d models` | List available models |
| `/live2d model <name>` | Switch to a specific model |
| `/live2d motions` | List the current model's motions |
| `/live2d expressions` | List the current model's expressions |
| `/live2d expression <name>` | Set expression (`off` / `normal` / `none` clears it) |
| `/live2d size <height>` | Set model height in px (40–800) |
| `/live2d pos <x> <y>` | Set absolute position |
| `/live2d move <dx> <dy>` | Move the model by an offset |
| `/live2d sway <0.0–3.0>` | Head-tracking sway strength |
| `/live2d smoothing <0.02–0.6>` | Animation smoothing factor |
| `/live2d followpitch <true/false>` | Follow vertical camera movement |
| `/live2d blink <true/false>` | Enable / disable automatic blinking |
| `/live2d masks <true/false>` | Enable / disable clip masks |
| `/live2d chatdrag <true/false>` | Enable / disable chat drag & wheel resize |
| `/live2d soundblink <true/false>` | Enable sound-reactive blinking |
| `/live2d soundblink <true/false> <threshold>` | ... with a volume threshold (0–1) |
| `/live2d events` | List configured event bindings |
| `/live2d event <name> off` | Remove an event binding |
| `/live2d event <name> <motion\|expression\|param> <target> [value] [duration] [fade]` | Bind a game event to an action |

### Events

Available event names:

`chat_opened` · `chat_closed` · `screen_open` · `screen_close` · `join_world` · `hurt` · `death` · `respawn` · `attack` · `jump` · `land` · `swim_start` · `swim_end` · `underwater` · `surface` · `sneak` · `stand` · `sprint_start` · `sprint_end` · `rain` · `clear` · `thunder` · `night` · `day` · `low_health` · `high_health` · `volume_high` · `volume_low` · `idle`

Example — make the model play the `idle` motion whenever the player takes damage:

```
/live2d event hurt motion idle
```

## Configuration

Settings are stored in `<gameDir>/config/live2d.json` and rewritten automatically whenever a value changes.

```jsonc
{
  "enabled": true,
  "model": "kaguya",
  "size": 260,              // overlay height in px
  "posX": 16,               // overlay position
  "posY": 110,
  "swayStrength": 3.0,      // head-tracking sway strength
  "smoothing": 0.1,         // animation smoothing
  "followPitch": true,      // follow vertical look direction
  "blinkEnabled": true,     // automatic blinking
  "expressionsEnabled": true,
  "masksEnabled": true,     // clip mask rendering
  "showPreviewOnError": true,
  "hiddenDrawables": [],    // drawable ids to hide
  "chatDragEnabled": true,  // drag to move / wheel to resize in chat
  "wheelResizeStep": 8,
  "soundBlinkEnabled": false,
  "soundBlinkThreshold": 0.5,
  "events": { }             // event bindings, see /live2d event
}
```

## Adding Your Own Models

Place a model folder in `<gameDir>/config/live2d/<name>/` containing at least:

- `<name>.model3.json`
- `<name>.moc3`
- textures referenced by `FileReferences.Textures`

Optional files are picked up automatically: physics (`*.physics3.json`), expressions (`*.exp3.json`), motions (`*.motion3.json`), and `preview.png` (shown if loading fails).

The model will appear in `/live2d models` and can be selected with `/live2d model <name>`. Note that the Live2D Cubism Core library shipped with this mod is the **Windows x86_64** build, so custom models are currently only tested on Windows.

## Building from Source

Requirements: JDK 21.

```
git clone <this-repo>
cd Live2D
./gradlew build
```

The mod jar is produced at `build/libs/live2d-<version>.jar`. To launch a dev client:

```
./gradlew runClient
```

## Project Structure

```
src/main/java/com/ciallo/live2d/
├── client/          # Fabric client entrypoint, HUD renderer, commands, model loader,
│                    # event system, and the special GUI element renderer
├── cubism/          # JNA bindings to Live2D Cubism Core + native model / renderer,
│                    # motion & expression playback
└── config/          # JSON config (config/live2d.json)
src/main/resources/assets/live2d/
├── live2d/          # built-in models (kaguya, noir, yachi) and the native DLL
├── lang/            # en_us / zh_cn translations
└── icon.png
```

## Notes

- This is an experimental / work-in-progress mod (version `1b`); rendering is done by rasterizing the model into an off-screen buffer that is then composited by the GUI renderer.
- Only the Live2D Cubism 3 (`moc3`) format is supported.

## License

MIT — see [LICENSE](LICENSE). Live2D Cubism Core is the property of Live2D Inc.; built-in model assets belong to their respective authors.  Mod By P1ay2r.
