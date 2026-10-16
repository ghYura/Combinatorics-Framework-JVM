# Face 2 integration verification — 2026-10-16

The original QA browser interface is integrated into the JVM workbench and has
passed live Firefox acceptance. Usage: [FACE2_BROWSER.md](FACE2_BROWSER.md).

## Source and application

- Repository: `ghYura/Combinatorics-Framework-JVM`.
- Integration base: `a44b908b018bd08411d29b65dfa4b1ca8aa90256`, verified against
  GitHub's live `main` before publishing.
- Application: `fw-app/target/fw-app.jar` (Java 25).
- Verified JAR SHA-256:
  `ab11cccd04f40a91485c414314b2a19098a96ca9ca01817df3679343eaa8efff`.
- Launch: `java -jar fw-app/target/fw-app.jar guif`, then select a scenario and
  open **Constraints / Sieve → Face 2 (Sieve) ↗**.
- Original template: 121,540 bytes, SHA-256
  `4e5af253ae0ef3a72bba29ebff693b89ba79d58c4c06677919204aca38b5c513`.
  The QA `_HTML` literal remains byte-identical. An appended transport adapter
  connects it to the JVM's existing validation, preview and companion-save path.

The JAR hash identifies the tested artifact; rebuilding may change archive
metadata. Build outputs are ignored by Git. The source, verifier and evidence
below are included in the repository.

## Verification results

- The complete 20-project reactor packages successfully.
- Desktop reports cover **118 tests with zero failures/errors/skips**. The
  earlier full run had one localhost skip; a focused host rerun passed all five
  `Face2BridgeTest` checks, including the formerly skipped socket check.
- `Face2WorkbenchTest` verifies actual HTTP-handler input → EDT → companion save
  → a fresh Framework run, executing **3 candidates instead of 4**. Repeated
  saves, undo/redo, retry after external file conflicts and stale submissions pass.
- Architecture, module, license, byte-type and hygiene gates: **22 passed**.
- Eight selected packaged-application tests passed under Failsafe, including
  required Face 2 assets, licenses, approved dependencies and CLI isolation.
- The Node adapter checks and exact original-template comparison passed.
- The browser verifier compiles with `-Xlint:all -Werror`; `git diff --check` passes.

A complete `mvn verify` was not rerun. These results describe the named checks,
not the full integration-test suite. The earlier broad invocation incorrectly
selected integration tests in Surefire, and a namespace-dependent test was blocked
in the restricted environment. No unrelated tests were weakened.

## Live browser evidence

Firefox drove the original rendered controls, drew a rule and submitted it.
The JVM workbench and scenario companion both acquired the rule, and the JVM
preview changed **8 → 6**. Edit again, delete and resubmit restored **8**.
A concurrent native edit then caused a stale browser submission to be rejected,
leaving the native edit intact. The temporary browser profile and test servers
were closed after the run; browser safeguards were not disabled.

- [Browser screenshot](docs/face2/screenshot.png)
- [Browser PASS report and tested JAR hash](docs/face2/browser-result.json)
- [Verification summary](docs/face2/verification.json)

The verifier checks rendered controls and persisted JVM state. It avoids relying
on page-level lexical variables that Firefox's WebDriver context cannot access.
Failure diagnostics include a screenshot and page source in the generated output.

## Reproduce

Use Java 25 and Maven; Node is needed only for the standalone JS checks.
Firefox and a local geckodriver are needed for browser acceptance.

```sh
mvn -B verify
node tools/verify-face2-js.cjs
java tools/SyncFace2Template.java --check --source ../Combinatorics-Framework-QA-edition/generator_trunk/constraints/editor.py
java -cp fw-app/target/fw-app.jar tools/VerifyFace2Browser.java /path/to/geckodriver
```

The template comparison needs the sibling QA checkout. The packaged browser UI
runs without that checkout, Python, npm, or a CDN. New browser evidence is written
to `fw-desktop/target/face2-browser/`; the committed evidence records the verified
integration run. Detailed local build logs are under `target/face2-verification/`.
