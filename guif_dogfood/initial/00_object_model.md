# Object model before execution

The 206 Appendix A identities are immutable. Duplicate controls are scoped by tab and occurrence in the component tree; unnamed Sieve controls use structural kind/occurrence selectors. Dynamic library and authoring table names are matched by their stable prefix. The catalog also includes cross-cutting logical objects.

P0: cancellation, draft ownership, export integrity and teardown. P1: search classification, runner settings, file guidance, persistence, authoring. P2: selection, descriptions and presentation. A dialog button is exercised through its backing chooser/API in headless mode; showing a native modal is excluded and never claimed as tested.

| Object | Tab | Kind / shown name | Risk | Scenarios |
|---|---|---|---|---|
| `t0_toggle_hints_on_f1` | 0 | toggle: Hints on (F1) | P2 | x_shell |
| `t1_list_groups_of_examples` | 1 | list: Groups of examples | P1 | t1_library |
| `t1_table_scenarios_of_the_selected_suite_documented_counts_and_sources` | 1 | table: Scenarios of the selected suite: documented counts and sources | P1 | t1_library |
| `t1_text_area_about_the_selected_example` | 1 | text area: About the selected example | P1 | t1_library |
| `t1_button_open_the_example_library` | 1 | button: Open the example library | P1 | t1_library |
| `t1_button_open_a_library` | 1 | button: Open a library... | P1 | t1_library |
| `t1_button_auto_search` | 1 | button: Auto-search… | P1 | t1_library |
| `t1_button_open_in_the_scenario_tab` | 1 | button: Open in the Scenario tab | P1 | t1_library |
| `t1_button_check_the_candidate_count` | 1 | button: Check the candidate count | P1 | t1_library |
| `t1_button_use_python` | 1 | button: Use Python | P1 | t1_library |
| `t1_button_runner_settings_step_3` | 1 | button: Runner settings (step 3) | P1 | t1_library |
| `t2_button_new` | 2 | button: New | P1 | t2_authoring |
| `t2_button_open` | 2 | button: Open... | P1 | t2_authoring |
| `t2_button_auto_search` | 2 | button: Auto-search… | P1 | t2_authoring |
| `t2_button_save` | 2 | button: Save | P1 | t2_authoring |
| `t2_button_save_as` | 2 | button: Save as... | P1 | t2_authoring |
| `t2_button_validate` | 2 | button: Validate | P1 | t2_authoring |
| `t2_list_sheets` | 2 | list: Sheets | P1 | t2_authoring |
| `t2_button_add_sheet` | 2 | button: Add sheet | P1 | t2_authoring |
| `t2_button_rename` | 2 | button: Rename | P1 | t2_authoring |
| `t2_button_remove` | 2 | button: Remove | P1 | t2_authoring |
| `t2_button_insert_row` | 2 | button: Insert row | P1 | t2_authoring |
| `t2_button_remove_row` | 2 | button: Remove row | P1 | t2_authoring |
| `t2_table_cells_of_sheet_fw_seq` | 2 | table: Cells of sheet FW_Seq | P1 | t2_authoring |
| `t2_list_cell_templates` | 2 | list: Cell templates | P1 | t2_authoring |
| `t2_button_insert_into_the_selected_cell` | 2 | button: Insert into the selected cell | P1 | t2_authoring |
| `t2_sub_tab_2_1_control_sheets` | 2 | sub-tab: 2.1 · Control sheets | P1 | t2_authoring |
| `t2_sub_tab_2_2_validation` | 2 | sub-tab: 2.2 · Validation | P1 | t2_authoring |
| `t2_sub_tab_2_3_operand_dependencies` | 2 | sub-tab: 2.3 · Operand dependencies | P1 | t2_authoring |
| `t2_sub_tab_2_4_dependency_graph` | 2 | sub-tab: 2.4 · Dependency graph | P1 | t2_authoring |
| `t2_button_load_from_the_document` | 2 | button: Load from the document | P1 | t2_authoring |
| `t2_button_apply_to_the_document` | 2 | button: Apply to the document | P1 | t2_authoring |
| `t2_button_add_verdict` | 2 | button: Add verdict | P1 | t2_authoring |
| `t2_button_remove_verdict` | 2 | button: Remove verdict | P1 | t2_authoring |
| `t2_text_area_fw_runmefirstonce_code_run_once_before_the_test_cases` | 2 | text area: FW_RunMeFirstOnce: code run once before the test cases | P1 | t2_authoring |
| `t2_text_area_fw_arguments_arguments_one_per_line` | 2 | text area: FW_Arguments: arguments, one per line | P1 | t2_authoring |
| `t2_table_fw_custom_var_verdict_codes_and_their_names` | 2 | table: FW_CUSTOM_VAR: verdict codes and their names | P1 | t2_authoring |
| `t2_text_area_check_results` | 2 | text area: Check results | P1 | t2_authoring |
| `t2_table_operand_dependencies_each_joiner_s_operands_and_their_lifetimes` | 2 | table: Operand dependencies: each joiner's operands and their lifetimes | P1 | t2_authoring |
| `t2_button_copy_dot` | 2 | button: Copy DOT | P1 | t2_authoring |
| `t2_button_save_dot_or_json` | 2 | button: Save DOT or JSON... | P1 | t2_authoring |
| `t2_text_area_problems_in_the_diagram` | 2 | text area: Problems in the diagram | P1 | t2_authoring |
| `t3_text_area_check_results` | 3 | text area: Check results | P1 | t3_runner |
| `t3_table_fw_seq_plan_each_sheet_s_role_values_and_directives` | 3 | table: FW_Seq plan: each sheet's role, values and directives | P1 | t3_runner |
| `t3_text_field_scenario_file_xlsx_json_yaml_or_toml` | 3 | text field: Scenario file (xlsx, json, yaml or toml) | P1 | t3_runner |
| `t3_button_browse` | 3 | button: Browse... | P1 | t3_runner |
| `t3_button_auto_search` | 3 | button: Auto-search… | P1 | t3_runner |
| `t3_button_use_the_example` | 3 | button: Use the example | P1 | t3_runner |
| `t3_button_validate_and_plan` | 3 | button: Validate and plan | P1 | t3_runner |
| `t3_button_use` | 3 | button: Use | P1 | t3_runner |
| `t3_choice_runner` | 3 | choice: Runner | P1 | t3_runner |
| `t3_text_field_add_on_and_dependency_paths` | 3 | text field: Add-on and dependency paths | P1 | t3_runner |
| `t3_text_field_python_interpreter` | 3 | text field: Python interpreter | P1 | t3_runner |
| `t3_text_field_python_import_directories` | 3 | text field: Python import directories | P1 | t3_runner |
| `t3_text_field_python_working_directory` | 3 | text field: Python working directory | P1 | t3_runner |
| `t3_text_field_python_environment_variables` | 3 | text field: Python environment variables | P1 | t3_runner |
| `t3_text_field_python_sandbox` | 3 | text field: Python sandbox | P1 | t3_runner |
| `t3_button_add` | 3 | button: Add... | P1 | t3_runner |
| `t3_button_browse_2` | 3 | button: Browse... | P1 | t3_runner |
| `t3_button_add_2` | 3 | button: Add... | P1 | t3_runner |
| `t3_button_browse_3` | 3 | button: Browse... | P1 | t3_runner |
| `t3_button_auto_search_2` | 3 | button: Auto-search… | P1 | t3_runner |
| `t3_button_auto_search_3` | 3 | button: Auto-search… | P1 | t3_runner |
| `t3_button_auto_search_4` | 3 | button: Auto-search… | P1 | t3_runner |
| `t3_button_auto_search_5` | 3 | button: Auto-search… | P1 | t3_runner |
| `t3_button_apply_runner_settings` | 3 | button: Apply runner settings | P1 | t3_runner |
| `t4_button_open_step_3_scenario` | 4 | button: Open step 3 scenario | P0 | t4_graph |
| `t4_button_import_constraints` | 4 | button: Import constraints… | P0 | t4_graph |
| `t4_button_auto_search` | 4 | button: Auto-search… | P0 | t4_graph |
| `t4_button_export_json` | 4 | button: Export JSON… | P0 | t4_graph |
| `t4_button_save_for_scenario` | 4 | button: Save for scenario | P0 | t4_graph |
| `t4_button_face_2_sieve` | 4 | button: Face 2 (Sieve) ↗ | P0 | t4_graph |
| `t4_button_undo` | 4 | button: Undo | P0 | t4_graph |
| `t4_button_redo` | 4 | button: Redo | P0 | t4_graph |
| `t4_button_validate` | 4 | button: Validate | P0 | t4_graph |
| `t4_button_check_impact` | 4 | button: Check impact | P0 | t4_graph |
| `t4_spinner_unnamed` | 4 | spinner: (unnamed) | P0 | t4_graph |
| `t4_button_cancel_work` | 4 | button: Cancel work | P0 | t4_graph |
| `t4_sub_tab_sieve_graph` | 4 | sub-tab: Sieve graph | P0 | t4_graph |
| `t4_sub_tab_bond_matrix` | 4 | sub-tab: Bond matrix | P0 | t4_graph |
| `t4_sub_tab_parameters_orders` | 4 | sub-tab: Parameters / orders | P0 | t4_graph |
| `t4_sub_tab_advanced_json` | 4 | sub-tab: Advanced JSON | P0 | t4_graph |
| `t4_text_field_unnamed` | 4 | text field: (unnamed) | P0 | t4_graph |
| `t4_button_fit` | 4 | button: Fit | P0 | t4_graph |
| `t4_button_plus` | 4 | button: + | P0 | t4_graph |
| `t4_button_minus` | 4 | button: − | P0 | t4_graph |
| `t4_button_arrange` | 4 | button: Arrange | P0 | t4_graph |
| `t4_choice_unnamed` | 4 | choice: (unnamed) | P0 | t4_graph |
| `t4_button_group_selected_values` | 4 | button: Group selected values | P0 | t4_graph |
| `t4_choice_unnamed_2` | 4 | choice: (unnamed) | P0 | t4_graph |
| `t4_text_field_unnamed_2` | 4 | text field: (unnamed) | P0 | t4_graph |
| `t4_choice_unnamed_3` | 4 | choice: (unnamed) | P0 | t4_graph |
| `t4_text_field_unnamed_3` | 4 | text field: (unnamed) | P0 | t4_graph |
| `t4_button_add_pair_at_cell` | 4 | button: Add pair at cell | P0 | t4_graph |
| `t4_button_edit_cell_rule` | 4 | button: Edit cell rule | P0 | t4_graph |
| `t4_table_unnamed` | 4 | table: (unnamed) | P0 | t4_graph |
| `t4_sub_tab_parameters` | 4 | sub-tab: Parameters | P0 | t4_data |
| `t4_sub_tab_value_orders` | 4 | sub-tab: Value orders | P0 | t4_data |
| `t4_table_unnamed_2` | 4 | table: (unnamed) | P0 | t4_data |
| `t4_button_add_attribute` | 4 | button: Add attribute | P0 | t4_data |
| `t4_button_remove_attribute` | 4 | button: Remove attribute | P0 | t4_data |
| `t4_choice_sheet` | 4 | choice: Sheet | P0 | t4_data |
| `t4_choice_order` | 4 | choice: Order | P0 | t4_data |
| `t4_list_unnamed` | 4 | list: (unnamed) | P0 | t4_data |
| `t4_choice_unnamed_4` | 4 | choice: (unnamed) | P0 | t4_data |
| `t4_button_add_value` | 4 | button: Add value | P0 | t4_data |
| `t4_button_up` | 4 | button: ↑ | P0 | t4_data |
| `t4_button_down` | 4 | button: ↓ | P0 | t4_data |
| `t4_button_remove` | 4 | button: Remove | P0 | t4_data |
| `t4_button_apply_data` | 4 | button: Apply data | P0 | t4_data |
| `t4_button_discard_data_draft` | 4 | button: Discard data draft | P0 | t4_data |
| `t4_text_area_sieve_execution_json` | 4 | text area: #Sieve execution JSON | P0 | t4_data |
| `t4_button_apply_json` | 4 | button: Apply JSON | P0 | t4_data |
| `t4_button_discard_json_draft` | 4 | button: Discard JSON draft | P0 | t4_data |
| `t4_text_field_unnamed_4` | 4 | text field: (unnamed) | P0 | t4_rules |
| `t4_choice_unnamed_5` | 4 | choice: (unnamed) | P0 | t4_rules |
| `t4_button_new_rule` | 4 | button: New rule | P0 | t4_rules |
| `t4_button_duplicate` | 4 | button: Duplicate | P0 | t4_rules |
| `t4_button_delete` | 4 | button: Delete | P0 | t4_rules |
| `t4_button_up_2` | 4 | button: ↑ | P0 | t4_rules |
| `t4_button_down_2` | 4 | button: ↓ | P0 | t4_rules |
| `t4_button_find_on_graph` | 4 | button: Find on graph | P0 | t4_rules |
| `t4_table_unnamed_3` | 4 | table: (unnamed) | P0 | t4_rules |
| `t4_text_field_rule_id` | 4 | text field: Rule ID | P0 | t4_rules |
| `t4_text_field_description` | 4 | text field: Description | P0 | t4_rules |
| `t4_choice_unnamed_6` | 4 | choice: (unnamed) | P0 | t4_rules |
| `t4_check_box_adjacent` | 4 | check box: Adjacent | P0 | t4_rules |
| `t4_check_box_within` | 4 | check box: Within | P0 | t4_rules |
| `t4_spinner_unnamed_2` | 4 | spinner: (unnamed) | P0 | t4_rules |
| `t4_sub_tab_rule_body` | 4 | sub-tab: Rule body | P0 | t4_rules |
| `t4_sub_tab_only_when` | 4 | sub-tab: Only when | P0 | t4_rules |
| `t4_button_condition` | 4 | button: + condition | P0 | t4_rules |
| `t4_button_all_of` | 4 | button: all of | P0 | t4_rules |
| `t4_button_any_of` | 4 | button: any of | P0 | t4_rules |
| `t4_button_not` | 4 | button: not | P0 | t4_rules |
| `t4_button_delete_2` | 4 | button: Delete | P0 | t4_rules |
| `t4_button_up_3` | 4 | button: Up | P0 | t4_rules |
| `t4_button_down_3` | 4 | button: Down | P0 | t4_rules |
| `t4_tree_parts_of_the_condition` | 4 | tree: Parts of the condition | P0 | t4_rules |
| `t4_choice_sheet_2` | 4 | choice: sheet | P0 | t4_rules |
| `t4_choice_test` | 4 | choice: test | P0 | t4_rules |
| `t4_choice_value` | 4 | choice: value | P0 | t4_rules |
| `t4_list_values` | 4 | list: Values | P0 | t4_rules |
| `t4_choice_present` | 4 | choice: present | P0 | t4_rules |
| `t4_choice_other_column` | 4 | choice: other column | P0 | t4_rules |
| `t4_spinner_count` | 4 | spinner: count | P0 | t4_rules |
| `t4_text_field_json` | 4 | text field: JSON | P0 | t4_rules |
| `t4_button_apply_json_2` | 4 | button: Apply JSON | P0 | t4_rules |
| `t4_button_apply_rule` | 4 | button: Apply rule | P0 | t4_rules |
| `t4_button_discard_draft` | 4 | button: Discard draft | P0 | t4_rules |
| `t4_text_area_unnamed` | 4 | text area: (unnamed) | P0 | t4_rules |
| `t5_text_field_look_at_most_at_test_cases` | 5 | text field: Look at most at (test cases) | P2 | t5_candidates |
| `t5_spinner_rows_per_page` | 5 | spinner: Rows per page | P2 | t5_candidates |
| `t5_check_box_executable_only` | 5 | check box: Executable only | P2 | t5_candidates |
| `t5_button_preview` | 5 | button: Preview | P2 | t5_candidates |
| `t5_button_next_page` | 5 | button: Next page | P2 | t5_candidates |
| `t5_button_cancel` | 5 | button: Cancel | P2 | t5_candidates |
| `t5_table_plan_estimates_each_count_with_its_confidence_and_formula` | 5 | table: Plan estimates: each count with its confidence and formula | P2 | t5_candidates |
| `t5_table_rule_impact_what_each_rule_reads_and_the_candidates_it_removes` | 5 | table: Rule impact: what each rule reads and the candidates it removes | P2 | t5_candidates |
| `t5_table_candidate_page_each_candidate_s_ids_and_fate_under_the_rules` | 5 | table: Candidate page: each candidate's ids and fate under the rules | P2 | t5_candidates |
| `t5_text_area_code_of_the_selected_test_case` | 5 | text area: Code of the selected test case | P2 | t5_candidates |
| `t6_text_area_run_report` | 6 | text area: Run report | P0 | t6_run, t6_sequences |
| `t6_button_run` | 6 | button: Run | P0 | t6_run, t6_sequences |
| `t6_button_pause` | 6 | button: Pause | P0 | t6_run, t6_sequences |
| `t6_button_resume` | 6 | button: Resume | P0 | t6_run, t6_sequences |
| `t6_button_cancel` | 6 | button: Cancel | P0 | t6_run, t6_sequences |
| `t6_choice_save_as` | 6 | choice: Save as | P0 | t6_run, t6_sequences |
| `t6_button_export` | 6 | button: Export... | P0 | t6_run, t6_sequences |
| `t6_button_use_python` | 6 | button: Use Python | P0 | t6_run, t6_sequences |
| `t6_button_runner_settings_step_3` | 6 | button: Runner settings (step 3) | P0 | t6_run, t6_sequences |
| `t7_button_run_campaign` | 7 | button: Run campaign | P0 | t7_campaign |
| `t7_button_pause` | 7 | button: Pause | P0 | t7_campaign |
| `t7_button_resume` | 7 | button: Resume | P0 | t7_campaign |
| `t7_button_cancel` | 7 | button: Cancel | P0 | t7_campaign |
| `t7_table_campaign_lineage_each_iteration_s_run_counts_winners_and_front` | 7 | table: Campaign lineage: each iteration's run, counts, winners and front | P0 | t7_campaign |
| `t7_text_area_campaign_report` | 7 | text area: Campaign report | P0 | t7_campaign |
| `t8_text_field_metrics_file_kv` | 8 | text field: Metrics file (.kv) | P1 | t8_analysis |
| `t8_button_browse` | 8 | button: Browse... | P1 | t8_analysis |
| `t8_button_auto_search` | 8 | button: Auto-search… | P1 | t8_analysis |
| `t8_button_use_the_example` | 8 | button: Use the example | P1 | t8_analysis |
| `t8_button_use` | 8 | button: Use | P1 | t8_analysis |
| `t8_choice_code_coverage_optional` | 8 | choice: Code coverage (optional) | P1 | t8_analysis |
| `t8_text_field_report_file` | 8 | text field: report file | P1 | t8_analysis |
| `t8_button_browse_2` | 8 | button: Browse... | P1 | t8_analysis |
| `t8_button_auto_search_2` | 8 | button: Auto-search… | P1 | t8_analysis |
| `t8_button_analyze` | 8 | button: Analyze | P1 | t8_analysis |
| `t8_text_area_analysis_report` | 8 | text area: Analysis report | P1 | t8_analysis |
| `t9_text_field_text_file_to_score` | 9 | text field: Text file to score | P2 | t9_scorer |
| `t9_button_browse` | 9 | button: Browse... | P2 | t9_scorer |
| `t9_button_auto_search` | 9 | button: Auto-search… | P2 | t9_scorer |
| `t9_button_use` | 9 | button: Use | P2 | t9_scorer |
| `t9_spinner_lines_per_learned_pattern` | 9 | spinner: Lines per learned pattern | P2 | t9_scorer |
| `t9_spinner_patterns_kept` | 9 | spinner: Patterns kept | P2 | t9_scorer |
| `t9_spinner_worth_improving_from_score` | 9 | spinner: Worth improving from score | P2 | t9_scorer |
| `t9_spinner_watch_from_score` | 9 | spinner: Watch from score | P2 | t9_scorer |
| `t9_button_load_rules` | 9 | button: Load rules... | P2 | t9_scorer |
| `t9_button_auto_plan` | 9 | button: Auto-plan | P2 | t9_scorer |
| `t9_button_score` | 9 | button: Score | P2 | t9_scorer |
| `t9_button_export_csv` | 9 | button: Export CSV... | P2 | t9_scorer |
| `t9_button_optimization_charts` | 9 | button: Optimization charts | P2 | t9_scorer |
| `t9_button_auto_search_2` | 9 | button: Auto-search… | P2 | t9_scorer |
| `t9_text_area_your_scoring_rules_as_json_required_tokens_token_weights_numeric_ranges_` | 9 | text area: Your scoring rules as JSON (required_tokens, token_weights, numeric_ranges, optimizations, ...) | P2 | t9_scorer |
| `t9_text_field_show_lines_containing` | 9 | text field: Show lines containing | P2 | t9_scorer |
| `t9_table_scored_lines_each_line_s_score_decision_and_features` | 9 | table: Scored lines: each line's score, decision and features | P2 | t9_scorer |
| `t9_text_area_why_this_score` | 9 | text area: Why this score | P2 | t9_scorer |
| `t9_text_area_summary_of_all_scores` | 9 | text area: Summary of all scores | P2 | t9_scorer |
| `x_run_roundtrip` | -1 | logic: x_run_roundtrip | P0 | t6_run |
| `x_run_sequence` | -1 | logic: x_run_sequence | P0 | t6_sequences |
| `x_dialog_scenario_open` | -1 | logic: x_dialog_scenario_open | P1 | x_dialogs |
| `x_dialog_document_open` | -1 | logic: x_dialog_document_open | P1 | x_dialogs |
| `x_dialog_document_save` | -1 | logic: x_dialog_document_save | P1 | x_dialogs |
| `x_dialog_constraints_open` | -1 | logic: x_dialog_constraints_open | P1 | x_dialogs |
| `x_dialog_constraints_save` | -1 | logic: x_dialog_constraints_save | P1 | x_dialogs |
| `x_dialog_graph_save` | -1 | logic: x_dialog_graph_save | P1 | x_dialogs |
| `x_dialog_export_report` | -1 | logic: x_dialog_export_report | P1 | x_dialogs |
| `x_dialog_export_results` | -1 | logic: x_dialog_export_results | P1 | x_dialogs |
| `x_dialog_export_samples` | -1 | logic: x_dialog_export_samples | P1 | x_dialogs |
| `x_dialog_export_metrics` | -1 | logic: x_dialog_export_metrics | P1 | x_dialogs |
| `x_dialog_export_checkpoint` | -1 | logic: x_dialog_export_checkpoint | P1 | x_dialogs |
| `x_dialog_results_open` | -1 | logic: x_dialog_results_open | P1 | x_dialogs |
| `x_dialog_coverage_open` | -1 | logic: x_dialog_coverage_open | P1 | x_dialogs |
| `x_dialog_text_open` | -1 | logic: x_dialog_text_open | P1 | x_dialogs |
| `x_dialog_rules_open` | -1 | logic: x_dialog_rules_open | P1 | x_dialogs |
| `x_dialog_scores_save` | -1 | logic: x_dialog_scores_save | P1 | x_dialogs |
| `x_dialog_library_open` | -1 | logic: x_dialog_library_open | P1 | x_dialogs |
| `x_dialog_addon_open` | -1 | logic: x_dialog_addon_open | P1 | x_dialogs |
| `x_dialog_program_open` | -1 | logic: x_dialog_program_open | P1 | x_dialogs |
| `x_dialog_imports_open` | -1 | logic: x_dialog_imports_open | P1 | x_dialogs |
| `x_dialog_workdir_open` | -1 | logic: x_dialog_workdir_open | P1 | x_dialogs |
| `x_trail` | -1 | logic: x_trail | P1 | x_dialogs |
| `x_search_match` | -1 | logic: x_search_match | P1 | x_search_data, x_search_tools |
| `x_search_roots` | -1 | logic: x_search_roots | P1 | x_search_data, x_search_tools |
| `x_search_budget` | -1 | logic: x_search_budget | P1 | x_search_data, x_search_tools |
| `x_search_filter` | -1 | logic: x_search_filter | P1 | x_search_data, x_search_tools |
| `x_search_selection` | -1 | logic: x_search_selection | P1 | x_search_data, x_search_tools |
| `x_tabs` | -1 | logic: x_tabs | P1 | x_shell |
| `x_hints` | -1 | logic: x_hints | P1 | x_shell |
| `x_zebra` | -1 | logic: x_zebra | P1 | x_shell |
| `x_lifecycle` | -1 | logic: x_lifecycle | P0 | x_shell |
