#!/usr/bin/env python3
"""Verify corrected design references, guided navigation and critical prototype behavior."""

import argparse
import atexit
import hashlib
import json
from pathlib import Path
import sys

from playwright.sync_api import sync_playwright

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
sys.path.insert(0, str(REPO / "tools"))
from audit_design_receipt import inspect

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--root", type=Path, required=True)
parser.add_argument("--output", type=Path, required=True)
parser.add_argument(
    "--full", action="store_true", help="Inspect every state at four widths"
)
args = parser.parse_args()
root = args.root.resolve()
site = root / "site"
out = args.output.resolve()
if out.is_relative_to(REPO) or out.is_relative_to(root) or root.is_relative_to(out):
    parser.error("Evidence output must be external and disjoint from the design tree")
out.mkdir(parents=True, exist_ok=False)
errors = []
results = {
    "kind": "design-prototype-verification-only",
    "applicationTests": False,
    "links": inspect(site),
    "journeys": [],
    "checks": [],
    "overflow": [],
    "captures": [],
    "pdfs": [],
}
completed = False


def preserve_failure():
    if not completed:
        results["ok"] = False
        results["errors"] = errors + [
            "Verification interrupted; not an acceptance result"
        ]
        (out / "verification.json").write_text(
            json.dumps(results, ensure_ascii=False, indent=2) + "\n"
        )


atexit.register(preserve_failure)
if not results["links"]["ok"]:
    errors.append("Local reference audit failed")
snapshot_path = root / "review/corrected-snapshot.json"
snapshot = json.loads(snapshot_path.read_text())


def snapshot_matches():
    paths = {str(p.relative_to(root)) for p in site.rglob("*") if p.is_file()}
    return paths == {x["path"] for x in snapshot["files"]} and all(
        hashlib.file_digest((root / x["path"]).open("rb"), "sha256").hexdigest()
        == x["sha256"]
        for x in snapshot["files"]
    )


results["snapshotSha256"] = snapshot["snapshotSha256"]
if not snapshot_matches():
    errors.append("Input snapshot no longer matches complete site/assets")
states = [
    m for f in (site / "manifest").glob("*.json") for m in json.loads(f.read_text())
]
journeys = json.loads((HERE / "journeys.json").read_text())
selected = [
    "S-05/choix",
    "S-06/nora",
    "S-06/jour-vide",
    "S-19/lecture-seule",
    "S-32/membre",
    "I-17/ouvert",
    "I-16/ouvert",
    "I-15/non-soutenu",
    "I-23/relance",
    "S-40/louis-en-file",
    "S-29/v2",
    "S-30/apercu",
    "I-07/qualitatif",
    "I-07/correction",
    "I-21/identite",
    "I-21/mesure",
    "I-21/disponibilite",
    "I-11/claire",
]
selected += [
    f"M-{i:02d}/{state}" for i in range(1, 6) for state in ["html", "texte", "sombre"]
]
console = []


def check(name, condition):
    results["checks"].append({"name": name, "passed": bool(condition)})
    if not condition:
        errors.append(name)
    print(("ok " if condition else "FAIL ") + name, flush=True)


def settle_images(page):
    # Do not classify a pending PDF-preview decode as a broken image.
    page.evaluate("""async () => {
      await Promise.all([...document.images].map(async image => {
        image.loading = 'eager';
        try { await image.decode(); } catch { /* The subsequent assertion records failures. */ }
      }));
    }""")


with sync_playwright() as p:
    b = p.chromium.launch()
    results["browser"] = b.version
    context = b.new_context(viewport={"width": 1440, "height": 1024})
    page = context.new_page()
    page.set_default_timeout(10000)
    page.on("pageerror", lambda e: console.append(str(e)))

    def goto(state, query=""):
        page.goto((site / "ecrans" / f"{state}.html").as_uri() + query)
        page.evaluate("document.fonts.ready")

    goto("S-02/connexion")
    page.get_by_role("link", name="Se connecter", exact=True).click()
    check("Primary Nora login keeps Nora context", "/S-05/un-seul.html" in page.url)

    for journey in journeys:
        goto(journey["steps"][0], "?journey=" + journey["id"] + "&step=0")
        for index, step in enumerate(journey["steps"]):
            check(
                journey["id"] + " step " + str(index + 1),
                "/ecrans/" + step + ".html" in page.url,
            )
            if index < len(journey["steps"]) - 1:
                page.get_by_role("link", name="Étape suivante", exact=True).click()
        results["journeys"].append(
            {
                "id": journey["id"],
                "steps": len(journey["steps"]),
                "navigatedThroughGuidedControls": True,
            }
        )
    goto("S-05/choix")
    page.get_by_role("link", name="Ouvrir", exact=True).nth(1).click()
    check(
        "Canal selection preserves Sami/Canal",
        "Sami Rahmani" in page.locator(".etaye-contexte").inner_text()
        and "Canal" in page.locator(".etaye-contexte").inner_text(),
    )
    page.get_by_role("link", name="Patients", exact=True).click()
    check(
        "Canal patient list stays isolated",
        "Hugo Petit" in page.locator("main").inner_text()
        and "Claire Martin" not in page.locator("main").inner_text(),
    )
    goto("I-09/source-src-b-001")
    check(
        "Canal source backdrop links preserve practice",
        page.locator(".etaye-nav a").filter(has_text="Patients").get_attribute("href")
        == "../S-07/canal.html",
    )
    check(
        "Canal navigation has no inherited Tilleuls count",
        page.locator(".etaye-nav__compte").count() == 0,
    )
    page.keyboard.press("Escape")
    page.wait_for_url("**/S-09/hugo.html*")
    page.get_by_role("link", name="Patients", exact=True).click()
    check(
        "Canal source drawer navigation preserves practice",
        "/S-07/canal.html" in page.url
        and "Hugo Petit" in page.locator("main").inner_text()
        and "Canal" in page.locator(".etaye-contexte").inner_text(),
    )
    goto("I-21/mesure")
    proposed = page.locator("tr").filter(has_text="Nouvelle valeur proposée")
    check(
        "Measurement comparison retains actual unsaved proposal",
        proposed.count() == 1
        and "68,4 kg · saisie non enregistrée" in proposed.inner_text(),
    )
    for state in [m for m in states if m["id"] == "S-38"]:
        goto(state["id"] + "/" + state["slug"], "?return=../S-23/validation.html")
        page.wait_for_function(
            'Boolean(document.activeElement.closest("[role=alertdialog]"))'
        )
        page.keyboard.press("Escape")
        page.wait_for_url("**/S-02/connexion.html*")
        check(
            "Session recovery Escape requires login: " + state["slug"],
            "/S-02/connexion.html" in page.url,
        )
    for state in ["I-19/patient", "S-29/extrait-src-a-005"]:
        goto(state)
        page.wait_for_function(
            'Boolean(document.activeElement.closest("[role=dialog]"))'
        )
        page.keyboard.press("Escape")
        page.wait_for_url("**/S-29/v2.html*")
        check(
            "Patient overlay returns to patient explanation: " + state,
            "/S-29/v2.html" in page.url,
        )
    goto("I-23/relance")
    page.get_by_role("link", name="Suivre la tâche", exact=True).click()
    check(
        "Louis retry never reuses Claire task",
        "Louis Bernard" in page.locator("main").inner_text()
        and "AI-A-001" not in page.locator("main").inner_text(),
    )
    goto("S-19/lecture-seule")
    check(
        "Revoked membership removes clinical record",
        "Louis" not in page.locator("body").inner_text()
        and "R-A-002" not in page.locator("body").inner_text(),
    )
    goto("S-10/claire")
    page.get_by_role("link", name="Ouvrir la source", exact=True).first.click()
    check(
        "New allergy opens exact source",
        "SRC-A-005" in page.locator("[role=dialog]").inner_text(),
    )
    goto("S-23/validation")
    source = page.locator("button.etaye-repere[data-source]").first
    check(
        "Reaction statement cites patient report",
        source.get_attribute("data-source") == "SRC-A-005",
    )
    goto("I-15/ouvert")
    field = page.locator("#i15-texte")
    field.fill("Correction de démonstration conservée en mémoire.")
    page.locator("button[data-source]").first.click()
    page.locator("dialog[data-source-preview] button").click()
    check(
        "Source inspection preserves unsaved correction",
        field.input_value() == "Correction de démonstration conservée en mémoire."
        and page.evaluate('document.activeElement.hasAttribute("data-source")'),
    )
    page.locator("button[data-source]").first.click()
    page.keyboard.press("Escape")
    check(
        "Source Escape preserves editor and restores focus",
        not page.locator("dialog[open]").count()
        and field.input_value() == "Correction de démonstration conservée en mémoire."
        and page.evaluate('document.activeElement.hasAttribute("data-source")'),
    )
    goto("I-16/ouvert")
    check(
        "Modal has initial focus",
        page.evaluate("document.activeElement.tagName") != "BODY",
    )
    before = page.url
    page.get_by_role("button", name="Valider la version", exact=True).click()
    check("Validation requires attestation", page.url == before)
    page.locator("input[type=checkbox]").first.check()
    page.get_by_role("button", name="Valider la version", exact=True).click()
    check(
        "Validation remains internal",
        "/S-23/valide.html" in page.url
        and "non publié" in page.locator("main").inner_text(),
    )
    goto("I-17/ouvert")
    page.get_by_role("button", name="Publier pour le patient", exact=True).click()
    check("Publication is separate explicit action", "/S-23/publie.html" in page.url)
    goto("I-18/retrait")
    before = page.url
    page.get_by_role("button", name="Retirer l’accès", exact=True).click()
    check("Withdrawal needs reason", before == page.url)
    page.locator("#i18-motif").fill("Correction de démonstration ; contenu à revoir.")
    page.get_by_role("button", name="Retirer l’accès", exact=True).click()
    check("Withdrawal reaches withdrawn state", "/S-24/retire.html" in page.url)
    goto("I-11/claire")
    before = page.url
    page.get_by_role("button", name="Confirmer le déplacement", exact=True).click()
    check("Move needs reason", before == page.url)
    page.locator("#mv-r").fill("Demande de la patiente")
    page.get_by_role("button", name="Confirmer le déplacement", exact=True).click()
    check("Move reaches event preview", "/M-04/modifie.html" in page.url)
    goto("S-26/annuler")
    before = page.url
    page.get_by_role("button", name="Annuler le rendez-vous", exact=True).click()
    check("Patient cancellation requires reason", before == page.url)
    page.locator("#cancel-reason").fill("Indisponibilité de la patiente")
    page.get_by_role("button", name="Annuler le rendez-vous", exact=True).click()
    check(
        "Patient cancellation reaches cancelled state", "/S-26/annule.html" in page.url
    )
    goto("S-32/membre")
    page.set_viewport_size({"width": 320, "height": 720})
    page.get_by_role("button", name="Ouvrir le menu").click()
    check("Mobile menu opens", page.locator(".etaye-nav").is_visible())
    page.set_viewport_size({"width": 1440, "height": 1024})
    goto("S-23/valide")
    trigger = page.locator('a[href*="I-17"]').first
    trigger.click()
    page.wait_for_function('Boolean(document.activeElement.closest("[role=dialog]"))')
    page.keyboard.press("Escape")
    page.wait_for_url("**/S-23/valide.html*")
    check(
        "Escape returns to trigger",
        page.evaluate("document.activeElement.id").startswith("prototype-link-"),
    )
    context.close()
    profiles = {
        "desktop": (1440, 1024),
        "tablet": (1024, 768),
        "mobile": (390, 844),
        "narrow": (320, 720),
    }
    for profile, (w, h) in profiles.items():
        ctx = b.new_context(viewport={"width": w, "height": h})
        pg = ctx.new_page()
        pg.on("pageerror", lambda e: console.append(str(e)))
        scope = (
            states
            if args.full
            else [m for m in states if m["id"] + "/" + m["slug"] in selected]
        )
        for m in scope:
            pg.goto((site / m["path"]).as_uri())
            pg.evaluate("document.fonts.ready")
            settle_images(pg)
            state = m["id"] + "/" + m["slug"]
            data = pg.evaluate(
                '({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,broken:[...document.images].filter(i=>!i.complete||!i.naturalWidth).map(i=>i.getAttribute("src"))})'
            )
            if data["scrollWidth"] > w + 1 or data["broken"]:
                results["overflow"].append({"state": state, "profile": profile, **data})
            if state in selected:
                filename = state.replace("/", "-") + "-" + profile + ".png"
                pg.screenshot(path=str(out / filename), full_page=True)
                results["captures"].append(filename)
        ctx.close()
        print(profile, "checked", len(scope), flush=True)
    for mode in ["dark", "fallback"]:
        ctx = b.new_context(viewport={"width": 390, "height": 844})
        pg = ctx.new_page()
        pg.on("pageerror", lambda e: console.append(str(e)))
        if mode == "fallback":
            pg.route("**/*.woff2", lambda r: r.abort())
        for state in selected:
            pg.goto(
                (site / "ecrans" / f"{state}.html").as_uri()
                + ("?theme=sombre" if mode == "dark" else "")
            )
            pg.evaluate("document.fonts.ready")
            settle_images(pg)
            data = pg.evaluate(
                '({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,broken:[...document.images].filter(i=>!i.complete||!i.naturalWidth).map(i=>i.getAttribute("src"))})'
            )
            if data["scrollWidth"] > 391 or data["broken"]:
                results["overflow"].append({"state": state, "profile": mode, **data})
            name = state.replace("/", "-") + "-" + mode + ".png"
            pg.screenshot(path=str(out / name), full_page=True)
            results["captures"].append(name)
        ctx.close()
    b.close()
check("No JavaScript errors in critical journeys", not console)
check("No page overflow or broken images in checked scope", not results["overflow"])
expected_pdfs = {
    f"P-{i:02d}-{variant}.pdf"
    for i in range(1, 4)
    for variant in ["court", "long", "incomplet"]
}
check(
    "Exact nine PDF variants",
    {p.name for p in (site / "docs").glob("*.pdf")} == expected_pdfs,
)
for pdf in sorted((site / "docs").glob("*.pdf")):
    from pypdf import PdfReader

    reader = PdfReader(pdf)
    checks = []
    for n, page in enumerate(reader.pages, 1):
        text = page.extract_text()
        checks.append(
            "Démonstration" in text and f"Page {n} / {len(reader.pages)}" in text
        )
    row = {
        "file": pdf.name,
        "pages": len(reader.pages),
        "allPageFooters": all(checks),
        "sha256": hashlib.file_digest(pdf.open("rb"), "sha256").hexdigest(),
    }
    results["pdfs"].append(row)
    check(pdf.name + " every page numbered", all(checks))
results["stateCount"] = len(states)
check("Artifact snapshot unchanged by verification", snapshot_matches())
results["families"] = len({m["id"] for m in states})
results["consoleErrors"] = console
results["errors"] = errors
results["ok"] = not errors
completed = True
(out / "verification.json").write_text(
    json.dumps(results, ensure_ascii=False, indent=2) + "\n"
)
print("Checks", len(results["checks"]), "errors", len(errors))
print("\n".join(errors))
raise SystemExit(1 if errors else 0)
