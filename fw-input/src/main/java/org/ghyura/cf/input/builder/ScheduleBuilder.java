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

package org.ghyura.cf.input.builder;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A fluent Java way to write a schedule, adapted from the legacy {@code ProgrammaticScheduleBuilder}. It builds
 * the same {@link WorkbookData} a JSON, YAML, TOML or XLSX file with the same sheets would, in the legacy layout:
 *
 * <ul>
 *   <li>sheet order: {@code FW_Seq}, {@code FW_SheetNames}, then {@code FW_RunMeFirstOnce}, {@code FW_Arguments}
 *       and {@code FW_CUSTOM_VAR} when set, then the payload sheets in declaration order;</li>
 *   <li>an {@code FW_Seq} row per payload sheet: the name, up to two flag tokens in columns 1 and 2 (an empty
 *       string cell when there is no flag, as legacy wrote them), up to three verbs in columns 3 to 5; joiner
 *       rows come last with only the name and the {@code FW_(...)} expression in column 3;</li>
 *   <li>{@code FW_SheetNames}: name, {@code FW_EMPTY_STRING}, {@code FW_EMPTY_STRING} for every sheet.</li>
 * </ul>
 *
 * Where the legacy builder silently dropped a third flag or a fourth verb, this one refuses with a located
 * error. A builder is single-threaded; {@link #build()} may be called more than once and always returns an
 * independent immutable model.
 */
public final class ScheduleBuilder {

    private static final String ORIGIN = "builder";
    private static final int MAX_FLAGS = 2;
    private static final int MAX_VERBS = 3;

    private static final class SheetSpec {
        final String name;
        final List<Flag> flags = new ArrayList<>(2);
        final List<String> verbs = new ArrayList<>(2);
        final List<List<String>> rows = new ArrayList<>();

        SheetSpec(String name) {
            this.name = name;
        }
    }

    private final Map<String, SheetSpec> sheets = new LinkedHashMap<>();
    private final Map<String, String> spellings = new LinkedHashMap<>();
    private final Map<String, String> joiners = new LinkedHashMap<>();
    private String runMeFirstOnce;
    private String arguments;
    private final Map<Integer, String> customVars = new LinkedHashMap<>();

    private ScheduleBuilder() {
    }

    public static ScheduleBuilder newSchedule() {
        return new ScheduleBuilder();
    }

    /** Introduces, or re-selects, a payload sheet. */
    public SheetEntry sheet(String name) {
        requireName(name);
        return new SheetEntry(this, specFor(name));
    }

    /** Introduces a joiner: one {@code FW_Seq} row carrying its {@code FW_(...)} expression, plus a same-named sheet. */
    public SheetEntry join(String name, JoinSpec spec) {
        requireName(name);
        if (spec == null) {
            throw new IllegalArgumentException("join: spec must not be null");
        }
        SheetSpec target = specFor(name);
        joiners.put(name, spec.toFwExpression());
        return new SheetEntry(this, target);
    }

    /** Like {@link #join} for forms {@link JoinSpec} cannot express; the expression must start with {@code FW_(}. */
    public SheetEntry joinRaw(String name, String expression) {
        requireName(name);
        if (expression == null || !expression.startsWith("FW_(")) {
            throw new IllegalArgumentException("joinRaw: the expression must start with 'FW_('");
        }
        SheetSpec target = specFor(name);
        joiners.put(name, expression);
        return new SheetEntry(this, target);
    }

    public ScheduleBuilder runMeFirstOnce(String javaSource) {
        this.runMeFirstOnce = javaSource;
        return this;
    }

    public ScheduleBuilder arguments(String args) {
        this.arguments = args;
        return this;
    }

    public ScheduleBuilder customVar(int index, String declaration) {
        customVars.put(index, declaration);
        return this;
    }

    /** @throws FrameworkException INPUT_SCHEMA when a sheet has more than two flags or three verbs */
    public WorkbookData build() {
        List<InputSheet> out = new ArrayList<>();

        List<InputRow> seqRows = new ArrayList<>();
        int seqRow = 0;
        for (SheetSpec s : sheets.values()) {
            if (joiners.containsKey(s.name)) {
                continue;
            }
            if (s.flags.size() > MAX_FLAGS) {
                throw tooMany(s.name, "flags", MAX_FLAGS, s.flags.size());
            }
            if (s.verbs.size() > MAX_VERBS) {
                throw tooMany(s.name, "verbs", MAX_VERBS, s.verbs.size());
            }
            List<InputCell> cells = new ArrayList<>();
            cells.add(cell("FW_Seq", seqRow, 0, s.name));
            cells.add(cell("FW_Seq", seqRow, 1, flagToken(s.flags, 0)));
            cells.add(cell("FW_Seq", seqRow, 2, flagToken(s.flags, 1)));
            for (int i = 0; i < s.verbs.size(); i++) {
                cells.add(cell("FW_Seq", seqRow, 3 + i, s.verbs.get(i)));
            }
            seqRows.add(new InputRow(seqRow, cells));
            seqRow++;
        }
        for (Map.Entry<String, String> joiner : joiners.entrySet()) {
            seqRows.add(new InputRow(seqRow, List.of(cell("FW_Seq", seqRow, 0, joiner.getKey()),
                    cell("FW_Seq", seqRow, 3, joiner.getValue()))));
            seqRow++;
        }
        out.add(sheet("FW_Seq", seqRows));

        List<InputRow> nameRows = new ArrayList<>();
        int nameRow = 0;
        for (String name : sheets.keySet()) {
            nameRows.add(new InputRow(nameRow, List.of(cell("FW_SheetNames", nameRow, 0, name),
                    cell("FW_SheetNames", nameRow, 1, "FW_EMPTY_STRING"), cell("FW_SheetNames", nameRow, 2, "FW_EMPTY_STRING"))));
            nameRow++;
        }
        out.add(sheet("FW_SheetNames", nameRows));

        if (runMeFirstOnce != null) {
            out.add(sheet("FW_RunMeFirstOnce", List.of(new InputRow(0, List.of(cell("FW_RunMeFirstOnce", 0, 0, runMeFirstOnce))))));
        }
        if (arguments != null) {
            out.add(sheet("FW_Arguments", List.of(new InputRow(0, List.of(cell("FW_Arguments", 0, 0, arguments))))));
        }
        if (!customVars.isEmpty()) {
            List<InputRow> rows = new ArrayList<>();
            int index = 0;
            for (Map.Entry<Integer, String> e : customVars.entrySet()) {
                rows.add(new InputRow(index, List.of(cell("FW_CUSTOM_VAR", index, 0, String.valueOf(e.getKey())),
                        cell("FW_CUSTOM_VAR", index, 1, e.getValue()))));
                index++;
            }
            out.add(sheet("FW_CUSTOM_VAR", rows));
        }

        for (SheetSpec s : sheets.values()) {
            List<InputRow> rows = new ArrayList<>();
            int rowIndex = 0;
            for (List<String> rowCells : s.rows) {
                List<InputCell> cells = new ArrayList<>();
                for (int c = 0; c < rowCells.size(); c++) {
                    if (rowCells.get(c) != null) {
                        cells.add(cell(s.name, rowIndex, c, rowCells.get(c)));
                    }
                }
                rows.add(new InputRow(rowIndex, cells));
                rowIndex++;
            }
            out.add(sheet(s.name, rows));
        }
        return new WorkbookData("programmatic:builder", out);
    }

    private static InputSheet sheet(String name, List<InputRow> rows) {
        return new InputSheet(name, SourceLocation.ofPath(ORIGIN, "sheet:" + name), rows);
    }

    private static InputCell cell(String sheet, int row, int column, String text) {
        return new InputCell(column, CellKind.TEXT, text, SourceLocation.ofPath(ORIGIN, sheet + "/" + row + "/" + column));
    }

    private static String flagToken(List<Flag> flags, int index) {
        return index >= flags.size() ? "" : flags.get(index).token();
    }

    private static FrameworkException tooMany(String sheet, String what, int max, int actual) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA,
                "sheet '" + sheet + "' has " + actual + " " + what + " but an FW_Seq row holds at most " + max,
                SourceLocation.ofPath(ORIGIN, "sheet:" + sheet), "sheet", sheet, what, Integer.toString(actual)));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("sheet/join name must not be blank");
        }
        if (name.startsWith("FW_")) {
            throw new IllegalArgumentException("sheet/join name '" + name + "' collides with the FW_* control namespace");
        }
    }

    /** The spec of this sheet; names are unique ignoring case, as in a workbook, and keep their first spelling. */
    private SheetSpec specFor(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        String existing = spellings.putIfAbsent(key, name);
        if (existing != null && !existing.equals(name)) {
            throw new IllegalArgumentException("sheet name '" + name + "' collides with '" + existing
                    + "' (sheet names are unique ignoring case)");
        }
        return sheets.computeIfAbsent(name, SheetSpec::new);
    }

    /** One sheet (or joiner) being filled; navigation methods return to the root builder. */
    public static final class SheetEntry {
        private final ScheduleBuilder parent;
        private final SheetSpec spec;

        private SheetEntry(ScheduleBuilder parent, SheetSpec spec) {
            this.parent = parent;
            this.spec = spec;
        }

        public SheetEntry flag(Flag flag) {
            if (flag != null) {
                spec.flags.add(flag);
            }
            return this;
        }

        public SheetEntry flags(Flag... flags) {
            if (flags != null) {
                for (Flag f : flags) {
                    flag(f);
                }
            }
            return this;
        }

        public SheetEntry verb(String token) {
            if (token != null) {
                spec.verbs.add(token);
            }
            return this;
        }

        public SheetEntry verbs(String... tokens) {
            if (tokens != null) {
                for (String t : tokens) {
                    verb(t);
                }
            }
            return this;
        }

        /** Appends a one-cell payload row. */
        public SheetEntry row(String value) {
            List<String> r = new ArrayList<>(1);
            r.add(value);
            spec.rows.add(r);
            return this;
        }

        public SheetEntry rows(String... values) {
            if (values != null) {
                for (String v : values) {
                    row(v);
                }
            }
            return this;
        }

        /** Appends a multi-cell payload row; a null cell is left absent and keeps its column. */
        public SheetEntry rowCells(String... cells) {
            List<String> r = new ArrayList<>(cells == null ? 0 : cells.length);
            if (cells != null) {
                for (String c : cells) {
                    r.add(c);
                }
            }
            spec.rows.add(r);
            return this;
        }

        public SheetEntry sheet(String name) {
            return parent.sheet(name);
        }

        public SheetEntry join(String name, JoinSpec joinSpec) {
            return parent.join(name, joinSpec);
        }

        public SheetEntry joinRaw(String name, String expression) {
            return parent.joinRaw(name, expression);
        }

        public ScheduleBuilder runMeFirstOnce(String source) {
            return parent.runMeFirstOnce(source);
        }

        public ScheduleBuilder arguments(String args) {
            return parent.arguments(args);
        }

        public ScheduleBuilder customVar(int index, String declaration) {
            return parent.customVar(index, declaration);
        }

        public WorkbookData build() {
            return parent.build();
        }

        public ScheduleBuilder and() {
            return parent;
        }
    }
}
