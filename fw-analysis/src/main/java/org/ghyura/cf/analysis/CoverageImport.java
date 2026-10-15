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
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Import-only counterpart of the legacy CoverageProbe: last matching counters, six-decimal fractions and imported
 * metrics winning over inline keys. JaCoCo deliberately uses the original ordered-attribute extraction grammar, not
 * an XML interpreter; neither DTDs nor external entities are opened. No instrumenter or subprocess is launched.
 * Missing/unreadable/malformed reports contribute no metrics and carry diagnostics. RESOURCE_LIMIT always propagates.
 * Returned immutable results are caller-owned; MetricCorpus keeps admission for their retained lifetime.
 */
public final class CoverageImport {
    public enum Format {
        JACOCO("jacoco"), COVERAGE_PY("coverage-py"), GCOV("gcov");
        private final String token;
        Format(String token) { this.token = token; }
        public String token() { return token; }
        public static Format parse(String token) {
            for (Format f : values()) if (f.token.equals(token)) return f;
            throw new IllegalArgumentException("coverage format expects jacoco, coverage-py or gcov: " + token);
        }
    }

    public enum Status { IMPORTED, EMPTY, MISSING, UNREADABLE, MALFORMED }

    /** {lineNo} is the physical one-based corpus line, including skipped blank/comment lines. */
    public record Source(Format format, String pathTemplate) {
        public Source {
            Objects.requireNonNull(format, "format");
            if (Objects.requireNonNull(pathTemplate, "pathTemplate").isBlank()) throw new IllegalArgumentException("coverage report path is empty");
            Path.of(pathTemplate.replace("{lineNo}", "1"));
        }
        public Path path(int lineNo) {
            if (lineNo < 1) throw new IllegalArgumentException("line number must be positive");
            return Path.of(pathTemplate.replace("{lineNo}", Integer.toString(lineNo)));
        }
    }

    public record Report(Format format, Path path, String sha256, Status status, Map<String, Double> metrics,
                         List<Diagnostic> diagnostics) {
        public Report {
            metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));
            diagnostics = List.copyOf(diagnostics);
        }
        public Map<String, Double> merge(Map<String, Double> inline) {
            Map<String, Double> merged = new LinkedHashMap<>(inline);
            merged.putAll(metrics);
            return Collections.unmodifiableMap(merged);
        }
    }

    private static final Pattern COUNTER = Pattern.compile("<counter\\s+type=\"([A-Z]+)\"\\s+missed=\"(\\d+)\"\\s+covered=\"(\\d+)\"\\s*/?>");
    private static final Pattern LINES = gcovPattern("Lines\\s+(?:executed|covered)");
    private static final Pattern BRANCHES = gcovPattern("Branches\\s+(?:executed|covered)");
    private static final Pattern CALLS = gcovPattern("Calls\\s+executed");

    private CoverageImport() { }

    public static Report read(Format format, Path path, MemoryBudget budget) {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(budget, "budget");
        if (Files.notExists(path)) return failed(format, path, "", Status.MISSING, ErrorCode.INPUT_UNREADABLE, "coverage report is missing");
        if (!Files.isRegularFile(path)) return failed(format, path, "", Status.UNREADABLE, ErrorCode.INPUT_UNREADABLE, "coverage report is not a readable regular file");
        try (AnalysisInput input = AnalysisInput.read(path, budget, 192)) {
            try {
                Map<String, Double> metrics = switch (format) {
                    case JACOCO -> jacoco(input.text);
                    case COVERAGE_PY -> coveragePy(input.text, path.toString());
                    case GCOV -> gcov(input.text);
                };
                return new Report(format, path, input.sha256, metrics.isEmpty() ? Status.EMPTY : Status.IMPORTED, metrics, List.of());
            } catch (IllegalArgumentException | ArithmeticException e) {
                return failed(format, path, input.sha256, Status.MALFORMED, ErrorCode.INPUT_MALFORMED, "coverage report has an invalid or overflowing counter");
            } catch (FrameworkException e) {
                if (e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)) throw e;
                return failed(format, path, input.sha256, Status.MALFORMED, ErrorCode.INPUT_MALFORMED, "coverage report is not valid JSON");
            }
        } catch (FrameworkException e) {
            if (e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)) throw e;
            boolean malformed = e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.INPUT_MALFORMED);
            return failed(format, path, "", malformed ? Status.MALFORMED : Status.UNREADABLE,
                    malformed ? ErrorCode.INPUT_MALFORMED : ErrorCode.INPUT_UNREADABLE,
                    malformed ? "coverage report is not UTF-8" : "coverage report cannot be read");
        }
    }

    private static Report failed(Format f, Path p, String hash, Status status, ErrorCode code, String message) {
        return new Report(f, p, hash, status, Map.of(), List.of(Diagnostic.warning(code, message,
                SourceLocation.of(p.toString()), "coverageFormat", f.token(), "coverageStatus", status.name())));
    }

    private static Map<String, Double> jacoco(String text) {
        Map<String, long[]> last = new LinkedHashMap<>();
        Matcher matcher = COUNTER.matcher(text);
        while (matcher.find()) {
            String type = matcher.group(1);
            long missed = Long.parseLong(matcher.group(2)), covered = Long.parseLong(matcher.group(3));
            // Only the five output types are retained; arbitrary report keys cannot grow this map.
            if (List.of("LINE", "BRANCH", "METHOD", "CLASS", "INSTRUCTION").contains(type))
                last.put(type, new long[]{missed, covered});
        }
        Map<String, Double> out = new LinkedHashMap<>();
        for (String type : List.of("LINE", "BRANCH", "METHOD", "CLASS", "INSTRUCTION")) {
            long[] counts = last.get(type);
            if (counts == null) continue;
            long total = Math.addExact(counts[0], counts[1]);
            if (total == 0) continue;
            String key = "coverage_" + type.toLowerCase(Locale.ROOT);
            out.put(key, fraction((double) counts[1] / total));
            out.put(key + "_covered", (double) counts[1]);
            out.put(key + "_total", (double) total);
        }
        return out;
    }

    private static Map<String, Double> coveragePy(String text, String origin) {
        if (text.isEmpty()) return Map.of();
        Object parsed = Json.parse(text, origin);
        if (!(parsed instanceof Map<?, ?> root) || !(root.get("totals") instanceof Map<?, ?> totals)) return Map.of();
        Map<String, Double> out = new LinkedHashMap<>();
        double percent = asDouble(totals.get("percent_covered"), Double.NaN);
        if (!Double.isNaN(percent)) out.put("coverage_line", fraction(percent / 100));
        count(out, "coverage_line_covered", asLong(totals.get("covered_lines")));
        count(out, "coverage_line_total", asLong(totals.get("num_statements")));
        count(out, "coverage_line_missed", asLong(totals.get("missing_lines")));
        long branches = asLong(totals.get("num_branches"));
        long covered = asLong(totals.get("covered_branches"));
        if (branches > 0 && covered >= 0) {
            out.put("coverage_branch", fraction((double) covered / branches));
            out.put("coverage_branch_covered", (double) covered);
            out.put("coverage_branch_total", (double) branches);
        }
        return out;
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number n) return n.doubleValue();
        if (value instanceof Boolean b) return b ? 1 : 0;
        if (value instanceof String s) {
            try { return Double.parseDouble(s.strip()); } catch (NumberFormatException ignored) { return fallback; }
        }
        return fallback;
    }

    private static long asLong(Object value) {
        if (value instanceof Long n) return n;
        if (value instanceof String s) {
            try { return Long.parseLong(s.strip()); } catch (NumberFormatException ignored) { /* decimal coercion below */ }
        }
        double n = asDouble(value, -1);
        if (!Double.isFinite(n)) throw new IllegalArgumentException("nonfinite count");
        return (long) n;
    }

    private static void count(Map<String, Double> out, String key, long n) { if (n >= 0) out.put(key, (double) n); }

    private static Pattern gcovPattern(String category) {
        return Pattern.compile(category + "\\s*:?\\s*(\\d+(?:\\.\\d+)?)\\s*%(?:\\s+of\\s+(\\d+))?", Pattern.CASE_INSENSITIVE);
    }

    private static Map<String, Double> gcov(String text) {
        Map<String, Double> out = new LinkedHashMap<>();
        gcovLast(out, "line", LINES.matcher(text));
        gcovLast(out, "branch", BRANCHES.matcher(text));
        gcovLast(out, "call", CALLS.matcher(text));
        return out;
    }

    private static void gcovLast(Map<String, Double> out, String tag, Matcher matcher) {
        String percent = null, total = null;
        while (matcher.find()) { percent = matcher.group(1); total = matcher.group(2); }
        if (percent == null) return;
        out.put("coverage_" + tag, fraction(Double.parseDouble(percent) / 100));
        if (total != null) {
            double n = Double.parseDouble(total);
            if (!Double.isFinite(n)) throw new IllegalArgumentException("nonfinite total");
            out.put("coverage_" + tag + "_total", n);
        }
    }

    private static double fraction(double n) {
        if (!Double.isFinite(n)) throw new IllegalArgumentException("nonfinite fraction");
        return Double.parseDouble(String.format(Locale.ROOT, "%.6f", n));
    }
}
