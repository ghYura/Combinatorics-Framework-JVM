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

import org.ghyura.cf.spi.OutputCapture;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * One attempt's output: standard output and error, at most {@code limit} bytes together (UTF-8); what does not fit is
 * dropped and {@link #truncated()} says so. Thread-safe: a sample may write from several threads.
 */
final class Capture implements OutputCapture {

    private final int limit;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final ByteArrayOutputStream err = new ByteArrayOutputStream();
    private int used;
    private boolean truncated;

    Capture(int limit) {
        this.limit = limit;
    }

    synchronized void write(boolean error, byte[] bytes, int offset, int length) {
        int room = limit - used;
        int take = Math.min(room, length);
        if (take > 0) {
            (error ? err : out).write(bytes, offset, take);
            used += take;
        }
        if (take < length) {
            truncated = true;
        }
    }

    @Override
    public void stdout(String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        write(false, b, 0, b.length);
    }

    @Override
    public void stderr(String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        write(true, b, 0, b.length);
    }

    @Override
    public synchronized boolean truncated() {
        return truncated;
    }

    synchronized String out() {
        return out.toString(StandardCharsets.UTF_8);
    }

    synchronized String err() {
        return err.toString(StandardCharsets.UTF_8);
    }
}
