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

package org.ghyura.cf.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Physical input lines grouped into logical commands as the legacy analyzer did (AnalyzerCore.collectLogicalCommands and
 * isShellCommandComplete): a blank or comment line stands alone, and otherwise lines join with '\n' until quotes,
 * backticks, brackets, here-documents and if/for/while/until/select/case blocks are closed and the text does not end in a
 * continuation (\, &&, ||, |, |&, ( or {). A physical line is one of BufferedReader's (split at \n, \r or \r\n), further
 * split at every other line terminator of {@code \R} (vertical tab, form feed, NEL, U+2028, U+2029), as the legacy
 * in-memory path did. The grammar is the legacy one, including its limits: block keywords count only at the start of a
 * line, so a one-line {@code for ...; done} leaves the block open. An unterminated last command starts at its first
 * physical line (delta D56; the legacy in-memory path reported its last).
 */
final class LogicalLines {

    private static final Pattern LINE_BREAK = Pattern.compile("\\R");
    private static final Pattern OPENS = Pattern.compile("(?i)^(if|for|while|until|select|case)\\b.*");
    private static final Pattern CLOSES = Pattern.compile("(?i)^(fi|done|esac)\\b.*");

    record Block(int firstLine, int lastLine, String text) {
    }

    private LogicalLines() {
    }

    static List<Block> group(List<String> physicalLines) {
        List<Block> blocks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int start = -1;
        int lineNo = 0;
        for (String rawLine : physicalLines) {
            String safe = rawLine == null ? "" : rawLine.replace("\r", "");
            for (String segment : LINE_BREAK.split(safe, -1)) {
                lineNo++;
                String trimmed = segment.trim();
                if (current.isEmpty()) {
                    if (trimmed.isEmpty() || TextFeatures.COMMENT.matcher(trimmed).lookingAt()) {
                        blocks.add(new Block(lineNo, lineNo, segment));
                        continue;
                    }
                    start = lineNo;
                } else if (trimmed.isEmpty() && complete(current.toString())) {
                    blocks.add(new Block(start, lineNo - 1, current.toString()));
                    current.setLength(0);
                    blocks.add(new Block(lineNo, lineNo, segment));
                    start = -1;
                    continue;
                }
                if (!current.isEmpty()) {
                    current.append('\n');
                }
                current.append(segment);
                if (complete(current.toString())) {
                    blocks.add(new Block(start, lineNo, current.toString()));
                    current.setLength(0);
                    start = -1;
                }
            }
        }
        if (!current.isEmpty()) {
            blocks.add(new Block(start, lineNo, current.toString()));
        }
        return blocks;
    }

    /** Whether the text is a complete shell command (legacy isShellCommandComplete). */
    static boolean complete(String text) {
        String normalized = text.replace("\r", "");
        if (normalized.trim().isEmpty()) {
            return false;
        }
        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean backtick = false;
        int parenDepth = 0;
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean continuation = false;
        int scriptDepth = 0;
        List<String> heredocs = new ArrayList<>();
        for (String line : normalized.split("\\n", -1)) {
            if (!heredocs.isEmpty()) {
                String terminator = heredocs.getFirst();
                String trimmed = line.trim();
                if (trimmed.equals(terminator) || trimmed.equals("-" + terminator)) {
                    heredocs.removeFirst();
                }
                continuation = false;
                continue;
            }
            boolean escaped = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (singleQuote) {
                    if (ch == '\'') singleQuote = false;
                    continue;
                }
                if (doubleQuote) {
                    if (escaped) {
                        escaped = false;
                        continue;
                    }
                    if (ch == '\\') {
                        escaped = true;
                        continue;
                    }
                    if (ch == '"') doubleQuote = false;
                    continue;
                }
                if (backtick) {
                    if (ch == '`') backtick = false;
                    continue;
                }
                if (ch == '\\') {
                    escaped = !escaped;
                    continue;
                }
                if (ch == '\'') {
                    singleQuote = true;
                    continue;
                }
                if (ch == '"') {
                    doubleQuote = true;
                    continue;
                }
                if (ch == '`') {
                    backtick = true;
                    continue;
                }
                if (ch == '(') {
                    parenDepth++;
                    continue;
                }
                if (ch == ')' && parenDepth > 0) {
                    parenDepth--;
                    continue;
                }
                if (ch == '{') {
                    braceDepth++;
                    continue;
                }
                if (ch == '}' && braceDepth > 0) {
                    braceDepth--;
                    continue;
                }
                if (ch == '[') {
                    bracketDepth++;
                    continue;
                }
                if (ch == ']' && bracketDepth > 0) {
                    bracketDepth--;
                    continue;
                }
                if (ch == '<' && i + 1 < line.length() && line.charAt(i + 1) == '<') {
                    int j = i + 2;
                    if (j < line.length() && line.charAt(j) == '-') j++;
                    while (j < line.length() && Character.isWhitespace(line.charAt(j))) j++;
                    int from = j;
                    while (j < line.length()) {
                        char c = line.charAt(j);
                        if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                            j++;
                        } else {
                            break;
                        }
                    }
                    if (j > from) {
                        heredocs.add(line.substring(from, j));
                        i = j - 1;
                    }
                }
            }
            String trimmed = line.trim();
            if (!singleQuote && !doubleQuote && !backtick) {
                if (OPENS.matcher(trimmed).matches()) scriptDepth++;
                if (CLOSES.matcher(trimmed).matches()) scriptDepth = Math.max(0, scriptDepth - 1);
                continuation = trimmed.endsWith("\\") || trimmed.endsWith("&&") || trimmed.endsWith("||") || trimmed.endsWith("|")
                        || trimmed.endsWith("|&") || trimmed.endsWith("(") || trimmed.endsWith("{");
            } else {
                continuation = true;
            }
        }
        return !singleQuote && !doubleQuote && !backtick && parenDepth == 0 && braceDepth == 0 && bracketDepth == 0
                && heredocs.isEmpty() && scriptDepth == 0 && !continuation;
    }
}
