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
import org.ghyura.cf.fixtures.demos.SessionMemory;
import org.ghyura.cf.fixtures.demos.SessionMemoryPlugin;
import org.ghyura.cf.fixtures.demos.SessionMemoryAnalysis;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Complete original D13d session-memory run and observed analysis through shared services. */
public final class SessionMemoryReport {
    private static String text(String value) { return Xml.escape(value).replace("&quot;","\"").replace("&apos;","'"); }
    private static boolean canonical(String t) { int max=-1; for (char c : t.toCharArray()) { int n=c-'0'; if (n>max+1) return false; max=Math.max(max,n); } return true; }
    private static List<List<Integer>> partition(String t) {
        var blocks=new java.util.LinkedHashMap<Character,List<Integer>>(); for (int i=0;i<3;i++) blocks.computeIfAbsent(t.charAt(i),k -> new ArrayList<>()).add(i+1); return List.copyOf(blocks.values());
    }
    private record Precheck(Map<String,Object> document,Map<String,Object> truth,Map<String,Object> stages) { }
    private static Precheck precheck(Path scenario,Session session) throws Exception {
        var spec=ScenarioLoader.load(scenario).spec(); var config=session.configuration().config(); var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan(); var budget=MemoryBudget.of(config); var options=GenerationOptions.of(config);
        if (spec.constraints().rules().size()!=2) throw new IllegalArgumentException("two embedded original D13d bonds required");
        var perRule=new TreeMap<String,Integer>(); var sequential=new int[3]; int overlap=0,removals=0; var truth=new TreeMap<String,Object>(); var pairs=new java.util.TreeSet<String>(); var strata=new TreeMap<String,Map<String,Integer>>(); for (String ax : List.of("policy","cuts","write_at")) strata.put(ax,new TreeMap<>()); Map<String,Object> stages;
        try (var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
             var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
             var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            for (int k=0;k<2;k++) { if (!assembly.rules().mandatoryOnly(k)) throw new IllegalArgumentException("mandatory D13d bonds required"); perRule.put(assembly.rules().name(k),0); }
            var rows=assembly.assessed(Cancellation.NONE);
            while (rows.hasNext()) {
                var r=rows.next(); var a=new TreeMap<String,String>(); var names=assembly.columnSheets();
                for (int k=0;k<names.size();k++) { var labels=r.candidate().labels(k); if (labels==null||labels.length!=1) throw new IllegalArgumentException("one atom per raw D13d sheet required"); a.put(names.get(k),dictionary.value(labels[0]).text()); }
                String policy=a.get("IMPL").substring(6,a.get("IMPL").length()-3); var u=new StringBuilder(); var s=new StringBuilder(); var x=new StringBuilder(); for (int i=1;i<=3;i++) { u.append(a.get("U"+i).charAt(10)); s.append(a.get("S"+i).charAt(13)); } for (String cut : List.of("R12","R23")) x.append(a.get(cut).charAt(12)); String w=a.get("WRITE_AT").substring(9,10);
                var hit=r.violations(); var rules=new TreeMap<String,Boolean>(); sequential[0]++;
                for (int k=0;k<2;k++) { String name=assembly.rules().name(k); rules.put(name,!hit.get(k)); if (hit.get(k)) perRule.merge(name,1,Integer::sum); if (hit.nextSetBit(0)<0||hit.nextSetBit(0)>=k+1) sequential[k+1]++; }
                if (hit.cardinality()>1) overlap++; if (!hit.isEmpty()) removals++; else { pairs.add(u+"|"+s); strata.get("policy").merge(policy,1,Integer::sum); strata.get("cuts").merge(x.toString(),1,Integer::sum); strata.get("write_at").merge(w,1,Integer::sum); }
                String id="P="+policy+"|U="+u+"|S="+s+"|X="+x+"|W="+w; if (truth.putIfAbsent(id,rules)!=null) throw new IllegalArgumentException("duplicate raw D13d row");
            }
            var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(1200)); if (!count.exact()) throw new IllegalStateException("exact D13d certificate required");
            stages=Map.of("raw",count.generated(),"early_excluded",count.removedEarly(),"late_excluded",count.removedLate(),"unselected",count.unselected(),"executable",count.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());
        }
        if (budget.reservedBytes()!=0) throw new IllegalStateException("D13d precheck retained reservations");
        var triples=new TreeMap<String,Object>(); var canon=new ArrayList<String>(); var canonParts=new java.util.HashSet<List<List<Integer>>>(); var allParts=new java.util.HashSet<List<List<Integer>>>(); boolean agrees=true;
        for (int a=0;a<2;a++) for (int b=0;b<3;b++) { String t="0"+a+b; boolean valid=canonical(t); triples.put(t,Map.of("canonical",valid,"partition",partition(t))); agrees&=valid==(b<=1+a); if (valid) { canon.add(t); canonParts.add(partition(t)); } }
        for (int a=0;a<3;a++) for (int b=0;b<3;b++) for (int c=0;c<3;c++) allParts.add(partition(""+a+b+c));
        var doc=new TreeMap<String,Object>(); doc.put("schema","d13d.precheck/v1"); doc.put("note","before execution; independent of the SUT, oracle and runtime"); doc.put("triples",triples); doc.put("canonical",canon); doc.put("canonical_are_the_bell3_partitions",canon.size()==5&&canonParts.equals(allParts)); doc.put("bond_equals_canonical_on_slot_domain",agrees); doc.put("truth_table",Map.of("raw",sequential[0],"per_rule",perRule,"overlap",overlap,"unique_removals",removals,"sequential",Arrays.stream(sequential).boxed().toList())); doc.put("partition_pairs",pairs.size()); doc.put("strata",strata);
        return new Precheck(doc,truth,stages);
    }
    public static void main(String[] args) throws Exception {
        Path scenario=null,out=null; String workers="1";
        for (int i=0;i<args.length;i+=2) { if (i+1==args.length) throw new IllegalArgumentException("option value required"); switch (args[i]) { case "--scenario" -> scenario=Path.of(args[i+1]); case "--out" -> out=Path.of(args[i+1]); case "--workers" -> workers=args[i+1]; default -> throw new IllegalArgumentException("unknown option "+args[i]); } }
        if (scenario==null||out==null) throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",SessionMemoryPlugin.ID,"D13d report"),new ConfigResolver.Override("cf.execution.workers",workers,"D13d report"))); var budget=MemoryBudget.of(session.configuration().config());
        try (var owned=budget.reserve(16L*1024*1024,"D13d raw rule certificates, observed bank, analysis and XML")) {
            if (owned.bytes()!=16L*1024*1024) throw new IllegalStateException("invalid D13d report reservation");
            var pre=precheck(scenario,session); var bank=new TreeMap<String,Map<String,String>>(); long generated,excluded,samples;
            try (var prepared=session.prepareRun(scenario)) {
                var run=prepared.handle(); var summary=run.await(); if (summary.state()!=RunState.COMPLETED) throw new IllegalStateException("D13d run failed: "+summary.diagnostics()); generated=summary.generated().longValueExact(); excluded=summary.excluded().longValueExact(); samples=summary.samples();
                for (var o : run.finals()) { var f=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D13d observation")).attributes(); if (!o.outcome().name().equals(f.get("verdict"))||bank.putIfAbsent(f.get("case_id"),f)!=null) throw new IllegalArgumentException("conflicting/duplicate D13d observations"); }
            }
            var analysis=SessionMemoryAnalysis.analyse(bank.values().stream().map(SessionMemoryAnalysis.Observed::new).toList());
            var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<session-memory-report schema=\"d13d.analysis/v1\" observations=\"").append(bank.size()).append("\" generated=\"").append(generated).append("\" excluded=\"").append(excluded).append("\" samples=\"").append(samples).append("\">\n");
            xml.append("  <precheck>").append(text(SessionMemory.json(pre.document()))).append("</precheck>\n  <stage-counts>").append(text(SessionMemory.json(pre.stages()))).append("</stage-counts>\n  <raw-bond-truth>").append(text(SessionMemory.json(pre.truth()))).append("</raw-bond-truth>\n");
            for (var row : bank.entrySet()) { xml.append("  <observation id=\"").append(Xml.escape(row.getKey())).append("\">\n"); for (var f : new TreeMap<>(row.getValue()).entrySet()) xml.append("    <field name=\"").append(f.getKey()).append("\">").append(text(f.getValue())).append("</field>\n"); xml.append("  </observation>\n"); }
            xml.append("  <analysis>").append(text(SessionMemory.json(analysis))).append("</analysis>\n</session-memory-report>\n"); Files.writeString(out,xml,StandardCharsets.UTF_8); System.out.println("D13d: "+generated+" raw, "+excluded+" excluded, "+samples+" observed; outcomes "+analysis.get("totals"));
        }
    }
}
