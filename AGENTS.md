# Gemtronic Ring on PICO

## What this project does

A PICO Spatial app that puts the Gemtronic smart ring on the wearer's real hand. Tilting the finger changes the ring's screen between 3 firmware images. It is a minimal proof of the two core ideas: the ring follows the hand, and tilt changes the page.

## Why this structure

- Stage in Mixed style: hand joint data needs Full Space, and the real hand must stay visible.
- Kotlin Spatial SDK, not Unity: its emulator delivers hand joints through the computer's camera.
- The screen is a separate disc entity, because the model's screen mesh has no texture coordinates.

## Key files

- `ui/ringwear/components/RingOnHand.kt`: reads the hand, sends events, places the ring on the finger.
- `ui/ringwear/components/RingRig.kt`: loads the ring, adds the screen disc, shows one screen disc per page. Model measurements live here.
- `domain/usecase/TiltPageUseCase.kt`: roll angle, 25° trigger, 10° reset, page wrap.
- `ui/ringwear/RingWearViewModel.kt`: app state.
- `ui/ringwear/components/HintPanel.kt`: the only 2D UI.
- `.scratch/design-spec.json`: accepted design. `.scratch/design-doc.md` explains why.

## Rules

- All 2D UI uses SpatialUI inside `PicoTheme`. No Material or Material3.
- `ring.glb` units are millimetres. The ring hole runs along model Z, centre about (-0.21, -8.02), inner radius about 8 mm.
- Joint axes were measured on the emulator: -Z runs along the finger toward the tip, +Y faces the back of the hand. The ring model is turned half a turn so the charging port sits at the bottom.
- Never swap materials on a ModelComponent to change pages. The SDK releases the material taken off, and reusing it crashes. Each page has its own screen disc.

## Known gaps

- Occlusion is not done: the band draws over the finger. The Editor's Occlusion Surface material is the likely fix.
- PICO does not support glass transmission, so the dome uses a translucent material instead.
- Follow and tilt are verified on the emulator only. The blue skeleton there is an emulator overlay; turn off Skeleton display to see the ring.
- Needs a real headset to judge fit, size and comfort.

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
2. Add the remaining firmware screens.
3. Check fit and size on a headset.
