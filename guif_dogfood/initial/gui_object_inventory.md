# GUI object inventory (generated from the live component tree, headless guif, 2026-10-18)

The kinds are text field, text area, button, toggle, check box, choice, spinner, table, list and sub-tab. Look-and-feel
internals (scroll and arrow buttons) are omitted. A shown name in `#…` is a component `setName` id. "—" means the
control has no hint of its own.

| Tab | Kind | Shown name | What it is for (hint) |
|---|---|---|---|
| 0 · window | toggle | Hints on (F1) | Shows or hides the pop-up hints that explain each part of the desktop, tooltips included. The F1 key does t… |
| 1 · Library | list | Groups of examples | The groups of example scenarios. Pick one to list its examples. |
| 1 · Library | table | Scenarios of the selected suite: documented counts and sources | The examples of the chosen group, with the number of test cases each one should make. Select one to read ab… |
| 1 · Library | text area | About the selected example | About the selected example: its name, what it checks, the counts it should give, and where its file is. Fil… |
| 1 · Library | button | Open the example library | Open the library of verified examples found next to this checkout. |
| 1 · Library | button | Open a library... | Choose a library: the generator_trunk folder of a QA edition checkout, or the verified_examples.json file i… |
| 1 · Library | button | Auto-search… | Search this checkout, the checkouts next to it and your folders for libraries of examples. |
| 1 · Library | button | Open in the Scenario tab | Use the selected example: it becomes the scenario of step 3, and the desktop switches there. |
| 1 · Library | button | Check the candidate count | Count the test cases of the selected example and compare the number with the one the library expects. |
| 1 · Library | button | Use Python | Choose the Python runner with the settings of step 3. The installed Python add-on must be available. Applyi… |
| 1 · Library | button | Runner settings (step 3) | Go to step 3 to choose the runner, its add-on paths, and the Python interpreter, import directories, workin… |
| 2 · Authoring | button | New | Start a new, empty scenario. |
| 2 · Authoring | button | Open... | Open a scenario file to change it. |
| 2 · Authoring | button | Auto-search… | Search the folders where scenario files usually are, then open one here to change it. |
| 2 · Authoring | button | Save | Save the scenario to its file. It also becomes the scenario of step 3. |
| 2 · Authoring | button | Save as... | Save the scenario to a new file. It also becomes the scenario of step 3. |
| 2 · Authoring | button | Validate | Check the scenario for mistakes without saving it. The result appears in tab 2.2 Validation. |
| 2 · Authoring | list | Sheets | The scenario's sheets. Each sheet is a list of values; the sheet FW_Seq says how they are combined. Pick a … |
| 2 · Authoring | button | Add sheet | Add a new sheet (a list of values) with a name you choose. |
| 2 · Authoring | button | Rename | Give the selected sheet a new name. |
| 2 · Authoring | button | Remove | Delete the selected sheet with all its values. |
| 2 · Authoring | button | Insert row | Add an empty row above the selected row. |
| 2 · Authoring | button | Remove row | Delete the selected row. |
| 2 · Authoring | table | Cells of sheet FW_Seq | The values of the selected sheet, one per row. Click a cell and type, or fill it from the templates on the … |
| 2 · Authoring | list | Cell templates | Ready-made cell contents that say how a sheet's values are picked, ordered or joined. Rest the pointer on o… |
| 2 · Authoring | button | Insert into the selected cell | Put the template chosen in the list into the selected cell of the sheet. |
| 2 · Authoring | sub-tab | 2.1 · Control sheets | Step 2.1: the three special sheets: code that runs once before the test cases, the arguments every test cas… |
| 2 · Authoring | sub-tab | 2.2 · Validation | Step 2.2: what the last check of the scenario found. |
| 2 · Authoring | sub-tab | 2.3 · Operand dependencies | Step 2.3: how the sheets feed each other where they are joined. |
| 2 · Authoring | sub-tab | 2.4 · Dependency graph | Step 2.4: a diagram of how the sheets depend on each other. |
| 2 · Authoring | button | Load from the document | Fill these three boxes from the scenario's control sheets. |
| 2 · Authoring | button | Apply to the document | Write these three boxes back into the scenario's control sheets. |
| 2 · Authoring | button | Add verdict | Add a row for one of your own result codes and its name. |
| 2 · Authoring | button | Remove verdict | Delete the selected result code. |
| 2 · Authoring | text area | FW_RunMeFirstOnce: code run once before the test cases | FW_RunMeFirstOnce: Java code that runs once before all test cases, for example to prepare data. Filled from… |
| 2 · Authoring | text area | FW_Arguments: arguments, one per line | FW_Arguments: the arguments every test case gets, one per line. Filled from the scenario when you press Loa… |
| 2 · Authoring | table | FW_CUSTOM_VAR: verdict codes and their names | FW_CUSTOM_VAR: your own result codes, each with a name, used instead of a plain pass or fail. |
| 2 · Authoring | text area | Check results | What the last check of the scenario found: problems to fix, and a summary. Filled after you press Validate. |
| 2 · Authoring | table | Operand dependencies: each joiner's operands and their lifetimes | Each place where two sheets are joined: which sheets it reads, how their rows are paired, and whether those… |
| 2 · Authoring | button | Copy DOT | Copy the diagram as text in the Graphviz DOT format, to paste into a diagram tool. |
| 2 · Authoring | button | Save DOT or JSON... | Save the diagram to a file, as Graphviz DOT or as JSON. |
| 2 · Authoring | text area | Problems in the diagram | Problems found in the diagram, such as a sheet that is used before it is built. Filled after you press Vali… |
| 3 · Scenario | text area | Check results | What the last check found: problems to fix, and a summary of how the scenario is built. Filled after you pr… |
| 3 · Scenario | table | FW_Seq plan: each sheet's role, values and directives | One row per sheet of the scenario: whether its values are always used, optional or only helpers for other s… |
| 3 · Scenario | text field | Scenario file (xlsx, json, yaml or toml) | The scenario file the next steps work on. Filled when you choose a file with Browse... or send an example f… |
| 3 · Scenario | button | Browse... | Choose the scenario file from a folder. |
| 3 · Scenario | button | Auto-search… | Search the folders where scenario files usually are, then pick one from the list it shows. |
| 3 · Scenario | button | Use the example | Put the showcase example scenario that comes with the framework into the box. |
| 3 · Scenario | button | Validate and plan | Check the scenario for mistakes and work out how its sheets are combined. Problems appear above, the plan i… |
| 3 · Scenario | button | Use | Put the suggested file into the box next to it. |
| 3 · Scenario | choice | Runner | Choose what runs the test cases: Java in this JVM, Python through its installed add-on, or a named plugin. … |
| 3 · Scenario | text field | Add-on and dependency paths | Jars or directories, separated by ':'. Filled from the current configuration, by typing paths or with Add..… |
| 3 · Scenario | text field | Python interpreter | The Python executable or its full path. Filled from the current configuration, by typing or with Browse...;… |
| 3 · Scenario | text field | Python import directories | Directories for Python imports, separated by ':'. Filled from the current configuration, by typing or with … |
| 3 · Scenario | text field | Python working directory | Where Python test cases work. Filled from the current configuration, by typing or with Browse...; leave emp… |
| 3 · Scenario | text field | Python environment variables | NAME=VALUE assignments separated by ';'. Filled from the current configuration or by typing. They affect on… |
| 3 · Scenario | text field | Python sandbox | Where each Python test case runs. Filled from the current configuration or by typing: empty runs it directl… |
| 3 · Scenario | button | Add... | Choose a jar or a class folder to add to the add-on and dependency paths. |
| 3 · Scenario | button | Browse... | Choose the Python program that runs the test cases. |
| 3 · Scenario | button | Add... | Choose a folder of Python modules to add to the import directories. |
| 3 · Scenario | button | Browse... | Choose the folder where each Python test case starts. |
| 3 · Scenario | button | Auto-search… | Search this application, this checkout and your folders for runner add-ons, then pick one to add. |
| 3 · Scenario | button | Auto-search… | Search your PATH and your virtual environments for Python interpreters, then pick one of them. |
| 3 · Scenario | button | Auto-search… | Search around the step 3 scenario for folders of Python modules, then pick one to add. |
| 3 · Scenario | button | Auto-search… | List the folders a Python test case can start in, the scenario's own first, then pick one. |
| 3 · Scenario | button | Apply runner settings | Use these settings for the next validation, candidate preview, run or campaign. An already started run keep… |
| 4 · Constraints / Sieve | button | Open step 3 scenario | Load the scenario chosen in step 3 with its rules, to see and edit them here; no file dialog opens. |
| 4 · Constraints / Sieve | button | Import constraints… | Replace the rules shown with the rules of another constraints file; nothing is saved until you press Save f… |
| 4 · Constraints / Sieve | button | Auto-search… | Search the folders where constraints files usually are, then import the rules of one here. |
| 4 · Constraints / Sieve | button | Export JSON… | Save a copy of the rules as a JSON file; runs keep reading the scenario's own rules file. |
| 4 · Constraints / Sieve | button | Save for scenario | Save the rules beside the scenario as the rules file that every run and preview reads. |
| 4 · Constraints / Sieve | button | Face 2 (Sieve) ↗ | Open the Face 2 web editor in your browser; its Submit button saves the rules for this scenario. |
| 4 · Constraints / Sieve | button | Undo | Take back the last change to the rules or the layout. |
| 4 · Constraints / Sieve | button | Redo | Make again the change that Undo took back. |
| 4 · Constraints / Sieve | button | Validate | Check the rules for mistakes without counting test cases. |
| 4 · Constraints / Sieve | button | Check impact | Count how many test cases each rule removes; nothing is run. |
| 4 · Constraints / Sieve | spinner | (unnamed) | The most test cases each counting pass looks at; a smaller number answers faster. |
| 4 · Constraints / Sieve | button | Cancel work | Stop the counting or saving that is still going. |
| 4 · Constraints / Sieve | sub-tab | Sieve graph | — |
| 4 · Constraints / Sieve | sub-tab | Bond matrix | — |
| 4 · Constraints / Sieve | sub-tab | Parameters / orders | — |
| 4 · Constraints / Sieve | sub-tab | Advanced JSON | — |
| 4 · Constraints / Sieve | text field | (unnamed) | — |
| 4 · Constraints / Sieve | button | Fit | — |
| 4 · Constraints / Sieve | button | + | — |
| 4 · Constraints / Sieve | button | − | — |
| 4 · Constraints / Sieve | button | Arrange | — |
| 4 · Constraints / Sieve | choice | (unnamed) | — |
| 4 · Constraints / Sieve | button | Group selected values | — |
| 4 · Constraints / Sieve | choice | (unnamed) | — |
| 4 · Constraints / Sieve | text field | (unnamed) | — |
| 4 · Constraints / Sieve | choice | (unnamed) | — |
| 4 · Constraints / Sieve | text field | (unnamed) | — |
| 4 · Constraints / Sieve | button | Add pair at cell | — |
| 4 · Constraints / Sieve | button | Edit cell rule | — |
| 4 · Constraints / Sieve | table | (unnamed) | — |
| 4 · Constraints / Sieve | sub-tab | Parameters | — |
| 4 · Constraints / Sieve | sub-tab | Value orders | — |
| 4 · Constraints / Sieve | table | (unnamed) | — |
| 4 · Constraints / Sieve | button | Add attribute | — |
| 4 · Constraints / Sieve | button | Remove attribute | — |
| 4 · Constraints / Sieve | choice | Sheet | — |
| 4 · Constraints / Sieve | choice | Order | — |
| 4 · Constraints / Sieve | list | (unnamed) | — |
| 4 · Constraints / Sieve | choice | (unnamed) | — |
| 4 · Constraints / Sieve | button | Add value | — |
| 4 · Constraints / Sieve | button | ↑ | — |
| 4 · Constraints / Sieve | button | ↓ | — |
| 4 · Constraints / Sieve | button | Remove | — |
| 4 · Constraints / Sieve | button | Apply data | — |
| 4 · Constraints / Sieve | button | Discard data draft | — |
| 4 · Constraints / Sieve | text area | #Sieve execution JSON | — |
| 4 · Constraints / Sieve | button | Apply JSON | — |
| 4 · Constraints / Sieve | button | Discard JSON draft | — |
| 4 · Constraints / Sieve | text field | (unnamed) | — |
| 4 · Constraints / Sieve | choice | (unnamed) | — |
| 4 · Constraints / Sieve | button | New rule | — |
| 4 · Constraints / Sieve | button | Duplicate | — |
| 4 · Constraints / Sieve | button | Delete | — |
| 4 · Constraints / Sieve | button | ↑ | — |
| 4 · Constraints / Sieve | button | ↓ | — |
| 4 · Constraints / Sieve | button | Find on graph | — |
| 4 · Constraints / Sieve | table | (unnamed) | — |
| 4 · Constraints / Sieve | text field | Rule ID | — |
| 4 · Constraints / Sieve | text field | Description | — |
| 4 · Constraints / Sieve | choice | (unnamed) | — |
| 4 · Constraints / Sieve | check box | Adjacent | — |
| 4 · Constraints / Sieve | check box | Within | — |
| 4 · Constraints / Sieve | spinner | (unnamed) | — |
| 4 · Constraints / Sieve | sub-tab | Rule body | — |
| 4 · Constraints / Sieve | sub-tab | Only when | — |
| 4 · Constraints / Sieve | button | + condition | Add a simple test next to the selected one. Inside a group it joins that group; otherwise both are grouped … |
| 4 · Constraints / Sieve | button | all of | Make the selected group "all of" (every part must hold), or put the selected test into a new "all of" group. |
| 4 · Constraints / Sieve | button | any of | Make the selected group "any of" (one part is enough), or put the selected test into a new "any of" group. |
| 4 · Constraints / Sieve | button | not | Turn the selected part into its opposite, or remove a "not". |
| 4 · Constraints / Sieve | button | Delete | Delete the selected part. |
| 4 · Constraints / Sieve | button | Up | Move the selected part up. |
| 4 · Constraints / Sieve | button | Down | Move the selected part down. |
| 4 · Constraints / Sieve | tree | Parts of the condition | The condition as a tree: "all of" (every part must hold), "any of" (one part is enough), "not" (the opposit… |
| 4 · Constraints / Sieve | choice | sheet | The sheet the test looks at. |
| 4 · Constraints / Sieve | choice | test | How the test compares: is, is not, any of, none of, contains, a number comparison, a count, whether the she… |
| 4 · Constraints / Sieve | choice | value | The value the sheet is compared with. |
| 4 · Constraints / Sieve | list | Values | The values for an "any of" or "none of" test; select several. |
| 4 · Constraints / Sieve | choice | present | Yes or no, for a test that asks whether the sheet takes part. |
| 4 · Constraints / Sieve | choice | other column | The other column, for a test that compares two columns. |
| 4 · Constraints / Sieve | spinner | count | The number, for a test that counts values. |
| 4 · Constraints / Sieve | text field | JSON | The selected part as JSON text. Filled when you select a part of the condition; type to change it, then pre… |
| 4 · Constraints / Sieve | button | Apply JSON | Use the JSON text for the selected part. |
| 4 · Constraints / Sieve | button | Apply rule | — |
| 4 · Constraints / Sieve | button | Discard draft | — |
| 4 · Constraints / Sieve | text area | (unnamed) | — |
| 5 · Candidates | text field | Look at most at (test cases) | The most test cases the preview looks at. Filled with 1000000 at the start; type a smaller number for a fas… |
| 5 · Candidates | spinner | Rows per page | How many test cases one page shows. |
| 5 · Candidates | check box | Executable only | Show only the test cases that will really run, not the ones a rule removes. |
| 5 · Candidates | button | Preview | Count the test cases of the scenario of step 3 and show the first page. |
| 5 · Candidates | button | Next page | Show the next page of test cases. |
| 5 · Candidates | button | Cancel | Stop the preview while it is still counting. |
| 5 · Candidates | table | Plan estimates: each count with its confidence and formula | How many test cases each stage makes, whether the number is exact or an estimate, and how it is worked out. |
| 5 · Candidates | table | Rule impact: what each rule reads and the candidates it removes | Each rule of step 4: the sheets it looks at and how many test cases it removes. |
| 5 · Candidates | table | Candidate page: each candidate's ids and fate under the rules | The test cases of this page: their number, their names, and whether they run or a rule removes them. Select… |
| 5 · Candidates | text area | Code of the selected test case | The code of the selected test case, exactly as it will run. Filled when you select a test case in the table… |
| 6 · Run | text area | Run report | Progress and results of the run: how many test cases passed, failed or could not run, and their result code… |
| 6 · Run | button | Run | Run all test cases of the scenario of step 3. |
| 6 · Run | button | Pause | Pause the run; test cases already started finish first. |
| 6 · Run | button | Resume | Continue a paused run. |
| 6 · Run | button | Cancel | Stop the run. The results so far are kept. |
| 6 · Run | choice | Save as | What to save: a summary report, every result row, a table of measurements (CSV), the metrics that step 8 an… |
| 6 · Run | button | Export... | Save the chosen kind of results to a file. |
| 6 · Run | button | Use Python | Choose the Python runner with the settings of step 3. The installed Python add-on must be available. Applyi… |
| 6 · Run | button | Runner settings (step 3) | Go to step 3 to choose the runner, its add-on paths, and the Python interpreter, import directories, workin… |
| 7 · Campaign | button | Run campaign | Start a campaign: several runs of the scenario of step 3, each steered by the best results of the one before. |
| 7 · Campaign | button | Pause | Pause the campaign after the current step. |
| 7 · Campaign | button | Resume | Continue a paused campaign. |
| 7 · Campaign | button | Cancel | Stop the campaign. The rounds so far are kept. |
| 7 · Campaign | table | Campaign lineage: each iteration's run, counts, winners and front | One row per round: its run, how many test cases it made and ran, its best results, and whether they have st… |
| 7 · Campaign | text area | Campaign report | What the campaign reports while it runs. Filled while a campaign runs (press Run campaign). |
| 8 · Results analysis | text field | Metrics file (.kv) | The metrics file to study: one line per test case of key=value measurements ending with its verdict FW_VAR.… |
| 8 · Results analysis | button | Browse... | Choose the metrics file to study. |
| 8 · Results analysis | button | Auto-search… | Search the folders where runs save metrics files, then pick one from the list it shows. |
| 8 · Results analysis | button | Use the example | Put an example metrics file from the library of examples into the box. |
| 8 · Results analysis | button | Use | Put the suggested file into the box next to it. |
| 8 · Results analysis | choice | Code coverage (optional) | The kind of code-coverage report given below, or none. |
| 8 · Results analysis | text field | report file | Optional: a code-coverage report to read with the results. Filled by you: type its path or choose it with B… |
| 8 · Results analysis | button | Browse... | Choose the code-coverage report your coverage tool wrote. |
| 8 · Results analysis | button | Auto-search… | Search the folders where reports of the chosen coverage kind usually are, then pick one from the list. |
| 8 · Results analysis | button | Analyze | Study the metrics file: measurements, the best combinations, and code coverage when a report is given. |
| 8 · Results analysis | text area | Analysis report | The analysis: measurements per setting, the best combinations, and how much of the code the tests reached. … |
| 9 · Text scorer | text field | Text file to score | The text file to score; every line is scored on its own. Filled when you choose a file with Browse...; you … |
| 9 · Text scorer | button | Browse... | Choose the text file. |
| 9 · Text scorer | button | Auto-search… | Search the folders where logs and other texts usually are, then pick one to score. |
| 9 · Text scorer | button | Use | Put the suggested file into the box next to it. |
| 9 · Text scorer | spinner | Lines per learned pattern | How many lines must share a pattern before the scorer learns it. |
| 9 · Text scorer | spinner | Patterns kept | How many learned patterns are kept. |
| 9 · Text scorer | spinner | Worth improving from score | Lines scoring at least this much are marked as worth improving. |
| 9 · Text scorer | spinner | Watch from score | Lines scoring at least this much are marked to watch. |
| 9 · Text scorer | button | Load rules... | Load scoring rules from a JSON file. |
| 9 · Text scorer | button | Auto-plan | Suggest the two thresholds from the scores. |
| 9 · Text scorer | button | Score | Give every line of the text a score. |
| 9 · Text scorer | button | Export CSV... | Save the scored lines as a table (CSV) for a spreadsheet. |
| 9 · Text scorer | button | Optimization charts | Open a window with charts of the scores. |
| 9 · Text scorer | button | Auto-search… | Search the folders where scoring rules files usually are, then load one into the box. |
| 9 · Text scorer | text area | Your scoring rules as JSON (required_tokens, token_weights, numeric_ranges, optimizations, ...) | Your scoring rules as JSON: words that must appear, how much each word weighs, allowed number ranges, and w… |
| 9 · Text scorer | text field | Show lines containing | Show only the lines that contain this text; empty shows every line. Filled by you: type the text to look for. |
| 9 · Text scorer | table | Scored lines: each line's score, decision and features | Every line with its score, the decision it led to, and the features that made the score. Select a line to s… |
| 9 · Text scorer | text area | Why this score | Why the selected line got its score. Filled when you select a line in the table above. |
| 9 · Text scorer | text area | Summary of all scores | A summary of all scores. Filled after you press Score. |
