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

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * A second, independent reading of the real workbooks: POI is used directly, from bytes, with none of the
 * framework's reader, interpreter or planner involved. It counts what the pipeline must also find and derives,
 * with its own short loops, the facts the legacy parser derived from FW_Seq. The corpus tests compare the two.
 *
 * <p>The survey only knows the shapes that really occur in these workbooks: a data sheet holds plain cells and
 * {@code FW_EMPTY_STRING}, every FW_Seq row starts with the name of a data sheet, and no row names two sheets.
 * If a workbook ever breaks one of those, the survey stops with an exception instead of guessing.</p>
 */
final class CorpusSurvey {

    private static final String DIR = "/org/ghyura/cf/input/legacy/";
    private static final List<String> FLAGS = List.of("FW_Exclude", "FW_Heading", "FW_LastInQueue", "FW_Reuse", "FW_ReuseTableOnly",
            "FW_Optional");

    record Entry(String file, String sha256) {
    }

    /** One data sheet's FW_Seq-derived state, formulated here independently of the planner. */
    static final class SheetFacts {
        String role;
        boolean reuse;
        boolean reuseTableOnly;
        boolean hasDirectives;
        int directivesRow = -1;
        boolean promoted;
    }

    static final class Book {
        final String file;
        /** Data sheet name to the texts of its cells in reading order; FW_EMPTY_STRING counts as the empty text. */
        final Map<String, List<String>> values = new LinkedHashMap<>();
        int seqRows;
        int seqCells;
        final Map<String, Integer> flags = new TreeMap<>();
        final Map<String, Integer> shapes = new TreeMap<>();
        int nestedPlaceholders;
        int overwritten;
        final Map<String, SheetFacts> sheets = new LinkedHashMap<>();

        Book(String file) {
            this.file = file;
        }

        int dataSheets() {
            return values.size();
        }

        int dataCells() {
            return values.values().stream().mapToInt(List::size).sum();
        }

        long count(String role) {
            return sheets.values().stream().filter(s -> s.role.equals(role)).count();
        }

        long countWhere(java.util.function.Predicate<SheetFacts> test) {
            return sheets.values().stream().filter(test).count();
        }
    }

    private CorpusSurvey() {
    }

    static byte[] bytes(String name) throws IOException {
        try (InputStream in = CorpusSurvey.class.getResourceAsStream(DIR + name)) {
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
        Document manifest = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes("corpus-manifest.xml")));
        NodeList nodes = manifest.getElementsByTagName("workbook");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            entries.add(new Entry(e.getAttribute("file"), e.getAttribute("sha256")));
        }
        return entries;
    }

    static Book survey(String file) throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Book book = new Book(file);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes(file)))) {
            for (Sheet sheet : workbook) {
                if (sheet.getSheetName().startsWith("FW_")) {
                    continue;
                }
                List<String> texts = new ArrayList<>();
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        String text = formatter.formatCellValue(cell);
                        if (cell.getCellType() == CellType.FORMULA) {
                            text = cell.getCellFormula();
                        } else if (text.equals("FW_EMPTY_STRING")) {
                            text = "";
                        } else if (text.startsWith("FW_")) {
                            throw new IllegalStateException(file + ": a data cell directive the survey does not know: " + text);
                        }
                        texts.add(text);
                    }
                }
                book.values.put(sheet.getSheetName(), texts);
            }
            for (Map.Entry<String, List<String>> data : book.values.entrySet()) {
                SheetFacts facts = new SheetFacts();
                facts.role = data.getValue().isEmpty() ? "NONE" : "MANDATORY";
                book.sheets.put(data.getKey(), facts);
            }
            Sheet seq = workbook.getSheet("FW_Seq");
            List<String> joinerTargets = new ArrayList<>();
            int rowNumber = 0;
            for (Row row : seq) {
                List<String> cells = new ArrayList<>();
                for (Cell cell : row) {
                    String text = formatter.formatCellValue(cell);
                    if (!text.isBlank()) {
                        cells.add(text);
                    }
                }
                book.seqRows++;
                book.seqCells += cells.size();
                String target = cells.getFirst();
                SheetFacts facts = book.sheets.get(target);
                if (facts == null) {
                    throw new IllegalStateException(file + " row " + (rowNumber + 1) + ": starts with '" + target + "', not a data sheet");
                }
                List<String> directives = new ArrayList<>();
                for (String text : cells.subList(1, cells.size())) {
                    if (book.sheets.containsKey(text)) {
                        throw new IllegalStateException(file + " row " + (rowNumber + 1) + ": names two sheets");
                    }
                    if (FLAGS.contains(text)) {
                        book.flags.merge(text, 1, Integer::sum);
                        switch (text) {
                            case "FW_Exclude", "FW_Heading" -> facts.role = facts.role.equals("MANDATORY") ? "EXCLUDED" : facts.role;
                            case "FW_Optional" -> facts.role = facts.role.equals("MANDATORY") ? "OPTIONAL" : facts.role;
                            case "FW_Reuse" -> facts.reuse = true;
                            case "FW_ReuseTableOnly" -> facts.reuseTableOnly = true;
                            default -> {
                                // FW_LastInQueue does not occur in these workbooks and is not part of the comparison
                            }
                        }
                    } else if (text.startsWith("FW_Concatenator")) {
                        throw new IllegalStateException(file + ": FW_Concatenator does not occur in these workbooks");
                    } else if (text.startsWith("FW_")) {
                        directives.add(text);
                        book.shapes.merge(shape(file, text), 1, Integer::sum);
                        if (text.startsWith("FW_(")) {
                            joiner(book, text, target, joinerTargets);
                        }
                    } else {
                        throw new IllegalStateException(file + " row " + (rowNumber + 1) + ": stray cell '" + text + "'");
                    }
                }
                if (!directives.isEmpty()) {
                    if (facts.hasDirectives && facts.directivesRow != rowNumber) {
                        book.overwritten++;
                    }
                    facts.hasDirectives = true;
                    facts.directivesRow = rowNumber;
                    facts.promoted = promotes(directives);
                }
                rowNumber++;
            }
        }
        return book;
    }

    private static String shape(String file, String text) {
        if (text.startsWith("FW_(")) {
            return "joiner";
        }
        for (String name : List.of("FW_CombiR", "FW_Combi", "FW_PermutR", "FW_Permut", "FW_Subsets", "FW_Cartes", "FW_Group", "FW_Separator")) {
            if (text.startsWith(name)) {
                return name.substring(3);
            }
        }
        throw new IllegalStateException(file + ": a directive the survey does not know: " + text);
    }

    /** Counts nested placeholders and marks the joiner targets they name, newest first, as the legacy rule did. */
    private static void joiner(Book book, String text, String target, List<String> joinerTargets) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        int start = 0;
        String inner = text.substring(4, text.length() - 1);
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                parts.add(inner.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(inner.substring(start).trim());
        int index = joinerTargets.size() - 1;
        for (int position : new int[]{2, 4}) {
            String operand = parts.get(position);
            if (operand.startsWith("FW_(")) {
                book.nestedPlaceholders++;
                if (index >= 0) {
                    SheetFacts named = book.sheets.get(joinerTargets.get(index--));
                    named.reuse = true;
                    named.reuseTableOnly = true;
                }
            }
        }
        joinerTargets.add(target);
    }

    /** The legacy rule: no joiner and exactly one verb of the combination family, judged by the cell text. */
    private static boolean promotes(List<String> directives) {
        int verbs = 0;
        for (String text : directives) {
            if (text.startsWith("FW_(")) {
                return false;
            }
            if (text.startsWith("FW_Combi(") || text.startsWith("FW_CombiR(") || text.startsWith("FW_Permut(")
                    || text.startsWith("FW_PermutR(") || text.equals("FW_Subsets") || text.startsWith("FW_Subsets(")) {
                verbs++;
            }
        }
        return verbs == 1;
    }
}
