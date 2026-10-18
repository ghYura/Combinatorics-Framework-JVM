# guif dog-fooding results

The modeled headless suite is complete. Product defects remain deliberately unfixed; named DOMAIN_FAIL verdicts are the expected regression evidence. All current executions have zero BROKEN, TIMEOUT, INFRA_FAIL, SKIPPED and CANCELLED outcomes, and no negative harness verdicts.

## Initial Framework runs

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

Initial totals: {'PASS': 362, 'DOMAIN_FAIL': 83, 'BROKEN': 0, 'TIMEOUT': 0, 'INFRA_FAIL': 0, 'SKIPPED': 0, 'CANCELLED': 0}. The table includes 445 executed candidates. Counts for generated and excluded candidates come from Framework summaries, not Python enumeration.

## Framework covering evidence

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

Selection is greedy set-cover, not a proven minimum. Fixed strengths are t=2 or t=3 with budget=0. Mandatory-row counts precede FW_Optional expansion, so they can be smaller than executed counts. Tuple totals include constant harness/watcher sheets; they are Framework counts, not counts of distinct behavioral requirements. Every feasible tuple in these reports is covered; constraints account for the infeasible tuples.

## Defects and minimal runs

| ID / severity | Minimal Framework verdict | Expected → actual | One-candidate reproducer |
|---|---|---|---|
| D001 · medium accessibility | 16 `GUIF_T4_SPINNER_UNNAMED_INVARIANT` | Editable Sieve input has an accessible name → several inputs have blank names. The focused witness uses the scan spinner; other unnamed fields retain separate watcher metrics. | [D001_accessible_names.json](regression/D001_accessible_names.json) |
| D002 · medium functional | 20 `GUIF_T4_SPINNER_UNNAMED_2_INVARIANT` | Load a pair rule and ask the enabled Within spinner for its next value → `ClassCastException` from Long value / Double bounds. | [D002_sieve_spinner.json](regression/D002_sieve_spinner.json) |
| D003 · medium reporting | 12 `GUIF_T3_BUTTON_VALIDATE_AND_PLAN_INVARIANT` | `plan` with strength 2 and budget 99 on four rows reports all rows → internal null dereference while rendering the selection method. | [D003_coverage_budget_plan.json](regression/D003_coverage_budget_plan.json) |
| D004 · medium input validation | 16 `GUIF_T8_TEXT_AREA_ANALYSIS_REPORT_INVARIANT` | Refuse checkpoint/CSV content despite a `.kv` name and give metrics-export guidance → analysis accepts it. The checkpoint's `schema=1` is treated as a metric in a passing sample. | [D004_disguised_checkpoint.json](regression/D004_disguised_checkpoint.json) |

| Scenario | Generated / excluded / executed | PASS | DOMAIN_FAIL | Verdict code → name → count | Seconds |
|---|---:|---:|---:|---|---:|
| `D001_accessible_names` | 1 executed | 0 | 1 | 16 GUIF_T4_SPINNER_UNNAMED_INVARIANT: 1 | 2.876 |
| `D002_sieve_spinner` | 1 executed | 0 | 1 | 20 GUIF_T4_SPINNER_UNNAMED_2_INVARIANT: 1 | 2.978 |
| `D003_coverage_budget_plan` | 1 executed | 0 | 1 | 12 GUIF_T3_BUTTON_VALIDATE_AND_PLAN_INVARIANT: 1 | 3.428 |
| `D004_disguised_checkpoint` | 1 executed | 0 | 1 | 16 GUIF_T8_TEXT_AREA_ANALYSIS_REPORT_INVARIANT: 1 | 2.931 |

D003 is outside the desktop itself but was discovered while the Framework planned its GUI tests. Its one generated regression candidate owns one isolated CLI plan subprocess, because the public main may exit the VM. Other GUI candidates are batched in one Framework JVM per scenario.

Narrow source confirmations: SieveRuleEditor.java constructor/show establish the Double/Long mismatch; CandidatesReport.java dereferences the all-rows selection’s null method; AnalysisView.notMetrics classifies the extension before its content-sniff fallback. No product code was changed.

## Refined runs and campaign

| Scenario | Generated / excluded / executed | PASS | DOMAIN_FAIL | Verdict code → name → count | Seconds |
|---|---:|---:|---:|---|---:|
| `D001_accessible_names_t3` | 18 / 0 / 18 | 0 | 18 | 16 GUIF_T4_SPINNER_UNNAMED_INVARIANT: 18 | 9.704 |
| `D002_sieve_spinner_t3` | 12 / 0 / 12 | 6 | 6 | 0 PASS: 6; 20 GUIF_T4_SPINNER_UNNAMED_2_INVARIANT: 6 | 6.345 |
| `D004_disguised_metrics_t3` | 32 / 0 / 32 | 16 | 16 | 0 PASS: 16; 16 GUIF_T8_TEXT_AREA_ANALYSIS_REPORT_INVARIANT: 16 | 10.008 |
| `R003_runner_interactions_t3` | 30 / 10 / 20 | 20 | 0 | 0 PASS: 20 | 8.873 |
| `R004_sieve_guard_sequences` | 1 executed | 1 | 0 | 0 PASS: 1 | 3.429 |
| `R005_cancel_impact` | 1 executed | 1 | 0 | 0 PASS: 1 | 3.226 |

| Scenario | Framework coverage report |
|---|---|
| `D001_accessible_names_t3` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 18 mandatory rows; 10391 tuples, 10391 feasible and covered, 0 infeasible |
| `D002_sieve_spinner_t3` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 12 mandatory rows; 15992 tuples, 15992 feasible and covered, 0 infeasible |
| `D004_disguised_metrics_t3` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 32 mandatory rows; 1754 tuples, 1754 feasible and covered, 0 infeasible |
| `R003_runner_interactions_t3` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 20 mandatory rows; 7302 tuples, 7238 feasible and covered, 64 infeasible |
| `R004_sieve_guard_sequences` | coverage      t=3 set-cover (greedy, not a proven minimum): kept 1 mandatory rows; 12341 tuples, 12341 feasible and covered, 0 infeasible |
| `R005_cancel_impact` | coverage      t=2 set-cover (greedy, not a proven minimum): kept 1 mandatory rows; 630 tuples, 630 feasible and covered, 0 infeasible |

D001 increases graph combinations to t=3. D002 crosses rule body × 0/1/Long.MAX_VALUE × next/previous: six enabled pair-rule cases reproduce the defect, while six formula-rule cases verify the disabled boundary. D004 crosses misleading contents × coverage-import format × repeated Analyze at t=3. R003 expands runner settings to all feasible triples; its name deliberately does not imply another coverage-budget reproducer. R004 and R005 close the ten Sieve action gaps with enabled-control sequences.

The focused spinner campaign maximizes violations, uses bias for two iterations with an exploration floor of 0.25, and writes a reusable seed under target. It ran 12 candidates per iteration. The second iteration used the seed but excluded no rows; no efficiency gain is claimed.

- strategy    bias, 2 of 2 iterations, stop when stable after 99, exploration floor 0.25, min winner support 5
- iteration 1 spinner_campaign-it1 COMPLETED: executable 12 of 12, front 6, stable 0; the base scenario (full space)
- iteration 2 spinner_campaign-it2 COMPLETED: executable 12 of 12, front 6, stable 1; bias: seed bias produced no exclusions: every sheet lacked support, already covered >=80%, or was protected by the exploration floor
- state       COMPLETED in 5706 ms
- candidates  generated 12, excluded 0, left out by covering selection 0, executable 12
- outcomes    PASS 6, DOMAIN_FAIL 6, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0
- verdicts    0 PASS: 6; 20 GUIF_T4_SPINNER_UNNAMED_2_INVARIANT: 6
- seed        11 winners of 12 samples, content 78ad88d9f27cf7a70b8b0cb505b36153dc52dd60bd4be3627ee53a8f0c43cd2d
- state       COMPLETED in 2493 ms
- candidates  generated 12, excluded 0, left out by covering selection 0, executable 12
- outcomes    PASS 6, DOMAIN_FAIL 6, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0
- verdicts    0 PASS: 6; 20 GUIF_T4_SPINNER_UNNAMED_2_INVARIANT: 6
- seed        11 winners of 12 samples, content f182640571bdaf49cb2b7d37a424f5e8cd8ed5aafb074ac512d7294f1e110d74

## Object and sequence evidence

206/206 Appendix A objects are exercised by the current selected candidates. [Per-object modes and counts](object_coverage.md) distinguish button activation, backing APIs, selections and boundary inspection. [Ordered-sequence accounting](sequence_coverage.md) records 27/27 action triples in each optional branch, and 9/9 contiguous pairs plus 27/27 contiguous triples in the uninterrupted branch.

Checkpoint provenance: 533 sample identities; {'exact_options_and_watchers': 274, 'direct_id': 259}. The identities come from CheckpointCodec, and matching checks every emitted watcher count. Earlier calibration cases cannot inflate these counts.

Every one of the 60 run-control and 84 ordered-run candidates exports metrics through RunView and analyzes them through AnalysisView. Verdict-bearing sample counts match the analyzer; cancelled/unverdicted samples remain trailing comments. All five export kinds are selected across the run-control triples.

Native file/search dialogs, confirmation/error dialogs, system clipboard integration, browser launch and chart windows remain outside this headless run. Chooser construction, placement/guidance, search results, validation APIs and offscreen chart rendering cover their documented backing logic. Disabled controls and read-only widgets have explicit inspection/selection modes; 206/206 does not mean every object received a mouse click or every behavior was tested. Face 2 HTTP was not newly exercised; the existing socket test remains skipped in the baseline. Watcher threads are explicitly joined and Desktop.close completes; platform-thread census cannot prove absence of every product virtual-thread leak. Large-directory, permissions and symlink stress beyond the modeled search fixtures are follow-up coverage.

## Baseline and protected state

start: [INFO] Tests run: 132, Failures: 0, Errors: 0, Skipped: 1; BUILD SUCCESS
end: [INFO] Tests run: 132, Failures: 0, Errors: 0, Skipped: 1; BUILD SUCCESS

1086 source/test/trail files match the pre-task SHA-256 snapshot; zero protected changes. Both baseline invocations were completed before the requested pause. On resumption only test assets and reports changed; the baseline was not run a third time. A fresh one-command rerun brackets all candidate execution with its own baseline pair.

## Calibration and operational notes

Superseded calibration results are excluded from the current count tables. Harness corrections addressed: integer campaign stopWhenStable; budget=0 to honor fixed coverage strength; asynchronous Sieve Apply completion; the exact headless data-draft validator; verdict-bearing `.kv` lines versus committed cancelled samples; recognizable CSV/checkpoint fixtures; Auto-plan preserving manual keys rather than JSON formatting; filename recognition of `.kv`; search cancellation retaining pre-offered roots; and structural selectors for the stable Only when condition editor. The spinner refinement initially appended Java factor statements after TAIL in dictionary order and failed compilation; replacing its three factor slots in place fixed the harness while preserving code 20. These are not additional product findings.

The initial offline shade build hit duplicate META-INF/NOTICE.md; forcing jar recreation succeeded. Restricted DNS prevented downloading the install plugin, so existing reactor artifacts were staged into a scratch Maven repository. The rerun script chains that scratch repository to the user repository, locks out overlapping batches and terminates owned child processes on interruption.

## Reproduce

`python3 guif_dogfood/run_suite.py all` from the repository root rebuilds, runs the two baselines, executes initial/regression/refined cases and the focused campaign, collects coverage, indexes selected evidence and regenerates reports. See [README](../README.md).
