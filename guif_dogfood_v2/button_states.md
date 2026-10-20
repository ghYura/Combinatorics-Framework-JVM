# Per-button availability ledger

Every button of the ten tabs, the search window and the chooser accessories. **Needs** is what the button needs before pressing it means anything ([probe/preconditions.tsv](probe/preconditions.tsv)). **Missing** counts checks made while that need was missing (the button should not be pressable), **Ready** those made while it was met (the button must be pressable). Pressable means enabled and shown. Checks come from every family and from the per-button sweep.

Verdict: PASS both halves hold (a button that needs nothing passes on its ready half) · SOFT pressable although its need is missing (see what a press does) · HARD ready but not pressable, or a press that failed · READY ONLY its missing half was not checked · UNTESTED no check.

| Button | Tab / section / text | Needs | Missing: wrong / checks | Ready: wrong / checks | Presses | When pressed without its need | Verdict |
|---|---|---|---:|---:|---:|---|---|
| dialog_document_open_show_the_example_1 | dialog_document_open / — / Show the example | nothing: the example exists | 0 / 0 | 0 / 1 | 1 | — | PASS |
| dialog_library_open_show_the_example_1 | dialog_library_open / — / Show the example | nothing: the example exists | 0 / 0 | 0 / 1 | 1 | — | PASS |
| dialog_results_open_show_the_example_1 | dialog_results_open / — / Show the example | nothing: the example exists | 0 / 0 | 0 / 1 | 1 | — | PASS |
| dialog_scenario_open_show_the_example_1 | dialog_scenario_open / — / Show the example | nothing: the example exists | 0 / 0 | 0 / 1 | 1 | — | PASS |
| search_close_1 | search / — / Close | nothing | 0 / 0 | 0 / 4 | 4 | — | PASS |
| search_stop_1 | search / — / Stop | a search still running | 0 / 6 | 0 / 4 | 2 | — | PASS |
| search_use_the_selected_1 | search / — / Use the selected | a selected search result | 0 / 10 | 0 / 6 | 6 | — | PASS |
| shell_hints | shell / — / Hints on | nothing: hints can always be switched | 0 / 0 | 0 / 958 | 1914 | — | PASS |
| t01_auto_search_1 | t01 / — / Auto-search… | nothing | 0 / 0 | 0 / 9 | 2 | — | PASS |
| t01_check_the_candidate_count_1 | t01 / — / Check the candidate count | a selected example of an open library, and no count running | 22 / 22 | 0 / 4 | 7 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/library-1181973267151913009/evidence.json) |
| t01_open_a_library_1 | t01 / — / Open a library... | nothing | 0 / 0 | 0 / 9 | 2 | — | PASS |
| t01_open_in_the_scenario_tab_1 | t01 / — / Open in the Scenario tab | a selected example of an open library | 21 / 21 | 0 / 4 | 8 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/library-1181973267151913009/evidence.json) |
| t01_open_the_example_library_1 | t01 / — / Open the example library | nothing | 0 / 0 | 0 / 9 | 2 | — | PASS |
| t01_runner_settings_step_3_1 | t01 / — / Runner settings (step 3) | nothing | 0 / 0 | 0 / 9 | 9 | — | PASS |
| t01_use_python_1 | t01 / — / Use Python | nothing: it applies the Python runner or shows step 3 with the reason | 0 / 0 | 0 / 9 | 2 | — | PASS |
| t02_2_1_control_sheets_add_verdict_1 | t02 / 2.1 · Control sheets / Add verdict | nothing | 0 / 0 | 0 / 18 | 20 | — | PASS |
| t02_2_1_control_sheets_apply_to_the_document_1 | t02 / 2.1 · Control sheets / Apply to the document | nothing: a document is always shown | 0 / 0 | 0 / 18 | 18 | — | PASS |
| t02_2_1_control_sheets_load_from_the_document_1 | t02 / 2.1 · Control sheets / Load from the document | nothing: a document is always shown | 0 / 0 | 0 / 18 | 18 | — | PASS |
| t02_2_1_control_sheets_remove_verdict_1 | t02 / 2.1 · Control sheets / Remove verdict | a selected verdict row | 34 / 34 | 0 / 18 | 20 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/authoring-6565183086860775869/evidence.json) |
| t02_2_4_dependency_graph_copy_dot_1 | t02 / 2.4 · Dependency graph / Copy DOT | a dependency graph, drawn by a successful validation | 26 / 26 | 0 / 10 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/authoring-6565183086860775869/evidence.json) |
| t02_2_4_dependency_graph_save_dot_or_json_1 | t02 / 2.4 · Dependency graph / Save DOT or JSON... | a dependency graph, drawn by a successful validation | 26 / 26 | 0 / 10 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/authoring-6565183086860775869/evidence.json) |
| t02_add_sheet_1 | t02 / — / Add sheet | nothing | 0 / 0 | 0 / 18 | 2 | — | PASS |
| t02_auto_search_1 | t02 / — / Auto-search… | nothing | 0 / 0 | 0 / 18 | 2 | — | PASS |
| t02_insert_into_the_selected_cell_1 | t02 / — / Insert into the selected cell | a selected cell and a selected template | 10 / 10 | 0 / 10 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/authoring-3995830737712100646/evidence.json) |
| t02_insert_row_1 | t02 / — / Insert row | a selected sheet whose rows are shown | 2 / 2 | 0 / 18 | 12 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_01-1692763648661378035/evidence.json) |
| t02_new_1 | t02 / — / New | nothing | 0 / 0 | 0 / 18 | 26 | — | PASS |
| t02_open_1 | t02 / — / Open... | nothing | 0 / 0 | 0 / 18 | 2 | — | PASS |
| t02_remove_1 | t02 / — / Remove | a selected sheet | 2 / 2 | 0 / 18 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_01-9530368220656301536/evidence.json) |
| t02_remove_row_1 | t02 / — / Remove row | a selected row | 10 / 10 | 0 / 10 | 12 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/authoring-3995830737712100646/evidence.json) |
| t02_rename_1 | t02 / — / Rename | a selected sheet | 2 / 2 | 0 / 18 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_01-11584669684317318904/evidence.json) |
| t02_save_1 | t02 / — / Save | nothing: an untitled scenario is saved under a new name | 0 / 0 | 0 / 34 | 2 | — | PASS |
| t02_save_as_1 | t02 / — / Save as... | nothing | 0 / 0 | 0 / 34 | 2 | — | PASS |
| t02_validate_1 | t02 / — / Validate | nothing: validation explains an empty or broken scenario | 0 / 0 | 0 / 34 | 2 | — | PASS |
| t03_auto_search_1 | t03 / — / Auto-search… | nothing | 0 / 0 | 0 / 8 | 2 | — | PASS |
| t03_browse_1 | t03 / — / Browse... | nothing | 0 / 0 | 0 / 8 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_add_1 | t03 / Runner settings (optional): what runs the test cases, and which of them / Add... | nothing: add-on paths serve every runner | 0 / 0 | 0 / 20 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_add_2 | t03 / Runner settings (optional): what runs the test cases, and which of them / Add... | the Python runner chosen | 0 / 12 | 0 / 10 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_apply_runner_settings_1 | t03 / Runner settings (optional): what runs the test cases, and which of them / Apply runner settings | nothing: applying explains what is wrong | 0 / 0 | 0 / 26 | 8 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_auto_search_1 | t03 / Runner settings (optional): what runs the test cases, and which of them / Auto-search… | nothing: add-on paths serve every runner | 0 / 0 | 0 / 20 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_auto_search_2 | t03 / Runner settings (optional): what runs the test cases, and which of them / Auto-search… | the Python runner chosen | 0 / 12 | 0 / 10 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_auto_search_3 | t03 / Runner settings (optional): what runs the test cases, and which of them / Auto-search… | the Python runner chosen | 0 / 12 | 0 / 10 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_auto_search_4 | t03 / Runner settings (optional): what runs the test cases, and which of them / Auto-search… | the Python runner chosen | 0 / 12 | 0 / 10 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_browse_1 | t03 / Runner settings (optional): what runs the test cases, and which of them / Browse... | the Python runner chosen | 0 / 12 | 0 / 10 | 2 | — | PASS |
| t03_runner_settings_optional_what_runs_the_test_cases_and_which_of_them_browse_2 | t03 / Runner settings (optional): what runs the test cases, and which of them / Browse... | the Python runner chosen | 0 / 12 | 0 / 10 | 2 | — | PASS |
| t03_use_1 | t03 / — / Use | a newer remembered scenario than the one in the box | 0 / 8 | 0 / 2 | 2 | — | PASS |
| t03_use_the_example_1 | t03 / — / Use the example | nothing | 0 / 0 | 0 / 8 | 8 | — | PASS |
| t03_validate_and_plan_1 | t03 / — / Validate and plan | a scenario file in the box, and no validation running | 8 / 14 | 0 / 8 | 10 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/scenario_runner-11788384517903812966/evidence.json) |
| t04__1 | t04 / — / ↑ | a selected rule with one above it | 14 / 42 | 0 / 2 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04__2 | t04 / — / ↓ | a selected rule with one below it | 14 / 42 | 0 / 2 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_adjacent_1 | t04 / — / Adjacent | a selected pairs, sets or formula rule | 2 / 34 | 0 / 6 | 8 | 2 leaves a pending draft | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-7957961243540005889/evidence.json) |
| t04_advanced_json_apply_json_1 | t04 / Advanced JSON / Apply JSON | a scenario opened in the Sieve: applying unchanged JSON only checks it | 2 / 30 | 0 / 6 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-3271960629219818022/evidence.json) |
| t04_advanced_json_discard_json_draft_1 | t04 / Advanced JSON / Discard JSON draft | an edited JSON text | 6 / 34 | 0 / 6 | 8 | 2 reloads the shown text | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_rules-15661931691578469671/evidence.json) |
| t04_apply_rule_1 | t04 / — / Apply rule | a selected rule or a new rule draft: applying it unchanged only checks it | 2 / 42 | 0 / 6 | 6 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-4635240422105069885/evidence.json) |
| t04_auto_search_1 | t04 / — / Auto-search… | a scenario opened in the Sieve | 14 / 42 | 0 / 14 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_bond_matrix_add_pair_at_cell_1 | t04 / Bond matrix / Add pair at cell | a selected value cell of the matrix | 26 / 54 | 0 / 14 | 16 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_bond_matrix_edit_cell_rule_1 | t04 / Bond matrix / Edit cell rule | a selected matrix cell that a rule counts | 26 / 54 | 0 / 2 | 16 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_cancel_work_1 | t04 / — / Cancel work | Sieve work in progress | 0 / 38 | 0 / 42 | 14 | — | PASS |
| t04_check_impact_1 | t04 / — / Check impact | a scenario opened in the Sieve | 14 / 42 | 0 / 14 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_delete_1 | t04 / — / Delete | a selected rule | 14 / 42 | 0 / 14 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_discard_draft_1 | t04 / — / Discard draft | a changed rule draft | 6 / 34 | 0 / 2 | 36 | 2 reloads the shown text | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_rules-15661931691578469671/evidence.json) |
| t04_duplicate_1 | t04 / — / Duplicate | a selected rule | 14 / 42 | 0 / 14 | 16 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_export_json_1 | t04 / — / Export JSON… | a scenario opened in the Sieve | 14 / 42 | 0 / 14 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_face_2_sieve_1 | t04 / — / Face 2 (Sieve) ↗ | a scenario chosen in step 3 | 14 / 42 | 0 / 14 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_find_on_graph_1 | t04 / — / Find on graph | a selected rule | 14 / 42 | 0 / 14 | 16 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_import_constraints_1 | t04 / — / Import constraints… | a scenario opened in the Sieve | 14 / 42 | 0 / 14 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_new_rule_1 | t04 / — / New rule | a scenario opened in the Sieve | 14 / 42 | 0 / 22 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_only_when_all_of_1 | t04 / Only when / all of | a selected condition | 0 / 34 | 0 / 6 | 6 | — | PASS |
| t04_only_when_any_of_1 | t04 / Only when / any of | a selected condition | 0 / 34 | 0 / 6 | 6 | — | PASS |
| t04_only_when_apply_json_1 | t04 / Only when / Apply JSON | a selected rule | 2 / 30 | 0 / 6 | 8 | 2 leaves a pending draft | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-16970675694101319465/evidence.json) |
| t04_only_when_condition_1 | t04 / Only when / + condition | a selected rule whose condition can grow | 2 / 30 | 0 / 6 | 12 | 2 leaves a pending draft | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-16864759395457991074/evidence.json) |
| t04_only_when_delete_1 | t04 / Only when / Delete | a selected condition | 0 / 34 | 0 / 6 | 6 | — | PASS |
| t04_only_when_down_1 | t04 / Only when / Down | a selected condition with one after it | 0 / 38 | 0 / 6 | 6 | — | PASS |
| t04_only_when_not_1 | t04 / Only when / not | a selected condition | 0 / 34 | 0 / 6 | 6 | — | PASS |
| t04_only_when_up_1 | t04 / Only when / Up | a selected condition with one before it | 0 / 42 | 0 / 2 | 6 | — | PASS |
| t04_open_step_3_scenario_1 | t04 / — / Open step 3 scenario | a scenario chosen in step 3 | 14 / 42 | 0 / 42 | 32 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_parameters_orders_apply_data_1 | t04 / Parameters / orders / Apply data | a scenario opened in the Sieve: applying unchanged data only checks it | 2 / 30 | 0 / 10 | 8 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-4977102430018910288/evidence.json) |
| t04_parameters_orders_discard_data_draft_1 | t04 / Parameters / orders / Discard data draft | a changed attribute or order | 10 / 38 | 0 / 10 | 24 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| t04_parameters_orders_parameters_add_attribute_1 | t04 / Parameters / orders / Parameters / Add attribute | a scenario opened in the Sieve | 2 / 30 | 0 / 10 | 16 | 2 leaves a pending draft | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-15684163569791147612/evidence.json) |
| t04_parameters_orders_parameters_remove_attribute_1 | t04 / Parameters / orders / Parameters / Remove attribute | a selected attribute row | 10 / 38 | 0 / 10 | 12 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| t04_parameters_orders_value_orders_1 | t04 / Parameters / orders / Value orders / ↑ | a selected ranked value with one above it | 14 / 42 | 0 / 6 | 8 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| t04_parameters_orders_value_orders_2 | t04 / Parameters / orders / Value orders / ↓ | a selected ranked value with one below it | 14 / 42 | 0 / 6 | 8 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| t04_parameters_orders_value_orders_add_value_1 | t04 / Parameters / orders / Value orders / Add value | a scenario opened in the Sieve | 2 / 30 | 0 / 10 | 44 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-2365245241874720861/evidence.json) |
| t04_parameters_orders_value_orders_remove_1 | t04 / Parameters / orders / Value orders / Remove | a selected ranked value | 10 / 38 | 0 / 10 | 12 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-10135731671977578741/evidence.json) |
| t04_redo_1 | t04 / — / Redo | an edit that Undo took back | 50 / 78 | 0 / 14 | 16 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_rule_body_1 | t04 / Rule body / + − × | an empty number slot selected in the formula | 2 / 6 | 0 / 4 | 4 | 2 replaces the formula | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-9921665219437941589/evidence.json) |
| t04_rule_body_add_row_1 | t04 / Rule body / Add row | a selected pairs rule | 0 / 2 | 0 / 6 | 6 | — | PASS |
| t04_rule_body_and_or_1 | t04 / Rule body / and / or | an empty true-or-false slot selected in the formula | 2 / 2 | 0 / 6 | 4 | 2 replaces the formula | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-11411781650776130314/evidence.json) |
| t04_rule_body_blocks_1 | t04 / Rule body / Blocks | a selected formula rule | 0 / 2 | 0 / 6 | 10 | — | PASS |
| t04_rule_body_compare_1 | t04 / Rule body / compare | an empty true-or-false slot selected in the formula | 2 / 2 | 0 / 6 | 4 | 2 replaces the formula | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-17269008651196230720/evidence.json) |
| t04_rule_body_delete_1 | t04 / Rule body / Delete | a selected block of the formula | 0 / 6 | 0 / 6 | 6 | — | PASS |
| t04_rule_body_move_1 | t04 / Rule body / Move… | a selected block that is not the whole formula | 0 / 6 | 0 / 6 | 6 | — | PASS |
| t04_rule_body_move_here_1 | t04 / Rule body / Move here | a picked-up block and a slot it fits selected | 0 / 6 | 0 / 2 | 2 | — | PASS |
| t04_rule_body_not_1 | t04 / Rule body / not | an empty true-or-false slot selected in the formula | 2 / 2 | 0 / 6 | 4 | 2 replaces the formula | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-18287713602088277029/evidence.json) |
| t04_rule_body_number_1 | t04 / Rule body / number | an empty number slot selected in the formula | 2 / 6 | 0 / 4 | 4 | 2 replaces the formula | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-12319738947854652404/evidence.json) |
| t04_rule_body_remove_row_1 | t04 / Rule body / Remove row | a selected row of the rule | 6 / 6 | 0 / 6 | 8 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_rules-15661931691578469671/evidence.json) |
| t04_rule_body_sheet_param_1 | t04 / Rule body / sheet.param | an empty number slot selected in the formula | 2 / 6 | 0 / 4 | 4 | 2 replaces the formula | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-16931750423042350066/evidence.json) |
| t04_rule_body_text_1 | t04 / Rule body / Text | a selected formula rule | 0 / 2 | 0 / 6 | 8 | — | PASS |
| t04_rule_body_use_chosen_sheets_1 | t04 / Rule body / Use chosen sheets | a selected pairs or sets rule with two sheets chosen in its list | 2 / 2 | 0 / 6 | 8 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_03-16737062916881173738/evidence.json) |
| t04_save_for_scenario_1 | t04 / — / Save for scenario | a scenario opened in the Sieve | 14 / 42 | 0 / 26 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_sieve_graph_1 | t04 / Sieve graph / + | a scenario opened in the Sieve: cards to zoom | 2 / 30 | 0 / 14 | 16 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-3603755479788586407/evidence.json) |
| t04_sieve_graph_2 | t04 / Sieve graph / − | a scenario opened in the Sieve: cards to zoom | 2 / 30 | 0 / 14 | 16 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-12548471947763173218/evidence.json) |
| t04_sieve_graph_arrange_1 | t04 / Sieve graph / Arrange | a scenario opened in the Sieve: cards to arrange | 2 / 30 | 0 / 14 | 16 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-17610637255299192282/evidence.json) |
| t04_sieve_graph_fit_1 | t04 / Sieve graph / Fit | a scenario opened in the Sieve: cards to fit | 2 / 30 | 0 / 14 | 16 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-1442323133812865688/evidence.json) |
| t04_sieve_graph_group_selected_values_1 | t04 / Sieve graph / Group selected values | values of two sheets chosen on the graph | 26 / 54 | 0 / 2 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_undo_1 | t04 / — / Undo | an edit to take back | 29 / 57 | 0 / 35 | 24 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_validate_1 | t04 / — / Validate | a scenario opened in the Sieve | 14 / 42 | 0 / 14 | 8 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sieve_graph-8731080843808272362/evidence.json) |
| t04_within_1 | t04 / — / Within | a selected pairs, sets or formula rule | 2 / 30 | 0 / 6 | 8 | 2 leaves a pending draft | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_02-6751028112573173901/evidence.json) |
| t05_auto_format_1 | t05 / — / Auto-format | the complete code of a selected test case, and no formatting running | 0 / 54 | 0 / 44 | 17 | — | PASS |
| t05_cancel_1 | t05 / — / Cancel | a preview running | 0 / 38 | 0 / 14 | 6 | — | PASS |
| t05_executable_only_1 | t05 / — / Executable only | nothing | 0 / 0 | 0 / 14 | 2 | — | PASS |
| t05_next_page_1 | t05 / — / Next page | a shown preview of the step 3 scenario with another page, and no preview running | 3 / 50 | 0 / 8 | 8 | family check | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/candidates-1112512003906392091/evidence.json) |
| t05_original_1 | t05 / — / Original | a formatted view to undo, or formatting running | 0 / 80 | 0 / 21 | 10 | — | PASS |
| t05_preview_1 | t05 / — / Preview | a scenario chosen in step 3, and no preview running | 14 / 38 | 0 / 14 | 16 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/candidates-9215170344141201448/evidence.json) |
| t06_cancel_1 | t06 / — / Cancel | a run that has not ended | 0 / 692 | 0 / 446 | 150 | — | PASS |
| t06_export_1 | t06 / — / Export... | an ended run | 0 / 770 | 0 / 368 | 2 | — | PASS |
| t06_pause_1 | t06 / — / Pause | a running run | 0 / 1076 | 0 / 62 | 22 | — | PASS |
| t06_resume_1 | t06 / — / Resume | a paused or pausing run | 0 / 752 | 0 / 386 | 76 | — | PASS |
| t06_run_1 | t06 / — / Run | a scenario chosen in step 3, and no run going | 164 / 608 | 0 / 530 | 166 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/run-7154035314176791557/evidence.json) |
| t06_runner_settings_step_3_1 | t06 / — / Runner settings (step 3) | nothing | 0 / 0 | 0 / 164 | 164 | — | PASS |
| t06_use_python_1 | t06 / — / Use Python | nothing: it applies the Python runner or shows step 3 with the reason | 0 / 0 | 0 / 164 | 2 | — | PASS |
| t07_cancel_1 | t07 / — / Cancel | a campaign that has not ended | 0 / 347 | 0 / 224 | 76 | — | PASS |
| t07_pause_1 | t07 / — / Pause | a running campaign | 0 / 539 | 0 / 32 | 12 | — | PASS |
| t07_resume_1 | t07 / — / Resume | a paused or pausing campaign | 0 / 377 | 0 / 194 | 39 | — | PASS |
| t07_run_campaign_1 | t07 / — / Run campaign | a scenario chosen in step 3, and no campaign going | 83 / 305 | 0 / 266 | 85 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/campaign-8376676202348254976/evidence.json) |
| t08_analyze_1 | t08 / — / Analyze | a metrics file in the box, and no analysis running | 8 / 14 | 0 / 14 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/analysis-14060807089935582095/evidence.json) |
| t08_auto_search_1 | t08 / — / Auto-search… | nothing | 0 / 0 | 0 / 8 | 2 | — | PASS |
| t08_auto_search_2 | t08 / — / Auto-search… | a coverage format chosen | 0 / 14 | 0 / 8 | 2 | — | PASS |
| t08_browse_1 | t08 / — / Browse... | nothing | 0 / 0 | 0 / 8 | 2 | — | PASS |
| t08_browse_2 | t08 / — / Browse... | a coverage format chosen | 0 / 14 | 0 / 8 | 2 | — | PASS |
| t08_use_1 | t08 / — / Use | a newer remembered metrics file than the one in the box | 0 / 8 | 0 / 2 | 2 | — | PASS |
| t08_use_the_example_1 | t08 / — / Use the example | nothing | 0 / 0 | 0 / 8 | 8 | — | PASS |
| t09_auto_plan_1 | t09 / — / Auto-plan | a text file in the box | 8 / 8 | 0 / 6 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |
| t09_auto_search_1 | t09 / — / Auto-search… | nothing | 0 / 0 | 0 / 6 | 2 | — | PASS |
| t09_auto_search_2 | t09 / — / Auto-search… | nothing | 0 / 0 | 0 / 2 | 2 | — | PASS |
| t09_browse_1 | t09 / — / Browse... | nothing | 0 / 0 | 0 / 6 | 2 | — | PASS |
| t09_export_csv_1 | t09 / — / Export CSV... | a successful scoring of the text and rules in the boxes | 4 / 12 | 0 / 4 | 2 | family check | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |
| t09_load_rules_1 | t09 / — / Load rules... | nothing | 0 / 0 | 0 / 6 | 2 | — | PASS |
| t09_optimization_charts_1 | t09 / — / Optimization charts | a successful scoring of the text and rules in the boxes | 4 / 12 | 0 / 4 | 2 | family check | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |
| t09_score_1 | t09 / — / Score | a text file in the box, and no scoring running | 8 / 12 | 0 / 10 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/scorer-18346386050706109217/evidence.json) |
| t09_use_1 | t09 / — / Use | a newer remembered text file than the one in the box | 0 / 6 | 0 / 2 | 2 | — | PASS |
| t10_0_1_situation_a_workflow_steps_whose_order_matters_1 | t10 / 0.1 · Situation / A workflow: steps whose order matters | nothing | 0 / 0 | 0 / 26 | 2 | — | PASS |
| t10_0_1_situation_fill_in_this_example_1 | t10 / 0.1 · Situation / Fill in this example | nothing: a situation is always chosen | 0 / 0 | 0 / 26 | 2 | — | PASS |
| t10_0_1_situation_none_of_these_i_describe_my_own_factors_1 | t10 / 0.1 · Situation / None of these: I describe my own factors | nothing | 0 / 0 | 0 / 26 | 2 | — | PASS |
| t10_0_1_situation_options_that_can_be_switched_on_together_1 | t10 / 0.1 · Situation / Options that can be switched on together | nothing | 0 / 0 | 0 / 26 | 2 | — | PASS |
| t10_0_1_situation_repeated_picks_the_same_item_may_come_more_than_once_1 | t10 / 0.1 · Situation / Repeated picks: the same item may come more than once | nothing | 0 / 0 | 0 / 26 | 2 | — | PASS |
| t10_0_1_situation_settings_or_a_form_each_setting_takes_one_value_1 | t10 / 0.1 · Situation / Settings or a form: each setting takes one value | nothing | 0 / 0 | 0 / 26 | 2 | — | PASS |
| t10_0_1_situation_something_that_may_or_may_not_happen_1 | t10 / 0.1 · Situation / Something that may or may not happen | nothing | 0 / 0 | 0 / 26 | 2 | — | PASS |
| t10_0_2_factors_add_a_factor_1 | t10 / 0.2 · Factors / Add a factor | nothing | 0 / 0 | 0 / 26 | 6 | — | PASS |
| t10_0_2_factors_move_down_1 | t10 / 0.2 · Factors / Move down | a selected factor with one below it | 11 / 11 | 0 / 17 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/wizard-17633075829542969749/evidence.json) |
| t10_0_2_factors_move_up_1 | t10 / 0.2 · Factors / Move up | a selected factor with one above it | 21 / 21 | 0 / 7 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/wizard-7887376197745120122/evidence.json) |
| t10_0_2_factors_remove_the_factor_1 | t10 / 0.2 · Factors / Remove the factor | a selected factor | 2 / 2 | 0 / 26 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-10849686477154058794/evidence.json) |
| t10_0_3_how_each_varies_a_fixed_number_of_them_1 | t10 / 0.3 · How each varies / A fixed number of them | a selected factor | 2 / 2 | 0 / 74 | 34 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-8009726333459885335/evidence.json) |
| t10_0_3_how_each_varies_a_value_may_appear_more_than_once_1 | t10 / 0.3 · How each varies / A value may appear more than once | a selected factor that takes a fixed number (2 or more) of values | 0 / 50 | 0 / 26 | 23 | — | PASS |
| t10_0_3_how_each_varies_any_number_of_them_within_bounds_1 | t10 / 0.3 · How each varies / Any number of them, within bounds | a selected factor | 2 / 2 | 0 / 74 | 30 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-13286708546930391231/evidence.json) |
| t10_0_3_how_each_varies_exactly_one_of_them_1 | t10 / 0.3 · How each varies / Exactly one of them | a selected factor | 2 / 2 | 0 / 74 | 56 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-14574474471832466410/evidence.json) |
| t10_0_3_how_each_varies_it_may_or_may_not_happen_a_sudden_action_1 | t10 / 0.3 · How each varies / It may or may not happen: a sudden action | a selected factor | 2 / 2 | 0 / 74 | 6 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-3018268090685572901/evidence.json) |
| t10_0_3_how_each_varies_no_they_form_a_group_and_a_b_is_the_same_as_b_a_1 | t10 / 0.3 · How each varies / No: they form a group, and a+b is the same as b+a | a selected factor that takes several values | 0 / 26 | 0 / 50 | 10 | — | PASS |
| t10_0_3_how_each_varies_yes_they_form_a_sequence_and_a_b_differs_from_b_a_1 | t10 / 0.3 · How each varies / Yes: they form a sequence, and a+b differs from b+a | a selected factor that takes several values | 0 / 26 | 0 / 50 | 50 | — | PASS |
| t10_0_4_rules_sieve_add_the_rule_1 | t10 / 0.4 · Rules (sieve) / Add the rule | two factors whose values a rule can join | 2 / 2 | 0 / 2 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_04-16768808700079251764/evidence.json) |
| t10_0_4_rules_sieve_remove_the_rule_1 | t10 / 0.4 · Rules (sieve) / Remove the rule | a selected rule | 26 / 26 | 0 / 2 | 4 | 2 silent | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/wizard-7887376197745120122/evidence.json) |
| t10_0_5_how_many_every_combination_1 | t10 / 0.5 · How many / Every combination | nothing | 0 / 0 | 0 / 98 | 54 | — | PASS |
| t10_0_5_how_many_every_pair_of_values_pairwise_1 | t10 / 0.5 · How many / Every pair of values (pairwise) | nothing | 0 / 0 | 0 / 98 | 30 | — | PASS |
| t10_0_5_how_many_every_triple_of_values_1 | t10 / 0.5 · How many / Every triple of values | nothing | 0 / 0 | 0 / 98 | 26 | — | PASS |
| t10_0_5_how_many_run_every_order_of_each_sequence_of_actions_with_each_kept_combination_1 | t10 / 0.5 · How many / Run every order of each sequence of actions with each kept combination | a covering selection and a sequence of actions | 0 / 86 | 0 / 14 | 2 | — | PASS |
| t10_0_5_how_many_search_harder_for_fewer_test_cases_set_cover_1 | t10 / 0.5 · How many / Search harder for fewer test cases (set-cover) | a covering selection, not every combination | 0 / 26 | 0 / 74 | 2 | — | PASS |
| t10_0_5_how_many_the_strongest_covering_within_a_number_of_rows_1 | t10 / 0.5 · How many / The strongest covering within a number of rows | nothing | 0 / 0 | 0 / 98 | 26 | — | PASS |
| t10_0_6_bootstrap_run_a_bootstrap_once_first_before_all_the_test_cases_fw_runmefirstonce_1 | t10 / 0.6 · Bootstrap / Run a bootstrap once, first, before all the test cases (FW_RunMeFirstOnce) | nothing | 0 / 0 | 0 / 2 | 26 | — | PASS |
| t10_0_7_finish_add_a_check_sheet_where_your_test_decides_its_verdict_1 | t10 / 0.7 · Finish / Add a check sheet where your test decides its verdict | nothing | 0 / 0 | 0 / 2 | 26 | — | PASS |
| t10_0_7_finish_check_with_the_framework_1 | t10 / 0.7 · Finish / Check with the Framework | a design without problems, and no check running | 2 / 26 | 0 / 50 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_05-683761631677250968/evidence.json) |
| t10_0_7_finish_open_it_in_step_2_1 | t10 / 0.7 · Finish / Open it in step 2 | a saved scenario | 0 / 26 | 0 / 26 | 26 | — | PASS |
| t10_0_7_finish_save_the_scenario_1 | t10 / 0.7 · Finish / Save the scenario... | a design without problems | 2 / 26 | 0 / 50 | 4 | 2 explains | SOFT · [evidence](../target/guif-dogfood-v2/fixtures/sweep_05-173054762155143741/evidence.json) |
| t10_0_7_finish_show_its_test_cases_step_5_1 | t10 / 0.7 · Finish / Show its test cases (step 5) | a saved scenario | 0 / 26 | 0 / 26 | 26 | — | PASS |
| t10_0_7_finish_use_it_in_step_3_1 | t10 / 0.7 · Finish / Use it in step 3 | a saved scenario | 0 / 26 | 0 / 26 | 26 | — | PASS |
| t10_back_1 | t10 / — / Back | a page before the shown one | 0 / 50 | 0 / 146 | 2 | — | PASS |
| t10_next_0_2_factors_1 | t10 / — / Next: 0.2 · Factors | a page after the shown one | 0 / 26 | 0 / 146 | 2 | — | PASS |
