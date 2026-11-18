# Targeted native visual corrections

This local development plugin edits 52 existing states in the original Figma file after the completed 456-state import. It is design tooling only. It does not implement the application, approve designs, replace roots, bind tokens, build reusable components or update the immutable import fingerprints.

The frozen correction plan is derived from the complete native before-observation and the frozen HTML rendered at 1,440px. It reconciles the seven affected families: S-02, S-09, S-18, S-22, S-23, S-39 and I-13. Reflow preserves every existing text ID; surplus old line nodes become empty, and additional measured lines share the last existing text node. Non-whitespace source content must agree before planning. Inline badges retain their IDs and are painted after the overlapping comparison fill. The existing I-13 input stops clipping its outline. PDF preview rectangles receive the measured one-pixel border. Missing disc markers are small editable ellipses using the source list color.

Build into a **new** local folder:

```powershell
python tools/build_figma_native_closure.py --before docs/design/reviews/2026-11-18-native-closure-before.json --plan design/figma-native-closure/correction-plan.json --output .cache/native-closure-build
```

Copy only the generated code/UI to the existing registered local plugin, retaining its real manifest ID and the offline manifest settings. Keep the plugin open until export and version saving are complete. An already corrected target is intentionally refused by the original before-fingerprint guard; never rerun the importer or silently reset its fingerprint after a visual correction.

The plugin checks the exact file/page/root/parent identities, full fingerprints and protected pages before writes. It validates every edit's membership and supported properties, loads exact existing fonts, then repeats the target fingerprints after that asynchronous step. No source or unrelated node can be an edit target. A setter error rolls back its scoped edits, sibling ordering and newly created markers. After mutation, export rechecks all 456 root IDs, fingerprints every untouched state, and writes 52 native PNG exports plus actual changed-node IDs and before/after hashes. Native export and visual inspection remain separate requirements.

Use the final-version button only after exports. It calls Figma's documented [saveVersionHistoryAsync](https://developers.figma.com/docs/plugins/api/properties/figma-saveversionhistoryasync/) and records its returned version identity. The editor's native undo boundary uses [commitUndo](https://developers.figma.com/docs/plugins/api/properties/figma-commitundo/). The plugin makes no HTTP request and changes no subscription.

Run `node --check design/figma-native-closure/runtime.js` and `node --test design/figma-native-closure/test-runtime.cjs`. Fixtures cover wrong targets, protected/target drift, off-root/unsupported edits, missing fonts, concurrent changes during font loading, retained identities, actual edit reporting and native-setter rollback. They are not native rendering or application tests. Actual run evidence and remaining review gates belong in the dated closure report.
