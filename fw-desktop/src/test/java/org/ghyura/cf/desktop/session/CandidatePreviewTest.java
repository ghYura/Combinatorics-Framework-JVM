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

import org.ghyura.cf.generation.Cardinality;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AN-13: the desktop's bounded candidate and sieve preview, through the shared session. */
public class CandidatePreviewTest {

    @Test
    void interactiveEstimatesPollCancellationWhileCounting(@TempDir Path dir) throws IOException {
        Path scenario = Files.writeString(dir.resolve("count.json"), """
                {"constraints":[{"id":"none","assert":{"sheet":"P","present":false},"polarity":"require"}],
                 "slots":[{"sheet":"P","values":["a","b"],"verb":"FW_PermutR(14)"}]}
                """);
        Session session = SessionTest.session("cf.plan.autoPromoteSingleVerb=false");
        var opened = ScenarioValidation.open(scenario, session.configuration());
        var config = opened.effective().config();
        ScenarioValidation.prepare(scenario.toString(), opened.loaded(), config, (summary, dictionary, budget) -> {
            try (var generated = org.ghyura.cf.generation.Generation.run(summary.plan(), dictionary,
                    org.ghyura.cf.generation.GenerationOptions.of(config), budget, Cancellation.NONE)) {
                var polls = new java.util.concurrent.atomic.AtomicInteger();
                var failure = org.junit.jupiter.api.Assertions.assertThrows(org.ghyura.cf.model.FrameworkException.class,
                        () -> PlanEstimates.of(summary, generated, dictionary, budget, config, BigInteger.valueOf(20_000),
                                () -> polls.incrementAndGet() >= 4));
                assertTrue(failure.diagnostics().stream().anyMatch(d -> d.code() == org.ghyura.cf.model.ErrorCode.RUN_CANCELLED));
            }
            return null;
        });
        CandidatePreview zero = preview(session, scenario, 0, 50, 3, false, false);
        assertTrue(zero.page().rows().isEmpty());
        assertEquals(BigInteger.ZERO, zero.page().visited());
        assertEquals(BigInteger.valueOf(50), zero.page().next());
    }

    /** The parcel-dispatch lesson: 48 mandatory rows, 4 per row (absent, two single options, both); one early rule. */
    public static Path tutor(Path dir) throws IOException {
        Files.writeString(dir.resolve("tutor.constraints.json"), "{\"version\": 1, \"constraints\": [{\"id\": \"wallet_requires_member\", "
                + "\"polarity\": \"forbid\", \"pairs\": [{\"payment\": \"wallet\", \"account\": \"guest\"}]}]}", StandardCharsets.UTF_8);
        return Files.writeString(dir.resolve("tutor.json"), "{\"sheets\": {\"FW_Seq\": [[\"channel\", \"FW_Combi(1)\"], [\"account\", "
                + "\"FW_Combi(1)\"], [\"payment\", \"FW_Combi(1)\"], [\"steps\", \"FW_Permut()\"], [\"duplicate_submit\", \"FW_Optional\", "
                + "\"FW_Combi(1)\"], [\"carrier_timeout\", \"FW_Optional\", \"FW_Combi(1)\"]], \"channel\": [[\"web\"], [\"mobile\"]], "
                + "\"account\": [[\"guest\"], [\"member\"]], \"payment\": [[\"card\"], [\"wallet\"]], \"steps\": [[\"reserve\"], [\"charge\"], "
                + "[\"dispatch\"]], \"duplicate_submit\": [[\"dup\"]], \"carrier_timeout\": [[\"timeout\"]]}}", StandardCharsets.UTF_8);
    }

    private static CandidatePreview preview(Session session, Path scenario, long scan, long first, int size, boolean keptOnly,
                                            boolean estimates) {
        return session.preview(scenario, new CandidatePreview.Request(BigInteger.valueOf(scan), BigInteger.valueOf(first), size, keptOnly,
                estimates), Cancellation.NONE);
    }

    @Test
    void everyGeneratedCandidateShowsItsFateAndKeptPagesFollowEachOtherToTheEnd(@TempDir Path dir) throws IOException {
        Path scenario = tutor(dir);
        Session session = SessionTest.session("cf.plan.autoPromoteSingleVerb=false");
        CandidatePreview all = preview(session, scenario, 1_000_000, 0, 200, false, true);
        assertEquals(BigInteger.valueOf(192), all.generated());
        assertEquals(List.of(new CandidatePreview.Rule("wallet_requires_member", true, BigInteger.valueOf(48))), all.rules());
        Map<CandidatePreview.Status, Integer> fates = new EnumMap<>(CandidatePreview.Status.class);
        for (CandidatePreview.Row row : all.page().rows()) {
            fates.merge(row.status(), 1, Integer::sum);
            assertEquals(row.status() == CandidatePreview.Status.KEPT, row.rules().isEmpty(), row.toString());
            assertFalse(row.source().isEmpty());
        }
        assertEquals(Map.of(CandidatePreview.Status.KEPT, 144, CandidatePreview.Status.REMOVED_EARLY, 48), fates);
        assertNull(all.page().next(), "the last page");
        CandidatePreview.Row removed = all.page().rows().stream().filter(r -> r.status() != CandidatePreview.Status.KEPT).findFirst().orElseThrow();
        assertEquals(List.of("wallet_requires_member"), removed.rules());
        Cardinality executable = all.estimates().candidates().stream().filter(s -> s.name().equals("executable")).findFirst().orElseThrow().count();
        assertEquals(BigInteger.valueOf(144), executable.value(), "the plan's counts come along when asked");

        Set<BigInteger> seen = new HashSet<>();
        List<BigInteger> ordinals = new ArrayList<>();
        BigInteger first = BigInteger.ZERO;
        int pages = 0;
        while (first != null) {
            CandidatePreview page = preview(session, scenario, 1_000_000, first.longValueExact(), 50, true, false);
            assertNull(page.estimates());
            for (CandidatePreview.Row row : page.page().rows()) {
                assertEquals(CandidatePreview.Status.KEPT, row.status());
                assertTrue(seen.add(row.ordinal()), "no candidate twice");
                ordinals.add(row.ordinal());
            }
            first = page.page().next();
            pages++;
        }
        assertEquals(144, seen.size(), "the kept pages hold exactly the executable candidates");
        assertEquals(3, pages);
        assertEquals(ordinals.stream().sorted().toList(), ordinals, "in canonical order");

        CandidatePreview deep = preview(session, scenario, 1_000_000, 150, 3, false, false);
        assertEquals(BigInteger.valueOf(150), deep.page().rows().getFirst().ordinal(), "a page starts at its ordinal directly");
        assertEquals(BigInteger.valueOf(3), deep.page().visited());
    }

    @Test
    void aPageReadsAtMostTheScanAndCoveringSelectionIsNamed(@TempDir Path dir) throws IOException {
        Path scenario = tutor(dir);
        Session session = SessionTest.session("cf.plan.autoPromoteSingleVerb=false");
        // the first mandatory rows with wallet and guest are removed: six of the first twelve generated candidates
        CandidatePreview bounded = preview(session, scenario, 2, 0, 50, true, false);
        assertTrue(bounded.page().rows().size() <= 2);
        assertEquals(BigInteger.valueOf(2), bounded.page().visited());
        assertFalse(bounded.page().limit().isEmpty(), "a page that stopped at the scan says so");
        assertEquals(BigInteger.valueOf(2), bounded.page().next());

        Session covering = SessionTest.session("cf.plan.autoPromoteSingleVerb=false", "cf.coverage.strength=2");
        CandidatePreview fates = preview(covering, scenario, 1_000_000, 0, 200, false, false);
        Map<CandidatePreview.Status, Integer> counted = new EnumMap<>(CandidatePreview.Status.class);
        fates.page().rows().forEach(r -> counted.merge(r.status(), 1, Integer::sum));
        assertEquals(Map.of(CandidatePreview.Status.KEPT, 104, CandidatePreview.Status.REMOVED_EARLY, 48, CandidatePreview.Status.UNSELECTED, 40),
                counted, "the plan's 192 - 48 - 40 = 104");
    }
}
