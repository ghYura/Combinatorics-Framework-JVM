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

package org.ghyura.cf.input;

import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * GEN:operator-authoring-ui and GEN:higher-order-dataflow, the FW_Seq dependency graph, against the unchanged legacy
 * fwseq_graph.build_graph: seq-graph-goldens.jsonl (made by handoff/outbox/devtools/graph/graph_goldens.py on a scratch copy)
 * holds the summary line, issues, JSON, DOT and hash of the hand-made spec documents (fwgen.parse_spec), of hand-made
 * workbooks and of the 32 legacy workbooks (fwgen.load_workbook_spec, as bundle plan read them).
 */
class SeqGraphTest {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> goldens() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream in = SeqGraphTest.class.getResourceAsStream("seq-graph-goldens.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    out.add((Map<String, Object>) Json.parse(line, "seq-graph-goldens.jsonl"));
                }
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static void assertGraph(Map<String, Object> g, String name, SeqGraph graph) {
        String what = g.get("case") + " " + name;
        assertEquals(g.get("summary"), graph.summary(name), what);
        List<List<Object>> issues = new ArrayList<>();
        for (SeqGraph.Issue i : graph.validate()) {
            issues.add(List.of(i.level(), i.code(), i.message(), List.copyOf(i.nodes())));
        }
        assertEquals(g.get("issues"), issues, what);
        assertEquals(g.get("json"), graph.json(), what);
        assertEquals(g.get("dot"), graph.dot(), what);
        assertEquals(g.get("hash"), graph.hash(), what);
    }

    private static String stem(String file) {
        return file.substring(0, file.lastIndexOf('.'));
    }

    @Test
    void everyGraphIsTheLegacyGraphExactly() throws IOException, URISyntaxException {
        int specs = 0;
        int refused = 0;
        int workbooks = 0;
        int corpus = 0;
        for (Map<String, Object> g : goldens()) {
            switch ((String) g.get("case")) {
                case "spec" -> {
                    String filename = (String) g.get("filename");
                    InputSource source = InputSource.ofText(filename, (String) g.get("source"));
                    InputFormat format = WorkbookFormats.formatOf(filename);
                    if (g.containsKey("error") || ((String) g.get("name")).startsWith("delta-")) {
                        // the legacy refused these too, or (delta-) parse_spec accepted what D57 refuses: a goal without a
                        // direction it can honour, two slots of one sheet
                        assertThrows(FrameworkException.class, () -> SpecReader.read(source, format), filename);
                        refused++;
                    } else {
                        assertGraph(g, stem(filename), SpecReader.graph(source, format));
                        specs++;
                    }
                }
                case "workbook" -> {
                    String name = (String) g.get("name");
                    WorkbookData workbook = WorkbookFormats.reader(InputFormat.JSON).read(InputSource.ofText(name + ".json", (String) g.get("source")))
                            .workbook();
                    assertGraph(g, name, SeqGraph.ofWorkbook(workbook));
                    workbooks++;
                }
                case "corpus-workbook" -> {
                    String file = (String) g.get("file");
                    Path path = Path.of(SeqGraphTest.class.getResource("legacy/" + file).toURI());
                    assertGraph(g, stem(file), SeqGraph.ofWorkbook(WorkbookFormats.reader(InputFormat.XLSX).read(InputSource.read(path)).workbook()));
                    corpus++;
                }
                default -> throw new AssertionError(g.get("case"));
            }
        }
        assertEquals(List.of(12, 26, 5, 32), List.of(specs, refused, workbooks, corpus));
    }

    @Test
    void aSpecWhoseGraphHasAnErrorIsRefusedWithTheLegacyGateMessage() {
        InputSource source = InputSource.ofText("gi.toml", """
                seq_extra = [["J1", "FW_(,,A,,B,,,,1:1)"], ["J2", "FW_(,,A,,B,,,,1:1)"]]
                [[slots]]
                sheet = "A"
                flags = ["FW_Exclude", "FW_ReuseTableOnly"]
                values = ["a"]
                [[slots]]
                sheet = "B"
                flags = ["FW_Exclude", "FW_Reuse"]
                values = ["b"]
                [[slots]]
                sheet = "J1"
                values = ["j"]
                [[slots]]
                sheet = "J2"
                values = ["j"]
                """);
        FrameworkException e = assertThrows(FrameworkException.class, () -> SpecReader.read(source, InputFormat.TOML));
        assertEquals("FW_Seq dependency graph rejected before workbook: [consumed_result_ambiguity] result of 'A' is consumed by 2 braces "
                + "(brace#0.1, brace#1.1) but its rows are cleaned after the first join (no FW_Reuse), so the later joins would read an "
                + "empty operand — mark it FW_Reuse to reuse its data", e.diagnostics().getFirst().message());
        assertEquals(2, SpecReader.graph(source, InputFormat.TOML).errors().size() + 1);
    }
}
