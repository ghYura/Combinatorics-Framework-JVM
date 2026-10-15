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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.DoubleUnaryOperator;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A12: the numerical methods against known solutions with declared tolerances (closed-form minima and roots,
 * Rosenbrock, exact integrals, polynomial exactness of Simpson's rule, exponential and harmonic ODE solutions), seeded
 * determinism, and explicit failures for nonfinite objectives and invalid expressions.
 */
class NumericMethodsTest {

    @Test
    void minimaAndRootsMatchTheirClosedForms() {
        DoubleUnaryOperator parabola = Expression.function("(x-7)^2 + 3", "x");
        NumericMethods.Min1D m = NumericMethods.brent(parabola, 0, 10, 1e-9, 200);
        assertTrue(m.converged());
        assertEquals(7.0, m.x(), 1e-6);
        assertEquals(3.0, m.fx(), 1e-12);
        assertEquals(Math.PI, NumericMethods.brent(Math::cos, 0, 2 * Math.PI, 1e-10, 200).x(), 1e-6, "cos has its minimum at pi");
        NumericMethods.Root r = NumericMethods.newton(x -> x * x - 2, x -> 2 * x, 1, 1e-14, 50);
        assertTrue(r.converged());
        assertEquals(Math.sqrt(2), r.x(), 1e-12);
        assertFalse(NumericMethods.newton(x -> x * x + 1, x -> 2 * x, 0, 1e-12, 50).converged(), "no real root, a flat start");
        NumericMethods.MinND g = NumericMethods.gradientDescent(x -> (x[0] - 1) * (x[0] - 1) + (x[1] + 2) * (x[1] + 2), new double[] {0, 0},
                0.1, 0.5, 5000, 1e-8);
        assertTrue(g.converged());
        assertArrayEquals(new double[] {1, -2}, g.x(), 1e-6);
        NumericMethods.MinND nm = NumericMethods.nelderMead(x -> 100 * Math.pow(x[1] - x[0] * x[0], 2) + Math.pow(1 - x[0], 2),
                new double[] {-1.2, 1}, 0.5, 10_000, 1e-14);
        assertTrue(nm.converged());
        assertArrayEquals(new double[] {1, 1}, nm.x(), 1e-3, "Rosenbrock's minimum");
        NumericMethods.Objective bowl = x -> (x[0] - 3) * (x[0] - 3) + (x[1] + 1) * (x[1] + 1);
        NumericMethods.MinND a = NumericMethods.simulatedAnnealing(bowl, new double[] {0, 0}, new double[] {0.5, 0.5}, 1.0, 0.995, 20_000, 42L);
        assertArrayEquals(new double[] {3, -1}, a.x(), 0.15);
        assertArrayEquals(a.x(), NumericMethods.simulatedAnnealing(bowl, new double[] {0, 0}, new double[] {0.5, 0.5}, 1.0, 0.995, 20_000, 42L)
                .x(), 0.0, "the same seed walks the same way");
        assertFalse(a.converged(), "annealing never claims convergence it did not test");
    }

    @Test
    void integralsAndOdeSolutionsMatchTheirReferences() {
        assertEquals(2.0, NumericMethods.simpson(Math::sin, 0, Math.PI, 200), 1e-8);
        assertEquals(4.0, NumericMethods.simpson(x -> x * x * x, 0, 2, 2), 1e-12, "Simpson is exact for cubics");
        assertEquals(1.0 / 3, NumericMethods.trapezoid(x -> x * x, 0, 1, 1000), 1e-6);
        double[] squares = new double[11];
        for (int i = 0; i < squares.length; i++) {
            squares[i] = (i * 0.1) * (i * 0.1);
        }
        assertEquals(1.0 / 3, NumericMethods.simpsonSamples(squares, 0.1), 1e-12, "even intervals: Simpson, exact for a parabola");
        double[] odd = {0, 1, 4, 9};
        assertEquals(1.0 / 3 * (0 + 4 * 1 + 4) + 0.5 * (4 + 9), NumericMethods.simpsonSamples(odd, 1), 1e-12, "a trapezoid ends odd counts");
        assertEquals(Math.exp(-1), NumericMethods.rk4((t, y) -> new double[] {-y[0]}, new double[] {1}, 0, 1, 0.01)[0], 1e-10);
        double[] oscillator = NumericMethods.rk4((t, y) -> new double[] {y[1], -y[0]}, new double[] {1, 0}, 0, 1, 0.001);
        assertArrayEquals(new double[] {Math.cos(1), -Math.sin(1)}, oscillator, 1e-10);
    }

    @Test
    void nonfiniteObjectivesAndInvalidExpressionsAreExplicitFailures() {
        FrameworkException nan = assertThrows(FrameworkException.class, () -> NumericMethods.brent(Math::sqrt, -1, 1, 1e-9, 100));
        assertEquals(ErrorCode.ANALYSIS_NONFINITE, nan.diagnostics().getFirst().code());
        FrameworkException inf = assertThrows(FrameworkException.class,
                () -> NumericMethods.nelderMead(x -> 1 / x[0], new double[] {0}, 1, 10, 1e-9));
        assertEquals(ErrorCode.ANALYSIS_NONFINITE, inf.diagnostics().getFirst().code());
        assertEquals(4.0, Expression.compile("-2^2", List.of()).evaluate(), "the legacy grammar: a leading minus binds to its operand");
        assertEquals(512.0, Expression.compile("2^3^2", List.of()).evaluate(), "right associative");
        assertEquals(1.0, Expression.compile("sin(pi/2) * abs(-e)/e", List.of()).evaluate(), 1e-15);
        assertEquals(7.0, Expression.compile("a*b + c", List.of("a", "b", "c")).evaluate(2, 3, 1));
        for (String bad : List.of("", "x +", "foo(x)", "y * 2", "(x", "2 $ 3", "import scipy")) {
            FrameworkException e = assertThrows(FrameworkException.class, () -> Expression.compile(bad, List.of("x")), bad);
            assertEquals(ErrorCode.ANALYSIS_INVALID_EXPRESSION, e.diagnostics().getFirst().code(), bad);
        }
    }
}
