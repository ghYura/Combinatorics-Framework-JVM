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

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The Cucumber showcase, in a space of its own, as a Java program the JDK runs from source (the Framework's tree holds Java
 * and data only, rule R01). Every command runs with its own Maven repository, Selenium cache, HOME, TMPDIR and XDG folders
 * under target/space, without DISPLAY, on loopback ports the system picks: nothing it does reaches the user's ~/.m2,
 * ~/.cache, ~/.mozilla, desktop, environment or another agent's parallel work. The variables are set for each command it
 * starts only; nothing is exported to the calling shell. Run it from this folder or from the Framework checkout.
 */
public final class Showcase {

    private static final String USAGE = """
            java Showcase.java build                 compile the glue, the systems under test and the dummy web shop; fetch the libraries
            java Showcase.java check  01|02          validate, plan (with the FW_Seq graph) and count the candidates; nothing runs
            java Showcase.java smoke  01|02          run the first 3 test cases only
            java Showcase.java run    01|02 [--set K=V ...]   run the whole design, then export, triage and analyze it
            java Showcase.java report 01|02 [RUN_ID]  export, triage and analyze a finished run again from its checkpoint
            java Showcase.java cucumber 01           run the .feature files the last run generated with plain Cucumber
            java Showcase.java serve [PORT]          serve the dummy web shop for a look by hand (member / secret signs in)

            Optional inputs: SHOWCASE_SPACE (default target/space), FW_APP_JAR (default ../fw-app/target/fw-app.jar) and
            SHOWCASE_HEAP (default -XX:MaxRAMPercentage=50.0).
            """;
    private static final Pattern SUMMARY = Pattern.compile("^ *(state|candidates|stopped|executed|outcomes|verdicts|reconciled|identity) .*");

    private final Path here;
    private final Path repo;
    private final Path space;
    private final Path jar;
    private final Path out;
    private final List<String> heap;
    /** No RAM limit of the showcase's own: the memory budget is half the machine's memory, as the heap may be. */
    private final List<String> budget;

    private Showcase(Path here) throws IOException {
        this.here = here;
        this.repo = here.getParent();
        String spaceSetting = System.getenv("SHOWCASE_SPACE");
        this.space = spaceSetting == null || spaceSetting.isBlank() ? here.resolve("target").resolve("space") : Path.of(spaceSetting).toAbsolutePath();
        String jarSetting = System.getenv("FW_APP_JAR");
        this.jar = jarSetting == null || jarSetting.isBlank() ? repo.resolve("fw-app").resolve("target").resolve("fw-app.jar") : Path.of(jarSetting).toAbsolutePath();
        this.out = here.resolve("target").resolve("runs");
        String heapSetting = System.getenv("SHOWCASE_HEAP");
        this.heap = List.of((heapSetting == null || heapSetting.isBlank() ? "-XX:MaxRAMPercentage=50.0" : heapSetting).trim().split("\\s+"));
        long memory = ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        this.budget = List.of("--set", "cf.memory.budget.bytes=" + memory / 2);
        for (String folder : List.of("m2", "selenium", "home", "tmp", "xdg-config", "xdg-cache", "xdg-data")) {
            Files.createDirectories(space.resolve(folder));
        }
        Files.createDirectories(out);
    }

    public static void main(String[] args) throws Exception {
        Path cwd = Path.of("").toAbsolutePath();
        Path here = Files.isDirectory(cwd.resolve("01-gherkin-checkout")) ? cwd
                : Files.isDirectory(cwd.resolve("showcase-cucumber").resolve("01-gherkin-checkout")) ? cwd.resolve("showcase-cucumber") : null;
        if (here == null) {
            System.err.print("run it from the showcase-cucumber folder or from the Framework checkout:\n" + USAGE);
            System.exit(2);
        }
        Showcase showcase = new Showcase(here);
        String command = args.length > 0 ? args[0] : "";
        String first = args.length > 1 ? args[1] : "";
        List<String> rest = args.length > 2 ? List.of(args).subList(2, args.length) : List.of();
        int code = switch (command) {
            case "build" -> showcase.build();
            case "check" -> showcase.check(first);
            case "smoke" -> {
                List<String> extra = new ArrayList<>(List.of("--set", "cf.run.stop.candidates=3", "--set", "cf.run.id=smoke_" + first));
                extra.addAll(rest);
                yield showcase.run(first, extra);
            }
            case "run" -> showcase.run(first, rest);
            case "report" -> showcase.report(first, args.length > 2 ? args[2] : "");
            case "cucumber" -> showcase.cucumber(first.isEmpty() ? "01" : first);
            case "serve" -> showcase.serve(first.isEmpty() ? "0" : first);
            default -> {
                System.out.print(USAGE);
                yield 2;
            }
        };
        System.exit(code);
    }

    // ---- the commands -------------------------------------------------------------------------------------------------

    private int build() throws IOException, InterruptedException {
        String mvn = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows") ? "mvn.cmd" : "mvn";
        int code = exec(here, List.of(mvn, "-B", "-q", "-Dmaven.repo.local=" + space.resolve("m2"),
                "-Dmaven.repo.local.tail=" + Path.of(System.getProperty("user.home"), ".m2", "repository"), "process-test-classes"), Io.INHERIT);
        if (code == 0) {
            try (Stream<Path> libraries = Files.list(here.resolve("target").resolve("lib"))) {
                System.out.println("built: " + here.resolve("target").resolve("test-classes") + " and " + libraries.count() + " libraries in "
                        + here.resolve("target").resolve("lib"));
            }
        }
        return code;
    }

    private int check(String which) throws IOException, InterruptedException {
        String s = scenario(which);
        String name = name(s);
        int code = needBuild();
        if (code == 0) code = cf(Io.INHERIT, "validate", "--scenario", s + ".json", "--config", s + ".properties", "--set", classpathSetting());
        if (code == 0) code = cf(Io.INHERIT, "plan", "--scenario", s + ".json", "--config", s + ".properties", "--graph",
                out.resolve(name + ".fwseq.dot").toString(), "--set", classpathSetting());
        if (code == 0) code = cf(Io.INHERIT, "candidates", "--scenario", s + ".json", "--config", s + ".properties", "--limit", "1",
                "--set", classpathSetting());
        return code;
    }

    private int run(String which, List<String> extra) throws IOException, InterruptedException {
        String s = scenario(which);
        String name = name(s);
        String id = runId(s);
        for (String a : extra) {
            if (a.startsWith("cf.run.id=")) id = a.substring("cf.run.id=".length());
        }
        int built = needBuild();
        if (built != 0) return built;
        Path dir = Files.createDirectories(out.resolve(name));
        Path checkpoint = dir.resolve(id + ".checkpoint");
        System.out.println("run " + id + ": output in " + dir.resolve(id + ".out") + ", diagnostics in " + dir.resolve(id + ".err"));
        List<String> command = new ArrayList<>(List.of("run", "--scenario", s + ".json", "--config", s + ".properties", "--set", classpathSetting()));
        command.addAll(budget);
        command.addAll(List.of("--checkpoint", checkpoint.toString()));
        command.addAll(extra);
        int code = cf(new Io(Redirect.to(dir.resolve(id + ".out").toFile()), Redirect.to(dir.resolve(id + ".err").toFile()), false),
                command.toArray(String[]::new));
        System.out.println("exit code " + code);
        Files.readAllLines(dir.resolve(id + ".out"), StandardCharsets.UTF_8).stream().filter(l -> SUMMARY.matcher(l).matches())
                .forEach(System.out::println);
        int reported = report(which, id);
        return reported != 0 ? reported : code;
    }

    /**
     * Exports of a finished run: the HTML page (with the captured Cucumber output of every sample, hence the larger memory
     * budget), the metrics corpus and its analysis, the samples as CSV, and the triage of the failures.
     */
    private int report(String which, String runId) throws IOException, InterruptedException {
        String s = scenario(which);
        String name = name(s);
        String id = runId.isEmpty() ? runId(s) : runId;
        Path dir = out.resolve(name);
        Path checkpoint = dir.resolve(id + ".checkpoint");
        if (!Files.isRegularFile(checkpoint)) {
            System.err.println("no checkpoint " + checkpoint);
            return 2;
        }
        File errors = dir.resolve(id + ".export.err").toFile();
        Path metrics = dir.resolve(id + "-metrics.kv");
        if (cf(new Io(Redirect.DISCARD, Redirect.to(errors), false), with("export", "--input", checkpoint.toString(), "--output",
                dir.resolve(id + ".html").toString(), "--kind", "html")) != 0) System.out.println("html export failed: " + errors);
        if (cf(new Io(Redirect.DISCARD, Redirect.appendTo(errors), false), with("export", "--input", checkpoint.toString(), "--output",
                metrics.toString(), "--kind", "metrics")) != 0) System.out.println("metrics export failed");
        if (cf(new Io(Redirect.DISCARD, Redirect.appendTo(errors), false), with("export", "--input", checkpoint.toString(), "--output",
                dir.resolve(id + "-samples.csv").toString(), "--kind", "csv")) != 0) System.out.println("csv export failed");
        // triage decodes a checkpoint within a fixed 128 MiB (it ignores cf.memory.budget.bytes there); past that it reads the
        // same run from its metrics corpus, in a run folder of its own, without the candidates' source sizes
        Io triage = new Io(Redirect.to(dir.resolve(id + ".triage.txt").toFile()), null, true);
        String json = dir.resolve(id + ".triage.json").toString();
        if (cf(triage, with("triage", "--input", checkpoint.toString(), "--json", json)) != 0) {
            Path input = Files.createDirectories(dir.resolve(id + ".triage-input"));
            Files.copy(metrics, input.resolve("metrics.kv"), StandardCopyOption.REPLACE_EXISTING);
            cf(triage, with("triage", "--input", input.toString(), "--top", "16", "--json", json));
        }
        cf(new Io(Redirect.to(dir.resolve(id + ".analysis.txt").toFile()), null, true),
                with("analyze", "--results", metrics.toString(), "--config", s + ".properties"));
        System.out.println("exports in " + dir + ": " + id + ".html, " + id + "-metrics.kv, " + id + "-samples.csv, " + id + ".triage.txt, "
                + id + ".analysis.txt");
        return 0;
    }

    private int cucumber(String which) throws IOException, InterruptedException {
        if (!which.equals("01")) {
            System.err.println("plain Cucumber runs the generated checkout features (01); the web ones need the bootstrap's shop");
            return 2;
        }
        int built = needBuild();
        if (built != 0) return built;
        return exec(repo, List.of(java(), "-cp", classpath(), "io.cucumber.core.cli.Main", "--glue", "org.ghyura.cf.showcase.cucumber.checkout",
                "--plugin", "summary", "--plugin", "junit:" + out.resolve("checkout_cucumber").resolve("plain-cucumber-junit.xml"), "--monochrome",
                slashes(repo.relativize(here.resolve("target").resolve("generated-features").resolve("01-gherkin-checkout")))), Io.INHERIT);
    }

    private int serve(String port) throws IOException, InterruptedException {
        int built = needBuild();
        if (built != 0) return built;
        return exec(Path.of("").toAbsolutePath(), List.of(java(), "-cp", classpath(), "org.ghyura.cf.showcase.cucumber.web.DummyWebShop", port),
                Io.INHERIT);
    }

    // ---- the parts ----------------------------------------------------------------------------------------------------

    /** Where a process's output goes: two redirects, or the error stream merged into the output. */
    private record Io(Redirect output, Redirect error, boolean merged) {
        static final Io INHERIT = new Io(Redirect.INHERIT, Redirect.INHERIT, false);
    }

    /** The scenario's path without its extension, relative to the Framework checkout, as its FW_Arguments expect. */
    private String scenario(String which) {
        String folder = switch (which) {
            case "01" -> "01-gherkin-checkout/checkout_cucumber";
            case "02" -> "02-selenium-webshop/webshop_selenium";
            default -> {
                System.err.println("choose 01 (Gherkin checkout) or 02 (Selenium web shop)");
                System.exit(2);
                yield "";
            }
        };
        return slashes(repo.relativize(here)) + "/" + folder;
    }

    private static String name(String scenario) {
        return scenario.substring(scenario.lastIndexOf('/') + 1);
    }

    /** The run id the scenario's settings name (cf.run.id=…). */
    private String runId(String scenario) throws IOException {
        return Files.readAllLines(repo.resolve(scenario + ".properties"), StandardCharsets.UTF_8).stream()
                .filter(l -> l.startsWith("cf.run.id=")).map(l -> l.substring("cf.run.id=".length()).trim()).findFirst().orElse("");
    }

    private int needBuild() throws IOException, InterruptedException {
        if (!Files.isDirectory(here.resolve("target").resolve("test-classes").resolve("org")) || !Files.isDirectory(here.resolve("target").resolve("lib"))) {
            int code = build();
            if (code != 0) return code;
        }
        if (!Files.isRegularFile(jar)) {
            System.err.println("no Framework jar at " + jar + ": build it (mvn -o -B -q package -DskipTests -pl fw-app -am) or set FW_APP_JAR");
            return 2;
        }
        return 0;
    }

    /** The glue in target/test-classes and the jars in target/lib. */
    private String classpath() throws IOException {
        try (Stream<Path> jars = Files.list(here.resolve("target").resolve("lib"))) {
            return Stream.concat(Stream.of(here.resolve("target").resolve("test-classes").toString()),
                    jars.filter(p -> p.getFileName().toString().endsWith(".jar")).map(Path::toString).sorted()).collect(Collectors.joining(File.pathSeparator));
        }
    }

    private String classpathSetting() throws IOException {
        return "cf.execution.classpath=" + classpath();
    }

    /** The command followed by the raised memory budget. */
    private String[] with(String... command) {
        List<String> all = new ArrayList<>(List.of(command));
        all.addAll(budget);
        return all.toArray(String[]::new);
    }

    /** The Framework, from the checkout, so the scenarios' relative FW_Arguments resolve as written. */
    private int cf(Io io, String... arguments) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(java(), "-Djava.awt.headless=true", "-Djava.io.tmpdir=" + space.resolve("tmp")));
        command.addAll(heap);
        command.addAll(List.of("-jar", jar.toString()));
        command.addAll(List.of(arguments));
        return exec(repo, command, io);
    }

    /** Runs a command in its own environment and waits for it. */
    private int exec(Path folder, List<String> command, Io io) throws IOException, InterruptedException {
        ProcessBuilder process = new ProcessBuilder(command).directory(folder.toFile()).redirectInput(Redirect.INHERIT);
        Map<String, String> environment = process.environment();
        environment.remove("DISPLAY");
        environment.remove("WAYLAND_DISPLAY");
        environment.put("HOME", space.resolve("home").toString());
        environment.put("TMPDIR", space.resolve("tmp").toString());
        environment.put("XDG_CONFIG_HOME", space.resolve("xdg-config").toString());
        environment.put("XDG_CACHE_HOME", space.resolve("xdg-cache").toString());
        environment.put("XDG_DATA_HOME", space.resolve("xdg-data").toString());
        environment.put("SE_CACHE_PATH", space.resolve("selenium").toString());
        environment.put("SE_AVOID_STATS", "true");
        environment.put("MOZ_HEADLESS", "1");
        process.redirectOutput(io.output());
        if (io.merged()) {
            process.redirectErrorStream(true);
        } else {
            process.redirectError(io.error());
        }
        return process.start().waitFor();
    }

    private static String java() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static String slashes(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }
}
