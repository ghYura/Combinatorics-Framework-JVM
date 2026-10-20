# After the fix: step 4 drafts and formula buttons

What changed in the product (2026-10-20), and why each check below moved:

- **The rule editor is off while no rule is shown.** Rule ID, Description, Effect, Adjacent, Within and its distance, the Only-when condition (+ condition, Apply JSON and its text), Apply rule and Discard draft are disabled until a rule is selected or New rule starts one (`SieveRuleEditor.shown`, `ConditionEditor.active`). Applying a condition the rule already has is no longer a draft.
- **The data editor is off until a scenario is opened** (`SieveDataEditor`): before that it has no values to describe.
- **The formula palette keeps replacing, and says so.** Replacing is intended: the palette's rule takes a slot "empty or not" (`FormulaBlocks.fits`), and the right-click menu calls it "Replace with". What was wrong was the button hint promising "the selected empty place" and the silent loss. The hint now says a block there is replaced, and each replacement reports what went: "Replaced (1 > 2) with a new compare block. Discard draft returns to the rule as last applied." The need these six buttons are checked against was corrected to match: a place that takes the block's type.

Buttons still SOFT below are pressable in a state this fix does not cover (a shown rule with no draft for Discard draft; an opened scenario with nothing selected for the data editor's Remove buttons and moves). They belong to the next round.

The verify phase `fix1` reran 7 scenarios (sieve_graph, sieve_data, sieve_rules, sieve_formula, sweep_01, sweep_02, sweep_03) and 4 reproducers on the fixed product, with the same probe, oracle and selection as the investigation; the investigation's own evidence is unchanged. Counts are wrong / checks.

## The buttons the fix touches

| Button | Needs (as checked after the fix) | Before | After | Before: a press without the need |
|---|---|---|---|---|
| Adjacent | a selected pairs, sets or formula rule | SOFT · missing 2/34 · ready 0/6 | PASS · missing 0/34 · ready 0/6 | 2 leaves a pending draft |
| Within | a selected pairs, sets or formula rule | SOFT · missing 2/30 · ready 0/6 | PASS · missing 0/30 · ready 0/6 | 2 leaves a pending draft |
| + condition | a selected rule whose condition can grow | SOFT · missing 2/30 · ready 0/6 | PASS · missing 0/30 · ready 0/6 | 2 leaves a pending draft |
| Apply JSON | a selected rule | SOFT · missing 2/30 · ready 0/6 | PASS · missing 0/30 · ready 0/6 | 2 leaves a pending draft |
| Add attribute | a scenario opened in the Sieve | SOFT · missing 2/30 · ready 0/10 | PASS · missing 0/30 · ready 0/10 | 2 leaves a pending draft |
| compare | a selected place that takes a truth value (a block already there is replaced) | SOFT · missing 2/2 · ready 0/6 | PASS · missing 0/2 · ready 0/6 | 2 replaces the formula |
| and / or | a selected place that takes a truth value (a block already there is replaced) | SOFT · missing 2/2 · ready 0/6 | PASS · missing 0/2 · ready 0/6 | 2 replaces the formula |
| not | a selected place that takes a truth value (a block already there is replaced) | SOFT · missing 2/2 · ready 0/6 | PASS · missing 0/2 · ready 0/6 | 2 replaces the formula |
| + − × | a selected place that takes a number (a block already there is replaced) | SOFT · missing 2/6 · ready 0/4 | PASS · missing 0/6 · ready 0/4 | 2 replaces the formula |
| sheet.param | a selected place that takes a number (a block already there is replaced) | SOFT · missing 2/6 · ready 0/4 | PASS · missing 0/6 · ready 0/4 | 2 replaces the formula |
| number | a selected place that takes a number (a block already there is replaced) | SOFT · missing 2/6 · ready 0/4 | PASS · missing 0/6 · ready 0/4 | 2 replaces the formula |
| Apply rule | a selected rule or a new rule draft: applying it unchanged only checks it | SOFT · missing 2/42 · ready 0/6 | PASS · missing 0/42 · ready 0/6 | 2 explains |
| Discard draft | a changed rule draft | SOFT · missing 6/34 · ready 0/2 | SOFT · missing 6/34 · ready 0/2 · [evidence](../target/guif-dogfood-v2/fixtures/sieve_rules-17826583331474334640/evidence.json) | 2 reloads the shown text |
| Add value | a scenario opened in the Sieve | SOFT · missing 2/30 · ready 0/10 | PASS · missing 0/30 · ready 0/10 | 2 silent |
| Apply data | a scenario opened in the Sieve: applying unchanged data only checks it | SOFT · missing 2/30 · ready 0/10 | PASS · missing 0/30 · ready 0/10 | 2 explains |
| Discard data draft | a changed attribute or order | SOFT · missing 10/38 · ready 0/10 | SOFT · missing 10/38 · ready 0/10 · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-16168385448731002207/evidence.json) | 2 silent |
| Remove attribute | a selected attribute row | SOFT · missing 10/38 · ready 0/10 | SOFT · missing 10/38 · ready 0/10 · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-16168385448731002207/evidence.json) | 2 silent |
| ↑ | a selected ranked value with one above it | SOFT · missing 14/42 · ready 0/6 | SOFT · missing 14/42 · ready 0/6 · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-16168385448731002207/evidence.json) | 2 silent |
| ↓ | a selected ranked value with one below it | SOFT · missing 14/42 · ready 0/6 | SOFT · missing 14/42 · ready 0/6 · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-16168385448731002207/evidence.json) | 2 silent |
| Remove | a selected ranked value | SOFT · missing 10/38 · ready 0/10 | SOFT · missing 10/38 · ready 0/10 · [evidence](../target/guif-dogfood-v2/fixtures/sieve_data-16168385448731002207/evidence.json) | 2 silent |

## Every other button these scenarios checked

148 buttons; verdict changed for 0.


## Reproducers after the fix

| Scenario | Verdict | What the window said or did | Evidence |
|---|---|---|---|
| repro_sieve_data_draft_before_open_locks_views | 0 PASS | Sieve view Bond matrix says: Press Open step 3 scenario to load the scenario chosen in step 3 with its rules (constraints). Edit them here, validate or check impact, then press Save for scenario. Graph, matrix and inspector share one document. Forbid/require,  | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-7959989745763660363/evidence.json) |
| repro_sieve_gate_without_rule_blocks_validate | 0 PASS | Sieve view Sieve graph says: Valid for execution; no candidate page scanned. pair1: not counted exactly within the scan limit | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-2214902682483430098/evidence.json) |
| repro_sieve_number_replaces_filled_operand | 0 PASS | : Replaced 1 with a new number block. Discard draft returns to the rule as last applied. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-1298452230901952729/evidence.json) |
| repro_sieve_palette_replaces_filled_slot | 0 PASS | : Replaced (1 > 2) with a new compare block. Discard draft returns to the rule as last applied. | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-4862360068061175851/evidence.json) |

Protected files during the phase: 1100 checked against the snapshot taken after the fix; changed 0, added 0.
