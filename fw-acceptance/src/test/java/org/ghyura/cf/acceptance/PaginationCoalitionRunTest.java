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

import org.ghyura.cf.algebra.LexicographicPermutations;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.fixtures.demos.CoalitionAllocation;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PaginationCoalitionRunTest {
    private Path original(Path dir,String tag) throws Exception {
        Path spec=dir.resolve("spec.toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/"+tag+"/spec.toml")){assertNotNull(in);Files.write(spec,in.readAllBytes());}return spec;
    }
    @Test void theOriginalPaginationScenarioRuns450CasesWithIdenticalSerialAndParallelTraces(@TempDir Path dir) throws Exception {
        var scenario=ScenarioLoader.load(original(dir,"d8")).spec();Map<String,Object> first=null;
        for(int workers:List.of(1,4)) {
            var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","demo-d8-ties-pagination","test"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test")));
            try(var run=RunService.prepare(scenario,config)) {
                var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());assertEquals(BigInteger.valueOf(450),s.generated());assertEquals(BigInteger.ZERO,s.excluded());
                assertEquals(450,s.samples());assertEquals(330,s.count(Outcome.PASS));assertEquals(120,s.count(Outcome.DOMAIN_FAIL));assertEquals(0,s.count(Outcome.BROKEN));
                Map<String,Object> traces=new LinkedHashMap<>();for(var o:run.finals())traces.put(o.key().candidateId().hex(),o.domain());
                assertEquals(450,traces.size());if(first==null)first=traces;else assertEquals(first,traces);
            }
        }
    }
    @Test void all64OriginalCoalitionIdentitiesFeedExactAllocationFromObservedSerialAndParallelValues(@TempDir Path dir) throws Exception {
        var scenario=ScenarioLoader.load(original(dir,"d11")).spec();Map<String,Object> first=null;CoalitionAllocation.Allocation firstAllocation=null;
        for(int workers:List.of(1,4)) {
            var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","demo-d11-coalition-value","test"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test"),new ConfigResolver.Override("cf.generation.distinctTables","true","original D11 Core settings")));
            try(var run=RunService.prepare(scenario,config)) {
                var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());assertEquals(BigInteger.valueOf(64),s.generated());assertEquals(BigInteger.ZERO,s.excluded());assertEquals(64,s.samples());assertEquals(64,s.count(Outcome.PASS));
                Map<String,Object> traces=new LinkedHashMap<>();List<CoalitionAllocation.Observed> values=new ArrayList<>();
                for(var o:run.finals()) {
                    traces.put(o.key().candidateId().hex(),o.domain());var at=o.domain().orElseThrow().attributes();
                    values.add(new CoalitionAllocation.Observed(at.get("case"),at.get("mask"),o.outcome().name(),new BigInteger(at.get("value"))));
                }
                assertEquals(64,traces.size());assertTrue(values.stream().anyMatch(o->o.mask().equals("000000")&&o.value().equals(BigInteger.ZERO)));
                var orders=new LexicographicPermutations(6);var allocation=CoalitionAllocation.allocate(values,java.util.stream.IntStream.range(0,orders.size().intValueExact()).mapToObj(i->orders.get(BigInteger.valueOf(i))).iterator());
                assertEquals(Map.of("A",CoalitionAllocation.Fraction.of(29,3),"B",CoalitionAllocation.Fraction.of(29,3),"C",CoalitionAllocation.Fraction.of(13,3),"D",CoalitionAllocation.Fraction.of(13,3),"E",CoalitionAllocation.Fraction.of(4,1),"F",CoalitionAllocation.Fraction.of(0,1)),allocation.shapley());
                assertTrue(allocation.checks().values().stream().allMatch(Boolean::booleanValue));
                if(first==null){first=traces;firstAllocation=allocation;}else{assertEquals(first,traces);assertEquals(firstAllocation,allocation);}
            }
        }
    }
}
