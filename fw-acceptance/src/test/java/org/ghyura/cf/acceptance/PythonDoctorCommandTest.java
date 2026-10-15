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

import org.ghyura.cf.app.Application;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import javax.xml.parsers.DocumentBuilderFactory;
import static org.junit.jupiter.api.Assertions.*;

class PythonDoctorCommandTest {
    private record Result(int code,String out,String err) {}
    private static Result run(String...args) {
        var out=new ByteArrayOutputStream();var err=new ByteArrayOutputStream();int code=Application.run(args,Map.of(),new PrintStream(out,true,StandardCharsets.UTF_8),new PrintStream(err,true,StandardCharsets.UTF_8));
        return new Result(code,out.toString(StandardCharsets.UTF_8),err.toString(StandardCharsets.UTF_8));
    }
    @Test
    void scenarioDefaultsScopeAndExitStatusesReachTheCliWithoutRunningCode(@TempDir Path dir) throws Exception {
        Path tree=Files.createDirectory(dir.resolve("generator_trunk"));Files.createDirectory(tree.resolve("nested"));
        Files.writeString(tree.resolve("sut_paths.py"),"# directory marker, never executed\n");
        Files.writeString(tree.resolve("codex_doctor_available.py"),"from pathlib import Path\nPath('module-executed').touch()\n");
        Path scenario=Files.writeString(tree.resolve("nested/scenario.toml"),"[[slots]]\nsheet = \"BODY\"\nraw = true\nvalues = ['''import codex_doctor_available, codex_doctor_absent\nfrom pathlib import Path\nPath('candidate-executed').touch()\nFW_VAR = 0\n''']\n");
        Path json=dir.resolve("doctor.json");Result checked=run("doctor","--scenario",scenario.toString(),"--set","cf.execution.sut=python","--format","xml","--json",json.toString());
        assertEquals(0,checked.code(),checked.err());
        assertEquals("doctor",DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new java.io.ByteArrayInputStream(checked.out().getBytes(StandardCharsets.UTF_8))).getDocumentElement().getAttribute("command"));
        assertTrue(checked.out().contains("name=\"python\" severity=\"OK\" message=\"Python "),checked.out());
        assertTrue(checked.out().contains("Missing static imports: codex_doctor_absent"),checked.out());
        assertFalse(checked.out().contains("Missing static imports: codex_doctor_available"),checked.out());
        assertTrue(checked.out().contains("workdir="+tree),checked.out());assertTrue(Files.readString(json).contains("python_import_scope"));
        assertFalse(Files.exists(tree.resolve("module-executed")));assertFalse(Files.exists(tree.resolve("candidate-executed")));
        Result blocked=run("doctor","--scenario",scenario.toString(),"--set","cf.execution.sut=python","--set","cf.python.executable="+dir.resolve("absent"),"--format","xml");
        assertEquals(1,blocked.code(),blocked.out());assertTrue(blocked.out().contains("name=\"python\" severity=\"BLOCKING\""));
        Result noScenario=run("doctor","--set","cf.execution.sut=python","--format","xml");assertEquals(0,noScenario.code());assertTrue(noScenario.out().contains("No scenario supplied: imports were not inspected"));
        Path large=Files.writeString(tree.resolve("large.toml"),"[[slots]]\nsheet = \"BODY\"\nraw = true\nverb = \"FW_Permut\"\nvalues = [\"import os\\n\",\"import json\\n\",\"import sys\\n\",\"import math\\n\",\"FW_VAR = 0\\n\"]\n");
        Result partial=run("doctor","--scenario",large.toString(),"--set","cf.execution.sut=python","--format","xml");assertEquals(0,partial.code(),partial.err());
        assertTrue(partial.out().contains("name=\"python_import_scope\" severity=\"WARNING\""),partial.out());assertTrue(partial.out().contains("visited 64 of 120"),partial.out());
        Path oversized=Files.writeString(tree.resolve("oversized.toml"),"[[slots]]\nsheet = \"BODY\"\nraw = true\nvalues = ['''import codex_doctor_absent\nfrom pathlib import Path\nPath('oversized-executed').touch()\n#"+"x".repeat(8_001)+"\n''']\n");
        Result skipped=run("doctor","--scenario",oversized.toString(),"--set","cf.execution.sut=python","--format","xml");
        assertEquals(0,skipped.code(),skipped.err());
        assertTrue(skipped.out().contains("name=\"python_imports\" severity=\"WARNING\""),skipped.out());
        assertTrue(skipped.out().contains("Inspected 0 distinct candidate sources; skipped 1"),skipped.out());
        assertTrue(skipped.out().contains("No candidate sources inspected; import availability is unknown"),skipped.out());
        assertTrue(skipped.out().contains("Missing modules: not checked"),skipped.out());
        assertFalse(skipped.out().contains("Missing modules: none"),skipped.out());
        assertFalse(Files.exists(tree.resolve("oversized-executed")));
    }
}
