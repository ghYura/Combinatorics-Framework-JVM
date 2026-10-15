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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.input.InputFormat;
import org.ghyura.cf.input.ScenarioLoader;
import org.ghyura.cf.input.SeqGraph;
import org.ghyura.cf.input.SpecReader;
import org.ghyura.cf.input.WorkbookFormats;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyEncodingPolicy;
import org.ghyura.cf.model.key.LabelWindow;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.plan.SheetRole;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The scenario half of {@code validate}, shared by the command line, the desktop and embedding: read the file, interpret it, parse and check FW_Seq, build the key
 * dictionary (which runs the census and picks the encoding), and summarize. Nothing is executed, written or
 * kept; the dictionary is closed again before the summary is returned.
 *
 * <p>When a later stage fails, the findings of the earlier ones (warnings, notes) travel with its error, so one
 * report shows everything that was found.</p>
 */
public final class ScenarioValidation {

    /** What validation learned about a scenario that has no error. */
    public record Summary(String file, InputFormat format, ScenarioSpec spec, SeqPlan plan, KeyEncoding encoding, KeyEncodingPolicy policy,
                   int firstLabel, int lastLabel, int highestKey, long reservedBytes, List<Diagnostic> findings,
                   SpecReader.Document authored, SeqGraph graph) {

        public Summary {
            findings = List.copyOf(findings);
        }

        /** The graph's line: its size and hash, then how many issues of each level it has. */
        public String graphLine() {
            List<SeqGraph.Issue> issues = graph.validate();
            long errors = issues.stream().filter(i -> i.level().equals(SeqGraph.ERROR)).count();
            return graph.nodes().size() + " nodes, " + graph.edges().size() + " edges, " + graph.hash() + "; " + errors + " errors, "
                    + (issues.size() - errors) + " warnings" + (errors > 0 && authored == null
                    ? " (bundle plan refused such a workbook; the Core runs it, so they are reported here)" : "");
        }

        public long rolesOf(SheetRole role) {
            return plan.sheets().stream().filter(s -> s.role() == role).count();
        }

        public long promoted() {
            return plan.sheets().stream().filter(SheetPlan::promoted).count();
        }

        public long count(Severity severity) {
            return findings.stream().filter(d -> d.severity() == severity).count();
        }

        public String toText() {
            StringBuilder text = new StringBuilder(1024);
            text.append("\nscenario: ").append(file).append('\n');
            line(text, "format", format.token());
            if (authored != null) {
                line(text, "authored", SpecReader.FORMAT + " spec '" + authored.title() + "' (spec_version " + authored.specVersion() + "), "
                        + authored.slots() + " slots compiled as fwgen compiled them" + (authored.aliases().isEmpty() ? ""
                        : "; aliases " + String.join(", ", authored.aliases().stream().map(a -> a.sheet() + " " + a.alias() + " -> "
                        + a.verb()).toList())) + (authored.settings().isEmpty() ? "" : "; chooses " + String.join(", ",
                        authored.settings().stream().map(v -> v.key() + "=" + v.value()).toList())));
            }
            line(text, "data sheets", Integer.toString(spec.sheetCount()));
            line(text, "values", spec.valueCount() == 0 ? "0" : spec.valueCount() + " (labels " + firstLabel + ".." + lastLabel + ")");
            line(text, "key encoding", encoding + " (policy " + policy + ", highest label " + highestKey + ")");
            line(text, "control", "FW_SheetNames " + spec.controls().sheetNames().size() + ", FW_CUSTOM_VAR "
                    + spec.controls().customVars().size() + ", FW_Arguments " + spec.controls().arguments().size()
                    + ", FW_RunMeFirstOnce " + spec.controls().runMeFirstOnce().size());
            line(text, "sequence", spec.sequence().size() + " FW_Seq rows");
            line(text, "plan", plan.activeSheets().size() + " sheets have directives; roles: " + rolesOf(SheetRole.MANDATORY)
                    + " mandatory, " + rolesOf(SheetRole.OPTIONAL) + " optional, " + rolesOf(SheetRole.EXCLUDED) + " excluded, "
                    + rolesOf(SheetRole.NONE) + " without values; " + plan.virtualSheetCount() + " virtual sheets; " + promoted()
                    + " single verbs repeated");
            line(text, "graph", graphLine());
            for (SeqGraph.Issue issue : graph.validate()) {
                text.append("    ").append(issue.level().equals(SeqGraph.ERROR) ? "✗" : "⚠").append(" [").append(issue.code()).append("] ")
                        .append(issue.message()).append('\n');
            }
            line(text, "identity", "scenario " + spec.contentDigest() + ", plan " + plan.contentDigest());
            line(text, "findings", count(Severity.ERROR) + " errors, " + count(Severity.WARNING) + " warnings, " + count(Severity.INFO)
                    + " notes (warnings are on standard error; --format xml lists the notes too)");
            return text.toString();
        }

        private static void line(StringBuilder text, String label, String value) {
            text.append(String.format(Locale.ROOT, "  %-13s %s\n", label, value));
        }

        public void appendXml(StringBuilder xml, String indent) {
            xml.append(indent).append("<scenario file=\"").append(Xml.escape(file)).append("\" format=\"").append(format.token())
                    .append("\" dataSheets=\"").append(spec.sheetCount()).append("\" values=\"").append(spec.valueCount())
                    .append("\" firstLabel=\"").append(firstLabel).append("\" lastLabel=\"").append(lastLabel)
                    .append("\" keyEncoding=\"").append(encoding).append("\" keyPolicy=\"").append(policy)
                    .append("\" highestKeyLabel=\"").append(highestKey).append("\" reservedBytes=\"").append(reservedBytes)
                    .append("\" sequenceRows=\"").append(spec.sequence().size()).append("\" virtualSheets=\"")
                    .append(plan.virtualSheetCount()).append("\" activeSheets=\"").append(plan.activeSheets().size())
                    .append("\" mandatory=\"").append(rolesOf(SheetRole.MANDATORY)).append("\" optional=\"")
                    .append(rolesOf(SheetRole.OPTIONAL)).append("\" excluded=\"").append(rolesOf(SheetRole.EXCLUDED))
                    .append("\" withoutValues=\"").append(rolesOf(SheetRole.NONE)).append("\" promoted=\"").append(promoted())
                    .append("\" scenarioDigest=\"").append(spec.contentDigest()).append("\" planDigest=\"")
                    .append(plan.contentDigest()).append("\">\n");
            if (authored != null) {
                xml.append(indent).append("  <authored format=\"").append(SpecReader.FORMAT).append("\" specVersion=\"")
                        .append(Xml.escape(authored.specVersion())).append("\" name=\"").append(Xml.escape(authored.name()))
                        .append("\" title=\"").append(Xml.escape(authored.title())).append("\" slots=\"").append(authored.slots()).append("\">\n");
                for (SpecReader.AliasUse a : authored.aliases()) {
                    xml.append(indent).append("    <alias sheet=\"").append(Xml.escape(a.sheet())).append("\" alias=\"").append(Xml.escape(a.alias()))
                            .append("\" verb=\"").append(Xml.escape(a.verb())).append("\"/>\n");
                }
                for (ConfigResolver.ScenarioValue v : authored.settings()) {
                    xml.append(indent).append("    <chooses key=\"").append(Xml.escape(v.key())).append("\" value=\"").append(Xml.escape(v.value()))
                            .append("\" location=\"").append(Xml.escape(v.where().toString())).append("\"/>\n");
                }
                xml.append(indent).append("  </authored>\n");
            }
            for (SheetPlan sheet : plan.sheets()) {
                xml.append(indent).append("  <sheet key=\"").append(sheet.key()).append("\" name=\"").append(Xml.escape(sheet.name()))
                        .append("\" virtual=\"").append(sheet.virtual()).append("\" values=\"").append(sheet.valueCount())
                        .append("\" role=\"").append(sheet.role()).append("\" active=\"").append(sheet.active())
                        .append("\" reuse=\"").append(sheet.reuse()).append("\" reuseTableOnly=\"").append(sheet.reuseTableOnly())
                        .append("\" lastInQueue=\"").append(sheet.lastInQueue()).append("\" heading=\"").append(sheet.heading())
                        .append("\" promoted=\"").append(sheet.promoted()).append('"');
                if (sheet.concatenator() != null) {
                    xml.append(" concatenator=\"").append(Xml.escape(sheet.concatenator())).append('"');
                }
                if (sheet.directives().isEmpty()) {
                    xml.append("/>\n");
                } else {
                    xml.append(">\n");
                    for (Directive directive : sheet.directives()) {
                        xml.append(indent).append("    <directive>").append(Xml.escape(directive.normalized())).append("</directive>\n");
                    }
                    xml.append(indent).append("  </sheet>\n");
                }
            }
            xml.append(indent).append("  <dependency-graph hash=\"").append(graph.hash()).append("\" nodes=\"").append(graph.nodes().size())
                    .append("\" edges=\"").append(graph.edges().size()).append('"');
            List<SeqGraph.Issue> issues = graph.validate();
            if (issues.isEmpty()) {
                xml.append("/>\n");
            } else {
                xml.append(">\n");
                for (SeqGraph.Issue issue : issues) {
                    xml.append(indent).append("    <issue level=\"").append(issue.level()).append("\" code=\"").append(issue.code())
                            .append("\" nodes=\"").append(Xml.escape(String.join(" ", issue.nodes()))).append("\">")
                            .append(Xml.escape(issue.message())).append("</issue>\n");
                }
                xml.append(indent).append("  </dependency-graph>\n");
            }
            xml.append(indent).append("</scenario>\n");
        }
    }

    private ScenarioValidation() {
    }

    /** A scenario file read with its constraint layer, and the configuration it runs under. */
    public record Opened(ScenarioLoader.Loaded loaded, EffectiveConfig effective) {
    }

    /**
     * Reads a scenario, a workbook or an fwgen spec, with its constraint layer, and derives the configuration it runs
     * under: a spec's goals and coverage choices apply below every explicit setting ({@link ConfigResolver#withScenario}).
     *
     * @throws FrameworkException with the reader's findings when the file cannot be used or a chosen setting is invalid
     */
    public static Opened open(Path file, EffectiveConfig base) {
        ScenarioLoader.Loaded loaded = readAdmitted(file, base.config());
        try {
            return new Opened(loaded, ConfigResolver.withScenario(base, settings(file, loaded)));
        } catch (FrameworkException e) {
            throw withEarlierFindings(loaded.diagnostics(), e);
        }
    }

    /**
     * The settings a scenario file chooses: an fwgen spec's own, and where its Python candidates run (cf.python.workdir, below
     * every explicit setting): the legacy generator tree it lies in, the nearest ancestor holding sut_paths.py, as the legacy
     * bundle ran that tree's candidates from it; otherwise the scenario's own directory.
     */
    public static java.util.List<ConfigResolver.ScenarioValue> settings(Path file, ScenarioLoader.Loaded loaded) {
        java.util.List<ConfigResolver.ScenarioValue> values = new java.util.ArrayList<>(loaded.settings());
        Path dir = file.toAbsolutePath().normalize().getParent();
        if (dir != null && values.stream().noneMatch(v -> v.key().equals("cf.python.workdir"))) {
            Path tree = null;
            for (Path p = dir; p != null && tree == null; p = p.getParent()) {
                if (java.nio.file.Files.isRegularFile(p.resolve("sut_paths.py"))) {
                    tree = p;
                }
            }
            values.add(new ConfigResolver.ScenarioValue("cf.python.workdir", (tree != null ? tree : dir).toString(),
                    org.ghyura.cf.model.SourceLocation.of(file.toString())));
            if (tree != null) {
                values.addAll(bundleSettings(tree, values));
            }
        }
        return values;
    }

    /** What the legacy bundle's Core read from config/core.fw.properties that shapes the candidates. */
    private static final java.util.Set<String> BUNDLE_CORE_KEYS = java.util.Set.of("cf.generation.distinctTables", "cf.assembly.distinct",
            "cf.keys.logical.labels");

    /**
     * A scenario in a legacy generator tree ran through that bundle (cross-check with the legacy 2026-10-13): its Core read the
     * tree's config/core.fw.properties, whose table and final-table distinctification and key strategy shape the candidates
     * (distinctify__fwX_Y__Tables, distinctify__fw_final_opt__Tables, core.keys.strategy, imported as --legacy-config imports
     * them), its Reader kept every cell's whitespace whatever the language, and its analyzer reported the best ten
     * (bundle/stages.py). They join below every explicit setting; the scenario's own choices win. Optional tables keep this
     * build's handling, which equals the bundle's run-time optional contract.
     */
    static java.util.List<ConfigResolver.ScenarioValue> bundleSettings(Path tree, java.util.List<ConfigResolver.ScenarioValue> chosen) {
        java.util.Set<String> taken = new java.util.HashSet<>();
        chosen.forEach(v -> taken.add(v.key()));
        java.util.List<ConfigResolver.ScenarioValue> out = new java.util.ArrayList<>();
        Path core = tree.resolve("config").resolve("core.fw.properties");
        if (java.nio.file.Files.isRegularFile(core)) {
            org.ghyura.cf.config.LegacyImport imported = org.ghyura.cf.config.LegacyImporter.importFiles(java.util.List.of(core), java.util.Map.of());
            if (!imported.hasErrors()) {
                for (org.ghyura.cf.config.LegacyImport.TargetValue t : imported.overrides()) {
                    if (BUNDLE_CORE_KEYS.contains(t.key()) && taken.add(t.key())) {
                        out.add(new ConfigResolver.ScenarioValue(t.key(), t.value(), t.where()));
                    }
                }
            }
        }
        org.ghyura.cf.model.SourceLocation stages = org.ghyura.cf.model.SourceLocation.of(tree.resolve("bundle").resolve("stages.py").toString());
        if (taken.add("cf.render.preserveWhitespace")) {
            out.add(new ConfigResolver.ScenarioValue("cf.render.preserveWhitespace", "true", stages));
        }
        if (taken.add("cf.analysis.topK")) {
            out.add(new ConfigResolver.ScenarioValue("cf.analysis.topK", "10", stages));
        }
        return out;
    }

    /** Admit the measured document estimate for every file-based validation entry point. */
    private static ScenarioLoader.Loaded readAdmitted(Path file, RunConfig config) {
        try (ScenarioInputs inputs = new ScenarioInputs(file, config)) {
            return ScenarioLoader.load(file, constraintFiles(config), inputs);
        }
    }

    /**
     * @param file   the scenario as the user named it
     * @param config the resolved configuration: the key encoding policy and the memory budget come from it
     * @throws FrameworkException with every finding so far when any stage has an error
     */
    public static Summary run(String file, RunConfig config) {
        return prepare(file, config, (summary, dictionary, budget) -> summary);
    }

    /** What a command does with a validated scenario while its key dictionary is open. */
    public interface Then<T> {
        T apply(Summary summary, KeyDictionary dictionary, MemoryBudget budget);
    }

    /**
     * Validates the scenario as {@link #run} does and hands the summary, the open dictionary and the run's memory
     * budget to {@code then}; the dictionary is closed afterwards. A failure of {@code then} carries the findings
     * of the earlier stages too.
     */
    public static <T> T prepare(String file, RunConfig config, Then<T> then) {
        return prepare(file, readAdmitted(Path.of(file), config), config, then);
    }

    /**
     * Validates a scenario already read and interpreted (with its constraint layer), named as the file it is or will be;
     * otherwise as {@link #prepare(String, RunConfig, Then)}.
     */
    @SuppressWarnings("try")
    public static <T> T prepare(String file, ScenarioLoader.Loaded loaded, RunConfig config, Then<T> then) {
        Path path = Path.of(file);
        List<Diagnostic> findings = new ArrayList<>(loaded.diagnostics());
        ScenarioSpec spec = loaded.spec();

        SeqPlanner.Planned planned;
        try {
            planned = SeqPlanner.plan(spec, PlanOptions.of(config));
        } catch (FrameworkException e) {
            throw withEarlierFindings(findings, e);
        }
        findings.addAll(planned.diagnostics());
        SeqPlan plan = planned.plan();

        MemoryBudget budget = MemoryBudget.of(config);
        // The dictionary owns the normalized spec. The compiled plan and authoring metadata remain live alongside it.
        long auxiliary = MemorySizes.add(MemorySizes.retained(plan), MemorySizes.add(MemorySizes.retained(loaded.authored()),
                MemorySizes.add(MemorySizes.retained(loaded.graph().nodes()), MemorySizes.retained(loaded.graph().edges()))));
        try (MemoryBudget.Lease metadata = budget.reserve(auxiliary, "scenario plan, metadata and dependency graph");
             KeyDictionary dictionary = KeyDictionary.build(spec, (int) plan.virtualSheetCount(), config.keyEncodingPolicy(),
                labelMode(config), windowScope(config), budget)) {
            LabelWindow window = dictionary.labelWindow();
            if (window.shifted()) {
                findings.add(Diagnostic.info(ErrorCode.KEY_WINDOW_SHIFTED, "the " + spec.valueCount() + " values do not fit the legacy "
                        + "numbering S+1..S+C below 32768, so cf.keys.logical.labels=legacy-window labels them "
                        + window.firstLabel() + ".." + window.lastLabel(spec.valueCount()) + ", the window the legacy identity strategy chose",
                        SourceLocation.of(spec.source()), "firstLabel", Integer.toString(window.firstLabel())));
            }
            Summary summary = new Summary(file, WorkbookFormats.formatOf(path.getFileName().toString()), spec, plan, dictionary.encoding(),
                    config.keyEncodingPolicy(), dictionary.firstLabel(), dictionary.lastLabel(), dictionary.census().max(),
                    dictionary.reservedBytes(), findings, loaded.authored(), loaded.graph());
            return then.apply(summary, dictionary, budget);
        } catch (FrameworkException e) {
            throw withEarlierFindings(findings, e);
        }
    }

    /** The constraint files the configuration names, resolved against the working directory as the legacy did. */
    public static ScenarioLoader.ConstraintFiles constraintFiles(RunConfig config) {
        return new ScenarioLoader.ConstraintFiles(config.constraintsFile().isEmpty() ? null : Path.of(config.constraintsFile()),
                config.constraintsBondsFile().isEmpty() ? null : Path.of(config.constraintsBondsFile()));
    }

    static LabelWindow.Mode labelMode(RunConfig config) {
        return switch (config.logicalLabels()) {
            case PRESERVE -> LabelWindow.Mode.PRESERVE;
            case LEGACY_WINDOW -> LabelWindow.Mode.LEGACY_WINDOW;
        };
    }

    static LabelWindow.Scope windowScope(RunConfig config) {
        return switch (config.windowScope()) {
            case ALL -> LabelWindow.Scope.ALL;
            case CELLS -> LabelWindow.Scope.CELLS;
        };
    }

    private static FrameworkException withEarlierFindings(List<Diagnostic> earlier, FrameworkException failure) {
        List<Diagnostic> all = new ArrayList<>(earlier);
        all.addAll(failure.diagnostics());
        return new FrameworkException(all);
    }
}
