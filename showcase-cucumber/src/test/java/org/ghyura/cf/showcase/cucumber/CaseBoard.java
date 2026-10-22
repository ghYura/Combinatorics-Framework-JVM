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

package org.ghyura.cf.showcase.cucumber;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where step definitions leave measurements for the candidate's metrics line. The Framework loads its execution class
 * path once per run and runs candidates on parallel workers, so this board is per thread (inherited by threads a step
 * starts), never a shared static map: each candidate sees only what its own scenario recorded.
 */
public final class CaseBoard {

    private static final InheritableThreadLocal<Map<String, String>> BOARD = new InheritableThreadLocal<>();

    private CaseBoard() {
    }

    static Map<String, String> open() {
        Map<String, String> board = new ConcurrentHashMap<>();
        BOARD.set(board);
        return board;
    }

    static void close() {
        BOARD.remove();
    }

    /** A measurement; ignored when no scenario of a candidate is running (a standalone Cucumber run). */
    public static void metric(String key, BigDecimal value) {
        put(key, value.stripTrailingZeros().toPlainString());
    }

    public static void metric(String key, long value) {
        put(key, Long.toString(value));
    }

    /** A text attribute, such as the browser that ran the scenario. */
    public static void attribute(String key, String value) {
        put(key, GherkinCase.token(value));
    }

    private static void put(String key, String value) {
        Map<String, String> board = BOARD.get();
        if (board != null) {
            board.put(key, value);
        }
    }
}
