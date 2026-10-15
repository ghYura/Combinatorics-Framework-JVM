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


package org.ghyura.cf.desktop.session;

import org.ghyura.cf.analysis.Analysis;
import org.ghyura.cf.analysis.AnalysisReport;
import org.ghyura.cf.campaign.RunHandle;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyCounts;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Candidate;
import org.ghyura.cf.generation.CandidateRenderer;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.MemoryBudget;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * The stage benchmark (GEN:diagnostics-reporting-release; the legacy bundle bench, bundle/benchmark.py): each stage timed on
 * its own at a size profile, never one fused end-to-end number, with the environment that makes the figures comparable
 * (JDK, heap, hardware, workers, seed) and an explicit disclaimer that no billion-execution throughput is claimed. The
 * profiles, their resolution, the exact factor pair of a size and the table are the legacy ones; sizes above 10 000 000 run
 * only when explicitly allowed. Every stage runs in this JVM: generator (the candidates of an exact a x b two-sheet scenario,
 * rendered), sieve (the legacy MODE x PAYLOAD rule over a scenario of the size), executor (the samples of the generator's
 * shape through the inline-metrics plugin, aggregate retention) and analyzer (a Pareto analysis of synthetic samples). A
 * stage reports its wall and process CPU time, the heap its memory pools reached above the stage's start, the memory it
 * reserved from the budget and its rate over the rows it actually processed; nothing touches a disk or a database.
 */
public final class Benchmark {

    public static final String SCHEMA = "cf.benchmark/v1";
    public static final long MAX_AUTO_CANDIDATES = 10_000_000;
    public static final List<String> STAGES = List.of("generator", "sieve", "executor", "analyzer");
    static final Map<String, Long> PROFILES = Map.of("10K", 10_000L, "1M", 1_000_000L, "10M", 10_000_000L);
    static final String DISCLAIMER = "No end-to-end 'billion executions' throughput is claimed or measured. Sizes above 10,000,000 "
            + "(100M/1B) are never run automatically.";

    private Benchmark() {
    }

    /** One stage's measurement, rounded as the legacy report rounded it. */
    public record Stage(String stage, long candidates, long rows, double wallSeconds, double cpuSeconds, long peakMemoryBytes,
                        long reservedPeakBytes, double throughputPerS, boolean skipped, String skipReason, String notes, String corpus) {
    }

    /** A benchmark report. */
    public record Report(String schema, String profile, long candidates, Map<String, Object> environment, List<Stage> stages) {
        public Report {
            environment = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(environment));
            stages = List.copyOf(stages);
        }
    }

    // ---- sizes -------------------------------------------------------------------------------------------------

    /**
     * resolve_profile: 10K, 1M, 10M or an integer (underscores and commas ignored); above 10 000 000 only with
     * {@code allowHuge}.
     *
     * @throws FrameworkException USAGE_INVALID_VALUE for an unknown or non-positive profile, RESOURCE_LIMIT for a size
     *                            above the ceiling, with the legacy messages
     */
    public static long resolveProfile(String profile, boolean allowHuge) {
        Long known = PROFILES.get(profile);
        BigInteger n;
        if (known != null) {
            n = BigInteger.valueOf(known);
        } else {
            n = pythonInt(profile.replace("_", "").replace(",", ""));
            if (n == null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "unknown profile " + pythonRepr(profile)
                        + " (known: 10K, 1M, 10M or an integer)", SourceLocation.of("command line"), "option", "--profile"));
            }
        }
        if (n.signum() <= 0) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "profile size must be positive, got " + n,
                    SourceLocation.of("command line"), "option", "--profile"));
        }
        if (n.compareTo(BigInteger.valueOf(MAX_AUTO_CANDIDATES)) > 0 && !allowHuge) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "profile size " + grouped(n) + " exceeds the "
                    + grouped(BigInteger.valueOf(MAX_AUTO_CANDIDATES)) + " auto-run ceiling — 100M/1B-class runs are never started "
                    + "automatically; pass allow_huge=True (CLI --allow-huge) to explicitly opt in", SourceLocation.of("command line"),
                    "option", "--profile"));
        }
        if (n.bitLength() > 62) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.RESOURCE_LIMIT, "profile size " + grouped(n) + " is beyond what this "
                    + "JVM counts in one stage", SourceLocation.of("command line"), "option", "--profile"));
        }
        return n.longValueExact();
    }

    /** _factor_pair: n = a x b exactly, a the largest divisor up to the square root (a prime gives 1 x n). */
    public static long[] factorPair(long size) {
        long n = Math.max(1, size);
        long a = (long) Math.sqrt((double) n);
        while (a * a > n) {
            a--;
        }
        while ((a + 1) * (a + 1) <= n) {
            a++;
        }
        while (a > 1 && n % a != 0) {
            a--;
        }
        return new long[]{a, n / a};
    }

    /** int(text) of CPython, or null: whitespace, a sign and Unicode decimal digits. */
    private static BigInteger pythonInt(String text) {
        String t = text.strip();
        int k = 0;
        boolean negative = false;
        if (!t.isEmpty() && (t.charAt(0) == '+' || t.charAt(0) == '-')) {
            negative = t.charAt(0) == '-';
            k = 1;
        }
        StringBuilder digits = new StringBuilder();
        while (k < t.length()) {
            int cp = t.codePointAt(k);
            if (Character.getType(cp) != Character.DECIMAL_DIGIT_NUMBER) {
                return null;
            }
            digits.append(Character.digit(cp, 10));
            k += Character.charCount(cp);
        }
        if (digits.isEmpty()) {
            return null;
        }
        BigInteger value = new BigInteger(digits.toString());
        return negative ? value.negate() : value;
    }

    /** repr() of a str, for the messages. */
    private static String pythonRepr(String s) {
        char quote = s.indexOf('\'') >= 0 && s.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder().append(quote);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c == quote) {
                        out.append('\\').append(c);
                    } else if (c < 0x20 || c == 0x7f) {
                        out.append(String.format(Locale.ROOT, "\\x%02x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append(quote).toString();
    }

    // ---- formatting (Python's format specifications, as format_table used them) ----------------------------------

    private static String grouped(BigInteger n) {
        String digits = n.abs().toString();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(',');
            }
            out.append(digits.charAt(i));
        }
        return (n.signum() < 0 ? "-" : "") + out;
    }

    private static String fixed(double x, int places, boolean group) {
        BigDecimal value = new BigDecimal(x).setScale(places, RoundingMode.HALF_EVEN);
        String plain = value.toPlainString();
        if (!group) {
            return plain;
        }
        int dot = plain.indexOf('.');
        String whole = dot < 0 ? plain : plain.substring(0, dot);
        boolean negative = whole.startsWith("-");
        String grouped = grouped(new BigInteger(negative ? whole.substring(1) : whole));
        return (negative ? "-" : "") + grouped + (dot < 0 ? "" : plain.substring(dot));
    }

    private static String right(String s, int width) {
        return s.length() >= width ? s : " ".repeat(width - s.length()) + s;
    }

    private static String left(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }

    static double round(double x, int places) {
        return new BigDecimal(x).setScale(places, RoundingMode.HALF_EVEN).doubleValue();
    }

    /** One stage line of format_table. */
    static String stageLine(Stage st) {
        if (st.skipped()) {
            String reason = st.skipReason().length() > 40 ? st.skipReason().substring(0, 40) : st.skipReason();
            return "  " + left(st.stage(), 18) + "(skipped: " + reason + ")";
        }
        return "  " + left(st.stage(), 18) + right(grouped(BigInteger.valueOf(st.candidates())), 10) + right(fixed(st.wallSeconds(), 3, false), 10)
                + right(fixed(st.cpuSeconds(), 3, false), 9) + right(fixed(st.peakMemoryBytes() / 1e6, 1, false), 9)
                + right(fixed(st.throughputPerS(), 0, true), 12);
    }

    /** The column header of format_table. */
    static String headerLine() {
        return "  " + left("stage", 18) + right("cand", 10) + right("wall_s", 10) + right("cpu_s", 9) + right("peakMB", 9) + right("thru/s", 12);
    }

    /** format_table, with this runtime's toolchain line. */
    @SuppressWarnings("unchecked")
    public static String table(Report r) {
        Map<String, Object> hardware = (Map<String, Object>) r.environment().get("hardware");
        Map<String, Object> os = (Map<String, Object>) r.environment().get("os_kernel");
        Map<String, Object> toolchain = (Map<String, Object>) r.environment().get("toolchain");
        Map<String, Object> jvm = (Map<String, Object>) r.environment().get("jvm");
        List<String> lines = new ArrayList<>();
        lines.add("benchmark " + r.schema() + "  profile=" + r.profile() + "  candidates=" + grouped(BigInteger.valueOf(r.candidates())));
        lines.add("  host: " + hardware.get("cpu_count") + " CPU, " + hardware.get("mem_total") + ", " + os.get("platform"));
        lines.add("  toolchain: java " + toolchain.get("java") + " | heap " + fixed(((Number) jvm.get("heap_max_bytes")).longValue() / 1048576.0, 1, false)
                + " MiB | workers " + jvm.get("workers") + ", seed " + jvm.get("seed"));
        lines.add(headerLine());
        r.stages().forEach(st -> lines.add(stageLine(st)));
        lines.add("  " + DISCLAIMER);
        return String.join("\n", lines);
    }

    /** The versioned report as JSON (keys in the legacy report's order). */
    public static String json(Report r) {
        List<Object> stages = new ArrayList<>();
        List<String> measured = new ArrayList<>();
        List<Object> skipped = new ArrayList<>();
        for (Stage st : r.stages()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stage", st.stage());
            m.put("candidates", st.candidates());
            m.put("rows", st.rows());
            m.put("wall_seconds", st.wallSeconds());
            m.put("cpu_seconds", st.cpuSeconds());
            m.put("peak_memory_bytes", st.peakMemoryBytes());
            m.put("reserved_peak_bytes", st.reservedPeakBytes());
            m.put("disk_bytes", 0L);
            m.put("wal_bytes", 0L);
            m.put("throughput_per_s", st.throughputPerS());
            m.put("skipped", st.skipped());
            m.put("skip_reason", st.skipReason());
            m.put("notes", st.notes());
            m.put("corpus", st.corpus());
            stages.add(m);
            if (st.skipped()) {
                Map<String, Object> skip = new LinkedHashMap<>();
                skip.put("stage", st.stage());
                skip.put("reason", st.skipReason());
                skipped.add(skip);
            } else {
                measured.add(st.stage());
            }
        }
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("measured_stages", measured);
        claims.put("skipped_stages", skipped);
        claims.put("scope", "per-stage micro-benchmark in one JVM, NOT a fused end-to-end pipeline; every stage runs in process at the "
                + "requested profile and reports the rows it actually processed");
        claims.put("disclaimer", DISCLAIMER);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schema", r.schema());
        doc.put("profile", r.profile());
        doc.put("candidates", r.candidates());
        doc.put("environment", r.environment());
        doc.put("stages", stages);
        doc.put("claims", claims);
        return Json.write(doc);
    }

    /** The report as an XML element. */
    public static void appendXml(Report r, StringBuilder xml, String indent) {
        xml.append(indent).append("<benchmark schema=\"").append(r.schema()).append("\" profile=\"").append(Xml.escape(r.profile()))
                .append("\" candidates=\"").append(r.candidates()).append("\">\n");
        for (Stage st : r.stages()) {
            xml.append(indent).append("  <stage name=\"").append(st.stage()).append("\" candidates=\"").append(st.candidates()).append("\" rows=\"")
                    .append(st.rows()).append("\" wallSeconds=\"").append(st.wallSeconds()).append("\" cpuSeconds=\"").append(st.cpuSeconds())
                    .append("\" peakMemoryBytes=\"").append(st.peakMemoryBytes()).append("\" reservedPeakBytes=\"").append(st.reservedPeakBytes())
                    .append("\" throughputPerS=\"").append(st.throughputPerS()).append("\" skipped=\"").append(st.skipped())
                    .append(st.skipped() ? "\" skipReason=\"" + Xml.escape(st.skipReason()) : "").append("\" notes=\"")
                    .append(Xml.escape(st.notes())).append("\" corpus=\"").append(Xml.escape(st.corpus())).append("\"/>\n");
        }
        xml.append(indent).append("  <disclaimer>").append(Xml.escape(DISCLAIMER)).append("</disclaimer>\n");
        xml.append(indent).append("</benchmark>\n");
    }

    // ---- running -----------------------------------------------------------------------------------------------

    /**
     * Runs the stages at the profile.
     *
     * @param configure resolves the configuration with stage settings added on top of the caller's
     */
    public static Report run(String profile, List<String> stages, boolean allowHuge, Function<List<ConfigResolver.Override>, EffectiveConfig> configure)
            throws IOException {
        long n = resolveProfile(profile, allowHuge);
        List<String> requested = stages.isEmpty() ? STAGES : stages;
        for (String s : requested) {
            if (!STAGES.contains(s)) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "unknown stage(s) [" + s + "]; known: "
                        + String.join(", ", STAGES), SourceLocation.of("command line"), "option", "--stages"));
            }
        }
        RunConfig base = configure.apply(List.of()).config();
        Path scratch = Files.createTempDirectory("cf-benchmark");
        try {
            List<Stage> results = new ArrayList<>();
            for (String s : requested) {
                try {
                    results.add(switch (s) {
                        case "generator" -> generator(n, scratch, configure);
                        case "sieve" -> sieve(n, scratch, configure);
                        case "executor" -> executor(n, scratch, configure);
                        default -> analyzer(n, configure);
                    });
                } catch (FrameworkException e) {
                    // a budget or the key width refusing the profile is that stage's result, as the legacy harness recorded a
                    // stage it could not run: SKIPPED with the precise reason, the other stages still measured
                    Diagnostic first = e.diagnostics().getFirst();
                    if (first.code().category() != ErrorCode.Category.RESOURCE && first.code() != ErrorCode.KEY_CENSUS_OVERFLOW) {
                        throw e;
                    }
                    results.add(new Stage(s, n, 0, 0.0, 0.0, 0, 0, 0.0, true, first.code() + ": " + first.message(), "", ""));
                }
            }
            return new Report(SCHEMA, profile, n, environment(base), results);
        } finally {
            try (Stream<Path> walk = Files.walk(scratch)) {
                for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }

    private record Work(long rows, String notes, long reservedPeak) {
    }

    private interface StageWork {
        Work run() throws IOException;
    }

    /**
     * Times one stage: wall and process CPU time, and the heap's peak occupancy above its use after a collection at the
     * start, sampled every 2 ms (it includes garbage not yet collected, so it is an upper figure; the legacy tracemalloc
     * peak counted live objects only), beside the memory budget's reservation high water.
     */
    private static Stage measure(String stage, long candidates, String corpus, StageWork work) throws IOException {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        System.gc();
        long base = memory.getHeapMemoryUsage().getUsed();
        AtomicLong highest = new AtomicLong(base);
        Thread sampler = Thread.ofPlatform().daemon().name("cf-benchmark-heap").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                highest.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max);
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });
        long cpu0 = processCpuNanos();
        long t0 = System.nanoTime();
        Work done;
        try {
            done = work.run();
        } finally {
            sampler.interrupt();
            try {
                sampler.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        double wall = (System.nanoTime() - t0) / 1e9;
        double cpu = (processCpuNanos() - cpu0) / 1e9;
        long peak = Math.max(0, highest.accumulateAndGet(memory.getHeapMemoryUsage().getUsed(), Math::max) - base);
        double rate = wall > 0 && done.rows() > 0 ? done.rows() / wall : 0.0;
        return new Stage(stage, candidates, done.rows(), round(wall, 6), round(cpu, 6), peak, done.reservedPeak(), round(rate, 2), false, "",
                done.notes(), corpus);
    }

    private static long processCpuNanos() {
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os) {
            long t = os.getProcessCpuTime();
            if (t >= 0) {
                return t;
            }
        }
        return ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime();
    }

    private static String sheet(String prefix, long count, String suffix) {
        StringBuilder rows = new StringBuilder();
        for (long i = 0; i < count; i++) {
            rows.append(i == 0 ? "" : ", ").append("[\"").append(prefix).append(i).append(suffix).append("\"]");
        }
        return rows.toString();
    }

    private static Stage generator(long n, Path scratch, Function<List<ConfigResolver.Override>, EffectiveConfig> configure) throws IOException {
        long[] ab = factorPair(n);
        Path file = scratch.resolve("generator.json");
        Files.writeString(file, "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"], [\"B\", \"FW_Combi(1)\"]], \"A\": [" + sheet("a", ab[0], ";")
                + "], \"B\": [" + sheet("b", ab[1], ";") + "]}}", StandardCharsets.UTF_8);
        EffectiveConfig effective = configure.apply(List.of());
        String corpus = ab[0] + " x " + ab[1] + " two-sheet scenario, every candidate rendered";
        return measure("generator", n, corpus, () -> {
            ScenarioValidation.Opened opened = ScenarioValidation.open(file, effective);
            RunConfig config = opened.effective().config();
            return ScenarioValidation.prepare(file.toString(), opened.loaded(), config, (summary, dictionary, budget) -> {
                GenerationOptions generation = GenerationOptions.of(config);
                RenderOptions render = RenderOptions.of(config);
                try (Generation.Result result = Generation.run(summary.plan(), dictionary, generation, budget, Cancellation.NONE);
                     Assembly assembly = Assembly.of(summary.plan(), result.result(), dictionary, generation, AssemblyOptions.of(config), render,
                             config.randomSeed(), budget, Cancellation.NONE);
                     CandidateRenderer renderer = CandidateRenderer.of(assembly, summary.plan(), dictionary, render, budget)) {
                    long rows = 0;
                    long chars = 0;
                    Iterator<Candidate> it = assembly.candidates(Cancellation.NONE);
                    while (it.hasNext()) {
                        chars += renderer.renderText(it.next()).length();
                        rows++;
                    }
                    return new Work(rows, "rendered " + rows + " candidates (" + chars + " characters) from an " + ab[0] + " x " + ab[1]
                            + " scenario (= exactly n)", budget.highWaterBytes());
                }
            });
        });
    }

    private static Stage sieve(long n, Path scratch, Function<List<ConfigResolver.Override>, EffectiveConfig> configure) throws IOException {
        long k = (n + 3) / 4;
        long[] fg = factorPair(k);
        Path file = scratch.resolve("sieve.json");
        Files.writeString(file, "{\"sheets\": {\"FW_Seq\": [[\"MODE\", \"FW_Combi(1)\"], [\"PAYLOAD\", \"FW_Combi(1)\"], [\"F\", \"FW_Combi(1)\"], "
                + "[\"G\", \"FW_Combi(1)\"]], \"MODE\": [[\"MODE = \\\"strict\\\"\"], [\"MODE = \\\"legacy\\\"\"]], \"PAYLOAD\": "
                + "[[\"PAYLOAD = \\\"ascii\\\"\"], [\"PAYLOAD = \\\"unicode\\\"\"]], \"F\": [" + sheet("f", fg[0], ";") + "], \"G\": ["
                + sheet("g", fg[1], ";") + "]}}", StandardCharsets.UTF_8);
        Files.writeString(scratch.resolve("sieve.constraints.json"), "{\"version\": 1, \"params\": {\"MODE\": {\"MODE = \\\"strict\\\"\": "
                + "{\"tier\": 2}, \"MODE = \\\"legacy\\\"\": {\"tier\": 1}}, \"PAYLOAD\": {\"PAYLOAD = \\\"ascii\\\"\": {\"unicode\": 0}, "
                + "\"PAYLOAD = \\\"unicode\\\"\": {\"unicode\": 1}}}, \"constraints\": [{\"id\": \"strict_ascii_ingress_policy\", \"sheets\": "
                + "[\"MODE\", \"PAYLOAD\"], \"when\": \"MODE.tier == 2 and PAYLOAD.unicode == 1\", \"gate\": {}}]}", StandardCharsets.UTF_8);
        EffectiveConfig effective = configure.apply(List.of());
        String corpus = "2 x 2 x " + fg[0] + " x " + fg[1] + " scenario with the legacy strict_ascii_ingress_policy when-rule, the first n "
                + "candidates sieved";
        return measure("sieve", n, corpus, () -> {
            ScenarioValidation.Opened opened = ScenarioValidation.open(file, effective);
            RunConfig config = opened.effective().config();
            return ScenarioValidation.prepare(file.toString(), opened.loaded(), config, (summary, dictionary, budget) -> {
                GenerationOptions generation = GenerationOptions.of(config);
                try (Generation.Result result = Generation.run(summary.plan(), dictionary, generation, budget, Cancellation.NONE);
                     Assembly assembly = Assembly.of(summary.plan(), result.result(), dictionary, generation, AssemblyOptions.of(config),
                             RenderOptions.of(config), config.randomSeed(), budget, Cancellation.NONE)) {
                    AssemblyCounts counts = assembly.count(Cancellation.NONE, BigInteger.valueOf(n));
                    BigInteger removed = counts.removedEarly().add(counts.removedLate() == null ? BigInteger.ZERO : counts.removedLate());
                    return new Work(counts.generated().longValue(), "sieved " + counts.generated() + " rows; " + removed + " removed",
                            budget.highWaterBytes());
                }
            });
        });
    }

    private static Stage executor(long n, Path scratch, Function<List<ConfigResolver.Override>, EffectiveConfig> configure) throws IOException {
        long[] ab = factorPair(n);
        Path file = scratch.resolve("executor.json");
        Files.writeString(file, "{\"sheets\": {\"FW_Seq\": [[\"X\", \"FW_Combi(1)\"], [\"Y\", \"FW_Combi(1)\"]], \"X\": [" + sheet("x=", ab[0], ";")
                + "], \"Y\": [" + sheet("y=", ab[1], " FW_VAR=0") + "]}}", StandardCharsets.UTF_8);
        EffectiveConfig effective = configure.apply(List.of(new ConfigResolver.Override("cf.execution.sut", "inline-metrics", "benchmark"),
                new ConfigResolver.Override("cf.results.retention", "aggregate-only", "benchmark")));
        String corpus = ab[0] + " x " + ab[1] + " samples through the inline-metrics plugin, aggregate retention";
        return measure("executor", n, corpus, () -> {
            ScenarioValidation.Opened opened = ScenarioValidation.open(file, effective);
            try (RunHandle handle = RunService.prepare(opened.loaded().spec(), opened.effective())) {
                RunSummary summary = handle.await();
                return new Work(summary.samples(), summary.samples() + " samples, " + summary.state() + ", " + opened.effective().config().workers()
                        + " workers", summary.execution().budgetHighWater());
            }
        });
    }

    private static Stage analyzer(long n, Function<List<ConfigResolver.Override>, EffectiveConfig> configure) throws IOException {
        EffectiveConfig effective = configure.apply(List.of(new ConfigResolver.Override("cf.analysis.goals", "lat:min,thr:max", "benchmark")));
        String corpus = n + " synthetic samples, goals lat:min and thr:max";
        return measure("analyzer", n, corpus, () -> {
            List<Analysis.Sample> samples = new ArrayList<>((int) Math.min(n, Integer.MAX_VALUE - 8));
            for (long i = 0; i < n; i++) {
                samples.add(new Analysis.Sample(Long.toString(i), Long.toString(i), 0, "", Analysis.Eligibility.ELIGIBLE, 0,
                        Map.of("lat", (double) (i * 7919 % 1000), "thr", (double) (i * 104729 % 997))));
            }
            MemoryBudget budget = MemoryBudget.of(effective.config());
            AnalysisReport report = Analysis.analyze(samples, Analysis.Options.of(effective.config()), budget);
            return new Work(report.samples(), report.samples() + " samples, front of " + report.front().size(), budget.highWaterBytes());
        });
    }

    // ---- environment -------------------------------------------------------------------------------------------

    /** capture_environment, for this runtime: hardware, operating system, JDK, heap, workers, seed and the application's hash. */
    static Map<String, Object> environment(RunConfig config) {
        Map<String, Object> hardware = new LinkedHashMap<>();
        hardware.put("cpu_count", (long) Runtime.getRuntime().availableProcessors());
        hardware.put("machine", System.getProperty("os.arch", "unknown"));
        hardware.put("processor", procLine("/proc/cpuinfo", "model name"));
        String mem = procLine("/proc/meminfo", "MemTotal");
        hardware.put("mem_total", mem.equals("unknown") ? mem : mem.replaceAll("\\s+", " ").strip());
        Map<String, Object> os = new LinkedHashMap<>();
        os.put("platform", System.getProperty("os.name") + "-" + System.getProperty("os.version") + "-" + System.getProperty("os.arch"));
        os.put("system", System.getProperty("os.name"));
        os.put("release", System.getProperty("os.version"));
        os.put("version", "unknown");
        Map<String, Object> toolchain = new LinkedHashMap<>();
        toolchain.put("java", Runtime.version().toString());
        toolchain.put("vm", System.getProperty("java.vm.name") + " (" + System.getProperty("java.vendor") + ")");
        toolchain.put("compiler", ToolProvider.getSystemJavaCompiler() != null ? "jdk.compiler" : "absent");
        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("heap_max_bytes", Runtime.getRuntime().maxMemory());
        jvm.put("gc", ManagementFactory.getGarbageCollectorMXBeans().stream().map(GarbageCollectorMXBean::getName).toList());
        jvm.put("workers", (long) config.workers());
        jvm.put("seed", config.randomSeed());
        jvm.put("memory_budget_bytes", config.memoryBudgetBytes());
        Map<String, Object> hashes = new LinkedHashMap<>();
        try {
            Path source = Path.of(Benchmark.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(source)) {
                hashes.put(source.getFileName().toString(), sha256(source));
            }
        } catch (Exception e) {
            // a component hash is best effort, as in the legacy capture
        }
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("hardware", hardware);
        env.put("os_kernel", os);
        env.put("toolchain", toolchain);
        env.put("jvm", jvm);
        env.put("component_hashes", hashes);
        env.put("db_settings", Map.of("note", "no database: results live in memory and checkpoints"));
        return env;
    }

    private static String procLine(String file, String key) {
        try {
            for (String line : Files.readAllLines(Path.of(file))) {
                if (line.startsWith(key)) {
                    return line.substring(line.indexOf(':') + 1).strip();
                }
            }
        } catch (IOException | RuntimeException e) {
            return "unknown";
        }
        return "unknown";
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(((DigestInputStream) in).getMessageDigest().digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }
}
