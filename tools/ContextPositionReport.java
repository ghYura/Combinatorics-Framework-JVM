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
import org.ghyura.cf.fixtures.demos.ContextPosition;
import org.ghyura.cf.fixtures.demos.ContextPositionPlugin;
import org.ghyura.cf.fixtures.demos.ContextPositionAnalysis;
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

/** Complete original D13e run, native order certificate and recorded calibration/paired tables/witnesses. */
public final class ContextPositionReport {
    private static String text(String value) { return Xml.escape(value).replace("&quot;","\"").replace("&apos;","'"); }

    private record Precheck(Map<String,Object> construction,Map<String,Object> stages) { }
    private static Precheck precheck(Path scenario,Session session)throws Exception{
        var spec=ScenarioLoader.load(scenario).spec();var config=session.configuration().config();var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan();var budget=MemoryBudget.of(config);var options=GenerationOptions.of(config);var construction=new TreeMap<String,Object>();Map<String,Object> stages;
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)){
            if(!assembly.columnSheets().equals(ContextPositionPlugin.SHEETS)||assembly.rules().size()!=0)throw new IllegalArgumentException("five original D13e structural columns without rules required");var codes=new TreeMap<String,Object>();for(var sheet:spec.sheets())codes.put(sheet.name(),Arrays.stream(dictionary.labelsOf(sheet.key())).boxed().toList());var rows=new ArrayList<List<Integer>>();var values=new ArrayList<List<String>>();var permutations=new ArrayList<String>();try(var cursor=generated.result().sheet("ORDER").orElseThrow().rows().open(Cancellation.NONE)){while(cursor.hasNext()){var labels=Arrays.stream(cursor.next().keys().labels()).boxed().toList();var text=labels.stream().map(x -> dictionary.value(x).text()).toList();rows.add(labels);values.add(text);permutations.add(ContextPositionPlugin.order(text.getFirst()));}}
            construction.put("predicted_codes",codes);construction.put("ORDER",rows);construction.put("ORDER_values",values);construction.put("orders",permutations);construction.put("data_sheets",spec.sheetCount());construction.put("structural_columns",assembly.columnSheets());construction.put("rule_count",assembly.rules().size());construction.put("virtual_sheets",plan.virtualSheetCount());var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(100));if(!count.exact())throw new IllegalStateException("exact D13e population required");stages=Map.of("raw",count.generated(),"early_excluded",count.removedEarly(),"late_excluded",count.removedLate(),"unselected",count.unselected(),"executable",count.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());
        }if(budget.reservedBytes()!=0)throw new IllegalStateException("D13e precheck retained reservations");return new Precheck(construction,stages);
    }
    public static void main(String[] args) throws Exception {
        Path scenario=null,out=null; String workers="1";
        for (int i=0;i<args.length;i+=2) { if (i+1==args.length) throw new IllegalArgumentException("option value required"); switch (args[i]) { case "--scenario" -> scenario=Path.of(args[i+1]); case "--out" -> out=Path.of(args[i+1]); case "--workers" -> workers=args[i+1]; default -> throw new IllegalArgumentException("unknown option "+args[i]); } }
        if (scenario==null||out==null) throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",ContextPositionPlugin.ID,"D13e report"),new ConfigResolver.Override("cf.execution.workers",workers,"D13e report"))); var budget=MemoryBudget.of(session.configuration().config());
        try (var owned=budget.reserve(16L*1024*1024,"D13e context/trial/calibration certificates, observed bank, analysis and XML")) {
            if (owned.bytes()!=16L*1024*1024) throw new IllegalStateException("invalid D13e report reservation");
            var pre=precheck(scenario,session); var bank=new TreeMap<String,Map<String,String>>(); long generated,excluded,samples;
            try (var prepared=session.prepareRun(scenario)) {
                var run=prepared.handle(); var summary=run.await(); if (summary.state()!=RunState.COMPLETED) throw new IllegalStateException("D13e run failed: "+summary.diagnostics()); generated=summary.generated().longValueExact(); excluded=summary.excluded().longValueExact(); samples=summary.samples();
                for (var o : run.finals()) { var f=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D13e observation")).attributes(); if (!o.outcome().name().equals(f.get("verdict"))||bank.putIfAbsent(f.get("case_id"),f)!=null) throw new IllegalArgumentException("conflicting/duplicate D13e observations"); }
            }
            var observations=bank.values().stream().map(ContextPositionAnalysis.Observed::new).toList();var analysis=ContextPositionAnalysis.analyse(observations);
            var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<context-position-report schema=\"d13e.analysis/v1\" observations=\"").append(bank.size()).append("\" generated=\"").append(generated).append("\" excluded=\"").append(excluded).append("\" samples=\"").append(samples).append("\">\n");
            xml.append("  <construction>").append(text(ContextPosition.json(pre.construction()))).append("</construction>\n  <stage-counts>").append(text(ContextPosition.json(pre.stages()))).append("</stage-counts>\n");
            for (var row : bank.entrySet()) { xml.append("  <observation id=\"").append(Xml.escape(row.getKey())).append("\">\n"); for (var f : new TreeMap<>(row.getValue()).entrySet()) xml.append("    <field name=\"").append(f.getKey()).append("\">").append(text(f.getValue())).append("</field>\n"); xml.append("  </observation>\n"); }
            xml.append("  <universe>").append(text(ContextPosition.json(ContextPositionAnalysis.universe()))).append("</universe>\n  <coverage>").append(text(ContextPosition.json(ContextPositionAnalysis.coverage()))).append("</coverage>\n");
            xml.append("  <analysis>").append(text(ContextPosition.json(analysis))).append("</analysis>\n</context-position-report>\n"); Files.writeString(out,xml,StandardCharsets.UTF_8); System.out.println("D13e: "+generated+" raw, "+excluded+" excluded, "+samples+" observed; outcomes "+analysis.get("totals"));
        }
    }
}
