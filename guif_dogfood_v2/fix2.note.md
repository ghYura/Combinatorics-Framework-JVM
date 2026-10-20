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
