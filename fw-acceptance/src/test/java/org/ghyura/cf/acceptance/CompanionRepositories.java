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

import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Bounded, read-only discovery of optional acceptance-test inputs; also usable as a standalone setup helper. */
public final class CompanionRepositories {
    enum Kind {
        QA("QA/legacy", "cf.qa.root", "Combinatorics-Framework-QA-edition", List.of("Core_trunk", "generator_trunk")),
        SUT("SUT", "cf.sut.root", "SUT", List.of("combination_thinking_tutor", "automation-scheme-studio", "FW_scenario_inputs"));

        final String label;
        final String property;
        final String folder;
        final List<String> markers;

        Kind(String label, String property, String folder, List<String> markers) {
            this.label = label;
            this.property = property;
            this.folder = folder;
            this.markers = markers;
        }
    }

    record Result(Path root, String detail) { }

    private CompanionRepositories() { }

    static Result discover(Path reactor, Kind kind, String explicit) {
        reactor = reactor.toAbsolutePath().normalize();
        if (explicit != null && !explicit.isBlank()) {
            Path selected = fromHint(reactor, kind, explicit);
            return new Result(selected, selected == null ? "The supplied -D" + kind.property + " path is not a readable checkout." : "explicit path");
        }
        Path parent = reactor.getParent();
        if (parent == null) return new Result(null, "No parent workspace to search.");
        Path sibling = checkout(parent.resolve(kind.folder), kind);
        if (sibling != null) return new Result(sibling, "sibling checkout");

        Set<Path> found = new LinkedHashSet<>();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        int remaining = 128;
        for (Path container = parent; container != null && remaining > 0; container = container == parent ? parent.getParent() : null) {
            Path direct = checkout(container.resolve(kind.folder), kind);
            if (direct != null) found.add(direct);
            List<Path> children = new ArrayList<>();
            try (DirectoryStream<Path> entries = Files.newDirectoryStream(container)) {
                for (Path child : entries) {
                    if (remaining-- <= 0 || System.nanoTime() >= deadline) break;
                    if (!child.getFileName().toString().startsWith(".") && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) children.add(child);
                }
            } catch (IOException | SecurityException ignored) {
                // An inaccessible nearby folder does not prevent the rest of the build.
            }
            children.sort(Path::compareTo);
            for (Path child : children) {
                if (System.nanoTime() >= deadline) break;
                Path candidate = checkout(child.resolve(kind.folder), kind);
                if (candidate != null) found.add(candidate);
            }
            if (System.nanoTime() >= deadline) break;
        }
        if (found.size() == 1) return new Result(found.iterator().next(), "nearby workspace");
        return new Result(null, found.isEmpty() ? "No checkout found in the bounded nearby search."
                : "Several nearby checkouts found (" + found.size() + "); select one explicitly. First matches: "
                        + found.stream().limit(5).toList());
    }

    private static Path checkout(Path candidate, Kind kind) {
        try {
            if (Files.isReadable(candidate) && kind.markers.stream().anyMatch(marker -> Files.isDirectory(candidate.resolve(marker)))) {
                return candidate.toRealPath();
            }
        } catch (IOException | SecurityException ignored) { }
        return null;
    }

    static Path fromHint(Path reactor, Kind kind, String text) {
        try {
            String hint = text.strip();
            if (hint.length() >= 2 && ((hint.startsWith("\"") && hint.endsWith("\"")) || (hint.startsWith("'") && hint.endsWith("'")))) {
                hint = hint.substring(1, hint.length() - 1);
            }
            Path path = reactor.resolve(Path.of(hint)).toAbsolutePath().normalize();
            if (!Files.exists(path)) return null;
            for (int depth = 0; path != null && depth < 8; depth++, path = path.getParent()) {
                Path root = checkout(path, kind);
                if (root != null) return root;
            }
        } catch (IllegalArgumentException | SecurityException ignored) { }
        return null;
    }

    /** Never starts a blocked input thread or closes the user's input stream. */
    static String reply(Reader input, long timeoutMillis) throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        StringBuilder line = new StringBuilder();
        while (System.nanoTime() < deadline) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            if (!input.ready()) {
                Thread.sleep(Math.min(20, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
                continue;
            }
            int next = input.read();
            if (next < 0 || next == '\n') return line.toString().strip();
            if (next != '\r') {
                if (line.length() >= 4096) return null;
                line.appendCodePoint(next);
            }
        }
        return null;
    }

    static Result locate(Path reactor, Kind kind, String explicit, Reader input, PrintStream out, long timeoutMillis) {
        Result result = discover(reactor, kind, explicit);
        if (result.root() != null) {
            out.println("Optional " + kind.label + " checkout: " + result.root() + " (" + result.detail() + ")");
            return result;
        }
        String help = "MISSING_AUTHORIZED_BACKEND: optional " + kind.label + " checkout unavailable. " + result.detail()
                + " Clone or unpack it beside the JVM checkout as " + kind.folder + ", or set -D" + kind.property
                + "=/absolute/path (a checkout directory or a file within it).";
        out.println(help);
        if (input != null && timeoutMillis > 0) {
            out.print("Enter its path, or Enter to skip (" + timeoutMillis / 1000 + " seconds): ");
            out.flush();
            try {
                String answer = reply(input, timeoutMillis);
                if (answer != null && !answer.isBlank()) {
                    Path selected = fromHint(reactor, kind, answer);
                    if (selected != null) return new Result(selected, "user-selected path");
                    help += " The entered path was not a readable checkout.";
                } else {
                    help += answer == null ? " Input timed out." : " No path supplied.";
                }
            } catch (IOException e) {
                help += " Input unavailable.";
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                help += " Input interrupted.";
            }
        } else {
            help += " No interactive wait; rerun with the path option, or run the standalone setup helper described in docs/OPTIONAL_TEST_CHECKOUTS.md.";
        }
        out.println("Skipping dependent " + kind.label + " checks; supply -D" + kind.property + " when ready.");
        return new Result(null, help);
    }

    static long promptMillis() {
        return TimeUnit.SECONDS.toMillis(Math.clamp(Integer.getInteger("cf.companions.prompt.seconds", 15), 0, 60));
    }

    /** Source-launch this file from a terminal to locate inputs before running Maven, whose forks often have no console. */
    public static void main(String[] args) {
        Path reactor = Path.of(args.length == 0 ? "." : args[0]).toAbsolutePath().normalize();
        Console console = System.console();
        for (Kind kind : Kind.values()) {
            Result result = locate(reactor, kind, System.getProperty(kind.property),
                    console == null ? null : new InputStreamReader(System.in, console.charset()),
                    System.err, promptMillis());
            if (result.root() != null) {
                String option = "-D" + kind.property + "=" + result.root();
                System.out.println("'" + option.replace("'", "'\"'\"'") + "'");
            }
        }
    }
}
