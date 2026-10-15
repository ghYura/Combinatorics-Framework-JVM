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

import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Pattern;

/**
 * Suggested goals for a text corpus (audit AN-12 autoplan; legacy AutoAnalysisPlanner, the analyzer's Auto-Analysis
 * button): the first 500 physical lines are read for key=value pairs with a finite numeric value; keys seen on at least
 * three lines, most seen first (first seen first on ties), at most eight, become goals with the direction their name
 * suggests ({@link Discovery#inferMode}), their median as target and an even share of a weight budget of 100 rounded to
 * cents. Lines that look like shell commands or Python assignments are counted as the legacy planner counted them, but
 * nothing is ever recommended for execution: this build runs no external command or interpreter, and generated Java or a
 * typed plugin is the Java execution adapter. A plan only suggests; applying it is explicit.
 */
public final class AutoPlan {

    public static final int SAMPLE_LIMIT = 500;
    public static final int MIN_OCCURRENCES = 3;
    public static final double WEIGHT_BUDGET = 100.0;
    public static final int MAX_GOALS = 8;

    private static final Pattern SHEBANG = Pattern.compile("^#!\\s*/");
    private static final List<String> COMMAND_HEADS = List.of("echo ", "printf ", "cat ", "ls ", "grep ", "awk ", "sed ", "head ", "tail ",
            "find ", "curl ", "wget ", "python ", "python3 ", "java ", "node ", "ruby ", "perl ", "bash ", "sh ", "zsh ", "make ", "cmake ",
            "git ", "docker ", "kubectl ");

    private AutoPlan() {
    }

    /**
     * Reads only the sampled prefix, admitting characters before growing any line or metric collection. A physical
     * line has no implicit size limit: even the first line can exhaust the supplied budget. The immutable returned
     * plan is caller-owned; all parsing reservations are released on return or failure.
     */
    public static Plan read(Path file, MemoryBudget budget) {
        List<MemoryBudget.Lease> leases = new ArrayList<>();
        try {
            leases.add(budget.reserve(65_536, "Auto-plan input buffers"));
            List<String> lines = new ArrayList<>();
            StringBuilder line = new StringBuilder();
            boolean skipLf = false;
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                char[] chunk = new char[1024];
                int count;
                reading: while ((count = reader.read(chunk)) >= 0) {
                    // UTF-16 copies, line storage, key/value maps, numeric lists, sorting and result rendering.
                    leases.add(budget.reserve(256L * count, "Auto-plan sampled text and metrics"));
                    for (int i = 0; i < count; i++) {
                        char c = chunk[i];
                        if (skipLf) {
                            skipLf = false;
                            if (c == '\n') continue;
                        }
                        if (c == '\r' || c == '\n') {
                            lines.add(line.toString());
                            line.setLength(0);
                            skipLf = c == '\r';
                            if (lines.size() == SAMPLE_LIMIT) break reading;
                        } else line.append(c);
                    }
                }
            }
            if (lines.size() < SAMPLE_LIMIT && !line.isEmpty()) lines.add(line.toString());
            return plan(lines);
        } catch (CharacterCodingException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "Auto-plan input is not UTF-8", SourceLocation.of(file.toString())));
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, "Auto-plan input cannot be read: " + e.getMessage(),
                    SourceLocation.of(file.toString())));
        } finally {
            for (MemoryBudget.Lease lease : leases) lease.close();
        }
    }

    /** One suggested goal with the evidence behind it. */
    public record Recommendation(String key, Goal.Mode mode, double target, double weight, int support, double median, double min,
                                 double max, String reason) {
    }

    /** The suggestion and what was sampled. */
    public record Plan(List<Recommendation> goals, int sampledLines, int nonEmptyLines, int shellishLines, int pythonEligibleLines,
                       Map<String, Integer> coverage) {
        public Plan {
            goals = List.copyOf(goals);
            coverage = Collections.unmodifiableMap(new LinkedHashMap<>(coverage));
        }

        /** The goals as a cf.analysis.goals value (key:min or key:max, then :WEIGHT). */
        public String goalsSetting() {
            List<String> parts = new ArrayList<>();
            for (Recommendation r : goals) {
                parts.add(r.key() + ":" + (r.mode() == Goal.Mode.MAXIMIZE ? "max" : "min") + ":" + r.weight());
            }
            return String.join(",", parts);
        }

        /** The goals as the text scorer's manual rules: {"optimizations": [{key, mode, target_value, weight}, ...]}. */
        public String rulesJson() {
            List<Object> optimizations = new ArrayList<>();
            for (Recommendation r : goals) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("key", r.key());
                o.put("mode", r.mode() == Goal.Mode.MAXIMIZE ? "maximize" : "minimize");
                o.put("target_value", r.target());
                o.put("weight", r.weight());
                optimizations.add(o);
            }
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("optimizations", optimizations);
            return Json.write(root);
        }

        public String toText() {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format(Locale.ROOT, "sampled %d lines (%d non-empty); shell-like %d, Python-assignment-like %d%n", sampledLines,
                    nonEmptyLines, shellishLines, pythonEligibleLines));
            if (goals.isEmpty()) {
                sb.append("no numeric key was seen on ").append(MIN_OCCURRENCES).append(" lines: no goal is suggested\n");
            } else {
                sb.append("suggested goals (").append(goals.size()).append("):\n");
                for (Recommendation g : goals) {
                    sb.append(String.format(Locale.ROOT, "  %-18s %-9s target=%-10.4g weight=%-6.2f n=%d median=%-10.4g range=[%g, %g] (%s)%n",
                            g.key(), g.mode() == Goal.Mode.MAXIMIZE ? "maximize" : "minimize", g.target(), g.weight(), g.support(), g.median(),
                            g.min(), g.max(), g.reason()));
                }
                sb.append("cf.analysis.goals=").append(goalsSetting()).append('\n');
            }
            if (shellishLines > 0 || pythonEligibleLines > 0) {
                sb.append("lines that look like commands or Python are scored as text: this build executes nothing outside Java; "
                        + "generated Java (cf.execution.sut=generated-java) or a typed plugin runs candidates\n");
            }
            return sb.toString();
        }
    }

    public static Plan plan(List<String> physicalLines) {
        Map<String, List<Double>> values = new LinkedHashMap<>();
        int sampled = 0;
        int nonEmpty = 0;
        int shellish = 0;
        int pyEligible = 0;
        int upper = Math.min(SAMPLE_LIMIT, physicalLines.size());
        for (int i = 0; i < upper; i++) {
            String raw = physicalLines.get(i);
            sampled++;
            if (raw == null || raw.isBlank()) {
                continue;
            }
            nonEmpty++;
            String text = raw.trim();
            if (looksShellish(text)) shellish++;
            if (text.contains("=") && text.contains(";")) pyEligible++;
            for (Map.Entry<String, String> e : MetricLines.keyValues(raw.replace("\r", "").trim()).entrySet()) {
                OptionalDouble v = Numbers.parse(e.getValue());
                if (v.isPresent()) {
                    values.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(v.getAsDouble());
                }
            }
        }
        List<Map.Entry<String, List<Double>>> byCount = new ArrayList<>(values.entrySet());
        byCount.sort((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()));
        Map<String, Integer> coverage = new LinkedHashMap<>();
        for (Map.Entry<String, List<Double>> e : byCount) {
            coverage.put(e.getKey(), e.getValue().size());
        }
        List<Recommendation> goals = new ArrayList<>();
        for (Map.Entry<String, Integer> e : coverage.entrySet()) {
            if (e.getValue() < MIN_OCCURRENCES) {
                continue;
            }
            List<Double> vs = values.get(e.getKey());
            double median = median(vs);
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double v : vs) {
                if (v < min) min = v;
                if (v > max) max = v;
            }
            Goal.Mode mode = Discovery.inferMode(e.getKey());
            goals.add(new Recommendation(e.getKey(), mode, median, 0.0, e.getValue(), median, min, max, reason(e.getKey(), mode)));
        }
        if (goals.size() > MAX_GOALS) {
            goals = goals.subList(0, MAX_GOALS);
        }
        List<Recommendation> weighted = new ArrayList<>(goals.size());
        double w = goals.isEmpty() ? 0 : Math.round(WEIGHT_BUDGET / goals.size() * 100.0) / 100.0;
        for (Recommendation g : goals) {
            weighted.add(new Recommendation(g.key(), g.mode(), g.target(), w, g.support(), g.median(), g.min(), g.max(), g.reason()));
        }
        return new Plan(weighted, sampled, nonEmpty, shellish, pyEligible, coverage);
    }

    private static String reason(String key, Goal.Mode mode) {
        String name = mode == Goal.Mode.MAXIMIZE ? "maximize" : "minimize";
        String lc = key.toLowerCase(Locale.ROOT);
        for (String h : Discovery.MIN_HINTS) {
            if (lc.contains(h)) return "name match: '" + h + "' → " + name;
        }
        for (String h : Discovery.MAX_HINTS) {
            if (lc.contains(h)) return "name match: '" + h + "' → " + name;
        }
        return "no name hint, defaulted to " + name;
    }

    private static double median(List<Double> values) {
        double[] a = new double[values.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = values.get(i);
        }
        Arrays.sort(a);
        return a.length % 2 == 1 ? a[a.length / 2] : (a[a.length / 2 - 1] + a[a.length / 2]) / 2.0;
    }

    static boolean looksShellish(String text) {
        if (text.isEmpty()) return false;
        if (SHEBANG.matcher(text).find()) return true;
        if (text.startsWith("./") || text.startsWith("/usr/") || text.startsWith("/bin/")) return true;
        for (String h : COMMAND_HEADS) {
            if (text.startsWith(h)) return true;
        }
        return false;
    }
}
