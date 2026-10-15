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

package org.ghyura.cf.artifacts;

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Imports a legacy hand-over (audit EX01): the {@code bundle.handoff/v2} manifest the legacy Reader wrote for the legacy
 * executor, validated as HandoffManifestV2.load did and failing closed on the first problem, a protocol whose name or
 * major version differs above all (INPUT_VERSION_UNSUPPORTED). What this build cannot honour is refused by name rather
 * than approximated: the sharded and gRPC transports, and execution policies that ask for a container
 * sandbox. The results database the manifest names is not used (no database here). The loose-files sources are read in a
 * fixed order, by source and then by file name, and their number must equal {@code candidate_count}.
 */
public final class LegacyHandoff {

    /** The only protocol this build reads. */
    public static final String PROTOCOL = "bundle.handoff/v2";

    /** The source language and its legacy loose-file extension; importing data never starts an interpreter. */
    public enum Language {
        JAVA("java", ".java"), PYTHON("python", ".py");

        private final String token;
        private final String extension;

        Language(String token, String extension) {
            this.token = token;
            this.extension = extension;
        }

        public String token() { return token; }
        public String extension() { return extension; }
    }

    /**
     * An imported hand-over.
     *
     * @param manifest      the manifest file
     * @param digest        SHA-256 of the manifest's bytes
     * @param runId         its run id (becomes cf.run.id)
     * @param verdict       its verdict mode (becomes cf.execution.verdict)
     * @param verdictNames  FW_CUSTOM_VAR codes and messages
     * @param arguments     the candidates' arguments
     * @param runFirstOnce  the preprocess source run once before the candidates, if any
     * @param policyRef     the execution policy reference, recorded as provenance
     * @param candidates    the candidates in order
     * @param diagnostics   what the import noticed (retired fields, unverifiable digests)
     * @param container     a Python hand-over's demand for the legacy container sandbox, which runs only inside the Python
     *                      add-on's matching docker sandbox (checked against the run's cf.python.sandbox before anything runs)
     */
    public record Bundle(Path manifest, String digest, String runId, RunConfig.VerdictField verdict, Map<Integer, String> verdictNames,
                         List<String> arguments, Optional<String> runFirstOnce, String policyRef, List<HandoffCandidate> candidates,
                         List<Diagnostic> diagnostics, Language language, Optional<ContainerDemand> container) {
        public Bundle {
            java.util.Objects.requireNonNull(language, "language");
            java.util.Objects.requireNonNull(container, "container");
            verdictNames = Collections.unmodifiableMap(new TreeMap<>(verdictNames));
            arguments = List.copyOf(arguments);
            candidates = List.copyOf(candidates);
            diagnostics = List.copyOf(diagnostics);
        }

        /** Existing Java callers keep the original constructor. */
        public Bundle(Path manifest, String digest, String runId, RunConfig.VerdictField verdict, Map<Integer, String> verdictNames,
                      List<String> arguments, Optional<String> runFirstOnce, String policyRef, List<HandoffCandidate> candidates,
                      List<Diagnostic> diagnostics) {
            this(manifest, digest, runId, verdict, verdictNames, arguments, runFirstOnce, policyRef, candidates, diagnostics, Language.JAVA,
                    Optional.empty());
        }
    }

    /**
     * The legacy container policy a Python hand-over was written for (execution_policy.json): its profile, its network mode
     * ({@code disabled}, or {@code allowlist} with the targets the candidates may reach).
     */
    public record ContainerDemand(String profile, String network, List<String> networkAllowlist) {
        public ContainerDemand {
            networkAllowlist = List.copyOf(networkAllowlist);
        }
    }

    /**
     * Why the selected Python sandbox does not honour the hand-over's container policy, or null when it does: only
     * docker:IMAGE for a policy without network. A named Docker network does not enforce the legacy allowlist, so that
     * policy remains refused; never silently replace it with host, bridge or another shared network.
     */
    public static String containerRefusal(ContainerDemand demand, String pythonSandbox) {
        String sandbox = pythonSandbox == null ? "" : pythonSandbox.trim();
        boolean docker = sandbox.startsWith("docker:");
        int at = sandbox.lastIndexOf('@');
        boolean named = docker && at > "docker:".length() && sandbox.indexOf(':', at) < 0;
        boolean networked = "allowlist".equals(demand.network()) && !demand.networkAllowlist().isEmpty();
        boolean closed = "disabled".equals(demand.network()) || "none".equals(demand.network());
        if (docker && closed && !named) {
            return null;
        }
        if ("allowlist".equals(demand.network())) {
            return "the execution policy (profile " + demand.profile() + ") requires an enforced network allowlist "
                    + demand.networkAllowlist() + "; the Python add-on cannot enforce it with a named Docker network; nothing was run";
        }
        return "the execution policy (profile " + demand.profile() + ") asks for the legacy 'container' sandbox"
                + (networked ? " reaching " + demand.networkAllowlist() : " without network") + "; container isolation is not available "
                + "in one JVM (audit EX09), so the hand-over runs only inside the Python add-on's docker sandbox: set cf.python.sandbox="
                + (networked ? "docker:IMAGE@NETWORK on a network that reaches those targets" : "docker:IMAGE (no network)")
                + (sandbox.isEmpty() ? "" : " instead of " + sandbox) + "; nothing was run";
    }

    private final Path manifest;
    private final String origin;

    private LegacyHandoff(Path manifest) {
        this.manifest = manifest;
        this.origin = manifest.toString();
    }

    /** @throws FrameworkException the first problem found, nothing imported */
    public static Bundle read(Path manifest) {
        return new LegacyHandoff(manifest).load();
    }

    private Bundle load() {
        byte[] bytes;
        String text;
        try {
            bytes = Files.readAllBytes(manifest);
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw fail(ErrorCode.INPUT_MALFORMED, "the manifest is not UTF-8");
        } catch (IOException e) {
            throw fail(ErrorCode.INPUT_UNREADABLE, "the manifest cannot be read: " + e.getMessage());
        }
        if (!(json(text, origin) instanceof Map<?, ?> parsed)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "the manifest is not a JSON object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        List<Diagnostic> notes = new ArrayList<>();

        String protocol = string(root, "protocol");
        int slash = protocol.lastIndexOf('/');
        if (slash < 0 || !protocol.substring(0, slash).equals("bundle.handoff") || !protocol.substring(slash + 1).startsWith("v")) {
            throw fail(ErrorCode.INPUT_VERSION_UNSUPPORTED, "protocol \"" + protocol + "\" is not a bundle.handoff/vN identifier");
        }
        if (!protocol.equals(PROTOCOL)) {
            throw fail(ErrorCode.INPUT_VERSION_UNSUPPORTED, "protocol \"" + protocol + "\" is not \"" + PROTOCOL
                    + "\": this build reads that version only, and a different version is refused rather than guessed (fail closed)");
        }
        String runId = string(root, "run_id");
        if (runId.isBlank()) {
            throw fail(ErrorCode.INPUT_SCHEMA, "run_id is empty");
        }
        String language = string(root, "language");
        if (!language.equals("python") && !language.equals("java")) {
            throw fail(ErrorCode.INPUT_SCHEMA, "language \"" + language + "\" is not \"python\"/\"java\"");
        }
        String transport = string(root, "candidate_transport");
        if (!transport.equals("loose-files") && !transport.equals("sharded") && !transport.equals("grpc")) {
            throw fail(ErrorCode.INPUT_SCHEMA, "candidate_transport \"" + transport + "\" is not \"loose-files\"/\"sharded\"/\"grpc\"");
        }
        long count = integer(root, "candidate_count");
        if (count < 0) {
            throw fail(ErrorCode.INPUT_SCHEMA, "candidate_count must be >= 0, got " + count);
        }
        if (string(root, "id_format").isBlank()) {
            throw fail(ErrorCode.INPUT_SCHEMA, "id_format must not be empty");
        }
        List<String[]> sources = sources(root);
        resultTarget(root);
        string(root, "result_schema_mode");
        String verdictMode = string(root, "verdict_mode");
        if (!verdictMode.equals("FW_VAR") && !verdictMode.equals("FW_CUSTOM_VAR")) {
            throw fail(ErrorCode.INPUT_SCHEMA, "verdict_mode \"" + verdictMode + "\" is not FW_VAR/FW_CUSTOM_VAR");
        }
        Map<Integer, String> verdicts = customVerdicts(root);
        if (verdictMode.equals("FW_CUSTOM_VAR") && verdicts.isEmpty()) {
            throw fail(ErrorCode.INPUT_SCHEMA, "verdict_mode=FW_CUSTOM_VAR requires a non-empty custom_verdicts list");
        }
        List<String> arguments = strings(root.get("arguments"), "arguments");
        long shift = root.containsKey("shift") ? integer(root, "shift") : 1;
        String preprocess = optional(root.get("preprocess"), "preprocess");
        String policyRef = optional(root.get("execution_policy_ref"), "execution_policy_ref");

        // what this build cannot honour is refused by name
        Language sourceLanguage = language.equals("python") ? Language.PYTHON : Language.JAVA;
        if (!transport.equals("loose-files")) {
            throw fail(ErrorCode.UNSUPPORTED_CAPABILITY, "candidate_transport \"" + transport + "\": this build imports loose-files "
                    + "hand-overs only (" + (transport.equals("grpc") ? "gRPC is a network transport and this build makes no network "
                    + "call" : "sharded containers are not read") + ")");
        }
        Optional<ContainerDemand> container = policy(notes, sourceLanguage);
        notes.add(Diagnostic.info(ErrorCode.CONFIG_LEGACY_RETIRED, "result_target and result_schema_mode name the legacy results "
                + "database; this build keeps every observation in the run and writes no database", SourceLocation.of(origin)));
        if (shift != 1) {
            notes.add(Diagnostic.info(ErrorCode.CONFIG_LEGACY_RETIRED, "shift = " + shift + " placed FW_VAR verdicts into result-table "
                    + "columns; verdicts are kept exactly here, so it has no effect", SourceLocation.of(origin)));
        }

        Path base = manifest.toAbsolutePath().getParent();
        List<HandoffCandidate> candidates = new ArrayList<>();
        for (String[] source : sources) {
            Path dir = base.resolve(source[1]).normalize();
            if (!Files.isDirectory(dir)) {
                throw fail(ErrorCode.INPUT_UNREADABLE, "source " + source[1] + " (kind " + source[0] + ") is not a directory: " + dir);
            }
            if (source[2] != null && sourceLanguage == Language.JAVA) {
                notes.add(Diagnostic.warning(ErrorCode.INPUT_SCHEMA, "source " + source[1] + " declares sha256 " + source[2] + " for a "
                        + "directory; the legacy executor never checked it and no directory digest is defined, so it is not verified; "
                        + "every candidate file's own digest is taken instead", SourceLocation.of(origin)));
            }
            List<Path> files;
            try (Stream<Path> list = Files.list(dir)) {
                files = list.filter(p -> p.getFileName().toString().endsWith(sourceLanguage.extension()) && Files.isRegularFile(p))
                        .sorted((a, b) -> sourceLanguage == Language.PYTHON
                                ? java.util.Arrays.compare(a.getFileName().toString().codePoints().toArray(), b.getFileName().toString().codePoints().toArray())
                                : a.getFileName().toString().compareTo(b.getFileName().toString())).toList();
            } catch (IOException e) {
                throw fail(ErrorCode.INPUT_UNREADABLE, "source " + dir + " cannot be listed: " + e.getMessage());
            }
            if (sourceLanguage == Language.PYTHON && source[2] != null && !source[2].isEmpty() && !source[2].equals(namesDigest(files))) {
                throw fail(ErrorCode.INPUT_SCHEMA, "source " + dir + " checksum differs from the sorted .py file-name digest; "
                        + "a changed hand-over is not run");
            }
            for (Path file : files) {
                byte[] content;
                try {
                    content = Files.readAllBytes(file);
                } catch (IOException e) {
                    throw fail(ErrorCode.INPUT_UNREADABLE, "candidate " + file + " cannot be read: " + e.getMessage());
                }
                String name = file.getFileName().toString();
                candidates.add(new HandoffCandidate(runId, name.substring(0, name.length() - sourceLanguage.extension().length()), candidates.size(), file,
                        Canonical.sha256Hex(content), content.length));
            }
        }
        if (candidates.size() != count) {
            throw fail(ErrorCode.INPUT_SCHEMA, "candidate_count is " + count + " but the sources hold " + candidates.size()
                    + " " + sourceLanguage.extension() + " files; a partial hand-over is not run");
        }
        Optional<String> runFirstOnce = Optional.empty();
        if (preprocess != null && !preprocess.isBlank()) {
            Path file = base.resolve(preprocess).normalize();
            try {
                runFirstOnce = Optional.of(Files.readString(file, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw fail(ErrorCode.INPUT_UNREADABLE, "preprocess " + preprocess + " cannot be read as a " + sourceLanguage.token() + " source: " + file);
            }
        }
        return new Bundle(manifest, Canonical.sha256Hex(bytes), runId, RunConfig.VerdictField.valueOf(verdictMode), verdicts, arguments,
                runFirstOnce, policyRef == null ? "" : policyRef, candidates, notes, sourceLanguage, container);
    }

    /** The legacy Python executor verifies the digest over sorted candidate names, one per line. */
    private static String namesDigest(List<Path> files) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            for (Path file : files) {
                digest.update(file.getFileName().toString().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }

    /** execution_policy.json beside the manifest, decided as the legacy SandboxedJavaRunner.decideExecMode did. */
    private Optional<ContainerDemand> policy(List<Diagnostic> notes, Language language) {
        Path file = manifest.toAbsolutePath().getParent().resolve("execution_policy.json");
        if (!Files.isRegularFile(file)) {
            notes.add(Diagnostic.info(ErrorCode.CONFIG_LEGACY_RETIRED, "no execution_policy.json beside the manifest: the legacy "
                    + "executor then required trusted mode; Java runs in-process and Python requires the explicit trusted-local "
                    + "Python add-on", SourceLocation.of(origin)));
            return Optional.empty();
        }
        Object doc;
        try {
            doc = json(Files.readString(file, StandardCharsets.UTF_8), file.toString());
        } catch (IOException e) {
            throw fail(ErrorCode.INPUT_UNREADABLE, "execution_policy.json cannot be read: " + e.getMessage());
        }
        Object policy = doc instanceof Map<?, ?> m ? m.get("policy") : null;
        if (!(policy instanceof Map<?, ?> p)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "execution_policy.json has no policy object; the legacy executor refused to run then");
        }
        Object backend = p.get("backend");
        boolean interpreterAllowed = p.get("allowed_interpreters") instanceof List<?> list && (language == Language.JAVA
                ? list.contains("java") && list.contains("javac") : list.contains("python") || list.contains("python3"));
        if ("container".equals(backend) && language == Language.PYTHON && interpreterAllowed) {
            // the Python add-on can honour it in its docker sandbox; the run decides once its configuration is known
            List<String> allow = p.get("network_allowlist") instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
            return Optional.of(new ContainerDemand(String.valueOf(p.get("profile")), String.valueOf(p.get("network")), allow));
        }
        if ("container".equals(backend) || "precompiled-container".equals(backend) || "container-precompiled".equals(backend)) {
            throw fail(ErrorCode.UNSUPPORTED_CAPABILITY, "the execution policy asks for the '" + backend + "' sandbox; container and OS "
                    + "isolation are not available in one JVM (audit EX09), so the hand-over is refused rather than run in-process");
        }
        if (!"local".equals(backend) || !Boolean.TRUE.equals(p.get("trusted")) || !interpreterAllowed) {
            throw fail(ErrorCode.UNSUPPORTED_CAPABILITY, "the execution policy does not allow trusted local " + language.token()
                    + " (backend local, trusted true, " + (language == Language.JAVA ? "java and javac" : "python or python3")
                    + " allowed); nothing was run");
        }
        return Optional.empty();
    }

    private List<String[]> sources(Map<String, Object> root) {
        if (!(root.get("sources") instanceof List<?> list)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "\"sources\" must be an array");
        }
        List<String[]> out = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                throw fail(ErrorCode.INPUT_SCHEMA, "a source entry must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> source = (Map<String, Object>) m;
            String kind = string(source, "kind");
            String path = string(source, "path");
            if (path.isBlank()) {
                throw fail(ErrorCode.INPUT_SCHEMA, "a source has an empty path (kind \"" + kind + "\")");
            }
            out.add(new String[] {kind, path, optional(source.get("sha256"), "sha256")});
        }
        if (out.isEmpty()) {
            throw fail(ErrorCode.INPUT_SCHEMA, "the manifest must declare at least one source location");
        }
        return out;
    }

    private void resultTarget(Map<String, Object> root) {
        if (!(root.get("result_target") instanceof Map<?, ?> m)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "\"result_target\" must be an object");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> target = (Map<String, Object>) m;
        if (string(target, "host").isBlank()) {
            throw fail(ErrorCode.INPUT_SCHEMA, "result_target.host must not be empty");
        }
        long port = integer(target, "port");
        if (port < 1 || port > 65535) {
            throw fail(ErrorCode.INPUT_SCHEMA, "result_target.port out of range: " + port);
        }
        if (string(target, "database").isBlank()) {
            throw fail(ErrorCode.INPUT_SCHEMA, "result_target.database must not be empty");
        }
        optional(target.get("user"), "user");
    }

    private Map<Integer, String> customVerdicts(Map<String, Object> root) {
        Object raw = root.get("custom_verdicts");
        Map<Integer, String> out = new TreeMap<>();
        if (raw == null) {
            return out;
        }
        if (!(raw instanceof List<?> list)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "\"custom_verdicts\" must be an array");
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> m)) {
                throw fail(ErrorCode.INPUT_SCHEMA, "a custom_verdicts entry must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> verdict = (Map<String, Object>) m;
            long code = integer(verdict, "code");
            if (code < Integer.MIN_VALUE || code > Integer.MAX_VALUE) {
                throw fail(ErrorCode.INPUT_SCHEMA, "custom verdict code " + code + " is not an int");
            }
            out.putIfAbsent((int) code, string(verdict, "message"));
        }
        return out;
    }

    private List<String> strings(Object raw, String field) {
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> list) || !list.stream().allMatch(String.class::isInstance)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "\"" + field + "\" must be an array of strings");
        }
        return list.stream().map(String.class::cast).toList();
    }

    private String string(Map<String, Object> map, String key) {
        if (!(map.get(key) instanceof String s)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "field \"" + key + "\" must be a non-null string");
        }
        return s;
    }

    /** An integer; a fractional number is refused (the legacy reader truncated it: deliberately stricter here). */
    private long integer(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v instanceof Long l) {
            return l;
        }
        if (v instanceof BigDecimal d) {
            try {
                return d.longValueExact();
            } catch (ArithmeticException e) {
                throw fail(ErrorCode.INPUT_SCHEMA, "field \"" + key + "\" must be an integer, got " + d.toPlainString());
            }
        }
        throw fail(ErrorCode.INPUT_SCHEMA, "field \"" + key + "\" must be a number");
    }

    private String optional(Object v, String field) {
        if (v == null) {
            return null;
        }
        if (!(v instanceof String s)) {
            throw fail(ErrorCode.INPUT_SCHEMA, "\"" + field + "\" must be a string or null");
        }
        return s;
    }

    /** Parses JSON; a syntax error is refused like every other problem of the hand-over. */
    private static Object json(String text, String where) {
        try {
            return Json.parse(text, where);
        } catch (FrameworkException e) {
            Diagnostic d = e.diagnostics().getFirst();
            throw new FrameworkException(Diagnostic.error(d.code(), "legacy hand-over: " + d.message() + "; nothing was imported",
                    d.location()));
        }
    }

    private FrameworkException fail(ErrorCode code, String why) {
        return new FrameworkException(Diagnostic.error(code, "legacy hand-over " + origin + ": " + why + "; nothing was imported",
                SourceLocation.of(origin)));
    }
}
