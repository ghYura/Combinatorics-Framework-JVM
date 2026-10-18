# Completion handoff

The requested pause was honored with a checkpoint. The later `continue` instruction resumed the same task; its modeled headless scope is now complete. This file replaces the earlier pending-work list.

- All 206 Appendix A objects have dedicated, bound watchers and exercise evidence from currently selected Framework candidates. Coverage modes distinguish actual controls, backing APIs, selections and headless boundaries.
- Sixteen initial scenarios executed 445 candidates: 362 PASS, 83 DOMAIN_FAIL.
- Four one-candidate regressions confirm unnamed Sieve inputs, the enabled Within spinner type mismatch, all-rows coverage reporting failure, and acceptance of checkpoint/CSV content disguised as metrics.
- Six refinements executed 84 candidates: 44 PASS, 40 DOMAIN_FAIL. The targeted Sieve sequences closed all ten remaining object gaps.
- The focused spinner campaign executed two rounds of 12 candidates each: 12 PASS and 12 DOMAIN_FAIL overall. It saved a seed but reported no exclusions in its second round.
- Combined current outer executions: 557, with 418 PASS and 139 DOMAIN_FAIL. Zero BROKEN, TIMEOUT, INFRA_FAIL, SKIPPED, CANCELLED or negative harness verdicts. Nested GUI activity and superseded calibration executions are not included.
- Ordered run-control evidence covers all 27 positional triples in each absent/cancel/refresh branch, and all 9 contiguous pairs and 27 contiguous triples without interruption. Real FW_Optional dispatches are verified separately.
- Run-tab metrics exports round-trip through Results analysis in all 144 run/control-sequence cases.
- Both required JUnit baselines passed: 132 tests, zero failures/errors, one existing socket-test skip. They had already completed before the pause; no third invocation was made. Protected hashes were rechecked after resumption: all 1,086 source/test/trail files match.

Read [README.md](README.md), [results_summary.md](post-results/results_summary.md), [sequence_coverage.md](post-results/sequence_coverage.md), and the [experience report](../testXP_guif_byCodex.md).

A fresh complete rerun is `python3 guif_dogfood/run_suite.py all`. It rebuilds, brackets all test execution with two baselines, runs every scenario and the campaign, collects covering reports, indexes checkpoint-selected evidence and rewrites the reports. Known product defects remain intentionally unfixed. Focused reruns and report regeneration are documented in README.

Keep product source and the live desktop untouched. All heavy artifacts remain under ignored target/guif-dogfood. Existing user modifications are preserved; no commit or push was made. Native dialogs, clipboard, browser launch and exhaustive virtual-thread leak detection remain the explicitly documented headless limits.
