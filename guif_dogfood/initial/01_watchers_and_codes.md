# Watchers and byte codes before execution

Each catalog object owns a virtual thread named `guif-watch-<id>` and an unbounded snapshot queue. Listeners register on the EDT. Document, action/item, model/selection, spinner/tab change, hierarchy and property events become immutable snapshots. Only the consumer checks assertions and updates violations. Semantic API results are immutable assertion snapshots routed to the responsible object, not assertions in the generated candidate.

Text watchers check editable-value/selection round trips; button watchers check the enabled action and its completion; choice and spinner watchers check selected values and bounds; table/list/tree watchers check selection and populated results; sub-tab watchers check selection or draft refusal. Scenario-specific checks below strengthen these smoke contracts. Listeners do no blocking work. After detach, poison pills and bounded joins drain all assertions before verdict aggregation. Desktop.close is awaited off the EDT. New cf-desktop and guif-watch threads are checked at teardown.

Code 0 is PASS. Negative codes -1/-2/-3/-4 identify harness exception/EDT timeout/close timeout/thread leak. Positive codes are data-sheet ordinals regenerated with FW_CUSTOM_VAR on every build. Names are scenario-qualified by their artifact, allowing the same number to mean different objects in different scenarios. The first failing watcher decides the verdict; every watcher still emits events, violations and exercise mode, so masking is visible.

| Scenario | Code | Name / watcher | Oracle |
|---|---:|---|---|
| t1_library | 6 | `GUIF_T1_LIST_GROUPS_OF_EXAMPLES_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 7 | `GUIF_T1_TABLE_SCENARIOS_OF_THE_SELECTED_SUITE_DOCUMENTED_COUNTS_AND_SOURCES_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 8 | `GUIF_T1_TEXT_AREA_ABOUT_THE_SELECTED_EXAMPLE_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 9 | `GUIF_T1_BUTTON_OPEN_THE_EXAMPLE_LIBRARY_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 10 | `GUIF_T1_BUTTON_OPEN_A_LIBRARY_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 11 | `GUIF_T1_BUTTON_AUTO_SEARCH_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 12 | `GUIF_T1_BUTTON_OPEN_IN_THE_SCENARIO_TAB_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 13 | `GUIF_T1_BUTTON_CHECK_THE_CANDIDATE_COUNT_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 14 | `GUIF_T1_BUTTON_USE_PYTHON_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t1_library | 15 | `GUIF_T1_BUTTON_RUNNER_SETTINGS_STEP_3_INVARIANT` | empty-selection guidance; manifest counts; details; sending selected scenario |
| t2_authoring | 6 | `GUIF_T2_BUTTON_NEW_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 7 | `GUIF_T2_BUTTON_OPEN_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 8 | `GUIF_T2_BUTTON_AUTO_SEARCH_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 9 | `GUIF_T2_BUTTON_SAVE_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 10 | `GUIF_T2_BUTTON_SAVE_AS_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 11 | `GUIF_T2_BUTTON_VALIDATE_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 12 | `GUIF_T2_LIST_SHEETS_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 13 | `GUIF_T2_BUTTON_ADD_SHEET_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 14 | `GUIF_T2_BUTTON_RENAME_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 15 | `GUIF_T2_BUTTON_REMOVE_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 16 | `GUIF_T2_BUTTON_INSERT_ROW_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 17 | `GUIF_T2_BUTTON_REMOVE_ROW_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 18 | `GUIF_T2_TABLE_CELLS_OF_SHEET_FW_SEQ_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 19 | `GUIF_T2_LIST_CELL_TEMPLATES_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 20 | `GUIF_T2_BUTTON_INSERT_INTO_THE_SELECTED_CELL_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 21 | `GUIF_T2_SUB_TAB_2_1_CONTROL_SHEETS_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 22 | `GUIF_T2_SUB_TAB_2_2_VALIDATION_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 23 | `GUIF_T2_SUB_TAB_2_3_OPERAND_DEPENDENCIES_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 24 | `GUIF_T2_SUB_TAB_2_4_DEPENDENCY_GRAPH_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 25 | `GUIF_T2_BUTTON_LOAD_FROM_THE_DOCUMENT_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 26 | `GUIF_T2_BUTTON_APPLY_TO_THE_DOCUMENT_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 27 | `GUIF_T2_BUTTON_ADD_VERDICT_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 28 | `GUIF_T2_BUTTON_REMOVE_VERDICT_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 29 | `GUIF_T2_TEXT_AREA_FW_RUNMEFIRSTONCE_CODE_RUN_ONCE_BEFORE_THE_TEST_CASES_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 30 | `GUIF_T2_TEXT_AREA_FW_ARGUMENTS_ARGUMENTS_ONE_PER_LINE_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 31 | `GUIF_T2_TABLE_FW_CUSTOM_VAR_VERDICT_CODES_AND_THEIR_NAMES_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 32 | `GUIF_T2_TEXT_AREA_CHECK_RESULTS_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 33 | `GUIF_T2_TABLE_OPERAND_DEPENDENCIES_EACH_JOINER_S_OPERANDS_AND_THEIR_LIFETIMES_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 34 | `GUIF_T2_BUTTON_COPY_DOT_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 35 | `GUIF_T2_BUTTON_SAVE_DOT_OR_JSON_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t2_authoring | 36 | `GUIF_T2_TEXT_AREA_PROBLEMS_IN_THE_DIAGRAM_INVARIANT` | sheet/cell mutations; duplicate rejection; control-sheet round trip; all four save formats; graph export |
| t3_runner | 6 | `GUIF_T3_TEXT_AREA_CHECK_RESULTS_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 7 | `GUIF_T3_TABLE_FW_SEQ_PLAN_EACH_SHEET_S_ROLE_VALUES_AND_DIRECTIVES_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 8 | `GUIF_T3_TEXT_FIELD_SCENARIO_FILE_XLSX_JSON_YAML_OR_TOML_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 9 | `GUIF_T3_BUTTON_BROWSE_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 10 | `GUIF_T3_BUTTON_AUTO_SEARCH_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 11 | `GUIF_T3_BUTTON_USE_THE_EXAMPLE_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 12 | `GUIF_T3_BUTTON_VALIDATE_AND_PLAN_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 13 | `GUIF_T3_BUTTON_USE_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 14 | `GUIF_T3_CHOICE_RUNNER_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 15 | `GUIF_T3_TEXT_FIELD_ADD_ON_AND_DEPENDENCY_PATHS_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 16 | `GUIF_T3_TEXT_FIELD_PYTHON_INTERPRETER_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 17 | `GUIF_T3_TEXT_FIELD_PYTHON_IMPORT_DIRECTORIES_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 18 | `GUIF_T3_TEXT_FIELD_PYTHON_WORKING_DIRECTORY_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 19 | `GUIF_T3_TEXT_FIELD_PYTHON_ENVIRONMENT_VARIABLES_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 20 | `GUIF_T3_TEXT_FIELD_PYTHON_SANDBOX_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 21 | `GUIF_T3_BUTTON_ADD_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 22 | `GUIF_T3_BUTTON_BROWSE_2_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 23 | `GUIF_T3_BUTTON_ADD_2_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 24 | `GUIF_T3_BUTTON_BROWSE_3_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 25 | `GUIF_T3_BUTTON_AUTO_SEARCH_2_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 26 | `GUIF_T3_BUTTON_AUTO_SEARCH_3_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 27 | `GUIF_T3_BUTTON_AUTO_SEARCH_4_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 28 | `GUIF_T3_BUTTON_AUTO_SEARCH_5_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t3_runner | 29 | `GUIF_T3_BUTTON_APPLY_RUNNER_SETTINGS_INVARIANT` | Python controls enabled iff Python; invalid settings refusal; wrong-file guidance; valid planning |
| t4_graph | 6 | `GUIF_T4_BUTTON_OPEN_STEP_3_SCENARIO_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 7 | `GUIF_T4_BUTTON_IMPORT_CONSTRAINTS_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 8 | `GUIF_T4_BUTTON_AUTO_SEARCH_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 9 | `GUIF_T4_BUTTON_EXPORT_JSON_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 10 | `GUIF_T4_BUTTON_SAVE_FOR_SCENARIO_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 11 | `GUIF_T4_BUTTON_FACE_2_SIEVE_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 12 | `GUIF_T4_BUTTON_UNDO_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 13 | `GUIF_T4_BUTTON_REDO_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 14 | `GUIF_T4_BUTTON_VALIDATE_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 15 | `GUIF_T4_BUTTON_CHECK_IMPACT_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 16 | `GUIF_T4_SPINNER_UNNAMED_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 17 | `GUIF_T4_BUTTON_CANCEL_WORK_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 18 | `GUIF_T4_SUB_TAB_SIEVE_GRAPH_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 19 | `GUIF_T4_SUB_TAB_BOND_MATRIX_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 20 | `GUIF_T4_SUB_TAB_PARAMETERS_ORDERS_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 21 | `GUIF_T4_SUB_TAB_ADVANCED_JSON_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 22 | `GUIF_T4_TEXT_FIELD_UNNAMED_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 23 | `GUIF_T4_BUTTON_FIT_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 24 | `GUIF_T4_BUTTON_PLUS_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 25 | `GUIF_T4_BUTTON_MINUS_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 26 | `GUIF_T4_BUTTON_ARRANGE_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 27 | `GUIF_T4_CHOICE_UNNAMED_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 28 | `GUIF_T4_BUTTON_GROUP_SELECTED_VALUES_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 29 | `GUIF_T4_CHOICE_UNNAMED_2_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 30 | `GUIF_T4_TEXT_FIELD_UNNAMED_2_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 31 | `GUIF_T4_CHOICE_UNNAMED_3_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 32 | `GUIF_T4_TEXT_FIELD_UNNAMED_3_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 33 | `GUIF_T4_BUTTON_ADD_PAIR_AT_CELL_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 34 | `GUIF_T4_BUTTON_EDIT_CELL_RULE_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_graph | 35 | `GUIF_T4_TABLE_UNNAMED_INVARIANT` | pair rule mutation; undo/redo; invalid JSON preserves document; saved sidecar and layout |
| t4_data | 8 | `GUIF_T4_SUB_TAB_PARAMETERS_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 9 | `GUIF_T4_SUB_TAB_VALUE_ORDERS_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 10 | `GUIF_T4_TABLE_UNNAMED_2_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 11 | `GUIF_T4_BUTTON_ADD_ATTRIBUTE_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 12 | `GUIF_T4_BUTTON_REMOVE_ATTRIBUTE_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 13 | `GUIF_T4_CHOICE_SHEET_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 14 | `GUIF_T4_CHOICE_ORDER_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 15 | `GUIF_T4_LIST_UNNAMED_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 16 | `GUIF_T4_CHOICE_UNNAMED_4_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 17 | `GUIF_T4_BUTTON_ADD_VALUE_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 18 | `GUIF_T4_BUTTON_UP_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 19 | `GUIF_T4_BUTTON_DOWN_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 20 | `GUIF_T4_BUTTON_REMOVE_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 21 | `GUIF_T4_BUTTON_APPLY_DATA_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 22 | `GUIF_T4_BUTTON_DISCARD_DATA_DRAFT_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 23 | `GUIF_T4_TEXT_AREA_SIEVE_EXECUTION_JSON_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 24 | `GUIF_T4_BUTTON_APPLY_JSON_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_data | 25 | `GUIF_T4_BUTTON_DISCARD_JSON_DRAFT_INVARIANT` | draft owns editor; attempted switch refuses; apply/discard clears ownership; optional undo/validate does not silently lose draft |
| t4_rules | 6 | `GUIF_T4_TEXT_FIELD_UNNAMED_4_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 7 | `GUIF_T4_CHOICE_UNNAMED_5_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 8 | `GUIF_T4_BUTTON_NEW_RULE_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 9 | `GUIF_T4_BUTTON_DUPLICATE_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 10 | `GUIF_T4_BUTTON_DELETE_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 11 | `GUIF_T4_BUTTON_UP_2_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 12 | `GUIF_T4_BUTTON_DOWN_2_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 13 | `GUIF_T4_BUTTON_FIND_ON_GRAPH_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 14 | `GUIF_T4_TABLE_UNNAMED_3_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 15 | `GUIF_T4_TEXT_FIELD_RULE_ID_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 16 | `GUIF_T4_TEXT_FIELD_DESCRIPTION_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 17 | `GUIF_T4_CHOICE_UNNAMED_6_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 18 | `GUIF_T4_CHECK_BOX_ADJACENT_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 19 | `GUIF_T4_CHECK_BOX_WITHIN_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 20 | `GUIF_T4_SPINNER_UNNAMED_2_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 21 | `GUIF_T4_SUB_TAB_RULE_BODY_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 22 | `GUIF_T4_SUB_TAB_ONLY_WHEN_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 23 | `GUIF_T4_BUTTON_CONDITION_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 24 | `GUIF_T4_BUTTON_ALL_OF_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 25 | `GUIF_T4_BUTTON_ANY_OF_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 26 | `GUIF_T4_BUTTON_NOT_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 27 | `GUIF_T4_BUTTON_DELETE_2_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 28 | `GUIF_T4_BUTTON_UP_3_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 29 | `GUIF_T4_BUTTON_DOWN_3_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 30 | `GUIF_T4_TREE_PARTS_OF_THE_CONDITION_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 31 | `GUIF_T4_CHOICE_SHEET_2_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 32 | `GUIF_T4_CHOICE_TEST_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 33 | `GUIF_T4_CHOICE_VALUE_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 34 | `GUIF_T4_LIST_VALUES_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 35 | `GUIF_T4_CHOICE_PRESENT_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 36 | `GUIF_T4_CHOICE_OTHER_COLUMN_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 37 | `GUIF_T4_SPINNER_COUNT_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 38 | `GUIF_T4_TEXT_FIELD_JSON_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 39 | `GUIF_T4_BUTTON_APPLY_JSON_2_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 40 | `GUIF_T4_BUTTON_APPLY_RULE_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 41 | `GUIF_T4_BUTTON_DISCARD_DRAFT_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t4_rules | 42 | `GUIF_T4_TEXT_AREA_UNNAMED_INVARIANT` | rule identity/body apply; invalid JSON cannot destroy last good rules; discard/undo recovery |
| t5_candidates | 8 | `GUIF_T5_TEXT_FIELD_LOOK_AT_MOST_AT_TEST_CASES_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 9 | `GUIF_T5_SPINNER_ROWS_PER_PAGE_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 10 | `GUIF_T5_CHECK_BOX_EXECUTABLE_ONLY_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 11 | `GUIF_T5_BUTTON_PREVIEW_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 12 | `GUIF_T5_BUTTON_NEXT_PAGE_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 13 | `GUIF_T5_BUTTON_CANCEL_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 14 | `GUIF_T5_TABLE_PLAN_ESTIMATES_EACH_COUNT_WITH_ITS_CONFIDENCE_AND_FORMULA_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 15 | `GUIF_T5_TABLE_RULE_IMPACT_WHAT_EACH_RULE_READS_AND_THE_CANDIDATES_IT_REMOVES_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 16 | `GUIF_T5_TABLE_CANDIDATE_PAGE_EACH_CANDIDATE_S_IDS_AND_FATE_UNDER_THE_RULES_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t5_candidates | 17 | `GUIF_T5_TEXT_AREA_CODE_OF_THE_SELECTED_TEST_CASE_INVARIANT` | scan/page boundaries; cancellation; selected source; page progression and executable filtering |
| t6_run | 7 | `GUIF_T6_TEXT_AREA_RUN_REPORT_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 8 | `GUIF_T6_BUTTON_RUN_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 9 | `GUIF_T6_BUTTON_PAUSE_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 10 | `GUIF_T6_BUTTON_RESUME_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 11 | `GUIF_T6_BUTTON_CANCEL_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 12 | `GUIF_T6_CHOICE_SAVE_AS_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 13 | `GUIF_T6_BUTTON_EXPORT_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 14 | `GUIF_T6_BUTTON_USE_PYTHON_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 15 | `GUIF_T6_BUTTON_RUNNER_SETTINGS_STEP_3_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_run | 16 | `GUIF_X_RUN_ROUNDTRIP_INVARIANT` | state transitions; cancellation terminality; all export formats; metrics export → analysis sample count |
| t6_sequences | 8 | `GUIF_T6_TEXT_AREA_RUN_REPORT_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 9 | `GUIF_T6_BUTTON_RUN_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 10 | `GUIF_T6_BUTTON_PAUSE_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 11 | `GUIF_T6_BUTTON_RESUME_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 12 | `GUIF_T6_BUTTON_CANCEL_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 13 | `GUIF_T6_CHOICE_SAVE_AS_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 14 | `GUIF_T6_BUTTON_EXPORT_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 15 | `GUIF_T6_BUTTON_USE_PYTHON_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 16 | `GUIF_T6_BUTTON_RUNNER_SETTINGS_STEP_3_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t6_sequences | 17 | `GUIF_X_RUN_SEQUENCE_INVARIANT` | ordered pause/resume/cancel transitions, repeats, terminal cancellation and optional interruption |
| t7_campaign | 7 | `GUIF_T7_BUTTON_RUN_CAMPAIGN_INVARIANT` | iterations/lineage; pause/resume/cancel; optional sudden cancellation |
| t7_campaign | 8 | `GUIF_T7_BUTTON_PAUSE_INVARIANT` | iterations/lineage; pause/resume/cancel; optional sudden cancellation |
| t7_campaign | 9 | `GUIF_T7_BUTTON_RESUME_INVARIANT` | iterations/lineage; pause/resume/cancel; optional sudden cancellation |
| t7_campaign | 10 | `GUIF_T7_BUTTON_CANCEL_INVARIANT` | iterations/lineage; pause/resume/cancel; optional sudden cancellation |
| t7_campaign | 11 | `GUIF_T7_TABLE_CAMPAIGN_LINEAGE_EACH_ITERATION_S_RUN_COUNTS_WINNERS_AND_FRONT_INVARIANT` | iterations/lineage; pause/resume/cancel; optional sudden cancellation |
| t7_campaign | 12 | `GUIF_T7_TEXT_AREA_CAMPAIGN_REPORT_INVARIANT` | iterations/lineage; pause/resume/cancel; optional sudden cancellation |
| t8_analysis | 6 | `GUIF_T8_TEXT_FIELD_METRICS_FILE_KV_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 7 | `GUIF_T8_BUTTON_BROWSE_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 8 | `GUIF_T8_BUTTON_AUTO_SEARCH_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 9 | `GUIF_T8_BUTTON_USE_THE_EXAMPLE_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 10 | `GUIF_T8_BUTTON_USE_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 11 | `GUIF_T8_CHOICE_CODE_COVERAGE_OPTIONAL_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 12 | `GUIF_T8_TEXT_FIELD_REPORT_FILE_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 13 | `GUIF_T8_BUTTON_BROWSE_2_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 14 | `GUIF_T8_BUTTON_AUTO_SEARCH_2_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 15 | `GUIF_T8_BUTTON_ANALYZE_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t8_analysis | 16 | `GUIF_T8_TEXT_AREA_ANALYSIS_REPORT_INVARIANT` | wrong-content refusal; unverdicted count; coverage enablement; repeated analyze |
| t9_scorer | 7 | `GUIF_T9_TEXT_FIELD_TEXT_FILE_TO_SCORE_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 8 | `GUIF_T9_BUTTON_BROWSE_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 9 | `GUIF_T9_BUTTON_AUTO_SEARCH_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 10 | `GUIF_T9_BUTTON_USE_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 11 | `GUIF_T9_SPINNER_LINES_PER_LEARNED_PATTERN_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 12 | `GUIF_T9_SPINNER_PATTERNS_KEPT_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 13 | `GUIF_T9_SPINNER_WORTH_IMPROVING_FROM_SCORE_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 14 | `GUIF_T9_SPINNER_WATCH_FROM_SCORE_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 15 | `GUIF_T9_BUTTON_LOAD_RULES_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 16 | `GUIF_T9_BUTTON_AUTO_PLAN_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 17 | `GUIF_T9_BUTTON_SCORE_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 18 | `GUIF_T9_BUTTON_EXPORT_CSV_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 19 | `GUIF_T9_BUTTON_OPTIMIZATION_CHARTS_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 20 | `GUIF_T9_BUTTON_AUTO_SEARCH_2_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 21 | `GUIF_T9_TEXT_AREA_YOUR_SCORING_RULES_AS_JSON_REQUIRED_TOKENS_TOKEN_WEIGHTS_NUMERIC_RANGES__INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 22 | `GUIF_T9_TEXT_FIELD_SHOW_LINES_CONTAINING_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 23 | `GUIF_T9_TABLE_SCORED_LINES_EACH_LINE_S_SCORE_DECISION_AND_FEATURES_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 24 | `GUIF_T9_TEXT_AREA_WHY_THIS_SCORE_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| t9_scorer | 25 | `GUIF_T9_TEXT_AREA_SUMMARY_OF_ALL_SCORES_INVARIANT` | malformed rules refused; score count; case-insensitive filter; CSV; offscreen charts |
| x_dialogs | 6 | `GUIF_X_DIALOG_SCENARIO_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 7 | `GUIF_X_DIALOG_DOCUMENT_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 8 | `GUIF_X_DIALOG_DOCUMENT_SAVE_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 9 | `GUIF_X_DIALOG_CONSTRAINTS_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 10 | `GUIF_X_DIALOG_CONSTRAINTS_SAVE_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 11 | `GUIF_X_DIALOG_GRAPH_SAVE_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 12 | `GUIF_X_DIALOG_EXPORT_REPORT_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 13 | `GUIF_X_DIALOG_EXPORT_RESULTS_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 14 | `GUIF_X_DIALOG_EXPORT_SAMPLES_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 15 | `GUIF_X_DIALOG_EXPORT_METRICS_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 16 | `GUIF_X_DIALOG_EXPORT_CHECKPOINT_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 17 | `GUIF_X_DIALOG_RESULTS_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 18 | `GUIF_X_DIALOG_COVERAGE_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 19 | `GUIF_X_DIALOG_TEXT_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 20 | `GUIF_X_DIALOG_RULES_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 21 | `GUIF_X_DIALOG_SCORES_SAVE_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 22 | `GUIF_X_DIALOG_LIBRARY_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 23 | `GUIF_X_DIALOG_ADDON_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 24 | `GUIF_X_DIALOG_PROGRAM_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 25 | `GUIF_X_DIALOG_IMPORTS_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 26 | `GUIF_X_DIALOG_WORKDIR_OPEN_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_dialogs | 27 | `GUIF_X_TRAIL_INVARIANT` | purpose-specific title/accessory/example/selection and isolated trail |
| x_search_data | 7 | `GUIF_X_SEARCH_MATCH_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_data | 8 | `GUIF_X_SEARCH_ROOTS_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_data | 9 | `GUIF_X_SEARCH_BUDGET_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_data | 10 | `GUIF_X_SEARCH_FILTER_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_data | 11 | `GUIF_X_SEARCH_SELECTION_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_tools | 7 | `GUIF_X_SEARCH_MATCH_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_tools | 8 | `GUIF_X_SEARCH_ROOTS_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_tools | 9 | `GUIF_X_SEARCH_BUDGET_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_tools | 10 | `GUIF_X_SEARCH_FILTER_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_search_tools | 11 | `GUIF_X_SEARCH_SELECTION_INVARIANT` | content classification; duplicate roots; hidden skips; budget and stop; filter/select |
| x_shell | 6 | `GUIF_T0_TOGGLE_HINTS_ON_F1_INVARIANT` | nine tabs; F1/hints toggle; numeric/boolean/text zebra; offscreen rendering; no windows or leaks |
| x_shell | 7 | `GUIF_X_TABS_INVARIANT` | nine tabs; F1/hints toggle; numeric/boolean/text zebra; offscreen rendering; no windows or leaks |
| x_shell | 8 | `GUIF_X_HINTS_INVARIANT` | nine tabs; F1/hints toggle; numeric/boolean/text zebra; offscreen rendering; no windows or leaks |
| x_shell | 9 | `GUIF_X_ZEBRA_INVARIANT` | nine tabs; F1/hints toggle; numeric/boolean/text zebra; offscreen rendering; no windows or leaks |
| x_shell | 10 | `GUIF_X_LIFECYCLE_INVARIANT` | nine tabs; F1/hints toggle; numeric/boolean/text zebra; offscreen rendering; no windows or leaks |
