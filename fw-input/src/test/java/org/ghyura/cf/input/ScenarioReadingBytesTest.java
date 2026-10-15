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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What reading a scenario document is estimated to hold, before it is parsed (A10: documents admitted to the budget). */
class ScenarioReadingBytesTest {

    @Test
    void textDocumentsCountTenBytesPerByteAndWorkbooksTwentyFivePerUncompressedByte(@TempDir Path dir) throws IOException {
        Path json = dir.resolve("scenario.json");
        Files.writeString(json, "{\"sheets\": {\"FW_Seq\": [[\"A\", \"FW_Combi(1)\"]], \"A\": [[\"a\"], [\"b\"]]}}", StandardCharsets.UTF_8);
        assertEquals(10 * Files.size(json), ScenarioLoader.readingBytes(json));
        Path toml = Files.writeString(dir.resolve("spec.toml"), "title = \"x\"\n", StandardCharsets.UTF_8);
        assertEquals(10 * Files.size(toml), ScenarioLoader.readingBytes(toml));

        Path xlsx = dir.resolve("scenario.xlsx");
        Files.write(xlsx, WorkbookFormats.writer(InputFormat.XLSX).write(WorkbookFormats.reader(InputFormat.JSON).read(InputSource.read(json)).workbook()));
        long expanded = 0;
        try (ZipFile zip = new ZipFile(xlsx.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                expanded += entries.nextElement().getSize();
            }
        }
        assertTrue(expanded > Files.size(xlsx), "a workbook's parts are compressed");
        assertEquals(25 * expanded, ScenarioLoader.readingBytes(xlsx));
        assertEquals(0, ScenarioLoader.readingBytes(dir.resolve("missing.json")), "reading a missing file reports the error itself");
    }
}
