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

import org.ghyura.cf.generation.RenderedSource;
import org.ghyura.cf.input.InputFormat;
import org.ghyura.cf.input.WorkbookFormats;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CandidateOriginsTest {
    @Test
    void allFourContainersKeepPhysicalLocationsOrderedActionPairsAndSuddenOptionalActions(@TempDir Path dir) throws Exception {
        WorkbookData workbook = new WorkbookData("test", List.of(
                sheet("FW_Seq", new String[]{"HEAD", "FW_Combi(1)"}, new String[]{"Actions", "FW_PermutR(2)"},
                        new String[]{"Sudden", "FW_Optional", "FW_Combi(1)"}, new String[]{"TAIL", "FW_Combi(1)"}),
                sheet("HEAD", new String[]{"function demo(){"}),
                sheet("Actions", new String[]{"ping();"}, new String[]{"pong();"}),
                sheet("Sudden", new String[]{"cancel();"}), sheet("TAIL", new String[]{"}"})));
        Session session = SessionTest.session("cf.plan.autoPromoteSingleVerb=false", "cf.render.preserveWhitespace=true");
        for (InputFormat format : InputFormat.values()) {
            Path file = dir.resolve("actions." + format.token());
            byte[] bytes = WorkbookFormats.writer(format).write(workbook);
            Files.write(file, bytes);
            var physical = WorkbookFormats.readFile(file).workbook();
            var preview = session.preview(file, new CandidatePreview.Request(BigInteger.valueOf(100), BigInteger.ZERO, 50, false, false), Cancellation.NONE);
            assertEquals(8, preview.page().rows().size(), format.token());
            var pairsAbsent = new HashSet<String>();
            var pairsPresent = new HashSet<String>();
            for (var row : preview.page().rows()) {
                List<RenderedSource.Origin> values = row.rendered().chunks().stream().flatMap(c -> c.origins().stream())
                        .filter(o -> o.kind() == RenderedSource.Kind.VALUE).toList();
                for (var value : values) {
                    assertTrue(physical.sheet(value.sheet()).orElseThrow().rows().stream().flatMap(r -> r.cells().stream())
                            .anyMatch(c -> c.location().equals(value.location())), format + ": " + value);
                    assertEquals(file.toString(), value.location().origin());
                }
                var steps = values.stream().filter(o -> o.sheet().equals("Actions")).toList();
                assertEquals(List.of(1, 2), steps.stream().map(RenderedSource.Origin::occurrence).toList());
                String pair = steps.stream().map(o -> o.location().path()).collect(java.util.stream.Collectors.joining(" → "));
                boolean sudden = values.stream().anyMatch(RenderedSource.Origin::optional);
                (sudden ? pairsPresent : pairsAbsent).add(pair);
                assertEquals(sudden, row.source().contains("cancel();"));
            }
            assertEquals(4, pairsAbsent.size(), "all ordered pairs, including repeated actions");
            assertEquals(pairsAbsent, pairsPresent, "each sequence also has a real optional intervention");
            assertArrayEquals(bytes, Files.readAllBytes(file), "preview never rewrites the container");
        }
    }

    @Test
    void decomposedSpecPointsBackToItsValuePath(@TempDir Path dir) throws Exception {
        Path file = Files.writeString(dir.resolve("code.json"), """
                {"slots":[{"sheet":"Query","values":["SELECT * FROM events;"],"verb":"FW_Combi(1)"}]}
                """);
        var preview = SessionTest.session().preview(file,
                new CandidatePreview.Request(BigInteger.TEN, BigInteger.ZERO, 1, true, false), Cancellation.NONE);
        var origin = preview.page().rows().getFirst().rendered().chunks().stream().flatMap(c -> c.origins().stream())
                .filter(o -> o.kind() == RenderedSource.Kind.VALUE).findFirst().orElseThrow();
        assertEquals("/slots/0/values/0", origin.location().path());
        assertEquals("Query", origin.sheet());
    }

    private static InputSheet sheet(String name, String[]... rows) {
        var result = new ArrayList<InputRow>();
        for (String[] row : rows) {
            var cells = new ArrayList<InputCell>();
            for (String text : row) cells.add(InputCell.text(cells.size(), text, SourceLocation.NONE));
            result.add(new InputRow(result.size(), cells));
        }
        return new InputSheet(name, SourceLocation.NONE, result);
    }
}
