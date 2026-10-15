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

import org.ghyura.cf.app.cli.CliOverrides;
import org.ghyura.cf.app.cli.ExitCode;
import org.ghyura.cf.app.cli.OutputFormat;
import org.ghyura.cf.app.cli.ParsedCommand;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.config.EffectiveConfig;
import org.ghyura.cf.config.LegacyImport;
import org.ghyura.cf.config.LegacyImporter;
import org.ghyura.cf.desktop.session.Doctor;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * {@code doctor [--scenario FILE] [--json FILE]} (GEN:diagnostics-reporting-release; the legacy {@code bundle doctor}): read-only probes of
 * this environment for the resolved configuration, in the legacy report shape, then the application itself. Exits 1
 * (RUN_ENVIRONMENT_BLOCKED) only when a check is BLOCKING; warnings keep exit 0.
 */
final class DoctorCommand implements CommandHandler {

    @Override
    public ExitCode execute(ParsedCommand command, CliContext context) {
        EffectiveConfig effective = null;
        FrameworkException refusal = null;
        try {
            Path file = command.value("--config").map(Path::of).orElse(null);
            List<Path> legacyFiles = command.values("--legacy-config").stream().map(Path::of).toList();
            LegacyImport legacy = legacyFiles.isEmpty() ? null : LegacyImporter.importFiles(legacyFiles, context.environment());
            effective = ConfigResolver.resolve(file, context.environment(), CliOverrides.from(command), legacy);
        } catch (FrameworkException e) {
            refusal = e;
        }
        List<Doctor.Check> checks = new ArrayList<>(Doctor.probes(effective, refusal, Path.of(""), command.value("--scenario").map(Path::of).orElse(null)));
        checks.add(application());
        Doctor.Report report = new Doctor.Report(checks);
        Path json = command.value("--json").map(Path::of).orElse(null);
        if (json != null) {
            try {
                AtomicFiles.write(json, report.json().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the doctor report cannot be written: "
                        + e.getMessage(), SourceLocation.of(json.toString()), "path", json.toString()));
            }
        }
        List<String> blocking = checks.stream().filter(c -> c.severity() == Doctor.Severity.BLOCKING).map(Doctor.Check::name).toList();
        Diagnostic failure = blocking.isEmpty() ? null : Diagnostic.error(ErrorCode.RUN_ENVIRONMENT_BLOCKED, blocking.size()
                + " blocking check(s): " + String.join(", ", blocking), SourceLocation.NONE);
        if (context.format() == OutputFormat.XML) {
            ExitCode code = failure == null ? ExitCode.OK : ExitCode.FAILED;
            StringBuilder xml = new StringBuilder(4096);
            xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            xml.append("<cf-report command=\"doctor\" status=\"").append(failure == null ? "OK" : "FAILED").append("\" exitCode=\"")
                    .append(code.code()).append("\" exitName=\"").append(code.name()).append('"');
            if (json != null) {
                xml.append(" json=\"").append(Xml.escape(json.toString())).append('"');
            }
            xml.append(">\n");
            report.appendXml(xml, "  ");
            if (failure != null) {
                xml.append("  <diagnostics>\n");
                DiagnosticXml.appendAll(xml, "    ", List.of(failure));
                xml.append("  </diagnostics>\n");
            }
            xml.append("</cf-report>\n");
            context.out().print(xml);
            return code;
        }
        context.out().println("DOCTOR");
        context.out().println(report.text());
        context.out().println("\noverall: " + report.overall());
        if (json != null) {
            context.out().println("  ✓ doctor report -> " + json);
        }
        if (failure != null) {
            throw new FrameworkException(failure);
        }
        return ExitCode.OK;
    }

    /** This build: its version banner and where its classes come from, with the jar's SHA-256. */
    private static Doctor.Check application() {
        List<String> details = new ArrayList<>();
        try {
            Path source = Path.of(DoctorCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(source)) {
                details.add("jar=" + source);
                details.add("sha256=" + sha256(source));
            } else {
                details.add("classes=" + source + " (a development build, not the packaged jar)");
            }
        } catch (URISyntaxException | IOException | RuntimeException e) {
            details.add("code source not readable: " + e.getMessage());
        }
        return new Doctor.Check("application", Doctor.Severity.OK, Versions.banner(), details);
    }

    private static String sha256(Path file) throws IOException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), MessageDigest.getInstance("SHA-256"))) {
            MessageDigest digest = ((DigestInputStream) in).getMessageDigest();
            in.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        }
    }
}
