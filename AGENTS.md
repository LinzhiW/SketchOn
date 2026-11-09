# SketchOn

## What this project does

SketchOn lets people sketch jewelry or other small wearable things in VR, see an AI render while they draw, turn it into a 3D model, and try it on. The folder was called GemtronicRing until 2026-11-07; the Android package id still says `gemtronicring`.

The first piece is the Gemtronic smart ring, which already works: a PICO Spatial app that puts the ring on the wearer's real hand. The user taps a finger on a hand shape, and the ring sits on that finger of whichever hand is raised. The ring runs the real menu with the same four gestures as the real ring. A left panel gives the same four inputs as buttons, because gestures can be missed.

## Why this structure

- Stage in Mixed style: hand joint data needs Full Space, and the real hand must stay visible.
- Kotlin Spatial SDK, not Unity: its emulator delivers hand joints through the computer's camera.
- The screen is a separate disc entity, because the model's screen mesh has no texture coordinates.

## Key files

- `ui/ringwear/components/RingOnHand.kt`: reads the hand, sends events, places the ring on the finger.
- `ui/ringwear/components/RingRig.kt`: loads the ring, adds one screen disc per firmware frame. Model measurements live here.
- `domain/usecase/RingGestureUseCase.kt`: the ring's gestures, ported from `firmware/core/menu_gesture.h` in the Aspirium repo.
- `domain/usecase/NavigateMenuUseCase.kt` and `data/repository/RingMenuRepository.kt`: the ring's menu.
- `ui/ringwear/components/HandPicker.kt`: the hand shape for choosing a finger.
- `ui/ringwear/RingWearViewModel.kt`: app state.
- `ui/ringwear/components/HintPanel.kt`: the panels: finger choice, gesture guide on the right, buttons on the left.
- `ui/ringwear/components/RingMirror.kt`: a large live copy of the ring screen in the side panel.
- `.scratch/design-spec.json`: accepted design. `.scratch/design-doc.md` explains why.

## Rules

- All 2D UI uses SpatialUI inside `PicoTheme`. No Material or Material3.
- `ring.glb` units are millimetres. The ring hole runs along model Z, centre about (-0.21, -8.02), inner radius about 8 mm.
- Joint axes were measured on the emulator: -Z runs along the finger toward the tip, +Y faces the back of the hand. The ring model is turned half a turn so the charging port sits at the bottom.
- Load textures off the main thread. Each takes about a second on the main thread, and 25 of them froze the app until the system reported it as not responding.
- `dumpsys input` includes a snapshot of the last ANR, which looks like live state. Check its timestamps and `/data/anr` before trusting it. An unanswered ANR dialog also stays around and reappears when the app is relaunched.
- Never swap materials on a ModelComponent to change pages. The SDK releases the material taken off, and reusing it crashes. Each page has its own screen disc.

## Known gaps

- The tip rule uses the firmware's numbers. The air click cannot: the ring reads acceleration, and hand tracking only gives positions. `DIP_SPEED_M_S` and the numbers beside it were not measured.
- The real ring ignores gestures inside game and rhythm screens and leaves them by button. There are no buttons here, so a double click in the air goes back, as in the website simulator.
- Only the right hand has been measured. Left-hand tip direction is unverified.
- Occlusion is not done: the band draws over the finger. The Editor's Occlusion Surface material is the likely fix.
- PICO does not support glass transmission, so the dome uses a translucent material instead.
- Follow and tilt are verified on the emulator only. The blue skeleton there is an emulator overlay; turn off Skeleton display to see the ring.
- Needs a real headset to judge fit, size and comfort. The ring is shown at 3x because the emulator holds the hand about 0.75 m away; a headset holds it closer, so this scale may be too large there.
- Emulator hand tracking runs near 30 samples a second, jumps, and reads roll poorly. In a recording, 25° tips almost never registered, and 20° misfired while the hand was still. So tips are unreliable on the emulator. Keep 25° and 10° and judge them on a headset. `TRACKING_JUMP_M_S` drops the jumps.
- Gestures read the worn finger, as the real ring's sensor does. Do not switch them to the palm.

## Render server (`server/`)

A Node server that keeps the fal key off the headset. `POST /api/render` turns a sketch into a render; `GET /` is a sketch test page. FLUX.2 Klein 4B renders live while drawing (about 0.8 s); Nano Banana 2 makes one faithful render on request (about 11 s, $0.08). The key lives in `server/.env.local`, which git ignores. Never print it.

```bash
npm --prefix server start
npm --prefix server test
```

## Sketch to try-on (V3)

- Home: Draw a ring, or Gallery (the Gemtronic ring plus every piece made so far, from `GET /api/library`; no accounts yet).
- Drawing: material (clay, resin, metals), style (refined, cute, vintage, minimal, bold) and view (front, side, top) are separate choices, combined into the prompt on the server. Each view shows a grey ring guide that is sent with the drawing.
- Drawing help (`ShapeStrokeUseCase`): Smooth averages out wobble; Snap shapes turns a near-circle into a true circle (least-squares fit) and closes strokes whose ends nearly meet. It acts on release, because the emulator sends too few points to detect a hold.
- Make it 3D: Tripo on fal, 1 to 2 minutes, about $0.30. The server poses every model (`server/src/ringfit.js`): it finds the hole in the mesh, so hole along Z, centre at origin, stone up, outer diameter 1. Tripo returns rings standing or lying flat, so never trust its pose.
- The app reaches the server at `BuildConfig.SERVER_URL` (10.0.2.2:8787 on the emulator).
- Ring screens and icons are plain files in this repo. They were once Git LFS pointers by mistake, which made textures fail with "Unable to decode texture". Do not add LFS rules for them.

## Build and run

```bash
./gradlew assembleDebug testDebugUnitTest
pico-cli emulator start --format json -y
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.example.gemtronicring.p4e7a91c3 --activity .platform.LaunchActivity
```

In the emulator, open Setting > Labs and check Hand Tracking. It needs the computer's camera and eye-hand mode.

## Next steps

1. Hide the band behind the finger.
2. Check fit and size on a headset.
