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
import org.ghyura.cf.fixtures.demos.Idempotency;
import org.ghyura.cf.fixtures.demos.IdempotencyPlugin;
import org.ghyura.cf.fixtures.demos.IdempotencyAnalysis;
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

/** Complete original D1 run, raw rule certificate and observed tables/histograms/witness groups. */
public final class IdempotencyReport {
    private static String text(String value) { return Xml.escape(value).replace("&quot;","\"").replace("&apos;","'"); }
    private record Precheck(Map<String,Object> summary,Map<String,Object> truth,Map<String,Object> stages) { }
    private static Precheck precheck(Path scenario,Session session) throws Exception {
        var spec=ScenarioLoader.load(scenario).spec(); var config=session.configuration().config(); var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan(); var budget=MemoryBudget.of(config); var options=GenerationOptions.of(config);
        if (spec.constraints().rules().size()!=2) throw new IllegalArgumentException("two embedded original D1 bonds required");
        var perRule=new TreeMap<String,Integer>(); var sequential=new int[3]; int overlap=0,removals=0; var truth=new TreeMap<String,Object>(); Map<String,Object> stages;
        try (var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
             var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
             var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            for (int k=0;k<2;k++) { if (!assembly.rules().mandatoryOnly(k)) throw new IllegalArgumentException("mandatory D1 bonds required"); perRule.put(assembly.rules().name(k),0); }
            var rows=assembly.assessed(Cancellation.NONE);
            while (rows.hasNext()) {
                var row=rows.next(); var atom=new TreeMap<String,String>(); var names=assembly.columnSheets(); for (int k=0;k<names.size();k++) { var labels=row.candidate().labels(k); if (labels==null||labels.length!=1) throw new IllegalArgumentException("one atom per raw D1 sheet required"); atom.put(names.get(k),dictionary.value(labels[0]).text()); }
                String policy=atom.get("IMPL").substring(8,atom.get("IMPL").length()-1),labels="0"+atom.get("L2").charAt(9)+atom.get("L3").charAt(9),cuts=""+atom.get("CUT1").charAt(7)+atom.get("CUT2").charAt(7),control=atom.get("CONTROL").substring(11,atom.get("CONTROL").length()-1); var hit=row.violations(); var rules=new TreeMap<String,Boolean>(); sequential[0]++;
                for (int k=0;k<2;k++) { String name=assembly.rules().name(k); rules.put(name,!hit.get(k)); if (hit.get(k)) perRule.merge(name,1,Integer::sum); if (hit.nextSetBit(0)<0||hit.nextSetBit(0)>=k+1) sequential[k+1]++; }
                if (hit.cardinality()>1) overlap++; if (!hit.isEmpty()) removals++; String id=new Idempotency.Config(policy,labels,cuts,control).caseId(); if (truth.putIfAbsent(id,rules)!=null) throw new IllegalArgumentException("duplicate raw D1 row");
            }
            var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(1000)); if (!count.exact()) throw new IllegalStateException("exact D1 certificate required"); stages=Map.of("raw",count.generated(),"early_excluded",count.removedEarly(),"late_excluded",count.removedLate(),"unselected",count.unselected(),"executable",count.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());
        }
        if (budget.reservedBytes()!=0) throw new IllegalStateException("D1 precheck retained reservations");
        return new Precheck(Map.of("per_rule",perRule,"overlap",overlap,"unique_removals",removals,"sequential",Arrays.stream(sequential).boxed().toList()),truth,stages);
    }
    public static void main(String[] args) throws Exception {
        Path scenario=null,out=null; String workers="1";
        for (int i=0;i<args.length;i+=2) { if (i+1==args.length) throw new IllegalArgumentException("option value required"); switch (args[i]) { case "--scenario" -> scenario=Path.of(args[i+1]); case "--out" -> out=Path.of(args[i+1]); case "--workers" -> workers=args[i+1]; default -> throw new IllegalArgumentException("unknown option "+args[i]); } }
        if (scenario==null||out==null) throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",IdempotencyPlugin.ID,"D1 report"),new ConfigResolver.Override("cf.execution.workers",workers,"D1 report"))); var budget=MemoryBudget.of(session.configuration().config());
        try (var owned=budget.reserve(8L*1024*1024,"D1 raw rule certificates, observed bank, analysis and XML")) {
            if (owned.bytes()!=8L*1024*1024) throw new IllegalStateException("invalid D1 report reservation");
            var pre=precheck(scenario,session); var bank=new TreeMap<String,Map<String,String>>(); long generated,excluded,samples;
            try (var prepared=session.prepareRun(scenario)) {
                var run=prepared.handle(); var summary=run.await(); if (summary.state()!=RunState.COMPLETED) throw new IllegalStateException("D1 run failed: "+summary.diagnostics()); generated=summary.generated().longValueExact(); excluded=summary.excluded().longValueExact(); samples=summary.samples();
                for (var o : run.finals()) { var f=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D1 observation")).attributes(); if (!o.outcome().name().equals(f.get("verdict"))||bank.putIfAbsent(f.get("case_id"),f)!=null) throw new IllegalArgumentException("conflicting/duplicate D1 observations"); }
            }
            var analysis=IdempotencyAnalysis.analyse(bank.values().stream().map(IdempotencyAnalysis.Observed::new).toList());
            var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<idempotency-report schema=\"d1.analysis/v1\" observations=\"").append(bank.size()).append("\" generated=\"").append(generated).append("\" excluded=\"").append(excluded).append("\" samples=\"").append(samples).append("\">\n");
            xml.append("  <sieve-summary>").append(text(Idempotency.json(pre.summary()))).append("</sieve-summary>\n  <stage-counts>").append(text(Idempotency.json(pre.stages()))).append("</stage-counts>\n  <raw-bond-truth>").append(text(Idempotency.json(pre.truth()))).append("</raw-bond-truth>\n");
            for (var row : bank.entrySet()) { xml.append("  <observation id=\"").append(Xml.escape(row.getKey())).append("\">\n"); for (var f : new TreeMap<>(row.getValue()).entrySet()) xml.append("    <field name=\"").append(f.getKey()).append("\">").append(text(f.getValue())).append("</field>\n"); xml.append("  </observation>\n"); }
            xml.append("  <analysis>").append(text(Idempotency.json(analysis))).append("</analysis>\n</idempotency-report>\n"); Files.writeString(out,xml,StandardCharsets.UTF_8); System.out.println("D1: "+generated+" raw, "+excluded+" excluded, "+samples+" observed; outcomes "+analysis.get("totals"));
        }
    }
}
