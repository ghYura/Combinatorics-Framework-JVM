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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.io.NumberOutput;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.input.CellKind;
import org.ghyura.cf.model.input.InputCell;
import org.ghyura.cf.model.input.InputRow;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the rows and cells of one sheet from a JSON token stream. This is the single place where JSON cell
 * semantics live, shared by the JSON reader and by the YAML and TOML subset readers, which hand it the array
 * text they cut out of their files:
 *
 * <ul>
 *   <li>a row is an array of cells; a string or number as a row is a one-cell row; null is an empty row;
 *       a boolean or an object as a row is an error;</li>
 *   <li>within a row, null leaves its column empty (the cell is absent, not blank); strings, numbers and
 *       booleans become TEXT cells whose text is what the legacy reader wrote: the string itself, the number
 *       as Jackson's tree model renders it ({@code 3}, {@code 1.0}, {@code 1.0E10}), {@code true} or
 *       {@code false}; an array or object inside a cell is an error where the legacy reader silently produced
 *       an empty string.</li>
 * </ul>
 */
final class JsonCells {

    private final SourceMap map;

    JsonCells(SourceMap map) {
        this.map = map;
    }

    /** The current token is START_ARRAY of a sheet's rows; consumes through the matching END_ARRAY. */
    List<InputRow> readSheetRows(JsonParser parser, String sheetName) throws IOException {
        List<InputRow> rows = new ArrayList<>();
        int index = 0;
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_ARRAY) {
                return rows;
            }
            if (token == null) {
                throw schema("the rows of sheet '" + sheetName + "' end unexpectedly", parser.currentLocation(), "");
            }
            rows.add(readRow(parser, token, sheetName, index++));
        }
    }

    /** Reads one row whose first token has already been read. */
    InputRow readRow(JsonParser parser, JsonToken first, String sheetName, int rowIndex) throws IOException {
        String rowPath = "/sheets/" + pointer(sheetName) + "/" + rowIndex;
        switch (first) {
            case START_ARRAY -> {
                List<InputCell> cells = new ArrayList<>();
                int column = 0;
                while (true) {
                    JsonToken token = parser.nextToken();
                    if (token == JsonToken.END_ARRAY) {
                        return new InputRow(rowIndex, cells);
                    }
                    if (token == null) {
                        throw schema("row " + rowIndex + " of sheet '" + sheetName + "' ends unexpectedly",
                                parser.currentLocation(), rowPath);
                    }
                    String cellPath = rowPath + "/" + column;
                    if (token != JsonToken.VALUE_NULL) {
                        cells.add(new InputCell(column, CellKind.TEXT, scalarText(parser, token, sheetName, cellPath),
                                map.at(parser.currentTokenLocation(), cellPath)));
                    }
                    column++;
                }
            }
            case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> {
                String cellPath = rowPath + "/0";
                InputCell cell = new InputCell(0, CellKind.TEXT, scalarText(parser, first, sheetName, cellPath),
                        map.at(parser.currentTokenLocation(), cellPath));
                return new InputRow(rowIndex, List.of(cell));
            }
            case VALUE_NULL -> {
                return new InputRow(rowIndex, List.of());
            }
            default -> throw schema("row " + rowIndex + " of sheet '" + sheetName + "' must be an array of cells, a string,"
                    + " a number or null; found " + describe(first), parser.currentTokenLocation(), rowPath);
        }
    }

    private String scalarText(JsonParser parser, JsonToken token, String sheetName, String path) throws IOException {
        return switch (token) {
            case VALUE_STRING -> parser.getText();
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> numberText(parser);
            case VALUE_TRUE -> "true";
            case VALUE_FALSE -> "false";
            default -> throw schema("a cell of sheet '" + sheetName + "' must be a string, a number, a boolean or null;"
                    + " found " + describe(token), parser.currentTokenLocation(), path);
        };
    }

    /** The text Jackson's tree model gives a number: integers as integers, everything else as a double. */
    static String numberText(JsonParser parser) throws IOException {
        return switch (parser.getNumberType()) {
            case INT, LONG -> Long.toString(parser.getLongValue());
            case BIG_INTEGER -> parser.getBigIntegerValue().toString();
            default -> NumberOutput.toString(parser.getDoubleValue());
        };
    }

    private FrameworkException schema(String message, JsonLocation where, String path) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA, message, map.at(where, path)));
    }

    static String describe(JsonToken token) {
        return switch (token) {
            case START_OBJECT -> "an object";
            case START_ARRAY -> "an array";
            case VALUE_TRUE, VALUE_FALSE -> "a boolean";
            case VALUE_NULL -> "null";
            case VALUE_STRING -> "a string";
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> "a number";
            default -> token.toString();
        };
    }

    /** JSON pointer escaping of one path segment. */
    static String pointer(String segment) {
        return segment.replace("~", "~0").replace("/", "~1");
    }
}
