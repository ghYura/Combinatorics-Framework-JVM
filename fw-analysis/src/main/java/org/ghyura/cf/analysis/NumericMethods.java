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

import java.util.Arrays;
import java.util.Random;
import java.util.function.DoubleUnaryOperator;

/**
 * The legacy analyzer's numerical methods (Optimizers), pure Java and deterministic: Brent minimisation, damped
 * Newton-Raphson, gradient descent with momentum on central differences, Nelder-Mead, simulated annealing with a seeded
 * java.util.Random, composite Simpson and trapezoid rules, Simpson over samples and classical fourth-order Runge-Kutta. The
 * algorithms and their constants are the legacy ones; every result carries its iteration count and whether it converged,
 * and an objective that returns NaN or an infinity stops the method with ANALYSIS_NONFINITE instead of steering it.
 */
public final class NumericMethods {

    private NumericMethods() {
    }

    /** An objective of several variables (the typed objective callback). */
    @FunctionalInterface
    public interface Objective {
        double value(double[] x);
    }

    /** The right-hand side of y' = f(t, y). */
    @FunctionalInterface
    public interface VectorField {
        double[] derivative(double t, double[] y);
    }

    public record Min1D(double x, double fx, int iterations, boolean converged) {
    }

    public record MinND(double[] x, double fx, int iterations, boolean converged) {
        public MinND {
            x = x.clone();
        }

        @Override
        public double[] x() {
            return x.clone();
        }
    }

    public record Root(double x, double fx, int iterations, boolean converged) {
    }

    /** Brent's method on [a, b] (golden section with parabolic steps). */
    public static Min1D brent(DoubleUnaryOperator f, double a, double b, double tol, int maxIter) {
        if (a > b) {
            double t = a;
            a = b;
            b = t;
        }
        final double golden = 0.3819660112501051;
        double x = a + golden * (b - a);
        double w = x;
        double v = x;
        double fx = eval(f, x);
        double fw = fx;
        double fv = fx;
        double d = 0;
        double e = 0;
        for (int iter = 0; iter < maxIter; iter++) {
            double xm = 0.5 * (a + b);
            double tol1 = tol * Math.abs(x) + 1e-12;
            double tol2 = 2.0 * tol1;
            if (Math.abs(x - xm) <= tol2 - 0.5 * (b - a)) {
                return new Min1D(x, fx, iter, true);
            }
            if (Math.abs(e) > tol1) {
                double r = (x - w) * (fx - fv);
                double q = (x - v) * (fx - fw);
                double p = (x - v) * q - (x - w) * r;
                q = 2 * (q - r);
                if (q > 0) {
                    p = -p;
                }
                q = Math.abs(q);
                double etemp = e;
                e = d;
                if (Math.abs(p) >= Math.abs(0.5 * q * etemp) || p <= q * (a - x) || p >= q * (b - x)) {
                    e = x >= xm ? a - x : b - x;
                    d = golden * e;
                } else {
                    d = p / q;
                    double u = x + d;
                    if (u - a < tol2 || b - u < tol2) {
                        d = Math.copySign(tol1, xm - x);
                    }
                }
            } else {
                e = x >= xm ? a - x : b - x;
                d = golden * e;
            }
            double u = Math.abs(d) >= tol1 ? x + d : x + Math.copySign(tol1, d);
            double fu = eval(f, u);
            if (fu <= fx) {
                if (u >= x) {
                    a = x;
                } else {
                    b = x;
                }
                v = w;
                fv = fw;
                w = x;
                fw = fx;
                x = u;
                fx = fu;
            } else {
                if (u < x) {
                    a = u;
                } else {
                    b = u;
                }
                if (fu <= fw || w == x) {
                    v = w;
                    fv = fw;
                    w = u;
                    fw = fu;
                } else if (fu <= fv || v == x || v == w) {
                    v = u;
                    fv = fu;
                }
            }
        }
        return new Min1D(x, fx, maxIter, false);
    }

    /** Newton-Raphson from x0 with the legacy damping (up to 12 halvings when |f| grows). */
    public static Root newton(DoubleUnaryOperator f, DoubleUnaryOperator fPrime, double x0, double tol, int maxIter) {
        double x = x0;
        for (int iter = 0; iter < maxIter; iter++) {
            double fx = eval(f, x);
            if (Math.abs(fx) < tol) {
                return new Root(x, fx, iter, true);
            }
            double dfx = eval(fPrime, x);
            if (Math.abs(dfx) < 1e-14) {
                return new Root(x, fx, iter, false);
            }
            double step = fx / dfx;
            double next = x - step;
            double fNext = eval(f, next);
            int trim = 0;
            while (Math.abs(fNext) > Math.abs(fx) && trim < 12) {
                step *= 0.5;
                next = x - step;
                fNext = eval(f, next);
                trim++;
            }
            x = next;
        }
        double fx = eval(f, x);
        return new Root(x, fx, maxIter, Math.abs(fx) < tol);
    }

    /** Gradient descent with momentum on central-difference gradients; converged when the gradient norm is below tol. */
    public static MinND gradientDescent(Objective f, double[] x0, double lr, double momentum, int maxIter, double tol) {
        int n = x0.length;
        double[] x = x0.clone();
        double[] velocity = new double[n];
        double[] grad = new double[n];
        double fx = eval(f, x);
        for (int iter = 0; iter < maxIter; iter++) {
            finiteDifferenceGradient(f, x, grad);
            double norm = 0;
            for (double g : grad) {
                norm += g * g;
            }
            if (Math.sqrt(norm) < tol) {
                return new MinND(x, fx, iter, true);
            }
            for (int i = 0; i < n; i++) {
                velocity[i] = momentum * velocity[i] - lr * grad[i];
                x[i] += velocity[i];
            }
            fx = eval(f, x);
        }
        return new MinND(x, fx, maxIter, false);
    }

    /** Symmetric central differences with h = 1e-6 max(1, |x_i|). */
    public static void finiteDifferenceGradient(Objective f, double[] x, double[] grad) {
        for (int i = 0; i < x.length; i++) {
            double saved = x[i];
            double h = 1e-6 * Math.max(1.0, Math.abs(saved));
            x[i] = saved + h;
            double fp = eval(f, x);
            x[i] = saved - h;
            double fm = eval(f, x);
            x[i] = saved;
            grad[i] = (fp - fm) / (2 * h);
        }
    }

    /** Nelder-Mead with reflection 1, expansion 2, contraction 0.5 and shrink 0.5; converged when the spread is below tol. */
    public static MinND nelderMead(Objective f, double[] x0, double initialStep, int maxIter, double tol) {
        int n = x0.length;
        double[][] simplex = new double[n + 1][];
        double[] fvals = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            simplex[i] = x0.clone();
            if (i > 0) {
                simplex[i][i - 1] += initialStep;
            }
            fvals[i] = eval(f, simplex[i]);
        }
        for (int iter = 0; iter < maxIter; iter++) {
            for (int i = 1; i <= n; i++) {
                for (int j = i; j > 0 && fvals[j - 1] > fvals[j]; j--) {
                    double tf = fvals[j];
                    fvals[j] = fvals[j - 1];
                    fvals[j - 1] = tf;
                    double[] ts = simplex[j];
                    simplex[j] = simplex[j - 1];
                    simplex[j - 1] = ts;
                }
            }
            if (fvals[n] - fvals[0] < tol) {
                return new MinND(simplex[0], fvals[0], iter, true);
            }
            double[] xo = new double[n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    xo[j] += simplex[i][j];
                }
            }
            for (int j = 0; j < n; j++) {
                xo[j] /= n;
            }
            double[] xr = new double[n];
            for (int j = 0; j < n; j++) {
                xr[j] = xo[j] + (xo[j] - simplex[n][j]);
            }
            double fr = eval(f, xr);
            if (fvals[0] <= fr && fr < fvals[n - 1]) {
                simplex[n] = xr;
                fvals[n] = fr;
                continue;
            }
            if (fr < fvals[0]) {
                double[] xe = new double[n];
                for (int j = 0; j < n; j++) {
                    xe[j] = xo[j] + 2.0 * (xr[j] - xo[j]);
                }
                double fe = eval(f, xe);
                if (fe < fr) {
                    simplex[n] = xe;
                    fvals[n] = fe;
                } else {
                    simplex[n] = xr;
                    fvals[n] = fr;
                }
                continue;
            }
            double[] xc = new double[n];
            for (int j = 0; j < n; j++) {
                xc[j] = xo[j] + 0.5 * (simplex[n][j] - xo[j]);
            }
            double fc = eval(f, xc);
            if (fc < fvals[n]) {
                simplex[n] = xc;
                fvals[n] = fc;
                continue;
            }
            for (int i = 1; i <= n; i++) {
                for (int j = 0; j < n; j++) {
                    simplex[i][j] = simplex[0][j] + 0.5 * (simplex[i][j] - simplex[0][j]);
                }
                fvals[i] = eval(f, simplex[i]);
            }
        }
        return new MinND(simplex[0], fvals[0], maxIter, false);
    }

    /**
     * Simulated annealing seeded with java.util.Random(seed), as the legacy did: the same seed gives the same walk. It
     * reports the best point seen; it never claims convergence it did not test, so converged is false.
     */
    public static MinND simulatedAnnealing(Objective f, double[] x0, double[] stepSize, double temp0, double cooling, int maxIter,
                                           long seed) {
        Random rng = new Random(seed);
        double[] x = x0.clone();
        double[] best = x.clone();
        double fx = eval(f, x);
        double fbest = fx;
        double t = temp0;
        for (int iter = 0; iter < maxIter; iter++) {
            double[] xn = x.clone();
            for (int i = 0; i < x.length; i++) {
                xn[i] += (rng.nextDouble() * 2 - 1) * stepSize[i];
            }
            double fn = eval(f, xn);
            double df = fn - fx;
            if (df < 0 || rng.nextDouble() < Math.exp(-df / Math.max(1e-12, t))) {
                x = xn;
                fx = fn;
                if (fx < fbest) {
                    fbest = fx;
                    best = x.clone();
                }
            }
            t *= cooling;
        }
        return new MinND(best, fbest, maxIter, false);
    }

    /** Composite Simpson with n intervals (raised to at least 2 and to an even number). */
    public static double simpson(DoubleUnaryOperator f, double a, double b, int n) {
        int m = Math.max(2, n);
        if (m % 2 != 0) {
            m++;
        }
        double h = (b - a) / m;
        double s = eval(f, a) + eval(f, b);
        for (int i = 1; i < m; i++) {
            s += (i % 2 == 0 ? 2 : 4) * eval(f, a + i * h);
        }
        return s * h / 3.0;
    }

    /** Composite trapezoid rule with n intervals (at least 1). */
    public static double trapezoid(DoubleUnaryOperator f, double a, double b, int n) {
        int m = Math.max(1, n);
        double h = (b - a) / m;
        double s = 0.5 * (eval(f, a) + eval(f, b));
        for (int i = 1; i < m; i++) {
            s += eval(f, a + i * h);
        }
        return s * h;
    }

    /** Composite Simpson over evenly spaced samples; an odd number of intervals ends with one trapezoid. */
    public static double simpsonSamples(double[] values, double dx) {
        int len = values.length;
        if (len == 0) {
            return 0;
        }
        for (double v : values) {
            finite(v);
        }
        if (len == 1) {
            return values[0] * dx;
        }
        int n = len - 1;
        int even = n % 2 == 0 ? n : n - 1;
        double s = values[0] + values[even];
        for (int i = 1; i < even; i++) {
            s += (i % 2 == 0 ? 2 : 4) * values[i];
        }
        double tail = even < n ? 0.5 * (values[even] + values[even + 1]) * dx : 0;
        return s * dx / 3.0 + tail;
    }

    /** Classical fourth-order Runge-Kutta from t0 to tf with step dt (the last step shortened to land on tf). */
    public static double[] rk4(VectorField f, double[] y0, double t0, double tf, double dt) {
        if (!(dt > 0) || !Double.isFinite(dt)) {
            throw new IllegalArgumentException("the step must be finite and positive: " + dt);
        }
        int n = y0.length;
        double[] y = y0.clone();
        double t = t0;
        while (t < tf) {
            double h = Math.min(dt, tf - t);
            double[] k1 = finite(f.derivative(t, y));
            double[] k2 = finite(f.derivative(t + h / 2, addScaled(y, k1, h / 2)));
            double[] k3 = finite(f.derivative(t + h / 2, addScaled(y, k2, h / 2)));
            double[] k4 = finite(f.derivative(t + h, addScaled(y, k3, h)));
            for (int i = 0; i < n; i++) {
                y[i] += h / 6.0 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
            }
            t += h;
        }
        return y;
    }

    private static double[] addScaled(double[] a, double[] b, double s) {
        double[] r = new double[a.length];
        for (int i = 0; i < a.length; i++) {
            r[i] = a[i] + s * b[i];
        }
        return r;
    }

    private static double eval(DoubleUnaryOperator f, double x) {
        return finite(f.applyAsDouble(x));
    }

    private static double eval(Objective f, double[] x) {
        return finite(f.value(x));
    }

    private static double finite(double v) {
        if (!Double.isFinite(v)) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.ANALYSIS_NONFINITE, "the objective returned " + v
                    + "; a numerical method does not steer by NaN or an infinity", SourceLocation.NONE));
        }
        return v;
    }

    private static double[] finite(double[] v) {
        for (double d : v) {
            finite(d);
        }
        return Arrays.copyOf(v, v.length);
    }
}
