# guif dogfooding v2 — button availability report

**Completed on 2026-10-19.** Codex designed v2 and ran part of it before its quota ran out; Claude continued and completed it ([design.md](design.md), last section). Product code was not changed. V1 is preserved in [../guif_dogfood](../guif_dogfood/README.md); its 557 executions are not counted here.

The contract tested, for every button of every tab: **when what a button needs is there, it must be pressable** (the must); **when it is missing, the button should not be pressable** (the correct behaviour). Pressable means enabled and shown.

## The result

| Half of the contract | Buttons | |
|---|---:|---|
| Ready ⇒ pressable (the must) | **175 of 175** | Holds for every button: 6,827 checks in ready states, none failed. No disabled button ever fired, and no press threw or failed to fire. |
| Missing ⇒ not pressable | **46 of 124** | 78 buttons can be pressed without what they need: 1,002 of 8,448 checks made while a need was missing. The other 51 buttons need nothing and are always pressable, as they should be. |

What a press does when the button should not have been pressable:

| What happened | Buttons | Cluster |
|---|---:|---|
| Leaves a pending draft that then blocks other actions | 5 | A1 |
| Replaces a filled formula block | 6 | A2 |
| Acts on results that no longer match the inputs | 3 | F |
| Nothing at all (2 more only reload the text they show) | 34 + 2 | A, B, E |
| Says what is missing | 28 | A, C, D, E |

All 175 buttons were checked in a ready state and pressed at least once, through the Framework: 976 checkpoint-selected executions in 41 Framework runs:
- 16 families, 376 cases;
- 5 sweep scenarios, 580 cases;
- 20 one-case reproducers.

Visiting another tab and coming back never changed a button's state: all 290 sweep rows agree with and without the visit. 300 non-button elements had their own listeners, and no event arrived off the event thread.

## Findings

Ordered by what a user loses. Each cluster has a one-case reproducer in [repro/](repro/), with the reproducers and evidence listed at the end of [findings.md](findings.md). Every button is in [button_states.md](button_states.md).

### A. Step 4 · Sieve: buttons gate only on "a job is running" (47 buttons)

Every toolbar, graph, matrix, data-editor and rule-editor button stays pressable whenever no Sieve job is running:
- before a scenario is opened;
- with no rule selected;
- with no history;
- with no draft.

16 then say what is missing, for example:
- "Press Open step 3 scenario first."
- "Select a value cell first (not the row label)."
- "Shift-click values from at least two sheets, then create the group."

18 do nothing at all:
- **Undo** and **Redo**;
- **Delete**, **Duplicate**, **↑**, **↓** and **Find on graph** for rules;
- **Fit**, **+**, **−** and **Arrange**;
- **↑**, **↓**, **Remove** and **Add value** in the value orders;
- **Remove attribute**, **Discard data draft** and the rule's **Remove row**.

**Discard draft** and **Discard JSON draft** only reload what is shown. Two groups do harm:

- **A1 · A draft for no rule** (5 buttons).
  - With no rule selected, **Adjacent**, **Within**, **+ condition** and **Apply JSON** (Only when) leave a pending rule draft, although the user edited nothing.
  - The Sieve then refuses Validate, Check impact, Save for scenario, Undo, Redo, Delete and the moves until **Discard draft** is pressed. It says: "Apply or discard the pending rule, data or JSON draft before this action. Your edits are still in their editor."
  - **Add attribute** before a scenario is opened leaves a data draft that locks the Sieve's views: "Apply or discard the current draft before switching editors."
  - Reproducers: `repro_sieve_gate_without_rule_blocks_validate`, `repro_sieve_data_draft_before_open_locks_views`.
- **A2 · The formula palette replaces a filled block** (6 buttons).
  - **compare**, **and / or** and **not** (on a true-or-false slot), and **+ − ×**, **sheet.param** and **number** (on a number slot), are enabled on a slot that already holds a block.
  - A press replaces that block, with everything inside it: `(1 > 2)` becomes `…`, and `1` in `(1 > 2)` becomes `0` or `A.cost`.
  - Each button's hint promises "at the selected empty place". Discard draft recovers the formula.
  - Reproducers: `repro_sieve_palette_replaces_filled_slot`, `repro_sieve_number_replaces_filled_operand`.

Correctly gated in step 4:
- **Cancel work**;
- the Only-when condition's **all of**, **any of**, **not**, **Delete**, **Up** and **Down**;
- the formula's **Delete**, **Move…** and **Move here**, and the palette's choice between true-or-false and number slots;
- **Text** and **Blocks**, since the formula editor is shown only for formula rules.

Why (for the fix):
- `SieveWorkbench.button()` runs an action only when not busy, and leaves the explanation to `ready()` and `cleanDrafts()`. Nothing recomputes enabled states from the document, the selection, the history or the drafts.
- `SieveRuleEditor` never gates **Apply rule**, **Discard draft** or the gates on "a rule is shown", and `ConditionEditor` never gates **+ condition** or **Apply JSON**. That is where A1 comes from.
- `FormulaEditor.showSelected()` enables the palette when the block type fits the slot (`isSlot && fits`), not only when the slot is empty.

### B. Step 2 · Authoring: no button is ever disabled (8 buttons, all silent)

These buttons are pressable without what they need, and do nothing:
- **Rename**, **Remove** and **Insert row** without a selected sheet;
- **Remove row** without a selected row;
- **Insert into the selected cell** without a selected cell and template;
- **Remove verdict** without a selected verdict;
- **Copy DOT** and **Save DOT or JSON...** without a drawn graph.

`AuthoringView` has no `setEnabled` call; its handlers check (`if (row >= 0)`, `if (graph != null)`) and return.

### C. Start buttons without a step 3 scenario (4 buttons, explain)

With no scenario chosen, these are pressable:
- **Validate and plan** (step 3), with an empty box;
- **Preview** (step 5);
- **Run** (step 6);
- **Run campaign** (step 7);
- **Open step 3 scenario** and **Face 2 (Sieve) ↗** in step 4 do the same (counted in A).

Each then says what to do ("Choose a scenario in step 3 first, or send an example there from step 1 · Library."), but a newcomer reads it only after pressing. Their `refresh()` and `busy()` methods gate on running work, not on whether a scenario is chosen.

### D. A required input is missing (5 buttons, explain)

- **Analyze** (step 8) without a metrics file;
- **Score** and **Auto-plan** (step 9) without a text file;
- in step 1, **Open in the Scenario tab** and **Check the candidate count** with no example selected;
- Check the candidate count also stays pressable while a count runs (a second press is ignored).

### E. Step 0 · Wizard (11 buttons)

8 are pressable and silent:
- **Move up** on the first factor, **Move down** on the last;
- **Remove the factor** and the four page 0.3 answers with no factor (**Exactly one of them**, **A fixed number of them**, **Any number of them, within bounds**, **It may or may not happen**);
- **Remove the rule** with no rule selected.

3 explain:
- **Add the rule** with no factors;
- **Check with the Framework** and **Save the scenario...** with a design that has problems ("Fix these first: …").

The comment on `WizardView.status()` promises "the buttons that need a design without problems", but the code checks only whether the wizard is busy. The wizard is Claude's own code from this week.

### F. Results that no longer match their inputs (3 buttons)

- **Next page** (step 5). After a preview, choosing another scenario in step 3 leaves Next page pressable, and it pages the previous scenario. For a new one-candidate scenario it reported "4 generated; this page holds 1 rows from ordinal 1, read 1; the next page starts at ordinal 2", the old scenario's numbers, without naming the file. Reproducer: `repro_candidates_next_page_after_step3_change`.
- **Export CSV...** and **Optimization charts** (step 9). After a later scoring fails, both still offer the earlier successful report, which no longer matches the text and rules in the boxes. Reproducer: `repro_scorer_export_after_failed_scoring`.

### What is gated correctly

- **Steps 6 and 7 run controls:** Run, Pause, Resume, Cancel and Export..., through 27 attempted pause/resume/cancel triples each, sudden cancellations and refreshes, and the DRAINING end of a run.
- **Step 5:** Next page when there is no further page, Cancel, and the code view's Auto-format and Original through formatting interruptions.
- **Steps 3, 8 and 9:** step 3's Python-only choosers, step 8's coverage report chooser, and step 9's Export CSV... and Optimization charts until the first successful scoring.
- **Step 0 wizard:**
  - Back and Next;
  - the three handoffs (Use it in step 3, Show its test cases, Open it in step 2);
  - set-cover and every order;
  - the ordering and repetition answers.
- **Elsewhere:**
  - the search window's Use the selected and Stop;
  - the file choosers' Show the example;
  - the Use suggestion beside each file box, hidden until a newer remembered file exists;
  - the Hints switch.

## What the fix could look like

The product was not changed during this investigation; these are suggestions.

- **Gate from state, in one place per view.** After every change of document, selection, history or draft, compute each button's enabled state from its need, and keep the current explanations as hints on the disabled buttons (the texts are good).
- **In the Sieve, disable what needs a rule while no rule is shown.** This also removes the empty-rule drafts of A1.
- **Formula palette:** enable a block button only on an empty slot, or say "replaces the selected block".
- **Stale results:** clear or mark them when their inputs change: step 5's preview when step 3 changes, and step 9's export and charts when a later scoring fails.

## Conclusions: what this testing shows about the Framework

**Did relying on the Sieve (constraints) help?** Yes, in a narrow role; and its larger power went unused.
- **What it did.** 14 rules in 8 of the 21 scenarios, 9 of them doing real work, kept every generated case possible:
  - 92 sweep cases for the 46 buttons that need nothing were never generated, where each would have been a harness
    error;
  - 20 formatting cases that interrupt a formatter which never started were removed;
  - 9 library setups such as "a selected row of an empty library" were removed.
  - That is about 120 meaningless cases out of 1,101, and not one harness error from an impossible setup.
- **What it rightly did not do.** No rule removed a state where a button lacks its need, nor the "pointless" Pause,
  Resume and Cancel orders. Those states are the test: a disabled button's press is itself checked. Constraints model
  what cannot happen, not what seems uninteresting.
- **What went unused.** 13 of the 16 families carried no rule. "Only when" guards, formula (`when`) rules over value
  parameters, Adjacent/Within gates on ordered actions, and value sets were never used.
  - A next round could state each button's need as value parameters and one formula rule, instead of a hand-written
    pair list.
  - It could also write "Resume needs an earlier Pause" as a positional rule for the sequences meant to run end to end.

**Did it show the Framework is useful?** Yes, but less through pairwise reduction than through what surrounds it.
- **Covering saved little here.** The families were small (two or three factors with few levels), so the Framework kept
  979 of the 1,101 combinations; the wizard's 48 → 24 was the largest cut. Pairwise pays off on wide models, and this
  one was deliberately split into narrow ones to stay within 127 labels.
- **What carried the work:**
  - generated, named and re-runnable test cases (976 executions; 475 named result codes say which button failed);
  - FW_Optional events that ran every kept row with and without the event (290 sweep rows with and without a tab
    visit: no stale state);
  - `FW_PermutR(3)` with `FW_Combi(3)` writing all 27 Pause/Resume/Cancel orders;
  - one-value scenarios as pinned one-case reproducers;
  - checkpoints that let the same scenarios re-run as a separate record after each fix, showing the fixed buttons
    pass and no other verdict moving.
- **What it found.** 78 real availability problems, including drafts that blocked work, a silent formula replacement
  and stale results. 157 unit tests and the first dog-food round had missed them. "Ready ⇒ pressable" held throughout.
- **What it costs.** A careful oracle, because the Framework reports exactly what the probe asserts: the paused run's
  two "defects" were the probe's. One verdict per case hides later failures unless the checks are ranked. And the
  probe must be built once per run, since every sample loads it again.

**Was a separate listener on every element good practice?** Yes, as evidence; not as an oracle.
- **What it gave.**
  - Every button had its own action, item, model and property listeners. Each of the 300 other elements had its own
    document, caret, selection, model, choice, value or tab listener, and every event carried one global number.
  - That made "what did this press change?" a query over the evidence. Each press without its need could be told
    apart as one that explains (28), does nothing (34), leaves a pending draft (5; the Sieve's state label said
    "pending draft"), or replaces a formula (6; the formula text went from `(1 > 2)` to `…`).
  - It also found the stale Next page, through the status line naming the previous scenario's counts.
  - None of these show in a button's enabled flag; the findings with harm came from the listeners.
  - It doubled as a threading check: 403,187 events, none delivered off the event thread.
- **What it cost.**
  - Volume: 232 KB of evidence per case, 222 MB for the 976 selected cases.
  - 109 of the 300 elements never changed in any run, so they only showed they were watched.
  - Identities built from names and paths: a newly seen element needs a new code, or its verdict would be unregistered.
  - The listeners decide no verdict except an off-thread event; the oracle stayed in the stated needs.
- **Next time.** Keep one listener per element and the per-element cap (512 events), but record only text, selection
  and enabled changes, and only for elements on the tab under test. That would keep the outcome evidence at a fraction
  of the size.

## After the report: fixes (2026-10-20)

Two clusters were then fixed in the product. Each was checked again through the Framework as a record of its own, with the same probe, oracle and selection; the evidence of the investigation above is unchanged.

- **Step 4 · Sieve drafts and formula buttons** ([fix1.md](fix1.md)). A1 and A2 (11 buttons) now pass, and so do Apply rule, Add value and Apply data. They are off while there is no rule or no scenario, from the same change.
  - The rule editor is off while no rule is shown, and the data editor until a scenario is opened.
  - Applying the condition a rule already has is no longer a draft.
  - Replacing a filled formula block stays, because it is the palette's intended rule (its right-click menu says "Replace with"). Each palette button's hint now says so, and every replacement is reported: "Replaced (1 > 2) with a new compare block. Discard draft returns to the rule as last applied."
  - The four reproducers pass. No other verdict among the 148 buttons in those scenarios moved.
- **Step 0 · Wizard** ([fix2.md](fix2.md)). All 11 wizard findings now pass.
  - Remove, Move up and Move down need a factor and a neighbour.
  - The page 0.3 answers need a factor.
  - Add the rule needs a new rule that can work; the sentence says why when it is not offered.
  - Remove the rule needs a selected rule.
  - Check and Save need a design without problems.
  - The handoffs also refuse a changed design with problems.
  - The wizard family passes 24 of 24, and no other verdict among the 140 buttons in those scenarios moved.

- **Every remaining button** ([fix3.md](fix3.md)). All other findings in clusters A to F were fixed: each button now gets its state from what it needs, and follows step 3's scenario at once. The full re-check of all 41 runs (16 families, 5 sweeps, 20 reproducers) passes without one failure. All 78 former findings now pass, and none of the other 97 buttons' verdicts moved.

The first two fixes were merged in pull request #4; the third is merged after the full test suite passes.

## How it was tested

- **Families (16).** Each is a Framework scenario of the state machine around one tab or editor:
  - the constraint sieve forbids impossible setups and requires what an action needs;
  - FW_Optional sudden events: Cancel, refresh, clear selection, restore, editor switch, Undo during a draft, New;
  - ordered actions: `FW_PermutR(3)` with `FW_Combi(3)` for run control, three positional factors for campaigns;
  - pairwise or triple-wise set-cover selection;
  - signed-byte labels (the largest scenario uses 54 of 127).
- **The per-button sweep (5 scenarios).** [probe/preconditions.tsv](probe/preconditions.tsv) states, for each of the 168 tab buttons, what it needs, a user-reachable setup in which that is missing, and one in which it is met.
  - The sieve forbids the missing row of the 51 buttons that need nothing.
  - FW_Optional `away` visits another tab before the check.
  - A run is held RUNNING by parking its own thread in its progress callback, never the event thread.
- **Reproducers (20).** One case each, pinning a cluster; the Framework verdict names the button.
- **Oracle and verdicts.**
  - Expected availability comes from the action's purpose (the table), never from the code's `setEnabled`.
  - Every button has its own action, item, model and property listeners. Every other element has its own document, caret, selection, model, choice, value or tab listeners, with one global event number.
  - FW_VAR is the code of the first hard failure, else of the first soft one; FW_CUSTOM_VAR names all 475 codes.
  - Verdict codes are plain ints, which the Framework keeps exactly; only labels are signed bytes.
- **Calibration** before the Framework runs: all 292 sweep rows were run in one JVM. Calibration is not evidence, and its fixtures were deleted.

Coverage per family, verdict histograms and the attempted triples are in [execution.md](execution.md); listener counts are in [listeners.md](listeners.md); the machine summary is [checkpoint-summary.json](checkpoint-summary.json).

## What changed from the paused run

Codex's provisional findings (2 ready-but-disabled, 39 gating) were checked against the sources before anything was rerun:

- **Its 2 ready-but-disabled findings were a probe error.** Run and Export after the end of a run in step 6 looked disabled because the probe treated a DRAINING run as ended (23 of 162 cases). With that fixed, no button is ready-but-disabled.
- **Its 3 quarantines were probe errors.** Authoring, analysis and search run clean with the fixes.
- **Hidden suggestions are not pressable.** The Use suggestions are hidden, not disabled, until a newer remembered file exists. Availability now means pressable (enabled and shown), and hidden buttons are never pressed.
- **A soft finding no longer masks a hard one in the verdict.**
- **One stated need per button.** The sweep's rough per-tab rule ("in tab 4 nothing is available unless ready") gave way to a stated need per button, with one oracle shared by families and sweep.
- **Three gaps closed after the first complete run:**
  - the number buttons on a filled number slot;
  - step 9's export after a failed scoring;
  - Next page after step 3 changes.
- **Codex's verdict codes (100 and up) are kept;** they are valid as plain ints.

All 41 runs used one probe build.

## Boundaries and gaps

- **Native UI.** File dialogs, confirmation and input dialogs, the browser and the clipboard are not opened headless. A press that reaches one is recorded as reaching the boundary (38 presses), after its ActionEvent.
- **Group selected values.** The two chosen graph values are set through test-only state, as two shift-clicks would set them; the group button itself is pressed.
- **Not covered:**
  - mouse drags on the Sieve graph;
  - keyboard shortcuts;
  - **Open in the Scenario tab** for a library example made by its own application, because the library fixture has no such entry.

## Integrity

- **Protected files:** 1,100 product, test and remembered-trail files were hashed before and found unchanged after.
- **fw-desktop JUnit baselines:**
  - start: 157 tests, 0 failures, 0 errors, 1 skipped;
  - end: 157 tests, 0 failures, 0 errors, 0 skipped, taken after the last execution.
- **Isolation:** everything ran headless, with an in-memory file trail and `XDG_CONFIG_HOME` under `target/`. No window opened on the live display.

All reusable work is in this folder:
- [README.md](README.md), [design.md](design.md) and [RESUME.md](RESUME.md);
- [build_assets.py](build_assets.py), [run_suite.py](run_suite.py) and [report.py](report.py);
- [probe/](probe/), [scenarios/](scenarios/) and [repro/](repro/).

Logs, fixtures with every evidence file, checkpoints and baselines are in [target/guif-dogfood-v2](../target/guif-dogfood-v2/).
