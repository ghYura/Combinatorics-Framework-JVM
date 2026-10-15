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


package org.ghyura.cf.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The doctor command: the legacy report shape, a JSON report, and exit 1 only for a blocking check. */
class DoctorCommandTest {

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void warningsKeepExitZeroAndABlockingCheckExitsOne(@TempDir Path dir) throws Exception {
        Path json = dir.resolve("doctor.json");
        Result fine = run("doctor", "--json", json.toString());
        assertEquals(0, fine.code(), fine.err());
        assertTrue(fine.out().startsWith("DOCTOR\n  ✓ [OK      ] java "), fine.out());
        assertTrue(fine.out().contains("\n  ✓ [OK      ] application "), fine.out());
        assertTrue(fine.out().matches("(?s).*\n\noverall: (OK|WARNING)\n  ✓ doctor report -> " + java.util.regex.Pattern.quote(json.toString()) + "\n"),
                "a low disk only warns: " + fine.out());
        assertTrue(Files.readString(json).startsWith("{\n  \"schema\": \"cf.doctor/v1\",\n  \"overall\": \""), Files.readString(json));

        Result blocked = run("doctor", "--set", "cf.execution.sut=no-such-plugin");
        assertEquals(1, blocked.code(), blocked.err());
        assertTrue(blocked.out().contains("  ✗ [BLOCKING] sut "), blocked.out());
        assertTrue(blocked.out().contains("\noverall: BLOCKING\n"), blocked.out());
        assertTrue(blocked.err().contains("RUN_ENVIRONMENT_BLOCKED") && blocked.err().contains("1 blocking check(s): sut"), blocked.err());

        Result xml = run("doctor", "--set", "cf.execution.workers=many", "--format", "xml");
        assertEquals(1, xml.code(), xml.err());
        assertTrue(xml.out().startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cf-report command=\"doctor\" status=\"FAILED\" exitCode=\"1\" "
                + "exitName=\"FAILED\">\n  <doctor schema=\"cf.doctor/v1\" overall=\"BLOCKING\">\n"), xml.out());
        assertTrue(xml.out().contains("<check name=\"configuration\" severity=\"BLOCKING\"") && xml.out().contains("RUN_ENVIRONMENT_BLOCKED"),
                "a refused configuration is reported, not thrown: " + xml.out());
    }
}
