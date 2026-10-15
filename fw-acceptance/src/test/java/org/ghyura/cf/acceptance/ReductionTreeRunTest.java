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
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReductionTreeRunTest {
    @Test void theUnchangedOriginalScenarioRunsAll168CasesWithIdenticalSerialAndParallelObservations(@TempDir Path dir) throws Exception {
        Path spec=dir.resolve("spec.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d7/spec.toml")) {assertNotNull(in);Files.write(spec,in.readAllBytes());}
        var scenario=ScenarioLoader.load(spec).spec();Map<String,Object> first=null;
        for(int workers:List.of(1,4)) {
            var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","demo-d7-reduction-trees","test"),
                    new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test")));
            try(var run=RunService.prepare(scenario,config)) {
                var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());
                assertEquals(BigInteger.valueOf(168),s.generated());assertEquals(BigInteger.ZERO,s.excluded());
                assertEquals(168,s.samples());assertEquals(156,s.count(Outcome.PASS));assertEquals(12,s.count(Outcome.DOMAIN_FAIL));
                assertEquals(0,s.count(Outcome.BROKEN));assertEquals(Map.of("PASS",156L,"DOMAIN_FAIL",12L),s.verdictsByName());
                Map<String,Object> observations=new LinkedHashMap<>();for(var o:run.finals())observations.put(o.key().candidateId().hex(),o.domain());
                if(first==null)first=observations;else assertEquals(first,observations);
            }
        }
    }
}
