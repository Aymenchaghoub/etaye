#!/usr/bin/env python3
"""Read-only Figma evidence capture. Credentials and temporary URLs are never logged."""
import argparse
import hashlib
import json
import re
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

FILE_KEY = "vfh4BSnbRAbaDE1q0CyssL"
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/design/figma"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--token-file", required=True, type=Path)
    parser.add_argument("--cached-file", type=Path)
    args = parser.parse_args()
    content = args.token_file.read_text().strip()
    match = re.search(r"figd_[A-Za-z0-9_-]+", content)
    token = match.group(0) if match else content.strip("\"'")

    def api(path, **query):
        suffix = "?" + urlencode(query) if query else ""
        req = Request("https://api.figma.com/v1/" + path + suffix,
                      headers={"X-Figma-Token": token})
        with urlopen(req, timeout=45) as response:
            return json.load(response)

    def save(name, data):
        p = OUT / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")

    data = json.loads(args.cached_file.read_text()) if args.cached_file else api("files/" + FILE_KEY)
    # Keep design properties, not account metadata or temporary service URLs.
    save("file-snapshot.json", {k: data[k] for k in
         ("name", "version", "lastModified", "document", "styles", "components", "componentSets") if k in data})
    manifest = {"fileKey": FILE_KEY, "version": data["version"],
                "lastModified": data["lastModified"], "consulted": datetime.now(timezone.utc).date().isoformat(),
                "method": "read-only REST API", "assets": [], "limitations": []}
    for endpoint in ("comments", "versions"):
        try:
            obj = api("files/" + FILE_KEY + "/" + endpoint)
            fields = ("id", "message", "created_at", "resolved_at", "client_meta", "parent_id") if endpoint == "comments" else ("id", "created_at", "label", "description")
            save(endpoint + ".json", [{k: row[k] for k in fields if k in row} for row in obj.get(endpoint, [])])
            if obj.get("pagination"):
                manifest["limitations"].append(endpoint + ": first API page only; inspect pagination before claiming complete history")
        except HTTPError as exc:
            manifest["limitations"].append(endpoint + ": HTTP " + str(exc.code))

    exports = {
        "svg": {"4:13": "logo-light", "4:19": "logo-monochrome", "4:20": "logo-patient",
                "4:33": "logo-dark", "4:37": "mark-light", "4:41": "mark-monochrome",
                "4:42": "app-icon-light", "4:47": "app-icon-dark"},
        "png": {"3:2": "colors-light", "3:87": "colors-dark", "3:172": "typography",
                "4:2": "logos", "5:22": "buttons", "5:46": "statuses", "6:2": "source-annotation"}
    }
    for fmt, nodes in exports.items():
        try:
            result = api("images/" + FILE_KEY, ids=",".join(nodes), format=fmt, scale=1)
            for node, name in nodes.items():
                url = result.get("images", {}).get(node)
                if not url:
                    manifest["limitations"].append("No export: " + node)
                    continue
                with urlopen(url, timeout=45) as response:
                    blob = response.read()
                relative = Path("assets" if fmt == "svg" else "screenshots") / (name + "." + fmt)
                path = OUT / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(blob)
                manifest["assets"].append({"nodeId": node, "file": str(relative),
                                           "sha256": hashlib.sha256(blob).hexdigest(),
                                           "visualReview": "pending"})
        except HTTPError as exc:
            manifest["limitations"].append(fmt + " export: HTTP " + str(exc.code))
    save("manifest.json", manifest)
    print(json.dumps({"ok": True, "version": data["version"], "exports": len(manifest["assets"]),
                      "limitations": manifest["limitations"]}))


if __name__ == "__main__":
    main()
