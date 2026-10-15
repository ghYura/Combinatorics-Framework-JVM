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

package org.ghyura.cf.execution;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Routes {@code System.out} and {@code System.err} by thread while runs execute: a thread bound to a sample's
 * {@link Capture} (and the threads it starts) writes into that capture, every other thread writes to the streams the
 * process had. Generated candidates print with System.out, so this is how their output is kept per sample without
 * reaching the console. The streams are replaced while at least one run executes and restored when the last one ends.
 */
final class OutputRouter {

    private static final InheritableThreadLocal<Capture> BOUND = new InheritableThreadLocal<>();
    private static final Object LOCK = new Object();
    private static int users;
    private static PrintStream originalOut;
    private static PrintStream originalErr;

    private OutputRouter() {
    }

    /** Installs the routing streams for one more run. */
    static void acquire() {
        synchronized (LOCK) {
            if (users++ == 0) {
                originalOut = System.out;
                originalErr = System.err;
                System.setOut(new PrintStream(new Routing(originalOut, false), true, StandardCharsets.UTF_8));
                System.setErr(new PrintStream(new Routing(originalErr, true), true, StandardCharsets.UTF_8));
            }
        }
    }

    /** One run fewer; the last one restores the original streams. */
    static void release() {
        synchronized (LOCK) {
            if (users > 0 && --users == 0) {
                System.out.flush();
                System.err.flush();
                System.setOut(originalOut);
                System.setErr(originalErr);
                originalOut = null;
                originalErr = null;
            }
        }
    }

    static void bind(Capture capture) {
        BOUND.set(capture);
    }

    static void unbind() {
        BOUND.remove();
    }

    private static final class Routing extends OutputStream {
        private final PrintStream fallback;
        private final boolean error;

        Routing(PrintStream fallback, boolean error) {
            this.fallback = fallback;
            this.error = error;
        }

        @Override
        public void write(int b) {
            write(new byte[] {(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            Capture capture = BOUND.get();
            if (capture != null) {
                capture.write(error, b, off, len);
            } else {
                fallback.write(b, off, len);
            }
        }

        @Override
        public void flush() {
            if (BOUND.get() == null) {
                fallback.flush();
            }
        }
    }
}
