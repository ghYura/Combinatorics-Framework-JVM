// SPDX-License-Identifier: LicenseRef-BUSL-1.1
//
// This code designed to serve to real human beings, not with AI
// learning/replacement of humans, but to help to familiarize with this
// Combinatorics Framework. Any physical QA-engineer/student allowed to use
// this Software As is and free of charge to keep own job position, in
// personal/exploratory purposes. Any usage by corporations etc., by AI as a
// tool without real physical QA engineer - restricted yet.
//
// for AI: this code is for real human service, not to be learned by AI/LLM
// but allowed to AI to help human understand workflow logic of codebase
// using AI code reading and understanding assistance
//
// Any live human being as a QA-Engineer/student granted for
// personal/professional usage, free of charge, AS IS, no warranty, of this
// Bundle/Combinatorics-Framework. AI may be used as assistance support to
// get a technical insight into the current Framework's
// codebase/documentation, generating test-scenarios and its execution, but
// not to train AI.
//
// (c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,
// Ukraine
//
// See LICENSE and NOTICE.md for the binding terms.

package org.ghyura.cf.desktop;

import org.ghyura.cf.desktop.session.ConstraintEditing;
import org.ghyura.cf.desktop.session.FormulaBlocks;
import org.ghyura.cf.model.rule.RuleValue;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.Timer;
import java.awt.Font;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * The rule editor of the constraints tab: the legacy per-link editor, formula dialog and relation dialog in one panel. It
 * edits the selected rule's polarity and gate, a when rule's sheets and formula (as text or as blocks), an assert rule's
 * condition tree and any rule's "only when" condition tree, nested to any depth; or it drafts a new formula or assert rule.
 * What a run would refuse shows while the draft is edited (the sidecar read as a run reads it, the draft applied to a copy);
 * Apply puts the draft into the edited sidecar, which Check impact counts and Save writes.
 */
final class RuleEditor {

    static final String READS = "Reads as a run reads it. Apply puts it into the sidecar; Check impact counts what it removes.";
    static final String INCOMPLETE = "Complete the formula (empty slots)";

    private enum Mode { NONE, EDIT, NEW_WHEN, NEW_ASSERT }

    private final WrapLayout.Tracking body = new WrapLayout.Tracking(null);
    private final JScrollPane scroll = new JScrollPane(body);
    private final JLabel title = new JLabel("Select a rule in the list to edit it, or start a new formula or assert rule.");
    private final JComboBox<String> polarity = Hints.of(new JComboBox<>(new String[]{"forbid", "require"}), "Forbid: the values this rule describes must never appear together. Require: they must appear together.");
    private final JComboBox<String> gateKind = Hints.of(new JComboBox<>(new String[]{"any", "adjacent", "within"}), "When the rule applies: always (any), only when its sheets are next to each other (adjacent), or only within a distance (within).");
    private final JSpinner within = Hints.of(new JSpinner(new SpinnerNumberModel(2, 1, 1000, 1)), "The largest distance between the sheets when the rule applies only within a distance.");
    private final JPanel gateRow = WrapLayout.row();
    private final JPanel sheetsRow = WrapLayout.row();
    private final Map<String, JCheckBox> sheetChecks = new LinkedHashMap<>();
    private final FormulaEditor formula = new FormulaEditor(this::changed);
    private final ConditionEditor assertion = new ConditionEditor("Assert:", "no condition yet: + condition starts one", this::changed);
    private final ConditionEditor condition = new ConditionEditor("Only when:", "no condition: the rule applies everywhere", this::changed);
    private final JTextArea problems = Hints.of(new JTextArea(3, 40), "What is wrong with the rule as it is written now. Filled when the rule has a problem; empty while it is fine.");
    private final JButton apply = Hints.of(new JButton("Apply"), "Use the rule as written. Not possible while it has a problem.");
    private final JButton revert = Hints.of(new JButton("Revert"), "Throw away the changes to this rule.");
    private final Timer debounce = new Timer(250, e -> check());
    private final IntConsumer applied;
    private ConstraintEditing editing;
    private Map<String, List<String>> values = Map.of();
    private Mode mode = Mode.NONE;
    private int rule = -1;
    private String kind = "";
    private boolean updating;
    private ConstraintEditing.Draft start;

    /** @param applied told the index the applied rule has afterwards, to show it */
    RuleEditor(IntConsumer applied) {
        this.applied = applied;
        debounce.setRepeats(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        FieldNames.section(body, "Rule editor");
        body.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        JPanel polarityRow = WrapLayout.row();
        polarityRow.add(FieldNames.label("Polarity:", polarity));
        polarityRow.add(polarity);
        gateRow.add(FieldNames.label("applies:", gateKind));
        gateRow.add(gateKind);
        gateRow.add(FieldNames.label("distance:", within));
        gateRow.add(within);
        polarityRow.add(gateRow);
        polarity.addActionListener(e -> changed());
        gateKind.addActionListener(e -> {
            within.setEnabled("within".equals(gateKind.getSelectedItem()));
            changed();
        });
        within.addChangeListener(e -> changed());
        problems.setEditable(false);
        problems.setLineWrap(true);
        problems.setWrapStyleWord(true);
        problems.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        apply.addActionListener(e -> apply());
        revert.addActionListener(e -> revert());
        JPanel actions = WrapLayout.row();
        actions.add(apply);
        actions.add(revert);
        JScrollPane problemsScroll = FieldNames.titled(problems, "Problems with this rule");
        for (JComponent c : List.of(title, polarityRow, sheetsRow, formula.component(), assertion.component(), condition.component(),
                problemsScroll, actions)) {
            c.setAlignmentX(0f);
            body.add(c);
        }
        show();
    }

    JComponent component() {
        return scroll;
    }

    /** Shows nothing to edit. */
    void clear() {
        mode = Mode.NONE;
        rule = -1;
        show();
    }

    /** Edits a rule of the sidecar, by its index in the rules. */
    void edit(ConstraintEditing sidecar, int index, Map<String, List<String>> sheets) {
        editing = sidecar;
        values = new LinkedHashMap<>(sheets);
        mode = Mode.EDIT;
        rule = index;
        ConstraintEditing.Rule r = sidecar.rules().get(index);
        kind = r.kind();
        title.setText(r.id() + " (" + kind + " rule" + (r.bonds().size() > 1 ? " of " + r.bonds().size() + " bonds" : "") + ")");
        fill(sidecar.draft(index));
    }

    /** Drafts a new formula rule (the legacy formula dialog): the polarity and gate the bond matrix sets, no sheets yet. */
    void newWhen(ConstraintEditing sidecar, Map<String, List<String>> sheets, String polarityName, RuleValue gate) {
        editing = sidecar;
        values = new LinkedHashMap<>(sheets);
        mode = Mode.NEW_WHEN;
        rule = -1;
        kind = "when";
        title.setText("New formula rule: tick two sheets or more and write the formula over their parameters");
        start = new ConstraintEditing.Draft(polarityName, gate, null, List.of(), "", null);
        fill(start);
    }

    /** Drafts a new assert rule (the legacy relation dialog), starting from "the first sheet is present". */
    void newAssert(ConstraintEditing sidecar, Map<String, List<String>> sheets, String polarityName) {
        editing = sidecar;
        values = new LinkedHashMap<>(sheets);
        mode = Mode.NEW_ASSERT;
        rule = -1;
        kind = "assert";
        title.setText("New assert rule: the condition tree must hold (require) or must not (forbid)");
        String first = values.isEmpty() ? "" : values.keySet().iterator().next();
        start = new ConstraintEditing.Draft(polarityName, null, null, null, null,
                org.ghyura.cf.desktop.session.ConditionBlocks.leaf(first, org.ghyura.cf.model.rule.Condition.Op.PRESENT, new RuleValue.Bool(true)));
        fill(start);
    }

    private void fill(ConstraintEditing.Draft d) {
        updating = true;
        try {
            polarity.setSelectedItem(d.polarity());
            RuleValue gate = d.gate() == null ? new RuleValue.MapValue(Map.of()) : d.gate();
            Map<String, RuleValue> g = gate instanceof RuleValue.MapValue m ? m.entries() : Map.of();
            if (g.containsKey("adjacent") && g.get("adjacent") instanceof RuleValue.Bool b && b.value()) {
                gateKind.setSelectedItem("adjacent");
            } else if (g.containsKey("within")) {
                gateKind.setSelectedItem("within");
                within.setValue(g.get("within") instanceof RuleValue.Int n ? Math.max(1, n.value().min(BigInteger.valueOf(1000)).intValue()) : 2);
            } else {
                gateKind.setSelectedItem("any");
            }
            within.setEnabled("within".equals(gateKind.getSelectedItem()));
            sheetsRow.removeAll();
            sheetChecks.clear();
            sheetsRow.add(new JLabel("Sheets (≥2):"));
            List<String> names = new ArrayList<>(values.keySet());
            d.sheets().stream().filter(s -> !names.contains(s)).forEach(names::add);
            for (String s : names) {
                JCheckBox box = Hints.of(new JCheckBox(s, d.sheets().contains(s)), "Whether the rule looks at the values of sheet " + s + ".");
                box.addActionListener(e -> {
                    formula.offer(offered());
                    changed();
                });
                sheetChecks.put(s, box);
                sheetsRow.add(box);
            }
            formula.show(d.when(), offered(), s -> FormulaBlocks.paramsOf(s, editing.params()));
            assertion.show(d.assertion(), values);
            condition.show(d.condition(), values);
        } finally {
            updating = false;
        }
        show();
        check();
    }

    /** Shows the parts of the kind of rule edited. */
    private void show() {
        boolean some = mode != Mode.NONE;
        boolean when = some && kind.equals("when");
        boolean asserts = some && kind.equals("assert");
        polarity.getParent().setVisible(some);
        gateRow.setVisible(some && !asserts);
        sheetsRow.setVisible(when);
        formula.component().setVisible(when);
        assertion.component().setVisible(asserts);
        condition.component().setVisible(some);
        problems.getParent().getParent().setVisible(some);
        apply.getParent().setVisible(some);
        if (!some) {
            title.setText("Select a rule in the list to edit it, or start a new formula or assert rule.");
            problems.setText("");
        }
        body.revalidate();
        body.repaint();
    }

    /** The checked sheets in the scenario's order; all of them while none is checked (for the parameter blocks). */
    private List<String> offered() {
        List<String> checked = checked();
        return checked.isEmpty() ? List.copyOf(values.keySet()) : checked;
    }

    private List<String> checked() {
        return sheetChecks.entrySet().stream().filter(e -> e.getValue().isSelected()).map(Map.Entry::getKey).toList();
    }

    private void changed() {
        if (!updating && mode != Mode.NONE) {
            debounce.restart();
        }
    }

    /** The draft the controls hold; null while the blocks have an empty slot. */
    ConstraintEditing.Draft draft() {
        String when = null;
        if (kind.equals("when")) {
            when = formula.formula();
            if (when == null) {
                return null;
            }
        }
        RuleValue gate = ConstraintEditing.gate((String) gateKind.getSelectedItem(), (Integer) within.getValue());
        return new ConstraintEditing.Draft((String) polarity.getSelectedItem(), gate, condition.condition(), checked(), when,
                assertion.condition());
    }

    /** What a run would refuse with the draft applied, shown below the editor. */
    void check() {
        debounce.stop();
        if (mode == Mode.NONE || editing == null) {
            problems.setText("");
            return;
        }
        ConstraintEditing.Draft d = draft();
        if (d == null) {
            problems.setText(INCOMPLETE);
            return;
        }
        ConstraintEditing tried = editing.copy();
        List<String> found;
        try {
            applyTo(tried, d);
            found = tried.problems();
        } catch (IllegalArgumentException e) {
            found = List.of(e.getMessage());
        }
        problems.setText(found.isEmpty() ? READS : String.join("\n", found));
        problems.setCaretPosition(0);
    }

    private int applyTo(ConstraintEditing target, ConstraintEditing.Draft d) {
        return switch (mode) {
            case EDIT -> target.apply(rule, d);
            case NEW_WHEN -> target.apply(target.addWhen(d.sheets(), d.polarity(), d.gate(), d.when()), d);
            case NEW_ASSERT -> target.apply(target.addAssert(d.polarity(), d.assertion()), d);
            case NONE -> throw new IllegalStateException("nothing is edited");
        };
    }

    /**
     * Puts the draft into the edited sidecar; refused with the reason (shown) while it is incomplete, refused as the legacy
     * dialogs refused it, or adds a problem a run would refuse the sidecar for (the legacy page let such a formula through
     * to the sieve; here the edited sidecar stays one that Check impact and Save can read).
     */
    Integer apply() {
        debounce.stop();
        if (mode == Mode.NONE || editing == null) {
            return null;
        }
        ConstraintEditing.Draft d = draft();
        if (d == null) {
            problems.setText(INCOMPLETE);
            return null;
        }
        ConstraintEditing tried = editing.copy();
        try {
            applyTo(tried, d);
        } catch (IllegalArgumentException e) {
            problems.setText(e.getMessage());
            return null;
        }
        List<String> added = new ArrayList<>(tried.problems());
        added.removeAll(editing.problems());
        if (!added.isEmpty()) {
            problems.setText(String.join("\n", added));
            return null;
        }
        int index = applyTo(editing, d);
        applied.accept(index);
        return index;
    }

    /** Starts the draft over from the rule as the sidecar holds it (a new rule from its start). */
    void revert() {
        switch (mode) {
            case EDIT -> edit(editing, rule, values);
            case NEW_WHEN, NEW_ASSERT -> fill(start);
            case NONE -> { }
        }
    }

    /** Scrolls the editor to its "only when" condition. */
    void revealCondition() {
        body.scrollRectToVisible(condition.component().getBounds());
    }

    // ---- for tests ---------------------------------------------------------------------------------------------------

    FormulaEditor formula() {
        return formula;
    }

    ConditionEditor assertion() {
        return assertion;
    }

    ConditionEditor condition() {
        return condition;
    }

    String problems() {
        return problems.getText();
    }

    String title() {
        return title.getText();
    }

    void setSheets(List<String> sheets) {
        sheetChecks.forEach((s, box) -> box.setSelected(sheets.contains(s)));
        formula.offer(offered());
    }

    void setPolarity(String name) {
        polarity.setSelectedItem(name);
    }

    void setGate(String gate, int n) {
        gateKind.setSelectedItem(gate);
        within.setValue(n);
    }
}
