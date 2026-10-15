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
import org.ghyura.cf.fixtures.demos.ConcurrencySchedules;
import org.ghyura.cf.fixtures.demos.ConcurrencySchedulesPlugin;
import org.ghyura.cf.fixtures.demos.ConcurrencyScheduleAnalysis;
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

/** Both original D6 campaigns, complete raw rule/exploration certificates and observed summaries through shared services.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 * tools/ConcurrencySchedulesReport.java --counter PATH/spec.toml --queue PATH/spec.toml --out schedules.xml [--workers 4]
 */
public final class ConcurrencySchedulesReport {
    private static String text(String value) { return Xml.escape(value).replace("&quot;","\"").replace("&apos;","'"); }
    private static byte expression(String w) {
        int a=w.charAt(0),b=w.charAt(1),c=w.charAt(2),d=w.charAt(3);
        return (byte)((a!=b?1:0)+(b!=c&&a!=b?1:0)+(c!=d&&a!=c&&b!=c?1:0));
    }
    // Own exploration, separate from machine execution and the policy-blind trace reference.
    private static byte prefixCount(String w) {
        byte count=0; for (int i=0;i<3;i++) { char t=w.charAt(i); int completed=0; for (int k=0;k<=i;k++) if (w.charAt(k)==t) completed++; if (t!=w.charAt(i+1)&&completed<2) count++; } return count;
    }
    private static byte switches(String w) { byte n=0; for (int i=0;i<3;i++) if (w.charAt(i)!=w.charAt(i+1)) n++; return n; }
    private static Byte disabledQueueStep(String w) {
        byte producer=0,consumer=0,items=0,index=0;
        for (char t : w.toCharArray()) { index++; if (t=='P') { if (++producer==1) items++; } else { consumer++; if (consumer==1&&items==0) return index; if (consumer==2&&items>0) items--; } } return null;
    }
    private record Precheck(Map<String,Object> document,List<Map<String,Object>> rawRows,Map<String,Object> stages) { }
    /** The live existing rule engine assesses every raw generated row, before any SUT runs. */
    private static Precheck precheck(String campaign,Path scenario,Session session) throws Exception {
        var spec=ScenarioLoader.load(scenario).spec(); var config=session.configuration().config(); var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan(); var budget=MemoryBudget.of(config); var options=GenerationOptions.of(config);
        if (spec.constraints().rules().size()!=2) throw new IllegalArgumentException("two original D6 bonds required");
        var perRule=new TreeMap<String,Integer>(); var sequential=new int[3]; int overlap=0,removals=0; var kept=new ArrayList<List<Object>>(); var rawRows=new ArrayList<Map<String,Object>>(); Map<String,Object> stages;
        try (var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
             var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
             var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            for (int k=0;k<2;k++) { if (!assembly.rules().mandatoryOnly(k)) throw new IllegalArgumentException("original mandatory D6 bonds required"); perRule.put(assembly.rules().name(k),0); }
            var rows=assembly.assessed(Cancellation.NONE);
            while (rows.hasNext()) {
                var r=rows.next(); var atom=new TreeMap<String,String>(); var names=assembly.columnSheets();
                for (int k=0;k<names.size();k++) { var labels=r.candidate().labels(k); if (labels==null||labels.length!=1) throw new IllegalArgumentException("one atom per D6 raw sheet required"); atom.put(names.get(k),dictionary.value(labels[0]).text().strip()); }
                String policy=atom.get("IMPL").substring(6,atom.get("IMPL").length()-3); Integer cap=campaign.equals("counter")?Integer.valueOf(atom.get("CAP").substring(4,5)):null;
                var word=new StringBuilder(); for (int k=1;k<=4;k++) word.append(atom.get("S"+k).charAt(12)); String w=word.toString(); var hit=r.violations(); var hits=new ArrayList<String>();
                sequential[0]++; for (int k=0;k<2;k++) { if (hit.get(k)) { String name=assembly.rules().name(k); perRule.merge(name,1,Integer::sum); hits.add(name); } if (hit.nextSetBit(0)<0||hit.nextSetBit(0)>=k+1) sequential[k+1]++; }
                if (hit.cardinality()>1) overlap++; if (!hit.isEmpty()) removals++; else kept.add(Arrays.asList(policy,cap,w));
                var raw=new TreeMap<String,Object>(); raw.put("policy",policy); raw.put("cap",cap); raw.put("word",w); raw.put("violated_bonds",hits); rawRows.add(raw);
            }
            var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(150)); if (!count.exact()) throw new IllegalStateException("D6 exact raw certificate required");
            stages=Map.of("raw",count.generated(),"early_excluded",count.removedEarly(),"late_excluded",count.removedLate(),"unselected",count.unselected(),"executable",count.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());
        }
        if (budget.reservedBytes()!=0) throw new IllegalStateException("D6 precheck retained operation reservations");
        kept.sort(java.util.Comparator.comparing(r -> r.getFirst()+"|"+r.get(1)+"|"+r.get(2)));
        var doc=new TreeMap<String,Object>(); doc.put("schema","d6.precheck/v1"); doc.put("campaign",campaign); doc.put("note","before execution; independent of the SUT and runtime"); doc.put("truth_table",Map.of("raw",sequential[0],"per_rule",perRule,"overlap",overlap,"unique_removals",removals,"sequential",Arrays.stream(sequential).boxed().toList())); doc.put("kept_rows",kept);
        var wordInfo=new TreeMap<String,Object>(); var byCap=new TreeMap<String,List<String>>(); var feasible=new ArrayList<String>(); var disabled=new TreeMap<String,Byte>(); boolean agrees=true;
        for (String w : ConcurrencyScheduleAnalysis.words(campaign)) {
            long n=w.chars().filter(t -> t==(campaign.equals("counter")?'A':'P')).count(); boolean two=n==2;
            if (campaign.equals("counter")) { byte prefix=prefixCount(w),expr=expression(w); agrees&=prefix==expr; wordInfo.put(w,Map.of("two_each",two,"prefix_preemptions",prefix,"expression",expr,"context_switches",switches(w))); }
            else {
                var info=new TreeMap<String,Object>(); String status; Object detail;
                if (!two) { status="malformed"; detail="not two steps per thread"; }
                else { Byte blocked=disabledQueueStep(w); if (blocked==null) { status="feasible"; detail=null; feasible.add(w); } else { status="disabled"; detail=blocked; disabled.put(w,blocked); } }
                info.put("status",status); info.put("detail",detail); wordInfo.put(w,info);
            }
        }
        doc.put("words",wordInfo);
        if (campaign.equals("counter")) {
            for (byte cap=0;cap<3;cap++) { byte limit=cap; byCap.put(Byte.toString(cap),ConcurrencyScheduleAnalysis.words(campaign).stream().filter(w -> w.chars().filter(t -> t=='A').count()==2&&prefixCount(w)<=limit).sorted().toList()); }
            doc.put("expression_equals_prefix_count_on_all_16",agrees); doc.put("schedules_by_cap",byCap);
        } else {
            feasible.sort(String::compareTo); doc.put("feasible",feasible); doc.put("infeasible_two_each",disabled); doc.put("feasible_equals_first_letter_P",feasible.equals(ConcurrencyScheduleAnalysis.words(campaign).stream().filter(w -> w.chars().filter(t -> t=='P').count()==2&&w.charAt(0)=='P').sorted().toList())); doc.put("bonds_retain_exactly_feasible",kept.stream().map(r -> (String)r.get(2)).distinct().sorted().toList().equals(feasible));
        }
        return new Precheck(doc,rawRows,stages);
    }
    public static void main(String[] args) throws Exception {
        Path counter=null,queue=null,out=null; String workers="1";
        for (int i=0;i<args.length;i+=2) { if (i+1==args.length) throw new IllegalArgumentException("option value required"); switch (args[i]) { case "--counter" -> counter=Path.of(args[i+1]); case "--queue" -> queue=Path.of(args[i+1]); case "--out" -> out=Path.of(args[i+1]); case "--workers" -> workers=args[i+1]; default -> throw new IllegalArgumentException("unknown option "+args[i]); } }
        if (counter==null||queue==null||out==null) throw new IllegalArgumentException("--counter --queue and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",ConcurrencySchedulesPlugin.ID,"D6 report"),new ConfigResolver.Override("cf.execution.workers",workers,"D6 report"))); var budget=MemoryBudget.of(session.configuration().config());
        try (var owned=budget.reserve(8L*1024*1024,"D6 raw rule certificates, observed bank, summaries/witnesses and XML")) {
            if (owned.bytes()!=8L*1024*1024) throw new IllegalStateException("invalid D6 report reservation");
            var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<concurrency-report schema=\"d6.analysis/v1\">\n"); int total=0;
            for (String campaign : List.of("counter","queue")) {
                Path scenario=campaign.equals("counter")?counter:queue; var pre=precheck(campaign,scenario,session); var bank=new TreeMap<String,Map<String,String>>(); long generated,excluded,samples;
                try (var prepared=session.prepareRun(scenario)) {
                    var run=prepared.handle(); var summary=run.await(); if (summary.state()!=RunState.COMPLETED) throw new IllegalStateException("D6 run failed: "+summary.diagnostics()); generated=summary.generated().longValueExact(); excluded=summary.excluded().longValueExact(); samples=summary.samples();
                    for (var o : run.finals()) { var f=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D6 observation")).attributes(); if (!o.outcome().name().equals(f.get("verdict"))||bank.putIfAbsent(f.get("case_id"),f)!=null) throw new IllegalArgumentException("conflicting/duplicate D6 observations"); }
                }
                var analysis=ConcurrencyScheduleAnalysis.analyse(campaign,bank.values().stream().map(ConcurrencyScheduleAnalysis.Observed::new).toList()); total+=bank.size();
                xml.append("  <campaign name=\"").append(campaign).append("\" generated=\"").append(generated).append("\" excluded=\"").append(excluded).append("\" samples=\"").append(samples).append("\">\n    <precheck>").append(text(ConcurrencySchedules.json(pre.document()))).append("</precheck>\n    <stage-counts>").append(text(ConcurrencySchedules.json(pre.stages()))).append("</stage-counts>\n    <raw-rule-rows>").append(text(ConcurrencySchedules.json(pre.rawRows()))).append("</raw-rule-rows>\n");
                for (var row : bank.entrySet()) { xml.append("    <observation id=\"").append(Xml.escape(row.getKey())).append("\">\n"); for (var f : new TreeMap<>(row.getValue()).entrySet()) xml.append("      <field name=\"").append(f.getKey()).append("\">").append(text(f.getValue())).append("</field>\n"); xml.append("    </observation>\n"); }
                xml.append("    <analysis>").append(text(ConcurrencySchedules.json(analysis))).append("</analysis>\n  </campaign>\n"); System.out.println("D6 "+campaign+": "+generated+" raw, "+excluded+" excluded, "+samples+" observed; outcomes "+analysis.get("totals"));
            }
            xml.append("  <total observations=\"").append(total).append("\"/>\n</concurrency-report>\n"); Files.writeString(out,xml,StandardCharsets.UTF_8);
        }
    }
}
