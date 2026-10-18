# Task for Codex: dog-food Combinatorics-Framework-JVM to test its own GUI (`guif`)

> Prepared for you by Claude on 2026-10-18. Everything below was read from the code or **verified by running it** on
> this machine (marked ✅). Do **not** re-explore the codebase: this brief is your map. Open a source file only to
> confirm one fact that a run contradicts, and then use `grep -n` plus `sed -n 'a,bp'`, never whole files.

---

## 1. Your role and mission

You are a senior **SDET / QA engineer**. Test the **whole FlatLaf desktop `guif`** (all 9 tabs, every field, every
button, the dialogs' logic and the cross-cutting behaviour) **by using Combinatorics-Framework-JVM itself as the test
engine**: dog-fooding. Concretely:

- Model the GUI's input space as Framework **scenarios**: factor sheets, levels, `FW_Seq` directives and constraints.
- Let the Framework **generate** the test cases with t-wise covering selection.
- **Run** them with the Framework's own runner (`generated-java`). Each candidate drives a headless `guif` Desktop.
- **Judge** them with per-object **watchers**, each owning a dedicated verdict code that the Framework names through
  `FW_CUSTOM_VAR`.
- **Analyze** the results with the Framework's own tabs and commands: the run summary, the step 6 export, step 8 analysis
  and, if useful, a step 7 campaign.

Be smart rather than exhaustive. Use risk-based prioritization, pairwise as the floor, t=3 where interactions are risky,
constraints to keep only meaningful combinations, and negative and boundary values.

## 2. Token policy (the user's rule)

**Test design: spend tokens. Test execution: economize your own tokens.**

During design, think deeply and write rich artifacts:
- the object model, risk analysis, factors and levels, constraints, oracles, the watcher catalog and the code map;
- a self-review pass that looks for naming interference (§11) and checks the Byte budget (§9).

During execution, follow these rules:
- Run long jobs in the background with logs written to files.
- Read only summaries. `grep -E "state|outcomes|verdicts"` on run output; `head`/`cut -c1-200` on anything else.
- Never print checkpoints, whole CSV/XML exports, whole logs, or `.xlsx`.
- Batch: one `run` per scenario, never one process per test case.
- Run the JUnit baseline twice only, at the start and at the end.
- Once a failure is pinned by a minimal reproducer (one candidate), record it and move on. Don't keep debugging.

## 3. Ground rules (safety; the user's machine)

1. **Headless only**: `-Djava.awt.headless=true`. DISPLAY `:0` is the user's live desktop. Do not open windows there
   without asking. Xvfb is not installed.
2. Do not change product code (`fw-*/src/main/**`) and do not commit or push. You test and report; fixes are a later
   decision for the user.
3. Protect the user's state:
   - the remembered file trail `~/.config/combinatorics-framework/desktop-places.properties` (it exists): use
     `FileTrail.inMemory()`, or run with `XDG_CONFIG_HOME=<temp>`;
   - `~/.m2`: don't install into it (see §14);
   - the user's home: Auto-search scans `~` by default, so in tests use `new AutoSearch(navigator, tempHome, budget)`.
4. Work in temp folders for every fixture. Put heavy artifacts (checkpoints, exports, logs) under
   `target/guif-dogfood/`, which git ignores (`**/target/`).
5. Never `System.exit` in candidate or probe code. The runner rejects VM-stopping calls, and it would kill the run.

## 4. Deliverables (exact paths, relative to this folder `/home/yb/16102026/Combinatorics-Framework-JVM`)

```
guif_dogfood/                     ← reusable test assets (create it)
  README.md                       ← what is where; one-command re-run of everything; how to read results
  initial/                        ← the design BEFORE execution
    00_object_model.md            ← GUI objects with stable ids (start from Appendix A), risk ranking per object
    01_watchers_and_codes.md      ← watcher per object: invariants, events listened to, dedicated verdict code + name
    02_factors_constraints.md     ← factors/levels per scenario, constraints rationale, t-wise strategy, label budget
    scenarios/*.json|*.xlsx       ← the Framework scenarios (+ <stem>.constraints.json, <stem>.properties)
    probe/                        ← driver/watcher library sources (if you use one) + build script
  post-results/                   ← the design AFTER the results
    regression/*.json             ← minimal reproducers of every defect found (one candidate each where possible)
    refined/*.json                ← re-weighted/extended scenarios (tighter constraints, t=3 around failures, campaign seeds)
    results_summary.md            ← per scenario: counts, verdict table (code → name → count), coverage achieved
testXP_guif_byCodex.md            ← in THIS folder: your testing-experience report (outline in §15)
```

## 5. Definition of done

- Every one of the 9 tabs and every interactive object in Appendix A has at least one watcher and was exercised by at
  least one executed candidate. Report coverage per object; justify any exclusion.
- t=2 covering over each scenario's main factors, as reported by the Framework itself ("coverage t=2 … covered").
  Add t=3 where interactions are risky:
  - run controls × timing × export kinds;
  - Auto-search kinds × roots × file contents;
  - Sieve drafts × editor switches × save/undo.
- All verdicts come from Framework runs, and the summary's `verdicts` line names every nonzero code (§8.4).
- At least one result set is exported from the **Run tab as "metrics for step 8 (.kv)"** and analyzed in the
  **Results analysis tab**, driven headless. That dog-foods steps 6 and 8.
- The JUnit baseline (132 tests) is still green at the end.
- `guif_dogfood/` re-runs with one command, and `testXP_guif_byCodex.md` is written.

---

## 6. The system under test: `guif`

- Launch (for humans; you stay headless): `java -jar fw-app/target/fw-app.jar guif`. It is FlatLaf, and it refuses a
  headless JVM. Module `fw-desktop`, package `org.ghyura.cf.desktop`. Session layer: `org.ghyura.cf.desktop.session`.
- **9 tabs**, titled `N · Name` (U+00B7 middle dot):
  1. Library
  2. Authoring
  3. Scenario (it also holds the runner settings section)
  4. Constraints / Sieve
  5. Candidates
  6. Run
  7. Campaign
  8. Results analysis
  9. Text scorer

  A "Hints on/off" toggle sits top right; F1 toggles it too. Library and Run also show a compact runner bar.
- Threads:
  - Swing EDT;
  - `Background.run`: one **virtual thread per task named `cf-desktop-<task>`**, whose callback runs on the EDT, with the
    returned future completing *after* the callback;
  - the FileTrail tracker thread;
  - RunView and CampaignView poll with Swing Timers at 250 ms;
  - the Face 2 HTTP server (loopback only).
- Recently added features, which are the highest risk:
  - **Auto-search…** on every file field (`AutoSearch` + `session.FileSeeker`);
  - "What to pick" panels in every file dialog (`Navigator`);
  - wrong-file explanations (`ScenarioView.notAScenario`, `AnalysisView.notMetrics`);
  - the export kind **metrics for step 8 (.kv)** (`session.CorpusExport`);
  - runner settings with Python boxes enabled only for the `python` runner;
  - **zebra tables** (`Table.alternateRowColor` = `#e4ebf5` via FlatLaf global extra defaults);
  - first-launch examples (`Examples`).

## 7. Driving `guif` headless (no window): verified recipe ✅

`Desktop` and the views are **package-private**. From a generated candidate (another class loader) use reflection; it
works on the class path (unnamed module) ✅. Public helpers:
- `org.ghyura.cf.desktop.session.Session.resolve(Path configFile, Map<String,String> env, List<ConfigResolver.Override>)`
- `org.ghyura.cf.desktop.session.FileTrail.inMemory()`
- `FileSeeker`, `CorpusExport`, `ScenarioLibrary`

```java
Constructor<?> make = Class.forName("org.ghyura.cf.desktop.Desktop")
        .getDeclaredConstructor(Session.class, Path.class, FileTrail.class, boolean.class);   // flatWorkbench=true → guif
make.setAccessible(true);
Object desktop = edt(() -> make.newInstance(session, scenarioOrNull, FileTrail.inMemory(), true));
// FlatLaf as in guif (optional, once per JVM): FlatDesktopLauncher.installLookAndFeel()  (package-private static)
```

- **All Swing access happens on the EDT** (`SwingUtilities.invokeAndWait`). Wait for `CompletableFuture`s *off* the EDT.
- Always end with `desktop.close().get(30, SECONDS)`. It stops previews, runs, campaigns, the Sieve and the trail.
- **Headless no-ops and blockers.** Test their logic through the APIs below:
  - The Auto-search button does nothing headless. Use `AutoSearch.roots/search` and `AutoSearch.Results`.
  - `Navigator.choose` would show a `JFileChooser`, which throws headless. Use `place()` and `chooser()`; the chooser can be
    built and inspected, but not shown.
  - `JOptionPane` in `SieveWorkbench.confirmDiscard()` runs when the Sieve is dirty, so it blocks headful and throws
    headless. Apply or discard drafts first.
  - The scorer's charts window, and opening Face 2 in a browser. Face 2's HTTP bridge can be tested over HTTP.

**Drivable API per object (package-private; call via reflection or from a probe in the same package):**

| Owner | Methods you can drive/observe |
|---|---|
| `Desktop` | `component() tabs() hints() hintsButton() runners() scenario() candidates() authoring() sieve() run() campaign() analysis() scorer() library() navigator() allowClose() close()` (`constraints()` is null in guif) |
| `LibraryView` (tab 1) | `open(Path)` → future, `selectSuite(i) selectScenario(row) openSelected() checkSelected() where() details() suiteNames() scenarios()` |
| `AuthoringView` (2) | `newDocument() open(Path) save(Path) validate() addSheet(n) renameSheet(i,n) removeSheet(i) selectSheet(i) type(r,c,text) insertRow(r) removeRow(r) selectCell(r,c) insert(templateId) loadControls() setControls(c) controls() applyControls() document() output() saveGraph(p) showGraph() graphInfo() graphIssues() dependencies()` |
| `ScenarioView` (3) | `setScenario(Path) scenario() validate() output() outputAtTop() plan()`, static `notAScenario(Path)` |
| `RunnerSettings` (3) | `apply() python() status() session()`; fields by accessible name (Appendix A) |
| `SieveWorkbench` (4) | `open() importFrom(Path) save() validate(boolean impact) applyJson(text) selectRule(i) pair(a,b) undo() redo() document() opened() output() dirty() canvas() confirmDiscard() close()` |
| `CandidatesView` (5) | `settings(scan, pageSize, executableOnly) preview() nextPage() cancel() status() source() select(row) estimates() rules() rows()` |
| `RunView` (6) | `start() export(kind, Path) status() output() refresh() controller()`; `RunController`: `pause() resume() cancel() view()` (state, committed, report), `observe(listener)`. Export kinds: `report results csv metrics checkpoint` |
| `CampaignView` (7) | `start() status() output() lineage() controller() refresh()` |
| `AnalysisView` (8) | `choose(Path corpus, String coverageFormat, String coverageReport) analyze() output()`, static `notMetrics(Path)` |
| `ScorerView` (9) | `choose(Path text, String rulesJson) tune(support, common, opt, watch) score() autoPlan() rulesText() setFilter(t) visibleRows() select(row) detail() summary() status() exportCsv(Path) charts()` |
| `Navigator` | `place(Purpose, Path) chooser(Purpose, Path) example(Purpose) tooltip(Purpose, Path) hint(field, Purpose) trail() examples() scenario()`. `Purpose` constants: `SCENARIO_OPEN DOCUMENT_OPEN DOCUMENT_SAVE CONSTRAINTS_OPEN CONSTRAINTS_SAVE GRAPH_SAVE EXPORT_REPORT EXPORT_RESULTS EXPORT_SAMPLES EXPORT_METRICS EXPORT_CHECKPOINT RESULTS_OPEN COVERAGE_OPEN TEXT_OPEN RULES_OPEN SCORES_SAVE LIBRARY_OPEN ADDON_OPEN PROGRAM_OPEN IMPORTS_OPEN WORKDIR_OPEN` |
| `AutoSearch` | `new AutoSearch(navigator, home, FileSeeker.Budget)`, `roots(kind, current)`, `search(kind, current, stop, progress, done)`, `Results(kind, chosen, stopping, closing)` with `show(result) status() filter() table() selected() use` |
| `HintLayer` | `hover(layer, point) settle(layer) press(layer, point) key(layer) text() pending() hintsShown() showHints(layer, on)` |
| `session.FileSeeker` (public) | `seek(Kind, List<Root>, Budget, stop, progress)`. Kinds: `SCENARIO LIBRARY CONSTRAINTS METRICS JACOCO COVERAGE_PY GCOV TEXT RULES ADDON PYTHON PYTHON_MODULES PYTHON_WORKDIR`. Budget default 4 s / 40,000 folders / 300 listed |

**Selectors.** Fields are found by **accessible name**, which is the visible label text without its trailing colon and
unique per section (an enforced rule outside tab 4). Buttons are found by text, *scoped by tab and neighbour*. Some
components have ids via `setName`:
- `"Open Face 2 in browser"`
- `"Sieve execution JSON"`
- `"What to pick"` (the dialog's side panel)

Reusable patterns already exist in the tests (copy them, don't rediscover them):
- `fw-desktop/src/test/java/org/ghyura/cf/desktop/DesktopTest.java`: `edt`, `until`, `all(component)`.
- `HintLayerTest.java`: `layout`, `paint` (offscreen render), `Recorder`.
- `FileGuidanceTest.java`: `field(root, accessibleName)`, `buttons(root, text)`.
- `AutoSearchTest.java`, `FileSeekerTest.java`: temp-tree fixtures with real file formats.

---

## 8. The Framework as your test engine (authoring and running contracts) ✅

### 8.1 Scenario anatomy

A scenario is an `.xlsx` workbook or the same sheets as `.json`/`.yaml`/`.toml`. The JSON form is the easiest to
generate:

```json
{"sheets": {
  "FW_Seq":  [["HEAD","FW_Combi(1)"], ["factor_a","FW_Combi(1)"], ["w_obj_x","FW_Combi(1)"], ["TAIL","FW_Combi(1)"]],
  "HEAD":    [["<java: imports, public class X { public static int FW_VAR = 0; public static void main(String[] a) throws Exception { ...>"]],
  "factor_a":[["<java statement for level 1>"], ["<java statement for level 2>"]],
  "w_obj_x": [["if (FW_VAR == 0 && <violation>) { FW_VAR = FW_EXIT_CODE; }"]],
  "TAIL":    [["<java: print the metrics line, close braces>"]],
  "FW_CUSTOM_VAR": [["0","PASS"], ["3","GUIF_T3_OBJ_X_BROKEN"]]
}}
```

- `FW_Seq` has one row per sheet, `[sheetName, directives…]`. The other sheets are data sheets of values: one value per
  row cell. Each candidate's source is the chosen values concatenated in the **data sheets' own order** (workbook order,
  or JSON key order). ✅ Re-verified after the first Codex run: with sheets `HEAD, B, A, TAIL` and `FW_Seq` rows
  `HEAD, A, B, TAIL`, `candidates` reports `columns HEAD, B, A, TAIL`. Moving a `FW_Seq` row does not move code, so keep
  both orders identical. The showcase follows exactly this pattern: `HEAD`, statement sheets, `check_*` oracle sheets with
  `FW_VAR = FW_EXIT_CODE`, then `TAIL` printing the metrics line (`scenarios/showcase/showcase.xlsx`).
- Control sheets are **not** data sheets: `FW_Seq`, `FW_CUSTOM_VAR` (rows `[whole number, NAME]`), `FW_Arguments`
  (arguments of `main`), `FW_RunMeFirstOnce` (code run once per run; it leaves `String FW_ARGS`) and `FW_SheetNames`.
- Write comments as `/* … */`, never as a trailing `// …`, because values are concatenated. Keep each value
  self-contained.
- Companions are picked up automatically beside the scenario: `<stem>.constraints.json`. With `java -jar` you must pass
  `--config <stem>.properties` explicitly.

### 8.2 Directives (cell templates' own wording)

| Directive | What it does |
|---|---|
| `FW_Combi(1)` / `FW_Combi` | one value at a time |
| `FW_Combi(k)` | every group of k different values |
| `FW_Combi(size)` | all of the sheet's values together, as one group |
| `FW_Combi(all)` | groups of every size, from one value up to all of them |
| `FW_Combi(0)` | one empty choice |
| `FW_CombiR(k)` | groups of k values in which a value may repeat |
| `FW_Permut` / `FW_Permut()` | every order of all values. `FW_Permut(k)`: k is ignored; put `FW_Combi(k)` in the same row to order k-groups |
| `FW_PermutR(k)` | ordered sequences of k values, with repeats |
| `FW_Subsets` | every subset (the showcase also uses `FW_Subsets_GIVEN(0,2)` and `FW_Subsets_BEFORE(2)`) |
| `FW_Exclude` | helper sheet: its values are prepared for joins, not used alone |
| `FW_Reuse` / `FW_ReuseTableOnly` | keep a helper's rows for later joins / empty them after a join uses them |
| `FW_Optional` | the sheet takes part both with and without its values |
| `FW_Heading` | helper sheet that is also a heading |
| `FW_LastInQueue` | combine this sheet last |
| `FW_Concatenator=<text>` | the text between this sheet's values |
| `FW_Cartes(OTHER)` | each value together with every value of `OTHER` |
| `FW_Separator(SEP)` | put the first value of helper sheet `SEP` between values |
| `FW_Group` | treat every row made so far as one unit |
| `FW_( … ,M:N)` | nested joiners, e.g. `FW_(cart_open,,book,cart_rel,qty,,cart_close,cart_sep,1:1)`; these create **virtual sheets** (`FW_VIRTUAL_*`) that consume labels (§9) |

### 8.3 Constraints sidecar (`<stem>.constraints.json`, auto-discovered)

The top-level keys are `version`, `params`, `constraints` and `orders`. Rule keys are `id`, `sheets`, `pairs`, `sets`,
`when`, `gate`, `polarity` (`forbid`/`require`), `desc`, `condition`, `mapping` and `assert`. Conditions combine with
`all`, `any` and `not`. A real example (`scenarios/showcase/showcase.constraints.json`):

```json
{"version": 1,
 "params": {"customer": {"o.customer(\"guest\");": {"trust": 1}, "o.customer(\"vip\");": {"trust": 3}},
            "payment":  {"o.pay(\"card\");": {"risk": 1}, "o.pay(\"cod\");": {"risk": 3}}},
 "constraints": [
  {"id": "no_cod_via_api", "polarity": "forbid", "pairs": [{"channel": "o.channel(\"api\");", "payment": "o.pay(\"cod\");"}]},
  {"id": "payment_needs_trust", "polarity": "forbid", "sheets": ["customer","payment"], "when": "payment.risk > customer.trust",
   "condition": {"not": {"all": [{"sheet": "channel", "eq": "o.channel(\"web\");"}, {"sheet": "gift", "present": true}]}}},
  {"id": "guests_do_not_stack_coupons", "polarity": "require",
   "assert": {"any": [{"sheet": "customer", "ne": "o.customer(\"guest\");"}, {"sheet": "coupons", "countLe": 1}]}}]}
```

Values in rules are the **exact cell texts**. Tab 4 (Sieve) edits this file, which is also a dog-food target.

### 8.4 The generated-java runner contract (verified) ✅

- Each candidate's source is compiled in memory, against the **application class path plus `cf.execution.classpath`**.
  It runs in its own class loader (`cf-candidate`), so static fields are fresh per sample.
- **Verdict.** After `main` returns, the runner reads the static int field named by `cf.execution.verdict` (`FW_VAR` by
  default, or `FW_CUSTOM_VAR`). `0` is PASS; any other value is DOMAIN_FAIL, kept exactly. A missing field gives a
  BROKEN sample.
- **`FW_EXIT_CODE`.** A cell matching `… FW_VAR = FW_EXIT_CODE …` gets `FW_EXIT_CODE` replaced by **that sheet's 1-based
  ordinal among data sheets**, in workbook order or JSON key order, with control sheets not counted. A failing candidate
  thereby names its carrier sheet. Verified on the showcase: `check_pricing…check_sla` are data sheets 23–26, and their
  codes 23–26 are named by `FW_CUSTOM_VAR`.
- **Metrics.** They come from the **first stdout line that starts with `app=` and contains `FW_VAR=`**. Its `key=value`
  tokens become numeric metrics (finite decimals) or attributes; the `FW_VAR` token is not a metric.
- The run summary prints `outcomes PASS n, DOMAIN_FAIL n, BROKEN n, TIMEOUT n, INFRA_FAIL n, SKIPPED n, CANCELLED n` and
  `verdicts 0 PASS: n, <code> <NAME>: n`. ✅ The walking skeleton (Appendix B) printed `verdicts 0 PASS: 2`; its
  deliberately broken twin printed `verdicts 4 GUIF_T3_PYTHON_BOXES_STATE: 2`.
- Outcomes are `PASS DOMAIN_FAIL BROKEN TIMEOUT INFRA_FAIL SKIPPED CANCELLED`. Run states are
  `NEW VALIDATING READY RUNNING PAUSING PAUSED CANCELLING CANCELLED DRAINING COMPLETED FAILED`.
- A runner plugin (`org.ghyura.cf.spi.SutPlugin`) is the alternative: `verdictNames()` maps codes to names, and its
  oracle passes on 0. Its jars go on `cf.execution.classpath`. That dog-foods the runner settings' "Add-on and
  dependency paths". Only do this if generated-java is not enough.

### 8.5 Coverage, counts, campaigns

- `cf.coverage.strength=t` with `cf.coverage.method=set-cover` gives greedy t-wise covering of the mandatory rows. On the
  showcase it kept 72 of 69,984 rows: "888 tuples, 884 feasible and covered, 4 infeasible" (the infeasible ones are due to
  constraints). Other keys: `cf.coverage.budget` and `cf.coverage.hardCap`.
- Count before you run: `plan --scenario F --scan N` and `candidates --scenario F --limit 0`. Tab 5 does the same in the
  GUI.
- Campaigns (tab 7) run iteratively, biased toward the previous round's winners. Keys: `cf.campaign.iterations`,
  `cf.campaign.stopWhenStable`, `cf.campaign.strategy=bias`, `cf.campaign.explorationFloor`,
  `cf.campaign.minWinnerSupport`, `cf.campaign.seed.input` and `cf.campaign.seed.output`. Use one in **post-results** to
  focus on failing areas.

### 8.6 Run, export, analyze

- CLI, verified:
  `java -Djava.awt.headless=true -jar fw-app/target/fw-app.jar run --scenario F --set cf.execution.workers=1 --set cf.keys.encoding=SIGNED_BYTE --set cf.run.id=ID --checkpoint target/guif-dogfood/ID.checkpoint`
  - other options: `--config`, `--constraints FILE`, `--workers`, `--repeat`, `--seed`, `--format text|xml`, `--debug`;
  - other commands: `validate`, `plan`, `candidates`, `export --input CKPT --output F --kind report|results|csv|html`,
    `analyze --results F.kv [--coverage-format jacoco|coverage-py|gcov --coverage-report PATH]`, `score`, `doctor`,
    `capabilities`.
- **Step 8 reads only a metrics corpus (`.kv`)**: one line per sample, `key=value … FW_VAR=n`. Keys are lower-cased.
  A line without `FW_VAR` is read as *passing*.
- The CLI exports `.kv` as well since the first Codex run: `export --input CKPT --output F.kv --kind metrics`. It writes the
  same content as the GUI Run tab's export kind `metrics` (`RunView.export("metrics", path)`):
  - It writes `candidate_id repeat_idx env_id run_id`, the attributes, the metrics, then `FW_VAR` (0 for PASS, the code
    for DOMAIN_FAIL).
  - Samples without a verdict go to trailing `#` comments.
  - Step 8 on that file reproduces the run's own analysis, except the tie order among equal samples.

### 8.7 Configuration keys you will need (`--set key=value`)

| Key | Default | Use |
|---|---|---|
| `cf.execution.workers` | 4 | **use 1**: candidates share one JVM, one EDT and one `UIManager`, so in-JVM GUI candidates must not run concurrently |
| `cf.execution.timeout.millis` | 30000 | raise for long GUI flows |
| `cf.execution.verdict` | `FW_VAR` | or `FW_CUSTOM_VAR` (static int field of that name) |
| `cf.execution.classpath` | empty | probe or plugin jars |
| `cf.execution.repeat.count`, `cf.execution.retry.maxAttempts`, `cf.execution.serialize` | 1, 1, false | flakiness/timing studies |
| `cf.keys.encoding` | `AUTO_PRESERVE` | **set `SIGNED_BYTE`** (§9) |
| `cf.coverage.strength`, `cf.coverage.method` | `0` (off), `greedy` | `2` or `3` with `set-cover`, as the showcase uses |
| `cf.analysis.goals` | empty | e.g. `elapsed_ms:min,violations:min` |
| `cf.analysis.discover` | true | false for strict goals |
| `cf.run.id` | — | names every sample key |
| `cf.constraints.file` | empty | a sidecar other than the companion |

---

## 9. The BYTE budget: the user's warning ("be wise")

The Framework's data-type range is **`Byte.MIN_VALUE..Byte.MAX_VALUE`**. Verified details:

- Every scenario allocates **labels**: data sheets get `1..S`, then every value cell `S+1..S+C`, plus virtual sheets
  created by joiners. `SIGNED_BYTE` holds **−127..127**; **−128 is reserved**. The showcase uses
  "values 53 (labels 28..80)"; the skeleton uses "labels 6..11".
- The default `AUTO_PRESERVE` *silently widens* to two-octet `PACKED_LEGACY16` above 127. **Always run with
  `--set cf.keys.encoding=SIGNED_BYTE`**, so an overflow fails loudly (`KEY_CENSUS_OVERFLOW`) instead of drifting.
- Budget per scenario: **data sheets + value cells + virtual sheets ≤ 127**. Keep about 15% headroom for post-results
  additions.
- Appendix A lists **206 objects**, so they cannot all be values or watcher sheets in one scenario.
  - **Partition**: one scenario per tab, split tab 4 (85 objects) into 2–3 scenarios, plus cross-cutting scenarios (hints,
    dialogs, auto-search, trail, threads and leaks, zebra).
  - Or keep watchers in a probe library and select watcher *groups* by value.
- Keep verdict codes in byte range too:
  - `0` = PASS.
  - **1..127** = GUI defects. These are naturally the `FW_EXIT_CODE` sheet ordinals, which are ≤ the data-sheet count.
  - **−1..−127** = harness/infrastructure problems you detect yourself: watcher died, EDT blocked longer than its
    budget, close() timed out, thread leak.
  - Never use −128.
- Values are cell texts. Prefer short, distinct texts and compact factor encodings. Fewer and richer levels beat many
  near-duplicates.

## 10. The watcher model: the user's hint, refined ✅

> *Every tab and every field is a unique object, watched by its own listener/controller in its own thread, for a
> dedicated FW_CUSTOM_VAR. The Framework owns the knowledge about these variables.*

1. **Object identity.** Start from Appendix A and give every object a stable id: `t<tab>_<kind>_<slug>`, ASCII,
   lowercase. Example: `t3_field_scenario_file`, `t6_button_export`, `t0_toggle_hints`. Use it everywhere: in sheet names
   `w_<id>`, thread names `guif-watch-<id>`, metric keys `w_<id>_viol` and code names `GUIF_<ID_UPPER>_…`.
2. **Listener.** Registered **on the EDT** to the object's own events:
   - `DocumentListener` for text;
   - `ItemListener`/`ActionListener` for combo boxes and toggles;
   - `ChangeListener` for spinners and tabs;
   - `TableModelListener` for tables;
   - `PropertyChangeListener("enabled"|"text"|"toolTipText")`;
   - `HierarchyListener` for visibility.

   It turns each event into an **immutable snapshot** (value, enabled, visible, text, rows, nanoTime) and puts it on a
   `BlockingQueue`. It does nothing else on the EDT.
3. **Controller.** A dedicated thread, `guif-watch-<id>`, consumes the snapshots. It checks the object's invariants (a
   small state machine) and timing, such as "status updated within N ms after Analyze", and records violations. It never
   touches Swing; if it must query, it uses `invokeAndWait` with a timeout.
4. **Dedicated code.** Each watcher owns one verdict code:
   - the `FW_EXIT_CODE` of its own sheet `w_<id>`: `if (FW_VAR == 0 && Watchers.violated("<id>")) { FW_VAR = FW_EXIT_CODE; }`;
   - or an explicit code in a byte range you allocate.

   Name every code in the scenario's `FW_CUSTOM_VAR` sheet. The Framework then names failures in its run summary,
   reports and exports. **Caution:** reordering or inserting data sheets renumbers `FW_EXIT_CODE`. Append new sheets at
   the end, or regenerate the `FW_CUSTOM_VAR` map from the sheet order with a script, and assert it with `validate`.
5. **Verdict aggregation.** The order of the `w_*` data sheets (sheet order, §8.1) *is* the severity order, because the
   first violated watcher sets `FW_VAR` and the guard `FW_VAR == 0` keeps it. Every watcher still reports in the metrics line,
   for example `w_<id>_viol=0|1` and `w_<id>_events=n`, so step 8 can rank which factors trigger which watcher.
6. **Teardown.** In `TAIL`:
   - stop the controllers with a poison pill and join them with a timeout;
   - call `desktop.close().get(…)`;
   - check for leaked threads (`cf-desktop-*`, `guif-watch-*`);
   - then print the single `app=guif …` metrics line.

## 11. Naming interference: read before you name anything ✅

| Area | Trap | Rule |
|---|---|---|
| Cell texts | A cell starting with `FW_` is parsed as a **directive** unless it starts with `FW_VAR`, `FW_EXIT_CODE` or `FW_CUSTOM_VAR` | never start a code cell with another `FW_…` identifier (e.g. `FW_ARGS = …;` → write `String a = FW_ARGS;`) |
| Sheet and code names | reserved: `FW_Seq`, `FW_VAR`, `FW_CUSTOM_VAR`, `FW_EXIT_CODE`, `FW_Arguments`, `FW_RunMeFirstOnce`, `FW_SheetNames`, every directive, virtual sheets `FW_VIRTUAL_*` | prefix yours `w_` (sheets) and `GUIF_` (code names); no `FW_` anywhere in your names |
| Code numbers | `FW_EXIT_CODE` = data-sheet ordinal, which shifts when sheets move | append-only sheet order, or regenerate the map |
| Metric keys | lower-cased by the reader; `candidate_id repeat_idx env_id source_ref run_id fw_var fw_custom_var app` are reserved; values may not contain spaces or `; \| , ] ) }` | ASCII `[a-z_][a-z0-9_.-]*`, no case-only twins, numbers as plain decimals |
| Metrics line | only the **first** stdout line starting with `app=` **and** containing `FW_VAR=` counts | print it exactly once, last; print nothing else that starts with `app=` |
| Button texts | duplicates across and within tabs (`Browse...` ×3 and `Auto-search…` ×5 in tab 3; `Auto-search…` ×2 in tabs 8 and 9; `Add...`, `Validate`, `Cancel`, `Use the example`); **`…` (U+2026) vs `...`** are different strings (`Auto-search…`, `Import constraints…`, `Export JSON…` vs `Browse...`, `Open...`, `Export...`) | select by tab → section → neighbouring accessible field name; copy texts from Appendix A byte-exactly |
| Tab titles | `N · Name` uses U+00B7; sub-tabs like `2.1 · Control sheets` | match by index or exact title |
| Tab 4 (Sieve) | Appendix A predates the D001 fix: it still shows 17 "(unnamed)" controls and missing hints "—". Every step 4 control now has a visible, linked name ("Scan ≤", "Find cards", "Group as", "Rows", "Row filter", "Columns", "Column filter", "Find rules", "Kind", "Effect", "columns apart", "Sheets the rule reads", "Value", "Ranked values", "Constraints as JSON", "Messages") and a hint | select by accessible name, as in the other tabs; regenerate the inventory before relying on it |
| Threads | product threads are `cf-desktop-<task>`, the trail tracker, and timers | name yours `guif-watch-<id>` for unambiguous leak checks |
| Files | the trail and Auto-search classify by name and content: `*.constraints.json`, `verified_examples.json`, `automation_scenarios.json`, `*.kv`, `*-results.csv`, `*-samples.csv`, `*-metrics.kv`, `*.checkpoint`, `sut_paths.py`, `pyvenv.cfg`, `.xlsx` with an `FW_Seq` sheet, … | use these names *deliberately* for positive fixtures; never accidentally. Isolate with temp dirs and `FileTrail.inMemory()` |
| Shared JVM state | one EDT, `UIManager` look-and-feel, FlatLaf global defaults, system properties | `workers=1`; restore what you change; one Desktop per candidate, always closed |
| Java in cells | values are concatenated; a trailing `//` comment swallows code; keywords as identifiers | `/* */` comments only; unique local names per sheet (e.g. `t3_` prefixes) |

## 12. Known contracts: oracles you can assert (from code and existing tests)

**Global**
- 9 tabs; tab 4 is the Sieve workbench in guif.
- Every control outside tab 4 has a hint: a sentence ending in `.`, at least 4 words, never using the words `legacy`,
  `sidecar`, `joiner`, `operand`, `directive`, `the Core` or `Reader`.
- Every field's visible name equals its accessible name and is unique per section.
- A text field's hint says what fills it (`Filled when|after|while|by|from|with|as`).
- Odd table rows are tinted `#e4ebf5`, including number and boolean cells.
- F1 toggles the hints.

**File dialogs (`Navigator`)**
- Titles read `Open <what> · here: <why>` or `Save <what> · here: <why>`.
- Each dialog has a "What to pick" side panel with plain guidance, and a "Show the example" button when an example is
  near.
- On a first start, with nothing known, a dialog opens at the example, pre-selected (`here: the example found nearby`).

**Tab 1 · Library**
- Before anything is opened: `No library open yet: …`.
- After opening a folder: `<path>: N scenario(s) in M suite(s)`, or `<path>: no examples here. A library is the generator_trunk folder that holds intake/verified_examples.json.`
- The details area starts with a `How to start` guide.
- Open or Check with nothing selected says `Open a library first, then select an example in the table.` or
  `Select an example in the table first.`

**Tab 3 · Scenario**
- `notAScenario` refuses:
  - folders;
  - constraints files, naming `<stem>.<ext> in the same folder`;
  - settings files;
  - library lists;
  - run results, which go to step 8;
  - scorer files.
- Validation is skipped for those files.
- **The Python boxes and their Browse/Add/Auto-search buttons are enabled iff the runner is `python`.**
- `apply()` returns false with `Settings could not be applied: …`.
- `python()` without the add-on mentions `addons/fw-python.jar`. In this dev layout the jar is at
  `fw-python/target/fw-python-0.1.0-SNAPSHOT.jar`.

**Tab 4 · Constraints / Sieve**
- The `Open step 3 scenario` button opens no dialog.
- Before opening, the location line names the step 3 scenario, or says none is chosen.
- Actions before opening say `Press Open step 3 scenario first.`
- Drafts block editor switches: `Apply or discard the current draft before switching editors…`.
- Save writes `<stem>.constraints.json` plus a separate layout file.
- Face 2 must fit beside the file actions at 1180 px.

**Tab 5 · Candidates, tab 6 · Run, tab 7 · Campaign**
- With no scenario chosen: `Choose a scenario in step 3 first, or send an example there from step 1 · Library.`
- Run status after a run: `COMPLETED, N samples committed`.
- Export status: `exported the run's <kind> to <path>`. The metrics kind adds `; study it in step 8 · Results analysis`.
- Export kinds are listed as `summary report (.xml)`, `every attempt (.csv)`, `measurements table (.csv)`,
  `metrics for step 8 (.kv)` and `checkpoint to resume (.checkpoint)`.

**Tab 8 · Results analysis**
- `notMetrics` refuses reports, CSV tables (both kinds) and checkpoints, even with misleading extensions (it sniffs the
  content), and tells you how to get a `.kv`.
- The coverage box and its Browse/Auto-search buttons are enabled iff the format ≠ `no coverage import`.
- The output starts `results: <file> (N samples, M without a verdict)`.

**Auto-search**
- Per-tab button census: tab 1: 1, tab 2: 1, tab 3: 5, tab 4: 1, tab 8: 2, tab 9: 2, tabs 5–7: 0.
- Ranked by root likelihood, then distance, then newest.
- Matching is content-based:
  - xlsx with an `FW_Seq` sheet;
  - specs with `slots`;
  - coverage.py JSON with `meta.branch_coverage` or `meta.show_contexts`;
  - JaCoCo doctype;
  - gcov `Lines executed:`;
  - add-on jars whose plugin classes are not already on the class path;
  - interpreters de-duplicated by real path, keeping venvs separate.
- It never walks `.git`, `node_modules`, `site-packages`, `classes`, ….
- Budget: 4 s, 40,000 folders, 300 listed. The status says `Found T … the likeliest N are listed` when capped.

## 13. Known gaps and risky areas: start your risk model here

- Tab 4 is unnamed and unhinted in places; its confirm dialogs (`JOptionPane`) appear on dirty state.
- Headless-only coverage misses: the real modal dialogs (Auto-search, file choosers, confirmations), the scorer's
  charts window, and opening Face 2 in a browser. If you need them, propose a headful plan and **ask the user first**.
- Races between async tasks and EDT callbacks:
  - double clicks;
  - validate while validating;
  - run start → close;
  - pause/resume/cancel timing against the 250 ms polling;
  - previews cancelled midway;
  - campaign steps.
- Resource leaks across many Desktop instances: FileTrail watchers (up to 64 folders each), timers, virtual threads,
  Face 2 server sockets.
- The trail's remembered store (persistence, corrupted file, unknown kinds), XDG_CONFIG_HOME handling.
- Auto-search:
  - huge folders (5,000 entries per folder cap);
  - symlink loops (links aren't followed);
  - unreadable folders;
  - non-UTF-8 names;
  - the stop button;
  - the budget cut-off message;
  - a home folder equal to the checkout's parent.
- The export/analysis loop: `.kv` round trip, unverdicted samples as comments, coverage `{lineNo}` alignment (samples
  first, comments last).
- The Python runner in a dev layout (the add-on is not in `addons/`); runner settings validation (env `NAME=VALUE;…`,
  sandbox values).
- After the first Codex run, these were fixed and pinned by regression tests:
  - D001: step 4 controls now have names and hints, and the house rules run on both desktops;
  - D002: the Within spinner's number types;
  - D003: an all-rows coverage budget renders without a method;
  - D004: content sniffing beats a `.kv` name;
  - Sieve draft errors go to its Messages area instead of modal dialogs;
  - the CLI gained `export --kind metrics`.

  Re-run `post-results/regression/` to confirm.

## 14. Environment and commands ✅

- Java 25 (`/usr/lib/jvm/jdk-25.0.3-oracle-x64`), Maven 3.9.15 (`/opt/maven`), Linux, X11 `:0` (don't use it), no Xvfb.
- Repositories:
  - this one: `/home/yb/16102026/Combinatorics-Framework-JVM`;
  - siblings `../Combinatorics-Framework-QA-edition` and `../SUT`, both read-only for you.
- **Build the app jar.** Offline, with `-am` required: `mvn -o -B -q package -DskipTests -pl fw-app -am` gives
  `fw-app/target/fw-app.jar`, rebuilt today.
- **JUnit baseline (132 tests, green today).** The parent POM forces `failIfNoTests`, so `-pl fw-desktop -Dtest=…` fails.
  Use a scratch local repository chained to `~/.m2` (never install into `~/.m2`):
  ```sh
  SC=target/guif-dogfood/m2; R="-Dmaven.repo.local=$PWD/$SC -Dmaven.repo.local.tail=$HOME/.m2/repository"
  mvn -B -q $R install -DskipTests -pl fw-desktop -am    # first time online: fetches maven-install-plugin 3.2.0 into $SC only
  mvn -o -B $R test -pl fw-desktop | grep -E "Tests run: [0-9]+, Fail|BUILD"
  ```
- **A probe library**, if you want one: `javac -cp fw-app/target/fw-app.jar -d target/guif-dogfood/probe …`, then jar it
  and pass `--set cf.execution.classpath=<jar>`. Generated candidates can already call it (it is on their compile and run
  class path).
- **Fixtures already on disk** (read-only; copy them into temp folders before modifying):
  - scenarios: `scenarios/showcase/showcase.xlsx` (with `.constraints.json` and `.properties`) and 16
    `../SUT/FW_scenario_inputs/*.xlsx`;
  - the library `../Combinatorics-Framework-QA-edition/generator_trunk` (74 scenarios in 5 suites);
  - a metrics corpus `…/generator_trunk/tryout_own/pricing/pricing_metrics.kv`;
  - scorer samples `../Combinatorics-Framework-QA-edition/Analyzer_trunk/samples/comprehensive_input.txt` and
    `comprehensive_config.json`;
  - Python venvs `~/.venvs/*` (Python 3.12.3);
  - the add-on `fw-python/target/fw-python-0.1.0-SNAPSHOT.jar`.
- Timing reference ✅: the skeleton ran 2 candidates in 2.7 s (the first ~0.9 s, then ~0.15 s each), so a 200-candidate
  suite takes a few minutes at `workers=1`.

## 15. `testXP_guif_byCodex.md`: the outline to fill

1. Scope and approach: how you modelled the GUI with the Framework, and why these partitions.
2. Design decisions: the watcher architecture, code allocation within the byte range, naming conventions, coverage
   strength per scenario, constraints.
3. Execution log, as a summary table: scenario → candidates → outcomes → verdicts by name → duration.
4. Defects: id, severity, verdict code/name, minimal reproducer (path in `post-results/regression/`), expected vs
   actual.
5. Coverage achieved: objects × watchers × candidates, t-wise numbers as the Framework reported them, and what was not
   covered and why.
6. Dog-fooding friction: where the Framework or GUI made testing itself hard, with concrete improvement proposals.
7. Token economy: design vs execution split, and what you would do differently.
8. How to re-run, and what to extend next.

---

## Appendix A: GUI object inventory (generated from the live component tree, headless guif, 2026-10-18)

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

## Appendix B — verified walking skeleton (`guif_skeleton.json`) ✅

Ran with: `java -Djava.awt.headless=true -jar fw-app/target/fw-app.jar run --scenario guif_skeleton.json --set cf.execution.workers=1 --set cf.keys.encoding=SIGNED_BYTE --set cf.run.id=guif_skeleton --checkpoint target/guif-dogfood/skeleton.checkpoint`
Result: `candidates generated 2 … executable 2`, `outcomes PASS 2`, `verdicts 0 PASS: 2`, labels 6..11 (SIGNED_BYTE). Its twin with the watcher condition inverted gave `verdicts 4 GUIF_T3_PYTHON_BOXES_STATE: 2`, and the `csv` export carried the metrics `elapsed_ms` and `python_box_on`. It builds one headless guif Desktop per candidate via reflection, sets the Runner choice on the EDT, lets the watcher sheet `w_t3_python_boxes` (data sheet #4, so `FW_EXIT_CODE` = 4) judge the Python boxes, closes the Desktop and prints the metrics line. Grow your scenarios from it: add factor sheets, add `w_<id>` watcher sheets (append-only), and keep the `FW_CUSTOM_VAR` map in step.

```json
{
 "sheets": {
  "FW_Seq": [
   [
    "HEAD",
    "FW_Combi(1)"
   ],
   [
    "runner",
    "FW_Combi(1)"
   ],
   [
    "act",
    "FW_Combi(1)"
   ],
   [
    "w_t3_python_boxes",
    "FW_Combi(1)"
   ],
   [
    "TAIL",
    "FW_Combi(1)"
   ]
  ],
  "HEAD": [
   [
    "import java.awt.Component; import java.awt.Container; import java.lang.reflect.*; import java.nio.file.Path; import java.util.*; import java.util.concurrent.*; import javax.swing.*;\n/* Walking skeleton: one headless guif Desktop per candidate, driven on the EDT, judged by watcher sheets. */\npublic class GuifSkeleton {\n    public static int FW_VAR = 0;\n    static Object desktop;\n    static JComponent root;\n    /* the first component of the type whose accessible name (or button text) is the name */\n    static <T extends Component> T find(Component c, Class<T> type, String name) {\n        if (type.isInstance(c)) {\n            String shown = c instanceof AbstractButton b && b.getText() != null && !b.getText().isBlank() ? b.getText() : c.getAccessibleContext().getAccessibleName();\n            if (name.equals(shown)) return type.cast(c);\n        }\n        if (c instanceof Container k) for (Component child : k.getComponents()) { T t = find(child, type, name); if (t != null) return t; }\n        return null;\n    }\n    static <T> T edt(Callable<T> work) throws Exception { FutureTask<T> task = new FutureTask<>(work); SwingUtilities.invokeAndWait(task); return task.get(30, TimeUnit.SECONDS); }\n    static Object call(String method) throws Exception { Method m = desktop.getClass().getDeclaredMethod(method); m.setAccessible(true); return m.invoke(desktop); }\n    public static void main(String[] args) throws Exception {\n        long started = System.nanoTime();\n        var session = org.ghyura.cf.desktop.session.Session.resolve(null, Map.of(), List.of());\n        var trail = org.ghyura.cf.desktop.session.FileTrail.inMemory();\n        Constructor<?> make = Class.forName(\"org.ghyura.cf.desktop.Desktop\").getDeclaredConstructor(org.ghyura.cf.desktop.session.Session.class, Path.class, org.ghyura.cf.desktop.session.FileTrail.class, boolean.class);\n        make.setAccessible(true);\n        desktop = edt(() -> make.newInstance(session, null, trail, true));\n        root = (JComponent) edt(() -> call(\"component\"));\n"
   ]
  ],
  "runner": [
   [
    "String runner = \"generated-java\";"
   ],
   [
    "String runner = \"python\";"
   ]
  ],
  "act": [
   [
    "        final String chosen = runner;\n        boolean pythonBoxOn = edt(() -> {\n            JComboBox<?> box = find(root, JComboBox.class, \"Runner\");\n            box.setSelectedItem(chosen);\n            return find(root, JTextField.class, \"Python interpreter\").isEnabled();\n        });\n"
   ]
  ],
  "w_t3_python_boxes": [
   [
    "if (FW_VAR == 0 && pythonBoxOn != runner.equals(\"python\")) { FW_VAR = FW_EXIT_CODE; }"
   ]
  ],
  "TAIL": [
   [
    "        ((CompletableFuture<?>) edt(() -> call(\"close\"))).get(30, TimeUnit.SECONDS);\n        System.out.println(\"app=guif runner=\" + runner + \" python_box_on=\" + (pythonBoxOn ? 1 : 0) + \" elapsed_ms=\" + (System.nanoTime() - started) / 1_000_000 + \" FW_VAR=\" + FW_VAR);\n    }\n}"
   ]
  ],
  "FW_CUSTOM_VAR": [
   [
    "0",
    "PASS"
   ],
   [
    "4",
    "GUIF_T3_PYTHON_BOXES_STATE"
   ]
  ]
 }
}
```
