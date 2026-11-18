#!/usr/bin/env python3
"""One WebKitGTK capture: load a page, wait for the study's ready flag, print layout facts, save a PNG.

Helper of engines.py (the WebKit engine). It needs PyGObject with WebKitGTK 4.1 and an X display
(GDK_BACKEND=x11; GDK_SCALE sets the display scale factor, the zoom level is real page zoom).
WebKitGTK is the WebKit engine of GNOME Web and many Linux applications. It is NOT Safari:
Safari on macOS and iOS ships its own WebKit build and platform graphics.

Usage: webkit_capture.py --url URL --width W --height H [--zoom Z] [--png PATH] [--layout] [--version]
W and H are the window size in device-independent pixels; the CSS viewport is W/Z by H/Z.
"""
from __future__ import annotations

import argparse
import sys

import gi

gi.require_version("Gtk", "3.0")
gi.require_version("WebKit2", "4.1")
from gi.repository import GLib, Gtk, WebKit2  # noqa: E402

READY = "document.documentElement.getAttribute('data-ready') === '1'"
FACTS = "(document.getElementById('measure-data') || {}).textContent || ''"
TIMEOUT_S = 30


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--url")
    ap.add_argument("--width", type=int)
    ap.add_argument("--height", type=int)
    ap.add_argument("--zoom", type=float, default=1.0)
    ap.add_argument("--png")
    ap.add_argument("--layout", action="store_true", help="print the layout facts JSON on stdout")
    ap.add_argument("--version", action="store_true")
    args = ap.parse_args()
    if args.version:
        print(f"WebKitGTK {WebKit2.get_major_version()}.{WebKit2.get_minor_version()}.{WebKit2.get_micro_version()}")
        return 0

    win = Gtk.OffscreenWindow()
    view = WebKit2.WebView()
    view.set_zoom_level(args.zoom)
    view.set_size_request(args.width, args.height)
    win.set_default_size(args.width, args.height)
    win.add(view)
    win.show_all()
    loop = GLib.MainLoop()
    result = {"code": 1}

    def finish(code: int, message: str = "") -> bool:
        result["code"] = code
        if message:
            print(message, file=sys.stderr)
        loop.quit()
        return False

    def on_snapshot(v, res, _data=None):
        try:
            surface = v.get_snapshot_finish(res)
            surface.write_to_png(args.png)
        except Exception as exc:  # noqa: BLE001 - report any snapshot failure to the caller
            return finish(1, f"snapshot failed: {exc}")
        return finish(0)

    def after_ready():
        if args.layout:
            def on_facts(v, res, _data=None):
                print(v.evaluate_javascript_finish(res).to_string())
                sys.stdout.flush()
                if args.png:
                    v.get_snapshot(WebKit2.SnapshotRegion.VISIBLE, WebKit2.SnapshotOptions.NONE, None, on_snapshot)
                else:
                    finish(0)
            view.evaluate_javascript(FACTS, -1, None, None, None, on_facts, None)
        elif args.png:
            view.get_snapshot(WebKit2.SnapshotRegion.VISIBLE, WebKit2.SnapshotOptions.NONE, None, on_snapshot)
        else:
            finish(0)

    def poll():
        def on_ready(v, res, _data=None):
            if v.evaluate_javascript_finish(res).to_boolean():
                GLib.timeout_add(150, lambda: (after_ready(), False)[1])  # one more frame before reading
            else:
                GLib.timeout_add(150, lambda: (poll(), False)[1])
        view.evaluate_javascript(READY, -1, None, None, None, on_ready, None)

    def on_load(_v, event):
        if event == WebKit2.LoadEvent.FINISHED:
            poll()

    view.connect("load-changed", on_load)
    view.connect("load-failed", lambda *_: finish(1, "load failed") or True)
    GLib.timeout_add_seconds(TIMEOUT_S, lambda: finish(1, "timeout waiting for the study page"))
    view.load_uri(args.url)
    loop.run()
    return result["code"]


if __name__ == "__main__":
    sys.exit(main())
