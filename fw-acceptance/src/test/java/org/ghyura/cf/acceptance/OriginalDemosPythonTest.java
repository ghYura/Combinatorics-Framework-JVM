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

import org.ghyura.cf.campaign.RunService;
import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.input.ScenarioLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The original demos run their own Python unchanged (author decisions 2026-10-12: the Python add-on, and Claude wiring the
 * legacy usage examples): every ported demo variant, whose scenario is a byte-identical copy of the original SmallUseCasesDemo
 * spec with its audited sut.py, oracle.py and runtime.py inlined, runs twice with the same settings, through its Java port
 * and through its own Python with cf.execution.sut=python, and every candidate has the same outcome and verdict code in both.
 * The Java ports stay as they are and are the independent check. D14b is not here: its original Python needs the pg8000
 * library and a PostgreSQL fixture this machine's Python lacks (recorded in the 0120 dependency matrix).
 */
class OriginalDemosPythonTest {

    private static final Path DEMOS = Reactor.root().resolve("fw-sut-fixtures/src/main/resources/org/ghyura/cf/fixtures/demos");

    /** Needs what this machine lacks: the pg8000 library for its PostgreSQL fixture. */
    private static final List<String> NEEDS_MORE = List.of("d14b");

    @BeforeAll
    static void needsPython() {
        boolean found = false;
        for (String dir : System.getenv().getOrDefault("PATH", "").split(File.pathSeparator)) {
            found |= !dir.isEmpty() && Files.isExecutable(Path.of(dir, "python3"));
        }
        Assumptions.assumeTrue(found, "MISSING_AUTHORIZED_BACKEND: no python3 on PATH, so the Python add-on the author requires "
                + "(decision 2026-10-12) cannot be verified here");
    }

    /** Every demo variant with its own run settings, as the Java ports run them. */
    static Stream<String> variants() throws IOException {
        List<String> names = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(DEMOS)) {
            walk.filter(p -> p.getFileName().toString().equals("run.properties")).forEach(p -> names.add(DEMOS.relativize(p.getParent()).toString()));
        }
        names.removeAll(NEEDS_MORE);
        return names.stream().sorted();
    }

    /** One sample's result: its outcome and, for a verdict, its code. */
    private record Result(String outcome, Integer verdict) {
    }

    private static Map<String, Result> run(Path dir, Map<String, String> settings) throws Exception {
        List<ConfigResolver.Override> overrides = new ArrayList<>();
        settings.forEach((k, v) -> overrides.add(new ConfigResolver.Override(k, v, "demo run settings")));
        overrides.add(new ConfigResolver.Override("cf.execution.workers", "8", "test"));
        var config = ConfigResolver.resolve(null, Map.of(), overrides);
        Map<String, Result> results = new TreeMap<>();
        try (var run = RunService.prepare(ScenarioLoader.load(dir.resolve("spec.toml")).spec(), config)) {
            var s = run.await();
            assertEquals(RunState.COMPLETED, s.state(), settings.get("cf.execution.sut") + ": " + s.diagnostics());
            for (var o : run.finals()) {
                assertNull(results.put(o.display(), new Result(o.outcome().name(), o.verdictCode().orElse(null))), "each case once: " + o.display());
            }
        }
        return results;
    }

    @ParameterizedTest
    @MethodSource("variants")
    void everyCaseOfAnOriginalDemoHasTheSameOutcomeAndVerdictFromItsOwnPythonAsFromItsJavaPort(String variant, @TempDir Path dir) throws Exception {
        Path source = DEMOS.resolve(variant);
        try (Stream<Path> files = Files.list(source)) {
            for (Path f : files.filter(Files::isRegularFile).toList()) {
                Files.copy(f, dir.resolve(f.getFileName()));
            }
        }
        Properties properties = new Properties();
        try (var in = Files.newInputStream(source.resolve("run.properties"))) {
            properties.load(in);
        }
        Map<String, String> javaPort = new TreeMap<>();
        properties.stringPropertyNames().forEach(k -> javaPort.put(k, properties.getProperty(k).trim()));
        javaPort.remove("cf.execution.classpath");   // the fixtures are on this test's class path
        Map<String, String> original = new TreeMap<>(javaPort);
        original.put("cf.execution.sut", "python");
        Map<String, Result> java = run(dir, javaPort);
        Map<String, Result> python = run(dir, original);
        assertTrue(!java.isEmpty(), variant + " ran no case");
        assertEquals(java.keySet(), python.keySet(), variant + ": the same cases");
        List<String> differing = new ArrayList<>();
        java.forEach((id, r) -> {
            if (!r.equals(python.get(id))) {
                differing.add(id + " java " + r + " python " + python.get(id));
            }
        });
        assertEquals(List.of(), differing.size() > 10 ? differing.subList(0, 10) : differing, variant + ": " + differing.size()
                + " of " + java.size() + " cases differ");
    }
}
