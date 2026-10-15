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

package org.ghyura.cf.algebra;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every operator reproduces, tuple by tuple, the order and the edge cases of the legacy library it stands for.
 * The baselines (legacy-orders.xml) were written by running combinatoricslib3 3.3.3 and the parallel library
 * 3.4.2-SNAPSHOT themselves (tools/LegacyOrderFixtures.java; the jars' SHA-256 are in the file).
 */
class LegacyOrderTest {

    private record Case(String op, LegacyLibrary library, Element element, String expected, boolean threw) {
        int attribute(String name) {
            return Integer.parseInt(element.getAttribute(name));
        }
    }

    private static List<Case> cases() throws Exception {
        try (InputStream in = LegacyOrderTest.class.getResourceAsStream("legacy-orders.xml")) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList nodes = factory.newDocumentBuilder().parse(in).getElementsByTagName("case");
            List<Case> cases = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Element e = (Element) nodes.item(i);
                LegacyLibrary library = e.getAttribute("library").equals("parallel") ? LegacyLibrary.PARALLEL : LegacyLibrary.COMBINATORICSLIB3;
                cases.add(new Case(e.getAttribute("op"), library, e, e.getTextContent(), e.hasAttribute("throws")));
            }
            return cases;
        }
    }

    /** The tuples of a sequence written like the fixtures: positions mapped to names, rows joined by ';'. */
    private static String render(RankedTuples tuples, String[] names) {
        StringBuilder out = new StringBuilder();
        try (TupleCursor cursor = tuples.cursor()) {
            while (cursor.hasNext()) {
                int[] tuple = cursor.next();
                // the fixture writer puts ';' only after text, so a leading empty tuple leaves no trace in the text;
                // the row count compared separately keeps that case exact
                if (!out.isEmpty()) {
                    out.append(';');
                }
                StringBuilder row = new StringBuilder();
                for (int p : tuple) {
                    row.append(row.isEmpty() ? "" : " ").append(names == null ? Integer.toString(p) : names[p]);
                }
                out.append(row);
            }
        }
        return out.toString();
    }

    private static int[] sizes(String text) {
        String[] parts = text.split(",");
        int[] sizes = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            sizes[i] = Integer.parseInt(parts[i]);
        }
        return sizes;
    }

    @Test
    void everyOperatorEmitsExactlyTheLegacyLibrarysTuplesInItsOrder() throws Exception {
        List<Case> cases = cases();
        assertEquals(232, cases.size());
        int compared = 0;
        int refused = 0;
        for (Case c : cases) {
            String[] names = null;
            RankedTuples tuples = switch (c.op()) {
                case "combinations" -> Operators.combinations(c.attribute("n"), c.attribute("k"), c.library());
                case "multi-combinations" -> Operators.combinationsWithRepetition(c.attribute("n"), c.attribute("k"), c.library());
                case "permutations" -> {
                    int n = c.attribute("n");
                    int[] classes = new int[n];
                    for (int i = 0; i < n; i++) {
                        classes[i] = i;
                    }
                    yield Operators.permutations(classes, c.library());
                }
                case "multiset-permutations" -> {
                    String elements = c.element().getAttribute("elements");
                    List<String> letters = new ArrayList<>();
                    names = new String[elements.length()];
                    for (int i = 0; i < elements.length(); i++) {
                        letters.add(String.valueOf(elements.charAt(i)));
                        names[i] = letters.get(i);
                    }
                    int[] classes = MultisetPermutations.classesOf(letters);
                    // the parallel library deduplicated always; combinatoricslib3 simple() only when an element repeats
                    yield c.library() == LegacyLibrary.PARALLEL ? Operators.distinctArrangements(classes)
                            : Operators.permutations(classes, c.library());
                }
                case "repetition-permutations" -> Operators.permutationsWithRepetition(c.attribute("n"), c.attribute("k"), c.library());
                case "subsets" -> Operators.subsets(c.attribute("n"));
                case "cartesian" -> Operators.cartesian(sizes(c.element().getAttribute("sizes")));
                default -> throw new AssertionError("unknown operator " + c.op());
            };
            String where = c.op() + " " + c.library() + " " + c.element().getAttributes().getNamedItem("n") + " "
                    + c.element().getAttribute("k") + c.element().getAttribute("elements") + c.element().getAttribute("sizes");
            if (c.threw()) {
                // the parallel CombinationGenerator threw for k > n; the legacy Core never asked (it returned no rows first)
                assertEquals("combinations PARALLEL", c.op() + " " + c.library(), where);
                assertEquals(BigInteger.ZERO, tuples.size(), where);
                refused++;
                continue;
            }
            assertEquals(c.expected(), render(tuples, names), where);
            assertEquals(new BigInteger(c.element().getAttribute("rows")), tuples.size(), where);
            compared++;
        }
        assertEquals(225, compared);
        assertEquals(7, refused);
    }

    @Test
    void theTwoLibrariesDifferWhereTheFixturesSayTheyDo() {
        // permutations: lexicographic versus adjacent swaps
        assertEquals("0 1 2;0 2 1;1 0 2;1 2 0;2 0 1;2 1 0", render(Operators.permutations(new int[]{0, 1, 2}, LegacyLibrary.PARALLEL), null));
        assertEquals("0 1 2;0 2 1;2 0 1;2 1 0;1 2 0;1 0 2",
                render(Operators.permutations(new int[]{0, 1, 2}, LegacyLibrary.COMBINATORICSLIB3), null));
        // permutations with repetition: the last versus the first place varying fastest
        assertEquals("0 0;0 1;1 0;1 1", render(Operators.permutationsWithRepetition(2, 2, LegacyLibrary.PARALLEL), null));
        assertEquals("0 0;1 0;0 1;1 1", render(Operators.permutationsWithRepetition(2, 2, LegacyLibrary.COMBINATORICSLIB3), null));
        // empty sources
        assertEquals(BigInteger.ONE, Operators.permutations(new int[0], LegacyLibrary.PARALLEL).size());
        assertEquals(BigInteger.ZERO, Operators.permutations(new int[0], LegacyLibrary.COMBINATORICSLIB3).size());
        assertEquals(BigInteger.ZERO, Operators.combinationsWithRepetition(0, 2, LegacyLibrary.PARALLEL).size());
        assertEquals(BigInteger.ONE, Operators.combinationsWithRepetition(0, 2, LegacyLibrary.COMBINATORICSLIB3).size());
        assertEquals(BigInteger.ONE, Operators.combinations(0, 0, LegacyLibrary.PARALLEL).size());
        assertEquals(BigInteger.ZERO, Operators.combinations(0, 0, LegacyLibrary.COMBINATORICSLIB3).size());
        // subsets of chosen sizes are the combinations of each size, smallest first
        assertEquals("0;1;2;0 1;0 2;1 2", render(Operators.subsetsOfSizes(3, new TreeSet<>(List.of(0, 1, 2)), LegacyLibrary.PARALLEL), null));
        assertTrue(render(Operators.subsetsOfSizes(3, new TreeSet<>(List.of(2, 0)), LegacyLibrary.PARALLEL), null).startsWith("0 1;0 2"));
    }
}
