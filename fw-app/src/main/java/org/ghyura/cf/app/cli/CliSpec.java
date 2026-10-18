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

package org.ghyura.cf.app.cli;

import org.ghyura.cf.model.Xml;

import java.util.List;
import java.util.Optional;

/**
 * The frozen command-line syntax (work package M00). Commands and options may be added by later work
 * packages; none is renamed or removed. The checked-in {@code cli-contract.xml} is the reviewed contract and a
 * test renders this class against it.
 */
public final class CliSpec {

    public static final String PROGRAM = "java -jar fw-app.jar";

    /** Accepted by every command. {@code --help}/{@code -h} and {@code --version} are handled before options. */
    public static final List<OptionSpec> GLOBAL_OPTIONS = List.of(
            new OptionSpec("--config", "FILE", false, "Properties file with cf.* settings, UTF-8."),
            new OptionSpec("--legacy-config", "FILE", true, "Legacy fw.properties file to import below every cf.* source; "
                    + "repeatable, later files win; every key is reported with its disposition (revision 2, decision F6-F31)."),
            new OptionSpec("--set", "KEY=VALUE", true,
                    "Override one cf.* setting; repeatable; wins over the file and the environment."),
            new OptionSpec("--workers", "N", false, "Shortcut for --set cf.execution.workers=N."),
            new OptionSpec("--repeat", "K", false, "Shortcut for --set cf.execution.repeat.count=K."),
            new OptionSpec("--seed", "S", false, "Shortcut for --set cf.random.seed=S."),
            new OptionSpec("--constraints", "FILE", false, "Constraint sidecar to apply (params, orders, constraints; shortcut for "
                    + "--set cf.constraints.file=FILE); without it a scenario's companion <stem>.constraints.json is read."),
            new OptionSpec("--format", "text|xml", false, "Output format of the report on standard output; default text."),
            new OptionSpec("--debug", null, false, "Print stack traces for unexpected failures."));

    public static final List<CommandSpec> COMMANDS = List.of(
            new CommandSpec("run", "Execute a scenario end to end: validate, plan, generate, execute, analyze.", "M04",
                    List.of(new OptionSpec("--scenario", "FILE", false, "Scenario to run (XLSX, JSON, YAML or TOML)."),
                            new OptionSpec("--out", "PATH", false,
                                    "Write the run report artifact to PATH; enables cf.artifacts.enabled."),
                            new OptionSpec("--checkpoint", "PATH", false,
                                    "Write quiescent checkpoints to PATH; enables cf.checkpoint.enabled and "
                                            + "cf.artifacts.enabled."),
                            new OptionSpec("--sut", "ID", false, "What runs the candidates: generated-java or a SUT plugin id "
                                    + "(shortcut for --set cf.execution.sut=ID)."),
                            new OptionSpec("--run-id", "ID", false, "The run's name in every sample key (shortcut for "
                                    + "--set cf.run.id=ID)."),
                            new OptionSpec("--legacy-handoff", "FILE", false, "Instead of a scenario, run the Java candidates of a "
                                    + "legacy bundle.handoff/v2 hand-over (its manifest.json and loose source files); its run id and "
                                    + "verdict mode apply unless set explicitly. Any other protocol version is refused.")),
                    List.of()),
            new CommandSpec("validate",
                    "Validate the configuration and, when one is given, a scenario, without running anything.", "M00",
                    List.of(new OptionSpec("--scenario", "FILE", false,
                            "Scenario to validate as well (scenario validation arrives with M01).")),
                    List.of()),
            new CommandSpec("plan",
                    "Validate a scenario and print its plan: operators, exact cardinalities with formulas and row lengths, the candidate "
                            + "and sample counts with how sure each is (exact, bounded, estimated, unknown), resource estimates.",
                    "M02",
                    List.of(new OptionSpec("--scenario", "FILE", false, "Scenario to plan."),
                            new OptionSpec("--scan", "N", false, "The most candidates the exact counts may visit; default 1000000."),
                            new OptionSpec("--graph", "FILE", false, "Also write the FW_Seq dependency graph as fwgen graph wrote it: Graphviz "
                                    + "DOT when FILE ends in .dot, its JSON otherwise.")),
                    List.of("--scenario")),
            new CommandSpec("candidates",
                    "Validate, plan, generate and assemble a scenario's candidates: counts (generated, excluded by rules, left out by "
                            + "covering selection, executable), per-rule impact and the first candidates with their ids and sources.",
                    "M03",
                    List.of(new OptionSpec("--scenario", "FILE", false, "Scenario to assemble."),
                            new OptionSpec("--limit", "N", false, "How many candidates to show, from the first; default 10, 0 for none."),
                            new OptionSpec("--scan", "N", false, "The most candidates the exact counts may visit; default 1000000."),
                            new OptionSpec("--export", "DIR", false, "Also write every executable candidate as a legacy bundle.handoff/v2 hand-over into DIR, which must be new or empty: one file per candidate, or shards with --transport sharded."),
                            new OptionSpec("--transport", "NAME", false, "How --export packs the candidates: loose-files (the default) or sharded.")),
                    List.of("--scenario")),
            new CommandSpec("analyze",
                    "Analyze an exported results artifact: metrics, coverage, Pareto fronts, optimizers.", "M06",
                    List.of(new OptionSpec("--results", "FILE", false, "Results artifact to analyze."),
                            new OptionSpec("--coverage-format", "jacoco|coverage-py|gcov", false,
                                    "Import existing coverage metrics; requires --coverage-report. Runs no coverage tool."),
                            new OptionSpec("--coverage-report", "PATH", false,
                                    "Coverage report path relative to the working directory; {lineNo} is the physical one-based results line. "
                                            + "Requires --coverage-format. Imported metrics override inline keys; missing/malformed reports are diagnosed.")),
                    List.of("--results")),
            new CommandSpec("resume", "Resume a run from an explicit checkpoint artifact.", "M07",
                    List.of(new OptionSpec("--checkpoint", "FILE", false, "Checkpoint artifact to resume from."),
                            new OptionSpec("--out", "PATH", false, "Write the run report artifact to PATH.")),
                    List.of("--checkpoint")),
            new CommandSpec("export", "Convert a checkpoint artifact into a report, legacy results_v2 rows or a metrics table.", "M07",
                    List.of(new OptionSpec("--input", "FILE", false, "Checkpoint artifact to read (run --checkpoint PATH writes one)."),
                            new OptionSpec("--output", "FILE", false, "File to write; replaced only once complete."),
                            new OptionSpec("--kind", "report|results|csv|html|metrics", false, "What to export: report (XML summary, the "
                                    + "default), results (legacy results_v2 rows, one per attempt, CSV), csv (final samples with their metrics), "
                                    + "html (the run as one self-contained page with its triage findings, the legacy bundle report) or metrics "
                                    + "(the metrics corpus analyze --results reads: one key=value line per sample with its verdict as FW_VAR).")),
                    List.of("--input", "--output")),
            new CommandSpec("score",
                    "Score the logical lines of a text file with the legacy analyzer's heuristic rules: a frozen corpus profile, "
                            + "named contributions and opt/watch/reject decisions. Runs nothing; decisions never make a sample eligible.",
                    "M09",
                    List.of(new OptionSpec("--input", "FILE", false, "UTF-8 text to score; lines join into logical shell commands."),
                            new OptionSpec("--rules", "FILE", false, "The legacy analyzer's manual rules (JSON: required/forbidden tokens, "
                                    + "keys and regexes, token weights, expected types and signatures, line_length, numeric_ranges, "
                                    + "optimizations)."),
                            new OptionSpec("--min-support", "N", false, "Lines a common token, key or number needs; default 3."),
                            new OptionSpec("--top-k", "N", false, "Common tokens, keys and numbers kept; default 12."),
                            new OptionSpec("--opt-threshold", "X", false, "Score from which a line is opt; default 75."),
                            new OptionSpec("--watch-threshold", "X", false, "Score from which a line is watch; default 45."),
                            new OptionSpec("--suggest", null, false, "Instead of scoring, suggest goals from the first 500 lines (the "
                                    + "legacy Auto-Analysis plan): manual-rules JSON and a cf.analysis.goals value. Never enables execution.")),
                    List.of("--input")),
            new CommandSpec("desktop", "Open the Swing desktop: validate and plan a scenario, run, pause, resume and cancel it with its "
                    + "progress and report, analyze results and score texts. Needs a display; refused on a headless JVM.", "M09",
                    List.of(new OptionSpec("--scenario", "FILE", false, "Scenario to open.")),
                    List.of()),
            new CommandSpec("guif", "Open the FlatLaf desktop with the shared Java workflows and the integrated Sieve constraint workbench. Needs a display; refused on a headless JVM.", "M09",
                    List.of(new OptionSpec("--scenario", "FILE", false, "Scenario to open.")),
                    List.of()),
            new CommandSpec("decompose",
                    "Split a source file into byte-preserving pieces and write the fwgen spec the legacy code_decompose.py wrote: one raw "
                            + "slot per piece with its original text, interaction notes from shared names; rewrites added to the slots are "
                            + "combined and reassemble into programs. Nothing is run.",
                    "M09",
                    List.of(new OptionSpec("--source", "FILE", false, "Source file to split."),
                            new OptionSpec("--lang", "auto|java|java-heuristic|generic|py", false, "java splits the first type at the "
                                    + "members the JDK parser finds; java-heuristic is the legacy scanner; generic splits at blank lines; "
                                    + "py splits at the top-level statements as the legacy ast decomposition did; auto (default) picks py when the text "
                                    + "parses as Python, else java or generic, as the legacy tool chose."),
                            new OptionSpec("--name", "NAME", false, "Logical name in the spec; default the file name without extensions."),
                            new OptionSpec("--out", "FILE", false, "Write the spec here and list the pieces; default standard output."),
                            new OptionSpec("--max-cell", "N", false, "Split pieces longer than N characters at line ends (the spreadsheet "
                                    + "cell holds 32767); default 0, off.")),
                    List.of("--source")),
            new CommandSpec("triage",
                    "Turn a finished run's verdicts into findings: failures grouped by what distinguishes them, a minimal witness for "
                            + "each and the declared values that explain them, as the legacy bundle triage reported. Reads only what the "
                            + "run persisted; nothing is executed.",
                    "M09",
                    List.of(new OptionSpec("--input", "PATH", false, "A checkpoint (run --checkpoint PATH writes one), a legacy run "
                                    + "directory holding metrics.kv or executor-summary.json, or a directory with runs/RUN/ (the latest is read)."),
                            new OptionSpec("--top", "N", false, "How many enrichment rows the text report shows; default 12."),
                            new OptionSpec("--json", "FILE", false, "Also write the legacy bundle.triage/v1 JSON report to FILE."),
                            new OptionSpec("--write", null, false, "Also write triage.json into the run directory (beside a checkpoint)."),
                            new OptionSpec("--fail-on-findings", null, false, "Exit 1 (ANALYSIS_FINDINGS) after the report when the run "
                                    + "has any finding.")),
                    List.of("--input")),
            new CommandSpec("doctor",
                    "Probe this environment for the resolved configuration, read-only: the Java runtime, its compiler, memory budget "
                            + "against heap, the working directory, the SUT plugin and capability refusals, each OK, WARNING or BLOCKING "
                            + "as the legacy bundle doctor reported; exits 1 only when a check is blocking.",
                    "M09",
                    List.of(new OptionSpec("--scenario", "FILE", false, "Scenario whose static Python imports are inspected from a bounded preview; no candidates or preprocessing run."),
                            new OptionSpec("--json", "FILE", false, "Also write the cf.doctor/v1 JSON report to FILE.")),
                    List.of()),
            new CommandSpec("benchmark",
                    "Time each stage on its own at a size profile, as the legacy bundle bench did: generator, sieve, executor and "
                            + "analyzer in this JVM, with the environment that makes the figures comparable (JDK, heap, hardware, workers, "
                            + "seed); no end-to-end or billion-execution throughput is claimed.",
                    "M09",
                    List.of(new OptionSpec("--profile", "10K|1M|10M|N", false, "Candidates per stage; default 10K. Above 10,000,000 only "
                                    + "with --allow-huge."),
                            new OptionSpec("--stages", "LIST", false, "Comma-separated stages: generator, sieve, executor, analyzer; "
                                    + "default all."),
                            new OptionSpec("--allow-huge", null, false, "Allow a profile above 10,000,000 candidates."),
                            new OptionSpec("--out", "FILE", false, "Also write the versioned JSON report (cf.benchmark/v1) to FILE.")),
                    List.of()),
            new CommandSpec("capabilities",
                    "Print what this build implements, what is not built yet, and what is incompatible by design.",
                    "M00", List.of(), List.of()));

    /** Values accepted by {@code export --kind}. */
    public static final List<String> EXPORT_KINDS = List.of("report", "results", "csv", "html", "metrics");

    private CliSpec() {
    }

    public static Optional<CommandSpec> command(String name) {
        return COMMANDS.stream().filter(c -> c.name().equals(name)).findFirst();
    }

    public static List<String> commandNames() {
        return COMMANDS.stream().map(CommandSpec::name).toList();
    }

    /** The contract file's content (without the license notice). */
    public static String toXml() {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<cli-contract schema=\"1\" program=\"").append(Xml.escape(PROGRAM)).append("\">\n");
        xml.append("  <global-options>\n");
        for (OptionSpec o : GLOBAL_OPTIONS) {
            appendOption(xml, "    ", o);
        }
        xml.append("  </global-options>\n");
        xml.append("  <commands>\n");
        for (CommandSpec c : COMMANDS) {
            xml.append("    <command name=\"").append(c.name()).append("\" owner=\"").append(c.owner())
                    .append("\" required=\"").append(String.join(" ", c.required())).append("\">\n");
            xml.append("      <summary>").append(Xml.escape(c.summary())).append("</summary>\n");
            for (OptionSpec o : c.options()) {
                appendOption(xml, "      ", o);
            }
            xml.append("    </command>\n");
        }
        xml.append("  </commands>\n");
        xml.append("  <exit-codes>\n");
        for (ExitCode e : ExitCode.values()) {
            xml.append("    <exit-code value=\"").append(e.code()).append("\" name=\"").append(e.name()).append("\">")
                    .append(Xml.escape(e.meaning())).append("</exit-code>\n");
        }
        xml.append("  </exit-codes>\n");
        xml.append("</cli-contract>\n");
        return xml.toString();
    }

    private static void appendOption(StringBuilder xml, String indent, OptionSpec o) {
        xml.append(indent).append("<option name=\"").append(o.name()).append('"');
        if (o.takesValue()) {
            xml.append(" value=\"").append(Xml.escape(o.valueName())).append('"');
        }
        xml.append(" repeatable=\"").append(o.repeatable()).append("\">").append(Xml.escape(o.description()))
                .append("</option>\n");
    }
}
