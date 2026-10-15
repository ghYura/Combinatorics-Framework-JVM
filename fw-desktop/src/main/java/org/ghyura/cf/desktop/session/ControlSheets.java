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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.input.ScenarioInterpreter;
import org.ghyura.cf.model.Severity;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The runtime control sheets of a scenario as the authoring editors show them (GEN:operator-authoring-ui; the legacy
 * face1 grid's control sheets): the FW_RunMeFirstOnce bootstrap source (cell A1), the FW_Arguments cells in order and
 * the FW_CUSTOM_VAR verdict codes with their names. Empty arguments and names are written as the legacy marker
 * FW_EMPTY_STRING so they keep their place, as face1 wrote them. The issues are face1's: a verdict code must be a Java
 * integer, two rows must not give the same code (02 is 2), and the bootstrap cell should declare class RunMeFirstOnce
 * with public static String FW_ARGS assigned.
 */
public record ControlSheets(String runOnce, List<String> arguments, List<Verdict> verdicts) {

    /** The legacy marker of an empty control cell. */
    public static final String EMPTY = "FW_EMPTY_STRING";
    public static final String RUN_ONCE_SHEET = "FW_RunMeFirstOnce";
    public static final String ARGUMENTS_SHEET = "FW_Arguments";
    public static final String VERDICTS_SHEET = "FW_CUSTOM_VAR";

    public ControlSheets {
        runOnce = runOnce == null ? "" : runOnce;
        arguments = List.copyOf(arguments);
        verdicts = List.copyOf(verdicts);
    }

    /** One FW_CUSTOM_VAR row: the verdict code as written and its name. */
    public record Verdict(String code, String name) {
        public Verdict {
            Objects.requireNonNull(code, "code");
            name = name == null ? "" : name;
        }
    }

    /** A finding of the editors, before any validation runs. */
    public record Issue(Severity severity, String message) {
    }

    /** What the document's control sheets hold now. */
    public static ControlSheets of(ScenarioDocument document) {
        int run = document.sheetNames().indexOf(RUN_ONCE_SHEET);
        String runOnce = run < 0 ? "" : document.cell(run, 0, 0);
        List<String> arguments = new ArrayList<>();
        int args = document.sheetNames().indexOf(ARGUMENTS_SHEET);
        if (args >= 0) {
            for (int r = 0; r < document.rowCount(args); r++) {
                arguments.add(unmark(document.cell(args, r, 0)));
            }
        }
        List<Verdict> verdicts = new ArrayList<>();
        int vars = document.sheetNames().indexOf(VERDICTS_SHEET);
        if (vars >= 0) {
            for (int r = 0; r < document.rowCount(vars); r++) {
                String code = document.cell(vars, r, 0);
                String name = document.cell(vars, r, 1);
                if (!code.isEmpty() || !name.isEmpty()) {
                    verdicts.add(new Verdict(code, unmark(name)));
                }
            }
        }
        return new ControlSheets(runOnce, arguments, verdicts);
    }

    /** Writes the three sheets into the document (added when missing, their old cells replaced). */
    public void applyTo(ScenarioDocument document) {
        int run = sheet(document, RUN_ONCE_SHEET);
        clear(document, run);
        if (!runOnce.isEmpty()) {
            document.setCell(run, 0, 0, runOnce);
        }
        int args = sheet(document, ARGUMENTS_SHEET);
        clear(document, args);
        for (int r = 0; r < arguments.size(); r++) {
            document.setCell(args, r, 0, mark(arguments.get(r)));
        }
        int vars = sheet(document, VERDICTS_SHEET);
        clear(document, vars);
        for (int r = 0; r < verdicts.size(); r++) {
            document.setCell(vars, r, 0, verdicts.get(r).code());
            document.setCell(vars, r, 1, mark(verdicts.get(r).name()));
        }
    }

    /** face1's checks of these sheets. */
    public List<Issue> issues() {
        List<Issue> issues = new ArrayList<>();
        if (!runOnce.isBlank() && !ScenarioInterpreter.runMeFirstOnceShaped(runOnce)) {
            issues.add(new Issue(Severity.WARNING, "FW_RunMeFirstOnce A1 does not declare class RunMeFirstOnce with public static String "
                    + "FW_ARGS assigned in it; the runtime expects that bootstrap"));
        }
        Map<BigInteger, Integer> seen = new HashMap<>();
        for (int r = 0; r < verdicts.size(); r++) {
            String code = verdicts.get(r).code().strip();
            BigInteger value;
            try {
                value = new BigInteger(code);
                Integer.parseInt(code);
            } catch (NumberFormatException e) {
                issues.add(new Issue(Severity.ERROR, "FW_CUSTOM_VAR row " + (r + 1) + ": '" + verdicts.get(r).code()
                        + "' must be a Java integer"));
                continue;
            }
            Integer first = seen.putIfAbsent(value, r + 1);
            if (first != null) {
                issues.add(new Issue(Severity.ERROR, "FW_CUSTOM_VAR row " + (r + 1) + ": '" + verdicts.get(r).code()
                        + "' duplicates verdict code " + value + " of row " + first));
            }
        }
        return issues;
    }

    private static int sheet(ScenarioDocument document, String name) {
        int index = document.sheetNames().indexOf(name);
        if (index < 0) {
            document.addSheet(name);
            index = document.sheetNames().indexOf(name);
        }
        return index;
    }

    private static void clear(ScenarioDocument document, int sheet) {
        for (int r = document.rowCount(sheet) - 1; r >= 0; r--) {
            document.removeRow(sheet, r);
        }
    }

    private static String mark(String text) {
        return text.isEmpty() ? EMPTY : text;
    }

    private static String unmark(String text) {
        return EMPTY.equalsIgnoreCase(text) ? "" : text;
    }
}
