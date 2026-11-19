# Paper and blueprint on entry screens

This builds a review copy of the [frozen offline mockups](../offline-reference/README.md) with variant B applied to S-25, S-02 and S-01. It uses the approved grain, blueprint symbols and `neutral-dark` tone from the [background study](../../docs/design/background-study/README.md). See the [render review](../../docs/design/reviews/2026-11-19-entry-background.md) for comparisons, measurements and Figma status.

## Preview

Use the Python design environment with Beautiful Soup from the [locked design requirements](../corrections/requirements.txt). From the repository root, choose a new external directory and serve it locally:

```sh
python design/entry-background/build.py --output /tmp/etaye-entry-review
python -m http.server 8765 --bind 127.0.0.1 --directory /tmp/etaye-entry-review
```

Open `http://127.0.0.1:8765/entry-background.html` and choose a screen state and theme. HTTP allows Chromium to load the same-origin SVG mask. The generated copy contains the original navigation and assets so links to other screens can still be followed. The original snapshot remains the baseline for comparison.

The [one-run Figma Desktop handoff](figma/README.md) packages the 20 desktop light-theme variants when the connector cannot write. It exports native evidence for review.
