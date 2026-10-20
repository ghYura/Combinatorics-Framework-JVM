# Framework execution and coverage

Current checkpoints only; calibration and superseded executions are excluded. Each family is one Framework run: generated-java, one worker, SIGNED_BYTE labels, set-cover at the stated strength.

| Family | Selected cases | Outcomes | Verdicts (code: cases) | Framework coverage |
|---|---:|---|---|---|
| library | 7 |     PASS 0, DOMAIN_FAIL 7, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 380: 7 | t=2 set-cover (greedy, not a proven minimum): kept 5 mandatory rows; 42 tuples, 41 feasible and covered, 1 infeasible |
| authoring | 16 |     PASS 0, DOMAIN_FAIL 16, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 387: 8, 397: 8 | t=2 set-cover (greedy, not a proven minimum): kept 8 mandatory rows; 38 tuples, 38 feasible and covered, 0 infeasible |
| scenario_runner | 6 |     PASS 0, DOMAIN_FAIL 6, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 415: 6 | t=2 set-cover (greedy, not a proven minimum): kept 6 mandatory rows; 32 tuples, 32 feasible and covered, 0 infeasible |
| candidates | 12 |     PASS 0, DOMAIN_FAIL 12, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 481: 12 | t=3 set-cover (greedy, not a proven minimum): kept 4 mandatory rows; 44 tuples, 44 feasible and covered, 0 infeasible |
| formatting | 25 |     PASS 25, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 25 | t=3 set-cover (greedy, not a proven minimum): kept 15 mandatory rows; 112 tuples, 112 feasible and covered, 0 infeasible |
| run | 162 |     PASS 0, DOMAIN_FAIL 162, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 486: 162 | t=3 set-cover (greedy, not a proven minimum): kept 54 mandatory rows; 394 tuples, 394 feasible and covered, 0 infeasible |
| campaign | 81 |     PASS 0, DOMAIN_FAIL 81, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 492: 81 | t=3 set-cover (greedy, not a proven minimum): kept 27 mandatory rows; 193 tuples, 193 feasible and covered, 0 infeasible |
| analysis | 6 |     PASS 0, DOMAIN_FAIL 6, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 493: 6 | t=2 set-cover (greedy, not a proven minimum): kept 6 mandatory rows; 32 tuples, 32 feasible and covered, 0 infeasible |
| scorer | 4 |     PASS 0, DOMAIN_FAIL 4, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 500: 4 | t=2 set-cover (greedy, not a proven minimum): kept 2 mandatory rows; 14 tuples, 14 feasible and covered, 0 infeasible |
| wizard | 24 |     PASS 0, DOMAIN_FAIL 24, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 517: 5, 518: 19 | t=2 set-cover (greedy, not a proven minimum): kept 24 mandatory rows; 98 tuples, 98 feasible and covered, 0 infeasible |
| sieve_graph | 12 |     PASS 0, DOMAIN_FAIL 12, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 443: 12 | t=3 set-cover (greedy, not a proven minimum): kept 4 mandatory rows; 44 tuples, 44 feasible and covered, 0 infeasible |
| sieve_data | 8 |     PASS 0, DOMAIN_FAIL 8, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 447: 8 | t=3 set-cover (greedy, not a proven minimum): kept 4 mandatory rows; 44 tuples, 44 feasible and covered, 0 infeasible |
| sieve_rules | 4 |     PASS 0, DOMAIN_FAIL 4, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 420: 4 | t=2 set-cover (greedy, not a proven minimum): kept 2 mandatory rows; 14 tuples, 14 feasible and covered, 0 infeasible |
| sieve_formula | 4 |     PASS 4, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 4 | t=2 set-cover (greedy, not a proven minimum): kept 4 mandatory rows; 26 tuples, 26 feasible and covered, 0 infeasible |
| search | 4 |     PASS 4, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 4 | t=2 set-cover (greedy, not a proven minimum): kept 2 mandatory rows; 14 tuples, 14 feasible and covered, 0 infeasible |
| dialog_buttons | 1 |     PASS 1, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 1 | t=2 set-cover (greedy, not a proven minimum): kept 1 mandatory rows; 10 tuples, 10 feasible and covered, 0 infeasible |
| sweep_01 | 116 |     PASS 94, DOMAIN_FAIL 22, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 94, 378: 2, 380: 2, 387: 2, 388: 2, 389: 2, 392: 2, 393: 2, 396: 2, 397: 2, 398: 2, 415: 2 | t=2 set-cover (greedy, not a proven minimum): kept 58 mandatory rows; 254 tuples, 232 feasible and covered, 22 infeasible |
| sweep_02 | 160 |     PASS 88, DOMAIN_FAIL 72, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 88, 416: 2, 417: 2, 418: 2, 419: 2, 420: 2, 422: 2, 423: 2, 424: 2, 426: 2, 427: 2, 429: 2, 430: 2, 431: 2, 432: 2, 433: 2, 434: 2, 438: 2, 443: 2, 444: 2, 445: 2, 446: 2, 447: 2, 448: 2, 449: 2, 450: 2, 451: 2, 452: 2, 467: 2, 468: 2, 469: 2, 470: 2, 471: 2, 472: 2, 473: 2, 474: 2, 475: 2 | t=2 set-cover (greedy, not a proven minimum): kept 80 mandatory rows; 254 tuples, 254 feasible and covered, 0 infeasible |
| sweep_03 | 148 |     PASS 120, DOMAIN_FAIL 28, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 120, 421: 2, 428: 2, 437: 2, 453: 2, 455: 2, 457: 2, 461: 2, 462: 2, 463: 2, 464: 2, 466: 2, 481: 2, 486: 2, 492: 2 | t=2 set-cover (greedy, not a proven minimum): kept 74 mandatory rows; 254 tuples, 248 feasible and covered, 6 infeasible |
| sweep_04 | 126 |     PASS 102, DOMAIN_FAIL 24, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 102, 493: 2, 500: 2, 507: 2, 517: 2, 518: 2, 519: 2, 520: 2, 522: 2, 523: 2, 524: 2, 527: 2, 528: 2 | t=2 set-cover (greedy, not a proven minimum): kept 63 mandatory rows; 254 tuples, 237 feasible and covered, 17 infeasible |
| sweep_05 | 30 |     PASS 26, DOMAIN_FAIL 4, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 0: 26, 537: 2, 539: 2 | t=2 set-cover (greedy, not a proven minimum): kept 15 mandatory rows; 62 tuples, 61 feasible and covered, 1 infeasible |
| repro_library_open_without_example | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 380: 1 |  |
| repro_authoring_remove_row_without_row | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 397: 1 |  |
| repro_authoring_copy_dot_without_graph | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 388: 1 |  |
| repro_scenario_validate_empty_box | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 415: 1 |  |
| repro_sieve_validate_before_open | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 474: 1 |  |
| repro_sieve_undo_without_history | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 473: 1 |  |
| repro_sieve_discard_without_draft | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 428: 1 |  |
| repro_sieve_palette_replaces_filled_slot | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 457: 1 |  |
| repro_sieve_number_replaces_filled_operand | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 462: 1 |  |
| repro_sieve_gate_without_rule_blocks_validate | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 418: 1 |  |
| repro_sieve_data_draft_before_open_locks_views | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 446: 1 |  |
| repro_candidates_preview_without_scenario | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 481: 1 |  |
| repro_candidates_next_page_after_step3_change | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 479: 1 |  |
| repro_run_without_scenario | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 486: 1 |  |
| repro_campaign_without_scenario | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 492: 1 |  |
| repro_analysis_without_metrics | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 493: 1 |  |
| repro_scorer_without_text | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 507: 1 |  |
| repro_scorer_export_after_failed_scoring | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 504: 1 |  |
| repro_wizard_move_up_at_first_factor | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 518: 1 |  |
| repro_wizard_check_empty_design | 1 |     PASS 0, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0 | 537: 1 |  |

## Ordered actions and sudden events

Run uses FW_PermutR(3) with FW_Combi(3); campaign uses three positional factors. Disabled attempts are kept on purpose: these are attempted triples, not a claim that every action was enabled and delivered.

- campaign: 27 distinct attempted pause/resume/cancel triples.
- run: 27 distinct attempted pause/resume/cancel triples.

- authoring: FW_Optional New, 8 dispatches.
- campaign: FW_Optional Cancel, 27 dispatches.
- campaign: FW_Optional refresh, 27 dispatches.
- candidates: FW_Optional Cancel, 4 dispatches.
- candidates: FW_Optional clear selection, 4 dispatches.
- formatting: FW_Optional Original, 5 dispatches.
- formatting: FW_Optional source selection, 5 dispatches.
- library: FW_Optional clear selection, 2 dispatches.
- run: FW_Optional Cancel, 54 dispatches.
- run: FW_Optional refresh, 54 dispatches.
- scorer: FW_Optional clear text path, 2 dispatches.
- search: FW_Optional Stop, 2 dispatches.
- sieve_data: FW_Optional Undo during data draft, 4 dispatches.
- sieve_graph: FW_Optional undo, 4 dispatches.
- sieve_graph: FW_Optional validate, 4 dispatches.
- sieve_rules: FW_Optional editor switch, 2 dispatches.
- sweep_01: FW_Optional visit another tab, 58 dispatches.
- sweep_02: FW_Optional visit another tab, 80 dispatches.
- sweep_03: FW_Optional visit another tab, 74 dispatches.
- sweep_04: FW_Optional visit another tab, 63 dispatches.
- sweep_05: FW_Optional visit another tab, 15 dispatches.
