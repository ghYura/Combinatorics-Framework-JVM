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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Provenance (R09): every source, XML and properties file carries the author's SPDX notice, which adapted
 * files must preserve verbatim. Apply it with {@code java tools/AddLicenseHeaders.java --apply}.
 */
class LicenseHeaderGateTest {

    private static final int WINDOW_LINES = 8;

    @Test
    void everySourceXmlAndPropertiesFileCarriesTheNotice() throws IOException {
        List<Path> files = Reactor.files(".java", ".xml", ".properties");
        assertTrue(files.size() >= 80, "the scan would be vacuous: only " + files.size() + " files");
        Path root = Reactor.root();
        List<String> missing = new ArrayList<>();
        for (Path file : files) {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            boolean found = false;
            for (int i = 0; i < Math.min(WINDOW_LINES, lines.size()); i++) {
                found |= lines.get(i).contains("SPDX-License-Identifier: LicenseRef-BUSL-1.1");
            }
            if (!found) {
                missing.add(root.relativize(file).toString());
            }
        }
        assertEquals(List.of(), missing, "files without the notice: run tools/AddLicenseHeaders.java --apply");
    }
}
