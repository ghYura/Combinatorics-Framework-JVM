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

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.SourceLocation;

import java.util.List;
import java.util.Objects;

/**
 * One parsed FW_Seq directive: the syntax-level AST of the legacy directive strings. It says what was
 * written, in typed form; what a directive does to rows belongs to the algebra and generation modules.
 *
 * <p>Two renderings. {@link #normalized()} is a readable one-line form for reports; it is not an identity.
 * {@link #identity()} is the unambiguous typed serialization that plan identity hashes: a type tag, then
 * every semantic field in a fixed order, separated by tabs. Text fields are escaped with
 * {@link Canonical#escape} (so they hold no tab and no line break), an optional field is {@code -} when absent
 * and {@code =} followed by the text when present, and a list is prefixed with its length. Two directives have
 * the same identity exactly when they mean the same, up to the spellings the grammar defines as equal: white
 * space around tokens, {@code FW_Combi} versus {@code FW_Combi(1)}, the case of keywords. Punctuation inside a
 * pattern or a replacement is always part of the identity.</p>
 */
public sealed interface Directive permits Directive.Combinatoric, Directive.Subsets, Directive.Cartes,
        Directive.Group, Directive.Separator, Directive.Joiner {

    /** The text of the cell as written. */
    String source();

    SourceLocation location();

    /** A readable normalised form for reports and messages. */
    String normalized();

    /** The typed serialization that gives plans their identity (see the type documentation). */
    String identity();

    private static String text(String value) {
        return Canonical.escape(value);
    }

    private static String optional(String value) {
        return value == null ? "-" : "=" + Canonical.escape(value);
    }

    enum Verb {
        COMBI("FW_Combi"), COMBI_R("FW_CombiR"), PERMUT("FW_Permut"), PERMUT_R("FW_PermutR");

        private final String token;

        Verb(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }
    }

    enum CountKind { DEFAULT, EXACT, SIZE, ALL }

    /**
     * The argument of a combination verb: nothing or {@code ()} (the verb's default of 1), a whole number, the
     * size of the sheet ({@code size}), or every size ({@code all} or {@code full}).
     */
    record Count(CountKind kind, int value) {
        public static final Count DEFAULT = new Count(CountKind.DEFAULT, 1);

        public Count {
            Objects.requireNonNull(kind, "kind");
            if (value < 0) {
                throw new IllegalArgumentException("a count is not negative: " + value);
            }
        }

        String normalized() {
            return switch (kind) {
                case DEFAULT -> "1";
                case EXACT -> Integer.toString(value);
                case SIZE -> "size";
                case ALL -> "all";
            };
        }

        /** The default means exactly 1, so the two share an identity. */
        String identity() {
            return switch (kind) {
                case DEFAULT -> "EXACT\t1";
                case EXACT -> "EXACT\t" + value;
                case SIZE -> "SIZE";
                case ALL -> "ALL";
            };
        }
    }

    /** FW_Combi, FW_CombiR, FW_Permut or FW_PermutR with its argument. */
    record Combinatoric(Verb verb, Count count, boolean duplicatesIdentical, String source, SourceLocation location)
            implements Directive {
        public Combinatoric {
            Objects.requireNonNull(verb, "verb");
            Objects.requireNonNull(count, "count");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(location, "location");
        }

        @Override
        public String normalized() {
            return verb.token() + "(" + (duplicatesIdentical ? "IDENTICAL" : count.normalized()) + ")";
        }

        @Override
        public String identity() {
            return "VERB\t" + verb.name() + "\t" + (duplicatesIdentical ? "IDENTICAL" : count.identity());
        }
    }

    enum SubsetMode { DEFAULT, BEFORE, AFTER, EXACT, RANGE, GIVEN }

    /** FW_Subsets, or FW_Subsets_MODE(sizes...): the subsets whose sizes the mode selects. */
    record Subsets(SubsetMode mode, List<Integer> sizes, String source, SourceLocation location) implements Directive {
        public Subsets {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(location, "location");
            sizes = List.copyOf(sizes);
        }

        @Override
        public String normalized() {
            if (mode == SubsetMode.DEFAULT) {
                return "FW_Subsets";
            }
            StringBuilder out = new StringBuilder("FW_Subsets_").append(mode.name()).append('(');
            for (int i = 0; i < sizes.size(); i++) {
                out.append(i == 0 ? "" : ",").append(sizes.get(i));
            }
            return out.append(')').toString();
        }

        @Override
        public String identity() {
            StringBuilder out = new StringBuilder("SUBSETS\t").append(mode.name()).append('\t').append(sizes.size());
            for (int size : sizes) {
                out.append('\t').append(size);
            }
            return out.toString();
        }
    }

    /** FW_Cartes(sheet) or FW_Cartes_first(sheet): the Cartesian product with another sheet's values. */
    record Cartes(boolean first, String sheet, String source, SourceLocation location) implements Directive {
        public Cartes {
            Objects.requireNonNull(sheet, "sheet");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(location, "location");
        }

        @Override
        public String normalized() {
            return "FW_Cartes" + (first ? "_first" : "") + "(" + sheet + ")";
        }

        @Override
        public String identity() {
            return "CARTES\t" + (first ? "FIRST" : "PLAIN") + "\t" + text(sheet);
        }
    }

    /**
     * One {@code FW_ReplaceRE("pattern", replacement)} item of a group: the regular expression, and what replaces
     * each match in the numeric key text of a grouped row. A quoted literal replacement is {@code prefix} alone
     * (no sheet, empty suffix). A joined replacement such as {@code "[" + X + "]"} is the literal text before
     * the sheet name, the first label of that sheet, and the literal text after it; the white space around the
     * {@code +} signs is not part of it.
     *
     * @param pattern        the regular expression, never empty
     * @param prefix         the literal replacement, or the literal text before the sheet's label
     * @param sheetReference the sheet whose first label is put between prefix and suffix, or null
     * @param suffix         the literal text after the sheet's label; empty for a literal replacement
     * @param written        the replacement as written, for messages; not part of the identity
     */
    record Replacement(String pattern, String prefix, String sheetReference, String suffix, String written) {
        public Replacement {
            Objects.requireNonNull(pattern, "pattern");
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(suffix, "suffix");
            Objects.requireNonNull(written, "written");
            if (pattern.isEmpty()) {
                throw new IllegalArgumentException("a replacement pattern is not empty");
            }
            if (sheetReference == null && !suffix.isEmpty()) {
                throw new IllegalArgumentException("a literal replacement has no suffix");
            }
            if (sheetReference != null && sheetReference.isEmpty()) {
                throw new IllegalArgumentException("a sheet reference is not empty");
            }
        }

        /** A quoted literal replacement. */
        public static Replacement literal(String pattern, String literal, String written) {
            return new Replacement(pattern, literal, null, "", written);
        }

        String identity() {
            return text(pattern) + "\t" + text(prefix) + "\t" + optional(sheetReference) + "\t" + text(suffix);
        }
    }

    /** FW_Group, optionally followed by FW_ReplaceRE items: group the rows built so far, then rewrite their text. */
    record Group(List<Replacement> replacements, String source, SourceLocation location) implements Directive {
        public Group {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(location, "location");
            replacements = List.copyOf(replacements);
        }

        @Override
        public String normalized() {
            StringBuilder out = new StringBuilder("FW_Group");
            for (Replacement r : replacements) {
                out.append(" FW_ReplaceRE(\"").append(r.pattern()).append("\", ").append(r.written()).append(')');
            }
            return out.toString();
        }

        @Override
        public String identity() {
            StringBuilder out = new StringBuilder("GROUP\t").append(replacements.size());
            for (Replacement r : replacements) {
                out.append('\t').append(r.identity());
            }
            return out.toString();
        }
    }

    /** FW_Separator(sheet): put the first value of a sheet between the combined values. */
    record Separator(String sheet, String source, SourceLocation location) implements Directive {
        public Separator {
            Objects.requireNonNull(sheet, "sheet");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(location, "location");
        }

        @Override
        public String normalized() {
            return "FW_Separator(" + sheet + ")";
        }

        @Override
        public String identity() {
            return "SEPARATOR\t" + text(sheet);
        }
    }

    enum Multiplicity {
        ONE_TO_ONE("1:1"), ONE_TO_N("1:N"), M_TO_ONE("M:1"), M_TO_M("M:M"), M_TO_N("M:N");

        private final String token;

        Multiplicity(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        public static Multiplicity ofToken(String token) {
            for (Multiplicity m : values()) {
                if (m.token.equals(token)) {
                    return m;
                }
            }
            throw new IllegalArgumentException("not a multiplicity: " + token);
        }

        public static boolean isToken(String token) {
            for (Multiplicity m : values()) {
                if (m.token.equals(token)) {
                    return true;
                }
            }
            return false;
        }
    }

    enum OperandKind { SHEET, NESTED, NESTED_GROUPED }

    /**
     * An operand table of a joiner: a sheet by name, or the placeholder FW_() / FW_()G for "the table of the
     * most recent earlier joiner" (plain or grouped). For a placeholder {@code sheet} is null until the planner
     * has resolved it to that joiner's sheet.
     */
    record Operand(OperandKind kind, String sheet) {
        public Operand {
            Objects.requireNonNull(kind, "kind");
            if (kind == OperandKind.SHEET && (sheet == null || sheet.isEmpty())) {
                throw new IllegalArgumentException("a sheet operand needs a sheet name");
            }
        }

        public boolean isPlaceholder() {
            return kind != OperandKind.SHEET;
        }

        public Operand resolvedTo(String targetSheet) {
            return new Operand(kind, targetSheet);
        }

        /** The legacy spelling: a resolved placeholder is the sheet name with the marker ~FWN or ~FWG. */
        String normalized() {
            return switch (kind) {
                case SHEET -> sheet;
                case NESTED -> (sheet == null ? "" : sheet) + "~FWN";
                case NESTED_GROUPED -> (sheet == null ? "" : sheet) + "~FWG";
            };
        }

        String identity() {
            return kind.name() + "\t" + optional(sheet);
        }
    }

    /**
     * FW_(start,,first,relation,second,,end,separator,multiplicity): join the tables of two sheets. Start,
     * relation, end and separator are sheet names (empty when unused); their first values are what the joiner
     * puts before, between and after the joined rows.
     */
    record Joiner(String start, Operand first, String relation, Operand second, String end, String separator,
                  Multiplicity multiplicity, String source, SourceLocation location) implements Directive {
        public Joiner {
            Objects.requireNonNull(start, "start");
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(relation, "relation");
            Objects.requireNonNull(second, "second");
            Objects.requireNonNull(end, "end");
            Objects.requireNonNull(separator, "separator");
            Objects.requireNonNull(multiplicity, "multiplicity");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(location, "location");
        }

        public Joiner withOperands(Operand newFirst, Operand newSecond) {
            return new Joiner(start, newFirst, relation, newSecond, end, separator, multiplicity, source, location);
        }

        @Override
        public String normalized() {
            return "FW_(" + start + ",," + first.normalized() + "," + relation + "," + second.normalized() + ",," + end + ","
                    + separator + "," + multiplicity.token() + ")";
        }

        @Override
        public String identity() {
            return "JOIN\t" + text(start) + "\t" + first.identity() + "\t" + text(relation) + "\t" + second.identity()
                    + "\t" + text(end) + "\t" + text(separator) + "\t" + multiplicity.token();
        }
    }
}
