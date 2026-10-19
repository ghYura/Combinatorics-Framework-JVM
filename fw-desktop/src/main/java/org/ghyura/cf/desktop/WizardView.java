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

import org.ghyura.cf.desktop.session.GuidedScenario;
import org.ghyura.cf.desktop.session.GuidedScenario.Bootstrap;
import org.ghyura.cf.desktop.session.GuidedScenario.Factor;
import org.ghyura.cf.desktop.session.GuidedScenario.Kind;
import org.ghyura.cf.desktop.session.GuidedScenario.Role;
import org.ghyura.cf.desktop.session.GuidedScenario.Rule;
import org.ghyura.cf.desktop.session.GuidedScenario.Selection;
import org.ghyura.cf.desktop.session.GuidedScenario.Situation;
import org.ghyura.cf.desktop.session.GuidedScenario.Use;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.spi.Cancellation;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Step 0, the wizard: composes a first scenario by asking what a newcomer knows. What is tested (a situation with an
 * example to start from), the things that vary and their values, what each is (a static parameter, a sequence of actions or
 * a sudden action, which a selection treats apart), how one test case uses each of them, what can never or must always come
 * together, how many test cases are enough, and the bootstrap (FW_RunMeFirstOnce) that runs once, first, for all of them. Every answer is shown at once as the FW_Seq instructions
 * that fit it (a sequence in a row when one instruction is not enough), their exact count and the first ways they give;
 * the rules page says when to sieve and when not to, the selection page when a covering selection helps. The Framework
 * checks the design as a run would count it; saved, the scenario goes to step 3 with its selection, to step 5 for its test
 * cases or to step 2 for editing. Checking and saving stay off the event thread.
 */
final class WizardView {

    /** What the wizard hands to the other steps. */
    interface Handoff {
        /** Makes the scenario the one of step 3, with the design's selection in step 3's run settings; returns their status. */
        String use(Path scenario, Map<String, String> selection);

        /** Shows step 5 with the test cases of step 3's scenario. */
        void testCases();

        /** Opens the scenario in step 2's editor. */
        void edit(Path scenario);
    }

    private static final Color PROBLEM = new Color(0xb42318);
    private static final Color READY = new Color(0x1a7f37);
    private static final String[] PAGES = {"Situation", "Factors", "How each varies", "Rules (sieve)", "How many", "Bootstrap", "Finish"};

    private final Supplier<Session> session;
    private final Navigator navigator;
    private final Handoff handoff;
    private GuidedScenario design = GuidedScenario.empty();
    /** The factor pages 0.2 and 0.3 show, or -1. */
    private int selected = -1;
    /** While the view writes the design into its fields, their listeners leave the design alone. */
    private boolean updating;
    /** The field whose typing changes the design now: its document is notifying, so it is not written back. */
    private JTextField typing;
    private boolean busy;
    private Path saved;
    private GuidedScenario savedDesign;

    private final JPanel panel = new JPanel(new BorderLayout(6, 6));
    private final JTabbedPane pages = new JTabbedPane();
    private final JButton back = Hints.of(new JButton("Back"), "Go back to the previous page of the wizard; nothing you entered is lost.");
    private final JButton next = Hints.of(new JButton("Next"), "Go on to the next page of the wizard; you can come back to any page.");
    private final JLabel summary = new JLabel(" ");
    private final JLabel problemLine = new JLabel(" ");

    // 0.1 situation
    private final JTextField nameField = FieldNames.placeholder(Hints.of(new JTextField(GuidedScenario.FIRST_NAME, 24),
            "The name of your scenario: its file is named after it, and so is the program each test case is. Filled with "
            + GuidedScenario.FIRST_NAME + " at first, or by typing letters, digits, _ or -."), "for example: checkout_first");
    private final List<JRadioButton> situations = new ArrayList<>();
    private final JTextArea situationAdvice = text(4, "What fits the chosen situation: the instructions such scenarios use, and why. "
            + "Filled when you choose a situation above.");
    private final JButton fill = Hints.of(new JButton("Fill in this example"), "Replace the factors and rules with the example of the "
            + "chosen situation, so that you start from a complete scenario and change it to yours.");

    // 0.2 factors
    private final ListTableModel<Factor> factorRows = new ListTableModel<>(List.of("Factor", "Kind", "Values", "One test case takes",
            "Ways"), List.<Function<Factor, Object>>of(Factor::name, f -> f.role().words(), Factor::valuesText, Factor::shape,
            f -> GuidedScenario.figure(f.rows())));
    private final JTable factorTable = Hints.of(new JTable(factorRows), "The factors of your scenario, each with its values, how one "
            + "test case uses them and the ways that gives. Select one to change it below.");
    private final JTextField factorName = FieldNames.placeholder(Hints.of(new JTextField(18), "The name of the selected factor: one "
            + "word, such as browser or payment. Filled when you select a factor in the table, and by typing."), "for example: browser");
    private final JTextField factorValues = FieldNames.placeholder(Hints.of(new JTextField(40), "The values the selected factor can "
            + "take, separated by commas, such as chrome, firefox, safari. Filled when you select a factor in the table, and by typing."),
            "for example: chrome, firefox, safari");
    private final JComboBox<Role> role = Hints.of(new JComboBox<>(Role.values()), "What the selected factor is: a static parameter, set "
            + "before any action and covered by pairwise; a sequence of actions, done in order; or a sudden action, which may or may "
            + "not happen.");

    // 0.3 how each varies
    private final DefaultListModel<String> factorNames = new DefaultListModel<>();
    private final JList<String> describe = Hints.of(new JList<>(factorNames), "The factors of your scenario. Select one to say how one "
            + "test case uses its values.");
    private final JRadioButton one = Hints.of(new JRadioButton("Exactly one of them"), "Each test case takes one value of this factor, "
            + "like one browser.");
    private final JRadioButton exactly = Hints.of(new JRadioButton("A fixed number of them"), "Each test case takes the same number of "
            + "this factor's values, set beside it, like two coupons.");
    private final JRadioButton range = Hints.of(new JRadioButton("Any number of them, within bounds"), "Each test case takes some of this "
            + "factor's values, from the fewest to the most set beside it, like any mix of options.");
    private final JSpinner count = Hints.of(new JSpinner(new SpinnerNumberModel(2, 1, 64, 1)), "How many values each test case takes "
            + "when the number is fixed.");
    private final JSpinner fewest = Hints.of(new JSpinner(new SpinnerNumberModel(0, 0, 64, 1)), "The fewest values a test case takes; 0 "
            + "allows none of them.");
    private final JSpinner most = Hints.of(new JSpinner(new SpinnerNumberModel(2, 0, 64, 1)), "The most values a test case takes; at most "
            + "as many as the factor has.");
    private final JRadioButton group = Hints.of(new JRadioButton("No: they form a group, and a+b is the same as b+a"), "The order of "
            + "the values does not matter: each group of values is one test case.");
    private final JRadioButton sequence = Hints.of(new JRadioButton("Yes: they form a sequence, and a+b differs from b+a"), "The order "
            + "matters, like steps of a workflow: each order of the values is a test case of its own.");
    private final JCheckBox repeat = Hints.of(new JCheckBox("A value may appear more than once"), "Allows a value twice in one test "
            + "case, like two of the same book in a cart. Only with a fixed number of values.");
    private final JCheckBox optional = Hints.of(new JCheckBox("It may or may not happen: a sudden action"), "Makes the factor a "
            + "sudden action, like a timeout: test cases are made with it and without it, in every combination with the other sudden "
            + "actions.");
    private final JTextArea meaning = text(9, "What your answers mean: the kind of freedom, the instructions in the factor's FW_Seq row, "
            + "how its ways are counted, why the instructions are written so, and the first ways. Filled from your answers above.");

    // 0.4 rules
    private final JComboBox<Kind> kind = Hints.of(new JComboBox<>(Kind.values()), "never together: the two values never meet in a test "
            + "case. needs: a test case with the first value must have the second too.");
    private final DefaultComboBoxModel<String> ruleFactors = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<String> ruleValues = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<String> otherFactors = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<String> otherValues = new DefaultComboBoxModel<>();
    private final JComboBox<String> ruleFactor = Hints.of(new JComboBox<>(ruleFactors), "The factor of the rule's first value.");
    private final JComboBox<String> ruleValue = Hints.of(new JComboBox<>(ruleValues), "The rule's first value, one of that factor's values.");
    private final JComboBox<String> otherFactor = Hints.of(new JComboBox<>(otherFactors), "The factor of the rule's second value; it may "
            + "be the same factor when a test case takes several of its values.");
    private final JComboBox<String> otherValue = Hints.of(new JComboBox<>(otherValues), "The rule's second value, one of that factor's values.");
    private final JLabel sentence = new JLabel(" ");
    private final ListTableModel<Rule> ruleRows = new ListTableModel<>(List.of("Rule", "As the constraints file writes it"),
            List.<Function<Rule, Object>>of(Rule::words, Rule::written));
    private final JTable ruleTable = Hints.of(new JTable(ruleRows), "The rules of your scenario, each in words and as the constraints "
            + "file beside the scenario holds it. Select one to remove it.");

    // 0.5 how many
    private final JTextArea counts = text(9, "How many combinations your answers make, which selection fits them and why, and what the "
            + "chosen selection keeps. Filled from your answers on every page.");
    private final JRadioButton every = Hints.of(new JRadioButton("Every combination"), "Every combination becomes a test case: best when "
            + "they are a few hundred at most.");
    private final JRadioButton pairs = Hints.of(new JRadioButton("Every pair of values (pairwise)"), "Keep enough combinations that "
            + "every value of each factor meets every value of every other factor at least once.");
    private final JRadioButton triples = Hints.of(new JRadioButton("Every triple of values"), "Keep enough combinations that every three "
            + "values of any three factors meet at least once: more test cases than pairwise.");
    private final JRadioButton within = Hints.of(new JRadioButton("The strongest covering within a number of rows"), "The Framework picks "
            + "the strongest covering whose rows fit the number beside it.");
    private final JSpinner budget = Hints.of(new JSpinner(new SpinnerNumberModel(100, 1, 1_000_000, 10)), "The most rows the covering "
            + "keeps; optional factors then multiply them.");
    private final JCheckBox smaller = Hints.of(new JCheckBox("Search harder for fewer test cases (set-cover)"), "Takes, each time, the "
            + "combination that covers most of what is still missing: usually fewer test cases, a little slower.");
    private final JCheckBox allOrders = Hints.of(new JCheckBox("Run every order of each sequence of actions with each kept combination",
            true), "Keeps the sequences of actions apart from the selection, which then covers the static parameters only; untick it "
            + "to let the selection choose orders as it chooses values, for fewer test cases.");

    // 0.6 bootstrap
    private final JCheckBox bootstrap = Hints.of(new JCheckBox("Run a bootstrap once, first, before all the test cases "
            + "(FW_RunMeFirstOnce)", true), "Adds the bootstrap: code that runs once per run, before any test case, to prepare what "
            + "they all share and hand them their arguments.");
    private final JTextField arguments = FieldNames.placeholder(Hints.of(new JTextField(40), "The arguments the bootstrap receives, "
            + "separated by spaces: the FW_Arguments line, such as an address and a region. Filled by typing; empty gives none."),
            "for example: http://localhost:8080 EU");
    private final JTextArea bootstrapCode = text(10, "The code the wizard writes into the FW_RunMeFirstOnce sheet. Filled from the "
            + "scenario's name and the choice above; change it in step 2 after saving.");

    // 0.7 finish
    private final JCheckBox check = Hints.of(new JCheckBox("Add a check sheet where your test decides its verdict", true), "Adds the "
            + "sheet check: the place to call the system you test and say whether it misbehaved. Its verdict code is named "
            + GuidedScenario.CHECK_FAILED + ".");
    private final ListTableModel<String[]> sheetRows = new ListTableModel<>(List.of("Sheet", "Kind", "FW_Seq row after the sheet's name",
            "Values", "Ways"), List.<Function<String[], Object>>of(r -> r[0], r -> r[1], r -> r[2], r -> r[3], r -> r[4]));
    private final JTable sheetTable = Hints.of(new JTable(sheetRows), "The sheets the wizard writes, in the order the test cases' code "
            + "follows: each sheet's FW_Seq row, its values and its ways.");
    private final JButton checkButton = Hints.of(new JButton("Check with the Framework"), "Let the Framework plan and count the "
            + "scenario as a run would, with its rules and selection, and show the first test case's code. Nothing runs.");
    private final JButton saveButton = Hints.of(new JButton("Save the scenario..."), "Choose where the scenario goes: the wizard writes it "
            + "with its rules file and its settings file beside it.");
    private final JButton useButton = Hints.of(new JButton("Use it in step 3"), "Make the saved scenario the one of step 3, with its "
            + "selection in step 3's run settings, and go there.");
    private final JButton casesButton = Hints.of(new JButton("Show its test cases (step 5)"), "Make the saved scenario the one of step 3 "
            + "and show its test cases, with their code, in step 5.");
    private final JButton editButton = Hints.of(new JButton("Open it in step 2"), "Open the saved scenario in step 2's editor, to change "
            + "its sheets and values by hand.");
    private final JTextArea output = text(10, "What the Framework counted, the problems to fix, and the files the wizard wrote. Filled "
            + "by Check with the Framework, by Save and by the buttons that hand the scenario on.");

    WizardView(Supplier<Session> session, Navigator navigator, Handoff handoff) {
        this.session = session;
        this.navigator = navigator;
        this.handoff = handoff;
        Steps.add(pages, "0.1", PAGES[0], situationPage(), "Page 0.1 · Situation: name your scenario and say what you are testing, then "
                + "start from its example or from nothing.");
        Steps.add(pages, "0.2", PAGES[1], factorsPage(), "Page 0.2 · Factors: the things that vary between test cases, each with its "
                + "values.");
        Steps.add(pages, "0.3", PAGES[2], variesPage(), "Page 0.3 · How each varies: for each factor, how many of its values one test case "
                + "uses and whether their order matters; the wizard answers with the instructions that fit.");
        Steps.add(pages, "0.4", PAGES[3], rulesPage(), "Page 0.4 · Rules (sieve): pairs of values that can never meet, or where one needs "
                + "the other, removed before any test runs.");
        Steps.add(pages, "0.5", PAGES[4], howManyPage(), "Page 0.5 · How many: every combination, or a covering selection that keeps "
                + "fewer while every pair or triple of values still meets.");
        Steps.add(pages, "0.6", PAGES[5], bootstrapPage(), "Page 0.6 · Bootstrap: FW_RunMeFirstOnce, the code that runs once, and "
                + "really first, for all the test cases a run executes after it; and the arguments it receives.");
        Steps.add(pages, "0.7", PAGES[6], finishPage(), "Page 0.7 · Finish: the sheets the wizard writes; check them with the Framework, "
                + "save them and hand the scenario on to steps 3, 5 or 2.");
        pages.addChangeListener(e -> steps());
        back.addActionListener(e -> pages.setSelectedIndex(Math.max(0, pages.getSelectedIndex() - 1)));
        next.addActionListener(e -> pages.setSelectedIndex(Math.min(pages.getTabCount() - 1, pages.getSelectedIndex() + 1)));
        JPanel moves = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        moves.add(back);
        moves.add(next);
        JPanel lines = new JPanel(new BorderLayout(0, 2));
        lines.add(summary, BorderLayout.NORTH);
        lines.add(problemLine, BorderLayout.SOUTH);
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.add(moves, BorderLayout.WEST);
        bottom.add(lines, BorderLayout.CENTER);
        panel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        panel.add(pages, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        refresh();
        steps();
    }

    JComponent component() {
        return panel;
    }

    // ---- pages --------------------------------------------------------------------------------------------------------

    private JComponent situationPage() {
        JPanel page = FieldNames.section(new JPanel(new BorderLayout(6, 6)), "0.1 · Situation");
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(2, 4, 6, 4);
        top.add(heading("The wizard composes your first scenario in seven short pages. A test case is one combination of values: the "
                + "wizard asks what varies and what kind of thing each is (a static parameter, a sequence of actions or a sudden "
                + "action), how one test case uses it, what must not happen, how many test cases are enough, and what the bootstrap "
                + "prepares once, first, for all of them. Every answer shows at once how many test cases it makes; nothing runs until "
                + "you choose to."), c);
        c.gridy = 1; c.gridwidth = 1; c.insets = new Insets(2, 4, 2, 4);
        top.add(FieldNames.label("Scenario name:", nameField), c);
        c.gridx = 1; c.fill = GridBagConstraints.NONE;
        top.add(nameField, c);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 2;
        top.add(new JLabel("What are you testing?"), c);
        ButtonGroup choice = new ButtonGroup();
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        for (Situation s : Situation.values()) {
            JRadioButton button = Hints.of(new JRadioButton(s.title()), "Choose this if it is closest to what you test: "
                    + s.advice().substring(0, s.advice().indexOf('.') + 1));
            button.addActionListener(e -> situationChosen());
            choice.add(button);
            situations.add(button);
            list.add(button);
        }
        situations.getFirst().setSelected(true);
        c.gridy = 3;
        top.add(list, c);
        c.gridy = 4; c.insets = new Insets(4, 4, 2, 4);
        top.add(fill, c);
        fill.addActionListener(e -> fill(situation()));
        page.add(leftAligned(top), BorderLayout.NORTH);
        page.add(FieldNames.titled(situationAdvice, "What fits this situation"), BorderLayout.CENTER);
        nameField.getDocument().addDocumentListener(typed(nameField, () -> change(design.named(nameField.getText().strip()))));
        situationChosen();
        return page;
    }

    private JComponent factorsPage() {
        JPanel page = FieldNames.section(new JPanel(new BorderLayout(6, 6)), "0.2 · Factors");
        page.add(heading("A factor is one thing that varies between test cases; its values are what it can be. Say what kind it is: "
                + "a static parameter is set before any action, like the browser (chrome, firefox, safari); a sequence of actions is "
                + "done in order, like the steps of a workflow (login, search, pay); a sudden action may or may not happen, like a "
                + "timeout. A selection treats them apart: pairwise covers the static parameters. Each factor becomes a sheet of the "
                + "scenario and each value a row of it."), BorderLayout.NORTH);
        factorTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        factorTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating && factorTable.getSelectedRow() >= 0) select(factorTable.getSelectedRow());
        });
        page.add(TableTitles.titled(factorTable, "Factors of the scenario: their values, how a test case uses them and their ways"),
                BorderLayout.CENTER);
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(2, 4, 2, 4);
        form.add(FieldNames.label("Factor name:", factorName), c);
        form.add(factorName, c);
        form.add(FieldNames.label("Kind of factor:", role), c);
        form.add(role, c);
        form.add(FieldNames.label("Values, separated by commas:", factorValues), c);
        c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        form.add(factorValues, c);
        role.addActionListener(e -> {
            if (!updating && selected >= 0 && role.getSelectedItem() instanceof Role r && r != current().role()) {
                change(design.withFactor(selected, current().as(r)));
            }
        });
        JButton add = Hints.of(new JButton("Add a factor"), "Add a new static parameter with two example values, then name it, say its "
                + "kind and type its values.");
        add.addActionListener(e -> addFactor());
        JButton remove = Hints.of(new JButton("Remove the factor"), "Remove the selected factor, with the rules that name it.");
        remove.addActionListener(e -> removeFactor());
        JButton up = Hints.of(new JButton("Move up"), "Move the selected factor up: the test cases' code follows the factors' order.");
        up.addActionListener(e -> move(-1));
        JButton down = Hints.of(new JButton("Move down"), "Move the selected factor down: the test cases' code follows the factors' order.");
        down.addActionListener(e -> move(1));
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(form, BorderLayout.NORTH);
        south.add(row(add, remove, up, down), BorderLayout.CENTER);
        south.add(FieldNames.note("Values are plain words; a value may hold spaces but no quote, backslash or comma. Rules and the "
                + "test cases' results name them as you write them."), BorderLayout.SOUTH);
        page.add(south, BorderLayout.SOUTH);
        factorName.getDocument().addDocumentListener(typed(factorName, () -> {
            if (selected >= 0) change(design.withFactor(selected, current().named(factorName.getText().strip())));
        }));
        factorValues.getDocument().addDocumentListener(typed(factorValues, () -> {
            if (selected >= 0) change(design.withFactor(selected, current().withValues(Factor.split(factorValues.getText()))));
        }));
        return page;
    }

    private JComponent variesPage() {
        JPanel page = FieldNames.section(new JPanel(new BorderLayout(6, 6)), "0.3 · How each varies");
        page.add(heading("For each factor, say how one test case uses its values. The wizard answers with the instructions of the "
                + "factor's FW_Seq row: the verb is dictated by the shape of the freedom, never chosen to hit a number."),
                BorderLayout.NORTH);
        describe.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        describe.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updating && describe.getSelectedIndex() >= 0) select(describe.getSelectedIndex());
        });
        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.add(FieldNames.label("Factor to describe:", describe), BorderLayout.NORTH);
        left.add(new JScrollPane(describe), BorderLayout.CENTER);
        JPanel questions = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 4; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(1, 4, 1, 4);
        questions.add(new JLabel("How many of its values does one test case use?"), c);
        ButtonGroup how = new ButtonGroup();
        for (JRadioButton b : List.of(one, exactly, range)) how.add(b);
        c.gridy = 1;
        questions.add(one, c);
        c.gridy = 2; c.gridwidth = 1;
        questions.add(exactly, c);
        c.gridx = 1;
        questions.add(FieldNames.label("Number of values:", count), c);
        c.gridx = 2;
        questions.add(count, c);
        c.gridx = 0; c.gridy = 3;
        questions.add(range, c);
        c.gridx = 1;
        questions.add(FieldNames.label("At least:", fewest), c);
        c.gridx = 2;
        questions.add(fewest, c);
        c.gridx = 3;
        JPanel atMost = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        atMost.add(FieldNames.label("At most:", most));
        atMost.add(most);
        questions.add(atMost, c);
        c.gridx = 0; c.gridy = 4; c.gridwidth = 4; c.insets = new Insets(6, 4, 1, 4);
        questions.add(new JLabel("Does their order matter?"), c);
        ButtonGroup order = new ButtonGroup();
        order.add(group);
        order.add(sequence);
        c.insets = new Insets(1, 4, 1, 4);
        c.gridy = 5;
        questions.add(group, c);
        c.gridy = 6;
        questions.add(sequence, c);
        c.gridy = 7; c.insets = new Insets(6, 4, 1, 4);
        questions.add(repeat, c);
        c.gridy = 8; c.insets = new Insets(1, 4, 1, 4);
        questions.add(optional, c);
        for (AbstractButton b : List.of(one, exactly, range, group, sequence, repeat, optional)) b.addActionListener(e -> answered());
        for (JSpinner s : List.of(count, fewest, most)) s.addChangeListener(e -> answered());
        JPanel right = new JPanel(new BorderLayout(0, 6));
        right.add(leftAligned(questions), BorderLayout.NORTH);
        right.add(FieldNames.titled(meaning, "What your answers mean"), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.2);
        JTable guide = Hints.of(new JTable(guideRows()), "Every kind of freedom the wizard knows, with the instructions of its FW_Seq row "
                + "and how its ways are counted for n values.");
        JScrollPane guidePane = TableTitles.titled(guide, "Which instructions fit which freedom: the FW_Seq row and its ways for n values");
        guidePane.setPreferredSize(new java.awt.Dimension(400, 190));
        JSplitPane all = new JSplitPane(JSplitPane.VERTICAL_SPLIT, split, guidePane);
        all.setResizeWeight(0.7);
        page.add(all, BorderLayout.CENTER);
        return page;
    }

    /** The freedoms the wizard knows and their instructions, as page 0.3's guide shows them. */
    private static ListTableModel<String[]> guideRows() {
        ListTableModel<String[]> rows = new ListTableModel<>(List.of("One test case takes", "FW_Seq row", "Ways for n values"),
                List.<Function<String[], Object>>of(r -> r[0], r -> r[1], r -> r[2]));
        rows.show(List.of(
                new String[]{"exactly one value", "FW_Combi(1)", "n"},
                new String[]{"k values as a group", "FW_Combi(k)", "C(n,k)"},
                new String[]{"k values in a row: a sequence in a row", "FW_Combi(k), FW_Permut()", "P(n,k) = n·(n−1)·…"},
                new String[]{"all values in every order", "FW_Permut", "n!"},
                new String[]{"k values that may repeat, as a group", "FW_CombiR(k), FW_Combi(k)", "C(n+k−1,k)"},
                new String[]{"k values that may repeat, in a row", "FW_PermutR(k), FW_Combi(k)", "n^k"},
                new String[]{"a group of a to b values", "FW_Subsets_RANGE(a,b)", "C(n,a) + … + C(n,b); 2^n for 0 to n"},
                new String[]{"a to b values in a row", "FW_Subsets_RANGE(a,b), FW_Permut", "P(n,a) + … + P(n,b), from 1 value"},
                new String[]{"any of these, or left out", "FW_Optional before them", "their ways + 1, after the selection"},
                new String[]{"several values in one test case", "FW_Concatenator=+ before them", "joins them as a+b"}));
        return rows;
    }

    private JComponent rulesPage() {
        JPanel page = FieldNames.section(new JPanel(new BorderLayout(6, 6)), "0.4 · Rules (sieve)");
        page.add(heading("Rules, the sieve, remove combinations before any test runs. Write one when a combination cannot happen or "
                + "makes no sense for your system, like safari on linux, or paying with nothing in the cart. Do not write rules to have "
                + "fewer test cases: a rule that removes valid combinations can hide a defect. When valid combinations are only too "
                + "many, keep them and let a selection choose (page 0.5); when a factor is freer than it really is, change its answers "
                + "(page 0.3)."), BorderLayout.NORTH);
        // the rule reads as a sentence: this value of this factor, never together with or needs, that value of that factor
        JPanel form = WrapLayout.row();
        form.add(FieldNames.label("Factor:", ruleFactor));
        form.add(ruleFactor);
        form.add(FieldNames.label("Value:", ruleValue));
        form.add(ruleValue);
        form.add(Box.createHorizontalStrut(8));
        form.add(FieldNames.label("Kind of rule:", kind));
        form.add(kind);
        form.add(Box.createHorizontalStrut(8));
        form.add(FieldNames.label("Other factor:", otherFactor));
        form.add(otherFactor);
        form.add(FieldNames.label("Other value:", otherValue));
        form.add(otherValue);
        ruleFactor.addActionListener(e -> {
            if (!updating) values(ruleValues, (String) ruleFactor.getSelectedItem());
            sentence();
        });
        otherFactor.addActionListener(e -> {
            if (!updating) values(otherValues, (String) otherFactor.getSelectedItem());
            sentence();
        });
        for (JComboBox<?> box : List.of(kind, ruleValue, otherValue)) box.addActionListener(e -> sentence());
        JButton add = Hints.of(new JButton("Add the rule"), "Add the rule the sentence below says to the scenario.");
        add.addActionListener(e -> addRule());
        JButton remove = Hints.of(new JButton("Remove the rule"), "Remove the rule selected in the table.");
        remove.addActionListener(e -> {
            int row = ruleTable.getSelectedRow();
            if (row >= 0) change(design.removingRule(row));
        });
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(form, BorderLayout.NORTH);
        top.add(sentence, BorderLayout.CENTER);
        top.add(row(add, remove), BorderLayout.SOUTH);
        ruleTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.add(top, BorderLayout.NORTH);
        middle.add(TableTitles.titled(ruleTable, "Rules of the scenario, in words and as the constraints file writes them"), BorderLayout.CENTER);
        page.add(middle, BorderLayout.CENTER);
        page.add(FieldNames.note("The rules are saved beside the scenario as <name>.constraints.json; step 4 opens that file to draw "
                + "more rules and shows how many test cases each one removes."), BorderLayout.SOUTH);
        return page;
    }

    private JComponent howManyPage() {
        JPanel page = FieldNames.section(new JPanel(new BorderLayout(6, 6)), "0.5 · How many");
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 3; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(1, 4, 1, 4);
        top.add(new JLabel("Which combinations become test cases?"), c);
        ButtonGroup which = new ButtonGroup();
        int y = 1;
        for (JRadioButton b : List.of(every, pairs, triples)) {
            which.add(b);
            c.gridy = y++;
            top.add(b, c);
        }
        which.add(within);
        c.gridy = y; c.gridwidth = 1;
        top.add(within, c);
        c.gridx = 1;
        top.add(FieldNames.label("Most rows:", budget), c);
        c.gridx = 2;
        top.add(budget, c);
        c.gridx = 0; c.gridy = y + 1; c.gridwidth = 3; c.insets = new Insets(6, 4, 1, 4);
        top.add(smaller, c);
        c.gridy = y + 2; c.insets = new Insets(1, 4, 1, 4);
        top.add(allOrders, c);
        for (AbstractButton b : List.of(every, pairs, triples, within, smaller, allOrders)) b.addActionListener(e -> selectionChosen());
        budget.addChangeListener(e -> selectionChosen());
        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.add(heading("Rules decide which combinations may happen; the selection decides how many of them to try. Pairwise is for "
                + "static parameters: it keeps fewer test cases while every value of each meets every value of every other in at "
                + "least one of them, and most defects come from one value or two values together. Sequences of actions and sudden "
                + "actions are of another kind: each kept combination runs every order of a sequence, and every sudden action happens "
                + "or not, in every combination with the others."), BorderLayout.NORTH);
        north.add(leftAligned(top), BorderLayout.CENTER);
        page.add(north, BorderLayout.NORTH);
        page.add(FieldNames.titled(counts, "How many, and what fits"), BorderLayout.CENTER);
        page.add(FieldNames.note("The selection is a run setting: Use it in step 3 puts it into step 3's run settings, and the "
                + "scenario's .properties file keeps it for the command line."), BorderLayout.SOUTH);
        return page;
    }

    private JComponent bootstrapPage() {
        JPanel page = FieldNames.section(new JPanel(new BorderLayout(6, 6)), "0.6 · Bootstrap");
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridy = 0; c.gridwidth = 2; c.anchor = GridBagConstraints.WEST; c.insets = new Insets(2, 4, 6, 4);
        top.add(heading("The bootstrap, FW_RunMeFirstOnce, is not a factor and is never combined: it runs once, and really first, for "
                + "the whole bunch of test cases a run executes after it. Put there what they all share: start or check the system "
                + "you test, read a configuration, seed data, and do it so that running it again changes nothing. It receives the "
                + "FW_Arguments line, and the FW_ARGS it leaves, split at spaces, is what every test case's main receives (the "
                + "test cases keep it in shared). What varies between test cases belongs in factors, never in the bootstrap."), c);
        c.gridy = 1; c.insets = new Insets(2, 4, 2, 4);
        top.add(bootstrap, c);
        c.gridy = 2; c.gridwidth = 1;
        top.add(FieldNames.label("Arguments it receives, separated by spaces:", arguments), c);
        c.gridx = 1;
        top.add(arguments, c);
        bootstrap.addActionListener(e -> bootstrapChosen());
        arguments.getDocument().addDocumentListener(typed(arguments, this::bootstrapChosen));
        page.add(leftAligned(top), BorderLayout.NORTH);
        page.add(FieldNames.titled(bootstrapCode, "The bootstrap the wizard writes (FW_RunMeFirstOnce)"), BorderLayout.CENTER);
        page.add(FieldNames.note("After saving, step 2 shows it in 2.1 · Control sheets, where you write your own preparation."),
                BorderLayout.SOUTH);
        bootstrapCode.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        return page;
    }

    private void bootstrapChosen() {
        change(design.withBootstrap(new Bootstrap(bootstrap.isSelected(), arguments.getText())));
    }

    private JComponent finishPage() {
        JPanel page = FieldNames.section(new JPanel(new BorderLayout(6, 6)), "0.7 · Finish");
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(heading("Each test case is a small Java program: HEAD starts it; each static parameter's line sets it, each "
                + "sequence's line does its actions in order and each sudden action's line makes it happen; the check decides the "
                + "verdict, and TAIL prints the values and the verdict for the results analysis (step 8)."), BorderLayout.NORTH);
        top.add(check, BorderLayout.SOUTH);
        check.addActionListener(e -> change(design.withCheck(check.isSelected())));
        JPanel buttons = row(checkButton, saveButton, useButton, casesButton, editButton);
        checkButton.addActionListener(e -> check());
        saveButton.addActionListener(e -> saveAs());
        useButton.addActionListener(e -> useInStep3());
        casesButton.addActionListener(e -> {
            if (useInStep3()) handoff.testCases();
        });
        editButton.addActionListener(e -> {
            if (current(saved)) handoff.edit(saved);
        });
        navigator.bind(saveButton, () -> Navigator.Purpose.DOCUMENT_SAVE, this::suggestion);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, TableTitles.titled(sheetTable,
                "Sheets the wizard writes: each sheet's FW_Seq row, values and ways"), FieldNames.titled(output, "Framework check and saved files"));
        split.setResizeWeight(0.45);
        JPanel middle = new JPanel(new BorderLayout(0, 4));
        middle.add(buttons, BorderLayout.NORTH);
        middle.add(split, BorderLayout.CENTER);
        page.add(top, BorderLayout.NORTH);
        page.add(middle, BorderLayout.CENTER);
        output.setText("Check with the Framework counts the test cases exactly; Save the scenario... writes it. Then hand it on: "
                + "Use it in step 3, Show its test cases (step 5) or Open it in step 2.");
        return page;
    }

    // ---- answers ------------------------------------------------------------------------------------------------------

    private Situation situation() {
        for (int i = 0; i < situations.size(); i++) if (situations.get(i).isSelected()) return Situation.values()[i];
        return Situation.OWN;
    }

    private void situationChosen() {
        Situation s = situation();
        situationAdvice.setText(s.advice() + (s == Situation.OWN ? "" : "\n\nIts example: " + example(s.example(design.name()))));
        situationAdvice.setCaretPosition(0);
        fill.setText(s == Situation.OWN ? "Start with no factors" : "Fill in this example");
    }

    /** An example design in a line: its factors with their freedom, and its count. */
    private static String example(GuidedScenario d) {
        List<String> parts = new ArrayList<>();
        for (Factor f : d.factors()) parts.add(f.name() + " (" + f.valuesText() + "): " + f.shape());
        return String.join("; ", parts) + ". " + GuidedScenario.figure(d.total()) + " combinations" + (d.rules().isEmpty() ? ""
                : ", with the rule: " + d.rules().getFirst().words()) + ".";
    }

    /** Replaces the design with the situation's example, keeping its name, and shows its factors. */
    void fill(Situation s) {
        design = s.example(design.name());
        selected = design.factors().isEmpty() ? -1 : 0;
        refresh();
        pages.setSelectedIndex(1);
    }

    private Factor current() {
        return design.factors().get(selected);
    }

    private void addFactor() {
        int n = design.factors().size() + 1;
        while (taken("factor" + n)) n++;
        design = design.adding(Factor.of("factor" + n, List.of("a", "b")));
        selected = design.factors().size() - 1;
        refresh();
        factorName.requestFocusInWindow();
        factorName.selectAll();
    }

    private boolean taken(String name) {
        return design.factors().stream().anyMatch(f -> f.name().equalsIgnoreCase(name));
    }

    private void removeFactor() {
        if (selected < 0) return;
        String name = current().name();
        design = design.removingFactor(selected).withRules(design.rules().stream().filter(r -> !r.factor().equals(name)
                && !r.other().equals(name)).toList());
        selected = Math.min(selected, design.factors().size() - 1);
        refresh();
    }

    private void move(int by) {
        if (selected < 0 || selected + by < 0 || selected + by >= design.factors().size()) return;
        design = design.moving(selected, by);
        selected += by;
        refresh();
    }

    /** Shows the factor on pages 0.2 and 0.3. */
    void select(int index) {
        selected = index;
        refresh();
    }

    /** The answers of page 0.3 for the selected factor. */
    private void answered() {
        if (updating || selected < 0) return;
        Use use = one.isSelected() ? Use.ONE : exactly.isSelected() ? Use.EXACTLY : Use.RANGE;
        Factor f = current().using(use, (Integer) count.getValue(), (Integer) fewest.getValue(), (Integer) most.getValue())
                .withOrder(sequence.isSelected()).withRepeats(repeat.isSelected()).withOptional(optional.isSelected());
        change(design.withFactor(selected, f));
    }

    private void addRule() {
        String f = (String) ruleFactor.getSelectedItem();
        String v = (String) ruleValue.getSelectedItem();
        String o = (String) otherFactor.getSelectedItem();
        String ov = (String) otherValue.getSelectedItem();
        if (f == null || v == null || o == null || ov == null) {
            say(sentence, "Choose two values first: a factor and its value, and the other factor and its value.", true);
            return;
        }
        Rule rule = new Rule((Kind) kind.getSelectedItem(), f, v, o, ov);
        List<String> problems = rule.problems(design.factors());
        if (!problems.isEmpty()) {
            say(sentence, problems.getFirst(), true);
            return;
        }
        if (design.rules().contains(rule)) {
            say(sentence, "The scenario has this rule already.", true);
            return;
        }
        change(design.adding(rule));
        say(sentence, "Added: " + rule.words() + ".", false);
    }

    private void sentence() {
        String f = (String) ruleFactor.getSelectedItem();
        String v = (String) ruleValue.getSelectedItem();
        String o = (String) otherFactor.getSelectedItem();
        String ov = (String) otherValue.getSelectedItem();
        if (f == null || v == null || o == null || ov == null) {
            say(sentence, design.factors().isEmpty() ? "Add factors first (page 0.2): a rule relates two of their values." : " ", false);
            return;
        }
        Rule rule = new Rule((Kind) kind.getSelectedItem(), f, v, o, ov);
        List<String> problems = rule.problems(design.factors());
        say(sentence, problems.isEmpty() ? "The rule: " + rule.words() + "." : problems.getFirst(), !problems.isEmpty());
    }

    private void selectionChosen() {
        if (updating) return;
        Selection s = pairs.isSelected() ? Selection.PAIRS : triples.isSelected() ? Selection.TRIPLES : within.isSelected()
                ? Selection.BUDGET : Selection.ALL;
        change(design.selecting(s, ((Integer) budget.getValue()).longValue(), smaller.isSelected()).withAllOrders(allOrders.isSelected()));
    }

    /** A new design from an answer: everything that shows it follows. */
    private void change(GuidedScenario changed) {
        if (updating || changed.equals(design)) return;
        design = changed;
        refresh();
    }

    // ---- showing the design -------------------------------------------------------------------------------------------

    /** Shows the design on every page; a field being typed in keeps its text. */
    private void refresh() {
        updating = true;
        try {
            if (selected >= design.factors().size()) selected = design.factors().size() - 1;
            if (selected < 0 && !design.factors().isEmpty()) selected = 0;
            if (typing != nameField && !nameField.getText().strip().equals(design.name())) nameField.setText(design.name());
            factorRows.show(design.factors());
            factorNames.clear();
            for (Factor f : design.factors()) factorNames.addElement(f.name() + ": " + GuidedScenario.figure(f.rows()) + " ways");
            if (selected >= 0) {
                factorTable.setRowSelectionInterval(selected, selected);
                describe.setSelectedIndex(selected);
                Factor f = current();
                if (typing != factorName && !factorName.getText().strip().equals(f.name())) factorName.setText(f.name());
                if (typing != factorValues && !Factor.split(factorValues.getText()).equals(f.values())) factorValues.setText(f.valuesText());
                role.setSelectedItem(f.role());
                questions(f);
            } else {
                factorName.setText("");
                factorValues.setText("");
                meaning.setText("Add a factor on page 0.2 first, then select it here to say how one test case uses its values.");
            }
            for (JComponent c : List.of(factorName, factorValues, role)) c.setEnabled(selected >= 0);
            ruleChoices();
            ruleRows.show(design.rules());
            selection();
            sheets();
            check.setSelected(design.check());
            bootstrap.setSelected(design.bootstrap().on());
            if (typing != arguments && !arguments.getText().equals(design.bootstrap().arguments())) arguments.setText(design.bootstrap().arguments());
            arguments.setEnabled(design.bootstrap().on());
            bootstrapCode.setText(design.bootstrap().on() ? design.bootstrap().code(design.name()) + "\nIt receives: "
                    + (design.bootstrap().words().isEmpty() ? "no arguments" : String.join(" ", design.bootstrap().words())) + "\n"
                    : "No bootstrap: every test case's main receives the FW_Arguments line, which is empty.");
            bootstrapCode.setCaretPosition(0);
        } finally {
            updating = false;
        }
        sentence();
        status();
    }

    /** Page 0.3's answers and their meaning for the factor. */
    private void questions(Factor f) {
        (f.use() == Use.ONE ? one : f.use() == Use.EXACTLY ? exactly : range).setSelected(true);
        count.setValue(Math.max(1, f.k()));
        fewest.setValue(Math.max(0, f.from()));
        most.setValue(Math.max(0, f.to()));
        (f.ordered() ? sequence : group).setSelected(true);
        repeat.setSelected(f.repeats());
        optional.setSelected(f.optional());
        count.setEnabled(f.use() == Use.EXACTLY);
        fewest.setEnabled(f.use() == Use.RANGE);
        most.setEnabled(f.use() == Use.RANGE);
        group.setEnabled(f.several());
        sequence.setEnabled(f.several());
        repeat.setEnabled(f.use() == Use.EXACTLY && f.k() >= 2);
        StringBuilder text = new StringBuilder();
        text.append(f.name()).append(", a ").append(f.role().words()).append(": ").append(f.shape()).append(".\n\n");
        text.append("FW_Seq row: ").append(f.name());
        for (String cell : design.instructions(f)) text.append("  |  ").append(cell);
        text.append("\nWays: ").append(f.formula()).append("\n\n").append(design.why(f)).append("\n\nThe first ways: ");
        List<String> shown = f.examples(7);
        text.append(String.join(",  ", shown.subList(0, Math.min(6, shown.size())))).append(shown.size() > 6 ? ",  ..." : "").append('\n');
        List<String> problems = f.problems();
        if (!problems.isEmpty()) text.append("\nTo fix: ").append(String.join(" ", problems)).append('\n');
        meaning.setText(text.toString());
        meaning.setCaretPosition(0);
    }

    /** The rule page's factors and values, keeping what is chosen while it still exists. */
    private void ruleChoices() {
        List<String> names = design.factors().stream().map(Factor::name).toList();
        keep(ruleFactors, names);
        // a list of factors filled anew offers two different factors; a pair chosen by hand is kept
        if (keep(otherFactors, names) && otherFactors.getSize() > 1 && otherFactor.getSelectedIndex() == ruleFactor.getSelectedIndex()) {
            otherFactor.setSelectedIndex(1);
        }
        values(ruleValues, (String) ruleFactor.getSelectedItem());
        values(otherValues, (String) otherFactor.getSelectedItem());
    }

    private void values(DefaultComboBoxModel<String> model, String factor) {
        Factor f = design.factors().stream().filter(x -> x.name().equals(factor)).findFirst().orElse(null);
        keep(model, f == null ? List.of() : f.values());
    }

    /** Fills a choice with the items, keeping the chosen one when it is still among them; whether it was filled anew. */
    private static boolean keep(DefaultComboBoxModel<String> model, List<String> items) {
        Object chosen = model.getSelectedItem();
        List<String> held = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) held.add(model.getElementAt(i));
        boolean refill = !held.equals(items);
        if (refill) {
            model.removeAllElements();
            for (String item : items) model.addElement(item);
        }
        if (chosen != null && items.contains(chosen)) model.setSelectedItem(chosen);
        else if (!items.isEmpty()) model.setSelectedItem(items.getFirst());
        return refill;
    }

    /** Page 0.5: the counts, what fits and why, and the chosen selection. */
    private void selection() {
        (switch (design.selection()) {
            case ALL -> every;
            case PAIRS -> pairs;
            case TRIPLES -> triples;
            case BUDGET -> within;
        }).setSelected(true);
        budget.setValue((int) Math.max(1, Math.min(1_000_000, design.budget())));
        budget.setEnabled(design.selection() == Selection.BUDGET);
        smaller.setSelected(design.smaller());
        smaller.setEnabled(design.selection() != Selection.ALL);
        allOrders.setSelected(design.allOrders());
        allOrders.setEnabled(design.selection() != Selection.ALL && !design.of(Role.SEQUENCE).isEmpty());
        StringBuilder text = new StringBuilder();
        for (Role r : Role.values()) {
            List<Factor> kind = design.of(r);
            if (kind.isEmpty()) continue;
            text.append(switch (r) {
                case PARAMETER -> "Static parameters: ";
                case SEQUENCE -> "Sequences of actions: ";
                case SUDDEN -> "Sudden actions (each may or may not happen): ";
            }).append(String.join(", ", kind.stream().map(f -> f.name() + " " + GuidedScenario.figure(f.rows())
                    + (r == Role.SEQUENCE ? " orders" : r == Role.SUDDEN ? " (with not happening)" : " ways")).toList())).append(".\n");
        }
        text.append("Every combination: ").append(GuidedScenario.figure(design.total()));
        List<String> ways = design.factors().stream().map(f -> GuidedScenario.figure(f.rows())).toList();
        if (ways.size() > 1) text.append(" = ").append(String.join(" × ", ways));
        text.append(".\n\n");
        Selection fits = design.recommended();
        text.append("What fits: ").append(words(fits)).append(". ").append(design.advice()).append("\n\n");
        text.append("Your choice, ").append(design.selectionWords()).append(", keeps ").append(design.kept()).append(".");
        if (design.selection() != Selection.ALL && design.varying() < 3) {
            text.append(" With fewer than three factors that vary, a covering keeps every combination anyway.");
        }
        text.append(" Check with the Framework (page 0.7) counts them exactly, rules included.");
        counts.setText(text.toString());
        counts.setCaretPosition(0);
    }

    private static String words(Selection s) {
        return switch (s) {
            case ALL -> "every combination";
            case PAIRS -> "every pair of values (pairwise)";
            case TRIPLES -> "every triple of values";
            case BUDGET -> "the strongest covering within a number of rows";
        };
    }

    /** Page 0.6's table: the sheets the wizard writes, in the order the code follows. */
    private void sheets() {
        List<String[]> rows = new ArrayList<>();
        if (design.bootstrap().on()) {
            rows.add(new String[]{"FW_RunMeFirstOnce", "bootstrap", "runs once, first, for all test cases", "receives: "
                    + (design.bootstrap().words().isEmpty() ? "no arguments" : String.join(" ", design.bootstrap().words())), "-"});
        }
        rows.add(new String[]{"HEAD", "program", "FW_Combi(1)", "the program's start: the class, set, act, happen and main", "1"});
        for (Factor f : design.factors()) {
            rows.add(new String[]{f.name(), f.role().words(), String.join(", ", design.instructions(f)), f.valuesText(),
                    GuidedScenario.figure(f.rows())});
        }
        if (design.check()) rows.add(new String[]{"check", "program", "FW_Combi(1)", "your check; it fails with code " + design.checkCode()
                + " " + GuidedScenario.CHECK_FAILED, "1"});
        rows.add(new String[]{"TAIL", "program", "FW_Combi(1)", "the program's end: prints the values and the verdict", "1"});
        sheetRows.show(rows);
    }

    /** The bottom lines, the moves and the buttons that need a design without problems or a saved scenario. */
    private void status() {
        summary.setText(design.factors().isEmpty() ? "No factors yet · " + design.labels() + " of " + GuidedScenario.MAX_LABELS
                + " labels" : count(design.factors().size(), "factor") + " · " + count(design.total(), "combination") + " · "
                + count(design.rules().size(), "rule") + " · " + design.selectionWords() + " · " + design.labels() + " of "
                + GuidedScenario.MAX_LABELS + " labels");
        List<String> problems = design.problems();
        if (problems.isEmpty()) {
            say(problemLine, "Ready: check it with the Framework and save it on page 0.7.", false);
        } else {
            say(problemLine, "To fix: " + problems.getFirst() + (problems.size() > 1 ? " (and " + (problems.size() - 1) + " more)" : ""), true);
        }
        checkButton.setEnabled(!busy);
        saveButton.setEnabled(!busy);
        for (JButton b : List.of(useButton, casesButton, editButton)) b.setEnabled(!busy && saved != null);
    }

    /** The moves name the pages they go to. */
    private void steps() {
        int at = pages.getSelectedIndex();
        back.setEnabled(at > 0);
        next.setEnabled(at < pages.getTabCount() - 1);
        back.setText(at > 0 ? "Back: " + pages.getTitleAt(at - 1) : "Back");
        next.setText(at < pages.getTabCount() - 1 ? "Next: " + pages.getTitleAt(at + 1) : "Next");
    }

    /** A number with its noun, in the singular for one: 1 rule, 2 rules. */
    private static String count(Object number, String noun) {
        String figure = number instanceof BigInteger b ? GuidedScenario.figure(b) : number.toString();
        return figure + " " + noun + (figure.equals("1") ? "" : "s");
    }

    private static void say(JLabel label, String text, boolean problem) {
        label.setText(text);
        label.setForeground(problem ? PROBLEM : READY);
    }

    // ---- checking, saving and handing on ------------------------------------------------------------------------------

    /** The Framework's count of the design, off the event thread; the problems instead when it has some. */
    CompletableFuture<GuidedScenario.Checked> check() {
        List<String> problems = design.problems();
        if (!problems.isEmpty()) {
            output.setText("Fix these first:\n- " + String.join("\n- ", problems));
            return CompletableFuture.completedFuture(null);
        }
        if (busy) return CompletableFuture.completedFuture(null);
        busy = true;
        status();
        GuidedScenario checking = design;
        output.setText("The Framework is planning and counting " + checking.fileName() + " ...");
        return Background.run("wizard-check", () -> checking.check(session.get(), Cancellation.NONE), checked -> {
            busy = false;
            output.setText(checked.text() + (checking.equals(design) ? "" : "\n(The design changed since: check it again.)"));
            output.setCaretPosition(0);
            status();
        }, failure -> {
            busy = false;
            output.setText("The Framework cannot plan the design:\n" + Background.describe(failure));
            status();
        });
    }

    /** Where Save proposes to write: the saved file, or a new file named after the scenario where scenarios go. */
    private Path suggestion() {
        if (saved != null) return saved;
        Path folder = navigator.place(Navigator.Purpose.DOCUMENT_SAVE, null).folder();
        Path file = folder.resolve(design.fileName());
        for (int i = 2; Files.exists(file) && i < 1000; i++) file = folder.resolve(design.name() + "-" + i + ".json");
        return file;
    }

    private void saveAs() {
        List<String> problems = design.problems();
        if (!problems.isEmpty()) {
            output.setText("Fix these first:\n- " + String.join("\n- ", problems));
            return;
        }
        Path chosen = navigator.choose(panel, Navigator.Purpose.DOCUMENT_SAVE, suggestion());
        if (chosen != null) save(chosen);
    }

    /** Writes the design as the file (with .json when it names no scenario format), its rules and settings beside it. */
    CompletableFuture<GuidedScenario.Saved> save(Path chosen) {
        String file = chosen.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        Path target = file.matches(".*\\.(xlsx|json|yaml|yml|toml)") ? chosen : chosen.resolveSibling(chosen.getFileName() + ".json");
        GuidedScenario writing = design;
        busy = true;
        status();
        return Background.run("wizard-save", () -> writing.save(target), written -> {
            busy = false;
            saved = written.scenario();
            savedDesign = writing;
            for (Path p : new Path[]{written.scenario(), written.constraints(), written.properties()}) {
                if (p != null) navigator.trail().record(p);
            }
            output.setText("Saved the scenario: " + written.scenario() + "\n"
                    + (written.constraints() == null ? "" : "Its rules: " + written.constraints() + "\n")
                    + "Its settings for the command line: " + written.properties() + "\n\n"
                    + "Next: Use it in step 3 (its selection goes into step 3's run settings), Show its test cases (step 5), or Open "
                    + "it in step 2 to change it by hand. Write your check in the sheet check, where the comment says.");
            status();
        }, failure -> {
            busy = false;
            output.setText("The scenario cannot be saved:\n" + Background.describe(failure));
            status();
        });
    }

    /** Whether the saved scenario holds the design: it is saved again, in its file, when the design changed since. */
    private boolean current(Path file) {
        if (file == null) return false;
        if (design.equals(savedDesign)) return true;
        List<String> problems = design.problems();
        if (!problems.isEmpty()) {
            output.setText("The design changed since it was saved and has problems; fix these first:\n- " + String.join("\n- ", problems));
            return false;
        }
        try {
            design.save(file);
            savedDesign = design;
            output.setText("Saved your changes again in " + file + ".\n");
            return true;
        } catch (RuntimeException failure) {
            output.setText("The scenario cannot be saved:\n" + Background.describe(failure));
            return false;
        }
    }

    /** Hands the saved scenario to step 3 with its selection; false when there is nothing saved to hand on. */
    boolean useInStep3() {
        if (!current(saved)) return false;
        String settings = handoff.use(saved, design.settings());
        output.append("\nStep 3 now uses " + saved + ".\nIts run settings: " + settings + "\n");
        return true;
    }

    // ---- building blocks ----------------------------------------------------------------------------------------------

    private static JTextArea text(int rows, String hint) {
        JTextArea area = Hints.of(new JTextArea(rows, 50), hint);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        return area;
    }

    private static JLabel heading(String text) {
        JLabel label = new JLabel("<html><body style='width: 640px'>" + text.replace("&", "&amp;").replace("<", "&lt;") + "</body></html>");
        label.setBorder(BorderFactory.createEmptyBorder(2, 4, 4, 4));
        return label;
    }

    /** The grid at its preferred widths on the left, its spare width given to an empty last column. */
    private static JPanel leftAligned(JPanel grid) {
        GridBagConstraints filler = new GridBagConstraints();
        filler.gridx = 20; filler.gridy = 0; filler.weightx = 1; filler.fill = GridBagConstraints.HORIZONTAL;
        grid.add(Box.createHorizontalGlue(), filler);
        return grid;
    }

    private static JPanel row(Component... parts) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        for (Component part : parts) row.add(part);
        row.add(Box.createHorizontalStrut(4));
        return row;
    }

    /** Runs the change on every edit of the field, which the change's refresh then leaves as typed. */
    private DocumentListener typed(JTextField field, Runnable change) {
        Runnable typed = () -> {
            if (updating) return;
            typing = field;
            try {
                change.run();
            } finally {
                typing = null;
            }
        };
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                typed.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                typed.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                typed.run();
            }
        };
    }

    // ---- for tests, on the event thread ------------------------------------------------------------------------------

    GuidedScenario design() {
        return design;
    }

    void setDesign(GuidedScenario changed) {
        design = changed;
        selected = changed.factors().isEmpty() ? -1 : 0;
        refresh();
    }

    JTabbedPane pages() {
        return pages;
    }

    String output() {
        return output.getText();
    }

    String meaning() {
        return meaning.getText();
    }

    String counts() {
        return counts.getText();
    }

    String problems() {
        return problemLine.getText();
    }

    Path saved() {
        return saved;
    }

    /** Page 0.3's answers for the selected factor, as a user gives them. */
    void answer(Use use, int k, int from, int to, boolean ordered, boolean repeats, boolean leftOut) {
        (use == Use.ONE ? one : use == Use.EXACTLY ? exactly : range).doClick();
        count.setValue(k);
        fewest.setValue(from);
        most.setValue(to);
        if (sequence.isEnabled() || group.isEnabled()) (ordered ? sequence : group).doClick();
        if (repeat.isEnabled() && repeat.isSelected() != repeats) repeat.doClick();
        if (optional.isSelected() != leftOut) optional.doClick();
    }

    /** Page 0.2's fields, typed into as a user does. */
    void type(String name, String values) {
        factorName.setText(name);
        factorValues.setText(values);
    }

    void addFactorForTest() {
        addFactor();
    }

    /** Page 0.2's kind of the selected factor, chosen as a user does. */
    void kind(Role r) {
        role.setSelectedItem(r);
    }

    void removeSelected() {
        removeFactor();
    }

    /** Page 0.6's arguments of the bootstrap, typed as a user does. */
    void bootstrapArguments(String text) {
        arguments.setText(text);
    }

    /** Page 0.4's rule, chosen and added as a user does; the sentence the page shows. */
    String rule(Kind k, String factor, String value, String other, String second) {
        kind.setSelectedItem(k);
        ruleFactor.setSelectedItem(factor);
        ruleValue.setSelectedItem(value);
        otherFactor.setSelectedItem(other);
        otherValue.setSelectedItem(second);
        addRule();
        return sentence.getText();
    }

    void choose(Selection s) {
        (switch (s) {
            case ALL -> every;
            case PAIRS -> pairs;
            case TRIPLES -> triples;
            case BUDGET -> within;
        }).doClick();
    }
}
