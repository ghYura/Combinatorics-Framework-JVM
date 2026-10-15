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

package org.ghyura.cf.campaign;

import org.ghyura.cf.analysis.BundleSeed;
import org.ghyura.cf.analysis.Goal;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.Condition;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.Gate;
import org.ghyura.cf.model.rule.Rule;
import org.ghyura.cf.model.rule.RuleValue;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The legacy seed bias (generator bundle/seedbias.py build_bias_plan), rule for rule. The winners of a seed narrow each
 * sheet to the values they hold plus the immediate neighbours of those values in declared order, never below the
 * exploration floor; every other value becomes a forbid rule {@code {sheet has value}} for the next iteration. A sheet is
 * left alone when fewer relevant winners than the minimum support hold one of its values, when they already cover 80% of
 * it, or when it is no larger than its floor. A plan that excludes nothing, or narrows every sheet only down to its floor,
 * carries no signal (CONVERGED_NO_SIGNAL). The winners are joined through the selections a seed of this build carries; a
 * legacy seed named database rows instead and cannot be joined.
 */
public final class SeedBias {

    /** Winner coverage of a sheet's values at which it carries no narrowing signal (legacy 0.80). */
    static final double COVERAGE_LIMIT = 0.80;

    private static final SourceLocation WHERE = SourceLocation.of("bias plan");

    private SeedBias() {
    }

    /** What a plan does to the next iteration. */
    public enum Status {
        /** It excludes values: the next iteration runs the base scenario with the plan's forbid rules. */
        BIASED,
        /** It carries no signal: the next iteration runs the base scenario unbiased, and the campaign stops after it. */
        CONVERGED_NO_SIGNAL,
        /** It would keep fewer than one candidate of the full space: the campaign stops before the next iteration. */
        EXHAUSTED
    }

    /**
     * What the bias decided for one sheet (legacy SheetBias).
     *
     * @param sheet        the sheet
     * @param domain       its distinct values, stripped, in declared order
     * @param observations relevant winners holding at least one of them
     * @param winnerCounts per value of the domain, the relevant winners holding it
     * @param kept         the values the next iteration keeps, in declared order
     * @param excluded     the values it forbids, in declared order
     * @param minKeep      the exploration floor: the fewest values a narrowed sheet keeps
     * @param coverage     values some winner holds over the domain size
     * @param reason       why the sheet was or was not narrowed
     */
    public record SheetBias(String sheet, List<String> domain, int observations, Map<String, Integer> winnerCounts, List<String> kept,
                            List<String> excluded, int minKeep, double coverage, String reason) {
        public SheetBias {
            domain = List.copyOf(domain);
            winnerCounts = Collections.unmodifiableMap(new LinkedHashMap<>(winnerCounts));
            kept = List.copyOf(kept);
            excluded = List.copyOf(excluded);
        }

        /** The values some relevant winner holds, in declared order. */
        public List<String> winnerValues() {
            return domain.stream().filter(v -> winnerCounts.getOrDefault(v, 0) > 0).toList();
        }
    }

    /**
     * A bias plan (legacy bundle.biasplan/v1).
     *
     * @param status           what it does to the next iteration
     * @param reason           why, in words
     * @param sourceRunId      the run the seed came from
     * @param seedDigest       the seed's content digest
     * @param explorationFloor the share of each sheet's values a narrowed sheet keeps at least
     * @param minWinnerSupport the relevant winners a sheet needs before it is narrowed
     * @param sheets           per sheet, in scenario order
     * @param rules            the forbid rules, sheet by sheet in declared value order; empty unless BIASED
     * @param keepFraction     the product of kept over domain size of every narrowed sheet
     * @param fullSpace        the full-space estimate the fraction applies to
     * @param postBias         floor(fullSpace x keepFraction), at least 1 (legacy est_post_bias_candidates)
     * @param winners          the seed's winners
     * @param joinable         those carrying selections
     * @param relevant         joinable winners relevant to the declared goals (every joinable one when none is)
     */
    public record Plan(Status status, String reason, String sourceRunId, String seedDigest, double explorationFloor, int minWinnerSupport,
                       List<SheetBias> sheets, List<Rule> rules, double keepFraction, BigInteger fullSpace, BigInteger postBias, int winners,
                       int joinable, int relevant) {
        public Plan {
            sheets = List.copyOf(sheets);
            rules = List.copyOf(rules);
        }

        /** The forbid rules as a constraint layer of their own. */
        public ConstraintSet constraints() {
            return layer(rules);
        }

        /**
         * The base scenario with this plan's rules after its own; a derived rule the author already wrote is not added twice
         * (legacy stage_seed_bias). Unless BIASED, the base scenario itself.
         */
        public ScenarioSpec apply(ScenarioSpec base) {
            if (status != Status.BIASED) {
                return base;
            }
            Set<String> seen = new HashSet<>();
            base.constraints().rules().forEach(r -> seen.add(r.canonical()));
            List<Rule> added = rules.stream().filter(r -> seen.add(r.canonical())).toList();
            return base.withConstraints(base.constraints().plus(layer(added)));
        }

        private ConstraintSet layer(List<Rule> list) {
            return list.isEmpty() ? ConstraintSet.NONE : new ConstraintSet(List.of(new ConstraintSet.Source("bias plan of run "
                    + sourceRunId, seedDigest, "seed-bias")), Map.of(), Map.of(), list, List.of());
        }

        /** A deterministic text of what the plan decided (the fields of the legacy bias_plan.json). */
        public String canonicalText() {
            StringBuilder out = new StringBuilder("cf.biasplan/1\n");
            out.append("STATUS\t").append(status).append('\t').append(Canonical.escape(reason)).append('\n');
            out.append("SEED\t").append(Canonical.escape(sourceRunId)).append('\t').append(seedDigest).append('\t').append(winners)
                    .append('\t').append(joinable).append('\t').append(relevant).append('\n');
            out.append("FLOOR\t").append(explorationFloor).append('\t').append(minWinnerSupport).append('\n');
            for (SheetBias s : sheets) {
                out.append("SHEET\t").append(Canonical.escape(s.sheet())).append('\t').append(s.observations()).append('\t')
                        .append(s.minKeep()).append('\t').append(s.coverage()).append("\tkeep");
                s.kept().forEach(v -> out.append('\t').append(Canonical.escape(v)));
                out.append("\texclude");
                s.excluded().forEach(v -> out.append('\t').append(Canonical.escape(v)));
                out.append('\n');
            }
            rules.forEach(r -> out.append(r.canonical()).append('\n'));
            out.append("ESTIMATE\t").append(fullSpace).append('\t').append(keepFraction).append('\t').append(postBias).append('\n');
            return out.toString();
        }

        public String contentDigest() {
            return Canonical.sha256Hex(canonicalText());
        }
    }

    /**
     * The bias plan of a seed for a scenario.
     *
     * @param scenario         the base scenario: each sheet's domain is its distinct stripped values in declared order
     * @param seed             the previous iteration's seed
     * @param goals            the declared goals: a champion counts only when its direction agrees with its goal's
     * @param explorationFloor 0 &lt; floor &lt;= 1
     * @param minWinnerSupport at least 1
     * @param fullSpace        the candidates of the full space (the previous run's generated count), or null for the product of
     *                         the sheet sizes (the legacy fallback estimate)
     * @throws FrameworkException CAMPAIGN_SEED_UNJOINABLE when no winner carries selections
     */
    public static Plan plan(ScenarioSpec scenario, BundleSeed seed, List<Goal> goals, double explorationFloor, int minWinnerSupport,
                            BigInteger fullSpace) {
        if (!(explorationFloor > 0 && explorationFloor <= 1)) {
            throw new IllegalArgumentException("the exploration floor must satisfy 0 < value <= 1: " + explorationFloor);
        }
        if (minWinnerSupport < 1) {
            throw new IllegalArgumentException("the minimum winner support must be >= 1: " + minWinnerSupport);
        }
        List<BundleSeed.Winner> joinable = seed.winners().stream().filter(w -> !w.selections().isEmpty()).toList();
        if (joinable.isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CAMPAIGN_SEED_UNJOINABLE, "the seed of run '" + seed.sourceRunId()
                    + "' cannot bias a scenario: none of its " + seed.winners().size() + " winners carries sheet selections (a legacy "
                    + "seed names database rows this build does not keep; write seeds with cf.campaign.seed.output)", WHERE));
        }
        List<BundleSeed.Winner> rows = relevant(joinable, seed, goals);
        List<SheetBias> sheets = new ArrayList<>();
        List<Rule> rules = new ArrayList<>();
        double keepFraction = 1.0;
        List<Boolean> floorLimited = new ArrayList<>();
        for (SheetSpec sheet : scenario.sheets()) {
            LinkedHashSet<String> distinct = new LinkedHashSet<>();
            for (ValueCell cell : sheet.cells()) {
                distinct.add(strip(cell.text()));
            }
            List<String> domain = List.copyOf(distinct);
            if (domain.isEmpty()) {
                sheets.add(new SheetBias(sheet.name(), domain, 0, Map.of(), List.of(), List.of(), 0, 0.0, "empty domain"));
                continue;
            }
            Map<String, Integer> counts = new LinkedHashMap<>();
            domain.forEach(v -> counts.put(v, 0));
            int observations = 0;
            for (BundleSeed.Winner row : rows) {
                Set<String> selected = new LinkedHashSet<>();
                for (String v : row.selections().getOrDefault(sheet.name(), List.of())) {
                    String s = strip(v);
                    if (counts.containsKey(s)) {
                        selected.add(s);
                    }
                }
                if (selected.isEmpty()) {
                    continue;
                }
                observations++;
                selected.forEach(v -> counts.merge(v, 1, Integer::sum));
            }
            List<String> winnerValues = domain.stream().filter(v -> counts.get(v) > 0).toList();
            double coverage = (double) winnerValues.size() / domain.size();
            int minKeep = Math.min(domain.size(), Math.max(2, (int) Math.ceil(explorationFloor * domain.size())));
            String reason;
            Set<String> kept = new HashSet<>(domain);
            List<String> excluded = List.of();
            if (observations < minWinnerSupport) {
                reason = "support " + observations + " < min_winner_support " + minWinnerSupport;
            } else if (coverage >= COVERAGE_LIMIT) {
                reason = String.format(Locale.ROOT, "winner coverage %.3f >= 0.800; no narrowing signal", coverage);
            } else if (domain.size() <= minKeep) {
                reason = "domain size " + domain.size() + " <= exploration minimum " + minKeep;
            } else {
                kept = adjacentKeep(domain, winnerValues);
                if (kept.size() < minKeep) {
                    for (String v : domain) {
                        kept.add(v);
                        if (kept.size() >= minKeep) {
                            break;
                        }
                    }
                }
                Set<String> keep = kept;
                excluded = domain.stream().filter(v -> !keep.contains(v)).toList();
                if (excluded.isEmpty()) {
                    reason = "adjacency/floor retention kept the full domain";
                    kept = new HashSet<>(domain);
                } else {
                    reason = "excluded values absent from winners; immediate declared-order neighbors retained";
                    keepFraction *= (double) kept.size() / domain.size();
                    floorLimited.add(kept.size() <= minKeep);
                    for (String v : excluded) {
                        rules.add(forbid(sheet.name(), v));
                    }
                }
            }
            Set<String> keep = kept;
            sheets.add(new SheetBias(sheet.name(), domain, observations, counts, domain.stream().filter(keep::contains).toList(), excluded,
                    minKeep, coverage, reason));
        }
        BigInteger full = fullSpace != null ? fullSpace : productOfSheetSizes(scenario);
        // as the legacy math.floor(full * keep_fraction): the product of a float, not of exact numbers
        double product = full.doubleValue() * keepFraction;
        BigInteger post = Double.isFinite(product) ? new BigDecimal(Math.floor(product)).toBigIntegerExact()
                : new BigDecimal(full).multiply(new BigDecimal(keepFraction)).setScale(0, RoundingMode.FLOOR).toBigIntegerExact();
        BigInteger estimate = post.max(BigInteger.ONE);
        Status status;
        String reason;
        if (rules.isEmpty()) {
            status = Status.CONVERGED_NO_SIGNAL;
            reason = "seed bias produced no exclusions: every sheet lacked support, already covered >=80%, or was protected by the "
                    + "exploration floor";
        } else if (!floorLimited.isEmpty() && floorLimited.stream().allMatch(b -> b)) {
            status = Status.CONVERGED_NO_SIGNAL;
            reason = "seed bias would push every narrowed sheet to its exploration floor; refusing to prune noise";
        } else if (post.signum() < 1) {
            status = Status.EXHAUSTED;
            reason = String.format(Locale.ROOT, "seed bias would keep <1 candidate: full_space_estimate=%s, est_keep_fraction=%.8f", full,
                    keepFraction);
        } else {
            status = Status.BIASED;
            reason = "excluded " + rules.size() + " values absent from the winners; full space " + full + ", estimated " + post
                    + " candidates left";
        }
        return new Plan(status, reason, seed.sourceRunId(), seed.contentDigest(), explorationFloor, minWinnerSupport, sheets,
                status == Status.BIASED ? rules : List.of(), keepFraction, full, estimate, seed.winners().size(), joinable.size(),
                rows.size());
    }

    /**
     * The winners relevant to the declared goals (legacy _rows_relevant_to_declared_goals): pareto and balanced winners, and
     * a champion of a declared metric when its goal's direction agrees or the metric has no goal; every winner when none is.
     */
    static List<BundleSeed.Winner> relevant(List<BundleSeed.Winner> rows, BundleSeed seed, List<Goal> goals) {
        Set<String> declared = new HashSet<>(seed.declaredMetrics());
        Map<String, Goal.Mode> directions = new HashMap<>();
        goals.forEach(g -> directions.put(g.key(), g.mode()));
        List<BundleSeed.Winner> out = new ArrayList<>();
        for (BundleSeed.Winner row : rows) {
            String role = row.role();
            if (role.equals("pareto") || role.startsWith("balanced:")) {
                out.add(row);
            } else if (role.startsWith("champion-min:") || role.startsWith("champion-max:")) {
                int colon = role.indexOf(':');
                String prefix = role.substring(0, colon);
                String key = role.substring(colon + 1);
                if (!declared.contains(key)) {
                    continue;
                }
                Goal.Mode direction = directions.get(key);
                if (direction == null || (prefix.equals("champion-min") && direction == Goal.Mode.MINIMIZE)
                        || (prefix.equals("champion-max") && direction == Goal.Mode.MAXIMIZE)) {
                    out.add(row);
                }
            }
        }
        return out.isEmpty() ? rows : out;
    }

    /** The kept values with their immediate neighbours in declared order (legacy _adjacent_keep). */
    private static Set<String> adjacentKeep(List<String> domain, List<String> kept) {
        Set<String> out = new HashSet<>(kept);
        for (String v : kept) {
            int i = domain.indexOf(v);
            if (i < 0) {
                continue;
            }
            if (i > 0) {
                out.add(domain.get(i - 1));
            }
            if (i + 1 < domain.size()) {
                out.add(domain.get(i + 1));
            }
        }
        return out;
    }

    /** The legacy derived constraint {id, polarity forbid, assert {sheet, has}} as a rule; its id is the legacy one. */
    static Rule forbid(String sheet, String value) {
        String id = "seedbias.forbid." + sheet + "." + sha1Hex(value).substring(0, 10);
        return new Rule(id, Rule.Polarity.FORBID, Gate.ANY, null,
                new Rule.Assert(Condition.Leaf.value(sheet, Condition.Op.HAS, new RuleValue.Text(value))),
                "BundleSeed bias: exclude " + sheet + "=" + repr(value) + " (not observed among winners)", WHERE);
    }

    private static BigInteger productOfSheetSizes(ScenarioSpec scenario) {
        BigInteger n = BigInteger.ONE;
        for (SheetSpec sheet : scenario.sheets()) {
            n = n.multiply(BigInteger.valueOf(Math.max(1, sheet.cells().size())));
        }
        return n;
    }

    private static String sha1Hex(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every JDK provides SHA-1", e);
        }
    }

    /** Python {@code str.strip()}: the characters Python counts as white space, from both ends (as the sieve compared). */
    static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    private static boolean isSpace(char c) {
        return Character.isWhitespace(c) || Character.isSpaceChar(c) || c == '\u0085';
    }

    /** Python {@code repr} of a text, for the legacy description. */
    private static String repr(String s) {
        char q = s.indexOf('\'') >= 0 && s.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder b = new StringBuilder().append(q);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c == q) {
                        b.append('\\');
                    }
                    b.append(c);
                }
            }
        }
        return b.append(q).toString();
    }
}
