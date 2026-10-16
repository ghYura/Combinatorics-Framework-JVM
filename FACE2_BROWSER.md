# Face 2 (Sieve) in the browser

The integration is built and verified in Firefox, including saving from the
original web interface and acquiring the saved sieve in the JVM Framework.
See [FACE2_CHECKPOINT.md](FACE2_CHECKPOINT.md) for the artifact hash and verification record.

Launch the built application with Java 25:

```sh
java -jar fw-app/target/fw-app.jar guif
```

Select a scenario in step 3, open **Constraints / Sieve**, then click
**Face 2 (Sieve) ↗** beside **Save for scenario**. The button also opens the
selected scenario's constraints if they have not been opened yet. Apply any
pending JVM rule, parameter or JSON draft first.

This opens the **original QA edition web interface** in the system browser.
Its HTML, CSS and JavaScript are packaged inside the JAR. No Python process,
npm install, CDN, database or separate web application is required at runtime.
If the operating system cannot open a browser automatically, copy the address
displayed in the tab into a browser. The connection requires local loopback sockets.

Draw and edit with the existing web controls. **Submit** validates and saves
through the same JVM Session and workbench save path as **Save for scenario**.
The JVM graph, rule inspector, JSON and undo history acquire the result.
Subsequent runs and previews discover the normal `*.constraints.json` companion.
**Edit again** reloads the saved snapshot and allows another editing cycle.
Closing the JVM closes its local browser connection.

## Original interface and JVM data

`fw-desktop/src/main/resources/org/ghyura/cf/desktop/face2/editor.html` is the
byte-for-byte `_HTML` literal from the sibling QA edition's
`generator_trunk/constraints/editor.py`. Its SHA-256 is
`4e5af253ae0ef3a72bba29ebff693b89ba79d58c4c06677919204aca38b5c513`
(121,540 UTF-8 bytes). The original title/data placeholders are filled at runtime;
`bridge.js` is appended as the JVM transport adapter. The visual template remains unchanged.
The source's BUSL-1.1 terms and author attribution remain applicable; see LICENSE and NOTICE.md.

The adapter gives the existing JVM communication and execution rules priority:

- Browser Submit acknowledges success only after JVM validation and saving succeed.
  Failed saves leave the JVM document unchanged and the browser edits available for
  retry. Save errors stay visible even when a delayed impact response arrives.
  Pending field/dialog edits must be applied or discarded; the page pauses editing
  while its submitted snapshot is being saved.
- Original rule IDs, descriptions, order, parameters, declared orders, JSON types
  and independent rule boundaries survive an unchanged round trip. Delta updates
  preserve untouched integers beyond JavaScript's exact numeric range.
  Editing one parameter in a web row also preserves the types and exact values of
  its unchanged neighbours. Parameters with embedded commas or equals signs can
  still be viewed; edit those rows in JVM Advanced JSON.
- New drawn links remain independent rules. Existing multi-tuple rules remain
  grouped; an edit that would split one into differently gated/type rules is
  refused with an explanation because splitting could change its meaning.
- Formulas, sets, conditions, assertions and mappings go through the existing
  constraint reader and engine. Configured bonds and configuration precedence
  remain in Session; ambiguous companion configurations retain their existing errors.
  Assertion gates are retained as data; execution follows the existing JVM engine.
- Impact comes from the actual bounded JVM candidate preview, including optional
  branches and selection. Unknown/bounded counts and errors are labelled rather
  than replaced with a local browser estimate. No SUT is executed by impact.
- A changed JVM document, scenario, configuration or pending draft blocks a stale
  browser submission. External companion edits retain the existing fingerprint
  protection. Browser reloads use a new revision after a successful save.
- Downloads are serialized by Java to retain exact JSON values. Editing an unsafe
  JavaScript integer is refused; use JVM Advanced JSON for that value.

The HTTP server binds only `127.0.0.1`, uses an unpredictable session path,
checks Host/Origin, accepts bounded JSON bodies and serves no arbitrary files.

## Verification

```sh
mvn -B verify
java tools/SyncFace2Template.java --check
node tools/verify-face2-js.cjs
```

The Java tests cover the original template hash, script-safe data injection,
round trips, advanced rule kinds, precision, grouping, invalid edits, the HTTP
handler, exact/bounded impact, browser reloads, stale revisions, and button
placement/draft protection. Literal `__TITLE__` sheet names, `_jvmSource` parameter
keys, and edited assertion gates also retain their data. `Face2WorkbenchTest`
submits through the real HTTP
handler and workbench callback, saves using the existing companion path, and
checks a **fresh Framework run** executes 3 candidates instead of 4. It also
checks repeated saves, undo, recovery after an external file conflict, and
rejection after native edits or configuration changes. The JavaScript check runs
the packaged adapter and checks original script syntax without npm dependencies.

Verified on 2026-10-16:

- The complete reactor packages successfully; the updated runnable JAR is
  `fw-app/target/fw-app.jar`.
- Desktop: **118 tests, zero failures/errors**. The previously skipped localhost
  check passed in the focused host run of all five `Face2BridgeTest` checks.
- Live Firefox: **PASS**. Drawing/submitting a rule saves it to both the workbench
  and the scenario companion, with **8 → 6** candidates in the JVM preview.
  Reload/delete/resubmit restores **8**; concurrent native edits reject a stale
  browser submission without overwriting the native edits.
- Architecture, module, license, byte-type and hygiene gates: **22 passed**.
- Eight selected packaged-application tests passed in Failsafe, including required
  Face 2 classes/resources, licenses, approved dependencies, CLI operation and
  scenario reading without loading the browser/server path.
- Original template equality, the Node adapter checks, browser-harness compilation
  with warnings as errors, and `git diff --check` passed.
- The packaged web resources and changed production classes match the current
  build. The [verification summary](docs/face2/verification.json) is included in
  the repository; detailed local logs are under `target/face2-verification/`.

This is targeted verification; a complete `mvn verify` was not rerun. The earlier
broad invocation mistakenly selected integration tests in Surefire, and a
namespace-dependent test was blocked in the restricted environment. Historical
evidence is retained in `target/face2-verification/`; see the checkpoint for details.

For a live browser test on a host that allows Firefox and localhost sockets:

```sh
java -cp fw-app/target/fw-app.jar tools/VerifyFace2Browser.java /path/to/geckodriver
```

This Java acceptance check uses Firefox's WebDriver protocol directly, with no
Selenium dependency. It uses a temporary browser profile and scenario,
opens the actual workbench connection, draws a rule in the original web UI,
checks the saved 8 → 6 candidate reduction, reloads and deletes it (6 → 8),
and verifies a concurrent JVM edit prevents a stale submission. Evidence goes
to `fw-desktop/target/face2-browser/`; `result.json` identifies the tested JAR by
SHA-256. The recorded run passed on 2026-10-16. See
[the browser screenshot](docs/face2/screenshot.png) and
[the result](docs/face2/browser-result.json).

The initial checkout used the existing local repository while network access was
restricted. After host execution was enabled, GitHub's live `main` was checked:
it matches the checkout base `a44b908b018bd08411d29b65dfa4b1ca8aa90256`.
The browser check ran with a temporary Firefox profile and its normal safeguards.
