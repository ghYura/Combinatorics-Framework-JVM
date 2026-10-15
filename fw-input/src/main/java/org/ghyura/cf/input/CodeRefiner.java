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

import java.util.regex.Pattern;

/**
 * The text transformation behind the {@code FW_RefineCodeExceptQuoted} cell directive: remove comments, turn
 * line breaks into spaces and squeeze the white space that is not inside a quoted string. It is a port of the
 * legacy {@code StringButQuotesRefiner} as the workbook parser used it (no extra padding rules configured),
 * with the legacy regular expressions kept verbatim so the output is the same for every text the legacy
 * accepted.
 *
 * <p>One deliberate difference: several of those expressions backtrack exponentially on a line that opens a
 * double quote, never closes it and holds many backslashes, which hangs the legacy parser. Every regular
 * expression here runs against a text that counts the characters it reads; a text that needs far more work
 * than any ordinary one is refused with {@link TooComplexException} instead of hanging.</p>
 */
public final class CodeRefiner {

    /** The texts the legacy expressions cannot process in reasonable time; reported as a size/complexity limit. */
    public static final class TooComplexException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TooComplexException(long budget) {
            super("the text needs more than " + budget + " character reads to refine (unbalanced quotes with many "
                    + "backslashes make the legacy expressions explode)");
        }
    }

    private static final Pattern COMMENTS = Pattern.compile("//.*|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R(?<![\\\\])");
    private static final Pattern UNESCAPED_DOUBLE_QUOTE = Pattern.compile("((?<!\\\\){1}\")");
    private static final Pattern SINGLE_QUOTE = Pattern.compile("'");
    private static final Pattern WHITESPACE_RUN = Pattern.compile("(\\s|\\t){2,}(?=([^']*'[^']*')*[^']*$)");
    private static final Pattern DROPPABLE_WHITESPACE = Pattern.compile(
            "((?<!')\\B(\\s|\\t){1,}((?<!')\\B))|(\\b(\\s|\\t){1,}((?<!')\\B))|((?<!')\\B(\\s|\\t){1,}\\b)");

    private final Budget budget;

    private CodeRefiner(String text) {
        this.budget = new Budget(32L * (text.length() + 64L) * Math.min(text.length() + 64L, 8192L));
    }

    /**
     * Removes comments, then line breaks, then the white space outside quotes: the legacy
     * {@code shrinkManySpacedStringExceptAnyQuoted(removeNewLines(removeCommentsFromMultipleLinedString(text)))}.
     *
     * @throws TooComplexException when the text makes the expressions exceed their work budget
     */
    public static String refine(String text) {
        CodeRefiner refiner = new CodeRefiner(text);
        return refiner.shrinkSpacesOutsideQuotes(refiner.removeLineBreaks(refiner.removeComments(text)));
    }

    /** Replaces {@code //} and block comments by a space; a double-quoted string is kept and followed by a space. */
    static String stripComments(String text) {
        return new CodeRefiner(text).removeComments(text);
    }

    private String removeComments(String text) {
        boolean lone = text.equals("\"");
        String work = lone ? "\" " : text;
        String result = COMMENTS.matcher(budget.wrap(work)).replaceAll("$1 ");
        if (lone) {
            result = result.replace("\" ", "\"");
        }
        return result;
    }

    private String removeLineBreaks(String text) {
        return LINE_BREAK.matcher(budget.wrap(text)).replaceAll(" ");
    }

    private String shrinkSpacesOutsideQuotes(String text) {
        String[] outer = UNESCAPED_DOUBLE_QUOTE.split(budget.wrap(text));
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < outer.length; i++) {
            if (i % 2 == 0) {
                String[] inner = SINGLE_QUOTE.split(budget.wrap(outer[i]));
                for (int j = 0; j < inner.length; j++) {
                    if (j % 2 == 0) {
                        out.append(squeeze(inner[j]));
                    } else {
                        out.append('\'').append(inner[j]).append('\'');
                    }
                }
            } else {
                out.append('"').append(outer[i]).append('"');
            }
        }
        return out.toString();
    }

    private String squeeze(String segment) {
        String collapsed = WHITESPACE_RUN.matcher(budget.wrap(segment)).replaceAll(" ");
        return DROPPABLE_WHITESPACE.matcher(budget.wrap(collapsed)).replaceAll("");
    }

    /** Counts the characters the regular expressions read and stops them past the limit. */
    private static final class Budget {
        private final long limit;
        private long used;

        Budget(long limit) {
            this.limit = limit;
        }

        CharSequence wrap(String text) {
            return new Counted(text, this);
        }

        void charge(long amount) {
            used += amount;
            if (used > limit) {
                throw new TooComplexException(limit);
            }
        }
    }

    private record Counted(String text, Budget budget) implements CharSequence {
        @Override
        public int length() {
            return text.length();
        }

        @Override
        public char charAt(int index) {
            budget.charge(1);
            return text.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            budget.charge(end - start);
            return text.substring(start, end);
        }

        @Override
        public String toString() {
            budget.charge(text.length());
            return text;
        }
    }
}
