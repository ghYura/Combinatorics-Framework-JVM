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

package org.ghyura.cf.acceptance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.opentest4j.TestAbortedException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(5)
class CompanionRepositoriesTest {
    private static final CompanionRepositories.Kind QA = CompanionRepositories.Kind.QA;
    private static final CompanionRepositories.Kind SUT = CompanionRepositories.Kind.SUT;

    private static Path reactor(Path work) throws IOException {
        Path root = Files.createDirectories(work.resolve("workspaces/new/JVM"));
        Files.writeString(root.resolve("pom.xml"), "<project/>");
        return root;
    }

    private static Path qa(Path parent) throws IOException {
        Path root = parent.resolve(QA.folder);
        Files.createDirectories(root.resolve("Core_trunk"));
        Files.createDirectories(root.resolve("generator_trunk"));
        return root;
    }

    private static PrintStream quiet() {
        return new PrintStream(new ByteArrayOutputStream());
    }

    @Test void immediateSiblingWinsOverAnotherWorkspace(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        Path sibling = qa(root.getParent());
        qa(work.resolve("workspaces/old"));
        assertEquals(sibling, CompanionRepositories.discover(root, QA, null).root());
    }

    @Test void findsQaAndSutInAnotherNearbyWorkspace(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        Path qa = qa(work.resolve("workspaces/old"));
        Path sut = work.resolve("workspaces/old/SUT");
        Files.createDirectories(sut.resolve("combination_thinking_tutor"));
        assertEquals(qa, CompanionRepositories.discover(root, QA, null).root());
        assertEquals(sut, CompanionRepositories.discover(root, SUT, null).root());
    }

    @Test void explicitDirectoryOrFileOverridesDiscoveryAndBadHintsDoNotFallBack(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        qa(root.getParent());
        Path chosen = Files.createDirectories(work.resolve("custom checkout/Core_trunk")).getParent();
        Path file = Files.writeString(chosen.resolve("Core_trunk/fw.properties"), "test=true");
        assertEquals(chosen, CompanionRepositories.discover(root, QA, chosen.toString()).root());
        assertEquals(chosen, CompanionRepositories.discover(root, QA, "\"" + file + "\"").root());
        assertEquals(chosen, CompanionRepositories.discover(root, QA, root.relativize(file).toString()).root());
        var missing = CompanionRepositories.discover(root, QA, work.resolve("absent").toString());
        assertNull(missing.root());
        assertTrue(missing.detail().contains("cf.qa.root"));
    }

    @Test void ambiguousCheckoutsNeedAChoiceAndPromptAcceptsAFile(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        qa(work.resolve("workspaces/one"));
        Path chosen = qa(work.resolve("workspaces/two"));
        Path file = Files.writeString(chosen.resolve("Core_trunk/fw.properties"), "test=true");
        var ambiguous = CompanionRepositories.discover(root, QA, null);
        assertNull(ambiguous.root());
        assertTrue(ambiguous.detail().contains("Several"));
        assertEquals(chosen, CompanionRepositories.locate(root, QA, null, new StringReader(file + "\n"), quiet(), 100).root());
    }

    @Test void noninteractiveBuildSkipsWithSetupInstructions(@TempDir Path work) throws Exception {
        var result = CompanionRepositories.locate(reactor(work), QA, null, null, quiet(), 15000);
        assertNull(result.root());
        assertTrue(result.detail().startsWith("MISSING_AUTHORIZED_BACKEND:"));
        assertTrue(result.detail().contains("Clone or unpack"));
        assertTrue(result.detail().contains("-Dcf.qa.root"));
        assertTrue(result.detail().contains("No interactive wait"));
    }

    @Test void blankInvalidAndUnavailableInputSkipGracefully(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        assertTrue(CompanionRepositories.locate(root, QA, null, new StringReader("\n"), quiet(), 100).detail().contains("No path supplied"));
        assertTrue(CompanionRepositories.locate(root, QA, null, new StringReader("/absent/path\n"), quiet(), 100).detail().contains("entered path"));
        var unavailable = new StringReader("") {
            @Override public boolean ready() throws IOException { throw new IOException("closed input"); }
        };
        assertTrue(CompanionRepositories.locate(root, QA, null, unavailable, quiet(), 100).detail().contains("Input unavailable"));
    }

    @Test void timeoutDoesNotReadUnreadyInputOrCloseIt(@TempDir Path work) throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        var input = new StringReader("") {
            @Override public boolean ready() { return false; }
            @Override public int read() { fail("must not block waiting for input"); return -1; }
            @Override public void close() { closed.set(true); super.close(); }
        };
        var result = CompanionRepositories.locate(reactor(work), QA, null, input, quiet(), 30);
        assertNull(result.root());
        assertTrue(result.detail().contains("Input timed out"));
        assertFalse(closed.get());
    }

    @Test void interruptionIsPreserved(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        Thread.currentThread().interrupt();
        try {
            var result = CompanionRepositories.locate(root, QA, null, new StringReader("\n"), quiet(), 100);
            assertNull(result.root());
            assertTrue(result.detail().contains("Input interrupted"));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test void searchDoesNotRecurseThroughArbitrarySubtrees(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        qa(work.resolve("workspaces/old/deep/inside"));
        assertNull(CompanionRepositories.discover(root, QA, null).root());
    }

    @Test void missingRepositoryAndMissingRequiredFileAbortOnlyDependentChecks(@TempDir Path work) throws Exception {
        Path root = reactor(work);
        String priorRoot = System.getProperty("cf.reactor.root");
        String priorQa = System.getProperty("cf.qa.root");
        String priorTimeout = System.getProperty("cf.companions.prompt.seconds");
        try {
            System.setProperty("cf.reactor.root", root.toString());
            System.setProperty("cf.qa.root", work.resolve("missing").toString());
            System.setProperty("cf.companions.prompt.seconds", "0");
            assertTrue(assertThrows(TestAbortedException.class,
                    () -> Reactor.companion(QA, "Core_trunk")).getMessage().contains("MISSING_AUTHORIZED_BACKEND"));
            Path chosen = qa(work.resolve("selected"));
            System.setProperty("cf.qa.root", chosen.toString());
            assertEquals(chosen, Reactor.companion(QA, "Core_trunk"));
            assertTrue(assertThrows(TestAbortedException.class,
                    () -> Reactor.companion(QA, "missing.txt")).getMessage().contains("lacks readable missing.txt"));
        } finally {
            restore("cf.reactor.root", priorRoot);
            restore("cf.qa.root", priorQa);
            restore("cf.companions.prompt.seconds", priorTimeout);
        }
    }

    private static void restore(String key, String value) {
        if (value == null) System.clearProperty(key); else System.setProperty(key, value);
    }
}
