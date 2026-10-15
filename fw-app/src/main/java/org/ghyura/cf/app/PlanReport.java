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

import org.ghyura.cf.desktop.session.ScenarioValidation;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.SheetOutcome;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.model.row.Row;
import org.ghyura.cf.model.row.RowCursor;
import org.ghyura.cf.model.row.RowSource;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What {@code plan} learned about every sheet's stages (work package M02): the directives, the exact number of rows
 * of the final table, whether the table is computed from its inputs or stored, what it reserved, its first rows, who
 * consumed it, and the memory the generation left reserved. Built while the generation's tables are open; it holds
 * plain values only.
 */
record PlanReport(GenerationOptions options, List<SheetReport> sheets, long budgetBytes, long dictionaryBytes, long generationBytes,
                  long highWaterBytes, List<Diagnostic> diagnostics) {

    /** How many rows of each table the report shows. */
    static final int SAMPLE_ROWS = 5;

    PlanReport {
        sheets = List.copyOf(sheets);
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * @param formula how the exact size follows from the table's inputs
     * @param lengths rows by number of keys, from the operators' parameters or counted while stored; null when unknown
     *                without reading the rows
     */
    record SheetReport(SheetPlan sheet, BigInteger rows, boolean stored, long reservedBytes, int uniformLength, String consumedBy,
                       List<String> first, String formula, SortedMap<Integer, BigInteger> lengths, String lengthsNote) {
        SheetReport {
            first = List.copyOf(first);
            lengths = lengths == null ? null : Collections.unmodifiableSortedMap(new TreeMap<>(lengths));
        }

        /** {@code 2:6, 3:4}, or a note when the lengths are not known without reading the rows. */
        String describeLengths() {
            if (lengths == null) {
                return lengthsNote;
            }
            if (lengths.isEmpty()) {
                return "none";
            }
            StringBuilder text = new StringBuilder();
            lengths.forEach((keys, count) -> text.append(text.isEmpty() ? "" : ", ").append(keys).append(':').append(count));
            return text.toString();
        }
    }

    static PlanReport of(ScenarioValidation.Summary summary, GenerationOptions options, Generation.Result result,
                         KeyDictionary dictionary, MemoryBudget budget) {
        List<SheetReport> sheets = new ArrayList<>();
        for (SheetOutcome outcome : result.result().sheets()) {
            RowSource rows = outcome.rows();
            List<String> first = new ArrayList<>();
            BigInteger shown = rows.size().min(BigInteger.valueOf(SAMPLE_ROWS));
            try (RowCursor cursor = rows.open(BigInteger.ZERO, shown, Cancellation.NONE)) {
                while (cursor.hasNext()) {
                    Row row = cursor.next();
                    first.add(row.keys().describe());
                }
            }
            SheetPlan sheet = summary.plan().sheet(outcome.sheet()).orElseThrow();
            SortedMap<Integer, BigInteger> lengths = null;
            String lengthsNote = "not available from operator metadata within planning limits";
            try {
                lengths = rows.lengths();
            } catch (FrameworkException e) {
                if (e.diagnostics().stream().anyMatch(d -> d.code() != ErrorCode.RESOURCE_LIMIT)) throw e;
                lengthsNote = e.getMessage();
            }
            sheets.add(new SheetReport(sheet, rows.size(), rows.stored(), rows.reservedBytes(), rows.uniformLength(),
                    outcome.consumedBy(), first, rows.formula(), lengths, lengthsNote));
        }
        long dictionaryBytes = dictionary.reservedBytes();
        return new PlanReport(options, sheets, budget.totalBytes(), dictionaryBytes, result.reservedBytesAtEnd() - dictionaryBytes,
                budget.highWaterBytes(), result.result().diagnostics());
    }

    String toText() {
        StringBuilder text = new StringBuilder(2048);
        text.append("\nplan: every sheet's stages (cf.generation.library ").append(library()).append(", cf.generation.distinctTables ")
                .append(options.distinctTables()).append(")\n");
        for (SheetReport s : sheets) {
            text.append(String.format(Locale.ROOT, "  %-13s key %d, %s: %s%n", s.sheet().name(), s.sheet().key(), s.sheet().role(),
                    String.join(", ", s.sheet().directives().stream().map(Directive::normalized).toList())));
            text.append("                ").append(s.rows()).append(s.rows().equals(BigInteger.ONE) ? " row, " : " rows, ")
                    .append(s.stored() ? "stored (" + s.reservedBytes() + " bytes reserved)" : "computed (no rows held"
                            + (s.reservedBytes() > 0 ? ", " + s.reservedBytes() + " bytes of index reserved)" : ")"));
            if (s.consumedBy() != null) {
                text.append(", consumed by ").append(s.consumedBy());
            }
            text.append('\n');
            text.append("                EXACT: ").append(s.formula()).append("; rows by keys ").append(s.describeLengths()).append('\n');
            if (!s.first().isEmpty()) {
                text.append("                first: ").append(String.join(" ", s.first()))
                        .append(s.rows().compareTo(BigInteger.valueOf(s.first().size())) > 0 ? " ..." : "").append('\n');
            }
        }
        text.append(String.format(Locale.ROOT, "  %-13s budget %d bytes; key dictionary %d; generation %d, kept until the tables are released; "
                + "high water %d%n", "memory", budgetBytes, dictionaryBytes, generationBytes, highWaterBytes));
        return text.toString();
    }

    void appendXml(StringBuilder xml, String indent) {
        xml.append(indent).append("<generation library=\"").append(library()).append("\" distinctTables=\"").append(options.distinctTables())
                .append("\" budgetBytes=\"").append(budgetBytes).append("\" dictionaryBytes=\"").append(dictionaryBytes)
                .append("\" generationBytes=\"").append(generationBytes).append("\" highWaterBytes=\"").append(highWaterBytes).append("\">\n");
        for (SheetReport s : sheets) {
            xml.append(indent).append("  <table sheet=\"").append(Xml.escape(s.sheet().name())).append("\" key=\"").append(s.sheet().key())
                    .append("\" role=\"").append(s.sheet().role()).append("\" rows=\"").append(s.rows()).append("\" held=\"")
                    .append(s.stored() ? "stored" : "computed").append("\" reservedBytes=\"").append(s.reservedBytes())
                    .append("\" uniformLength=\"").append(s.uniformLength()).append("\" mode=\"EXACT\" formula=\"")
                    .append(Xml.escape(s.formula())).append('"');
            if (s.consumedBy() != null) {
                xml.append(" consumedBy=\"").append(Xml.escape(s.consumedBy())).append('"');
            }
            xml.append(">\n");
            for (String row : s.first()) {
                xml.append(indent).append("    <row>").append(Xml.escape(row)).append("</row>\n");
            }
            if (s.lengths() != null) {
                xml.append(indent).append("    <lengths>\n");
                s.lengths().forEach((keys, count) -> xml.append(indent).append("      <length keys=\"").append(keys).append("\" rows=\"")
                        .append(count).append("\"/>\n"));
                xml.append(indent).append("    </lengths>\n");
            } else {
                xml.append(indent).append("    <lengths mode=\"UNKNOWN\" reason=\"").append(Xml.escape(s.lengthsNote()))
                        .append("\"/>\n");
            }
            xml.append(indent).append("  </table>\n");
        }
        xml.append(indent).append("</generation>\n");
    }

    private String library() {
        return switch (options.library()) {
            case PARALLEL -> "PARALLEL";
            case COMBINATORICSLIB3 -> "COMBINATORICSLIB3";
        };
    }
}
