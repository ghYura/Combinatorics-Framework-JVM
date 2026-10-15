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

import org.ghyura.cf.campaign.*;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.fixtures.redteam.AttackDefenseMatrix;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AttackDefenseRunTest {
    static Path scenario(Path work)throws Exception{Path p=work.resolve("attack-defense.toml");try(var in=AttackDefenseRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/redteam/attack-defense/spec.toml")){assertNotNull(in);Files.write(p,in.readAllBytes());}return p;}
    static org.ghyura.cf.config.EffectiveConfig config(int workers){return ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut","local-attack-defense","source matrix"),new ConfigResolver.Override("cf.execution.classpath",Reactor.root().resolve("fw-sut-fixtures/target/classes").toString(),"explicit addon"),new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"worker invariance"),new ConfigResolver.Override("cf.generation.distinctTables","true","source profile"),new ConfigResolver.Override("cf.assembly.distinct","true","source profile")));}
    @Test void original10368PairsNativeMetricsCandidateIdsAndSourceFrontiersMatchAcrossWorkers(@TempDir Path work)throws Exception{
        var f=javax.xml.parsers.DocumentBuilderFactory.newInstance();f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var root=f.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/redteam/attack-defense-golden.xml").toFile()).getDocumentElement();var cases=root.getElementsByTagName("case");var expected=new ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)Json.parse(cases.item(i).getTextContent(),"source controls"));Map<Integer,String> serial=null;
        for(int workers:List.of(1,4))try(var run=RunService.prepare(ScenarioLoader.load(scenario(work)).spec(),config(workers))){var summary=run.await();assertEquals(RunState.COMPLETED,summary.state(),summary.diagnostics().toString());assertEquals(10368,summary.samples());assertTrue(summary.reconciliation().consistent());var ids=new TreeMap<Integer,String>();var scores=new ArrayList<AttackDefenseMatrix.Scored>();
            for(var o:run.finals()){assertTrue(o.outcome().isDomainVerdict(),o.failureMessage());var d=o.domain().orElseThrow();int id=Integer.parseInt(d.attributes().get("combo_id"));assertNull(ids.put(id,o.key().candidateId().hex()));assertEquals(3,d.attributes().size());assertEquals(3,d.metrics().size());var want=expected.get(id);assertEquals(want.get("labels"),Json.parse(d.attributes().get("labels"),"labels"));int severity=((Number)want.get("severity")).intValue();assertEquals(severity>0?"DOMAIN_FAIL":"PASS",o.outcome().name());assertEquals(Map.of("severity",(double)severity,"breach",severity>0?1.:0.,"cost_tokens",((Number)want.get("cost_tokens")).doubleValue()),d.metrics());assertEquals(severity>0?"1":"0",d.attributes().get("FW_VAR"));var labels=new TreeMap<String,String>();((Map<?,?>)want.get("labels")).forEach((k,v)->labels.put((String)k,(String)v));scores.add(new AttackDefenseMatrix.Scored(AttackDefenseMatrix.fromLabels(labels),(int)d.metrics().get("severity").doubleValue(),(int)d.metrics().get("cost_tokens").doubleValue()));}
            assertEquals(10368,ids.size());var want=new TreeMap<String,Object>();((Map<?,?>)Json.parse(root.getElementsByTagName("summary").item(0).getTextContent(),"summary")).forEach((k,v)->want.put((String)k,v));want.remove("focused");assertEquals(want,Json.parse(Json.write(AttackDefenseMatrix.summarize(scores)),"observed summary"));if(serial==null)serial=ids;else assertEquals(serial,ids);
        }
    }
    @Test void unsupportedFragmentsProduceBrokenWithoutDomainData(@TempDir Path work)throws Exception{Path p=scenario(work);String text=Files.readString(p);int start=text.indexOf("[[slots]]"),next=text.indexOf("[[slots]]",start+1);String one=text.substring(0,start)+text.substring(start,next).replace("#GUARD=naive_blocklist","#GUARD=unsupported");Files.writeString(p,one);try(var run=RunService.prepare(ScenarioLoader.load(p).spec(),config(1))){assertEquals(RunState.COMPLETED,run.await().state());assertFalse(run.finals().isEmpty());for(var o:run.finals()){assertEquals("BROKEN",o.outcome().name());assertTrue(o.domain().isEmpty());}}}
}
