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

package org.ghyura.cf.model.input;

import org.ghyura.cf.model.Canonical;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The normalized, source-located content of one scenario file, independent of its format: ordered sheets of
 * physical rows of cells. XLSX, JSON, YAML, TOML and the Java builder all produce this same model, and gate
 * A03 compares them through {@link #canonicalText()}, which ignores locations and the stored kind of a cell
 * (a numeric cell 3 and the text "3" are the same logical cell) but keeps sheet order, empty rows, column
 * positions, text and the formula marker.
 *
 * <p>Sheet names are unique ignoring case, as in a workbook; a name is kept exactly as written. Nothing here
 * holds a POI object, a stream or a file handle.</p>
 */
public record WorkbookData(String source, List<InputSheet> sheets) {

    public WorkbookData {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(sheets, "sheets");
        sheets = List.copyOf(sheets);
        Set<String> seen = new HashSet<>();
        for (InputSheet sheet : sheets) {
            if (!seen.add(sheet.name().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("duplicate sheet name (ignoring case): '" + sheet.name() + "'");
            }
        }
    }

    public Optional<InputSheet> sheet(String exactName) {
        for (InputSheet sheet : sheets) {
            if (sheet.name().equals(exactName)) {
                return Optional.of(sheet);
            }
        }
        return Optional.empty();
    }

    public List<String> sheetNames() {
        return sheets.stream().map(InputSheet::name).toList();
    }

    /**
     * A deterministic text of the logical content: one line per sheet, row and cell. Two inputs that mean the
     * same scenario have the same canonical text whatever their format, file name or cell kinds.
     */
    public String canonicalText() {
        StringBuilder out = new StringBuilder(1024);
        out.append("cf.workbook/1\n");
        for (InputSheet sheet : sheets) {
            out.append("S\t").append(Canonical.escape(sheet.name())).append('\n');
            for (InputRow row : sheet.rows()) {
                out.append("R\n");
                for (InputCell cell : row.cells()) {
                    out.append("C\t").append(cell.column()).append('\t').append(cell.isFormula() ? 'f' : 'v').append('\t')
                            .append(Canonical.escape(cell.text())).append('\n');
                }
            }
        }
        return out.toString();
    }

    /** SHA-256 of the canonical text, as 64 lowercase hex characters: the content identity of the input. */
    public String contentDigest() {
        return Canonical.sha256Hex(canonicalText());
    }
}
