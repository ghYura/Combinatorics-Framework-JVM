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
import org.ghyura.cf.fixtures.demos.ToolCallAnalysis;
import org.ghyura.cf.fixtures.demos.ToolCallOrderPlugin;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.store.MemoryBudget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Full original D13a campaign and offline observed analysis through the shared run service.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 * tools/ToolCallOrderReport.java --scenario PATH/spec.toml --out tool-order.xml [--workers 4]
 */
public final class ToolCallOrderReport {
    // Quotes are legal in element text; avoid redundant JSON quote entities under default JDK limits.
    private static String elementText(String value) { return Xml.escape(value).replace("&quot;", "\"").replace("&apos;", "'"); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object value) { return (Map<String,Object>)value; }
    public static void main(String[] args) throws Exception {
        Path scenario=null,out=null; String workers="1";
        for (int i=0;i<args.length;i+=2) { if (i+1==args.length) throw new IllegalArgumentException("option value required"); switch (args[i]) { case "--scenario" -> scenario=Path.of(args[i+1]); case "--out" -> out=Path.of(args[i+1]); case "--workers" -> workers=args[i+1]; default -> throw new IllegalArgumentException("unknown option "+args[i]); } }
        if (scenario==null||out==null) throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",ToolCallOrderPlugin.ID,"D13a report"),new ConfigResolver.Override("cf.execution.workers",workers,"D13a report")));
        var budget=MemoryBudget.of(session.configuration().config());
        try (var owned=budget.reserve(16L*1024*1024,"D13a complete observed trace bank, axis/violation/prefix analysis and XML")) {
            if (owned.bytes()!=16L*1024*1024) throw new IllegalStateException("invalid D13a report reservation");
            var rows=new TreeMap<String,Map<String,String>>();
            try (var prepared=session.prepareRun(scenario)) {
                var run=prepared.handle(); var summary=run.await(); if (summary.state()!=RunState.COMPLETED) throw new IllegalStateException("D13a run failed: "+summary.diagnostics());
                for (var o : run.finals()) { var a=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D13a observation")).attributes(); if (!o.outcome().name().equals(a.get("verdict"))) throw new IllegalArgumentException("conflicting D13a verdict"); if (rows.putIfAbsent(a.get("case_id"),a)!=null) throw new IllegalArgumentException("duplicate D13a observation"); }
            }
            var records=rows.values().stream().map(ToolCallAnalysis.Observed::new).toList(); var analysis=ToolCallAnalysis.analyse(records);
            long core=rows.keySet().stream().map(id -> id.substring(0,id.lastIndexOf("|X="))).distinct().count(); var cuts=rows.values().stream().map(a -> a.get("revoke_cut")).collect(java.util.stream.Collectors.toSet());
            var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tool-order-report schema=\"d13a.analysis/v1\" source=\"observed traces of all 480 cases\" observations=\"").append(rows.size()).append("\">\n  <population mandatory-core=\"").append(core).append("\" optional-revoke-rows=\"").append(cuts.size()-1).append("\" optional-multiplier=\"").append(cuts.size()).append("\"/>\n");
            for (var o : rows.entrySet()) { xml.append("  <observation id=\"").append(Xml.escape(o.getKey())).append("\">\n"); for (var f : new TreeMap<>(o.getValue()).entrySet()) xml.append("    <field name=\"").append(f.getKey()).append("\">").append(elementText(f.getValue())).append("</field>\n"); xml.append("  </observation>\n"); }
            for (var axis : List.of("by_policy","by_policy_repetitions","by_policy_read_failure","by_policy_revoke_cut","by_policy_order")) {
                xml.append("  <axis name=\"").append(axis).append("\">\n");
                for (var group : object(analysis.get(axis)).entrySet()) { xml.append("    <group key=\"").append(Xml.escape(group.getKey())).append("\">"); for (var count : object(group.getValue()).entrySet()) xml.append("<outcome name=\"").append(count.getKey()).append("\" count=\"").append(count.getValue()).append("\"/>"); xml.append("</group>\n"); }
                xml.append("  </axis>\n");
            }
            xml.append("  <violation-kinds>\n");
            for (var p : object(analysis.get("violation_kinds")).entrySet()) { xml.append("    <policy name=\"").append(p.getKey()).append("\">"); for (var v : object(p.getValue()).entrySet()) xml.append("<violation kind=\"").append(v.getKey()).append("\" count=\"").append(v.getValue()).append("\"/>"); xml.append("</policy>\n"); }
            xml.append("  </violation-kinds>\n  <counts"); for (var c : object(analysis.get("counts")).entrySet()) xml.append(' ').append(c.getKey()).append("=\"").append(c.getValue()).append('"'); xml.append("/>\n  <timelines>\n");
            for (var w : object(analysis.get("timelines")).entrySet()) {
                xml.append("    <witness id=\"").append(Xml.escape(w.getKey())).append("\">\n");
                for (var value : (List<?>)w.getValue()) { var e=object(value); var local=object(e.get("local_state")); var prefix=object(e.get("prefix_evidence")); xml.append("      <event label=\"").append(Xml.escape((String)e.get("event"))).append("\" buffer=\"").append(Xml.escape((String)e.get("buffer"))).append("\" outbox-size=\"").append(e.get("outbox_size")).append('"'); for (var s : local.entrySet()) xml.append(" local-").append(s.getKey()).append("=\"").append(s.getValue()).append('"'); for (var s : prefix.entrySet()) xml.append(" prefix-").append(s.getKey()).append("=\"").append(s.getValue()).append('"'); xml.append("/>\n"); }
                xml.append("    </witness>\n");
            }
            xml.append("  </timelines>\n</tool-order-report>\n"); Files.writeString(out,xml,StandardCharsets.UTF_8);
            System.out.println("D13a: "+rows.size()+" observations; outcomes "+analysis.get("by_policy")+"; counts "+analysis.get("counts")+"; all five axes,three fault mechanisms and five prefix timelines");
        }
    }
}
