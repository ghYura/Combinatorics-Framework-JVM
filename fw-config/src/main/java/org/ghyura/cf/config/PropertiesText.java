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

package org.ghyura.cf.config;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.SourceLocation;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads {@code .properties} text with the escape and continuation rules of {@link Properties}, but with what
 * the JDK reader cannot give: the line each key was defined on, and a duplicate key is an error instead of a
 * silent last-wins. Each logical line is handed to the JDK parser on its own, so escapes behave identically.
 */
final class PropertiesText {

    record Line(String key, String value, int line) {
    }

    private PropertiesText() {
    }

    /**
     * @param origin   name used in locations (file path as given)
     * @param problems receives CONFIG_MALFORMED and CONFIG_DUPLICATE_KEY diagnostics
     * @return the entries in file order; for a duplicated key only the first occurrence
     */
    static List<Line> parse(String origin, String text, List<Diagnostic> problems) {
        return parse(origin, text, problems, false);
    }

    /**
     * Every entry of a legacy file in file order, a repeated key included: {@link Properties} kept the last one
     * silently, so the caller reports the repetition and applies the last. No duplicate error is raised.
     */
    static List<Line> parseAll(String origin, String text, List<Diagnostic> problems) {
        return parse(origin, text, problems, true);
    }

    private static List<Line> parse(String origin, String text, List<Diagnostic> problems, boolean keepRepeats) {
        String body = text.startsWith("﻿") ? text.substring(1) : text;
        List<String> physical = physicalLines(body);
        List<Line> entries = new ArrayList<>();
        Map<String, Integer> firstLine = new HashMap<>();

        StringBuilder logical = new StringBuilder();
        int logicalStart = 0;
        for (int lineNumber = 1; lineNumber <= physical.size(); lineNumber++) {
            String line = physical.get(lineNumber - 1);
            if (logical.isEmpty()) {
                String stripped = line.stripLeading();
                if (stripped.isEmpty() || stripped.charAt(0) == '#' || stripped.charAt(0) == '!') {
                    continue;
                }
                logicalStart = lineNumber;
            }
            logical.append(line).append('\n');
            if (!continues(line)) {
                accept(origin, logical.toString(), logicalStart, entries, firstLine, problems, keepRepeats);
                logical.setLength(0);
            }
        }
        if (!logical.isEmpty()) {
            accept(origin, logical.toString(), logicalStart, entries, firstLine, problems, keepRepeats);
        }
        return entries;
    }

    /** Splits on LF, CR LF and CR; a terminator at the very end does not create an extra empty line. */
    static List<String> physicalLines(String body) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int i = 0;
        while (i < body.length()) {
            char c = body.charAt(i);
            if (c == '\n' || c == '\r') {
                lines.add(body.substring(start, i));
                if (c == '\r' && i + 1 < body.length() && body.charAt(i + 1) == '\n') {
                    i++;
                }
                i++;
                start = i;
            } else {
                i++;
            }
        }
        if (start < body.length()) {
            lines.add(body.substring(start));
        }
        return lines;
    }

    /** A physical line continues when it ends in an odd number of backslashes. */
    private static boolean continues(String line) {
        int backslashes = 0;
        for (int i = line.length() - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static void accept(String origin, String logical, int startLine, List<Line> entries,
                               Map<String, Integer> firstLine, List<Diagnostic> problems, boolean keepRepeats) {
        Properties one = new Properties();
        try {
            one.load(new StringReader(logical));
        } catch (IOException | IllegalArgumentException e) {
            problems.add(Diagnostic.error(ErrorCode.CONFIG_MALFORMED,
                    "not valid properties syntax: " + e.getMessage(), SourceLocation.of(origin, startLine)));
            return;
        }
        for (String key : one.stringPropertyNames()) {
            Integer seen = firstLine.get(key);
            if (seen != null && !keepRepeats) {
                problems.add(Diagnostic.error(ErrorCode.CONFIG_DUPLICATE_KEY,
                        "'" + key + "' is already defined on line " + seen, SourceLocation.of(origin, startLine),
                        "key", key, "firstLine", Integer.toString(seen)));
                continue;
            }
            firstLine.putIfAbsent(key, startLine);
            entries.add(new Line(key, one.getProperty(key), startLine));
        }
    }
}
