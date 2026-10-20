# After the fix: every remaining button

What changed in the product (2026-10-20): every remaining button gets its enabled state from what it needs, re-evaluated when that changes.

- **Step 1 · Library**: Open in the Scenario tab and Check the candidate count need a selected example read from a file (not made by its application); a count also needs none running.
- **Step 2 · Authoring**: Rename, Remove and Insert row need a selected sheet; Remove row a selected row; Insert into the selected cell a cell and a template; Remove verdict a selected verdict; Copy DOT and Save DOT or JSON... a drawn graph.
- **Steps 3 to 7**: Validate and plan, Preview, Run, Run campaign, Open step 3 scenario and Face 2 need a scenario in step 3, and follow it the moment step 3 changes.
- **Step 4 · Sieve**: every action needs an opened scenario, and then what it acts on: edit history, a selected rule and its neighbour, a matrix cell (with a rule, to edit it), values of two sheets chosen on the graph, a JSON, data or rule draft to discard, two chosen sheets, a selected row or value.
- **Steps 8 and 9**: Analyze needs a metrics file; Score and Auto-plan a text file; Export CSV... and Optimization charts a successful scoring of the text and rules now in the boxes.
- **Step 5 · Next page** needs another page of the scenario step 3 names now, not the one previewed before it changed.
- **The probe follows the same needs**: it waits on each view's own work flag, not on a button that is rightly off, and its Authoring family selects a template before it expects Insert.

The verify phase `fix3` reran 21 scenarios (library, authoring, scenario_runner, candidates, formatting, run, campaign, analysis, scorer, wizard, sieve_graph, sieve_data, sieve_rules, sieve_formula, search, dialog_buttons, sweep_01, sweep_02, sweep_03, sweep_04, sweep_05) and 20 reproducers on the fixed product, with the same probe, oracle and selection as the investigation; the investigation's own evidence is unchanged. Counts are wrong / checks.

## The buttons the fix touches

| Button | Needs (as checked after the fix) | Before | After | Before: a press without the need |
|---|---|---|---|---|
| Check the candidate count | a selected example of an open library, and no count running | SOFT · missing 22/22 · ready 0/4 | PASS · missing 0/22 · ready 0/4 | 2 explains |
| Open in the Scenario tab | a selected example of an open library | SOFT · missing 21/21 · ready 0/4 | PASS · missing 0/21 · ready 0/4 | 2 explains |
| Remove verdict | a selected verdict row | SOFT · missing 34/34 · ready 0/18 | PASS · missing 0/34 · ready 0/18 | 2 silent |
| Copy DOT | a dependency graph, drawn by a successful validation | SOFT · missing 26/26 · ready 0/10 | PASS · missing 0/26 · ready 0/10 | 2 silent |
| Save DOT or JSON... | a dependency graph, drawn by a successful validation | SOFT · missing 26/26 · ready 0/10 | PASS · missing 0/26 · ready 0/10 | 2 silent |
| Insert into the selected cell | a selected cell and a selected template | SOFT · missing 10/10 · ready 0/10 | PASS · missing 0/10 · ready 0/10 | 2 silent |
| Insert row | a selected sheet whose rows are shown | SOFT · missing 2/2 · ready 0/18 | PASS · missing 0/2 · ready 0/18 | 2 silent |
| Remove | a selected sheet | SOFT · missing 2/2 · ready 0/18 | PASS · missing 0/2 · ready 0/18 | 2 silent |
| Remove row | a selected row | SOFT · missing 10/10 · ready 0/10 | PASS · missing 0/10 · ready 0/10 | 2 silent |
| Rename | a selected sheet | SOFT · missing 2/2 · ready 0/18 | PASS · missing 0/2 · ready 0/18 | 2 silent |
| Validate and plan | a scenario file in the box, and no validation running | SOFT · missing 8/14 · ready 0/8 | PASS · missing 0/14 · ready 0/8 | 2 explains |
| ↑ | a selected rule with one above it | SOFT · missing 14/42 · ready 0/2 | PASS · missing 0/42 · ready 0/2 | 2 silent |
| ↓ | a selected rule with one below it | SOFT · missing 14/42 · ready 0/2 | PASS · missing 0/42 · ready 0/2 | 2 silent |
| Adjacent | a selected pairs, sets or formula rule | SOFT · missing 2/34 · ready 0/6 | PASS · missing 0/34 · ready 0/6 | 2 leaves a pending draft |
| Apply JSON | a scenario opened in the Sieve: applying unchanged JSON only checks it | SOFT · missing 2/30 · ready 0/6 | PASS · missing 0/30 · ready 0/6 | 2 explains |
| Discard JSON draft | an edited JSON text | SOFT · missing 6/34 · ready 0/6 | PASS · missing 0/34 · ready 0/6 | 2 reloads the shown text |
| Apply rule | a selected rule or a new rule draft: applying it unchanged only checks it | SOFT · missing 2/42 · ready 0/6 | PASS · missing 0/42 · ready 0/6 | 2 explains |
| Auto-search… | a scenario opened in the Sieve | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 explains |
| Add pair at cell | a selected value cell of the matrix | SOFT · missing 26/54 · ready 0/14 | PASS · missing 0/54 · ready 0/14 | 2 explains |
| Edit cell rule | a selected matrix cell that a rule counts | SOFT · missing 26/54 · ready 0/2 | PASS · missing 0/54 · ready 0/2 | 2 explains |
| Check impact | a scenario opened in the Sieve | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 explains |
| Delete | a selected rule | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 silent |
| Discard draft | a changed rule draft | SOFT · missing 6/34 · ready 0/2 | PASS · missing 0/34 · ready 0/2 | 2 reloads the shown text |
| Duplicate | a selected rule | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 silent |
| Export JSON… | a scenario opened in the Sieve | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 explains |
| Face 2 (Sieve) ↗ | a scenario chosen in step 3 | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 explains |
| Find on graph | a selected rule | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 silent |
| Import constraints… | a scenario opened in the Sieve | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 explains |
| New rule | a scenario opened in the Sieve | SOFT · missing 14/42 · ready 0/22 | PASS · missing 0/42 · ready 0/22 | 2 explains |
| Apply JSON | a selected rule | SOFT · missing 2/30 · ready 0/6 | PASS · missing 0/30 · ready 0/6 | 2 leaves a pending draft |
| + condition | a selected rule whose condition can grow | SOFT · missing 2/30 · ready 0/6 | PASS · missing 0/30 · ready 0/6 | 2 leaves a pending draft |
| Open step 3 scenario | a scenario chosen in step 3 | SOFT · missing 14/42 · ready 0/42 | PASS · missing 0/42 · ready 0/42 | 2 explains |
| Apply data | a scenario opened in the Sieve: applying unchanged data only checks it | SOFT · missing 2/30 · ready 0/10 | PASS · missing 0/30 · ready 0/10 | 2 explains |
| Discard data draft | a changed attribute or order | SOFT · missing 10/38 · ready 0/10 | PASS · missing 0/38 · ready 0/10 | 2 silent |
| Add attribute | a scenario opened in the Sieve | SOFT · missing 2/30 · ready 0/10 | PASS · missing 0/30 · ready 0/10 | 2 leaves a pending draft |
| Remove attribute | a selected attribute row | SOFT · missing 10/38 · ready 0/10 | PASS · missing 0/38 · ready 0/10 | 2 silent |
| ↑ | a selected ranked value with one above it | SOFT · missing 14/42 · ready 0/6 | PASS · missing 0/42 · ready 0/6 | 2 silent |
| ↓ | a selected ranked value with one below it | SOFT · missing 14/42 · ready 0/6 | PASS · missing 0/42 · ready 0/6 | 2 silent |
| Add value | a scenario opened in the Sieve | SOFT · missing 2/30 · ready 0/10 | PASS · missing 0/30 · ready 0/10 | 2 silent |
| Remove | a selected ranked value | SOFT · missing 10/38 · ready 0/10 | PASS · missing 0/38 · ready 0/10 | 2 silent |
| Redo | an edit that Undo took back | SOFT · missing 50/78 · ready 0/14 | PASS · missing 0/78 · ready 0/14 | 2 silent |
| + − × | a selected place that takes a number (a block already there is replaced) | SOFT · missing 2/6 · ready 0/4 | PASS · missing 0/6 · ready 0/4 | 2 replaces the formula |
| and / or | a selected place that takes a truth value (a block already there is replaced) | SOFT · missing 2/2 · ready 0/6 | PASS · missing 0/2 · ready 0/6 | 2 replaces the formula |
| compare | a selected place that takes a truth value (a block already there is replaced) | SOFT · missing 2/2 · ready 0/6 | PASS · missing 0/2 · ready 0/6 | 2 replaces the formula |
| not | a selected place that takes a truth value (a block already there is replaced) | SOFT · missing 2/2 · ready 0/6 | PASS · missing 0/2 · ready 0/6 | 2 replaces the formula |
| number | a selected place that takes a number (a block already there is replaced) | SOFT · missing 2/6 · ready 0/4 | PASS · missing 0/6 · ready 0/4 | 2 replaces the formula |
| Remove row | a selected row of the rule | SOFT · missing 6/6 · ready 0/6 | PASS · missing 0/6 · ready 0/6 | 2 silent |
| sheet.param | a selected place that takes a number (a block already there is replaced) | SOFT · missing 2/6 · ready 0/4 | PASS · missing 0/6 · ready 0/4 | 2 replaces the formula |
| Use chosen sheets | a selected pairs or sets rule with two sheets chosen in its list | SOFT · missing 2/2 · ready 0/6 | PASS · missing 0/2 · ready 0/6 | 2 explains |
| Save for scenario | a scenario opened in the Sieve | SOFT · missing 14/42 · ready 0/26 | PASS · missing 0/42 · ready 0/26 | 2 explains |
| + | a scenario opened in the Sieve: cards to zoom | SOFT · missing 2/30 · ready 0/14 | PASS · missing 0/30 · ready 0/14 | 2 silent |
| − | a scenario opened in the Sieve: cards to zoom | SOFT · missing 2/30 · ready 0/14 | PASS · missing 0/30 · ready 0/14 | 2 silent |
| Arrange | a scenario opened in the Sieve: cards to arrange | SOFT · missing 2/30 · ready 0/14 | PASS · missing 0/30 · ready 0/14 | 2 silent |
| Fit | a scenario opened in the Sieve: cards to fit | SOFT · missing 2/30 · ready 0/14 | PASS · missing 0/30 · ready 0/14 | 2 silent |
| Group selected values | values of two sheets chosen on the graph | SOFT · missing 26/54 · ready 0/2 | PASS · missing 0/54 · ready 0/2 | 2 explains |
| Undo | an edit to take back | SOFT · missing 29/57 · ready 0/35 | PASS · missing 0/57 · ready 0/35 | 2 silent |
| Validate | a scenario opened in the Sieve | SOFT · missing 14/42 · ready 0/14 | PASS · missing 0/42 · ready 0/14 | 2 explains |
| Within | a selected pairs, sets or formula rule | SOFT · missing 2/30 · ready 0/6 | PASS · missing 0/30 · ready 0/6 | 2 leaves a pending draft |
| Next page | a shown preview of the step 3 scenario with another page, and no preview running | SOFT · missing 3/50 · ready 0/8 | PASS · missing 0/50 · ready 0/8 |  |
| Preview | a scenario chosen in step 3, and no preview running | SOFT · missing 14/38 · ready 0/14 | PASS · missing 0/38 · ready 0/14 | 2 explains |
| Run | a scenario chosen in step 3, and no run going | SOFT · missing 164/608 · ready 0/530 | PASS · missing 0/608 · ready 0/530 | 2 explains |
| Run campaign | a scenario chosen in step 3, and no campaign going | SOFT · missing 83/305 · ready 0/266 | PASS · missing 0/305 · ready 0/266 | 2 explains |
| Analyze | a metrics file in the box, and no analysis running | SOFT · missing 8/14 · ready 0/14 | PASS · missing 0/14 · ready 0/14 | 2 explains |
| Auto-plan | a text file in the box | SOFT · missing 8/8 · ready 0/6 | PASS · missing 0/8 · ready 0/6 | 2 explains |
| Export CSV... | a successful scoring of the text and rules in the boxes | SOFT · missing 4/12 · ready 0/4 | PASS · missing 0/12 · ready 0/4 |  |
| Optimization charts | a successful scoring of the text and rules in the boxes | SOFT · missing 4/12 · ready 0/4 | PASS · missing 0/12 · ready 0/4 |  |
| Score | a text file in the box, and no scoring running | SOFT · missing 8/12 · ready 0/10 | PASS · missing 0/12 · ready 0/10 | 2 explains |
| Move down | a selected factor with one below it | SOFT · missing 11/11 · ready 0/17 | PASS · missing 0/11 · ready 0/17 | 2 silent |
| Move up | a selected factor with one above it | SOFT · missing 21/21 · ready 0/7 | PASS · missing 0/21 · ready 0/7 | 2 silent |
| Remove the factor | a selected factor | SOFT · missing 2/2 · ready 0/26 | PASS · missing 0/2 · ready 0/26 | 2 silent |
| A fixed number of them | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| Any number of them, within bounds | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| Exactly one of them | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| It may or may not happen: a sudden action | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| Add the rule | a rule the chosen values make that is new and can work | SOFT · missing 2/2 · ready 0/2 | PASS · missing 0/2 · ready 0/2 | 2 explains |
| Remove the rule | a selected rule | SOFT · missing 26/26 · ready 0/2 | PASS · missing 0/26 · ready 0/2 | 2 silent |
| Check with the Framework | a design without problems, and no check running | SOFT · missing 2/26 · ready 0/50 | PASS · missing 0/30 · ready 0/46 | 2 explains |
| Save the scenario... | a design without problems | SOFT · missing 2/26 · ready 0/50 | PASS · missing 0/30 · ready 0/46 | 2 explains |

## Every other button these scenarios checked

97 buttons; verdict changed for 0.


## Reproducers after the fix

| Scenario | Verdict | What the window said or did | Evidence |
|---|---|---|---|
| repro_analysis_without_metrics | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-14526665867861980577/evidence.json) |
| repro_authoring_copy_dot_without_graph | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-18305100627469741415/evidence.json) |
| repro_authoring_remove_row_without_row | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-9159172137428512045/evidence.json) |
| repro_campaign_without_scenario | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-2700500851558705851/evidence.json) |
| repro_candidates_next_page_after_step3_change | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-2949448786980695418/evidence.json) |
| repro_candidates_preview_without_scenario | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-12830287084550471050/evidence.json) |
| repro_library_open_without_example | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-3787746424800238520/evidence.json) |
| repro_run_without_scenario | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-13453874870874610011/evidence.json) |
| repro_scenario_validate_empty_box | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-9743139672884588897/evidence.json) |
| repro_scorer_export_after_failed_scoring | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-11679117656482697146/evidence.json) |
| repro_scorer_without_text | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-1047310674240104548/evidence.json) |
| repro_sieve_data_draft_before_open_locks_views | 0 PASS | Sieve view Bond matrix says: Press Open step 3 scenario to load the scenario chosen in step 3 with its rules (constraints). Edit them here, validate or check impact, then press Save for scenario. Graph, matrix and inspector share one document. Forbid/require,  | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-13841371711871364047/evidence.json) |
| repro_sieve_discard_without_draft | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-8976498828099808685/evidence.json) |
| repro_sieve_gate_without_rule_blocks_validate | 0 PASS | Sieve view Sieve graph says: Valid for execution; no candidate page scanned. pair1: not counted exactly within the scan limit | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-16334400899960311575/evidence.json) |
| repro_sieve_number_replaces_filled_operand | 0 PASS | : Replaced 1 with a new number block. Discard draft returns to the rule as last applied. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-1538483463161548718/evidence.json) |
| repro_sieve_palette_replaces_filled_slot | 0 PASS | : Replaced (1 > 2) with a new compare block. Discard draft returns to the rule as last applied. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-8062241286484321314/evidence.json) |
| repro_sieve_undo_without_history | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-17511673400483304802/evidence.json) |
| repro_sieve_validate_before_open | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-7690087654450366492/evidence.json) |
| repro_wizard_check_empty_design | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-4682212923455405167/evidence.json) |
| repro_wizard_move_up_at_first_factor | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-140418756945438658/evidence.json) |

Protected files during the phase: 1100 checked against the snapshot taken after the fix; changed 0, added 0.
