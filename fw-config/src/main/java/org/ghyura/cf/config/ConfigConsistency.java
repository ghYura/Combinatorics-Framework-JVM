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

package org.ghyura.cf.config;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.SourceLocation;

import java.util.List;
import java.util.Map;

/** Cross-field rules: each value is valid alone, but some combinations contradict each other. */
final class ConfigConsistency {

    private ConfigConsistency() {
    }

    /**
     * @param entries   the winning entry of every key, for source locations
     * @param maxHeap   the JVM's maximum heap in bytes, or 0 when unknown (skips the heap comparison)
     */
    static void check(RunConfig c, Map<String, ConfigEntry> entries, long maxHeap, List<Diagnostic> out) {
        long budget = c.memoryBudgetBytes();
        within(out, entries, ConfigKeys.PIPELINE_QUEUE_MAX_BYTES, c.queueMaxBytes(), budget);
        within(out, entries, ConfigKeys.COMPILER_CACHE_MAX_BYTES, c.compilerCacheMaxBytes(), budget);
        within(out, entries, ConfigKeys.RESULTS_MAX_ATTACHMENT_BYTES, c.resultsMaxAttachmentBytes(), budget);
        within(out, entries, ConfigKeys.EXECUTION_OUTPUT_MAX_BYTES, c.outputMaxBytes(), budget);
        if (c.analysisMode() == RunConfig.AnalysisMode.FORMAL && c.analysisGoals().isEmpty() && c.analysisMinimize().isEmpty()
                && c.analysisMaximize().isEmpty()) {
            out.add(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT, "cf.analysis.mode=formal analyses the declared goals only, and "
                    + "none is declared: set cf.analysis.goals (or cf.analysis.minimize, cf.analysis.maximize)",
                    where(entries, ConfigKeys.ANALYSIS_MODE), "key", ConfigKeys.ANALYSIS_MODE.name()));
        }

        if (c.checkpointEnabled() && !c.artifactsEnabled()) {
            out.add(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT,
                    "checkpoints are artifact exports: " + ConfigKeys.CHECKPOINT_ENABLED + " requires "
                            + ConfigKeys.ARTIFACTS_ENABLED + "=true", where(entries, ConfigKeys.CHECKPOINT_ENABLED),
                    "key", ConfigKeys.CHECKPOINT_ENABLED.name(), "requires", ConfigKeys.ARTIFACTS_ENABLED.name()));
        }
        if (!c.checkpointFile().isEmpty() && !c.checkpointEnabled()) {
            out.add(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT,
                    ConfigKeys.CHECKPOINT_FILE + " names where checkpoints go, but " + ConfigKeys.CHECKPOINT_ENABLED
                            + " is false: enable checkpoints (run --checkpoint PATH does both)", where(entries, ConfigKeys.CHECKPOINT_FILE),
                    "key", ConfigKeys.CHECKPOINT_FILE.name(), "requires", ConfigKeys.CHECKPOINT_ENABLED.name()));
        }
        ConfigEntry preserve = entries.get(ConfigKeys.RENDER_PRESERVE_WHITESPACE.name());
        if (c.runsPython() && preserve != null && preserve.origin() != ConfigOrigin.DEFAULT && !Boolean.parseBoolean(preserve.rawValue().trim())) {
            out.add(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT, "cf.execution.sut=python runs every candidate as Python, whose line "
                    + "breaks and indentation are its syntax, but cf.render.preserveWhitespace=false would join each value's lines: "
                    + "leave it unset or true (the legacy bundle rendered Python with reader.cells.preserveWhitespace=true)",
                    where(entries, ConfigKeys.RENDER_PRESERVE_WHITESPACE), "key", ConfigKeys.RENDER_PRESERVE_WHITESPACE.name(),
                    "requires", "true under cf.execution.sut=python"));
        }
        if (c.ordinalWindow() < c.workers()) {
            out.add(Diagnostic.warning(ErrorCode.CONFIG_INCONSISTENT,
                    "the ordinal window (" + c.ordinalWindow() + ") is smaller than the worker count (" + c.workers()
                            + "); some workers will always be idle", where(entries, ConfigKeys.PIPELINE_ORDINAL_WINDOW),
                    "key", ConfigKeys.PIPELINE_ORDINAL_WINDOW.name(), "workers", Integer.toString(c.workers())));
        }
        if (c.resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY) {
            if (c.analysisEnabled()) needsHistory(out, entries, ConfigKeys.ANALYSIS_ENABLED);
            if (!c.campaignSeedOutput().isEmpty()) needsHistory(out, entries, ConfigKeys.CAMPAIGN_SEED_OUTPUT);
            if (c.campaignIterations() > 1 || !c.campaignSeedInput().isEmpty()) needsHistory(out, entries, ConfigKeys.CAMPAIGN_ITERATIONS);
            if (c.checkpointEnabled()) needsHistory(out, entries, ConfigKeys.CHECKPOINT_ENABLED);
        }
        if (maxHeap > 0 && budget > maxHeap) {
            out.add(Diagnostic.warning(ErrorCode.CONFIG_INCONSISTENT,
                    "the memory budget (" + budget + " bytes) exceeds this JVM's maximum heap (" + maxHeap
                            + " bytes); an OutOfMemoryError can arrive before RESOURCE_LIMIT does",
                    where(entries, ConfigKeys.MEMORY_BUDGET_BYTES), "key", ConfigKeys.MEMORY_BUDGET_BYTES.name(),
                    "maxHeapBytes", Long.toString(maxHeap)));
        }
    }

    private static void needsHistory(List<Diagnostic> out, Map<String, ConfigEntry> entries, ConfigKey<?> key) {
        out.add(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT, "cf.results.retention=aggregate-only retains no observations; "
                + key.name() + " needs full retention", where(entries, key), "key", key.name(), "requires", "cf.results.retention=full"));
    }

    private static void within(List<Diagnostic> out, Map<String, ConfigEntry> entries, ConfigKey<?> key, long value,
                               long budget) {
        if (value > budget) {
            out.add(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT,
                    key.name() + " (" + value + ") exceeds " + ConfigKeys.MEMORY_BUDGET_BYTES.name() + " (" + budget
                            + "): a sub-budget cannot be larger than the total it is charged against",
                    where(entries, key), "key", key.name(), "value", Long.toString(value),
                    "budget", Long.toString(budget)));
        }
    }

    private static SourceLocation where(Map<String, ConfigEntry> entries, ConfigKey<?> key) {
        ConfigEntry entry = entries.get(key.name());
        return entry == null ? SourceLocation.NONE : entry.location();
    }
}
