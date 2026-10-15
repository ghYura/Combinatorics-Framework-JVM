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
import org.ghyura.cf.fixtures.demos.NestedScopes;
import org.ghyura.cf.fixtures.demos.NestedScopesPlugin;
import org.ghyura.cf.fixtures.demos.NestedScopesAnalysis;
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
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.SeqRow;

/** Complete D5 B construction and observations through shared native services. */
public final class NestedScopesReport {
    private static String text(String value) { return Xml.escape(value).replace("&quot;","\"").replace("&apos;","'"); }
    private record Precheck(Map<String,Object> construction,Map<String,Object> stages,String phase,Map<String,Object> probes,List<String> summaries) { }
    private static List<List<Integer>> rows(org.ghyura.cf.model.row.RowSource source,boolean sort) { var out=new ArrayList<List<Integer>>();try(var cursor=source.open(Cancellation.NONE)){while(cursor.hasNext())out.add(Arrays.stream(cursor.next().keys().labels()).boxed().toList());}if(sort)out.sort((a,b)->{for(int i=0;i<Math.min(a.size(),b.size());i++){int c=Integer.compare(a.get(i),b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());});return List.copyOf(out); }
    private static ScenarioSpec variant(ScenarioSpec spec,boolean subsetOnly) { var sequence=spec.sequence().stream().map(row->{var cells=new ArrayList<>(row.cells());if(subsetOnly&&cells.getFirst().text().equals("E2")&&cells.size()==5&&cells.getLast().text().equals("FW_Combi(size)"))cells.removeLast();if(!subsetOnly)for(int i=0;i<cells.size();i++){var cell=cells.get(i);if(cell.text().startsWith("FW_Group")){var lines=cell.text().split("\n");if(lines.length!=4)throw new IllegalArgumentException("three original ordered rewrites required");cells.set(i,new LocatedText(String.join("\n",lines[0],lines[2],lines[1],lines[3]),cell.location()));}}return new SeqRow(row.ordinal(),cells);}).toList();return new ScenarioSpec(spec.source(),spec.sheets(),spec.controls(),true,sequence,spec.constraints()); }
    private static Map<String,Object> probes(ScenarioSpec spec,org.ghyura.cf.config.RunConfig config) { var budget=MemoryBudget.of(config);var out=new TreeMap<String,Object>();for(boolean subsets:List.of(true,false)){var variant=variant(spec,subsets);var plan=SeqPlanner.plan(variant,PlanOptions.of(config)).plan();try(var dictionary=KeyDictionary.build(variant,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,GenerationOptions.of(config),budget,Cancellation.NONE)){var table=rows(generated.result().sheet(subsets?"E2":"E1").orElseThrow().rows(),!subsets);out.put(subsets?"E2_after_subsets":"reversed_E1",table);if(!subsets){int tmp=dictionary.labelsOf(variant.sheets().stream().filter(s->s.name().equals("TMP")).findFirst().orElseThrow().key())[0];out.put("reversed_contains_TMP",table.stream().allMatch(row->row.contains(tmp)));}}}if(budget.reservedBytes()!=0)throw new IllegalStateException("D5 B probe retained reservations");return Map.copyOf(out); }
    private static Precheck precheck(Path scenario,Session session)throws Exception {
        var spec=ScenarioLoader.load(scenario).spec();var config=session.configuration().config();var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan();var budget=MemoryBudget.of(config);var options=GenerationOptions.of(config);var construction=new TreeMap<String,Object>();Map<String,Object> stages;String phase;List<String> summaries;
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            phase=NestedScopesPlugin.phase(assembly.columnSheets());var codes=new TreeMap<String,Object>();for(var sheet:spec.sheets())codes.put(sheet.name(),Arrays.stream(dictionary.labelsOf(sheet.key())).boxed().toList());var tables=new TreeMap<String,Object>();for(String name:List.of("E1","E2","E3","JZIP","JCAT","ROOT","SEAL","BUNDLE"))generated.result().sheet(name).ifPresent(s->tables.put(name,rows(s.rows(),true)));var flags=new TreeMap<String,Object>();var braces=new TreeMap<String,Object>();var rewrites=new ArrayList<Map<String,Object>>();for(var sheet:plan.sheets()){flags.put(sheet.name(),Map.of("role",sheet.role().name(),"reuse",sheet.reuse(),"reuse_table_only",sheet.reuseTableOnly()));for(var directive:sheet.directives()){if(directive instanceof Directive.Joiner j)braces.put(sheet.name(),NestedScopes.fields("start",j.start(),"first",j.first().sheet(),"first_kind",j.first().kind().name(),"relation",j.relation(),"second",j.second().sheet(),"second_kind",j.second().kind().name(),"end",j.end(),"multiplicity",j.multiplicity().token()));if(directive instanceof Directive.Group group)for(var rewrite:group.replacements()){int key=spec.sheets().stream().filter(s->s.name().equals(rewrite.sheetReference())).findFirst().orElseThrow().key();rewrites.add(Map.of("pattern",rewrite.pattern(),"sheet",rewrite.sheetReference(),"replacement",rewrite.prefix()+dictionary.labelsOf(key)[0]+rewrite.suffix()));}}}construction.putAll(NestedScopes.fields("predicted_codes",codes,"data_sheets",spec.sheetCount(),"structural_columns",assembly.columnSheets(),"rule_count",assembly.rules().size(),"virtual_sheets",plan.virtualSheetCount(),"tables",tables,"flags",flags,"braces",braces,"rewrites",rewrites));var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(100));if(!count.exact())throw new IllegalStateException("exact D5 B population required");stages=Map.of("raw",count.generated(),"early_excluded",count.removedEarly(),"late_excluded",count.removedLate(),"unselected",count.unselected(),"executable",count.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());summaries=generated.result().diagnostics().stream().filter(d->d.code().name().equals("GEN_REPLACEMENT_SUMMARY")).map(d->d.message()).toList();
        }if(budget.reservedBytes()!=0)throw new IllegalStateException("D5 B precheck retained reservations");return new Precheck(construction,stages,phase,probes(spec,config),summaries);
    }
    public static void main(String[] args)throws Exception {
        Path scenario=null,out=null;String workers="1";for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){case "--scenario"->scenario=Path.of(args[i+1]);case "--out"->out=Path.of(args[i+1]);case "--workers"->workers=args[i+1];default->throw new IllegalArgumentException("unknown option");}}if(scenario==null||out==null)throw new IllegalArgumentException("scenario/out required");var overrides=new ArrayList<ConfigResolver.Override>();overrides.add(new ConfigResolver.Override("cf.execution.sut",NestedScopesPlugin.ID,"D5 B report"));overrides.add(new ConfigResolver.Override("cf.execution.workers",workers,"D5 B report"));for(var e:Map.of("cf.generation.distinctTables","true","cf.generation.replace.pattern","warn","cf.generation.replace.unmatched","ignore","cf.generation.replace.unparseable","warn","cf.generation.replace.identity","allow","cf.generation.replace.diagnostics","summary").entrySet())overrides.add(new ConfigResolver.Override(e.getKey(),e.getValue(),"original D5 B applicable Core settings"));var session=Session.resolve(null,Map.of(),overrides);var budget=MemoryBudget.of(session.configuration().config());
        try(var owned=budget.reserve(16L*1024*1024,"D5 B native construction/probes, retained tree records/trace bank, analysis and streamedXML")){if(owned.bytes()!=16L*1024*1024)throw new IllegalStateException("invalid report reservation");var pre=precheck(scenario,session);var bank=new TreeMap<String,Map<String,String>>();long generated,excluded,samples;try(var prepared=session.prepareRun(scenario)){var run=prepared.handle();var summary=run.await();if(summary.state()!=RunState.COMPLETED)throw new IllegalStateException("D5 B failed:"+summary.diagnostics());generated=summary.generated().longValueExact();excluded=summary.excluded().longValueExact();samples=summary.samples();for(var o:run.finals()){var f=o.domain().orElseThrow().attributes();if(!o.outcome().name().equals(f.get("verdict"))||bank.putIfAbsent(f.get("case_id"),f)!=null)throw new IllegalArgumentException("duplicate/conflicting D5 B observation");}}var analysis=NestedScopesAnalysis.analyse(pre.phase(),bank.values().stream().map(NestedScopesAnalysis.Observed::new).toList());try(var xml=Files.newBufferedWriter(out,StandardCharsets.UTF_8)){xml.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<nested-scopes-report phase=\""+pre.phase()+"\" observations=\""+bank.size()+"\" generated=\""+generated+"\" excluded=\""+excluded+"\" samples=\""+samples+"\">\n");for(var section:List.of(Map.entry("construction",pre.construction()),Map.entry("stage-counts",pre.stages()),Map.entry("probes",pre.probes()),Map.entry("replacement-summary",pre.summaries()),Map.entry("analysis",analysis)))xml.write("  <"+section.getKey()+">"+text(NestedScopes.json(section.getValue()))+"</"+section.getKey()+">\n");for(var row:bank.entrySet()){xml.write("  <observation id=\""+Xml.escape(row.getKey())+"\">\n");for(var f:new TreeMap<>(row.getValue()).entrySet())xml.write("    <field name=\""+f.getKey()+"\">"+text(f.getValue())+"</field>\n");xml.write("  </observation>\n");}xml.write("</nested-scopes-report>\n");}System.out.println("D5 "+pre.phase()+": "+generated+"raw/"+samples+"observed;"+analysis.get("totals"));}
    }
}
