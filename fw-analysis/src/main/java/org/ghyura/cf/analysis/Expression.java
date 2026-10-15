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

package org.ghyura.cf.analysis;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.util.List;
import java.util.Set;
import java.util.function.DoubleUnaryOperator;

/**
 * An objective expression (port of the legacy ExprParser): numbers, the declared variables, + - * / and ^ (right
 * associative, tighter than unary minus on its left operand only as in the legacy grammar), unary signs, parentheses, the
 * constants pi and e, and sin, cos, tan, exp, log, log10, abs, sqrt, sign. Unlike the legacy parser, an unknown variable or
 * function is refused when the expression is compiled (ANALYSIS_INVALID_EXPRESSION), not when it is evaluated, and no
 * other language (Python, scipy) is accepted.
 */
public final class Expression {

    private static final Set<String> FUNCTIONS = Set.of("sin", "cos", "tan", "exp", "log", "log10", "abs", "sqrt", "sign");

    private interface Node {
        double eval(double[] values);
    }

    private final String src;
    private final List<String> variables;
    private final Node root;
    private int pos;

    private Expression(String src, List<String> variables) {
        this.src = src;
        this.variables = List.copyOf(variables);
        this.root = parseAll();
    }

    /** @throws FrameworkException ANALYSIS_INVALID_EXPRESSION */
    public static Expression compile(String source, List<String> variables) {
        if (source == null || source.isBlank()) {
            throw invalid("an objective expression is empty", source == null ? "" : source);
        }
        return new Expression(source, variables);
    }

    /** A one-variable expression as a function. */
    public static DoubleUnaryOperator function(String source, String variable) {
        Expression e = compile(source, List.of(variable));
        return x -> e.evaluate(x);
    }

    public List<String> variables() {
        return variables;
    }

    /** The value at the given variable values, in the declared order. */
    public double evaluate(double... values) {
        if (values.length != variables.size()) {
            throw new IllegalArgumentException("expected " + variables.size() + " values, got " + values.length);
        }
        return root.eval(values);
    }

    private Node parseAll() {
        pos = 0;
        Node node = parseExpr();
        skipWhitespace();
        if (pos < src.length()) {
            throw invalid("unexpected '" + src.charAt(pos) + "' at position " + pos, src);
        }
        return node;
    }

    private Node parseExpr() {
        Node left = parseTerm();
        skipWhitespace();
        while (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
            char op = src.charAt(pos++);
            Node l = left;
            Node r = parseTerm();
            left = op == '+' ? v -> l.eval(v) + r.eval(v) : v -> l.eval(v) - r.eval(v);
            skipWhitespace();
        }
        return left;
    }

    private Node parseTerm() {
        Node left = parseFactor();
        skipWhitespace();
        while (pos < src.length() && (src.charAt(pos) == '*' || src.charAt(pos) == '/')) {
            char op = src.charAt(pos++);
            Node l = left;
            Node r = parseFactor();
            left = op == '*' ? v -> l.eval(v) * r.eval(v) : v -> l.eval(v) / r.eval(v);
            skipWhitespace();
        }
        return left;
    }

    private Node parseFactor() {
        Node left = parseUnary();
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '^') {
            pos++;
            Node r = parseFactor();
            return v -> Math.pow(left.eval(v), r.eval(v));
        }
        return left;
    }

    private Node parseUnary() {
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '-') {
            pos++;
            Node inner = parseUnary();
            return v -> -inner.eval(v);
        }
        if (pos < src.length() && src.charAt(pos) == '+') {
            pos++;
            return parseUnary();
        }
        return parseAtom();
    }

    private Node parseAtom() {
        skipWhitespace();
        if (pos >= src.length()) {
            throw invalid("unexpected end of the expression", src);
        }
        char c = src.charAt(pos);
        if (c == '(') {
            pos++;
            Node inner = parseExpr();
            expectClose();
            return inner;
        }
        if (Character.isDigit(c) || c == '.') {
            return parseNumber();
        }
        if (Character.isLetter(c) || c == '_') {
            return parseIdent();
        }
        throw invalid("unexpected '" + c + "' at position " + pos, src);
    }

    private Node parseNumber() {
        int start = pos;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
            pos++;
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
        }
        double value;
        try {
            value = Double.parseDouble(src.substring(start, pos));
        } catch (NumberFormatException e) {
            throw invalid("'" + src.substring(start, pos) + "' is not a number", src);
        }
        return v -> value;
    }

    private Node parseIdent() {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        String name = src.substring(start, pos);
        int index = variables.indexOf(name);
        if (index < 0 && name.equalsIgnoreCase("pi")) {
            return v -> Math.PI;
        }
        if (index < 0 && name.equalsIgnoreCase("e") && (pos >= src.length() || src.charAt(pos) != '(')) {
            return v -> Math.E;
        }
        skipWhitespace();
        if (pos < src.length() && src.charAt(pos) == '(') {
            if (!FUNCTIONS.contains(name)) {
                throw invalid("unknown function '" + name + "' (known: " + String.join(", ", FUNCTIONS.stream().sorted().toList()) + ")",
                        src);
            }
            pos++;
            Node arg = parseExpr();
            expectClose();
            return switch (name) {
                case "sin" -> v -> Math.sin(arg.eval(v));
                case "cos" -> v -> Math.cos(arg.eval(v));
                case "tan" -> v -> Math.tan(arg.eval(v));
                case "exp" -> v -> Math.exp(arg.eval(v));
                case "log" -> v -> Math.log(arg.eval(v));
                case "log10" -> v -> Math.log10(arg.eval(v));
                case "abs" -> v -> Math.abs(arg.eval(v));
                case "sqrt" -> v -> Math.sqrt(arg.eval(v));
                default -> v -> Math.signum(arg.eval(v));
            };
        }
        if (index < 0) {
            throw invalid("unknown variable '" + name + "' (declared: " + variables + ")", src);
        }
        return v -> v[index];
    }

    private void expectClose() {
        skipWhitespace();
        if (pos >= src.length() || src.charAt(pos) != ')') {
            throw invalid("expected ')' at position " + pos, src);
        }
        pos++;
    }

    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
            pos++;
        }
    }

    private static FrameworkException invalid(String why, String source) {
        return new FrameworkException(Diagnostic.error(ErrorCode.ANALYSIS_INVALID_EXPRESSION, "objective '" + source + "': " + why,
                SourceLocation.NONE));
    }
}
