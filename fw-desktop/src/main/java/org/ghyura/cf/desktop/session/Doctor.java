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

import org.ghyura.cf.config.CapabilityCatalog;
import org.ghyura.cf.config.CapabilityStatus;
import org.ghyura.cf.config.ConfigOrigin;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Xml;

import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Read-only environment checks in the legacy doctor report shape: OK, WARNING or BLOCKING. Framework stages stay in
 * this JVM. With the explicit Python add-on, its interpreter and a bounded candidate preview can be inspected without
 * running candidates, preprocessing or imported modules. Nothing is installed or written to scenario/import directories.
 */
public final class Doctor {

    public static final String SCHEMA = "cf.doctor/v1";

    /** Ordered worst last. */
    public enum Severity {
        OK, WARNING, BLOCKING
    }

    /** One diagnosed fact. */
    public record Check(String name, Severity severity, String message, List<String> details) {
        public Check {
            details = List.copyOf(details);
        }
    }

    /** The checks in their canonical order. */
    public record Report(List<Check> checks) {
        public Report {
            checks = List.copyOf(checks);
        }

        /** The worst severity. */
        public Severity overall() {
            return checks.stream().map(Check::severity).max(Enum::compareTo).orElse(Severity.OK);
        }

        /** The legacy table: a glyph, the severity, the name and the message, details indented under each check. */
        public String text() {
            int width = checks.stream().mapToInt(c -> c.name().length()).max().orElse(0);
            StringBuilder out = new StringBuilder();
            for (Check c : checks) {
                String glyph = switch (c.severity()) {
                    case OK -> "✓";
                    case WARNING -> "!";
                    case BLOCKING -> "✗";
                };
                out.append(out.isEmpty() ? "" : "\n").append("  ").append(glyph).append(" [").append(pad(c.severity().name(), 8)).append("] ")
                        .append(pad(c.name(), width)).append("  ").append(c.message());
                c.details().forEach(d -> out.append("\n      ").append(d));
            }
            return out.toString();
        }

        /** {@code {"schema", "overall", "checks": [{name, severity, message, details}]}}. */
        public String json() {
            List<Object> list = new ArrayList<>();
            for (Check c : checks) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", c.name());
                m.put("severity", c.severity().name());
                m.put("message", c.message());
                m.put("details", c.details());
                list.add(m);
            }
            Map<String, Object> doc = new LinkedHashMap<>();
            doc.put("schema", SCHEMA);
            doc.put("overall", overall().name());
            doc.put("checks", list);
            return Json.write(doc);
        }

        public void appendXml(StringBuilder xml, String indent) {
            xml.append(indent).append("<doctor schema=\"").append(SCHEMA).append("\" overall=\"").append(overall()).append("\">\n");
            for (Check c : checks) {
                xml.append(indent).append("  <check name=\"").append(Xml.escape(c.name())).append("\" severity=\"").append(c.severity())
                        .append("\" message=\"").append(Xml.escape(c.message())).append('"');
                if (c.details().isEmpty()) {
                    xml.append("/>\n");
                    continue;
                }
                xml.append(">\n");
                c.details().forEach(d -> xml.append(indent).append("    <detail>").append(Xml.escape(d)).append("</detail>\n"));
                xml.append(indent).append("  </check>\n");
            }
            xml.append(indent).append("</doctor>\n");
        }
    }

    private static String pad(String s, int width) {
        return s.length() >= width ? s : s + " ".repeat(width - s.length());
    }

    private static Check check(String name, Severity severity, String message, String... details) {
        return new Check(name, severity, message, List.of(details));
    }

    private Doctor() {
    }

    /**
     * Every probe for a resolved configuration, or, when the configuration itself was refused, its refusal followed by the
     * probes of the defaults.
     *
     * @param effective the resolved configuration, or null when resolving it failed
     * @param refusal   why it failed (null when it did not)
     * @param workDirectory where commands write what they are told to write
     */
    public static List<Check> probes(EffectiveConfig effective, FrameworkException refusal, Path workDirectory) {
        return probes(effective, refusal, workDirectory, null);
    }

    /** With an optional scenario: the Python add-on inspects at most64 generated positions, without evaluating them. */
    public static List<Check> probes(EffectiveConfig effective, FrameworkException refusal, Path workDirectory, Path scenario) {
        List<Check> extra = new ArrayList<>();
        List<String> sources = List.of();
        if (scenario != null && effective != null) {
            try {
                effective = Session.of(effective).configurationFor(scenario);
                if ("python".equals(effective.config().executionSut())) {
                    CandidatePreview preview = Session.of(effective).preview(scenario, new CandidatePreview.Request(
                            java.math.BigInteger.valueOf(64), java.math.BigInteger.ZERO, 64, false, false), org.ghyura.cf.spi.Cancellation.NONE);
                    sources = preview.page().rows().stream().filter(row -> row.status() == CandidatePreview.Status.KEPT)
                            .map(CandidatePreview.Row::source).toList();
                    boolean partial = preview.page().next() != null || preview.page().rows().stream()
                            .anyMatch(row -> row.status() == CandidatePreview.Status.KEPT && row.source().length()>CandidatePreview.MAX_SOURCE_CHARS);
                    extra.add(check("python_import_scope",partial?Severity.WARNING:Severity.OK,
                            "Read-only preview visited "+preview.page().visited()+" of "+preview.generated()+" generated positions",
                            "Only kept, untruncated sources in that page are inspected; later candidates may import other modules. Neither candidates nor preprocessing are executed."));
                }
            } catch (FrameworkException failure) {
                extra.add(check("scenario",Severity.BLOCKING,"The scenario cannot be inspected",failure.diagnostics().getFirst().message()));
            }
        }
        RunConfig config = effective != null ? effective.config() : RunConfig.defaults();
        List<Check> checks = new ArrayList<>();
        checks.add(java());
        checks.add(configuration(effective, refusal));
        checks.add(compiler(config));
        checks.add(memory(config));
        checks.add(workDirectory(workDirectory));
        checks.add(sut(config));
        checks.add(capabilities(config));
        if ("python".equals(config.executionSut()) && effective != null) {
            checks.addAll(python(effective, scenario, sources));
            if (scenario == null) extra.add(check("python_import_scope",Severity.WARNING,"No scenario supplied: imports were not inspected",
                    "Use doctor --scenario FILE with the Python runner to inspect a bounded source preview."));
        }
        checks.addAll(extra);
        checks.add(replaced(config));
        return checks;
    }

    static Check java() {
        Runtime.Version v = Runtime.version();
        String vm = System.getProperty("java.vm.name", "?") + " (" + System.getProperty("java.vendor", "?") + ")";
        if (v.feature() < 25) {
            return check("java", Severity.BLOCKING, "Java " + v + " is older than the Java 25 this build needs", vm);
        }
        return check("java", Severity.OK, "Java " + v + ", " + vm, "java.home=" + System.getProperty("java.home", "?"));
    }

    static Check configuration(EffectiveConfig effective, FrameworkException refusal) {
        if (effective == null) {
            List<String> details = new ArrayList<>();
            List<Diagnostic> diagnostics = refusal == null ? List.of() : refusal.diagnostics();
            diagnostics.forEach(d -> details.add(d.code() + ": " + d.message()));
            return new Check("configuration", Severity.BLOCKING, "the cf.* configuration is refused; the probes below use the defaults",
                    details);
        }
        List<String> details = new ArrayList<>();
        long explicit = effective.entries().stream().filter(e -> e.origin() != ConfigOrigin.DEFAULT).count();
        details.add(effective.entries().size() + " settings, " + explicit + " of them set explicitly");
        boolean warned = false;
        for (Diagnostic d : effective.diagnostics()) {
            details.add(d.severity() + " " + d.code() + ": " + d.message());
            warned |= d.severity() != org.ghyura.cf.model.Severity.INFO;
        }
        return new Check("configuration", warned ? Severity.WARNING : Severity.OK, warned ? "the configuration resolves with warnings"
                : "the configuration resolves", details);
    }

    static Check compiler(RunConfig config) {
        boolean generated = config.executionSut().equals(RunConfig.GENERATED_JAVA);
        if (ToolProvider.getSystemJavaCompiler() == null) {
            return check("compiler", generated ? Severity.BLOCKING : Severity.WARNING, "no Java compiler in this runtime (module jdk.compiler)",
                    "generated Java candidates are compiled in this JVM; a runtime without jdk.compiler cannot run them",
                    generated ? "cf.execution.sut=generated-java needs it: run on a JDK or name a typed SUT plugin"
                            : "cf.execution.sut names a typed plugin, which does not need it");
        }
        return check("compiler", Severity.OK, "javac in this runtime (jdk.compiler " + Runtime.version().feature() + ")",
                generated ? "cf.execution.sut=generated-java compiles each candidate in memory" : "not needed by the configured typed SUT plugin");
    }

    static Check memory(RunConfig config) {
        long heap = Runtime.getRuntime().maxMemory();
        long budget = config.memoryBudgetBytes();
        int processors = Runtime.getRuntime().availableProcessors();
        String[] details = {"heap limit " + mib(heap), "cf.memory.budget.bytes " + mib(budget), "cf.execution.workers " + config.workers()
                + " on " + processors + " processors"};
        if (heap != Long.MAX_VALUE && budget > heap) {
            return check("memory", Severity.WARNING, "the memory budget (" + mib(budget) + ") exceeds the heap limit (" + mib(heap)
                    + "): the heap runs out before the budget refuses work; raise -Xmx or lower cf.memory.budget.bytes", details);
        }
        return check("memory", Severity.OK, "the memory budget " + mib(budget) + " fits the heap limit " + mib(heap), details);
    }

    private static String mib(long bytes) {
        return bytes == Long.MAX_VALUE ? "unlimited" : String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0));
    }

    static Check workDirectory(Path dir) {
        Path abs = dir.toAbsolutePath().normalize();
        if (!Files.isDirectory(abs) || !Files.isWritable(abs)) {
            return check("work_directory", Severity.WARNING, "the working directory is not writable: " + abs,
                    "commands write only where told (--checkpoint, --out, --json); give them writable paths");
        }
        try {
            FileStore store = Files.getFileStore(abs);
            double free = store.getUsableSpace() / (1024.0 * 1024.0 * 1024.0);
            String[] details = {"path=" + abs, String.format(Locale.ROOT, "free=%.1f GiB", free),
                "free inodes are not observable from the JVM"};
            if (free < 5.0) {
                return check("work_directory", Severity.WARNING, String.format(Locale.ROOT, "low on space: %.2f GiB free", free), details);
            }
            return check("work_directory", Severity.OK, String.format(Locale.ROOT, "writable, %.1f GiB free", free), details);
        } catch (IOException e) {
            return check("work_directory", Severity.WARNING, "writable, but its free space cannot be read: " + abs, String.valueOf(e.getMessage()));
        }
    }

    static Check sut(RunConfig config) {
        String id = config.executionSut();
        if (id.equals(RunConfig.GENERATED_JAVA)) {
            return check("sut", Severity.OK, "generated Java candidates, compiled and run in this JVM");
        }
        CapabilityCatalog catalog;
        try {
            catalog = CapabilityCatalog.load(config.executionClasspath());
        } catch (FrameworkException e) {
            return check("sut", Severity.BLOCKING, "the SUT class path cannot be read", e.diagnostics().getFirst().message());
        }
        List<CapabilityCatalog.Plugin> matches = catalog.plugins().stream().filter(p -> p.id().equals(id)).toList();
        List<String> available = catalog.plugins().stream().map(p -> p.id() + " " + p.version() + " (" + p.origin() + ")").toList();
        if (matches.size() == 1) {
            CapabilityCatalog.Plugin p = matches.getFirst();
            return check("sut", Severity.OK, "plugin " + p.id() + " " + p.version() + " from " + p.origin());
        }
        List<String> details = new ArrayList<>(available.isEmpty() ? List.of("no SUT plugin on the class path") : available);
        return new Check("sut", Severity.BLOCKING, matches.isEmpty() ? "cf.execution.sut = " + id + " names no SUT plugin"
                : matches.size() + " SUT plugins have the id " + id, details);
    }

    static Check capabilities(RunConfig config) {
        CapabilityCatalog catalog;
        try {
            catalog = CapabilityCatalog.load(config.executionClasspath());
        } catch (FrameworkException e) {
            catalog = CapabilityCatalog.load();
        }
        Map<CapabilityStatus, Integer> counts = new EnumMap<>(CapabilityStatus.class);
        catalog.entries().forEach(e -> counts.merge(e.status(), 1, Integer::sum));
        String summary = catalog.entries().size() + " capabilities: " + counts.getOrDefault(CapabilityStatus.IMPLEMENTED, 0) + " implemented / "
                + counts.getOrDefault(CapabilityStatus.PARTIAL, 0) + " partial / " + counts.getOrDefault(CapabilityStatus.NOT_IMPLEMENTED, 0)
                + " not implemented / " + counts.getOrDefault(CapabilityStatus.INCOMPATIBLE, 0) + " incompatible";
        if (config.coverage() == RunConfig.Coverage.LINES && !config.executionSut().equals(RunConfig.GENERATED_JAVA)) {
            return check("capability_matrix", Severity.BLOCKING, "cf.execution.coverage=lines with a typed SUT plugin is refused "
                    + "(UNSUPPORTED_CAPABILITY): only generated Java candidates are instrumented", summary);
        }
        return check("capability_matrix", Severity.OK, "this configuration is supported", summary);
    }

    private static List<Check> python(EffectiveConfig effective, Path scenario, List<String> sources) {
        RunConfig config = effective.config();
        Map<String,String> settings = new LinkedHashMap<>();
        effective.entries().forEach(entry -> settings.put(entry.key(),entry.rawValue()));
        org.ghyura.cf.spi.RunInputs inputs = new org.ghyura.cf.spi.RunInputs(List.of(),java.util.Optional.empty(),"FW_VAR",Map.of(),
                scenario == null?java.util.Optional.empty():java.util.Optional.of(scenario.toAbsolutePath().normalize().getParent()),settings);
        try (java.net.URLClassLoader loader = new java.net.URLClassLoader("cf-doctor-addons",
                config.executionClasspath().stream().map(path -> {
                    try { return Path.of(path).toAbsolutePath().toUri().toURL(); }
                    catch (java.net.MalformedURLException failure) { throw new IllegalArgumentException(failure); }
                }).toArray(java.net.URL[]::new), org.ghyura.cf.spi.SutPlugin.class.getClassLoader())) {
            List<org.ghyura.cf.spi.SutPlugin> matches = java.util.ServiceLoader.load(org.ghyura.cf.spi.SutPlugin.class,loader).stream()
                    .map(java.util.ServiceLoader.Provider::get).filter(plugin -> plugin.id().equals("python")).toList();
            if (matches.size()!=1 || !(matches.getFirst() instanceof org.ghyura.cf.spi.SutDoctor doctor)) {
                return List.of(check("python",Severity.WARNING,"Install a Python add-on that supports environment diagnostics"));
            }
            return doctor.diagnose(inputs,sources).stream().map(found -> new Check(found.name(),switch(found.severity()) {
                case INFO -> Severity.OK; case WARNING -> Severity.WARNING; case ERROR -> Severity.BLOCKING;
            },found.message(),found.details())).toList();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt(); return List.of(check("python",Severity.WARNING,"Python diagnostics interrupted"));
        } catch (Exception | java.util.ServiceConfigurationError failure) {
            return List.of(check("python",Severity.WARNING,"Python diagnostics unavailable",String.valueOf(failure.getMessage())));
        }
    }

    static Check replaced(RunConfig config) {
        return check("legacy_probes", Severity.OK, "the legacy environment probes have no counterpart in this runtime",
                "python".equals(config.executionSut()) ? "python: the installed add-on probes its configured interpreter and static imports above"
                        : "python: framework stages run in this JVM; Python candidates require the explicit optional add-on",
                "core_jar, reader_jar, analyzer: one application, this JVM",
                "main_db, results_db, db_privileges, tablespace_provisioning: no database; results live in memory and checkpoints",
                "python".equals(config.executionSut()) && !config.pythonSandbox().isEmpty()
                        ? "container_runtime, sandbox: the core drives no container or OS sandbox (C08 unchanged); the Python add-on runs each candidate, "
                                + "its preprocessor and the probe above in cf.python.sandbox=" + config.pythonSandbox()
                        : "container_runtime, sandbox: the core drives no container or OS sandbox (C08 unchanged); generated Java uses per-sample class "
                                + "loaders, the Python add-on runs trusted local processes unless cf.python.sandbox selects bwrap or docker",
                "component_inventory: the application check reports this build");
    }
}
