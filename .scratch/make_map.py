"""Write design_implementation_map.json from what the finished app actually does."""
import hashlib
import json
from pathlib import Path

here = Path(__file__).parent
spec_path = here / "design-spec.json"
raw = spec_path.read_bytes()
spec = json.loads(raw)
SRC = "app/src/main/java/com/example/gemtronicring/p4e7a91c3/"

NODE_FIELDS = ("kind", "component", "rendererKey", "content", "assetId", "props", "bindings", "events",
               "layout", "appearance", "textStyle", "children", "visibleInStates", "accessibilityLabel")
nodes = {n["id"]: {k: n[k] for k in NODE_FIELDS if k in n} for n in spec["nodes"]}
surfaces = {s["id"]: {k: v for k, v in s.items() if k != "id"} for s in spec["surfaces"]}
states = {s["id"]: {k: v for k, v in s.items() if k != "id"} for s in spec["states"]}
transitions = {t["id"]: {k: v for k, v in t.items() if k != "id"} for t in spec["transitions"]}
actions = {a["id"]: {k: v for k, v in a.items() if k in ("type", "transitionId", "binding", "value", "payload")} for a in spec["actions"]}
assets = {a["id"]: {k: a[k] for k in ("kind", "src", "alt") if k in a} for a in spec["assets"]}

mappings = [
    {"file": SRC + "ui/ringwear/components/RingOnHand.kt", "symbols": ["RingOnHand", "wearOnFinger", "RaisedHand"],
     "evidence": ["HandTrackingProvider", "RING_PANEL_POSITION", "fingerSegment"],
     "design_refs": ["state-model:initial", "surface:stage-scene", "node:stage-root", "node:ring-on-hand"]},
    {"file": SRC + "ui/ringwear/components/RingRig.kt", "symbols": ["RingRig"],
     "evidence": ["DISPLAY_SCALE", "asset://ring.glb", "SCREEN_DIAMETER_MM"],
     "design_refs": ["asset:ring-model"] + ["asset:" + a["id"] for a in spec["assets"] if a["kind"] == "image"]},
    {"file": SRC + "ui/ringwear/components/RingMirror.kt", "symbols": ["RingMirror"],
     "evidence": ["MirrorSize", "FilterQuality.None"],
     "design_refs": ["node:ring-mirror"]},
    {"file": SRC + "ui/ringwear/components/HandPicker.kt", "symbols": ["HandPicker", "HandFinger"],
     "evidence": ["FingerSpots", "spatialHoverEffect", "drawRingMark"],
     "design_refs": ["node:hand-picker"]},
    {"file": SRC + "ui/ringwear/components/HintPanel.kt", "symbols": ["RingPanels", "FingerPanel", "GestureGuide", "ControlPanel", "ArrowButton", "HandHint", "TrackingMessage"],
     "evidence": ["control_title", "confirm_button", "back_button", "ic_arrow_left", "ic_arrow_right", "finger_title", "guide_next", "guide_previous", "guide_open", "guide_back", "guide_click", "wear_button", "change_finger", "hand_hint", "tracking_message"],
     "design_refs": ["node:finger-panel", "node:wear-button", "node:gesture-guide", "node:guide-next", "node:guide-previous", "node:guide-open", "node:guide-back", "node:guide-click", "node:change-finger-button", "node:hand-hint", "node:tracking-message", "node:control-panel", "node:control-arrows", "node:previous-button", "node:next-button", "node:confirm-button", "node:back-button", "asset:icon-arrow-left", "asset:icon-arrow-right"]},
    {"file": SRC + "ui/ringwear/RingWearViewModel.kt", "symbols": ["RingWearViewModel"],
     "evidence": ["RingPhase.FingerChoosing", "RingPhase.RingWorn", "RingPhase.HandSearching", "RingPhase.TrackingUnavailable", "navigate.move"],
     "design_refs": ["state:" + s["id"] for s in spec["states"]] + ["transition:" + t["id"] for t in spec["transitions"]]
     + ["action:" + a["id"] for a in spec["actions"]]},
    {"file": SRC + "data/repository/RingMenuRepository.kt", "symbols": ["BundledRingMenuRepository"],
     "evidence": ["home-pet", "face-gem-dragon", "circuit-beat"],
     "design_refs": ["data-case:normal", "data-case:fallback", "data-case:error"]},
]

receipt = {
    "schema_version": 1,
    "design_spec": {"path": "design-spec.json", "sha256": hashlib.sha256(raw).hexdigest()},
    "source_mappings": mappings,
    "implementation": {
        "initialStateId": spec["initialStateId"], "surfaces": surfaces, "nodes": nodes, "states": states,
        "transitions": transitions, "actions": actions, "responsiveRules": {},
        "dataCases": sorted(spec["dataCases"]), "assets": assets,
    },
}
(here / "design_implementation_map.json").write_text(json.dumps(receipt, ensure_ascii=False, indent=2), encoding="utf-8")
print("ok")
