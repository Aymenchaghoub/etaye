"""Generate the A4 PDFs P-01…P-03 from the built print pages, then page images for review and previews.

Output:
  site/docs/<P>-<slug>.pdf            real PDF (selectable text, tagged, outline, embedded fonts)
  site/docs/<P>-<slug>-p<n>.png       colour page previews used by S-39 / I-19 (≈ 400 px wide)
  review/<P>/<slug>-gris-p<n>.png     grayscale pages (≈ 900 px wide) for opened review
  review/_pdf-<P>-<slug>.png          grayscale contact sheet of every page
  review/pdf-report.json              page count, fonts, text checks per PDF
Usage: python3 pdf.py [P-01 ...]   (run build.py screens_documents first)
"""
import glob, json, os, re, subprocess, sys
import os as _os, sys as _sys; _sys.path.insert(0, _os.path.dirname(_os.path.abspath(__file__))); from paths import ROOT
from playwright.sync_api import sync_playwright
from PIL import Image, ImageDraw

SITE, REV = f"{ROOT}/site", f"{ROOT}/review"
DOCS = f"{SITE}/docs"


def sheet(pngs, out, cols=4, w=420):
    ims = [Image.open(p) for p in pngs]
    ims = [im.resize((w, int(im.height * w / im.width))) for im in ims]
    h = max(i.height for i in ims)
    rows = (len(ims) + cols - 1) // cols
    S = Image.new("L", (cols * (w + 12) + 12, rows * (h + 32) + 12), 150)
    for k, im in enumerate(ims):
        x, y = 12 + (k % cols) * (w + 12), 12 + (k // cols) * (h + 32)
        S.paste(im.convert("L"), (x, y + 20)); ImageDraw.Draw(S).text((x, y + 4), f"page {k + 1}/{len(ims)}", fill=255)
    S.save(out)


def main(only=None):
    os.makedirs(DOCS, exist_ok=True)
    man = json.load(open(f"{SITE}/manifest/screens_documents.json"))
    items = [m for m in man if m["id"].startswith("P-") and (not only or m["id"] in only)]
    report = json.load(open(f"{REV}/pdf-report.json")) if os.path.exists(f"{REV}/pdf-report.json") else {}
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page()
        for m in items:
            name = f'{m["id"]}-{m["slug"]}'
            pdf = f"{DOCS}/{name}.pdf"
            pg.goto(f'file://{SITE}/{m["path"]}'); pg.wait_for_load_state("networkidle"); pg.evaluate("document.fonts.ready")
            pg.emulate_media(media="print")
            pg.pdf(path=pdf, format="A4", print_background=True, prefer_css_page_size=True, tagged=True, outline=True)
            for f in glob.glob(f"{DOCS}/{name}-p*.png") + glob.glob(f"{REV}/{m['id']}/{m['slug']}-gris-p*.png"):
                os.remove(f)
            subprocess.run(["pdftoppm", "-png", "-r", "48", pdf, f"{DOCS}/{name}-p"], check=True)
            os.makedirs(f"{REV}/{m['id']}", exist_ok=True)
            subprocess.run(["pdftoppm", "-png", "-gray", "-r", "110", pdf, f"{REV}/{m['id']}/{m['slug']}-gris-p"], check=True)
            # pdftoppm pads page numbers (p-1 / p-01); normalise to p1, p2…
            for d, pat in ((DOCS, f"{name}-p-*.png"), (f"{REV}/{m['id']}", f"{m['slug']}-gris-p-*.png")):
                for f in glob.glob(f"{d}/{pat}"):
                    k = int(f.rsplit("-", 1)[1].split(".")[0]); os.rename(f, f.rsplit("-p-", 1)[0] + f"-p{k}.png")
            grays = sorted(glob.glob(f"{REV}/{m['id']}/{m['slug']}-gris-p*.png"), key=lambda f: int(f.rsplit("-p", 1)[1].split(".")[0]))
            sheet(grays, f"{REV}/_pdf-{name}.png")
            info = subprocess.run(["pdfinfo", pdf], capture_output=True, text=True).stdout
            fonts = subprocess.run(["pdffonts", pdf], capture_output=True, text=True).stdout.splitlines()[2:]
            txt = subprocess.run(["pdftotext", "-layout", pdf, "-"], capture_output=True, text=True).stdout
            npages = int([l for l in info.splitlines() if l.startswith("Pages:")][0].split()[1])
            report[name] = {"pages": npages, "tagged": bool(re.search(r"^Tagged:\s+yes", info, re.M)),
                            "fonts": sorted({l.split()[0].split("+")[-1] for l in fonts if l.strip()}),
                            "all_fonts_embedded": all(" yes " in l for l in fonts if l.strip()),
                            "footer_on_every_page": txt.count("Démonstration — personnes et données fictives") >= npages,
                            "page_numbers": f"Page {npages} / {npages}" in txt,
                            "accents_extractable": "réaction" in txt or "déclaration" in txt or "Démonstration" in txt}
            print(name, report[name])
        b.close()
    json.dump(report, open(f"{REV}/pdf-report.json", "w"), ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main(sys.argv[1:] or None)
