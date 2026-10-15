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
import org.ghyura.cf.fixtures.demos.PortfolioRanking;
import org.ghyura.cf.fixtures.demos.RobustPortfolioPlugin;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.model.Outcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import javax.xml.parsers.DocumentBuilderFactory;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import static org.junit.jupiter.api.Assertions.*;

class RobustPortfoliosRunTest {
    private Element golden() throws Exception {
        var f = DocumentBuilderFactory.newInstance(); f.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        try (var in = Files.newInputStream(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d9-golden.xml"))) { return f.newDocumentBuilder().parse(in).getDocumentElement(); }
    }
    private Path spec(Path dir) throws Exception {
        Path spec = dir.resolve("spec.toml"); try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d9/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); } return spec;
    }
    private Map<String, Map<String, String>> run(Path spec, int workers) throws Exception {
        var config = ConfigResolver.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",RobustPortfolioPlugin.ID,"test"),
                new ConfigResolver.Override("cf.execution.workers",Integer.toString(workers),"test"),new ConfigResolver.Override("cf.generation.distinctTables","true","original Core setting")));
        try (var run = RunService.prepare(ScenarioLoader.load(spec).spec(),config)) {
            var s = run.await(); assertEquals(RunState.COMPLETED,s.state(),s.diagnostics().toString()); assertEquals(BigInteger.valueOf(1024),s.generated()); assertEquals(BigInteger.ZERO,s.excluded()); assertEquals(1024,s.samples()); assertEquals(1024,s.count(Outcome.PASS));
            var results = new TreeMap<String, Map<String, String>>();
            for (var o : run.finals()) { assertEquals(Outcome.PASS,o.outcome()); var a = o.domain().orElseThrow().attributes(); assertEquals(o.outcome().name(),a.get("verdict")); assertNull(results.put(a.get("case_id"),a)); }
            assertEquals(1024,results.size()); return results;
        }
    }
    @Test void unchangedOriginalScenarioProducesAll1024IndependentCostsInSerialAndParallel(@TempDir Path dir) throws Exception {
        var cases = golden().getElementsByTagName("case"); Map<String, Map<String, String>> first = null;
        for (int workers : List.of(1,4)) {
            var actual = run(spec(dir),workers); assertEquals(1024,cases.getLength());
            for (int i = 0; i < cases.getLength(); i++) {
                var c = (Element)cases.item(i); var a = actual.get(c.getAttribute("id")); assertNotNull(a,c.getAttribute("id")); assertEquals(c.getAttribute("verdict"),a.get("verdict"));
                var fields = c.getElementsByTagName("field");
                for (int j = 0; j < fields.getLength(); j++) { var f = (Element)fields.item(j); assertEquals(f.getTextContent(),a.get(f.getAttribute("name")),c.getAttribute("id")+" "+f.getAttribute("name")); }
            }
            if (first == null) first = actual; else assertEquals(first,actual);
        }
    }
    @Test void completeRankingsAndSensitivityConsumeActualSharedRunObservations(@TempDir Path dir) throws Exception {
        var actual = run(spec(dir),4); var observations = new ArrayList<PortfolioRanking.Observed>();
        for (var a : actual.values()) observations.add(new PortfolioRanking.Observed(a.get("case_id"),a.get("design"),Integer.parseInt(a.get("demand")),Integer.parseInt(a.get("disruption")),a.get("verdict"),new BigInteger(a.get("total_cost"))));
        var r = PortfolioRanking.rank(observations); var g = golden(); var m = (Element)g.getElementsByTagName("minimax").item(0);
        assertEquals(m.getAttribute("ranking"),String.join(" ",r.minimaxRanking())); assertEquals(m.getAttribute("primary-ties"),String.join(" ",r.primaryTies()));
        var profiles = g.getElementsByTagName("expected-ranking"); for (int i = 0; i < profiles.getLength(); i++) { var p = (Element)profiles.item(i); assertEquals(p.getAttribute("ranking"),String.join(" ",r.expectedRankings().get(p.getAttribute("profile")))); }
        var steps = g.getElementsByTagName("sensitivity"); for (int i = 0; i < steps.getLength(); i++) { var s = (Element)steps.item(i); var got = r.sensitivity().get(i); assertEquals(s.getAttribute("ranking"),String.join(" ",got.ranking())); assertEquals(s.getAttribute("winner-expected"),got.winnerExpected().toString()); }
        assertEquals("000000",r.expectedRankings().get("calm").getFirst()); assertEquals("110100",r.expectedRankings().get("stress").getFirst());
    }
}
