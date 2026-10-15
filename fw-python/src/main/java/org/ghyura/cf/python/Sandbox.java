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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Where the Python add-on runs a candidate (cf.python.sandbox, author 2026-10-13: a workaround for the legacy Executor
 * sandbox while the core stays sandbox-free). It only wraps the unchanged interpreter command:
 * <ul>
 *   <li>none: the interpreter itself, trusted (legacy trusted-local);</li>
 *   <li>bwrap, bwrap-network: bubblewrap with the whole file system read-only, a private /tmp (the directories the run needs
 *       that lie under /tmp are bound back, read-only), the candidate's own directory writable, new namespaces and no network
 *       unless bwrap-network keeps the host's;</li>
 *   <li>docker:IMAGE[@NETWORK]: a container as the legacy sandbox built it (Executor_trunk/sandbox.py): read-only root, no
 *       capabilities, no new privileges, a process limit, a private /tmp, the working directory and import directories mounted
 *       read-only at their own paths, the candidate's directory writable, only the add-on's own variables passed in, no
 *       network unless one is named; images are never pulled. The interpreter is the image's.</li>
 * </ul>
 */
final class Sandbox {

    enum Kind { NONE, BWRAP, BWRAP_NETWORK, DOCKER }

    static final Sandbox NONE = new Sandbox(Kind.NONE, "", "none");

    private final Kind kind;
    private final String image;
    private final String network;

    private Sandbox(Kind kind, String image, String network) {
        this.kind = kind;
        this.image = image;
        this.network = network;
    }

    /** The sandbox a cf.python.sandbox value names (fw-config has checked its form). */
    static Sandbox of(String value) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            return NONE;
        }
        if (text.equals("bwrap")) {
            return new Sandbox(Kind.BWRAP, "", "none");
        }
        if (text.equals("bwrap-network")) {
            return new Sandbox(Kind.BWRAP_NETWORK, "", "host");
        }
        if (text.startsWith("docker:")) {
            String rest = text.substring("docker:".length());
            int at = rest.lastIndexOf('@');
            if (at > 0 && rest.indexOf(':', at) < 0) {
                return new Sandbox(Kind.DOCKER, rest.substring(0, at), rest.substring(at + 1));
            }
            return new Sandbox(Kind.DOCKER, rest, "none");
        }
        throw new IllegalArgumentException("not a Python sandbox: " + text);
    }

    Kind kind() {
        return kind;
    }

    boolean isNone() {
        return kind == Kind.NONE;
    }

    /** A fresh container name for one run; unused outside docker. */
    static String newName() {
        return "cf-python-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * The command that runs {@code command} (the interpreter first) in this sandbox.
     *
     * @param writable the candidate's own directory, the only place it may write besides its private /tmp
     */
    List<String> wrap(List<String> command, Path workdir, Optional<Path> writable, Map<String, String> environment, String name) {
        if (kind == Kind.NONE) {
            return command;
        }
        TreeSet<Path> readable = new TreeSet<>(Comparator.comparing(Path::toString));
        readable.add(workdir.toAbsolutePath().normalize());
        for (String key : List.of("PYTHONPATH", "BUNDLE_REPO_ROOT", "BUNDLE_SUT_ROOT")) {
            for (String entry : environment.getOrDefault(key, "").split(File.pathSeparator)) {
                if (!entry.isBlank() && Files.isDirectory(Path.of(entry))) {
                    readable.add(Path.of(entry).toAbsolutePath().normalize());
                }
            }
        }
        List<String> out = new ArrayList<>();
        if (kind == Kind.DOCKER) {
            out.addAll(List.of("docker", "run", "--rm", "-i", "--pull", "never", "--name", name, "--network", network, "--read-only",
                    "--cap-drop", "ALL", "--security-opt", "no-new-privileges", "--pids-limit", "256", "--tmpfs", "/tmp:rw,mode=1777",
                    "--env", "HOME=/tmp", "--env", "TMPDIR=/tmp"));
            for (String key : new TreeSet<>(environment.keySet())) {
                if (!key.startsWith("DOCKER_")) {
                    out.addAll(List.of("--env", key));   // the value comes from the docker client's own environment
                }
            }
            for (Path p : readable) {
                out.addAll(List.of("--volume", p + ":" + p + ":ro"));
            }
            writable.ifPresent(p -> out.addAll(List.of("--volume", p + ":" + p)));
            out.addAll(List.of("--workdir", workdir.toAbsolutePath().normalize().toString(), image));
            out.addAll(command);
            return out;
        }
        out.addAll(List.of("bwrap", "--ro-bind", "/", "/", "--dev", "/dev", "--proc", "/proc", "--tmpfs", "/tmp", "--unshare-all"));
        if (kind == Kind.BWRAP_NETWORK) {
            out.add("--share-net");
        }
        out.addAll(List.of("--die-with-parent", "--new-session", "--setenv", "HOME", "/tmp", "--setenv", "TMPDIR", "/tmp"));
        Path tmp = Path.of("/tmp");
        List<Path> back = new ArrayList<>(readable);
        Path interpreter = Path.of(command.getFirst());
        if (interpreter.isAbsolute() && interpreter.startsWith(tmp) && interpreter.getParent() != null) {
            back.add(interpreter.getParent().getParent() != null ? interpreter.getParent().getParent() : interpreter.getParent());
        }
        back.sort(Comparator.comparingInt(Path::getNameCount));   // a parent before what lies inside it
        for (Path p : back) {
            if (p.startsWith(tmp)) {
                out.addAll(List.of("--ro-bind", p.toString(), p.toString()));
            }
        }
        writable.ifPresent(p -> out.addAll(List.of("--bind", p.toString(), p.toString())));
        out.addAll(List.of("--chdir", workdir.toAbsolutePath().normalize().toString(), "--"));
        out.addAll(command);
        return out;
    }

    /** Whether the sandbox, not the candidate, failed: docker's own codes or bubblewrap's own message. */
    boolean failedToStart(int exitCode, String stderrTail) {
        return switch (kind) {
            case NONE -> false;
            case DOCKER -> exitCode == 125 || exitCode == 126 || exitCode == 127;
            case BWRAP, BWRAP_NETWORK -> stderrTail.lines().anyMatch(line -> line.startsWith("bwrap: "));
        };
    }

    /** After a run that was killed: a container outlives its docker client, so it is removed by name. */
    void cleanUp(String name, Path workdir, Map<String, String> environment) {
        if (kind != Kind.DOCKER) {
            return;
        }
        boolean interrupted = Thread.interrupted();   // a deadline interrupts the worker; the removal must still run
        try {
            PythonProcess.run(List.of("docker", "rm", "-f", name), workdir, environment, java.util.concurrent.TimeUnit.SECONDS.toNanos(20),
                    org.ghyura.cf.spi.Cancellation.NONE, text -> { }, text -> { });
        } catch (Exception e) {
            // best effort: the container was started with --rm and ends with its interpreter
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public String toString() {
        return switch (kind) {
            case NONE -> "none";
            case BWRAP -> "bwrap (read-only, no network)";
            case BWRAP_NETWORK -> "bwrap-network (read-only, host network)";
            case DOCKER -> "docker " + image + " (network " + network + ")";
        };
    }
}
