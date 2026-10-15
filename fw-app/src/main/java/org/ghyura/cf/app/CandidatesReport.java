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

import org.ghyura.cf.constraints.Coverage;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyCounts;
import org.ghyura.cf.generation.Candidate;
import org.ghyura.cf.generation.CandidateRenderer;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.spi.Cancellation;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * What {@code candidates} reports (work package M03): the columns, the mandatory product and optional sizes, the counts
 * kept apart (generated, removed by the early and the late rules, left out by covering selection, executable), the
 * impact of each rule, the covering selection, and the first candidates with their ordinal, legacy display id,
 * identity, states and source. Built while the assembly is open; it holds plain values only.
 */
record CandidatesReport(List<String> columns, List<Boolean> optional, BigInteger mandatoryRows, BigInteger mandatoryUncut,
                        BigInteger perMandatoryRow, boolean absent, List<long[]> sizes, AssemblyCounts counts, List<String> ruleNames,
                        List<Boolean> ruleEarly, Coverage.Selection coverage, List<Shown> shown) {

    record Shown(BigInteger ordinal, String display, String id, List<SelectionState> states, String source) {
    }

    CandidatesReport {
        columns = List.copyOf(columns);
        optional = List.copyOf(optional);
        sizes = List.copyOf(sizes);
        ruleNames = List.copyOf(ruleNames);
        ruleEarly = List.copyOf(ruleEarly);
        shown = List.copyOf(shown);
    }

    static CandidatesReport of(Assembly assembly, CandidateRenderer renderer, long limit, BigInteger scan, Cancellation cancellation) {
        List<Boolean> optional = new ArrayList<>();
        for (int c = 0; c < assembly.columnSheets().size(); c++) {
            optional.add(assembly.optionalColumn(c));
        }
        List<String> names = new ArrayList<>();
        List<Boolean> early = new ArrayList<>();
        for (int i = 0; i < assembly.rules().size(); i++) {
            names.add(assembly.rules().name(i));
            early.add(assembly.rules().mandatoryOnly(i));
        }
        AssemblyCounts counts = assembly.count(cancellation, scan);
        List<Shown> shown = new ArrayList<>();
        Iterator<Candidate> it = assembly.candidates(cancellation);
        while (shown.size() < limit && it.hasNext()) {
            Candidate c = it.next();
            shown.add(new Shown(c.ordinal(), c.display(), c.id().hex(), c.states(), renderer.renderText(c)));
        }
        return new CandidatesReport(assembly.columnSheets(), optional, assembly.mandatoryRows(), assembly.mandatoryRowsUncut(),
                assembly.perMandatoryRow(), assembly.absentIncluded(), assembly.optionalSizes(), counts, names, early, assembly.coverage(), shown);
    }

    private static String count(BigInteger value) {
        return value == null ? "unknown (more than --scan candidates to read)" : value.toString();
    }

    String toText() {
        StringBuilder text = new StringBuilder(2048);
        text.append("\ncandidates:\n");
        List<String> cols = new ArrayList<>();
        for (int c = 0; c < columns.size(); c++) {
            cols.add(columns.get(c) + (optional.get(c) ? " (optional)" : ""));
        }
        line(text, "columns", cols.isEmpty() ? "none (one structural candidate)" : String.join(", ", cols));
        line(text, "mandatory", mandatoryRows + " rows" + (mandatoryRows.equals(mandatoryUncut) ? "" : " (cut from " + mandatoryUncut + ")"));
        StringBuilder per = new StringBuilder(perMandatoryRow.toString()).append(" per mandatory row: ")
                .append(absent ? "absent branch" : "no absent branch");
        for (long[] s : sizes) {
            per.append(", size ").append(s[0]).append(": ").append(s[1] < 0 ? "more than 2^63" : Long.toString(s[1]));
        }
        line(text, "optional", per.toString());
        line(text, "generated", counts.generated().toString());
        line(text, "rules", ruleNames.size() + " (" + ruleEarly.stream().filter(b -> b).count() + " read only mandatory sheets)");
        if (counts.exact()) {
            line(text, "removed", "early " + counts.removedEarly() + ", late " + counts.removedLate() + " (overlap " + counts.overlap() + ")");
        } else {
            line(text, "removed", count(null));
        }
        if (coverage != null) {
            line(text, "coverage", "t=" + coverage.strength() + " " + coverage.method().name().toLowerCase(Locale.ROOT).replace('_', '-')
                    + " (greedy, not a proven minimum): kept " + coverage.kept().length + " mandatory rows; " + coverage.universe()
                    + " tuples, " + coverage.feasible() + " feasible and covered, " + coverage.infeasible() + " infeasible");
            line(text, "unselected", count(counts.unselected()));
        }
        line(text, "executable", count(counts.executable()));
        for (int i = 0; i < ruleNames.size(); i++) {
            line(text, i == 0 ? "per rule" : "", ruleNames.get(i) + (ruleEarly.get(i) ? " (early)" : " (late)") + ": removed "
                    + count(counts.exact() ? counts.perRule()[i] : null));
        }
        if (!shown.isEmpty()) {
            text.append("  first ").append(shown.size()).append(":\n");
            for (Shown s : shown) {
                text.append(String.format(Locale.ROOT, "    #%s  %s  %s  %s\n", s.ordinal(), s.display(), s.id().substring(0, 12),
                        Canonical.escape(s.source())));
            }
        }
        return text.toString();
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(String.format(Locale.ROOT, "  %-13s %s\n", label, value));
    }

    void appendXml(StringBuilder xml, String indent) {
        xml.append(indent).append("<candidates mandatoryRows=\"").append(mandatoryRows).append("\" mandatoryUncut=\"").append(mandatoryUncut)
                .append("\" perMandatoryRow=\"").append(perMandatoryRow).append("\" absentBranch=\"").append(absent)
                .append("\" generated=\"").append(counts.generated()).append("\" exact=\"").append(counts.exact()).append('"');
        if (counts.exact()) {
            xml.append(" removedEarly=\"").append(counts.removedEarly()).append("\" removedLate=\"").append(counts.removedLate())
                    .append("\" unselected=\"").append(counts.unselected()).append("\" executable=\"").append(counts.executable())
                    .append("\" overlap=\"").append(counts.overlap()).append('"');
        }
        xml.append(">\n");
        for (int c = 0; c < columns.size(); c++) {
            xml.append(indent).append("  <column sheet=\"").append(Xml.escape(columns.get(c))).append("\" optional=\"").append(optional.get(c))
                    .append("\"/>\n");
        }
        for (long[] s : sizes) {
            xml.append(indent).append("  <optional-size size=\"").append(s[0]).append("\" rows=\"").append(s[1]).append("\"/>\n");
        }
        for (int i = 0; i < ruleNames.size(); i++) {
            xml.append(indent).append("  <rule name=\"").append(Xml.escape(ruleNames.get(i))).append("\" phase=\"")
                    .append(ruleEarly.get(i) ? "early" : "late").append('"');
            if (counts.exact()) {
                xml.append(" removed=\"").append(counts.perRule()[i]).append('"');
            }
            xml.append("/>\n");
        }
        if (coverage != null) {
            xml.append(indent).append("  <coverage strength=\"").append(coverage.strength()).append("\" method=\"")
                    .append(coverage.method().name().toLowerCase(Locale.ROOT).replace('_', '-')).append("\" minimum=\"not-claimed\" kept=\"")
                    .append(coverage.kept().length).append("\" universe=\"").append(coverage.universe()).append("\" feasible=\"")
                    .append(coverage.feasible()).append("\" covered=\"").append(coverage.covered()).append("\" infeasible=\"")
                    .append(coverage.infeasible()).append("\"/>\n");
        }
        for (Shown s : shown) {
            xml.append(indent).append("  <candidate ordinal=\"").append(s.ordinal()).append("\" display=\"").append(Xml.escape(s.display()))
                    .append("\" id=\"").append(s.id()).append("\" states=\"");
            for (int c = 0; c < s.states().size(); c++) {
                xml.append(c == 0 ? "" : " ").append(s.states().get(c));
            }
            xml.append("\"><source>").append(Xml.escape(s.source())).append("</source></candidate>\n");
        }
        xml.append(indent).append("</candidates>\n");
    }
}
