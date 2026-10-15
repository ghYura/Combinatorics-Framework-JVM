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


package org.ghyura.cf.desktop.session;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.model.FrameworkException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:diagnostics-reporting-release, the doctor: read-only probes of this JVM in the legacy report shape. */
class DoctorTest {

    private static EffectiveConfig config(String... settings) {
        List<ConfigResolver.Override> overrides = java.util.Arrays.stream(settings).map(s -> new ConfigResolver.Override(s.substring(0,
                s.indexOf('=')), s.substring(s.indexOf('=') + 1), "test")).toList();
        return ConfigResolver.resolve(null, Map.of(), overrides);
    }

    private static Doctor.Check named(List<Doctor.Check> checks, String name) {
        return checks.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void theProbesComeInTheirOrderAndAJdkRunsGeneratedJava(@TempDir Path dir) {
        List<Doctor.Check> checks = Doctor.probes(config(), null, dir);
        assertEquals(List.of("java", "configuration", "compiler", "memory", "work_directory", "sut", "capability_matrix", "legacy_probes"),
                checks.stream().map(Doctor.Check::name).toList());
        assertEquals(Doctor.Severity.OK, named(checks, "java").severity());
        assertEquals(Doctor.Severity.OK, named(checks, "compiler").severity(), "the test JVM is a JDK");
        assertEquals(Doctor.Severity.OK, named(checks, "sut").severity());
        assertTrue(named(checks, "legacy_probes").details().stream().anyMatch(d -> d.startsWith("main_db, results_db")));
        assertTrue(named(checks, "capability_matrix").details().getFirst().matches("\\d+ capabilities: \\d+ implemented / .*"));
    }

    @Test
    void whatWouldFailARunIsBlockingAndWhatOnlyDegradesItIsAWarning(@TempDir Path dir) throws Exception {
        List<Doctor.Check> unknown = Doctor.probes(config("cf.execution.sut=no-such-plugin"), null, dir);
        Doctor.Check sut = named(unknown, "sut");
        assertEquals(Doctor.Severity.BLOCKING, sut.severity());
        assertTrue(sut.message().contains("names no SUT plugin") && sut.details().stream().anyMatch(d -> d.startsWith("inline-metrics ")),
                sut.toString());
        assertEquals(List.of("not needed by the configured typed SUT plugin"), named(unknown, "compiler").details(),
                "a typed plugin never needs the compiler, so its absence would only warn");

        Doctor.Check lines = named(Doctor.probes(config("cf.execution.sut=inline-metrics", "cf.execution.coverage=lines"), null, dir),
                "capability_matrix");
        assertEquals(Doctor.Severity.BLOCKING, lines.severity());
        assertTrue(lines.message().contains("UNSUPPORTED_CAPABILITY"), lines.message());

        long heap = Runtime.getRuntime().maxMemory();
        Doctor.Check memory = named(Doctor.probes(config("cf.memory.budget.bytes=" + Math.min(Long.MAX_VALUE / 2, heap * 2)), null, dir), "memory");
        assertEquals(Doctor.Severity.WARNING, memory.severity(), memory.toString());
        assertTrue(memory.message().startsWith("the memory budget ("), memory.message());

        Path readOnly = Files.createDirectory(dir.resolve("ro"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("r-xr-xr-x")));
        assertEquals(Doctor.Severity.WARNING, Doctor.workDirectory(readOnly).severity());

        FrameworkException refusal = assertThrows(FrameworkException.class, () -> config("cf.execution.workers=many"));
        List<Doctor.Check> refused = Doctor.probes(null, refusal, dir);
        Doctor.Check configuration = named(refused, "configuration");
        assertEquals(Doctor.Severity.BLOCKING, configuration.severity());
        assertTrue(configuration.details().getFirst().startsWith("CONFIG_"), configuration.details().toString());
        assertEquals(8, refused.size(), "the other probes still run, on the defaults");
    }

    @Test
    void theReportHasTheLegacyShape() {
        Doctor.Report report = new Doctor.Report(List.of(new Doctor.Check("java", Doctor.Severity.OK, "Java 25", List.of("java.home=/j")),
                new Doctor.Check("work_directory", Doctor.Severity.WARNING, "low on space", List.of()),
                new Doctor.Check("sut", Doctor.Severity.BLOCKING, "names no SUT plugin", List.of("a", "b"))));
        assertEquals(Doctor.Severity.BLOCKING, report.overall());
        assertEquals(String.join("\n", "  ✓ [OK      ] java            Java 25", "      java.home=/j",
                "  ! [WARNING ] work_directory  low on space", "  ✗ [BLOCKING] sut             names no SUT plugin", "      a", "      b"),
                report.text());
        assertEquals("""
                {
                  "schema": "cf.doctor/v1",
                  "overall": "BLOCKING",
                  "checks": [
                    {
                      "name": "java",
                      "severity": "OK",
                      "message": "Java 25",
                      "details": [
                        "java.home=/j"
                      ]
                    },""", report.json().substring(0, report.json().indexOf("},") + 2));
        StringBuilder xml = new StringBuilder();
        report.appendXml(xml, "");
        assertTrue(xml.toString().startsWith("<doctor schema=\"cf.doctor/v1\" overall=\"BLOCKING\">\n  <check name=\"java\" severity=\"OK\" "
                + "message=\"Java 25\">\n    <detail>java.home=/j</detail>\n  </check>\n"), xml.toString());
    }
}
