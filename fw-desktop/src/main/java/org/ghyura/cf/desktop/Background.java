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

package org.ghyura.cf.desktop;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.FrameworkException;

import javax.swing.SwingUtilities;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Work off the event dispatch thread, its result or failure handed back to the event dispatch thread. */
final class Background {

    private Background() {
    }

    /** Runs the work on a new virtual thread; the future completes after the event-thread callback ran. */
    static <T> CompletableFuture<T> run(String name, Supplier<T> work, Consumer<T> done, Consumer<Throwable> failed) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Thread.ofVirtual().name("cf-desktop-" + name).start(() -> {
            try {
                T value = work.get();
                SwingUtilities.invokeLater(() -> {
                    try {
                        done.accept(value);
                        future.complete(value);
                    } catch (Throwable e) {
                        future.completeExceptionally(e);
                    }
                });
            } catch (Throwable e) {
                SwingUtilities.invokeLater(() -> {
                    try {
                        failed.accept(e);
                    } catch (Throwable callbackFailure) {
                        if (callbackFailure != e) e.addSuppressed(callbackFailure);
                    } finally {
                        future.completeExceptionally(e);
                    }
                });
            }
        });
        return future;
    }

    /** A failure as the user reads it: every diagnostic of a framework failure, else the exception. */
    static String describe(Throwable failure) {
        if (failure instanceof FrameworkException e) {
            StringBuilder text = new StringBuilder();
            for (Diagnostic d : e.diagnostics()) {
                text.append(d).append('\n');
            }
            return text.toString();
        }
        return String.valueOf(failure);
    }
}
