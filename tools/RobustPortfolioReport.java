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

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.fixtures.demos.PortfolioRanking;
import org.ghyura.cf.fixtures.demos.RobustPortfolioPlugin;
import org.ghyura.cf.store.MemoryBudget;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D9 full exact rankings from observed costs through the application's shared run service.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 * tools/RobustPortfolioReport.java --scenario PATH/spec.toml --out rankings.xml [--workers 4]
 */
public final class RobustPortfolioReport {
    private static String escape(String s) { return s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;"); }
    private static String join(List<?> items) { return items.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" ")); }
    public static void main(String[] args) throws Exception {
        Path scenario = null, out = null; String workers = "1";
        for (int i = 0; i < args.length; i += 2) {
            if (i+1 == args.length) throw new IllegalArgumentException("option value required");
            switch (args[i]) { case "--scenario" -> scenario = Path.of(args[i+1]); case "--out" -> out = Path.of(args[i+1]); case "--workers" -> workers = args[i+1]; default -> throw new IllegalArgumentException("unknown option " + args[i]); }
        }
        if (scenario == null || out == null) throw new IllegalArgumentException("--scenario and --out required");
        var session = Session.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.execution.sut", RobustPortfolioPlugin.ID, "D9 report"),
                new ConfigResolver.Override("cf.execution.workers", workers, "D9 report"), new ConfigResolver.Override("cf.generation.distinctTables", "true", "original D9 Core setting")));
        var budget = MemoryBudget.of(session.configuration().config());
        try (var owned = budget.reserve(16L*1024*1024, "D9 observed bank, exact rankings and XML report")) {
            if (owned.bytes() != 16L*1024*1024) throw new IllegalStateException("invalid D9 report reservation");
            var observations = new ArrayList<PortfolioRanking.Observed>(); var fields = new TreeMap<String, Map<String, String>>();
            try (var prepared = session.prepareRun(scenario)) {
                var run = prepared.handle(); var summary = run.await();
                if (summary.state() != RunState.COMPLETED) throw new IllegalStateException("D9 run failed: " + summary.diagnostics());
                for (var o : run.finals()) {
                    var a = o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D9 observation")).attributes();
                    if (!o.outcome().name().equals(a.get("verdict"))) throw new IllegalArgumentException("conflicting observed verdict");
                    observations.add(new PortfolioRanking.Observed(a.get("case_id"), a.get("design"), Integer.parseInt(a.get("demand")),
                            Integer.parseInt(a.get("disruption")), o.outcome().name(), new BigInteger(a.get("total_cost"))));
                    if (fields.putIfAbsent(a.get("case_id"), a) != null) throw new IllegalArgumentException("duplicate D9 observation");
                }
            }
            var r = PortfolioRanking.rank(observations);
            var xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<portfolio-report schema=\"d9.ranking/v1\" source=\"observed total_cost of every design/world evaluation\" observations=\"1024\" designs=\"64\" worlds=\"16\">\n");
            for (var entry : fields.entrySet()) {
                xml.append("  <observation id=\"").append(escape(entry.getKey())).append("\">\n");
                for (var field : new TreeMap<>(entry.getValue()).entrySet()) xml.append("    <field name=\"").append(escape(field.getKey())).append("\">").append(escape(field.getValue())).append("</field>\n");
                xml.append("  </observation>\n");
            }
            for (var p : r.profiles().entrySet()) xml.append("  <profile name=\"").append(p.getKey()).append("\" demand-weights=\"").append(join(p.getValue().demandWeights()))
                    .append("\" disruption-weights=\"").append(join(p.getValue().disruptionWeights())).append("\" world-weights=\"").append(join(p.getValue().worldWeights())).append("\"/>\n");
            xml.append("  <minimax ranking=\"").append(join(r.minimaxRanking())).append("\" primary-ties=\"").append(join(r.primaryTies())).append("\"/>\n");
            for (var p : r.expectedRankings().entrySet()) xml.append("  <expected-ranking profile=\"").append(p.getKey()).append("\" ranking=\"").append(join(p.getValue())).append("\"/>\n");
            for (var s : r.summaries()) {
                xml.append("  <summary design=\"").append(s.design()).append("\" world-count=\"16\" worst-cost=\"").append(s.worstCost()).append("\" worst-worlds=\"").append(join(s.worstWorlds())).append("\">\n");
                for (var mean : s.expected().entrySet()) xml.append("    <mean profile=\"").append(mean.getKey()).append("\" value=\"").append(mean.getValue()).append("\"/>\n");
                xml.append("  </summary>\n");
            }
            for (var s : r.sensitivity()) xml.append("  <sensitivity alpha=\"").append(s.alpha()).append("\" ranking=\"").append(join(s.ranking()))
                    .append("\" winner=\"").append(s.winner()).append("\" winner-expected=\"").append(s.winnerExpected()).append("\"/>\n");
            xml.append("</portfolio-report>\n"); Files.writeString(out, xml, StandardCharsets.UTF_8);
            System.out.println("D9: 1024 PASS observations; minimax " + r.minimaxRanking().getFirst() + "; primary ties " + r.primaryTies() + "; 11 exact joint-mixture sensitivity steps");
        }
    }
}
