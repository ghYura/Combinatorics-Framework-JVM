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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Auto-search: the files (or folders) of one kind in the folders where they are most likely. Each folder to search, a
 * root, is walked to its own depth, the likeliest roots first, without following links and past what never holds such
 * files (version control, caches, installed packages, build internals), within a time and folder budget, and stoppable.
 * A candidate is recognized by its name and then by its first bytes: a workbook with an FW_Seq sheet, a spec with slots,
 * metrics lines with FW_VAR, a report a coverage tool wrote, a jar that adds a runner the application does not have, an
 * interpreter of a virtual environment, ... What is found is ranked by the likelihood of its root, then by its distance
 * below it, then newest first. Nothing is run, and nothing is read beyond those first bytes.
 */
public final class FileSeeker {

    /** What a search looks for. */
    public enum Kind {
        SCENARIO("scenario files"), LIBRARY("libraries of examples"), CONSTRAINTS("constraints files"), METRICS("metrics files"),
        JACOCO("JaCoCo coverage reports"), COVERAGE_PY("coverage.py JSON reports"), GCOV("gcov coverage summaries"),
        TEXT("text files to score"), RULES("scoring rules files"), ADDON("runner add-ons"), PYTHON("Python interpreters"),
        PYTHON_MODULES("folders of Python modules"), PYTHON_WORKDIR("folders to start Python test cases in");

        private final String what;

        Kind(String what) {
            this.what = what;
        }

        /** What is looked for, in plain words (plural). */
        public String what() {
            return what;
        }

        /** Whether the kind finds folders rather than files. */
        public boolean folders() {
            return this == LIBRARY || this == PYTHON_MODULES || this == PYTHON_WORKDIR;
        }

        /** Whether build output folders (target, build, ...) may hold it: reports, add-on jars and logs are written there. */
        boolean inBuildOutput() {
            return this == JACOCO || this == COVERAGE_PY || this == GCOV || this == ADDON || this == TEXT;
        }
    }

    /**
     * A folder to search.
     *
     * @param depth  how many levels below it are walked (0: only its own entries)
     * @param rank   how likely what is found there is, 0 the likeliest
     * @param why    why it is searched, in words ("the step 3 scenario's folder")
     * @param itself whether the folder itself is offered (for the kinds that find folders)
     */
    public record Root(Path folder, int depth, int rank, String why, boolean itself) {
        public Root(Path folder, int depth, int rank, String why) {
            this(folder, depth, rank, why, false);
        }
    }

    /** A file or folder found: why it is there (its root's reason), what it is, when written, and how likely. */
    public record Found(Path path, String why, String note, long modified, int rank, int distance) {
    }

    /**
     * What a search found, likeliest first (at most the budget's results of them); how many it found in all; the folders it
     * walked; whether it walked them all; how long it took; where it looked.
     */
    public record Result(Kind kind, List<Found> found, int total, int folders, boolean complete, long millis, List<Root> roots) {
    }

    /** The most a search may take: time, folders walked, and things listed. */
    public record Budget(long millis, int folders, int results) {
        public static final Budget DEFAULT = new Budget(4_000, 40_000, 300);
    }

    /** Folders that never hold what any kind looks for, or are too big to walk: version control, caches, packages, build internals. */
    private static final Set<String> NEVER = Set.of(".git", ".hg", ".svn", "node_modules", "__pycache__", ".idea", ".vscode", ".gradle",
            ".m2", ".cache", ".npm", ".cargo", ".rustup", "site-packages", "dist-packages", ".tox", ".mypy_cache", ".pytest_cache",
            "classes", "test-classes", "generated-sources", "generated-test-sources", "maven-status", "maven-archiver", "Trash",
            "snap", "proc", "sys", "dev");
    /** Build output: walked only by the kinds whose files are written there. */
    private static final Set<String> BUILD = Set.of("target", "build", "out", "dist");
    /** Hidden folders the Python search walks: where virtual environments and installed interpreters usually are. */
    private static final Set<String> PYTHON_HIDDEN = Set.of(".venv", ".venvs", ".virtualenvs", ".pyenv", ".local", ".conda", ".env");
    /** Text files that hold no lines worth scoring: package lists, licences and the like. */
    private static final Pattern DULL_TEXT = Pattern.compile("(requirements.*|license.*|licence.*|notice.*|readme.*|copying.*|"
            + "authors.*|cmakelists.*|third_party.*|robots|.*\\.lock)\\.txt");
    private static final Pattern PYTHON_NAME = Pattern.compile("python(3(\\.\\d+)?t?)?");
    private static final Pattern SPEC = Pattern.compile("(?m)^\\s*(\\[\\[slots]]|\"?slots\"?\\s*[=:])");
    private static final Pattern JSON_WORKBOOK = Pattern.compile("\"sheets\"\\s*:\\s*\\{|\"slots\"\\s*:\\s*\\[");
    private static final Pattern JSON_CONSTRAINTS = Pattern.compile("\"constraints\"\\s*:\\s*\\[");
    private static final Pattern JSON_RULE = Pattern.compile("\"(polarity|pairs|sets)\"\\s*:");
    private static final Pattern METRIC_LINE = Pattern.compile("(?m)^.*\\b[A-Za-z_][\\w.\\-]*=\\S+.*\\bFW_VAR\\s*[=:]\\s*-?\\d+");
    private static final Pattern GCOV_SUMMARY = Pattern.compile("(?i)\\blines\\s+executed\\s*:?\\s*\\d+(\\.\\d+)?\\s*%");
    private static final String SUT_PLUGIN = "META-INF/services/org.ghyura.cf.spi.SutPlugin";
    private static final String PYENV_SHIM = "pyenv's chosen Python";
    /** Folders inside an installed Python that hold its library, not its interpreter. */
    private static final Set<String> PYTHON_INTERNALS = Set.of("lib", "lib64", "include", "share", "man");
    /** The most entries of one folder looked at, so one huge folder cannot take the whole budget. */
    private static final int MOST_ENTRIES = 5_000;
    /** The most candidates kept before ranking. */
    private static final int MOST_CANDIDATES = 5_000;

    /** A folder still to walk, and how far below its root it is. */
    private record Frame(Path folder, int distance) {
    }

    private FileSeeker() {
    }

    /**
     * Walks the roots for the kind, within the budget, until stopped.
     *
     * @param stop     asked between folders; true ends the search with what was found so far
     * @param progress told the number of folders walked, now and then
     */
    public static Result seek(Kind kind, List<Root> roots, Budget budget, BooleanSupplier stop, IntConsumer progress) {
        long start = System.nanoTime();
        long deadline = start + budget.millis() * 1_000_000L;
        Map<Path, Integer> walked = new HashMap<>();          // folder → the most levels below it walked so far
        Map<Path, Found> found = new LinkedHashMap<>();
        int folders = 0;
        boolean complete = true;
        List<Root> ordered = roots.stream().filter(r -> r.folder() != null).sorted(Comparator.comparingInt(Root::rank)).toList();
        search:
        for (Root root : ordered) {
            Path top = root.folder().toAbsolutePath().normalize();
            if (!Files.isDirectory(top)) continue;
            if (root.itself()) {
                String note = kind.folders() ? folderNote(kind, top) : null;
                offer(found, new Found(top, root.why(), note == null ? "" : note, modified(top), root.rank(), 0));
            }
            Deque<Frame> stack = new ArrayDeque<>();
            stack.push(new Frame(top, 0));
            while (!stack.isEmpty()) {
                if (stop.getAsBoolean() || System.nanoTime() > deadline || folders >= budget.folders() || found.size() >= MOST_CANDIDATES) {
                    complete = false;
                    break search;
                }
                Frame frame = stack.pop();
                Path folder = frame.folder();
                int distance = frame.distance();
                int left = root.depth() - distance;
                Integer before = walked.get(folder);
                if (before != null && before >= left) continue;
                walked.put(folder, left);
                if (++folders % 256 == 0) progress.accept(folders);
                if (kind.folders()) {
                    String note = folderNote(kind, folder);
                    if (note != null) {
                        offer(found, new Found(folder, root.why(), note, modified(folder), root.rank(), distance));
                        if (kind == Kind.LIBRARY) continue;     // a library's own folders hold no other library
                    }
                }
                if (Files.isRegularFile(folder.resolve("pyvenv.cfg"))) {
                    // a virtual environment: its interpreter, and nothing else inside it is looked at
                    if (kind == Kind.PYTHON) venv(found, folder, root, distance);
                    continue;
                }
                List<Path> below = new ArrayList<>();
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
                    int seen = 0;
                    for (Path entry : entries) {
                        if (++seen > MOST_ENTRIES) break;
                        BasicFileAttributes a;
                        try {
                            a = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                        } catch (IOException | RuntimeException e) {
                            continue;
                        }
                        String name = entry.getFileName().toString();
                        if (a.isDirectory()) {
                            if (left > 0 && enters(kind, name)) below.add(entry);
                        } else if (!kind.folders()) {
                            String note = fileNote(kind, entry, name, a, root.depth() == 0);
                            // logs and program output before plain texts of the same place
                            int after = kind == Kind.TEXT && name.toLowerCase(Locale.ROOT).endsWith(".txt") ? 1 : 0;
                            if (note != null) {
                                offer(found, new Found(entry, root.why(), note, a.lastModifiedTime().toMillis(), root.rank(), distance + after));
                            }
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    continue;   // unreadable folders are passed over
                }
                below.sort(Comparator.comparing(Path::toString).reversed());
                for (Path next : below) stack.push(new Frame(next, distance + 1));
            }
        }
        List<Found> ranked = new ArrayList<>(found.values());
        ranked.sort(Comparator.comparingInt(Found::rank).thenComparingInt(Found::distance)
                .thenComparing(Comparator.comparingLong(Found::modified).reversed()).thenComparing(f -> f.path().toString()));
        if (kind == Kind.PYTHON) {
            // one interpreter under several names (python3 → python3.12, /bin → /usr/bin) is listed once, by its likeliest name;
            // a virtual environment stays itself (its packages differ from its base's), and pyenv's shims are one choice
            Set<Object> seen = new java.util.HashSet<>();
            ranked.removeIf(f -> !seen.add(f.note().startsWith("virtual environment") ? f.path()
                    : f.note().equals(PYENV_SHIM) ? PYENV_SHIM : realPath(f.path())));
        }
        int total = ranked.size();
        if (ranked.size() > budget.results()) ranked = ranked.subList(0, budget.results());
        progress.accept(folders);
        return new Result(kind, List.copyOf(ranked), total, folders, complete, (System.nanoTime() - start) / 1_000_000L, List.copyOf(ordered));
    }

    /** Keeps the likeliest finding of a path (it can be met from several roots). */
    private static void offer(Map<Path, Found> found, Found f) {
        found.merge(f.path(), f, (a, b) -> b.rank() < a.rank() || b.rank() == a.rank() && b.distance() < a.distance() ? b : a);
    }

    /** Whether the walk goes into a folder of this name. */
    static boolean enters(Kind kind, String name) {
        if (NEVER.contains(name)) return false;
        if (BUILD.contains(name) && !kind.inBuildOutput()) return false;
        if (name.startsWith(".")) return kind == Kind.PYTHON && PYTHON_HIDDEN.contains(name);
        return kind != Kind.PYTHON || !PYTHON_INTERNALS.contains(name);
    }

    // ---- what a file is ------------------------------------------------------------------------------------------------

    /** Why the file is of the kind, in a few words; null when it is not. */
    static String fileNote(Kind kind, Path file, String name, BasicFileAttributes a, boolean onPath) {
        String lower = name.toLowerCase(Locale.ROOT);
        String extension = lower.contains(".") ? lower.substring(lower.lastIndexOf('.') + 1) : "";
        boolean constraintsName = lower.matches(".*\\.constraints\\.(json|toml|ya?ml)");
        if (kind == Kind.PYTHON) {
            boolean bin = file.getParent() != null && file.getParent().getFileName() != null
                    && file.getParent().getFileName().toString().equals("bin");
            return PYTHON_NAME.matcher(name).matches() && (bin || onPath) && Files.isExecutable(file) ? python(file, onPath) : null;
        }
        if (!a.isRegularFile() || a.size() == 0) return null;
        return switch (kind) {
            case SCENARIO -> {
                if (constraintsName) yield null;
                yield switch (extension) {
                    case "xlsx" -> !lower.startsWith("~$") && zipText(file, "xl/workbook.xml", 262_144).contains("name=\"FW_Seq\"")
                            ? "workbook with an FW_Seq sheet" : null;
                    case "json" -> JSON_WORKBOOK.matcher(head(file, 16_384)).find() ? "scenario as JSON" : null;
                    case "toml", "yaml", "yml" -> SPEC.matcher(head(file, 262_144)).find() ? "spec with slots" : null;
                    default -> null;
                };
            }
            case CONSTRAINTS -> {
                if (constraintsName) yield "rules of " + name.substring(0, name.indexOf('.'));
                if (!extension.equals("json")) yield null;
                String head = head(file, 16_384);
                // a constraints file of another name: its rules list with the framework's rule keys, beside its version or params
                yield JSON_CONSTRAINTS.matcher(head).find() && !JSON_WORKBOOK.matcher(head).find() && JSON_RULE.matcher(head).find()
                        && (head.contains("\"version\"") || head.contains("\"params\"")) ? "rules as JSON" : null;
            }
            case METRICS -> extension.equals("kv") ? "metrics file"
                    : Set.of("log", "txt", "out").contains(extension) && METRIC_LINE.matcher(head(file, 65_536)).find()
                    ? "lines with measurements and FW_VAR" : null;
            case JACOCO -> extension.equals("xml") && (head(file, 4_096).contains("-//JACOCO//DTD")
                    || lower.contains("jacoco") && head(file, 4_096).contains("<report")) ? "JaCoCo XML report" : null;
            case COVERAGE_PY -> {
                if (!extension.equals("json")) yield null;
                String head = head(file, 4_096);
                yield head.contains("\"meta\"") && (head.contains("\"branch_coverage\"") || head.contains("\"show_contexts\""))
                        ? "coverage.py JSON report" : null;
            }
            case GCOV -> Set.of("gcov", "txt", "log", "out").contains(extension) && GCOV_SUMMARY.matcher(head(file, 65_536)).find()
                    ? "gcov summary (Lines executed)" : null;
            case TEXT -> {
                if (!Set.of("log", "out", "txt").contains(extension) || DULL_TEXT.matcher(lower).matches()) yield null;
                if (a.size() < 256 || a.size() > 64L * 1024 * 1024) yield null;   // a line or two is not worth scoring
                yield extension.equals("txt") ? "text, " + size(a.size()) : (extension.equals("log") ? "log, " : "program output, ") + size(a.size());
            }
            case RULES -> {
                if (!extension.equals("json")) yield null;
                String text = head(file, 65_536);
                yield text.contains("\"required_tokens\"") || text.contains("\"forbidden_tokens\"") || text.contains("\"token_weights\"")
                        ? "scoring rules" : null;
            }
            case ADDON -> extension.equals("jar") && !lower.startsWith("original-") ? addon(file) : null;
            default -> null;
        };
    }

    /** Why a folder is of a folder kind; null when it is not. */
    static String folderNote(Kind kind, Path folder) {
        return switch (kind) {
            case LIBRARY -> Files.isRegularFile(folder.resolve("intake").resolve("verified_examples.json"))
                    || Files.isRegularFile(folder.resolve("intake").resolve("automation_scenarios.json")) ? "holds intake/verified_examples.json" : null;
            case PYTHON_MODULES, PYTHON_WORKDIR -> {
                if (Files.isRegularFile(folder.resolve("pyvenv.cfg"))) yield null;
                if (Files.isRegularFile(folder.resolve("sut_paths.py"))) yield "a generator tree (holds sut_paths.py)";
                int modules = 0;
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder, "*.py")) {
                    for (Path ignored : entries) if (++modules >= 100) break;
                } catch (IOException | RuntimeException e) {
                    yield null;
                }
                yield modules > 0 ? modules + (modules >= 100 ? "+" : "") + " Python file" + (modules == 1 ? "" : "s") : null;
            }
            default -> null;
        };
    }

    /** The interpreter of a virtual environment, offered with its Python version. */
    private static void venv(Map<Path, Found> found, Path environment, Root root, int distance) {
        for (String name : new String[]{"python3", "python"}) {
            Path python = environment.resolve("bin").resolve(name);
            if (Files.isExecutable(python)) {
                String version = "";
                for (String line : head(environment.resolve("pyvenv.cfg"), 4_096).split("\n")) {
                    String[] pair = line.split("=", 2);
                    if (pair.length == 2 && (pair[0].strip().equals("version") || pair[0].strip().equals("version_info"))) {
                        version = " (Python " + pair[1].strip() + ")";
                        break;
                    }
                }
                String label = environment.getFileName().toString();
                if ((label.startsWith(".") || label.equals("venv") || label.equals("env")) && environment.getParent() != null) {
                    label = environment.getParent().getFileName() + "/" + label;   // a project's .venv is named by its project
                }
                offer(found, new Found(python, root.why(), "virtual environment " + label + version, modified(environment), root.rank(),
                        distance + 1));
                return;
            }
        }
    }

    private static String python(Path file, boolean onPath) {
        String path = file.toString();
        if (path.contains("/.pyenv/shims/")) return PYENV_SHIM;
        if (path.contains("/.pyenv/versions/")) return "pyenv Python " + file.getParent().getParent().getFileName();
        if (path.contains("conda")) return "conda Python";
        return onPath ? "on your PATH" : "installed Python";
    }

    /**
     * A jar that adds a runner: it names runner plugins in its services, and the application cannot load them already.
     * The application's own modules name plugins too; offering them would only add what is there.
     */
    private static String addon(Path jar) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry services = zip.getEntry(SUT_PLUGIN);
            if (services == null) return null;
            Set<String> added = new java.util.LinkedHashSet<>();
            try (InputStream in = zip.getInputStream(services)) {
                for (String line : new String(in.readNBytes(16_384), StandardCharsets.UTF_8).split("\n")) {
                    String type = line.replaceAll("#.*", "").strip();
                    if (type.isEmpty()) continue;
                    try {
                        Class.forName(type, false, FileSeeker.class.getClassLoader());
                    } catch (ClassNotFoundException | LinkageError absent) {
                        String simple = type.substring(type.lastIndexOf('.') + 1);
                        added.add(simple.contains("$") ? simple.substring(0, simple.indexOf('$')) : simple);
                    }
                }
            }
            if (added.isEmpty()) return null;
            List<String> names = added.stream().limit(2).toList();
            return "adds the runner" + (added.size() > 1 ? "s " : " ") + String.join(", ", names) + (added.size() > 2 ? ", …" : "");
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    // ---- reading a little --------------------------------------------------------------------------------------------

    /** The first bytes of a file as text; empty when unreadable. */
    static String head(Path file, int bytes) {
        try (InputStream in = Files.newInputStream(file)) {
            return new String(in.readNBytes(bytes), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /** The first bytes of one entry of a zip file (a workbook) as text; empty when it has none or is unreadable. */
    static String zipText(Path zip, String entry, int bytes) {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            ZipEntry e = file.getEntry(entry);
            if (e == null) return "";
            try (InputStream in = file.getInputStream(e)) {
                return new String(in.readNBytes(bytes), StandardCharsets.UTF_8);
            }
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private static Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException | RuntimeException e) {
            return path;
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException | RuntimeException e) {
            return 0;
        }
    }

    private static String size(long bytes) {
        return bytes < 1024 ? bytes + " B" : bytes < 1024 * 1024 ? (bytes / 1024) + " KB" : (bytes / (1024 * 1024)) + " MB";
    }
}
