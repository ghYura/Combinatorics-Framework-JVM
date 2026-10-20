# After the fix: step 0 wizard buttons

What changed in the product (2026-10-20), all in `WizardView`:

- **Factors (page 0.2).** Remove the factor needs a selected factor; Move up needs one above it, Move down one below it.
- **How each varies (page 0.3).** The four answers (Exactly one, A fixed number, Any number within bounds, It may or may not happen) need a selected factor. Without one, every answer and its number fields are off, so none keeps the last factor's state.
- **Rules (page 0.4).**
  - Add the rule needs a rule, from the chosen values, that can work and that the scenario does not have yet. When the rule is not offered, the sentence says why ("The scenario has this rule already: …").
  - Remove the rule needs a selected rule.
- **Finish (page 0.7).**
  - Check with the Framework and Save the scenario... need a design without problems; the line above them says what to fix.
  - The three handoffs need a saved scenario. When the design changed since it was saved, they also need no problems, since a handoff saves it again first.
- **The probe's wizard family now follows the same needs.** It used to expect Check and Save to be pressable right after "Start with no factors", which contradicted the precondition table.

The verify phase `fix2` reran 3 scenarios (wizard, sweep_04, sweep_05) and 2 reproducers on the fixed product, with the same probe, oracle and selection as the investigation; the investigation's own evidence is unchanged. Counts are wrong / checks.

## The buttons the fix touches

| Button | Needs (as checked after the fix) | Before | After | Before: a press without the need |
|---|---|---|---|---|
| Remove the factor | a selected factor | SOFT · missing 2/2 · ready 0/26 | PASS · missing 0/2 · ready 0/26 | 2 silent |
| Move up | a selected factor with one above it | SOFT · missing 21/21 · ready 0/7 | PASS · missing 0/21 · ready 0/7 | 2 silent |
| Move down | a selected factor with one below it | SOFT · missing 11/11 · ready 0/17 | PASS · missing 0/11 · ready 0/17 | 2 silent |
| Exactly one of them | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| A fixed number of them | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| Any number of them, within bounds | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| It may or may not happen: a sudden action | a selected factor | SOFT · missing 2/2 · ready 0/74 | PASS · missing 0/2 · ready 0/74 | 2 silent |
| Add the rule | a rule the chosen values make that is new and can work | SOFT · missing 2/2 · ready 0/2 | PASS · missing 0/2 · ready 0/2 | 2 explains |
| Remove the rule | a selected rule | SOFT · missing 26/26 · ready 0/2 | PASS · missing 0/26 · ready 0/2 | 2 silent |
| Check with the Framework | a design without problems, and no check running | SOFT · missing 2/26 · ready 0/50 | PASS · missing 0/30 · ready 0/46 | 2 explains |
| Save the scenario... | a design without problems | SOFT · missing 2/26 · ready 0/50 | PASS · missing 0/30 · ready 0/46 | 2 explains |
| Use it in step 3 | a saved scenario (a design changed since is saved again first, so it must have no problems) | PASS · missing 0/26 · ready 0/26 | PASS · missing 0/26 · ready 0/26 |  |
| Show its test cases (step 5) | a saved scenario (a design changed since is saved again first, so it must have no problems) | PASS · missing 0/26 · ready 0/26 | PASS · missing 0/26 · ready 0/26 |  |
| Open it in step 2 | a saved scenario (a design changed since is saved again first, so it must have no problems) | PASS · missing 0/26 · ready 0/26 | PASS · missing 0/26 · ready 0/26 |  |

## Every other button these scenarios checked

140 buttons; verdict changed for 0.


## Reproducers after the fix

| Scenario | Verdict | What the window said or did | Evidence |
|---|---|---|---|
| repro_wizard_check_empty_design | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-11467154152206764975/evidence.json) |
| repro_wizard_move_up_at_first_factor | 0 PASS | nothing changed | [evidence](../target/guif-dogfood-v2/fixtures/sweep_repro-12117617286795316190/evidence.json) |

Protected files during the phase: 1100 checked against the snapshot taken after the fix; changed 0, added 0.
