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

package org.ghyura.cf.compiler;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the top-level type a generated source declares, so the compiler can be given the file name Java requires
 * (the legacy executor's extractClassNameSTATIC). Comments, string, character and text-block literals are skipped;
 * only declarations at brace depth 0 count.
 */
public final class TypeNames {

    private static final Pattern DECLARATION = Pattern.compile(
            "(?:^|[\\s;}])((?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\\s+)*)"
                    + "(class|interface|enum|record|@interface)\\s+([\\p{javaJavaIdentifierStart}][\\p{javaJavaIdentifierPart}]*)");

    private TypeNames() {
    }

    /** The first public top-level type, else the first top-level type, of {@code source}. */
    public static Optional<String> mainType(String source) {
        String code = topLevelText(source);
        String first = null;
        Matcher m = DECLARATION.matcher(code);
        while (m.find()) {
            String name = m.group(3);
            if (m.group(1).contains("public")) {
                return Optional.of(name);
            }
            if (first == null) {
                first = name;
            }
        }
        return Optional.ofNullable(first);
    }

    /**
     * The source with comments and literals blanked and everything inside braces blanked, so that only depth-0 text
     * remains; positions are kept (blanked characters become spaces).
     */
    static String topLevelText(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int depth = 0;
        int i = 0;
        int n = source.length();
        while (i < n) {
            char c = source.charAt(i);
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '/') {
                while (i < n && source.charAt(i) != '\n') {
                    out.append(' ');
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && source.charAt(i + 1) == '*') {
                int end = source.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                blank(out, stop - i);
                i = stop;
                continue;
            }
            if (c == '"' && source.startsWith("\"\"\"", i)) {
                int end = source.indexOf("\"\"\"", i + 3);
                while (end > 0 && escaped(source, end)) {
                    end = source.indexOf("\"\"\"", end + 1);
                }
                int stop = end < 0 ? n : end + 3;
                blank(out, stop - i);
                i = stop;
                continue;
            }
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n && source.charAt(j) != c && source.charAt(j) != '\n') {
                    j += source.charAt(j) == '\\' ? 2 : 1;
                }
                int stop = Math.min(n, j + 1);
                blank(out, stop - i);
                i = stop;
                continue;
            }
            if (c == '{') {
                depth++;
                out.append(' ');
            } else if (c == '}') {
                depth = Math.max(0, depth - 1);
                out.append(depth == 0 ? '}' : ' ');
            } else {
                out.append(depth == 0 ? c : (c == '\n' ? '\n' : ' '));
            }
            i++;
        }
        return out.toString();
    }

    private static boolean escaped(String s, int index) {
        int backslashes = 0;
        for (int k = index - 1; k >= 0 && s.charAt(k) == '\\'; k--) {
            backslashes++;
        }
        return backslashes % 2 == 1;
    }

    private static void blank(StringBuilder out, int count) {
        for (int k = 0; k < count; k++) {
            out.append(' ');
        }
    }
}
