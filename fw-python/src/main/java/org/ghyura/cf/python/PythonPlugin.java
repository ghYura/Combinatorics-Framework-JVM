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

package org.ghyura.cf.python;

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.OutputCapture;
import org.ghyura.cf.spi.RunInputs;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;
import org.ghyura.cf.spi.SutUnavailable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The Python add-on's plugin, {@code cf.execution.sut=python} (capability C07, author decision 2026-10-12): every
 * candidate is the Python the framework rendered, every value's line breaks kept, run the way the legacy executor ran it
 * (Executor_trunk py_executor.run_candidate) in the legacy trusted-local mode, with no sandbox:
 * <ol>
 *   <li>the candidate is written to a file of its own, named after its display id, in a fresh temporary directory;</li>
 *   <li>a Python FW_RunMeFirstOnce runs first as {@code python <it> <candidate file>}, for at most 10 seconds, and may
 *       patch the file; whatever it does, the candidate runs next. A Java RunMeFirstOnce stub, which the legacy Reader
 *       attached to Python hand-overs too, is ignored as legacy ignored it;</li>
 *   <li>{@code python -c <runner> <candidate file> <FW_Arguments cells...>} runs it as {@code __main__} in
 *       {@code cf.python.workdir} (by default the scenario's directory), with {@code cf.python.path} before PYTHONPATH;
 *       the runner prints {@code __FWV__ <FW_VAR> <FW_CUSTOM_VAR>}, -999 for a variable the candidate did not set.</li>
 * </ol>
 * The verdict is FW_CUSTOM_VAR under {@code cf.execution.verdict=FW_CUSTOM_VAR} and FW_VAR otherwise: 0 passes, any
 * other code is a domain failure kept exactly. No marker (an exception, a syntax error, an empty candidate) is no
 * verdict, BROKEN; an interpreter that cannot be started is INFRA_FAIL; the run's deadline kills the process, TIMEOUT.
 * Standard output and error go to the sample's bounded capture, and the legacy {@code app=} metrics line becomes
 * metrics. Python writes no bytecode caches (PYTHONDONTWRITEBYTECODE), so the code under test stays as it was.
 */
public final class PythonPlugin implements SutPlugin, org.ghyura.cf.spi.SutDoctor {

    /** The plugin's id, the value of {@code cf.execution.sut}. */
    public static final String ID = "python";

    /** The legacy runner (py_executor {@code _RUNNER}), unchanged. */
    static final String RUNNER = "import runpy,sys\n"
            + "ns = runpy.run_path(sys.argv[1], run_name='__main__')\n"
            + "print('__FWV__ %d %d' % (int(ns.get('FW_VAR', -999)), int(ns.get('FW_CUSTOM_VAR', -999))))\n";

    /** How long a Python FW_RunMeFirstOnce may work on a candidate (the legacy preprocess timeout). */
    static final long PREPROCESS_MILLIS = 10_000;

    private static final Pattern JAVA_STUB = Pattern.compile("\\bclass\\s+RunMeFirstOnce\\b");

    private final RunInputs inputs;
    private final String executable;
    private final Path workdir;
    private final Optional<String> preprocess;
    private final boolean customVerdict;
    private final Map<String, String> environment;
    private final Sandbox sandbox;

    /** The plugin as ServiceLoader finds it: no scenario inputs yet, the default interpreter. */
    public PythonPlugin() {
        this(new RunInputs(List.of(), Optional.empty(), "FW_VAR", Map.of(), Optional.empty(), Map.of()));
    }

    PythonPlugin(RunInputs inputs) {
        this.inputs = inputs;
        String configured = inputs.settings().getOrDefault("cf.python.executable", "").trim();
        this.executable = configured.isEmpty() ? "python3" : configured;
        String dir = inputs.settings().getOrDefault("cf.python.workdir", "").trim();
        Optional<Path> scenarioTree = inputs.scenarioDirectory().flatMap(PythonPlugin::generatorTree);
        this.workdir = !dir.isEmpty() ? Path.of(dir).toAbsolutePath().normalize()
                : scenarioTree.or(inputs::scenarioDirectory).orElse(Path.of("").toAbsolutePath());
        Optional<Path> tree = generatorTree(workdir);   // the working directory's tree: as the scenario chose it, or configured
        this.preprocess = inputs.runMeFirstOnce().filter(text -> !isJavaStub(text));
        this.customVerdict = "FW_CUSTOM_VAR".equals(inputs.verdictVariable());
        this.environment = environment(inputs.settings().getOrDefault("cf.python.path", ""), tree,
                inputs.settings().getOrDefault("cf.python.env", ""));
        this.sandbox = Sandbox.of(inputs.settings().getOrDefault("cf.python.sandbox", ""));
    }

    /** The project directories the legacy sut_paths.py knows: one of them marks a directory as a SUT root. */
    private static final List<String> LEGACY_SUT_PROJECTS = List.of("advanced_surrogate", "automation-scheme-studio", "telemetry_catalog_service",
            "fin_tech_to_test", "GPT-4-Lite_sandbox", "legacy_surrogate_apps", "llm_transformer_testme", "3Dprofile-VS-2Dsieve");

    /**
     * The legacy generator tree a scenario lies in: the nearest ancestor directory that holds sut_paths.py, the directory the
     * legacy candidates searched for themselves. Its scenarios run as the legacy bundle ran them (see {@link #environment}).
     */
    static Optional<Path> generatorTree(Path scenarioDirectory) {
        for (Path p = scenarioDirectory.toAbsolutePath().normalize(); p != null; p = p.getParent()) {
            if (Files.isRegularFile(p.resolve("sut_paths.py"))) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    /**
     * The legacy SUT root (sut_paths.discovered_sut_root): the repository's suts/ when it holds a known project, else a sibling
     * SUT or SUT-main checkout that does, else suts/.
     */
    static Path legacySutRoot(Path repository) {
        Path inTree = repository.resolve("suts");
        if (LEGACY_SUT_PROJECTS.stream().anyMatch(d -> Files.isDirectory(inTree.resolve(d)))) {
            return inTree;
        }
        Path parent = repository.getParent();
        for (String sibling : List.of("SUT", "SUT-main")) {
            Path candidate = parent == null ? null : parent.resolve(sibling);
            if (candidate != null && LEGACY_SUT_PROJECTS.stream().anyMatch(d -> Files.isDirectory(candidate.resolve(d)))) {
                return candidate;
            }
        }
        return inTree;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public Map<Integer, String> verdictNames() {
        return inputs.verdictNames();
    }

    @Override
    public boolean harvestsMetricLines() {
        return true;
    }

    /** The rendered source is the program that runs, so results record its SHA-256 as the legacy recorded the file's. */
    @Override
    public boolean runsCandidateSource() {
        return true;
    }

    @Override
    public SutPlugin forRun(RunInputs runInputs) {
        return new PythonPlugin(runInputs);
    }

    @Override
    public List<org.ghyura.cf.spi.SutDoctor.Check> diagnose(RunInputs inputs, List<String> sources) {
        PythonPlugin configured = new PythonPlugin(inputs);
        return PythonDoctor.probe(configured.executable, configured.workdir, configured.environment, configured.sandbox, sources);
    }

    /** The interpreter this plugin starts. */
    String executable() {
        return executable;
    }

    /** Where every candidate runs. */
    Path workdir() {
        return workdir;
    }

    @Override
    public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override
            public DomainObservation evaluate(SutCandidate candidate, ExecutionContext sample) throws Exception {
                return run(candidate, sample);
            }

            @Override
            public void close() {
                // each candidate's files are removed as soon as it ran
            }
        };
    }

    /** Whether FW_RunMeFirstOnce is the Java stub the legacy Reader attached to every hand-over (py_executor ignores it). */
    static boolean isJavaStub(String text) {
        return JAVA_STUB.matcher(text).find() && text.contains("public static");
    }

    private DomainObservation run(SutCandidate candidate, ExecutionContext context) throws Exception {
        String source = candidate.source();
        if (source.isEmpty()) {
            return null;   // an empty candidate prints no verdict (py_executor: BROKEN)
        }
        Path dir = Files.createTempDirectory("cf-python-");
        try {
            Path file = dir.resolve(fileName(candidate.displayId()) + ".py");
            Files.writeString(file, source, StandardCharsets.UTF_8);
            if (preprocess.isPresent()) {
                Path first = dir.resolve("runmefirstonce.first");
                Files.writeString(first, preprocess.get(), StandardCharsets.UTF_8);
                String once = Sandbox.newName();
                PythonProcess.Result patched = start(sandbox.wrap(List.of(executable, first.toString(), file.toString()), workdir,
                        Optional.of(dir), environment, once), TimeUnit.MILLISECONDS.toNanos(PREPROCESS_MILLIS), context, text -> { }, text -> { }, once);
                if (patched.end() == PythonProcess.End.CANCELLED) {
                    throw new InterruptedException("the sample was cancelled while FW_RunMeFirstOnce ran");
                }
                // a failing or slow preprocessor leaves the candidate as it was, as legacy did
            }
            List<String> command = new ArrayList<>(List.of(executable, "-c", RUNNER, file.toString()));
            command.addAll(inputs.arguments());
            VerdictMarker marker = new VerdictMarker();
            OutputCapture output = context.output();
            StringBuilder stderrTail = new StringBuilder();
            String name = Sandbox.newName();
            PythonProcess.Result ran = start(sandbox.wrap(command, workdir, Optional.of(dir), environment, name), Long.MAX_VALUE, context, text -> {
                marker.accept(text);
                output.stdout(text);
            }, text -> {
                output.stderr(text);
                stderrTail.append(text);
                if (stderrTail.length() > 4_000) {
                    stderrTail.delete(0, stderrTail.length() - 2_000);
                }
            }, name);
            if (ran.end() != PythonProcess.End.EXITED) {
                throw new InterruptedException("the sample was cancelled while its candidate ran");
            }
            marker.end();
            Optional<int[]> values = marker.values();
            if (values.isEmpty() && sandbox.failedToStart(ran.exitCode(), stderrTail.toString())) {
                throw new SutUnavailable("the Python sandbox " + sandbox + " could not run the candidate (exit " + ran.exitCode() + "): "
                        + stderrTail.toString().strip().lines().reduce((a, b) -> b).orElse(""), null);
            }
            if (values.isEmpty()) {
                return null;   // no verdict marker: the candidate raised before its end or is not runnable (BROKEN)
            }
            return DomainObservation.of(customVerdict ? values.get()[1] : values.get()[0]);
        } finally {
            delete(dir);
        }
    }

    /**
     * Starts a command, naming the sandbox program when it is the sandbox that cannot be started. Whenever it does not end by
     * itself (a limit, a cancellation, an interrupted worker), what the sandbox left behind is removed: a container outlives
     * its killed docker client.
     */
    private PythonProcess.Result start(List<String> command, long limitNanos, ExecutionContext context, java.util.function.Consumer<String> stdout,
                                       java.util.function.Consumer<String> stderr, String name) throws SutUnavailable, InterruptedException {
        if (context.cancellation().isCancelled()) {
            return new PythonProcess.Result(PythonProcess.End.CANCELLED, -1);   // nothing started, nothing to remove
        }
        boolean ended = false;
        try {
            PythonProcess.Result result = PythonProcess.run(command, workdir, environment, limitNanos, context.cancellation(), stdout, stderr);
            ended = result.end() == PythonProcess.End.EXITED;
            return result;
        } catch (SutUnavailable e) {
            if (sandbox.isNone()) {
                throw e;
            }
            throw new SutUnavailable("the Python sandbox program '" + command.getFirst() + "' (cf.python.sandbox " + sandbox
                    + ") could not be started in " + workdir, e);
        } finally {
            if (!ended) {
                sandbox.cleanUp(name, workdir, environment);
            }
        }
    }

    /** The legacy candidate file name: its display id, every character outside [A-Za-z0-9_-] replaced. */
    static String fileName(String displayId) {
        String name = displayId.replaceAll("[^A-Za-z0-9_-]", "_");
        return name.isEmpty() ? "candidate" : name;
    }

    /**
     * The candidates' environment over the inherited one: PYTHONPATH with cf.python.path first; inside a legacy generator tree the
     * legacy bundle's settings (bundle/stages.py): the tree and its repository on PYTHONPATH, BUNDLE_REPO_ROOT and BUNDLE_SUT_ROOT
     * unless the inherited environment sets them; then every cf.python.env assignment, which wins over everything.
     */
    private static Map<String, String> environment(String pythonPath, Optional<Path> tree, String assignments) {
        Map<String, String> env = new HashMap<>();
        List<String> entries = new ArrayList<>();
        if (!pythonPath.isBlank()) {
            entries.add(pythonPath);
        }
        tree.ifPresent(t -> {
            entries.add(t.toString());
            if (t.getParent() != null) {
                entries.add(t.getParent().toString());
            }
        });
        String inherited = System.getenv("PYTHONPATH");
        if (inherited != null && !inherited.isEmpty()) {
            entries.add(inherited);
        }
        if (!entries.isEmpty()) {
            env.put("PYTHONPATH", String.join(File.pathSeparator, entries));
        }
        env.put("PYTHONDONTWRITEBYTECODE", "1");   // no __pycache__ beside the code under test
        if (System.getenv("PYTHONIOENCODING") == null) {
            env.put("PYTHONIOENCODING", "utf-8");   // the output is read as UTF-8 whatever the locale
        }
        tree.map(Path::getParent).ifPresent(repository -> {
            if (System.getenv("BUNDLE_REPO_ROOT") == null) {
                env.put("BUNDLE_REPO_ROOT", repository.toString());
            }
            if (System.getenv("BUNDLE_SUT_ROOT") == null) {
                env.put("BUNDLE_SUT_ROOT", legacySutRoot(repository).toString());
            }
        });
        for (String assignment : assignments.split(";")) {
            int eq = assignment.indexOf('=');
            if (eq > 0) {
                env.put(assignment.substring(0, eq).trim(), assignment.substring(eq + 1).trim());
            }
        }
        return Map.copyOf(env);
    }

    private static void delete(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(p);
            }
        } catch (IOException e) {
            // a file the candidate left open or locked stays in the temporary directory; nothing depends on it
        }
    }
}
