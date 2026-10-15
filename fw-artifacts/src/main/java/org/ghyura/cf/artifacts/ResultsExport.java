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

package org.ghyura.cf.artifacts;

import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SampleSlot;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

/**
 * Exports of a checkpoint's committed observations (work package M07): the legacy results_v2 rows, a metrics table of the
 * final samples and a summary report. Rows are ordered by candidate id, repeat, environment and attempt, so an export does
 * not depend on the order in which workers committed. Writing is the caller's; nothing here touches a file.
 */
public final class ResultsExport {

    /**
     * The columns of the legacy results_v2 table (Executor py_executor.py and ResultsV2Writer), without its surrogate id and
     * insertion time.
     */
    public static final List<String> RESULTS_V2_COLUMNS = List.of("run_id", "candidate_id", "attempt", "outcome", "verdict_code",
            "verdict_message", "duration_ms", "exit_code", "signal", "worker", "source_hash", "stdout_ref", "stderr_ref", "policy_id",
            "policy_hash", "repeat_idx", "env_id");

    private static final Comparator<Observation> ORDER = Comparator.comparing((Observation o) -> o.key().candidateId().hex())
            .thenComparingInt(o -> o.key().repeatIndex()).thenComparing(o -> o.key().environmentId()).thenComparingInt(o -> o.key().attempt());

    private ResultsExport() {
    }

    /**
     * Admitted UTF-8 export. The caller owns the checkpoint and returned bytes. The reservation covers indexing,
     * rendering, escaping and all encoding buffers together; no partial output is written on refusal.
     */
    @SuppressWarnings("try")
    public static byte[] encode(Checkpoint c, String kind, MemoryBudget budget) {
        long metricEntries = 0;
        for (Observation o : c.observations()) metricEntries = MemorySizes.add(metricEntries,
                o.domain().map(d -> d.metrics().size()).orElse(0));
        long indexes = MemorySizes.add(4096, MemorySizes.add(MemorySizes.multiply(320, c.observations().size()),
                MemorySizes.multiply(256, metricEntries)));
        try (MemoryBudget.Lease index = budget.reserve(indexes, "result export indexes")) {
            long chars = 8192;
            if (kind.equals("results")) {
                for (Observation o : c.observations()) {
                    long fields = o.key().runId().value().length() + (long) o.display().length() + o.sourceDigest().length()
                            + o.policyDigest().length() + o.key().environmentId().length();
                    chars = MemorySizes.add(chars, MemorySizes.add(512, MemorySizes.multiply(2, fields)));
                }
            } else if (kind.equals("csv")) {
                TreeSet<String> keys = new TreeSet<>();
                for (Observation o : c.observations()) o.domain().ifPresent(d -> keys.addAll(d.metrics().keySet()));
                for (String key : keys) chars = MemorySizes.add(chars, MemorySizes.add(3, MemorySizes.multiply(2, key.length())));
                // Missing cells still emit commas; nonempty doubles need at most 32 characters each.
                chars = MemorySizes.add(chars, MemorySizes.add(MemorySizes.multiply(keys.size(), c.observations().size()),
                        MemorySizes.multiply(32, metricEntries)));
                for (Observation o : c.observations()) chars = MemorySizes.add(chars,
                        MemorySizes.add(256, MemorySizes.multiply(2, o.display().length() + (long) o.key().environmentId().length())));
            } else if (kind.equals("report")) {
                for (String field : List.of(c.runId(), c.state(), c.scenarioPath(), c.scenarioDigest(), c.planDigest(),
                        c.dictionaryDigest(), c.policyDigest(), c.plugin(), c.pluginVersion(), c.configDigest()))
                    chars = MemorySizes.add(chars, MemorySizes.multiply(6, field.length()));
                chars = MemorySizes.add(chars, MemorySizes.multiply(128, c.observations().size()));
            } else throw new IllegalArgumentException("unknown result export kind: " + kind);
            if (chars > Integer.MAX_VALUE - 16L) throw MemorySizes.limit("result export exceeds the supported string size");
            try (MemoryBudget.Lease output = budget.reserve(MemorySizes.multiply(16, chars), "result export rendering and UTF-8 buffers")) {
                String text = switch (kind) {
                    case "results" -> resultsV2(c);
                    case "csv" -> metricsCsv(c);
                    default -> report(c);
                };
                return text.getBytes(StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Every committed attempt as a legacy results_v2 row (CSV with a header, UTF-8, CRLF-free): the display id as
     * candidate_id, the attempt counted from 1, the outcome name, the verdict code of a PASS or DOMAIN_FAIL, the duration in
     * whole milliseconds and the SHA-256 of the source, as the legacy writers recorded them. verdict_message, exit_code,
     * signal and the output references stay empty as the legacy Java writer left them; worker and policy_id are empty
     * because this build runs no worker processes and names no policy files (policy_hash is the policy's digest).
     */
    public static String resultsV2(Checkpoint c) {
        StringBuilder out = new StringBuilder(256);
        out.append(String.join(",", RESULTS_V2_COLUMNS)).append('\n');
        List<Observation> all = new ArrayList<>(c.observations());
        all.sort(ORDER);
        for (Observation o : all) {
            List<String> row = new ArrayList<>(RESULTS_V2_COLUMNS.size());
            row.add(o.key().runId().value());
            row.add(o.display());
            row.add(Integer.toString(o.key().attempt() + 1));
            row.add(o.outcome().name());
            row.add(o.domain().map(d -> Integer.toString(d.verdictCode())).orElse(""));
            row.add("");
            row.add(Long.toString(TimeUnit.NANOSECONDS.toMillis(o.elapsedNanos())));
            row.add("");
            row.add("");
            row.add("");
            row.add(o.sourceDigest());
            row.add("");
            row.add("");
            row.add("");
            row.add(o.policyDigest());
            row.add(Integer.toString(o.key().repeatIndex()));
            row.add(o.key().environmentId());
            csvRow(out, row);
        }
        return out.toString();
    }

    /** The final observation of every sample with one column per metric key (sorted), for spreadsheets. */
    public static String metricsCsv(Checkpoint c) {
        List<Observation> finals = finals(c);
        TreeSet<String> keys = new TreeSet<>();
        finals.forEach(o -> o.domain().ifPresent(d -> keys.addAll(d.metrics().keySet())));
        StringBuilder out = new StringBuilder();
        List<String> header = new ArrayList<>(List.of("candidate_id", "repeat_idx", "env_id", "attempt", "outcome", "verdict_code"));
        header.addAll(keys);
        csvRow(out, header);
        for (Observation o : finals) {
            List<String> row = new ArrayList<>(List.of(o.display(), Integer.toString(o.key().repeatIndex()), o.key().environmentId(),
                    Integer.toString(o.key().attempt() + 1), o.outcome().name(),
                    o.domain().map(d -> Integer.toString(d.verdictCode())).orElse("")));
            for (String k : keys) {
                Double v = o.domain().map(d -> d.metrics().get(k)).orElse(null);
                row.add(v == null ? "" : Double.toString(v));
            }
            csvRow(out, row);
        }
        return out.toString();
    }

    /** A summary of the checkpoint: identity, samples, attempts and outcome and verdict totals of the final observations. */
    public static String report(Checkpoint c) {
        List<Observation> finals = finals(c);
        Map<Outcome, Long> outcomes = new EnumMap<>(Outcome.class);
        for (Outcome o : Outcome.values()) {
            outcomes.put(o, 0L);
        }
        Map<Integer, Long> verdicts = new TreeMap<>();
        long retried = 0;
        for (Observation o : finals) {
            outcomes.merge(o.outcome(), 1L, Long::sum);
            o.domain().ifPresent(d -> verdicts.merge(d.verdictCode(), 1L, Long::sum));
            retried += o.key().attempt() > 0 ? 1 : 0;
        }
        StringBuilder xml = new StringBuilder(2048);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cf-export kind=\"report\" schema=\"1\">\n");
        xml.append("  <run id=\"").append(Xml.escape(c.runId())).append("\" state=\"").append(Xml.escape(c.state())).append("\" scenario=\"")
                .append(Xml.escape(c.scenarioPath())).append("\" scenarioDigest=\"").append(c.scenarioDigest()).append("\" planDigest=\"")
                .append(c.planDigest()).append("\" dictionaryDigest=\"").append(Xml.escape(c.dictionaryDigest())).append("\" policyDigest=\"")
                .append(c.policyDigest()).append("\" sut=\"").append(Xml.escape(c.plugin())).append("\" sutVersion=\"")
                .append(Xml.escape(c.pluginVersion())).append("\" configDigest=\"").append(c.configDigest()).append("\"/>\n");
        xml.append("  <samples samples=\"").append(finals.size()).append("\" attempts=\"").append(c.observations().size()).append("\" retried=\"")
                .append(retried).append("\" attachments=\"").append(c.attachments().size()).append("\" retention=\"full\"/>\n");
        xml.append("  <outcomes>\n");
        outcomes.forEach((o, n) -> xml.append("    <outcome name=\"").append(o).append("\" count=\"").append(n).append("\"/>\n"));
        xml.append("  </outcomes>\n  <verdicts>\n");
        verdicts.forEach((v, n) -> xml.append("    <verdict code=\"").append(v).append("\" count=\"").append(n).append("\"/>\n"));
        xml.append("  </verdicts>\n</cf-export>\n");
        return xml.toString();
    }

    /** The latest attempt of every sample, in candidate id, repeat and environment order. */
    static List<Observation> finals(Checkpoint c) {
        Map<SampleSlot, Observation> latest = new LinkedHashMap<>();
        for (Observation o : c.observations()) {
            latest.merge(o.key().slot(), o, (a, b) -> b.key().attempt() > a.key().attempt() ? b : a);
        }
        List<Observation> out = new ArrayList<>(latest.values());
        out.sort(ORDER);
        return out;
    }

    /** One CSV row (RFC 4180): a field with a comma, quote or line break is quoted, its quotes doubled. */
    private static void csvRow(StringBuilder out, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            String f = fields.get(i);
            if (i > 0) {
                out.append(',');
            }
            if (f.indexOf(',') >= 0 || f.indexOf('"') >= 0 || f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0) {
                out.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                out.append(f);
            }
        }
        out.append('\n');
    }
}
