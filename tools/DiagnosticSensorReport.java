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
import org.ghyura.cf.fixtures.demos.DiagnosticSensorPlugin;
import org.ghyura.cf.fixtures.demos.SensorDiagnosis;
import org.ghyura.cf.store.MemoryBudget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D10 complete observed diagnosis through the shared run service.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 * tools/DiagnosticSensorReport.java --scenario PATH/spec.toml --out diagnosis.xml [--workers 4]
 */
public final class DiagnosticSensorReport {
    private static String escape(String s) { return s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;"); }
    private static String join(List<?> values) { return values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" ")); }
    private static void node(StringBuilder xml,SensorDiagnosis.Node n) {
        xml.append("<node cost=\"").append(n.cost()).append('"');
        if (n.sensor()==null) xml.append(" class=\"").append(n.diagnosis()).append("\"/>");
        else { xml.append(" sensor=\"").append(n.sensor()).append("\"><branch reading=\"0\">"); node(xml,n.zero()); xml.append("</branch><branch reading=\"1\">"); node(xml,n.one()); xml.append("</branch></node>"); }
    }
    public static void main(String[] args) throws Exception {
        Path scenario=null,out=null; String workers="1";
        for (int i=0;i<args.length;i+=2) { if (i+1==args.length) throw new IllegalArgumentException("option value required"); switch (args[i]) { case "--scenario" -> scenario=Path.of(args[i+1]); case "--out" -> out=Path.of(args[i+1]); case "--workers" -> workers=args[i+1]; default -> throw new IllegalArgumentException("unknown option "+args[i]); } }
        if (scenario==null||out==null) throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",DiagnosticSensorPlugin.ID,"D10 report"),new ConfigResolver.Override("cf.execution.workers",workers,"D10 report"),new ConfigResolver.Override("cf.generation.distinctTables","true","original D10 Subsets to Subsets distinctification")));
        var budget=MemoryBudget.of(session.configuration().config());
        try (var owned=budget.reserve(16L*1024*1024,"D10 complete observed matrix, suite/adaptive/noise analysis and XML")) {
            if (owned.bytes()!=16L*1024*1024) throw new IllegalStateException("invalid D10 report reservation");
            var records=new ArrayList<SensorDiagnosis.Observed>(); var fields=new TreeMap<String,Map<String,String>>();
            try (var prepared=session.prepareRun(scenario)) {
                var run=prepared.handle(); var summary=run.await(); if (summary.state()!=RunState.COMPLETED) throw new IllegalStateException("D10 run failed: "+summary.diagnostics());
                for (var o : run.finals()) {
                    var a=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D10 observation")).attributes(); if (!o.outcome().name().equals(a.get("verdict"))) throw new IllegalArgumentException("conflicting D10 verdict");
                    records.add(new SensorDiagnosis.Observed(a.get("case_id"),a.get("mask"),a.get("fault"),o.outcome().name(),SensorDiagnosis.parseReadings(a.get("readings"))));
                    if (fields.putIfAbsent(a.get("case_id"),a)!=null) throw new IllegalArgumentException("duplicate D10 observation");
                }
            }
            var r=SensorDiagnosis.analyse(records); var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<diagnostic-report schema=\"d10.diagnosis/v1\" source=\"observed readings of all 256 x 12 evaluations; H0 declared\" observations=\"3072\" unquotiented_label_min_distance=\"").append(r.unquotientedLabelMinDistance()).append("\">\n");
            for (var o : fields.entrySet()) { xml.append("  <observation id=\"").append(escape(o.getKey())).append("\">\n"); for (var f : new TreeMap<>(o.getValue()).entrySet()) xml.append("    <field name=\"").append(f.getKey()).append("\">").append(escape(f.getValue())).append("</field>\n"); xml.append("  </observation>\n"); }
            xml.append("  <healthy-reference class=\"0\" members=\"H0\" source=\"").append(escape(SensorDiagnosis.HEALTHY_SOURCE)).append("\"/>\n  <classes>\n");
            for (var c : r.classes()) xml.append("    <class index=\"").append(c.index()).append("\" code=\"").append(c.code()).append("\" signature=\"").append(join(c.signature())).append("\" members=\"").append(join(c.members())).append("\" source=\"").append(escape(c.source())).append("\"/>\n");
            xml.append("  </classes>\n  <suites>\n");
            for (var s : r.suites()) {
                xml.append("    <suite mask=\"").append(s.mask()).append("\" cost=\"").append(s.cost()).append("\" min_distance=\"").append(s.minDistance()).append("\" detect=\"").append(s.detect()).append("\" separate=\"").append(s.separate()).append("\" erasure=\"").append(s.erasure()).append("\" error=\"").append(s.error()).append("\">\n");
                for (var word : s.signatures()) xml.append("      <signature>").append(word).append("</signature>\n"); for (var d : s.distances()) xml.append("      <distance classes=\"").append(d.first()).append(' ').append(d.second()).append("\" value=\"").append(d.value()).append("\"/>\n"); xml.append("    </suite>\n");
            }
            xml.append("  </suites>\n  <winners>\n");
            for (var goal : List.of("detect","separate","erasure","error")) { var w=r.winners().get(goal); xml.append("    <winner goal=\"").append(goal).append("\" mask=\"").append(w.mask()).append("\" cost=\"").append(w.cost()).append("\" feasible_count=\"").append(w.feasibleCount()).append("\" minimum_cost_masks=\"").append(join(w.minimumCostMasks())).append("\"/>\n"); }
            xml.append("  </winners>\n  <adaptive>\n    "); node(xml,r.adaptive().tree()); xml.append('\n');
            for (var b : r.adaptive().dp()) xml.append("    <belief classes=\"").append(join(b.classes())).append("\" cost=\"").append(b.cost()).append("\" sensor=\"").append(b.sensor()==null?"none":b.sensor().toString()).append("\"/>\n");
            for (var p : r.adaptive().paths()) { xml.append("    <path class=\"").append(p.diagnosis()).append("\" cost=\"").append(p.cost()).append("\">"); for (var s : p.steps()) xml.append("<step sensor=\"").append(s.sensor()).append("\" reading=\"").append(s.reading()).append("\"/>"); xml.append("</path>\n"); }
            xml.append("  </adaptive>\n  <noise>\n");
            for (var kind : List.of("erasure","error")) { xml.append("    <").append(kind).append(">\n"); for (var n : r.noise().get(kind)) xml.append("      <control class=\"").append(n.diagnosis()).append("\" position=\"").append(n.position()).append("\" observed=\"").append(n.observed()).append("\" decoded=\"").append(join(n.decoded())).append("\"/>\n"); xml.append("    </").append(kind).append(">\n"); }
            xml.append("  </noise>\n  <identify-examples>\n");
            for (var e : new TreeMap<>(r.identifyExamples()).entrySet()) xml.append("    <identify fault=\"").append(e.getKey()).append("\" class=\"").append(e.getValue().diagnosis()).append("\" members=\"").append(join(e.getValue().members())).append("\"/>\n");
            xml.append("  </identify-examples>\n</diagnostic-report>\n"); Files.writeString(out,xml,StandardCharsets.UTF_8);
            System.out.println("D10: 3072 observations; 7 classes; 256 suites; 127 adaptive beliefs, worst-case cost "+r.adaptive().tree().cost()+"; 49 erasure and 56 error controls; winners "+r.winners());
        }
    }
}
