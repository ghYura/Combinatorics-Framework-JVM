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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared corpus ingestion for the CLI and embedding. Coverage paths resolve against the caller's working directory;
 * {lineNo} denotes a physical corpus line. Imported metrics override inline keys before duplicate-identity checks and
 * eligibility are computed. Reports never execute candidates, change verdicts or create repeats/environments.
 * Keep this object open through analysis to share admission with input, samples and coverage provenance. Retaining
 * returned collections after close transfers their memory ownership to the embedding caller.
 */
public final class MetricCorpus implements AutoCloseable {
    public record Imported(int line, String candidateId, int repeat, String environment, CoverageImport.Report report) { }
    private record Identity(String candidate, int repeat, String environment) { }

    private final AnalysisInput input;
    private final List<MemoryBudget.Lease> retained = new ArrayList<>();
    private List<Analysis.Sample> samples = List.of();
    private List<Imported> imports = List.of();
    private List<Diagnostic> diagnostics = List.of();
    private long missingVerdicts;

    private MetricCorpus(AnalysisInput input) { this.input = input; }

    public static MetricCorpus read(Path path, CoverageImport.Source coverage, MemoryBudget budget) {
        // Includes UTF-8/UTF-16 copies, line splitting, token maps, identities, samples and report rendering.
        MetricCorpus corpus = new MetricCorpus(AnalysisInput.read(path, budget, 768));
        boolean success = false;
        try {
            corpus.load(path, coverage, budget);
            success = true;
            return corpus;
        } finally {
            if (!success) corpus.close();
        }
    }

    private void load(Path path, CoverageImport.Source coverage, MemoryBudget budget) {
        List<Analysis.Sample> loaded = new ArrayList<>();
        List<Imported> provenance = new ArrayList<>();
        List<Diagnostic> findings = new ArrayList<>();
        Map<Identity, MetricLines.Line> seen = new LinkedHashMap<>();
        String[] lines = input.text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            MetricLines.Line sample;
            try { sample = MetricLines.sample(line, i + 1); }
            catch (IllegalArgumentException e) {
                throw malformed(path, i + 1, e.getMessage());
            }
            if (coverage != null) {
                // Report output is at most 15 metrics plus one diagnostic. Include path copies and XML escaping.
                retained.add(budget.reserve(MemorySizes.add(16_384, MemorySizes.multiply(32, coverage.pathTemplate().length())),
                        "retained coverage metrics and provenance"));
                CoverageImport.Report report = CoverageImport.read(coverage.format(), coverage.path(i + 1), budget);
                findings.addAll(report.diagnostics());
                provenance.add(new Imported(i + 1, sample.candidateId(), sample.repeat(), sample.environment(), report));
                Map<String, String> attributes = new LinkedHashMap<>(sample.attributes());
                report.metrics().keySet().forEach(attributes::remove);
                sample = new MetricLines.Line(sample.candidateId(), sample.repeat(), sample.environment(), sample.verdict(),
                        report.merge(sample.metrics()), attributes);
            }
            Identity key = new Identity(sample.candidateId(), sample.repeat(), sample.environment());
            MetricLines.Line prior = seen.putIfAbsent(key, sample);
            if (prior != null) {
                if (!prior.equals(sample)) throw malformed(path, i + 1, "sample (" + sample.candidateId() + ", repeat " + sample.repeat()
                        + ", environment '" + sample.environment() + "') appears twice with different content after coverage import");
                continue;
            }
            if (sample.verdict() == null) missingVerdicts++;
            Analysis.Eligibility eligibility = sample.verdict() != null && sample.verdict() != 0
                    ? Analysis.Eligibility.DOMAIN_FAIL : Analysis.Eligibility.ELIGIBLE;
            loaded.add(new Analysis.Sample(sample.candidateId(), sample.candidateId(), sample.repeat(), sample.environment(),
                    eligibility, sample.verdict(), sample.metrics()));
        }
        if (missingVerdicts > 0) findings.add(Diagnostic.info(ErrorCode.INPUT_SCHEMA, missingVerdicts + " lines carry no FW_VAR; they were read as "
                + "passing, as the legacy analyzer read corpora", SourceLocation.of(path.toString())));
        samples = List.copyOf(loaded);
        imports = List.copyOf(provenance);
        diagnostics = List.copyOf(findings);
    }

    private static FrameworkException malformed(Path path, int line, String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, message, SourceLocation.of(path.toString(), line)));
    }

    public List<Analysis.Sample> samples() { return samples; }
    public List<Imported> imports() { return imports; }
    public List<Diagnostic> diagnostics() { return diagnostics; }
    public long missingVerdicts() { return missingVerdicts; }
    public String sha256() { return input.sha256; }

    @Override public void close() {
        for (MemoryBudget.Lease lease : retained) lease.close();
        retained.clear();
        input.close();
    }
}
