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

package org.ghyura.cf.compiler;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GEN:source-decomposition against the unchanged legacy code_decompose.py: decompose-goldens.jsonl holds, per input
 * and language, the legacy pieces and TOML spec (made by handoff/outbox/devtools/decompose/decompose_goldens.py on a
 * scratch copy of the reference tree; the inputs are real Reader and Executor sources and edge cases).
 */
class SourceDecompositionTest {

    private static Object read(JsonParser p) throws IOException {
        JsonToken t = p.currentToken();
        return switch (t) {
            case START_OBJECT -> {
                Map<String, Object> m = new LinkedHashMap<>();
                while (p.nextToken() != JsonToken.END_OBJECT) {
                    String key = p.currentName();
                    p.nextToken();
                    m.put(key, read(p));
                }
                yield m;
            }
            case START_ARRAY -> {
                List<Object> l = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    l.add(read(p));
                }
                yield l;
            }
            case VALUE_STRING -> p.getText();
            case VALUE_NUMBER_INT -> p.getIntValue();
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            default -> null;
        };
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> goldens() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream in = SourceDecompositionTest.class.getResourceAsStream("decompose-goldens.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    try (JsonParser p = new JsonFactory().createParser(line)) {
                        p.nextToken();
                        out.add((Map<String, Object>) read(p));
                    }
                }
            }
        }
        return out;
    }

    private static List<Map<String, Object>> asValues(List<SourceDecomposition.Piece> pieces) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SourceDecomposition.Piece p : pieces) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("kind", p.kind());
            m.put("name", p.name());
            m.put("code", p.code());
            m.put("defines", new ArrayList<>(p.defines()));
            m.put("uses", new ArrayList<>(p.uses()));
            m.put("verb", p.verb());
            m.put("note", p.note());
            out.add(m);
        }
        return out;
    }

    private static List<SourceDecomposition.Piece> split(Map<String, Object> g, SourceDecomposition.Language language) {
        List<SourceDecomposition.Piece> pieces = SourceDecomposition.decompose((String) g.get("source"), language);
        int limit = (Integer) g.get("max_cell");
        return limit > 0 ? SourceDecomposition.subsplitOversize(pieces, limit) : pieces;
    }

    @Test
    void theLegacyHeuristicAndTheBlankLineSplitAreTheLegacyToolsExactly() throws IOException {
        List<Map<String, Object>> cases = goldens();
        assertEquals(20, cases.size());
        int refused = 0;
        for (Map<String, Object> g : cases) {
            String name = (String) g.get("case");
            SourceDecomposition.Language language = g.get("lang").equals("java") ? SourceDecomposition.Language.JAVA_HEURISTIC
                    : SourceDecomposition.Language.GENERIC;
            if (g.containsKey("refused")) {
                FrameworkException e = assertThrows(FrameworkException.class, () -> split(g, language), name);
                assertEquals(ErrorCode.INPUT_MALFORMED, e.diagnostics().getFirst().code(), name);
                refused++;
                continue;
            }
            List<SourceDecomposition.Piece> pieces = split(g, language);
            assertEquals(g.get("pieces"), asValues(pieces), name + ": pieces");
            String toml = SourceDecomposition.toml(pieces, (String) g.get("name"));
            assertEquals(withoutValues((String) g.get("toml")), withoutValues(toml), name + ": the spec around its values");
            assertEquals(pieces.stream().map(SourceDecomposition.Piece::code).toList(), values(toml), name + ": every value reads back to its piece");
            assertEquals(g.get("source"), SourceDecomposition.join(pieces), name + ": byte-preserving");
        }
        assertEquals(1, refused, "the legacy scanner refuses the text-block class");
    }

    private static final java.util.regex.Pattern VALUE = java.util.regex.Pattern.compile("(?s)values = \\[\n(.*?),\n  # \u2191 baseline");

    /** The spec with each value block replaced by a marker: the legacy text and ours differ only inside them. */
    private static String withoutValues(String toml) {
        return VALUE.matcher(toml).replaceAll("values = [\n<value>,\n  # \u2191 baseline");
    }

    /** The values of the spec, decoded from our two block forms. */
    private static List<String> values(String toml) {
        List<String> out = new ArrayList<>();
        java.util.regex.Matcher m = VALUE.matcher(toml);
        while (m.find()) {
            String block = m.group(1);
            String body = block.substring(4, block.length() - 3);
            if (block.startsWith("'''\n")) {
                out.add(body);
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (int k = 0; k < body.length(); k++) {
                char c = body.charAt(k);
                if (c != '\\') {
                    text.append(c);
                    continue;
                }
                char e = body.charAt(++k);
                switch (e) {
                    case 'r' -> text.append('\r');
                    case 'u' -> {
                        text.append((char) Integer.parseInt(body.substring(k + 1, k + 5), 16));
                        k += 4;
                    }
                    default -> text.append(e);
                }
            }
            out.add(text.toString());
        }
        return out;
    }

    @Test
    void piecesWithEdgesTheLegacyValuesLostStillReadBack() {
        List<String> codes = List.of("\nstarts with a newline\n", "no final newline", "ends with quote'", "ends with two quotes''",
                "holds ''' three quotes\n", "a\r\nCRLF line\r\n", "a tab\tand a \u0001 control", "back\\slash \"\"\" quotes \"\"\"\"");
        for (String code : codes) {
            String value = SourceDecomposition.tomlValue(code);
            assertEquals(List.of(code), values("values = [\n" + value + ",\n  # \u2191 baseline"), code);
        }
    }

    @Test
    void theSyntaxAwareSplitAgreesWhereTheHeuristicIsRightAndMendsWhereItIsNot() throws IOException {
        for (Map<String, Object> g : goldens()) {
            String name = (String) g.get("case");
            if (!g.get("lang").equals("java")) {
                continue;
            }
            List<SourceDecomposition.Piece> pieces = split(g, SourceDecomposition.Language.JAVA);
            assertEquals(g.get("source"), SourceDecomposition.join(pieces), name + ": byte-preserving");
            if (name.startsWith("edge-members") || name.startsWith("edge-textblock")) {
                continue;
            }
            assertEquals(g.get("pieces"), asValues(pieces), name + ": the same pieces as the legacy tool");
        }
        Map<String, Object> members = goldens().stream().filter(g -> g.get("case").equals("edge-members.java.in:java")).findFirst().orElseThrow();
        assertEquals(2, ((List<?>) members.get("pieces")).size(), "the legacy scanner took the annotation's brace for the body: header and footer only");
        List<SourceDecomposition.Piece> mended = SourceDecomposition.decompose((String) members.get("source"), SourceDecomposition.Language.JAVA);
        // one piece per member, its leading comment or annotation with it; names and kinds keep the legacy rules (a static
        // block is named by the field it assigns, a nested type by its first method)
        assertEquals(List.of("header", "field", "field", "field", "field", "field", "member", "method", "method", "field", "method", "member",
                "method", "method", "footer"), mended.stream().map(SourceDecomposition.Piece::kind).toList());
        assertEquals(List.of("", "TABLE", "brace", "open", "counter", "counter", "", "Members", "wrap", "twice", "size", "", "Pair", "area", ""),
                mended.stream().map(SourceDecomposition.Piece::name).toList());
        assertTrue(mended.getFirst().code().endsWith("public class Members {\n"), mended.getFirst().code());
        assertEquals("}\n", mended.getLast().code());

        Map<String, Object> text = goldens().stream().filter(g -> g.get("case").equals("edge-textblock.java.in:java")).findFirst().orElseThrow();
        List<SourceDecomposition.Piece> blocks = SourceDecomposition.decompose((String) text.get("source"), SourceDecomposition.Language.JAVA);
        assertEquals(List.of("header", "field", "method", "method", "footer"), blocks.stream().map(SourceDecomposition.Piece::kind).toList(),
                "a text block holding quotes and braces does not break the split");
        assertEquals(List.of("JSON", "sql", "after"), blocks.subList(1, 4).stream().map(SourceDecomposition.Piece::name).toList());
    }

    @Test
    void autoChoosesPythonJavaOrBlankLinesAsTheLegacyToolDid() {
        String java = "class A {\n    int x;\n}\n";
        assertEquals("header", SourceDecomposition.decompose(java, SourceDecomposition.Language.AUTO).getFirst().kind());
        assertEquals("statements", SourceDecomposition.decompose("a\n\nb\n", SourceDecomposition.Language.AUTO).getFirst().kind(),
                "two bare names parse as Python, so the legacy auto chose py");
        assertEquals("block", SourceDecomposition.decompose("Hello, world!\n\nMore prose.\n", SourceDecomposition.Language.AUTO).getFirst().kind());
        FrameworkException e = assertThrows(FrameworkException.class, () -> SourceDecomposition.decompose("x = (\n",
                SourceDecomposition.Language.PYTHON));
        assertEquals(ErrorCode.INPUT_MALFORMED, e.diagnostics().getFirst().code());
        assertEquals(Map.entry(3, 5), SourceDecomposition.lineAndColumn("ab\ncd\nefgh", 10));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pythonGoldens() throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        try (InputStream in = SourceDecompositionTest.class.getResourceAsStream("decompose-py-goldens.jsonl")) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.isBlank()) {
                    try (JsonParser p = new JsonFactory().createParser(line)) {
                        p.nextToken();
                        out.add((Map<String, Object>) read(p));
                    }
                }
            }
        }
        return out;
    }

    @Test
    void pythonNestingHonorsTheTokenizerLimitWithoutOverflowingTheStack() {
        String allowed = "x = " + "(".repeat(200) + "1" + ")".repeat(200) + "\n";
        assertEquals("statements", SourceDecomposition.decompose(allowed, SourceDecomposition.Language.PYTHON).getFirst().kind());
        for (int depth : new int[] {201, 500}) {
            String refused = "x = " + "(".repeat(depth) + "1" + ")".repeat(depth) + "\n";
            FrameworkException failure = assertThrows(FrameworkException.class,
                    () -> SourceDecomposition.decompose(refused, SourceDecomposition.Language.PYTHON));
            assertEquals(ErrorCode.INPUT_MALFORMED, failure.diagnostics().getFirst().code());
        }
        StringBuilder indented = new StringBuilder();
        for (int depth = 0; depth < 100; depth++) {
            indented.append(" ".repeat(depth)).append("if True:\n");
        }
        indented.append(" ".repeat(100)).append("pass\n");
        FrameworkException indentation = assertThrows(FrameworkException.class,
                () -> SourceDecomposition.decompose(indented.toString(), SourceDecomposition.Language.PYTHON));
        assertEquals(ErrorCode.INPUT_MALFORMED, indentation.diagnostics().getFirst().code());
    }

    @Test
    void pythonTemplateStringNestingMatchesTheTokenizerBoundary() {
        for (String prefix : List.of("f", "t")) {
            String expression = "value";
            for (int depth = 1; depth <= 300; depth++) {
                expression = prefix + "\"{" + expression + "}\"";
                if (depth == 149) {
                    var piece = SourceDecomposition.decompose("result = " + expression + "\n", SourceDecomposition.Language.PYTHON).getFirst();
                    assertEquals(java.util.Set.of("value"), piece.uses());
                } else if (depth == 150 || depth == 300) {
                    String refused = "result = " + expression + "\n";
                    FrameworkException failure = assertThrows(FrameworkException.class,
                            () -> SourceDecomposition.decompose(refused, SourceDecomposition.Language.PYTHON));
                    assertEquals(ErrorCode.INPUT_MALFORMED, failure.primary().code());
                }
            }
            var siblings = SourceDecomposition.decompose(("result = " + prefix + "\"{value}\"\n").repeat(300),
                    SourceDecomposition.Language.PYTHON);
            assertEquals(java.util.Set.of("value"), siblings.getFirst().uses(), "completed strings release their nesting level");
        }
    }

    @Test
    void binaryOperatorSequencesPreserveNamesAndRemainInvalidAssignmentTargets() {
        String operators = "a | b ^ c & d << e >> f + g - h * i / j // k % l @ m ** n";
        var piece = SourceDecomposition.decompose("result = " + operators + "\n", SourceDecomposition.Language.PYTHON).getFirst();
        assertEquals(java.util.Set.of("result"), piece.defines());
        assertEquals(java.util.Set.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m", "n"), piece.uses());
        for (String operator : List.of("|", "^", "&", "<<", ">>", "+", "-", "*", "/", "//", "%", "@", "**")) {
            FrameworkException failure = assertThrows(FrameworkException.class,
                    () -> SourceDecomposition.decompose("left " + operator + " right = value\n", SourceDecomposition.Language.PYTHON));
            assertEquals(ErrorCode.INPUT_MALFORMED, failure.primary().code());
        }
    }

    @Test
    void nestedStringFieldsRetainSiblingNamesAndAssignmentContexts() {
        String expression = "leaf";
        for (int depth = 0; depth < 120; depth++) {
            expression = "f\"{left}{" + expression + "}{(bound := right)}\"";
        }
        var piece = SourceDecomposition.decompose("result = " + expression + "\n", SourceDecomposition.Language.PYTHON).getFirst();
        assertEquals(java.util.Set.of("result"), piece.defines());
        assertEquals(java.util.Set.of("left", "leaf", "right"), piece.uses());
        String malformed = "result = " + expression.replace("{leaf}", "{leaf +}") + "\n";
        FrameworkException failure = assertThrows(FrameworkException.class,
                () -> SourceDecomposition.decompose(malformed, SourceDecomposition.Language.PYTHON));
        assertEquals(ErrorCode.INPUT_MALFORMED, failure.primary().code());
    }

    @Test
    void pythonFormatFieldsAllowTwoNestedLevelsAndRefuseTheThird() {
        for (String prefix : List.of("f", "t")) {
            String expression = "value";
            for (int depth = 0; depth <= 4; depth++) {
                String source = "result = " + prefix + "\"{" + expression + "}\"\n";
                if (depth <= 2) {
                    assertEquals(java.util.Set.of("value"),
                            SourceDecomposition.decompose(source, SourceDecomposition.Language.PYTHON).getFirst().uses());
                } else {
                    FrameworkException failure = assertThrows(FrameworkException.class,
                            () -> SourceDecomposition.decompose(source, SourceDecomposition.Language.PYTHON));
                    assertEquals(ErrorCode.INPUT_MALFORMED, failure.primary().code());
                }
                expression = "value:{" + expression + "}";
            }
        }
    }

    @Test
    void pythonLongOperatorChainsPreserveNamesWithoutRecursingForEveryOperator() {
        for (String expression : List.of("-".repeat(4_000) + "value", "not ".repeat(2_000) + "value",
                "value**".repeat(2_000) + "other")) {
            var piece = SourceDecomposition.decompose("result = " + expression + "\n", SourceDecomposition.Language.PYTHON).getFirst();
            assertEquals(java.util.Set.of("result"), piece.defines());
            assertEquals(expression.endsWith("other") ? java.util.Set.of("value", "other") : java.util.Set.of("value"), piece.uses());
        }
    }

    /**
     * GEN:source-decomposition, Python: decompose-py-goldens.jsonl holds what the unchanged legacy code_decompose.py made of
     * edge cases, the legacy inputs and a sample of real Python files of the reference trees (made by
     * handoff/outbox/devtools/decompose/py_goldens.py; PySurvey.java there compares all 825 files the same way).
     */
    @Test
    void thePythonDecompositionIsTheLegacyToolsExactly() throws IOException {
        List<Map<String, Object>> cases = pythonGoldens();
        assertEquals(44, cases.size());
        int decomposed = 0;
        int refused = 0;
        for (Map<String, Object> g : cases) {
            String name = (String) g.get("case");
            String source = (String) g.get("source");
            boolean python = SourceDecomposition.looksPython(source);
            assertEquals(g.get("looks_python"), python, name + ": _looks_python");
            assertEquals(g.get("auto"), python ? "py" : SourceDecomposition.looksJava(source) ? "java" : "generic", name + ": auto");
            if (g.containsKey("refused")) {
                FrameworkException e = assertThrows(FrameworkException.class, () -> SourceDecomposition.decompose(source,
                        SourceDecomposition.Language.PYTHON), name);
                assertEquals(ErrorCode.INPUT_MALFORMED, e.diagnostics().getFirst().code(), name);
                refused++;
                continue;
            }
            List<SourceDecomposition.Piece> pieces = SourceDecomposition.decompose(source, SourceDecomposition.Language.PYTHON);
            assertEquals(g.get("pieces"), asValues(pieces), name + ": pieces");
            String toml = SourceDecomposition.toml(pieces, (String) g.get("name"));
            assertEquals(withoutValues((String) g.get("toml")), withoutValues(toml), name + ": the spec around its values");
            assertEquals(pieces.stream().map(SourceDecomposition.Piece::code).toList(), values(toml), name + ": every value reads back");
            assertEquals(source, SourceDecomposition.join(pieces), name + ": byte-preserving");
            decomposed++;
        }
        assertEquals(10, refused, "invalid Python, Java and prose are refused as the legacy ast.parse refused them");
        assertTrue(decomposed >= 30, "decomposed: " + decomposed);
    }
}
