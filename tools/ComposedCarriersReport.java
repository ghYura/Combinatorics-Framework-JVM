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

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.fixtures.demos.ComposedCarriers;
import org.ghyura.cf.fixtures.demos.ComposedCarriersPlugin;
import org.ghyura.cf.fixtures.demos.ComposedCarriersAnalysis;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.plan.Directive;
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

/** Original D13b shared generation/run, actual intermediate composition and complete observed analysis. */
public final class ComposedCarriersReport {
    private static String text(String value) { return Xml.escape(value).replace("&quot;","\"").replace("&apos;","'"); }
    private record Precheck(Map<String,Object> construction,Map<String,Object> stages,List<String> diagnostics) { }
    private static Precheck precheck(Path scenario,Session session) throws Exception {
        var spec=ScenarioLoader.load(scenario).spec();var config=session.configuration().config();var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan();var budget=MemoryBudget.of(config);var options=GenerationOptions.of(config);var construction=new TreeMap<String,Object>();Map<String,Object> stages;List<String> diagnostics;
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            if(!assembly.columnSheets().equals(ComposedCarriersPlugin.SHEETS)||assembly.rules().size()!=0)throw new IllegalArgumentException("six original D13b structural columns without sieves required");
            var codes=new TreeMap<String,List<Integer>>();for(var sheet:spec.sheets())codes.put(sheet.name(),Arrays.stream(dictionary.labelsOf(sheet.key())).boxed().toList());construction.put("predicted_codes",codes);
            var model=new TreeMap<String,Object>();var counts=new TreeMap<String,Integer>();var lengths=new TreeMap<String,List<Integer>>();var rootTexts=new ArrayList<String>();var structures=new ArrayList<Map<String,Object>>();var pairs=new ArrayList<List<String>>();var chunkTexts=new ArrayList<List<String>>();var groupSteps=new ArrayList<List<String>>();
            var group=plan.sheets().stream().filter(s -> s.name().equals("CHUNKS")).flatMap(s -> s.directives().stream()).filter(x -> x instanceof Directive.Group).map(x -> (Directive.Group)x).findFirst().orElseThrow();var rewrites=group.replacements().stream().map(x -> List.of(x.pattern(),x.prefix()+x.suffix())).toList();construction.put("rewrite_cell",group.source());model.put("rewrites",rewrites);
            var cartes=new ArrayList<List<Integer>>();for(int v:codes.get("CHUNKS"))for(int x:codes.get("LEAF_END"))cartes.add(List.of(v,x));model.put("CHUNKS_cartes",cartes);model.put("CHUNKS_group_input",cartes);
            for(String name:List.of("CHUNKS","INNER","ROOT")) {
                var rows=generated.result().sheet(name).orElseThrow().rows();var data=new ArrayList<List<Integer>>();var sizes=new java.util.TreeSet<Integer>();try(var cur=rows.open(Cancellation.NONE)){while(cur.hasNext()){var labels=Arrays.stream(cur.next().keys().labels()).boxed().toList();data.add(labels);sizes.add(labels.size());var values=labels.stream().map(x -> dictionary.value(x).text()).toList();
                    if(name.equals("CHUNKS")){chunkTexts.add(values);if(labels.size()!=4)throw new IllegalArgumentException("D13b CHUNKS must retain whole pairs");String before=List.of(labels.subList(0,2),labels.subList(2,4)).toString();var steps=new ArrayList<String>();steps.add(before);for(var rewrite:rewrites){before=java.util.regex.Pattern.compile(rewrite.getFirst()).matcher(before).replaceAll(java.util.regex.Matcher.quoteReplacement(rewrite.getLast()));steps.add(before);}groupSteps.add(steps);}
                    if(name.equals("ROOT")){String actual=String.join("",values);var root=ComposedCarriers.decodeRoot(actual);rootTexts.add(actual);structures.add(root.document());pairs.add(List.of(root.carrier(),root.order()));}
                }}model.put(name,data);counts.put(name,data.size());lengths.put(name,new ArrayList<>(sizes));
            }
            model.put("CHUNKS_group_steps",groupSteps);counts.put("programs",assembly.mandatoryRows().intValueExact());pairs.sort(java.util.Comparator.comparing(x -> x.get(0)+"|"+x.get(1)));construction.put("model",model);construction.put("counts",counts);construction.put("row_lengths",lengths);construction.put("CHUNKS_as_text",chunkTexts);construction.put("ROOT_text",rootTexts);construction.put("ROOT_structure",structures);construction.put("carriers_x_orders",pairs);
            var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(200));if(!count.exact())throw new IllegalStateException("exact D13b population certificate required");stages=Map.of("raw",count.generated(),"early_excluded",count.removedEarly(),"late_excluded",count.removedLate(),"unselected",count.unselected(),"executable",count.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());diagnostics=generated.result().diagnostics().stream().map(x -> x.code()+": "+x.message()).toList();
        }
        if(budget.reservedBytes()!=0)throw new IllegalStateException("D13b precheck retained reservations");return new Precheck(construction,stages,diagnostics);
    }
    private static List<ConfigResolver.Override> settings(int workers) { var values=new ArrayList<ConfigResolver.Override>(); ComposedCarriersPlugin.SETTINGS.forEach((k,v) -> values.add(new ConfigResolver.Override(k,v,"original D13b config"))); values.add(new ConfigResolver.Override("cf.execution.sut",ComposedCarriersPlugin.ID,"D13b")); values.add(new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D13b")); return List.copyOf(values); }
    public static void main(String[] args) throws Exception {
        Path scenario=null,out=null;int workers=1;for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){case "--scenario" -> scenario=Path.of(args[i+1]);case "--out" -> out=Path.of(args[i+1]);case "--workers" -> workers=Integer.parseInt(args[i+1]);default -> throw new IllegalArgumentException("unknown option "+args[i]);}}if(scenario==null||out==null)throw new IllegalArgumentException("--scenario and --out required");
        var session=Session.resolve(null,Map.of(),settings(workers));var budget=MemoryBudget.of(session.configuration().config());try(var owned=budget.reserve(8L*1024*1024,"D13b full intermediate certificates/observed bank/analysis/XML")) {
            if(owned.bytes()!=8L*1024*1024)throw new IllegalStateException("invalid D13b report reservation");var pre=precheck(scenario,session);var bank=new TreeMap<String,Map<String,String>>();long generated,excluded,samples;
            try(var prepared=session.prepareRun(scenario)){var run=prepared.handle();var summary=run.await();if(summary.state()!=RunState.COMPLETED)throw new IllegalStateException("D13b run failed: "+summary.diagnostics());generated=summary.generated().longValueExact();excluded=summary.excluded().longValueExact();samples=summary.samples();for(var o:run.finals()){var f=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D13b observation: "+o.outcome()+": "+o.failureMessage())).attributes();if(!o.outcome().name().equals(f.get("verdict"))||bank.putIfAbsent(f.get("case_id"),f)!=null)throw new IllegalArgumentException("conflicting/duplicate D13b observations");}}
            var analysis=ComposedCarriersAnalysis.analyse(bank.values().stream().map(ComposedCarriersAnalysis.Observed::new).toList());var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<composed-carriers-report schema=\"d13b.analysis/v1\" observations=\"").append(bank.size()).append("\" generated=\"").append(generated).append("\" excluded=\"").append(excluded).append("\" samples=\"").append(samples).append("\">\n");
            xml.append("  <construction>").append(text(ComposedCarriers.json(pre.construction()))).append("</construction>\n  <stage-counts>").append(text(ComposedCarriers.json(pre.stages()))).append("</stage-counts>\n  <generation-diagnostics>").append(text(ComposedCarriers.json(pre.diagnostics()))).append("</generation-diagnostics>\n  <generation-settings>").append(text(ComposedCarriers.json(ComposedCarriersPlugin.SETTINGS))).append("</generation-settings>\n");
            for(var row:bank.entrySet()){xml.append("  <observation id=\"").append(Xml.escape(row.getKey())).append("\">\n");for(var f:new TreeMap<>(row.getValue()).entrySet())xml.append("    <field name=\"").append(f.getKey()).append("\">").append(text(f.getValue())).append("</field>\n");xml.append("  </observation>\n");}xml.append("  <analysis>").append(text(ComposedCarriers.json(analysis))).append("</analysis>\n</composed-carriers-report>\n");Files.writeString(out,xml,StandardCharsets.UTF_8);System.out.println("D13b: "+generated+" raw, "+excluded+" excluded, "+samples+" observed; outcomes "+analysis.get("totals"));
        }
    }
}
