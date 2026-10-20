# Face 2: compact visibility

The header now offers **Compact view**. It changes the browser presentation only. Switch it off to return to the previous free-layout viewport.

## Reading connections

- Cards keep their sheet and value order. Labels wrap instead of being clipped. Column widths and row spacing account for label size and the number of connection ports.
- Connections run through separate lanes outside the cards. The two sides of a gutter use staggered ports, so unrelated links cannot merge along the same horizontal segment.
- Forbidden connections are muted red and dashed; required connections are muted green and solid. A light outline separates crossings. A crossing does **not** join two connections.
- A diamond joins the branches of one group. A small open circle edits an individual branch. The selected branch has its own stronger highlight.
- Connection numbers correspond to the side-panel list. Numbers also appear on unobstructed line segments. Every connection has a description and a keyboard-accessible target, including connections with no free space for a number badge.
- Hover, focus or select a connection to highlight its member cards and show its complete description above the canvas, including its gate and condition. Enter opens the existing connection/branch editor; double-click retains the existing editing gesture.
- **Fit** accounts for actual card bounds and leaves room for zoom controls. **100%** returns to readable text size. Large graphs may require panning; the interface identifies a reduced-scale view as an overview.

The toolbar and workspace now use the available window height rather than assuming a one-line toolbar. Below 850 CSS pixels, the inspector follows the canvas in a vertically scrollable workspace. Dialogs, condition builders, parameter rows and long connection descriptions can wrap. Nested conditions retain a visible indentation guide.

## Compatibility boundary

The imported editor.html, bridge.js, Face2Bridge, document-delta format, endpoints, revision checks, validation and persistence are unchanged. Face2Document.html() embeds two local resources, visibility.css and visibility.js, after the existing bridge. There are no new production dependencies or remote assets.

The adapter does not write bonds, parameters, sheet order, rule identifiers or undo history. Layout-only switching redraws the canvas, leaving editor fields and pending drafts in place. Existing vertical-drag coordinates remain presentation state. Display connection numbers may change after a connection is deleted; they are not persisted rule identifiers.

## Verification on 2026-10-20

| Check | Result |
| --- | --- |
| Actual presentation JavaScript, 60 geometry combinations | Passed |
| No overlapping cards, wires through cards, or coincident independent wire segments | Passed with deterministic element measurements |
| Mixed pair/group connections, parallel links, long labels, dense and sparse graphs | Covered by geometry combinations |
| Empty input and sheet-level formula/mapping/assert rules | Passed; advanced rules do not become value wires |
| Layout toggle state preservation, add/delete redraw, pre-existing drag offsets, drag release | Passed |
| Keyboard routing and named connection/branch targets | Passed in the adapter harness |
| Existing JavaScript bridge regression checks | Passed |
| JVM Face 2 document, bridge and workbench tests | 16 passed; 1 socket-dependent test skipped |
| Packaged application | Built successfully |
| Native browser screenshots, actual CSS metrics and responsive interaction checks | **Blocked; not claimed as passed** |

The geometry harness executes the real adapter with deterministic DOM measurements. It is not a browser renderer. Firefox failed during startup under the execution restrictions; the isolated Chromium fallback also failed. Automatic approval control rejected running the prepared browser checks outside the sandbox because sandbox escalation is disabled for this session. The attempted test-only DOM-library download also failed at DNS resolution. No new library was added to the product.

A routing defect was found and fixed during verification: an endpoint of one link could coincide with the departure segment of another link in the same gutter. Staggered port rows now separate those independent connections. The geometry oracle checks for this explicitly.

### Reproduce without launching a browser

From the repository root:

~~~sh
node tools/verify-face2-geometry.cjs
node tools/verify-face2-js.cjs
mvn -o -pl fw-desktop -Dtest=Face2DocumentTest,Face2BridgeTest,Face2WorkbenchTest test
~~~

Use the repository's normal Maven cache configuration. This session used a writable cache at target/code-preview-m2 with /home/yb/.m2/repository as its read-only tail.

Evidence is in target/face2-visibility/: geometry-result.json, java-tests.log, package.log and browser-startup diagnostics. This directory is ignored build output.

### Browser acceptance still required

On a host that permits browser startup and local sockets:

~~~sh
python3 tools/verify-face2-visibility.py --driver /path/to/geckodriver
java -cp fw-app/target/fw-app.jar tools/VerifyFace2Browser.java /path/to/geckodriver
~~~

The first script uses an isolated headless profile and example data with stubbed transport. It checks desktop/laptop/tablet/narrow dimensions, full labels, real pointer selection, keyboard editing, branch-pane visibility, pending parameter text, language switching and exact outgoing-payload equality before/after layout changes. It saves screenshots and a JSON result. The second script exercises the existing real JVM browser connection, save, edit-again and stale-submission behaviour.

These browser steps were prepared but could not be completed in this session. Visual acceptance, including text legibility at reduced zoom and modal behaviour on narrow windows, remains open.
