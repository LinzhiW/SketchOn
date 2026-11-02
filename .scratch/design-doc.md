# Spatial App Design Doc — Gemtronic Ring

## 1 · Frame

**Subject** — Gemtronic is a smart ring whose tiny round LCD is worn like a gem. This app puts the ring's 3D model on the wearer's real hand.

**Viewer & context** — The ring's designer, then people trying it. Standing or seated, hand raised about 35 cm from the eyes, real room visible.

**The single job** — Feel the ring on your own finger and tip it to change its screen.

**Spatial justification**

| Core task | Affordance | Why spatial beats flat | 2D counterfactual |
| --------- | ---------- | ---------------------- | ----------------- |
| See the ring worn | body, scale | The ring sits on your own finger at its place on the body | A turntable render in a browser: the object is seen, never worn |
| Change the screen | body, motion | The same finger tip that drives the real ring drives this one | Arrow buttons or a swipe, which teach nothing about the real gesture |

These tasks need the real hand in view and hand joint data, so the form is a Stage in Mixed style (Full Space, real room visible). A Volumetric window would clip and cannot read hand data.

**Assumptions**

| Assumption | Confidence | What changes if wrong |
| ---------- | ---------- | --------------------- |
| A 3x ring and a large screen copy read better than true size | Medium | Set `displayScale` to 1 and drop `ring-mirror` |
| 25° trigger and 10° reset feel right | Medium | Tune the two values in `ring-on-hand.props` |
| The chosen finger, first segment | High | Change `joint`; ring position shifts |
| The menu is the website simulator's map of the firmware | High | Edit `dataCases.normal.menu` |
| Double click in the air leaves game and rhythm screens | Medium | The real ring ignores gestures there and needs a button |

## 2 · Explore — alternatives

**Direction generation** — inline-isolated: each was developed alone before comparing, because this session spawns no subagents without the user asking.

### Direction A — The ring is the only UI

Stage Mixed. The ring sits on the finger, and its screen is the whole interface. One short hint shows until the hand appears. Risk: a small screen may be hard to read.

### Direction B — Ring plus floating magnifier

Stage Mixed. The ring sits on the finger, and a large copy of the screen floats above the hand. Reads easily, but the eye leaves the ring, and the magic of the small worn object goes.

### Direction C — Showroom table

Volumetric window. A large ring on a turntable, driven by hand tilt from a distance. Comfortable and stable, but the ring is never worn, which is the whole brief.

**Decision matrix**

| Direction | Task efficiency | Spatial value | PICO comfort | Domain fit | Risk | Distinctiveness |
| --------- | --------------- | ------------- | ------------ | ---------- | ---- | --------------- |
| A         | High            | High          | Medium       | High       | Readability | High |
| B         | High            | Medium        | Medium       | Medium     | Split attention | Medium |
| C         | Medium          | Low           | High         | Low        | Misses the brief | Low |

**Chosen: A** — It is the only direction where the worn object carries the experience, and its readability risk is handled by a 1.5x display scale.

**Rejected** — B adds a second screen the real ring does not have. C never puts the ring on the hand.

## 3 · Plan

**Executable spec** — `design-spec.json`, schema version `1.0`, revision `11`.

### Visual tokens

All 16 `ColorScheme` roles inherit their same-name SpatialUI Vibrant role, unchanged. The hint uses `labelPrimaryLight` text.

**Brand / decorative tokens outside `ColorScheme`**

| Token name | Exact hex | Used for |
| ---------- | --------- | -------- |
| `ringBand` | `#3A3A40` | Preview drawing of the band only; the app shows the real model |
| `ringBezel` | `#B9B9C0` | Preview drawing of the bezel only |
| `ringScreenBase` | `#0B120D` | Screen backing behind page images; hint panel fill, kept dark so white text stays readable in any room |

**Type** — `title` and `body` styles; the hint uses `sui-text` title and body variants.

**Depth** — The ring lives at the finger. The hint panel sits 0.8 m in front of the viewer and 0.1 m below eye height.

**Window-background premise** — No window background: the Stage shows the real room.

### Signature element

The ring's lit screen is the only UI. Nothing else appears while the ring is worn.

### Surface sizing

| Window / surface | Type | Content | Default | Min | Max |
| ---------------- | ---- | ------- | ------- | --- | --- |
| `stage-scene` | Stage (Mixed) | Ring and hint | 1.2 m cube, nominal | same | same |

### Layout

```
real room
        [ Raise your hand to put on the ring ]      <- only until a hand is seen

              (ring on finger)           <- primary focus once worn
```

### State graph

| State | Primary task | Primary focus | Components | Data deps | Entry/exit |
| ----- | ------------ | ------------- | ---------- | --------- | ---------- |
| `hand-searching` | Raise the hand | Hint | `hand-hint` | none | App start, or hand lost |
| `ring-worn` | Tip to change screen | Ring | `ring-on-hand` | `pages`, `activePageIndex` | Hand seen; exits when hand lost |
| `tracking-unavailable` | Fix permission | Message | `tracking-message` | tracking status | Hand data refused |

| Transition | From → To | Trigger | Explicit confirm? |
| ---------- | --------- | ------- | ----------------- |
| `hand-found` | searching → worn | Hand data arrives | No |
| `hand-lost` | worn → searching | Hand gone for 0.3 s | No |
| `tracking-denied` | searching → unavailable | Provider reports no permission | No |

### Components

- `ring-on-hand` — wears the ring and shows the page. Data: hand joints and `pages`. States: hidden, worn.
- `hand-hint` — tells the user what to do. Data: fixed copy.
- `tracking-message` — explains a tracking failure. Data: fixed copy.

### SpatialUI Web component map

| Planned element | SpatialUI tag | API checked in | Public event/state | Custom fallback reason |
| --------------- | ------------- | -------------- | ------------------ | ---------------------- |
| Hint text | `sui-text` | `assets/spatialui-web/js/text.js` | none | n/a |
| Failure message | `sui-text` | `assets/spatialui-web/js/text.js` | none | n/a |
| Worn ring | none | n/a | custom events | No control equivalent: a 3D object on a hand joint |

## 4 · Critique (before build)

**Generic-default simulation** — A lazy version would float a dashboard with page buttons, a tilt meter and a title beside the hand.

**Where the plan risked that default, and what I changed.** I dropped page dots and a tilt gauge. The ring's own screen is the feedback.

**Hard checks**

| Check | Pass? | Evidence |
| ----- | ----- | -------- |
| ≥3 real alternatives with rejection reasons | Yes | A, B, C differ in form and interaction |
| Every core task has a 2D counterfactual | Yes | Section 1 table |
| Form/scale follows the spatial justification | Yes | Needs real hand and joint data |
| Every window/surface size was internally validated | Yes | Stage has no window; ring size comes from the model |
| Attachments justified vs None/in-place | Yes | None; only one hint panel |
| Domain-swap test | Yes | Tied to the ring's gesture and screen |
| Every core component has data source + task | Yes | Components section |
| All 16 `ColorScheme` roles are explicitly defined | Yes | `theme.colorScheme` |
| Every standard control/system surface maps to SpatialUI Web | Yes | Component map |
| `design-spec.json` is valid and all references resolve | Yes | Validated with the schema |
| Surface-discipline check passes | Yes | Script run |
| Repeated-item depth is state/data-driven | Yes | No repeated items |
| Single primary focus / clear decision | Yes | The ring |

## 5 · Build — coverage manifest

| # | Kind | JSON ID/path | Implemented in preview.html |
| - | ---- | ------------ | --------------------------- |
| 1 | surface | `stage-scene` | `#stage-scene` |
| 2 | state | `hand-searching`, `ring-worn`, `tracking-unavailable` | `data-state` on `#app` |
| 3 | transition | `hand-found`, `hand-lost`, `tracking-denied` | Pointer enter, pointer leave, error data case |
| 4 | action | `show-next-page`, `show-previous-page` | Drag on the stage past the trigger angle |
| 5 | node | `ring-on-hand`, `hand-hint`, `tracking-message` | `[data-node-id]` |
| 6 | binding | `data.pages`, `state.activePageIndex` | Ring screen image |
| 7 | asset | `page-gem`, `page-clock`, `page-pet` | Inlined images |

## 6 · Critique again

**Independent coverage rebuild** — Rebuilt from `design-spec.json` by `verify_preview.py`-style checks in the browser: every state reached, every page shown, all data cases rendered.

**Quality bar**

| Criterion | Met? | Evidence |
| --------- | ---- | -------- |
| App's job is a clear decision/task outcome | Yes | One gesture, one result |
| Form/scale follows the spatial justification | Yes | Stage Mixed |
| Spatialization justified affordance-by-affordance | Yes | Section 1 |
| ≥3 alternatives compared, rejects have reasons | Yes | Section 2 |
| Every window/surface records final default/min/max dimensions | Yes | Surface sizing |
| Design derives from domain | Yes | Ring gesture and firmware screens |
| `design-spec.json` is schema-valid and all references resolve | Yes | Schema run |
| Embedded JSON exactly matches `design-spec.json` | Yes | Generated from the file |
| SpatialUI bundle is inline; mapped tags are registered | Yes | Browser check |
| No unjustified native/hand-rolled duplicate of an available `sui-*` control | Yes | Only `sui-text` and the ring |
| Root leaves the system window background unchanged | Yes | No app root fill |
| Structural regions are transparent; no nested app-authored surfaces | Yes | Discipline script |
| Preview backgrounds use the canonical path | Yes | `data-design-surface` only |
| No app-authored content borders | Yes | None |
| No design/debug-only controls appear | Yes | Test hook is script-only |
| preview.html is a triggerable state machine | Yes | Browser check |
| Rendered page passes visual review | Yes | Inspected in the browser pane |
| Signature present, surroundings quiet | Yes | Ring alone while worn |

**Patch log**

| Round | Problem | JSON target | Expected improvement | Revision | Re-ran? |
| ----- | ------- | ----------- | -------------------- | -------- | ------- |
| 1 | Hint text too faint on a bright background | `hand-hint`, `tracking-message` fill and size | Readable in any room | 2 | Yes |
| 2 | The app should be in English | All node text and asset alt text | English UI | 3 | Yes |
| 3 | The ring needs the real gestures and menu, a finger choice, and instructions | States, actions, panels, data | Same use as the real ring | 4 | Yes |
| 4 | Finger choice should be a hand shape, no hand question, index by default | `hand-picker` replaces the two selectors | Faster, no reading | 5 | Yes |
| 5 | The gesture guide sat outside the field of view | `gesture-guide` width and placement | Visible without turning the head | 6 | Yes |
| 6 | The ring screen was unreadable at arm's length | `displayScale` and new `ring-mirror` | Screen always readable | 7 | Yes |
| 7 | Gestures need large movements on the emulator, so they cannot be the only input | New `control-panel` on the left; ring at 3.5x | A reliable way to use the ring | 8 | Yes |
| 8 | 3.5x was too large on the hand | `displayScale` | Ring fits better | 9 | Yes |
| 9 | Panels merged into one window on a wrong diagnosis, then restored | No net change | Keeps the tested layout | 11 | Yes |

**Device validation** — `not_performed` (Web validates logic and layout only; real-device comfort, occlusion, size and performance are not verified).

**Verdict** — pass
