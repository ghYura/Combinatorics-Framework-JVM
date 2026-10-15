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

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The when-predicate grammar: Python precedence and literals for the constructs the legacy restricted eval could
 * evaluate, the legacy unsafe-text refusal, and refusals of what lies outside the grammar.
 */
class ExprParserTest {

    /** A fully parenthesized rendering, so precedence is visible in an assertion. */
    private static String show(Expr e) {
        return switch (e) {
            case Expr.Constant c -> c.value().display();
            case Expr.Name n -> n.name();
            case Expr.Attribute a -> show(a.target()) + "." + a.name();
            case Expr.Call c -> c.function() + "(" + String.join(", ", c.args().stream().map(ExprParserTest::show).toList()) + ")";
            case Expr.Subscript s -> show(s.target()) + "[" + show(s.index()) + "]";
            case Expr.Unary u -> "(" + switch (u.op()) {
                case NEGATE -> "-";
                case PLUS -> "+";
                case INVERT -> "~";
            } + show(u.operand()) + ")";
            case Expr.Binary b -> "(" + show(b.left()) + " " + b.op().symbol() + " " + show(b.right()) + ")";
            case Expr.Compare c -> {
                StringBuilder out = new StringBuilder("(").append(show(c.first()));
                for (int i = 0; i < c.ops().size(); i++) {
                    out.append(' ').append(c.ops().get(i).symbol()).append(' ').append(show(c.rest().get(i)));
                }
                yield out.append(')').toString();
            }
            case Expr.And a -> "(" + String.join(" and ", a.operands().stream().map(ExprParserTest::show).toList()) + ")";
            case Expr.Or o -> "(" + String.join(" or ", o.operands().stream().map(ExprParserTest::show).toList()) + ")";
            case Expr.Not n -> "(not " + show(n.operand()) + ")";
            case Expr.IfElse i -> "(" + show(i.then()) + " if " + show(i.test()) + " else " + show(i.otherwise()) + ")";
            case Expr.ListDisplay l -> "[" + String.join(", ", l.items().stream().map(ExprParserTest::show).toList()) + "]";
            case Expr.TupleDisplay t -> "(" + String.join(", ", t.items().stream().map(ExprParserTest::show).toList()) + ",)";
        };
    }

    private static String parse(String text) {
        return show(ExprParser.parse(text));
    }

    @Test
    void theCorpusPredicatesParseWithPythonPrecedence() {
        assertEquals("((TIER.is_free == 1) and (REGION.restricted == 1))", parse("TIER.is_free == 1 and REGION.restricted == 1"));
        assertEquals("((((S1.a + S2.a) + S3.a) + S4.a) == 2)", parse("S1.a + S2.a + S3.a + S4.a == 2"));
        assertEquals("(((int((S1.a != S2.a)) + int(((S2.a != S3.a) and (S1.a != S2.a)))) + int(((S3.a != S4.a) and (S1.a != S3.a) "
                        + "and (S2.a != S3.a)))) <= CAP.n)",
                parse("int(S1.a != S2.a) + int(S2.a != S3.a and S1.a != S2.a) + int(S3.a != S4.a and S1.a != S3.a and S2.a != S3.a) <= CAP.n"));
        assertEquals("((D_MODEL.dm % N_HEADS.nh) != 0)", parse("D_MODEL.dm % N_HEADS.nh != 0"));
        assertEquals("((MODE.wait == 0) or (S1.a == 1))", parse("MODE.wait == 0 or S1.a == 1"));
        assertEquals("(U3.n <= (1 + U2.n))", parse("U3.n <= 1 + U2.n"));
        assertEquals("((RESOURCE.sensitivity >= 3) and (ROLE.value == _role = \"admin\";))",
                parse("RESOURCE.sensitivity >= 3 and ROLE.value == '_role = \"admin\";'"));
    }

    @Test
    void operatorsBindAsInPython() {
        assertEquals("(-(2 ** 2))", parse("-2 ** 2"));
        assertEquals("(2 ** (-1))", parse("2 ** -1"));
        assertEquals("(2 ** (3 ** 2))", parse("2 ** 3 ** 2"));
        assertEquals("((not (a.x == 1)) or (b.y and c.z))".replace("(b.y and c.z)", "(b.y and c.z)"),
                parse("not a.x == 1 or b.y and c.z"));
        assertEquals("(1 < a.x <= 3)", parse("1 < a.x <= 3"));
        assertEquals("(a.x not in [1, 2])", parse("a.x not in [1, 2]"));
        assertEquals("(a.x is not null)", parse("a.x is not None"));
        assertEquals("(((1 + 2) * 3) // 4)", parse("(1 + 2) * 3 // 4"));
        assertEquals("((a.x | (b.y & c.z)) ^ 1)".replace("((a.x | (b.y & c.z)) ^ 1)", "(a.x | ((b.y & c.z) ^ 1))"),
                parse("a.x | b.y & c.z ^ 1"));
        assertEquals("(1 if a.x else 2)", parse("1 if a.x else 2"));
        assertEquals("(1, 2,)", parse("(1, 2)"));
        assertEquals("a.tags[0]", parse("a.tags[0]"));
        assertEquals("max(a.x, b.y, 3)", parse("max(a.x, b.y, 3)"));
    }

    @Test
    void literalsFollowPython() {
        assertEquals(new RuleValue.Int(BigInteger.valueOf(255)), ((Expr.Constant) ExprParser.parse("0xFF")).value());
        assertEquals(new RuleValue.Int(BigInteger.valueOf(8)), ((Expr.Constant) ExprParser.parse("0o10")).value());
        assertEquals(new RuleValue.Int(BigInteger.valueOf(5)), ((Expr.Constant) ExprParser.parse("0b101")).value());
        assertEquals(new RuleValue.Int(BigInteger.valueOf(1_000_000)), ((Expr.Constant) ExprParser.parse("1_000_000")).value());
        assertEquals(new RuleValue.Int(new BigInteger("123456789012345678901234567890")),
                ((Expr.Constant) ExprParser.parse("123456789012345678901234567890")).value(), "Python ints have no size limit");
        assertEquals(new RuleValue.Real(0.5), ((Expr.Constant) ExprParser.parse(".5")).value());
        assertEquals(new RuleValue.Real(1e10), ((Expr.Constant) ExprParser.parse("1e10")).value());
        assertEquals(new RuleValue.Real(5.0), ((Expr.Constant) ExprParser.parse("5.")).value());
        assertEquals(new RuleValue.Real(Double.POSITIVE_INFINITY), ((Expr.Constant) ExprParser.parse("1e400")).value());
        assertEquals(new RuleValue.Text("a\nbé\t'x'"), ((Expr.Constant) ExprParser.parse("'a\\nb\\xe9\\t\\'x\\''")).value());
        assertEquals(new RuleValue.Text("a\\nb"), ((Expr.Constant) ExprParser.parse("r'a\\nb'")).value());
        assertEquals(new RuleValue.Text("ab"), ((Expr.Constant) ExprParser.parse("'a' \"b\"")).value(), "adjacent literals join");
        assertEquals(new RuleValue.Text("xé"), ((Expr.Constant) ExprParser.parse("'x\\N{LATIN SMALL LETTER E WITH ACUTE}'")).value());
        assertEquals(new RuleValue.Text("A"), ((Expr.Constant) ExprParser.parse("'\\101'")).value());
        assertEquals(new RuleValue.Text("\\d"), ((Expr.Constant) ExprParser.parse("'\\d'")).value(), "an unknown escape keeps its backslash");
        assertEquals(new RuleValue.Text("line1\nline2"), ((Expr.Constant) ExprParser.parse("'''line1\nline2'''")).value());
        assertInstanceOf(Expr.Constant.class, ExprParser.parse("  True  "));
        assertEquals("(1 + 2)", parse("(1 +\n 2)"), "line breaks inside brackets are white space");
        assertEquals("(1 + 2)", parse("1 + \\\n2"), "a backslash continues the line");
        assertEquals("1", parse("1 # a comment"));
    }

    @Test
    void theLegacyUnsafeTextAndEverythingOutsideTheGrammarIsRefused() {
        for (String unsafe : List.of("a.__class__", "__import__('os')", "lambda: 1", "(x := 1)", "exec('1')", "eval('1')",
                "open('f')", "globals()", "locals()", "'import' == a.x")) {
            ExprParser.SyntaxError e = assertThrows(ExprParser.SyntaxError.class, () -> ExprParser.parse(unsafe), unsafe);
            assertTrue(e.getMessage().startsWith("unsafe expression rejected"), unsafe + ": " + e.getMessage());
        }
        for (String bad : List.of("", "1 +", "a.x ==", "a.f(1)", "a.x[1:2]", "f(x=1)", "[x for x in a]", "{1: 2}", "1j", "b'x'",
                "f'x'", "01", "1__0", "0x", "'open", "1\n+ 2", "a.x = 1", "a @ b", "a.", "not", "a if b", "x.if", "1abc")) {
            assertThrows(ExprParser.SyntaxError.class, () -> ExprParser.parse(bad), "'" + bad + "'");
        }
    }

    @Test
    void namesAndAttributesAreCollectedForValidation() {
        Expr e = ExprParser.parse("int(A.charge * C.charge > 0) + len(A.tags) >= abs(B.x)");
        assertEquals(List.of("int", "A", "C", "len", "abs", "B"), e.names());
        assertEquals(List.of("charge", "tags"), e.attributesOf("A"));
        assertFalse(e.attributesOf("Z").iterator().hasNext());
    }
}
