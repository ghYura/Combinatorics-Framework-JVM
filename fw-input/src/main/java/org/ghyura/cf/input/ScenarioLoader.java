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

package org.ghyura.cf.input;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.ConstraintSet;
import org.ghyura.cf.model.spec.ScenarioSpec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The whole of M01's input side in one call: read a scenario file in the format its name says, then interpret
 * it into a {@link ScenarioSpec}. Read-only, no static state; the findings of the reader and of the interpreter
 * come back together, in the order they were made.
 */
public final class ScenarioLoader {

    /**
     * The scenario and every finding that did not stop it (warnings and notes, never errors); for an fwgen spec document,
     * also what the spec said besides its workbook ({@code authored}, null for a workbook); and the FW_Seq dependency graph,
     * of the spec as fwgen built it or of the workbook as bundle plan read it.
     */
    public record Loaded(ScenarioSpec spec, List<Diagnostic> diagnostics, SpecReader.Document authored, SeqGraph graph) {
        public Loaded {
            Objects.requireNonNull(spec, "spec");
            Objects.requireNonNull(graph, "graph");
            diagnostics = List.copyOf(diagnostics);
        }

        /** The same scenario with another constraint layer and findings. */
        public Loaded with(ScenarioSpec changed, List<Diagnostic> findings) {
            return new Loaded(changed, findings, authored, graph);
        }

        /** The settings the scenario chose (an fwgen spec's goals and coverage choices), empty for a workbook. */
        public List<org.ghyura.cf.config.ConfigResolver.ScenarioValue> settings() {
            return authored == null ? List.of() : authored.settings();
        }
    }

    /**
     * The constraint files a run names besides the scenario: an explicit sidecar (cf.constraints.file) and a legacy
     * compiled bonds file (cf.constraints.bondsFile); either may be null.
     */
    public record ConstraintFiles(Path sidecar, Path bonds) {
        public static final ConstraintFiles NONE = new ConstraintFiles(null, null);
    }

    private ScenarioLoader() {
    }

    /**
     * Loads a scenario file and its constraint layer: the companion {@code <stem>.constraints.json} beside it when it
     * exists (as the legacy workbook loader read it), or the explicit sidecar, plus a legacy bonds file.
     *
     * @throws FrameworkException CONSTRAINT_AMBIGUOUS when a companion exists and a different explicit sidecar is named;
     *                            CONSTRAINT_UNREADABLE or CONSTRAINT_INVALID for a file that cannot be applied as written
     */
    public static Loaded load(Path file, ConstraintFiles constraints) {
        return load(file, constraints, InputSource::read);
    }

    /** Loads the scenario, its FW_File inputs and constraint files through one owner's reader. */
    public static Loaded load(Path file, ConstraintFiles constraints, InputSource.Reader inputs) {
        Objects.requireNonNull(constraints, "constraints");
        return withConstraints(file, loadWithInputs(file, inputs), constraints, inputs);
    }

    /** Reads a scenario without its companion, using the same reader for FW_File inputs. */
    public static Loaded loadWithInputs(Path file, InputSource.Reader inputs) {
        Path directory = file.toAbsolutePath().getParent();
        return load(file, FileResolver.inDirectory(directory, InputSource.DEFAULT_MAX_BYTES, inputs), inputs);
    }

    /**
     * Applies the constraint layer {@link #load(Path, ConstraintFiles)} applies to a scenario interpreted otherwise (an
     * editor's unsaved document, for instance), as if it were the given file: its companion sidecar, or the explicit one,
     * and a legacy bonds file.
     */
    public static Loaded withConstraints(Path file, Loaded loaded, ConstraintFiles constraints) {
        return withConstraints(file, loaded, constraints, InputSource::read);
    }

    /** Applies the constraint layer through the supplied reader, retaining the same selection and ambiguity rules. */
    public static Loaded withConstraints(Path file, Loaded loaded, ConstraintFiles constraints, InputSource.Reader inputs) {
        Objects.requireNonNull(constraints, "constraints");
        List<Diagnostic> notes = new ArrayList<>(loaded.diagnostics());
        ConstraintSet layer = loaded.spec().constraints();
        Path companion = ConstraintSidecarReader.companionOf(file);
        boolean hasCompanion = java.nio.file.Files.isRegularFile(companion);
        if (hasCompanion && loaded.authored() != null && (constraints.sidecar() == null || !same(companion, constraints.sidecar()))) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_AMBIGUOUS, "the spec document carries its own constraints "
                    + "and the companion constraint sidecar " + companion + " lies beside it; the legacy loader read companions for "
                    + "workbooks only and would have ignored it: move its rules into the spec, or name it with cf.constraints.file",
                    SourceLocation.of(companion.toString())));
        }
        if (constraints.sidecar() != null) {
            if (hasCompanion && !same(companion, constraints.sidecar())) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_AMBIGUOUS, "the scenario has the companion "
                        + "constraint sidecar " + companion + " and the configuration names " + constraints.sidecar()
                        + "; remove one of them, so that no rule is left out unnoticed", SourceLocation.of(constraints.sidecar().toString())));
            }
            layer = layer.plus(ConstraintSidecarReader.read(constraintFile(constraints.sidecar(), inputs)));
        } else if (hasCompanion) {
            layer = layer.plus(ConstraintSidecarReader.read(constraintFile(companion, inputs)));
            notes.add(Diagnostic.info(ErrorCode.CONSTRAINT_LOADED, "read the companion constraint sidecar " + companion,
                    SourceLocation.of(companion.toString())));
        }
        if (constraints.bonds() != null) {
            layer = layer.plus(ConstraintSidecarReader.readBonds(constraintFile(constraints.bonds(), inputs)));
        }
        if (layer.isEmpty() && layer.sources().isEmpty()) {
            return loaded;
        }
        return loaded.with(loaded.spec().withConstraints(layer), notes);
    }

    private static boolean same(Path a, Path b) {
        try {
            return java.nio.file.Files.isSameFile(a, b);
        } catch (java.io.IOException e) {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        }
    }

    private static InputSource constraintFile(Path path, InputSource.Reader inputs) {
        try {
            return inputs.read(path, InputSource.DEFAULT_MAX_BYTES);
        } catch (FrameworkException e) {
            if (e.primary().code() == ErrorCode.RESOURCE_LIMIT) {
                throw e;
            }
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONSTRAINT_UNREADABLE, "the constraint file cannot be read: "
                    + e.diagnostics().getFirst().message(), SourceLocation.of(path.toString())), e);
        }
    }

    /** Bytes held per byte while a text document (JSON, YAML, TOML, spec) is read into a scenario. */
    public static final long TEXT_READING_FACTOR = 10;

    /** Bytes held per uncompressed byte of a workbook's parts while an XLSX scenario is read (POI keeps its XML trees). */
    public static final long XLSX_READING_FACTOR = 25;

    /**
     * Admission estimate for reading the scenario document, calculated from its size before a byte is
     * parsed: TEXT_READING_FACTOR per byte of a text document, XLSX_READING_FACTOR per byte of a workbook's parts once
     * uncompressed. The factors were measured as the smallest heap that reads a 30 000-cell scenario in each format, minus
     * the JVM's own (handoff 0052); they are not a universal heap bound. An unreadable file estimates as nothing:
     * reading it reports the error. An unrepresentable estimate saturates, so it cannot bypass admission.
     */
    public static long readingBytes(Path file) {
        try {
            long size = java.nio.file.Files.size(file);
            if (WorkbookFormats.formatOf(file.getFileName().toString()) != InputFormat.XLSX) {
                return Math.multiplyExact(TEXT_READING_FACTOR, size);
            }
            long expanded = 0;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    long entry = entries.nextElement().getSize();
                    expanded = Math.addExact(expanded, entry < 0 ? Math.multiplyExact(8, size) : entry);
                }
            }
            return Math.multiplyExact(XLSX_READING_FACTOR, Math.max(expanded, size));
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        } catch (java.io.IOException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * Loads a scenario file, a workbook or an fwgen spec document (compiled as the legacy fwgen compiled it); {@code FW_File=}
     * paths in it resolve against the file's own directory.
     */
    public static Loaded load(Path file) {
        Path directory = file.toAbsolutePath().getParent();
        return load(file, FileResolver.inDirectory(directory, InputSource.DEFAULT_MAX_BYTES));
    }

    /**
     * @throws FrameworkException with every finding when the file cannot be read, is not a schedule, or its
     *                            content has at least one error
     */
    public static Loaded load(Path file, FileResolver files) {
        return load(file, files, InputSource::read);
    }

    private static Loaded load(Path file, FileResolver files, InputSource.Reader inputs) {
        InputFormat format = WorkbookFormats.formatOf(file.getFileName().toString());
        InputSource source = inputs.read(file, InputSource.DEFAULT_MAX_BYTES);
        if (SpecReader.isSpec(format, source)) {
            SpecReader.Compiled compiled = SpecReader.read(source, format);
            Loaded loaded = interpret(new ReadResult(compiled.workbook(), compiled.warnings()), files);
            return new Loaded(loaded.spec().withConstraints(compiled.constraints()), loaded.diagnostics(), compiled.document(), compiled.graph());
        }
        return interpret(WorkbookFormats.reader(format).read(source), files);
    }

    /** Interprets an already read document, keeping the reader's warnings in front of the interpreter's findings. */
    public static Loaded interpret(ReadResult read, FileResolver files) {
        try {
            ScenarioInterpreter.Interpretation interpretation = ScenarioInterpreter.interpret(read.workbook(), files);
            List<Diagnostic> all = new ArrayList<>(read.warnings());
            all.addAll(interpretation.diagnostics());
            return new Loaded(interpretation.spec(), all, null, SeqGraph.ofWorkbook(read.workbook()));
        } catch (FrameworkException e) {
            List<Diagnostic> all = new ArrayList<>(read.warnings());
            all.addAll(e.diagnostics());
            throw new FrameworkException(all);
        }
    }
}
