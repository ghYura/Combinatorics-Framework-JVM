# Findings with evidence

Hard (ready but not pressable, a disabled button's action, a failing press): 0 buttons. Soft (pressable although what it needs is missing): 78 buttons. Each line links one execution that shows it; [button_states.md](button_states.md) has every button.

## Hard

None.

## Soft, by tab

### t01 — 2 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Check the candidate count | a selected example of an open library, and no count running | initial | 2 explains — “About the selected example: Open a library first, then select an example in the table.  How to start 1. Press "Open the example library": it opens /home/yb/16102026/Combinatorics” | [evidence](../target/guif-dogfood-v2/fixtures/library-1181973267151913009/evidence.json) |
| Open in the Scenario tab | a selected example of an open library | initial | 2 explains — “About the selected example: Open a library first, then select an example in the table.  How to start 1. Press "Open the example library": it opens /home/yb/16102026/Combinatorics” | [evidence](../target/guif-dogfood-v2/fixtures/library-1181973267151913009/evidence.json) |

### t02 — 8 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Remove verdict | a selected verdict row | new document | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/authoring-6565183086860775869/evidence.json) |
| Copy DOT | a dependency graph, drawn by a successful validation | new document | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/authoring-6565183086860775869/evidence.json) |
| Save DOT or JSON... | a dependency graph, drawn by a successful validation | new document | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/authoring-6565183086860775869/evidence.json) |
| Insert into the selected cell | a selected cell and a selected template | cell selection | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/authoring-3995830737712100646/evidence.json) |
| Insert row | a selected sheet whose rows are shown | needs missing: authoring_open no_sheet | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_01-1692763648661378035/evidence.json) |
| Remove | a selected sheet | needs missing: authoring_open no_sheet | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_01-9530368220656301536/evidence.json) |
| Remove row | a selected row | cell selection | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/authoring-3995830737712100646/evidence.json) |
| Rename | a selected sheet | needs missing: authoring_open no_sheet | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_01-11584669684317318904/evidence.json) |

### t03 — 1 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Validate and plan | a scenario file in the box, and no validation running | initial | 2 explains — “Check results: Choose a scenario file first: press Browse..., or pick an example in step 1 · Library.” | [evidence](../target/guif-dogfood-v2/fixtures/scenario_runner-11788384517903812966/evidence.json) |

### t04 — 47 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| ↑ | a selected rule with one above it | one rule selected | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| ↓ | a selected rule with one below it | one rule selected | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Adjacent | a selected pairs, sets or formula rule | needs missing: sieve_open rules | 2 leaves a pending draft — “0 rules · : Unsaved edits · 1 rules · pending draft: apply or discard · Save for scenario makes these rules available to every Framework entry point.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-7957961243540005889/evidence.json) |
| Apply JSON | a scenario opened in the Sieve: applying unchanged JSON only checks it | needs missing: scenario json | 2 explains — “Messages: Apply or discard the rule/data draft first.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-3271960629219818022/evidence.json) |
| Discard JSON draft | an edited JSON text | JSON owns editor | 2 reloads the shown text — “Constraints as JSON: {   "version": 1,   "constraints": [] }” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_rules-15661931691578469671/evidence.json) |
| Apply rule | a selected rule or a new rule draft: applying it unchanged only checks it | needs missing: sieve_open rules | 2 explains — “Messages: The rule was not applied: Select or create a rule first.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-4635240422105069885/evidence.json) |
| Auto-search… | a scenario opened in the Sieve | initial | 2 explains — “Messages: Press Open step 3 scenario first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Add pair at cell | a selected value cell of the matrix | initial | 2 explains — “Messages: Select a value cell first (not the row label).” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Edit cell rule | a selected matrix cell that a rule counts | initial | 2 explains — “Messages: No tuple/set rule in this cell.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Check impact | a scenario opened in the Sieve | initial | 2 explains — “Messages: Press Open step 3 scenario first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Delete | a selected rule | initial | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Discard draft | a changed rule draft | rule selected | 2 reloads the shown text — “Rule ID: pair1” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_rules-15661931691578469671/evidence.json) |
| Duplicate | a selected rule | initial | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Export JSON… | a scenario opened in the Sieve | initial | 2 explains — “Messages: Press Open step 3 scenario first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Face 2 (Sieve) ↗ | a scenario chosen in step 3 | initial | 2 explains — “Messages: Select a scenario in step 3 first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Find on graph | a selected rule | initial | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Import constraints… | a scenario opened in the Sieve | initial | 2 explains — “Messages: Press Open step 3 scenario first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| New rule | a scenario opened in the Sieve | initial | 2 explains — “Messages: Press Open step 3 scenario first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Apply JSON | a selected rule | needs missing: sieve_open rules | 2 leaves a pending draft — “0 rules · : Unsaved edits · 1 rules · pending draft: apply or discard · Save for scenario makes these rules available to every Framework entry point.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-16970675694101319465/evidence.json) |
| + condition | a selected rule whose condition can grow | needs missing: sieve_open rules | 2 leaves a pending draft — “0 rules · : Unsaved edits · 1 rules · pending draft: apply or discard · Save for scenario makes these rules available to every Framework entry point.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-16864759395457991074/evidence.json) |
| Open step 3 scenario | a scenario chosen in step 3 | initial | 2 explains — “Messages: Choose a scenario in step 3 first, then press Open step 3 scenario.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Apply data | a scenario opened in the Sieve: applying unchanged data only checks it | needs missing: scenario data | 2 explains — “Messages: Apply or discard rule/JSON drafts first.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-4977102430018910288/evidence.json) |
| Discard data draft | a changed attribute or order | data editor active | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| Add attribute | a scenario opened in the Sieve | needs missing: scenario data | 2 leaves a pending draft — “0 rules · : 0 rules · pending draft: apply or discard ·” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-15684163569791147612/evidence.json) |
| Remove attribute | a selected attribute row | data editor active | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| ↑ | a selected ranked value with one above it | data editor active | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| ↓ | a selected ranked value with one below it | data editor active | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| Add value | a scenario opened in the Sieve | needs missing: scenario data orders | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-2365245241874720861/evidence.json) |
| Remove | a selected ranked value | data editor active | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| Redo | an edit that Undo took back | initial | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| + − × | an empty number slot selected in the formula | needs missing: sieve_open mixed rule0 formula formula_operand | 2 replaces the formula — “makes: …” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-9921665219437941589/evidence.json) |
| and / or | an empty true-or-false slot selected in the formula | needs missing: sieve_open mixed rule0 formula formula_root | 2 replaces the formula — “makes: …” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-11411781650776130314/evidence.json) |
| compare | an empty true-or-false slot selected in the formula | needs missing: sieve_open mixed rule0 formula formula_root | 2 replaces the formula — “makes: …” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-17269008651196230720/evidence.json) |
| not | an empty true-or-false slot selected in the formula | needs missing: sieve_open mixed rule0 formula formula_root | 2 replaces the formula — “makes: …” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-18287713602088277029/evidence.json) |
| number | an empty number slot selected in the formula | needs missing: sieve_open mixed rule0 formula formula_operand | 2 replaces the formula — “makes: (0 > 2)” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-12319738947854652404/evidence.json) |
| Remove row | a selected row of the rule | rule selected | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_rules-15661931691578469671/evidence.json) |
| sheet.param | an empty number slot selected in the formula | needs missing: sieve_open mixed rule0 formula formula_operand | 2 replaces the formula — “makes: (A.cost > 2)” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-16931750423042350066/evidence.json) |
| Use chosen sheets | a selected pairs or sets rule with two sheets chosen in its list | needs missing: sieve_open rules rule0 one_sheet | 2 explains — “Messages: The rule was not applied: Choose at least two sheets.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-16737062916881173738/evidence.json) |
| Save for scenario | a scenario opened in the Sieve | initial | 2 explains — “Messages: Press Open step 3 scenario first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| + | a scenario opened in the Sieve: cards to zoom | needs missing: scenario | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-3603755479788586407/evidence.json) |
| − | a scenario opened in the Sieve: cards to zoom | needs missing: scenario | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-12548471947763173218/evidence.json) |
| Arrange | a scenario opened in the Sieve: cards to arrange | needs missing: scenario | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-17610637255299192282/evidence.json) |
| Fit | a scenario opened in the Sieve: cards to fit | needs missing: scenario | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-1442323133812865688/evidence.json) |
| Group selected values | values of two sheets chosen on the graph | initial | 2 explains — “Messages: Shift-click values from at least two sheets, then create the group.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Undo | an edit to take back | initial | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Validate | a scenario opened in the Sieve | initial | 2 explains — “Messages: Press Open step 3 scenario first.” | [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| Within | a selected pairs, sets or formula rule | needs missing: sieve_open rules | 2 leaves a pending draft — “0 rules · : Unsaved edits · 1 rules · pending draft: apply or discard · Save for scenario makes these rules available to every Framework entry point.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-6751028112573173901/evidence.json) |

### t05 — 2 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Next page | a shown preview of the step 3 scenario with another page, and no preview running | scenario changed after preview | family check | [evidence](../target/guif-dogfood-v2/fixtures/candidates-1112512003906392091/evidence.json) |
| Preview | a scenario chosen in step 3, and no preview running | initial | 2 explains — “No preview yet.: Choose a scenario in step 3 first, or send an example there from step 1 · Library.” | [evidence](../target/guif-dogfood-v2/fixtures/candidates-9215170344141201448/evidence.json) |

### t06 — 1 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Run | a scenario chosen in step 3, and no run going | initial | 2 explains — “Run report: Choose a scenario in step 3 first, or send an example there from step 1 · Library.” | [evidence](../target/guif-dogfood-v2/fixtures/run-7154035314176791557/evidence.json) |

### t07 — 1 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Run campaign | a scenario chosen in step 3, and no campaign going | initial | 2 explains — “Campaign report: Choose a scenario in step 3 first, or send an example there from step 1 · Library.” | [evidence](../target/guif-dogfood-v2/fixtures/campaign-8376676202348254976/evidence.json) |

### t08 — 1 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Analyze | a metrics file in the box, and no analysis running | initial | 2 explains — “Analysis report: Choose a metrics file first: in step 6 save the run as "metrics for step 8 (.kv)", or press Browse...” | [evidence](../target/guif-dogfood-v2/fixtures/analysis-14060807089935582095/evidence.json) |

### t09 — 4 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Auto-plan | a text file in the box | initial | 2 explains — “Idle: Choose a text file first.” | [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |
| Export CSV... | a successful scoring of the text and rules in the boxes | a later scoring failed | family check | [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |
| Optimization charts | a successful scoring of the text and rules in the boxes | a later scoring failed | family check | [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |
| Score | a text file in the box, and no scoring running | initial | 2 explains — “Idle: Choose a text file first.” | [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |

### t10 — 11 buttons

| Button | Needs | While missing it was | Press outcome | Example |
|---|---|---|---|---|
| Move down | a selected factor with one below it | example filled | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/wizard-17633075829542969749/evidence.json) |
| Move up | a selected factor with one above it | example filled | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/wizard-7887376197745120122/evidence.json) |
| Remove the factor | a selected factor | needs missing: wizard_own | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-10849686477154058794/evidence.json) |
| A fixed number of them | a selected factor | needs missing: wizard_own | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-8009726333459885335/evidence.json) |
| Any number of them, within bounds | a selected factor | needs missing: wizard_own | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-13286708546930391231/evidence.json) |
| Exactly one of them | a selected factor | needs missing: wizard_own | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-14574474471832466410/evidence.json) |
| It may or may not happen: a sudden action | a selected factor | needs missing: wizard_own | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-3018268090685572901/evidence.json) |
| Add the rule | two factors whose values a rule can join | needs missing: wizard_own | 2 explains — “Add factors first (page 0.2): a rule relates two of their values.: Choose two values first: a factor and its value, and the other factor and its value.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-16768808700079251764/evidence.json) |
| Remove the rule | a selected rule | example filled | 2 silent | [evidence](../target/guif-dogfood-v2/fixtures/wizard-7887376197745120122/evidence.json) |
| Check with the Framework | a design without problems, and no check running | needs missing: wizard_own | 2 explains — “Framework check and saved files: Fix these first: - Add at least one factor: a thing that varies between test cases.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_05-683761631677250968/evidence.json) |
| Save the scenario... | a design without problems | needs missing: wizard_own | 2 explains — “Framework check and saved files: Fix these first: - Add at least one factor: a thing that varies between test cases.” | [evidence](../target/guif-dogfood-v2/fixtures/sweep_05-173054762155143741/evidence.json) |

## Reproducers

One candidate each, in [repro/](repro/): the Framework verdict names the button, the evidence shows what the press did. [RESUME.md](RESUME.md) has the command.

| Scenario | Verdict | What the window said or did | Evidence |
|---|---|---|---|
| repro_analysis_without_metrics | 493 t08_analyze_1 | Analysis report: Choose a metrics file first: in step 6 save the run as "metrics for step 8 (.kv)", or press Browse... | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-16169151263166168868/evidence.json) |
| repro_authoring_copy_dot_without_graph | 388 t02_2_4_dependency_graph_copy_dot_1 | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-3220546097301639855/evidence.json) |
| repro_authoring_remove_row_without_row | 397 t02_remove_row_1 | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-17636782626513527122/evidence.json) |
| repro_campaign_without_scenario | 492 t07_run_campaign_1 | Campaign report: Choose a scenario in step 3 first, or send an example there from step 1 · Library. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-18268642506587942385/evidence.json) |
| repro_candidates_next_page_after_step3_change | 479 t05_next_page_1 | No preview yet.: 4 generated; this page holds 1 rows from ordinal 1, read 1; the next page starts at ordinal 2 | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-15441460504298569516/evidence.json) |
| repro_candidates_preview_without_scenario | 481 t05_preview_1 | No preview yet.: Choose a scenario in step 3 first, or send an example there from step 1 · Library. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-1604978698176738442/evidence.json) |
| repro_library_open_without_example | 380 t01_open_in_the_scenario_tab_1 | About the selected example: Open a library first, then select an example in the table.  How to start 1. Press "Open the example library": it opens /home/yb/16102026/Combinatorics | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-2414782739382419569/evidence.json) |
| repro_run_without_scenario | 486 t06_run_1 | Run report: Choose a scenario in step 3 first, or send an example there from step 1 · Library. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-17138978641710056236/evidence.json) |
| repro_scenario_validate_empty_box | 415 t03_validate_and_plan_1 | Check results: Choose a scenario file first: press Browse..., or pick an example in step 1 · Library. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-10408617231234337842/evidence.json) |
| repro_scorer_export_after_failed_scoring | 504 t09_export_csv_1 | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-17299960759249313464/evidence.json) |
| repro_scorer_without_text | 507 t09_score_1 | Idle: Choose a text file first. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-18391499646040588691/evidence.json) |
| repro_sieve_data_draft_before_open_locks_views | 446 t04_parameters_orders_parameters_add_attribute_1 | Sieve view Parameters / orders says: Apply or discard the current draft before switching editors. Your edits are still present. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-14380701189326589211/evidence.json) |
| repro_sieve_discard_without_draft | 428 t04_discard_draft_1 | Rule ID: pair1 | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-15782771610070378466/evidence.json) |
| repro_sieve_gate_without_rule_blocks_validate | 418 t04_adjacent_1 | Sieve view Sieve graph says: Apply or discard the pending rule, data or JSON draft before this action. Your edits are still in their editor. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-1195327644531243161/evidence.json) |
| repro_sieve_number_replaces_filled_operand | 462 t04_rule_body_number_1 | makes: (0 > 2) | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-696625200232928902/evidence.json) |
| repro_sieve_palette_replaces_filled_slot | 457 t04_rule_body_compare_1 | makes: … | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-626335924991412161/evidence.json) |
| repro_sieve_undo_without_history | 473 t04_undo_1 | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-8925241475390816386/evidence.json) |
| repro_sieve_validate_before_open | 474 t04_validate_1 | Messages: Press Open step 3 scenario first. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-11460053567041981805/evidence.json) |
| repro_wizard_check_empty_design | 537 t10_0_7_finish_check_with_the_framework_1 | Framework check and saved files: Fix these first: - Add at least one factor: a thing that varies between test cases. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-17012412024298536153/evidence.json) |
| repro_wizard_move_up_at_first_factor | 518 t10_0_2_factors_move_up_1 | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-16444278912591586907/evidence.json) |
