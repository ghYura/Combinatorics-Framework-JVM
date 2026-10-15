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

package org.ghyura.cf.input;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Suggestions;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.spec.ControlSpec;
import org.ghyura.cf.model.spec.CustomVar;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.model.spec.ValueKind;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns what a file contains (a {@link WorkbookData}) into what a scenario means (a {@link ScenarioSpec}): the
 * rules of the legacy {@code WorkbookParser} for data cells and directives, and the typed reading of the
 * control sheets that the legacy Core copied into database tables. It is pure: no database, no static state,
 * and the only file access goes through the {@link FileResolver} it is given.
 *
 * <p>Where the legacy parser silently skipped something that cannot work (an unknown {@code FW_} cell, a
 * database or CSV cell, an {@code FW_Optional} with nothing before it), this reports it with the cell's
 * location. Findings that the legacy parser tolerated and that cannot corrupt the result are warnings.</p>
 */
public final class ScenarioInterpreter {

    /** The scenario and the findings that did not stop it. */
    public record Interpretation(ScenarioSpec spec, List<Diagnostic> diagnostics) {
        public Interpretation {
            Objects.requireNonNull(spec, "spec");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private static final List<String> KNOWN_CONTROL_SHEETS = List.of("FW_Seq", "FW_Info", "FW_SheetNames",
            "FW_CUSTOM_VAR", "FW_RunMeFirstOnce", "FW_Arguments", "FW_Complex", "FW_Combi", "FW_CombiR", "FW_Permut",
            "FW_PermutR", "FW_Subsets", "FW_Cartes");

    private static final List<String> CELL_DIRECTIVES = List.of("FW_File=", "FW_Optional", "FW_RefineCodeExceptQuoted",
            "FW_EMPTY_STRING", "FW_CSVFile=", "FW_Separator=", "FW_BinaryFile=", "FW_DBURL=", "FW_DBUSER=", "FW_DBPASS=",
            "FW_SQL=");

    private static final Pattern FW_VAR_EXIT_CODE = Pattern.compile(
            "(?s).*?\\s+?FW_VAR\\s*?=\\s*?FW_EXIT_CODE.*?|^\\s{0,}FW_VAR\\s*?=\\s*?FW_EXIT_CODE.*?");
    private static final Pattern RUNME_CLASS = Pattern.compile("(?s).*class\\s+?RunMeFirstOnce\\s*?.*");
    private static final Pattern RUNME_FIELD = Pattern.compile("(?s).*public\\s+?static\\s+?String\\s+?FW_ARGS.*");
    private static final Pattern RUNME_ASSIGNMENT = Pattern.compile("(?s).*FW_ARGS\\s*?=.*");

    private final WorkbookData workbook;
    private final FileResolver files;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private ScenarioInterpreter(WorkbookData workbook, FileResolver files) {
        this.workbook = workbook;
        this.files = files;
    }

    /**
     * @throws FrameworkException with every finding when at least one of them is an error
     */
    public static Interpretation interpret(WorkbookData workbook, FileResolver files) {
        Objects.requireNonNull(workbook, "workbook");
        Objects.requireNonNull(files, "files");
        return new ScenarioInterpreter(workbook, files).run();
    }

    private Interpretation run() {
        List<SheetSpec> dataSheets = new ArrayList<>();
        List<SheetDecoration> sheetNames = new ArrayList<>();
        List<LocatedText> runMeFirstOnce = new ArrayList<>();
        List<LocatedText> arguments = new ArrayList<>();
        List<CustomVar> customVars = new ArrayList<>();
        List<SeqRow> sequence = new ArrayList<>();
        boolean hasSequence = false;
        long expectedValues = 0;

        for (InputSheet sheet : workbook.sheets()) {
            if (!sheet.isControl()) {
                expectedValues += keysConsumedBy(sheet);
                dataSheets.add(dataSheet(sheet, dataSheets.size() + 1));
                continue;
            }
            switch (sheet.name()) {
                case "FW_Seq" -> {
                    hasSequence = true;
                    sequence.addAll(sequenceRows(sheet));
                }
                case "FW_SheetNames" -> sheetNames.addAll(sheetNames(sheet));
                case "FW_CUSTOM_VAR" -> customVars.addAll(customVars(sheet));
                case "FW_RunMeFirstOnce" -> runMeFirstOnce.addAll(runMeFirstOnce(sheet));
                case "FW_Arguments" -> arguments.addAll(nonBlankCells(sheet));
                default -> {
                    if (!KNOWN_CONTROL_SHEETS.contains(sheet.name())) {
                        unknownControlSheet(sheet);
                    }
                }
            }
        }

        boolean failed = diagnostics.stream().anyMatch(d -> d.severity().isError());
        long issued = 0;
        for (SheetSpec sheet : dataSheets) {
            issued += sheet.cells().size();
        }
        if (!failed && expectedValues != issued) {
            diagnostics.add(Diagnostic.error(ErrorCode.KEY_CENSUS_MISMATCH, "the census counted " + expectedValues
                    + " values but the interpreter issued " + issued + ": keysConsumedBy and the cell rules have diverged",
                    SourceLocation.of(workbook.source()), "expected", Long.toString(expectedValues), "actual",
                    Long.toString(issued)));
            failed = true;
        }
        if (failed) {
            throw new FrameworkException(diagnostics);
        }
        ScenarioSpec spec = new ScenarioSpec(workbook.source(), dataSheets,
                new ControlSpec(sheetNames, runMeFirstOnce, arguments, customVars), hasSequence, sequence);
        return new Interpretation(spec, diagnostics);
    }

    // ---- data sheets ----------------------------------------------------------------------------------------

    /**
     * How many values the legacy parser issues for this cell: the one predicate the census and the parser share.
     * A formula yields one value. Every other cell is judged by its formatted text, as the legacy parser judged
     * {@code DataFormatter.formatCellValue} of every non-formula cell: a number whose custom format renders
     * {@code FW_EMPTY_STRING1} is that directive (review finding IP03). It yields one value unless it is a cell
     * directive that makes no value, i.e. everything except {@code FW_File=} and {@code FW_EMPTY_STRING}.
     */
    static int keysConsumedBy(InputCell cell) {
        if (cell.isFormula()) {
            return 1;
        }
        String text = cell.text();
        if (isCellDirective(text)) {
            return text.startsWith("FW_File=") || text.startsWith("FW_EMPTY_STRING") ? 1 : 0;
        }
        return 1;
    }

    private static long keysConsumedBy(InputSheet sheet) {
        long count = 0;
        for (InputRow row : sheet.rows()) {
            for (InputCell cell : row.cells()) {
                count += keysConsumedBy(cell);
            }
        }
        return count;
    }

    private static boolean isCellDirective(String text) {
        return text.startsWith("FW_") && !text.startsWith("FW_EXIT_CODE") && !text.startsWith("FW_VAR")
                && !text.startsWith("FW_CUSTOM_VAR");
    }

    private static boolean isFwVarExitCode(String text) {
        return text.contains("FW_VAR") && text.contains("FW_EXIT_CODE") && FW_VAR_EXIT_CODE.matcher(text).matches();
    }

    private SheetSpec dataSheet(InputSheet sheet, int key) {
        List<ValueCell> cells = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int duplicates = 0;
        SourceLocation firstDuplicate = null;
        for (InputRow row : sheet.rows()) {
            for (InputCell cell : row.cells()) {
                String text = cell.text();
                SourceLocation where = cell.location();
                if (cell.isFormula()) {
                    issue(cells, seen, ValueCell.of(text, ValueKind.FORMULA, where));
                    diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_FORMULA_AS_TEXT, "the formula '" + text
                            + "' is kept as text and is not evaluated", where, "sheet", sheet.name()));
                } else if (isCellDirective(text)) {
                    directive(sheet, cells, seen, cell);
                } else if (isFwVarExitCode(text)) {
                    issue(cells, seen, ValueCell.of(text.replace("FW_EXIT_CODE", Integer.toString(key)),
                            ValueKind.EXIT_CODE_VAR, where));
                } else {
                    if (seen.contains(text)) {
                        duplicates++;
                        if (firstDuplicate == null) {
                            firstDuplicate = where;
                        }
                    }
                    issue(cells, seen, ValueCell.of(text, ValueKind.TEXT, where));
                }
            }
        }
        if (duplicates > 0) {
            diagnostics.add(Diagnostic.info(ErrorCode.SPEC_DUPLICATE_VALUE, "sheet '" + sheet.name() + "' repeats "
                    + duplicates + " value" + (duplicates == 1 ? "" : "s") + " that already occurred in it; each repeat "
                    + "keeps its own key", firstDuplicate, "sheet", sheet.name(), "repeats", Integer.toString(duplicates)));
        }
        return new SheetSpec(sheet.name(), key, cells, sheet.location());
    }

    private static void issue(List<ValueCell> cells, Set<String> seen, ValueCell cell) {
        cells.add(cell);
        seen.add(cell.text());
    }

    private void directive(InputSheet sheet, List<ValueCell> cells, Set<String> seen, InputCell cell) {
        String text = cell.text();
        SourceLocation where = cell.location();
        if (text.startsWith("FW_CSVFile=") || text.startsWith("FW_Separator=")) {
            unsupported(sheet, text, where, "csv-import", "importing CSV rows is not implemented: the legacy "
                    + "implementation read the rows under stub keys that collided with real keys and never added "
                    + "them to the sheet");
        } else if (text.startsWith("FW_BinaryFile=") || text.startsWith("FW_DBURL=") || text.startsWith("FW_DBUSER=")
                || text.startsWith("FW_DBPASS=") || text.startsWith("FW_SQL=")) {
            unsupported(sheet, text, where, text.startsWith("FW_BinaryFile=") ? "binary-file" : "database",
                    "database and binary-file cells have no place in a database-free single-JVM run; the legacy "
                            + "parser only logged a STUB warning and ignored the cell");
        } else if (text.startsWith("FW_File=")) {
            fileValue(sheet, cells, seen, cell);
        } else if (text.startsWith("FW_Optional")) {
            trailingText(sheet, text, "FW_Optional", where);
            ValueCell previous = previousValue(sheet, cells, text, where);
            if (previous != null) {
                cells.set(cells.size() - 1, previous.asOptional());
            }
        } else if (text.startsWith("FW_RefineCodeExceptQuoted")) {
            trailingText(sheet, text, "FW_RefineCodeExceptQuoted", where);
            ValueCell previous = previousValue(sheet, cells, text, where);
            if (previous != null) {
                try {
                    cells.set(cells.size() - 1, previous.refinedTo(CodeRefiner.refine(previous.text())));
                } catch (CodeRefiner.TooComplexException e) {
                    diagnostics.add(Diagnostic.error(ErrorCode.INPUT_LIMIT, "FW_RefineCodeExceptQuoted cannot refine the "
                            + "value before it: " + e.getMessage(), where, "sheet", sheet.name()));
                }
            }
        } else if (text.startsWith("FW_EMPTY_STRING")) {
            trailingText(sheet, text, "FW_EMPTY_STRING", where);
            issue(cells, seen, ValueCell.of("", ValueKind.EMPTY, where));
        } else {
            String token = directiveToken(text);
            String hint = Suggestions.closest(token, CELL_DIRECTIVES.stream().map(ScenarioInterpreter::bare).toList(), 3);
            diagnostics.add(Diagnostic.error(ErrorCode.SPEC_UNKNOWN_DIRECTIVE, "'" + text + "' starts with FW_ but is "
                    + "not a cell directive" + (hint == null ? "" : "; did you mean " + hint + "?")
                    + " (the legacy parser logged 'not implemented' and dropped the cell)", where, "sheet", sheet.name(),
                    "directive", token));
        }
    }

    private void fileValue(InputSheet sheet, List<ValueCell> cells, Set<String> seen, InputCell cell) {
        String path = cell.text().substring("FW_File=".length());
        if (path.isEmpty()) {
            diagnostics.add(Diagnostic.error(ErrorCode.SPEC_DIRECTIVE_SYNTAX, "FW_File= needs the path of a file after "
                    + "the equals sign", cell.location(), "sheet", sheet.name(), "directive", "FW_File="));
            return;
        }
        try {
            issue(cells, seen, ValueCell.of(files.readText(path, cell.location()), ValueKind.FILE, cell.location()));
        } catch (FrameworkException e) {
            diagnostics.addAll(e.diagnostics());
        }
    }

    private ValueCell previousValue(InputSheet sheet, List<ValueCell> cells, String text, SourceLocation where) {
        if (cells.isEmpty()) {
            diagnostics.add(Diagnostic.error(ErrorCode.SPEC_DIRECTIVE_WITHOUT_TARGET, "'" + text + "' applies to the "
                    + "value before it, but sheet '" + sheet.name() + "' has no value before this cell (the legacy "
                    + "parser would have changed the last value of the previous sheet)", where, "sheet", sheet.name(),
                    "directive", directiveToken(text)));
            return null;
        }
        return cells.getLast();
    }

    /** The legacy parser matches these three directives by prefix; text after the name is ignored, so say so. */
    private void trailingText(InputSheet sheet, String text, String name, SourceLocation where) {
        if (text.length() > name.length()) {
            diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_DIRECTIVE_SYNTAX, "'" + text + "' is read as " + name
                    + "; the text after it ('" + text.substring(name.length()) + "') is ignored", where,
                    "sheet", sheet.name(), "directive", name));
        }
    }

    private void unsupported(InputSheet sheet, String text, SourceLocation where, String capability, String why) {
        String token = directiveToken(text);
        diagnostics.add(Diagnostic.error(ErrorCode.UNSUPPORTED_CAPABILITY, "the cell directive " + token + " is not "
                + "supported: " + why, where, "sheet", sheet.name(), "directive", token, "capability", capability));
    }

    /** {@code FW_File=data.txt} gives {@code FW_File=}; {@code FW_Optionl} gives {@code FW_Optionl}. */
    private static String directiveToken(String text) {
        int equals = text.indexOf('=');
        if (equals > 0 && text.substring(0, equals).matches("FW_[A-Za-z_]+")) {
            return text.substring(0, equals + 1);
        }
        int end = 3;
        while (end < text.length() && (Character.isLetter(text.charAt(end)) || text.charAt(end) == '_')) {
            end++;
        }
        return text.substring(0, end);
    }

    private static String bare(String directive) {
        return directive;
    }

    // ---- control sheets -------------------------------------------------------------------------------------

    private void unknownControlSheet(InputSheet sheet) {
        String hint = Suggestions.closest(sheet.name(), KNOWN_CONTROL_SHEETS, 3);
        diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_CONTROL_SHEET_UNKNOWN, "the sheet '" + sheet.name() + "' starts "
                + "with FW_ but is not a control sheet this framework knows" + (hint == null ? "" : "; did you mean "
                + hint + "?") + " It is ignored", sheet.location(), "sheet", sheet.name()));
    }

    private List<SeqRow> sequenceRows(InputSheet sheet) {
        List<SeqRow> rows = new ArrayList<>();
        int ordinal = 0;
        for (InputRow row : sheet.rows()) {
            List<LocatedText> cells = new ArrayList<>();
            for (InputCell cell : row.cells()) {
                if (!cell.text().isBlank()) {
                    cells.add(new LocatedText(cell.text(), cell.location()));
                }
            }
            if (!cells.isEmpty()) {
                rows.add(new SeqRow(ordinal, cells));
            }
            ordinal++;
        }
        return rows;
    }

    private List<SheetDecoration> sheetNames(InputSheet sheet) {
        List<SheetDecoration> rows = new ArrayList<>();
        Set<String> listed = new HashSet<>();
        for (InputRow row : sheet.rows()) {
            if (row.isEmpty()) {
                continue;
            }
            InputCell name = cellAt(row, 0);
            InputCell prefix = cellAt(row, 1);
            InputCell ending = cellAt(row, 2);
            SourceLocation where = row.cells().getFirst().location();
            if (name == null || name.text().isBlank()) {
                diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_CONTROL_ROW, "a FW_SheetNames row without a sheet name "
                        + "in its first column is ignored", where, "sheet", sheet.name()));
            } else if (ending == null) {
                diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_CONTROL_ROW, "the FW_SheetNames row of '" + name.text()
                        + "' has no ending in its third column and is ignored (the legacy parser ignored such rows too)",
                        where, "sheet", sheet.name(), "name", name.text()));
            } else {
                if (!listed.add(name.text())) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_CONTROL_ROW, "'" + name.text() + "' has more than "
                            + "one row in FW_SheetNames", name.location(), "sheet", sheet.name(), "name", name.text()));
                }
                rows.add(new SheetDecoration(name.text(), emptyIfMarker(prefix == null ? "" : prefix.text()),
                        emptyIfMarker(ending.text()), name.location()));
            }
        }
        return rows;
    }

    private List<CustomVar> customVars(InputSheet sheet) {
        List<CustomVar> rows = new ArrayList<>();
        for (InputRow row : sheet.rows()) {
            if (row.isEmpty()) {
                continue;
            }
            InputCell index = cellAt(row, 0);
            InputCell declaration = cellAt(row, 1);
            SourceLocation where = row.cells().getFirst().location();
            if (index == null || declaration == null) {
                diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_CONTROL_ROW, "a FW_CUSTOM_VAR row needs a number in its "
                        + "first column and a declaration in its second; this row is ignored (the legacy parser ignored "
                        + "such rows too)", where, "sheet", sheet.name()));
                continue;
            }
            try {
                rows.add(new CustomVar(Integer.parseInt(index.text()), emptyIfMarker(declaration.text()), index.location()));
            } catch (NumberFormatException e) {
                diagnostics.add(Diagnostic.error(ErrorCode.SPEC_CONTROL_ROW, "the first column of a FW_CUSTOM_VAR row must "
                        + "be a whole number, not '" + index.text() + "'", index.location(), "sheet", sheet.name()));
            }
        }
        return rows;
    }

    private List<LocatedText> runMeFirstOnce(InputSheet sheet) {
        List<LocatedText> code = nonBlankCells(sheet);
        if (!code.isEmpty()) {
            String first = code.getFirst().text();
            if (!runMeFirstOnceShaped(first)) {
                diagnostics.add(Diagnostic.warning(ErrorCode.SPEC_RUNMEFIRSTONCE_SHAPE, "the FW_RunMeFirstOnce cell is "
                        + "expected to declare 'class RunMeFirstOnce' with 'public static String FW_ARGS' assigned in it",
                        code.getFirst().location(), "sheet", sheet.name()));
            }
        }
        return code;
    }

    private static List<LocatedText> nonBlankCells(InputSheet sheet) {
        List<LocatedText> texts = new ArrayList<>();
        for (InputRow row : sheet.rows()) {
            for (InputCell cell : row.cells()) {
                if (!cell.text().isBlank()) {
                    texts.add(new LocatedText(cell.text(), cell.location()));
                }
            }
        }
        return texts;
    }

    private static InputCell cellAt(InputRow row, int column) {
        for (InputCell cell : row.cells()) {
            if (cell.column() == column) {
                return cell;
            }
        }
        return null;
    }

    /** Whether a FW_RunMeFirstOnce cell has the shape the reader expects (class RunMeFirstOnce assigning FW_ARGS); the editors ask too. */
    public static boolean runMeFirstOnceShaped(String code) {
        return RUNME_CLASS.matcher(code).matches() && RUNME_FIELD.matcher(code).matches() && RUNME_ASSIGNMENT.matcher(code).matches();
    }

    /** The legacy control-sheet reader turns FW_EMPTY_STRING (any case) into the empty string. */
    private static String emptyIfMarker(String text) {
        return "FW_EMPTY_STRING".equalsIgnoreCase(text) ? "" : text;
    }
}
