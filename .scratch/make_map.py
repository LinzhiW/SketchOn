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
    {"file": SRC + "ui/ringwear/components/RingOnHand.kt", "symbols": ["RingOnHand", "wearOnFinger"],
     "evidence": ["HandTrackingProvider", "attachments.entity(HINT_PANEL_ID)", "RING_PROXIMAL"],
     "design_refs": ["state-model:initial", "surface:stage-scene", "node:stage-root", "node:ring-on-hand"]},
    {"file": SRC + "ui/ringwear/components/RingRig.kt", "symbols": ["RingRig"],
     "evidence": ["DISPLAY_SCALE", "asset://ring.glb", "SCREEN_DIAMETER_MM"],
     "design_refs": ["asset:ring-model"]},
    {"file": SRC + "ui/ringwear/components/HintPanel.kt", "symbols": ["HandHint", "TrackingMessage"],
     "evidence": ["hand_hint", "tracking_message", "GemtronicColors.ringScreenBase"],
     "design_refs": ["node:hand-hint", "node:tracking-message"]},
    {"file": SRC + "ui/ringwear/RingWearViewModel.kt", "symbols": ["RingWearViewModel"],
     "evidence": ["RingPhase.RingWorn", "RingPhase.HandSearching", "RingPhase.TrackingUnavailable", "movedPage"],
     "design_refs": ["state:" + s["id"] for s in spec["states"]] + ["transition:" + t["id"] for t in spec["transitions"]]
     + ["action:" + a["id"] for a in spec["actions"]]},
    {"file": SRC + "data/repository/RingPageRepository.kt", "symbols": ["BundledRingPageRepository"],
     "evidence": ["pages/page_gem.png", "pages/page_clock.png", "pages/page_pet.png"],
     "design_refs": ["asset:page-gem", "asset:page-clock", "asset:page-pet", "data-case:normal", "data-case:fallback", "data-case:error"]},
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
