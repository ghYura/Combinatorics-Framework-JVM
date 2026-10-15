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
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.SampleSlot;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.results.LegacyMetricsCorpus;
import org.ghyura.cf.results.ResultRepository;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Result triage (GEN:diagnostics-reporting-release; the legacy bundle/triage.py behind {@code bundle triage}): a finished
 * run's failures grouped into distinct findings, the minimal witness of each (the failing candidate with the fewest source
 * bytes) and the declared values whose failure rate departs from the run's (lift). It reads only what the run persisted
 * and never executes a candidate.
 *
 * <p>A key whose value never varies among the passing candidates classifies a failure (it says what went wrong); any other
 * key, and every numeric one, describes the conditions. When every candidate failed, a constant or lopsided key (four to
 * one or more) is the outcome and a balanced one an axis. Findings group the failures by their non-empty classifying
 * values, most frequent first; enrichment reports the values of descriptive keys with at most 24 distinct values that do
 * not merely restate the verdict and are carried by at least 1% (and two) of the candidates, ranked by how much of the
 * corpus their effect explains.</p>
 *
 * <p>Two inputs: a legacy run directory (its metrics.kv, executor-summary.json and src/, read exactly as the legacy tool
 * read them, except that a source_ref outside src/ is not measured) and a checkpoint of this framework, whose final
 * samples are the corpus: a harvested legacy metrics line is read as the legacy executor wrote it into metrics.kv, and a
 * typed domain observation contributes its verdict, attributes and metrics; source sizes come from the kept source
 * attachments.</p>
 */
public final class Triage {

    public static final String SCHEMA = "bundle.triage/v1";

    /** Keys the executor prepends for provenance: they identify a candidate and take no part in grouping or enrichment. */
    public static final Set<String> PROVENANCE_KEYS = Set.of("candidate_id", "source_ref", "run_id", "repeat_idx", "env_id");

    /** The verdict token; non-zero means the oracle fired. */
    public static final String VERDICT_KEY = "FW_VAR";

    static final int MAX_AXIS_VALUES = 24;
    static final int OUTCOME_SKEW = 4;
    static final String REDACTED = "***REDACTED***";
    private static final List<String> SECRET_FRAGMENTS = List.of("password", "token", "secret", "api_key", "credential");

    static final String NO_CORPUS = "no K=V corpus: metrics.kv is absent or empty, so failures cannot be grouped. A candidate "
            + "contributes a line only when it prints one starting with 'app=' and containing 'FW_VAR=' -- check the spec's TAIL "
            + "slot against that rule.";
    static final String NO_VERDICTS = "no sample of this run ended with a domain verdict, so failures cannot be grouped; samples "
            + "that were BROKEN, TIMEOUT, INFRA_FAIL, SKIPPED or CANCELLED are counted in the outcomes only.";
    static final String UNDIFFERENTIATED = "every failure shares one signature because the candidates reported nothing that varies "
            + "between them; the campaign may be saturated by a single defect, or the oracle may not be reporting what "
            + "distinguishes one failure from another.";

    private Triage() {
    }

    /** One candidate's reported fields, in the order it reported them (a repeated key keeps its first place, last value). */
    public record Record(String candidateId, String sourceRef, boolean failed, Map<String, String> fields) {
        public Record {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
        }
    }

    /** One distinct way the run failed: its classifying values, how many failures carry them, and its minimal witness. */
    public record Finding(List<Map.Entry<String, String>> signature, int count, List<String> members, Record witness,
                          Long witnessBytes) {
        public Finding {
            signature = List.copyOf(signature);
            members = List.copyOf(members);
        }

        /** The signature as {@code k=v} pairs, or {@code (undifferentiated)}. */
        public String label() {
            StringBuilder s = new StringBuilder();
            for (Map.Entry<String, String> e : signature) {
                s.append(s.isEmpty() ? "" : " ").append(e.getKey()).append('=').append(e.getValue());
            }
            return s.isEmpty() ? "(undifferentiated)" : s.toString();
        }
    }

    /** One declared value: how often it fails and the lift of that rate over the run's. */
    public record Enrichment(String key, String value, int failing, int total, double rate, double lift, double coverage, double score) {
    }

    /** What the run itself summarized: its id, how many samples it processed and its outcome totals (JSON values as read). */
    public record Summary(String runId, Object processed, Map<String, Object> outcomes) {
        public static final Summary NONE = new Summary("", null, Map.of());

        public Summary {
            outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        }
    }

    /** The triage of one run. */
    public record Report(String runId, String corpusPath, int records, int failing, Map<String, Object> outcomes, Object processed,
                         List<String> classifying, List<String> descriptive, List<Finding> findings, List<Enrichment> enrichment,
                         List<String> notes) {
        public Report {
            outcomes = Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
            classifying = List.copyOf(classifying);
            descriptive = List.copyOf(descriptive);
            findings = List.copyOf(findings);
            enrichment = List.copyOf(enrichment);
            notes = List.copyOf(notes);
        }
    }

    // ---- the corpus ------------------------------------------------------------------------------------------------

    /** The records of a K=V corpus: one per line that names a candidate_id, its verdict from FW_VAR (absent reads as 0). */
    public static List<Record> parse(String text) {
        List<Record> out = new ArrayList<>();
        for (String line : Py.splitLines(text)) {
            if (Py.isBlank(line)) {
                continue;
            }
            Map<String, String> fields = keyValues(line);
            String id = fields.getOrDefault("candidate_id", "");
            if (!id.isEmpty()) {
                out.add(new Record(id, fields.getOrDefault("source_ref", ""), failed(fields.getOrDefault(VERDICT_KEY, "0")), fields));
            }
        }
        return out;
    }

    /** Every {@code \w+=\S*} of a line, Unicode-aware as a str pattern is, scanning on after each match. */
    static Map<String, String> keyValues(String line) {
        Map<String, String> kv = new LinkedHashMap<>();
        int k = 0;
        int n = line.length();
        while (k < n) {
            int end = k;
            while (end < n && Py.isWord(line.codePointAt(end))) {
                end += Character.charCount(line.codePointAt(end));
            }
            if (end > k && end < n && line.charAt(end) == '=') {
                int stop = end + 1;
                while (stop < n && !Py.isSpace(line.codePointAt(stop))) {
                    stop += Character.charCount(line.codePointAt(stop));
                }
                kv.put(line.substring(k, end), line.substring(end + 1, stop));
                k = stop;
            } else {
                // every start inside the run ends where this one did, before the same character
                k = end > k ? end : k + Character.charCount(line.codePointAt(k));
            }
        }
        return kv;
    }

    /** {@code int(v) != 0}, or, when int() refuses it, whether it is non-empty. */
    static boolean failed(String verdict) {
        BigDecimal value = Py.integer(verdict);
        return value != null ? value.signum() != 0 : !verdict.isEmpty();
    }

    // ---- the analysis ------------------------------------------------------------------------------------------

    /**
     * The triage of a corpus.
     *
     * @param sourceBytes the size of a source_ref's file in bytes, or null when it is not known
     * @param emptyNote   the note for an empty corpus
     */
    public static Report triage(List<Record> records, Summary summary, Function<String, Long> sourceBytes, String corpusPath,
                                String emptyNote) {
        List<String> notes = new ArrayList<>();
        if (records.isEmpty()) {
            notes.add(emptyNote);
        }
        List<String> classifying = new ArrayList<>();
        List<String> descriptive = new ArrayList<>();
        classify(records, classifying, descriptive);
        List<Finding> findings = group(records, classifying, sourceBytes);
        List<Enrichment> enrichment = enrichment(records, descriptive);
        int failing = (int) records.stream().filter(Record::failed).count();
        if (!records.isEmpty() && findings.size() == 1 && classifying.isEmpty()) {
            notes.add(UNDIFFERENTIATED);
        }
        String runId = records.isEmpty() ? summary.runId() : records.getFirst().fields().getOrDefault("run_id", "");
        Object processed = summary.processed() != null ? summary.processed() : (Object) (long) records.size();
        return new Report(runId == null ? "" : runId, corpusPath, records.size(), failing, summary.outcomes(), processed, classifying,
                descriptive, findings, enrichment, notes);
    }

    private static void classify(List<Record> records, List<String> classifying, List<String> descriptive) {
        List<Record> passing = records.stream().filter(r -> !r.failed()).toList();
        TreeSet<String> keys = new TreeSet<>(Py::compare);
        records.forEach(r -> keys.addAll(r.fields().keySet()));
        keys.removeAll(PROVENANCE_KEYS);
        keys.remove(VERDICT_KEY);
        for (String key : keys) {
            if (isMeasurement(key, records)) {
                descriptive.add(key);
                continue;
            }
            boolean outcome;
            if (!passing.isEmpty()) {
                Set<String> values = new HashSet<>();
                passing.forEach(r -> values.add(r.fields().get(key)));
                outcome = values.size() <= 1;
            } else {
                outcome = looksLikeOutcome(key, records);
            }
            (outcome ? classifying : descriptive).add(key);
        }
    }

    /** With no passing candidate to compare against: a constant or lopsided key is the outcome, a balanced one an axis. */
    private static boolean looksLikeOutcome(String key, List<Record> records) {
        Map<String, Integer> counts = new HashMap<>();
        for (Record r : records) {
            String v = r.fields().get(key);
            if (v != null) {
                counts.merge(v, 1, Integer::sum);
            }
        }
        if (counts.size() <= 1) {
            return true;
        }
        int lo = Collections.min(counts.values());
        int hi = Collections.max(counts.values());
        return hi >= lo * OUTCOME_SKEW;
    }

    /** A key every observed (non-empty) value of which float() reads is a measurement, never a failure category. */
    private static boolean isMeasurement(String key, List<Record> records) {
        Set<String> values = new HashSet<>();
        for (Record r : records) {
            String v = r.fields().get(key);
            if (v != null && !v.isEmpty()) {
                values.add(v);
            }
        }
        return !values.isEmpty() && values.stream().allMatch(Py::isFloat);
    }

    private static List<Finding> group(List<Record> records, List<String> classifying, Function<String, Long> sourceBytes) {
        Map<List<Map.Entry<String, String>>, List<Record>> buckets = new LinkedHashMap<>();
        for (Record r : records) {
            if (!r.failed()) {
                continue;
            }
            List<Map.Entry<String, String>> signature = new ArrayList<>();
            for (String k : classifying) {
                String v = r.fields().getOrDefault(k, "");
                if (!v.isEmpty()) {
                    signature.add(Map.entry(k, v));
                }
            }
            buckets.computeIfAbsent(signature, s -> new ArrayList<>()).add(r);
        }
        Map<String, Record> byId = new HashMap<>();
        records.forEach(r -> byId.put(r.candidateId(), r));
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<List<Map.Entry<String, String>>, List<Record>> b : buckets.entrySet()) {
            List<String> members = b.getValue().stream().map(Record::candidateId).toList();
            // the smallest source wins; without sizes, the first member (the record kept for an id is its last line)
            Record best = null;
            Long bestSize = null;
            for (String id : members) {
                Record r = byId.get(id);
                if (r == null) {
                    continue;
                }
                Long size = r.sourceRef().isEmpty() ? null : sourceBytes.apply(r.sourceRef());
                if (best == null || (size != null && (bestSize == null || size < bestSize))) {
                    best = r;
                    bestSize = size;
                }
            }
            findings.add(new Finding(b.getKey(), members.size(), members, best, bestSize));
        }
        findings.sort(Comparator.comparingInt((Finding f) -> -f.count()).thenComparing(Finding::label, Py::compare));
        return findings;
    }

    private static List<Enrichment> enrichment(List<Record> records, List<String> descriptive) {
        int total = records.size();
        int failures = (int) records.stream().filter(Record::failed).count();
        if (total == 0 || failures == 0) {
            return List.of();
        }
        double base = (double) failures / total;
        int minSupport = Math.max(2, (int) (total * 0.01));
        List<Enrichment> rows = new ArrayList<>();
        for (String key : descriptive) {
            Map<String, int[]> counts = new TreeMap<>(Py::compare);
            for (Record r : records) {
                String v = r.fields().get(key);
                if (v != null) {
                    int[] c = counts.computeIfAbsent(v, x -> new int[2]);
                    c[1]++;
                    if (r.failed()) {
                        c[0]++;
                    }
                }
            }
            if (counts.size() > MAX_AXIS_VALUES) {
                continue;
            }
            if (counts.values().stream().allMatch(c -> c[1] > 0 && (c[0] == 0 || c[0] == c[1]))) {
                continue;
            }
            for (Map.Entry<String, int[]> e : counts.entrySet()) {
                int bad = e.getValue()[0];
                int seen = e.getValue()[1];
                if (seen < minSupport) {
                    continue;
                }
                double rate = (double) bad / seen;
                double lift = rate / base;
                double share = (double) seen / total;
                rows.add(new Enrichment(key, e.getKey(), bad, seen, Py.round(rate, 4), Py.round(lift, 3), Py.round(share, 4),
                        Py.round(Math.abs(lift - 1.0) * share, 4)));
            }
        }
        rows.sort(Comparator.comparingDouble((Enrichment r) -> -r.score()).thenComparing(Enrichment::key, Py::compare)
                .thenComparing(Enrichment::value, Py::compare));
        return rows;
    }

    // ---- the inputs --------------------------------------------------------------------------------------------

    /**
     * The run a legacy target names: a run directory (it holds metrics.kv or executor-summary.json), else the last of
     * {@code runs/*} and then of {@code *}{@code /runs/*} in name order.
     *
     * @throws FrameworkException INPUT_UNREADABLE when there is none
     */
    public static Path resolveRun(Path target) {
        if (Files.isRegularFile(target.resolve("metrics.kv")) || Files.isRegularFile(target.resolve("executor-summary.json"))) {
            return target;
        }
        List<Path> runs = children(target.resolve("runs"));
        if (runs.isEmpty()) {
            for (Path db : children(target)) {
                runs.addAll(children(db.resolve("runs")));
            }
        }
        if (runs.isEmpty()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, "no run directory under " + target + ": expected it "
                    + "to hold metrics.kv / executor-summary.json, or to contain runs/<run-id>/, or to be a checkpoint file",
                    SourceLocation.of(target.toString())));
        }
        runs.sort(Comparator.comparing(Path::toString, Py::compare));
        return runs.getLast();
    }

    private static List<Path> children(Path dir) {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            // glob's '*' leaves out names that start with a dot
            entries.forEach(p -> {
                if (!p.getFileName().toString().startsWith(".")) {
                    out.add(p);
                }
            });
        } catch (IOException e) {
            return out;
        }
        return out;
    }

    /** The triage of a legacy run directory, read within {@code budget}. */
    public static Report ofLegacyRun(Path run, MemoryBudget budget) {
        Path metrics = run.resolve("metrics.kv");
        List<Record> records = List.of();
        if (Files.isRegularFile(metrics)) {
            try (AnalysisInput input = AnalysisInput.read(metrics, budget, 384, true)) {
                records = parse(input.text);
            }
        }
        Summary summary = Summary.NONE;
        Path summaryFile = run.resolve("executor-summary.json");
        if (Files.isRegularFile(summaryFile)) {
            summary = summary(summaryFile, budget);
        }
        Path src = run.resolve("src").toAbsolutePath().normalize();
        return triage(records, summary, ref -> size(src, ref), metrics.toString(), NO_CORPUS);
    }

    /** The executor summary, or none when it is not a JSON object (the legacy tool ignored one it could not read). */
    private static Summary summary(Path file, MemoryBudget budget) {
        try (AnalysisInput input = AnalysisInput.read(file, budget, 16)) {
            if (!(Json.parse(input.text, file.toString()) instanceof Map<?, ?> map)) {
                return Summary.NONE;
            }
            Map<String, Object> outcomes = new LinkedHashMap<>();
            if (map.get("outcomes") instanceof Map<?, ?> o) {
                o.forEach((k, v) -> outcomes.put((String) k, python(v)));
            }
            Object runId = map.get("run_id");
            return new Summary(runId instanceof String s ? s : runId == null ? "" : String.valueOf(python(runId)),
                    map.containsKey("processed") ? python(map.get("processed")) : null, outcomes);
        } catch (FrameworkException e) {
            return Summary.NONE;
        }
    }

    /** A JSON number as Python reads it: an integer stays one, any other number is a float. */
    private static Object python(Object v) {
        return v instanceof BigDecimal d ? (Object) d.doubleValue() : v;
    }

    /** The size of src/REF, or null when it is not a regular file inside src/. */
    private static Long size(Path src, String ref) {
        try {
            Path file = src.resolve(ref).normalize();
            if (!file.startsWith(src) || !Files.isRegularFile(file)) {
                return null;
            }
            return Files.size(file);
        } catch (InvalidPathException | IOException e) {
            return null;
        }
    }

    /**
     * The triage of a run of this framework from its checkpoint: the final attempt of every sample, in the legacy corpus
     * order (display id, repeat, environment); outcome totals over every final sample.
     */
    public static Report ofCheckpoint(Checkpoint checkpoint, String path, MemoryBudget budget) {
        Map<SampleSlot, Observation> latest = new LinkedHashMap<>();
        for (Observation o : checkpoint.observations()) {
            latest.merge(o.key().slot(), o, (a, b) -> b.key().attempt() > a.key().attempt() ? b : a);
        }
        List<Observation> finals = new ArrayList<>(latest.values());
        try (MemoryBudget.Lease _ = budget.reserve(MemorySizes.add(65_536, MemorySizes.multiply(4_096, finals.size())),
                "triage of checkpoint " + path)) {
            finals.sort(Comparator.comparing(Observation::display).thenComparingInt(o -> o.key().repeatIndex())
                    .thenComparing(o -> o.key().environmentId()).thenComparing(o -> o.key().candidateId().hex()));
            boolean repeatAware = finals.stream().anyMatch(o -> o.key().repeatIndex() > 0 || !o.key().environmentId().isEmpty());
            List<Record> records = new ArrayList<>();
            Map<String, Object> outcomes = new LinkedHashMap<>();
            for (Outcome outcome : Outcome.values()) {
                outcomes.put(outcome.name(), 0L);
            }
            for (Observation o : finals) {
                outcomes.merge(o.outcome().name(), 1L, (a, b) -> (Long) a + (Long) b);
                if (o.domain().isEmpty()) {
                    continue;
                }
                DomainObservation domain = o.domain().get();
                if (domain.attributes().containsKey(LegacyMetricsCorpus.LINE_ATTRIBUTE)) {
                    records.addAll(parse(LegacyMetricsCorpus.format(List.of(o), checkpoint.runId(), repeatAware)));
                } else {
                    records.add(typed(o, domain, checkpoint.runId(), repeatAware));
                }
            }
            Map<String, Long> sources = new HashMap<>();
            Map<SampleKey, String> displays = new HashMap<>();
            checkpoint.observations().forEach(o -> displays.put(o.key(), o.display()));
            for (ResultRepository.Attachment a : checkpoint.attachments()) {
                String display = displays.get(a.key());
                if (a.name().equals("source") && display != null) {
                    sources.putIfAbsent(display.replace(' ', '_') + ".java", a.bytes());
                }
            }
            return triage(records, new Summary(checkpoint.runId(), (long) finals.size(), outcomes), sources::get, path, NO_VERDICTS);
        }
    }

    /** A typed observation as the line its candidate would have printed: the verdict, then its attributes and metrics by key. */
    private static Record typed(Observation o, DomainObservation domain, String runId, boolean repeatAware) {
        String id = o.display().replace(' ', '_');
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("candidate_id", id);
        fields.put("source_ref", id + ".java");
        if (!runId.isBlank()) {
            fields.put("run_id", runId.replace(' ', '_'));
        }
        if (repeatAware) {
            fields.put("repeat_idx", Integer.toString(o.key().repeatIndex()));
            fields.put("env_id", o.key().environmentId().replace(' ', '_'));
        }
        fields.put(VERDICT_KEY, Integer.toString(domain.verdictCode()));
        new TreeMap<>(domain.attributes()).forEach(fields::putIfAbsent);
        new TreeMap<>(domain.metrics()).forEach((k, v) -> fields.putIfAbsent(k, v == Math.rint(v) && Math.abs(v) < 1e15
                ? Long.toString((long) v.doubleValue()) : Py.repr(v)));
        return new Record(id, id + ".java", domain.verdictCode() != 0, fields);
    }

    // ---- the reports -------------------------------------------------------------------------------------------

    /** The legacy text report, enrichment cut at {@code top} rows as {@code rows[:top]} cuts. */
    public static String text(Report r, int top) {
        List<String> out = new ArrayList<>();
        out.add("triage " + (r.runId().isEmpty() ? "(run id unknown)" : r.runId()));
        out.add("  candidates " + r.records() + "   failing " + r.failing() + "   distinct findings " + r.findings().size());
        if (!r.outcomes().isEmpty()) {
            StringBuilder s = new StringBuilder("  outcomes   ");
            boolean first = true;
            for (Map.Entry<String, Object> e : sorted(r.outcomes()).entrySet()) {
                if (truthy(e.getValue())) {
                    s.append(first ? "" : "  ").append(e.getKey()).append('=').append(str(e.getValue()));
                    first = false;
                }
            }
            out.add(s.toString());
        }
        r.notes().forEach(n -> out.add("  ! " + n));
        if (!r.findings().isEmpty()) {
            out.add("");
            out.add("FINDINGS  (one row per distinct failure, most frequent first)");
            int i = 0;
            for (Finding f : r.findings()) {
                i++;
                out.add("  " + i + ". " + Py.left(f.label(), 46) + " " + Py.right(Integer.toString(f.count()), 5) + " candidates  "
                        + Py.right(Py.fixed(100.0 * share(f, r), 1), 5) + "%");
                if (f.witness() != null) {
                    Record w = f.witness();
                    String size = f.witnessBytes() != null ? f.witnessBytes() + "B" : "?";
                    out.add("     minimal witness  " + (w.sourceRef().isEmpty() ? w.candidateId() : w.sourceRef()) + "  (" + size + ")");
                    StringBuilder detail = new StringBuilder();
                    for (Map.Entry<String, String> e : detail(w).entrySet()) {
                        if (!e.getKey().equals(VERDICT_KEY)) {
                            detail.append(detail.isEmpty() ? "" : "  ").append(e.getKey()).append('=').append(e.getValue());
                        }
                    }
                    if (!detail.isEmpty()) {
                        out.add("     " + Py.head(detail.toString(), 200));
                    }
                }
            }
        }
        if (!r.enrichment().isEmpty()) {
            out.add("");
            out.add("AXIS ENRICHMENT  (lift = how much this value changes the odds of failing; 1.0 = noise. Ranked by how much of "
                    + "the corpus the effect explains.)");
            int n = r.enrichment().size();
            int end = top >= 0 ? Math.min(top, n) : Math.max(0, n + top);
            for (Enrichment e : r.enrichment().subList(0, end)) {
                out.add("  " + Py.left(e.key(), 14) + " " + Py.left(Py.head(e.value(), 24), 24) + " " + Py.right(Integer.toString(e.failing()), 4)
                        + "/" + Py.left(Integer.toString(e.total()), 5) + "  " + Py.right(Py.fixed(100.0 * e.rate(), 1), 5) + "%  lift "
                        + Py.right(Py.fixed(e.lift(), 2), 5) + "  covers " + Py.right(Py.fixed(100.0 * e.coverage(), 1), 4) + "%");
            }
        }
        return String.join("\n", out);
    }

    private static double share(Finding f, Report r) {
        return r.failing() > 0 ? Py.round((double) f.count() / r.failing(), 4) : 0.0;
    }

    /** The witness's fields without its provenance. */
    private static Map<String, String> detail(Record w) {
        Map<String, String> detail = new LinkedHashMap<>(w.fields());
        detail.keySet().removeAll(PROVENANCE_KEYS);
        return detail;
    }

    private static <V> Map<String, V> sorted(Map<String, V> map) {
        Map<String, V> out = new TreeMap<>(Py::compare);
        out.putAll(map);
        return out;
    }

    private static boolean truthy(Object v) {
        return switch (v) {
            case null -> false;
            case Boolean b -> b;
            case Long l -> l != 0;
            case Double d -> d != 0;
            case String s -> !s.isEmpty();
            case Map<?, ?> m -> !m.isEmpty();
            case List<?> l -> !l.isEmpty();
            default -> true;
        };
    }

    /** Python's {@code str()} of a JSON value read from the run. */
    private static String str(Object v) {
        return switch (v) {
            case Boolean b -> b ? "True" : "False";
            case Double d -> Py.repr(d);
            default -> String.valueOf(v);
        };
    }

    /** The bundle.triage/v1 document as write_json_atomic wrote it: keys sorted, secret-shaped keys redacted, one final newline. */
    public static String json(Report r) {
        Map<String, Object> corpus = new LinkedHashMap<>();
        corpus.put("path", r.corpusPath());
        corpus.put("records", (long) r.records());
        corpus.put("failing", (long) r.failing());
        corpus.put("outcomes", r.outcomes());
        corpus.put("processed", r.processed());
        List<Object> findings = new ArrayList<>();
        for (Finding f : r.findings()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("signature", f.signature().stream().map(e -> List.of(e.getKey(), e.getValue())).toList());
            m.put("label", f.label());
            m.put("count", (long) f.count());
            m.put("share", share(f, r));
            if (f.witness() == null) {
                m.put("witness", null);
            } else {
                Map<String, Object> w = new LinkedHashMap<>();
                w.put("candidate_id", f.witness().candidateId());
                w.put("source_ref", f.witness().sourceRef());
                w.put("source_bytes", f.witnessBytes());
                w.put("detail", detail(f.witness()));
                m.put("witness", w);
            }
            findings.add(m);
        }
        List<Object> enrichment = new ArrayList<>();
        for (Enrichment e : r.enrichment()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", e.key());
            m.put("value", e.value());
            m.put("failing", (long) e.failing());
            m.put("total", (long) e.total());
            m.put("rate", e.rate());
            m.put("lift", e.lift());
            m.put("coverage", e.coverage());
            m.put("score", e.score());
            enrichment.add(m);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schema", SCHEMA);
        data.put("run_id", r.runId());
        data.put("corpus", corpus);
        data.put("keys", Map.of("classifying", r.classifying(), "descriptive", r.descriptive()));
        data.put("findings", findings);
        data.put("enrichment", enrichment);
        data.put("notes", r.notes());
        return Py.json(redact(data)) + "\n";
    }

    /** Every mapping value whose key looks like a secret replaced, recursively (the legacy jsonio.redact). */
    static Object redact(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((k, v) -> out.put((String) k, secret((String) k) ? REDACTED : redact(v)));
            return out;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(Triage::redact).toList();
        }
        return value;
    }

    private static boolean secret(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return SECRET_FRAGMENTS.stream().anyMatch(k::contains);
    }

    /** The report as an XML element (every finding and enrichment row, no secret redaction: the text report shows them too). */
    public static String xml(Report r, String indent) {
        StringBuilder x = new StringBuilder();
        x.append(indent).append("<triage schema=\"").append(SCHEMA).append("\" run=\"").append(Xml.escape(r.runId())).append("\" corpus=\"")
                .append(Xml.escape(r.corpusPath())).append("\" records=\"").append(r.records()).append("\" failing=\"").append(r.failing())
                .append("\" findings=\"").append(r.findings().size()).append("\" processed=\"").append(Xml.escape(str(r.processed())))
                .append("\">\n");
        String in = indent + "  ";
        sorted(r.outcomes()).forEach((k, v) -> x.append(in).append("<outcome name=\"").append(Xml.escape(k)).append("\" count=\"")
                .append(Xml.escape(str(v))).append("\"/>\n"));
        r.classifying().forEach(k -> x.append(in).append("<key name=\"").append(Xml.escape(k)).append("\" role=\"classifying\"/>\n"));
        r.descriptive().forEach(k -> x.append(in).append("<key name=\"").append(Xml.escape(k)).append("\" role=\"descriptive\"/>\n"));
        int rank = 0;
        for (Finding f : r.findings()) {
            x.append(in).append("<finding rank=\"").append(++rank).append("\" label=\"").append(Xml.escape(f.label())).append("\" count=\"")
                    .append(f.count()).append("\" share=\"").append(Py.repr(share(f, r))).append("\">\n");
            for (Map.Entry<String, String> e : f.signature()) {
                x.append(in).append("  <signature key=\"").append(Xml.escape(e.getKey())).append("\" value=\"").append(Xml.escape(e.getValue()))
                        .append("\"/>\n");
            }
            if (f.witness() != null) {
                x.append(in).append("  <witness candidate=\"").append(Xml.escape(f.witness().candidateId())).append("\" source=\"")
                        .append(Xml.escape(f.witness().sourceRef())).append('"');
                if (f.witnessBytes() != null) {
                    x.append(" bytes=\"").append(f.witnessBytes()).append('"');
                }
                x.append(">\n");
                detail(f.witness()).forEach((k, v) -> x.append(in).append("    <field key=\"").append(Xml.escape(k)).append("\" value=\"")
                        .append(Xml.escape(v)).append("\"/>\n"));
                x.append(in).append("  </witness>\n");
            }
            x.append(in).append("</finding>\n");
        }
        for (Enrichment e : r.enrichment()) {
            x.append(in).append("<enrichment key=\"").append(Xml.escape(e.key())).append("\" value=\"").append(Xml.escape(e.value()))
                    .append("\" failing=\"").append(e.failing()).append("\" total=\"").append(e.total()).append("\" rate=\"").append(Py.repr(e.rate()))
                    .append("\" lift=\"").append(Py.repr(e.lift())).append("\" coverage=\"").append(Py.repr(e.coverage())).append("\" score=\"")
                    .append(Py.repr(e.score())).append("\"/>\n");
        }
        r.notes().forEach(n -> x.append(in).append("<note>").append(Xml.escape(n)).append("</note>\n"));
        x.append(indent).append("</triage>\n");
        return x.toString();
    }

    /** The JSON report's bytes, for writing. */
    public static byte[] jsonBytes(Report r) {
        return json(r).getBytes(StandardCharsets.UTF_8);
    }
}
