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

package org.ghyura.cf.generation;

import java.util.List;
import java.util.regex.Pattern;

/**
 * The value refinement of the legacy Reader (Reader_trunk StringButQuotesRefiner, the variant ComboGenerationPipeline
 * used): comments removed, line breaks turned into spaces, runs of white space outside quotes collapsed to one space
 * (the Reader's "bred" variant keeps single spaces, unlike the Core's), then the configured affix expressions applied
 * outside quotes. The legacy regular expressions are kept verbatim, and so are Java's split and replaceAll semantics
 * the legacy code relied on, so the output is the legacy output for every text the legacy handled.
 *
 * <p>One deliberate difference: the comment expression backtracks exponentially on a text that opens a double quote,
 * never closes it and holds many backslashes. Every expression here reads a text that counts its character reads, and a
 * text needing far more work than any ordinary one is refused ({@link TooComplexException}) instead of hanging.</p>
 */
final class ReaderRefiner {

    /** A text the legacy expressions cannot process in reasonable time. */
    static final class TooComplexException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        TooComplexException(long budget) {
            super("the value needs more than " + budget + " character reads to refine (unbalanced quotes with many backslashes "
                    + "make the legacy expressions explode)");
        }
    }

    private static final Pattern COMMENTS = Pattern.compile("//.*|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/");
    private static final Pattern LINE_BREAK = Pattern.compile("\\R(?<![\\\\])");
    private static final Pattern TWO_QUOTES = Pattern.compile(".*(\"){1,}.*((?<!\\\\){1}\"){1,}.*");
    private static final Pattern TWO_APOSTROPHES = Pattern.compile(".*('){1,}.*('){1,}.*");
    private static final Pattern UNESCAPED_QUOTE = Pattern.compile("((?<!\\\\){1}\")");
    private static final Pattern APOSTROPHE = Pattern.compile("'");
    private static final Pattern RUN = Pattern.compile("(\\s|\\t){2,}(?=([^']*'[^']*')*[^']*$)");

    private final List<Pattern> spaceBefore;
    private final List<Pattern> spaceAfter;
    private final List<Pattern> newlineBefore;
    private final List<Pattern> newlineAfter;
    private final List<String> spaceBeforeText;
    private final List<String> spaceAfterText;
    private final List<String> newlineBeforeText;
    private final List<String> newlineAfterText;

    ReaderRefiner(RenderOptions options) {
        this.spaceBeforeText = options.spaceBefore();
        this.spaceAfterText = options.spaceAfter();
        this.newlineBeforeText = options.newlineBefore();
        this.newlineAfterText = options.newlineAfter();
        this.spaceBefore = spaceBeforeText.stream().map(Pattern::compile).toList();
        this.spaceAfter = spaceAfterText.stream().map(Pattern::compile).toList();
        this.newlineBefore = newlineBeforeText.stream().map(Pattern::compile).toList();
        this.newlineAfter = newlineAfterText.stream().map(Pattern::compile).toList();
    }

    /** shrinkManySpacedStringExceptAnyQuoted(removeNewLines(removeCommentsFromMultipleLinedString(text))). */
    String refine(String text) {
        Budget budget = new Budget(32L * (text.length() + 64L) * Math.min(text.length() + 64L, 8192L));
        return shrink(removeLineBreaks(removeComments(text, budget), budget), budget);
    }

    private static String removeComments(String text, Budget budget) {
        boolean lone = text.equals("\"");
        String work = lone ? "\" " : text;
        String result = COMMENTS.matcher(budget.wrap(work)).replaceAll("$1 ");
        if (lone) {
            result = result.replaceAll("\"\\s", "\"");
        }
        return result;
    }

    private static String removeLineBreaks(String text, Budget budget) {
        return LINE_BREAK.matcher(budget.wrap(text)).replaceAll(" ");
    }

    private String shrink(String text, Budget budget) {
        StringBuilder out = new StringBuilder(text.length());
        if (TWO_QUOTES.matcher(budget.wrap(text)).matches()) {
            String[] splits = UNESCAPED_QUOTE.split(budget.wrap(text));
            for (int i = 0; i < splits.length; i++) {
                if (i % 2 == 0) {
                    if (TWO_APOSTROPHES.matcher(budget.wrap(splits[i])).matches()) {
                        String[] inner = APOSTROPHE.split(budget.wrap(splits[i]));
                        for (int j = 0; j < inner.length; j++) {
                            if (j % 2 == 0) {
                                out.append(affix(collapse(inner[j], budget), budget));
                            } else {
                                out.append('\'').append(inner[j]).append('\'');
                            }
                        }
                    } else {
                        out.append(affix(collapse(splits[i], budget), budget));
                    }
                } else {
                    out.append('"').append(splits[i]).append('"');
                }
            }
        } else if (TWO_APOSTROPHES.matcher(budget.wrap(text)).matches()) {
            String[] splits = APOSTROPHE.split(budget.wrap(text));
            for (int k = 0; k < splits.length; k++) {
                if (k % 2 == 0) {
                    out.append(affix(collapse(splits[k], budget), budget));
                } else {
                    out.append('\'').append(splits[k]).append('\'');
                }
            }
        } else {
            out.append(affix(collapse(text, budget), budget));
        }
        return out.toString();
    }

    private static String collapse(String segment, Budget budget) {
        return RUN.matcher(budget.wrap(segment)).replaceAll(" ");
    }

    /** applyAffixRules: each expression is a regular expression, and the replacement text is the expression itself. */
    private String affix(String text, Budget budget) {
        String current = text;
        for (int i = 0; i < spaceBefore.size(); i++) {
            current = spaceBefore.get(i).matcher(budget.wrap(current)).replaceAll(" " + spaceBeforeText.get(i));
        }
        for (int i = 0; i < spaceAfter.size(); i++) {
            current = spaceAfter.get(i).matcher(budget.wrap(current)).replaceAll(spaceAfterText.get(i) + " ");
        }
        for (int i = 0; i < newlineBefore.size(); i++) {
            current = newlineBefore.get(i).matcher(budget.wrap(current)).replaceAll("\n" + newlineBeforeText.get(i));
        }
        for (int i = 0; i < newlineAfter.size(); i++) {
            current = newlineAfter.get(i).matcher(budget.wrap(current)).replaceAll(newlineAfterText.get(i) + "\n");
        }
        return current;
    }

    /** Counts the characters the expressions read and stops them past the limit. */
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
