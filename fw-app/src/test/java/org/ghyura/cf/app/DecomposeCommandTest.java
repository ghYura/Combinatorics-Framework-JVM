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

package org.ghyura.cf.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:source-decomposition end to end: the spec decompose writes is read by the JVM and reassembles the source exactly. */
class DecomposeCommandTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static Element root(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))).getDocumentElement();
    }

    private static final String SOURCE = """
            package demo;

            import java.util.List;

            /** Pieces with blank lines, comments and a text block. */
            public class Demo {

                static final String QUERY = \"\"\"
                    SELECT "a" FROM t WHERE x = '}'
                    \"\"\";

                // the size of a list
                static int size(List<String> items) {
                    return items.size();
                }

                static int twice(int x) {
                    return x * 2;
                }
            }
            """;

    /** The first candidate of the spec, rendered verbatim and concatenated with nothing between the pieces. */
    private static String reassembled(Path spec) throws Exception {
        Result r = run("candidates", "--scenario", spec.toString(), "--limit", "1", "--format", "xml", "--set", "cf.render.preserveWhitespace=true",
                "--set", "cf.render.concatenator=");
        assertEquals(0, r.code(), r.err());
        return root(r.out()).getElementsByTagName("source").item(0).getTextContent();
    }

    @Test
    void theSpecReadsBackAndItsFirstCandidateIsTheSourceExactly(@TempDir Path dir) throws Exception {
        for (String text : new String[]{SOURCE, SOURCE.replace("\n", "\r\n")}) {
            Path source = Files.writeString(dir.resolve("Demo.java"), text, StandardCharsets.UTF_8);
            Path spec = dir.resolve("demo.toml");
            Result listed = run("decompose", "--source", source.toString(), "--out", spec.toString());
            assertEquals(0, listed.code(), listed.err());
            assertTrue(listed.out().startsWith("5 pieces → " + spec), listed.out());
            assertTrue(listed.out().contains("  P03 method    size"), listed.out());
            assertEquals(text, reassembled(spec), "decompose, read, plan and render give the source back byte for byte");

            Result split = run("decompose", "--source", source.toString(), "--out", spec.toString(), "--max-cell", "60");
            assertEquals(0, split.code(), split.err());
            assertTrue(split.out().contains("__part2"), split.out());
            assertEquals(text, reassembled(spec), "oversize parts reassemble too");
        }
        Path source = Files.writeString(dir.resolve("Demo.java"), SOURCE, StandardCharsets.UTF_8);
        Result xml = run("decompose", "--source", source.toString(), "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        Element decomposition = (Element) root(xml.out()).getElementsByTagName("decomposition").item(0);
        assertEquals("5", decomposition.getAttribute("pieces"));
        Element field = (Element) decomposition.getElementsByTagName("piece").item(1);
        assertEquals("QUERY", field.getAttribute("name"));
        assertEquals("7", field.getAttribute("line"), "the field's piece starts after the header's line");
        assertTrue(decomposition.getElementsByTagName("spec").item(0).getTextContent().startsWith("# ====="), "without --out the spec is inline");

        Result plain = run("decompose", "--source", source.toString());
        assertTrue(plain.out().contains("[[slots]]") && plain.out().contains("raw    = true"), plain.out());
    }

    private static final String PYTHON = """
            import os

            CONST = 2

            @staticmethod
            def twice(x):
                return x * CONST


            class Shape:
                def area(self):
                    return twice(os.sep)
            """;

    @Test
    void pythonSourcesSplitAsTheLegacyToolAndTheLegacyScannerRefusesTextBlocks(@TempDir Path dir) throws Exception {
        Path py = Files.writeString(dir.resolve("shapes.py"), PYTHON, StandardCharsets.UTF_8);
        Path spec = dir.resolve("shapes.toml");
        Result split = run("decompose", "--source", py.toString(), "--out", spec.toString());
        assertEquals(0, split.code(), split.err());
        assertTrue(split.out().startsWith("3 pieces \u2192 " + spec), "auto chose Python: " + split.out());
        assertTrue(split.out().contains("  P02 function  twice") && split.out().contains("  P03 class     Shape"), split.out());
        assertEquals(PYTHON, reassembled(spec), "the Python pieces reassemble the source byte for byte");

        Path source = Files.writeString(dir.resolve("Demo.java"), SOURCE, StandardCharsets.UTF_8);
        Result python = run("decompose", "--source", source.toString(), "--lang", "py");
        assertEquals(3, python.code(), python.err());
        assertTrue(python.err().contains("INPUT_MALFORMED") && python.err().contains("does not parse as Python"), python.err());
        // the class whose text block broke the legacy scanner (its escaped quote inside the block)
        Path textBlock = Files.writeString(dir.resolve("TextBlocks.java"), "public class TextBlocks {\n    static final String JSON = \"\"\"\n"
                + "        {\"key\": \"value\", \"quote\": \"\\\\\" }\"\n        \"\"\";\n\n    static int after() {\n        return 1;\n    }\n}\n",
                StandardCharsets.UTF_8);
        Result legacy = run("decompose", "--source", textBlock.toString(), "--lang", "java-heuristic");
        assertNotEquals(0, legacy.code());
        assertTrue(legacy.err().contains("INPUT_MALFORMED") && legacy.err().contains("text blocks"), legacy.err());
        Result unknown = run("decompose", "--source", source.toString(), "--lang", "cobol");
        assertEquals(2, unknown.code(), unknown.err());
    }
}
