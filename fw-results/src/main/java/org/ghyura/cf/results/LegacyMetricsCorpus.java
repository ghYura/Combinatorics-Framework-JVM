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

package org.ghyura.cf.results;

import org.ghyura.cf.model.Observation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The legacy metrics corpus (audit EX07), the text the legacy executors wrote to {@code -metricsFile}: one line per sample
 * whose candidate printed a metrics line, {@code candidate_id=ID source_ref=ID.java run_id=RUN [repeat_idx=R env_id=ENV]
 * LINE}, spaces in the ids written as '_', the repeat tokens only for repeat-aware runs, sorted by candidate id (string
 * order, so {@code 10_0_0} precedes {@code 1_0_0}), repeat index and environment, every line ended by '\n' and nothing at
 * all when no line was harvested (MainWatch.flushMetricsCorpusOnShutdown, py_executor._write_metrics_corpus).
 *
 * <p>One difference is deliberate: the legacy map kept the last line of a repeated candidate id, which let one candidate
 * overwrite another; here every sample keeps its line, the repeats ordered by their candidate digest.</p>
 */
public final class LegacyMetricsCorpus {

    /** The observation attribute that keeps the harvested {@code app=} line verbatim. */
    public static final String LINE_ATTRIBUTE = "metrics.line";

    private LegacyMetricsCorpus() {
    }

    /**
     * @param finals      the final observation of every sample (a repository's {@code finals()})
     * @param runId       the run id written into every line
     * @param repeatAware whether repeat index and environment are written (the legacy K &gt; 1 runs)
     */
    public static String format(List<Observation> finals, String runId, boolean repeatAware) {
        Objects.requireNonNull(finals, "finals");
        Objects.requireNonNull(runId, "runId");
        List<Observation> harvested = new ArrayList<>();
        for (Observation o : finals) {
            if (o.domain().isPresent() && o.domain().get().attributes().containsKey(LINE_ATTRIBUTE)) {
                harvested.add(o);
            }
        }
        harvested.sort(Comparator.comparing(Observation::display).thenComparingInt(o -> o.key().repeatIndex())
                .thenComparing(o -> o.key().environmentId()).thenComparing(o -> o.key().candidateId().hex()));
        StringBuilder text = new StringBuilder();
        for (Observation o : harvested) {
            String id = o.display().replace(' ', '_');
            text.append("candidate_id=").append(id).append(" source_ref=").append(id).append(".java");
            if (!runId.isBlank()) {
                text.append(" run_id=").append(runId.replace(' ', '_'));
            }
            if (repeatAware) {
                text.append(" repeat_idx=").append(o.key().repeatIndex()).append(" env_id=").append(o.key().environmentId().replace(' ', '_'));
            }
            text.append(' ').append(o.domain().get().attributes().get(LINE_ATTRIBUTE)).append('\n');
        }
        return text.toString();
    }
}
