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

import org.ghyura.cf.store.MemoryBudget;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The heuristic textual scorer (audit AN-02; legacy AnalyzerCore.OptimizationAnalyzer.analyzeLines without a database):
 * physical lines are grouped into logical commands, every command's features are extracted and accumulated into a corpus
 * profile, the profile is frozen and its rules synthesized, and only then is every line scored, so each score uses the
 * same profile. Nothing is executed. The features, profile and breakdowns are admitted to the memory budget before they
 * are built, and the admission is released when scoring returns: the report is the caller's.
 */
public final class TextScoring {

    private static final long CHUNK = 256L * 1024;

    private TextScoring() {
    }

    /**
     * The legacy analyzer's tuning: minimum support of the common tokens, keys and numbers (1..1000000), how many of each
     * are kept (1..100000) and the finite opt and watch thresholds.
     */
    public record Settings(int minSupport, int topK, double optThreshold, double watchThreshold) {

        /** The legacy defaults: 3, 12, 75 and 45. */
        public static final Settings LEGACY = new Settings(3, 12, 75.0, 45.0);

        public Settings {
            if (minSupport < 1 || minSupport > 1_000_000) {
                throw new IllegalArgumentException("the minimum support must be 1..1000000, not " + minSupport);
            }
            if (topK < 1 || topK > 100_000) {
                throw new IllegalArgumentException("the number of common tokens, keys and numbers must be 1..100000, not " + topK);
            }
            if (!Double.isFinite(optThreshold) || !Double.isFinite(watchThreshold)) {
                throw new IllegalArgumentException("the opt and watch thresholds must be finite");
            }
        }
    }

    /** The scored corpus: the profile every score used, its rules and one breakdown per logical line, in input order. */
    public record Report(Settings settings, RuleSet rules, CorpusProfile profile, CorpusProfile.Synthesized synthesized,
                         List<ScoreBreakdown> lines, String sha256) {

        public Report {
            lines = List.copyOf(lines);
        }

        /** How many lines got the decision. */
        public long count(String decision) {
            return lines.stream().filter(l -> l.decision().equals(decision)).count();
        }

        /** The line with the highest score, the first of equals (legacy findOptimalResult); empty without lines. */
        public java.util.Optional<ScoreBreakdown> optimal() {
            ScoreBreakdown best = null;
            for (ScoreBreakdown line : lines) {
                if (best == null || line.score() > best.score()) best = line;
            }
            return java.util.Optional.ofNullable(best);
        }

        /**
         * The lines as the legacy analyzer exported them (AnalyzerCore.exportCsv): a header, then per line its number,
         * score (two decimals), decision, original text, type, length, entropy (three decimals), token, key and number
         * counts, signature, notes and matched rules (each joined with " ; "); a field with a comma, quote or line break is
         * quoted with doubled quotes.
         */
        public String toCsv() {
            StringBuilder out = new StringBuilder(128 + 96 * lines.size());
            out.append("LineNo,Score,Decision,OriginalLine,Type,Length,Entropy,Tokens,Keys,Numbers,Signature,Notes,MatchedRules\n");
            for (ScoreBreakdown r : lines) {
                TextFeatures f = r.features();
                out.append(String.format(java.util.Locale.ROOT, "%d,%.2f,%s,%s,%s,%d,%.3f,%d,%d,%d,%s,%s,%s\n", r.lineNo(), r.score(),
                        r.decision(), csv(r.originalLine()), f.lineType(), f.length(), f.entropy(), f.tokens().size(), f.keyValues().size(),
                        f.numbers().size(), csv(f.signature()), csv(String.join(" ; ", r.notes())), csv(String.join(" ; ", r.matchedRules()))));
            }
            return out.toString();
        }

        private static String csv(String s) {
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                return "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
    }

    /**
     * Scores a UTF-8 text file.
     *
     * @throws org.ghyura.cf.model.FrameworkException INPUT_UNREADABLE, INPUT_MALFORMED (not UTF-8) or RESOURCE_LIMIT
     */
    public static Report score(Path file, RuleSet rules, Settings settings, MemoryBudget budget) {
        // Include physical-line splitting before the per-logical-line feature reservations are taken.
        try (AnalysisInput input = AnalysisInput.read(file, budget, 128)) {
            return score(input.text.lines().toList(), rules, settings, budget, input.sha256);
        }
    }

    /**
     * Scores physical lines, as {@code Files.readAllLines} returns them.
     *
     * @throws org.ghyura.cf.model.FrameworkException RESOURCE_LIMIT when the features, profile or breakdowns do not fit
     */
    public static Report score(List<String> physicalLines, RuleSet rules, Settings settings, MemoryBudget budget) {
        return score(physicalLines, rules, settings, budget, null);
    }

    private static Report score(List<String> physicalLines, RuleSet rules, Settings settings, MemoryBudget budget, String sha256) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(settings, "settings");
        try (Admission admission = new Admission(budget)) {
            long chars = 0;
            for (String line : physicalLines) {
                chars += line == null ? 0 : line.length() + 1;
            }
            admission.admit(4096 + 4 * chars + 64L * physicalLines.size());
            List<LogicalLines.Block> blocks = LogicalLines.group(physicalLines);
            CorpusProfile.Builder builder = new CorpusProfile.Builder();
            List<TextFeatures> features = new ArrayList<>(blocks.size());
            for (LogicalLines.Block block : blocks) {
                admission.admit(1024 + 96L * block.text().length());
                TextFeatures f = TextFeatures.extract(block.firstLine(), block.text(), block.text());
                features.add(f);
                builder.accumulate(f, rules.goals());
            }
            CorpusProfile profile = builder.freeze(settings.minSupport(), settings.topK());
            CorpusProfile.Synthesized synthesized = profile.synthesize(settings.minSupport());
            RuleScorer scorer = new RuleScorer(rules, profile, synthesized, settings.optThreshold(), settings.watchThreshold());
            long perLine = 512 + 160L * rules.size();
            List<ScoreBreakdown> lines = new ArrayList<>(features.size());
            for (TextFeatures f : features) {
                admission.admit(perLine);
                lines.add(scorer.score(f));
            }
            return new Report(settings, rules, profile, synthesized, lines, sha256);
        }
    }

    /** Reservations taken ahead of the allocations they cover, in growing chunks, all returned on close. */
    private static final class Admission implements AutoCloseable {
        private final MemoryBudget budget;
        private final List<MemoryBudget.Lease> leases = new ArrayList<>();
        private long reserved;
        private long available;

        Admission(MemoryBudget budget) {
            this.budget = budget;
        }

        void admit(long bytes) {
            if (bytes > available) {
                long chunk = Math.max(bytes - available, Math.min(Math.max(CHUNK, reserved), budget.availableBytes()));
                leases.add(budget.reserve(chunk, "text scoring"));
                reserved += chunk;
                available += chunk;
            }
            available -= bytes;
        }

        @Override
        public void close() {
            for (MemoryBudget.Lease lease : leases) {
                lease.close();
            }
        }
    }
}
