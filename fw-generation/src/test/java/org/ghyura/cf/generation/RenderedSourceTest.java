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

package org.ghyura.cf.generation;

import org.ghyura.cf.config.RunConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RenderedSourceTest {
    @Test
    void provenanceMatchesExecutionAcrossRenderingPoliciesOrderedRepeatsAndOptionalBranches() {
        Scenario scenario = Scenario.of().sheet("Actions", "ping(  1 );", "emit(\"🙂\");")
                .sheet("Sudden", "cancel();")
                .seq("Actions", "FW_Concatenator= ", "FW_PermutR(2)")
                .seq("Sudden", "FW_Optional", "FW_Combi(1)").options(Scenario.UNPROMOTED)
                .names("Actions", "{", ";").names("Sudden", CandidateRenderer.MARKER + ":", ";");
        for (boolean preserve : List.of(false, true)) for (boolean marker : List.of(false, true)) {
            for (String global : List.of("", "\n")) for (var glue : RunConfig.SheetConcatenator.values()) {
                RenderOptions options = new RenderOptions(global, glue, preserve, List.of(), List.of(), List.of(), List.of(), marker);
                try (var run = scenario.run(GenerationOptions.defaults()); var assembly = run.assemble(AssemblyOptions.defaults(), options);
                     var renderer = CandidateRenderer.of(assembly, run.plan(), run.dictionary(), options, run.budget())) {
                    List<Candidate> candidates = run.candidates(assembly);
                    assertEquals(8, candidates.size()); // 4 ordered pairs × absent/present sudden action
                    for (var candidate : candidates) {
                        RenderedSource source = renderer.renderWithOrigins(candidate, 10_000);
                        assertEquals(renderer.renderText(candidate), source.text());
                        assertFalse(source.truncated());
                        int end = 0;
                        for (var chunk : source.chunks()) {
                            assertEquals(end, chunk.start(), "no invented or missing bytes");
                            end = chunk.end();
                            for (var origin : chunk.origins()) {
                                if (origin.label() != null) assertEquals(run.dictionary().value(origin.label()).location(), origin.location());
                            }
                        }
                        assertEquals(source.text().length(), end);
                        assertEquals(!candidate.absentBranch(), source.chunks().stream().flatMap(c -> c.origins().stream()).anyMatch(RenderedSource.Origin::optional));
                        assertEquals(List.of(1, 2), source.chunks().stream().flatMap(c -> c.origins().stream())
                                .filter(o -> o.kind() == RenderedSource.Kind.VALUE && o.sheet().equals("Actions"))
                                .map(RenderedSource.Origin::occurrence).toList());
                    }
                }
            }
        }
    }

    @Test
    void aMarkerSplitAcrossValuesRetainsBothContributorsAndTrimmingUsesExecutionSemantics() {
        String marker = CandidateRenderer.MARKER;
        Scenario scenario = Scenario.of().sheet("A", marker.substring(0, 17), marker.substring(17))
                .seq("A", "FW_Permut()").options(Scenario.UNPROMOTED).names("A", "λ🙂", "|");
        try (var run = scenario.run(GenerationOptions.defaults()); var assembly = run.assemble(AssemblyOptions.defaults(), RenderOptions.defaults());
             var renderer = CandidateRenderer.of(assembly, run.plan(), run.dictionary(), RenderOptions.defaults(), run.budget())) {
            Candidate candidate = run.candidates(assembly).getFirst();
            var rendered = renderer.renderWithOrigins(candidate, 10_000);
            assertEquals("λ🙂" + candidate.display(), rendered.text());
            assertEquals(2, rendered.chunks().getLast().origins().size());
            assertEquals(List.of(1, 2), rendered.chunks().getLast().origins().stream().map(RenderedSource.Origin::occurrence).toList());
            assertTrue(rendered.chunks().getLast().origins().stream().allMatch(o -> o.detail().contains("marker")));
            assertEquals(renderer.renderText(candidate), rendered.text());
            var clipped = renderer.renderWithOrigins(candidate, 2);
            assertEquals("λ…", clipped.text(), "never split the surrogate pair");
            assertTrue(clipped.truncated());
            assertEquals(1, clipped.chunks().getLast().end());
        }
    }

    @Test
    void joinedValuesKeepTheirOriginalSheetAsWellAsTheColumnThatEmittedThem() {
        Scenario scenario = Scenario.of().sheet("A", "a").sheet("B", "b")
                .seq("A", "FW_Reuse", "FW_Combi(1)").seq("B", "FW_Cartes(A)").options(Scenario.UNPROMOTED)
                .names("A", "", "").names("B", "", "");
        try (var run = scenario.run(GenerationOptions.defaults()); var assembly = run.assemble(AssemblyOptions.defaults(), RenderOptions.defaults());
             var renderer = CandidateRenderer.of(assembly, run.plan(), run.dictionary(), RenderOptions.defaults(), run.budget())) {
            var source = renderer.renderWithOrigins(run.candidates(assembly).getFirst(), 10_000);
            assertTrue(source.chunks().stream().flatMap(c -> c.origins().stream())
                    .anyMatch(o -> o.sheet().equals("A") && o.assembledSheet().equals("B")));
        }
    }
}
