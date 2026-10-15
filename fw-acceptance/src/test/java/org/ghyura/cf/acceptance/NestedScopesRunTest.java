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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.fixtures.demos.NestedScopes;
import org.ghyura.cf.fixtures.demos.NestedScopesPlugin;
import org.ghyura.cf.fixtures.demos.NestedScopesAnalysis;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.desktop.session.Session;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class NestedScopesRunTest {
    private record Precheck(Map<String,Object> construction,Map<String,Object> stages,String phase,Map<String,Object> probes,List<String> summaries) { }
    private Element golden()throws Exception { var f=DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);return f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d5b-golden.xml").toFile()).getDocumentElement(); }
    private Path original(Path dir,String phase)throws Exception { var p=dir.resolve(phase+".toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d5b/"+phase+"/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p; }
    private org.ghyura.cf.config.EffectiveConfig config(int workers) { var overrides=new ArrayList<ConfigResolver.Override>();overrides.add(new ConfigResolver.Override("cf.execution.sut",NestedScopesPlugin.ID,"D5 B"));overrides.add(new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"D5 B"));for(var e:Map.of("cf.generation.distinctTables","true","cf.generation.replace.pattern","warn","cf.generation.replace.unmatched","ignore","cf.generation.replace.unparseable","warn","cf.generation.replace.identity","allow","cf.generation.replace.diagnostics","summary").entrySet())overrides.add(new ConfigResolver.Override(e.getKey(),e.getValue(),"original applicable Core settings"));return ConfigResolver.resolve(null,Map.of(),overrides); }
    private Map<String,Map<String,String>> run(Path path,String phase,int workers)throws Exception { try(var run=RunService.prepare(ScenarioLoader.load(path).spec(),config(workers))){var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());assertEquals(BigInteger.valueOf(phase.equals("B1")?24:3),summary.generated());assertEquals(BigInteger.ZERO,summary.excluded());assertEquals(phase.equals("B1")?24:3,summary.samples());assertEquals(phase.equals("B1")?8:1,summary.count(Outcome.PASS));assertEquals(phase.equals("B1")?16:2,summary.count(Outcome.DOMAIN_FAIL));var bank=new TreeMap<String,Map<String,String>>();for(var row:run.finals()){assertTrue(row.outcome()==Outcome.PASS||row.outcome()==Outcome.DOMAIN_FAIL);var f=row.domain().orElseThrow().attributes();assertEquals(row.outcome().name(),f.get("verdict"));assertNull(bank.put(f.get("case_id"),f));}return bank;} }
    @Test void bothOriginalCampaignsMatchEveryFieldSerialParallelAndFreshBundleMembers(@TempDir Path dir)throws Exception {
        var g=golden();Map<String,Map<String,String>> b1=null;for(String phase:List.of("B1","B2")){var path=original(dir,phase);var bank=run(path,phase,1);assertEquals(bank,run(path,phase,4));assertEquals(NestedScopesAnalysis.expectedIds(phase),bank.keySet());var cases=g.getElementsByTagName("case");for(int i=0;i<cases.getLength();i++){var c=(Element)cases.item(i);if(!c.getAttribute("phase").equals(phase))continue;var actual=bank.get(c.getAttribute("id"));assertNotNull(actual);var fields=c.getElementsByTagName("field");assertEquals(fields.getLength(),actual.size());for(int k=0;k<fields.getLength();k++){var f=(Element)fields.item(k);String key=f.getAttribute("name");assertEquals(f.getAttribute("kind").equals("json")?Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?Json.parse(actual.get(key),"actual"):actual.get(key),c.getAttribute("id")+" "+key);}}var analyses=g.getElementsByTagName("analyse");for(int i=0;i<analyses.getLength();i++)if(((Element)analyses.item(i)).getAttribute("phase").equals(phase))assertEquals(Json.parse(analyses.item(i).getTextContent(),"golden"),Json.parse(NestedScopes.json(NestedScopesAnalysis.analyse(phase,bank.values().stream().map(NestedScopesAnalysis.Observed::new).toList())),"actual"));if(phase.equals("B1"))b1=bank;else for(String p:NestedScopes.POLICIES){var results=NestedScopesAnalysis.object(Json.parse(bank.get("B2|"+p+"|BUNDLE=all8").get("results"),"actual"));for(var e:results.entrySet())assertEquals(Json.parse(b1.get("B1|"+p+"|"+e.getKey()).get("result"),"single tree"),e.getValue());}}
    }
    @Test void nativeRewritesSubsetsZipCatAndGroupedNestedReferencesMatchIndependentTables(@TempDir Path dir)throws Exception {
        var g=golden();for(String phase:List.of("B1","B2")){var settings=new ArrayList<ConfigResolver.Override>();for(var e:Map.of("cf.generation.distinctTables","true","cf.generation.replace.pattern","warn","cf.generation.replace.unmatched","ignore","cf.generation.replace.unparseable","warn","cf.generation.replace.identity","allow","cf.generation.replace.diagnostics","summary").entrySet())settings.add(new ConfigResolver.Override(e.getKey(),e.getValue(),"D5 B native settings"));var session=Session.resolve(null,Map.of(),settings);var pre=precheck(original(dir,phase),session);for(var tags:List.of(Map.entry("construction",pre.construction()),Map.entry("probe",pre.probes()))){var nodes=g.getElementsByTagName(tags.getKey());for(int i=0;i<nodes.getLength();i++)if(((Element)nodes.item(i)).getAttribute("phase").equals(phase))assertEquals(Json.parse(nodes.item(i).getTextContent(),"golden"),Json.parse(NestedScopes.json(tags.getValue()),"native"));}assertEquals(Json.parse(NestedScopes.json(Map.of("raw",phase.equals("B1")?24:3,"early_excluded",0,"late_excluded",0,"unselected",0,"executable",phase.equals("B1")?24:3,"mandatory_rows",phase.equals("B1")?24:3,"optional_multiplier",1)),"expected"),Json.parse(NestedScopes.json(pre.stages()),"native"));assertEquals(1,pre.summaries().size());assertTrue(pre.summaries().getFirst().contains("rows seen=2 rewritten=2 dropped=0"));}
    }
    private static List<List<Integer>> rows(org.ghyura.cf.model.row.RowSource source,boolean sort) { var out=new ArrayList<List<Integer>>();try(var cursor=source.open(Cancellation.NONE)){while(cursor.hasNext())out.add(Arrays.stream(cursor.next().keys().labels()).boxed().toList());}if(sort)out.sort((a,b)->{for(int i=0;i<Math.min(a.size(),b.size());i++){int c=Integer.compare(a.get(i),b.get(i));if(c!=0)return c;}return Integer.compare(a.size(),b.size());});return List.copyOf(out); }
    private static ScenarioSpec variant(ScenarioSpec spec,boolean subsetOnly) { var sequence=spec.sequence().stream().map(row->{var cells=new ArrayList<>(row.cells());if(subsetOnly&&cells.getFirst().text().equals("E2")&&cells.size()==5&&cells.getLast().text().equals("FW_Combi(size)"))cells.removeLast();if(!subsetOnly)for(int i=0;i<cells.size();i++){var cell=cells.get(i);if(cell.text().startsWith("FW_Group")){var lines=cell.text().split("\n");if(lines.length!=4)throw new IllegalArgumentException("three original ordered rewrites required");cells.set(i,new LocatedText(String.join("\n",lines[0],lines[2],lines[1],lines[3]),cell.location()));}}return new SeqRow(row.ordinal(),cells);}).toList();return new ScenarioSpec(spec.source(),spec.sheets(),spec.controls(),true,sequence,spec.constraints()); }
    private static Map<String,Object> probes(ScenarioSpec spec,org.ghyura.cf.config.RunConfig config) { var budget=MemoryBudget.of(config);var out=new TreeMap<String,Object>();for(boolean subsets:List.of(true,false)){var variant=variant(spec,subsets);var plan=SeqPlanner.plan(variant,PlanOptions.of(config)).plan();try(var dictionary=KeyDictionary.build(variant,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,GenerationOptions.of(config),budget,Cancellation.NONE)){var table=rows(generated.result().sheet(subsets?"E2":"E1").orElseThrow().rows(),!subsets);out.put(subsets?"E2_after_subsets":"reversed_E1",table);if(!subsets){int tmp=dictionary.labelsOf(variant.sheets().stream().filter(s->s.name().equals("TMP")).findFirst().orElseThrow().key())[0];out.put("reversed_contains_TMP",table.stream().allMatch(row->row.contains(tmp)));}}}if(budget.reservedBytes()!=0)throw new IllegalStateException("D5 B probe retained reservations");return Map.copyOf(out); }
    private static Precheck precheck(Path scenario,Session session)throws Exception {
        var spec=ScenarioLoader.load(scenario).spec();var config=session.configuration().config();var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan();var budget=MemoryBudget.of(config);var options=GenerationOptions.of(config);var construction=new TreeMap<String,Object>();Map<String,Object> stages;String phase;List<String> summaries;
        try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
            phase=NestedScopesPlugin.phase(assembly.columnSheets());var codes=new TreeMap<String,Object>();for(var sheet:spec.sheets())codes.put(sheet.name(),Arrays.stream(dictionary.labelsOf(sheet.key())).boxed().toList());var tables=new TreeMap<String,Object>();for(String name:List.of("E1","E2","E3","JZIP","JCAT","ROOT","SEAL","BUNDLE"))generated.result().sheet(name).ifPresent(s->tables.put(name,rows(s.rows(),true)));var flags=new TreeMap<String,Object>();var braces=new TreeMap<String,Object>();var rewrites=new ArrayList<Map<String,Object>>();for(var sheet:plan.sheets()){flags.put(sheet.name(),Map.of("role",sheet.role().name(),"reuse",sheet.reuse(),"reuse_table_only",sheet.reuseTableOnly()));for(var directive:sheet.directives()){if(directive instanceof Directive.Joiner j)braces.put(sheet.name(),NestedScopes.fields("start",j.start(),"first",j.first().sheet(),"first_kind",j.first().kind().name(),"relation",j.relation(),"second",j.second().sheet(),"second_kind",j.second().kind().name(),"end",j.end(),"multiplicity",j.multiplicity().token()));if(directive instanceof Directive.Group group)for(var rewrite:group.replacements()){int key=spec.sheets().stream().filter(s->s.name().equals(rewrite.sheetReference())).findFirst().orElseThrow().key();rewrites.add(Map.of("pattern",rewrite.pattern(),"sheet",rewrite.sheetReference(),"replacement",rewrite.prefix()+dictionary.labelsOf(key)[0]+rewrite.suffix()));}}}construction.putAll(NestedScopes.fields("predicted_codes",codes,"data_sheets",spec.sheetCount(),"structural_columns",assembly.columnSheets(),"rule_count",assembly.rules().size(),"virtual_sheets",plan.virtualSheetCount(),"tables",tables,"flags",flags,"braces",braces,"rewrites",rewrites));var count=assembly.count(Cancellation.NONE,BigInteger.valueOf(100));if(!count.exact())throw new IllegalStateException("exact D5 B population required");stages=Map.of("raw",count.generated(),"early_excluded",count.removedEarly(),"late_excluded",count.removedLate(),"unselected",count.unselected(),"executable",count.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());summaries=generated.result().diagnostics().stream().filter(d->d.code().name().equals("GEN_REPLACEMENT_SUMMARY")).map(d->d.message()).toList();
        }if(budget.reservedBytes()!=0)throw new IllegalStateException("D5 B precheck retained reservations");return new Precheck(construction,stages,phase,probes(spec,config),summaries);
    }
}
