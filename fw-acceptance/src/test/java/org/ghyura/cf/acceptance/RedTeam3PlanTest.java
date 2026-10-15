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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.desktop.session.CandidatePreview;
import org.ghyura.cf.fixtures.redteam.RedTeam3;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class RedTeam3PlanTest {
    @Test void bothUnchangedSourceSpecsYield64NativeCoordinatesAndCorrectOptionalCalls(@TempDir Path directory) throws Exception {
        var session = Session.resolve(null, Map.of(), List.of(
                new ConfigResolver.Override("cf.generation.distinctTables", "true", "source profile"),
                new ConfigResolver.Override("cf.assembly.distinct", "true", "source profile"),
                new ConfigResolver.Override("cf.render.preserveWhitespace", "true", "literal source preview")));
        for (var variant : RedTeam3.Variant.values()) {
            Path spec = directory.resolve(variant.name().toLowerCase(Locale.ROOT) + ".toml");
            try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/redteam/v3/" + spec.getFileName())) { assertNotNull(in); Files.write(spec, in.readAllBytes()); }
            var preview = session.preview(spec, new CandidatePreview.Request(BigInteger.valueOf(100), BigInteger.ZERO, 100, true, true), Cancellation.NONE);
            assertEquals(BigInteger.valueOf(64), preview.generated()); assertEquals(64, preview.page().rows().size());
            var coordinates = new TreeSet<Integer>(); var identities = new HashSet<String>();
            for (var row : preview.page().rows()) {
                assertEquals(CandidatePreview.Status.KEPT, row.status()); assertTrue(identities.add(row.id()));
                String body = row.source();
                // HEAD itself sets the default EP. Remove fixed scaffolding before attributing variable slots.
                for (var slot : RedTeam3.slots(variant)) if (slot.sheet().equals("HEAD") || slot.sheet().equals("TAIL")) {
                    assertTrue(body.contains(slot.values().getFirst().strip()), slot.sheet());
                    body = body.replace(slot.values().getFirst().strip(), "");
                }
                String variableSource = body;
                var selected = new ArrayList<List<String>>();
                for (var slot : RedTeam3.slots(variant)) selected.add(slot.sheet().equals("HEAD") || slot.sheet().equals("TAIL")
                        ? slot.values() : slot.values().stream().filter(v -> variableSource.contains(v.strip())).toList());
                var c = RedTeam3.coordinate(variant, selected); assertTrue(coordinates.add(c.comboId()));
                var plan = RedTeam3.plan(variant, c, 1423);
                assertEquals(1 + (c.memory() ? 1 : 0) + (c.training() ? 2 : 0), plan.calls().size());
                assertEquals(c.endpoint(), plan.calls().get((c.memory() ? 1 : 0) + (c.training() ? 1 : 0)).path());
            }
            assertEquals(0, coordinates.first()); assertEquals(63, coordinates.last());
        }
    }
}
