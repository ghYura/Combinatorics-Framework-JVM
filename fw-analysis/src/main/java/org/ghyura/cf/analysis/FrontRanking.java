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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.store.MemorySizes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The legacy front rankers (NsgaII, Spea2, MoeaD, NsgaIII). Every one ranks by fast non-dominated sort, so rank 1 is the
 * exact Pareto front whatever the algorithm; they differ in the diagnostics they attach: NSGA-II crowding distance, SPEA2
 * strength, raw fitness, k-th nearest distance and density, MOEA/D the best Tchebycheff value over Das-Dennis weights, and
 * NSGA-III the associated reference point after normalisation. These rank existing candidates; they are not an
 * evolutionary generator (that is the campaign's job).
 */
public final class FrontRanking {

    private FrontRanking() {
    }

    /** Temporary ranking storage; input vectors and the analysis report have their own admission. */
    static long workingBytes(RunConfig.FrontAlgorithm algorithm, int points, int goals, boolean frontOnly) {
        if (points == 0) return 0;
        // Front/index lists, diagnostics, sorting arrays and translated vectors.
        long bytes = MemorySizes.add(1024, MemorySizes.multiply(points,
                MemorySizes.add(1024, MemorySizes.multiply(24, goals))));
        if (!frontOnly) {
            // At most one directed dominance edge per pair, stored as boxed indexes in growable lists.
            // The fronts and SPEA2 phases each build such a graph; they are not simultaneously retained.
            long pairs = MemorySizes.multiply(points, points - 1L) / 2;
            bytes = MemorySizes.add(bytes, MemorySizes.multiply(40, pairs));
        }
        if (goals > 0 && (algorithm == RunConfig.FrontAlgorithm.NSGA_III || algorithm == RunConfig.FrontAlgorithm.MOEA_D)) {
            int divisions = defaultDivisions(goals);
            // Stars and bars: C(goals + divisions - 1, divisions), counted without generating the directions.
            long references = 1;
            for (int i = 1; i <= divisions; i++) references = MemorySizes.multiply(references, goals + (long) i - 1) / i;
            bytes = MemorySizes.add(bytes, MemorySizes.multiply(references, MemorySizes.add(64, MemorySizes.multiply(8, goals))));
            // Reference-generation state, normalization scratch and the NSGA-III intercept/solve matrices.
            bytes = MemorySizes.add(bytes, MemorySizes.multiply(64, goals));
            if (algorithm == RunConfig.FrontAlgorithm.NSGA_III)
                bytes = MemorySizes.add(bytes, MemorySizes.multiply(16, MemorySizes.multiply(goals, goals + 1L)));
        }
        return bytes;
    }

    /** One item's rank (1 = Pareto front) and its algorithm's diagnostics by name. */
    public record Rank(int index, int rank, Map<String, Double> diagnostics) {
        public Rank {
            diagnostics = Map.copyOf(diagnostics);
        }
    }

    /** Fast non-dominated sort: the fronts, each a list of indexes in input order. */
    public static List<List<Integer>> fronts(double[][] vectors, Goal[] goals) {
        int n = vectors.length;
        if (n == 0) {
            return List.of();
        }
        int[] dominationCount = new int[n];
        List<List<Integer>> dominatedBy = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dominatedBy.add(new ArrayList<>());
        }
        List<List<Integer>> fronts = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (int p = 0; p < n; p++) {
            for (int q = 0; q < n; q++) {
                if (p == q) {
                    continue;
                }
                if (Dominance.dominates(vectors[p], vectors[q], goals)) {
                    dominatedBy.get(p).add(q);
                } else if (Dominance.dominates(vectors[q], vectors[p], goals)) {
                    dominationCount[p]++;
                }
            }
            if (dominationCount[p] == 0) {
                current.add(p);
            }
        }
        int placed = 0;
        while (!current.isEmpty()) {
            fronts.add(current);
            placed += current.size();
            if (placed >= n) {
                break;
            }
            List<Integer> next = new ArrayList<>();
            for (int p : current) {
                for (int q : dominatedBy.get(p)) {
                    if (--dominationCount[q] == 0) {
                        next.add(q);
                    }
                }
            }
            current = next;
        }
        return fronts;
    }

    /** Ranks every item with the chosen algorithm's diagnostics, in input order. */
    public static List<Rank> rank(RunConfig.FrontAlgorithm algorithm, double[][] vectors, Goal[] goals) {
        int n = vectors.length;
        List<List<Integer>> fronts = fronts(vectors, goals);
        int[] rankOf = new int[n];
        Arrays.fill(rankOf, fronts.size() + 1);
        for (int r = 0; r < fronts.size(); r++) {
            for (int i : fronts.get(r)) {
                rankOf[i] = r + 1;
            }
        }
        List<Map<String, Double>> diag = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            diag.add(new LinkedHashMap<>());
        }
        switch (algorithm) {
            case PARETO -> {
            }
            case NSGA_II -> {
                for (List<Integer> front : fronts) {
                    double[] cd = crowding(front, vectors, goals);
                    for (int k = 0; k < front.size(); k++) {
                        diag.get(front.get(k)).put("crowding", cd[k]);
                    }
                }
            }
            case SPEA2 -> spea2(vectors, goals, diag);
            case MOEA_D -> moeaD(vectors, goals, diag);
            case NSGA_III -> nsgaIII(vectors, goals, diag);
        }
        List<Rank> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            out.add(new Rank(i, rankOf[i], diag.get(i)));
        }
        return out;
    }

    /** NSGA-II crowding distance of one front, boundaries infinite (legacy NsgaII.crowdingDistance). */
    static double[] crowding(List<Integer> front, double[][] vectors, Goal[] goals) {
        int m = front.size();
        double[] dist = new double[m];
        if (m <= 2) {
            Arrays.fill(dist, Double.POSITIVE_INFINITY);
            return dist;
        }
        for (int a = 0; a < goals.length; a++) {
            Goal g = goals[a];
            if (g == null) {
                continue;
            }
            int axis = a;
            Integer[] sorted = front.toArray(new Integer[0]);
            Arrays.sort(sorted, Comparator.comparingDouble(i -> {
                double v = axis < vectors[i].length ? vectors[i][axis] : Double.NaN;
                return Double.isNaN(v) ? Double.POSITIVE_INFINITY : g.deviation(v);
            }));
            double vMin = vectors[sorted[0]][axis];
            double vMax = vectors[sorted[m - 1]][axis];
            double range = Double.isNaN(vMin) || Double.isNaN(vMax) ? 0 : g.deviation(vMax) - g.deviation(vMin);
            int[] pos = new int[m];
            for (int k = 0; k < m; k++) {
                pos[k] = front.indexOf(sorted[k]);
            }
            dist[pos[0]] = Double.POSITIVE_INFINITY;
            dist[pos[m - 1]] = Double.POSITIVE_INFINITY;
            if (range <= 0) {
                continue;
            }
            for (int k = 1; k < m - 1; k++) {
                if (Double.isInfinite(dist[pos[k]])) {
                    continue;
                }
                double prev = vectors[sorted[k - 1]][axis];
                double next = vectors[sorted[k + 1]][axis];
                if (Double.isNaN(prev) || Double.isNaN(next)) {
                    continue;
                }
                dist[pos[k]] += (g.deviation(next) - g.deviation(prev)) / range;
            }
        }
        return dist;
    }

    private static void spea2(double[][] v, Goal[] goals, List<Map<String, Double>> diag) {
        int n = v.length;
        int[] strength = new int[n];
        List<List<Integer>> dominators = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            dominators.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) {
                    continue;
                }
                if (Dominance.dominates(v[i], v[j], goals)) {
                    strength[i]++;
                }
                if (Dominance.dominates(v[j], v[i], goals)) {
                    dominators.get(i).add(j);
                }
            }
        }
        int k = Math.max(1, (int) Math.sqrt(n));
        for (int i = 0; i < n; i++) {
            double raw = 0;
            for (int j : dominators.get(i)) {
                raw += strength[j];
            }
            double knn = 0;
            if (n > 1) {
                double[] d = new double[n - 1];
                int idx = 0;
                for (int j = 0; j < n; j++) {
                    if (i != j) {
                        d[idx++] = distance(v[i], v[j], goals);
                    }
                }
                Arrays.sort(d);
                knn = d[Math.max(0, Math.min(k - 1, d.length - 1))];
            }
            double density = 1.0 / (knn + 2.0);
            Map<String, Double> m = diag.get(i);
            m.put("strength", (double) strength[i]);
            m.put("rawFitness", raw);
            m.put("kNearestDistance", knn);
            m.put("density", density);
            m.put("fitness", raw + density);
        }
    }

    static double distance(double[] a, double[] b, Goal[] goals) {
        double sum = 0;
        int len = Math.min(Math.min(a.length, b.length), goals.length);
        for (int i = 0; i < len; i++) {
            if (goals[i] == null || Double.isNaN(a[i]) || Double.isNaN(b[i])) {
                continue;
            }
            double d = goals[i].deviation(a[i]) - goals[i].deviation(b[i]);
            sum += d * d;
        }
        return Math.sqrt(sum);
    }

    /** Deviations translated by the ideal point (missing values 0, as the legacy did). */
    private static double[][] translated(double[][] v, Goal[] goals) {
        int n = v.length;
        int m = goals.length;
        double[][] dev = new double[n][m];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < m; j++) {
                double x = j < v[i].length ? v[i][j] : Double.NaN;
                dev[i][j] = goals[j] == null || Double.isNaN(x) ? 0 : goals[j].deviation(x);
            }
        }
        for (int j = 0; j < m; j++) {
            double mn = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n; i++) {
                mn = Math.min(mn, dev[i][j]);
            }
            double ideal = Double.isFinite(mn) ? mn : 0;
            for (int i = 0; i < n; i++) {
                dev[i][j] -= ideal;
            }
        }
        return dev;
    }

    /** Das-Dennis points with the given divisions (legacy NsgaIII.referencePoints). */
    static double[][] referencePoints(int objectives, int divisions) {
        List<double[]> out = new ArrayList<>();
        generate(new int[objectives], 0, divisions, divisions, out);
        return out.toArray(new double[0][]);
    }

    private static void generate(int[] state, int axis, int remaining, int divisions, List<double[]> out) {
        if (axis == state.length - 1) {
            state[axis] = remaining;
            double[] p = new double[state.length];
            for (int i = 0; i < state.length; i++) {
                p[i] = state[i] / (double) divisions;
            }
            out.add(p);
            return;
        }
        for (int v = 0; v <= remaining; v++) {
            state[axis] = v;
            generate(state, axis + 1, remaining - v, divisions, out);
        }
    }

    static int defaultDivisions(int objectives) {
        if (objectives <= 2) {
            return 12;
        }
        return switch (objectives) {
            case 3 -> 7;
            case 4 -> 5;
            case 5 -> 4;
            default -> 3;
        };
    }

    private static void moeaD(double[][] v, Goal[] goals, List<Map<String, Double>> diag) {
        if (v.length == 0 || goals.length == 0) {
            return;
        }
        double[][] tr = translated(v, goals);
        double[][] weights = referencePoints(goals.length, defaultDivisions(goals.length));
        for (int i = 0; i < v.length; i++) {
            double best = Double.POSITIVE_INFINITY;
            int bestIdx = 0;
            double sum = 0;
            for (int w = 0; w < weights.length; w++) {
                double t = tchebycheff(tr[i], weights[w]);
                if (t < best) {
                    best = t;
                    bestIdx = w;
                }
                sum += t;
            }
            diag.get(i).put("weightIndex", (double) bestIdx);
            diag.get(i).put("tchebycheff", best);
            diag.get(i).put("meanTchebycheff", sum / weights.length);
        }
    }

    static double tchebycheff(double[] deviations, double[] weights) {
        double max = 0;
        int len = Math.min(deviations.length, weights.length);
        for (int i = 0; i < len; i++) {
            max = Math.max(max, Math.max(weights[i], 1e-6) * Math.abs(deviations[i]));
        }
        return max;
    }

    private static void nsgaIII(double[][] v, Goal[] goals, List<Map<String, Double>> diag) {
        int n = v.length;
        int m = goals.length;
        if (n == 0 || m == 0) {
            return;
        }
        double[][] tr = translated(v, goals);
        int[] extreme = new int[m];
        for (int axis = 0; axis < m; axis++) {
            double bestAsf = Double.POSITIVE_INFINITY;
            int bestIdx = -1;
            for (int i = 0; i < n; i++) {
                double asf = Double.NEGATIVE_INFINITY;
                for (int j = 0; j < m; j++) {
                    asf = Math.max(asf, tr[i][j] / (j == axis ? 1.0 : 1e-6));
                }
                if (asf < bestAsf) {
                    bestAsf = asf;
                    bestIdx = i;
                }
            }
            extreme[axis] = Math.max(bestIdx, 0);
        }
        double[] intercepts = intercepts(tr, extreme, m);
        double[][] refs = referencePoints(m, defaultDivisions(m));
        for (int i = 0; i < n; i++) {
            double[] p = new double[m];
            for (int j = 0; j < m; j++) {
                p[j] = tr[i][j] / (Math.abs(intercepts[j]) < 1e-12 ? 1e-12 : intercepts[j]);
            }
            int bestR = 0;
            double bestDist = Double.POSITIVE_INFINITY;
            for (int k = 0; k < refs.length; k++) {
                double d = perpendicular(p, refs[k]);
                if (d < bestDist) {
                    bestDist = d;
                    bestR = k;
                }
            }
            diag.get(i).put("referencePoint", (double) bestR);
            diag.get(i).put("perpendicularDistance", bestDist);
        }
    }

    private static double[] intercepts(double[][] tr, int[] extreme, int m) {
        double[][] a = new double[m][m + 1];
        for (int k = 0; k < m; k++) {
            System.arraycopy(tr[extreme[k]], 0, a[k], 0, m);
            a[k][m] = 1.0;
        }
        double[] x = solve(a, m);
        double[] out = new double[m];
        for (int j = 0; j < m; j++) {
            if (x == null) {
                double mx = 0;
                for (double[] row : tr) {
                    mx = Math.max(mx, row[j]);
                }
                out[j] = mx > 1e-12 ? mx : 1.0;
            } else {
                out[j] = Math.abs(x[j]) < 1e-12 ? 1.0 : 1.0 / x[j];
                if (out[j] < 1e-12) {
                    out[j] = 1.0;
                }
            }
        }
        return out;
    }

    /** Gaussian elimination with partial pivoting; null when singular (legacy gaussianSolve). */
    private static double[] solve(double[][] aug, int n) {
        for (int col = 0; col < n; col++) {
            int pivot = col;
            double max = Math.abs(aug[col][col]);
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(aug[r][col]) > max) {
                    pivot = r;
                    max = Math.abs(aug[r][col]);
                }
            }
            if (max < 1e-12) {
                return null;
            }
            double[] t = aug[col];
            aug[col] = aug[pivot];
            aug[pivot] = t;
            for (int r = col + 1; r < n; r++) {
                double factor = aug[r][col] / aug[col][col];
                for (int c = col; c <= n; c++) {
                    aug[r][c] -= factor * aug[col][c];
                }
            }
        }
        double[] x = new double[n];
        for (int r = n - 1; r >= 0; r--) {
            double sum = aug[r][n];
            for (int c = r + 1; c < n; c++) {
                sum -= aug[r][c] * x[c];
            }
            x[r] = sum / aug[r][r];
        }
        return x;
    }

    static double perpendicular(double[] point, double[] ref) {
        double dot = 0;
        double norm = 0;
        int len = Math.min(point.length, ref.length);
        for (int i = 0; i < len; i++) {
            dot += point[i] * ref[i];
            norm += ref[i] * ref[i];
        }
        if (norm < 1e-12) {
            double d2 = 0;
            for (int i = 0; i < len; i++) {
                d2 += point[i] * point[i];
            }
            return Math.sqrt(d2);
        }
        double t = dot / norm;
        double d2 = 0;
        for (int i = 0; i < len; i++) {
            double d = point[i] - t * ref[i];
            d2 += d * d;
        }
        return Math.sqrt(d2);
    }
}
