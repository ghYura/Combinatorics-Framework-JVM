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

import org.ghyura.cf.spi.Cancellation;
import org.ghyura.cf.spi.SutUnavailable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The one place the framework starts a process, and the only class of the project the architecture gate allows
 * ProcessBuilder and Process (author decision 2026-10-12: the Python add-on): the configured Python interpreter, as
 * the legacy executor's {@code subprocess.run} started it. Standard input is closed at once; standard output and
 * error are read as UTF-8 while the process runs and handed on piece by piece, so nothing waits on a full pipe. The
 * process and every process it started are killed when the sample is cancelled (its deadline passed or the run was
 * cancelled) or when the given limit runs out.
 */
final class PythonProcess {

    /** How the process ended. */
    enum End {
        /** It exited by itself; the exit code is real. */
        EXITED,
        /** The given limit ran out and it was killed. */
        TIMED_OUT,
        /** The sample was cancelled and it was killed. */
        CANCELLED
    }

    /** How it ended and, when it exited by itself, its exit code (-1 otherwise). */
    record Result(End end, int exitCode) {
    }

    private static final long POLL_MILLIS = 20;
    /** How long the readers may go on after the process ended (a child it left behind can hold its pipes open). */
    private static final long DRAIN_MILLIS = 2_000;

    private PythonProcess() {
    }

    /**
     * Runs the command and waits for it.
     *
     * @param limitNanos   the longest it may run, or {@link Long#MAX_VALUE} for as long as the sample runs
     * @param stdout       receives standard output, piece by piece, on a reader thread
     * @param stderr       receives standard error, piece by piece, on another reader thread
     * @throws SutUnavailable       when the interpreter cannot be started at all (missing, not executable)
     * @throws InterruptedException when the calling thread is interrupted; the process is killed first
     */
    static Result run(List<String> command, Path workdir, Map<String, String> environment, long limitNanos, Cancellation cancellation,
                      Consumer<String> stdout, Consumer<String> stderr) throws SutUnavailable, InterruptedException {
        if (cancellation.isCancelled()) {
            return new Result(End.CANCELLED, -1);
        }
        ProcessBuilder builder = new ProcessBuilder(command).directory(workdir.toFile());
        builder.environment().putAll(environment);
        Process process;
        try {
            process = builder.start();
        } catch (IOException | RuntimeException e) {
            throw new SutUnavailable("the Python interpreter '" + command.getFirst() + "' could not be started in " + workdir, e);
        }
        try {
            process.getOutputStream().close();   // the candidate reads an empty standard input, never the framework's
        } catch (IOException e) {
            // the process may already have ended; its output is still read
        }
        Thread out = Thread.ofVirtual().name("python-stdout").start(() -> pump(process.getInputStream(), stdout));
        Thread err = Thread.ofVirtual().name("python-stderr").start(() -> pump(process.getErrorStream(), stderr));
        long started = System.nanoTime();
        End end = End.EXITED;
        try {
            while (!process.waitFor(POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if (cancellation.isCancelled()) {
                    end = End.CANCELLED;
                    break;
                }
                if (System.nanoTime() - started >= limitNanos) {
                    end = End.TIMED_OUT;
                    break;
                }
            }
        } catch (InterruptedException e) {
            kill(process);
            throw e;
        }
        if (end != End.EXITED) {
            kill(process);
        }
        out.join(DRAIN_MILLIS);
        err.join(DRAIN_MILLIS);
        return new Result(end, end == End.EXITED ? process.exitValue() : -1);
    }

    /** Kills the process and everything it started, children first. */
    private static void kill(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private static void pump(InputStream stream, Consumer<String> sink) {
        char[] buffer = new char[8192];
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            for (int n = reader.read(buffer); n >= 0; n = reader.read(buffer)) {
                if (n > 0) {
                    sink.accept(new String(buffer, 0, n));
                }
            }
        } catch (IOException e) {
            // the process ended or was killed: what it wrote until then was handed on
        }
    }
}
