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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the YAML subset of the legacy Core ({@code YamlScheduleParser}): a top-level {@code sheets:} mapping
 * whose keys are sheet names and whose values are block sequences of rows, each row a flow-style array.
 *
 * <pre>
 * sheets:
 *   FW_Seq:
 *     - ["HEAD", "FW_Exclude", null, "FW_Combi(1)"]
 *   HEAD:
 *     - ["scenario=demo"]
 * </pre>
 *
 * The grammar, the sheet-name pattern and the rejection of everything outside it follow the legacy parser;
 * what is added is a source location for every cell and every problem, INPUT_DUPLICATE_SHEET for a sheet
 * that appears twice (legacy merged their rows), and a warning for top-level keys that are ignored. Each row
 * array is parsed by the same JSON cell rules as the JSON reader.
 */
public final class YamlWorkbookReader implements WorkbookReader {

    private static final Pattern SHEET_NAME_LINE = Pattern.compile("^(\\s+)([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*$");
    private static final Pattern SHEET_ROW_LINE = Pattern.compile("^(\\s+)-\\s*(.+)$");
    private static final Pattern TOP_LEVEL_LINE = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)\\s*:\\s*$");

    @Override
    public InputFormat format() {
        return InputFormat.YAML;
    }

    @Override
    public ReadResult read(InputSource source) {
        String origin = source.name();
        String[] lines = Utf8.decode(source).split("\\R", -1);
        List<Diagnostic> warnings = new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<SourceLocation> nameLocations = new ArrayList<>();
        List<List<InputRow>> rowsBySheet = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        boolean inSheetsBlock = false;
        boolean sheetsSeen = false;
        int sheetIndent = -1;
        int current = -1;

        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String trimmed = raw.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int lineNumber = i + 1;
            int indent = leadingSpaces(raw);

            if (!inSheetsBlock) {
                Matcher top = TOP_LEVEL_LINE.matcher(raw);
                if (top.matches() && indent == 0) {
                    if (top.group(1).equals("sheets")) {
                        if (sheetsSeen) {
                            throw unsupported(origin, lineNumber, 1, "the key 'sheets:' appears twice");
                        }
                        inSheetsBlock = true;
                        sheetsSeen = true;
                    } else if (sheetsSeen) {
                        warnings.add(Diagnostic.warning(ErrorCode.INPUT_SCHEMA, "the top-level key '" + top.group(1)
                                + "' is not part of the schedule format and was ignored",
                                new SourceLocation(origin, lineNumber, 1, "/" + JsonCells.pointer(top.group(1))),
                                "key", top.group(1)));
                    } else {
                        throw unsupported(origin, lineNumber, 1, "the YAML schedule subset expects the top-level key"
                                + " 'sheets:' first; got: " + raw);
                    }
                    continue;
                }
                if (indent == 0) {
                    throw unsupported(origin, lineNumber, 1, "the YAML schedule subset expects the top-level key"
                            + " 'sheets:' first; got: " + raw);
                }
                if (!sheetsSeen) {
                    throw unsupported(origin, lineNumber, indent + 1, "indented content before 'sheets:' is outside the"
                            + " YAML schedule subset: " + raw);
                }
                continue;
            }

            if (indent <= 0) {
                Matcher top = TOP_LEVEL_LINE.matcher(raw);
                if (top.matches()) {
                    inSheetsBlock = false;
                    if (top.group(1).equals("sheets")) {
                        throw unsupported(origin, lineNumber, 1, "the key 'sheets:' appears twice");
                    }
                    warnings.add(Diagnostic.warning(ErrorCode.INPUT_SCHEMA, "the top-level key '" + top.group(1)
                            + "' is not part of the schedule format and was ignored",
                            new SourceLocation(origin, lineNumber, 1, "/" + JsonCells.pointer(top.group(1))),
                            "key", top.group(1)));
                    continue;
                }
                throw unsupported(origin, lineNumber, 1, "unexpected dedent in the sheets block: " + raw);
            }

            Matcher name = SHEET_NAME_LINE.matcher(raw);
            if (name.matches()) {
                if (sheetIndent < 0) {
                    sheetIndent = name.group(1).length();
                }
                if (indent != sheetIndent) {
                    throw unsupported(origin, lineNumber, indent + 1, "sheet name indent is inconsistent (expected "
                            + sheetIndent + ", got " + indent + ")");
                }
                String sheet = name.group(2);
                if (!seen.add(sheet.toLowerCase(Locale.ROOT))) {
                    throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_DUPLICATE_SHEET,
                            "the sheet '" + sheet + "' is defined twice (sheet names are unique ignoring case)",
                            new SourceLocation(origin, lineNumber, indent + 1, "/sheets/" + JsonCells.pointer(sheet)),
                            "sheet", sheet));
                }
                names.add(sheet);
                nameLocations.add(new SourceLocation(origin, lineNumber, indent + 1, "/sheets/" + JsonCells.pointer(sheet)));
                rowsBySheet.add(new ArrayList<>());
                current = names.size() - 1;
                continue;
            }

            Matcher row = SHEET_ROW_LINE.matcher(raw);
            if (row.matches()) {
                if (current < 0) {
                    throw unsupported(origin, lineNumber, indent + 1, "a row '- ...' appears before any sheet name");
                }
                if (indent <= sheetIndent) {
                    throw unsupported(origin, lineNumber, indent + 1, "a row's indent must exceed its sheet name's indent");
                }
                String content = row.group(2).trim();
                int contentColumn = raw.indexOf(content, row.start(2)) + 1;
                if (!content.startsWith("[")) {
                    throw unsupported(origin, lineNumber, contentColumn, "the YAML schedule subset requires rows to be"
                            + " flow-style arrays (for example - [\"a\", \"b\"]): " + content);
                }
                List<InputRow> rows = rowsBySheet.get(current);
                rows.add(parseRow(content, new SourceMap(origin, lineNumber, contentColumn), names.get(current), rows.size()));
                continue;
            }
            throw unsupported(origin, lineNumber, indent + 1, "unrecognised syntax: " + raw);
        }

        if (!sheetsSeen) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA,
                    "the YAML schedule subset requires a top-level 'sheets:' mapping", SourceLocation.of(origin)));
        }
        List<InputSheet> sheets = new ArrayList<>();
        for (int s = 0; s < names.size(); s++) {
            sheets.add(new InputSheet(names.get(s), nameLocations.get(s), rowsBySheet.get(s)));
        }
        return new ReadResult(new WorkbookData(Utf8.displayName("yaml", origin), sheets), warnings);
    }

    private static InputRow parseRow(String content, SourceMap map, String sheetName, int rowIndex) {
        try (JsonParser parser = JsonWorkbookReader.FACTORY.createParser(content)) {
            JsonToken first = parser.nextToken();
            InputRow row = new JsonCells(map).readRow(parser, first, sheetName, rowIndex);
            if (parser.nextToken() != null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                        "content after the row array (a trailing comment is not allowed in this subset)",
                        map.at(parser.currentTokenLocation(), "")));
            }
            return row;
        } catch (JsonProcessingException e) {
            throw JsonWorkbookReader.malformed(e, map);
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, String.valueOf(e.getMessage()),
                    map.at(null, "")), e);
        }
    }

    private static int leadingSpaces(String s) {
        int n = 0;
        while (n < s.length() && s.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static FrameworkException unsupported(String origin, int line, int column, String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, message,
                new SourceLocation(origin, line, column, "")));
    }
}
