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

import org.ghyura.cf.desktop.session.RunReport;
import org.ghyura.cf.campaign.Campaign;
import org.ghyura.cf.model.Xml;

import java.math.BigInteger;
import java.util.Locale;

/** The text and XML of a campaign's lineage (legacy bundle.iterate/v1), with every iteration's run report. */
final class CampaignReport {

    private CampaignReport() {
    }

    static String toText(Campaign.Result r) {
        StringBuilder text = new StringBuilder(2048);
        text.append("\ncampaign: ").append(r.baseRunId()).append('\n');
        line(text, "status", r.status() + ": " + r.reason());
        line(text, "strategy", r.strategy() + ", " + r.iterations().size() + " of " + r.iterationsRequested() + " iterations, stop when "
                + "stable " + (r.stopWhenStable() == 0 ? "never" : "after " + r.stopWhenStable()) + ", exploration floor "
                + r.explorationFloor() + ", min winner support " + r.minWinnerSupport());
        if (!r.inputSeedDigest().isEmpty()) {
            line(text, "input seed", r.inputSeedDigest());
        }
        for (Campaign.Iteration it : r.iterations()) {
            line(text, "iteration " + it.index(), it.runId() + " " + it.state() + ": executable " + it.executable() + " of " + it.generated()
                    + ", front " + it.frontIds().size() + ", stable " + it.stableTransitions() + "; " + it.derivation());
        }
        for (Campaign.Iteration it : r.iterations()) {
            text.append(RunReport.toText(it.summary()));
        }
        return text.toString();
    }

    static void appendXml(StringBuilder xml, String indent, Campaign.Result r) {
        xml.append(indent).append("<campaign status=\"").append(r.status()).append("\" baseRunId=\"").append(Xml.escape(r.baseRunId()))
                .append("\" strategy=\"").append(r.strategy()).append("\" iterationsRequested=\"").append(r.iterationsRequested())
                .append("\" stopWhenStable=\"").append(r.stopWhenStable()).append(String.format(Locale.ROOT, "\" explorationFloor=\"%s",
                        r.explorationFloor())).append("\" minWinnerSupport=\"").append(r.minWinnerSupport()).append("\" baseScenarioDigest=\"")
                .append(r.baseScenarioDigest()).append("\" inputSeedDigest=\"").append(r.inputSeedDigest()).append("\">\n");
        xml.append(indent).append("  <reason>").append(Xml.escape(r.reason())).append("</reason>\n");
        for (Campaign.Iteration it : r.iterations()) {
            xml.append(indent).append("  <iteration index=\"").append(it.index()).append("\" runId=\"").append(Xml.escape(it.runId()))
                    .append("\" state=\"").append(it.state()).append("\" planStatus=\"").append(it.planStatus()).append("\" biasPlanDigest=\"")
                    .append(it.biasPlanDigest()).append("\" parentScenarioDigest=\"").append(it.parentScenarioDigest())
                    .append("\" parentPlanDigest=\"").append(it.parentPlanDigest()).append("\" scenarioDigest=\"").append(it.scenarioDigest())
                    .append("\" planDigest=\"").append(it.planDigest()).append("\" inputSeedDigest=\"").append(it.inputSeedDigest())
                    .append("\" seedDigest=\"").append(it.seedDigest()).append("\" preBiasEstimate=\"").append(text(it.preBiasEstimate()))
                    .append("\" postBiasEstimate=\"").append(text(it.postBiasEstimate())).append("\" generated=\"").append(it.generated())
                    .append("\" excluded=\"").append(it.excluded()).append("\" executable=\"").append(it.executable()).append("\" samples=\"")
                    .append(it.samples()).append("\" winners=\"").append(it.winners()).append("\" stableTransitions=\"")
                    .append(it.stableTransitions()).append("\">\n");
            xml.append(indent).append("    <derivation>").append(Xml.escape(it.derivation())).append("</derivation>\n");
            xml.append(indent).append("    <front>\n");
            for (String id : it.frontIds()) {
                xml.append(indent).append("      <candidate id=\"").append(Xml.escape(id)).append("\"/>\n");
            }
            xml.append(indent).append("    </front>\n");
            RunReport.appendXml(xml, indent + "    ", it.summary());
            xml.append(indent).append("  </iteration>\n");
        }
        xml.append(indent).append("</campaign>\n");
    }

    private static String text(BigInteger n) {
        return n == null ? "" : n.toString();
    }

    private static void line(StringBuilder text, String label, String value) {
        text.append(String.format(Locale.ROOT, "  %-11s %s%n", label, value));
    }
}
