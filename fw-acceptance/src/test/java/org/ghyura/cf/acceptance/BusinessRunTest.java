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

import org.ghyura.cf.campaign.RunHandle;
import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.campaign.RunSummary;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.FileResolver;
import org.ghyura.cf.input.ScenarioInterpreter;
import org.ghyura.cf.input.builder.Flag;
import org.ghyura.cf.input.builder.ScheduleBuilder;
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** First four A16 domain slices run through generation, ServiceLoader execution and immutable results. */
class BusinessRunTest {
    private record Run(RunSummary summary, List<Observation> observations) { }
    private static Run run(String plugin, WorkbookData workbook, int workers) {
        ScenarioSpec spec = ScenarioInterpreter.interpret(workbook, FileResolver.none()).spec();
        try (RunHandle handle = RunService.prepare(spec, ConfigResolver.resolve(null, Map.of(), List.of(
                new ConfigResolver.Override("cf.execution.sut", plugin, "test"),
                new ConfigResolver.Override("cf.execution.workers", Integer.toString(workers), "test"))))) {
            RunSummary summary = handle.await();
            assertEquals(RunState.COMPLETED, summary.state(), summary.diagnostics().toString());
            assertTrue(summary.countsExact());
            assertEquals(summary.generated(), summary.executable()); assertEquals(BigInteger.ZERO, summary.excluded());
            assertEquals(summary.samples(), summary.attempts());
            return new Run(summary, handle.observations());
        }
    }
    private static void judged(Run run, Map<String, Long> expected) {
        assertEquals(expected, run.summary().verdictsByName());
        for (Outcome outcome : List.of(Outcome.BROKEN, Outcome.TIMEOUT, Outcome.INFRA_FAIL, Outcome.SKIPPED, Outcome.CANCELLED))
            assertEquals(0, run.summary().count(outcome), outcome.name());
    }
    private static Map<CandidateId, Object> projection(Run run) {
        Map<CandidateId, Object> result = new LinkedHashMap<>();
        for (Observation o : run.observations()) result.put(o.key().candidateId(), o.domain().orElseThrow());
        assertEquals(run.summary().samples(), result.size()); return result;
    }

    private static Map<String, Long> matrix(String family) throws Exception {
        try (InputStream in = BusinessRunTest.class.getResourceAsStream("/org/ghyura/cf/fixtures/business/matrices-golden.xml")) {
            assertNotNull(in); DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var cases = factory.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("case");
            for (int i = 0; i < cases.getLength(); i++) {
                Element element = (Element) cases.item(i);
                if (!element.getAttribute("id").equals(family)) continue;
                Object value = Json.parse(element.getElementsByTagName("expected").item(0).getTextContent(), "legacy matrix");
                assertInstanceOf(Map.class, value); Map<String, Long> result = new LinkedHashMap<>();
                ((Map<?, ?>) value).forEach((key, count) -> result.put(key.toString(), Long.parseLong(count.toString()))); return result;
            }
            throw new AssertionError("Missing legacy matrix " + family);
        }
    }

    @Test void pricingMatrixIsIsolatedAndDeterministicAcrossWorkerCounts() throws Exception {
        WorkbookData workbook = ScheduleBuilder.newSchedule()
                .sheet("mode").verbs("FW_Combi(1)").rows("strict", "fast", "legacy")
                .sheet("region").verbs("FW_Combi(1)").rows("us", "eu")
                .sheet("coupons").flag(Flag.OPTIONAL).verbs("FW_Combi(1)").row("LOYALTY")
                .sheet("stages").verbs("FW_Combi(3)", "FW_Permut()").rows("discount", "tax", "round")
                .sheet("duplicate_submit").flag(Flag.OPTIONAL).verbs("FW_Combi(1)").row("duplicate_submit").build();
        Run serial = run("bundle-pricing", workbook, 1), parallel = run("bundle-pricing", workbook, 4);
        assertEquals(144, serial.summary().samples());
        // Original canonical_price and checkout, not the Java plugin, supplied these expected distributions.
        judged(serial, matrix("pricing"));
        judged(parallel, serial.summary().verdictsByName()); assertEquals(projection(serial), projection(parallel));
    }

    @Test void securityMatrixPreservesLeaksStaleKeysAndCachePoison() throws Exception {
        WorkbookData workbook = ScheduleBuilder.newSchedule()
                .sheet("mode").verbs("FW_Combi(1)").rows("strict", "legacy")
                .sheet("text").verbs("FW_Combi(1)").rows("note SECRET-ABC end", "note ＳＥＣＲＥＴ-ＡＢＣ end")
                .sheet("order").verbs("FW_Combi(3)", "FW_Permut()").rows("normalize", "redact", "tag")
                .sheet("features").verbs("FW_Combi(2)").rows("cache", "audit")
                .sheet("rotate_key").flag(Flag.OPTIONAL).verbs("FW_Combi(1)").row("rotate_key")
                .sheet("poison_cache").flag(Flag.OPTIONAL).verbs("FW_Combi(1)").row("poison_cache").build();
        Run serial = run("bundle-secure-pipeline", workbook, 1), parallel = run("bundle-secure-pipeline", workbook, 4);
        assertEquals(96, serial.summary().samples());
        judged(serial, matrix("security"));
        judged(parallel, serial.summary().verdictsByName()); assertEquals(projection(serial), projection(parallel));
    }

    @Test void billingAndFulfilmentJudgeTheOriginalHealthyAndFaultySequences() {
        WorkbookData healthyBilling = ScheduleBuilder.newSchedule().sheet("actions").verbs("FW_Combi(2)", "FW_Permut()")
                .rows("{\"op\":\"tick\",\"days\":30}", "{\"op\":\"refund\"}").build();
        judged(run("billing-engine", healthyBilling, 4), Map.of("PASS", 2L));
        WorkbookData faultyBilling = ScheduleBuilder.newSchedule().sheet("plan").verbs("FW_Combi(1)").rows("basic", "pro")
                .sheet("actions").verbs("FW_Combi(3)", "FW_Permut()")
                .rows("{\"op\":\"refund\",\"invoice_id\":1}", "{\"op\":\"chargeback\",\"invoice_id\":1}",
                        "{\"op\":\"replay\",\"event_id\":\"refund-2\"}").build();
        judged(run("billing-engine", faultyBilling, 4), Map.of("INVARIANT_VIOLATION", 12L));
        WorkbookData healthyFulfilment = ScheduleBuilder.newSchedule().sheet("actions").verbs("FW_Combi(2)")
                .rows("{\"op\":\"submit\",\"oid\":\"one\"}", "{\"op\":\"drain\"}").build();
        judged(run("order-fulfilment", healthyFulfilment, 4), Map.of("PASS", 1L));
        WorkbookData faultyFulfilment = ScheduleBuilder.newSchedule().sheet("actions").verbs("FW_Combi(9)")
                .rows("{\"op\":\"submit\",\"oid\":\"one\"}", "{\"op\":\"deliver\"}", "{\"op\":\"deliver\"}",
                        "{\"op\":\"deliver\"}", "{\"op\":\"deliver\"}", "{\"op\":\"duplicate\"}",
                        "{\"op\":\"restart\",\"service\":\"payment\"}", "{\"op\":\"duplicate\"}", "{\"op\":\"drain\"}").build();
        Run fault = run("order-fulfilment", faultyFulfilment, 4);
        judged(fault, Map.of("INVARIANT_VIOLATION", 1L));
        assertTrue(fault.observations().getFirst().domain().orElseThrow().attributes().get("audit").contains("J5"));
    }

    @Test void malformedAndExcessiveActionSequencesAreBrokenRatherThanDomainVerdicts() {
        WorkbookData malformed = ScheduleBuilder.newSchedule().sheet("actions").verbs("FW_Combi(1)").row("{\"op\":\"unsupported\"}").build();
        assertEquals(1, run("billing-engine", malformed, 1).summary().count(Outcome.BROKEN));
        // Two orders would create 6000 retry operations: tick counts must include this fan-out.
        WorkbookData excessive = ScheduleBuilder.newSchedule().sheet("actions").verbs("FW_Combi(3)")
                .rows("{\"op\":\"submit\",\"oid\":\"one\"}", "{\"op\":\"submit\",\"oid\":\"two\"}", "{\"op\":\"tick\",\"n\":3000}").build();
        assertEquals(1, run("order-fulfilment", excessive, 1).summary().count(Outcome.BROKEN));
    }
}
