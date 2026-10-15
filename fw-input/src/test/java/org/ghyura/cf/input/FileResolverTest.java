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

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;

import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileResolverTest {

    private static final SourceLocation CELL = SourceLocation.ofPath("scenario.json", "Data!A1");

    private static Path write(Path dir, String name, byte[] content) throws IOException {
        Path file = dir.resolve(name);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return file;
    }

    private static FileResolver resolver(Path dir) {
        return FileResolver.inDirectory(dir, 1024);
    }

    @Test
    void everyLineIsEndedByOneLineFeedWhateverEndedItBefore() throws IOException {
        assertEquals("a\nb\nc\nd\n", FileResolver.withLineFeeds("a\r\nb\rc\nd"));
        assertEquals("x\n", FileResolver.withLineFeeds("x"));
        assertEquals("x\n", FileResolver.withLineFeeds("x\n"));
        assertEquals("x\n", FileResolver.withLineFeeds("x\r\n"));
        assertEquals("\n", FileResolver.withLineFeeds("\n"));
        assertEquals("\n\n", FileResolver.withLineFeeds("\r\r"));
        assertEquals("\n\n", FileResolver.withLineFeeds("\n\n"));
        assertEquals("", FileResolver.withLineFeeds(""));
    }

    @Test
    void onlyLineFeedCarriageReturnAndTheirPairAreLineEnds() {
        // the legacy reader used BufferedReader semantics, so U+2028 and U+0085 stay inside their line
        assertEquals("a b\u0085c\n", FileResolver.withLineFeeds("a b\u0085c"));
    }

    @Test
    void aFileIsReadAsStrictUtf8WithoutItsByteOrderMark(@TempDir Path dir) throws IOException {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] text = "café б\r\nline 2".getBytes(StandardCharsets.UTF_8);
        byte[] both = new byte[bom.length + text.length];
        System.arraycopy(bom, 0, both, 0, bom.length);
        System.arraycopy(text, 0, both, bom.length, text.length);
        write(dir, "code.txt", both);
        assertEquals("café б\nline 2\n", resolver(dir).readText("code.txt", CELL));
    }

    @Test
    void anEmptyFileGivesAnEmptyValue(@TempDir Path dir) throws IOException {
        write(dir, "empty.txt", new byte[0]);
        assertEquals("", resolver(dir).readText("empty.txt", CELL));
    }

    @Test
    void relativePathsStartInTheGivenDirectoryAndAbsolutePathsAreUsedAsTheyAre(@TempDir Path dir, @TempDir Path elsewhere)
            throws IOException {
        write(dir, "sub/x.txt", "inside".getBytes(StandardCharsets.UTF_8));
        Path other = write(elsewhere, "y.txt", "outside".getBytes(StandardCharsets.UTF_8));
        assertEquals("inside\n", resolver(dir).readText("sub/x.txt", CELL));
        assertEquals("outside\n", resolver(dir).readText(other.toString(), CELL));
    }

    @Test
    void aMissingFileOrADirectoryIsUnreadableAndTheDiagnosticSitsOnTheCell(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("folder"));
        FrameworkException missing = expectCode(ErrorCode.INPUT_UNREADABLE, () -> resolver(dir).readText("nope.txt", CELL));
        assertEquals(CELL, missing.primary().location());
        assertTrue(missing.primary().message().contains("FW_File=nope.txt"), missing.primary().message());
        assertEquals(dir.resolve("nope.txt").toString(), missing.primary().attributes().get("file"));
        expectCode(ErrorCode.INPUT_UNREADABLE, () -> resolver(dir).readText("folder", CELL));
    }

    @Test
    void invalidUtf8AndOversizedFilesAreRefusedAtTheCell(@TempDir Path dir) throws IOException {
        write(dir, "bad.txt", new byte[]{'o', 'k', (byte) 0xFF, (byte) 0xFE});
        FrameworkException bad = expectCode(ErrorCode.INPUT_MALFORMED, () -> resolver(dir).readText("bad.txt", CELL));
        assertEquals(CELL, bad.primary().location());
        write(dir, "big.txt", new byte[5]);
        FrameworkException big = expectCode(ErrorCode.INPUT_LIMIT, () -> FileResolver.inDirectory(dir, 4).readText("big.txt", CELL));
        assertEquals(CELL, big.primary().location());
        assertEquals("5", big.primary().attributes().get("bytes"));
        assertEquals("4", big.primary().attributes().get("limit"));
        assertEquals(6, FileResolver.inDirectory(dir, 5).readText("big.txt", CELL).length(), "five NUL characters plus a line feed");
    }

    @Test
    void readingLeavesTheFileUntouched(@TempDir Path dir) throws IOException {
        byte[] content = "stable\n".getBytes(StandardCharsets.UTF_8);
        Path file = write(dir, "s.txt", content);
        FileTime stamp = FileTime.fromMillis(1_100_000_000_000L);
        Files.setLastModifiedTime(file, stamp);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));
        assertEquals("stable\n", resolver(dir).readText("s.txt", CELL));
        assertArrayEquals(content, Files.readAllBytes(file));
        assertEquals(stamp, Files.getLastModifiedTime(file));
        try (var listing = Files.list(dir)) {
            assertEquals(1, listing.count());
        }
    }

    @Test
    void aScenarioWithoutADirectoryRefusesEveryFileCell() {
        FrameworkException e = expectCode(ErrorCode.INPUT_UNREADABLE, () -> FileResolver.none().readText("a.txt", CELL));
        assertEquals(CELL, e.primary().location());
        assertTrue(e.primary().message().contains("FW_File=a.txt"), e.primary().message());
    }
}
