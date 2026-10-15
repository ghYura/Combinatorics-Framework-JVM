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

import org.ghyura.cf.model.input.WorkbookData;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The 32 workbooks of the reference checkout, byte-identical copies listed in {@code corpus-manifest.xml},
 * and what an independent survey of them found. The survey was a throwaway program that opened each copy
 * straight through POI (from bytes, never from a path) and counted, with no framework code involved, the data
 * sheets (names not starting with FW_) and every cell of them, blank-typed cells included.
 */
final class LegacyCorpus {

    private static final String DIR = "/org/ghyura/cf/input/legacy/";

    record Entry(String file, String source, int bytes, String sha256) {
    }

    /** Per workbook: {data sheets S, data cells C} as counted by the independent survey. */
    static final Map<String, int[]> SURVEY = Map.ofEntries(
            Map.entry("01-Combinatorics-Framework-QA-edition-generator_trunk.xlsx", new int[]{202, 399}),
            Map.entry("02-01-d1-idempotency-spec.xlsx", new int[]{9, 22}),
            Map.entry("03-02-d2-stream-boundaries-spec.xlsx", new int[]{10, 23}),
            Map.entry("04-spec-catalogue-SCA3.xlsx", new int[]{4, 14}),
            Map.entry("05-spec-full.xlsx", new int[]{4, 10}),
            Map.entry("06-spec-A.xlsx", new int[]{6, 10}),
            Map.entry("07-spec-B-alt.xlsx", new int[]{4, 8}),
            Map.entry("08-spec-B.xlsx", new int[]{4, 8}),
            Map.entry("09-spec-controls.xlsx", new int[]{4, 11}),
            Map.entry("10-spec-main.xlsx", new int[]{9, 17}),
            Map.entry("11-phase-a-spec.xlsx", new int[]{5, 11}),
            Map.entry("12-spec-B1.xlsx", new int[]{15, 21}),
            Map.entry("13-spec-B2.xlsx", new int[]{19, 25}),
            Map.entry("14-spec-counter.xlsx", new int[]{8, 15}),
            Map.entry("15-spec-queue.xlsx", new int[]{7, 12}),
            Map.entry("16-07-d7-reduction-trees-spec.xlsx", new int[]{5, 23}),
            Map.entry("17-08-d8-ties-and-pagination-spec.xlsx", new int[]{5, 82}),
            Map.entry("18-09-d8-dependency-dags-spec.xlsx", new int[]{10, 21}),
            Map.entry("19-10-d9-robust-portfolios-spec.xlsx", new int[]{5, 16}),
            Map.entry("20-11-d10-diagnostic-sensors-spec.xlsx", new int[]{4, 22}),
            Map.entry("21-12-d11-coalition-value-spec.xlsx", new int[]{3, 8}),
            Map.entry("22-spec-classes.xlsx", new int[]{3, 132}),
            Map.entry("23-spec-words.xlsx", new int[]{8, 20}),
            Map.entry("24-14-d13-tool-call-order-spec.xlsx", new int[]{7, 17}),
            Map.entry("25-15-d13-composed-carriers-spec.xlsx", new int[]{16, 24}),
            Map.entry("26-16-d13-agent-message-order-spec.xlsx", new int[]{9, 18}),
            Map.entry("27-17-d13-session-memory-spec.xlsx", new int[]{12, 24}),
            Map.entry("28-18-d13-context-position-spec.xlsx", new int[]{5, 16}),
            Map.entry("29-19-d14-language-processors-spec.xlsx", new int[]{9, 19}),
            Map.entry("30-20-d14-sql-partitioning-spec.xlsx", new int[]{5, 15}),
            Map.entry("31-21-d14-solver-relations-spec.xlsx", new int[]{6, 14}),
            Map.entry("32-22-d15-fault-schedules-spec.xlsx", new int[]{7, 16}));

    private LegacyCorpus() {
    }

    static byte[] bytes(String name) throws IOException {
        try (InputStream in = LegacyCorpus.class.getResourceAsStream(DIR + name)) {
            if (in == null) {
                throw new IllegalStateException("missing corpus resource " + name);
            }
            return in.readAllBytes();
        }
    }

    static List<Entry> entries() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes("corpus-manifest.xml")));
        NodeList nodes = document.getElementsByTagName("workbook");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            entries.add(new Entry(e.getAttribute("file"), e.getAttribute("source"), Integer.parseInt(e.getAttribute("bytes")),
                    e.getAttribute("sha256")));
        }
        return entries;
    }

    static WorkbookData workbook(Entry entry) throws IOException {
        return new XlsxWorkbookReader().read(InputSource.of(entry.file(), bytes(entry.file()))).workbook();
    }
}
