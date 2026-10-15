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

import org.ghyura.cf.model.Severity;
import org.ghyura.cf.spi.RunInputs;
import org.ghyura.cf.spi.SutDoctor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class PythonDoctorTest {
    private static RunInputs inputs(Path dir, Map<String,String> settings) {
        return new RunInputs(List.of(),Optional.of("raise RuntimeError('preprocessor must not execute')\n"),"FW_VAR",Map.of(),Optional.of(dir),settings);
    }
    private static SutDoctor.Check named(List<SutDoctor.Check> checks,String name) {
        return checks.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }
    @Test
    void versionAndMissingDottedModulesAreReadWithoutExecutingSourcesOrPackages(@TempDir Path dir) throws Exception {
        Path pkg=Files.createDirectory(dir.resolve("codex_doctor_pkg"));
        String sabotage="from pathlib import Path\nPath('executed-marker').write_text('wrong')\nraise RuntimeError('do not import')\n";
        Files.writeString(pkg.resolve("__init__.py"),sabotage);Files.writeString(pkg.resolve("exists.py"),sabotage);
        Files.writeString(dir.resolve("ast.py"),sabotage);Files.writeString(dir.resolve("sitecustomize.py"),sabotage);
        String path=System.getenv("PATH"),pyPath=System.getenv("PYTHONPATH");
        List<SutDoctor.Check> checks=new PythonPlugin().diagnose(inputs(dir,Map.of("cf.python.path",dir.toString(),"cf.python.env","PYTHONDONTWRITEBYTECODE=0")),
                List.of("import codex_doctor_pkg.exists, codex_doctor_pkg.missing, codex_missing_doctor_dependency\n"+sabotage));
        assertEquals(Severity.INFO,named(checks,"python").severity());
        assertTrue(named(checks,"python").message().matches("Python [0-9]+[.][0-9]+.*"),named(checks,"python").toString());
        SutDoctor.Check imports=named(checks,"python_imports");assertEquals(Severity.WARNING,imports.severity());
        assertTrue(imports.message().contains("codex_doctor_pkg.missing")&&imports.message().contains("codex_missing_doctor_dependency"),imports.toString());
        assertFalse(imports.message().contains("codex_doctor_pkg.exists"),imports.toString());
        assertFalse(Files.exists(dir.resolve("executed-marker")));assertFalse(Files.exists(dir.resolve("__pycache__")));
        assertFalse(Files.exists(pkg.resolve("__pycache__")));assertEquals(path,System.getenv("PATH"));assertEquals(pyPath,System.getenv("PYTHONPATH"));
    }
    @Test
    void aMissingInterpreterBlocksWithoutMisclassifyingItAsACandidateFailure(@TempDir Path dir) {
        var checks=new PythonPlugin().diagnose(inputs(dir,Map.of("cf.python.executable",dir.resolve("absent-interpreter").toString())),List.of("import os\n"));
        assertEquals(1,checks.size());assertEquals("python",checks.getFirst().name());assertEquals(Severity.ERROR,checks.getFirst().severity());
        assertTrue(checks.getFirst().message().contains("could not be started"));
    }
    @Test
    void invalidSyntaxAndOversizedPreviewAreReportedAsPartial(@TempDir Path dir) {
        var checks=new PythonPlugin().diagnose(inputs(dir,Map.of()),List.of("if (\n","x".repeat(8001),"import os\n"));
        assertEquals(Severity.INFO,named(checks,"python").severity());var imports=named(checks,"python_imports");assertEquals(Severity.WARNING,imports.severity());
        assertTrue(imports.details().stream().anyMatch(s -> s.contains("skipped 1")),imports.toString());
        assertTrue(imports.details().stream().anyMatch(s -> s.startsWith("candidate 1:")),imports.toString());
        for (var sources : List.of(List.<String>of(), List.of("import codex_doctor_absent\n"+"x".repeat(8001)))) {
            var unchecked=named(new PythonPlugin().diagnose(inputs(dir,Map.of()),sources),"python_imports");
            assertEquals(Severity.WARNING,unchecked.severity());
            assertEquals("No candidate sources inspected; import availability is unknown",unchecked.message());
            assertTrue(unchecked.details().contains("Missing modules: not checked"),unchecked.toString());
        }
    }
}
