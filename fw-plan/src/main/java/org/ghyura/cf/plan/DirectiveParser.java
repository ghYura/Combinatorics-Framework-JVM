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
import org.ghyura.cf.model.SourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The strict grammar of the FW_Seq directive cells. Every text the legacy dispatcher accepted and understood
 * parses to the same meaning here; what the legacy code reinterpreted silently (a verb argument it could not
 * read became 1, a subset mode it did not know became "all subsets", a joiner with a missing operand produced
 * no rows) is refused with a message that says what to write instead.
 *
 * <p>White space around tokens is insignificant (architect decision F23): around the whole directive, between
 * a keyword and its bracket, inside brackets, around commas, colons and {@code +} signs. So
 * {@code FW_Combi( 2 )} is {@code FW_Combi(2)}; the legacy dispatcher read it as 1, and it looked up
 * {@code FW_Cartes( A )} as a sheet named " A ". White space inside a quoted literal, a pattern or a sheet name
 * is kept.</p>
 */
public final class DirectiveParser {

    private static final Pattern VERB = Pattern.compile("(FW_CombiR|FW_Combi|FW_PermutR|FW_Permut)(?:\\s*\\(\\s*(\\d+"
            + "|[sS][iI][zZ][eE]|[aA][lL][lL]|[fF][uU][lL][lL]|PermutationGenerator\\.TreatDuplicatesAs\\.IDENTICAL)?\\s*\\))?");
    private static final Pattern SUBSETS_DEFAULT = Pattern.compile("FW_Subsets(?:\\s*\\(\\s*\\))?");
    private static final Pattern SUBSETS_MODE = Pattern.compile("FW_Subsets_([A-Za-z]+)\\s*\\(\\s*(\\d+(?:\\s*,\\s*\\d+)*)\\s*\\)");
    private static final Pattern CARTES = Pattern.compile("FW_Cartes(_[fF][iI][rR][sS][tT])?\\s*\\(\\s*(.*?)\\s*\\)");
    private static final Pattern SEPARATOR = Pattern.compile("FW_Separator\\s*\\(\\s*(.*?)\\s*\\)");
    private static final Pattern NESTED = Pattern.compile("FW_\\(\\s*\\)");
    private static final Pattern NESTED_GROUPED = Pattern.compile("FW_\\(\\s*\\)G");
    private static final Pattern MULTIPLICITY = Pattern.compile("([1M])\\s*:\\s*([1NM])");
    /** The ASCII word characters: what the legacy {@code \w} meant, and so what a sheet named in a replacement may hold. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]+");

    private DirectiveParser() {
    }

    /**
     * @return the directive, or null when the text does not start like any directive family (the caller reports
     *         it as an unknown directive and may suggest a spelling)
     * @throws FrameworkException PLAN_DIRECTIVE_SYNTAX when it starts like a directive but breaks its grammar
     */
    public static Directive parse(String written, SourceLocation where) {
        String text = written.strip();
        if (text.startsWith("FW_(")) {
            return joiner(text, written, where);
        }
        if (text.startsWith("FW_Group")) {
            return group(text, written, where);
        }
        if (text.startsWith("FW_Separator")) {
            return separator(text, written, where);
        }
        if (text.startsWith("FW_Cartes")) {
            return cartes(text, written, where);
        }
        if (text.startsWith("FW_Subsets")) {
            return subsets(text, written, where);
        }
        if (text.startsWith("FW_Combi") || text.startsWith("FW_Permut")) {
            return verb(text, written, where);
        }
        if (text.startsWith("FW_ReplaceRE")) {
            throw syntax(written, where, "FW_ReplaceRE is written inside a FW_Group cell, after FW_Group (the legacy parser "
                    + "ignored a FW_ReplaceRE that stood alone)");
        }
        return null;
    }

    private static FrameworkException syntax(String text, SourceLocation where, String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.PLAN_DIRECTIVE_SYNTAX, "'" + text + "': " + message, where,
                "directive", text));
    }

    // ---- combination verbs -----------------------------------------------------------------------------------

    private static Directive verb(String text, String written, SourceLocation where) {
        Matcher m = VERB.matcher(text);
        if (!m.matches()) {
            String name = text.startsWith("FW_Combi") ? "FW_Combi" : "FW_Permut";
            throw syntax(written, where, "a combination verb is written " + name + ", " + name + "(), " + name + "(n), "
                    + name + "(size) or " + name + "(all); the legacy parser silently read other spellings as "
                    + name + "(1)");
        }
        Directive.Verb verb = switch (m.group(1)) {
            case "FW_CombiR" -> Directive.Verb.COMBI_R;
            case "FW_Combi" -> Directive.Verb.COMBI;
            case "FW_PermutR" -> Directive.Verb.PERMUT_R;
            default -> Directive.Verb.PERMUT;
        };
        String argument = m.group(2);
        if (argument == null) {
            return new Directive.Combinatoric(verb, Directive.Count.DEFAULT, false, written, where);
        }
        if (argument.startsWith("PermutationGenerator")) {
            if (verb != Directive.Verb.PERMUT) {
                throw syntax(written, where, "the duplicate marker " + argument + " belongs to FW_Permut only");
            }
            return new Directive.Combinatoric(verb, Directive.Count.DEFAULT, true, written, where);
        }
        String lower = argument.toLowerCase(Locale.ROOT);
        if (lower.equals("size")) {
            return new Directive.Combinatoric(verb, new Directive.Count(Directive.CountKind.SIZE, 0), false, written, where);
        }
        if (lower.equals("all") || lower.equals("full")) {
            return new Directive.Combinatoric(verb, new Directive.Count(Directive.CountKind.ALL, 0), false, written, where);
        }
        try {
            return new Directive.Combinatoric(verb, new Directive.Count(Directive.CountKind.EXACT, Integer.parseInt(argument)),
                    false, written, where);
        } catch (NumberFormatException e) {
            throw syntax(written, where, "the number " + argument + " is too large");
        }
    }

    // ---- subsets -----------------------------------------------------------------------------------------------

    private static Directive subsets(String text, String written, SourceLocation where) {
        if (SUBSETS_DEFAULT.matcher(text).matches()) {
            return new Directive.Subsets(Directive.SubsetMode.DEFAULT, List.of(), written, where);
        }
        Matcher m = SUBSETS_MODE.matcher(text);
        if (!m.matches()) {
            throw syntax(written, where, "subsets are written FW_Subsets (all subsets) or FW_Subsets_MODE(sizes) with MODE "
                    + "one of BEFORE, AFTER, EXACT, RANGE or GIVEN; the legacy parser read anything else as \"all "
                    + "subsets\" without saying so");
        }
        Directive.SubsetMode mode;
        try {
            mode = Directive.SubsetMode.valueOf(m.group(1).toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            mode = Directive.SubsetMode.DEFAULT;
        }
        if (mode == Directive.SubsetMode.DEFAULT) {
            throw syntax(written, where, "'" + m.group(1) + "' is not a subset mode; use BEFORE, AFTER, EXACT, RANGE or GIVEN "
                    + "(the legacy parser fell back to all subsets)");
        }
        List<Integer> sizes = new ArrayList<>();
        for (String number : m.group(2).split("\\s*,\\s*")) {
            try {
                sizes.add(Integer.parseInt(number.trim()));
            } catch (NumberFormatException e) {
                throw syntax(written, where, "the size " + number.trim() + " is too large");
            }
        }
        switch (mode) {
            case BEFORE, AFTER, EXACT -> {
                if (sizes.size() != 1) {
                    throw syntax(written, where, mode + " takes exactly one size, not " + sizes.size() + " (the legacy "
                            + "parser used the first and ignored the rest)");
                }
            }
            case RANGE -> {
                if (sizes.size() != 2 || sizes.get(0) > sizes.get(1)) {
                    throw syntax(written, where, "RANGE takes two sizes, the smaller first: FW_Subsets_RANGE(from,to)");
                }
            }
            default -> {
                // GIVEN: any number of sizes, at least one (the pattern guarantees it)
            }
        }
        return new Directive.Subsets(mode, sizes, written, where);
    }

    // ---- cartesian product and separator -----------------------------------------------------------------------

    private static Directive cartes(String text, String written, SourceLocation where) {
        Matcher m = CARTES.matcher(text);
        if (!m.matches() || m.group(2).isEmpty()) {
            throw syntax(written, where, "a Cartesian product is written FW_Cartes(sheet) or FW_Cartes_first(sheet)");
        }
        return new Directive.Cartes(m.group(1) != null, m.group(2), written, where);
    }

    private static Directive separator(String text, String written, SourceLocation where) {
        Matcher m = SEPARATOR.matcher(text);
        if (!m.matches() || m.group(1).isEmpty()) {
            throw syntax(written, where, "a separator is written FW_Separator(sheet): the name of the sheet whose first "
                    + "value separates the combined values");
        }
        return new Directive.Separator(m.group(1), written, where);
    }

    // ---- group with replacements -------------------------------------------------------------------------------

    /**
     * {@code FW_Group} followed by any number of {@code FW_ReplaceRE("pattern", replacement)} items, each on its
     * own line or on one line. The pattern is the shortest non-empty text between the opening quote and a quote
     * that is followed by a comma, as in the legacy expression. The replacement is a quoted literal, or quoted
     * literals and exactly one sheet name joined with {@code +}, starting and ending with a literal.
     *
     * <p>Where the legacy expression {@code (FW_ReplaceRE\(["](.+?)["],\s+("(.*)")\))} gave another meaning, this
     * grammar refuses or reads the evident intent, and says which: two items on one line were one replacement
     * whose text ran to the last {@code ")}; an item without white space after the comma was ignored; a literal
     * containing {@code +} was taken for a joined replacement; the letters and digits of a joined replacement's
     * literals were replaced by the label too and became part of the sheet name looked up.</p>
     */
    private static Directive group(String text, String written, SourceLocation where) {
        String rest = text.substring("FW_Group".length());
        if (!rest.isEmpty() && !Character.isWhitespace(rest.charAt(0))) {
            throw syntax(written, where, "FW_Group stands alone or is followed by white space and FW_ReplaceRE items");
        }
        List<Directive.Replacement> replacements = new ArrayList<>();
        Cursor in = new Cursor(rest, written, where);
        while (true) {
            in.skipSpace();
            if (in.atEnd()) {
                break;
            }
            if (!in.take("FW_ReplaceRE")) {
                throw syntax(written, where, "unexpected text '" + in.remainder().strip() + "' after FW_Group; replacements "
                        + "are written FW_ReplaceRE(\"pattern\", \"replacement\") or FW_ReplaceRE(\"pattern\", \"text\" + "
                        + "SHEET + \"text\")");
            }
            in.skipSpace();
            in.expect('(', "an opening bracket after FW_ReplaceRE");
            in.skipSpace();
            in.expect('"', "the pattern in double quotes");
            String pattern = in.pattern();
            try {
                Pattern.compile(pattern);
            } catch (PatternSyntaxException e) {
                throw syntax(written, where, "the pattern \"" + pattern + "\" is not a valid regular expression: "
                        + e.getDescription() + (e.getIndex() >= 0 ? " at index " + e.getIndex() : ""));
            }
            in.skipSpace();
            in.expect(',', "a comma after the pattern");
            in.skipSpace();
            replacements.add(replacement(pattern, in, written, where));
            in.skipSpace();
            in.expect(')', "a closing bracket after the replacement");
        }
        return new Directive.Group(replacements, written, where);
    }

    private static Directive.Replacement replacement(String pattern, Cursor in, String written, SourceLocation where) {
        int start = in.position();
        List<String> literals = new ArrayList<>();
        List<Boolean> isLiteral = new ArrayList<>();
        do {
            in.skipSpace();
            if (in.peek() == '"') {
                literals.add(in.literal());
                isLiteral.add(true);
            } else {
                String name = in.name();
                if (name.isEmpty()) {
                    throw syntax(written, where, "a replacement is a quoted text, or quoted texts and one sheet name joined "
                            + "with +, like \"[\" + SHEET + \"]\"");
                }
                literals.add(name);
                isLiteral.add(false);
            }
            in.skipSpace();
        } while (in.take("+"));
        String template = in.written(start).strip();
        if (literals.size() == 1) {
            if (!isLiteral.getFirst()) {
                throw syntax(written, where, "the replacement " + template + " names a sheet without quoted texts around it; "
                        + "write \"\" + " + template + " + \"\" (the legacy parser ignored this item)");
            }
            return Directive.Replacement.literal(pattern, literals.getFirst(), template);
        }
        int names = 0;
        for (boolean literal : isLiteral) {
            names += literal ? 0 : 1;
        }
        if (names != 1 || !isLiteral.getFirst() || !isLiteral.getLast()) {
            throw syntax(written, where, "a joined replacement starts and ends with a quoted text and names exactly one "
                    + "sheet, like \"[\" + SHEET + \"]\"; " + template + " does not (the legacy parser looked up a sheet "
                    + "named by all the letters and digits of the replacement, and ignored the item when there was none)");
        }
        StringBuilder prefix = new StringBuilder();
        StringBuilder suffix = new StringBuilder();
        String sheet = null;
        for (int i = 0; i < literals.size(); i++) {
            if (!isLiteral.get(i)) {
                sheet = literals.get(i);
                continue;
            }
            if (NAME.matcher(literals.get(i)).find()) {
                throw syntax(written, where, "the quoted text \"" + literals.get(i) + "\" of a joined replacement holds a "
                        + "letter, digit or underscore; the legacy parser replaced those with the label as well and made "
                        + "them part of the sheet name it looked up, so write them in a separate FW_ReplaceRE");
            }
            (sheet == null ? prefix : suffix).append(literals.get(i));
        }
        return new Directive.Replacement(pattern, prefix.toString(), sheet, suffix.toString(), template);
    }

    /** A position in the text after FW_Group, with the few reads the replacement grammar needs. */
    private static final class Cursor {
        private final String text;
        private final String written;
        private final SourceLocation where;
        private int at;

        Cursor(String text, String written, SourceLocation where) {
            this.text = text;
            this.written = written;
            this.where = where;
        }

        int position() {
            return at;
        }

        boolean atEnd() {
            return at >= text.length();
        }

        char peek() {
            return atEnd() ? '\0' : text.charAt(at);
        }

        String remainder() {
            return text.substring(at);
        }

        String written(int from) {
            return text.substring(from, at);
        }

        void skipSpace() {
            while (!atEnd() && Character.isWhitespace(text.charAt(at))) {
                at++;
            }
        }

        boolean take(String token) {
            if (text.startsWith(token, at)) {
                at += token.length();
                return true;
            }
            return false;
        }

        void expect(char c, String what) {
            if (peek() != c) {
                throw syntax(written, where, "expected " + what + (atEnd() ? " but the cell ends" : " at '"
                        + abbreviated(remainder()) + "'"));
            }
            at++;
        }

        /** After the opening quote: the shortest non-empty text up to a quote followed by a comma, on one line. */
        String pattern() {
            int from = at;
            for (int i = from; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') {
                    break;
                }
                if (c == '"' && i > from) {
                    int next = i + 1;
                    while (next < text.length() && (text.charAt(next) == ' ' || text.charAt(next) == '\t')) {
                        next++;
                    }
                    if (next < text.length() && text.charAt(next) == ',') {
                        at = i + 1;
                        return text.substring(from, i);
                    }
                }
            }
            throw syntax(written, where, "the pattern of FW_ReplaceRE is a non-empty text in double quotes followed by a "
                    + "comma, on one line");
        }

        /** At an opening quote: the text up to the next quote, on one line. */
        String literal() {
            int from = at + 1;
            for (int i = from; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n' || c == '\r') {
                    break;
                }
                if (c == '"') {
                    at = i + 1;
                    return text.substring(from, i);
                }
            }
            throw syntax(written, where, "a quoted text of the replacement is not closed on its line");
        }

        String name() {
            Matcher m = NAME.matcher(text).region(at, text.length());
            if (m.lookingAt()) {
                at = m.end();
                return m.group();
            }
            return "";
        }

        private static String abbreviated(String text) {
            String line = text.lines().findFirst().orElse("");
            return line.length() > 40 ? line.substring(0, 40) + "..." : line;
        }
    }

    // ---- joiner ------------------------------------------------------------------------------------------------

    private static Directive joiner(String text, String written, SourceLocation where) {
        if (!text.endsWith(")")) {
            throw syntax(written, where, "a joiner ends with a closing bracket: "
                    + "FW_(start,,first,relation,second,,end,separator,multiplicity)");
        }
        List<String> parts = splitTopLevelByComma(text.substring("FW_(".length(), text.length() - 1));
        if (parts.size() != 9) {
            throw syntax(written, where, "a joiner has nine comma-separated fields, FW_(start,,first,relation,second,,end,"
                    + "separator,multiplicity), not " + parts.size());
        }
        if (!parts.get(1).isEmpty() || !parts.get(5).isEmpty()) {
            throw syntax(written, where, "the second and the sixth field are reserved and stay empty");
        }
        Directive.Operand first = operand(parts.get(2), "the third field", written, where);
        Directive.Operand second = operand(parts.get(4), "the fifth field", written, where);
        Matcher multiplicity = MULTIPLICITY.matcher(parts.get(8));
        String token = multiplicity.matches() ? multiplicity.group(1) + ":" + multiplicity.group(2) : parts.get(8);
        if (!Directive.Multiplicity.isToken(token)) {
            String upper = token.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
            String hint = Directive.Multiplicity.isToken(upper)
                    ? "; did you mean " + upper + "? (the legacy joiner did nothing for any other case)" : "";
            throw syntax(written, where, "the multiplicity '" + parts.get(8) + "' is not one of 1:1, 1:N, M:1, M:M, M:N" + hint);
        }
        return new Directive.Joiner(name(parts.get(0), "the first field", written, where), first,
                name(parts.get(3), "the fourth field", written, where), second,
                name(parts.get(6), "the seventh field", written, where), name(parts.get(7), "the eighth field", written, where),
                Directive.Multiplicity.ofToken(token), written, where);
    }

    private static Directive.Operand operand(String field, String which, String written, SourceLocation where) {
        if (field.isEmpty()) {
            throw syntax(written, where, which + " names the table to join and cannot be empty (a joiner with a missing "
                    + "table produced no rows without saying so in the legacy parser)");
        }
        if (NESTED.matcher(field).matches()) {
            return new Directive.Operand(Directive.OperandKind.NESTED, null);
        }
        if (NESTED_GROUPED.matcher(field).matches()) {
            return new Directive.Operand(Directive.OperandKind.NESTED_GROUPED, null);
        }
        if (field.startsWith("FW_(")) {
            throw syntax(written, where, which + " refers to an earlier joiner and is written exactly FW_() or FW_()G (the "
                    + "legacy parser ignored anything inside the brackets and read FW_() G as the plain FW_())");
        }
        return new Directive.Operand(Directive.OperandKind.SHEET, name(field, which, written, where));
    }

    private static String name(String field, String which, String written, SourceLocation where) {
        if (field.startsWith("FW_(") || field.indexOf('(') >= 0 || field.indexOf(')') >= 0) {
            throw syntax(written, where, which + " must be a sheet name or empty, not '" + field + "'");
        }
        return field;
    }

    /** Splits on commas outside brackets and trims each part, as the legacy parser did. */
    public static List<String> splitTopLevelByComma(String text) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            } else if (c == ',' && depth == 0) {
                parts.add(text.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(text.substring(start).trim());
        return parts;
    }
}
