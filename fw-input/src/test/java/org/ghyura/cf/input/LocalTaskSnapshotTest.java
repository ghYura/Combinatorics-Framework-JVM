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

package org.ghyura.cf.input;

import org.ghyura.cf.input.proposal.StubProposalProvider;
import org.ghyura.cf.input.proposal.TaskFreezer;
import org.ghyura.cf.input.proposal.TaskSnapshot;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.proposal.TaskDefinition;
import org.ghyura.cf.model.proposal.ProposalProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LocalTaskSnapshotTest {
    private static final long ALLOWANCE=1024*1024;
    private static final SourceLocation SOURCE=SourceLocation.of("original-task.py",100,3);
    private static TaskDefinition task(long seed) {
        var quantity=new TaskDefinition.Slot("QTY","qty","FW_Combi(1)",List.of(),List.of(),true,
                new TaskDefinition.Proposed("qty","money fallback context",3,"qty = ",";\n"),SOURCE);
        var operations=new TaskDefinition.Slot("OPS","ops","FW_Combi(2)",List.of(),List.of("FW_Permut()","FW_Group"),true,
                new TaskDefinition.Fixed(List.of("A();\n","M();\n","S();\n")),SOURCE);
        return new TaskDefinition("task","1","Local task","discount context",seed,List.of(quantity,operations),
                List.of("mode=task"),List.of("bugs_found"),List.of(new TaskDefinition.CustomVerdict(2,"invariant broken")),SOURCE);
    }
    @Test void nativeReaderReceivesFrozenValuesGoalsFlagsAndOrderedExpertDirectiveRows() {
        var frozen=TaskFreezer.freeze(task(17),new StubProposalProvider(),ALLOWANCE);
        assertEquals(List.of("1","0","-1"),frozen.responses().getFirst());
        var compiled=TaskSnapshot.compile(frozen,ALLOWANCE);
        var spec=ScenarioInterpreter.interpret(compiled.workbook(),FileResolver.none()).spec().withConstraints(compiled.constraints());
        assertEquals(2,spec.sheets().size());
        assertEquals(List.of("qty = 1;\n","qty = 0;\n","qty = -1;\n"),spec.sheets().getFirst().cells().stream().map(c->c.text()).toList());
        assertTrue(compiled.document().settings().stream().anyMatch(s->s.value().contains("bugs_found")));
        assertEquals("mode=task",spec.controls().arguments().getFirst().text());
        var expert=spec.sequence().getLast();
        assertEquals(List.of("OPS","FW_Combi(2)","FW_Permut()","FW_Group"),expert.cells().stream().map(c->c.text()).toList());
        assertEquals(SOURCE,frozen.task().slots().getFirst().location());
        assertThrows(UnsupportedOperationException.class,()->frozen.responses().getFirst().set(0,"9"));
    }
    @Test void persistedReplayRetainsIdentityAndNeverCallsProviderAgain(@TempDir Path directory) throws Exception {
        var calls=new AtomicInteger();var stub=new StubProposalProvider();
        ProposalProvider spy=new ProposalProvider(){
            public Identity identity(){return stub.identity();}
            public List<String> propose(Request request){calls.incrementAndGet();return stub.propose(request);}
        };
        var frozen=TaskFreezer.freeze(task(17),spy,ALLOWANCE);assertEquals(1,calls.get());
        Path saved=directory.resolve("snapshot.json");TaskSnapshot.save(frozen,saved,ALLOWANCE);
        Path moved=directory.resolve("renamed.json");Files.move(saved,moved);
        var restored=TaskSnapshot.load(moved,ALLOWANCE);
        assertEquals(frozen,restored);assertEquals(frozen.digest(),restored.digest());assertEquals(frozen.specJson(),restored.specJson());
        assertEquals(frozen.runId("run"),restored.runId("run"));assertEquals(64,frozen.runId("run").value().length());
        assertEquals(1,calls.get());assertNotEquals(frozen.runId("run"),frozen.runId("other"));
        var changedSeed=TaskFreezer.freeze(task(18),stub,ALLOWANCE);
        assertEquals(frozen.responses(),changedSeed.responses());assertNotEquals(frozen.runId("run"),changedSeed.runId("run"));
        ProposalProvider changed=new ProposalProvider(){
            public Identity identity(){return new Identity("stub","changed-version",stub.identity().contentDigest());}
            public List<String> propose(Request request){return stub.propose(request);}
        };
        var changedProvider=TaskFreezer.freeze(task(17),changed,ALLOWANCE);
        assertEquals(frozen.responses(),changedProvider.responses());assertNotEquals(frozen.runId("run"),changedProvider.runId("run"));
    }
    @Test void corruptionUnknownSchemaAndBudgetRefusalsPreserveLastGoodSnapshot(@TempDir Path directory) throws Exception {
        var frozen=TaskFreezer.freeze(task(17),new StubProposalProvider(),ALLOWANCE);
        Path saved=directory.resolve("snapshot.json");TaskSnapshot.save(frozen,saved,ALLOWANCE);var original=Files.readAllBytes(saved);
        assertThrows(IllegalArgumentException.class,()->TaskSnapshot.save(frozen,saved,16));assertArrayEquals(original,Files.readAllBytes(saved));
        assertThrows(IllegalArgumentException.class,()->TaskSnapshot.decode(frozen.snapshotJson().replace("money fallback context","changed context"),ALLOWANCE));
        assertThrows(IllegalArgumentException.class,()->TaskSnapshot.decode(frozen.snapshotJson().replace("cf.local-task/v1","cf.local-task/v2"),ALLOWANCE));
        assertThrows(IllegalArgumentException.class,()->TaskSnapshot.decode(frozen.snapshotJson().replace("\"body\": {","\"body\": {\"unknown\":true,"),ALLOWANCE));
        assertThrows(IllegalArgumentException.class,()->TaskFreezer.freeze(task(17),new StubProposalProvider(),1024));
        var originalValues=new ArrayList<>(List.of("A();\n"));var fixed=new TaskDefinition.Fixed(originalValues);originalValues.set(0,"bad");
        assertEquals(List.of("A();\n"),fixed.values());
    }
}
