"""Browser engines for the background study measurements.

Every engine renders study.html at an exact CSS viewport and returns either the layout facts
(the JSON the page writes in #measure-data) or a PNG. A capture is described by:

    q      study query string, for example "v=b&t=clair&m=1"
    css_w, css_h   CSS viewport in CSS px (what the page sees as innerWidth x innerHeight)
    dsf    display scale factor of the screen (1 = standard, 2 = HiDPI)
    zoom   browser page zoom (1 = 100 %, 2 = 200 %); the page then sees a viewport of window/zoom

The PNG is css_w * dsf * zoom pixels wide: device pixels, never resampled.

Engines (all local, no download, no network beyond 127.0.0.1):
    chromium   headless Chromium command line, the engine of the original measurements
    firefox    headless Firefox driven over WebDriver BiDi (websocket-client)
    webkit     WebKitGTK through webkit_capture.py. This is the WebKit engine of Linux
               applications, NOT Safari: Safari has its own WebKit build and platform graphics.
"""
from __future__ import annotations

import base64
import json
import os
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path
from urllib.parse import quote

from PIL import Image

HERE = Path(__file__).resolve().parent
STUDY = "docs/design/background-study/study.html"
FRAME = "docs/design/background-study/frame.html"
MIN_WINDOW = 500  # headless Chromium refuses narrower windows


def _free_port() -> int:
    sock = socket.socket()
    sock.bind(("127.0.0.1", 0))
    port = sock.getsockname()[1]
    sock.close()
    return port


def _check_png(path: Path, width_px: int, height_px: int) -> None:
    size = Image.open(path).size
    if size != (width_px, height_px):
        raise RuntimeError(f"{path.name}: expected {(width_px, height_px)} px, got {size}")


class Engine:
    name = "engine"

    def __init__(self, base: str):
        self.base = base  # http://127.0.0.1:PORT of the static server rooted at the repository

    def start(self) -> None: ...

    def stop(self) -> None: ...

    def version(self) -> str:
        raise NotImplementedError

    def layout(self, q: str, css_w: int, css_h: int, dsf: float = 1, zoom: float = 1) -> dict:
        raise NotImplementedError

    def shoot(self, q: str, css_w: int, css_h: int, dsf: float, zoom: float, path: Path) -> None:
        raise NotImplementedError

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, *exc):
        self.stop()


class Chromium(Engine):
    """Headless Chromium, command line. Hosts the study in frame.html (an iframe of the exact CSS
    size) because the headless window cannot be narrower than 500 px; the extra width is cropped."""

    name = "chromium"
    binary = "chromium-browser"

    def _run(self, args: list[str], dpr: float) -> str:
        base = [self.binary, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                f"--force-device-scale-factor={dpr:g}", "--virtual-time-budget=4000"]
        return subprocess.run(base + args, capture_output=True, text=True, check=True).stdout

    def _url(self, q: str, css_w: int, css_h: int) -> str:
        return f"{self.base}/{FRAME}?w={css_w}&h={css_h}&q={quote(q)}"

    def version(self) -> str:
        return subprocess.run([self.binary, "--version"], capture_output=True, text=True).stdout.strip()

    def layout(self, q, css_w, css_h, dsf=1, zoom=1):
        import re
        out = self._run([f"--window-size={max(css_w, MIN_WINDOW)},{css_h}", "--dump-dom",
                         self._url(q, css_w, css_h)], dsf * zoom)
        found = re.search(r'<script type="application/json" id="measure-data">(.*?)</script>', out, re.S)
        if not found:
            raise RuntimeError("layout data missing: the page did not finish loading")
        return json.loads(found.group(1))

    def shoot(self, q, css_w, css_h, dsf, zoom, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        dpr = dsf * zoom
        self._run([f"--window-size={max(css_w, MIN_WINDOW)},{css_h}", f"--screenshot={path}",
                   self._url(q, css_w, css_h)], dpr)
        img = Image.open(path)
        want = (round(css_w * dpr), round(css_h * dpr))
        if img.size[1] != want[1] or img.size[0] < want[0]:
            raise RuntimeError(f"{path.name}: expected at least {want}, got {img.size}")
        if img.size[0] != want[0]:
            img.crop((0, 0, *want)).save(path)  # window wider than the iframe: keep the iframe only


class Firefox(Engine):
    """Headless Firefox over WebDriver BiDi. A throw-away profile keeps the user's profile untouched and
    switches off telemetry and experiment sync, so nothing but 127.0.0.1 is requested by the page."""

    name = "firefox"
    binary = "firefox"
    PREFS = {"app.normandy.enabled": "false", "messaging-system.rsexperimentloader.enabled": "false",
             "datareporting.policy.dataSubmissionEnabled": "false", "toolkit.telemetry.enabled": "false",
             "browser.shell.checkDefaultBrowser": "false"}

    def start(self):
        import websocket  # websocket-client
        self._profile = tempfile.mkdtemp(prefix="etaye-firefox-")
        (Path(self._profile) / "user.js").write_text(
            "".join(f'user_pref("{k}", {v});\n' for k, v in self.PREFS.items()), encoding="utf-8")
        port = _free_port()
        self._proc = subprocess.Popen(
            [self.binary, "--headless", "--no-remote", "--profile", self._profile,
             f"--remote-debugging-port={port}", "about:blank"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        self._ws, last = None, None
        for _ in range(150):  # up to 30 s for the browser to open its BiDi socket
            try:
                self._ws = websocket.create_connection(f"ws://127.0.0.1:{port}/session", timeout=60,
                                                       suppress_origin=True)
                break
            except Exception as exc:  # noqa: BLE001 - the socket is not open yet
                last = exc
                time.sleep(0.2)
        if self._ws is None:
            self.stop()
            raise RuntimeError(f"Firefox BiDi did not open: {last!r}")
        self._id = 0
        self._version = self._call("session.new", capabilities={})["capabilities"]["browserVersion"]
        self._ctx = self._call("browsingContext.getTree")["contexts"][0]["context"]

    def stop(self):
        try:
            if getattr(self, "_ws", None):
                self._ws.close()
        finally:
            proc = getattr(self, "_proc", None)
            if proc and proc.poll() is None:
                os.killpg(proc.pid, signal.SIGKILL)
            shutil.rmtree(getattr(self, "_profile", ""), ignore_errors=True)

    def _call(self, method: str, **params) -> dict:
        self._id += 1
        self._ws.send(json.dumps({"id": self._id, "method": method, "params": params}))
        while True:
            msg = json.loads(self._ws.recv())
            if msg.get("id") == self._id:
                if msg.get("type") == "error":
                    raise RuntimeError(f"{method}: {msg.get('error')} {msg.get('message')}")
                return msg["result"]

    def _eval(self, expression: str):
        r = self._call("script.evaluate", expression=expression, target={"context": self._ctx},
                       awaitPromise=False)
        if r.get("type") != "success":
            raise RuntimeError(f"script error: {r}")
        return r["result"].get("value")

    def version(self) -> str:
        return f"Firefox {self._version}"

    def _load(self, q: str, css_w: int, css_h: int, dpr: float) -> None:
        self._call("browsingContext.setViewport", context=self._ctx,
                   viewport={"width": css_w, "height": css_h}, devicePixelRatio=dpr)
        self._call("browsingContext.navigate", context=self._ctx, url=f"{self.base}/{STUDY}?{q}", wait="complete")
        for _ in range(150):
            if self._eval("document.documentElement.getAttribute('data-ready')") == "1":
                return
            time.sleep(0.1)
        raise RuntimeError("the study page never set data-ready")

    def layout(self, q, css_w, css_h, dsf=1, zoom=1):
        self._load(q, css_w, css_h, dsf * zoom)
        return json.loads(self._eval("document.getElementById('measure-data').textContent"))

    def shoot(self, q, css_w, css_h, dsf, zoom, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        dpr = dsf * zoom
        self._load(q, css_w, css_h, dpr)
        shot = self._call("browsingContext.captureScreenshot", context=self._ctx, origin="viewport")
        path.write_bytes(base64.b64decode(shot["data"]))
        _check_png(path, round(css_w * dpr), round(css_h * dpr))


class WebKit(Engine):
    """WebKitGTK 4.1 in an offscreen window (X11 backend), one process per capture."""

    name = "webkit"

    def _env(self, dsf: float) -> dict:
        env = dict(os.environ)
        env.update({"GDK_BACKEND": "x11", "GDK_SCALE": str(int(dsf)), "WEBKIT_DISABLE_COMPOSITING_MODE": "1"})
        return env

    def _run(self, q, css_w, css_h, dsf, zoom, extra: list[str]) -> str:
        cmd = [sys.executable, str(HERE / "webkit_capture.py"), "--url", f"{self.base}/{STUDY}?{q}",
               "--width", str(round(css_w * zoom)), "--height", str(round(css_h * zoom)), "--zoom", f"{zoom:g}"] + extra
        done = subprocess.run(cmd, capture_output=True, text=True, env=self._env(dsf), timeout=90)
        if done.returncode != 0:
            raise RuntimeError(f"webkit capture failed: {done.stderr.strip()[-300:]}")
        return done.stdout

    def version(self) -> str:
        return subprocess.run([sys.executable, str(HERE / "webkit_capture.py"), "--version"], capture_output=True,
                              text=True, env=self._env(1)).stdout.strip() + " (Linux GTK port, not Safari)"

    def layout(self, q, css_w, css_h, dsf=1, zoom=1):
        return json.loads(self._run(q, css_w, css_h, dsf, zoom, ["--layout"]).strip().splitlines()[-1])

    def shoot(self, q, css_w, css_h, dsf, zoom, path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self._run(q, css_w, css_h, dsf, zoom, ["--png", str(path)])
        _check_png(path, round(css_w * dsf * zoom), round(css_h * dsf * zoom))


ENGINES = {"chromium": Chromium, "firefox": Firefox, "webkit": WebKit}
