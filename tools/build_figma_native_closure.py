#!/usr/bin/env python3
"""Build a guarded local Figma correction plugin from pinned native evidence."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def build(before_path, plan_path, output, flow_path=None):
    before = json.loads(before_path.read_text(encoding="utf-8"))
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    if plan.get("issues") or len(plan["plans"]) != 52:
        raise ValueError("Expected the complete reviewed 52-state correction plan")
    roots = {r["nodeId"]: r for r in before["roots"]}
    original_file = json.loads((ROOT / "docs/design/figma/manifest.json").read_text(encoding="utf-8"))["fileKey"]
    if len(roots) != 456 or before["pageId"] != "15:2" or before["fileKey"] != original_file:
        raise ValueError("Incomplete native baseline")
    for item in plan["plans"]:
        expected = roots[item["rootId"]]
        if expected["state"] != item["state"] or expected["fingerprint"] != item["beforeFingerprint"]:
            raise ValueError("Correction plan does not match its native baseline")
    source = (ROOT / "design/figma-windows-import/code.js").read_text(encoding="utf-8")
    helpers = source[source.index("const stable ="):source.index("\n}", source.index("function nativeFingerprint(")) + 2]
    scope = {key: before[key] for key in ("fileKey", "pageId", "roots", "protectedPages")}
    runtime = (ROOT / "design/figma-native-closure/runtime.js").read_text(encoding="utf-8")
    code = "const SCOPE=" + json.dumps(scope, ensure_ascii=False) + ";\nconst PLAN=" + json.dumps(plan, ensure_ascii=False) + ";\n" + helpers + "\n" + runtime
    if flow_path:
        code = "const INLINE_FLOW_PLAN=" + flow_path.read_text(encoding="utf-8") + ";\n" + code
    # A new output directory preserves any registered plugin, manifest ID or prior result.
    output.mkdir(parents=True, exist_ok=False)
    (output / "code.js").write_text(code, encoding="utf-8")
    (output / "ui.html").write_bytes((ROOT / "design/figma-native-closure/ui.html").read_bytes())
    (output / "manifest.template.json").write_bytes((ROOT / "design/figma-windows-import/manifest.template.json").read_bytes())
    (output / "provenance.json").write_text(json.dumps({
        "beforeSha256": hashlib.sha256(before_path.read_bytes()).hexdigest(),
        "planSha256": hashlib.sha256(plan_path.read_bytes()).hexdigest(),
        "generatedCodeSha256": hashlib.sha256((output / "code.js").read_bytes()).hexdigest(),
        "applicationImplemented": False,
    }, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--before", type=Path, required=True)
    parser.add_argument("--plan", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--flow", type=Path)
    args = parser.parse_args()
    build(args.before, args.plan, args.output, args.flow)
