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

package org.ghyura.cf.analysis;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageImportTest {
    @Test
    void matchesIndependentOriginalProbeGoldens(@TempDir Path dir) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = getClass().getResourceAsStream("coverage-goldens.xml")) {
            assertNotNull(in);
            var cases = factory.newDocumentBuilder().parse(in).getElementsByTagName("case");
            assertEquals(12, cases.getLength());
            MemoryBudget budget = new MemoryBudget(4L << 20);
            for (int i = 0; i < cases.getLength(); i++) {
                Element c = (Element) cases.item(i);
                String body = c.getElementsByTagName("report").item(0).getTextContent();
                String expected = c.getElementsByTagName("expected").item(0).getTextContent();
                Map<String, Double> metrics = new LinkedHashMap<>();
                for (String token : expected.strip().split("\\s+")) {
                    if (token.isEmpty()) continue;
                    String[] kv = token.split("=", 2);
                    metrics.put(kv[0], Double.parseDouble(kv[1]));
                }
                Path report = Files.writeString(dir.resolve("report.data"), body, StandardCharsets.UTF_8);
                CoverageImport.Report imported = CoverageImport.read(CoverageImport.Format.parse(c.getAttribute("format")), report, budget);
                assertEquals(metrics, imported.metrics(), c.getAttribute("id"));
                assertEquals(metrics.isEmpty() ? CoverageImport.Status.EMPTY : CoverageImport.Status.IMPORTED, imported.status());
                assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8))), imported.sha256());
                assertTrue(imported.diagnostics().isEmpty(), imported.diagnostics().toString());
                assertEquals(0, budget.reservedBytes());
            }
        }
    }

    @Test
    void invalidReportsAreAtomicDiagnosedAndNeverMaskResourceLimits(@TempDir Path dir) throws Exception {
        MemoryBudget budget = new MemoryBudget(1L << 20);
        Path report = dir.resolve("report.json");
        assertEquals(CoverageImport.Status.MISSING, CoverageImport.read(CoverageImport.Format.COVERAGE_PY, report, budget).status());
        assertEquals(CoverageImport.Status.UNREADABLE, CoverageImport.read(CoverageImport.Format.GCOV, dir, budget).status());
        for (String body : new String[]{"{", "{\"totals\":{\"covered_lines\":1,\"covered_lines\":2}}", "{\"totals\":{\"percent_covered\":1e999}}",
                "{\"totals\":{\"covered_lines\":9223372036854775808}}", "{\"totals\":{\"covered_lines\":1},\"bad\":\"\\uZZZZ\"}"}) {
            Files.writeString(report, body);
            CoverageImport.Report imported = CoverageImport.read(CoverageImport.Format.COVERAGE_PY, report, budget);
            assertEquals(CoverageImport.Status.MALFORMED, imported.status(), body);
            assertTrue(imported.metrics().isEmpty());
            assertEquals(ErrorCode.INPUT_MALFORMED, imported.diagnostics().getFirst().code());
            assertEquals(64, imported.sha256().length());
        }
        Files.writeString(report, "<counter type=\"BRANCH\" missed=\"1\" covered=\"1\"/><counter type=\"LINE\" missed=\"1\" covered=\"9223372036854775807\"/>");
        assertTrue(CoverageImport.read(CoverageImport.Format.JACOCO, report, budget).metrics().isEmpty(), "overflow discards all metrics from that report");
        Files.write(report, new byte[]{(byte) 0xc3, (byte) 0x28});
        assertEquals(CoverageImport.Status.MALFORMED, CoverageImport.read(CoverageImport.Format.GCOV, report, budget).status());
        try (FileChannel file = FileChannel.open(report, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            file.position(256L << 20);
            file.write(ByteBuffer.wrap(new byte[]{0}));
        }
        FrameworkException refused = assertThrows(FrameworkException.class, () -> CoverageImport.read(CoverageImport.Format.GCOV, report, budget));
        assertEquals(ErrorCode.RESOURCE_LIMIT, refused.diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void physicalLinesProvenanceEligibilityAndMergedDuplicateIdentityArePreserved(@TempDir Path dir) throws Exception {
        Path corpus = Files.writeString(dir.resolve("results.kv"), "# skipped\n\ncandidate_id=a repeat_idx=2 env_id=e coverage_line=0.1 FW_VAR=0\n"
                + "candidate_id=a repeat_idx=2 env_id=e coverage_line=unknown FW_VAR=0\n"
                + "candidate_id=b FW_VAR=7\n"
                + "candidate_id=a repeat_idx=3 env_id=f FW_VAR=0\n");
        for (int i = 3; i <= 5; i++) Files.writeString(dir.resolve("coverage-" + i + ".xml"), "<counter type=\"LINE\" missed=\"1\" covered=\"9\"/>");
        CoverageImport.Source source = new CoverageImport.Source(CoverageImport.Format.JACOCO, dir.resolve("coverage-{lineNo}.xml").toString());
        MemoryBudget budget = new MemoryBudget(4L << 20);
        try (MetricCorpus data = MetricCorpus.read(corpus, source, budget)) {
            assertEquals(3, data.samples().size(), "identical merged identity counts once; a different repeat/environment stays separate");
            assertEquals(4, data.imports().size(), "duplicate lines retain their own report provenance");
            assertEquals(3, data.imports().getFirst().line());
            assertEquals(0.9, data.samples().getFirst().metrics().get("coverage_line"));
            assertEquals(Analysis.Eligibility.DOMAIN_FAIL, data.samples().get(1).eligibility());
            assertEquals(Analysis.Eligibility.NO_METRICS, data.samples().get(2).eligibility());
            assertEquals(CoverageImport.Status.MISSING, data.imports().getLast().report().status());
            assertTrue(budget.reservedBytes() > 0);
            assertThrows(UnsupportedOperationException.class, () -> data.imports().getFirst().report().metrics().clear());
        }
        assertEquals(0, budget.reservedBytes());
        Files.writeString(dir.resolve("coverage-4.xml"), "<counter type=\"LINE\" missed=\"2\" covered=\"8\"/>");
        FrameworkException conflict = assertThrows(FrameworkException.class, () -> MetricCorpus.read(corpus, source, budget));
        assertEquals(ErrorCode.INPUT_MALFORMED, conflict.diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
    }

    @Test
    void retainedReportsAndInputShareOneBudgetAndFailureReleasesBoth(@TempDir Path dir) throws Exception {
        Path corpus = Files.writeString(dir.resolve("results.kv"), "FW_VAR=0\n".repeat(40));
        Path report = Files.writeString(dir.resolve("coverage.json"), "{\"totals\":{\"percent_covered\":80}}");
        MemoryBudget budget = new MemoryBudget(1L << 20);
        CoverageImport.Source source = new CoverageImport.Source(CoverageImport.Format.COVERAGE_PY, report.toString());
        FrameworkException refused = assertThrows(FrameworkException.class, () -> MetricCorpus.read(corpus, source, budget));
        assertEquals(ErrorCode.RESOURCE_LIMIT, refused.diagnostics().getFirst().code());
        assertTrue(budget.highWaterBytes() > 700_000, "retained provenance must be charged across reports");
        assertEquals(0, budget.reservedBytes());
        try (FileChannel file = FileChannel.open(corpus, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            file.position(256L << 20);
            file.write(ByteBuffer.wrap(new byte[]{0}));
        }
        FrameworkException huge = assertThrows(FrameworkException.class, () -> MetricCorpus.read(corpus, null, budget));
        assertEquals(ErrorCode.RESOURCE_LIMIT, huge.diagnostics().getFirst().code());
        assertEquals(0, budget.reservedBytes());
    }
}
