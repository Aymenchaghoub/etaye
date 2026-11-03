"""Rule lint over every generated state (visible text + links). Not a substitute for opening captures.
Checks: broken internal links, forbidden button labels, combined validate+publish, confidence percentages,
"IA" badge, emoji, gradients, non-example.test e-mails, unquoted "Aucune allergie connue", "aucun traitement"
used as a fact. Usage: python3 lint.py  → review/lint-report.json
"""
import glob, html, json, os, re
import os as _os, sys as _sys; _sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__))); from paths import ROOT

SITE = f"{ROOT}/site"
EMOJI = re.compile("[\U0001F300-\U0001FAFF☀-➿\U0001F000-\U0001F2FF]")


def visible(src):
    src = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", src, flags=re.S)
    return html.unescape(re.sub(r"<[^>]+>", " ", src))


issues = {}
pages = sorted(glob.glob(f"{SITE}/ecrans/*/*.html"))
for f in pages:
    rel = os.path.relpath(f, SITE)
    src = open(f).read()
    txt = re.sub(r"\s+", " ", visible(src))
    out = []
    for h in re.findall(r'href="(\.\./[^"#?]+)"', src):
        p = os.path.normpath(os.path.join(os.path.dirname(f), h))
        if p.startswith(f"{SITE}/ecrans") and not os.path.exists(p):
            out.append(f"broken link {os.path.relpath(p, SITE)}")
    for lab in re.findall(r'class="etaye-bouton[^"]*"[^>]*>(.*?)</(?:a|button)>', src, flags=re.S):
        t = re.sub(r"\s+", " ", visible(lab)).strip()
        if t in ("OK", "Ok", "Confirmer", "Valider"):
            out.append(f"vague button label '{t}'")
        if re.search(r"valider et (publier|partager)", t, re.I):
            out.append(f"combined validate+publish '{t}'")
    if re.search(r"\b\d{1,3}\s?%", txt):
        out.append("percentage in visible text: " + re.search(r".{30}\b\d{1,3}\s?%.{10}", txt).group(0) if re.search(r".{30}\b\d{1,3}\s?%.{10}", txt) else "percentage")
    if re.search(r"(?<![\w-])IA(?![\w-])", txt):
        out.append("standalone 'IA' in visible text")
    if EMOJI.search(txt):
        out.append("emoji")
    if "gradient(" in src:
        out.append("gradient")
    for m in set(re.findall(r"[\w.+-]+@[\w-]+(?:\.[\w-]+)+", txt)):
        if not m.endswith("example.test"):
            out.append(f"e-mail outside example.test: {m}")
    for m in re.finditer(r"aucune allergie connue", txt, re.I):
        before = txt[max(0, m.start() - 3):m.start()]
        if "«" not in before and "“" not in before:
            out.append("unquoted 'Aucune allergie connue': …" + txt[max(0, m.start() - 40):m.end() + 10])
    for m in re.finditer(r"aucun traitement", txt, re.I):
        ctx = txt[max(0, m.start() - 60):m.end() + 5]
        if not re.search(r"(pas|jamais|écrire|signifie|constatation)", ctx, re.I):
            out.append("'aucun traitement' as a fact: …" + ctx)
    if out:
        issues[rel] = sorted(set(out))
json.dump({"pages": len(pages), "issues": issues}, open(f"{ROOT}/review/lint-report.json", "w"), ensure_ascii=False, indent=1)
print(len(pages), "pages,", len(issues), "with issues")
for k, v in list(issues.items())[:40]:
    print(k, v)
