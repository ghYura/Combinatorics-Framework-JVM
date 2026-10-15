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

import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A03 on real inputs: the 32 workbooks of the reference checkout (byte-identical copies, hashed in
 * {@code corpus-manifest.xml}). They were written by the author's generator and are independent of everything
 * in this project.
 */
class LegacyWorkbookCorpusTest {

    private static final String DIR = "/org/ghyura/cf/input/legacy/";
    private static final Pattern YAML_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TOML_KEY = Pattern.compile("[A-Za-z0-9_-]+");
    private static final Set<String> LEGACY_CONTROL_SHEETS = Set.of("FW_Arguments", "FW_CUSTOM_VAR", "FW_Info",
            "FW_RunMeFirstOnce", "FW_Seq", "FW_SheetNames");

    private record Entry(String file, String source, int bytes, String sha256) {
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = LegacyWorkbookCorpusTest.class.getResourceAsStream(DIR + name)) {
            if (in == null) {
                throw new IllegalStateException("missing corpus resource " + name);
            }
            return in.readAllBytes();
        }
    }

    private static List<Entry> manifest() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(resource("corpus-manifest.xml")));
        NodeList nodes = document.getElementsByTagName("workbook");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            entries.add(new Entry(e.getAttribute("file"), e.getAttribute("source"), Integer.parseInt(e.getAttribute("bytes")),
                    e.getAttribute("sha256")));
        }
        return entries;
    }

    private static WorkbookData read(Entry entry) throws IOException {
        return new XlsxWorkbookReader().read(InputSource.of(entry.file(), resource(entry.file()))).workbook();
    }

    private static String sha256(byte[] data) throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(data)) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }

    @Test
    void theCopiedWorkbooksAreExactlyTheOnesInTheManifest() throws Exception {
        List<Entry> entries = manifest();
        assertEquals(32, entries.size());
        for (Entry entry : entries) {
            byte[] data = resource(entry.file());
            assertEquals(entry.bytes(), data.length, entry.file());
            assertEquals(entry.sha256(), sha256(data), entry.file());
        }
    }

    /**
     * The totals were counted once, outside the code under test, by iterating every sheet, row and cell of the
     * original files directly with POI (all rows, cells and cell types, control sheets included).
     */
    @Test
    void theReaderSeesExactlyTheSheetsRowsAndCellsAnIndependentPoiSurveyCounted() throws Exception {
        int sheets = 0;
        int controlSheets = 0;
        int rows = 0;
        int emptyRows = 0;
        int cells = 0;
        int longest = 0;
        Map<CellKind, Integer> kinds = new EnumMap<>(CellKind.class);
        for (Entry entry : manifest()) {
            WorkbookData workbook = read(entry);
            for (InputSheet sheet : workbook.sheets()) {
                sheets++;
                controlSheets += sheet.isControl() ? 1 : 0;
                for (InputRow row : sheet.rows()) {
                    rows++;
                    emptyRows += row.isEmpty() ? 1 : 0;
                    for (InputCell cell : row.cells()) {
                        cells++;
                        longest = Math.max(longest, cell.text().length());
                        kinds.merge(cell.kind(), 1, Integer::sum);
                    }
                }
            }
        }
        assertEquals(621, sheets);
        assertEquals(192, controlSheets);
        assertEquals(2680, rows);
        assertEquals(0, emptyRows);
        assertEquals(7003, cells);
        assertEquals(20932, longest);
        assertEquals(5774, kinds.get(CellKind.TEXT));
        assertEquals(658, kinds.get(CellKind.NUMBER));
        assertEquals(571, kinds.get(CellKind.BLANK));
        assertEquals(3, kinds.size(), "the corpus has no formula, boolean or error cells: " + kinds);
    }

    @Test
    void everyRealWorkbookHasTheSixLegacyControlSheetsAndNothingElseControlLike() throws Exception {
        for (Entry entry : manifest()) {
            WorkbookData workbook = read(entry);
            Set<String> control = new java.util.TreeSet<>();
            for (InputSheet sheet : workbook.sheets()) {
                if (sheet.isControl()) {
                    control.add(sheet.name());
                }
            }
            assertEquals(new java.util.TreeSet<>(LEGACY_CONTROL_SHEETS), control, entry.file());
            assertTrue(workbook.sheet("FW_Seq").orElseThrow().rows().size() >= 1, entry.file());
        }
    }

    @Test
    void readingARealWorkbookIsDeterministic() throws Exception {
        for (Entry entry : manifest()) {
            assertEquals(read(entry), read(entry), entry.file());
            assertEquals(read(entry).contentDigest(), read(entry).contentDigest(), entry.file());
        }
    }

    @Test
    void everyRealWorkbookRoundTripsThroughEveryFormatItsNamesAllow() throws Exception {
        int xlsx = 0;
        int json = 0;
        int yaml = 0;
        int toml = 0;
        int skippedYaml = 0;
        int skippedToml = 0;
        for (Entry entry : manifest()) {
            WorkbookData original = read(entry);
            for (InputFormat format : InputFormat.values()) {
                boolean namesAllow = switch (format) {
                    case YAML -> original.sheetNames().stream().allMatch(n -> YAML_NAME.matcher(n).matches());
                    case TOML -> original.sheetNames().stream().allMatch(n -> TOML_KEY.matcher(n).matches());
                    default -> true;
                };
                if (!namesAllow) {
                    if (format == InputFormat.YAML) {
                        skippedYaml++;
                    } else {
                        skippedToml++;
                    }
                    continue;
                }
                byte[] written = WorkbookFormats.writer(format).write(original);
                WorkbookData back = WorkbookFormats.reader(format).read(InputSource.of("rt." + format.token(), written)).workbook();
                assertEquals(original.canonicalText(), back.canonicalText(), entry.file() + " via " + format.token());
                assertEquals(original.contentDigest(), back.contentDigest(), entry.file() + " via " + format.token());
                switch (format) {
                    case XLSX -> xlsx++;
                    case JSON -> json++;
                    case YAML -> yaml++;
                    case TOML -> toml++;
                }
            }
        }
        assertEquals(32, xlsx);
        assertEquals(32, json);
        assertEquals(32, yaml + skippedYaml);
        assertEquals(32, toml + skippedToml);
        assertTrue(yaml >= 16 && toml >= 16, "too few real workbooks exercised YAML/TOML: yaml=" + yaml + " toml=" + toml);
    }

    @Test
    void theRealCorpusAgreesAcrossFormatsPairwiseNotJustWithItself() throws Exception {
        for (Entry entry : manifest()) {
            WorkbookData original = read(entry);
            byte[] json = TextWorkbookWriters.json().write(original);
            byte[] xlsx = new XlsxWorkbookWriter().write(original);
            WorkbookData viaJson = new JsonWorkbookReader().read(InputSource.of("a.json", json)).workbook();
            WorkbookData viaXlsx = new XlsxWorkbookReader().read(InputSource.of("a.xlsx", xlsx)).workbook();
            assertEquals(viaJson.canonicalText(), viaXlsx.canonicalText(), entry.file());
        }
    }
}
