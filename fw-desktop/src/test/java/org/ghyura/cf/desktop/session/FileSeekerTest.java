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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.desktop.session.FileSeeker.Found;
import org.ghyura.cf.desktop.session.FileSeeker.Kind;
import org.ghyura.cf.desktop.session.FileSeeker.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Auto-search finds each kind's files by their content where they are likely, and never walks what holds none. */
class FileSeekerTest {

    @Test
    void eachKindFindsItsOwnFilesByTheirContentNotByTheirNameAlone(@TempDir Path dir) throws Exception {
        Path workbook = xlsx(dir.resolve("shop/shop.xlsx"), "FW_Seq");
        xlsx(dir.resolve("docs/budget.xlsx"), "Sheet1");
        Path spec = write(dir.resolve("specs/run/scenario.toml"), "# a header longer than nothing\nspec_version = \"1\"\n\n[[slots]]\nsheet = \"A\"\n");
        write(dir.resolve("specs/pyproject.toml"), "[project]\nname = \"not-a-scenario\"\n");
        Path json = write(dir.resolve("shop/shop.json"), "{\"sheets\": {\"FW_Seq\": [], \"A\": [[\"a\"]]}}");
        Path rules = write(dir.resolve("shop/shop.constraints.json"), "{\"version\": 1, \"constraints\": [{\"sheets\": [\"A\", \"B\"], \"pairs\": []}]}");
        Path sidecar = write(dir.resolve("specs/pipe.sidecar.json"), "{\"version\": 1, \"constraints\": [{\"polarity\": \"forbid\", \"sheets\": [\"A\"]}]}");
        write(dir.resolve("tutor/lesson.json"), "{\"title\": \"x\", \"constraints\": [{\"if\": \"a\", \"then\": \"b\"}]}");
        Path kv = write(dir.resolve("runs/run-metrics.kv"), "candidate_id=a latency=1 FW_VAR=0\n");
        Path sutLog = write(dir.resolve("runs/sut.log"), "starting\ncandidate_id=b latency=2 FW_VAR=0\n" + "x".repeat(300) + "\n");
        Path buildLog = write(dir.resolve("runs/build.log"), "BUILD SUCCESS\n" + "y".repeat(300) + "\n");
        write(dir.resolve("runs/requirements.txt"), "z".repeat(400));
        write(dir.resolve("runs/tiny.log"), "one line\n");
        Path jacoco = write(dir.resolve("app/target/site/jacoco/jacoco.xml"), "<?xml version=\"1.0\"?><!DOCTYPE report PUBLIC "
                + "\"-//JACOCO//DTD Report 1.1//EN\" \"report.dtd\"><report name=\"app\">");
        Path coveragePy = write(dir.resolve("app/coverage.json"), "{\"meta\": {\"format\": 2, \"version\": \"7.4\", \"branch_coverage\": false, "
                + "\"show_contexts\": false}, \"files\": {}}");
        write(dir.resolve("demo/coverage.json"), "{\"universe_size\": 720, \"orders\": []}");
        Path gcov = write(dir.resolve("app/gcov-summary.out"), "File 'a.c'\nLines executed:85.00% of 20\n");
        Path scoring = write(dir.resolve("samples/config.json"), "{\"_comment\": [\"rules for the sample\"], \"required_tokens\": [\"status\"]}");
        Path addon = jar(dir.resolve("plugins/target/my-addon.jar"), "org.example.absent.MyPlugin");
        jar(dir.resolve("plugins/target/built-in.jar"), FileSeekerTest.class.getName());
        Path library = Files.createDirectories(dir.resolve("qa/generator_trunk/intake")).getParent();
        write(library.resolve("intake/verified_examples.json"), "[]");
        // what is never walked: version control, packages, build internals
        xlsx(dir.resolve(".git/objects/kept.xlsx"), "FW_Seq");
        xlsx(dir.resolve("node_modules/pkg/fixture.xlsx"), "FW_Seq");
        write(dir.resolve("app/target/classes/copy.constraints.json"), "{}");
        write(dir.resolve("app/target/classes/jacoco-copy.xml"), "<!DOCTYPE report PUBLIC \"-//JACOCO//DTD Report 1.1//EN\">");

        List<Root> here = List.of(new Root(dir, 6, 0, "the test folder"));
        assertEquals(Set.of(workbook, spec, json), paths(Kind.SCENARIO, here), "a workbook with FW_Seq, a spec with slots, sheets as JSON");
        assertEquals(Set.of(rules, sidecar), paths(Kind.CONSTRAINTS, here), "by name, or by the framework's rule keys");
        assertEquals(Set.of(kv, sutLog), paths(Kind.METRICS, here), "a .kv, or a log with measurements and FW_VAR");
        assertEquals(Set.of(sutLog, buildLog), paths(Kind.TEXT, here), "logs worth scoring; no package lists, no one-liners");
        assertEquals(Set.of(jacoco), paths(Kind.JACOCO, here), "in build output, but never in its class folders");
        assertEquals(Set.of(coveragePy), paths(Kind.COVERAGE_PY, here), "coverage.py's own report, not any coverage.json");
        assertEquals(Set.of(gcov), paths(Kind.GCOV, here));
        assertEquals(Set.of(scoring), paths(Kind.RULES, here));
        assertEquals(Set.of(addon), paths(Kind.ADDON, here), "a runner the application lacks; its own runners are not offered");
        assertEquals(Set.of(library), paths(Kind.LIBRARY, here));
        Found first = seek(Kind.ADDON, here).found().getFirst();
        assertEquals("adds the runner MyPlugin", first.note());
        assertEquals("the test folder", first.why());
    }

    @Test
    void theLikeliestRootComesFirstThenTheNearestThenTheNewestAndAFileFoundTwiceIsListedOnce(@TempDir Path dir) throws Exception {
        Path likely = Files.createDirectories(dir.resolve("likely"));
        Path older = xlsx(likely.resolve("older.xlsx"), "FW_Seq");
        Path deeper = xlsx(likely.resolve("a/b/deeper.xlsx"), "FW_Seq");
        Path newer = xlsx(dir.resolve("elsewhere/newer.xlsx"), "FW_Seq");
        Path newest = xlsx(dir.resolve("elsewhere/newest.xlsx"), "FW_Seq");
        Files.setLastModifiedTime(older, FileTime.fromMillis(1_000_000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2_000_000_000_000L));
        Files.setLastModifiedTime(newest, FileTime.fromMillis(2_100_000_000_000L));
        List<Root> roots = List.of(new Root(dir, 4, 3, "the whole folder"), new Root(likely, 3, 0, "the likely folder"));
        List<Found> found = seek(Kind.SCENARIO, roots).found();
        assertEquals(List.of(older, deeper, newest, newer), found.stream().map(Found::path).toList());
        assertEquals("the likely folder", found.getFirst().why(), "met from both roots, it keeps the likelier reason");
    }

    @Test
    void theBudgetAndAStopEndTheSearchWithWhatWasFoundAndTheWalkSkipsWhatHoldsNothing(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 20; i++) xlsx(dir.resolve("f" + i + "/s" + i + ".xlsx"), "FW_Seq");
        List<Root> roots = List.of(new Root(dir, 3, 0, "here"));
        FileSeeker.Result cut = FileSeeker.seek(Kind.SCENARIO, roots, new FileSeeker.Budget(10_000, 5, 300), () -> false, n -> { });
        assertFalse(cut.complete());
        assertEquals(5, cut.folders());
        FileSeeker.Result stopped = FileSeeker.seek(Kind.SCENARIO, roots, FileSeeker.Budget.DEFAULT, () -> true, n -> { });
        assertFalse(stopped.complete());
        assertTrue(stopped.found().isEmpty());
        FileSeeker.Result whole = seek(Kind.SCENARIO, roots);
        assertTrue(whole.complete());
        assertEquals(20, whole.found().size());
        FileSeeker.Result listed = FileSeeker.seek(Kind.SCENARIO, roots, new FileSeeker.Budget(10_000, 1_000, 10), () -> false, n -> { });
        assertEquals(10, listed.found().size(), "only the likeliest are listed");
        assertEquals(20, listed.total(), "while the count says how many there are");

        assertFalse(FileSeeker.enters(Kind.SCENARIO, ".git"));
        assertFalse(FileSeeker.enters(Kind.SCENARIO, "node_modules"));
        assertFalse(FileSeeker.enters(Kind.SCENARIO, "target"), "a scenario is not build output");
        assertTrue(FileSeeker.enters(Kind.JACOCO, "target"), "a coverage report is");
        assertFalse(FileSeeker.enters(Kind.JACOCO, "classes"));
        assertFalse(FileSeeker.enters(Kind.TEXT, ".hidden"));
        assertTrue(FileSeeker.enters(Kind.PYTHON, ".venv") && FileSeeker.enters(Kind.PYTHON, ".pyenv"));
        assertFalse(FileSeeker.enters(Kind.PYTHON, "lib"), "an installed Python's library holds no interpreter");
    }

    @Test
    void interpretersAreFoundOnThePathInBinFoldersAndInVirtualEnvironmentsEachOnce(@TempDir Path dir) throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"), "executable files need POSIX permissions");
        Path onPath = executable(dir.resolve("path/python3"));
        Path installed = executable(dir.resolve("tools/py311/bin/python3.11"));
        Path alias = Files.createSymbolicLink(dir.resolve("tools/py311/bin/python3"), installed.getFileName());
        executable(dir.resolve("tools/py311/bin/python3-config"));
        executable(dir.resolve("tools/py311/scripts/python3"));            // not in a bin folder: not an interpreter's place
        Path project = Files.createDirectories(dir.resolve("work/proj/.venv"));
        write(project.resolve("pyvenv.cfg"), "home = /usr/bin\nversion = 3.12.3\n");
        Path venvPython = executable(project.resolve("bin/python3"));
        executable(project.resolve("lib/python3.12/site-packages/bin/python3"));
        List<Root> roots = List.of(new Root(dir.resolve("path"), 0, 0, "on your PATH"), new Root(dir.resolve("tools"), 3, 1, "tools"),
                new Root(dir.resolve("work"), 3, 2, "work"));
        List<Found> found = seek(Kind.PYTHON, roots).found();
        assertEquals(3, found.size(), "python3 → python3.11 is one interpreter; site-packages is never walked: " + found);
        assertEquals(onPath, found.get(0).path());
        assertTrue(Set.of(installed, alias).contains(found.get(1).path()), "listed once, by either of its names");
        assertEquals(venvPython, found.get(2).path());
        assertEquals("on your PATH", found.get(0).note());
        assertEquals("virtual environment proj/.venv (Python 3.12.3)", found.get(2).note());
    }

    @Test
    void folderKindsOfferFoldersOfModulesAGeneratorTreeAndAFolderThatIsItselfTheAnswer(@TempDir Path dir) throws Exception {
        Path tree = Files.createDirectories(dir.resolve("tree"));
        write(tree.resolve("sut_paths.py"), "# tree marker\n");
        Path pkg = write(dir.resolve("tree/sut/pkg/model.py"), "x = 1\n").getParent();
        write(dir.resolve("tree/.venv/pyvenv.cfg"), "version = 3.12.3\n");
        write(dir.resolve("tree/.venv/lib/mod.py"), "y = 2\n");
        Path empty = Files.createDirectories(dir.resolve("scenario"));
        List<Found> modules = seek(Kind.PYTHON_MODULES, List.of(new Root(dir, 3, 1, "around"))).found();
        assertEquals(Set.of(tree, pkg), modules.stream().map(Found::path).collect(Collectors.toSet()), "no virtual environment's insides");
        assertTrue(modules.stream().anyMatch(f -> f.path().equals(tree) && f.note().equals("a generator tree (holds sut_paths.py)")));
        List<Root> roots = List.of(new Root(empty, 0, 0, "the scenario's folder", true), new Root(dir, 3, 1, "around"));
        List<Found> workdirs = seek(Kind.PYTHON_WORKDIR, roots).found();
        assertEquals(empty, workdirs.getFirst().path(), "the scenario's folder is offered itself, first");
        assertTrue(workdirs.stream().map(Found::path).toList().containsAll(List.of(tree, pkg)));
    }

    private static FileSeeker.Result seek(Kind kind, List<Root> roots) {
        return FileSeeker.seek(kind, roots, FileSeeker.Budget.DEFAULT, () -> false, n -> { });
    }

    private static Set<Path> paths(Kind kind, List<Root> roots) {
        FileSeeker.Result result = seek(kind, roots);
        assertTrue(result.complete(), kind + " walked everything");
        return result.found().stream().map(Found::path).collect(Collectors.toSet());
    }

    private static Path write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        return Files.writeString(file, text, StandardCharsets.UTF_8);
    }

    /** A workbook whose only sheet has the name. */
    private static Path xlsx(Path file, String sheet) throws Exception {
        return zip(file, "xl/workbook.xml", "<workbook><sheets><sheet name=\"" + sheet + "\" sheetId=\"1\"/></sheets></workbook>");
    }

    /** A jar that names a runner plugin in its services. */
    private static Path jar(Path file, String plugin) throws Exception {
        return zip(file, "META-INF/services/org.ghyura.cf.spi.SutPlugin", "# the runner\n" + plugin + "\n");
    }

    private static Path zip(Path file, String entry, String text) throws Exception {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(text.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return file;
    }

    private static Path executable(Path file) throws Exception {
        write(file, "#!/bin/sh\n");
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        return file;
    }
}
