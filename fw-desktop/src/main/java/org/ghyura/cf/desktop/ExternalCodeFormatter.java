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

package org.ghyura.cf.desktop;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** Optional local formatters for other languages. Fixed commands, stdin only, no shell, downloads or project plugins. */
final class ExternalCodeFormatter {
    private static final int OUTPUT_LIMIT = 4 * CodeFormatter.MAX_FORMATTED_CHARS;

    static String format(String source, CodeFormatter.Language language) {
        List<String> command = command(language);
        Path executable = executable(command.getFirst());
        if (executable == null) {
            throw new IllegalArgumentException(language + " formatting needs " + command.getFirst()
                    + " on PATH. Chunk origins work without a formatter.");
        }
        command.set(0, executable.toString());
        Path directory = null;
        Process process = null;
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            directory = Files.createTempDirectory("cf-code-format-");
            process = new ProcessBuilder(command).directory(directory.toFile()).start();
            Process owned = process;
            var output = workers.submit(() -> read(owned.getInputStream()));
            var errors = workers.submit(() -> read(owned.getErrorStream()));
            var input = workers.submit(() -> {
                try (var stdin = owned.getOutputStream()) { stdin.write(source.getBytes(StandardCharsets.UTF_8)); }
                return null;
            });
            try {
                if (!process.waitFor(10, TimeUnit.SECONDS)) throw new IllegalArgumentException("Formatting timed out; original retained.");
                input.get(1, TimeUnit.SECONDS);
                String stdout = output.get(1, TimeUnit.SECONDS);
                String stderr = errors.get(1, TimeUnit.SECONDS);
                if (process.exitValue() != 0) {
                    throw new IllegalArgumentException(language + " formatter: " + stderr.strip().substring(0, Math.min(600, stderr.strip().length())));
                }
                return stdout;
            } finally {
                // Also close inherited pipes if a formatter unexpectedly spawned a child.
                process.descendants().forEach(child -> child.destroyForcibly());
                process.destroyForcibly();
                process.getOutputStream().close();
                process.getInputStream().close();
                process.getErrorStream().close();
                input.cancel(true);
                output.cancel(true);
                errors.cancel(true);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Formatting cancelled; original retained.", e);
        } catch (Exception e) {
            throw new IllegalArgumentException("The local formatter could not finish: " + e.getMessage(), e);
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (directory != null) {
                try { Files.deleteIfExists(directory); } catch (IOException ignored) { }
            }
        }
    }

    private static String read(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(OUTPUT_LIMIT + 1);
        if (bytes.length > OUTPUT_LIMIT) throw new IOException("formatter output exceeds the preview limit");
        return new String(bytes, StandardCharsets.UTF_8);
    }

    static Path executable(String name) {
        String path = System.getenv("PATH");
        if (path == null) return null;
        for (String entry : path.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))) {
            if (entry.isBlank()) continue;
            Path directory = Path.of(entry);
            if (!directory.isAbsolute()) continue;
            for (String suffix : System.getProperty("os.name", "").startsWith("Windows") ? List.of(".exe", "") : List.of("")) {
                Path file = directory.resolve(name + suffix);
                if (Files.isRegularFile(file) && Files.isExecutable(file)) return file;
            }
        }
        return null;
    }

    private static List<String> command(CodeFormatter.Language language) {
        return new ArrayList<>(switch (language) {
            case PYTHON -> List.of("ruff", "format", "--isolated", "--no-cache", "--quiet",
                    "--config", "format.quote-style='preserve'", "--config", "format.docstring-code-format=false", "-");
            case JAVASCRIPT, TYPESCRIPT, HTML, CSS, YAML -> List.of("prettier", "--no-config", "--no-editorconfig",
                    "--parser", switch (language) {
                        case JAVASCRIPT -> "babel";
                        case TYPESCRIPT -> "typescript";
                        default -> language.name().toLowerCase(java.util.Locale.ROOT);
                    }, "--trailing-comma", "none");
            case C, CPP, CSHARP -> List.of("clang-format", "--style={BasedOnStyle: LLVM, SortIncludes: Never, ReflowComments: false}",
                    "--assume-filename=preview." + switch (language) { case C -> "c"; case CPP -> "cpp"; default -> "cs"; });
            case GO -> List.of("gofmt");
            case RUST -> List.of("rustfmt", "--emit", "stdout", "--edition", "2024");
            case SHELL -> List.of("shfmt", "-i", "2");
            default -> throw new IllegalArgumentException("No formatter for " + language + "; origins remain available.");
        });
    }
}
