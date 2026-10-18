# guif testing experience

Completed the modeled headless dog-fooding suite: 206/206 objects exercised, 445 initial candidates, four minimized defects, 84 refined candidates and a two-round focused campaign. The existing 132-test baseline remains green with its one pre-existing socket-test skip. Product fixes are left for a separate change.

## 1. Scope and approach

The Framework generated and ran tests against its own nine-tab FlatLaf desktop using generated-java. Sixteen partitions keep the signed-byte label budget small while separating Sieve graph, drafts and rules and giving search, dialogs and shell behavior their own factors. Repeated actions, invalid inputs, cancellation and missing prerequisites are modeled as meaningful paths. Pairwise coverage is the floor; risky interactions use triples.

## 2. Design decisions

Each Appendix A object has a stable ID, EDT-installed listeners, immutable snapshots and a dedicated guif-watch virtual thread. Its watcher sheet assigns a named FW_EXIT_CODE, while all watchers emit metrics even if a higher-priority watcher owns the verdict. Code maps follow data-sheet ordinals. SIGNED_BYTE is forced; the maximum label allocation is 90/127, no virtual joiner sheets or reserved -128 code are used. Companion constraints exclude selected rows without a library and Python-only invalid settings under the Java runner.

The package-peer probe lives on the application classpath; generated candidates call its public API. All Swing access runs on the EDT, future waits run outside it, and teardown joins watchers and awaits Desktop.close. Ordered first/second/third action factors retain all 27 pause/resume/cancel triples. Actual FW_Optional sheets insert sudden cancel/refresh or draft undo/validate, with omission as a separate Framework-generated branch.

Initial design: [object model](guif_dogfood/initial/00_object_model.md), [watchers and code map](guif_dogfood/initial/01_watchers_and_codes.md), [factors and constraints](guif_dogfood/initial/02_factors_constraints.md). The frozen design hashes and calibration history are retained under target; corrected harness assumptions are documented rather than rewritten as original predictions.

## 3. Execution summary

| Scenario | Generated / excluded / executed | PASS | DOMAIN_FAIL | Verdict code → name → count | Seconds |
|---|---:|---:|---:|---|---:|
| `t1_library` | 12 / 4 / 7 | 7 | 0 | 0 PASS: 7 | 6.875 |
| `t2_authoring` | 24 / 0 / 12 | 12 | 0 | 0 PASS: 12 | 10.840 |
| `t3_runner` | 30 / 10 / 16 | 16 | 0 | 0 PASS: 16 | 9.552 |
| `t4_graph` | 18 / 0 / 9 | 0 | 9 | 16 GUIF_T4_SPINNER_UNNAMED_INVARIANT: 9 | 10.257 |
| `t4_data` | 108 / 0 / 54 | 0 | 54 | 16 GUIF_T4_CHOICE_UNNAMED_4_INVARIANT: 54 | 15.470 |
| `t4_rules` | 12 / 0 / 12 | 0 | 12 | 6 GUIF_T4_TEXT_FIELD_UNNAMED_4_INVARIANT: 12 | 10.078 |
| `t5_candidates` | 48 / 0 / 14 | 14 | 0 | 0 PASS: 14 | 8.503 |
| `t6_run` | 60 / 0 / 60 | 60 | 0 | 0 PASS: 60 | 23.449 |
| `t6_sequences` | 162 / 0 / 84 | 84 | 0 | 0 PASS: 84 | 22.655 |
| `t7_campaign` | 24 / 0 / 12 | 12 | 0 | 0 PASS: 12 | 9.349 |
| `t8_analysis` | 32 / 0 / 16 | 8 | 8 | 0 PASS: 8; 16 GUIF_T8_TEXT_AREA_ANALYSIS_REPORT_INVARIANT: 8 | 6.900 |
| `t9_scorer` | 36 / 0 / 11 | 11 | 0 | 0 PASS: 11 | 5.941 |
| `x_dialogs` | 24 / 0 / 12 | 12 | 0 | 0 PASS: 12 | 7.549 |
| `x_search_data` | 189 / 0 / 63 | 63 | 0 | 0 PASS: 63 | 15.259 |
| `x_search_tools` | 162 / 0 / 54 | 54 | 0 | 0 PASS: 54 | 13.551 |
| `x_shell` | 18 / 0 / 9 | 9 | 0 | 0 PASS: 9 | 7.094 |

Initial: 445 executions, 362 PASS and 83 DOMAIN_FAIL. Four one-candidate regressions reproduce the four findings. Refined: 84 executions, 44 PASS and 40 DOMAIN_FAIL. The focused campaign adds 24 executions, 12 PASS and 12 DOMAIN_FAIL. Across these current runs: 557 executions, 418 PASS and 139 DOMAIN_FAIL, zero execution/harness errors. Nested GUI runs are additional SUT activity, not added to this outer candidate total.

start: [INFO] Tests run: 132, Failures: 0, Errors: 0, Skipped: 1; BUILD SUCCESS
end: [INFO] Tests run: 132, Failures: 0, Errors: 0, Skipped: 1; BUILD SUCCESS

## 4. Defects

| ID / severity | Minimal Framework verdict | Expected → actual | One-candidate reproducer |
|---|---|---|---|
| D001 · medium accessibility | 16 `GUIF_T4_SPINNER_UNNAMED_INVARIANT` | Editable Sieve input has an accessible name → several inputs have blank names. The focused witness uses the scan spinner; other unnamed fields retain separate watcher metrics. | [D001_accessible_names.json](guif_dogfood/post-results/regression/D001_accessible_names.json) |
| D002 · medium functional | 20 `GUIF_T4_SPINNER_UNNAMED_2_INVARIANT` | Load a pair rule and ask the enabled Within spinner for its next value → `ClassCastException` from Long value / Double bounds. | [D002_sieve_spinner.json](guif_dogfood/post-results/regression/D002_sieve_spinner.json) |
| D003 · medium reporting | 12 `GUIF_T3_BUTTON_VALIDATE_AND_PLAN_INVARIANT` | `plan` with strength 2 and budget 99 on four rows reports all rows → internal null dereference while rendering the selection method. | [D003_coverage_budget_plan.json](guif_dogfood/post-results/regression/D003_coverage_budget_plan.json) |
| D004 · medium input validation | 16 `GUIF_T8_TEXT_AREA_ANALYSIS_REPORT_INVARIANT` | Refuse checkpoint/CSV content despite a `.kv` name and give metrics-export guidance → analysis accepts it. The checkpoint's `schema=1` is treated as a metric in a passing sample. | [D004_disguised_checkpoint.json](guif_dogfood/post-results/regression/D004_disguised_checkpoint.json) |

All four minimal cases completed with the named nonzero verdict shown above. D001’s broad initial failures can mask D002 in the first-failure verdict; per-object metrics preserve both, and the focused D002 witness enables only its target verdict sheet. Full outcome tables and source confirmations are in [results_summary.md](guif_dogfood/post-results/results_summary.md).

## 5. Coverage achieved and limits

| Scenario | Framework coverage report |
|---|---|
| `t1_library` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 7 mandatory rows; 185 tuples, 183 feasible and covered, 2 infeasible |
| `t2_authoring` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 12 mandatory rows; 893 tuples, 893 feasible and covered, 0 infeasible |
| `t3_runner` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 16 mandatory rows; 652 tuples, 650 feasible and covered, 2 infeasible |
| `t4_graph` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 9 mandatory rows; 813 tuples, 813 feasible and covered, 0 infeasible |
| `t4_data` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 18 mandatory rows; 4267 tuples, 4267 feasible and covered, 0 infeasible |
| `t4_rules` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 12 mandatory rows; 15992 tuples, 15992 feasible and covered, 0 infeasible |
| `t5_candidates` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 7 mandatory rows; 225 tuples, 225 feasible and covered, 0 infeasible |
| `t6_run` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 30 mandatory rows; 1499 tuples, 1499 feasible and covered, 0 infeasible |
| `t6_sequences` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 28 mandatory rows; 1810 tuples, 1810 feasible and covered, 0 infeasible |
| `t7_campaign` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 6 mandatory rows; 115 tuples, 115 feasible and covered, 0 infeasible |
| `t8_analysis` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 16 mandatory rows; 263 tuples, 263 feasible and covered, 0 infeasible |
| `t9_scorer` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 11 mandatory rows; 488 tuples, 488 feasible and covered, 0 infeasible |
| `x_dialogs` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 12 mandatory rows; 551 tuples, 551 feasible and covered, 0 infeasible |
| `x_search_data` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 63 mandatory rows; 1440 tuples, 1440 feasible and covered, 0 infeasible |
| `x_search_tools` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 54 mandatory rows; 1313 tuples, 1313 feasible and covered, 0 infeasible |
| `x_shell` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 9 mandatory rows; 113 tuples, 113 feasible and covered, 0 infeasible |

All 206 Appendix objects have bound and exercised watchers in the current checkpoint-selected evidence, with modes and counts in [object_coverage.md](guif_dogfood/post-results/object_coverage.md). Run-control traces cover 27/27 positional triples in each absent/cancel/refresh branch; uninterrupted traces cover all 9 ordered pairs and 27 contiguous triples. Optional actions remain adjacency barriers. The two Sieve draft-order branches and sudden undo/validate dispatches are also accounted for in [sequence_coverage.md](guif_dogfood/post-results/sequence_coverage.md).

The Run-tab metrics export → Results-analysis round trip succeeds in all 144 run/control-sequence cases. Counts exclude comments for samples without verdicts. Export kinds, run timing and control actions use t=3. Search kind × roots × contents interactions also use t=3.

Native file/search dialogs, confirmation/error dialogs, system clipboard integration, browser launch and chart windows remain outside this headless run. Chooser construction, placement/guidance, search results, validation APIs and offscreen chart rendering cover their documented backing logic. Disabled controls and read-only widgets have explicit inspection/selection modes; 206/206 does not mean every object received a mouse click or every behavior was tested. Face 2 HTTP was not newly exercised; the existing socket test remains skipped in the baseline. Watcher threads are explicitly joined and Desktop.close completes; platform-thread census cannot prove absence of every product virtual-thread leak. Large-directory, permissions and symlink stress beyond the modeled search fixtures are follow-up coverage.

## 6. Dog-fooding friction and improvements

Give Sieve controls accessible names and section-stable identifiers. Keep spinner values and bounds in one numeric type. Render all-rows coverage selections without assuming a method is present, and make budget-versus-strength precedence explicit. Sniff known wrong-file formats before accepting a .kv extension. Expose invalid-draft results independently from modal dialogs. Add CLI .kv export so batch users can use the same analysis workflow as the Run tab.

Preserve explicit dictionary/code-map checks when extending generated Java sheets: moving a FW_Seq row alone did not move the appended statements in this refinement. Matching dictionary and execution order, or replacing slots in place, avoids compile errors and ordinal drift. Check sample outcomes as well as process exit status: a completed CLI command can contain BROKEN or DOMAIN_FAIL samples.

The scratch Maven repository and forced jar recreation made the build repeatable without installing into the user repository. The harness now locks batches and terminates their owned child process groups on interruption. Superseded calibration results are excluded from the current count tables. Harness corrections addressed: integer campaign stopWhenStable; budget=0 to honor fixed coverage strength; asynchronous Sieve Apply completion; the exact headless data-draft validator; verdict-bearing `.kv` lines versus committed cancelled samples; recognizable CSV/checkpoint fixtures; Auto-plan preserving manual keys rather than JSON formatting; filename recognition of `.kv`; search cancellation retaining pre-offered roots; and structural selectors for the stable Only when condition editor. The spinner refinement initially appended Java factor statements after TAIL in dictionary order and failed compilation; replacing its three factor slots in place fixed the harness while preserving code 20. These are not additional product findings.

## 7. Token economy

Rich design artifacts were written before running candidates. Execution was batched per scenario with logs, exports and checkpoints redirected under ignored target folders; triage used summaries and compact evidence. Calibration consumed avoidable repeated work. Next time, establish configuration precedence, asynchronous completion and negative-file formats in the calibration set before selecting the broad suite. No measured token split is available, so none is invented.

## 8. Re-run and next extensions

Run `python3 guif_dogfood/run_suite.py all` from the repository root. [README](guif_dogfood/README.md) explains prerequisites, focused commands and artifacts. Known findings intentionally remain DOMAIN_FAIL; zero BROKEN/TIMEOUT/INFRA_FAIL and negative harness codes are required. The spinner campaign saves a seed but its second round excludes no rows, so it demonstrates feedback without a reduction claim.

After product fixes, rerun the minimal witnesses and the affected refinements. Future coverage can extend longer ordered histories, close-during-work races, search filesystem stress and native dialog/browser/clipboard behavior in a separately authorized desktop environment.

Protected-state verification: 1086 pre-existing source/test/trail files checked, zero changes. No commit, push or live-desktop window was created. Exactly two baseline runs were used; both preceded the pause, with protected hashes rechecked after resumed test-only work.
