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
import org.ghyura.cf.constraints.RuleEngine;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyCounts;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Candidate;
import org.ghyura.cf.generation.CandidateRenderer;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.generation.RenderedSource;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;

/**
 * A bounded look at a scenario's candidate space and its sieve (AN-13; the legacy run UI's plan panel and the sieve's dry
 * run): the plan's counts with how sure each is, every rule with what it removes, and one page of generated candidates
 * in canonical order with what became of each (kept, removed by named rules, left out by covering selection) and its
 * rendered source. A request reads at most {@code scan} generated candidates from where its page starts, so looking for
 * survivors behind many rejected candidates never runs away (0034); a page that needs more says so. A page starts at a
 * generated ordinal directly, reading nothing before it. Nothing is executed and nothing is held after the request.
 *
 * @param estimates   the plan's counts, or null when the request did not ask for them
 * @param rules       every rule of the constraint layer, in order
 * @param diagnostics the assembly's findings
 */
public record CandidatePreview(PlanEstimates estimates, List<Rule> rules, BigInteger generated, Page page, List<Diagnostic> diagnostics) {

    /** The most rows a page holds. */
    public static final int MAX_PAGE = 500;
    /** Sources longer than this many characters are cut, marked with an ellipsis. */
    public static final int MAX_SOURCE_CHARS = 8_000;

    public CandidatePreview {
        rules = List.copyOf(rules);
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * What to show.
     *
     * @param scan      the generated-candidate limit for this page, also used as the threshold for each separate counting pass;
     *                  validation, generation and covering selection have their own resource limits
     * @param first     the generated ordinal the page starts at
     * @param size      rows per page, 1 to {@link #MAX_PAGE}
     * @param keptOnly  only the candidates a run would execute; otherwise every generated one, with its fate
     * @param estimates whether to compute the plan's counts too
     */
    public record Request(BigInteger scan, BigInteger first, int size, boolean keptOnly, boolean estimates) {
        public Request {
            Objects.requireNonNull(scan, "scan");
            Objects.requireNonNull(first, "first");
            if (scan.signum() < 0 || first.signum() < 0 || size < 1 || size > MAX_PAGE) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "a preview reads 0 or more candidates from "
                        + "ordinal 0 or more, 1 to " + MAX_PAGE + " rows per page; got scan " + scan + ", first " + first + ", size "
                        + size, SourceLocation.NONE));
            }
        }
    }

    /** A rule and the candidates it removes, or null when they were not counted within the scan. */
    public record Rule(String name, boolean early, BigInteger removed) {
    }

    /** What became of a generated candidate. */
    public enum Status {
        /** A run executes it. */
        KEPT,
        /** A rule that reads only mandatory sheets removed its mandatory row. */
        REMOVED_EARLY,
        /** Covering selection left its mandatory row out. */
        UNSELECTED,
        /** A rule that reads optional sheets removed it. */
        REMOVED_LATE
    }

    /** One generated candidate: its ordinal, legacy display id, identity, fate, the rules it breaks and its source. */
    public record Row(BigInteger ordinal, String display, String id, Status status, List<String> rules, RenderedSource rendered) {
        public Row {
            rules = List.copyOf(rules);
            Objects.requireNonNull(rendered, "rendered");
        }

        /** The unformatted execution source, bounded by the preview limit. */
        public String source() {
            return rendered.text();
        }

        /** Compatibility for callers supplying a source without provenance. */
        public Row(BigInteger ordinal, String display, String id, Status status, List<String> rules, String source) {
            this(ordinal, display, id, status, rules, new RenderedSource(source, List.of(), false));
        }
    }

    /**
     * @param next    the ordinal the following page starts at, or null after the last generated candidate
     * @param visited the generated candidates this page read
     * @param limit   why the page has fewer rows than asked although candidates remain, or empty
     */
    public record Page(BigInteger first, List<Row> rows, BigInteger next, BigInteger visited, String limit) {
        public Page {
            rows = List.copyOf(rows);
        }
    }

    /** Builds the preview while the generation's tables are open. */
    static CandidatePreview of(ScenarioValidation.Summary summary, Generation.Result result, KeyDictionary dictionary, MemoryBudget budget,
                               RunConfig config, Request request, Cancellation cancellation) {
        cancellation.throwIfCancelled();
        PlanEstimates estimates = request.estimates()
                ? PlanEstimates.of(summary, result, dictionary, budget, config, request.scan(), cancellation) : null;
        try (Assembly assembly = Assembly.of(summary.plan(), result.result(), dictionary, GenerationOptions.of(config), AssemblyOptions.of(config),
                RenderOptions.of(config), config.randomSeed(), budget, cancellation);
             CandidateRenderer renderer = CandidateRenderer.of(assembly, summary.plan(), dictionary, RenderOptions.of(config), budget)) {
            RuleEngine engine = assembly.rules();
            AssemblyCounts counts = engine.size() == 0 ? null : assembly.count(cancellation, request.scan());
            BigInteger[] perRule = counts != null && counts.exact() ? counts.perRule() : null;
            List<Rule> rules = new ArrayList<>();
            for (int i = 0; i < engine.size(); i++) {
                rules.add(new Rule(engine.name(i), engine.mandatoryOnly(i), perRule == null ? null : perRule[i]));
            }
            return new CandidatePreview(estimates, rules, assembly.generated(), page(assembly, renderer, request, cancellation),
                    assembly.diagnostics());
        }
    }

    private static Page page(Assembly assembly, CandidateRenderer renderer, Request request, Cancellation cancellation) {
        cancellation.throwIfCancelled();
        RuleEngine engine = assembly.rules();
        List<Row> rows = new ArrayList<>();
        BigInteger visited = BigInteger.ZERO;
        Iterator<Assembly.Assessed> it = assembly.assessed(cancellation, request.first());
        while (request.first().add(visited).compareTo(assembly.generated()) < 0 && rows.size() < request.size()
                && visited.compareTo(request.scan()) < 0) {
            cancellation.throwIfCancelled();
            Assembly.Assessed a = it.next();
            visited = visited.add(BigInteger.ONE);
            Candidate c = a.candidate();
            BitSet broken = a.violations();
            boolean early = broken.stream().anyMatch(engine::mandatoryOnly);
            Status status = early ? Status.REMOVED_EARLY : !assembly.selectedRow(c.mandatory()) ? Status.UNSELECTED
                    : broken.isEmpty() ? Status.KEPT : Status.REMOVED_LATE;
            if (request.keptOnly() && status != Status.KEPT) {
                continue;
            }
            List<String> names = broken.stream().mapToObj(engine::name).toList();
            RenderedSource source = renderer.renderWithOrigins(c, MAX_SOURCE_CHARS);
            rows.add(new Row(c.ordinal(), c.display(), c.id().hex(), status, names, source));
        }
        cancellation.throwIfCancelled();
        BigInteger next = request.first().add(visited);
        if (next.compareTo(assembly.generated()) >= 0) next = null;
        String limit = next != null && rows.size() < request.size() && visited.compareTo(request.scan()) >= 0
                ? "this page read the --scan limit of " + request.scan() + " generated candidates; the next page starts after them" : "";
        return new Page(request.first(), rows, next, visited, limit);
    }
}
