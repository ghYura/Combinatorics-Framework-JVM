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

/*
 * The TOML parser is adapted from CPython tomllib (original SPDX-License-Identifier: MIT).
 * Original SPDX-FileCopyrightText: 2021 Taneli Hukkinen.
 * Licensed to PSF under a Contributor Agreement.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.ghyura.cf.input;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.RuleValue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A complete TOML 1.0 reader, a statement-by-statement port of Python's {@code tomllib} (the reader the legacy fwgen
 * loaded spec files with), so that exactly the documents it accepted are accepted, with its messages and positions,
 * and every part of the result knows its line and column. Dates and times become texts as Python prints them.
 */
final class TomlReader {

    private static final int FROZEN = 0;
    private static final int EXPLICIT_NEST = 1;
    private static final int MAX_DEPTH = 128;

    private static final String TIME = "([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9])(?:\\.([0-9]{1,6})[0-9]*)?";
    private static final Pattern NUMBER = Pattern.compile("0(?:x[0-9A-Fa-f](?:_?[0-9A-Fa-f])*|b[01](?:_?[01])*|o[0-7](?:_?[0-7])*)"
            + "|[+-]?(?:0|[1-9](?:_?[0-9])*)(?<floatpart>(?:\\.[0-9](?:_?[0-9])*)?(?:[eE][+-]?[0-9](?:_?[0-9])*)?)");
    private static final Pattern LOCAL_TIME = Pattern.compile(TIME);
    private static final Pattern DATE_TIME = Pattern.compile("([0-9]{4})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])"
            + "(?:[Tt ]" + TIME + "(?:([Zz])|([+-])([01][0-9]|2[0-3]):([0-5][0-9]))?)?");

    private final String origin;
    private final String src;
    private final int len;
    private final int[] lineStarts;
    private int pos;
    private int depth;

    private TomlReader(String origin, String text) {
        this.origin = origin;
        this.src = text.replace("\r\n", "\n");
        this.len = src.length();
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < len; i++) {
            if (src.charAt(i) == '\n') {
                starts.add(i + 1);
            }
        }
        this.lineStarts = starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /**
     * Reads a TOML document (newlines already normalized as Python's text mode reads a file).
     *
     * @throws FrameworkException INPUT_MALFORMED with tomllib's message and position
     */
    static DocTree.Obj read(String origin, String text) {
        TomlReader reader = new TomlReader(origin, text);
        MTable root = reader.document();
        return (DocTree.Obj) reader.convert(root, "", 0);
    }

    // ---- the mutable result, as tomllib builds it -----------------------------------------------------------

    private static final class MTable {
        final Map<String, Object> map = new LinkedHashMap<>();
        final int at;

        MTable(int at) {
            this.at = at;
        }
    }

    private static final class MList {
        final List<Object> items = new ArrayList<>();
        final int at;

        MList(int at) {
            this.at = at;
        }
    }

    private record MLeaf(RuleValue value, DocTree.Temporal temporal, int at) {
    }

    private static final class NoNest extends Exception {
        private static final long serialVersionUID = 1L;
    }

    /** tomllib's Flags: which namespaces are frozen (inline tables, arrays) or explicitly declared. */
    private static final class Flags {
        private static final class Node {
            final Set<Integer> flags = new HashSet<>();
            final Set<Integer> recursive = new HashSet<>();
            final Map<String, Node> nested = new HashMap<>();
        }

        private record Pending(List<String> key, int flag) {
        }

        private final Map<String, Node> root = new HashMap<>();
        private final Set<Pending> pending = new LinkedHashSet<>();

        void addPending(List<String> key, int flag) {
            pending.add(new Pending(List.copyOf(key), flag));
        }

        void finalizePending() {
            for (Pending p : pending) {
                set(p.key(), p.flag(), false);
            }
            pending.clear();
        }

        void unsetAll(List<String> key) {
            Map<String, Node> cont = root;
            for (String k : key.subList(0, key.size() - 1)) {
                Node n = cont.get(k);
                if (n == null) {
                    return;
                }
                cont = n.nested;
            }
            cont.remove(key.getLast());
        }

        void set(List<String> key, int flag, boolean recursive) {
            Map<String, Node> cont = root;
            for (String k : key.subList(0, key.size() - 1)) {
                cont = cont.computeIfAbsent(k, x -> new Node()).nested;
            }
            Node stem = cont.computeIfAbsent(key.getLast(), x -> new Node());
            (recursive ? stem.recursive : stem.flags).add(flag);
        }

        boolean is(List<String> key, int flag) {
            if (key.isEmpty()) {
                return false;
            }
            Map<String, Node> cont = root;
            for (String k : key.subList(0, key.size() - 1)) {
                Node inner = cont.get(k);
                if (inner == null) {
                    return false;
                }
                if (inner.recursive.contains(flag)) {
                    return true;
                }
                cont = inner.nested;
            }
            Node stem = cont.get(key.getLast());
            return stem != null && (stem.flags.contains(flag) || stem.recursive.contains(flag));
        }
    }

    private static MTable getOrCreateNest(MTable root, List<String> key, boolean accessLists, int at) throws NoNest {
        Object cont = root;
        for (String k : key) {
            MTable table = (MTable) cont;
            cont = table.map.computeIfAbsent(k, x -> new MTable(at));
            if (accessLists && cont instanceof MList list) {
                if (list.items.isEmpty()) {
                    throw new NoNest();
                }
                cont = list.items.getLast();
            }
            if (!(cont instanceof MTable)) {
                throw new NoNest();
            }
        }
        return (MTable) cont;
    }

    private static void appendNestToList(MTable root, List<String> key, int at) throws NoNest {
        MTable cont = getOrCreateNest(root, key.subList(0, key.size() - 1), true, at);
        String last = key.getLast();
        Object existing = cont.map.get(last);
        if (existing != null) {
            if (!(existing instanceof MList list)) {
                throw new NoNest();
            }
            list.items.add(new MTable(at));
        } else {
            MList list = new MList(at);
            list.items.add(new MTable(at));
            cont.map.put(last, list);
        }
    }

    // ---- statements -----------------------------------------------------------------------------------------

    private MTable document() {
        MTable root = new MTable(0);
        Flags flags = new Flags();
        List<String> header = List.of();
        while (true) {
            skipChars(" \t");
            if (pos >= len) {
                break;
            }
            char c = src.charAt(pos);
            if (c == '\n') {
                pos++;
                continue;
            }
            if (isBareKeyChar(c) || c == '"' || c == '\'') {
                keyValueRule(root, flags, header);
                skipChars(" \t");
            } else if (c == '[') {
                boolean list = pos + 1 < len && src.charAt(pos + 1) == '[';
                flags.finalizePending();
                header = list ? createListRule(root, flags) : createDictRule(root, flags);
                skipChars(" \t");
            } else if (c != '#') {
                throw error("Invalid statement", pos);
            }
            skipComment();
            if (pos >= len) {
                break;
            }
            if (src.charAt(pos) != '\n') {
                throw error("Expected newline or end of document after a statement", pos);
            }
            pos++;
        }
        return root;
    }

    private List<String> createDictRule(MTable root, Flags flags) {
        int at = pos;
        pos++;
        skipChars(" \t");
        List<String> key = parseKey();
        if (flags.is(key, EXPLICIT_NEST) || flags.is(key, FROZEN)) {
            throw error("Cannot declare " + tupleRepr(key) + " twice", pos);
        }
        flags.set(key, EXPLICIT_NEST, false);
        try {
            getOrCreateNest(root, key, true, at);
        } catch (NoNest e) {
            throw error("Cannot overwrite a value", pos);
        }
        if (!src.startsWith("]", pos)) {
            throw error("Expected ']' at the end of a table declaration", pos);
        }
        pos++;
        return key;
    }

    private List<String> createListRule(MTable root, Flags flags) {
        int at = pos;
        pos += 2;
        skipChars(" \t");
        List<String> key = parseKey();
        if (flags.is(key, FROZEN)) {
            throw error("Cannot mutate immutable namespace " + tupleRepr(key), pos);
        }
        flags.unsetAll(key);
        flags.set(key, EXPLICIT_NEST, false);
        try {
            appendNestToList(root, key, at);
        } catch (NoNest e) {
            throw error("Cannot overwrite a value", pos);
        }
        if (!src.startsWith("]]", pos)) {
            throw error("Expected ']]' at the end of an array declaration", pos);
        }
        pos += 2;
        return key;
    }

    private void keyValueRule(MTable root, Flags flags, List<String> header) {
        int at = pos;
        List<String> key = parseKey();
        Object value = afterKey();
        List<String> keyParent = key.subList(0, key.size() - 1);
        String keyStem = key.getLast();
        List<String> absKeyParent = concat(header, keyParent);
        for (int i = 1; i < key.size(); i++) {
            List<String> contKey = concat(header, key.subList(0, i));
            if (flags.is(contKey, EXPLICIT_NEST)) {
                throw error("Cannot redefine namespace " + tupleRepr(contKey), pos);
            }
            flags.addPending(contKey, EXPLICIT_NEST);
        }
        if (flags.is(absKeyParent, FROZEN)) {
            throw error("Cannot mutate immutable namespace " + tupleRepr(absKeyParent), pos);
        }
        MTable nest;
        try {
            nest = getOrCreateNest(root, absKeyParent, true, at);
        } catch (NoNest e) {
            throw error("Cannot overwrite a value", pos);
        }
        if (nest.map.containsKey(keyStem)) {
            throw error("Cannot overwrite a value", pos);
        }
        if (value instanceof MTable || value instanceof MList) {
            flags.set(concat(header, key), FROZEN, true);
        }
        nest.map.put(keyStem, value);
    }

    /** The {@code = value} after a key (parse_key_value_pair without the key). */
    private Object afterKey() {
        if (pos >= len || src.charAt(pos) != '=') {
            throw error("Expected '=' after a key in a key/value pair", pos);
        }
        pos++;
        skipChars(" \t");
        return parseValue();
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a.size() + b.size());
        out.addAll(a);
        out.addAll(b);
        return out;
    }

    private List<String> parseKey() {
        List<String> key = new ArrayList<>();
        key.add(parseKeyPart());
        skipChars(" \t");
        while (pos < len && src.charAt(pos) == '.') {
            pos++;
            skipChars(" \t");
            key.add(parseKeyPart());
            skipChars(" \t");
        }
        return key;
    }

    private String parseKeyPart() {
        char c = pos < len ? src.charAt(pos) : 0;
        if (pos < len && isBareKeyChar(c)) {
            int start = pos;
            while (pos < len && isBareKeyChar(src.charAt(pos))) {
                pos++;
            }
            return src.substring(start, pos);
        }
        if (pos < len && c == '\'') {
            return parseLiteralStr();
        }
        if (pos < len && c == '"') {
            pos++;
            return parseBasicStr(false);
        }
        throw error("Invalid initial character for a key part", pos);
    }

    private static boolean isBareKeyChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
    }

    // ---- values ---------------------------------------------------------------------------------------------

    private Object parseValue() {
        if (++depth > MAX_DEPTH) throw depthLimit("");
        try {
            return parseValueAtDepth();
        } finally {
            depth--;
        }
    }

    private Object parseValueAtDepth() {
        int at = pos;
        char c = pos < len ? src.charAt(pos) : 0;
        if (pos < len && c == '"') {
            String s;
            if (src.startsWith("\"\"\"", pos)) {
                s = parseMultilineStr(false);
            } else {
                pos++;
                s = parseBasicStr(false);
            }
            return new MLeaf(new RuleValue.Text(s), DocTree.Temporal.NONE, at);
        }
        if (pos < len && c == '\'') {
            String s = src.startsWith("'''", pos) ? parseMultilineStr(true) : parseLiteralStr();
            return new MLeaf(new RuleValue.Text(s), DocTree.Temporal.NONE, at);
        }
        if (c == 't' && src.startsWith("true", pos)) {
            pos += 4;
            return new MLeaf(new RuleValue.Bool(true), DocTree.Temporal.NONE, at);
        }
        if (c == 'f' && src.startsWith("false", pos)) {
            pos += 5;
            return new MLeaf(new RuleValue.Bool(false), DocTree.Temporal.NONE, at);
        }
        if (pos < len && c == '[') {
            return parseArray();
        }
        if (pos < len && c == '{') {
            return parseInlineTable();
        }
        Matcher dt = DATE_TIME.matcher(src).region(pos, len);
        if (dt.lookingAt()) {
            MLeaf leaf = dateTime(dt, at);
            pos = dt.end();
            return leaf;
        }
        Matcher lt = LOCAL_TIME.matcher(src).region(pos, len);
        if (lt.lookingAt()) {
            pos = lt.end();
            return new MLeaf(new RuleValue.Text(time(lt, 1)), DocTree.Temporal.TIME, at);
        }
        Matcher number = NUMBER.matcher(src).region(pos, len);
        if (number.lookingAt()) {
            pos = number.end();
            return new MLeaf(number(number), DocTree.Temporal.NONE, at);
        }
        String three = src.substring(pos, Math.min(len, pos + 3));
        if (three.equals("inf") || three.equals("nan")) {
            pos += 3;
            return new MLeaf(new RuleValue.Real(three.equals("inf") ? Double.POSITIVE_INFINITY : Double.NaN), DocTree.Temporal.NONE, at);
        }
        String four = src.substring(pos, Math.min(len, pos + 4));
        if (four.equals("-inf") || four.equals("+inf") || four.equals("-nan") || four.equals("+nan")) {
            pos += 4;
            double d = four.endsWith("nan") ? Double.NaN : four.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
            return new MLeaf(new RuleValue.Real(d), DocTree.Temporal.NONE, at);
        }
        throw error("Invalid value", pos);
    }

    private static RuleValue number(Matcher m) {
        String text = m.group();
        String floatPart = m.group("floatpart");
        if (floatPart != null && !floatPart.isEmpty()) {
            return new RuleValue.Real(Double.parseDouble(text.replace("_", "")));
        }
        String digits = text.replace("_", "");
        if (digits.startsWith("0x")) {
            return new RuleValue.Int(new BigInteger(digits.substring(2), 16));
        }
        if (digits.startsWith("0o")) {
            return new RuleValue.Int(new BigInteger(digits.substring(2), 8));
        }
        if (digits.startsWith("0b")) {
            return new RuleValue.Int(new BigInteger(digits.substring(2), 2));
        }
        return new RuleValue.Int(new BigInteger(digits));
    }

    /** tomllib's match_to_datetime, rendered as Python's {@code str()} of the date or datetime. */
    private MLeaf dateTime(Matcher m, int at) {
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        int day = Integer.parseInt(m.group(3));
        if (year < 1 || day > java.time.YearMonth.of(year, month).lengthOfMonth()) {
            throw error("Invalid date or datetime", at);
        }
        String date = String.format(Locale.ROOT, "%04d-%02d-%02d", year, month, day);
        if (m.group(4) == null) {
            return new MLeaf(new RuleValue.Text(date), DocTree.Temporal.DATE, at);
        }
        String text = date + " " + time(m, 4);
        if (m.group(9) != null) {
            int minutes = Integer.parseInt(m.group(10)) * 60 + Integer.parseInt(m.group(11));
            text += (m.group(9).equals("-") && minutes != 0 ? "-" : "+")
                    + String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
        } else if (m.group(8) != null) {
            text += "+00:00";
        }
        return new MLeaf(new RuleValue.Text(text), DocTree.Temporal.DATE_TIME, at);
    }

    /** Python's {@code str()} of the time in groups {@code first..first+3}: microseconds only when not zero. */
    private static String time(Matcher m, int first) {
        String text = m.group(first) + ":" + m.group(first + 1) + ":" + m.group(first + 2);
        String fraction = m.group(first + 3);
        if (fraction != null) {
            String micros = (fraction + "000000").substring(0, 6);
            if (Integer.parseInt(micros) != 0) {
                text += "." + micros;
            }
        }
        return text;
    }

    private MList parseArray() {
        MList array = new MList(pos);
        pos++;
        skipCommentsAndArrayWs();
        if (src.startsWith("]", pos)) {
            pos++;
            return array;
        }
        while (true) {
            array.items.add(parseValue());
            skipCommentsAndArrayWs();
            char c = pos < len ? src.charAt(pos) : 0;
            if (pos < len && c == ']') {
                pos++;
                return array;
            }
            if (pos >= len || c != ',') {
                throw error("Unclosed array", pos);
            }
            pos++;
            skipCommentsAndArrayWs();
            if (src.startsWith("]", pos)) {
                pos++;
                return array;
            }
        }
    }

    private MTable parseInlineTable() {
        MTable table = new MTable(pos);
        pos++;
        Flags flags = new Flags();
        skipChars(" \t");
        if (src.startsWith("}", pos)) {
            pos++;
            return table;
        }
        while (true) {
            int at = pos;
            List<String> key = parseKey();
            Object value = afterKey();
            String keyStem = key.getLast();
            if (flags.is(key, FROZEN)) {
                throw error("Cannot mutate immutable namespace " + tupleRepr(key), pos);
            }
            MTable nest;
            try {
                nest = getOrCreateNest(table, key.subList(0, key.size() - 1), false, at);
            } catch (NoNest e) {
                throw error("Cannot overwrite a value", pos);
            }
            if (nest.map.containsKey(keyStem)) {
                throw error("Duplicate inline table key " + PyText.repr(keyStem), pos);
            }
            nest.map.put(keyStem, value);
            skipChars(" \t");
            char c = pos < len ? src.charAt(pos) : 0;
            if (pos < len && c == '}') {
                pos++;
                return table;
            }
            if (pos >= len || c != ',') {
                throw error("Unclosed inline table", pos);
            }
            if (value instanceof MTable || value instanceof MList) {
                flags.set(key, FROZEN, true);
            }
            pos++;
            skipChars(" \t");
        }
    }

    // ---- strings --------------------------------------------------------------------------------------------

    private String parseLiteralStr() {
        pos++;
        int start = pos;
        pos = skipUntil(pos, "'", false, true);
        String s = src.substring(start, pos);
        pos++;
        return s;
    }

    private String parseMultilineStr(boolean literal) {
        pos += 3;
        if (src.startsWith("\n", pos)) {
            pos++;
        }
        String result;
        String delim;
        if (literal) {
            delim = "'";
            int end = skipUntil(pos, "'''", true, true);
            result = src.substring(pos, end);
            pos = end + 3;
        } else {
            delim = "\"";
            result = parseBasicStr(true);
        }
        if (!src.startsWith(delim, pos)) {
            return result;
        }
        pos++;
        if (!src.startsWith(delim, pos)) {
            return result + delim;
        }
        pos++;
        return result + delim + delim;
    }

    private String parseBasicStr(boolean multiline) {
        StringBuilder result = new StringBuilder();
        int start = pos;
        while (true) {
            if (pos >= len) {
                throw error("Unterminated string", pos);
            }
            char c = src.charAt(pos);
            if (c == '"') {
                if (!multiline) {
                    result.append(src, start, pos);
                    pos++;
                    return result.toString();
                }
                if (src.startsWith("\"\"\"", pos)) {
                    result.append(src, start, pos);
                    pos += 3;
                    return result.toString();
                }
                pos++;
                continue;
            }
            if (c == '\\') {
                result.append(src, start, pos);
                result.append(parseEscape(multiline));
                start = pos;
                continue;
            }
            if (illegal(c, multiline)) {
                throw error("Illegal character " + PyText.repr(String.valueOf(c)), pos);
            }
            pos++;
        }
    }

    private String parseEscape(boolean multiline) {
        String id = src.substring(pos, Math.min(len, pos + 2));
        pos += 2;
        if (multiline && (id.equals("\\ ") || id.equals("\\\t") || id.equals("\\\n"))) {
            if (!id.equals("\\\n")) {
                skipChars(" \t");
                if (pos >= len) {
                    return "";
                }
                if (src.charAt(pos) != '\n') {
                    throw error("Unescaped '\\' in a string", pos);
                }
                pos++;
            }
            skipChars(" \t\n");
            return "";
        }
        if (id.equals("\\u")) {
            return hexChar(4);
        }
        if (id.equals("\\U")) {
            return hexChar(8);
        }
        return switch (id) {
            case "\\b" -> "\b";
            case "\\t" -> "\t";
            case "\\n" -> "\n";
            case "\\f" -> "\f";
            case "\\r" -> "\r";
            case "\\\"" -> "\"";
            case "\\\\" -> "\\";
            default -> throw error("Unescaped '\\' in a string", pos);
        };
    }

    private String hexChar(int hexLen) {
        String hex = src.substring(Math.min(len, pos), Math.min(len, pos + hexLen));
        if (hex.length() != hexLen || !hex.chars().allMatch(c -> (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
            throw error("Invalid hex value", pos);
        }
        pos += hexLen;
        long cp = Long.parseLong(hex, 16);
        if (!((cp >= 0 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0x10FFFF))) {
            throw error("Escaped character is not a Unicode scalar value", pos);
        }
        return Character.toString((int) cp);
    }

    private static boolean illegal(char c, boolean multiline) {
        return (c < 0x20 && c != '\t' && !(multiline && c == '\n')) || c == 0x7F;
    }

    // ---- whitespace and comments ----------------------------------------------------------------------------

    private void skipChars(String chars) {
        while (pos < len && chars.indexOf(src.charAt(pos)) >= 0) {
            pos++;
        }
    }

    private int skipUntil(int from, String expect, boolean multiline, boolean errorOnEof) {
        int found = src.indexOf(expect, from);
        if (found < 0) {
            found = len;
            if (errorOnEof) {
                throw error("Expected " + PyText.repr(expect), found);
            }
        }
        for (int k = from; k < found; k++) {
            if (illegal(src.charAt(k), multiline)) {
                throw error("Found invalid character " + PyText.repr(String.valueOf(src.charAt(k))), k);
            }
        }
        return found;
    }

    private void skipComment() {
        if (pos < len && src.charAt(pos) == '#') {
            pos = skipUntil(pos + 1, "\n", false, false);
        }
    }

    private void skipCommentsAndArrayWs() {
        while (true) {
            int before = pos;
            skipChars(" \t\n");
            skipComment();
            if (pos == before) {
                return;
            }
        }
    }

    // ---- results and errors ---------------------------------------------------------------------------------

    private FrameworkException depthLimit(String path) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_LIMIT,
                "TOML nesting exceeds the limit of " + MAX_DEPTH, location(pos, path)));
    }

    private DocTree convert(Object value, String path, int level) {
        if (level > MAX_DEPTH) throw depthLimit(path);
        return switch (value) {
            case MTable t -> {
                Map<String, DocTree> fields = new LinkedHashMap<>();
                t.map.forEach((k, v) -> fields.put(k, convert(v, path + "/" + DocTree.pointer(k), level + 1)));
                yield new DocTree.Obj(fields, location(t.at, path));
            }
            case MList l -> {
                List<DocTree> items = new ArrayList<>();
                for (int i = 0; i < l.items.size(); i++) {
                    items.add(convert(l.items.get(i), path + "/" + i, level + 1));
                }
                yield new DocTree.Arr(items, location(l.at, path));
            }
            case MLeaf leaf -> new DocTree.Leaf(leaf.value(), leaf.temporal(), location(leaf.at(), path));
            default -> throw new IllegalStateException("unexpected TOML node " + value);
        };
    }

    private int lineOf(int offset) {
        int low = 0;
        int high = lineStarts.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (lineStarts[mid] <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low + 1;
    }

    /** Python's line and column: the column counts code points from the line start. */
    private SourceLocation location(int offset, String path) {
        int at = Math.min(offset, len);
        int line = lineOf(at);
        int column = src.codePointCount(lineStarts[line - 1], at) + 1;
        return new SourceLocation(origin, line, column, path);
    }

    private FrameworkException error(String message, int offset) {
        SourceLocation where = location(offset, "");
        String coordinate = offset >= len ? "end of document" : "line " + where.line() + ", column " + where.column();
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "not valid TOML: " + message + " (at " + coordinate + ")",
                where));
    }

    /** Python's repr of a key tuple, as tomllib's messages print it. */
    private static String tupleRepr(List<String> key) {
        StringBuilder b = new StringBuilder("(");
        for (int i = 0; i < key.size(); i++) {
            if (i > 0) {
                b.append(", ");
            }
            b.append(PyText.repr(key.get(i)));
        }
        return b.append(key.size() == 1 ? ",)" : ")").toString();
    }
}
