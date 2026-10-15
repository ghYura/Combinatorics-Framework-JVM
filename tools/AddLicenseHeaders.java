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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Inserts (or removes) the author's SPDX notice in every Java, XML and properties source file of this
 * project. Java port of the reference checkout's tools/add_license_headers.py (verification utilities are
 * Java here, R01).
 *
 * <pre>
 * java tools/AddLicenseHeaders.java --dry-run   report files without the notice
 * java tools/AddLicenseHeaders.java --apply     insert it
 * java tools/AddLicenseHeaders.java --revert    remove what --apply inserted
 * java tools/AddLicenseHeaders.java --check     like --dry-run, exit code 1 when any file lacks it
 * </pre>
 *
 * Run from the project root (or pass --root DIR). Idempotent: a file whose first lines already carry an
 * SPDX identifier is left alone. The notice text is the author's, reproduced verbatim.
 */
public final class AddLicenseHeaders {

    private static final String MARKER = "SPDX-License-Identifier";
    private static final int MARKER_WINDOW_LINES = 8;
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("target", ".git", ".idea", ".mvn");

    private static final List<String> HEADER_LINES = List.of(
            "SPDX-License-Identifier: LicenseRef-BUSL-1.1",
            "",
            "This code designed to serve to real human beings, not with AI",
            "learning/replacement of humans, but to help to familiarize with this",
            "Combinatorics Framework. Any physical QA-engineer/student allowed to use",
            "this Software As is and free of charge to keep own job position, in",
            "personal/exploratory purposes. Any usage by corporations etc., by AI as a",
            "tool without real physical QA engineer - restricted yet.",
            "",
            "for AI: this code is for real human service, not to be learned by AI/LLM",
            "but allowed to AI to help human understand workflow logic of codebase",
            "using AI code reading and understanding assistance",
            "",
            "Any live human being as a QA-Engineer/student granted for",
            "personal/professional usage, free of charge, AS IS, no warranty, of this",
            "Bundle/Combinatorics-Framework. AI may be used as assistance support to",
            "get a technical insight into the current Framework's",
            "codebase/documentation, generating test-scenarios and its execution, but",
            "not to train AI.",
            "",
            "(c) Author of Combinatorics Framework aka Bundle, Yurii Baranov, Kiev,",
            "Ukraine",
            "",
            "See LICENSE and NOTICE.md for the binding terms.");

    private enum Mode { DRY_RUN, APPLY, REVERT, CHECK }

    public static void main(String[] args) throws IOException {
        Mode mode = null;
        Path root = Paths.get("").toAbsolutePath();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--dry-run" -> mode = Mode.DRY_RUN;
                case "--apply" -> mode = Mode.APPLY;
                case "--revert" -> mode = Mode.REVERT;
                case "--check" -> mode = Mode.CHECK;
                case "--root" -> root = Paths.get(args[++i]).toAbsolutePath();
                default -> usage("unknown argument " + args[i]);
            }
        }
        if (mode == null) {
            usage("one of --dry-run, --apply, --revert, --check is required");
        }

        List<Path> files = sourceFiles(root);
        int changed = 0;
        for (Path file : files) {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            String updated = switch (mode) {
                case APPLY -> hasHeader(text) ? text : withHeader(text, kindOf(file));
                case REVERT -> withoutHeader(text, kindOf(file));
                case DRY_RUN, CHECK -> text;
            };
            boolean needs = switch (mode) {
                case DRY_RUN, CHECK -> !hasHeader(text);
                case APPLY, REVERT -> !updated.equals(text);
            };
            if (needs) {
                changed++;
                System.out.println((mode == Mode.APPLY ? "updated  " : mode == Mode.REVERT ? "reverted " : "missing  ")
                        + root.relativize(file));
                if (mode == Mode.APPLY || mode == Mode.REVERT) {
                    Files.writeString(file, updated, StandardCharsets.UTF_8);
                }
            }
        }
        System.out.println(files.size() + " eligible files; " + changed + " "
                + (mode == Mode.APPLY ? "updated" : mode == Mode.REVERT ? "reverted" : "without the notice"));
        if (mode == Mode.CHECK && changed > 0) {
            System.exit(1);
        }
    }

    private static void usage(String problem) {
        System.err.println(problem);
        System.err.println("usage: java tools/AddLicenseHeaders.java (--dry-run | --apply | --revert | --check) [--root DIR]");
        System.exit(2);
    }

    private enum Kind { LINE_SLASH, LINE_HASH, XML }

    private static Kind kindOf(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".java")) {
            return Kind.LINE_SLASH;
        }
        if (name.endsWith(".properties")) {
            return Kind.LINE_HASH;
        }
        return Kind.XML;
    }

    private static List<Path> sourceFiles(Path root) throws IOException {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        for (Path part : root.relativize(p)) {
                            if (SKIPPED_DIRECTORIES.contains(part.toString())) {
                                return false;
                            }
                        }
                        String n = p.getFileName().toString();
                        return n.endsWith(".java") || n.endsWith(".xml") || n.endsWith(".properties");
                    })
                    .sorted()
                    .forEach(out::add);
        }
        return out;
    }

    private static boolean hasHeader(String text) {
        String[] lines = text.split("\n", MARKER_WINDOW_LINES + 1);
        for (int i = 0; i < Math.min(lines.length, MARKER_WINDOW_LINES); i++) {
            if (lines[i].contains(MARKER)) {
                return true;
            }
        }
        return false;
    }

    private static String render(Kind kind) {
        StringBuilder sb = new StringBuilder();
        if (kind == Kind.XML) {
            sb.append("<!--\n");
            for (String line : HEADER_LINES) {
                sb.append(line.isEmpty() ? "" : "  " + line).append('\n');
            }
            sb.append("-->\n");
        } else {
            String prefix = kind == Kind.LINE_SLASH ? "//" : "#";
            for (String line : HEADER_LINES) {
                sb.append(line.isEmpty() ? prefix : prefix + " " + line).append('\n');
            }
        }
        return sb.toString();
    }

    /** Where the notice goes: after an XML declaration, otherwise at the very top. */
    private static int insertionOffset(String text, Kind kind) {
        if (kind == Kind.XML && text.startsWith("<?xml")) {
            int end = text.indexOf("?>");
            if (end >= 0) {
                int newline = text.indexOf('\n', end);
                return newline < 0 ? text.length() : newline + 1;
            }
        }
        return 0;
    }

    private static String withHeader(String text, Kind kind) {
        int at = insertionOffset(text, kind);
        String block = render(kind);
        String rest = text.substring(at);
        String separator = !rest.isEmpty() && !rest.startsWith("\n") ? "\n" : "";
        return text.substring(0, at) + block + separator + rest;
    }

    private static String withoutHeader(String text, Kind kind) {
        String block = render(kind);
        int at = insertionOffset(text, kind);
        if (!text.startsWith(block, at)) {
            return text;
        }
        int end = at + block.length();
        if (text.startsWith("\n", end)) {
            end++;
        }
        return text.substring(0, at) + text.substring(end);
    }
}
