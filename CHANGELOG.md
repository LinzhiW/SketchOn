# Changelog

## V3 (3.0) — 2026-11-09

The project grew from a single smart ring into **SketchOn**: sketch a ring, watch an AI render follow your lines, turn it into 3D, colour it, and wear it on your hand.

### New

- **Home and Gallery.** The app opens on *Draw a ring* and *Gallery*. The Gallery holds the Gemtronic ring and every piece made so far, each with its name and the render it was made from.
- **Drawing panel with a live render.** Draw on the left; FLUX.2 Klein renders the drawing on the right while you draw (about 0.8 s per image on fal).
- **Material, style and view as separate choices.** Material: clay, resin, or metal (gold, silver, rose gold, platinum). Style: refined, cute cartoon, vintage, minimal, bold. View: band shape, top & stone, side profile. Each view shows a small 3D picture of which part of the ring it shapes, and a grey band guide on the canvas.
- **Drawing help.** *Smooth* removes wobble from dense strokes. *Snap shapes* turns a stroke that goes nearly all the way round into a true circle, and closes strokes whose ends nearly meet.
- **Make it 3D, named while it is made.** The render goes to Tripo through fal (1 to 2 minutes). The 3D job starts at once, and the user names the piece meanwhile; the piece is then shared in the Gallery under that name.
- **Automatic fit.** The server finds the ring's hole in the generated mesh and poses every model the same way: hole along the finger, stone on top, outer diameter fixed. Tripo returns rings standing, lying flat or turned, so its own pose is never trusted.
- **Colour before wearing.** Clay and resin rings render white. Before choosing a finger, the ring is held still beside the panel; turn it, choose the band or the decoration, and colour each part with a colour wheel. The server splits each ring into those two parts.
- **Wear it.** Choose a finger and raise your hand; the ring follows that finger, as the Gemtronic ring does.

### Fixed

- Ring screens and app icons were Git LFS pointer files instead of images after a repository move, so the Gemtronic screens failed to load.
- Generated rings sat about 20 cm off the finger and in the wrong pose: PICO's GLB loader ignores a node `matrix`, and setting the loaded node's scale overwrote the pose. The pose is now written as translation, rotation and scale, and the size is set on a holder entity.
- On the emulator a drag delivers only a down, one move and an up. Strokes now end where the pointer is released, and only dense strokes are smoothed so few-point strokes stay where they were drawn.
- The drawing panel is cut off beyond a fixed height and width; it is now two fixed-width columns.

### Architecture

- `app/`: PICO Spatial app (Kotlin, Compose, Spatial SDK 6.1.9). State in `RingWearViewModel`; drawing help in `ShapeStrokeUseCase`; server client in `data/remote/SketchOnServer.kt`.
- `server/`: Node render server. Keeps the fal key off the headset. `/api/render` (live render), `/api/to3d` and `/api/jobs/ID` (Tripo job), `/api/jobs/ID/name`, `/api/library`. `ringfit.js` poses models; `parts.js` splits them into band and decoration.
- Tests: 44 app unit tests and 22 server tests. Paid APIs are never called in tests.

### Known limitations

- Tested on the PICO Emulator only, not on a headset.
- The render server must run on a computer with a fal API key. The app reaches it at `10.0.2.2:8787` on the emulator; a headset build needs `-PsketchonServer=http://<computer>:8787`. The Gemtronic ring works without the server.
- Drawing on the emulator is coarse, since it sends very few points per drag.
- Colour is per part (band, decoration), not by brush. Brush painting on the 3D ring is planned for V4.
- The band still draws over the finger (no occlusion), and rings are shown at 3x for the emulator's arm's-length view.
- The Gallery is one shared list on the server; there are no accounts yet.

## V2 (2.0) — 2026-11-01

- The Gemtronic smart ring on your hand in PICO, following the chosen finger through hand tracking.
- The real ring's 25 firmware screens and its gestures (tip to change pages, click in the air to open or go back), with on-screen buttons as a backup.
