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

package org.ghyura.cf.app;

import org.ghyura.cf.analysis.AutoPlan;
import org.ghyura.cf.analysis.CorpusProfile;
import org.ghyura.cf.analysis.RuleSet;
import org.ghyura.cf.analysis.ScoreBreakdown;
import org.ghyura.cf.analysis.TextScoring;
import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.store.MemoryBudget;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The heuristic textual scorer (AN-02) over a text file, with the legacy analyzer's manual rules and tuning. */
final class ScoreCommand implements CommandHandler {
    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        Path file = command.value("--config").map(Path::of).orElse(null);
        List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
        LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
        EffectiveConfig effective = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        TextScoring.Settings defaults = TextScoring.Settings.LEGACY;
        TextScoring.Settings settings;
        try {
            settings = new TextScoring.Settings(
                    (int) number(command, "--min-support", defaults.minSupport()), (int) number(command, "--top-k", defaults.topK()),
                    number(command, "--opt-threshold", defaults.optThreshold()), number(command, "--watch-threshold", defaults.watchThreshold()));
        } catch (IllegalArgumentException e) {
            throw usage(e.getMessage());
        }
        Path input = Path.of(command.value("--input").orElseThrow());
        if (command.flag("--suggest")) {
            return suggest(input, effective, context);
        }
        MemoryBudget budget = new MemoryBudget(Math.max(1, Math.min(effective.config().memoryBudgetBytes(), Runtime.getRuntime().maxMemory() / 4)));
        RuleSet rules = command.value("--rules").map(p -> RuleSet.read(Path.of(p), budget)).orElse(RuleSet.NONE);
        TextScoring.Report report = TextScoring.score(input, rules, settings, budget);
        if (context.format() == OutputFormat.XML) {
            StringBuilder xml = new StringBuilder(8192);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"score\" status=\"OK\" exitCode=\"0\" exitName=\"OK\">\n");
            effective.appendXml(xml, "  ");
            appendXml(xml, input, command.value("--rules").orElse(null), report);
            if (!effective.diagnostics().isEmpty()) {
                xml.append("  <diagnostics>\n");
                DiagnosticXml.appendAll(xml, "    ", effective.diagnostics());
                xml.append("  </diagnostics>\n");
            }
            xml.append("</cf-report>\n");
            context.out().print(xml);
        } else {
            context.out().print(toText(input, report));
            for (Diagnostic d : effective.diagnostics()) context.err().println(d);
        }
        return ExitCode.OK;
    }

    /** The legacy Auto-Analysis plan of the input's first lines, as explicit settings; nothing is scored or run. */
    private static ExitCode suggest(Path input, EffectiveConfig effective, CliContext context) {
        AutoPlan.Plan plan = Session.of(effective).suggest(input);
        if (context.format() == OutputFormat.XML) {
            StringBuilder xml = new StringBuilder(2048);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"score\" status=\"OK\" exitCode=\"0\" exitName=\"OK\">\n");
            xml.append("  <suggestion input=\"").append(Xml.escape(input.toString())).append("\" sampledLines=\"").append(plan.sampledLines())
                    .append("\" nonEmptyLines=\"").append(plan.nonEmptyLines()).append("\" shellLike=\"").append(plan.shellishLines())
                    .append("\" pythonLike=\"").append(plan.pythonEligibleLines()).append("\" goals=\"").append(Xml.escape(plan.goalsSetting()))
                    .append("\">\n");
            for (AutoPlan.Recommendation r : plan.goals()) {
                xml.append("    <goal key=\"").append(Xml.escape(r.key())).append("\" mode=\"").append(r.mode()).append("\" target=\"")
                        .append(r.target()).append("\" weight=\"").append(r.weight()).append("\" support=\"").append(r.support())
                        .append("\" min=\"").append(r.min()).append("\" max=\"").append(r.max()).append("\" reason=\"").append(Xml.escape(r.reason()))
                        .append("\"/>\n");
            }
            xml.append("    <rules>").append(Xml.escape(plan.rulesJson())).append("</rules>\n");
            xml.append("  </suggestion>\n</cf-report>\n");
            context.out().print(xml);
        } else {
            context.out().print("\n" + plan.toText());
            if (!plan.goals().isEmpty()) context.out().print("manual rules for --rules:\n" + plan.rulesJson() + "\n");
        }
        return ExitCode.OK;
    }

    private static double number(ParsedCommand command, String option, double absent) {
        String text = command.value(option).orElse(null);
        if (text == null) {
            return absent;
        }
        try {
            double value = Double.parseDouble(text);
            if (option.equals("--min-support") || option.equals("--top-k")) {
                if (value != Math.rint(value) || Math.abs(value) > Integer.MAX_VALUE) {
                    throw usage(option + " takes a whole number, got '" + text + "'");
                }
            }
            return value;
        } catch (NumberFormatException e) {
            throw usage(option + " takes a number, got '" + text + "'");
        }
    }

    private static FrameworkException usage(String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, message, SourceLocation.of("command line")));
    }

    private static void appendXml(StringBuilder xml, Path input, String rulesFile, TextScoring.Report report) {
        TextScoring.Settings s = report.settings();
        CorpusProfile p = report.profile();
        CorpusProfile.Synthesized r = report.synthesized();
        xml.append("  <text-scoring input=\"").append(Xml.escape(input.toString())).append("\" sha256=\"").append(report.sha256())
                .append(rulesFile == null ? "" : "\" rules=\"" + Xml.escape(rulesFile)).append("\" lines=\"").append(report.lines().size())
                .append("\" opt=\"").append(report.count(ScoreBreakdown.OPT)).append("\" watch=\"").append(report.count(ScoreBreakdown.WATCH))
                .append("\" reject=\"").append(report.count(ScoreBreakdown.REJECT)).append("\" minSupport=\"").append(s.minSupport())
                .append("\" topK=\"").append(s.topK()).append("\" optThreshold=\"").append(s.optThreshold()).append("\" watchThreshold=\"")
                .append(s.watchThreshold()).append("\">\n");
        xml.append("    <profile totalLines=\"").append(p.totalLines()).append("\" nonemptyLines=\"").append(p.nonemptyLines())
                .append("\" emptyLines=\"").append(p.emptyLines()).append("\" lengthMean=\"").append(p.lengthMean()).append("\" lengthMedian=\"")
                .append(p.lengthMedian()).append("\" lengthP10=\"").append(p.lengthP10()).append("\" lengthP90=\"").append(p.lengthP90())
                .append("\" lengthStdev=\"").append(p.lengthStdev()).append("\" entropyMean=\"").append(p.entropyMean()).append("\" entropyMedian=\"")
                .append(p.entropyMedian()).append("\" entropyP10=\"").append(p.entropyP10()).append("\" entropyP90=\"").append(p.entropyP90())
                .append("\" entropyStdev=\"").append(p.entropyStdev()).append("\" dominantLineType=\"").append(Xml.escape(p.dominantLineType()))
                .append("\" dominantSignature=\"").append(Xml.escape(p.dominantSignature())).append("\" confidence=\"").append(r.confidence())
                .append("\" tokenDfCutoff=\"").append(r.tokenDfCutoff()).append("\" numberDfCutoff=\"").append(r.numberDfCutoff()).append("\">\n");
        common(xml, "token", p.commonTokens(), p.tokenFrequency());
        common(xml, "key", p.commonKeys(), p.keyFrequency());
        common(xml, "number", p.commonNumbers(), p.numberFrequency());
        p.metrics().forEach((key, band) -> xml.append("      <metric key=\"").append(Xml.escape(key)).append("\" count=\"").append(band.count())
                .append("\" min=\"").append(band.min()).append("\" max=\"").append(band.max()).append("\" low=\"").append(band.low())
                .append("\" high=\"").append(band.high()).append("\"/>\n"));
        xml.append("    </profile>\n");
        for (RuleSet.Goal g : report.rules().goals()) {
            xml.append("    <goal key=\"").append(Xml.escape(g.key())).append("\" mode=\"").append(g.mode()).append("\" target=\"").append(g.target())
                    .append("\" weight=\"").append(g.weight()).append('"');
            if (g.objective() != null) {
                xml.append(" objective=\"").append(Xml.escape(g.objective())).append("\" optimumValid=\"").append(g.optimumValid())
                        .append("\" argmin=\"").append(g.argmin()).append("\" optimum=\"").append(g.optimum()).append('"');
            }
            xml.append("/>\n");
        }
        for (ScoreBreakdown line : report.lines()) {
            xml.append("    <line no=\"").append(line.lineNo()).append("\" score=\"").append(line.score()).append("\" decision=\"")
                    .append(line.decision()).append("\" type=\"").append(line.features().lineType()).append("\" signature=\"")
                    .append(Xml.escape(line.features().signature())).append("\" matched=\"").append(String.join(" ", line.matchedRules()))
                    .append("\">\n");
            xml.append("      <text>").append(Xml.escape(line.originalLine())).append("</text>\n");
            for (Map.Entry<String, Double> c : line.contributions().entrySet()) {
                xml.append("      <contribution name=\"").append(Xml.escape(c.getKey())).append("\" value=\"").append(c.getValue()).append("\"/>\n");
            }
            for (String note : line.notes()) xml.append("      <note>").append(Xml.escape(note)).append("</note>\n");
            xml.append("    </line>\n");
        }
        xml.append("  </text-scoring>\n");
    }

    private static void common(StringBuilder xml, String kind, List<String> values, Map<String, Integer> support) {
        for (String v : values) {
            xml.append("      <common kind=\"").append(kind).append("\" value=\"").append(Xml.escape(v)).append("\" support=\"")
                    .append(support.getOrDefault(v, 0)).append("\"/>\n");
        }
    }

    private static String toText(Path input, TextScoring.Report report) {
        CorpusProfile p = report.profile();
        StringBuilder out = new StringBuilder(4096);
        out.append("\ninput: ").append(input).append(" (").append(report.lines().size()).append(" logical lines, sha256 ").append(report.sha256())
                .append(")\n");
        out.append(String.format(Locale.ROOT, "profile: %d lines, %d empty; dominant type %s, signature %s; confidence %.4f%n", p.totalLines(),
                p.emptyLines(), p.dominantLineType(), p.dominantSignature(), report.synthesized().confidence()));
        out.append("common tokens: ").append(String.join(", ", p.commonTokens())).append('\n');
        out.append("common keys: ").append(String.join(", ", p.commonKeys())).append('\n');
        out.append("common numbers: ").append(String.join(", ", p.commonNumbers())).append('\n');
        out.append(String.format(Locale.ROOT, "decisions: opt %d, watch %d, reject %d (thresholds %s and %s)%n", report.count(ScoreBreakdown.OPT),
                report.count(ScoreBreakdown.WATCH), report.count(ScoreBreakdown.REJECT), report.settings().optThreshold(),
                report.settings().watchThreshold()));
        for (ScoreBreakdown line : report.lines()) {
            String text = line.originalLine().replace('\n', ' ');
            out.append(String.format(Locale.ROOT, "#%-4d score=%8.2f %-6s %-10s %s%n", line.lineNo(), line.score(), line.decision(),
                    line.features().lineType(), text.length() > 80 ? text.substring(0, 77) + "..." : text));
            if (!line.contributions().isEmpty()) {
                StringBuilder c = new StringBuilder("      ");
                line.contributions().forEach((k, v) -> c.append(' ').append(k).append('=').append(String.format(Locale.ROOT, "%.2f", v)));
                out.append(c).append('\n');
            }
            for (String note : line.notes()) out.append("       note: ").append(note).append('\n');
        }
        return out.toString();
    }
}
