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

import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.SampleSlot;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * A run's final samples as a metrics corpus, the file results analysis reads (step 8, {@code analyze --results}): one line
 * per sample of key=value pairs, its identity (candidate_id, repeat_idx, env_id, run_id), its attributes and metrics, and
 * its outcome as FW_VAR, 0 for a PASS and the verdict code (at least 1) for a DOMAIN_FAIL, so the corpus is read as the run's
 * own analysis read the samples. A sample without a domain verdict (an error, a timeout, ...) has no metrics and the corpus
 * has no way to say "no verdict" (a line without FW_VAR is read as passing): it is listed after the samples as a comment,
 * which the reader skips. Sample lines come first, so the physical line N of a coverage {lineNo} is the Nth sample. Samples
 * are named by their display ids, as the run report names them; the analysis breaks ties by name, so among equally good
 * samples another one may be listed first than in the run's own analysis, with the same counts, measures and scores.
 */
public final class CorpusExport {

    /** A key the corpus reader reads back as the same key. */
    private static final Pattern KEY = Pattern.compile("[A-Za-z_][\\w.\\-]*");
    /** Keys the reader takes as the identity or the verdict, never as an attribute or a metric. */
    private static final Set<String> RESERVED = Set.of("candidate_id", "repeat_idx", "env_id", "source_ref", "run_id", "fw_var",
            "fw_custom_var");
    private static final Comparator<Observation> ORDER = Comparator.comparing((Observation o) -> o.key().candidateId().hex())
            .thenComparingInt(o -> o.key().repeatIndex()).thenComparing(o -> o.key().environmentId());

    private CorpusExport() {
    }

    /** The corpus as UTF-8, admitted against the budget with its rendering buffers; the caller owns the checkpoint. */
    @SuppressWarnings("try")
    public static byte[] encode(Checkpoint c, MemoryBudget budget) {
        long chars = 4096;
        for (Observation o : c.observations()) {
            long fields = o.display().length() + (long) o.key().environmentId().length() + c.runId().length();
            if (o.domain().isPresent()) {
                for (var e : o.domain().get().metrics().entrySet()) fields = MemorySizes.add(fields, 32L + e.getKey().length());
                for (var e : o.domain().get().attributes().entrySet()) {
                    fields = MemorySizes.add(fields, 2L + e.getKey().length() + e.getValue().length());
                }
            }
            chars = MemorySizes.add(chars, MemorySizes.add(128, fields));
        }
        if (chars > Integer.MAX_VALUE - 16L) throw MemorySizes.limit("metrics corpus export exceeds the supported string size");
        try (MemoryBudget.Lease output = budget.reserve(MemorySizes.multiply(16, chars), "metrics corpus rendering and UTF-8 buffers")) {
            return text(c).getBytes(StandardCharsets.UTF_8);
        }
    }

    /** The corpus text: the sample lines, then the samples without a verdict as comments. */
    static String text(Checkpoint c) {
        StringBuilder out = new StringBuilder(256);
        List<Observation> unverdicted = new ArrayList<>();
        for (Observation o : finals(c)) {
            if (o.domain().isEmpty() || (o.outcome() != Outcome.PASS && o.outcome() != Outcome.DOMAIN_FAIL)) {
                unverdicted.add(o);
                continue;
            }
            identity(out, c, o);
            var domain = o.domain().get();
            new TreeMap<>(domain.attributes()).forEach((key, value) -> pair(out, key, value));
            new TreeMap<>(domain.metrics()).forEach((key, value) -> {
                if (Double.isFinite(value)) pair(out, key, BigDecimal.valueOf(value).stripTrailingZeros().toPlainString());
            });
            int verdict = o.outcome() == Outcome.PASS ? 0 : domain.verdictCode() != 0 ? domain.verdictCode() : 1;
            out.append(" FW_VAR=").append(verdict).append('\n');
        }
        if (!unverdicted.isEmpty()) {
            out.append("# ").append(unverdicted.size()).append(" sample(s) ended without a domain verdict and carry no metrics; ")
                    .append("they are listed as comments, which analysis skips:\n");
            for (Observation o : unverdicted) {
                out.append("# ");
                identity(out, c, o);
                out.append(" outcome=").append(o.outcome().name()).append('\n');
            }
        }
        return out.toString();
    }

    /** The latest attempt of every sample, in candidate id, repeat and environment order: the samples a run's analysis reads. */
    public static List<Observation> finals(Checkpoint c) {
        Map<SampleSlot, Observation> latest = new LinkedHashMap<>();
        for (Observation o : c.observations()) {
            latest.merge(o.key().slot(), o, (a, b) -> b.key().attempt() > a.key().attempt() ? b : a);
        }
        List<Observation> out = new ArrayList<>(latest.values());
        out.sort(ORDER);
        return out;
    }

    private static void identity(StringBuilder out, Checkpoint c, Observation o) {
        out.append("candidate_id=").append(value(o.display())).append(" repeat_idx=").append(o.key().repeatIndex());
        if (!o.key().environmentId().isEmpty()) out.append(" env_id=").append(value(o.key().environmentId()));
        if (!c.runId().isEmpty()) out.append(" run_id=").append(value(c.runId()));
    }

    /** One more pair; a key the reader would read differently, or would take as identity or verdict, is left out. */
    private static void pair(StringBuilder out, String key, String value) {
        if (!KEY.matcher(key).matches() || RESERVED.contains(key.toLowerCase(Locale.ROOT)) || value.isEmpty()) return;
        out.append(' ').append(key).append('=').append(value(value));
    }

    /** A value as one token: white space and the reader's separators become underscores. */
    private static String value(String text) {
        String token = text.replaceAll("[\\s;|,\\])}]", "_");
        return token.isEmpty() ? "_" : token;
    }
}
