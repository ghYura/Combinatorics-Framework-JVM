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

package org.ghyura.cf.showcase.cucumber;

import io.cucumber.core.options.CommandlineOptionsParser;
import io.cucumber.core.options.RuntimeOptions;
import io.cucumber.core.runtime.Runtime;
import io.cucumber.plugin.ConcurrentEventListener;
import io.cucumber.plugin.event.EventPublisher;
import io.cucumber.plugin.event.HookTestStep;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.Result;
import io.cucumber.plugin.event.Status;
import io.cucumber.plugin.event.TestStepFinished;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * One Gherkin scenario that a Framework candidate composes and runs, the bridge between the two: each data sheet of the
 * candidate adds a Given, When or Then line through this class, then the candidate's TAIL writes the scenario as a
 * {@code .feature} file and runs it with Cucumber in this JVM. The first step that does not pass decides the verdict:
 *
 * <ul>
 *   <li>every step passed: 0;</li>
 *   <li>a Then step failed: the verdict code that Then was given, which the generated code takes from its sheet's
 *       {@code FW_EXIT_CODE}, so the verdict names the check that failed;</li>
 *   <li>a scenario's Given or When step failed: the {@linkplain #onStepFailure step-failure code};</li>
 *   <li>a Background step or a hook failed, or a step is undefined, pending or ambiguous: an exception, so the Framework
 *       records the sample as BROKEN. Those are harness problems, never findings about the system under test.</li>
 * </ul>
 *
 * <p>Facts (the factor values the steps name) and the metrics the step definitions put on the {@link CaseBoard} make the
 * candidate's metrics line. One instance belongs to one candidate; the Cucumber run happens on the calling thread.</p>
 */
public final class GherkinCase {

    /** What a run of the scenario gave. */
    public record Outcome(int verdict, String status, String failedStep, long millis, Map<String, String> metrics) {

        /** The facts, metrics and outcome as {@code key=value} tokens for the Framework's metrics line. */
        public String metricsText() {
            StringBuilder text = new StringBuilder();
            metrics.forEach((k, v) -> text.append(text.isEmpty() ? "" : " ").append(k).append('=').append(token(v)));
            text.append(" cucumber=").append(status).append(" cucumber_ms=").append(millis);
            if (!failedStep.isEmpty()) {
                text.append(" failed_step=").append(token(failedStep));
            }
            return text.toString();
        }
    }

    private enum Kind { BACKGROUND, GIVEN, WHEN, THEN }

    private record StepLine(Kind kind, String text, int code) {
    }

    private record Strike(int after, StepLine step) {
    }

    private final String feature;
    private final String caseId;
    private final Map<String, String> placeholders = new LinkedHashMap<>();
    private final Map<String, String> facts = new LinkedHashMap<>();
    private final List<String> background = new ArrayList<>();
    private final List<StepLine> steps = new ArrayList<>();
    private final List<Strike> strikes = new ArrayList<>();
    private int stepFailureCode = 1;

    /**
     * @param feature the feature's name
     * @param caseId  the candidate's display id, which names the scenario and its file
     */
    public GherkinCase(String feature, String caseId) {
        this.feature = Objects.requireNonNull(feature, "feature");
        this.caseId = Objects.requireNonNull(caseId, "caseId").replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    public String caseId() {
        return caseId;
    }

    /** Text {@code {name}} in the Background is replaced by the value (a base URL the bootstrap prepared, for instance). */
    public GherkinCase placeholder(String name, String value) {
        placeholders.put("{" + name + "}", value);
        return this;
    }

    /** The verdict of a scenario whose Given or When step fails (an action the system refused, a page that broke). */
    public void onStepFailure(int code) {
        stepFailureCode = code;
    }

    /** Background lines as written (a FW_File fragment), "Background:" and its steps; blank lines are dropped. */
    public void background(String gherkin) {
        for (String line : gherkin.split("\\R")) {
            if (!line.isBlank()) {
                String text = line.strip();
                for (Map.Entry<String, String> p : placeholders.entrySet()) {
                    text = text.replace(p.getKey(), p.getValue());
                }
                background.add(text);
            }
        }
    }

    /** A Given step: {@code template} with {@code %s} for the value; an empty value (an empty group) reads "none". */
    public void given(String factor, String template, String value) {
        step(Kind.GIVEN, factor, template, value);
    }

    public void when(String factor, String template, String value) {
        step(Kind.WHEN, factor, template, value);
    }

    /** Several When steps, one per value of an ordered group joined with {@code |}, in their order. */
    public void whenEach(String factor, String template, String joined) {
        List<String> values = joined.isEmpty() ? List.of() : List.of(joined.split("\\|"));
        fact(factor, values.isEmpty() ? "none" : String.join(">", values));
        for (String value : values) {
            steps.add(new StepLine(Kind.WHEN, String.format(Locale.ROOT, template, value.strip()), 0));
        }
    }

    /**
     * A sudden action that strikes at a moment of the action sequence: {@code when} is "after N", and the step is woven in
     * right after the N-th When step of the timeline (the ordered actions and the When steps that follow them), whatever
     * those actions turn out to be in this test case. Two sudden actions at one moment keep the order of their sheets.
     */
    public void strike(String factor, String template, String when) {
        String w = when.strip();
        if (!w.matches("after [1-9][0-9]*")) {
            throw new HarnessProblem("a sudden action strikes \"after N\", not \"" + when + "\"");
        }
        fact(factor, w);
        strikes.add(new Strike(Integer.parseInt(w.substring("after ".length())), new StepLine(Kind.WHEN, template, 0)));
    }

    /** A Then step, an assertion, with the verdict it gives when it fails. */
    public void then(String text, int verdictCode) {
        steps.add(new StepLine(Kind.THEN, text, verdictCode));
    }

    /** The steps in the order they run: each sudden action woven in after the When step it strikes after. */
    private List<StepLine> timeline() {
        List<StepLine> out = new ArrayList<>();
        int whens = 0;
        for (StepLine s : steps) {
            out.add(s);
            if (s.kind() == Kind.WHEN) {
                whens++;
                for (Strike k : strikes) {
                    if (k.after() == whens) {
                        out.add(k.step());
                    }
                }
            }
        }
        for (Strike k : strikes) {
            if (k.after() > whens) {
                throw new HarnessProblem("'" + k.step().text() + "' strikes after When step " + k.after() + ", but the scenario has "
                        + whens);
            }
        }
        return out;
    }

    private void step(Kind kind, String factor, String template, String value) {
        String shown = value.isEmpty() ? "none" : value;
        fact(factor, shown);
        steps.add(new StepLine(kind, String.format(Locale.ROOT, template, shown), 0));
    }

    /** A fact for the metrics line and the tags, such as the "none" an absent optional sheet stands for. */
    public void fact(String factor, String value) {
        if (factor != null && !factor.isEmpty()) {
            facts.put(factor, value);
        }
    }

    /** The facts as {@code key=value} tokens. */
    public String facts() {
        StringBuilder text = new StringBuilder();
        facts.forEach((k, v) -> text.append(text.isEmpty() ? "" : " ").append(k).append('=').append(token(v)));
        return text.toString();
    }

    /** The whole .feature text: tags from the facts, the Background, then one Scenario. */
    public String featureText() {
        return render(new LinkedHashMap<>());
    }

    private String render(Map<Integer, StepLine> lines) {
        List<String> out = new ArrayList<>();
        out.add("# Generated by the Combinatorics Framework JVM: one candidate, one scenario (case " + caseId + ").");
        out.add("Feature: " + feature);
        out.add("");
        for (String line : background) {
            int at = out.size() + 1;
            boolean header = line.startsWith("Background:");
            boolean table = line.startsWith("|");
            out.add((header ? "  " : table ? "      " : "    ") + line);
            if (!header && !table && !line.startsWith("#")) {
                lines.put(at, new StepLine(Kind.BACKGROUND, line, 0));
            }
        }
        out.add("");
        StringBuilder tags = new StringBuilder("  @cf-generated");
        facts.forEach((k, v) -> tags.append(" @").append(k).append('-').append(v.replaceAll("[^A-Za-z0-9_+.>-]", "_")));
        out.add(tags.toString());
        out.add("  Scenario: case " + caseId + (facts.isEmpty() ? "" : " (" + facts() + ")"));
        Kind previous = null;
        for (StepLine s : timeline()) {
            String keyword = s.kind() == previous ? "And" : switch (s.kind()) {
                case GIVEN, BACKGROUND -> "Given";
                case WHEN -> "When";
                case THEN -> "Then";
            };
            previous = s.kind();
            lines.put(out.size() + 1, s);
            out.add("    " + keyword + " " + s.text());
        }
        return String.join("\n", out) + "\n";
    }

    /** Creates the folder for generated features and empties it of earlier ones; the bootstrap calls it once per run. */
    public static String prepareFolder(String folder) throws IOException {
        Path dir = Path.of(folder).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        try (DirectoryStream<Path> old = Files.newDirectoryStream(dir, "*.{feature,xml}")) {
            for (Path p : old) {
                Files.delete(p);
            }
        }
        return dir.toString();
    }

    /**
     * Writes the scenario to {@code <folder>/<caseId>.feature} and runs it with Cucumber, the glue taken from the package.
     *
     * @throws IllegalStateException for a harness problem (see the class description): the sample is then BROKEN
     */
    public Outcome run(String folder, String gluePackage) {
        Map<Integer, StepLine> lines = new LinkedHashMap<>();
        String text = render(lines);
        Path file = Path.of(folder).resolve(caseId + ".feature");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, text, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
        FirstProblem listener = new FirstProblem();
        // the Cucumber report goes to a JUnit XML file beside the feature, not to the captured output, so the run's
        // checkpoint stays small however many test cases there are
        Path junit = file.resolveSibling(caseId + ".junit.xml");
        RuntimeOptions options = new CommandlineOptionsParser(System.out)
                .parse("--glue", gluePackage, "--plugin", "junit:" + junit, "--monochrome", file.toString())
                .build();
        // Cucumber finds its Gherkin parser and backends with ServiceLoader through the thread's context class loader; in
        // the Framework that is the application's loader, which cannot see cf.execution.classpath, so lend it ours
        ClassLoader loader = GherkinCase.class.getClassLoader();
        Thread thread = Thread.currentThread();
        ClassLoader previous = thread.getContextClassLoader();
        Map<String, String> board = CaseBoard.open();
        long started = System.nanoTime();
        try {
            thread.setContextClassLoader(loader);
            Runtime.builder().withRuntimeOptions(options).withClassLoader(() -> loader).withAdditionalPlugins(listener).build().run();
        } finally {
            thread.setContextClassLoader(previous);
            CaseBoard.close();
        }
        long millis = (System.nanoTime() - started) / 1_000_000;
        Map<String, String> metrics = new LinkedHashMap<>(facts);
        metrics.putAll(board);
        if (listener.problem == null) {
            if (listener.passed == 0) {
                throw new IllegalStateException("Cucumber ran no step of " + file);
            }
            return new Outcome(0, "passed", "", millis, metrics);
        }
        Problem p = listener.problem;
        String where = file.getFileName() + ":" + p.line();
        if (p.status() != Status.FAILED) {
            throw new IllegalStateException("glue mismatch at " + where + ": the step '" + p.text() + "' is " + p.status());
        }
        StepLine step = p.hook() ? null : lines.get(p.line());
        if (p.harness()) {
            throw new IllegalStateException("the harness failed at " + where + ": " + p.error());
        }
        if (step == null || step.kind() == Kind.BACKGROUND) {
            throw new IllegalStateException("the harness failed at " + where + " (" + (p.hook() ? "hook" : "Background step '" + p.text() + "'")
                    + "): " + p.error());
        }
        int verdict = step.kind() == Kind.THEN ? step.code() : stepFailureCode;
        return new Outcome(verdict, "failed", step.text(), millis, metrics);
    }

    /** A value as one metrics-line token: no spaces and none of {@code ; | , ] ) }}. */
    static String token(String value) {
        return value.replaceAll("[\\s;|,\\])}=]+", "_");
    }

    private record Problem(Status status, boolean hook, int line, String text, String error, boolean harness) {
    }

    /** Remembers the first step or hook that did not pass. */
    private static final class FirstProblem implements ConcurrentEventListener {
        private Problem problem;
        private int passed;

        @Override
        public void setEventPublisher(EventPublisher publisher) {
            publisher.registerHandlerFor(TestStepFinished.class, this::finished);
        }

        private synchronized void finished(TestStepFinished event) {
            Result result = event.getResult();
            Status status = result.getStatus();
            if (status == Status.PASSED) {
                passed += event.getTestStep() instanceof PickleStepTestStep ? 1 : 0;
                return;
            }
            if (problem != null || status == Status.SKIPPED || status == Status.UNUSED) {
                return;
            }
            Throwable thrown = result.getError();
            String error = thrown == null ? "" : thrown.getClass().getSimpleName() + ": " + thrown.getMessage();
            boolean harness = thrown instanceof HarnessProblem;
            if (event.getTestStep() instanceof PickleStepTestStep s) {
                problem = new Problem(status, false, s.getStep().getLine(), s.getStep().getText(), error, harness);
            } else if (event.getTestStep() instanceof HookTestStep h) {
                problem = new Problem(status, true, -1, h.getHookType().toString(), error, harness);
            }
        }
    }
}
