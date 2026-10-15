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

package org.ghyura.cf.python;

import java.util.Optional;

/**
 * Streams the legacy first {@code __FWV__ a b} match (py_executor's {@code _FWV.search}).
 * Digits are accumulated with checked int arithmetic, so even an arbitrarily long or zero-padded number retains
 * constant state. A wide number is reported only once both numbers form a complete marker; malformed prefixes do
 * not hide a later valid marker. As with the legacy pattern, the second number may end at any non-digit or at EOF.
 */
final class VerdictMarker {

    private static final String PREFIX = "__FWV__ ";
    private static final int[] PREFIX_FALLBACK = {0, 1, 0, 0, 0, 1, 2, 0};

    private enum State { PREFIX, FIRST_START, FIRST_DIGIT, FIRST, SECOND_START, SECOND_DIGIT, SECOND }

    private State state = State.PREFIX;
    private int prefixLength;
    private int number;
    private boolean negative;
    private boolean overflow;
    private int first;
    private boolean firstOverflow;
    private int second;
    private boolean found;

    synchronized void accept(String piece) {
        for (int i = 0; i < piece.length() && !found; i++) {
            char c = piece.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            switch (state) {
                case PREFIX -> search(c);
                case FIRST_START, SECOND_START -> {
                    boolean firstNumber = state == State.FIRST_START;
                    if (c == '-') {
                        negative = true;
                        state = firstNumber ? State.FIRST_DIGIT : State.SECOND_DIGIT;
                    } else if (digit) {
                        digit(c);
                        state = firstNumber ? State.FIRST : State.SECOND;
                    } else restart(c);
                }
                case FIRST_DIGIT, SECOND_DIGIT -> {
                    if (digit) {
                        digit(c);
                        state = state == State.FIRST_DIGIT ? State.FIRST : State.SECOND;
                    } else restart(c);
                }
                case FIRST -> {
                    if (digit) digit(c);
                    else if (c == ' ') {
                        first = negative ? number : -number;
                        firstOverflow = overflow;
                        resetNumber();
                        state = State.SECOND_START;
                    } else restart(c);
                }
                case SECOND -> {
                    if (digit) digit(c);
                    else finish();
                }
            }
        }
    }

    synchronized void end() {
        if (!found && state == State.SECOND) finish();
    }

    synchronized Optional<int[]> values() {
        if (!found) return Optional.empty();
        if (firstOverflow || overflow) throw new NumberFormatException("Python verdict is outside the Java int range");
        return Optional.of(new int[]{first, second});
    }

    private void search(char c) {
        while (prefixLength > 0 && c != PREFIX.charAt(prefixLength)) prefixLength = PREFIX_FALLBACK[prefixLength - 1];
        if (c == PREFIX.charAt(prefixLength)) prefixLength++;
        if (prefixLength == PREFIX.length()) {
            prefixLength = 0;
            resetNumber();
            state = State.FIRST_START;
        }
    }

    private void restart(char c) {
        state = State.PREFIX;
        prefixLength = 0;
        search(c);
    }

    private void resetNumber() {
        number = 0;
        negative = false;
        overflow = false;
    }

    private void digit(char c) {
        if (overflow) return;
        int digit = c - '0';
        int limit = negative ? Integer.MIN_VALUE : -Integer.MAX_VALUE;
        if (number < limit / 10 || number * 10 < limit + digit) overflow = true;
        else number = number * 10 - digit;
    }

    private void finish() {
        second = negative ? number : -number;
        found = true;
    }
}
