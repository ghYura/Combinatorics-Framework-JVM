# Design before execution

## Continuity and scope

V1 supplied stable headless driving patterns, isolated fixtures, Framework-generated Java candidates, per-object watchers, metrics export/analysis, ordered run-control triples and real FW_Optional interruptions. Its 557 historical executions are not counted as v2 executions. Its four defects were subsequently fixed. V2 investigates button availability, including controls added since v1, across all ten current tabs and nested editors.

Inventory consists of application-owned Swing AbstractButtons (push, toggle, checkbox and radio controls), identified by tab, nested page, text and duplicate ordinal. Look-and-feel scrollbar, spinner and combo arrow buttons are implementation chrome; they are counted separately, not silently mistaken for application actions. Dynamic rule-editor buttons and search-result buttons extend the inventory when constructed. Native dialog buttons are outside headless execution, although launch-button enablement remains in scope.

## Oracle

Every observation records the actual enabled flag, ancestor visibility, current state/event and actual action-listener deliveries. isShowing cannot serve as visibility in a headless component tree. Hidden controls are not considered pressable merely because isEnabled is true. Disabling an ancestor does not by itself disable a Swing child; each button's flag is inspected.

Expected availability is derived from the action's purpose and necessary input, not copied from setEnabled implementations. Examples: removal needs a selection; moving up needs an earlier sibling; undo needs history; export needs results; preview pagination needs another page; cancellation needs outstanding work. Validation/check actions may be enabled for invalid input to produce useful diagnostics. File-selection buttons do not require an already chosen file. Buttons that start a run require an existing scenario. Save-as can open a destination chooser for an unsaved document.

For asynchronous work, capture the synchronous state immediately after dispatch on the same EDT turn, then await completion off the EDT and assert restoration. Timer-driven run/campaign buttons are checked after refresh, with controller state captured on the same turn. Real controller progress hooks make pause/cancel timing deterministic. No sleep is used as proof that a state was reached.

The explicit user requirement, **ready implies enabled**, is reported separately from the stronger diagnostic expectation, **missing prerequisite implies disabled**. The latter detects misleading affordances and no-op actions. If a handler safely explains missing input, an enabled button is an availability/UX finding, not evidence that an invalid operation executed. This distinction must remain visible in the report and severity classification.

Watchers distinguish: enabled without prerequisites; disabled despite prerequisites; disabled-button ActionEvent leak; stale state after recovery; operation failure; harness failure. Failure does not prevent later independent checks in the same candidate. The Framework verdict identifies the first failing button; evidence retains every failed assertion.

Every button owns its own ActionListener, ItemListener, ButtonModel ChangeListener and property listener. Every non-button semantic element owns separate property/hierarchy and appropriate document/caret, table model, selection, tree model, combo, spinner or tab listeners. Listener payloads are captured on the EDT with a global monotonically increasing event number for correlation. Static labels/status outputs are included. Per-element histories retain at most 512 events and explicitly count any omitted tail; no unobserved transition is invented. Different element histories are never replaced by one aggregate listener.

The probe/codes.tsv resource assigns stable, distinct integer verdicts to every element. Generated candidates put the first failing watcher code into FW_VAR; FW_CUSTOM_VAR names every code. Verdict integers are separate from signed-byte generation labels. Unlike v1's per-watcher code sheets, v2 uses one dispatch sheet to read the independent watcher results, preserving the signed-byte budget for actions and states. All violations, including those masked by the first Framework verdict, remain in evidence.

## Combinatorial strategy

Families isolate unrelated state machines to keep signed-byte labels below 127. Pairwise is the floor across fixture shape, selection, prior action and recovery. Run/campaign controls, Sieve edit history, and formatting interruptions use strength three. Ordered actions are generated as separate positional factors and with FW_PermutR for repeated subsequences where appropriate. Coverage over a sequence-valued factor is not claimed as positional sequence coverage; actual adjacent pairs/triples are calculated from dispatched traces.

FW_Optional represents an actual event dispatched between actions: cancel, refresh, clear selection, change scenario, restore original or tab switch. Absence is a generated level, not a fake idle action. Constraints use both forbidden pairs and required assertions between nodes. They remove impossible setup operations (selecting a row from an empty fixture), while retaining missing-prerequisite states as intentional button probes. They must never sieve away a known product failure.

Each family has one Framework run, workers=1, generated-java, signed-byte encoding, named FW_CUSTOM_VAR verdicts and unbounded covering budget within a finite hard cap. The Framework supplies selected candidates and covering reports. Checkpoint evidence IDs bind coverage to the selected executions; calibration artifacts are excluded. Minimal one-candidate reproducers pin each distinct defect cluster, followed by targeted refinement only where evidence exposes a gap.

## Execution discipline

Capture product/test/remembered-trail hashes before execution and verify at the end. Use FileTrail.inMemory, isolated XDG_CONFIG_HOME, temporary fixture roots and a scratch Maven repository chained to the existing cache. Two JUnit baselines bracket this new suite, not the historic suite. Build and baseline output goes to files; inspect compact summaries only. Stop at durable batch boundaries if the user requests a pause. Do not fix product code during this investigation.

## Continuation after the pause (2026-10-19, Claude)

Codex paused at the quota limit with 16 families run, 3 of them quarantined, and the individual-button sweep not run. The design above stands; these corrections and additions came from reading the product sources against the oracle before any rerun.

- **Run state oracle.** The probe treated only RUNNING, PAUSING, PAUSED and CANCELLING as a run that goes, while the run tab treats every non-terminal state so (DRAINING, VALIDATING, READY too). 23 of 162 run cases sampled "terminal" during DRAINING and reported Run and Export as ready-but-disabled and Cancel as enabled after the end. The probe now reads `RunState.terminal()`, and each expectation is computed from the same state the buttons were refreshed with (one refresh between two equal readings).
- **Quarantines resolved.** Authoring now saves the graph only through the same `graph != null` guard the real button has. Analysis and search were rerun with Codex's corrected setups.
- **Pressable, not merely enabled.** The Use button beside a file box is hidden while no newer remembered file is offered, and an editor that swaps its body detaches its buttons. Expected availability is now compared with *pressable*: enabled, shown while its own tab is shown, and still in the window. A hidden button is never pressed.
- **Severity in the verdict.** A broken must (ready but not pressable, a disabled button that fires, a press that throws or fires nothing, an event off the EDT) is *hard*; pressable without the need is *soft*. FW_VAR names the first hard failure, else the first soft one, so a soft finding in an initial state no longer masks a hard one. Codes stay Codex's: verdict codes are plain ints that the Framework keeps exactly ("custom codes above 127 included"); only labels are signed bytes, and every scenario stays within 127 labels.
- **The per-button sweep is data.** [probe/preconditions.tsv](probe/preconditions.tsv) states, for each of the 168 sweep buttons, what it needs, the setup steps that leave that need missing ("-" when nothing can be missing), and the steps that meet it. Every step is a user-reachable state (open a library, select a row, start a run...). A run is held in RUNNING by parking its own thread in its progress callback until the check is done, never the EDT. The constraint sieve forbids `setup=missing` for buttons that need nothing. An FW_Optional `away` visit to another tab and back probes state that should survive navigation. All 292 rows were calibrated before the Framework runs (calibration fixtures deleted, not evidence).
- **Families and sweep share one oracle.** "Validate and plan" with an empty box, Copy DOT without a graph, and the Use hints now expect what the table says, so a family and the sweep never disagree about the same state.
- **Reproducers.** One-candidate scenarios in [repro/](repro/) pin each finding cluster; [RESUME.md](RESUME.md) has the command that runs one.
