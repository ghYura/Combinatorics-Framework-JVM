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

class FinanceClearingRunTest {
    private static Path scenario(Path directory) throws Exception {
        Path path=directory.resolve("finance-clearing.toml");
        try (var in=FinanceClearingRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/finance/clearing/spec.toml")) {
            assertNotNull(in);Files.write(path,in.readAllBytes());
        }
        return path;
    }
    private static org.ghyura.cf.config.EffectiveConfig config(int workers) {
        return ConfigResolver.resolve(null,Map.of(),List.of(
                new ConfigResolver.Override("cf.execution.sut","finance-stack-clearing","local coordinator"),
                new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"explicit addon"),
                new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"worker invariance"),
                new ConfigResolver.Override("cf.generation.distinctTables","true","control profile"),
                new ConfigResolver.Override("cf.assembly.distinct","true","control profile")));
    }
    @Test void all192ClearingScriptsKeepCompleteStateErrorsEventsAndBalancesAcrossWorkers(@TempDir Path work) throws Exception {
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var rows=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/clearing-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");
        var expected=new ArrayList<Map<?,?>>();for(int i=0;i<rows.getLength();i++)expected.add((Map<?,?>)Json.parse(rows.item(i).getTextContent(),"golden"));
        Map<String,Object> serial=null;
        for(int workers:List.of(1,4)) {
            var actual=new TreeMap<String,Object>();var seen=new HashSet<Integer>();
            try(var run=RunService.prepare(ScenarioLoader.load(scenario(work)).spec(),config(workers))) {
                var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());
                assertEquals(192,summary.samples());assertTrue(summary.reconciliation().consistent());
                for(var observation:run.finals()) {
                    assertEquals("PASS",observation.outcome().name(),observation.failureMessage());var domain=observation.domain().orElseThrow();
                    var request=(Map<?,?>)Json.parse(domain.attributes().get("request"),"request");int index=-1;
                    for(int i=0;i<expected.size();i++)if(Objects.equals(request,expected.get(i).get("request"))) { index=i;break; }
                    assertTrue(index>=0);assertTrue(seen.add(index));assertEquals("0",domain.attributes().get("FW_VAR"));
                    assertEquals(8,domain.attributes().size());assertEquals(4,domain.metrics().size());
                    var gold=(Map<?,?>)expected.get(index).get("expected");
                    for(var entry:gold.entrySet())assertEquals(entry.getValue(),Json.parse(domain.attributes().get(entry.getKey()),"observed source "+entry.getKey()),"script "+index+":"+entry.getKey());
                    assertEquals((double)((List<?>)gold.get("results")).size(),domain.metrics().get("operations"));
                    assertEquals((double)((List<?>)gold.get("errors")).size(),domain.metrics().get("error_count"));
                    var transfers=(List<?>)gold.get("transfers");
                    for(String status:List.of("SETTLED","EXPIRED"))assertEquals((double)transfers.stream().filter(v->((Map<?,?>)v).get("status").equals(status)).count(),domain.metrics().get(status.equals("SETTLED")?"settled_count":"expired_count"));
                    actual.put(observation.key().candidateId().hex(),Map.of("fields",domain.attributes(),"metrics",domain.metrics(),"outcome",observation.outcome()));
                }
            }
            assertEquals(192,seen.size());if(serial==null)serial=actual;else assertEquals(serial,actual);
        }
    }
    @Test void unsupportedClearingProfileNeverCreatesSuccessfulDomainData(@TempDir Path work) throws Exception {
        Path path=scenario(work);String original=Files.readString(path);String prefix="{\"amount\":";assertTrue(original.contains(prefix));
        Files.writeString(path,original.replaceFirst(java.util.regex.Pattern.quote(prefix),"{\"unsupported\":1,\"amount\":"));int broken=0;
        try(var run=RunService.prepare(ScenarioLoader.load(path).spec(),config(1))) {
            assertEquals(RunState.COMPLETED,run.await().state());
            for(var observation:run.finals())if(observation.outcome().name().equals("BROKEN")) { broken++;assertTrue(observation.domain().isEmpty()); }
            else assertEquals("PASS",observation.outcome().name());
        }
        assertEquals(1,broken);
    }
}
