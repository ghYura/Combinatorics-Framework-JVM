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

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.fixtures.proposal.LocalProposalScenarios;
import org.ghyura.cf.fixtures.proposal.LocalPromptGuardPlugin;
import org.ghyura.cf.input.proposal.TaskFreezer;
import org.ghyura.cf.input.proposal.TaskSnapshot;
import org.ghyura.cf.input.proposal.StubProposalProvider;
import org.ghyura.cf.input.ScenarioInterpreter;
import org.ghyura.cf.input.FileResolver;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.proposal.ProposalProvider;
import org.ghyura.cf.model.proposal.FrozenTask;
import org.ghyura.cf.results.Checkpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LocalTaskCheckpointTest {
    private static EffectiveConfig configuration(FrozenTask task) {
        var compiled=TaskSnapshot.compile(task,1024*1024);
        var base=ConfigResolver.resolve(null,Map.of(),List.of(
                new ConfigResolver.Override("cf.execution.sut",LocalPromptGuardPlugin.ID,"task checkpoint"),
                new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"task checkpoint"),
                new ConfigResolver.Override("cf.execution.workers","1","task checkpoint"),
                new ConfigResolver.Override("cf.pipeline.ordinal.window","2","task checkpoint"),
                new ConfigResolver.Override("cf.run.id",task.runId("task-checkpoint").value(),"frozen task provenance"),
                new ConfigResolver.Override("cf.generation.distinctTables","true","originalCoreproperties"),
                new ConfigResolver.Override("cf.assembly.distinct","true","originalCoreproperties")));
        return ConfigResolver.withScenario(base,compiled.document().settings());
    }
    private static org.ghyura.cf.model.spec.ScenarioSpec scenario(FrozenTask task) {
        var compiled=TaskSnapshot.compile(task,1024*1024);
        return ScenarioInterpreter.interpret(compiled.workbook(),FileResolver.none()).spec().withConstraints(compiled.constraints());
    }
    private static Map<String,String> projection(List<Observation> observations) {
        var out=new TreeMap<String,String>();
        for(var o:observations)out.put(o.key().candidateId().hex(),o.outcome()+" "+o.domain().orElseThrow().attributes());
        return out;
    }
    @Test void pausedOriginalTaskResumesFromSavedProposalsAndRejectsChangedMetadataWithIdenticalValues(@TempDir Path directory)throws Exception {
        var calls=new AtomicInteger();var stub=new StubProposalProvider();
        ProposalProvider spy=new ProposalProvider(){
            public Identity identity(){return stub.identity();}
            public List<String> propose(Request request){calls.incrementAndGet();return stub.propose(request);}
        };
        var frozen=TaskFreezer.freeze(LocalProposalScenarios.of("guard",0),spy,1024*1024);
        assertEquals(3,calls.get());Path saved=directory.resolve("frozen.json");TaskSnapshot.save(frozen,saved,1024*1024);
        var spec=scenario(frozen);var config=configuration(frozen);List<Observation> expected;
        try(var full=RunService.prepare(spec,config)){assertEquals(RunState.COMPLETED,full.await().state());expected=full.finals();assertEquals(128,expected.size());}
        var written=new CopyOnWriteArrayList<Checkpoint>();
        try(var partial=RunService.prepare(spec,config)) {
            partial.checkpointTo("local-task.json",written::add);
            partial.observe(p->{if(p.committed()==5)partial.pause();});partial.start();
            long deadline=System.nanoTime()+10_000_000_000L;
            while(partial.state()!=RunState.PAUSED && System.nanoTime()<deadline)Thread.sleep(10);
            assertEquals(RunState.PAUSED,partial.state());partial.cancel();assertEquals(RunState.CANCELLED,partial.await().state());
        }
        var paused=written.getFirst();assertEquals("PAUSED",paused.state());assertTrue(paused.observations().size()>=5&&paused.observations().size()<128);
        var restored=TaskSnapshot.load(saved,1024*1024);assertEquals(3,calls.get());
        try(var resumed=RunService.resume(scenario(restored),configuration(restored),paused)) {
            var result=resumed.await();assertEquals(RunState.COMPLETED,result.state(),result.diagnostics().toString());
            assertEquals(projection(expected),projection(resumed.finals()));assertEquals(128,result.samples());
            assertEquals(paused.observations().size(),result.execution().restoredAttempts());assertTrue(result.reconciliation().consistent());
        }
        assertEquals(3,calls.get(),"resume must have no provider boundary");
        var changed=TaskFreezer.freeze(LocalProposalScenarios.of("guard",1),stub,1024*1024);
        assertEquals(frozen.responses(),changed.responses());assertEquals(spec.contentDigest(),scenario(changed).contentDigest());
        var error=assertThrows(FrameworkException.class,()->RunService.resume(scenario(changed),configuration(changed),paused));
        assertEquals(ErrorCode.CHECKPOINT_MISMATCH,error.primary().code());
    }
}
