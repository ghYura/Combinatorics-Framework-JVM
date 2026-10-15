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

import org.ghyura.cf.constraints.ColumnLayout;
import org.ghyura.cf.constraints.PredicateEvaluator;
import org.ghyura.cf.constraints.Py;
import org.ghyura.cf.constraints.RuleEngine;
import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.rule.Expr;
import org.ghyura.cf.model.rule.ExprParser;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A06, independent oracle: the evaluator against the legacy implementations themselves. The golden files were
 * written once (2026-10-02) by running byte-identical copies of the legacy code outside the build:
 * <ul>
 *   <li>{@code sieve-golden.xml}: generator_trunk/constraints/sieve.py (SHA-256 in the file) {@code row_violations}
 *       on 600 generated sidecars x 12 rows (every body family, every condition operator, gates, orders, params of
 *       every type, absent sheets, multi-value selections), and CPython 3.14 {@code eval} with the sieve's restricted
 *       environment on 3000 generated predicates; generator gen_sieve_golden.py, SHA-256
 *       d9344135acf72525b1268a3d19cfd30824989b01a0b4d842a2770ec3c6e44006;</li>
 *   <li>{@code bond-golden.xml}: Reader_trunk OptionalBondFilter (SHA-256 in the file), compiled on its own, on 400
 *       generated bond files x 12 candidates; generator GenBondGolden.java, SHA-256
 *       0d4dd03b50200fb64977e96f45079082fc8417836d17e45ca58e6a585f2be05d.</li>
 * </ul>
 * The expected values therefore come from the legacy code path, not from this build's.
 */
class LegacyConstraintGoldenTest {

    private static Document golden(String name) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (InputStream in = LegacyConstraintGoldenTest.class.getResourceAsStream("golden/" + name)) {
            return factory.newDocumentBuilder().parse(in);
        }
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element e && e.getTagName().equals(tag)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Labels for the golden values: one per distinct text, the text being the label's value. */
    private static final class Labels {
        private final Map<String, Integer> byText = new LinkedHashMap<>();
        private final List<String> texts = new ArrayList<>();

        int of(String text) {
            return byText.computeIfAbsent(text, t -> {
                texts.add(t);
                return texts.size() - 1;
            });
        }

        String text(int label) {
            return texts.get(label);
        }
    }

    @Test
    void theRuleEngineAgreesWithTheLegacySieveOnEveryGoldenRow() throws Exception {
        Element root = golden("sieve-golden.xml").getDocumentElement();
        List<String> sheets = List.of(root.getAttribute("sheets").split(","));
        List<ColumnLayout.Column> columns = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            columns.add(new ColumnLayout.Column(sheets.get(i), i + 1, false));
        }
        ColumnLayout layout = new ColumnLayout(columns);
        Labels labels = new Labels();
        int rows = 0;
        int errors = 0;
        int withHits = 0;
        for (Element c : children(root, "case")) {
            ConstraintSet set = ConstraintSidecarReader.read(InputSource.ofText(c.getAttribute("id"), c.getAttribute("sidecar")));
            List<String> values = new ArrayList<>();
            for (Element row : children(c, "row")) {
                for (Element col : children(row, "col")) {
                    for (Element v : children(col, "v")) {
                        labels.of(v.getTextContent());
                        values.add(v.getTextContent());
                    }
                }
            }
            RuleEngine engine = RuleEngine.compile(set, layout, sheets, labels::text, values);
            for (Element row : children(c, "row")) {
                rows++;
                int[][] view = new int[sheets.size()][];
                for (Element col : children(row, "col")) {
                    int column = sheets.indexOf(col.getAttribute("sheet"));
                    if (col.getAttribute("absent").equals("true")) {
                        continue;
                    }
                    List<Element> vs = children(col, "v");
                    view[column] = vs.stream().mapToInt(v -> labels.of(v.getTextContent())).toArray();
                }
                String where = c.getAttribute("id") + " " + c.getAttribute("sidecar") + " row " + row.getTextContent();
                if (row.hasAttribute("error")) {
                    errors++;
                    FrameworkException e = org.junit.jupiter.api.Assertions.assertThrows(FrameworkException.class,
                            () -> engine.violations(column -> view[column]), where);
                    assertEquals(ErrorCode.ASSEMBLY_RULE_ERROR, e.diagnostics().getFirst().code(), where);
                    continue;
                }
                BitSet hits = engine.violations(column -> view[column]);
                List<String> names = new ArrayList<>();
                hits.stream().forEach(i -> names.add(engine.name(i)));
                String expected = row.getAttribute("hits");
                assertEquals(expected.isEmpty() ? List.of() : List.of(expected.split(",")), names, where);
                if (!names.isEmpty()) {
                    withHits++;
                }
            }
        }
        assertTrue(rows > 7000 && errors > 500 && withHits > 2000, rows + " rows, " + errors + " errors, " + withHits + " with hits");
    }

    @Test
    void predicatesEvaluateAsCPythonEvaluatedThem() throws Exception {
        Element root = golden("sieve-golden.xml").getDocumentElement();
        Element predicates = children(root, "predicates").getFirst();
        int checked = 0;
        for (Element p : children(predicates, "predicate")) {
            String expr = p.getAttribute("expr");
            String result = p.getAttribute("result");
            // the environment is read with the production sidecar reader, as the params of a dummy value "_"
            String sidecar = "{\"version\": 1, \"params\": {\"P\": {\"_\": " + section(p.getAttribute("env"), "P") + "}, \"Q\": {\"_\": "
                    + section(p.getAttribute("env"), "Q") + "}}}";
            ConstraintSet env = ConstraintSidecarReader.read(InputSource.ofText("env", sidecar));
            Map<String, Py.Value> names = new LinkedHashMap<>();
            for (String sheet : List.of("P", "Q")) {
                Map<String, Py.Value> attrs = new LinkedHashMap<>();
                env.params().get(sheet).get("_").forEach((k, v) -> attrs.put(k, Py.of(v)));
                names.put(sheet, new Py.Namespace(sheet, attrs));
            }
            String actual;
            try {
                Expr parsed = ExprParser.parse(expr);
                actual = PredicateEvaluator.holds(parsed, names) ? "true" : "false";
            } catch (Py.Failure e) {
                actual = "error:" + e.type();
            } catch (ExprParser.SyntaxError e) {
                actual = "syntax:" + e.getMessage();
            }
            assertEquals(result, actual, expr + " with " + p.getAttribute("env"));
            checked++;
        }
        assertEquals(3000, checked);
    }

    /** The JSON object of one sheet in the generator's environment {"P": {...}, "Q": {...}}. */
    private static String section(String env, String sheet) {
        int start = env.indexOf("\"" + sheet + "\": {") + sheet.length() + 4;
        int depth = 0;
        for (int i = start; i < env.length(); i++) {
            char ch = env.charAt(i);
            if (ch == '"') {
                i = env.indexOf('"', i + 1);
                while (env.charAt(i - 1) == '\\') {
                    i = env.indexOf('"', i + 1);
                }
            } else if (ch == '{') {
                depth++;
            } else if (ch == '}' && --depth == 0) {
                return env.substring(start, i + 1);
            }
        }
        throw new IllegalArgumentException(env);
    }

    @Test
    void legacyBondsFilterCandidatesAsTheReaderFilterDid() throws Exception {
        Element root = golden("bond-golden.xml").getDocumentElement();
        List<String> sheets = List.of(root.getAttribute("sheets").split(","));
        List<ColumnLayout.Column> columns = new ArrayList<>();
        for (int i = 0; i < sheets.size(); i++) {
            columns.add(new ColumnLayout.Column(sheets.get(i), i + 1, true));
        }
        ColumnLayout layout = new ColumnLayout(columns);
        int rows = 0;
        int violating = 0;
        for (Element c : children(root, "case")) {
            ConstraintSet set = ConstraintSidecarReader.readBonds(InputSource.ofText(c.getAttribute("id"), c.getAttribute("bonds")));
            RuleEngine engine = RuleEngine.compile(set, layout, sheets, label -> Integer.toString(label), List.of());
            for (Element row : children(c, "row")) {
                int[][] view = new int[sheets.size()][];
                for (Element col : children(row, "col")) {
                    if (!col.getAttribute("absent").equals("true")) {
                        view[sheets.indexOf(col.getAttribute("sheet"))] = children(col, "l").stream()
                                .mapToInt(l -> Integer.parseInt(l.getTextContent())).toArray();
                    }
                }
                boolean expected = Boolean.parseBoolean(row.getAttribute("violates"));
                assertEquals(expected, !engine.violations(column -> view[column]).isEmpty(),
                        c.getAttribute("bonds") + " / " + row.getTextContent());
                rows++;
                violating += expected ? 1 : 0;
            }
        }
        assertEquals(4800, rows);
        assertTrue(violating > 1000, "the golden rows exercise both outcomes: " + violating);
    }

    /**
     * coverage-golden.xml: the legacy fwgen nwise_greedy and nwise_optimal (copy of generator_trunk/fwgen.py, SHA-256 in
     * the file) on 500 random level grids, some rows removed as rules would; generator gen_cov_golden.py, SHA-256
     * bf48aa9ed144b18171a0a3edefc54aa56c787ba64413575e1a88a9311f3cbfb4. Besides agreeing with the legacy choice, each
     * selection is checked independently: every feasible tuple (one some allowed row contains) is in a kept row, and the
     * infeasible count is what a brute-force walk over the full level product finds.
     */
    @Test
    void coverageSelectionsAreTheLegacyReducersAndCoverEveryFeasibleTuple() throws Exception {
        Element root = golden("coverage-golden.xml").getDocumentElement();
        int cases = 0;
        for (Element c : children(root, "case")) {
            int[] levels = java.util.Arrays.stream(c.getAttribute("levels").split(",")).mapToInt(Integer::parseInt).toArray();
            int t = Integer.parseInt(c.getAttribute("t"));
            List<int[]> rows = new ArrayList<>();
            for (String row : c.getAttribute("rows").split(";")) {
                rows.add(java.util.Arrays.stream(row.split(",")).mapToInt(Integer::parseInt).toArray());
            }
            for (org.ghyura.cf.constraints.Coverage.Method method : org.ghyura.cf.constraints.Coverage.Method.values()) {
                org.ghyura.cf.constraints.Coverage.Selection selection = org.ghyura.cf.constraints.Coverage.select(rows, levels, t, method,
                        org.ghyura.cf.spi.Cancellation.NONE);
                String expected = c.getAttribute(method == org.ghyura.cf.constraints.Coverage.Method.GREEDY ? "greedy" : "set-cover");
                assertEquals(expected, String.join(",", java.util.Arrays.stream(selection.kept()).mapToObj(Integer::toString).toList()),
                        method + " " + c.getAttribute("levels") + " t=" + t + " rows " + c.getAttribute("rows"));
                // independent check: brute force over factor subsets and level combinations
                java.util.Set<String> feasible = new java.util.HashSet<>();
                java.util.Set<String> covered = new java.util.HashSet<>();
                long universe = 0;
                for (int[] subset : subsets(levels.length, t)) {
                    long product = 1;
                    for (int f : subset) {
                        product *= levels[f];
                    }
                    universe += product;
                    for (int r = 0; r < rows.size(); r++) {
                        StringBuilder key = new StringBuilder(java.util.Arrays.toString(subset));
                        for (int f : subset) {
                            key.append('/').append(rows.get(r)[f]);
                        }
                        feasible.add(key.toString());
                        int finalR = r;
                        if (java.util.Arrays.stream(selection.kept()).anyMatch(k -> k == finalR)) {
                            covered.add(key.toString());
                        }
                    }
                }
                assertEquals(feasible, covered, "every feasible tuple is covered");
                assertEquals(feasible.size(), selection.feasible());
                assertEquals(feasible.size(), selection.covered());
                assertEquals(universe, selection.universe());
                assertEquals(universe - feasible.size(), selection.infeasible(), "infeasible tuples are counted apart");
            }
            cases++;
        }
        assertEquals(500, cases);
    }

    private static List<int[]> subsets(int n, int t) {
        List<int[]> out = new ArrayList<>();
        int[] idx = new int[t];
        for (int i = 0; i < t; i++) {
            idx[i] = i;
        }
        while (true) {
            out.add(idx.clone());
            int i = t - 1;
            while (i >= 0 && idx[i] == n - t + i) {
                i--;
            }
            if (i < 0) {
                return out;
            }
            idx[i]++;
            for (int j = i + 1; j < t; j++) {
                idx[j] = idx[j - 1] + 1;
            }
        }
    }

    @Test
    void theGoldenFilesRecordTheirLegacySources() throws Exception {
        Element sieve = golden("sieve-golden.xml").getDocumentElement();
        assertEquals("41dcfd67d953961af467a95708d8b7953c6454b695477f2e5487aec2f26d7df0", sieve.getAttribute("source-sha256"));
        assertEquals("0bc3e5acf6cf8f612a08ce640d236132c448b29b", sieve.getAttribute("source-commit"));
        Element bond = golden("bond-golden.xml").getDocumentElement();
        assertEquals("0bc3e5acf6cf8f612a08ce640d236132c448b29b", bond.getAttribute("source-commit"));
        assertTrue(bond.getAttribute("source-sha256").matches("[0-9a-f]{64}"));
        java.nio.file.Path checkout = Reactor.root().getParent().resolve("Combinatorics-Framework-QA-edition");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(checkout.resolve("Core_trunk")),
                "the legacy checkout is not beside the project: the golden sources' hashes cannot be rechecked");
        assertEquals(sieve.getAttribute("source-sha256"), sha256(checkout.resolve("generator_trunk/constraints/sieve.py")));
        assertEquals(bond.getAttribute("source-sha256"), sha256(checkout.resolve("Reader_trunk/src/main/java/com/company/OptionalBondFilter.java")));
    }

    private static String sha256(java.nio.file.Path file) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(java.nio.file.Files.readAllBytes(file)));
    }
}
