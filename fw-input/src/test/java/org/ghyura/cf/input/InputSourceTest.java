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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.ghyura.cf.input.InputTestSupport.expectCode;
import static org.ghyura.cf.input.InputTestSupport.shape;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputSourceTest {

    @Test
    void theStreamLimitStopsGrowingInputWithoutConsumingTheRest() throws IOException {
        var consumed = new java.util.concurrent.atomic.AtomicInteger();
        var growing = new java.io.InputStream() {
            @Override public int read() { consumed.incrementAndGet(); return 'x'; }
        };
        FrameworkException refused = org.junit.jupiter.api.Assertions.assertThrows(FrameworkException.class,
                () -> InputSource.read("growing.json", growing, 16));
        assertEquals(ErrorCode.INPUT_LIMIT, refused.primary().code());
        assertEquals(17, consumed.get(), "one byte past the ceiling is enough to refuse without reading the rest");
        assertEquals(16, InputSource.read("exact.json", new java.io.ByteArrayInputStream(new byte[16]), 16).size());
        assertEquals(0, InputSource.read("empty.json", new java.io.ByteArrayInputStream(new byte[0]), 0).size());
    }

    @Test
    void aSourceHoldsACopyOfItsBytesAndANameForLocations() {
        byte[] bytes = {1, 2, 3};
        InputSource source = InputSource.of("x.json", bytes);
        bytes[0] = 99;
        assertArrayEquals(new byte[]{1, 2, 3}, source.copyOfBytes());
        assertNotSame(source.copyOfBytes(), source.copyOfBytes());
        assertEquals("x.json", source.name());
        assertEquals(3, source.size());
        assertEquals(3, InputSource.ofText("t", "hé").size(), "UTF-8: h is one octet and é is two");
    }

    @Test
    void readingAFileIsReadOnlyAndLeavesItUntouched(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("s.json");
        byte[] content = "{\"sheets\":{\"S\":[[\"a\"]]}}".getBytes(StandardCharsets.UTF_8);
        Files.write(file, content);
        FileTime stamp = FileTime.fromMillis(1_100_000_000_000L);
        Files.setLastModifiedTime(file, stamp);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("r--r--r--"));

        ReadResult result = WorkbookFormats.readFile(file);

        assertEquals(List.of("S = [0:a]"), shape(result.workbook()));
        assertArrayEquals(content, Files.readAllBytes(file));
        assertEquals(stamp, Files.getLastModifiedTime(file));
        try (var listing = Files.list(dir)) {
            assertEquals(1, listing.count());
        }
    }

    @Test
    void aMissingOrNonRegularFileIsUnreadableAndALargeOneIsOverTheLimit(@TempDir Path dir) throws IOException {
        FrameworkException missing = expectCode(ErrorCode.INPUT_UNREADABLE, () -> InputSource.read(dir.resolve("absent.json")));
        assertEquals(dir.resolve("absent.json").toString(), missing.primary().location().origin());
        expectCode(ErrorCode.INPUT_UNREADABLE, () -> InputSource.read(dir));

        Path big = dir.resolve("big.json");
        Files.write(big, new byte[100]);
        FrameworkException tooBig = expectCode(ErrorCode.INPUT_LIMIT, () -> InputSource.read(big, 99));
        assertEquals("100", tooBig.primary().attributes().get("bytes"));
        assertEquals("99", tooBig.primary().attributes().get("limit"));
        assertEquals(100, InputSource.read(big, 100).size());
    }

    @Test
    void anUnrecognizedExtensionIsReportedBeforeAnythingIsRead(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("scenario.txt");
        Files.writeString(file, "{}");
        FrameworkException e = expectCode(ErrorCode.INPUT_UNRECOGNIZED_FORMAT, () -> WorkbookFormats.readFile(file));
        assertTrue(e.primary().message().contains("scenario.txt"));
    }

    @Test
    void anInvalidUtf8TextFileNamesTheFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("bad.yaml");
        Files.write(file, new byte[]{'s', 'h', (byte) 0xFF, (byte) 0xFE});
        FrameworkException e = expectCode(ErrorCode.INPUT_MALFORMED, () -> WorkbookFormats.readFile(file));
        assertEquals(file.toString(), e.primary().location().origin());
    }
}
