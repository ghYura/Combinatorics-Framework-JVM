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

import com.fasterxml.jackson.core.io.JsonStringEncoder;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * The JSON, YAML and TOML writers. All three write rows as JSON-syntax arrays of strings, with {@code null}
 * standing for a column that has no cell, which is exactly what the readers accept, so a written file reads
 * back to the same logical model. Formula cells cannot be written (a text format has no formula marker, and
 * writing the formula as a plain string would change what it means).
 */
public final class TextWorkbookWriters {

    private static final Pattern YAML_SHEET_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern TOML_BARE_KEY = Pattern.compile("[A-Za-z0-9_-]+");

    private TextWorkbookWriters() {
    }

    public static WorkbookWriter json() {
        return new JsonWriter();
    }

    public static WorkbookWriter yaml() {
        return new YamlWriter();
    }

    public static WorkbookWriter toml() {
        return new TomlWriter();
    }

    private static final class JsonWriter implements WorkbookWriter {
        @Override
        public InputFormat format() {
            return InputFormat.JSON;
        }

        @Override
        public byte[] write(WorkbookData workbook) {
            StringBuilder out = new StringBuilder(1024).append("{\n  \"sheets\": {");
            boolean firstSheet = true;
            for (InputSheet sheet : workbook.sheets()) {
                out.append(firstSheet ? "\n" : ",\n").append("    ");
                firstSheet = false;
                quote(out, sheet.name());
                if (sheet.rows().isEmpty()) {
                    out.append(": []");
                    continue;
                }
                out.append(": [\n");
                for (int i = 0; i < sheet.rows().size(); i++) {
                    out.append("      ").append(row(sheet.rows().get(i))).append(i + 1 < sheet.rows().size() ? ",\n" : "\n");
                }
                out.append("    ]");
            }
            out.append(firstSheet ? "}\n}\n" : "\n  }\n}\n");
            return out.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class YamlWriter implements WorkbookWriter {
        @Override
        public InputFormat format() {
            return InputFormat.YAML;
        }

        @Override
        public byte[] write(WorkbookData workbook) {
            StringBuilder out = new StringBuilder(1024).append("sheets:\n");
            for (InputSheet sheet : workbook.sheets()) {
                if (!YAML_SHEET_NAME.matcher(sheet.name()).matches()) {
                    throw unsupported(sheet, "YAML schedule sheet names are letters, digits and '_' and do not start with a digit");
                }
                out.append("  ").append(sheet.name()).append(":\n");
                for (InputRow row : sheet.rows()) {
                    out.append("    - ").append(row(row)).append('\n');
                }
            }
            return out.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class TomlWriter implements WorkbookWriter {
        @Override
        public InputFormat format() {
            return InputFormat.TOML;
        }

        @Override
        public byte[] write(WorkbookData workbook) {
            StringBuilder out = new StringBuilder(1024).append("[sheets]\n");
            for (InputSheet sheet : workbook.sheets()) {
                if (!TOML_BARE_KEY.matcher(sheet.name()).matches()) {
                    throw unsupported(sheet, "TOML schedule sheet names are bare keys: letters, digits, '_' and '-'");
                }
                if (sheet.rows().isEmpty()) {
                    out.append(sheet.name()).append(" = []\n");
                    continue;
                }
                out.append(sheet.name()).append(" = [\n");
                for (InputRow row : sheet.rows()) {
                    out.append("  ").append(row(row)).append(",\n");
                }
                out.append("]\n");
            }
            return out.toString().getBytes(StandardCharsets.UTF_8);
        }
    }

    /** {@code ["a", null, "b"]}: absent columns before and between cells become null. */
    static String row(InputRow row) {
        StringBuilder out = new StringBuilder("[");
        int next = 0;
        boolean first = true;
        for (InputCell cell : row.cells()) {
            if (cell.isFormula()) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNSUPPORTED_SYNTAX,
                        "a formula cell cannot be written to a text format (it would silently become a string)",
                        cell.location()));
            }
            for (; next < cell.column(); next++) {
                out.append(first ? "" : ", ").append("null");
                first = false;
            }
            out.append(first ? "" : ", ");
            quote(out, cell.text());
            first = false;
            next = cell.column() + 1;
        }
        return out.append(']').toString();
    }

    private static void quote(StringBuilder out, String text) {
        out.append('"');
        JsonStringEncoder.getInstance().quoteAsString(text, out);
        out.append('"');
    }

    private static FrameworkException unsupported(InputSheet sheet, String why) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNSUPPORTED_SYNTAX,
                "the sheet name '" + sheet.name() + "' cannot be written: " + why, sheet.location(),
                "sheet", sheet.name()));
    }
}
