"""Render preview.html from design-spec.json. Scope: web_design_validation_only."""
import base64
import json
from pathlib import Path

HERE = Path(__file__).parent
SKILL = HERE.parent.parent / ".claude/skills/pico-spatial-app-designer"
spec_text = (HERE / "design-spec.json").read_text(encoding="utf-8")
spec = json.loads(spec_text)
ASSETS = HERE.parent / "app/src/main/assets"
images = {a["id"]: "data:image/png;base64," + base64.b64encode((ASSETS / a["src"]).read_bytes()).decode() for a in spec["assets"] if a["kind"] == "image"}
for a in spec["assets"]:
    if a["kind"] == "icon":
        images[a["id"]] = (SKILL / "assets/icon-7.0/svg" / (a["src"].rsplit("/", 1)[1] + ".svg")).read_text(encoding="utf-8")
bundle = (SKILL / "assets/spatialui-web/spatialui.bundle.js").read_text(encoding="utf-8")

RENDERER = (HERE / "renderer_v5.js").read_text(encoding="utf-8")

owners = "const surfaceOwners = {" + "".join(
    f"{json.dumps(n['id'])}: el => {{ el.dataset.designSurface = {json.dumps(n['id'])}; }},"
    for n in spec["nodes"] if n.get("appearance", {}).get("fill")) + "};"
RENDERER = RENDERER.replace("/*OWNERS*/", owners)
bundle_safe = bundle.replace("</script>", "<\\/script>")
html = f"""<!doctype html>
<html lang="zh"><head><meta charset="utf-8"><title>Gemtronic Ring</title>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
body {{ margin:0; min-height:100vh; display:grid; place-items:center; background-color:#dad6d3; background-image:none; font-family:sans-serif; }}
[data-design-surface] {{ background: var(--pico-design-surface-fill); }}
#app {{ touch-action:none; user-select:none; }}
#stage-scene {{ display:grid; place-items:center; max-width:100vw; }}
.overlay {{ display:grid; place-items:center; }}
.overlay > * {{ grid-area:1/1; }}
.overlay > [hidden], .column > [hidden] {{ display:none; }}
.column {{ display:flex; flex-direction:column; gap:16px; box-sizing:border-box; }}
[data-node-id="gesture-guide"] {{ justify-self:end; }}
[data-node-id="control-panel"] {{ justify-self:start; align-items:center; }}
.row {{ display:flex; gap:16px; justify-content:center; }}
</style>
<script>{bundle_safe}</script>
</head><body>
<main id="app" data-design-spec-revision="{spec['metadata']['revision']}"></main>
<script id="pico-design-spec" type="application/json">{spec_text}</script>
<script id="pico-images" type="application/json">{json.dumps(images)}</script>
<script type="module">{RENDERER}</script>
</body></html>
"""
(HERE / "preview.html").write_text(html, encoding="utf-8")
print("preview.html", len(html))
