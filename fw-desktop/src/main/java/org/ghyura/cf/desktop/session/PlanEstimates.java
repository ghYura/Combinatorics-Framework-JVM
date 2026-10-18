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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyCounts;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Candidate;
import org.ghyura.cf.generation.CandidateRenderer;
import org.ghyura.cf.generation.Cardinality;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * How sure {@code plan} is of every count after the tables (GEN:cardinality-resource-planning, after fwgen's cardinality,
 * count and resource plans): the candidate space stage by stage, the samples and attempts a run of it would execute,
 * and resource estimates. Exact counts stay exact; a count the plan did not finish is a proven range, and one it cannot
 * know is unknown, never zero. Resource figures state their assumptions and are kept apart from the memory the plan
 * reserved, which is exact. Nothing is executed.
 *
 * @param assemblyBytes the bytes the assembly reserved while the plan held it (exact), or -1 when it was not assembled
 */
public record PlanEstimates(BigInteger scan, List<Stage> candidates, List<Stage> execution, List<Stage> resources, String runClass,
                     long assemblyBytes, List<Diagnostic> diagnostics) {

    /** One named count. */
    public record Stage(String name, Cardinality count) {
    }

    /** How many rendered candidates the source estimate reads, from the first. */
    public static final int SOURCE_SAMPLE = 8;
    /** fwgen's default run-class limits (ResourceThresholds): S up to 500 samples, B up to 50000, L up to 5000000, X beyond. */
    static final long[] CLASS_LIMITS = {500, 50_000, 5_000_000};
    /** The bytes the result store charges an observation without output, metrics or failure text (ResultRepository.estimate). */
    static final long OBSERVATION_BYTES = 320 + 2 * (64 + 64);

    public PlanEstimates {
        candidates = List.copyOf(candidates);
        execution = List.copyOf(execution);
        resources = List.copyOf(resources);
        diagnostics = List.copyOf(diagnostics);
    }

    public static PlanEstimates of(ScenarioValidation.Summary summary, Generation.Result result, KeyDictionary dictionary, MemoryBudget budget,
                            RunConfig config, BigInteger scan) {
        return of(summary, result, dictionary, budget, config, scan, Cancellation.NONE);
    }

    /** The same plan for an interactive caller; cancellation also reaches counting and source sampling. */
    public static PlanEstimates of(ScenarioValidation.Summary summary, Generation.Result result, KeyDictionary dictionary, MemoryBudget budget,
                                   RunConfig config, BigInteger scan, Cancellation cancellation) {
        cancellation.throwIfCancelled();
        List<Stage> candidates = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Cardinality executable;
        long[] sourceBytes = null;
        String sourceReason = "no candidate could be rendered to measure";
        long before = budget.reservedBytes();
        long assemblyBytes = -1;
        try (Assembly assembly = Assembly.of(summary.plan(), result.result(), dictionary, GenerationOptions.of(config),
                AssemblyOptions.of(config), RenderOptions.of(config), config.randomSeed(), budget, cancellation);
             CandidateRenderer renderer = CandidateRenderer.of(assembly, summary.plan(), dictionary, RenderOptions.of(config), budget)) {
            assemblyBytes = budget.reservedBytes() - before;
            diagnostics.addAll(assembly.diagnostics());
            executable = candidateStages(assembly, result, scan, candidates, cancellation);
            if (executable.mode() == Cardinality.Mode.EXACT) {
                try {
                    if (executable.value().signum() > 0) sourceBytes = sourceSample(assembly, renderer, cancellation);
                } catch (FrameworkException e) {
                    if (e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RUN_CANCELLED)) throw e;
                    sourceReason = "source sampling was omitted: " + e.getMessage();
                }
            } else {
                sourceReason = "source sampling was omitted because locating survivors could exceed --scan " + scan;
            }
        } catch (FrameworkException e) {
            if (e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RUN_CANCELLED)) {
                throw e;
            }
            String why = e.diagnostics().isEmpty() ? String.valueOf(e.getMessage()) : e.diagnostics().getFirst().message();
            executable = Cardinality.unknown("the candidate space could not be assembled", why);
            candidates.add(new Stage("executable", executable));
        }
        List<Stage> execution = new ArrayList<>();
        Cardinality samples = executionStages(executable, config, execution);
        Cardinality attempts = execution.getLast().count();
        List<Stage> resources = new ArrayList<>();
        resources.add(new Stage("duration-ms", duration(attempts, config)));
        resources.add(new Stage("result-bytes", results(attempts, config)));
        resources.add(new Stage("source-bytes", sources(execution.getFirst().count(), sourceBytes, sourceReason)));
        cancellation.throwIfCancelled();
        return new PlanEstimates(scan, candidates, execution, resources, runClass(samples), assemblyBytes, diagnostics);
    }

    // ---- the candidate space ----------------------------------------------------------------------------------------

    /** Adds the candidate stages and returns the executable count. */
    private static Cardinality candidateStages(Assembly assembly, Generation.Result result, BigInteger scan, List<Stage> stages,
                                               Cancellation cancellation) {
        List<String> names = new ArrayList<>();
        List<String> factors = new ArrayList<>();
        BigInteger product = BigInteger.ONE;
        for (int c = 0; c < assembly.columnSheets().size(); c++) {
            if (!assembly.optionalColumn(c)) {
                String sheet = assembly.columnSheets().get(c);
                BigInteger rows = result.result().sheets().stream().filter(s -> s.sheet().equals(sheet)).map(o -> o.rows().size())
                        .findFirst().orElse(BigInteger.ZERO);
                names.add("|" + sheet + "|");
                factors.add(rows.toString());
                product = product.multiply(rows);
            }
        }
        BigInteger mandatory = assembly.mandatoryRows();
        String formula = factors.isEmpty() ? "1: no mandatory sheet, the structural empty tuple"
                : String.join(" × ", names) + " = " + String.join(" × ", factors)
                + (assembly.mandatoryRowsUncut().equals(product) ? "" : ", de-duplicated (cf.assembly.distinct)");
        if (!mandatory.equals(assembly.mandatoryRowsUncut())) {
            formula += ", cut from " + assembly.mandatoryRowsUncut() + " (cf.assembly.mandatoryLimit)";
        }
        stages.add(new Stage("mandatory", Cardinality.exact(mandatory, formula)));
        if (assembly.coverage() != null) {
            var s = assembly.coverage();
            // a selection without a method kept every row: the coverage budget holds all of them
            stages.add(new Stage("selected rows", s.method() == null
                    ? Cardinality.exact(BigInteger.valueOf(s.kept().length), "every row: the coverage budget holds all of them",
                    "no covering selection was needed; the count is exact")
                    : Cardinality.exact(BigInteger.valueOf(s.kept().length), "t=" + s.strength() + " "
                    + s.method().name().toLowerCase(Locale.ROOT).replace('_', '-') + " covering of " + s.feasible() + " feasible tuples",
                    "greedy covering selection; the count is exact, not a proven minimum")));
        }
        List<String> optional = new ArrayList<>();
        boolean anyOptional = false;
        for (int c = 0; c < assembly.columnSheets().size(); c++) {
            anyOptional |= assembly.optionalColumn(c);
        }
        if (assembly.absentIncluded()) {
            optional.add(anyOptional ? "1 absent branch" : "1: no optional sheet");
        }
        for (long[] size : assembly.optionalSizes()) {
            optional.add((size[1] < 0 ? "more than 2^63" : Long.toString(size[1])) + " of size " + size[0]);
        }
        BigInteger per = assembly.perMandatoryRow();
        stages.add(new Stage("per row", Cardinality.exact(per, optional.isEmpty() ? "0: no optional row and no absent branch"
                : String.join(" + ", optional))));
        BigInteger generated = assembly.generated();
        stages.add(new Stage("generated", Cardinality.exact(generated, mandatory + " × " + per)));
        Cardinality executable;
        int rules = assembly.rules().size();
        if (rules == 0 && assembly.coverage() == null) {
            executable = Cardinality.exact(generated, "= generated: no rule and no covering selection");
        } else {
            AssemblyCounts counts = assembly.count(cancellation, scan);
            if (counts.exact()) {
                List<String> terms = new ArrayList<>(List.of(generated.toString()));
                for (BigInteger removed : List.of(counts.removedEarly(), counts.unselected(), counts.removedLate())) {
                    terms.add(removed.toString());
                }
                executable = Cardinality.exact(counts.executable(), String.join(" − ", terms) + " (generated − removed early − unselected"
                        + " − removed late, counted)");
            } else {
                BigInteger upper = assembly.coverage() != null ? BigInteger.valueOf(assembly.coverage().kept().length).multiply(per) : generated;
                executable = Cardinality.bounded(BigInteger.ZERO, upper, "[0, " + (assembly.coverage() != null ? "selected rows × per row"
                        : "generated") + "]", rules + " rule(s) can remove from none to all of these candidates; the plan counts them "
                        + "exactly only up to --scan " + scan + " candidates");
            }
        }
        stages.add(new Stage("executable", executable));
        return executable;
    }

    /** The UTF-8 sizes of the first rendered candidates: count, least, total, most; null when there is none. */
    private static long[] sourceSample(Assembly assembly, CandidateRenderer renderer, Cancellation cancellation) {
        long[] sample = {0, Long.MAX_VALUE, 0, 0};
        Iterator<Candidate> it = assembly.candidates(cancellation);
        while (sample[0] < SOURCE_SAMPLE && it.hasNext()) {
            cancellation.throwIfCancelled();
            long bytes = renderer.renderText(it.next()).getBytes(StandardCharsets.UTF_8).length;
            sample[0]++;
            sample[1] = Math.min(sample[1], bytes);
            sample[2] += bytes;
            sample[3] = Math.max(sample[3], bytes);
        }
        return sample[0] == 0 ? null : sample;
    }

    // ---- what a run would execute -----------------------------------------------------------------------------------

    /** Adds admitted, samples, verdicts, measurements and attempts (last), and returns the samples. */
    private static Cardinality executionStages(Cardinality executable, RunConfig config, List<Stage> stages) {
        Cardinality admitted = executable;
        String formula = "= executable";
        if (config.stopCandidates() > 0) {
            formula = "min(executable, " + config.stopCandidates() + ")";
            admitted = admitted.atMost(BigInteger.valueOf(config.stopCandidates()), formula, "cf.run.stop.candidates admits at most "
                    + config.stopCandidates());
        }
        List<String> earlier = new ArrayList<>();
        if (config.stopErrors() > 0) {
            earlier.add("cf.run.stop.errors");
        }
        if (!config.stopTarget().isNone()) {
            earlier.add("cf.run.stop.target");
        }
        if (config.runBudgetMillis() > 0) {
            earlier.add("cf.run.budget.millis");
        }
        if (!earlier.isEmpty()) {
            formula += " or fewer";
            admitted = admitted.orFewer(formula, String.join(", ", earlier) + " can end admission earlier");
        } else {
            admitted = new Cardinality(admitted.mode(), admitted.value(), admitted.lower(), admitted.upper(), formula, admitted.reasons(),
                    admitted.assumptions());
        }
        stages.add(new Stage("admitted", admitted));
        int k = config.repeatCount();
        int e = config.environments().size();
        boolean nested = config.repeatPolicy() == RunConfig.RepeatPolicy.NESTED;
        long slots = (long) k * (nested ? e : 1);
        long verdicts = config.repeatScope() == RunConfig.RepeatScope.METRICS ? (nested ? e : 1) : slots;
        String layout = "K=" + k + (nested ? ", E=" + e : "") + ", " + config.repeatPolicy().token() + "/" + config.repeatScope().token();
        Cardinality samples = admitted.times(BigInteger.valueOf(slots), "admitted × " + slots + " (" + layout + ")");
        Cardinality verdictSamples = admitted.times(BigInteger.valueOf(verdicts), "admitted × " + verdicts + " (" + layout + ")");
        Cardinality measurements = admitted.times(BigInteger.valueOf(slots - verdicts), "admitted × " + (slots - verdicts) + " (" + layout + ")");
        stages.add(new Stage("samples", samples));
        stages.add(new Stage("verdicts", verdictSamples));
        stages.add(new Stage("measurements", measurements));
        int retries = config.retryMaxAttempts();
        Cardinality attempts;
        if (retries == 1 && slots == verdicts) {
            attempts = new Cardinality(samples.mode(), samples.value(), samples.lower(), samples.upper(), "= samples: one attempt each",
                    samples.reasons(), samples.assumptions());
        } else if (!samples.known()) {
            attempts = Cardinality.unknown("samples × up to " + retries + " attempts", "the number of samples is unknown");
        } else {
            BigInteger upper = samples.upper().multiply(BigInteger.valueOf(retries));
            attempts = Cardinality.bounded(verdictSamples.lower(), upper, "[verdicts, samples × " + retries + "]",
                    (retries > 1 ? "a TIMEOUT or INFRA_FAIL attempt is retried up to cf.execution.retry.maxAttempts = " + retries : "")
                            + (retries > 1 && slots > verdicts ? "; " : "")
                            + (slots > verdicts ? "a measurement is not attempted when its verdict sample gave no verdict" : ""));
        }
        stages.add(new Stage("attempts", attempts));
        return samples;
    }

    private static String runClass(Cardinality samples) {
        if (!samples.known()) {
            return "X";
        }
        String[] names = {"S", "B", "L"};
        for (int i = 0; i < CLASS_LIMITS.length; i++) {
            if (samples.value().compareTo(BigInteger.valueOf(CLASS_LIMITS[i])) <= 0) {
                return names[i];
            }
        }
        return "X";
    }

    // ---- resources --------------------------------------------------------------------------------------------------

    /** A deadline-based scheduling model, not a wall-clock bound on compilation, queueing, cleanup or the full run. */
    private static Cardinality duration(Cardinality attempts, RunConfig config) {
        BigInteger each = BigInteger.valueOf(config.timeoutMillis()).add(BigInteger.valueOf(config.cancelGraceMillis()));
        int parallel = config.serializeSamples() ? 1 : config.workers();
        BigInteger workers = BigInteger.valueOf(parallel);
        String assumption = "timeout plus grace per attempt, balanced work over " + parallel
                + " workers (one when cf.execution.serialize); compilation, queueing, scheduling delays, cleanup, analysis and reporting "
                + "are excluded, so this is not a wall-clock upper bound";
        BigInteger cap = config.runBudgetMillis() > 0 ? BigInteger.valueOf(config.runBudgetMillis())
                .add(BigInteger.valueOf(config.cancelGraceMillis())) : null;
        if (!attempts.known()) {
            return cap == null ? Cardinality.unknown("⌈attempts / workers⌉ × (timeout + grace)", "the number of attempts is unknown")
                    : new Cardinality(Cardinality.Mode.ESTIMATED, cap, BigInteger.ZERO, cap, "cf.run.budget.millis + grace", List.of(),
                    List.of(assumption));
        }
        BigInteger rounds = attempts.upper().add(workers).subtract(BigInteger.ONE).divide(workers);
        BigInteger upper = rounds.multiply(each);
        String formula = "⌈" + attempts.upper() + " / " + parallel + "⌉ × (" + config.timeoutMillis() + " + "
                + config.cancelGraceMillis() + ")";
        if (cap != null && cap.compareTo(upper) < 0) {
            upper = cap;
            formula = "cf.run.budget.millis + grace, below " + formula;
        }
        return new Cardinality(Cardinality.Mode.ESTIMATED, upper, BigInteger.ZERO, upper, formula, List.of(), List.of(assumption));
    }

    /** The result store's charge for the attempts it keeps (full retention) or holds in flight (aggregate-only). */
    private static Cardinality results(Cardinality attempts, RunConfig config) {
        if (!attempts.known()) {
            return Cardinality.unknown("attempts × bytes per observation", "the number of attempts is unknown");
        }
        long envBytes = 2L * config.environments().stream().mapToInt(String::length).max().orElse(0);
        BigInteger least = BigInteger.valueOf(OBSERVATION_BYTES + envBytes);
        BigInteger most = least.add(BigInteger.valueOf(4L * config.outputMaxBytes()));
        List<String> assumptions = new ArrayList<>(List.of("an observation without output takes " + least + " bytes and one with full "
                + "stdout and stderr " + most + " (ResultRepository.estimate); metrics, attributes and failure text add to it"));
        List<String> reasons = new ArrayList<>();
        BigInteger kept = attempts.value();
        BigInteger keptLow = attempts.lower();
        BigInteger keptHigh = attempts.upper();
        String formula = "attempts × [" + least + ", " + most + "]";
        if (config.resultsRetention() == RunConfig.ResultsRetention.AGGREGATE_ONLY) {
            BigInteger inFlight = BigInteger.valueOf((long) config.queueMaxItems() + config.workers());
            kept = kept.min(inFlight);
            keptLow = keptLow.min(inFlight);
            keptHigh = keptHigh.min(inFlight);
            formula = "min(attempts, queue + workers) × [" + least + ", " + most + "]";
            assumptions.add("aggregate-only retention keeps exact counters and only the samples in flight");
        } else {
            BigInteger max = BigInteger.valueOf(config.resultsMaxSamples());
            if (attempts.lower().compareTo(max) > 0) {
                reasons.add("more than cf.results.maxSamples = " + max + " observations: the run would end with RESOURCE_LIMIT");
            } else if (attempts.upper().compareTo(max) > 0) {
                reasons.add("up to " + attempts.upper() + " observations may pass cf.results.maxSamples = " + max + " (RESOURCE_LIMIT)");
            }
        }
        BigInteger memory = BigInteger.valueOf(config.memoryBudgetBytes());
        if (keptLow.multiply(least).compareTo(memory) > 0) {
            reasons.add("even the least figure passes cf.memory.budget.bytes = " + memory + " (RESOURCE_LIMIT)");
        } else if (keptHigh.multiply(most).compareTo(memory) > 0) {
            reasons.add("large outputs could pass cf.memory.budget.bytes = " + memory + " (RESOURCE_LIMIT)");
        }
        return Cardinality.estimated(kept.multiply(least), keptLow.multiply(least), keptHigh.multiply(most), formula, reasons, assumptions);
    }

    /** The candidates' source text, from the sizes of the first rendered ones. */
    private static Cardinality sources(Cardinality admitted, long[] sample, String reason) {
        if (sample == null) {
            return admitted.known() && admitted.upper().signum() == 0 ? Cardinality.exact(BigInteger.ZERO, "no candidate")
                    : Cardinality.unknown("admitted × bytes per source", reason);
        }
        if (!admitted.known()) {
            return Cardinality.unknown("admitted × bytes per source", "the number of admitted candidates is unknown");
        }
        BigInteger mean = BigInteger.valueOf(sample[2] / sample[0]);
        return Cardinality.estimated(admitted.value().multiply(mean), admitted.lower().multiply(BigInteger.valueOf(sample[1])),
                admitted.upper().multiply(BigInteger.valueOf(sample[3])), "admitted × " + mean + " (in [" + sample[1] + ", " + sample[3]
                        + "])", List.of(), List.of("the first " + sample[0] + " rendered sources (" + sample[1] + " to " + sample[3]
                        + " bytes, UTF-8) stand for every candidate"));
    }

    // ---- rendering --------------------------------------------------------------------------------------------------

    public String toText() {
        StringBuilder text = new StringBuilder(2048);
        text.append("cardinality: every count with how sure it is (rules counted over at most --scan ").append(scan).append(" candidates)\n");
        List<String> said = List.of();
        for (Stage s : candidates) {
            said = line(text, s, said);
        }
        for (Stage s : execution) {
            said = line(text, s, said);
        }
        text.append("resources: estimates, apart from the memory reserved above (run class ").append(runClass).append(": S up to ")
                .append(CLASS_LIMITS[0]).append(" samples, B up to ").append(CLASS_LIMITS[1]).append(", L up to ").append(CLASS_LIMITS[2])
                .append(", X beyond or unknown)\n");
        for (Stage s : resources) {
            line(text, s, List.of());
        }
        if (assemblyBytes >= 0) {
            text.append(String.format(Locale.ROOT, "  %-13s %d bytes reserved while the plan held it (admitted, exact)%n", "assembly",
                    assemblyBytes));
        }
        return text.toString();
    }

    /** One stage and its reasons, those the stage above already gave left out; returns this stage's reasons. */
    private static List<String> line(StringBuilder text, Stage s, List<String> said) {
        text.append(String.format(Locale.ROOT, "  %-13s %s%n", s.name(), s.count().describe()));
        for (String r : s.count().reasons()) {
            if (!said.contains(r)) {
                text.append("                because ").append(r).append('\n');
            }
        }
        for (String a : s.count().assumptions()) {
            text.append("                assuming ").append(a).append('\n');
        }
        return s.count().reasons();
    }

    public void appendXml(StringBuilder xml, String indent) {
        xml.append(indent).append("<cardinality scan=\"").append(scan).append("\">\n");
        for (Stage s : candidates) {
            s.count().appendXml(xml, indent + "  ", s.name());
        }
        for (Stage s : execution) {
            s.count().appendXml(xml, indent + "  ", s.name());
        }
        xml.append(indent).append("</cardinality>\n");
        xml.append(indent).append("<resources runClass=\"").append(runClass).append('"');
        if (assemblyBytes >= 0) {
            xml.append(" assemblyBytes=\"").append(assemblyBytes).append('"');
        }
        xml.append(">\n");
        for (Stage s : resources) {
            s.count().appendXml(xml, indent + "  ", s.name());
        }
        xml.append(indent).append("</resources>\n");
    }
}
