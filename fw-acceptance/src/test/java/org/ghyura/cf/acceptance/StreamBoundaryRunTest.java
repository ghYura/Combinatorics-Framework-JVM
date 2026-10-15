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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class StreamBoundaryRunTest {
    @Test void theUnchangedOriginalScenarioFilters672CutsAndRuns480CasesIdenticallyInSerialAndParallel(@TempDir Path dir) throws Exception {
        Path spec=dir.resolve("spec.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d2/spec.toml")) {assertNotNull(in);Files.write(spec,in.readAllBytes());}
        var loaded=ScenarioLoader.load(spec);assertNotNull(loaded.authored());
        assertEquals(5,loaded.spec().constraints().rules().size());Map<String,Object> first=null;
        for(int workers:List.of(1,4)) {
            var config=ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","demo-d2-stream-boundaries","test"),
                    new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test")));
            try(var run=RunService.prepare(loaded.spec(),config)) {
                var s=run.await();assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString());
                assertEquals(BigInteger.valueOf(1152),s.generated());assertEquals(BigInteger.valueOf(672),s.excluded());
                assertEquals(480,s.samples());assertEquals(332,s.count(Outcome.PASS));assertEquals(148,s.count(Outcome.DOMAIN_FAIL));
                assertEquals(0,s.count(Outcome.BROKEN));assertEquals(Map.of("PASS",332L,"DOMAIN_FAIL",148L),s.verdictsByName());
                Map<String,Object> observations=new LinkedHashMap<>();Map<String,Long> classes=new HashMap<>();
                for(var o:run.finals()) {
                    observations.put(o.key().candidateId().hex(),o.domain());
                    classes.merge(o.domain().orElseThrow().attributes().get("failure_class"),1L,Long::sum);
                }
                assertEquals(480,observations.size());
                assertEquals(Map.of("none",332L,"unexpected_rejection",47L,"unexpected_acceptance",20L,"wrong_text",81L),classes);
                if(first==null)first=observations;else assertEquals(first,observations);
            }
        }
    }
}
