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

import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyEncodingPolicy;
import org.ghyura.cf.model.key.LabelWindow;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A02 through complete candidate assembly: the legacy-window labels of a scenario too large for the legacy
 * numbering (negative labels, packed -128 among them) through generation, FW_Group's signed order, FW_ReplaceRE on the
 * label text, the dictionary, rendering and candidate identity; and the compact/packed encodings agreeing on everything
 * but the octets.
 */
class LegacyWindowTest {

    @Test
    void nativeCompactNegativeKeysSurviveGroupingReplacementAndRendering() {
        String[] padding = new String[253];
        java.util.Arrays.fill(padding, "padding");
        Scenario s = Scenario.of().sheet("A", "removed", "kept").sheet("PAD", padding)
                .seq("A", "FW_Combi(1)", "FW_Group FW_ReplaceRE(\"\\[-127\\], \", \"\")", "FW_Combi(2)")
                .options(Scenario.UNPROMOTED).names("A", "<", ">");
        ScenarioSpec spec = s.spec();
        SeqPlan plan = SeqPlanner.plan(spec, Scenario.UNPROMOTED).plan();
        MemoryBudget budget = new MemoryBudget(64L << 20);
        try (KeyDictionary dictionary = KeyDictionary.build(spec, 0, KeyEncodingPolicy.SIGNED_BYTE, budget);
             Generation.Result result = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
             Assembly assembly = Assembly.of(plan, result.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(),
                     RenderOptions.defaults(), 1L, budget, Cancellation.NONE);
             CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, RenderOptions.defaults(), budget)) {
            assertEquals(-127, dictionary.firstLabel());
            assertEquals(BigInteger.ONE, assembly.generated());
            Candidate candidate = assembly.candidates(Cancellation.NONE).next();
            assertEquals(KeyEncoding.SIGNED_BYTE, candidate.keys(0).encoding());
            assertEquals(List.of(-126), java.util.Arrays.stream(candidate.labels(0)).boxed().toList());
            assertEquals("<kept", renderer.renderText(candidate));
        }
        assertEquals(0, budget.reservedBytes());
    }

    /** PAD fills the window up to -128, A then holds labels -128 and -127, BIG the rest: 40000 values, S = 3. */
    private static Scenario big(String... aDirectives) {
        return bigWithValues(new String[]{"minus128", "minus127"}, aDirectives);
    }

    private static Scenario bigWithValues(String[] values, String... aDirectives) {
        String[] pad = new String[7104];
        for (int i = 0; i < pad.length; i++) {
            pad[i] = "pad" + i;
        }
        String[] rest = new String[40000 - 7104 - values.length];
        for (int i = 0; i < rest.length; i++) {
            rest[i] = "big" + i;
        }
        String[] seq = new String[aDirectives.length + 1];
        seq[0] = "A";
        System.arraycopy(aDirectives, 0, seq, 1, aDirectives.length);
        return Scenario.of().sheet("PAD", pad).sheet("A", values).sheet("BIG", rest).seq(seq)
                .options(Scenario.UNPROMOTED).names("A", "<", ">");
    }

    private static KeyDictionary window(ScenarioSpec spec, SeqPlan plan, MemoryBudget budget) {
        return KeyDictionary.build(spec, (int) plan.virtualSheetCount(), KeyEncodingPolicy.AUTO_PRESERVE, LabelWindow.Mode.LEGACY_WINDOW,
                LabelWindow.Scope.ALL, budget);
    }

    @Test
    void negativeLabelsFlowThroughGroupingReplacementRenderingAndIdentity() {
        Scenario s = big("FW_Combi(1)", "FW_Group FW_ReplaceRE(\"\\[-128\\], \", \"\")", "FW_Combi(2)");
        ScenarioSpec spec = s.spec();
        SeqPlan plan = SeqPlanner.plan(spec, Scenario.UNPROMOTED).plan();
        MemoryBudget budget = new MemoryBudget(Scenario.BIG);
        try (KeyDictionary dictionary = window(spec, plan, budget)) {
            assertEquals(-7232, dictionary.firstLabel());
            assertEquals(KeyEncoding.PACKED_LEGACY16, dictionary.encoding());
            assertEquals(List.of(-128, -127), java.util.Arrays.stream(dictionary.labelsOf(2)).boxed().toList(),
                    "the window puts -128 on A's first value; packed storage holds it as an ordinary label");
            assertEquals("minus128", dictionary.value(-128).text());
            assertTrue(dictionary.identity().endsWith("labels=legacy-window;scope=all;first=-7232"));
            try (Generation.Result result = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE)) {
                // the grouped pass sees [[-128], [-127]] (signed order) and the replacement removes "[-128], ": one row [-127]
                List<String> rows = Scenario.all(result.result().sheet("A").orElseThrow().rows()).stream()
                        .map(r -> java.util.Arrays.toString(r.keys().labels())).toList();
                assertEquals(List.of("[-127]"), rows);
                try (Assembly a = Assembly.of(plan, result.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(),
                        RenderOptions.defaults(), 1L, budget, Cancellation.NONE);
                     CandidateRenderer renderer = CandidateRenderer.of(a, plan, dictionary, RenderOptions.defaults(), budget)) {
                    Candidate c = a.candidates(Cancellation.NONE).next();
                    assertEquals("<minus127", renderer.renderText(c), "the label -127 renders its value");
                    assertEquals(BigInteger.ONE, a.generated());
                }
            }
        }
    }

    @Test
    void packedMinus128IsAValidLabelInRowsRulesAndSources() {
        Scenario s = big("FW_Combi(2)");
        ScenarioSpec spec = s.spec();
        SeqPlan plan = SeqPlanner.plan(spec, Scenario.UNPROMOTED).plan();
        MemoryBudget budget = new MemoryBudget(Scenario.BIG);
        try (KeyDictionary dictionary = window(spec, plan, budget);
             Generation.Result result = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
             Assembly a = Assembly.of(plan, result.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(),
                     RenderOptions.defaults(), 1L, budget, Cancellation.NONE);
             CandidateRenderer renderer = CandidateRenderer.of(a, plan, dictionary, RenderOptions.defaults(), budget)) {
            Candidate c = a.candidates(Cancellation.NONE).next();
            assertEquals(List.of(-128, -127), java.util.Arrays.stream(c.labels(0)).boxed().toList());
            assertEquals("<minus128minus127", renderer.renderText(c));
        }
    }

    @Test
    void compactAndPackedEncodingsGiveTheSameCandidatesButDifferentIdentities() {
        Scenario s = Scenario.of().sheet("A", "a1", "a2", "a3").sheet("B", "b1", "b2").seq("A", "FW_Combi(2)").seq("B", "FW_Combi(1)")
                .options(Scenario.UNPROMOTED).names("A", "A=", ";").names("B", "B=", ";");
        ScenarioSpec spec = s.spec();
        SeqPlan plan = SeqPlanner.plan(spec, Scenario.UNPROMOTED).plan();
        List<List<String>> sources = new java.util.ArrayList<>();
        List<String> ids = new java.util.ArrayList<>();
        for (KeyEncodingPolicy policy : List.of(KeyEncodingPolicy.AUTO_PRESERVE, KeyEncodingPolicy.PACKED_LEGACY16)) {
            MemoryBudget budget = new MemoryBudget(Scenario.BIG);
            try (KeyDictionary dictionary = KeyDictionary.build(spec, 0, policy, budget);
                 Generation.Result result = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
                 Assembly a = Assembly.of(plan, result.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(),
                         RenderOptions.defaults(), 1L, budget, Cancellation.NONE);
                 CandidateRenderer renderer = CandidateRenderer.of(a, plan, dictionary, RenderOptions.defaults(), budget)) {
                assertEquals(policy == KeyEncodingPolicy.AUTO_PRESERVE ? KeyEncoding.SIGNED_BYTE : KeyEncoding.PACKED_LEGACY16,
                        dictionary.encoding());
                List<String> list = new java.util.ArrayList<>();
                a.candidates(Cancellation.NONE).forEachRemaining(c -> list.add(renderer.renderText(c)));
                sources.add(list);
                ids.add(a.candidates(Cancellation.NONE).next().id().hex());
            }
        }
        assertEquals(sources.get(0), sources.get(1));
        assertEquals(6, sources.get(0).size());
        assertNotEquals(ids.get(0), ids.get(1), "the encoding is part of the dictionary's identity");
    }
    @Test
    void everySignedByteLabelIncludingMinus128AndZeroRendersAfterRealWindowedTraversal() {
        String[] values = new String[256];
        for (int i = 0; i < values.length; i++) values[i] = "value=" + (Byte.MIN_VALUE + i);
        ScenarioSpec spec = bigWithValues(values, "FW_Combi(1)").spec();
        SeqPlan plan = SeqPlanner.plan(spec, Scenario.UNPROMOTED).plan();
        MemoryBudget budget = new MemoryBudget(128L << 20);
        try (KeyDictionary dictionary = window(spec, plan, budget);
             Generation.Result result = Generation.run(plan, dictionary, GenerationOptions.defaults(), budget, Cancellation.NONE);
             Assembly assembly = Assembly.of(plan, result.result(), dictionary, GenerationOptions.defaults(), AssemblyOptions.defaults(),
                     RenderOptions.defaults(), 1L, budget, Cancellation.NONE);
             CandidateRenderer renderer = CandidateRenderer.of(assembly, plan, dictionary, RenderOptions.defaults(), budget)) {
            assertEquals(-128, dictionary.labelsOf(2)[0]);
            assertEquals(127, dictionary.labelsOf(2)[255]);
            assertEquals(BigInteger.valueOf(256), assembly.generated());
            var cursor = assembly.candidates(Cancellation.NONE);
            for (int label = Byte.MIN_VALUE; label <= Byte.MAX_VALUE; label++) {
                assertTrue(cursor.hasNext());
                Candidate candidate = cursor.next();
                assertEquals(label, candidate.keys(0).label(0));
                assertEquals(label, org.ghyura.cf.model.key.KeyWire.decode(org.ghyura.cf.model.key.KeyWire.encode(candidate.keys(0))).label(0));
                assertEquals("<value=" + label, renderer.renderText(candidate), "ordinary labels are never skipped as Reader NULLs");
            }
            org.junit.jupiter.api.Assertions.assertFalse(cursor.hasNext());
        }
        assertEquals(0, budget.reservedBytes());
    }

}
