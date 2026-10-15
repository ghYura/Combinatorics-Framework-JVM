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

import java.nio.file.Path;

/** Picks the reader or writer of a format, and reads a file by its name. */
public final class WorkbookFormats {

    private WorkbookFormats() {
    }

    public static WorkbookReader reader(InputFormat format) {
        return switch (format) {
            case JSON -> new JsonWorkbookReader();
            case YAML -> new YamlWorkbookReader();
            case TOML -> new TomlWorkbookReader();
            case XLSX -> new XlsxWorkbookReader();
        };
    }

    public static WorkbookWriter writer(InputFormat format) {
        return switch (format) {
            case JSON -> TextWorkbookWriters.json();
            case YAML -> TextWorkbookWriters.yaml();
            case TOML -> TextWorkbookWriters.toml();
            case XLSX -> new XlsxWorkbookWriter();
        };
    }

    /**
     * The format named by a file name's extension.
     *
     * @throws FrameworkException INPUT_UNRECOGNIZED_FORMAT: unlike the legacy reader, an unknown extension is
     *                            not treated as XLSX
     */
    public static InputFormat formatOf(String fileName) {
        return InputFormat.fromFileName(fileName).orElseThrow(() -> new FrameworkException(Diagnostic.error(
                ErrorCode.INPUT_UNRECOGNIZED_FORMAT, "cannot tell the input format from the name '" + fileName
                        + "' (expected .json, .yaml, .yml, .toml, .xlsx or .xlsm)", SourceLocation.of(fileName))));
    }

    /**
     * Reads a workbook file, read-only, in the format its name says.
     *
     * @throws FrameworkException INPUT_UNSUPPORTED_SYNTAX for an fwgen spec document: it is a scenario, read by
     *                            {@link ScenarioLoader}, not a workbook to edit or convert
     */
    public static ReadResult readFile(Path file) {
        InputFormat format = formatOf(file.getFileName().toString());
        InputSource source = InputSource.read(file);
        if (SpecReader.isSpec(format, source)) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, file.getFileName() + " is an fwgen spec "
                    + "document (" + SpecReader.FORMAT + "), not a workbook: validate, plan, candidates and run read it as a scenario, "
                    + "the workbook editor edits workbooks only", SourceLocation.of(file.toString())));
        }
        return reader(format).read(source);
    }
}
