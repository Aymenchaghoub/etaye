#!/usr/bin/env python3
"""Generate and verify the token tables of docs/design/design-system.md.

The tables come from the received CSS tokens and are cross-checked against the values
resolved from Figma (docs/design/figma/resolved-tokens.json). A disagreement stops the
generation, so the document cannot silently drift from either source.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOKENS_CSS = ROOT / "design/received/2026-11-03/export/etaye-design-system/tokens/tokens.css"
ICONS_CSS = ROOT / "design/received/2026-11-03/icons.css"
RESOLVED = ROOT / "docs/design/figma/resolved-tokens.json"
DOC = ROOT / "docs/design/design-system.md"

# (Figma role, CSS variable, use). The use column restates the token reference and the received CSS.
ROLES = [
    ("surface/fond", "fond", "Quiet framing: application background, top bar, navigation."),
    ("surface/surface", "surface", "Work areas: main region, panels, fields, dialogs, drawers, secondary button."),
    ("surface/surface-2", "surface-2", "Secondary surface: hover, disabled and read-only fills, skeletons."),
    ("surface/papier", "papier", "Reading and document areas, sign-in and patient shells, excerpts."),
    ("texte/encre", "encre", "Primary text, primary button fill, selected tab underline, error border."),
    ("texte/encre-2", "encre-2", "Secondary text, labels, disabled text."),
    ("texte/encre-3", "encre-3", "Meta text: dates, origins, source references."),
    ("texte/encre-inverse", "encre-inverse", "Text on the ink fill (primary button, toast, selected slot)."),
    ("filet/filet", "filet", "Hairlines and decorative borders: panels, dividers, table rows."),
    ("filet/filet-controle", "filet-controle", "Control borders (fields, secondary button), rails, 3:1 boundary."),
    ("accent/accent", "accent", "Source marker, links, focus ring, validation fill, selected states."),
    ("accent/accent-fond", "accent-fond", "Accent wash: validated status, highlighted excerpt, information banner."),
    ("accent/sur-accent", "sur-accent", "Text on accent fills."),
    ("clinique/critique", "critique", "Critical status and withdrawal; never a computed risk category."),
    ("clinique/critique-fond", "critique-fond", "Critical wash (allergy block, critical banner)."),
    ("clinique/attention", "attention", "Warning, declared or discordant information, draft, to confirm."),
    ("clinique/attention-fond", "attention-fond", "Attention wash (banner, confirmation block)."),
]
STROKE_NAMES = {"trait-filet": "Hairline", "trait-focus": "Focus ring", "trait-erreur": "Error border"}
TARGET_NAMES = {"cible-soignant": "Clinician touch target", "cible-patient": "Patient touch target"}
BLOCKS = ("colors", "typography", "scale", "motion-effects", "icons")


def _block(text, pattern):
    match = re.search(pattern, text, re.S | re.M)
    if not match:
        raise ValueError("Token block not found: " + pattern)
    return dict(re.findall(r"--([a-z0-9-]+):\s*([^;]+);", match.group(1)))


def read_tokens(css_path=TOKENS_CSS, icons_path=ICONS_CSS):
    css = Path(css_path).read_text(encoding="utf-8")
    tokens = {
        "light": _block(css, r'^:root, \[data-theme="clair"\] \{(.*?)^\}'),
        "dark": _block(css, r'^\[data-theme="sombre"\] \{(.*?)^\}'),
        "root": _block(css, r"^:root \{(.*?)^\}"),
        "typography": {},
        "icons": re.findall(r"^\.etaye-icone--([a-z0-9]+)\{--i:", Path(icons_path).read_text(encoding="utf-8"), re.M),
    }
    for name, body in re.findall(r"^\.([a-z-]+) \{ (font-family[^}]*)\}", css, re.M):
        props = dict(re.findall(r"([a-z-]+):\s*([^;]+);", body))
        tokens["typography"][name] = props
    if len(tokens["typography"]) != 14:
        raise ValueError("Expected 14 typography classes, found %d" % len(tokens["typography"]))
    return tokens


def _px(value):
    return float(value.strip().removesuffix("px"))


def _family(tokens, props):
    var = re.search(r"var\(--([a-z-]+)\)", props["font-family"]).group(1)
    return tokens["root"][var].split(",")[0].strip().strip('"')


def cross_check(tokens, resolved):
    """Return every disagreement between the CSS tokens and the Figma-resolved values."""
    problems = []
    for theme, key in (("light", "light"), ("dark", "dark")):
        for role, var, _use in ROLES:
            expected = resolved["colors"][theme][role]["value"].lower()
            actual = tokens[key].get(var, "").lower()
            if actual != expected:
                problems.append("color %s %s: css %s, figma %s" % (theme, role, actual, expected))
    for role, spec in resolved["typography"].items():
        props = tokens["typography"].get(role)
        if props is None:
            problems.append("typography %s: missing in css" % role)
            continue
        size = _px(props["font-size"])
        letter = round(_px(props["letter-spacing"].replace("em", "")) * size, 2) if "letter-spacing" in props else 0.0
        found = (_family(tokens, props), int(props["font-weight"]), size, _px(props["line-height"]), letter)
        wanted = (spec["fontFamily"], spec["fontWeight"], spec["fontSize"], spec["lineHeightPx"], round(spec["letterSpacing"], 2))
        if found != wanted:
            problems.append("typography %s: css %s, figma %s" % (role, found, wanted))
    for name, spec in resolved["spacing"].items():
        if _px(tokens["root"].get(name, "nan")) != spec["value"]:
            problems.append("spacing %s: css %s, figma %s" % (name, tokens["root"].get(name), spec["value"]))
    for name, spec in resolved["radii"].items():
        if _px(tokens["root"].get(name, "nan")) != spec["value"]:
            problems.append("radius %s: css %s, figma %s" % (name, tokens["root"].get(name), spec["value"]))
    return problems


def render(tokens):
    colors = ["| Role (Figma name) | CSS variable | Light (`clair`) | Dark (`sombre`) | Use |", "|---|---|---|---|---|"]
    for role, var, use in ROLES:
        colors.append("| `%s` | `--%s` | `%s` | `%s` | %s |" % (role, var, tokens["light"][var], tokens["dark"][var], use))
    typography = ["| Role | CSS class | Family | Size / line height | Weight | Letter spacing |", "|---|---|---|---|---|---|"]
    for role, props in tokens["typography"].items():
        size, line = _px(props["font-size"]), _px(props["line-height"])
        letter = "%.2f px (0.01 em)" % (_px(props["letter-spacing"].replace("em", "")) * size) if "letter-spacing" in props else "0"
        typography.append("| `%s` | `.%s` | %s | %g / %g px | %s | %s |" % (role, role, _family(tokens, props), size, line, props["font-weight"], letter))
    root = tokens["root"]
    spacing = ", ".join("`--%s` %s" % (k, v) for k, v in root.items() if k.startswith("espace-"))
    radii = ", ".join("`--%s` %s" % (k, v) for k, v in root.items() if k.startswith("rayon-"))
    scale = [
        "| Group | Tokens |", "|---|---|",
        "| Spacing (4 px base) | " + spacing + " |",
        "| Radii | " + radii + " |",
        "| Strokes | " + ", ".join("`--%s` %s (%s)" % (k, root[k], n) for k, n in STROKE_NAMES.items()) + " |",
        "| Targets | " + ", ".join("`--%s` %s (%s)" % (k, root[k], n) for k, n in TARGET_NAMES.items()) + " |",
        "| Font stacks | " + ", ".join("`--%s` %s" % (k, root[k]) for k in ("font-texte", "font-donnee", "font-document")) + " |",
    ]
    motion = [
        "| Token | Value |", "|---|---|",
        "| `--courbe` | `%s` |" % root["courbe"],
        "| `--duree-retour` / `--duree-flottant` / `--duree-entree` | %s / %s / %s |" % (root["duree-retour"], root["duree-flottant"], root["duree-entree"]),
        "| `--ombre-flottant`, light | `%s` |" % tokens["light"]["ombre-flottant"],
        "| `--ombre-flottant`, dark | `%s` |" % tokens["dark"]["ombre-flottant"],
    ]
    icons = "%d icons: %s." % (len(tokens["icons"]), ", ".join("`%s`" % name for name in tokens["icons"]))
    return {"colors": "\n".join(colors), "typography": "\n".join(typography), "scale": "\n".join(scale),
            "motion-effects": "\n".join(motion), "icons": icons}


def _marker(name, edge):
    return "<!-- generated:%s:%s -->" % (name, edge)


def splice(doc_text, blocks):
    for name in BLOCKS:
        start, end = _marker(name, "start"), _marker(name, "end")
        if doc_text.count(start) != 1 or doc_text.count(end) != 1:
            raise ValueError("Missing or duplicated generated block: " + name)
        head, rest = doc_text.split(start)
        _old, tail = rest.split(end)
        doc_text = head + start + "\n" + blocks[name] + "\n" + end + tail
    return doc_text


def check(doc_path=DOC, css_path=TOKENS_CSS, icons_path=ICONS_CSS, resolved_path=RESOLVED):
    """Return a list of problems: source disagreement or a stale document."""
    tokens = read_tokens(css_path, icons_path)
    resolved = json.loads(Path(resolved_path).read_text(encoding="utf-8"))
    problems = cross_check(tokens, resolved)
    if problems:
        return problems
    text = Path(doc_path).read_text(encoding="utf-8")
    if splice(text, render(tokens)) != text:
        problems.append("%s is stale: run python3 tools/design_system_reference.py --write" % Path(doc_path).name)
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="verify only (the default)")
    parser.add_argument("--write", action="store_true", help="rewrite the generated blocks in the document")
    args = parser.parse_args()
    if args.check and args.write:
        parser.error("--check and --write are exclusive")
    if args.write:
        tokens = read_tokens()
        problems = cross_check(tokens, json.loads(RESOLVED.read_text(encoding="utf-8")))
        if problems:
            print("\n".join(problems), file=sys.stderr)
            return 1
        DOC.write_text(splice(DOC.read_text(encoding="utf-8"), render(tokens)), encoding="utf-8")
        print("generated blocks written")
        return 0
    problems = check()
    print("\n".join(problems) if problems else "design-system.md matches the CSS tokens and the Figma-resolved values")
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
