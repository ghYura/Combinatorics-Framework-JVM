# guif dog-fooding assets

> **Status after the fixes (2026-10-18).** The four defects this suite found (D001–D004) are fixed in the product, and
> each one-candidate witness in `post-results/regression/` now yields `0 PASS: 1`
> (`python3 guif_dogfood/postprocess.py --rerun --phase regression`). Because D001 gave the formerly unnamed Step 4
> (Sieve) controls their accessible names, 14 selectors in `initial/probe/objects.tsv` now name them ("Scan ≤",
> "columns apart", "Find cards", "Row filter", "Column filter", "Find rules", "Group as", "Rows", "Columns", "Value",
> "Kind", "Effect", "Ranked values", "Messages"). The rest of this README and the reports below describe the run as
> Codex executed it, before the fixes. A report rebuild checks the source hashes taken before that run, so it stops
> once product sources have changed; a fresh `run_suite.py all` takes new hashes first.

The modeled headless suite is complete: **206/206 objects exercised**, 557 Framework candidate executions including refinements and campaign rounds, and four minimized product defects. See the [experience report](../testXP_guif_byCodex.md) and [results](post-results/results_summary.md). Known defects remain unfixed and intentionally produce named DOMAIN_FAIL verdicts.

From the repository root, a fresh complete rerun is:

```sh
python3 guif_dogfood/run_suite.py all
```

Java 25, Maven and cached build dependencies are required. The script uses a scratch Maven repository chained to the existing user repository. It does not install into `~/.m2`. Everything runs with `java.awt.headless=true`, one Framework worker and signed-byte labels. FlatLaf animation is disabled. Product source is not modified. Known product defects intentionally yield named DOMAIN_FAIL verdicts; a zero CLI exit code alone does not establish a passing suite.

- `initial/00_object_model.md`: stable identities and risks for the 206 Appendix A objects.
- `initial/01_watchers_and_codes.md`: event architecture, oracles, and all Framework verdict names.
- `initial/02_factors_constraints.md`: factors, constraints, ordered sequences, FW_Optional interruptions and byte budgets.
- `initial/scenarios/`: sixteen Framework scenarios with constraints and explicit properties.
- `initial/probe/`: Java driver/watchers, generated selector metadata and build script.
- `post-results/regression/`: focused one-candidate defect witnesses.
- `post-results/refined/`: executed t=3 refinements, targeted Sieve action sequences and a two-round bias campaign configuration.
- `post-results/results_summary.md`: Framework counts, verdicts and triage status.
- `post-results/object_coverage.md`: per-object coverage and exercise modes from current selected candidates.
- `post-results/sequence_coverage.md`: ordered pairs/triples and actual FW_Optional interruption dispatches.
- `RESUME.md`: completion handoff and the earlier pause's history.

Logs, checkpoints, temporary fixtures, nested GUI exports, event evidence and scratch dependencies live under ignored `target/guif-dogfood/`. Each candidate creates an isolated fixture folder with `evidence.json`. `EvidenceIndex` reads sample identities and watcher metrics through the Framework's CheckpointCodec. New samples link directly through `evidence_id`; older samples match every factor, verdict and watcher count. Unselected calibration evidence remains on disk but is excluded from reported coverage. `completion-summary.json` contains compact machine-readable totals and provenance.

`build_assets.py` regenerates initial scenarios, metadata and the code map from the supplied inventory. `refine_assets.py` regenerates the four combinatorial refinements and campaign configuration. Preserve the recorded design when changing either. The driver received calibration corrections after the initial design; `target/guif-dogfood/design-before.json` preserves the original hashes. Every report rebuild audits dictionary order, watcher codes, signed-byte properties and the label budget (maximum 90/127).

Useful commands:

```sh
bash guif_dogfood/initial/probe/build.sh
python3 guif_dogfood/run_suite.py initial t6_sequences
python3 guif_dogfood/postprocess.py --rerun --phase regression
python3 guif_dogfood/postprocess.py
```

The commands compile test assets, run one selected initial scenario, rerun only the minimal regressions, and refresh reports without executing candidates. `postprocess.py --rerun` executes all regressions, refinements and the campaign. The full `all` command runs exactly two JUnit baselines; focused commands run none. A process lock prevents overlapping batches, and interruption terminates only the batch's owned child process group.

The task's two baseline runs both passed: 132 tests, zero failures/errors, one existing socket-test skip. They preceded the requested pause. After resumption, protected source/test/trail hashes were checked again instead of running a third baseline. All 1,086 protected files match. A fresh `all` rerun places its ending baseline after all candidate execution.

Native modal dialogs, clipboard integration, browser launch and chart windows are excluded by the headless requirement. Their backing logic is covered where documented. Inspection, read-only selection, backing-API exercise and actual button events are separate coverage modes; none should be presented as native-window coverage.
