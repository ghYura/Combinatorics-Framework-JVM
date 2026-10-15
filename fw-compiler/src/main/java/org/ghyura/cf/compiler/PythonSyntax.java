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


package org.ghyura.cf.compiler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Python source read the way CPython 3.14's {@code ast.parse} reads it, for what the legacy decomposition
 * (code_decompose.py) asked of the tree: whether a text parses at all (its {@code _looks_python}), the top-level statements
 * with their kind, name and last line ({@code ast.parse(src).body} with {@code end_lineno}), and the identifiers its
 * {@code _py_names} gathered: the names of every {@code def} and {@code class}, every name inside a plain assignment's
 * targets, and every name loaded anywhere. A tokenizer and a recursive-descent parser over the 3.14 grammar (f- and
 * t-strings with nested replacement fields, soft keywords, patterns, type parameters, except without parentheses); nothing
 * is executed and no Python runs.
 */
final class PythonSyntax {

    /** A text CPython refuses: SyntaxError, IndentationError, TabError or the null-byte error. */
    static final class Failure extends Exception {
        private static final long serialVersionUID = 1L;
        final int line;

        Failure(String message, int line) {
            super(message + " (line " + line + ")");
            this.line = line;
        }
    }

    /** A top-level statement: function, class or stmt, the name of a function or class, and its last line (1-based). */
    record Statement(String kind, String name, int endLine) {
    }

    /** A module: its top-level statements and the names _py_names gathered. */
    record Module(List<Statement> body, Set<String> defined, Set<String> used) {
    }

    private PythonSyntax() {
    }

    /** Reads a source; line ends are \n, \r\n or \r, as the CPython tokenizer counts them. */
    static Module parse(String source) throws Failure {
        if (source.indexOf('\0') >= 0) {
            throw new Failure("source code string cannot contain null bytes", 1);
        }
        String text = source.replace("\r\n", "\n").replace('\r', '\n');
        // a byte order mark in a str is not skipped: ast.parse refuses it as a non-printable character
        List<Tok> tokens = new Lexer(text).run();
        return new Parser(tokens, new ArrayList<>(), new LinkedHashSet<>()).module();
    }

    /** Whether CPython would parse the text: _looks_python. */
    static boolean parses(String source) {
        try {
            parse(source);
            return true;
        } catch (Failure e) {
            return false;
        }
    }

    // ---- tokens --------------------------------------------------------------------------------------------------

    private enum Kind {
        NAME, NUMBER, STRING, OP, NEWLINE, INDENT, DEDENT, END
    }

    /** A token; a string carries its flavour (s str, b bytes, f f-string, t t-string) and its replacement fields' tokens. */
    private static final class Tok {
        final Kind kind;
        final String text;
        final int line;
        final int endLine;
        final List<List<Tok>> fields;
        final char flavour;

        Tok(Kind kind, String text, int line, int endLine, List<List<Tok>> fields, char flavour) {
            this.kind = kind;
            this.text = text;
            this.line = line;
            this.endLine = endLine;
            this.fields = fields;
            this.flavour = flavour;
        }

        static Tok of(Kind kind, String text, int line) {
            return new Tok(kind, text, line, line, List.of(), ' ');
        }
    }

    private static final String[] OPERATORS = {"**=", "//=", ">>=", "<<=", "...", "!=", "**", "//", ">>", "<<", "<=", ">=", "==", "->",
        ":=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "@=", "(", ")", "[", "]", "{", "}", ",", ":", ".", ";", "+", "-", "*",
        "/", "%", "&", "|", "^", "~", "<", ">", "=", "@"};
    private static final Set<String> NUMBER_SUFFIX_KEYWORDS = Set.of("and", "else", "for", "if", "in", "is", "not", "or");
    private static final Set<String> STRING_PREFIXES = Set.of("r", "u", "f", "b", "t", "br", "rb", "fr", "rf", "tr", "rt");

    private static boolean identStart(int cp) {
        return cp == '_' || Character.isUnicodeIdentifierStart(cp);
    }

    private static boolean identPart(int cp) {
        return cp == '_' || Character.isUnicodeIdentifierPart(cp) && !Character.isIdentifierIgnorable(cp);
    }

    private static final class Lexer {
        private final String s;
        private final int n;
        private int pos;
        private int line = 1;
        private int templateDepth;

        Lexer(String s) {
            this.s = s;
            this.n = s.length();
        }

        List<Tok> run() throws Failure {
            List<Tok> out = new ArrayList<>();
            Deque<Integer> indents = new ArrayDeque<>();
            Deque<Integer> alts = new ArrayDeque<>();
            indents.push(0);
            alts.push(0);
            Deque<Character> brackets = new ArrayDeque<>();
            boolean lineStart = true;
            while (true) {
                if (lineStart && brackets.isEmpty()) {
                    int col = 0;
                    int alt = 0;
                    int p = pos;
                    while (p < n) {
                        char c = s.charAt(p);
                        if (c == ' ') {
                            col++;
                            alt++;
                        } else if (c == '\t') {
                            col = (col / 8 + 1) * 8;
                            alt++;
                        } else if (c == '\f') {
                            col = 0;
                            alt = 0;
                        } else {
                            break;
                        }
                        p++;
                    }
                    pos = p;
                    if (p >= n) {
                        break;
                    }
                    char c = s.charAt(p);
                    if (c == '#' || c == '\n') {
                        if (c == '#') {
                            comment();
                        }
                        if (pos < n) {
                            pos++;
                            line++;
                        }
                        continue;
                    }
                    int top = indents.peek();
                    int altTop = alts.peek();
                    if (col > top) {
                        if (alt <= altTop) {
                            throw new Failure("inconsistent use of tabs and spaces in indentation", line);
                        }
                        if (indents.size() >= 100) {
                            throw new Failure("too many levels of indentation", line);
                        }
                        indents.push(col);
                        alts.push(alt);
                        out.add(Tok.of(Kind.INDENT, "", line));
                    } else if (col < top) {
                        while (col < indents.peek()) {
                            indents.pop();
                            alts.pop();
                            out.add(Tok.of(Kind.DEDENT, "", line));
                        }
                        if (col != indents.peek()) {
                            throw new Failure("unindent does not match any outer indentation level", line);
                        }
                        if (alt != alts.peek()) {
                            throw new Failure("inconsistent use of tabs and spaces in indentation", line);
                        }
                    } else if (alt != altTop) {
                        throw new Failure("inconsistent use of tabs and spaces in indentation", line);
                    }
                    lineStart = false;
                }
                if (pos >= n) {
                    break;
                }
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\f') {
                    pos++;
                    continue;
                }
                if (c == '#') {
                    comment();
                    continue;
                }
                if (c == '\n') {
                    pos++;
                    if (brackets.isEmpty()) {
                        out.add(Tok.of(Kind.NEWLINE, "", line));
                        lineStart = true;
                    }
                    line++;
                    continue;
                }
                if (c == '\\') {
                    if (pos + 1 < n && s.charAt(pos + 1) == '\n') {
                        pos += 2;
                        line++;
                        continue;
                    }
                    throw new Failure(pos + 1 >= n ? "unexpected EOF while parsing" : "unexpected character after line continuation character", line);
                }
                out.add(token(brackets));
            }
            if (!brackets.isEmpty()) {
                throw new Failure("'" + brackets.peek() + "' was never closed", line);
            }
            if (!lineStart) {
                out.add(Tok.of(Kind.NEWLINE, "", line));
            }
            while (indents.peek() > 0) {
                indents.pop();
                out.add(Tok.of(Kind.DEDENT, "", line));
            }
            out.add(Tok.of(Kind.END, "", line));
            return out;
        }

        private void comment() {
            while (pos < n && s.charAt(pos) != '\n') {
                pos++;
            }
        }

        /** One name, number, string or operator at pos. */
        private Tok token(Deque<Character> brackets) throws Failure {
            int cp = s.codePointAt(pos);
            char c = s.charAt(pos);
            if (identStart(cp)) {
                int start = pos;
                pos += Character.charCount(cp);
                while (pos < n && identPart(s.codePointAt(pos))) {
                    pos += Character.charCount(s.codePointAt(pos));
                }
                String word = s.substring(start, pos);
                if (pos < n && (s.charAt(pos) == '"' || s.charAt(pos) == '\'') && STRING_PREFIXES.contains(word.toLowerCase(Locale.ROOT))) {
                    return string(word.toLowerCase(Locale.ROOT));
                }
                return Tok.of(Kind.NAME, word, line);
            }
            if (c >= '0' && c <= '9' || c == '.' && pos + 1 < n && s.charAt(pos + 1) >= '0' && s.charAt(pos + 1) <= '9') {
                return number();
            }
            if (c == '"' || c == '\'') {
                return string("");
            }
            for (String op : OPERATORS) {
                if (s.startsWith(op, pos)) {
                    pos += op.length();
                    switch (op) {
                        case "(", "[", "{" -> {
                            // CPython's tokenizer allows at most 200 simultaneously open delimiters.
                            if (brackets.size() >= 200) {
                                throw new Failure("too many nested parentheses", line);
                            }
                            brackets.push(op.charAt(0));
                        }
                        case ")", "]", "}" -> {
                            if (brackets.isEmpty()) {
                                throw new Failure("unmatched '" + op + "'", line);
                            }
                            char open = brackets.pop();
                            if ("([{".indexOf(open) != ")]}".indexOf(op.charAt(0))) {
                                throw new Failure("closing parenthesis '" + op + "' does not match opening parenthesis '" + open + "'", line);
                            }
                        }
                        default -> {
                        }
                    }
                    return Tok.of(Kind.OP, op, line);
                }
            }
            throw new Failure("invalid character '" + new String(Character.toChars(cp)) + "'", line);
        }

        // ---- numbers ---------------------------------------------------------------------------------------------

        private static boolean digit(char c, char base) {
            return switch (base) {
                case 'x' -> c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
                case 'o' -> c >= '0' && c <= '7';
                case 'b' -> c == '0' || c == '1';
                default -> c >= '0' && c <= '9';
            };
        }

        /** digit (_? digit)*, from pos; false when no digit starts there. */
        private boolean digits(char base) throws Failure {
            if (pos >= n || !digit(s.charAt(pos), base)) {
                return false;
            }
            pos++;
            while (pos < n) {
                char c = s.charAt(pos);
                if (digit(c, base)) {
                    pos++;
                } else if (c == '_') {
                    if (pos + 1 < n && digit(s.charAt(pos + 1), base)) {
                        pos += 2;
                    } else {
                        throw new Failure("invalid decimal literal", line);
                    }
                } else {
                    break;
                }
            }
            return true;
        }

        private Tok number() throws Failure {
            int start = pos;
            char c = s.charAt(pos);
            boolean integer = true;
            if (c == '0' && pos + 1 < n && "xXoObB".indexOf(s.charAt(pos + 1)) >= 0) {
                char base = Character.toLowerCase(s.charAt(pos + 1));
                pos += 2;
                if (pos < n && s.charAt(pos) == '_') {
                    pos++;
                }
                String what = base == 'x' ? "hexadecimal" : base == 'o' ? "octal" : "binary";
                if (!digits(base)) {
                    throw new Failure("invalid " + what + " literal", line);
                }
                if (pos < n && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                    throw new Failure("invalid digit '" + s.charAt(pos) + "' in " + what + " literal", line);
                }
                integer = false;
            } else {
                boolean whole = c != '.' && digits('d');
                if (pos < n && s.charAt(pos) == '.') {
                    pos++;
                    integer = false;
                    if (pos < n && s.charAt(pos) >= '0' && s.charAt(pos) <= '9') {
                        digits('d');
                    } else if (!whole) {
                        throw new Failure("invalid decimal literal", line);
                    }
                }
                if (pos < n && (s.charAt(pos) == 'e' || s.charAt(pos) == 'E')) {
                    int e = pos + 1;
                    if (e < n && (s.charAt(e) == '+' || s.charAt(e) == '-')) {
                        e++;
                    }
                    if (e < n && s.charAt(e) >= '0' && s.charAt(e) <= '9') {
                        pos = e;
                        digits('d');
                        integer = false;
                    }
                }
                if (pos < n && (s.charAt(pos) == 'j' || s.charAt(pos) == 'J')) {
                    pos++;
                    integer = false;
                }
                if (integer) {
                    String text = s.substring(start, pos).replace("_", "");
                    if (text.length() > 1 && text.charAt(0) == '0' && text.chars().anyMatch(ch -> ch != '0')) {
                        throw new Failure("leading zeros in decimal integer literals are not permitted; use an 0o prefix for octal integers", line);
                    }
                }
            }
            if (pos < n && identStart(s.codePointAt(pos))) {
                int e = pos;
                while (e < n && identPart(s.codePointAt(e))) {
                    e += Character.charCount(s.codePointAt(e));
                }
                if (!NUMBER_SUFFIX_KEYWORDS.contains(s.substring(pos, e))) {
                    throw new Failure("invalid decimal literal", line);
                }
            }
            return Tok.of(Kind.NUMBER, s.substring(start, pos), line);
        }

        // ---- strings ---------------------------------------------------------------------------------------------

        private Tok string(String prefix) throws Failure {
            boolean template = prefix.indexOf('f') >= 0 || prefix.indexOf('t') >= 0;
            // CPython's 150 tokenizer modes include the initial, non-template mode.
            if (template && templateDepth >= 149) {
                throw new Failure("too many nested f-strings or t-strings", line);
            }
            if (template) templateDepth++;
            try {
                return stringContents(prefix);
            } finally {
                if (template) templateDepth--;
            }
        }

        private Tok stringContents(String prefix) throws Failure {
            boolean raw = prefix.indexOf('r') >= 0;
            boolean bytes = prefix.indexOf('b') >= 0;
            char flavour = prefix.indexOf('f') >= 0 ? 'f' : prefix.indexOf('t') >= 0 ? 't' : bytes ? 'b' : 's';
            char q = s.charAt(pos);
            boolean triple = pos + 2 < n && s.charAt(pos + 1) == q && s.charAt(pos + 2) == q;
            int startLine = line;
            pos += triple ? 3 : 1;
            List<List<Tok>> fields = new ArrayList<>();
            while (true) {
                if (pos >= n) {
                    throw new Failure(triple ? "unterminated triple-quoted string literal" : "unterminated string literal", startLine);
                }
                char c = s.charAt(pos);
                if (c == '\\') {
                    if (pos + 1 >= n) {
                        throw new Failure("unterminated string literal", startLine);
                    }
                    char e = s.charAt(pos + 1);
                    if (e == '\n') {
                        line++;
                        pos += 2;
                    } else if (raw) {
                        pos += e == q || e == '\\' ? 2 : 1;
                    } else {
                        escape(bytes, flavour == 'f' || flavour == 't');
                    }
                    continue;
                }
                if (c == '\n') {
                    if (!triple) {
                        throw new Failure("unterminated string literal", startLine);
                    }
                    line++;
                    pos++;
                    continue;
                }
                if (c == q && (!triple || pos + 2 < n && s.charAt(pos + 1) == q && s.charAt(pos + 2) == q)) {
                    pos += triple ? 3 : 1;
                    break;
                }
                if ((flavour == 'f' || flavour == 't') && (c == '{' || c == '}')) {
                    if (pos + 1 < n && s.charAt(pos + 1) == c) {
                        pos += 2;
                    } else if (c == '}') {
                        throw new Failure("f-string: single '}' is not allowed", line);
                    } else {
                        pos++;
                        field(fields, q, triple, raw, bytes, 0);
                    }
                    continue;
                }
                if (bytes && c > 127) {
                    throw new Failure("bytes can only contain ASCII literal characters", line);
                }
                pos++;
            }
            return new Tok(Kind.STRING, "", startLine, line, fields, flavour);
        }

        /** A backslash escape of a non-raw literal, checked as the unicode-escape codec checks it. */
        private void escape(boolean bytes, boolean template) throws Failure {
            char e = s.charAt(pos + 1);
            pos += 2;
            switch (e) {
                case 'x' -> hex(2, "truncated \\xXX escape");
                case 'u' -> {
                    if (!bytes) {
                        hex(4, "truncated \\uXXXX escape");
                    }
                }
                case 'U' -> {
                    if (!bytes) {
                        int start = pos;
                        hex(8, "truncated \\UXXXXXXXX escape");
                        if (Long.parseLong(s.substring(start, pos), 16) > 0x10FFFF) {
                            throw new Failure("(unicode error) illegal Unicode character", line);
                        }
                    }
                }
                case 'N' -> {
                    if (!bytes) {
                        if (pos >= n || s.charAt(pos) != '{') {
                            throw new Failure("(unicode error) malformed \\N character escape", line);
                        }
                        int close = s.indexOf('}', pos);
                        int eol = s.indexOf('\n', pos);
                        if (close < 0 || eol >= 0 && eol < close) {
                            throw new Failure("(unicode error) malformed \\N character escape", line);
                        }
                        String name = s.substring(pos + 1, close);
                        try {
                            Character.codePointOf(name);
                        } catch (IllegalArgumentException ex) {
                            throw new Failure("(unicode error) unknown Unicode character name", line);
                        }
                        pos = close + 1;
                    }
                }
                default -> {
                    if (e == '{' && template) {
                        pos--;
                    }
                }
            }
        }

        private void hex(int count, String message) throws Failure {
            for (int i = 0; i < count; i++) {
                if (pos >= n || !digit(s.charAt(pos), 'x')) {
                    throw new Failure("(unicode error) " + message, line);
                }
                pos++;
            }
        }

        /** One replacement field of an f- or t-string, from after its '{' to after its '}'. */
        private void field(List<List<Tok>> fields, char q, boolean triple, boolean raw, boolean bytes, int formatDepth) throws Failure {
            if (formatDepth >= 3) {
                throw new Failure("f-string: expressions nested too deeply", line);
            }
            List<Tok> expr = new ArrayList<>();
            Deque<Character> brackets = new ArrayDeque<>();
            while (true) {
                if (pos >= n) {
                    throw new Failure("f-string: expecting '}'", line);
                }
                char c = s.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\f') {
                    pos++;
                    continue;
                }
                if (c == '\n') {
                    line++;
                    pos++;
                    continue;
                }
                if (c == '#') {
                    comment();
                    continue;
                }
                if (c == '\\') {
                    if (pos + 1 < n && s.charAt(pos + 1) == '\n') {
                        pos += 2;
                        line++;
                        continue;
                    }
                    throw new Failure("unexpected character after line continuation character", line);
                }
                if (brackets.isEmpty()) {
                    if (c == '}' || c == ':' || c == '!' && !(pos + 1 < n && s.charAt(pos + 1) == '=')
                            || c == '=' && !(pos + 1 < n && s.charAt(pos + 1) == '=')) {
                        break;
                    }
                }
                expr.add(token(brackets));
            }
            if (expr.isEmpty()) {
                throw new Failure("f-string: valid expression required before '" + s.charAt(pos) + "'", line);
            }
            if (!expr.isEmpty() && expr.getFirst().kind == Kind.NAME && expr.getFirst().text.equals("lambda") && s.charAt(pos) == ':') {
                throw new Failure("f-string: lambda expressions are not allowed without parentheses", line);
            }
            expr.add(Tok.of(Kind.END, "", line));
            fields.add(expr);
            if (s.charAt(pos) == '=') {
                pos++;
                while (pos < n && (s.charAt(pos) == ' ' || s.charAt(pos) == '\t')) {
                    pos++;
                }
            }
            if (pos < n && s.charAt(pos) == '!') {
                pos++;
                int start = pos;
                while (pos < n && identPart(s.codePointAt(pos))) {
                    pos += Character.charCount(s.codePointAt(pos));
                }
                String conversion = s.substring(start, pos);
                if (!Set.of("r", "s", "a").contains(conversion)) {
                    throw new Failure("f-string: invalid conversion character", line);
                }
            }
            if (pos < n && s.charAt(pos) == ':') {
                pos++;
                while (true) {
                    if (pos >= n) {
                        throw new Failure("f-string: expecting '}'", line);
                    }
                    char c = s.charAt(pos);
                    if (c == '}') {
                        break;
                    }
                    if (c == '{') {
                        pos++;
                        field(fields, q, triple, raw, bytes, formatDepth + 1);
                        continue;
                    }
                    if (c == '\n') {
                        if (!triple) {
                            throw new Failure("f-string: expecting '}'", line);
                        }
                        line++;
                    } else if (c == q && (!triple || pos + 2 < n && s.charAt(pos + 1) == q && s.charAt(pos + 2) == q)) {
                        throw new Failure("f-string: expecting '}'", line);
                    } else if (c == '\\' && pos + 1 < n) {
                        pos++;
                    }
                    pos++;
                }
            }
            if (pos >= n || s.charAt(pos) != '}') {
                throw new Failure("f-string: expecting '}'", line);
            }
            pos++;
        }
    }

    // ---- parser ---------------------------------------------------------------------------------------------------

    private static final Set<String> KEYWORDS = Set.of("False", "None", "True", "and", "as", "assert", "async", "await", "break", "class",
            "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
            "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with", "yield");
    private static final Set<String> AUGMENTED = Set.of("+=", "-=", "*=", "/=", "//=", "%=", "@=", "&=", "|=", "^=", ">>=", "<<=", "**=");
    private static final Set<String> COMPARISONS = Set.of("==", "!=", "<", "<=", ">", ">=");
    private static final Set<String> BINARY_OPERATORS = Set.of("|", "^", "&", "<<", ">>", "+", "-", "*", "/", "//", "%", "@");

    /** An expression, as much of ast's shape as name contexts and assignment targets need. */
    private static final class Node {
        final char kind;        // N name, A attribute, S subscript, * starred, T tuple, L list, X anything else
        final String id;
        final List<Node> kids;
        char ctx = 'L';         // L load, S store, D del

        Node(char kind, String id, List<Node> kids) {
            this.kind = kind;
            this.id = id;
            this.kids = kids;
        }
    }

    private interface Rule {
        Node parse() throws Failure;
    }

    private static final class Parser {
        private final List<Tok> t;
        private int p;
        private int lastLine;
        private final List<Node> names;
        private final Set<String> defined;
        private final IdentityHashMap<List<Tok>, ParsedField> parsedFields;

        private record ParsedField(Node expression, List<Node> names) {
        }

        Parser(List<Tok> tokens, List<Node> names, Set<String> defined) {
            this(tokens, names, defined, new IdentityHashMap<>());
        }

        private Parser(List<Tok> tokens, List<Node> names, Set<String> defined,
                IdentityHashMap<List<Tok>, ParsedField> parsedFields) {
            this.t = tokens;
            this.names = names;
            this.defined = defined;
            this.parsedFields = parsedFields;
        }

        Module module() throws Failure {
            List<Statement> body = new ArrayList<>();
            while (!at(Kind.END)) {
                if (at(Kind.INDENT)) {
                    throw fail("unexpected indent");
                }
                String[] head = statement();
                body.add(new Statement(head[0], head[1], lastLine));
            }
            Set<String> used = new LinkedHashSet<>();
            for (Node name : names) {
                if (name.ctx == 'L') {
                    used.add(name.id);
                }
            }
            return new Module(List.copyOf(body), defined, used);
        }

        // ---- tokens ------------------------------------------------------------------------------------------------

        private Tok peek() {
            return t.get(p);
        }

        private Tok peek(int k) {
            return t.get(Math.min(p + k, t.size() - 1));
        }

        private boolean at(Kind kind) {
            return peek().kind == kind;
        }

        private boolean op(String o) {
            Tok x = peek();
            return x.kind == Kind.OP && x.text.equals(o);
        }

        private boolean op(int k, String o) {
            Tok x = peek(k);
            return x.kind == Kind.OP && x.text.equals(o);
        }

        private boolean kw(String w) {
            Tok x = peek();
            return x.kind == Kind.NAME && x.text.equals(w);
        }

        private boolean kw(int k, String w) {
            Tok x = peek(k);
            return x.kind == Kind.NAME && x.text.equals(w);
        }

        private Tok next() {
            Tok x = t.get(p++);
            if (x.kind == Kind.NAME || x.kind == Kind.NUMBER || x.kind == Kind.STRING || x.kind == Kind.OP) {
                lastLine = x.endLine;
            }
            return x;
        }

        private void expectOp(String o) throws Failure {
            if (!op(o)) {
                throw fail("expected '" + o + "'");
            }
            next();
        }

        private void expectKw(String w) throws Failure {
            if (!kw(w)) {
                throw fail("expected '" + w + "'");
            }
            next();
        }

        private Failure fail(String message) {
            return new Failure("invalid syntax: " + message, peek().line);
        }

        private static boolean ident(Tok x) {
            return x.kind == Kind.NAME && !KEYWORDS.contains(x.text);
        }

        private String name() throws Failure {
            if (!ident(peek())) {
                throw fail("expected a name");
            }
            return next().text;
        }

        private Node nameNode(String id) {
            Node n = new Node('N', id, List.of());
            names.add(n);
            return n;
        }

        private static Node x(List<Node> kids) {
            return new Node('X', null, kids);
        }

        /** Where the parse can return to: position, the names seen and the names defined. */
        private record Mark(int p, int lastLine, int names, Set<String> defined) {
        }

        private Mark mark() {
            return new Mark(p, lastLine, names.size(), new LinkedHashSet<>(defined));
        }

        private void reset(Mark m) {
            p = m.p();
            lastLine = m.lastLine();
            names.subList(m.names(), names.size()).clear();
            defined.clear();
            defined.addAll(m.defined());
        }

        // ---- statements --------------------------------------------------------------------------------------------

        /** One statement; returns its kind (function, class, stmt) and name. */
        private String[] statement() throws Failure {
            Tok x = peek();
            if (x.kind == Kind.OP && x.text.equals("@")) {
                while (op("@")) {
                    next();
                    namedExpression();
                    if (!at(Kind.NEWLINE)) {
                        throw fail("expected a newline after a decorator");
                    }
                    next();
                }
                if (kw("def") || kw("async") && kw(1, "def")) {
                    return new String[]{"function", functionDef()};
                }
                if (kw("class")) {
                    return new String[]{"class", classDef()};
                }
                throw fail("expected a function or class after decorators");
            }
            if (x.kind == Kind.NAME) {
                switch (x.text) {
                    case "def" -> {
                        return new String[]{"function", functionDef()};
                    }
                    case "class" -> {
                        return new String[]{"class", classDef()};
                    }
                    case "async" -> {
                        if (kw(1, "def")) {
                            return new String[]{"function", functionDef()};
                        }
                        if (kw(1, "for")) {
                            next();
                            forStatement();
                            return new String[]{"stmt", ""};
                        }
                        if (kw(1, "with")) {
                            next();
                            withStatement();
                            return new String[]{"stmt", ""};
                        }
                    }
                    case "if" -> {
                        ifStatement();
                        return new String[]{"stmt", ""};
                    }
                    case "while" -> {
                        next();
                        namedExpression();
                        expectOp(":");
                        block();
                        elseBlock();
                        return new String[]{"stmt", ""};
                    }
                    case "for" -> {
                        forStatement();
                        return new String[]{"stmt", ""};
                    }
                    case "try" -> {
                        tryStatement();
                        return new String[]{"stmt", ""};
                    }
                    case "with" -> {
                        withStatement();
                        return new String[]{"stmt", ""};
                    }
                    case "match" -> {
                        Mark m = mark();
                        try {
                            matchStatement();
                            return new String[]{"stmt", ""};
                        } catch (Failure f) {
                            reset(m);
                        }
                    }
                    default -> {
                    }
                }
            }
            simpleStatements();
            return new String[]{"stmt", ""};
        }

        private void block() throws Failure {
            if (at(Kind.NEWLINE)) {
                next();
                if (!at(Kind.INDENT)) {
                    throw fail("expected an indented block");
                }
                next();
                while (!at(Kind.DEDENT)) {
                    if (at(Kind.END)) {
                        throw fail("unexpected end of the block");
                    }
                    statement();
                }
                next();
            } else {
                simpleStatements();
            }
        }

        private void elseBlock() throws Failure {
            if (kw("else")) {
                next();
                expectOp(":");
                block();
            }
        }

        private String functionDef() throws Failure {
            if (kw("async")) {
                next();
            }
            expectKw("def");
            String nm = name();
            defined.add(nm);
            if (op("[")) {
                typeParams();
            }
            expectOp("(");
            parameters(true, ")");
            expectOp(")");
            if (op("->")) {
                next();
                expression();
            }
            expectOp(":");
            block();
            return nm;
        }

        private String classDef() throws Failure {
            expectKw("class");
            String nm = name();
            defined.add(nm);
            if (op("[")) {
                typeParams();
            }
            if (op("(")) {
                next();
                arguments(")");
                expectOp(")");
            }
            expectOp(":");
            block();
            return nm;
        }

        private void typeParams() throws Failure {
            expectOp("[");
            if (op("]")) {
                throw fail("type parameter list cannot be empty");
            }
            while (!op("]")) {
                if (op("*")) {
                    next();
                    name();
                    if (op("=")) {
                        next();
                        starExpression();
                    }
                } else if (op("**")) {
                    next();
                    name();
                    if (op("=")) {
                        next();
                        expression();
                    }
                } else {
                    name();
                    if (op(":")) {
                        next();
                        expression();
                    }
                    if (op("=")) {
                        next();
                        expression();
                    }
                }
                if (!op("]")) {
                    expectOp(",");
                }
            }
            next();
        }

        /** Function or lambda parameters up to close; returns their default and annotation expressions. */
        private List<Node> parameters(boolean annotations, String close) throws Failure {
            List<Node> kids = new ArrayList<>();
            boolean slash = false;
            boolean star = false;
            boolean bareStar = false;
            boolean keywordOnly = false;
            boolean doubleStar = false;
            boolean defaults = false;
            int count = 0;
            while (!op(close)) {
                if (doubleStar) {
                    throw fail("arguments cannot follow var-keyword argument");
                }
                if (op("/")) {
                    if (slash || star || count == 0) {
                        throw fail("/ must be ahead of * and follow a parameter");
                    }
                    next();
                    slash = true;
                } else if (op("*")) {
                    if (star) {
                        throw fail("* argument may appear only once");
                    }
                    next();
                    star = true;
                    if (ident(peek())) {
                        name();
                        if (annotations && op(":")) {
                            next();
                            kids.add(op("*") ? starExpression() : expression());
                        }
                    } else {
                        bareStar = true;
                    }
                } else if (op("**")) {
                    next();
                    name();
                    if (annotations && op(":")) {
                        next();
                        kids.add(expression());
                    }
                    doubleStar = true;
                } else {
                    name();
                    if (star) {
                        keywordOnly = true;
                    }
                    if (annotations && op(":")) {
                        next();
                        kids.add(expression());
                    }
                    if (op("=")) {
                        next();
                        kids.add(expression());
                        defaults = true;
                    } else if (defaults && !star) {
                        throw fail("parameter without a default follows parameter with a default");
                    }
                }
                count++;
                if (!op(close)) {
                    expectOp(",");
                }
            }
            if (bareStar && !keywordOnly) {
                throw fail("named arguments must follow bare *");
            }
            return kids;
        }

        private void ifStatement() throws Failure {
            next();
            namedExpression();
            expectOp(":");
            block();
            while (kw("elif")) {
                next();
                namedExpression();
                expectOp(":");
                block();
            }
            elseBlock();
        }

        private void forStatement() throws Failure {
            expectKw("for");
            store(starTargets());
            expectKw("in");
            starExpressions();
            expectOp(":");
            block();
            elseBlock();
        }

        private void tryStatement() throws Failure {
            next();
            expectOp(":");
            block();
            if (kw("finally")) {
                next();
                expectOp(":");
                block();
                return;
            }
            if (!kw("except")) {
                throw fail("expected 'except' or 'finally' block");
            }
            Boolean starred = null;
            while (kw("except")) {
                next();
                boolean star = op("*");
                if (star) {
                    next();
                }
                if (starred != null && starred != star) {
                    throw fail("cannot have both 'except' and 'except*' on the same 'try'");
                }
                starred = star;
                if (op(":")) {
                    if (star) {
                        throw fail("expected one or more exception types");
                    }
                    next();
                    block();
                    continue;
                }
                expression();
                if (op(",")) {
                    while (op(",")) {
                        next();
                        expression();
                    }
                    if (kw("as")) {
                        throw fail("multiple exception types must be parenthesized when using 'as'");
                    }
                }
                if (kw("as")) {
                    next();
                    name();
                }
                expectOp(":");
                block();
            }
            elseBlock();
            if (kw("finally")) {
                next();
                expectOp(":");
                block();
            }
        }

        private void withStatement() throws Failure {
            expectKw("with");
            boolean done = false;
            if (op("(")) {
                Mark m = mark();
                try {
                    next();
                    withItem();
                    while (op(",")) {
                        next();
                        if (op(")")) {
                            break;
                        }
                        withItem();
                    }
                    expectOp(")");
                    done = op(":");
                } catch (Failure f) {
                    done = false;
                }
                if (!done) {
                    reset(m);
                }
            }
            if (!done) {
                withItem();
                while (op(",")) {
                    next();
                    withItem();
                }
            }
            expectOp(":");
            block();
        }

        private void withItem() throws Failure {
            expression();
            if (kw("as")) {
                next();
                store(starTarget());
            }
        }

        private void matchStatement() throws Failure {
            expectKw("match");
            if (op("*")) {
                starNamedExpression();
            } else {
                namedExpression();
            }
            if (op(",")) {
                while (op(",")) {
                    next();
                    if (op(":")) {
                        break;
                    }
                    starNamedExpression();
                }
            }
            expectOp(":");
            if (!at(Kind.NEWLINE)) {
                throw fail("expected a newline after match");
            }
            next();
            if (!at(Kind.INDENT)) {
                throw fail("expected an indented block");
            }
            next();
            if (!kw("case")) {
                throw fail("expected 'case'");
            }
            while (kw("case")) {
                next();
                pattern();
                if (op(",")) {
                    while (op(",")) {
                        next();
                        if (op(":") || kw("if")) {
                            break;
                        }
                        starPattern();
                    }
                }
                if (kw("if")) {
                    next();
                    namedExpression();
                }
                expectOp(":");
                block();
            }
            if (!at(Kind.DEDENT)) {
                throw fail("expected 'case'");
            }
            next();
        }

        private void starPattern() throws Failure {
            if (op("*")) {
                next();
                name();
            } else {
                pattern();
            }
        }

        private void pattern() throws Failure {
            closedPattern();
            while (op("|")) {
                next();
                closedPattern();
            }
            if (kw("as")) {
                next();
                if (name().equals("_")) {
                    throw fail("cannot use '_' as a target");
                }
            }
        }

        private boolean literalPattern() throws Failure {
            if (op("-") && peek(1).kind == Kind.NUMBER || at(Kind.NUMBER)) {
                if (op("-")) {
                    next();
                }
                next();
                if ((op("+") || op("-")) && peek(1).kind == Kind.NUMBER) {
                    next();
                    next();
                }
                return true;
            }
            if (at(Kind.STRING)) {
                strings();
                return true;
            }
            if (kw("None") || kw("True") || kw("False")) {
                next();
                return true;
            }
            return false;
        }

        private void closedPattern() throws Failure {
            if (literalPattern()) {
                return;
            }
            if (op("(")) {
                next();
                while (!op(")")) {
                    starPattern();
                    if (!op(")")) {
                        expectOp(",");
                    }
                }
                next();
                return;
            }
            if (op("[")) {
                next();
                while (!op("]")) {
                    starPattern();
                    if (!op("]")) {
                        expectOp(",");
                    }
                }
                next();
                return;
            }
            if (op("{")) {
                next();
                while (!op("}")) {
                    if (op("**")) {
                        next();
                        name();
                        if (op(",")) {
                            next();
                        }
                        break;
                    }
                    if (!literalPattern()) {
                        if (ident(peek()) && op(1, ".")) {
                            dottedValue();
                        } else {
                            throw fail("mapping pattern keys may only match literals and attribute lookups");
                        }
                    }
                    expectOp(":");
                    pattern();
                    if (!op("}")) {
                        expectOp(",");
                    }
                }
                expectOp("}");
                return;
            }
            if (ident(peek())) {
                if (op(1, ".") || op(1, "(")) {
                    dottedValue();
                    if (op("(")) {
                        next();
                        boolean keywords = false;
                        while (!op(")")) {
                            if (ident(peek()) && op(1, "=")) {
                                next();
                                next();
                                pattern();
                                keywords = true;
                            } else {
                                if (keywords) {
                                    throw fail("positional patterns follow keyword patterns");
                                }
                                pattern();
                            }
                            if (!op(")")) {
                                expectOp(",");
                            }
                        }
                        next();
                    }
                    return;
                }
                next();
                return;
            }
            throw fail("invalid pattern");
        }

        /** NAME ('.' NAME)*: a value pattern or a class pattern's class, loaded. */
        private void dottedValue() throws Failure {
            nameNode(name());
            while (op(".")) {
                next();
                name();
            }
        }

        private void simpleStatements() throws Failure {
            simpleStatement();
            while (op(";")) {
                next();
                if (at(Kind.NEWLINE)) {
                    break;
                }
                simpleStatement();
            }
            if (!at(Kind.NEWLINE)) {
                throw fail("expected a newline");
            }
            next();
        }

        private boolean endOfSimple() {
            return at(Kind.NEWLINE) || op(";");
        }

        private void simpleStatement() throws Failure {
            Tok x = peek();
            if (x.kind == Kind.NAME) {
                switch (x.text) {
                    case "pass", "break", "continue" -> {
                        next();
                        return;
                    }
                    case "return" -> {
                        next();
                        if (!endOfSimple()) {
                            starExpressions();
                        }
                        return;
                    }
                    case "raise" -> {
                        next();
                        if (!endOfSimple()) {
                            expression();
                            if (kw("from")) {
                                next();
                                expression();
                            }
                        }
                        return;
                    }
                    case "global", "nonlocal" -> {
                        next();
                        name();
                        while (op(",")) {
                            next();
                            name();
                        }
                        return;
                    }
                    case "del" -> {
                        next();
                        delete(primary());
                        while (op(",")) {
                            next();
                            if (endOfSimple()) {
                                break;
                            }
                            delete(primary());
                        }
                        if (!endOfSimple()) {
                            throw fail("invalid del target");
                        }
                        return;
                    }
                    case "assert" -> {
                        next();
                        expression();
                        if (op(",")) {
                            next();
                            expression();
                        }
                        return;
                    }
                    case "import" -> {
                        next();
                        dottedAsName();
                        while (op(",")) {
                            next();
                            dottedAsName();
                        }
                        return;
                    }
                    case "from" -> {
                        importFrom();
                        return;
                    }
                    case "type" -> {
                        if (ident(peek(1))) {
                            Mark m = mark();
                            try {
                                next();
                                nameNode(name()).ctx = 'S';
                                if (op("[")) {
                                    typeParams();
                                }
                                expectOp("=");
                                expression();
                                if (endOfSimple()) {
                                    return;
                                }
                                throw fail("type alias");
                            } catch (Failure f) {
                                reset(m);
                            }
                        }
                    }
                    default -> {
                    }
                }
            }
            expressionStatement();
        }

        private void dottedName() throws Failure {
            name();
            while (op(".")) {
                next();
                name();
            }
        }

        private void dottedAsName() throws Failure {
            dottedName();
            if (kw("as")) {
                next();
                name();
            }
        }

        private void importAsName() throws Failure {
            name();
            if (kw("as")) {
                next();
                name();
            }
        }

        private void importFrom() throws Failure {
            next();
            int dots = 0;
            while (op(".") || op("...")) {
                dots += next().text.length();
            }
            if (dots == 0 || !kw("import")) {
                dottedName();
            }
            expectKw("import");
            if (op("*")) {
                next();
            } else if (op("(")) {
                next();
                importAsName();
                while (op(",")) {
                    next();
                    if (op(")")) {
                        break;
                    }
                    importAsName();
                }
                expectOp(")");
            } else {
                importAsName();
                while (op(",")) {
                    next();
                    importAsName();
                }
            }
        }

        private void expressionStatement() throws Failure {
            Node first = kw("yield") ? yieldExpression() : starExpressions();
            if (op("=")) {
                List<Node> targets = new ArrayList<>();
                targets.add(first);
                while (op("=")) {
                    next();
                    Node rhs = kw("yield") ? yieldExpression() : starExpressions();
                    if (op("=")) {
                        targets.add(rhs);
                    }
                }
                for (Node target : targets) {
                    store(target);
                    walkNames(target);
                }
            } else if (peek().kind == Kind.OP && AUGMENTED.contains(peek().text)) {
                if (first.kind != 'N' && first.kind != 'A' && first.kind != 'S') {
                    throw fail("illegal expression for augmented assignment");
                }
                first.ctx = 'S';
                next();
                if (kw("yield")) {
                    yieldExpression();
                } else {
                    starExpressions();
                }
            } else if (op(":")) {
                if (first.kind != 'N' && first.kind != 'A' && first.kind != 'S') {
                    throw fail("only single target (not tuple) can be annotated");
                }
                first.ctx = 'S';
                next();
                expression();
                if (op("=")) {
                    next();
                    if (kw("yield")) {
                        yieldExpression();
                    } else {
                        starExpressions();
                    }
                }
            }
        }

        /** Marks an assignment target: names, attributes and subscripts store; tuples, lists and starred recurse. */
        private void store(Node n) throws Failure {
            switch (n.kind) {
                case 'N', 'A', 'S' -> n.ctx = 'S';
                case 'T', 'L' -> {
                    for (Node kid : n.kids) {
                        store(kid);
                    }
                }
                case '*' -> store(n.kids.getFirst());
                default -> throw fail("cannot assign to expression");
            }
        }

        private void delete(Node n) throws Failure {
            switch (n.kind) {
                case 'N', 'A', 'S' -> n.ctx = 'D';
                case 'T', 'L' -> {
                    for (Node kid : n.kids) {
                        delete(kid);
                    }
                }
                default -> throw fail("cannot delete expression");
            }
        }

        /** ast.walk over a target: every name in it, whatever its context, is defined. */
        private void walkNames(Node n) {
            if (n.kind == 'N') {
                defined.add(n.id);
            }
            for (Node kid : n.kids) {
                walkNames(kid);
            }
        }

        // ---- expressions -------------------------------------------------------------------------------------------

        private boolean startsExpression() {
            Tok x = peek();
            return switch (x.kind) {
                case NUMBER, STRING -> true;
                case NAME -> !KEYWORDS.contains(x.text) || Set.of("None", "True", "False", "lambda", "not", "await").contains(x.text);
                case OP -> Set.of("(", "[", "{", "-", "+", "~", "*", "...").contains(x.text);
                default -> false;
            };
        }

        private Node starExpressions() throws Failure {
            Node first = starExpression();
            if (!op(",")) {
                return first;
            }
            List<Node> items = new ArrayList<>();
            items.add(first);
            while (op(",")) {
                next();
                if (!startsExpression()) {
                    break;
                }
                items.add(starExpression());
            }
            return new Node('T', null, items);
        }

        private Node starExpression() throws Failure {
            if (op("*")) {
                next();
                return new Node('*', null, List.of(bitwiseOr()));
            }
            return expression();
        }

        private Node starNamedExpression() throws Failure {
            if (op("*")) {
                next();
                return new Node('*', null, List.of(bitwiseOr()));
            }
            return namedExpression();
        }

        private Node namedExpression() throws Failure {
            if (ident(peek()) && op(1, ":=")) {
                Node target = nameNode(next().text);
                target.ctx = 'S';
                next();
                return x(List.of(target, expression()));
            }
            Node e = expression();
            if (op(":=")) {
                throw fail("cannot use assignment expressions with this target");
            }
            return e;
        }

        private Node expression() throws Failure {
            if (kw("lambda")) {
                next();
                List<Node> kids = new ArrayList<>();
                if (!op(":")) {
                    kids.addAll(parameters(false, ":"));
                }
                expectOp(":");
                kids.add(expression());
                return x(kids);
            }
            Node d = disjunction();
            if (kw("if")) {
                next();
                Node condition = disjunction();
                expectKw("else");
                return x(List.of(d, condition, expression()));
            }
            return d;
        }

        private Node disjunction() throws Failure {
            return words(this::conjunction, "or");
        }

        private Node conjunction() throws Failure {
            return words(this::inversion, "and");
        }

        private Node words(Rule sub, String word) throws Failure {
            Node first = sub.parse();
            if (!kw(word)) {
                return first;
            }
            List<Node> kids = new ArrayList<>();
            kids.add(first);
            while (kw(word)) {
                next();
                kids.add(sub.parse());
            }
            return x(kids);
        }

        private Node inversion() throws Failure {
            boolean inverted = false;
            while (kw("not")) {
                next();
                inverted = true;
            }
            Node value = comparison();
            return inverted ? x(List.of(value)) : value;
        }

        private Node comparison() throws Failure {
            Node first = bitwiseOr();
            List<Node> kids = null;
            while (true) {
                if (peek().kind == Kind.OP && COMPARISONS.contains(peek().text) || kw("in")) {
                    next();
                } else if (kw("not") && kw(1, "in")) {
                    next();
                    next();
                } else if (kw("is")) {
                    next();
                    if (kw("not")) {
                        next();
                    }
                } else {
                    break;
                }
                if (kids == null) {
                    kids = new ArrayList<>();
                    kids.add(first);
                }
                kids.add(bitwiseOr());
            }
            return kids == null ? first : x(kids);
        }

        private Node bitwiseOr() throws Failure {
            // These operators all need factor operands and produce non-target expressions. Their precedence
            // does not affect the gathered names/contexts; flattening avoids six grammar layers per delimiter.
            Node first = factor();
            List<Node> kids = null;
            while (peek().kind == Kind.OP && BINARY_OPERATORS.contains(peek().text)) {
                next();
                if (kids == null) {
                    kids = new ArrayList<>();
                    kids.add(first);
                }
                kids.add(factor());
            }
            return kids == null ? first : x(kids);
        }

        private Node factor() throws Failure {
            List<Node> kids = new ArrayList<>();
            boolean operated = false;
            while (true) {
                while (op("+") || op("-") || op("~")) {
                    next();
                    operated = true;
                }
                Node base = kw("await") ? awaited() : primary();
                kids.add(base);
                if (!op("**")) {
                    // Only name contexts and assignment-target kinds are needed, as in bitwiseOr().
                    return operated ? x(kids) : base;
                }
                next();
                operated = true;
            }
        }

        private Node awaited() throws Failure {
            next();
            return x(List.of(primary()));
        }

        private Node primary() throws Failure {
            Node a = atom();
            while (true) {
                if (op(".")) {
                    next();
                    name();
                    a = new Node('A', null, List.of(a));
                } else if (op("(")) {
                    next();
                    List<Node> kids = new ArrayList<>();
                    kids.add(a);
                    kids.addAll(arguments(")"));
                    expectOp(")");
                    a = x(kids);
                } else if (op("[")) {
                    next();
                    List<Node> kids = new ArrayList<>();
                    kids.add(a);
                    kids.add(slice());
                    while (op(",")) {
                        next();
                        if (op("]")) {
                            break;
                        }
                        kids.add(slice());
                    }
                    expectOp("]");
                    a = new Node('S', null, kids);
                } else {
                    return a;
                }
            }
        }

        private boolean comprehensionFollows() {
            return kw("for") || kw("async") && kw(1, "for");
        }

        /** Call arguments (or class bases) up to close. */
        private List<Node> arguments(String close) throws Failure {
            List<Node> kids = new ArrayList<>();
            boolean keywords = false;
            boolean doubleStars = false;
            int count = 0;
            while (!op(close)) {
                if (op("*")) {
                    next();
                    if (doubleStars) {
                        throw fail("iterable argument unpacking follows keyword argument unpacking");
                    }
                    kids.add(expression());
                } else if (op("**")) {
                    next();
                    kids.add(expression());
                    doubleStars = true;
                } else if (ident(peek()) && op(1, "=")) {
                    next();
                    next();
                    kids.add(expression());
                    keywords = true;
                } else {
                    Node e = namedExpression();
                    if (comprehensionFollows()) {
                        e = comprehension(List.of(e));
                        if (count > 0 || !op(close)) {
                            throw fail("Generator expression must be parenthesized");
                        }
                    }
                    if (keywords || doubleStars) {
                        throw fail("positional argument follows keyword argument");
                    }
                    kids.add(e);
                }
                count++;
                if (!op(close)) {
                    expectOp(",");
                }
            }
            return kids;
        }

        private Node slice() throws Failure {
            if (op("*")) {
                next();
                return new Node('*', null, List.of(bitwiseOr()));
            }
            List<Node> kids = new ArrayList<>();
            if (!op(":")) {
                Node e = namedExpression();
                if (!op(":")) {
                    return e;
                }
                kids.add(e);
            }
            next();
            if (!op(":") && !op(",") && !op("]")) {
                kids.add(expression());
            }
            if (op(":")) {
                next();
                if (!op(",") && !op("]")) {
                    kids.add(expression());
                }
            }
            return x(kids);
        }

        private Node atom() throws Failure {
            Tok x = peek();
            switch (x.kind) {
                case NAME -> {
                    if (x.text.equals("None") || x.text.equals("True") || x.text.equals("False")) {
                        next();
                        return x(List.of());
                    }
                    if (KEYWORDS.contains(x.text)) {
                        throw fail("unexpected '" + x.text + "'");
                    }
                    next();
                    return nameNode(x.text);
                }
                case NUMBER -> {
                    next();
                    return x(List.of());
                }
                case STRING -> {
                    return strings();
                }
                case OP -> {
                    switch (x.text) {
                        case "..." -> {
                            next();
                            return x(List.of());
                        }
                        case "(" -> {
                            return parenthesized();
                        }
                        case "[" -> {
                            return listDisplay();
                        }
                        case "{" -> {
                            return braceDisplay();
                        }
                        default -> {
                        }
                    }
                }
                default -> {
                }
            }
            throw fail("invalid syntax");
        }

        /** Adjacent string literals, with the expressions of their replacement fields. */
        private Node strings() throws Failure {
            List<Node> kids = new ArrayList<>();
            boolean bytes = false;
            boolean text = false;
            boolean templates = false;
            boolean plain = false;
            while (at(Kind.STRING)) {
                Tok s = next();
                if (s.flavour == 'b') {
                    bytes = true;
                } else {
                    text = true;
                }
                if (s.flavour == 't') {
                    templates = true;
                } else {
                    plain = true;
                }
                for (List<Tok> field : s.fields) {
                    ParsedField parsed = parseField(field);
                    names.addAll(parsed.names());
                    kids.add(parsed.expression());
                }
            }
            if (templates && plain) {
                throw fail("cannot mix t-string literals with string or bytes literals");
            }
            if (bytes && text) {
                throw fail("cannot mix bytes and nonbytes literals");
            }
            return x(kids);
        }

        /** Parse nested string fields from the leaves, without repeating the expression grammar on the Java stack. */
        private ParsedField parseField(List<Tok> field) throws Failure {
            if (!parsedFields.containsKey(field)) {
                Deque<List<Tok>> pending = new ArrayDeque<>();
                pending.push(field);
                while (!pending.isEmpty()) {
                    List<Tok> current = pending.peek();
                    boolean ready = true;
                    for (Tok token : current) {
                        for (List<Tok> child : token.fields) {
                            if (!parsedFields.containsKey(child)) {
                                pending.push(child);
                                ready = false;
                            }
                        }
                    }
                    if (ready) {
                        List<Node> fieldNames = new ArrayList<>();
                        Parser sub = new Parser(current, fieldNames, defined, parsedFields);
                        Node expression = sub.fieldExpression();
                        parsedFields.put(current, new ParsedField(expression, List.copyOf(fieldNames)));
                        pending.pop();
                    }
                }
            }
            return parsedFields.get(field);
        }

        /** The expression of one replacement field: yield or star expressions, and nothing after them. */
        private Node fieldExpression() throws Failure {
            Node e = kw("yield") ? yieldExpression() : starExpressions();
            if (!at(Kind.END)) {
                throw fail("f-string: expecting '}'");
            }
            return e;
        }

        private Node parenthesized() throws Failure {
            next();
            if (op(")")) {
                next();
                return new Node('T', null, List.of());
            }
            if (kw("yield")) {
                Node y = yieldExpression();
                expectOp(")");
                return y;
            }
            Node first = starNamedExpression();
            if (comprehensionFollows()) {
                Node g = comprehension(List.of(first));
                expectOp(")");
                return g;
            }
            if (op(",")) {
                List<Node> items = new ArrayList<>();
                items.add(first);
                while (op(",")) {
                    next();
                    if (op(")")) {
                        break;
                    }
                    items.add(starNamedExpression());
                }
                expectOp(")");
                return new Node('T', null, items);
            }
            expectOp(")");
            if (first.kind == '*') {
                throw fail("cannot use starred expression here");
            }
            return first;
        }

        private Node listDisplay() throws Failure {
            next();
            if (op("]")) {
                next();
                return new Node('L', null, List.of());
            }
            Node first = starNamedExpression();
            if (comprehensionFollows()) {
                Node c = comprehension(List.of(first));
                expectOp("]");
                return c;
            }
            List<Node> items = new ArrayList<>();
            items.add(first);
            while (op(",")) {
                next();
                if (op("]")) {
                    break;
                }
                items.add(starNamedExpression());
            }
            expectOp("]");
            return new Node('L', null, items);
        }

        private Node braceDisplay() throws Failure {
            next();
            if (op("}")) {
                next();
                return x(List.of());
            }
            List<Node> kids = new ArrayList<>();
            boolean dict;
            if (op("**")) {
                next();
                kids.add(bitwiseOr());
                dict = true;
            } else {
                Node first = starNamedExpression();
                kids.add(first);
                dict = op(":");
                if (dict) {
                    next();
                    kids.add(expression());
                }
                if (comprehensionFollows()) {
                    Node c = comprehension(kids);
                    expectOp("}");
                    return c;
                }
            }
            while (op(",")) {
                next();
                if (op("}")) {
                    break;
                }
                if (dict) {
                    if (op("**")) {
                        next();
                        kids.add(bitwiseOr());
                    } else {
                        kids.add(expression());
                        expectOp(":");
                        kids.add(expression());
                    }
                } else {
                    kids.add(starNamedExpression());
                }
            }
            expectOp("}");
            return x(kids);
        }

        private Node comprehension(List<Node> elements) throws Failure {
            List<Node> kids = new ArrayList<>(elements);
            do {
                if (kw("async")) {
                    next();
                }
                expectKw("for");
                Node target = starTargets();
                store(target);
                kids.add(target);
                expectKw("in");
                kids.add(disjunction());
                while (kw("if")) {
                    next();
                    kids.add(disjunction());
                }
            } while (comprehensionFollows());
            return x(kids);
        }

        private Node starTargets() throws Failure {
            Node first = starTarget();
            if (!op(",")) {
                return first;
            }
            List<Node> items = new ArrayList<>();
            items.add(first);
            while (op(",")) {
                next();
                if (kw("in") || op("=") || op(")") || op(":")) {
                    break;
                }
                items.add(starTarget());
            }
            return new Node('T', null, items);
        }

        private Node starTarget() throws Failure {
            if (op("*")) {
                next();
                return new Node('*', null, List.of(starTarget()));
            }
            return primary();
        }

        private Node yieldExpression() throws Failure {
            next();
            if (kw("from")) {
                next();
                return x(List.of(expression()));
            }
            if (startsExpression()) {
                return x(List.of(starExpressions()));
            }
            return x(List.of());
        }
    }
}
