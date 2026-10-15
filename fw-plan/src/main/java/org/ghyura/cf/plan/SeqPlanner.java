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

package org.ghyura.cf.plan;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Suggestions;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.spec.SheetSpec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Reads the FW_Seq rows of a {@link ScenarioSpec} into a {@link SeqPlan}: a port of the legacy
 * {@code SeqParser} and of the virtual-sheet pre-scan of {@code WorkbookParser}, with the same rules in the same
 * order (a flag moves a sheet only while it is mandatory, the target of a row carries over to the next row, a
 * later row replaces an earlier row's directives, a single combination verb is repeated), but with every
 * place where the legacy code carried on silently reported at the cell that caused it, and with every
 * reference resolved before the plan exists.
 */
public final class SeqPlanner {

    /** The plan and the findings that did not stop it. */
    public record Planned(SeqPlan plan, List<Diagnostic> diagnostics) {
        public Planned {
            Objects.requireNonNull(plan, "plan");
            diagnostics = List.copyOf(diagnostics);
        }
    }

    private enum Flag {
        EXCLUDE("FW_Exclude"), HEADING("FW_Heading"), LAST_IN_QUEUE("FW_LastInQueue"), REUSE("FW_Reuse"),
        REUSE_TABLE_ONLY("FW_ReuseTableOnly"), OPTIONAL("FW_Optional");

        private final String token;

        Flag(String token) {
            this.token = token;
        }
    }

    /** Operands that are directives or flags, never sheets, when the pre-scan looks for missing sheets. */
    private static final List<String> NON_TARGET_PREFIXES = List.of("FW_Optional", "FW_Heading", "FW_Exclude",
            "FW_LastInQueue", "FW_Reuse", "FW_ReuseTableOnly", "FW_Concatenator", "FW_Subsets", "FW_Permut", "FW_Combi",
            "FW_Cartes", "FW_Group", "FW_ReplaceRE", "FW_Separator");
    private static final List<String> KNOWN_TOKENS = List.of("FW_Exclude", "FW_Heading", "FW_LastInQueue", "FW_Reuse",
            "FW_ReuseTableOnly", "FW_Optional", "FW_Concatenator", "FW_Combi", "FW_CombiR", "FW_Permut", "FW_PermutR",
            "FW_Subsets", "FW_Cartes", "FW_Group", "FW_Separator");
    private static final Pattern MULTIPLICITY_SHAPE = Pattern.compile("[1MNm]:[1MNn]");

    private static final class SheetState {
        final String name;
        final int key;
        final boolean virtual;
        final int valueCount;
        final SourceLocation location;
        SheetRole role;
        boolean heading;
        boolean lastInQueue;
        boolean reuse;
        boolean reuseTableOnly;
        String concatenator;
        List<Directive> directives = List.of();
        int directivesRow = -1;
        boolean promoted;

        SheetState(String name, int key, boolean virtual, int valueCount, SourceLocation location) {
            this.name = name;
            this.key = key;
            this.virtual = virtual;
            this.valueCount = valueCount;
            this.location = location;
            this.role = valueCount > 0 ? SheetRole.MANDATORY : SheetRole.NONE;
        }
    }

    private final ScenarioSpec spec;
    private final PlanOptions options;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private final Map<String, Integer> keys = new LinkedHashMap<>();
    private final List<SheetState> states = new ArrayList<>();
    private final Map<Integer, Integer> headless = new LinkedHashMap<>();
    private final List<Integer> firstOperands = new ArrayList<>();
    private final List<Integer> secondOperands = new ArrayList<>();
    private final List<Integer> priorJoinerTargets = new ArrayList<>();
    private String target;

    private SeqPlanner(ScenarioSpec spec, PlanOptions options) {
        this.spec = spec;
        this.options = options;
    }

    /**
     * @throws FrameworkException with every finding when at least one of them is an error
     */
    public static Planned plan(ScenarioSpec spec, PlanOptions options) {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(options, "options");
        return new SeqPlanner(spec, options).run();
    }

    private Planned run() {
        if (!spec.hasSequenceSheet() || spec.sequence().isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.PLAN_NO_SEQUENCE, spec.hasSequenceSheet()
                    ? "the FW_Seq sheet has no row with content" : "the scenario has no FW_Seq sheet",
                    SourceLocation.of(spec.source())));
        }
        for (SheetSpec sheet : spec.sheets()) {
            register(sheet.name(), false, sheet.cells().size(), sheet.location());
        }
        if (options.autoGenerateMissingSheets()) {
            discoverVirtualSheets();
        }
        for (SeqRow row : spec.sequence()) {
            row(row);
        }
        checkReferences();
        checkCycles();
        List<SheetDecoration> decorations = decorations();
        if (diagnostics.stream().anyMatch(d -> d.severity().isError())) {
            throw new FrameworkException(diagnostics);
        }
        List<SheetPlan> sheets = new ArrayList<>();
        for (SheetState s : states) {
            sheets.add(new SheetPlan(s.name, s.key, s.virtual, s.role, s.heading, s.lastInQueue, s.reuse, s.reuseTableOnly,
                    s.concatenator, s.directives, s.promoted, s.valueCount, s.location));
        }
        return new Planned(new SeqPlan(sheets, headless, firstOperands, secondOperands, decorations, options), diagnostics);
    }

    private int register(String name, boolean virtual, int valueCount, SourceLocation location) {
        int key = states.size() + 1;
        keys.put(name, key);
        states.add(new SheetState(name, key, virtual, valueCount, location));
        return key;
    }

    // ---- virtual sheets ----------------------------------------------------------------------------------------

    /** The sheets FW_Seq mentions that the workbook lacks, found with the legacy pre-scan's rules (it trims). */
    private void discoverVirtualSheets() {
        Set<String> known = new LinkedHashSet<>(keys.keySet());
        for (SeqRow row : spec.sequence()) {
            LocatedText first = row.cells().getFirst();
            String firstText = first.text().trim();
            if (firstText.startsWith("FW_")) {
                String name = options.virtualSheetPrefix() + (row.ordinal() + 1);
                int suffix = 1;
                while (known.contains(name)) {
                    suffix++;
                    name = options.virtualSheetPrefix() + (row.ordinal() + 1) + "_" + suffix;
                }
                known.add(name);
                headless.put(row.ordinal(), register(name, true, 0, first.location()));
                diagnostics.add(Diagnostic.of(Severity.INFO, ErrorCode.PLAN_VIRTUAL_SHEET, "FW_Seq row " + (row.ordinal() + 1)
                        + " starts with '" + firstText + "' and names no sheet; the generated sheet '" + name + "' is its "
                        + "target", first.location(), "sheet", name, "row", Integer.toString(row.ordinal() + 1)));
            } else if (!known.contains(firstText)) {
                known.add(firstText);
                register(firstText, true, 0, first.location());
                diagnostics.add(virtual(firstText, "the first cell of FW_Seq row " + (row.ordinal() + 1), first.location()));
            }
            for (LocatedText cell : row.cells()) {
                String text = cell.text().trim();
                if (!text.startsWith("FW_(") || !text.endsWith(")")) {
                    continue;
                }
                List<String> parts = DirectiveParser.splitTopLevelByComma(text.substring(text.indexOf('(') + 1,
                        text.lastIndexOf(')')));
                for (int i = 0; i < parts.size(); i++) {
                    String operand = parts.get(i);
                    if (operand.isEmpty() || operand.startsWith("FW_(") || operand.equals("FW_()")) {
                        continue;
                    }
                    if (i == parts.size() - 1 && MULTIPLICITY_SHAPE.matcher(operand).matches()) {
                        continue;
                    }
                    if (NON_TARGET_PREFIXES.stream().anyMatch(operand::startsWith)) {
                        continue;
                    }
                    if (known.add(operand)) {
                        register(operand, true, 0, cell.location());
                        diagnostics.add(virtual(operand, "the joiner '" + text + "' in FW_Seq row " + (row.ordinal() + 1),
                                cell.location()));
                    }
                }
            }
        }
    }

    private Diagnostic virtual(String name, String where, SourceLocation location) {
        return Diagnostic.warning(ErrorCode.PLAN_VIRTUAL_SHEET, where + " refers to '" + name + "', which is not a data "
                + "sheet; an empty virtual sheet of that name was generated (if '" + name + "' is a typo, everything "
                + "joined with it is empty; the separator field names a sheet, it is not literal text)", location,
                "sheet", name);
    }

    // ---- rows --------------------------------------------------------------------------------------------------

    private void row(SeqRow row) {
        List<Directive> directives = new ArrayList<>();
        Integer synthetic = options.headlessRowsGetTarget() ? headless.get(row.ordinal()) : null;
        boolean ownTarget = false;
        String generated = synthetic == null ? null : states.get(synthetic - 1).name;
        LocatedText hitGenerated = null;
        if (synthetic != null) {
            target = generated;
            ownTarget = true;
        }
        for (LocatedText cell : row.cells()) {
            // A sheet name matches exactly; flags and directives are recognised without the white space around
            // them (architect decision F23), except that a concatenator keeps everything after its '='.
            String written = cell.text();
            String text = written.strip();
            SourceLocation where = cell.location();
            if (keys.containsKey(written)) {
                text = written;
                if (hitGenerated != null && !text.equals(generated)) {
                    ambiguous(hitGenerated.text() + " comes before the sheet name '" + text + "' of its row, so it applies to the "
                            + "generated sheet '" + generated + "' (as in the legacy parser), not to '" + text + "'",
                            hitGenerated.location());
                    hitGenerated = null;
                }
                target = text;
                ownTarget = true;
                continue;
            }
            Integer key = target == null ? null : keys.get(target);
            Flag flag = flagOf(text);
            if (flag != null) {
                if (key == null) {
                    diagnostics.add(Diagnostic.error(ErrorCode.PLAN_UNKNOWN_SHEET, text + " has no sheet to apply to: no "
                            + "sheet name came before it in FW_Seq", where, "flag", text));
                    continue;
                }
                if (!ownTarget) {
                    ambiguous(text + " comes before the sheet name of its row, so it applies to '" + target + "', the "
                            + "target of an earlier row", where);
                }
                apply(flag, states.get(key - 1), where);
                if (hitGenerated == null && generated != null && generated.equals(target)) {
                    hitGenerated = cell;
                }
            } else if (text.startsWith("FW_Concatenator")) {
                concatenator(written.stripLeading(), key, ownTarget, where);
                if (hitGenerated == null && generated != null && generated.equals(target)) {
                    hitGenerated = cell;
                }
            } else if (text.startsWith("FW_")) {
                Directive directive = directive(written, key, where);
                if (directive != null) {
                    directives.add(directive);
                }
            } else {
                Flag suffixed = flagTheTextEndsWith(text);
                if (suffixed != null) {
                    diagnostics.add(Diagnostic.error(ErrorCode.PLAN_UNKNOWN_DIRECTIVE, "'" + written + "' ends with "
                            + suffixed.token + ", which the legacy parser accepted as that flag; write the flag alone in its "
                            + "cell", where, "directive", written));
                } else {
                    ignored(written, where);
                }
            }
        }
        if (directives.isEmpty()) {
            return;
        }
        if (target == null) {
            return; // every directive above already reported that it has no sheet
        }
        if (!ownTarget) {
            ambiguous("FW_Seq row " + (row.ordinal() + 1) + " names no sheet, so its directives apply to '" + target
                    + "', the target of an earlier row", row.cells().getFirst().location());
        }
        boolean promoted = promote(directives);
        SheetState state = states.get(keys.get(target) - 1);
        if (!state.directives.isEmpty() && state.directivesRow != row.ordinal()) {
            diagnostics.add(Diagnostic.info(ErrorCode.PLAN_ROW_OVERWRITTEN, "FW_Seq row " + (row.ordinal() + 1)
                    + " replaces the directives that row " + (state.directivesRow + 1) + " gave to '" + state.name + "'; "
                    + "only the last row counts", row.cells().getFirst().location(), "sheet", state.name));
        }
        state.directives = directives;
        state.directivesRow = row.ordinal();
        state.promoted = promoted;
    }

    private static Flag flagTheTextEndsWith(String text) {
        for (Flag flag : Flag.values()) {
            if (text.endsWith(flag.token)) {
                return flag;
            }
        }
        return null;
    }

    private static Flag flagOf(String text) {
        for (Flag flag : Flag.values()) {
            if (flag.token.equals(text)) {
                return flag;
            }
        }
        return null;
    }

    private void apply(Flag flag, SheetState sheet, SourceLocation where) {
        switch (flag) {
            case EXCLUDE, HEADING -> {
                if (flag == Flag.HEADING) {
                    sheet.heading = true;
                }
                if (sheet.role == SheetRole.MANDATORY) {
                    sheet.role = SheetRole.EXCLUDED;
                } else if (sheet.role == SheetRole.OPTIONAL) {
                    conflict(sheet, flag.token, "FW_Optional", where);
                }
            }
            case OPTIONAL -> {
                if (sheet.role == SheetRole.MANDATORY) {
                    sheet.role = SheetRole.OPTIONAL;
                } else if (sheet.role == SheetRole.EXCLUDED) {
                    conflict(sheet, flag.token, "FW_Exclude or FW_Heading", where);
                }
            }
            case LAST_IN_QUEUE -> {
                if (sheet.role == SheetRole.MANDATORY) {
                    sheet.lastInQueue = true;
                }
            }
            case REUSE -> sheet.reuse = true;
            case REUSE_TABLE_ONLY -> sheet.reuseTableOnly = true;
        }
    }

    private void conflict(SheetState sheet, String flag, String earlier, SourceLocation where) {
        diagnostics.add(Diagnostic.warning(ErrorCode.PLAN_CONFLICTING_FLAGS, "'" + sheet.name + "' is flagged " + flag
                + " but already moved by " + earlier + "; only the first of the two counts", where, "sheet", sheet.name));
    }

    private void concatenator(String text, Integer key, boolean ownTarget, SourceLocation where) {
        if (!text.startsWith("FW_Concatenator=")) {
            diagnostics.add(Diagnostic.error(ErrorCode.PLAN_DIRECTIVE_SYNTAX, "'" + text + "': a concatenator is written "
                    + "FW_Concatenator=text (the legacy parser took the whole cell text as the concatenator)", where,
                    "directive", text));
            return;
        }
        if (key == null) {
            diagnostics.add(Diagnostic.error(ErrorCode.PLAN_UNKNOWN_SHEET, text + " has no sheet to apply to: no sheet name "
                    + "came before it in FW_Seq", where));
            return;
        }
        if (!ownTarget) {
            ambiguous(text + " comes before the sheet name of its row, so it applies to '" + target + "', the target of an "
                    + "earlier row", where);
        }
        states.get(key - 1).concatenator = text.substring("FW_Concatenator=".length());
    }

    private Directive directive(String text, Integer key, SourceLocation where) {
        Directive directive;
        try {
            directive = DirectiveParser.parse(text, where);
        } catch (FrameworkException e) {
            diagnostics.addAll(e.diagnostics());
            return null;
        }
        if (directive == null) {
            unknownDirective(text, where);
            return null;
        }
        if (key == null) {
            diagnostics.add(Diagnostic.error(ErrorCode.PLAN_UNKNOWN_SHEET, text + " has no sheet to apply to: no sheet name "
                    + "came before it in FW_Seq", where, "directive", text));
            return null;
        }
        if (directive instanceof Directive.Joiner joiner) {
            Directive.Joiner resolved = resolveNested(joiner);
            firstOperands.add(operandKey(resolved.first()));
            secondOperands.add(operandKey(resolved.second()));
            priorJoinerTargets.add(key);
            return resolved;
        }
        return directive;
    }

    private int operandKey(Directive.Operand operand) {
        Integer key = operand.sheet() == null ? null : keys.get(operand.sheet());
        return key == null ? 0 : key;
    }

    /** Placeholders FW_() and FW_()G name the most recent earlier joiner targets, newest first, as in the legacy parser. */
    private Directive.Joiner resolveNested(Directive.Joiner joiner) {
        if (!joiner.first().isPlaceholder() && !joiner.second().isPlaceholder()) {
            return joiner;
        }
        if (!options.nestedJoinersEnabled()) {
            diagnostics.add(Diagnostic.error(ErrorCode.PLAN_DIRECTIVE_SYNTAX, "'" + joiner.source() + "' uses the nested "
                    + "placeholder FW_() but nested joiners are disabled in the plan options", joiner.location(),
                    "directive", joiner.source()));
            return joiner;
        }
        int index = priorJoinerTargets.size() - 1;
        Directive.Operand first = joiner.first();
        Directive.Operand second = joiner.second();
        if (first.isPlaceholder()) {
            if (index >= 0) {
                first = first.resolvedTo(nestedTarget(priorJoinerTargets.get(index--)));
            } else {
                noEarlierJoiner(joiner, "first");
            }
        }
        if (second.isPlaceholder()) {
            if (index >= 0) {
                second = second.resolvedTo(nestedTarget(priorJoinerTargets.get(index)));
            } else {
                noEarlierJoiner(joiner, "second");
            }
        }
        return joiner.withOperands(first, second);
    }

    private String nestedTarget(int key) {
        SheetState sheet = states.get(key - 1);
        sheet.reuse = true;
        sheet.reuseTableOnly = true;
        return sheet.name;
    }

    private void noEarlierJoiner(Directive.Joiner joiner, String which) {
        diagnostics.add(Diagnostic.error(ErrorCode.PLAN_UNKNOWN_SHEET, "the " + which + " table of '" + joiner.source()
                + "' refers to an earlier joiner with FW_(), but there is none (the legacy parser used an empty table and "
                + "produced no rows)", joiner.location(), "directive", joiner.source()));
    }

    /**
     * The legacy rule: with no joiner and exactly one combination verb in the row, repeat it, so that both table
     * generations a joiner may read are filled. It looks at the cell text, so a verb without brackets and the
     * FW_Subsets_MODE forms are not repeated.
     */
    private boolean promote(List<Directive> directives) {
        if (!options.autoPromoteSingleVerb()) {
            return false;
        }
        int comboIndex = -1;
        int comboCount = 0;
        for (int i = 0; i < directives.size(); i++) {
            Directive directive = directives.get(i);
            if (directive instanceof Directive.Joiner) {
                return false;
            }
            if (isComboRuleVerb(directive.source().strip())) {
                comboCount++;
                if (comboIndex < 0) {
                    comboIndex = i;
                }
            }
        }
        if (comboCount != 1) {
            return false;
        }
        directives.add(directives.get(comboIndex));
        return true;
    }

    private static boolean isComboRuleVerb(String text) {
        return text.startsWith("FW_Combi(") || text.startsWith("FW_CombiR(") || text.startsWith("FW_Permut(")
                || text.startsWith("FW_PermutR(") || text.equals("FW_Subsets") || text.startsWith("FW_Subsets(");
    }

    private void unknownDirective(String text, SourceLocation where) {
        int end = 3;
        while (end < text.length() && (Character.isLetter(text.charAt(end)) || text.charAt(end) == '_')) {
            end++;
        }
        String token = text.substring(0, end);
        String hint = Suggestions.closest(token, KNOWN_TOKENS, 3);
        for (Flag flag : Flag.values()) {
            if (text.endsWith(flag.token) && hint == null) {
                hint = flag.token;
            }
        }
        diagnostics.add(Diagnostic.error(ErrorCode.PLAN_UNKNOWN_DIRECTIVE, "'" + text + "' is not a FW_Seq flag or directive"
                + (hint == null ? "" : "; did you mean " + hint + "?") + " (the legacy parser kept it and never used it)",
                where, "directive", text));
    }

    private void ignored(String text, SourceLocation where) {
        String trimmed = text.trim();
        String why = keys.containsKey(trimmed)
                ? " It has white space around the name of a sheet; the legacy parser matches target names exactly, so it "
                + "matches nothing"
                : "";
        diagnostics.add(Diagnostic.warning(ErrorCode.PLAN_IGNORED_CELL, "the FW_Seq cell '" + text + "' is neither the name "
                + "of a sheet nor an FW_ token and is ignored." + why, where, "text", text));
    }

    private void ambiguous(String message, SourceLocation where) {
        diagnostics.add(Diagnostic.warning(ErrorCode.PLAN_AMBIGUOUS_TARGET, message, where));
    }

    // ---- references and cycles ---------------------------------------------------------------------------------

    private void checkReferences() {
        for (SheetState sheet : states) {
            for (Directive directive : sheet.directives) {
                switch (directive) {
                    case Directive.Cartes c -> require(c.sheet(), "FW_Cartes", c);
                    case Directive.Separator s -> require(s.sheet(), "FW_Separator", s);
                    case Directive.Group g -> {
                        for (Directive.Replacement r : g.replacements()) {
                            if (r.sheetReference() != null) {
                                require(r.sheetReference(), "the replacement " + r.written(), g);
                            }
                        }
                    }
                    case Directive.Joiner j -> {
                        requireIfPresent(j.start(), "the start of a joiner", j);
                        requireIfPresent(j.relation(), "the relation of a joiner", j);
                        requireIfPresent(j.end(), "the end of a joiner", j);
                        requireIfPresent(j.separator(), "the separator of a joiner", j);
                        require(j.first().sheet(), "the first table of a joiner", j);
                        require(j.second().sheet(), "the second table of a joiner", j);
                    }
                    case Directive.Combinatoric c -> {
                        // refers to no other sheet
                    }
                    case Directive.Subsets s -> {
                        // refers to no other sheet
                    }
                }
            }
        }
    }

    private void requireIfPresent(String name, String what, Directive directive) {
        if (!name.isEmpty()) {
            require(name, what, directive);
        }
    }

    private void require(String name, String what, Directive directive) {
        if (name == null || keys.containsKey(name)) {
            return; // an unresolved placeholder was reported when it was read
        }
        String hint = name.isEmpty() ? null : Suggestions.closest(name, keys.keySet(), 2);
        diagnostics.add(Diagnostic.error(ErrorCode.PLAN_UNKNOWN_SHEET, what + " in '" + directive.source() + "' names '"
                + name + "', which is not a sheet" + (hint == null ? "" : "; did you mean '" + hint + "'?"),
                directive.location(), "sheet", name, "directive", directive.source()));
    }

    /** A joiner waits for the tables of its two operands; a circle of waits can never finish. */
    private void checkCycles() {
        Map<Integer, List<Integer>> waitsFor = new LinkedHashMap<>();
        Map<Integer, SourceLocation> where = new LinkedHashMap<>();
        for (SheetState sheet : states) {
            for (Directive directive : sheet.directives) {
                if (directive instanceof Directive.Joiner joiner) {
                    List<Integer> edges = waitsFor.computeIfAbsent(sheet.key, k -> new ArrayList<>());
                    where.putIfAbsent(sheet.key, joiner.location());
                    for (Directive.Operand operand : List.of(joiner.first(), joiner.second())) {
                        Integer key = operand.sheet() == null ? null : keys.get(operand.sheet());
                        if (key != null) {
                            edges.add(key);
                        }
                    }
                }
            }
        }
        // Depth-first search with an explicit stack (review finding IP02: the recursive version overflowed the
        // thread stack on an 8000-sheet chain). A node is white (absent), grey (1, on the current path) or
        // black (2, finished); an edge to a grey node closes a circle.
        Map<Integer, Integer> color = new HashMap<>();
        Set<Set<Integer>> reported = new HashSet<>();
        List<Integer> path = new ArrayList<>();
        List<Integer> nextEdge = new ArrayList<>();
        Map<Integer, Integer> depth = new HashMap<>();
        for (Integer start : waitsFor.keySet()) {
            if (color.containsKey(start)) {
                continue;
            }
            color.put(start, 1);
            depth.put(start, 0);
            path.add(start);
            nextEdge.add(0);
            while (!path.isEmpty()) {
                int top = path.size() - 1;
                int node = path.get(top);
                List<Integer> edges = waitsFor.getOrDefault(node, List.of());
                int edge = nextEdge.get(top);
                if (edge == edges.size()) {
                    color.put(node, 2);
                    depth.remove(node);
                    path.removeLast();
                    nextEdge.removeLast();
                    continue;
                }
                nextEdge.set(top, edge + 1);
                int next = edges.get(edge);
                int state = color.getOrDefault(next, 0);
                if (state == 0) {
                    color.put(next, 1);
                    depth.put(next, path.size());
                    path.add(next);
                    nextEdge.add(0);
                } else if (state == 1) {
                    reportCycle(new ArrayList<>(path.subList(depth.get(next), path.size())), next, where, reported);
                }
            }
        }
    }

    private void reportCycle(List<Integer> cycle, int closing, Map<Integer, SourceLocation> where, Set<Set<Integer>> reported) {
        if (!reported.add(new HashSet<>(cycle))) {
            return;
        }
        StringBuilder text = new StringBuilder();
        for (int key : cycle) {
            text.append(states.get(key - 1).name).append(" -> ");
        }
        text.append(states.get(closing - 1).name);
        diagnostics.add(Diagnostic.error(ErrorCode.PLAN_CYCLE, "joiners wait for each other's tables in a circle: "
                + text, where.getOrDefault(closing, SourceLocation.of(spec.source())), "cycle", text.toString()));
    }

    // ---- FW_SheetNames -----------------------------------------------------------------------------------------

    private List<SheetDecoration> decorations() {
        List<SheetDecoration> result = new ArrayList<>(spec.controls().sheetNames());
        Set<String> covered = new HashSet<>();
        for (SheetDecoration declared : spec.controls().sheetNames()) {
            covered.add(declared.sheet());
            if (!keys.containsKey(declared.sheet())) {
                String hint = Suggestions.closest(declared.sheet(), keys.keySet(), 2);
                diagnostics.add(Diagnostic.of(Severity.WARNING, ErrorCode.PLAN_UNKNOWN_SHEET, "FW_SheetNames lists '"
                        + declared.sheet() + "', which is not a sheet" + (hint == null ? "" : "; did you mean '" + hint + "'?"),
                        declared.location(), "sheet", declared.sheet()));
            }
        }
        if (options.autoGenerateSheetNames()) {
            for (SheetState sheet : states) {
                if (!covered.contains(sheet.name)) {
                    result.add(new SheetDecoration(sheet.name, "", "", sheet.location));
                }
            }
        }
        return result;
    }
}
