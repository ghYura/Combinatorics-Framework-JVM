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
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.fixtures.proposal.LocalProposalScenarios;
import org.ghyura.cf.fixtures.proposal.LocalPromptGuardPlugin;
import org.ghyura.cf.input.proposal.StubProposalProvider;
import org.ghyura.cf.input.proposal.TaskFreezer;
import org.ghyura.cf.input.proposal.TaskSnapshot;
import org.ghyura.cf.model.proposal.ProposalProvider;
import org.ghyura.cf.model.proposal.FrozenTask;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.generation.Assembly;
import org.ghyura.cf.generation.AssemblyOptions;
import org.ghyura.cf.generation.Generation;
import org.ghyura.cf.generation.GenerationOptions;
import org.ghyura.cf.generation.RenderOptions;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.spi.Cancellation;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.math.BigInteger;

/** Full original quote/parser/guard tasks:freeze once,replay saved values,and run the native framework. */
public final class LocalProposalReport {
    private static String xml(String value){return Xml.escape(value).replace("&quot;","\"").replace("&apos;","'");}
    public static void main(String[] args)throws Exception {
        String kind=null,workers="1";Path out=null,work=null,addon=null;
        for(int i=0;i<args.length;i+=2){if(i+1==args.length)throw new IllegalArgumentException("option value required");switch(args[i]){
            case "--task"->kind=args[i+1];case "--out"->out=Path.of(args[i+1]);case "--work"->work=Path.of(args[i+1]);
            case "--addon"->addon=Path.of(args[i+1]);case "--workers"->workers=args[i+1];default->throw new IllegalArgumentException("unknown option");}}
        if(kind==null||out==null||work==null||addon==null)throw new IllegalArgumentException("task,out,work,addon required");
        Files.createDirectories(work);Path saved=work.resolve("frozen-task.json"),scenario=work.resolve("local-task.json");
        var calls=new AtomicInteger();FrozenTask frozen;
        if(Files.exists(saved))frozen=TaskSnapshot.load(saved,1024*1024);
        else {
            var stub=new StubProposalProvider();ProposalProvider counted=new ProposalProvider(){
                public Identity identity(){return stub.identity();}
                public List<String> propose(Request request){calls.incrementAndGet();return stub.propose(request);}
            };
            frozen=TaskFreezer.freeze(LocalProposalScenarios.of(kind,0),counted,1024*1024);TaskSnapshot.save(frozen,saved,1024*1024);
        }
        if(!frozen.task().id().equals(kind))throw new IllegalArgumentException("saved task doesnotmatchrequestedtask");
        TaskSnapshot.requireWithin(frozen,1024*1024);AtomicFiles.write(scenario,Canonical.utf8(frozen.specJson()));
        var session=Session.resolve(null,Map.of(),List.of(
                new ConfigResolver.Override("cf.execution.sut",kind.equals("guard")?LocalPromptGuardPlugin.ID:RunConfig.GENERATED_JAVA,"local task"),
                new ConfigResolver.Override("cf.execution.classpath",addon.toAbsolutePath().toString(),"local task"),
                new ConfigResolver.Override("cf.execution.workers",workers,"local task"),
                new ConfigResolver.Override("cf.run.id",frozen.runId("local-task").value(),"frozen task provenance"),
                new ConfigResolver.Override("cf.generation.distinctTables","true","original Core_trunk/fw.properties line205"),
                new ConfigResolver.Override("cf.assembly.distinct","true","original Core_trunk/fw.properties line204")));
        var budget=MemoryBudget.of(session.configuration().config());
        try(var retained=budget.reserve(16L*1024*1024,"local task recipes,observations and streaming XML")) {
            if(retained.bytes()!=16L*1024*1024)throw new IllegalStateException("invalid task report reservation");
            var spec=ScenarioLoader.load(scenario).spec();var config=session.configuration().config();
            var plan=SeqPlanner.plan(spec,PlanOptions.of(config)).plan();var options=GenerationOptions.of(config);
            var recipes=new TreeMap<String,Object>();Map<String,Object> stages;
            try(var dictionary=KeyDictionary.build(spec,(int)plan.virtualSheetCount(),config.keyEncodingPolicy(),budget);
                var generated=Generation.run(plan,dictionary,options,budget,Cancellation.NONE);
                var assembly=Assembly.of(plan,generated.result(),dictionary,options,AssemblyOptions.of(config),RenderOptions.of(config),config.randomSeed(),budget,Cancellation.NONE)) {
                var counts=assembly.count(Cancellation.NONE,BigInteger.valueOf(2000));if(!counts.exact())throw new IllegalStateException("exact task population required");
                stages=Map.of("raw",counts.generated(),"early_excluded",counts.removedEarly(),"late_excluded",counts.removedLate(),
                        "unselected",counts.unselected(),"executable",counts.executable(),"mandatory_rows",assembly.mandatoryRows(),"optional_multiplier",assembly.perMandatoryRow());
                var iterator=assembly.candidates(Cancellation.NONE);
                while(iterator.hasNext()) {
                    var candidate=iterator.next();var values=new TreeMap<String,Object>();var codes=new TreeMap<String,Object>();
                    for(int c=0;c<assembly.columnSheets().size();c++) {
                        var labels=candidate.labels(c);String sheet=assembly.columnSheets().get(c);
                        codes.put(sheet,labels==null?List.of():Arrays.stream(labels).boxed().toList());
                        values.put(sheet,labels==null?List.of():Arrays.stream(labels).mapToObj(label->dictionary.value(label).text()).toList());
                    }
                    recipes.put(candidate.id().toString(),Map.of("ordinal",candidate.ordinal(),"display",candidate.display(),"optional_size",candidate.size(),"values",values,"codes",codes));
                }
            }
            try(var prepared=session.prepareRun(scenario)) {
                var run=prepared.handle();var summary=run.await();if(summary.state()!=RunState.COMPLETED)throw new IllegalStateException("task run failed: "+summary.diagnostics());
                try(var writer=Files.newBufferedWriter(out,StandardCharsets.UTF_8)) {
                    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<local-proposal-report task=\""+Xml.escape(kind)+"\" observations=\""+run.finals().size()+"\" provider-calls=\""+calls.get()+"\" frozen-digest=\""+frozen.digest()+"\" run-id=\""+summary.run()+"\">\n");
                    writer.write("<stage-counts>"+xml(Json.write(stages))+"</stage-counts>\n");
                    for(var observation:run.finals()) {
                        String id=observation.key().candidateId().toString();if(!recipes.containsKey(id))throw new IllegalStateException("unknown native task coordinate");
                        writer.write("<observation candidate=\""+id+"\" outcome=\""+observation.outcome()+"\">\n");
                        writer.write("<recipe>"+xml(Json.write(recipes.get(id)))+"</recipe>\n<stdout>"+xml(observation.stdout())+"</stdout>\n");
                        writer.write("<fields>"+xml(Json.write(observation.domain().map(d->d.attributes()).orElse(Map.of())))+"</fields>\n</observation>\n");
                        if(!observation.outcome().isDomainVerdict())throw new IllegalStateException("non-domain task observation: "+observation.failure()+" "+observation.failureMessage());
                    }
                    writer.write("</local-proposal-report>\n");
                }
                System.out.println(kind+": "+summary.samples()+" samples;"+summary.outcomes()+";provider calls="+calls.get());
            }
        }
        if(budget.reservedBytes()!=0)throw new IllegalStateException("task report retained reservations");
    }
}
