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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.input.InputRow;
import org.ghyura.cf.model.input.InputSheet;
import org.ghyura.cf.model.input.WorkbookData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the JSON schedule format of the legacy Core ({@code JsonScheduleParser}):
 *
 * <pre>
 * { "sheets": { "FW_Seq": [ ["HEAD", "FW_Exclude", null, "FW_Combi(1)"], ... ], "HEAD": [["x=1"]] } }
 * </pre>
 *
 * Sheet order is the order of the keys. Cell and row rules are in {@link JsonCells}. Beyond the legacy reader:
 * every cell carries its exact line and column and a JSON-pointer path, a duplicate sheet name is an error
 * instead of the last one silently winning, and other top-level keys are reported as warnings instead of
 * vanishing without a trace. The document is decoded as strict UTF-8.
 */
public final class JsonWorkbookReader implements WorkbookReader {

    static final JsonFactory FACTORY = JsonFactory.builder()
            .disable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build();

    @Override
    public InputFormat format() {
        return InputFormat.JSON;
    }

    @Override
    public ReadResult read(InputSource source) {
        String origin = source.name();
        SourceMap map = SourceMap.whole(origin);
        List<Diagnostic> warnings = new ArrayList<>();
        try (JsonParser parser = FACTORY.createParser(Utf8.decode(source))) {
            JsonCells cells = new JsonCells(map);
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_OBJECT) {
                throw schema(map, "a JSON schedule is an object with a \"sheets\" object; found "
                        + (token == null ? "an empty document" : JsonCells.describe(token)), parser.currentLocation(), "");
            }
            List<InputSheet> sheets = null;
            while ((token = parser.nextToken()) == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonLocation fieldLocation = parser.currentTokenLocation();
                parser.nextToken();
                if (field.equals("sheets")) {
                    if (sheets != null) {
                        throw schema(map, "the key \"sheets\" appears twice", fieldLocation, "/sheets");
                    }
                    sheets = readSheets(parser, cells, map);
                } else {
                    warnings.add(Diagnostic.warning(ErrorCode.INPUT_SCHEMA, "the top-level key '" + field
                            + "' is not part of the schedule format and was ignored",
                            map.at(fieldLocation, "/" + JsonCells.pointer(field)), "key", field));
                    parser.skipChildren();
                }
            }
            if (parser.nextToken() != null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                        "content after the end of the JSON document", map.at(parser.currentTokenLocation(), "")));
            }
            if (sheets == null) {
                throw schema(map, "Expected top-level 'sheets' object in JSON schedule", parser.currentLocation(), "");
            }
            return new ReadResult(new WorkbookData(Utf8.displayName("json", origin), sheets), warnings);
        } catch (JsonProcessingException e) {
            throw malformed(e, map);
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, e.getMessage() == null
                    ? e.getClass().getSimpleName() : e.getMessage(), map.at(null, "")), e);
        }
    }

    private static List<InputSheet> readSheets(JsonParser parser, JsonCells cells, SourceMap map) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw schema(map, "'sheets' must be an object whose keys are sheet names", parser.currentTokenLocation(), "/sheets");
        }
        List<InputSheet> sheets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String name = parser.currentName();
            JsonLocation nameLocation = parser.currentTokenLocation();
            String path = "/sheets/" + JsonCells.pointer(name);
            if (name.isBlank()) {
                throw schema(map, "a sheet needs a name", nameLocation, path);
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_DUPLICATE_SHEET,
                        "the sheet '" + name + "' is defined twice (sheet names are unique ignoring case)",
                        map.at(nameLocation, path), "sheet", name));
            }
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw schema(map, "sheets[\"" + name + "\"] must be a JSON array of row arrays",
                        parser.currentTokenLocation(), path);
            }
            List<InputRow> rows = cells.readSheetRows(parser, name);
            sheets.add(new InputSheet(name, map.at(nameLocation, path), rows));
        }
        return sheets;
    }

    private static FrameworkException schema(SourceMap map, String message, JsonLocation where, String path) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA, message, map.at(where, path)));
    }

    static FrameworkException malformed(JsonProcessingException e, SourceMap map) {
        ErrorCode code = e instanceof StreamConstraintsException ? ErrorCode.INPUT_LIMIT : ErrorCode.INPUT_MALFORMED;
        return new FrameworkException(Diagnostic.error(code, e.getOriginalMessage(), map.at(e.getLocation(), "")), e);
    }
}
