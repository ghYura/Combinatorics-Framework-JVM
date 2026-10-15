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
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads the TOML subset of the legacy Core ({@code TomlScheduleParser}): one {@code [sheets]} table whose bare
 * keys are sheet names and whose values are arrays of row arrays, possibly spanning lines, with line comments
 * and trailing commas allowed, and the bare token {@code null} accepted for an empty cell (a pragmatic
 * extension: TOML has no null).
 *
 * <pre>
 * [sheets]
 * FW_Seq = [
 *   ["HEAD", "FW_Exclude", null, "FW_Combi(1)"],
 * ]
 * HEAD = [["scenario=demo"]]
 * </pre>
 *
 * Differences from the legacy reader, all in the direction of not losing data silently: every cell has an
 * exact line and column; comments and trailing commas are blanked out instead of deleted, so positions stay
 * true, and a comma inside a string cell such as {@code "x,]"} is never touched (the legacy cleaner stripped it);
 * a duplicate key is an error; content after the first other table header is reported as ignored; literal
 * strings, multi-line strings and inline tables are named as unsupported rather than failing as bad JSON.
 */
public final class TomlWorkbookReader implements WorkbookReader {

    @Override
    public InputFormat format() {
        return InputFormat.TOML;
    }

    @Override
    public ReadResult read(InputSource source) {
        String origin = source.name();
        String text = Utf8.decode(source);
        LineIndex index = new LineIndex(origin, text);
        List<Diagnostic> warnings = new ArrayList<>();

        int header = findTableHeader(text, "sheets");
        if (header < 0) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_SCHEMA,
                    "the TOML schedule subset requires a '[sheets]' table", SourceLocation.of(origin)));
        }
        int beforeHeader = skipWhitespaceAndComments(text, 0);
        if (beforeHeader < header) {
            warnings.add(Diagnostic.warning(ErrorCode.INPUT_UNSUPPORTED_SYNTAX,
                    "content before the [sheets] table is outside the TOML schedule subset and was ignored",
                    index.at(beforeHeader, ""), "offset", Integer.toString(beforeHeader)));
        }
        int pos = lineEnd(text, header) + 1;

        List<InputSheet> sheets = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        while (pos < text.length()) {
            int t = skipWhitespaceAndComments(text, pos);
            if (t >= text.length()) {
                break;
            }
            if (text.charAt(t) == '[') {
                warnings.add(Diagnostic.warning(ErrorCode.INPUT_UNSUPPORTED_SYNTAX,
                        "everything from this table header on is outside the TOML schedule subset and was ignored",
                        index.at(t, ""), "offset", Integer.toString(t)));
                break;
            }
            int keyStart = t;
            int keyEnd = keyStart;
            while (keyEnd < text.length() && isBareKeyChar(text.charAt(keyEnd))) {
                keyEnd++;
            }
            if (keyEnd == keyStart) {
                throw unsupported(index, keyStart, "expected a bare key (letters, digits, '_' or '-'); quoted and dotted keys"
                        + " are outside the TOML schedule subset");
            }
            String key = text.substring(keyStart, keyEnd);
            int eq = skipSpacesAndTabs(text, keyEnd);
            if (eq >= text.length() || text.charAt(eq) != '=') {
                throw unsupported(index, Math.min(eq, text.length() - 1), "expected '=' after the key '" + key + "'");
            }
            int valueStart = skipWhitespaceAndComments(text, skipSpacesAndTabs(text, eq + 1));
            if (valueStart >= text.length() || text.charAt(valueStart) != '[') {
                throw unsupported(index, Math.min(valueStart, text.length() - 1), "the value of '" + key
                        + "' must be an array of row arrays");
            }
            int valueEnd = findMatchingBracket(text, valueStart, index);
            String path = "/sheets/" + JsonCells.pointer(key);
            if (!seen.add(key.toLowerCase(Locale.ROOT))) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_DUPLICATE_SHEET,
                        "the sheet '" + key + "' is defined twice (sheet names are unique ignoring case)",
                        index.at(keyStart, path), "sheet", key));
            }
            String cleaned = clean(text, valueStart, valueEnd, index);
            sheets.add(new InputSheet(key, index.at(keyStart, path), parseSheet(cleaned, index.mapFrom(valueStart), key)));
            pos = valueEnd + 1;
        }
        return new ReadResult(new WorkbookData(Utf8.displayName("toml", origin), sheets), warnings);
    }

    private static List<InputRow> parseSheet(String cleaned, SourceMap map, String sheetName) {
        try (JsonParser parser = JsonWorkbookReader.FACTORY.createParser(cleaned)) {
            parser.nextToken();
            List<InputRow> rows = new JsonCells(map).readSheetRows(parser, sheetName);
            if (parser.nextToken() != null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED,
                        "content after the end of the array", map.at(parser.currentTokenLocation(), "")));
            }
            return rows;
        } catch (JsonProcessingException e) {
            throw JsonWorkbookReader.malformed(e, map);
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, String.valueOf(e.getMessage()),
                    map.at(null, "")), e);
        }
    }

    // ---- scanning --------------------------------------------------------------------------------------------

    /** Offset of the '[' of a line that is exactly {@code [name]} (after trimming and a trailing comment), or -1. */
    private static int findTableHeader(String s, String name) {
        String target = "[" + name + "]";
        int pos = 0;
        while (pos < s.length()) {
            int lineEnd = lineEnd(s, pos);
            int trimLeft = pos;
            while (trimLeft < lineEnd && (s.charAt(trimLeft) == ' ' || s.charAt(trimLeft) == '\t')) {
                trimLeft++;
            }
            int trimRight = lineEnd;
            for (int i = trimLeft; i < lineEnd; i++) {
                if (s.charAt(i) == '#') {
                    trimRight = i;
                    break;
                }
                if (s.charAt(i) == '"') {
                    int j = i + 1;
                    while (j < lineEnd && s.charAt(j) != '"') {
                        if (s.charAt(j) == '\\') {
                            j++;
                        }
                        j++;
                    }
                    i = j;
                }
            }
            while (trimRight > trimLeft && (s.charAt(trimRight - 1) == ' ' || s.charAt(trimRight - 1) == '\t'
                    || s.charAt(trimRight - 1) == '\r')) {
                trimRight--;
            }
            if (s.substring(trimLeft, trimRight).equals(target)) {
                return trimLeft;
            }
            pos = lineEnd + 1;
        }
        return -1;
    }

    private static int lineEnd(String s, int from) {
        int i = from;
        while (i < s.length() && s.charAt(i) != '\n') {
            i++;
        }
        return i;
    }

    private static int skipSpacesAndTabs(String s, int from) {
        int i = from;
        while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t')) {
            i++;
        }
        return i;
    }

    private static int skipWhitespaceAndComments(String s, int from) {
        int i = from;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                i++;
            } else if (c == '#') {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
            } else {
                break;
            }
        }
        return i;
    }

    private static boolean isBareKeyChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    /** Offset of the ']' that closes the '[' at {@code open}, honouring strings and comments. */
    private static int findMatchingBracket(String s, int open, LineIndex index) {
        int depth = 0;
        int i = open;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '"') {
                int next = skipString(s, i);
                if (next < 0) {
                    throw malformed(index, i, "unterminated string: a basic string must close on the line it opens");
                }
                i = next;
                continue;
            }
            if (c == '#') {
                while (i < s.length() && s.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        throw unsupported(index, open, "this '[' is never closed");
    }

    /**
     * Offset just past the basic string that starts at {@code open}, or -1 when the line ends (or the text ends)
     * before the closing quote: a basic string never spans lines.
     */
    private static int skipString(CharSequence s, int open) {
        int j = open + 1;
        while (j < s.length() && s.charAt(j) != '"') {
            char c = s.charAt(j);
            if (c == '\n' || c == '\r') {
                return -1;
            }
            if (c == '\\' && j + 1 < s.length()) {
                j++;
            }
            j++;
        }
        return j >= s.length() ? -1 : j + 1;
    }

    /**
     * The array text with comments and trailing commas blanked to spaces (newlines kept), so that JSON parsing
     * sees valid JSON and every remaining character keeps its original line and column. Constructs outside the
     * subset are rejected here by name.
     */
    private static String clean(String text, int start, int end, LineIndex index) {
        char[] out = text.substring(start, end + 1).toCharArray();
        CharSequence view = CharBuffer.wrap(out); // a live view of out: no copy per string literal
        int i = 0;
        while (i < out.length) {
            char c = out[i];
            if (c == '"') {
                if (i + 2 < out.length && out[i + 1] == '"' && out[i + 2] == '"') {
                    throw unsupported(index, start + i, "multi-line strings (\"\"\"...\"\"\") are not supported; use a"
                            + " basic string with \\n");
                }
                int next = skipString(view, i);
                if (next < 0) {
                    throw malformed(index, start + i, "unterminated string: a basic string must close on the line it opens");
                }
                i = next;
                continue;
            }
            if (c == '#') {
                while (i < out.length && out[i] != '\n') {
                    out[i++] = ' ';
                }
                continue;
            }
            if (c == '\'') {
                throw unsupported(index, start + i, "literal strings ('...') are not supported; use a basic string"
                        + " in double quotes");
            }
            if (c == '{') {
                throw unsupported(index, start + i, "inline tables are not supported");
            }
            i++;
        }
        // trailing commas, outside strings: a comma whose next non-blank character is ']'
        i = 0;
        while (i < out.length) {
            char c = out[i];
            if (c == '"') {
                i = skipString(view, i); // already validated by the first pass
                continue;
            }
            if (c == ',') {
                int j = i + 1;
                while (j < out.length && Character.isWhitespace(out[j])) {
                    j++;
                }
                if (j < out.length && out[j] == ']') {
                    out[i] = ' ';
                }
            }
            i++;
        }
        return new String(out);
    }

    private static FrameworkException malformed(LineIndex index, int offset, String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, message, index.at(offset, "")));
    }

    private static FrameworkException unsupported(LineIndex index, int offset, String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNSUPPORTED_SYNTAX, message, index.at(offset, "")));
    }
}
