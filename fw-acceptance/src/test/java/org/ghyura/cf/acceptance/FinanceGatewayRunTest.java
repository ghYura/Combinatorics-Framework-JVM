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

import org.ghyura.cf.model.Json;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class FinanceGatewayRunTest {
    private static Path scenario(Path directory) throws Exception {
        Path path=directory.resolve("finance-gateway.toml");
        try(var in=FinanceGatewayRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/finance/gateway/spec.toml")) {
            assertNotNull(in);Files.write(path,in.readAllBytes());
        }
        return path;
    }
    private static org.ghyura.cf.config.EffectiveConfig config(int workers) {
        return ConfigResolver.resolve(null,Map.of(),List.of(
            new ConfigResolver.Override("cf.execution.sut","finance-stack-gateway","local source gateway"),
            new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"explicit addon"),
            new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"worker invariance"),
            new ConfigResolver.Override("cf.generation.distinctTables","true","control profile"),
            new ConfigResolver.Override("cf.assembly.distinct","true","control profile")));
    }
    @Test void hmacAuthenticationReviewAndErrorProjectionsMatchIndependentPredictionsAcrossWorkers(@TempDir Path work) throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var doc=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/gateway-native-golden.xml").toFile()).getDocumentElement();
        var expected=(Map<?,?>)Json.parse(doc.getElementsByTagName("predictions").item(0).getTextContent(),"independent gateway");
        assertEquals(Set.of("signatures","verification","reviews","errors"),expected.keySet());
        Map<String,Object> serial=null;
        for(int workers:List.of(1,4))try(var run=RunService.prepare(ScenarioLoader.load(scenario(work)).spec(),config(workers))) {
            var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());
            assertEquals(1,summary.samples());assertTrue(summary.reconciliation().consistent());
            var observation=run.finals().getFirst();assertEquals("PASS",observation.outcome().name(),observation.failureMessage());
            var domain=observation.domain().orElseThrow();assertEquals(6,domain.attributes().size());assertEquals("0",domain.attributes().get("FW_VAR"));
            assertEquals(Map.of("scenario","source_gateway_security"),Json.parse(domain.attributes().get("request"),"request"));
            for(var entry:expected.entrySet())assertEquals(entry.getValue(),Json.parse(domain.attributes().get(entry.getKey()),"observed "+entry.getKey()));
            assertEquals(Map.of("hmac_vectors",72.0,"auth_checks",13.0,"review_cases",11.0,"error_cases",5.0),domain.metrics());
            var actual=Map.<String,Object>of("candidate",observation.key().candidateId().hex(),"fields",domain.attributes(),"metrics",domain.metrics(),"outcome",observation.outcome());
            if(serial==null)serial=actual;else assertEquals(serial,actual);
        }
    }
    @Test void unsupportedGatewayProfileNeverProducesSuccessfulDomainData(@TempDir Path work) throws Exception {
        var path=scenario(work);Files.writeString(path,Files.readString(path).replace("source_gateway_security","unsupported"));
        try(var run=RunService.prepare(ScenarioLoader.load(path).spec(),config(1))) {
            assertEquals(RunState.COMPLETED,run.await().state());assertEquals(1,run.finals().size());
            var observed=run.finals().getFirst();assertEquals("BROKEN",observed.outcome().name());assertTrue(observed.domain().isEmpty());
        }
    }
}
