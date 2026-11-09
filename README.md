# SketchOn

Sketch a piece of jewelry in mixed reality, watch an AI render follow your lines as you draw, turn it into a 3D model, and try it on your own hand.

SketchOn is a [PICO](https://developer.picoxr.com/) Spatial app. It is an early work in progress.

## What works now (V3)

1. **Draw.** Sketch a ring on a panel in mixed reality. Choose its material (clay, resin, or metal), style and view; an AI render (FLUX.2 Klein on [fal](https://fal.ai)) follows your lines while you draw.
2. **Make it 3D.** The render becomes a 3D model with Tripo (1 to 2 minutes). You name the piece while it is made, and it is shared in the Gallery.
3. **Colour it.** Clay and resin rings come out white. Turn the ring in front of you and colour its band and its decoration separately.
4. **Wear it.** Choose a finger and raise your hand; the ring follows that finger. The server finds the ring's hole in the mesh and poses every model so it sits on the finger the right way up.
5. **Gallery.** Try on the Gemtronic smart ring, with its real screens and gestures, or any piece made so far.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each version and the known limitations.

## Planned

- Brush painting directly on the 3D ring.
- Testing on a headset, and hiding the band behind the finger.
- Accounts and a real community gallery.
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

The app reaches the server at `http://10.0.2.2:8787`, which is your computer as seen from the PICO emulator. For a headset, build with `./gradlew assembleDebug -PsketchonServer=http://<your computer's LAN address>:8787`. You can also open http://localhost:8787 on the computer to try the live render in a browser. The key stays on the server and is never sent to the browser or the headset. `.env.local` is ignored by git; never commit it.

## License

The source code is licensed under the [GNU General Public License v3.0](LICENSE).

The Gemtronic ring's 3D model and screen images are **not** open source. They are © Linzhi Wang, all rights reserved. See [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
