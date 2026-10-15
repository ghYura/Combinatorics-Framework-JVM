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

package org.ghyura.cf.model.rule;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses a {@code when} predicate: the Python expression grammar (precedence, literals, comparison chains) for
 * the operators the legacy sieve's restricted {@code eval} could evaluate. Text the legacy refused before evaluating
 * ({@code _UNSAFE}: double underscores, import, lambda, the walrus, exec, eval, open, globals, locals) is refused here
 * as well; so is every construct outside the grammar (comprehensions, slices, keyword arguments, f-strings, bytes,
 * complex numbers), with the offset of the problem.
 */
public final class ExprParser {

    /** The legacy {@code _UNSAFE} pattern of constraints/sieve.py, verbatim. */
    public static final Pattern UNSAFE = Pattern.compile(
            "__|import|lambda|:=|\\bexec\\b|\\beval\\b|\\bopen\\b|\\bglobals\\b|\\blocals\\b");

    /** The functions the legacy {@code _SAFE_FUNCS} allowed. */
    public static final Set<String> FUNCTIONS = Set.of("abs", "min", "max", "round", "len", "int", "float", "bool");

    private static final Set<String> KEYWORDS = Set.of("False", "None", "True", "and", "as", "assert", "async", "await",
            "break", "class", "continue", "def", "del", "elif", "else", "except", "finally", "for", "from", "global",
            "if", "import", "in", "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
            "with", "yield");

    /** A text that is not a supported expression; {@code offset} is the character where the problem is. */
    public static final class SyntaxError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int offset;

        SyntaxError(String message, int offset) {
            super(message);
            this.offset = offset;
        }

        public int offset() {
            return offset;
        }
    }

    private enum Kind { NAME, NUMBER, STRING, OP, END }

    private record Token(Kind kind, String text, RuleValue value, int offset) {
    }

    private final String source;
    private final List<Token> tokens = new ArrayList<>();
    private int position;

    private ExprParser(String source) {
        this.source = source;
    }

    /**
     * @throws SyntaxError when the text is unsafe in the legacy sense or not an expression of the grammar
     */
    public static Expr parse(String text) {
        java.util.regex.Matcher unsafe = UNSAFE.matcher(text);
        if (unsafe.find()) {
            throw new SyntaxError("unsafe expression rejected (the legacy sieve refused '" + unsafe.group() + "' as well)",
                    unsafe.start());
        }
        ExprParser parser = new ExprParser(text);
        parser.tokenize();
        Expr expr = parser.test();
        Token end = parser.peek();
        if (end.kind != Kind.END) {
            throw new SyntaxError("unexpected '" + end.text + "' after the expression", end.offset);
        }
        return expr;
    }

    // ---- tokens ------------------------------------------------------------------------------------------------

    private void tokenize() {
        int i = 0;
        int depth = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == ' ' || c == '\t' || c == '\f') {
                i++;
                continue;
            }
            if (c == '\\' && i + 1 < n && (source.charAt(i + 1) == '\n' || source.charAt(i + 1) == '\r')) {
                i += source.charAt(i + 1) == '\r' && i + 2 < n && source.charAt(i + 2) == '\n' ? 3 : 2;
                continue;
            }
            if (c == '\n' || c == '\r') {
                if (depth == 0 && !onlyBlankAfter(i)) {
                    throw new SyntaxError("a line break outside brackets ends the expression", i);
                }
                i++;
                continue;
            }
            if (c == '#') {
                while (i < n && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
                    i++;
                }
                continue;
            }
            if (Character.isUnicodeIdentifierStart(c) || c == '_') {
                int start = i;
                while (i < n && (Character.isUnicodeIdentifierPart(source.charAt(i)) && !Character.isIdentifierIgnorable(source.charAt(i)))) {
                    i++;
                }
                String word = source.substring(start, i);
                if (i < n && (source.charAt(i) == '\'' || source.charAt(i) == '"') && isPrefix(word)) {
                    i = string(word, start, i);
                    continue;
                }
                tokens.add(new Token(Kind.NAME, word, null, start));
                continue;
            }
            if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(source.charAt(i + 1)))) {
                i = number(i);
                continue;
            }
            if (c == '\'' || c == '"') {
                i = string("", i, i);
                continue;
            }
            String op = operator(i);
            if (op == null) {
                throw new SyntaxError("unexpected character '" + c + "'", i);
            }
            if (op.equals("(") || op.equals("[") || op.equals("{")) {
                depth++;
            } else if (op.equals(")") || op.equals("]") || op.equals("}")) {
                depth--;
            }
            tokens.add(new Token(Kind.OP, op, null, i));
            i += op.length();
        }
        tokens.add(new Token(Kind.END, "end of the expression", null, n));
    }

    private boolean onlyBlankAfter(int i) {
        for (int j = i; j < source.length(); j++) {
            char c = source.charAt(j);
            if (c == '#') {
                return true;
            }
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPrefix(String word) {
        return switch (word.toLowerCase(java.util.Locale.ROOT)) {
            case "r", "u", "b", "f", "br", "rb", "fr", "rf" -> true;
            default -> false;
        };
    }

    private static final String[] OPERATORS = {"**", "//", "<<", ">>", "<=", ">=", "==", "!=", ":=", "->", "+", "-", "*", "/",
            "%", "~", "<", ">", "(", ")", "[", "]", "{", "}", ",", ".", ":", "&", "|", "^", "@", "="};

    private String operator(int i) {
        for (String op : OPERATORS) {
            if (source.startsWith(op, i)) {
                return op;
            }
        }
        return null;
    }

    private int number(int start) {
        int n = source.length();
        int i = start;
        if (source.charAt(i) == '0' && i + 1 < n && "xXoObB".indexOf(source.charAt(i + 1)) >= 0) {
            char base = Character.toLowerCase(source.charAt(i + 1));
            int radix = base == 'x' ? 16 : base == 'o' ? 8 : 2;
            i += 2;
            StringBuilder digits = new StringBuilder();
            boolean lastUnderscore = true;
            while (i < n && (Character.digit(source.charAt(i), 16) >= 0 || source.charAt(i) == '_')) {
                char c = source.charAt(i);
                if (c == '_') {
                    if (lastUnderscore) {
                        throw new SyntaxError("invalid number literal", i);
                    }
                    lastUnderscore = true;
                } else {
                    if (Character.digit(c, radix) < 0) {
                        throw new SyntaxError("invalid digit '" + c + "' in a base " + radix + " literal", i);
                    }
                    digits.append(c);
                    lastUnderscore = false;
                }
                i++;
            }
            if (digits.isEmpty() || lastUnderscore) {
                throw new SyntaxError("invalid number literal", i);
            }
            checkNumberEnd(i);
            tokens.add(new Token(Kind.NUMBER, source.substring(start, i), new RuleValue.Int(new BigInteger(digits.toString(), radix)), start));
            return i;
        }
        boolean isFloat = false;
        if (source.charAt(i) != '.') {
            i = digitPart(i);
        }
        if (i < n && source.charAt(i) == '.') {
            isFloat = true;
            i++;
            if (i < n && Character.isDigit(source.charAt(i))) {
                i = digitPart(i);
            }
        }
        if (i < n && (source.charAt(i) == 'e' || source.charAt(i) == 'E')) {
            int e = i + 1;
            if (e < n && (source.charAt(e) == '+' || source.charAt(e) == '-')) {
                e++;
            }
            if (e < n && Character.isDigit(source.charAt(e))) {
                isFloat = true;
                i = digitPart(e);
            }
        }
        if (i < n && (source.charAt(i) == 'j' || source.charAt(i) == 'J')) {
            throw new SyntaxError("complex numbers are not supported", i);
        }
        checkNumberEnd(i);
        String literal = source.substring(start, i);
        String plain = literal.replace("_", "");
        if (isFloat) {
            tokens.add(new Token(Kind.NUMBER, literal, new RuleValue.Real(Double.parseDouble(plain)), start));
        } else {
            if (plain.length() > 1 && plain.charAt(0) == '0' && !plain.chars().allMatch(ch -> ch == '0')) {
                throw new SyntaxError("leading zeros in decimal integer literals are not permitted", start);
            }
            tokens.add(new Token(Kind.NUMBER, literal, new RuleValue.Int(new BigInteger(plain)), start));
        }
        return i;
    }

    private int digitPart(int i) {
        int n = source.length();
        if (i >= n || !Character.isDigit(source.charAt(i))) {
            throw new SyntaxError("invalid number literal", i);
        }
        while (i < n && (Character.isDigit(source.charAt(i)) || source.charAt(i) == '_')) {
            if (source.charAt(i) == '_' && (i + 1 >= n || !Character.isDigit(source.charAt(i + 1)))) {
                throw new SyntaxError("invalid number literal", i);
            }
            i++;
        }
        return i;
    }

    /** A letter, digit or underscore right after a number is refused rather than split into two tokens. */
    private void checkNumberEnd(int i) {
        if (i < source.length() && Character.isUnicodeIdentifierPart(source.charAt(i))) {
            throw new SyntaxError("invalid number literal", i);
        }
    }

    private int string(String prefix, int start, int quoteAt) {
        String p = prefix.toLowerCase(java.util.Locale.ROOT);
        if (p.contains("b")) {
            throw new SyntaxError("bytes literals are not supported", start);
        }
        if (p.contains("f")) {
            throw new SyntaxError("f-strings are not supported", start);
        }
        boolean raw = p.contains("r");
        int n = source.length();
        char quote = source.charAt(quoteAt);
        boolean triple = source.startsWith(String.valueOf(quote).repeat(3), quoteAt);
        int i = quoteAt + (triple ? 3 : 1);
        StringBuilder out = new StringBuilder();
        while (true) {
            if (i >= n) {
                throw new SyntaxError("unterminated string literal", start);
            }
            char c = source.charAt(i);
            if (triple ? source.startsWith(String.valueOf(quote).repeat(3), i) : c == quote) {
                i += triple ? 3 : 1;
                break;
            }
            if (!triple && (c == '\n' || c == '\r')) {
                throw new SyntaxError("unterminated string literal", start);
            }
            if (c == '\\') {
                if (i + 1 >= n) {
                    throw new SyntaxError("unterminated string literal", start);
                }
                char e = source.charAt(i + 1);
                if (raw) {
                    out.append(c).append(e);
                    i += 2;
                    continue;
                }
                i = escape(e, i, out);
                continue;
            }
            out.append(c);
            i++;
        }
        String text = out.toString();
        Token previous = tokens.isEmpty() ? null : tokens.getLast();
        if (previous != null && previous.kind == Kind.STRING) {
            tokens.set(tokens.size() - 1, new Token(Kind.STRING, previous.text + source.substring(start, i),
                    new RuleValue.Text(((RuleValue.Text) previous.value).value() + text), previous.offset));
        } else {
            tokens.add(new Token(Kind.STRING, source.substring(start, i), new RuleValue.Text(text), start));
        }
        return i;
    }

    private int escape(char e, int at, StringBuilder out) {
        int i = at + 2;
        switch (e) {
            case '\n' -> { }
            case '\r' -> {
                if (i < source.length() && source.charAt(i) == '\n') {
                    i++;
                }
            }
            case '\\', '\'', '"' -> out.append(e);
            case 'a' -> out.append('\u0007');
            case 'b' -> out.append('\b');
            case 'f' -> out.append('\f');
            case 'n' -> out.append('\n');
            case 'r' -> out.append('\r');
            case 't' -> out.append('\t');
            case 'v' -> out.append('\u000b');
            case 'x' -> i = hex(at, 2, out);
            case 'u' -> i = hex(at, 4, out);
            case 'U' -> i = hex(at, 8, out);
            case 'N' -> {
                int close = source.indexOf('}', i);
                if (i >= source.length() || source.charAt(i) != '{' || close < 0) {
                    throw new SyntaxError("malformed \\N{...} escape", at);
                }
                try {
                    out.appendCodePoint(Character.codePointOf(source.substring(i + 1, close)));
                } catch (IllegalArgumentException ex) {
                    throw new SyntaxError("unknown Unicode character name in \\N{...}", at);
                }
                i = close + 1;
            }
            default -> {
                if (e >= '0' && e <= '7') {
                    int value = e - '0';
                    int j = at + 2;
                    while (j < at + 4 && j < source.length() && source.charAt(j) >= '0' && source.charAt(j) <= '7') {
                        value = value * 8 + (source.charAt(j) - '0');
                        j++;
                    }
                    out.appendCodePoint(value);
                    i = j;
                } else {
                    out.append('\\').append(e);
                }
            }
        }
        return i;
    }

    private int hex(int at, int digits, StringBuilder out) {
        int start = at + 2;
        if (start + digits > source.length()) {
            throw new SyntaxError("truncated \\" + source.charAt(at + 1) + " escape", at);
        }
        String hex = source.substring(start, start + digits);
        for (int k = 0; k < hex.length(); k++) {
            if (Character.digit(hex.charAt(k), 16) < 0) {
                throw new SyntaxError("truncated \\" + source.charAt(at + 1) + " escape", at);
            }
        }
        long cp = Long.parseLong(hex, 16);
        if (cp > Character.MAX_CODE_POINT) {
            throw new SyntaxError("illegal Unicode character", at);
        }
        out.appendCodePoint((int) cp);
        return start + digits;
    }

    // ---- grammar -----------------------------------------------------------------------------------------------

    private Token peek() {
        return tokens.get(position);
    }

    private Token next() {
        return tokens.get(position++);
    }

    private boolean atOp(String op) {
        Token t = peek();
        return t.kind == Kind.OP && t.text.equals(op);
    }

    private boolean atWord(String word) {
        Token t = peek();
        return t.kind == Kind.NAME && t.text.equals(word);
    }

    private void expectOp(String op) {
        Token t = next();
        if (t.kind != Kind.OP || !t.text.equals(op)) {
            throw new SyntaxError("expected '" + op + "' but found '" + t.text + "'", t.offset);
        }
    }

    /** test: or_test ['if' or_test 'else' test] | lambda (refused). */
    private Expr test() {
        Expr value = orTest();
        if (atWord("if")) {
            next();
            Expr condition = orTest();
            if (!atWord("else")) {
                throw new SyntaxError("expected 'else' in a conditional expression", peek().offset);
            }
            next();
            Expr otherwise = test();
            return new Expr.IfElse(condition, value, otherwise);
        }
        return value;
    }

    private Expr orTest() {
        Expr first = andTest();
        if (!atWord("or")) {
            return first;
        }
        List<Expr> operands = new ArrayList<>(List.of(first));
        while (atWord("or")) {
            next();
            operands.add(andTest());
        }
        return new Expr.Or(operands);
    }

    private Expr andTest() {
        Expr first = notTest();
        if (!atWord("and")) {
            return first;
        }
        List<Expr> operands = new ArrayList<>(List.of(first));
        while (atWord("and")) {
            next();
            operands.add(notTest());
        }
        return new Expr.And(operands);
    }

    private Expr notTest() {
        if (atWord("not")) {
            next();
            return new Expr.Not(notTest());
        }
        return comparison();
    }

    private Expr comparison() {
        Expr first = bitOr();
        List<Expr.CompareOp> ops = new ArrayList<>();
        List<Expr> rest = new ArrayList<>();
        while (true) {
            Expr.CompareOp op = compareOp();
            if (op == null) {
                break;
            }
            ops.add(op);
            rest.add(bitOr());
        }
        return ops.isEmpty() ? first : new Expr.Compare(first, ops, rest);
    }

    private Expr.CompareOp compareOp() {
        Token t = peek();
        if (t.kind == Kind.OP) {
            Expr.CompareOp op = switch (t.text) {
                case "<" -> Expr.CompareOp.LESS;
                case "<=" -> Expr.CompareOp.LESS_EQUAL;
                case ">" -> Expr.CompareOp.GREATER;
                case ">=" -> Expr.CompareOp.GREATER_EQUAL;
                case "==" -> Expr.CompareOp.EQUAL;
                case "!=" -> Expr.CompareOp.NOT_EQUAL;
                default -> null;
            };
            if (op != null) {
                next();
            }
            return op;
        }
        if (t.kind == Kind.NAME) {
            switch (t.text) {
                case "in" -> {
                    next();
                    return Expr.CompareOp.IN;
                }
                case "is" -> {
                    next();
                    if (atWord("not")) {
                        next();
                        return Expr.CompareOp.IS_NOT;
                    }
                    return Expr.CompareOp.IS;
                }
                case "not" -> {
                    Token after = tokens.get(position + 1);
                    if (after.kind == Kind.NAME && after.text.equals("in")) {
                        position += 2;
                        return Expr.CompareOp.NOT_IN;
                    }
                    return null;
                }
                default -> {
                    return null;
                }
            }
        }
        return null;
    }

    private Expr bitOr() {
        Expr left = bitXor();
        while (atOp("|")) {
            next();
            left = new Expr.Binary(Expr.BinaryOp.BIT_OR, left, bitXor());
        }
        return left;
    }

    private Expr bitXor() {
        Expr left = bitAnd();
        while (atOp("^")) {
            next();
            left = new Expr.Binary(Expr.BinaryOp.BIT_XOR, left, bitAnd());
        }
        return left;
    }

    private Expr bitAnd() {
        Expr left = shift();
        while (atOp("&")) {
            next();
            left = new Expr.Binary(Expr.BinaryOp.BIT_AND, left, shift());
        }
        return left;
    }

    private Expr shift() {
        Expr left = arith();
        while (atOp("<<") || atOp(">>")) {
            Expr.BinaryOp op = next().text.equals("<<") ? Expr.BinaryOp.SHIFT_LEFT : Expr.BinaryOp.SHIFT_RIGHT;
            left = new Expr.Binary(op, left, arith());
        }
        return left;
    }

    private Expr arith() {
        Expr left = term();
        while (atOp("+") || atOp("-")) {
            Expr.BinaryOp op = next().text.equals("+") ? Expr.BinaryOp.ADD : Expr.BinaryOp.SUBTRACT;
            left = new Expr.Binary(op, left, term());
        }
        return left;
    }

    private Expr term() {
        Expr left = factor();
        while (true) {
            Expr.BinaryOp op;
            if (atOp("*")) {
                op = Expr.BinaryOp.MULTIPLY;
            } else if (atOp("/")) {
                op = Expr.BinaryOp.DIVIDE;
            } else if (atOp("//")) {
                op = Expr.BinaryOp.FLOOR_DIVIDE;
            } else if (atOp("%")) {
                op = Expr.BinaryOp.MODULO;
            } else if (atOp("@")) {
                throw new SyntaxError("matrix multiplication is not supported", peek().offset);
            } else {
                return left;
            }
            next();
            left = new Expr.Binary(op, left, factor());
        }
    }

    private Expr factor() {
        if (atOp("-")) {
            next();
            return new Expr.Unary(Expr.UnaryOp.NEGATE, factor());
        }
        if (atOp("+")) {
            next();
            return new Expr.Unary(Expr.UnaryOp.PLUS, factor());
        }
        if (atOp("~")) {
            next();
            return new Expr.Unary(Expr.UnaryOp.INVERT, factor());
        }
        return power();
    }

    private Expr power() {
        Expr base = primary();
        if (atOp("**")) {
            next();
            return new Expr.Binary(Expr.BinaryOp.POWER, base, factor());
        }
        return base;
    }

    private Expr primary() {
        Expr e = atom();
        while (true) {
            if (atOp(".")) {
                next();
                Token name = next();
                if (name.kind != Kind.NAME || KEYWORDS.contains(name.text)) {
                    throw new SyntaxError("expected an attribute name after '.'", name.offset);
                }
                e = new Expr.Attribute(e, name.text);
            } else if (atOp("(")) {
                Token open = next();
                if (!(e instanceof Expr.Name callee)) {
                    throw new SyntaxError("only the functions " + String.join(", ", new java.util.TreeSet<>(FUNCTIONS))
                            + " can be called", open.offset);
                }
                List<Expr> args = new ArrayList<>();
                while (!atOp(")")) {
                    if (atOp("*") || atOp("**")) {
                        throw new SyntaxError("argument unpacking is not supported", peek().offset);
                    }
                    Expr arg = test();
                    if (atOp("=")) {
                        throw new SyntaxError("keyword arguments are not supported", peek().offset);
                    }
                    if (atWord("for")) {
                        throw new SyntaxError("generator expressions are not supported", peek().offset);
                    }
                    args.add(arg);
                    if (!atOp(",")) {
                        break;
                    }
                    next();
                }
                expectOp(")");
                e = new Expr.Call(callee.name(), args);
            } else if (atOp("[")) {
                Token open = next();
                Expr index = test();
                if (atOp(":") || atOp(",")) {
                    throw new SyntaxError("slices and tuple indexes are not supported", open.offset);
                }
                expectOp("]");
                e = new Expr.Subscript(e, index);
            } else {
                return e;
            }
        }
    }

    private Expr atom() {
        Token t = next();
        switch (t.kind) {
            case NUMBER, STRING -> {
                return new Expr.Constant(t.value);
            }
            case NAME -> {
                switch (t.text) {
                    case "True" -> {
                        return new Expr.Constant(new RuleValue.Bool(true));
                    }
                    case "False" -> {
                        return new Expr.Constant(new RuleValue.Bool(false));
                    }
                    case "None" -> {
                        return new Expr.Constant(RuleValue.Null.NULL);
                    }
                    default -> {
                        if (KEYWORDS.contains(t.text)) {
                            throw new SyntaxError("unexpected keyword '" + t.text + "'", t.offset);
                        }
                        return new Expr.Name(t.text);
                    }
                }
            }
            case OP -> {
                switch (t.text) {
                    case "(" -> {
                        if (atOp(")")) {
                            next();
                            return new Expr.TupleDisplay(List.of());
                        }
                        Expr first = test();
                        if (atWord("for")) {
                            throw new SyntaxError("generator expressions are not supported", peek().offset);
                        }
                        if (atOp(")")) {
                            next();
                            return first;
                        }
                        List<Expr> items = new ArrayList<>(List.of(first));
                        while (atOp(",")) {
                            next();
                            if (atOp(")")) {
                                break;
                            }
                            items.add(test());
                        }
                        expectOp(")");
                        return new Expr.TupleDisplay(items);
                    }
                    case "[" -> {
                        List<Expr> items = new ArrayList<>();
                        while (!atOp("]")) {
                            items.add(test());
                            if (atWord("for")) {
                                throw new SyntaxError("list comprehensions are not supported", peek().offset);
                            }
                            if (!atOp(",")) {
                                break;
                            }
                            next();
                        }
                        expectOp("]");
                        return new Expr.ListDisplay(items);
                    }
                    case "{" -> throw new SyntaxError("dicts and sets are not supported", t.offset);
                    default -> throw new SyntaxError("unexpected '" + t.text + "'", t.offset);
                }
            }
            default -> throw new SyntaxError("the expression is incomplete", t.offset);
        }
    }
}
