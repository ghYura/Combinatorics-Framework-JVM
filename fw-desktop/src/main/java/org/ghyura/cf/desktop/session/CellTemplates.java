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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The authoring palette (GEN:operator-authoring-ui; the legacy face1 grid_model TEMPLATES): every FW_Seq cell form the
 * Core reads, as templates with a stable id, a label, a category (row role, choose, order, set, compose, transform), the
 * cell text they put into a grid, a description in plain words and the legacy palette colour. Placeholders such as OTHER, LEFT, RIGHT,
 * START, RELATION, END and SEPARATOR stand for sheet names the author replaces. The list is the legacy palette's, in its
 * order; nothing here loads a windowing toolkit class.
 */
public final class CellTemplates {

    /**
     * One palette entry.
     *
     * @param directive the cell text it puts into the grid (one cell; the group cleanup cell holds several lines)
     * @param color     the legacy palette colour, as {@code #rrggbb}
     */
    public record CellTemplate(String id, String label, String category, String directive, String description, String color) {
        public CellTemplate {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(label, "label");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(directive, "directive");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(color, "color");
        }
    }

    /** Every template, in the legacy palette's order. */
    public static final List<CellTemplate> ALL = List.of(
            new CellTemplate("exclude", "FW_Exclude", "Row role", "FW_Exclude", "Makes this sheet a helper: its values are prepared for other sheets, for example for a join, but are not part of the test cases by themselves. Put it before FW_Reuse and before the cells that make values.", "#b45309"),
            new CellTemplate("reuse", "FW_Reuse", "Row role", "FW_Reuse", "Keeps the rows of a helper sheet after a join has used them, so a later join can use them again. Where this flag stands in the row does not matter.", "#b45309"),
            new CellTemplate("reuse_table", "FW_ReuseTableOnly", "Row role", "FW_ReuseTableOnly", "Keeps a helper sheet for later joins, but empties its rows once a join has used them.", "#b45309"),
            new CellTemplate("optional", "FW_Optional", "Row role", "FW_Optional", "Makes this sheet optional: test cases are made both with and without its values.", "#b45309"),
            new CellTemplate("heading", "FW_Heading", "Row role", "FW_Heading", "A helper sheet, like FW_Exclude, that also serves as a heading.", "#b45309"),
            new CellTemplate("last_queue", "FW_LastInQueue", "Row role", "FW_LastInQueue", "Moves this sheet to the end of the order in which the sheets are combined.", "#b45309"),
            new CellTemplate("concatenator", "FW_Concatenator", "Row role", "FW_Concatenator=", "Sets the text that goes between this sheet's values when they are put together; write it after the equals sign.", "#b45309"),
            new CellTemplate("combi_bare", "Choose \u00b7 default", "Choose", "FW_Combi", "Takes the sheet's values one at a time (the same as choosing 1).", "#0f766e"),
            new CellTemplate("combi_default", "Choose \u00b7 ()", "Choose", "FW_Combi()", "Takes the values one at a time; empty brackets mean 1.", "#0f766e"),
            new CellTemplate("combi_zero", "Choose \u00b7 zero", "Choose", "FW_Combi(0)", "Takes none of the values: one empty choice.", "#0f766e"),
            new CellTemplate("combi_one", "Choose \u00b7 one", "Choose", "FW_Combi(1)", "Takes the sheet's values one at a time.", "#0f766e"),
            new CellTemplate("combi_k", "Choose \u00b7 k", "Choose", "FW_Combi(2)", "Takes every group of k different values, in any order and without repeats. Change the number to set k.", "#0f766e"),
            new CellTemplate("combi_size", "Choose \u00b7 size", "Choose", "FW_Combi(size)", "Takes all of the sheet's values together, as one group.", "#0f766e"),
            new CellTemplate("combi_all", "Choose \u00b7 all", "Choose", "FW_Combi(all)", "Takes groups of every size, from one value up to all of them.", "#0f766e"),
            new CellTemplate("combi_full", "Choose \u00b7 full", "Choose", "FW_Combi(full)", "The same as Choose · all: groups of every size.", "#0f766e"),
            new CellTemplate("combi_repeat", "ChooseR \u00b7 k", "Choose", "FW_CombiR(2)", "Takes groups of k values in which a value may repeat, in any order. Change the number to set k.", "#0f766e"),
            new CellTemplate("combi_repeat_size", "ChooseR \u00b7 size", "Choose", "FW_CombiR(size)", "Groups in which values may repeat, as many values long as the sheet has values.", "#0f766e"),
            new CellTemplate("combi_repeat_all", "ChooseR \u00b7 all", "Choose", "FW_CombiR(all)", "Groups in which values may repeat, of every size.", "#0f766e"),
            new CellTemplate("combi_repeat_full", "ChooseR \u00b7 full", "Choose", "FW_CombiR(full)", "The same as ChooseR · all.", "#0f766e"),
            new CellTemplate("permut", "Permute all", "Order", "FW_Permut", "Every order of all the sheet's values.", "#3159c6"),
            new CellTemplate("permut_empty", "Permute \u00b7 ()", "Order", "FW_Permut()", "Every order of all the values (the same as Permute all).", "#3159c6"),
            new CellTemplate("permut_k", "Permute \u00b7 k", "Order", "FW_Permut(2)", "The number is accepted but ignored: you still get every order of all values. To order groups of k values, put Choose · k in this cell and Permute · () in the next one.", "#3159c6"),
            new CellTemplate("permut_identical", "Permute \u00b7 duplicates", "Order", "FW_Permut(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)", "Every order of the values, where equal values count as the same, so no order appears twice.", "#3159c6"),
            new CellTemplate("permut_repeat", "PermuteR \u00b7 k", "Order", "FW_PermutR(2)", "Ordered sequences of k values in which a value may repeat. Change the number to set k.", "#3159c6"),
            new CellTemplate("permut_repeat_size", "PermuteR \u00b7 size", "Order", "FW_PermutR(size)", "Ordered sequences in which values may repeat, as long as the sheet has values.", "#3159c6"),
            new CellTemplate("permut_repeat_all", "PermuteR \u00b7 all", "Order", "FW_PermutR(all)", "Ordered sequences in which values may repeat, of every length.", "#3159c6"),
            new CellTemplate("permut_repeat_full", "PermuteR \u00b7 full", "Order", "FW_PermutR(full)", "The same as PermuteR · all.", "#3159c6"),
            new CellTemplate("subsets", "Subsets \u00b7 default", "Set", "FW_Subsets", "Every subset of the values, from none of them to all of them.", "#287b95"),
            new CellTemplate("subsets_exact", "Subsets \u00b7 exact", "Set", "FW_Subsets_EXACT(2)", "Only the subsets of the given size.", "#287b95"),
            new CellTemplate("subsets_before", "Subsets \u00b7 before", "Set", "FW_Subsets_BEFORE(2)", "Only the subsets smaller than the given size.", "#287b95"),
            new CellTemplate("subsets_after", "Subsets \u00b7 after", "Set", "FW_Subsets_AFTER(2)", "Only the subsets larger than the given size.", "#287b95"),
            new CellTemplate("subsets_range", "Subsets \u00b7 range", "Set", "FW_Subsets_RANGE(2,3)", "Only the subsets whose size lies between the two numbers, both included.", "#287b95"),
            new CellTemplate("subsets_given", "Subsets \u00b7 given", "Set", "FW_Subsets_GIVEN(1,3)", "Only the subsets of the listed sizes.", "#287b95"),
            new CellTemplate("cartes", "Cartesian sheet", "Compose", "FW_Cartes(OTHER)", "Puts each value together with every value of another sheet; write that sheet's name in place of OTHER.", "#7c3aed"),
            new CellTemplate("cartes_first", "Cartesian baseline", "Compose", "FW_Cartes_first(OTHER)", "Puts each value together with only the first value of another sheet, its default; write that sheet's name in place of OTHER.", "#7c3aed"),
            new CellTemplate("separator", "Separator", "Compose", "FW_Separator(SEPARATOR)", "Puts the first value of a helper sheet between the values; write that sheet's name in place of SEPARATOR.", "#7c3aed"),
            new CellTemplate("brace_1_1", "Join \u00b7 1:1", "Compose", "FW_(,,LEFT,,RIGHT,,,,1:1)", "Rows of two helper sheets (LEFT and RIGHT) are joined: every row of one with every row of the other. Their values are taken in turn, one from each side; both rows must be equally long.", "#7c3aed"),
            new CellTemplate("brace_1_n", "Join \u00b7 1:N", "Compose", "FW_(,,LEFT,,RIGHT,,,,1:N)", "Rows of two helper sheets (LEFT and RIGHT) are joined: every row of one with every row of the other. After each LEFT value come the relation and the whole RIGHT row.", "#7c3aed"),
            new CellTemplate("brace_m_1", "Join \u00b7 M:1", "Compose", "FW_(,,LEFT,,RIGHT,,,,M:1)", "Rows of two helper sheets (LEFT and RIGHT) are joined: every row of one with every row of the other. The whole LEFT row and the relation come before each RIGHT value.", "#7c3aed"),
            new CellTemplate("brace_m_m", "Join \u00b7 M:M", "Compose", "FW_(,,LEFT,,RIGHT,,,,M:M)", "Rows of two helper sheets (LEFT and RIGHT) are joined: every row of one with every row of the other. The whole LEFT row, the relation, then the whole RIGHT row; both rows must be equally long.", "#7c3aed"),
            new CellTemplate("brace", "Join \u00b7 M:N", "Compose", "FW_(,,LEFT,,RIGHT,,,,M:N)", "Rows of two helper sheets (LEFT and RIGHT) are joined: every row of one with every row of the other. The whole LEFT row, the relation, then the whole RIGHT row. Helper sheets kept with FW_Reuse can feed later joins.", "#7c3aed"),
            new CellTemplate("brace_full_1_1", "Join full \u00b7 1:1", "Compose", "FW_(START,,LEFT,RELATION,RIGHT,,END,SEPARATOR,1:1)", "A join with all its parts filled in: START text before each joined row, the LEFT sheet, the RELATION placed between the sides, the RIGHT sheet, END text after it, and a SEPARATOR between groups. The two sides are taken in turn, one value from each (1:1).", "#7c3aed"),
            new CellTemplate("brace_full_1_n", "Join full \u00b7 1:N", "Compose", "FW_(START,,LEFT,RELATION,RIGHT,,END,SEPARATOR,1:N)", "A join with all its parts filled in: START text before each joined row, the LEFT sheet, the RELATION placed between the sides, the RIGHT sheet, END text after it, and a SEPARATOR between groups. The RIGHT row follows each LEFT value (1:N).", "#7c3aed"),
            new CellTemplate("brace_full_m_1", "Join full \u00b7 M:1", "Compose", "FW_(START,,LEFT,RELATION,RIGHT,,END,SEPARATOR,M:1)", "A join with all its parts filled in: START text before each joined row, the LEFT sheet, the RELATION placed between the sides, the RIGHT sheet, END text after it, and a SEPARATOR between groups. The LEFT row comes before each RIGHT value (M:1).", "#7c3aed"),
            new CellTemplate("brace_full_m_m", "Join full \u00b7 M:M", "Compose", "FW_(START,,LEFT,RELATION,RIGHT,,END,SEPARATOR,M:M)", "A join with all its parts filled in: START text before each joined row, the LEFT sheet, the RELATION placed between the sides, the RIGHT sheet, END text after it, and a SEPARATOR between groups. Whole rows of equal length side by side (M:M).", "#7c3aed"),
            new CellTemplate("brace_full_m_n", "Join full \u00b7 M:N", "Compose", "FW_(START,,LEFT,RELATION,RIGHT,,END,SEPARATOR,M:N)", "A join with all its parts filled in: START text before each joined row, the LEFT sheet, the RELATION placed between the sides, the RIGHT sheet, END text after it, and a SEPARATOR between groups. Whole rows side by side (M:N).", "#7c3aed"),
            new CellTemplate("brace_nested_left", "Join nested \u00b7 left", "Compose", "FW_(,,FW_(),,RIGHT,,,,M:N)", "A join whose LEFT side is the result of the join just before it.", "#7c3aed"),
            new CellTemplate("brace_nested_right", "Join nested \u00b7 right", "Compose", "FW_(,,LEFT,,FW_(),,,,M:N)", "A join whose RIGHT side is the result of the join just before it.", "#7c3aed"),
            new CellTemplate("brace_nested_both", "Join nested \u00b7 both", "Compose", "FW_(,,FW_(),,FW_(),,,,M:N)", "A join of the results of the two joins just before it.", "#7c3aed"),
            new CellTemplate("brace_grouped_left", "Join grouped \u00b7 left", "Compose", "FW_(,,FW_()G,,RIGHT,,,,M:N)", "Like the nested join, but its LEFT side is the grouped result (FW_Group) of the join before it.", "#7c3aed"),
            new CellTemplate("brace_grouped_right", "Join grouped \u00b7 right", "Compose", "FW_(,,LEFT,,FW_()G,,,,M:N)", "Like the nested join, but its RIGHT side is the grouped result (FW_Group) of the join before it.", "#7c3aed"),
            new CellTemplate("brace_grouped_both", "Join grouped \u00b7 both", "Compose", "FW_(,,FW_()G,,FW_()G,,,,M:N)", "Like the nested join, but both sides are the grouped results (FW_Group) of the two joins before it.", "#7c3aed"),
            new CellTemplate("group", "FW_Group", "Transform", "FW_Group", "Treats every row made so far as one unit, so the next instruction works with whole rows instead of single values. It may stand in any instruction column, and its place in the order is kept.", "#536171"),
            new CellTemplate("group_cleanup", "Group cleanup cell", "Transform", "FW_Group\nFW_ReplaceRE(\"^\\[\", \"\")\nFW_ReplaceRE(\"\\]$\", \"\")\nFW_ReplaceRE(\"\\[\\]\", \"[\" + 0 +\"]\")\nFW_ReplaceRE(\"\\]\\, \\[\", \", \")\nFW_ReplaceRE(\"\\[\", \"\")\nFW_ReplaceRE(\"\\]\", \"\")", "FW_Group together with text clean-up steps (ReplaceRE) in one cell, to tidy the grouped text.", "#536171"),
            new CellTemplate("replace", "ReplaceRE", "Transform", "FW_ReplaceRE(\"pattern\", \"replacement\")", "Rewrites the text with a search-and-replace pattern (a regular expression); double-click the cell to edit the pattern and its replacement.", "#536171")
    );

    private static final Map<String, CellTemplate> BY_ID = byId();

    private CellTemplates() {
    }

    private static Map<String, CellTemplate> byId() {
        Map<String, CellTemplate> map = new LinkedHashMap<>();
        for (CellTemplate t : ALL) {
            if (map.put(t.id(), t) != null) {
                throw new IllegalStateException("two templates have the id " + t.id());
            }
        }
        return Map.copyOf(map);
    }

    public static Optional<CellTemplate> byId(String id) {
        return Optional.ofNullable(BY_ID.get(id));
    }

    /**
     * The cell text after putting a template into a cell holding {@code existing}: a FW_ReplaceRE line joins a FW_Group
     * cell as its next line (the Core reads a FW_ReplaceRE only inside a FW_Group cell); any other template replaces the
     * cell.
     */
    public static String apply(String existing, CellTemplate template) {
        String current = existing == null ? "" : existing;
        if (template.directive().startsWith("FW_ReplaceRE") && current.strip().startsWith("FW_Group")) {
            return current.stripTrailing() + "\n" + template.directive();
        }
        return template.directive();
    }

    /** The categories, in the order they first appear in the palette. */
    public static List<String> categories() {
        return ALL.stream().map(CellTemplate::category).distinct().toList();
    }
}
