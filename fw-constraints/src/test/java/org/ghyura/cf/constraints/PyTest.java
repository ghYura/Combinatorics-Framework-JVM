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

package org.ghyura.cf.constraints;

import org.ghyura.cf.model.rule.ExprParser;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Python semantics worked out by hand from the language reference (independent of the golden file). */
class PyTest {

    private static Py.Value eval(String expr) {
        return PredicateEvaluator.evaluate(ExprParser.parse(expr), Map.of());
    }

    private static Py.Value i(long v) {
        return new Py.Int(BigInteger.valueOf(v));
    }

    @Test
    void integerDivisionFloorsAndModuloTakesTheDivisorsSign() {
        assertEquals(i(-4), eval("-7 // 2"));
        assertEquals(i(1), eval("-7 % 2"));
        assertEquals(i(-1), eval("7 % -2"));
        assertEquals(i(3), eval("7 // 2"));
        assertEquals(new Py.Float(-4.0), eval("-7.0 // 2"));
        assertEquals(new Py.Float(1.0), eval("-7.0 % 2"));
        assertEquals(new Py.Float(0.5), eval("1 / 2"));
        assertEquals(new Py.Int(new BigInteger("1267650600228229401496703205376")), eval("2 ** 100"));
        assertEquals(new Py.Float(0.5), eval("2 ** -1"));
        assertEquals(i(-8), eval("(-2) ** 3"));
        assertEquals(i(1), eval("(-1) ** 1000000000001 + 2"), "-1 to an odd power, exactly");
        assertEquals("ZeroDivisionError", assertThrows(Py.Failure.class, () -> eval("1 // 0")).type());
        assertEquals("ZeroDivisionError", assertThrows(Py.Failure.class, () -> eval("1.5 % 0")).type());
    }

    @Test
    void boolsAreIntsAndAndOrReturnOperands() {
        assertEquals(i(2), eval("True + True"));
        assertEquals(Py.Bool.TRUE, eval("1 == 1.0 == True"));
        assertEquals(i(0), eval("0 and 5"));
        assertEquals(new Py.Str("x"), eval("'' or 'x'"));
        assertEquals(Py.Bool.TRUE, eval("not ''"));
        assertEquals(Py.Bool.FALSE, eval("1 < 2 < 2"), "a chain is the conjunction of its links");
        assertEquals(new Py.Str("ababab"), eval("'ab' * 3"));
        assertEquals(Py.Bool.TRUE, eval("'b' in 'abc'"));
        assertEquals(Py.Bool.TRUE, eval("2 in [1, 2.0]"));
        assertEquals(Py.Bool.FALSE, eval("'2' in [1, 2]"));
        assertEquals("TypeError", assertThrows(Py.Failure.class, () -> eval("1 < 'a'")).type());
        assertEquals("TypeError", assertThrows(Py.Failure.class, () -> eval("1 in 'a'")).type());
        assertEquals(Py.Bool.TRUE, eval("None is None"));
    }

    @Test
    void conversionsAndRoundingFollowPython() {
        assertEquals(i(2), eval("round(2.5)"), "half to even");
        assertEquals(i(4), eval("round(3.5)"));
        assertEquals(new Py.Float(2.67), eval("round(2.675, 2)"), "the binary value of 2.675 is below the half");
        assertEquals(i(-10), eval("int('  -1_0 ')"));
        assertEquals(i(-2), eval("int(-2.9)"), "toward zero");
        assertEquals(new Py.Float(Double.POSITIVE_INFINITY), eval("float(' Infinity ')"));
        assertEquals(new Py.Float(1000.5), eval("float('1_000.5')"));
        assertEquals("ValueError", assertThrows(Py.Failure.class, () -> eval("int('1.5')")).type());
        assertEquals("ValueError", assertThrows(Py.Failure.class, () -> eval("int(float('nan'))")).type());
        assertEquals(i(3), eval("len('aé😀')"), "len counts code points");
        assertEquals(i(1), eval("min(3, 1, 2)"));
        assertEquals(new Py.Str("c"), eval("max('abc')"));
        assertEquals(new Py.Int(BigInteger.valueOf(5)), eval("abs(-5)"));
        assertEquals("ValueError", assertThrows(Py.Failure.class, () -> eval("min([])")).type());
        assertEquals("NameError", assertThrows(Py.Failure.class, () -> eval("nope")).type());
    }
}
