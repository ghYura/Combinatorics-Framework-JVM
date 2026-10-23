# AI delegation template validation — 2026-10-23

The templates in [AI_DELEGATION.md](../AI_DELEGATION.md) were checked against the JVM application's
`0.1.0-SNAPSHOT` build on JDK 25, using local fixtures and the Python add-on with `/usr/bin/python3`.
The application JAR SHA-256 was
`deface97164e9c5345f59f8a9183bb944f5da1828d429b66fb623b4019f51488`.

Every scenario passed `validate`, `plan` and `candidates`, then executed. Java fixtures and the plugin compiled
with `javac --release 25`. Template A also passed a three-case smoke run, triage and HTML export.

| Template | What was checked | Full-run cases | PASS | Expected DOMAIN_FAIL |
|---|---|---:|---:|---:|
| A | The three documented checkout files, unchanged | 48 | 44 | 4 |
| B | A's checkout logic called through an external Java JAR | 48 | 44 | 4 |
| C | Python workbook with the documented faulty `pricing.py` fixture | 12 | 11 | 1 |
| D | TOML spec with that fixture and C's Python properties | 7 | 6 | 1 |
| E | The unchanged plugin stub, packaged and discovered through its service entry | 4 | 4 | 0 |
| F | One rendered failing candidate from A, frozen into a single-source scenario | 1 | 0 | 1 |

Totals: **120 full-run cases**, 109 PASS and 11 expected DOMAIN_FAIL results, plus three passing smoke cases.
All runs had zero BROKEN, TIMEOUT, INFRA_FAIL, SKIPPED and CANCELLED outcomes. These are sample counts, not a
claim of 120 independent defects or assertions.

## Fixtures and reproduction

Use the invocation and workflow in the manual's §0.3–0.4. Run each scenario through:

```sh
cf validate --scenario SCENARIO --config PROPERTIES
cf plan --scenario SCENARIO --config PROPERTIES
cf candidates --scenario SCENARIO --config PROPERTIES --limit 3
cf run --scenario SCENARIO --config PROPERTIES --run-id UNIQUE_ID --checkpoint RUN.checkpoint
```

- **A:** copied the JSON, constraints and properties blocks verbatim. There were 72 generated candidates,
  24 excluded by the constraint and 48 executable cases. Failures had verdict 6, `CHECK_FAILED`.
- **B:** moved A's lost-order condition into a public static method in `example.CheckoutSystem` in a helper JAR, imported that class
  in HEAD and called its static method in the check. Passed the JAR through `cf.execution.classpath`; retained
  A's constraint and properties. This verifies the external-library integration described in B.
- **C:** substituted the local Python add-on path and interpreter in the properties. Used the
  `pricing.py` now included in C. Its basic/SAVE10/quantity-1 case returned -5, producing verdict 5, `NEGATIVE_TOTAL`.
- **D:** used the unchanged TOML block with C's local Python properties and fixture. Of 12 candidates,
  constraints removed 2 and pairwise selection omitted 3. The remaining 7 included the negative-price case.
- **E:** compiled the documented `ShopPlugin`, packaged its service registration, and supplied two factors:
  `tier = basic | gold`, `coupon = NONE | SAVE10`, each using `FW_Combi(1)`. Set
  `cf.execution.sut=shop` and `cf.execution.classpath` for inspection and execution. The placeholder total is
  always zero, so this verifies plugin wiring, not a shop's business logic.
- **F:** selected A's rendered mobile/guest/login+pay/timeout-present source and used the revised single-CASE
  recipe with the original verdict map. Planning and generation produced exactly one candidate; execution
  reproduced verdict 6. Merely retaining the chosen login/pay values with the old permutation directives
  produced two candidates, confirming why the earlier reduction advice needed correction.

## Findings reflected in the manual

- Added a runnable Python fixture and measured results for C and D.
- Replaced the ambiguous one-case reproducer instructions with the checked rendered-source recipe.
- Clarified that `--sut` is a `run` shortcut; inspection commands use `cf.execution.sut`.
- Corrected the XML-output guidance: `export` writes its selected file format and prints a plain-text confirmation.
- Recorded the existing spec-reader label spelling: D's verdict 2 is displayed as
  `FWCUSTOMVAR=2 negative total`; its passing verdict is code 0 without a supplied name.
- Clarified the Java bootstrap's once-before-the-first-case timing and the separate Python behavior.

## Scope

The template code was extracted from the manual, with environment paths substituted where required. All final
published executable blocks were checked against the extracted files. B is the documented adaptation of A;
E remains a stub; F contains a placeholder to replace with the selected rendered source.

No network services, browser showcase, production system or QA-edition comparison was run. The large §21 showcase
measurements were not repeated. Java heap was capped at 512 MiB per template command, and commands had 45-second
timeouts. Validation logs, checkpoints, fixtures and the command ledger were retained in the operator's local
`cf-work/ai-delegation-templates-20261023/` workspace; these generated files are not part of the repository.

The 22 required repository acceptance checks also passed with zero failures, errors or skips.
