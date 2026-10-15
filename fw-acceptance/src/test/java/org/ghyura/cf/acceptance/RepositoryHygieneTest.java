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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Repository hygiene (GEN:diagnostics-reporting-release; the legacy {@code bundle hygiene}, D66) as a build gate rather than
 * a command: no file of the reactor looks like a backup or an obsolete copy by the legacy rule (bundle/hygiene.py is_backup:
 * a backup extension, or a backup marker in the name of anything but a Python file), so none can reach a compile source set.
 * Build output, version control and anything under an ai-assist path are never scanned, as the legacy tool never scanned
 * them.
 */
class RepositoryHygieneTest {

    private static final Set<String> BACKUP_EXTENSIONS = Set.of(".bak", ".djava", ".orig", ".old", ".rej", ".save", ".swp", ".swo");
    private static final Pattern BACKUP = Pattern.compile("(~$|\\.bak\\b|\\.may\\d+\\.bak$|bkup|bckup|backup)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SKIPPED = Set.of("target", ".git", "__pycache__", "node_modules", ".m2");

    /** Python's {@code Path.suffix}: from the last dot of the name, none for a name that only starts with one. */
    private static String suffix(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 ? name.substring(dot) : "";
    }

    static boolean isBackup(String name) {
        String suffix = suffix(name).toLowerCase(Locale.ROOT);
        return BACKUP_EXTENSIONS.contains(suffix) || (BACKUP.matcher(name).find() && !suffix.equals(".py"));
    }

    @Test
    void theRuleIsTheLegacyHygieneRule() {
        for (String name : List.of("Foo.java.bak", "Reader.djava", "x~", "Core.may12.bak", "notes.save", "a.swp", "file.old", "bkup.txt",
                "MyBackupService.java", "diff.rej")) {
            assertTrue(isBackup(name), name);
        }
        for (String name : List.of("backup_tool.py", ".gitignore", "Foo.java", "Old.java", "bake.java", "pom.xml")) {
            assertFalse(isBackup(name), name);
        }
    }

    @Test
    void noFileOfTheReactorLooksLikeABackupOrAnObsoleteCopy() throws IOException {
        Path root = Reactor.root();
        try (Stream<Path> files = Files.walk(root)) {
            List<String> backups = files.filter(Files::isRegularFile)
                    .filter(p -> root.relativize(p).toString().toLowerCase(Locale.ROOT).indexOf("ai-assist") < 0)
                    .filter(p -> {
                        for (Path part : root.relativize(p)) {
                            if (SKIPPED.contains(part.toString())) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .filter(p -> isBackup(p.getFileName().toString()))
                    .map(p -> root.relativize(p).toString()).sorted().toList();
            assertEquals(List.of(), backups, "archive these outside the reactor; nothing here may be a stale copy of a source");
        }
    }
}
