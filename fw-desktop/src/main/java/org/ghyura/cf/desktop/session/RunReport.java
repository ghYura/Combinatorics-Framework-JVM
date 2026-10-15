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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.campaign.Reconciliation;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.Xml;

import java.util.Locale;
import java.util.Map;

/** The run command's report of a {@link RunSummary}, as text and XML. */
public final class RunReport {

    private RunReport() {
    }

    public static String toText(RunSummary s) {
        StringBuilder text = new StringBuilder(1024);
        text.append("\nrun: ").append(s.run()).append('\n');
        line(text, "state", s.state() + " in " + s.elapsedMillis() + " ms");
        line(text, "sut", s.sut() + (s.sutVersion().isEmpty() ? "" : " " + s.sutVersion()));
        line(text, "candidates", "generated " + s.generated() + ", excluded " + s.excluded() + ", left out by covering selection "
                + s.unselected() + ", executable " + s.executable() + (s.countsExact() ? "" : " (counts not exact: the walk was cut)"));
        line(text, "stopped", s.stop().reason() + ": " + s.stop().detail() + (s.stop().boundary() >= 0
                && s.stop().reason() != org.ghyura.cf.campaign.StopPolicy.Reason.EXHAUSTED ? " (decided at candidate " + s.stop().boundary()
                + " in candidate order)" : ""));
        line(text, "executed", s.admitted() + " candidates admitted, " + s.samples() + " samples, " + s.attempts() + " attempts ("
                + s.retried() + " samples retried)");
        StringBuilder outcomes = new StringBuilder();
        for (Outcome o : Outcome.values()) {
            outcomes.append(outcomes.isEmpty() ? "" : ", ").append(o).append(' ').append(s.count(o));
        }
        line(text, "outcomes", outcomes.toString());
        if (!s.verdicts().isEmpty()) {
            StringBuilder verdicts = new StringBuilder();
            for (Map.Entry<Integer, Long> e : s.verdicts().entrySet()) {
                verdicts.append(verdicts.isEmpty() ? "" : "; ").append(e.getKey());
                String name = s.verdictNames().get(e.getKey());
                if (name != null) {
                    verdicts.append(' ').append(name);
                }
                verdicts.append(": ").append(e.getValue());
            }
            line(text, "verdicts", verdicts.toString());
        }
        if (s.compilations() + s.cacheHits() > 0) {
            line(text, "compiler", s.compilations() + " sources compiled in memory, " + s.cacheHits() + " served by the bytecode cache");
        }
        if (s.abandoned() > 0) {
            line(text, "abandoned", s.abandoned() + " samples did not stop when asked; their threads were left running, not stopped "
                    + "(still alive at the end: " + s.stillRunning() + ")");
        }
        RunSummary.Execution x = s.execution();
        if (!x.environments().equals(java.util.List.of("")) || !x.repeatScope().equals("all")) {
            line(text, "repeats", "environments " + x.environments().stream().map(e -> "'" + e + "'").toList() + ", policy "
                    + x.repeatPolicy() + ", scope " + x.repeatScope());
        }
        if (x.measurements() > 0) {
            StringBuilder measured = new StringBuilder();
            for (Outcome o : Outcome.values()) {
                measured.append(measured.isEmpty() ? "" : ", ").append(o).append(' ').append(x.measurementOutcomes().get(o));
            }
            line(text, "measured", x.measurements() + " measurement samples: " + measured);
        }
        if (x.attachmentsKept() + x.attachmentsOmitted() > 0) {
            line(text, "attached", x.attachmentsKept() + " candidate sources kept (" + x.attachmentBytes() + " bytes), "
                    + x.attachmentsOmitted() + " omitted by cf.results.maxAttachmentBytes");
        }
        if (x.listenerFailures() > 0) {
            line(text, "listener", x.listenerFailures() + " observations never taken by the listener (" + x.listenerRetries() + " retries)");
        }
        line(text, "resources", "memory budget high-water " + x.budgetHighWater() + " of " + x.budgetBytes() + " bytes; work queue peak "
                + x.peakQueuedBytes() + " bytes; window peak " + x.peakWindow() + " candidates" + (x.budgetExhausted()
                ? "; the run's time budget ended it" : ""));
        line(text, "identity", "scenario " + s.scenarioDigest() + ", plan " + s.planDigest() + ", policy " + s.policyDigest());
        Reconciliation r = s.reconciliation();
        line(text, "reconciled", (r.consistent() ? "every total adds up: " : "THE TOTALS DO NOT ADD UP: ") + r.count(Reconciliation.Status.HOLDS)
                + " checks hold, " + r.count(Reconciliation.Status.FAILS) + " fail, " + r.count(Reconciliation.Status.NOT_APPLICABLE)
                + " do not apply");
        for (Reconciliation.Check c : r.failures()) {
            line(text, "", (c.kind() == Reconciliation.Kind.CONSISTENCY ? "FAILS " : "notice ") + c.id() + ": " + c.rule() + "; expected "
                    + c.expected() + ", found " + c.actual());
        }
        s.seed().ifPresent(seed -> line(text, "seed", seed.winners().size() + " winners of " + seed.totalCandidatesObserved()
                + " samples, content " + seed.contentDigest()));
        s.analysis().ifPresent(a -> text.append(a.toText()));
        return text.toString();
    }

    public static void appendXml(StringBuilder xml, String indent, RunSummary s) {
        xml.append(indent).append("<run id=\"").append(Xml.escape(s.run().value())).append("\" state=\"").append(s.state())
                .append("\" sut=\"").append(Xml.escape(s.sut())).append("\" sutVersion=\"").append(Xml.escape(s.sutVersion()))
                .append("\" elapsedMillis=\"").append(s.elapsedMillis()).append("\" scenario=\"").append(Xml.escape(s.scenario()))
                .append("\" scenarioDigest=\"").append(s.scenarioDigest()).append("\" planDigest=\"").append(s.planDigest())
                .append("\" policyDigest=\"").append(s.policyDigest()).append("\">\n");
        xml.append(indent).append("  <candidates exact=\"").append(s.countsExact()).append("\" generated=\"").append(s.generated())
                .append("\" excluded=\"").append(s.excluded()).append("\" unselected=\"").append(s.unselected()).append("\" executable=\"")
                .append(s.executable()).append("\"/>\n");
        xml.append(indent).append("  <stop reason=\"").append(s.stop().reason()).append("\" boundary=\"").append(s.stop().boundary())
                .append("\" admitted=\"").append(s.stop().admitted()).append("\" detail=\"").append(Xml.escape(s.stop().detail())).append("\"/>\n");
        xml.append(indent).append("  <samples admitted=\"").append(s.admitted()).append("\" samples=\"").append(s.samples())
                .append("\" attempts=\"").append(s.attempts()).append("\" retried=\"").append(s.retried()).append("\"/>\n");
        xml.append(indent).append("  <outcomes>\n");
        for (Outcome o : Outcome.values()) {
            xml.append(indent).append("    <outcome name=\"").append(o).append("\" count=\"").append(s.count(o)).append("\"/>\n");
        }
        xml.append(indent).append("  </outcomes>\n");
        xml.append(indent).append("  <verdicts>\n");
        for (Map.Entry<Integer, Long> e : s.verdicts().entrySet()) {
            xml.append(indent).append("    <verdict code=\"").append(e.getKey()).append('"');
            String name = s.verdictNames().get(e.getKey());
            if (name != null) {
                xml.append(" name=\"").append(Xml.escape(name)).append('"');
            }
            xml.append(" count=\"").append(e.getValue()).append("\"/>\n");
        }
        xml.append(indent).append("  </verdicts>\n");
        xml.append(indent).append(String.format(Locale.ROOT, "  <compiler compiled=\"%d\" cacheHits=\"%d\"/>%n", s.compilations(), s.cacheHits()));
        xml.append(indent).append("  <threads abandoned=\"").append(s.abandoned()).append("\">\n");
        for (String name : s.stillRunning()) {
            xml.append(indent).append("    <still-running name=\"").append(Xml.escape(name)).append("\"/>\n");
        }
        xml.append(indent).append("  </threads>\n");
        RunSummary.Execution x = s.execution();
        xml.append(indent).append("  <execution repeatPolicy=\"").append(x.repeatPolicy()).append("\" repeatScope=\"").append(x.repeatScope())
                .append("\" measurements=\"").append(x.measurements()).append("\" deliveries=\"").append(x.deliveries())
                .append("\" listenerRetries=\"").append(x.listenerRetries()).append("\" listenerFailures=\"").append(x.listenerFailures())
                .append("\" peakQueuedBytes=\"").append(x.peakQueuedBytes()).append("\" peakWindow=\"").append(x.peakWindow())
                .append("\" budgetHighWater=\"").append(x.budgetHighWater()).append("\" budgetBytes=\"").append(x.budgetBytes())
                .append("\" budgetExhausted=\"").append(x.budgetExhausted()).append("\" retention=\"").append(x.retention())
                .append("\">\n");
        for (String env : x.environments()) {
            xml.append(indent).append("    <environment id=\"").append(Xml.escape(env)).append("\"/>\n");
        }
        if (x.measurements() > 0) {
            for (Outcome o : Outcome.values()) {
                xml.append(indent).append("    <measurement-outcome name=\"").append(o).append("\" count=\"")
                        .append(x.measurementOutcomes().get(o)).append("\"/>\n");
            }
        }
        xml.append(indent).append("    <attachments kept=\"").append(x.attachmentsKept()).append("\" bytes=\"").append(x.attachmentBytes())
                .append("\" omitted=\"").append(x.attachmentsOmitted()).append('"');
        if (x.retention().equals("aggregate-only")) xml.append(" omissionReason=\"aggregate-only retention\"");
        xml.append("/>\n");
        xml.append(indent).append("  </execution>\n");
        Reconciliation r = s.reconciliation();
        xml.append(indent).append("  <reconciliation consistent=\"").append(r.consistent()).append("\" holds=\"")
                .append(r.count(Reconciliation.Status.HOLDS)).append("\" fails=\"").append(r.count(Reconciliation.Status.FAILS))
                .append("\" notApplicable=\"").append(r.count(Reconciliation.Status.NOT_APPLICABLE)).append("\">\n");
        for (Reconciliation.Check c : r.checks()) {
            xml.append(indent).append("    <check id=\"").append(c.id()).append("\" legacy=\"").append(Xml.escape(c.legacy())).append("\" stage=\"")
                    .append(c.stage()).append("\" kind=\"").append(c.kind()).append("\" status=\"").append(c.status()).append("\" rule=\"")
                    .append(Xml.escape(c.rule())).append("\" expected=\"").append(Xml.escape(c.expected())).append("\" actual=\"")
                    .append(Xml.escape(c.actual())).append("\"/>\n");
        }
        for (RunSummary.Axis a : x.axes()) {
            xml.append(indent).append("    <axis sheet=\"").append(Xml.escape(a.sheet())).append("\" declared=\"").append(a.declared())
                    .append("\" exercised=\"").append(a.exercised()).append("\"");
            if (a.unexercised().isEmpty()) {
                xml.append("/>\n");
            } else {
                xml.append(">\n");
                for (String v : a.unexercised()) {
                    xml.append(indent).append("      <unexercised>").append(Xml.escape(v)).append("</unexercised>\n");
                }
                xml.append(indent).append("    </axis>\n");
            }
        }
        xml.append(indent).append("  </reconciliation>\n");
        s.analysis().ifPresent(a -> a.appendXml(xml, indent + "  "));
        s.seed().ifPresent(seed -> xml.append(indent).append("  <seed sourceRunId=\"").append(Xml.escape(seed.sourceRunId()))
                .append("\" winners=\"").append(seed.winners().size()).append("\" samples=\"").append(seed.totalCandidatesObserved())
                .append("\" contentDigest=\"").append(seed.contentDigest()).append("\"/>\n"));
        xml.append(indent).append("</run>\n");
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(String.format(Locale.ROOT, "  %-11s %s%n", label, value));
    }
}
