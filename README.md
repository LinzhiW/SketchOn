# SketchOn

Sketch a piece of jewelry in mixed reality, watch an AI render follow your lines as you draw, turn it into a 3D model, and try it on your own hand.

SketchOn is a [PICO](https://developer.picoxr.com/) Spatial app. It is an early work in progress.

## What works now

- **Wear a ring on your real hand.** Tap a finger on a hand shape, raise your hand, and a 3D ring sits on that finger and follows it. The first ring is the Gemtronic smart ring.
- **Control it like the real ring.** Tilt your finger to change pages, and click in the air to open or go back. A side panel has the same four controls as buttons, and another panel explains the gestures.
- **Live sketch rendering (test page).** A small server turns a sketch into a photoreal render through [fal](https://fal.ai). FLUX.2 Klein 4B renders while you draw (about 0.8 s an image). Nano Banana 2 makes one more faithful render on request (about 11 s).

## Planned

- A drawing panel inside the headset, wired to the render server.
- Turning a finished render into a 3D model, and fitting the ring hole to your finger automatically.
- More kinds of pieces than rings.

## Repository layout

| Path | What it is |
| --- | --- |
| `app/` | The PICO Spatial app (Kotlin, Jetpack Compose, PICO Spatial SDK 6.1.9) |
| `server/` | The render server and the sketch test page (Node.js) |
| `.scratch/` | Design notes and the accepted design spec |
| `AGENTS.md` | Notes for developers and coding agents: structure, rules, known gaps |

## Run the app

You need Android Studio's SDK, and the PICO emulator or a PICO headset.

```bash
./gradlew assembleDebug testDebugUnitTest
pico-cli emulator start --format json -y
pico-cli app install app/build/outputs/apk/debug/app-debug.apk
pico-cli app launch com.example.gemtronicring.p4e7a91c3 --activity .platform.LaunchActivity
```

In the emulator, open Setting > Labs and turn on Hand Tracking. It uses your computer's camera.

## Run the render server

You need Node.js 20.6 or newer and a [fal](https://fal.ai) API key. Rendering costs money on your fal account.

```bash
cd server
npm install
echo FAL_KEY=your-key-here > .env.local
npm start
```

Then open http://localhost:8787 and draw. The key stays on the server and is never sent to the browser or the headset. `.env.local` is ignored by git; never commit it.

## License

The source code is licensed under the [GNU General Public License v3.0](LICENSE).

The Gemtronic ring's 3D model and screen images are **not** open source. They are © Linzhi Wang, all rights reserved. See [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
