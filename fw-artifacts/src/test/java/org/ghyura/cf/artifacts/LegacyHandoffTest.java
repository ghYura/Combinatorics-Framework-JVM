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
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The legacy hand-over import (audit EX01) against the rules of HandoffManifestV2.load and the legacy executor: a valid
 * loose-files bundle, then each way it fails closed.
 */
class LegacyHandoffTest {

    private static final String VALID = "{\"protocol\": \"bundle.handoff/v2\", \"run_id\": \"legacy-run\", \"language\": \"java\", "
            + "\"candidate_transport\": \"loose-files\", \"candidate_count\": 3, \"id_format\": \"<combi_id>_0_0\", "
            + "\"sources\": [{\"kind\": \"loose-files\", \"path\": \"out\"}], "
            + "\"result_target\": {\"host\": \"localhost\", \"port\": 5432, \"database\": \"results\"}, "
            + "\"result_schema_mode\": \"placeholders=6\", \"verdict_mode\": \"FW_CUSTOM_VAR\", "
            + "\"custom_verdicts\": [{\"code\": 2, \"message\": \"LOST\"}], \"arguments\": [\"x\"], \"shift\": 1}";

    private static Path bundle(Path dir, String manifest) throws IOException {
        Path out = Files.createDirectories(dir.resolve("out"));
        for (String id : List.of("1_0_0", "10_0_0", "2_0_0")) {
            Files.writeString(out.resolve(id + ".java"), "public class C" + id + " { public static int FW_CUSTOM_VAR = -1; "
                    + "public static void main(String[] a) { FW_CUSTOM_VAR = 0; } }", StandardCharsets.UTF_8);
        }
        Files.writeString(out.resolve("notes.txt"), "not a candidate", StandardCharsets.UTF_8);
        Path file = dir.resolve("manifest.json");
        Files.writeString(file, manifest, StandardCharsets.UTF_8);
        return file;
    }

    private static ErrorCode refusal(Path dir, String manifest) throws IOException {
        FrameworkException e = assertThrows(FrameworkException.class, () -> LegacyHandoff.read(bundle(dir, manifest)));
        assertTrue(e.getMessage().contains("nothing was imported"), e.getMessage());
        return e.diagnostics().getFirst().code();
    }

    @Test
    void aLooseFilesBundleImportsItsCandidatesInAFixedOrderWithItsRunIdAndVerdicts(@TempDir Path dir) throws IOException {
        LegacyHandoff.Bundle b = LegacyHandoff.read(bundle(dir, VALID));
        assertEquals(List.of("10_0_0", "1_0_0", "2_0_0"), b.candidates().stream().map(HandoffCandidate::displayId).toList(),
                "file names in string order, as the legacy corpus ordered candidate ids");
        assertEquals("legacy-run", b.runId());
        assertEquals(RunConfig.VerdictField.FW_CUSTOM_VAR, b.verdict());
        assertEquals(Map.of(2, "LOST"), b.verdictNames());
        assertEquals(List.of("x"), b.arguments());
        assertEquals(3, b.candidates().stream().map(c -> c.id().hex()).distinct().count());
        assertEquals(b.candidates().getFirst().id(), LegacyHandoff.read(dir.resolve("manifest.json")).candidates().getFirst().id(),
                "ids are stable");
        assertTrue(b.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.CONFIG_LEGACY_RETIRED), "the database is not used");
        HandoffCandidate first = b.candidates().getFirst();
        assertTrue(first.source().contains("class C10_0_0"));
        Files.writeString(first.file(), "changed", StandardCharsets.UTF_8);
        assertThrows(IllegalStateException.class, first::source, "a candidate changed since the import is refused");
        assertNotEquals(b.digest(), "");
    }

    @Test
    void everyOtherVersionAndWhatThisBuildCannotHonourAreRefusedByName(@TempDir Path dir) throws IOException {
        assertEquals(ErrorCode.INPUT_VERSION_UNSUPPORTED, refusal(dir.resolve("v3"), VALID.replace("bundle.handoff/v2", "bundle.handoff/v3")));
        assertEquals(ErrorCode.INPUT_VERSION_UNSUPPORTED, refusal(dir.resolve("name"), VALID.replace("bundle.handoff/v2", "bundle.other/v2")));
        assertEquals(ErrorCode.INPUT_SCHEMA, refusal(dir.resolve("py"), VALID.replace("\"java\"", "\"python\"")),
                "a Python manifest with only Java files is an incomplete hand-over");
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, refusal(dir.resolve("grpc"), VALID.replace("\"loose-files\", \"candidate_count\"",
                "\"grpc\", \"candidate_count\"")));
        assertEquals(ErrorCode.INPUT_SCHEMA, refusal(dir.resolve("count"), VALID.replace("\"candidate_count\": 3", "\"candidate_count\": 4")));
        assertEquals(ErrorCode.INPUT_SCHEMA, refusal(dir.resolve("custom"), VALID.replace("[{\"code\": 2, \"message\": \"LOST\"}]", "[]")));
        assertEquals(ErrorCode.INPUT_SCHEMA, refusal(dir.resolve("port"), VALID.replace("5432", "0")));
        assertEquals(ErrorCode.INPUT_SCHEMA, refusal(dir.resolve("fraction"), VALID.replace("\"candidate_count\": 3", "\"candidate_count\": 3.5")));
        assertEquals(ErrorCode.INPUT_MALFORMED, refusal(dir.resolve("twice"), VALID.replace("\"shift\": 1", "\"shift\": 1, \"shift\": 2")));
        // an execution policy asking for a container sandbox is refused; a trusted local Java policy is accepted
        Path sandbox = dir.resolve("sandbox");
        Files.createDirectories(sandbox);
        Files.writeString(sandbox.resolve("execution_policy.json"), "{\"policy\": {\"backend\": \"container\", "
                + "\"allowed_interpreters\": [\"java\", \"javac\"]}}", StandardCharsets.UTF_8);
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, refusal(sandbox, VALID));
        Path local = dir.resolve("local");
        Files.createDirectories(local);
        Files.writeString(local.resolve("execution_policy.json"), "{\"policy\": {\"backend\": \"local\", \"trusted\": true, "
                + "\"allowed_interpreters\": [\"java\", \"javac\"]}}", StandardCharsets.UTF_8);
        assertEquals(3, LegacyHandoff.read(bundle(local, VALID)).candidates().size());
    }

    @Test
    void pythonBundlesReadOnlyPythonFilesAndRequireTheMatchingTrustedPolicy(@TempDir Path dir) throws IOException {
        Path manifest = bundle(dir, VALID.replace("\"java\"", "\"python\""));
        for (String id : List.of("1_0_0", "10_0_0", "2_0_0")) {
            Files.writeString(dir.resolve("out").resolve(id + ".py"), "FW_CUSTOM_VAR = 0\n", StandardCharsets.UTF_8);
        }
        LegacyHandoff.Bundle b = LegacyHandoff.read(manifest);
        assertEquals(LegacyHandoff.Language.PYTHON, b.language());
        assertEquals(List.of("10_0_0", "1_0_0", "2_0_0"), b.candidates().stream().map(HandoffCandidate::displayId).toList());
        assertTrue(b.candidates().stream().allMatch(c -> c.source().equals("FW_CUSTOM_VAR = 0\n")));
        Path policy = dir.resolve("execution_policy.json");
        Files.writeString(policy, "{\"policy\":{\"backend\":\"local\",\"trusted\":true,\"allowed_interpreters\":[\"python3\"]}}");
        assertEquals(3, LegacyHandoff.read(manifest).candidates().size());
        Files.writeString(policy, "{\"policy\":{\"backend\":\"local\",\"trusted\":true,\"allowed_interpreters\":[\"java\",\"javac\"]}}");
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, assertThrows(FrameworkException.class,
                () -> LegacyHandoff.read(manifest)).diagnostics().getFirst().code());
        // A legacy container policy (generated-default) runs only in the Python add-on's matching docker sandbox (0128,
        // cross-check with the legacy 2026-10-13): reading records it; the run refuses any other cf.python.sandbox.
        Files.writeString(policy, "{\"policy\":{\"profile\":\"generated-default\",\"backend\":\"container\",\"trusted\":false,"
                + "\"network\":\"disabled\",\"network_allowlist\":[],\"allowed_interpreters\":[\"python3\"]}}");
        LegacyHandoff.ContainerDemand closed = LegacyHandoff.read(manifest).container().orElseThrow();
        assertEquals(new LegacyHandoff.ContainerDemand("generated-default", "disabled", List.of()), closed);
        assertEquals(null, LegacyHandoff.containerRefusal(closed, "docker:python:3-slim"));
        for (String other : List.of("", "bwrap", "bwrap-network", "docker:python:3-slim@bridge")) {
            assertTrue(LegacyHandoff.containerRefusal(closed, other).contains("nothing was run"), other);
        }
        Files.writeString(policy, "{\"policy\":{\"profile\":\"networked-api-probe\",\"backend\":\"container\",\"trusted\":false,"
                + "\"network\":\"allowlist\",\"network_allowlist\":[\"db\"],\"allowed_interpreters\":[\"python3\"]}}");
        LegacyHandoff.ContainerDemand networked = LegacyHandoff.read(manifest).container().orElseThrow();
        for (String sandbox : List.of("docker:client:1@fixture-net", "docker:client:1@host", "docker:client:1@bridge",
                "docker:client:1", "bwrap-network", "")) {
            assertTrue(LegacyHandoff.containerRefusal(networked, sandbox).contains("cannot enforce"), sandbox);
        }
        Files.writeString(policy, "{\"policy\":{\"backend\":\"container\",\"trusted\":true,\"allowed_interpreters\":[\"java\",\"javac\"]}}");
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, assertThrows(FrameworkException.class,
                () -> LegacyHandoff.read(manifest)).diagnostics().getFirst().code(), "a container policy without Python stays refused");
    }
}
