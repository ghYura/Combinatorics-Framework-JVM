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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Finds the files of the reactor the acceptance gates inspect. The reactor root arrives as a system property. */
final class Reactor {

    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", ".git", ".idea", ".mvn");
    private static final Map<String, CompanionRepositories.Result> COMPANIONS = new HashMap<>();

    private Reactor() {
    }

    static Path root() {
        String property = System.getProperty("cf.reactor.root");
        if (property == null || property.isBlank()) {
            throw new IllegalStateException("system property cf.reactor.root is not set; run the gates through Maven");
        }
        Path root = Path.of(property).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("pom.xml"))) {
            throw new IllegalStateException("cf.reactor.root does not point at the reactor root: " + root);
        }
        return root;
    }

    /** Resolve each optional repository once per build, with an actionable, classified skip if unavailable. */
    static synchronized Path companion(CompanionRepositories.Kind kind, String required) {
        Path reactor = root();
        String explicit = System.getProperty(kind.property);
        String key = reactor + "|" + kind + "|" + explicit;
        CompanionRepositories.Result result = COMPANIONS.computeIfAbsent(key, ignored -> {
            var console = System.console();
            return CompanionRepositories.locate(reactor, kind, explicit,
                    console == null ? null : new java.io.InputStreamReader(System.in, console.charset()),
                    System.err, CompanionRepositories.promptMillis());
        });
        org.junit.jupiter.api.Assumptions.assumeTrue(result.root() != null, result.detail());
        Path root = result.root();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isReadable(root.resolve(required)),
                "MISSING_AUTHORIZED_BACKEND: optional " + kind.label + " checkout " + root + " lacks readable " + required
                        + "; select a complete checkout with -D" + kind.property + "=/absolute/path.");
        return root;
    }

    /** Names of the {@code fw-*} directories that hold a pom, sorted. */
    static List<String> modules() {
        try (Stream<Path> children = Files.list(root())) {
            return children.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().startsWith("fw-"))
                    .filter(p -> Files.isRegularFile(p.resolve("pom.xml")))
                    .map(p -> p.getFileName().toString())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every file under the reactor with one of the suffixes, skipping build output and VCS data. */
    static List<Path> files(String... suffixes) {
        Path root = root();
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).filter(p -> {
                for (Path part : root.relativize(p)) {
                    if (SKIPPED_DIRECTORIES.contains(part.toString())) {
                        return false;
                    }
                }
                String name = p.getFileName().toString();
                for (String suffix : suffixes) {
                    if (name.endsWith(suffix)) {
                        return true;
                    }
                }
                return false;
            }).sorted().forEach(out::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** All {@code .java} files, including the tools directory. */
    static List<Path> javaSources() {
        return files(".java");
    }

    /**
     * Compiled classes of every module: {@code target/classes} when {@code main} is true, otherwise
     * {@code target/test-classes}. A module without the directory contributes nothing; callers assert that the
     * total is not empty, so a scan can never pass vacuously.
     */
    static List<Path> classFiles(boolean main) {
        Path root = root();
        List<Path> out = new ArrayList<>();
        for (String module : modules()) {
            Path dir = root.resolve(module).resolve("target").resolve(main ? "classes" : "test-classes");
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dir)) {
                walk.filter(p -> p.getFileName().toString().endsWith(".class")).sorted().forEach(out::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return out;
    }
}
