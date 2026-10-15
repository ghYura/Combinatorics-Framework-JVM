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

import org.ghyura.cf.input.InputFormat;
import org.ghyura.cf.input.WorkbookFormats;
import org.ghyura.cf.input.builder.Flag;
import org.ghyura.cf.input.builder.JoinSpec;
import org.ghyura.cf.input.builder.Multiplicity;
import org.ghyura.cf.input.builder.ScheduleBuilder;
import org.ghyura.cf.input.builder.Verb;
import org.ghyura.cf.model.input.WorkbookData;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A01, packaged half: the one runnable jar is self-contained, ships no native code or banned artifact,
 * and starts and answers in an environment with no Python, no database, no tools on the path, a read-only
 * working directory, a small heap and a plain-ASCII locale. Runs after the jar is built (failsafe).
 *
 * <p>Two tests enforce the boundary at the operating-system level when the tools exist: {@code bwrap} runs the
 * jar in a root with a loopback-only network, no {@code /usr/bin}, no {@code /bin}, no {@code /home} (so no
 * Python anywhere) and a read-only working directory, after an {@link IsolationProbe} proves those denials;
 * {@code strace} records every process, thread and socket system call. Without those tools the tests are
 * skipped and show as skipped, never as passed; the weaker environment-stripping tests still run.</p>
 *
 * <p>Architect decision F29 (revision 2): the JDK's first use of NIO file channels and of its security providers
 * makes the JVM create, and close at once, an IPv4 and an IPv6 socket to probe what the local network stack
 * supports. That probe is not network use. The tests therefore tell it apart from network use: any socket may be
 * created, but nothing may connect, bind, send to an internet address, listen or accept, no class that builds a
 * connection target or a socket stream may load, and no child process may start.</p>
 */
class PackagedApplicationIT {

    /**
     * A20 determinism: the same scenario (non-ASCII values, a constraint sidecar, a numeric rule) planned, listed, run and
     * exported by the packaged jar in four processes whose locale, time zone and worker count all differ gives the same plan,
     * the same candidates with the same identities and rendered sources, the same digests and the same final samples; only
     * wall-clock durations and the workers setting itself may differ, and they are taken out before comparing.
     */
    @Test
    void identitiesAndResultsAreTheSameInEveryLocaleTimeZoneAndWorkerCount(@TempDir Path work) throws Exception {
        Files.writeString(work.resolve("det.json"), """
                {"sheets": {"FW_Seq": [["P", "FW_Combi(1)"], ["M", "FW_Combi(1)"], ["N", "FW_Combi(1)"]],
                 "P": [["proto=http;"], ["proto=İstanbul;"], ["proto=ı;"]],
                 "M": [["mode=a FW_VAR=0 ratio=1.5;"], ["mode=b FW_VAR=1 ratio=2.25;"]],
                 "N": [["n=1e3;"], ["n=ß;"]]}}
                """, StandardCharsets.UTF_8);
        Files.writeString(work.resolve("det.constraints.json"), """
                {"version": 1, "params": {"P": {"proto=ı;": {"t": 1}, "proto=http;": {"t": 0}, "proto=İstanbul;": {"t": 0}},
                                          "M": {"mode=a FW_VAR=0 ratio=1.5;": {"f": 0}, "mode=b FW_VAR=1 ratio=2.25;": {"f": 1}}},
                 "constraints": [{"id": "dotless_i_with_b", "sheets": ["P", "M"], "when": "P.t == 1 and M.f == 1", "gate": {}}]}
                """, StandardCharsets.UTF_8);
        String[][] variants = {{"C", "en", "US", "UTC", "1"}, {"tr_TR.UTF-8", "tr", "TR", "Pacific/Kiritimati", "4"},
            {"ar_SA.UTF-8", "ar", "SA", "America/St_Johns", "3"}, {"de_DE.UTF-8", "de", "DE", "Asia/Kathmandu", "2"}};
        java.util.regex.Pattern digests = java.util.regex.Pattern.compile("(scenarioDigest|planDigest|policyDigest)=\"[^\"]*\"");
        Map<String, String> first = null;
        for (String[] v : variants) {
            String[] jvm = {"-Duser.language=" + v[1], "-Duser.country=" + v[2], "-Duser.timezone=" + v[3]};
            Outcome plan = launch(work, work, List.of("plan", "--scenario", "det.json", "--format", "xml"), v[0], jvm);
            Outcome candidates = launch(work, work, List.of("candidates", "--scenario", "det.json", "--limit", "20", "--format", "xml"), v[0], jvm);
            String checkpoint = "cp-" + v[1] + ".xml";
            Outcome run = launch(work, work, List.of("run", "--scenario", "det.json", "--sut", "inline-metrics", "--run-id", "det", "--workers",
                    v[4], "--checkpoint", checkpoint, "--format", "xml"), v[0], jvm);
            Outcome finals = launch(work, work, List.of("export", "--input", checkpoint, "--output", "finals-" + v[1] + ".csv", "--kind", "csv"),
                    v[0], jvm);
            Outcome attempts = launch(work, work, List.of("export", "--input", checkpoint, "--output", "attempts-" + v[1] + ".csv", "--kind",
                    "results"), v[0], jvm);
            for (Outcome o : List.of(plan, candidates, run, finals, attempts)) {
                assertEquals(0, o.exit(), String.join(" ", v) + ": " + o.err());
            }
            Map<String, String> observed = new java.util.LinkedHashMap<>();
            observed.put("plan", plan.out());
            observed.put("candidates", candidates.out());
            java.util.regex.Matcher m = digests.matcher(run.out());
            StringBuilder found = new StringBuilder();
            while (m.find()) {
                found.append(m.group()).append('\n');
            }
            observed.put("digests", found.toString());
            observed.put("finals", Files.readString(work.resolve("finals-" + v[1] + ".csv")));
            StringBuilder rows = new StringBuilder();
            for (String line : Files.readAllLines(work.resolve("attempts-" + v[1] + ".csv"))) {
                List<String> cells = new ArrayList<>(List.of(line.split(",", -1)));
                cells.remove(9);   // worker
                cells.remove(6);   // duration_ms
                rows.append(String.join(",", cells)).append('\n');
            }
            observed.put("attempts", rows.toString());
            if (first == null) {
                first = observed;
                assertTrue(candidates.out().contains("generated=\"12\"") && candidates.out().contains("executable=\"10\""), candidates.out());
                assertTrue(candidates.out().contains("proto=İstanbul;") && candidates.out().contains("n=ß;"), "UTF-8 under LC_ALL=C too");
                assertEquals(3, found.toString().lines().count(), run.out());
                assertTrue(rows.toString().startsWith("run_id,candidate_id,attempt,outcome,verdict_code,verdict_message,exit_code,signal,"), rows.toString());
            } else {
                for (String key : first.keySet()) {
                    assertEquals(first.get(key), observed.get(key), key + " under " + String.join(" ", v));
                }
            }
        }
    }

    /**
     * M10 binary notices: META-INF/cf-third-party.xml declares every third-party component the jar carries, and the jar
     * proves it: each third-party entry lies under a declared component's roots, each declared license and notice file is
     * present and not empty, every bundled pom.properties version is the declared one, the colliding upstream LICENSE and
     * NOTICE names are gone (each travels under META-INF/licenses/ARTIFACT/), the ported tomli parser carries its MIT notice
     * and what could not be settled offline is stated as a residual.
     */
    @Test
    void everyBundledComponentTravelsWithItsLicenseAndNotice() throws Exception {
        try (JarFile archive = new JarFile(jar().toFile())) {
            JarEntry inventory = archive.getJarEntry("META-INF/cf-third-party.xml");
            assertNotNull(inventory, "the jar declares its third-party code");
            Element root;
            try (var in = archive.getInputStream(inventory)) {
                root = parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
            List<String> roots = new ArrayList<>();
            Set<String> declaredFiles = new LinkedHashSet<>();
            Map<String, String> versions = new java.util.HashMap<>();
            NodeList components = root.getElementsByTagName("component");
            assertTrue(components.getLength() >= 13, "components: " + components.getLength());
            for (String tag : List.of("component", "ported")) {
                NodeList nodes = root.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element c = (Element) nodes.item(i);
                    roots.addAll(List.of(c.getAttribute("packages").split("\\s+")));
                    for (String file : c.getAttribute("files").split("\\s+")) {
                        if (!file.isEmpty()) {
                            JarEntry entry = archive.getJarEntry(file);
                            assertTrue(entry != null && entry.getSize() > 0, c.getAttribute("artifact") + c.getAttribute("name") + ": " + file);
                            declaredFiles.add(file);
                        }
                    }
                    if (tag.equals("component")) {
                        versions.put(c.getAttribute("group") + ":" + c.getAttribute("artifact"), c.getAttribute("version"));
                        assertTrue(!c.getAttribute("files").isBlank() || !c.getAttribute("residual").isBlank(),
                                c.getAttribute("artifact") + " has neither its license files nor a stated residual");
                    }
                }
            }
            roots.removeIf(String::isEmpty);
            List<String> undeclared = new ArrayList<>();
            List<String> checkedVersions = new ArrayList<>();
            for (Enumeration<JarEntry> entries = archive.entries(); entries.hasMoreElements(); ) {
                JarEntry e = entries.nextElement();
                String name = e.getName();
                if (e.isDirectory() || name.startsWith("org/ghyura/") || name.equals("META-INF/MANIFEST.MF") || name.startsWith("META-INF/services/")
                        || name.startsWith("META-INF/licenses/") || name.equals("META-INF/cf-third-party.xml") || declaredFiles.contains(name)
                        || name.equals("META-INF/LICENSE") || name.equals("META-INF/NOTICE.md")) {
                    continue;
                }
                if (name.startsWith("META-INF/maven/")) {
                    if (name.endsWith("/pom.properties") && !name.startsWith("META-INF/maven/org.ghyura")) {
                        Properties pom = new Properties();
                        try (var in = archive.getInputStream(e)) {
                            pom.load(in);
                        }
                        String key = pom.getProperty("groupId") + ":" + pom.getProperty("artifactId");
                        assertEquals(versions.get(key), pom.getProperty("version"), "the declared version of " + key);
                        checkedVersions.add(key);
                    }
                    continue;
                }
                String path = name.matches("META-INF/versions/\\d+/.*") ? name.substring(name.indexOf('/', "META-INF/versions/".length()) + 1) : name;
                if (!path.startsWith("org/ghyura/") && roots.stream().noneMatch(path::startsWith)) {
                    undeclared.add(name);
                }
            }
            assertEquals(List.of(), undeclared, "third-party entries no declared component owns");
            // the POI and xmlbeans jars carry no pom.properties; the nine others do, and each version was the declared one
            assertEquals(9, checkedVersions.size(), "bundled versions checked: " + checkedVersions);
            for (String colliding : List.of("META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/DEPENDENCIES")) {
                assertEquals(null, archive.getJarEntry(colliding), colliding + " would be one upstream jar's file standing for all");
            }
            // META-INF/LICENSE and META-INF/NOTICE.md are the project's own terms, unchanged (OPEN-08), never an upstream file
            for (String terms : List.of("LICENSE", "NOTICE.md")) {
                try (var in = archive.getInputStream(archive.getJarEntry("META-INF/" + terms))) {
                    assertArrayEquals(Files.readAllBytes(Reactor.root().resolve(terms)), in.readAllBytes(), "META-INF/" + terms);
                }
            }
            try (var in = archive.getInputStream(archive.getJarEntry("META-INF/licenses/tomli/LICENSE"))) {
                String mit = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(mit.startsWith("MIT License\n\nCopyright (c) 2021 Taneli Hukkinen\n\nPermission is hereby granted, free of charge,")
                        && mit.endsWith("OTHER DEALINGS IN THE\nSOFTWARE.\n"), mit);
            }
            // curvesapi (BSD-3-Clause, shipped without a license file) is excluded with POI's Visio classes, its only users:
            // no class of it is carried and no carried class refers to it
            List<String> curvesapi = new ArrayList<>();
            for (Enumeration<JarEntry> entries = archive.entries(); entries.hasMoreElements(); ) {
                JarEntry e = entries.nextElement();
                if (e.getName().startsWith("com/graphbuilder/") || e.getName().startsWith("org/apache/poi/xdgf/")
                        || e.getName().startsWith("META-INF/maven/com.github.virtuald/")) {
                    curvesapi.add(e.getName());
                } else if (e.getName().endsWith(".class")) {
                    try (var in = archive.getInputStream(e)) {
                        if (new String(in.readAllBytes(), StandardCharsets.ISO_8859_1).contains("com/graphbuilder")) {
                            curvesapi.add(e.getName() + " refers to it");
                        }
                    }
                }
            }
            assertEquals(List.of(), curvesapi, "curvesapi and its only users are left out of the distribution");
        }
    }

    /** One A10 workload: what it ran, what it must end with, and the report attribute that states its reservation high water. */
    private record Workload(String name, int exit, String reservation, String refusal, List<String> args) {
    }

    /**
     * A10 memory: workloads of the packaged jar, each in its own process under a 64 MiB heap with GC logging, either finish
     * within their reservation budget or stop with an explicit RESOURCE_LIMIT, never an OutOfMemoryError: a ranked product
     * of 10^12 candidates is planned and 200 000 of them scanned without materialising it; a 1.6 MB, 30 000-cell document is
     * refused before it is parsed; a key dictionary holding a 1 MB file value, a full result history and a Pareto analysis of
     * 50 000 samples each exhaust a 1 MiB budget; 20 000 samples, and 60 000 with three repeats, run with aggregate retention. For every workload the reservation high water the report states and the
     * heap the GC log observed (the largest occupancy before a collection or at exit) are written, machine-readable, to
     * fw-acceptance/target/a10-memory-evidence.xml.
     */
    @Test
    void boundedHeapWorkloadsStayWithinTheirBudgetsOrRefuseExplicitly(@TempDir Path work) throws Exception {
        StringBuilder big = new StringBuilder("{\"sheets\": {\"FW_Seq\": [");
        for (int i = 0; i < 12; i++) {
            big.append(i == 0 ? "" : ", ").append("[\"S").append(i).append("\", \"FW_Combi(1)\"]");
        }
        big.append("]");
        for (int i = 0; i < 12; i++) {
            big.append(", \"S").append(i).append("\": [");
            for (int j = 0; j < 10; j++) {
                big.append(j == 0 ? "" : ", ").append("[\"s").append(i).append('v').append(j).append(";\"]");
            }
            big.append(']');
        }
        Files.writeString(work.resolve("big.json"), big.append("}}").toString());
        StringBuilder dict = new StringBuilder("{\"sheets\": {\"FW_Seq\": [[\"D\", \"FW_Combi(1)\"], [\"E\", \"FW_Combi(1)\"]], \"E\": [[\"e;\"]], \"D\": [");
        for (int j = 0; j < 30_000; j++) {
            dict.append(j == 0 ? "" : ", ").append(String.format(java.util.Locale.ROOT, "[\"distinct-label-number-%06d-with-some-padding;\"]", j));
        }
        Files.writeString(work.resolve("dict.json"), dict.append("]}}").toString());
        // a small scenario whose one value is a whole file: read within any budget, its key dictionary keeps the file's text
        Files.writeString(work.resolve("file-value.json"), "{\"sheets\": {\"FW_Seq\": [[\"D\", \"FW_Combi(1)\"]], \"D\": [[\"FW_File=values.txt\"]]}}");
        Files.writeString(work.resolve("values.txt"), "a long value line;\n".repeat(60_000));
        StringBuilder runs = new StringBuilder("{\"sheets\": {\"FW_Seq\": [[\"P\", \"FW_Combi(1)\"], [\"M\", \"FW_Combi(1)\"]], \"P\": [");
        for (int i = 0; i < 200; i++) {
            runs.append(i == 0 ? "" : ", ").append("[\"x=").append(i).append(";\"]");
        }
        runs.append("], \"M\": [");
        for (int j = 0; j < 100; j++) {
            runs.append(j == 0 ? "" : ", ").append("[\"y=").append(j).append(" FW_VAR=").append(j % 2).append(" \"]");
        }
        Files.writeString(work.resolve("runs.json"), runs.append("]}}").toString());
        StringBuilder corpus = new StringBuilder();
        for (int i = 0; i < 50_000; i++) {
            corpus.append("candidate_id=").append(i).append(" FW_VAR=0 lat=").append(i * 7919L % 1000).append(" thr=").append(i * 104729L % 997).append('\n');
        }
        Files.writeString(work.resolve("corpus.kv"), corpus);
        StringBuilder wide = new StringBuilder("candidate_id=wide FW_VAR=0");
        List<String> wideGoals = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            wide.append(" m").append(i).append("=1");
            wideGoals.add("m" + i + ":min");
        }
        Files.writeString(work.resolve("wide.kv"), wide.append('\n'));
        String wideGoalSetting = "cf.analysis.goals=" + String.join(",", wideGoals);
        Files.writeString(work.resolve("fit.properties"), "cf.memory.budget.bytes=33554432\ncf.pipeline.queue.maxBytes=4194304\n"
                + "cf.compiler.cache.maxBytes=4194304\ncf.results.maxAttachmentBytes=1048576\n");
        Files.writeString(work.resolve("small.properties"), "cf.memory.budget.bytes=1048576\ncf.pipeline.queue.maxBytes=262144\n"
                + "cf.compiler.cache.maxBytes=262144\ncf.results.maxAttachmentBytes=65536\n");
        // Compact cells fit parsing admission but their retained dictionary and source locations exceed 1 MiB.
        StringBuilder dictionary = new StringBuilder("{\"sheets\":{\"FW_Seq\":[[\"P\",\"FW_Combi(1)\"]],\"P\":[");
        for (int i = 0; i < 4_000; i++) dictionary.append(i == 0 ? "" : ",").append("[\"").append(i).append("\"]");
        Files.writeString(work.resolve("dictionary.json"), dictionary.append("]}}").toString());
        List<Workload> workloads = List.of(
                new Workload("plan-ranked-product", 0, "highWaterBytes", "", List.of("plan", "--scenario", "big.json", "--config", "fit.properties")),
                new Workload("scan-ranked-product", 0, "highWaterBytes", "", List.of("candidates", "--scenario", "big.json", "--limit", "3", "--scan",
                        "200000", "--config", "fit.properties")),
                new Workload("document-admission", 5, "", "for reading the scenario", List.of("plan", "--scenario", "dict.json", "--config",
                        "small.properties")),
                new Workload("referenced-input-admission", 5, "", "for reading the scenario input", List.of("plan", "--scenario", "file-value.json", "--config",
                        "small.properties")),
                new Workload("dictionary-exhaustion", 5, "", "for the key dictionary", List.of("plan", "--scenario", "dictionary.json", "--config",
                        "small.properties")),
                new Workload("result-history-exhaustion", 5, "budgetHighWater", "", List.of("run", "--scenario", "runs.json", "--sut",
                        "inline-metrics", "--run-id", "full", "--config", "small.properties")),
                new Workload("aggregate-results", 0, "budgetHighWater", "", List.of("run", "--scenario", "runs.json", "--sut", "inline-metrics",
                        "--run-id", "agg", "--config", "fit.properties", "--set", "cf.results.retention=aggregate-only")),
                new Workload("repeated-observations", 0, "budgetHighWater", "", List.of("run", "--scenario", "runs.json", "--sut", "inline-metrics",
                        "--run-id", "rep", "--repeat", "3", "--config", "fit.properties", "--set", "cf.results.retention=aggregate-only")),
                new Workload("analysis-input-exhaustion", 5, "", "", List.of("analyze", "--results", "corpus.kv", "--config", "small.properties",
                        "--set", "cf.analysis.goals=lat:min,thr:max")),
                new Workload("nsga3-reference-exhaustion", 5, "", "for analysis ranking workspace", List.of("analyze", "--results", "wide.kv",
                        "--config", "small.properties", "--set", wideGoalSetting, "--set", "cf.analysis.front=nsga-iii")),
                new Workload("moead-reference-exhaustion", 5, "", "for analysis ranking workspace", List.of("analyze", "--results", "wide.kv",
                        "--config", "small.properties", "--set", wideGoalSetting, "--set", "cf.analysis.front=moea-d")),
                new Workload("reference-directions-fit", 0, "", "", List.of("analyze", "--results", "wide.kv", "--config", "fit.properties",
                        "--set", wideGoalSetting, "--set", "cf.analysis.front=nsga-iii")));
        java.util.regex.Pattern pause = java.util.regex.Pattern.compile("(\\d+)M->\\d+M\\(\\d+M\\)");
        java.util.regex.Pattern exitHeap = java.util.regex.Pattern.compile("garbage-first heap\\s+total reserved \\d+K, committed \\d+K, used (\\d+)K");
        StringBuilder evidence = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<a10-memory heapMaxBytes=\"67108864\" jdk=\"")
                .append(Runtime.version()).append("\" processors=\"").append(Runtime.getRuntime().availableProcessors()).append("\">\n");
        for (Workload w : workloads) {
            Path gc = work.resolve(w.name() + ".gc");
            List<String> args = new ArrayList<>(w.args());
            args.addAll(List.of("--format", "xml"));
            Outcome o = launch(work, work, args, "C", "-Xmx64m", "-Xlog:gc*=info:file=" + gc);
            assertEquals(w.exit(), o.exit(), w.name() + ": " + o.err() + o.out());
            assertFalse((o.out() + o.err()).contains("OutOfMemoryError"), w.name());
            if (w.exit() == 5) {
                assertTrue(o.out().contains("code=\"RESOURCE_LIMIT\""), w.name() + " ends with an explicit refusal: " + o.out());
                assertTrue(o.out().contains(w.refusal()), w.name() + " is refused " + w.refusal() + ": " + o.out());
            }
            long heapPeak = 0;
            String log = Files.exists(gc) ? Files.readString(gc) : "";
            java.util.regex.Matcher m = pause.matcher(log);
            while (m.find()) {
                heapPeak = Math.max(heapPeak, Long.parseLong(m.group(1)) << 20);
            }
            m = exitHeap.matcher(log);
            while (m.find()) {
                heapPeak = Math.max(heapPeak, Long.parseLong(m.group(1)) << 10);
            }
            assertTrue(heapPeak > 0 && heapPeak <= 64L << 20, w.name() + " heap observed: " + heapPeak);
            if (w.name().equals("document-admission")) {
                assertTrue(heapPeak < 24L << 20, "a document too large to read within the budget is refused before it is parsed: " + heapPeak);
            }
            long reserved = -1;
            long budget = w.args().contains("small.properties") ? 1L << 20 : 32L << 20;
            if (!w.reservation().isEmpty()) {
                java.util.regex.Matcher r = java.util.regex.Pattern.compile(w.reservation() + "=\"(\\d+)\"").matcher(o.out());
                assertTrue(r.find(), w.name() + " states its reservation high water: " + o.out());
                reserved = Long.parseLong(r.group(1));
                assertTrue(reserved > 0 && reserved <= budget, w.name() + " reserved " + reserved + " of " + budget);
            }
            evidence.append("  <workload name=\"").append(w.name()).append("\" command=\"").append(org.ghyura.cf.model.Xml.escape(String.join(" ", w.args())))
                    .append("\" exit=\"").append(o.exit()).append("\" budgetBytes=\"").append(budget).append("\" reservationHighWaterBytes=\"")
                    .append(reserved < 0 ? "" : Long.toString(reserved)).append("\" heapObservedBytes=\"").append(heapPeak).append("\"/>\n");
        }
        Files.writeString(Reactor.root().resolve("fw-acceptance/target/a10-memory-evidence.xml"), evidence.append("</a10-memory>\n"));
    }

    /** Runs the release tool as the release runs it: java tools/ReleaseManifest.java, from the reactor's tools directory. */
    private static String releaseTool(Path work, List<String> args) throws Exception {
        List<String> command = new ArrayList<>(List.of(javaExecutable().toString(), Reactor.root().resolve("tools/ReleaseManifest.java").toString()));
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true);
        builder.environment().remove("JAVA_TOOL_OPTIONS");   // the JVM would announce it on the output the test reads
        Process process = builder.start();
        String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "the release tool finished");
        assertEquals(0, process.exitValue(), out);
        return out;
    }

    /** The release manifest (M10, D68): skip reasons are classified by the legacy rule with this build's reasons; unknown ones block. */
    @Test
    void releaseManifestSkipsAreClassifiedByTheLegacyRuleAndAnUnknownReasonBlocks(@TempDir Path work) throws Exception {
        String out = releaseTool(work, List.of("--classify",
                "bwrap cannot create namespaces here: the isolated-root evidence cannot be produced",
                "strace cannot trace here: the syscall evidence cannot be produced",
                "the legacy checkout is not beside the project: the evidence lines cannot be checked",
                "the SUT checkout is not beside the project",
                "this account can write to a read-only directory (running as root?); the read-only check cannot be enforced",
                "Analyzer jar/AnalyzeKv build not present",
                "live e2e only when explicitly enabled",
                "requires Linux",
                "needs a reachable postgres",
                "UNSUPPORTED_PLATFORM: declared by the test",
                "flaky on this machine",
                " "));
        assertEquals(List.of("MISSING_AUTHORIZED_BACKEND", "MISSING_AUTHORIZED_BACKEND", "MISSING_AUTHORIZED_BACKEND", "MISSING_AUTHORIZED_BACKEND",
                "UNSUPPORTED_PLATFORM", "EXPECTED_OPTIONAL", "EXPECTED_OPTIONAL", "UNSUPPORTED_PLATFORM", "MISSING_AUTHORIZED_BACKEND",
                "UNSUPPORTED_PLATFORM", "BLOCKING_UNEXPECTED", "BLOCKING_UNEXPECTED"), out.lines().toList());
    }

    /** The release manifest of this tree: its listing hash, inputs, the jar's own capability counts, tests, skips, residuals and SBOM. */
    @Test
    void releaseManifestOfThisTreeStatesItsSourceInputsTestsAndResiduals(@TempDir Path work) throws Exception {
        Path manifest = work.resolve("manifest.xml");
        Path sbom = work.resolve("sbom.xml");
        String out = releaseTool(work, List.of("--root", Reactor.root().toString(), "--out", manifest.toString(), "--sbom", sbom.toString(), "--maven", "test"));
        assertTrue(out.startsWith("release manifest "), out);
        Element root = parse(Files.readString(manifest));
        assertEquals("cf.release-manifest/v1", root.getAttribute("schema"));
        assertTrue(List.of("READY", "BLOCKED", "FAILED").contains(root.getAttribute("status")), root.getAttribute("status"));
        Element source = (Element) root.getElementsByTagName("source").item(0);
        assertTrue(source.getAttribute("treeSha256").matches("[0-9a-f]{64}") && Integer.parseInt(source.getAttribute("files")) > 500);
        assertEquals("none", source.getAttribute("versionControl"), "this tree has no version control; its listing hash stands in");
        assertEquals(6, root.getElementsByTagName("input").getLength());
        assertEquals("true", ((Element) root.getElementsByTagName("capabilities").item(0)).getAttribute("observed"), "the jar reports its capabilities");
        Element tests = (Element) root.getElementsByTagName("tests").item(0);
        assertTrue(Integer.parseInt(tests.getAttribute("tests")) > 0, "the reports of this build are read");
        Element skips = (Element) root.getElementsByTagName("skips").item(0);
        assertEquals(skips.getAttribute("total"), tests.getAttribute("skipped"), "every skipped test is classified");
        assertEquals(1, root.getElementsByTagName("residuals").getLength(), "the residuals are stated, as a list");
        assertEquals(0, root.getElementsByTagName("residual").getLength(),
                "the project terms travel in the jar (OPEN-08) and every bundled component has its license files: nothing is residual");
        String bom = Files.readString(sbom);
        assertTrue(bom.contains("<bom xmlns=\"http://cyclonedx.org/schema/bom/1.5\"") && bom.contains("<license><name>LicenseRef-BUSL-1.1</name></license>")
                && bom.contains("<purl>pkg:maven/org.apache.poi/poi@5.5.1</purl>"), bom);
    }

    @Test
    void releaseManifestCannotBeReadyWithoutEverySuiteAndTheDistribution(@TempDir Path work) throws Exception {
        Path root = Files.createDirectories(work.resolve("reactor"));
        Files.writeString(root.resolve("pom.xml"), "<project><version>test</version><modules><module>sample</module></modules></project>");
        Path module = Files.createDirectories(root.resolve("sample"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        Path tests = Files.createDirectories(module.resolve("src/test/java/example"));
        Files.writeString(tests.resolve("RequiredTest.java"), "package example; class RequiredTest { @org.junit.jupiter.api.Test void required() {} }");
        for (String input : List.of("fw-app/target/fw-app.jar", "fw-app/src/main/resources/org/ghyura/cf/app/cli-contract.xml",
                "fw-model/src/main/resources/org/ghyura/cf/model/error-taxonomy.xml", "fw-config/src/main/resources/org/ghyura/cf/config/compatibility-deltas.xml",
                "fw-acceptance/src/test/resources/feature-mapping.xml", "fw-app/src/main/resources/META-INF/cf-third-party.xml")) {
            Path target = root.resolve(input);
            Files.createDirectories(target.getParent());
            Files.copy(Reactor.root().resolve(input), target);
        }
        Path report = Files.createDirectories(module.resolve("target/surefire-reports")).resolve("TEST-example.RequiredTest.xml");
        Path manifest = work.resolve("manifest.xml"), sbom = work.resolve("sbom.xml");
        List<String> args = List.of("--root", root.toString(), "--out", manifest.toString(), "--sbom", sbom.toString());
        releaseTool(work, args);
        assertEquals("BLOCKED", parse(Files.readString(manifest)).getAttribute("status"), "missing suite cannot pass");
        assertTrue(Files.readString(manifest).contains("Missing test report:"));
        Files.writeString(report, "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"example.RequiredTest\" name=\"required\"/></testsuite>");
        releaseTool(work, args);
        assertEquals("READY", parse(Files.readString(manifest)).getAttribute("status"), "complete fixture evidence can pass");
        Files.writeString(report, "<testsuite tests=\"0\" failures=\"0\" errors=\"0\" skipped=\"0\"/>");
        releaseTool(work, args);
        assertEquals("BLOCKED", parse(Files.readString(manifest)).getAttribute("status"), "empty suite cannot pass");
        Files.writeString(report, "<testsuite tests=\"1\" failures=\"0\" errors=\"0\" skipped=\"0\"><testcase classname=\"example.RequiredTest\" name=\"required\"/></testsuite>");
        Files.delete(root.resolve("fw-app/target/fw-app.jar"));
        releaseTool(work, args);
        assertEquals("BLOCKED", parse(Files.readString(manifest)).getAttribute("status"), "missing distribution cannot pass");
        assertTrue(Files.readString(manifest).contains("Missing required input: fw-app/target/fw-app.jar"));
    }

    @Test
    void coverageImportsRunThroughTheJarWithoutToolsAndRefuseHugeReports(@TempDir Path work) throws Exception {
        Files.writeString(work.resolve("results.kv"), "candidate_id=a FW_VAR=0\n");
        String[] formats = {"jacoco", "coverage-py", "gcov"};
        String[] reports = {"<counter type=\"LINE\" missed=\"1\" covered=\"9\"/>",
                "{\"totals\":{\"percent_covered\":90}}", "Lines executed:90% of 10"};
        for (int i = 0; i < formats.length; i++) {
            Files.writeString(work.resolve("coverage.data"), reports[i]);
            Outcome result = launch(work, work, List.of("analyze", "--results", "results.kv", "--coverage-format", formats[i],
                    "--coverage-report", "coverage.data", "--format", "xml"), "C", "-Xmx64m");
            assertEquals(0, result.exit(), result.err());
            assertTrue(result.out().contains("<metric key=\"coverage_line\" value=\"0.9\"/>"), result.out());
            assertTrue(result.out().contains("status=\"IMPORTED\""), result.out());
        }
        try (var file = java.nio.channels.FileChannel.open(work.resolve("coverage.data"), java.nio.file.StandardOpenOption.WRITE)) {
            file.position(256L << 20);
            file.write(java.nio.ByteBuffer.wrap(new byte[]{0}));
        }
        Outcome refused = launch(work, work, List.of("analyze", "--results", "results.kv", "--coverage-format", "gcov",
                "--coverage-report", "coverage.data"), "C", "-Xmx64m");
        assertTrue(refused.exit() != 0, refused.out());
        assertTrue(refused.err().contains("RESOURCE_LIMIT"), refused.err());
        assertFalse(refused.err().contains("OutOfMemoryError"), refused.err());
    }

    @Test
    void checkpointMemoryIsBoundedThroughTheDistribution(@TempDir Path work) throws Exception {
        Path application = Files.copy(jar(), work.resolve("fw-app.jar"));
        String resource = "org/ghyura/cf/acceptance/CheckpointMemoryProbe.class";
        Path probe = work.resolve("probe").resolve(resource);
        Files.createDirectories(probe.getParent());
        try (var input = CheckpointMemoryProbe.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            Files.copy(input, probe);
        }
        Path output = work.resolve("memory.xml"), error = work.resolve("memory.log");
        ProcessBuilder builder = new ProcessBuilder(javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true", "-cp",
                application + java.io.File.pathSeparator + work.resolve("probe"), CheckpointMemoryProbe.class.getName(), work.toString());
        builder.directory(work.toFile());
        builder.environment().clear();
        builder.redirectOutput(output.toFile());
        builder.redirectError(error.toFile());
        Process process = builder.start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("bounded checkpoint probe timed out");
        }
        assertEquals(0, process.exitValue(), Files.readString(error));
        Element proof = parse(Files.readString(output));
        assertEquals("verified", proof.getAttribute("status"));
        assertTrue(Long.parseLong(proof.getAttribute("heapBytes")) <= (64L << 20));
        Outcome refused = launch(work, work, List.of("resume", "--checkpoint", work.resolve("oversized.xml").toString(),
                "--format", "xml"), "C", "-Xmx64m");
        assertTrue(refused.exit() != 0);
        assertTrue((refused.out() + refused.err()).contains("RESOURCE_LIMIT"), refused.out() + refused.err());
        assertFalse((refused.out() + refused.err()).contains("OutOfMemoryError"));
    }

    /**
     * Third-party artifacts the distribution may contain (group:artifact), the result of the D10 audit of the
     * resolved tree: pure-Java POI (OOXML workbooks) with its commons libraries, and Jackson's streaming core.
     * None is a database, network, native or process-launching artifact. Grows only with a new audit; curvesapi left it
     * when it was excluded with POI's Visio support (D67).
     */
    private static final Set<String> APPROVED_THIRD_PARTY = Set.of(
            "com.formdev:flatlaf",
            "com.fasterxml.jackson.core:jackson-core",
            "org.apache.poi:poi", "org.apache.poi:poi-ooxml", "org.apache.poi:poi-ooxml-lite",
            "org.apache.xmlbeans:xmlbeans", "org.apache.commons:commons-compress", "org.apache.commons:commons-lang3",
            "org.apache.commons:commons-math3", "org.apache.commons:commons-collections4", "commons-io:commons-io",
            "commons-codec:commons-codec", "com.zaxxer:SparseBitSet", "org.apache.logging.log4j:log4j-api");

    private record Outcome(int exit, String out, String err) {
    }

    private static Path jar() {
        String path = System.getProperty("cf.app.jar");
        assertNotNull(path, "system property cf.app.jar is not set; run through Maven verify");
        Path jar = Path.of(path).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(jar), "the application jar was not built: " + jar);
        return jar;
    }

    @Test
    void theJarIsOneSelfContainedRunnableArtifact() throws IOException {
        try (JarFile jar = new JarFile(jar().toFile())) {
            assertEquals("org.ghyura.cf.app.Main", jar.getManifest().getMainAttributes().getValue("Main-Class"));
            for (String entry : List.of("org/ghyura/cf/app/Main.class", "org/ghyura/cf/app/Application.class",
                    "org/ghyura/cf/config/ConfigResolver.class", "org/ghyura/cf/model/key/KeyRow.class",
                    "org/ghyura/cf/config/runtime-defaults.properties", "org/ghyura/cf/config/compatibility.xml",
                    "org/ghyura/cf/config/legacy-settings.xml", "org/ghyura/cf/config/compatibility-deltas.xml",
                    "org/ghyura/cf/model/error-taxonomy.xml", "org/ghyura/cf/app/cli-contract.xml",
                    "org/ghyura/cf/app/version.properties")) {
                assertNotNull(jar.getJarEntry(entry), "missing from the jar: " + entry);
            }
            Properties version = new Properties();
            try (var in = jar.getInputStream(jar.getJarEntry("org/ghyura/cf/app/version.properties"))) {
                version.load(in);
            }
            assertEquals("0.1.0-SNAPSHOT", version.getProperty("app.version"), "resource filtering did not run");
        }
    }

    @Test
    void theJarShipsNoNativeCodeAndOnlyApprovedArtifacts() throws Exception {
        Path jarFile = jar();
        Set<String> artifacts = new LinkedHashSet<>();
        int entries = 0;
        try (JarFile jar = new JarFile(jarFile.toFile())) {
            for (Enumeration<JarEntry> e = jar.entries(); e.hasMoreElements(); ) {
                String name = e.nextElement().getName();
                entries++;
                String lower = name.toLowerCase(java.util.Locale.ROOT);
                for (String suffix : List.of(".so", ".dll", ".dylib", ".jnilib", ".exe")) {
                    assertFalse(lower.endsWith(suffix), "native code in the jar: " + name);
                }
                assertFalse(lower.contains(".so."), "native code in the jar: " + name);
                if (name.startsWith("META-INF/maven/") && name.endsWith("/pom.properties")) {
                    String[] parts = name.split("/");
                    artifacts.add(parts[2] + ":" + parts[3]);
                }
            }
        }
        assertTrue(entries >= 60, "suspiciously small jar: " + entries + " entries");
        assertTrue(artifacts.containsAll(Set.of("org.ghyura.cf:fw-model", "org.ghyura.cf:fw-config", "org.ghyura.cf:fw-app")),
                "the module artifacts are missing from the shaded jar: " + artifacts);
        for (String artifact : artifacts) {
            int colon = artifact.indexOf(':');
            String group = artifact.substring(0, colon);
            assertFalse(BannedDependencies.isBanned(group, artifact.substring(colon + 1)),
                    "banned artifact in the distribution: " + artifact);
            assertTrue(group.equals("org.ghyura.cf") || APPROVED_THIRD_PARTY.contains(artifact),
                    "unapproved third-party artifact in the distribution: " + artifact);
        }
        writeInventory(jarFile, entries, artifacts);
    }

    @Test
    void capabilitiesAnswersWithNoPythonNoDatabaseNoToolsAndAReadOnlyWorkingDirectory(@TempDir Path work) throws Exception {
        Path cwd = readOnlyDirectory(work);
        try {
            Path javaBin = javaExecutable().getParent();
            try (Stream<Path> tools = Files.list(javaBin)) {
                assertTrue(tools.noneMatch(p -> p.getFileName().toString().startsWith("python")),
                        "the only PATH entry must not provide Python");
            }
            Outcome outcome = launch(work, cwd, List.of("capabilities", "--format", "xml"), "C", "-Xmx64m");
            assertEquals(0, outcome.exit(), outcome.err());
            assertEquals("", outcome.err());
            Element root = parse(outcome.out());
            assertEquals("capabilities", root.getTagName());
            assertEquals(18, root.getElementsByTagName("capability").getLength());
            // C02 since M03, C06 (the executor's dynamic Java) since M05, C11 (numerical optimisation in Java) since M06, C10 (the
            // analyzer, with optional line instrumentation) since M09, C04 (with decomposition and the stage benchmark) since M10, C05 (the
            // desktop, its library included) since 0110, C03 (with the candidate export) since 0111
            assertEquals("10", root.getAttribute("implemented"));
            assertTrue(outcome.out().contains("<capability id=\"C01\" status=\"IMPLEMENTED\""), outcome.out());
            assertTrue(outcome.out().contains("<capability id=\"C05\" status=\"IMPLEMENTED\""), outcome.out());
            assertFalse(outcome.out().contains("�"), "output was not valid UTF-8");
        } finally {
            makeWritable(cwd);
        }
        try (Stream<Path> left = Files.list(cwd)) {
            assertEquals(0, left.count(), "the application wrote into its working directory");
        }
    }

    @Test
    void validationRefusalsAndUsageErrorsAlsoWorkInTheRestrictedEnvironment(@TempDir Path work) throws Exception {
        Path cwd = readOnlyDirectory(work);
        try {
            Outcome validate = launch(work, cwd, List.of("validate"), "C", "-Xmx64m");
            assertEquals(0, validate.exit(), validate.err());
            assertTrue(validate.out().contains("cf.execution.workers"), validate.out());

            Outcome missing = launch(work, cwd, List.of("run", "--scenario", "x.json"), "C", "-Xmx64m");
            assertEquals(3, missing.exit(), "run exists since M04; a missing scenario fails validation before anything runs");
            assertTrue(missing.err().contains("x.json"), missing.err());

            Outcome refused = launch(work, cwd, List.of("desktop"), "C", "-Xmx64m");
            assertEquals(4, refused.exit());
            assertTrue(refused.err().contains("UNSUPPORTED_CAPABILITY"), refused.err());

            Outcome usage = launch(work, cwd, List.of("bogus"), "C", "-Xmx64m");
            assertEquals(2, usage.exit());
            assertTrue(usage.err().contains("USAGE_UNKNOWN_COMMAND"), usage.err());

            Outcome version = launch(work, cwd, List.of("--version"), "C", "-Xmx64m");
            assertEquals(0, version.exit());
            assertTrue(version.out().startsWith("fw-app 0.1.0-SNAPSHOT"), version.out());
        } finally {
            makeWritable(cwd);
        }
    }

    @Test
    void startupLoadsNoWindowingToolkitAndNoNetworkingClass(@TempDir Path work) throws Exception {
        Path cwd = Files.createDirectory(work.resolve("cwd"));
        Path classLog = work.resolve("classes.log");
        Outcome outcome = launch(work, cwd, List.of("capabilities"), "C", "-Xmx64m",
                "-Xlog:class+load=info:file=" + classLog);
        assertEquals(0, outcome.exit(), outcome.err());
        List<String> loaded = Files.readAllLines(classLog, StandardCharsets.UTF_8);
        assertTrue(loaded.size() > 200, "class-load log looks empty: " + loaded.size());
        List<String> windowing = loaded.stream()
                .filter(line -> line.contains(" java.awt.") || line.contains(" javax.swing.") || line.contains(" sun.awt."))
                .toList();
        assertEquals(List.of(), windowing, "a headless command must not initialise AWT or Swing");
        // the jar: URL machinery (java.net.URL, JarURLConnection) is class loading, and the channel classes the JDK
        // loads for its local capability probe (sun.nio.ch.SocketChannelImpl among them) open no connection; what
        // a connection, a listener or an HTTP request needs is a socket address, a socket or an HTTP handler
        assertEquals(List.of(), connecting(loaded), "the commands of this build must not load a class that connects or listens");
    }

    @Test
    void anIsolatedRootWithNoNetworkNoPythonAndAReadOnlyWorkingDirectoryStillRunsTheApplication(@TempDir Path work)
            throws Exception {
        Assumptions.assumeTrue(commandWorks(List.of("bwrap", "--unshare-all", "--ro-bind", "/", "/", "true")),
                "bwrap cannot create namespaces here: the isolated-root evidence cannot be produced");
        Path cwd = Files.createDirectory(work.resolve("empty"));
        Path testClasses = Path.of(IsolationProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());

        // 1. the control: what does this root actually deny?
        Outcome probe = sandboxed(work, cwd, Map.of("/app/test-classes", testClasses),
                List.of("-cp", "/app/test-classes", IsolationProbe.class.getName(), "/work"));
        assertEquals(0, probe.exit(), probe.err());
        Map<String, String> facts = new java.util.LinkedHashMap<>();
        for (String line : probe.out().split("\n")) {
            int eq = line.indexOf('=');
            facts.put(line.substring(0, eq), line.substring(eq + 1));
        }
        assertEquals("false", facts.get("usr.bin"), "the isolated root must not contain /usr/bin: " + facts);
        assertEquals("false", facts.get("bin"), "the isolated root must not contain /bin: " + facts);
        assertEquals("false", facts.get("home"), "the isolated root must not contain /home (pyenv, user installs): " + facts);
        assertEquals("lo", facts.get("interfaces"), "only a loopback interface may exist: " + facts);
        assertEquals("SocketException", facts.get("connect"), "an outbound connection must fail, not succeed: " + facts);
        assertEquals("FileSystemException", facts.get("write"), "the working directory must be read-only: " + facts);

        // 2. the application, in exactly that root
        Map<String, Path> app = Map.of("/app/fw-app.jar", jar());
        Outcome capabilities = sandboxed(work, cwd, app, List.of("-jar", "/app/fw-app.jar", "capabilities", "--format", "xml"));
        assertEquals(0, capabilities.exit(), capabilities.err());
        assertEquals("", capabilities.err());
        assertEquals(18, parse(capabilities.out()).getElementsByTagName("capability").getLength());

        Outcome validate = sandboxed(work, cwd, app, List.of("-jar", "/app/fw-app.jar", "validate"));
        assertEquals(0, validate.exit(), validate.err());
        assertTrue(validate.out().contains("cf.memory.budget.bytes"), validate.out());

        Outcome missing = sandboxed(work, cwd, app, List.of("-jar", "/app/fw-app.jar", "run", "--scenario", "x.json"));
        assertEquals(3, missing.exit(), "run exists since M04; a missing scenario fails validation before anything runs");
        assertTrue(missing.err().contains("x.json"), missing.err());

        Outcome refused = sandboxed(work, cwd, app, List.of("-jar", "/app/fw-app.jar", "desktop"));
        assertEquals(4, refused.exit());
        assertTrue(refused.err().contains("UNSUPPORTED_CAPABILITY"), refused.err());
    }

    @Test
    void syscallObservationShowsNoSubprocessAndNoNetworkUse(@TempDir Path work) throws Exception {
        Assumptions.assumeTrue(commandWorks(List.of("strace", "-o", "/dev/null", "true")),
                "strace cannot trace here: the syscall evidence cannot be produced");
        assertNoSubprocessAndNoNetworkUse(observe(work, "capabilities", List.of("capabilities")));
    }

    @Test
    void validatingAScenarioInEveryFormatAlsoStartsNoSubprocessAndUsesNoNetwork(@TempDir Path work) throws Exception {
        Assumptions.assumeTrue(commandWorks(List.of("strace", "-o", "/dev/null", "true")),
                "strace cannot trace here: the syscall evidence cannot be produced");
        for (Map.Entry<InputFormat, Path> scenario : writeExampleScenarios(work).entrySet()) {
            assertNoSubprocessAndNoNetworkUse(observe(work, "validate-" + scenario.getKey().token(),
                    List.of("validate", "--scenario", scenario.getValue().toString())));
        }
    }

    /**
     * Generation (work package M02) runs inside the same jar under the same rule: planning a scenario in every format
     * starts no subprocess and uses no network, and the plan holds the joined table the example scenario describes
     * (3 COST rows times 3 LAT rows, wrapped by HEAD and TAIL).
     */
    @Test
    void planningAScenarioThroughTheJarGeneratesItsTablesWithNoSubprocessAndNoNetworkUse(@TempDir Path work) throws Exception {
        Assumptions.assumeTrue(commandWorks(List.of("strace", "-o", "/dev/null", "true")),
                "strace cannot trace here: the syscall evidence cannot be produced");
        Map<InputFormat, Path> scenarios = writeExampleScenarios(work);
        for (Map.Entry<InputFormat, Path> scenario : scenarios.entrySet()) {
            assertNoSubprocessAndNoNetworkUse(observe(work, "plan-" + scenario.getKey().token(),
                    List.of("plan", "--scenario", scenario.getValue().toString())));
        }
        Outcome outcome = launch(work, work, List.of("plan", "--scenario", scenarios.get(InputFormat.JSON).toString(), "--format", "xml"),
                "C", "-Xmx64m");
        assertEquals(0, outcome.exit(), outcome.err());
        Element generation = (Element) parse(outcome.out()).getElementsByTagName("generation").item(0);
        assertEquals("PARALLEL", generation.getAttribute("library"));
        NodeList tables = generation.getElementsByTagName("table");
        boolean found = false;
        for (int i = 0; i < tables.getLength(); i++) {
            Element table = (Element) tables.item(i);
            if (table.getAttribute("sheet").equals("ROW")) {
                assertEquals("9", table.getAttribute("rows"));
                assertEquals("4", table.getAttribute("uniformLength"));
                found = true;
            }
        }
        assertTrue(found, outcome.out());
    }

    /**
     * Gate A15 through the distribution (work package M04): the tutor lesson runs from the packaged jar as a typed plugin
     * found with ServiceLoader in a separate fixtures jar on cf.execution.classpath, and as generated Java compiled inside
     * the jar's JVM; both give the exact distribution, with no subprocess and no network use. The fixtures never enter the
     * application jar.
     */
    @Test
    void theTutorRunsThroughTheJarAsATypedPluginAndAsGeneratedJava(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing"));
        assertTrue(Files.isRegularFile(fixtures), "the fixtures jar " + fixtures + " is built before the integration tests");
        for (String name : List.of("tutor-typed.json", "tutor-typed.constraints.json", "tutor-generated.json",
                "tutor-generated.constraints.json")) {
            try (java.io.InputStream in = PackagedApplicationIT.class.getResourceAsStream("/org/ghyura/cf/fixtures/tutor/" + name)) {
                assertNotNull(in, name);
                Files.write(work.resolve(name), in.readAllBytes());
            }
        }
        List<String> typed = List.of("run", "--scenario", work.resolve("tutor-typed.json").toString(), "--sut", "tutor", "--set",
                "cf.execution.classpath=" + fixtures);
        Outcome t = launch(work, work, typed, "C", "-Xmx256m");
        assertEquals(0, t.exit(), t.err());
        assertTrue(t.out().contains("generated 192, excluded 48, left out by covering selection 0, executable 144"), t.out());
        assertTrue(t.out().contains("PASS 18, DOMAIN_FAIL 126, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), t.out());
        assertTrue(t.out().contains("0 PASS: 18; 20 ORDER_VIOLATION: 120; 30 DOUBLE_CHARGE: 2; 31 DISPATCH_LOST: 4"), t.out());
        List<String> generated = List.of("run", "--scenario", work.resolve("tutor-generated.json").toString(), "--set",
                "cf.execution.verdict=FW_CUSTOM_VAR");
        Outcome g = launch(work, work, generated, "C", "-Xmx256m");
        assertEquals(0, g.exit(), g.err());
        assertTrue(g.out().contains("0 PASS: 18; 20 ORDER_VIOLATION: 120; 30 DOUBLE_CHARGE: 2; 31 DISPATCH_LOST: 4"), g.out());
        assertTrue(g.out().contains("144 sources compiled in memory"), g.out());
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jar().toFile())) {
            assertTrue(jar.stream().noneMatch(e -> e.getName().startsWith("org/ghyura/cf/fixtures/")),
                    "test fixtures must not enter the application jar");
        }
        if (commandWorks(List.of("strace", "-o", "/dev/null", "true"))) {
            assertNoSubprocessAndNoNetworkUse(observe(work, "run-generated", generated));
            assertNoSubprocessAndNoNetworkUse(observe(work, "run-typed", typed));
        }
    }

    /** A16 first batch: the four domain plugins load from the fixtures jar and execute without a service or subprocess. */
    @Test
    void theFourBusinessPortsRunThroughTheDistribution(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing"));
        assertTrue(Files.isRegularFile(fixtures));
        Map<String, String> plugins = Map.of("pricing", "bundle-pricing", "security", "bundle-secure-pipeline",
                "billing", "billing-engine", "fulfilment", "order-fulfilment");
        Map<String, String> distributions = Map.of("pricing", "0 PASS: 93; 1 PRICE_MISMATCH: 27; 2 DOUBLE_CHARGE: 24",
                "security", "0 PASS: 57; 1 REDACTION_LEAK: 3; 2 STALE_KEY: 12; 3 CACHE_POISON: 24",
                "billing", "1 INVARIANT_VIOLATION: 1", "fulfilment", "1 INVARIANT_VIOLATION: 1");
        for (String family : List.of("pricing", "security", "billing", "fulfilment")) {
            Path scenario = work.resolve(family + ".json");
            try (java.io.InputStream in = PackagedApplicationIT.class.getResourceAsStream("/org/ghyura/cf/fixtures/business/" + family + ".json")) {
                assertNotNull(in, family); Files.write(scenario, in.readAllBytes());
            }
            List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", plugins.get(family), "--set",
                    "cf.execution.classpath=" + fixtures);
            Outcome result = launch(work, work, args, "C", "-Xmx256m");
            assertEquals(0, result.exit(), result.err());
            assertTrue(result.out().contains(distributions.get(family)), result.out());
            assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
            if (commandWorks(List.of("strace", "-o", "/dev/null", "true")))
                assertNoSubprocessAndNoNetworkUse(observe(work, "run-business-" + family, args));
        }
    }

    @Test
    void catalogAndPubRunFromTheFixtureAddon(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing"));
        assertTrue(Files.isRegularFile(fixtures));
        for (String family : List.of("telemetry", "pub")) {
            Path scenario = work.resolve(family + ".json");
            try (java.io.InputStream in = PackagedApplicationIT.class.getResourceAsStream("/org/ghyura/cf/fixtures/catalogpub/" + family + ".json")) {
                assertNotNull(in); Files.write(scenario, in.readAllBytes());
            }
            List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", family.equals("telemetry") ? "telemetry-catalog" : "tester-pub",
                    "--set", "cf.execution.classpath=" + fixtures);
            Outcome result = launch(work, work, args, "C", "-Xmx256m");
            assertEquals(0, result.exit(), result.err());
            assertTrue(result.out().contains(family.equals("telemetry") ? "0 VALID: 4; 1 INVALID_QUERY: 4" : "0 ADEQUATE: 19; 1 DEFECT: 13"), result.out());
            assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
            if (commandWorks(List.of("strace", "-o", "/dev/null", "true")))
                assertNoSubprocessAndNoNetworkUse(observe(work, "run-" + family, args));
        }
    }

    @Test
    void theCompleteReductionTreeDemoRunsFromThePackagedAddonWithoutPython(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing"));
        assertTrue(Files.isRegularFile(fixtures));
        Path scenario = work.resolve("spec.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d7/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d7-reduction-trees",
                "--set", "cf.execution.classpath=" + fixtures);
        Outcome result = launch(work, work, args, "C", "-Xmx256m");
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("0 PASS: 156; 2 DOMAIN_FAIL: 12"), result.out());
        assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d7", args));
    }

    @Test
    void theCompleteStreamBoundaryDemoRunsFromThePackagedAddonWithoutPython(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing"));
        assertTrue(Files.isRegularFile(fixtures));
        Path scenario = work.resolve("spec.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d2/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d2-stream-boundaries",
                "--set", "cf.execution.classpath=" + fixtures);
        Outcome result = launch(work, work, args, "C", "-Xmx256m");
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("0 PASS: 332; 2 DOMAIN_FAIL: 148"), result.out());
        assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d2", args));
    }

    @Test
    void theCompletePaginationDemoPreservesBothDefectsInThePackagedAddon(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing"));
        assertTrue(Files.isRegularFile(fixtures));
        Path scenario = work.resolve("spec.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d8/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d8-ties-pagination",
                "--set", "cf.execution.classpath=" + fixtures);
        Outcome result = launch(work, work, args, "C", "-Xmx256m");
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("0 PASS: 330; 2 DOMAIN_FAIL: 120"), result.out());
        assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d8", args));
    }

    @Test
    void theCoalitionDemoAndObservedAllocationRunWithoutPythonFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures));
        Path scenario = work.resolve("spec.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d11/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d11-coalition-value",
                "--set", "cf.execution.classpath=" + fixtures, "--set", "cf.generation.distinctTables=true");
        Outcome result = launch(work, work, args, "C", "-Xmx256m");
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("0 PASS: 64"), result.out());
        assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d11", args));

        Path trace = work.resolve("allocation-strace.log"), output = work.resolve("allocation.xml"), console = work.resolve("allocation-console.log");
        ProcessBuilder builder = new ProcessBuilder("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", trace.toString(), javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true",
                "--class-path", jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures,
                Reactor.root().resolve("tools/CoalitionAllocationReport.java").toString(),
                "--scenario", scenario.toString(), "--out", output.toString(), "--workers", "4");
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString());
        builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "allocation source launcher did not finish");
        assertEquals(0, process.exitValue(), Files.readString(console));
        assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var allocation = parse(Files.readString(output));
        assertEquals("observed coalition values", allocation.getAttribute("source"));
        assertEquals("32", allocation.getAttribute("grand-value"));
        assertEquals(64, allocation.getElementsByTagName("observation").getLength());
        assertEquals(192, allocation.getElementsByTagName("marginal").getLength());
        assertEquals(720, allocation.getElementsByTagName("permutation").getLength());
        assertEquals(64, allocation.getElementsByTagName("dividend").getLength());
        var players = allocation.getElementsByTagName("player");
        assertEquals(6, players.getLength());
        List<String> fractions = new ArrayList<>();
        for (int i = 0; i < players.getLength(); i++) {
            var player = (org.w3c.dom.Element) players.item(i);
            fractions.add(player.getAttribute("id") + "=" + player.getAttribute("n") + "/" + player.getAttribute("d"));
        }
        assertEquals(List.of("A=29/3", "B=29/3", "C=13/3", "D=13/3", "E=4/1", "F=0/1"), fractions);
        var checks = allocation.getElementsByTagName("check"); assertEquals(8, checks.getLength());
        for (int i = 0; i < checks.getLength(); i++) assertEquals("true", ((org.w3c.dom.Element) checks.item(i)).getAttribute("passed"));
    }

    @Test
    void bothConfigurationRuleScenariosAndTheirObservedPairwiseProofRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures));
        Path main = null;
        for (String phase : List.of("main", "controls")) {
            Path scenario = work.resolve(phase + ".toml");
            try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d4/" + phase + "/spec.toml")) {
                assertNotNull(in); Files.write(scenario, in.readAllBytes());
            }
            if (phase.equals("main")) main = scenario;
            List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d4-configuration-rules",
                    "--set", "cf.execution.classpath=" + fixtures, "--set", "cf.generation.distinctTables=true", "--set", "cf.assembly.distinct=true");
            Outcome result = launch(work, work, args, "C", "-Xmx64m");
            assertEquals(0, result.exit(), result.err());
            assertTrue(result.out().contains(phase.equals("main") ? "0 PASS: 48; 2 DOMAIN_FAIL: 18" : "0 PASS: 17; 2 DOMAIN_FAIL: 1"), result.out());
            assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
            assertNoSubprocessAndNoNetworkUse(observe(work, "run-d4-" + phase, args));
        }
        Path trace = work.resolve("d4-proof-strace.log"), output = work.resolve("proof.xml"), console = work.resolve("proof-console.log");
        ProcessBuilder builder = new ProcessBuilder("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", trace.toString(), javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true",
                "--class-path", jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures,
                Reactor.root().resolve("tools/ConfigurationRulesReport.java").toString(),
                "--scenario", main.toString(), "--out", output.toString(), "--workers", "4");
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString());
        builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "D4 proof source launcher did not finish");
        assertEquals(0, process.exitValue(), Files.readString(console));
        assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof = parse(Files.readString(output));
        assertEquals("66", proof.getAttribute("observations")); assertEquals("22", proof.getAttribute("configurations"));
        assertEquals("60", proof.getAttribute("feasible-pairs")); assertEquals("60", proof.getAttribute("covered-pairs"));
        assertEquals("9", proof.getAttribute("selected")); assertEquals(9, proof.getElementsByTagName("selected").getLength());
        assertEquals(66, proof.getElementsByTagName("observation").getLength());
        var pairs = proof.getElementsByTagName("obligation"); assertEquals(60, pairs.getLength());
        for (int i = 0; i < pairs.getLength(); i++) assertEquals("true", ((org.w3c.dom.Element)pairs.item(i)).getAttribute("covered"));
        var wrong = (org.w3c.dom.Element)proof.getElementsByTagName("wrong-sieve").item(0);
        assertEquals("54", wrong.getAttribute("removed-legal")); assertEquals("12", wrong.getAttribute("remaining"));
        assertEquals("0", wrong.getAttribute("remaining-failures")); assertEquals("18", wrong.getAttribute("hidden-failures"));
    }

    @Test
    void theOriginalDependencyDagScenarioAndCompleteTopologyProofRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures));
        Path scenario = work.resolve("spec.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d8b/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d8b-dependency-dags",
                "--set", "cf.execution.classpath=" + fixtures);
        Outcome result = launch(work, work, args, "C", "-Xmx64m");
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("0 PASS: 604; 2 DOMAIN_FAIL: 164"), result.out());
        assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d8b", args));
        Path trace = work.resolve("d8b-proof-strace.log"), output = work.resolve("proof.xml"), console = work.resolve("proof-console.log");
        ProcessBuilder builder = new ProcessBuilder("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", trace.toString(), javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true",
                "--class-path", jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures,
                Reactor.root().resolve("tools/DependencyDagsReport.java").toString(),
                "--scenario", scenario.toString(), "--out", output.toString(), "--workers", "4");
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString());
        builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "D8b proof source launcher did not finish");
        assertEquals(0, process.exitValue(), Files.readString(console));
        assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof = parse(Files.readString(output));
        assertEquals("768", proof.getAttribute("observations")); assertEquals("64", proof.getAttribute("graphs"));
        assertEquals("315", proof.getAttribute("graph-order-pairs")); assertEquals("31", proof.getAttribute("eligible-additions"));
        assertEquals(768, proof.getElementsByTagName("observation").getLength());
        assertEquals(64, proof.getElementsByTagName("graph").getLength());
        var additions = proof.getElementsByTagName("addition"); assertEquals(31, additions.getLength());
        for (int i = 0; i < additions.getLength(); i++) {
            var a = (org.w3c.dom.Element)additions.item(i);
            assertEquals("true", a.getAttribute("equal-sets")); assertEquals(a.getAttribute("orders-before"), a.getAttribute("orders-after"));
            assertEquals(a.getAttribute("minimum-before"), a.getAttribute("minimum-after"));
        }
    }

    @Test
    void originalRobustPortfoliosAndFullObservedRankingsRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures));
        Path scenario = work.resolve("spec.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d9/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d9-robust-portfolios",
                "--set", "cf.execution.classpath=" + fixtures, "--set", "cf.generation.distinctTables=true");
        Outcome result = launch(work, work, args, "C", "-Xmx64m");
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("0 PASS: 1024"), result.out());
        assertTrue(result.out().contains("PASS 1024, DOMAIN_FAIL 0"), result.out());
        assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d9", args));
        Path trace = work.resolve("d9-proof-strace.log"), output = work.resolve("proof.xml"), console = work.resolve("proof-console.log");
        ProcessBuilder builder = new ProcessBuilder("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", trace.toString(), javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true",
                "--class-path", jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures,
                Reactor.root().resolve("tools/RobustPortfolioReport.java").toString(),
                "--scenario", scenario.toString(), "--out", output.toString(), "--workers", "4");
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString());
        builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "D9 ranking source launcher did not finish");
        assertEquals(0, process.exitValue(), Files.readString(console));
        assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof = parse(Files.readString(output));
        assertEquals("1024", proof.getAttribute("observations")); assertEquals("64", proof.getAttribute("designs"));
        assertEquals(1024, proof.getElementsByTagName("observation").getLength());
        assertEquals(64, proof.getElementsByTagName("summary").getLength());
        var minimax = (org.w3c.dom.Element)proof.getElementsByTagName("minimax").item(0);
        assertTrue(minimax.getAttribute("ranking").startsWith("110000 110001 "));
        assertEquals("110000 110001", minimax.getAttribute("primary-ties"));
        var steps = proof.getElementsByTagName("sensitivity"); assertEquals(11, steps.getLength());
        List<String> winners = List.of("000000", "010000", "010000", "110000", "110000", "110000", "110000", "110100", "110100", "110100", "110100");
        for (int i = 0; i < steps.getLength(); i++) assertEquals(winners.get(i), ((org.w3c.dom.Element)steps.item(i)).getAttribute("winner"));
    }

    @Test
    void bothOriginalCyclicScenariosAndFullObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures));
        for (String campaign : List.of("words", "classes")) {
            Path scenario = work.resolve(campaign + ".toml");
            try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d12/" + campaign + "/spec.toml")) {
                assertNotNull(in); Files.write(scenario, in.readAllBytes());
            }
            List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d12-cyclic-patterns",
                    "--set", "cf.execution.classpath=" + fixtures);
            Outcome result = launch(work, work, args, "C", "-Xmx64m");
            assertEquals(0, result.exit(), result.err());
            String count = campaign.equals("words") ? "729" : "130";
            assertTrue(result.out().contains("0 PASS: " + count), result.out());
            assertTrue(result.out().contains("PASS " + count + ", DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
            assertNoSubprocessAndNoNetworkUse(observe(work, "run-d12-" + campaign, args));
        }
        Path trace = work.resolve("d12-proof-strace.log"), output = work.resolve("proof.xml"), console = work.resolve("proof-console.log");
        ProcessBuilder builder = new ProcessBuilder("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", trace.toString(), javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true",
                "--class-path", jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures,
                Reactor.root().resolve("tools/CyclicPatternReport.java").toString(),
                "--words", work.resolve("words.toml").toString(), "--classes", work.resolve("classes.toml").toString(), "--out", output.toString(), "--workers", "4");
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString());
        builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "D12 observed source report did not finish");
        assertEquals(0, process.exitValue(), Files.readString(console));
        assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof = parse(Files.readString(output));
        assertEquals("729", proof.getAttribute("word-observations")); assertEquals("130", proof.getAttribute("class-observations"));
        assertEquals(859, proof.getElementsByTagName("observation").getLength()); assertEquals(130, proof.getElementsByTagName("orbit").getLength());
        var partition = (org.w3c.dom.Element)proof.getElementsByTagName("partition").item(0);
        assertEquals("729 3 9 27 9 3", partition.getAttribute("burnside-fixed-counts"));
        assertEquals("54", partition.getAttribute("self-reflection-classes")); assertEquals("38", partition.getAttribute("mirror-pairs"));
        var cross = (org.w3c.dom.Element)proof.getElementsByTagName("cross-campaign").item(0);
        assertEquals("130", cross.getAttribute("representatives-matched")); assertEquals("", cross.getAttribute("mismatches")); assertEquals("true", cross.getAttribute("weighted-histogram-equals-words"));
        var ranking = (org.w3c.dom.Element)proof.getElementsByTagName("ranking").item(0);
        assertEquals("ABCABC", ranking.getAttribute("best-classes")); assertEquals("ABCABC BCABCA CABCAB", ranking.getAttribute("best-labelled-words"));
        assertEquals("1/130", ranking.getAttribute("best-class-mass")); assertEquals("1/243", ranking.getAttribute("best-word-mass"));
        var populations = proof.getElementsByTagName("population"); assertEquals(3, populations.getLength());
        for (int i = 0; i < populations.getLength(); i++) {
            var population = (org.w3c.dom.Element)populations.item(i);
            assertEquals(population.getAttribute("name").equals("classes_uniform") ? "942/65" : "14/1", population.getAttribute("mean"));
        }
    }

    @Test
    void originalDiagnosticSensorsAndCompleteObservedDiagnosisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures)); Path scenario = work.resolve("d10.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d10/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d10-diagnostic-sensors",
                "--set", "cf.execution.classpath=" + fixtures, "--set", "cf.generation.distinctTables=true");
        Outcome result = launch(work, work, args, "C", "-Xmx64m");
        assertEquals(0, result.exit(), result.err()); assertTrue(result.out().contains("0 PASS: 3072"), result.out());
        assertTrue(result.out().contains("PASS 3072, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d10", args));
        Path trace = work.resolve("d10-proof-strace.log"), output = work.resolve("proof.xml"), console = work.resolve("proof-console.log");
        ProcessBuilder builder = new ProcessBuilder("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", trace.toString(), javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true",
                "--class-path", jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures,
                Reactor.root().resolve("tools/DiagnosticSensorReport.java").toString(),
                "--scenario", scenario.toString(), "--out", output.toString(), "--workers", "4");
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString());
        builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "D10 observed source report did not finish");
        assertEquals(0, process.exitValue(), Files.readString(console));
        assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof = parse(Files.readString(output)); assertEquals("3072", proof.getAttribute("observations"));
        assertEquals(3072, proof.getElementsByTagName("observation").getLength());
        assertEquals(7, proof.getElementsByTagName("class").getLength()); assertEquals(256, proof.getElementsByTagName("suite").getLength());
        assertEquals(5376, proof.getElementsByTagName("distance").getLength()); assertEquals(127, proof.getElementsByTagName("belief").getLength()); assertEquals(7, proof.getElementsByTagName("path").getLength());
        var adaptive = (org.w3c.dom.Element)proof.getElementsByTagName("node").item(0); assertEquals("3", adaptive.getAttribute("cost")); assertEquals("0", adaptive.getAttribute("sensor"));
        var winners = proof.getElementsByTagName("winner"); assertEquals(4, winners.getLength());
        List<String> masks = List.of("00000011", "00001111", "00111111", "01111111"), costs = List.of("2", "4", "6", "7"), feasible = List.of("201", "149", "37", "9"); List<Integer> ties = List.of(7, 56, 28, 8);
        for (int i = 0; i < 4; i++) { var w = (org.w3c.dom.Element)winners.item(i); assertEquals(masks.get(i), w.getAttribute("mask")); assertEquals(costs.get(i), w.getAttribute("cost")); assertEquals(feasible.get(i), w.getAttribute("feasible_count")); assertEquals(ties.get(i), w.getAttribute("minimum_cost_masks").split(" ").length); }
        assertEquals(49, ((org.w3c.dom.Element)proof.getElementsByTagName("erasure").item(0)).getElementsByTagName("control").getLength()); assertEquals(56, ((org.w3c.dom.Element)proof.getElementsByTagName("error").item(0)).getElementsByTagName("control").getLength());
        assertEquals("0", proof.getAttribute("unquotiented_label_min_distance")); var examples = proof.getElementsByTagName("identify"); assertEquals(12, examples.getLength()); assertEquals("F01 F02", ((org.w3c.dom.Element)examples.item(0)).getAttribute("members"));
        var healthy = (org.w3c.dom.Element)proof.getElementsByTagName("healthy-reference").item(0); assertEquals("H0", healthy.getAttribute("members")); assertTrue(healthy.getAttribute("source").contains("not a Framework observation"));
    }

    @Test
    void originalToolCallOrderAndCompleteObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures)); Path scenario = work.resolve("d13a.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13a/spec.toml")) {
            assertNotNull(in); Files.write(scenario, in.readAllBytes());
        }
        List<String> args = List.of("run", "--scenario", scenario.toString(), "--sut", "demo-d13a-tool-call-order",
                "--set", "cf.execution.classpath=" + fixtures);
        Outcome result = launch(work, work, args, "C", "-Xmx64m");
        assertEquals(0, result.exit(), result.err()); assertTrue(result.out().contains("0 PASS: 454"), result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 26"), result.out());
        assertTrue(result.out().contains("PASS 454, DOMAIN_FAIL 26, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        assertNoSubprocessAndNoNetworkUse(observe(work, "run-d13a", args));
        Path trace = work.resolve("d13a-proof-strace.log"), output = work.resolve("proof.xml"), console = work.resolve("proof-console.log");
        ProcessBuilder builder = new ProcessBuilder("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", trace.toString(), javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true",
                "--class-path", jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures,
                Reactor.root().resolve("tools/ToolCallOrderReport.java").toString(),
                "--scenario", scenario.toString(), "--out", output.toString(), "--workers", "4");
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString());
        builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "D13a observed source report did not finish");
        assertEquals(0, process.exitValue(), Files.readString(console));
        assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof = parse(Files.readString(output)); assertEquals("480", proof.getAttribute("observations")); assertEquals(480, proof.getElementsByTagName("observation").getLength());
        var population = (org.w3c.dom.Element)proof.getElementsByTagName("population").item(0); assertEquals("96", population.getAttribute("mandatory-core")); assertEquals("4", population.getAttribute("optional-revoke-rows")); assertEquals("5", population.getAttribute("optional-multiplier"));
        assertEquals(5, proof.getElementsByTagName("axis").getLength()); assertEquals(64, proof.getElementsByTagName("group").getLength()); assertEquals(5, proof.getElementsByTagName("witness").getLength()); assertEquals(37, proof.getElementsByTagName("event").getLength());
        var counts = (org.w3c.dom.Element)proof.getElementsByTagName("counts").item(0); assertEquals("480", counts.getAttribute("framework_attempts")); assertEquals("720", counts.getAttribute("send_requests")); assertEquals("90", counts.getAttribute("outbox_effects")); assertEquals("90", counts.getAttribute("emit_responses"));
        var violations = proof.getElementsByTagName("violation"); assertEquals(3, violations.getLength());
        Map<String,String> expected = Map.of("unapproved_emit", "4", "tainted_emit", "14", "duplicate_emit", "8"); for (int i = 0; i < violations.getLength(); i++) { var v = (org.w3c.dom.Element)violations.item(i); assertEquals(expected.get(v.getAttribute("kind")), v.getAttribute("count")); }
        boolean disagreement = false; var events = proof.getElementsByTagName("event"); for (int i = 0; i < events.getLength(); i++) { var e = (org.w3c.dom.Element)events.item(i); if (!e.getAttribute("local-approved").equals(e.getAttribute("prefix-approved")) || !e.getAttribute("local-tainted").equals(e.getAttribute("prefix-tainted"))) disagreement = true; } assertTrue(disagreement, "faulty local flags must remain beside authoritative prefix evidence");
    }

    @Test
    void bothOriginalConcurrencyScenariosAndCompleteCertificatesRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures));
        for (String campaign : List.of("counter","queue")) {
            Path spec=work.resolve(campaign+".toml"); try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d6/"+campaign+"/spec.toml")) { assertNotNull(in); Files.write(spec,in.readAllBytes()); }
            List<String> args=List.of("run","--scenario",spec.toString(),"--sut","demo-d6-concurrency-schedules","--set","cf.execution.classpath="+fixtures);
            Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); boolean counter=campaign.equals("counter");
            assertTrue(result.out().contains(counter?"0 PASS: 18":"0 PASS: 5"),result.out()); assertTrue(result.out().contains(counter?"2 DOMAIN_FAIL: 6":"2 DOMAIN_FAIL: 1"),result.out());
            assertTrue(result.out().contains(counter?"PASS 18, DOMAIN_FAIL 6, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0":"PASS 5, DOMAIN_FAIL 1, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d6-"+campaign,args));
        }
        Path trace=work.resolve("d6-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/ConcurrencySchedulesReport.java").toString(),"--counter",work.resolve("counter.toml").toString(),"--queue",work.resolve("queue.toml").toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D6 report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals(30,proof.getElementsByTagName("observation").getLength()); assertEquals("30",((org.w3c.dom.Element)proof.getElementsByTagName("total").item(0)).getAttribute("observations"));
        var expectedFactory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); expectedFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var golden=expectedFactory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d6-golden.xml").toFile()).getDocumentElement();
        var expectedExploration=(Map<?,?>)org.ghyura.cf.model.Json.parse(golden.getElementsByTagName("exploration").item(0).getTextContent(),"golden"); var expectedAnalysis=(Map<?,?>)org.ghyura.cf.model.Json.parse(golden.getElementsByTagName("analysis").item(0).getTextContent(),"golden");
        var campaigns=proof.getElementsByTagName("campaign"); assertEquals(2,campaigns.getLength());
        for (int i=0;i<campaigns.getLength();i++) {
            var c=(org.w3c.dom.Element)campaigns.item(i); String name=c.getAttribute("name"); boolean counter=name.equals("counter"); assertEquals(counter?"96":"32",c.getAttribute("generated")); assertEquals(counter?"72":"26",c.getAttribute("excluded")); assertEquals(counter?"24":"6",c.getAttribute("samples"));
            assertEquals(expectedExploration.get(name),org.ghyura.cf.model.Json.parse(c.getElementsByTagName("precheck").item(0).getTextContent(),"report")); assertEquals(expectedAnalysis.get(name),org.ghyura.cf.model.Json.parse(c.getElementsByTagName("analysis").item(0).getTextContent(),"report"));
            assertEquals(counter?96:32,((List<?>)org.ghyura.cf.model.Json.parse(c.getElementsByTagName("raw-rule-rows").item(0).getTextContent(),"report")).size());
        }
    }

    @Test
    void originalSessionMemoryAndCompleteObservedCertificatesRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d13d.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13d/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d13d-session-memory","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 718"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 82"),result.out()); assertTrue(result.out().contains("PASS 718, DOMAIN_FAIL 82, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d13d",args));
        Path trace=work.resolve("d13d-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/SessionMemoryReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D13d report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("800",proof.getAttribute("observations")); assertEquals("1152",proof.getAttribute("generated")); assertEquals("352",proof.getAttribute("excluded")); assertEquals("800",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13d-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("exploration","precheck"),Map.entry("analysis","analysis"),Map.entry("raw-bond-truth","raw-bond-truth"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(800,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":1152,\"early_excluded\":352,\"late_excluded\":0,\"unselected\":0,\"executable\":800,\"mandatory_rows\":1152,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void originalIdempotencyAndAllObservedTablesHistogramsAndWitnessesRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d1.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d1/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d1-idempotency","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 77"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 43"),result.out()); assertTrue(result.out().contains("PASS 77, DOMAIN_FAIL 43, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d1",args));
        Path trace=work.resolve("d1-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/IdempotencyReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D1 report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("120",proof.getAttribute("observations")); assertEquals("600",proof.getAttribute("generated")); assertEquals("480",proof.getAttribute("excluded")); assertEquals("120",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d1-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("sieve-summary","sieve-summary"),Map.entry("analysis","analysis"),Map.entry("raw-bond-truth","raw-bond-truth"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(120,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":600,\"early_excluded\":480,\"late_excluded\":0,\"unselected\":0,\"executable\":120,\"mandatory_rows\":600,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }


    @Test
    void redteam3CapturedObservationsScoreBothSourceVariantsFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(fixtures));
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var golden = factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/redteam/v3-golden.xml").toFile()).getDocumentElement();
        var nodes = golden.getElementsByTagName("case");
        var expected = new java.util.ArrayList<Map<?,?>>(); var captured = new java.util.ArrayList<Map<String,Object>>();
        for (int i = 0; i < nodes.getLength(); i++) {
            var row = (Map<?,?>) org.ghyura.cf.model.Json.parseBinary64(nodes.item(i).getTextContent(), "independent controls");
            expected.add(row);
            captured.add(Map.of("variant", row.get("variant"), "combo_id", row.get("combo_id"), "pid", row.get("pid"), "replies", row.get("replies")));
        }
        Path input = work.resolve("observations.json"), output = work.resolve("report.json");
        Files.writeString(input, org.ghyura.cf.model.Json.write(Map.of("schema", "cf.redteam3-observations/v1", "records", captured)));
        String classpath = jar().toAbsolutePath() + java.io.File.pathSeparator + fixtures;
        Path classes = work.resolve("classes"); Files.createDirectories(classes);
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler(); assertNotNull(compiler);
        assertEquals(0, compiler.run(null, null, null, "--release", "25", "-Xlint:all", "-Werror", "-cp", classpath, "-d", classes.toString(), Reactor.root().resolve("tools/RedTeam3Report.java").toString()));
        Path trace = work.resolve("redteam3-strace.log"), console = work.resolve("redteam3-report.log");
        var builder = new ProcessBuilder("strace", "-f", "-qq", "-e", "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg", "-o", trace.toString(),
                javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true", "--class-path", classes + java.io.File.pathSeparator + classpath, "RedTeam3Report", input.toString(), output.toString());
        builder.directory(work.toFile()); builder.environment().clear();
        builder.environment().put("PATH", javaExecutable().getParent().toString()); builder.environment().put("HOME", work.toString()); builder.environment().put("LC_ALL", "C");
        Process process = builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) { process.destroyForcibly(); throw new AssertionError("redteam3 replay report did not finish"); }
        assertEquals(0, process.exitValue(), Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var report = (Map<?,?>) org.ghyura.cf.model.Json.parseBinary64(Files.readString(output), "packaged replay");
        assertEquals("cf.redteam3-report/v1", report.get("schema"));
        assertEquals(org.ghyura.cf.model.Canonical.sha256Hex(Files.readString(input)), report.get("input_sha256"));
        var records = (List<?>) report.get("records"); assertEquals(128, records.size());
        for (int i = 0; i < records.size(); i++) {
            var got = (Map<?,?>) records.get(i); var want = expected.get(i);
            for (String key : List.of("variant", "combo_id", "pid", "uid", "marker", "response")) assertEquals(want.get(key), got.get(key), key + "/" + i);
            assertEquals(((List<?>) want.get("calls")).size(), ((Number) got.get("request_count")).intValue());
            var metrics = (Map<?,?>) got.get("metrics"); var expectedMetrics = (Map<?,?>) want.get("verdict");
            assertEquals(expectedMetrics.keySet(), metrics.keySet());
            expectedMetrics.forEach((k,v) -> assertEquals(((Number) v).doubleValue(), ((Number) metrics.get(k)).doubleValue()));
            var attrs = new java.util.TreeMap<String,String>(); ((Map<?,?>) want.get("attributes")).forEach((k,v) -> attrs.put((String) k, v.toString()));
            assertEquals(attrs, got.get("attributes"));
        }
        var summary = (Map<?,?>) org.ghyura.cf.model.Json.parse(golden.getElementsByTagName("summary").item(0).getTextContent(), "source family controls");
        var observed = (Map<?,?>) report.get("endpoint_families"); assertEquals(summary.keySet(), observed.keySet());
        summary.forEach((k,v) -> {
            var counts = (List<?>) v; var got = (Map<?,?>) observed.get(k);
            assertEquals(counts.get(0), got.get("breaches")); assertEquals(counts.get(1), got.get("applicable"));
            assertEquals(100.0 * ((Number) counts.get(0)).intValue() / Math.max(1, ((Number) counts.get(1)).intValue()), ((Number) got.get("rate_percent")).doubleValue());
        });
    }

    @Test
    void originalAttackDefenseMatrixAndObservedFrontiersRunFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("attack-defense.toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/redteam/attack-defense/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","local-attack-defense","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"attack-defense",args));
        String classpath=jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures;Path classes=work.resolve("report-classes");Files.createDirectories(classes);var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);assertEquals(0,compiler.run(null,null,null,"--release","25","-Xlint:all","-Werror","-cp",classpath,"-d",classes.toString(),Reactor.root().resolve("tools/AttackDefenseReport.java").toString()));
        Path trace=work.resolve("attack-defense-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("attack-defense-proof.log");ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",classes+java.io.File.pathSeparator+classpath,"AttackDefenseReport","--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("attack/defense report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output));assertEquals("10368",proof.getAttribute("samples"));var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/redteam/attack-defense-golden.xml").toFile()).getDocumentElement();var expected=new java.util.ArrayList<Map<?,?>>();var cases=golden.getElementsByTagName("case");for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)org.ghyura.cf.model.Json.parse(cases.item(i).getTextContent(),"independent source"));var observations=proof.getElementsByTagName("observation");assertEquals(10368,observations.getLength());var seen=new java.util.HashSet<Integer>();var candidates=new java.util.HashSet<String>();
        for(int i=0;i<observations.getLength();i++){var o=(Element)observations.item(i);assertTrue(candidates.add(o.getAttribute("candidate")));var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(o.getElementsByTagName("fields").item(0).getTextContent(),"observed fields");int id=Integer.parseInt((String)fields.get("combo_id"));assertTrue(seen.add(id));var want=expected.get(id);assertEquals(want.get("labels"),org.ghyura.cf.model.Json.parse((String)fields.get("labels"),"labels"));assertEquals(3,fields.size());int severity=((Number)want.get("severity")).intValue();assertEquals(severity>0?"DOMAIN_FAIL":"PASS",o.getAttribute("outcome"));assertEquals(severity>0?"1":"0",fields.get("FW_VAR"));var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(o.getElementsByTagName("metrics").item(0).getTextContent(),"observed metrics");assertEquals(3,metrics.size());assertEquals((double)severity,((Number)metrics.get("severity")).doubleValue());assertEquals(severity>0?1.:0.,((Number)metrics.get("breach")).doubleValue());assertEquals(((Number)want.get("cost_tokens")).doubleValue(),((Number)metrics.get("cost_tokens")).doubleValue());}
        var want=new java.util.TreeMap<String,Object>();((Map<?,?>)org.ghyura.cf.model.Json.parse(golden.getElementsByTagName("summary").item(0).getTextContent(),"expected summary")).forEach((k,v)->want.put((String)k,v));want.remove("focused");assertEquals(want,org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("summary").item(0).getTextContent(),"observed frontier summary"));
    }

    @Test
    void originalLanguageProcessorsAndAllProgramsBytecodesVmTracesDeclarationControlsAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d14a.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d14a/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d14a-language-processors","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 316"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 116"),result.out()); assertTrue(result.out().contains("PASS 316, DOMAIN_FAIL 116, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d14a",args));
        Path trace=work.resolve("d14a-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/LanguageProcessorsReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D14a report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("432",proof.getAttribute("observations")); assertEquals("432",proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals("432",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d14a-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("construction","construction"),Map.entry("analysis","analysis"),Map.entry("proofs","proofs"),Map.entry("controls","controls"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(432,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":432,\"early_excluded\":0,\"late_excluded\":0,\"unselected\":0,\"executable\":432,\"mandatory_rows\":432,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void originalContextPositionAndAllTrialsSharedReadingsCalibrationCoverageAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d13e.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13e/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d13e-context-position","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 49"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 5"),result.out()); assertTrue(result.out().contains("PASS 49, DOMAIN_FAIL 5, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d13e",args));
        Path trace=work.resolve("d13e-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/ContextPositionReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D13e report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("54",proof.getAttribute("observations")); assertEquals("54",proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals("54",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13e-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("construction","construction"),Map.entry("analysis","analysis"),Map.entry("universe","universe"),Map.entry("coverage","coverage"))) assertTrue(org.ghyura.cf.fixtures.demos.ContextPositionAnalysis.equivalent(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report")));
        var observations=proof.getElementsByTagName("observation"); assertEquals(54,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":54,\"early_excluded\":0,\"late_excluded\":0,\"unselected\":0,\"executable\":54,\"mandatory_rows\":54,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void originalSqlPartitioningAndAllQueryBagsNullableControlsAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d14b.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d14b/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d14b-sql-partitioning","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 36"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 36"),result.out()); assertTrue(result.out().contains("PASS 36, DOMAIN_FAIL 36, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d14b",args));
        Path trace=work.resolve("d14b-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/SqlPartitioningReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D14b report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("72",proof.getAttribute("observations")); assertEquals("72",proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals("72",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d14b-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("construction","construction"),Map.entry("analysis","analysis"),Map.entry("kleene","kleene"),Map.entry("truth","truth"),Map.entry("fixture","fixture"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(72,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":72,\"early_excluded\":0,\"late_excluded\":0,\"unselected\":0,\"executable\":72,\"mandatory_rows\":72,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void originalFaultSchedulesAndAllAttemptsCheckpointsJournalsPayloadsAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d15.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d15/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d15-fault-schedules","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 216"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 96"),result.out()); assertTrue(result.out().contains("PASS 216, DOMAIN_FAIL 96, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d15",args));
        Path trace=work.resolve("d15-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FaultSchedulesReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D15 report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("312",proof.getAttribute("observations")); assertEquals("336",proof.getAttribute("generated")); assertEquals("24",proof.getAttribute("excluded")); assertEquals("312",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d15-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("construction","construction"),Map.entry("analysis","analysis"),Map.entry("controls","controls"),Map.entry("restart_ids","restart-ids"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(312,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":336,\"early_excluded\":24,\"late_excluded\":0,\"unselected\":0,\"executable\":312,\"mandatory_rows\":336,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void sourcePortableForestKeepsAll625PredictionsFromPackagedJarsWithoutOriginalRuntime(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("forest.toml");try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/assistant/forest/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","local-surrogate-forest","--set","cf.execution.classpath="+fixtures);Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 625, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"source-forest",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("forest-strace.log"),console=work.resolve("forest-proof.log");ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/PortableForestReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("source forest report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("625",proof.getAttribute("observations"));assertEquals("625",proof.getAttribute("samples"));var expected=PortableForestRunTest.expected();var seen=new java.util.HashSet<String>();var observations=proof.getElementsByTagName("observation");assertEquals(625,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);assertEquals("PASS",row.getAttribute("outcome"));assertTrue(row.getAttribute("candidate").matches("[0-9a-f]{64}"));var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"model fields");String key=PortableForestRunTest.key((List<?>)org.ghyura.cf.model.Json.parse((String)fields.get("features"),"features"));assertTrue(seen.add(key));var want=expected.get(key);assertNotNull(want);var raw=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");var metrics=new java.util.TreeMap<String,Double>();raw.forEach((k,v)->metrics.put((String)k,((Number)v).doubleValue()));PortableForestRunTest.check(fields,metrics,want);}
        assertEquals(expected.keySet(),seen);
    }

    @Test
    void originalRedTeam2KeepsAll192VersionRecordsAndObservedRegressionFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("redteam2.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/redteam/v2/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","local-redteam-regression-v2","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"redteam2",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("redteam2-strace.log"),console=work.resolve("redteam2-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/RedTeamRegressionReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("redteam2 observed report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("96",proof.getAttribute("observations"));assertEquals("96",proof.getAttribute("samples"));
        var expected=RedTeamRegressionRunTest.expected();var seen=new java.util.HashSet<Integer>();var observations=proof.getElementsByTagName("observation");assertEquals(96,observations.getLength());var totals=new java.util.TreeMap<String,int[]>();
        for(int i=0;i<observations.getLength();i++){var observation=(org.w3c.dom.Element)observations.item(i);assertTrue(observation.getAttribute("candidate").matches("[0-9a-f]{64}"));var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(observation.getElementsByTagName("fields").item(0).getTextContent(),"fields");int combo=Integer.parseInt((String)fields.get("combo_id"));assertTrue(seen.add(combo));assertEquals(6,fields.size());var raw=(Map<?,?>)org.ghyura.cf.model.Json.parse(observation.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");var metrics=new java.util.TreeMap<String,Double>();raw.forEach((k,v)->metrics.put((String)k,((Number)v).doubleValue()));assertEquals(9,metrics.size());var versions=(List<?>)org.ghyura.cf.model.Json.parse((String)fields.get("versions"),"versions");assertEquals(2,versions.size());int aggregate=0,severity=0;double slowest=0;
            for(int v=0;v<2;v++){String version=v==0?"app2":"app3";var row=(Map<?,?>)versions.get(v);assertEquals(version,row.get("version"));var want=expected.get(combo+"/"+version);assertNotNull(want);assertEquals(want.get("labels"),org.ghyura.cf.model.Json.parse((String)fields.get("labels"),"labels"));RedTeamRegressionRunTest.check(combo,row,want,metrics);int breach=((Number)row.get("breach")).intValue();aggregate|=breach;severity=Math.max(severity,((Number)row.get("severity")).intValue());slowest=Math.max(slowest,((Number)row.get("latency_ms")).doubleValue());String family=(String)((Map<?,?>)want.get("labels")).get("family");int[] cell=totals.computeIfAbsent(family+"/"+version,k->new int[2]);cell[0]+=breach;cell[1]++;}
            assertEquals(aggregate==0?"PASS":"DOMAIN_FAIL",observation.getAttribute("outcome"));assertEquals(Integer.toString(aggregate),fields.get("FW_VAR"));assertEquals((double)aggregate,metrics.get("breach"));assertEquals((double)severity,metrics.get("severity"));assertEquals(slowest,metrics.get("latency_ms"));assertTrue(((String)fields.get("timing_scope")).contains("not equivalent"));
        }assertEquals(96,seen.size());var regression=(org.w3c.dom.Element)proof.getElementsByTagName("regression").item(0);var families=regression.getElementsByTagName("family");assertEquals(4,families.getLength());double max=0;var vulnerabilities=new java.util.ArrayList<String>();
        for(int i=0;i<families.getLength();i++){var family=(org.w3c.dom.Element)families.item(i);String name=family.getAttribute("name");int[] a=totals.get(name+"/app2"),b=totals.get(name+"/app3");assertNotNull(a);assertNotNull(b);assertEquals(24,a[1]);assertEquals(24,b[1]);assertEquals(a[0],Integer.parseInt(family.getAttribute("app2_breaches")));assertEquals(b[0],Integer.parseInt(family.getAttribute("app3_breaches")));assertEquals(24,Integer.parseInt(family.getAttribute("app2_total")));assertEquals(24,Integer.parseInt(family.getAttribute("app3_total")));assertEquals(100.*a[0]/24,Double.parseDouble(family.getAttribute("app2_percent")));assertEquals(100.*b[0]/24,Double.parseDouble(family.getAttribute("app3_percent")));double delta=100.*Math.abs(a[0]-b[0])/24;assertEquals(delta,Double.parseDouble(family.getAttribute("delta_percent")),1e-12);max=Math.max(max,delta);if(a[0]>0)vulnerabilities.add(name);}
        assertEquals(max,Double.parseDouble(regression.getAttribute("max_delta_percent")),1e-12);assertEquals(Boolean.toString(max<1e-6),regression.getAttribute("unchanged"));assertEquals(vulnerabilities,org.ghyura.cf.model.Json.parse(regression.getElementsByTagName("app2-vulnerabilities").item(0).getTextContent(),"app2 vulnerabilities"));
    }

    @Test
    void financeStackPeeringRecoversOnHoldTransfersAcrossTwoInJvmNodesFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("finance-peering.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/finance/peering/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","finance-stack-peering","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 1, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FinancePeeringReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local peering report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("1",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var goldDocument=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/peering-native-golden.xml").toFile()).getDocumentElement();
        var gold=(Map<?,?>)org.ghyura.cf.model.Json.parse(goldDocument.getElementsByTagName("predictions").item(0).getTextContent(),"independent peering");assertEquals(6,gold.size());
        var observations=proof.getElementsByTagName("observation");assertEquals(1,observations.getLength());var row=(org.w3c.dom.Element)observations.item(0);
        assertEquals("PASS",row.getAttribute("outcome"));var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");
        assertEquals(8,fields.size());assertEquals("0",fields.get("FW_VAR"));assertEquals(Map.of("scenario","source_multinode"),org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request"));
        for(var e:gold.entrySet())assertEquals(e.getValue(),org.ghyura.cf.model.Json.parse((String)fields.get(e.getKey()),"observed peering "+e.getKey()));
        var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");
        assertEquals(4,metrics.size());for(var e:Map.of("recovery_steps",3.0,"proxy_cases",3.0,"propagation_cases",4.0,"inbound_cases",6.0).entrySet())assertEquals(e.getValue(),((Number)metrics.get(e.getKey())).doubleValue());
    }

    @Test
    void financeStackGatewayRuns72Hmac13Auth11ReviewAnd5ErrorProjectionsFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("finance-gateway.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/finance/gateway/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","finance-stack-gateway","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 1, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FinanceGatewayReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local gateway report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("1",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var goldDocument=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/gateway-native-golden.xml").toFile()).getDocumentElement();
        var gold=(Map<?,?>)org.ghyura.cf.model.Json.parse(goldDocument.getElementsByTagName("predictions").item(0).getTextContent(),"independent gateway");assertEquals(4,gold.size());
        var observations=proof.getElementsByTagName("observation");assertEquals(1,observations.getLength());var row=(org.w3c.dom.Element)observations.item(0);
        assertEquals("PASS",row.getAttribute("outcome"));var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");
        assertEquals(6,fields.size());assertEquals("0",fields.get("FW_VAR"));assertEquals(Map.of("scenario","source_gateway_security"),org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request"));
        for(var e:gold.entrySet())assertEquals(e.getValue(),org.ghyura.cf.model.Json.parse((String)fields.get(e.getKey()),"observed gateway "+e.getKey()));
        var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");
        assertEquals(4,metrics.size());for(var e:Map.of("hmac_vectors",72.0,"auth_checks",13.0,"review_cases",11.0,"error_cases",5.0).entrySet())assertEquals(e.getValue(),((Number)metrics.get(e.getKey())).doubleValue());
    }

    @Test
    void financeStackEcosystemRunsThirteenPhasesAnd23TransfersFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("finance-ecosystem.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/finance/ecosystem/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","finance-stack-ecosystem","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 1, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FinanceEcosystemReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local assistant report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("1",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        var goldDocument=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/ecosystem-golden.xml").toFile()).getDocumentElement();
        var gold=(Map<?,?>)org.ghyura.cf.model.Json.parse(goldDocument.getElementsByTagName("predictions").item(0).getTextContent(),"independent ecosystem");
        var observations=proof.getElementsByTagName("observation");assertEquals(1,observations.getLength());var row=(org.w3c.dom.Element)observations.item(0);
        assertEquals("PASS",row.getAttribute("outcome"));var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");
        assertEquals(5,fields.size());assertEquals("0",fields.get("FW_VAR"));assertEquals(Map.of("scenario","source_simulation"),org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request"));
        for(var e:gold.entrySet())assertEquals(e.getValue(),org.ghyura.cf.model.Json.parse((String)fields.get(e.getKey()),"observed ecosystem "+e.getKey()));
        var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");
        assertEquals(4,metrics.size());for(var e:Map.of("phases",13.0,"transfers",23.0,"settled_count",12.0,"blocked_count",5.0).entrySet())assertEquals(e.getValue(),((Number)metrics.get(e.getKey())).doubleValue());
    }

    @Test
    void financeStackClearingRuns192CompleteLifecycleScriptsFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("finance-clearing.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/finance/clearing/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","finance-stack-clearing","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 192, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FinanceClearingReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local assistant report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("192",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/clearing-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new java.util.ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)org.ghyura.cf.model.Json.parse(cases.item(i).getTextContent(),"golden"));var seen=new java.util.HashSet<Integer>();var observations=proof.getElementsByTagName("observation");assertEquals(192,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");var request=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request");int index=-1;for(int k=0;k<expected.size();k++){var want=expected.get(k);var wanted=(Map<?,?>)want.get("request");if(java.util.Objects.equals(wanted,request)){index=k;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);var gold=(Map<?,?>)want.get("expected");for(var e:gold.entrySet())assertEquals(e.getValue(),org.ghyura.cf.model.Json.parse((String)fields.get(e.getKey()),"observed clearing "+e.getKey()),"profile "+index+":"+e.getKey());assertEquals("PASS",row.getAttribute("outcome"));assertEquals("0",fields.get("FW_VAR"));assertEquals(8,fields.size());var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");assertEquals(4,metrics.size());assertEquals((double)((List<?>)gold.get("results")).size(),((Number)metrics.get("operations")).doubleValue());assertEquals((double)((List<?>)gold.get("errors")).size(),((Number)metrics.get("error_count")).doubleValue());var transfers=(List<?>)gold.get("transfers");for(String status:List.of("SETTLED","EXPIRED"))assertEquals((double)transfers.stream().filter(v->((Map<?,?>)v).get("status").equals(status)).count(),((Number)metrics.get(status.equals("SETTLED")?"settled_count":"expired_count")).doubleValue());
        }assertEquals(192,seen.size());
    }

    @Test
    void financeStackSanctionsRuns268CompleteGateRecordsFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("finance-network.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/finance/network/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","finance-stack-sanctions","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 268, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FinanceNetworkReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local assistant report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("268",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/network-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new java.util.ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)org.ghyura.cf.model.Json.parse(cases.item(i).getTextContent(),"golden"));var seen=new java.util.HashSet<Integer>();var observations=proof.getElementsByTagName("observation");assertEquals(268,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");var request=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request");int index=-1;for(int k=0;k<expected.size();k++){var want=expected.get(k);var wanted=(Map<?,?>)want.get("request");if(java.util.Objects.equals(wanted,request)){index=k;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);var gold=(Map<?,?>)want.get("expected");assertEquals(gold,org.ghyura.cf.model.Json.parse((String)fields.get("gate_result"),"observed gate result"),"profile "+index);assertEquals("PASS",row.getAttribute("outcome"));assertEquals("0",fields.get("FW_VAR"));assertEquals(3,fields.size());var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");assertEquals(2,metrics.size());assertEquals((double)((List<?>)gold.get("reasons")).size(),((Number)metrics.get("reason_count")).doubleValue());assertEquals(gold.get("decision").equals("BLOCK")?1.:0.,((Number)metrics.get("is_blocked")).doubleValue());
        }assertEquals(268,seen.size());
    }

    @Test
    void financeStackLedgerRuns72ScriptsAnd336FullOperationsFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("finance-ledger.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/finance/ledger/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","finance-stack-ledger","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 72, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FinanceLedgerReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local assistant report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("72",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/ledger-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new java.util.ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)org.ghyura.cf.model.Json.parse(cases.item(i).getTextContent(),"golden"));var seen=new java.util.HashSet<Integer>();var observations=proof.getElementsByTagName("observation");assertEquals(72,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");var request=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request");int index=-1;for(int k=0;k<expected.size();k++){var want=expected.get(k);var wanted=(Map<?,?>)want.get("request");if(java.util.Objects.equals(wanted,request)){index=k;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);var gold=(Map<?,?>)want.get("expected");for(String key:List.of("results","accounts","cards","card_tokens","entries","debit_totals","credit_totals","events"))assertEquals(gold.get(key),org.ghyura.cf.model.Json.parse((String)fields.get(key),"observed "+key),"script "+index+" field "+key);assertEquals("PASS",row.getAttribute("outcome"));assertEquals("0",fields.get("FW_VAR"));assertEquals(10,fields.size());var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");assertEquals(8,metrics.size());var results=(List<?>)gold.get("results");assertEquals((double)results.size(),((Number)metrics.get("operations")).doubleValue());assertEquals((double)results.stream().filter(v->((Map<?,?>)v).containsKey("error")).count(),((Number)metrics.get("error_count")).doubleValue());for(String key:List.of("accounts","cards","entries","events"))assertEquals((double)((List<?>)gold.get(key)).size(),((Number)metrics.get(Map.of("accounts","account_count","cards","card_count","entries","entry_count","events","event_count").get(key))).doubleValue());for(String key:List.of("debit_totals","credit_totals"))assertEquals(((Map<?,?>)gold.get(key)).values().stream().map(Number.class::cast).mapToDouble(Number::doubleValue).sum(),((Number)metrics.get(key.equals("debit_totals")?"debit_total":"credit_total")).doubleValue());
        }assertEquals(72,seen.size());
    }

    @Test
    void financeStackPolicyRuns98ScriptsAnd762FullDecisionsFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("finance-policy.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/finance/policy/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","finance-stack-policy","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 98, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FinancePolicyReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local assistant report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("98",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/finance/policy-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new java.util.ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)org.ghyura.cf.model.Json.parse(cases.item(i).getTextContent(),"golden"));var seen=new java.util.HashSet<Integer>();var observations=proof.getElementsByTagName("observation");assertEquals(98,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");var request=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request");int index=-1;for(int k=0;k<expected.size();k++){var want=expected.get(k);var wanted=(Map<?,?>)want.get("request");if(java.util.Objects.equals(wanted,request)){index=k;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);var gold=(Map<?,?>)want.get("expected");for(String key:List.of("results","restrictions","clients","events"))assertEquals(gold.get(key),org.ghyura.cf.model.Json.parse((String)fields.get(key),"observed "+key),"script "+index+" field "+key);assertEquals("PASS",row.getAttribute("outcome"));assertEquals("0",fields.get("FW_VAR"));assertEquals(6,fields.size());var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");assertEquals(8,metrics.size());var results=(List<?>)gold.get("results");var restrictions=(Map<?,?>)gold.get("restrictions");var metadata=(Map<?,?>)((Map<?,?>)results.getLast()).get("metadata");for(String name:List.of("ALLOW","REVIEW","BLOCK"))assertEquals((double)results.stream().filter(v->name.equals(((Map<?,?>)v).get("decision"))).count(),((Number)metrics.get(name.toLowerCase(java.util.Locale.ROOT)+"_count")).doubleValue());assertEquals((double)results.size(),((Number)metrics.get("evaluations")).doubleValue());assertEquals((double)((List<?>)gold.get("events")).size(),((Number)metrics.get("event_count")).doubleValue());assertEquals(((Number)restrictions.get("window_count")).doubleValue(),((Number)metrics.get("window_count")).doubleValue());assertEquals(Double.valueOf((String)restrictions.get("window_sum")),((Number)metrics.get("window_sum")).doubleValue());assertEquals(((Number)metadata.get("score")).doubleValue(),((Number)metrics.get("score")).doubleValue());
        }assertEquals(98,seen.size());
    }

    @Test
    void structuredContentKeepsAll13OriginalResponsesFromPackagedJars(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("assistant.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/assistant/structured/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","local-structured-assistant","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 13, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/StructuredContentReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local assistant report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("13",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/assistant/structured-chat-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new java.util.ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)org.ghyura.cf.model.Json.parse(cases.item(i).getTextContent(),"golden"));var seen=new java.util.HashSet<Integer>();var observations=proof.getElementsByTagName("observation");assertEquals(13,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");var request=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request");int index=-1;for(int k=0;k<expected.size();k++){var want=expected.get(k);var wanted=(Map<?,?>)want.get("request");if(java.util.Objects.equals(wanted.get("messages"),request.get("messages"))&&java.util.Objects.equals(wanted.get("max_tokens"),request.get("max_tokens"))){index=k;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);assertEquals(want.get("response"),fields.get("response"));assertEquals(want.get("sources"),org.ghyura.cf.model.Json.parse((String)fields.get("sources"),"sources"));var completion=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("response_json"),"completion");assertEquals(want.get("usage"),completion.get("usage"));assertEquals("chatcmpl-"+row.getAttribute("candidate").substring(0,12),completion.get("id"));assertEquals(0,((Number)completion.get("created")).longValue());assertEquals("PASS",row.getAttribute("outcome"));assertEquals(7,fields.size());var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");assertEquals(5,metrics.size());for(String k:List.of("prompt_tokens","completion_tokens","total_tokens"))assertEquals(((Number)((Map<?,?>)want.get("usage")).get(k)).doubleValue(),((Number)metrics.get(k)).doubleValue());assertEquals(3,((Number)metrics.get("document_count")).intValue());assertEquals(0,((Number)metrics.get("source_count")).intValue());
        }assertEquals(13,seen.size());
    }

    @Test
    void localTextAssistantRunsAll13IndependentRequestsFromPackagedJarsWithoutExternalServices(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("assistant.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/assistant/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","local-cpu-assistant","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 13, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"local-assistant",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("assistant-strace.log"),console=work.resolve("assistant-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/LocalAssistantReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("local assistant report did not finish");}assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("13",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var cases=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/assistant/chat-golden.xml").toFile()).getDocumentElement().getElementsByTagName("case");var expected=new java.util.ArrayList<Map<?,?>>();for(int i=0;i<cases.getLength();i++)expected.add((Map<?,?>)org.ghyura.cf.model.Json.parse(cases.item(i).getTextContent(),"golden"));var seen=new java.util.HashSet<Integer>();var observations=proof.getElementsByTagName("observation");assertEquals(13,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");var request=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("request"),"request");int index=-1;for(int k=0;k<expected.size();k++){var want=expected.get(k);if(java.util.Objects.equals(want.get("messages"),request.get("messages"))&&java.util.Objects.equals(want.get("max_tokens"),request.get("max_tokens"))){index=k;break;}}assertTrue(index>=0);assertTrue(seen.add(index));var want=expected.get(index);assertEquals(want.get("response"),fields.get("response"));assertEquals(want.get("sources"),org.ghyura.cf.model.Json.parse((String)fields.get("sources"),"sources"));var completion=(Map<?,?>)org.ghyura.cf.model.Json.parse((String)fields.get("response_json"),"completion");assertEquals(want.get("usage"),completion.get("usage"));assertEquals("chatcmpl-"+row.getAttribute("candidate").substring(0,12),completion.get("id"));assertEquals(0,((Number)completion.get("created")).longValue());assertEquals("PASS",row.getAttribute("outcome"));assertEquals(7,fields.size());var metrics=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics");assertEquals(5,metrics.size());for(String k:List.of("prompt_tokens","completion_tokens","total_tokens"))assertEquals(((Number)((Map<?,?>)want.get("usage")).get(k)).doubleValue(),((Number)metrics.get(k)).doubleValue());assertEquals(3,((Number)metrics.get("document_count")).intValue());assertEquals(0,((Number)metrics.get("source_count")).intValue());
        }assertEquals(13,seen.size());
    }

    @Test
    void originalRedTeamV1RunsFromPackagedJarsWithoutExternalServices(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));Path scenario=work.resolve("redteam.toml");
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/redteam/spec.toml")){assertNotNull(in);Files.write(scenario,in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","local-redteam-mock-v1","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.assembly.distinct=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m");assertEquals(0,result.exit(),result.err());assertTrue(result.out().contains("PASS 54, DOMAIN_FAIL 234, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out());assertNoSubprocessAndNoNetworkUse(observe(work,"redteam-v1",args));
        Path output=work.resolve("proof.xml"),trace=work.resolve("redteam-strace.log"),console=work.resolve("redteam-proof.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/RedTeamReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--addon",fixtures.toString(),"--workers","4");
        builder.directory(work.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",work.toString());builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();if(!process.waitFor(90,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("redteam-v1 report did not finish");}
        assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));var proof=parse(Files.readString(output));assertEquals("288",proof.getAttribute("observations"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/redteam/v1-golden.xml").toFile()).getDocumentElement();
        var expected=new java.util.TreeMap<String,Map<?,?>>();var cases=golden.getElementsByTagName("case");for(int i=0;i<cases.getLength();i++){var c=(org.w3c.dom.Element)cases.item(i);expected.put(c.getAttribute("id"),(Map<?,?>)org.ghyura.cf.model.Json.parse(c.getTextContent(),"golden"));}
        var seen=new java.util.HashSet<String>();var observations=proof.getElementsByTagName("observation");assertEquals(288,observations.getLength());
        for(int i=0;i<observations.getLength();i++){var row=(org.w3c.dom.Element)observations.item(i);var fields=(Map<?,?>)org.ghyura.cf.model.Json.parse(row.getElementsByTagName("fields").item(0).getTextContent(),"fields");String id=(String)fields.get("candidate_id");assertTrue(seen.add(id));var want=expected.get(id);assertNotNull(want,id);
            for(String key:List.of("prompt","prompt_sha256","response","outcome"))assertEquals(want.get(key),fields.get(key),id+" "+key);
            assertEquals(want.get("labels"),org.ghyura.cf.model.Json.parse((String)fields.get("labels"),"labels"));assertEquals(want.get("oracle"),org.ghyura.cf.model.Json.parse((String)fields.get("oracle"),"oracle"));assertEquals(want.get("fw_var").toString(),fields.get("FW_VAR"));assertEquals(want.get("combo_id").toString(),fields.get("combo_id"));assertEquals(11,fields.size());assertEquals("mock",fields.get("provider"));assertEquals("mock-weatherbot",fields.get("model"));
            var metrics=new java.util.TreeMap<String,Object>();for(String key:List.of("n_chars","cost_tokens","latency_ms"))metrics.put(key,((Number)want.get(key)).doubleValue());((Map<?,?>)want.get("oracle")).forEach((key,value)->metrics.put((String)key,((Number)value).doubleValue()));assertEquals(org.ghyura.cf.model.Json.parse(org.ghyura.cf.model.Json.write(metrics),"expected metrics"),org.ghyura.cf.model.Json.parse(row.getElementsByTagName("metrics").item(0).getTextContent(),"metrics"));assertEquals(((Number)want.get("fw_var")).intValue()==0?"PASS":"DOMAIN_FAIL",row.getAttribute("outcome"));
        }assertEquals(expected.keySet(),seen);
    }

    @Test
    void originalLocalProposalWorkflowsMatchAll992IndependentRecordsAndReplayWithoutProviders(@TempDir Path work)throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath();assertTrue(Files.isRegularFile(fixtures));
        Path classes=Files.createDirectory(work.resolve("driver"));String classpath=jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures;
        var compiler=javax.tools.ToolProvider.getSystemJavaCompiler();assertNotNull(compiler);
        assertEquals(0,compiler.run(null,null,null,"--release","25","-Xlint:all","-Werror","-cp",classpath,"-d",classes.toString(),Reactor.root().resolve("tools/LocalProposalReport.java").toString()));
        String runtime=classes+java.io.File.pathSeparator+classpath;
        for(String family:List.of("quote","parser","guard")) {
            Path taskWork=Files.createDirectory(work.resolve(family));Map<String,Object> serial=null;
            for(int workers:List.of(1,4)) {
                String label=family+"-"+workers;Path output=taskWork.resolve(label+".xml"),trace=taskWork.resolve(label+"-strace.log"),console=taskWork.resolve(label+".log");
                ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),workers==1?"-Xmx64m":"-Xmx256m","-Djava.awt.headless=true","--class-path",runtime,"LocalProposalReport","--task",family,"--out",output.toString(),"--work",taskWork.toString(),"--addon",fixtures.toString(),"--workers",Integer.toString(workers));
                builder.directory(taskWork.toFile());builder.environment().clear();builder.environment().put("PATH",javaExecutable().getParent().toString());builder.environment().put("HOME",taskWork.toString());builder.environment().put("LC_ALL","C");
                Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start();
                if(!process.waitFor(180,TimeUnit.SECONDS)){process.destroyForcibly();throw new AssertionError("original local task did not finish: "+label);}
                assertEquals(0,process.exitValue(),Files.readString(console));assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
                var proof=LocalProposalProof.verify(output,family,workers==1?(family.equals("parser")?6:3):0);
                if(workers==1)serial=proof;else assertEquals(serial,proof,"saved proposals/candidate identities/original outputs must be worker independent");
            }
        }
    }

    @Test
    void originalNestedScopesBothCampaignsAndGroupedBundleRunFromPackagedJars(@TempDir Path work) throws Exception {
        for(String phase:List.of("B1","B2")){int population=phase.equals("B1")?24:3,pass=phase.equals("B1")?8:1,fail=phase.equals("B1")?16:2;Path campaignWork=Files.createDirectory(work.resolve(phase));
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=campaignWork.resolve("d5b.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d5b/"+phase+"/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d5b-nested-scopes","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true","--set","cf.generation.replace.pattern=warn","--set","cf.generation.replace.diagnostics=summary");
        Outcome result=launch(campaignWork,campaignWork,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: "+pass),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: "+fail),result.out()); assertTrue(result.out().contains("PASS "+pass+", DOMAIN_FAIL "+fail+", BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(campaignWork,"run-d5b",args));
        Path trace=campaignWork.resolve("d5b-proof-strace.log"),output=campaignWork.resolve("proof.xml"),console=campaignWork.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/NestedScopesReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(campaignWork.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",campaignWork.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D5 B report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals(Integer.toString(population),proof.getAttribute("observations")); assertEquals(Integer.toString(population),proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals(Integer.toString(population),proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d5b-golden.xml").toFile()).getDocumentElement();
        for(var tags:List.of(Map.entry("construction","construction"),Map.entry("analyse","analysis"),Map.entry("probe","probes"))){var nodes=golden.getElementsByTagName(tags.getKey());boolean found=false;for(int i=0;i<nodes.getLength();i++)if(((org.w3c.dom.Element)nodes.item(i)).getAttribute("phase").equals(phase)){found=true;assertEquals(org.ghyura.cf.model.Json.parse(nodes.item(i).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));}assertTrue(found);}
        var observations=proof.getElementsByTagName("observation"); assertEquals(population,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i);if(!c.getAttribute("phase").equals(phase))continue; var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse(org.ghyura.cf.fixtures.demos.NestedScopes.json(Map.of("raw",population,"early_excluded",0,"late_excluded",0,"unselected",0,"executable",population,"mandatory_rows",population,"optional_multiplier",1)),"expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
        }
    }

    @Test
    void originalFieldPipelineGroupedRowsAndExactRecordVerdictsRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d5a.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d5a/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d5a-field-pipeline","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true");
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 32"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 40"),result.out()); assertTrue(result.out().contains("PASS 32, DOMAIN_FAIL 40, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d5a",args));
        Path trace=work.resolve("d5a-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/FieldPipelineReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D5 A report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("72",proof.getAttribute("observations")); assertEquals("72",proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals("72",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d5a-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("construction","construction"),Map.entry("analysis","analysis"),Map.entry("native-prefix-counts","native-prefix-counts"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(72,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":72,\"early_excluded\":0,\"late_excluded\":0,\"unselected\":0,\"executable\":72,\"mandatory_rows\":72,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void originalWorkflowCoverageAndAllCheckpointsCertificatesAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d3c.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d3c/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d3c-workflow-coverage","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 1410"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 30"),result.out()); assertTrue(result.out().contains("PASS 1410, DOMAIN_FAIL 30, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d3c",args));
        Path trace=work.resolve("d3c-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/WorkflowCoverageReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D3C report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("1440",proof.getAttribute("observations")); assertEquals("1440",proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals("1440",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d3c-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("construction","construction"),Map.entry("analysis","analysis"),Map.entry("catalogue","catalogue"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(1440,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":1440,\"early_excluded\":0,\"late_excluded\":0,\"unselected\":0,\"executable\":1440,\"mandatory_rows\":1440,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void originalWorkflowOrderAllPhasesAndRepeatedWordsMatchIndependentSnapshotsFromPackagedJars(@TempDir Path work) throws Exception {
        var bBank=new java.util.TreeMap<String,Map<String,String>>();
        for(String variant:List.of("A","B","B-alt")){String phase=variant.equals("A")?"A":"B";int population=phase.equals("A")?72:81,pass=phase.equals("A")?48:66,fail=phase.equals("A")?24:15;Path variantWork=Files.createDirectory(work.resolve(variant));
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=variantWork.resolve("d3.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d3/"+variant+"/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d3-workflow-order","--set","cf.execution.classpath="+fixtures,"--set","cf.generation.distinctTables=true");
        Outcome result=launch(variantWork,variantWork,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: "+pass),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: "+fail),result.out()); assertTrue(result.out().contains("PASS "+pass+", DOMAIN_FAIL "+fail+", BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(variantWork,"run-d3",args));
        Path trace=variantWork.resolve("d3-proof-strace.log"),output=variantWork.resolve("proof.xml"),console=variantWork.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/WorkflowOrderReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(variantWork.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",variantWork.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D3 A/B report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals(Integer.toString(population),proof.getAttribute("observations")); assertEquals(Integer.toString(population),proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals(Integer.toString(population),proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d3-golden.xml").toFile()).getDocumentElement();
        var constructions=golden.getElementsByTagName("construction");for(int i=0;i<constructions.getLength();i++)if(((org.w3c.dom.Element)constructions.item(i)).getAttribute("variant").equals(variant))assertEquals(org.ghyura.cf.model.Json.parse(constructions.item(i).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("construction").item(0).getTextContent(),"report"));
        var analyses=golden.getElementsByTagName("analysis");for(int i=0;i<analyses.getLength();i++)if(((org.w3c.dom.Element)analyses.item(i)).getAttribute("phase").equals(phase))assertEquals(org.ghyura.cf.model.Json.parse(analyses.item(i).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("analysis").item(0).getTextContent(),"report"));assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName("alternate").item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("alternate").item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(population,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); if(!c.getAttribute("phase").equals(phase))continue; var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse(org.ghyura.cf.fixtures.demos.WorkflowOrder.json(Map.of("raw",population,"early_excluded",0,"late_excluded",0,"unselected",0,"executable",population,"mandatory_rows",phase.equals("A")?18:81,"optional_multiplier",phase.equals("A")?4:1)),"expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
        if(variant.equals("B"))bBank.putAll(bank);if(variant.equals("B-alt"))assertEquals(bBank,bank);
        }
    }

    @Test
    void originalSolverRelationsAndAllNativePermutationsOptimaControlsAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d14c.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d14c/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d14c-solver-relations","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 72"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 72"),result.out()); assertTrue(result.out().contains("PASS 72, DOMAIN_FAIL 72, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d14c",args));
        Path trace=work.resolve("d14c-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/SolverRelationsReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D14c report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("144",proof.getAttribute("observations")); assertEquals("144",proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals("144",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d14c-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("construction","construction"),Map.entry("analysis","analysis"),Map.entry("proofs","proofs"),Map.entry("controls","controls"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(144,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":144,\"early_excluded\":0,\"late_excluded\":0,\"unselected\":0,\"executable\":144,\"mandatory_rows\":144,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @Test
    void originalAgentMessageOrderAndAllRawBondsEnablednessPromisesAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d13c.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13c/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        try(var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13c/demo.constraints.json")){assertNotNull(in);Files.write(work.resolve("demo.constraints.json"),in.readAllBytes());}
        List<String> args=List.of("run","--scenario",scenario.toString(),"--sut","demo-d13c-agent-message-order","--set","cf.execution.classpath="+fixtures);
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 36"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 18"),result.out()); assertTrue(result.out().contains("PASS 36, DOMAIN_FAIL 18, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d13c",args));
        Path trace=work.resolve("d13c-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/AgentMessageOrderReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D13c report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("54",proof.getAttribute("observations")); assertEquals("288",proof.getAttribute("generated")); assertEquals("234",proof.getAttribute("excluded")); assertEquals("54",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13c-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("sieve-summary","sieve-summary"),Map.entry("analysis","analysis"),Map.entry("raw-bond-truth","raw-bond-truth"),Map.entry("exploration","exploration"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(54,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":288,\"early_excluded\":234,\"late_excluded\":0,\"unselected\":0,\"executable\":54,\"mandatory_rows\":288,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void originalComposedCarriersAndCompleteConstructionAndObservedAnalysisRunFromPackagedJars(@TempDir Path work) throws Exception {
        Path fixtures=Path.of(System.getProperty("cf.fixtures.jar","missing")).toAbsolutePath(); assertTrue(Files.isRegularFile(fixtures)); Path scenario=work.resolve("d13b.toml");
        try (var in=getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d13b/spec.toml")) { assertNotNull(in); Files.write(scenario,in.readAllBytes()); }
        List<String> args=new java.util.ArrayList<>(List.of("run","--scenario",scenario.toString(),"--sut","demo-d13b-composed-carriers","--set","cf.execution.classpath="+fixtures)); org.ghyura.cf.fixtures.demos.ComposedCarriersPlugin.SETTINGS.forEach((k,v) -> args.addAll(List.of("--set",k+"="+v)));
        Outcome result=launch(work,work,args,"C","-Xmx64m"); assertEquals(0,result.exit(),result.err()); assertTrue(result.out().contains("0 PASS: 80"),result.out()); assertTrue(result.out().contains("2 DOMAIN_FAIL: 28"),result.out()); assertTrue(result.out().contains("PASS 80, DOMAIN_FAIL 28, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"),result.out()); assertNoSubprocessAndNoNetworkUse(observe(work,"run-d13b",args));
        Path trace=work.resolve("d13b-proof-strace.log"),output=work.resolve("proof.xml"),console=work.resolve("proof-console.log");
        ProcessBuilder builder=new ProcessBuilder("strace","-f","-qq","-e","trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg","-o",trace.toString(),javaExecutable().toString(),"-Xmx64m","-Djava.awt.headless=true","--class-path",jar().toAbsolutePath()+java.io.File.pathSeparator+fixtures,Reactor.root().resolve("tools/ComposedCarriersReport.java").toString(),"--scenario",scenario.toString(),"--out",output.toString(),"--workers","4");
        builder.directory(work.toFile()); builder.environment().clear(); builder.environment().put("PATH",javaExecutable().getParent().toString()); builder.environment().put("HOME",work.toString()); builder.environment().put("LC_ALL","C");
        Process process=builder.redirectErrorStream(true).redirectOutput(console.toFile()).start(); assertTrue(process.waitFor(90,TimeUnit.SECONDS),"D13b report did not finish"); assertEquals(0,process.exitValue(),Files.readString(console)); assertNoSubprocessAndNoNetworkUse(Files.readAllLines(trace));
        var proof=parse(Files.readString(output)); assertEquals("108",proof.getAttribute("observations")); assertEquals("108",proof.getAttribute("generated")); assertEquals("0",proof.getAttribute("excluded")); assertEquals("108",proof.getAttribute("samples"));
        var factory=javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); var golden=factory.newDocumentBuilder().parse(Reactor.root().resolve("fw-sut-fixtures/src/test/resources/org/ghyura/cf/fixtures/demos/d13b-golden.xml").toFile()).getDocumentElement();
        for (var tags : List.of(Map.entry("analysis","analysis"))) assertEquals(org.ghyura.cf.model.Json.parse(golden.getElementsByTagName(tags.getKey()).item(0).getTextContent(),"golden"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName(tags.getValue()).item(0).getTextContent(),"report"));
        var expectedConstruction=(java.util.Map<String,Object>)org.ghyura.cf.model.Json.parse(golden.getElementsByTagName("construction").item(0).getTextContent(),"golden"); var actualConstruction=(java.util.Map<String,Object>)org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("construction").item(0).getTextContent(),"proof"); for(var entry:expectedConstruction.entrySet()) if(!entry.getKey().equals("origin")) assertEquals(entry.getValue(),actualConstruction.get(entry.getKey()),"construction "+entry.getKey());
        assertEquals(org.ghyura.cf.model.Json.parse(org.ghyura.cf.model.Json.write(org.ghyura.cf.fixtures.demos.ComposedCarriersPlugin.SETTINGS),"expected settings"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("generation-settings").item(0).getTextContent(),"settings")); assertTrue(proof.getElementsByTagName("generation-diagnostics").item(0).getTextContent().contains("rows seen=2 rewritten=2 dropped=0"));
        var observations=proof.getElementsByTagName("observation"); assertEquals(108,observations.getLength()); var bank=new java.util.TreeMap<String,Map<String,String>>();
        for (int i=0;i<observations.getLength();i++) { var o=(org.w3c.dom.Element)observations.item(i); var fields=new java.util.TreeMap<String,String>(); var nodes=o.getElementsByTagName("field"); for (int k=0;k<nodes.getLength();k++) { var f=(org.w3c.dom.Element)nodes.item(k); org.junit.jupiter.api.Assertions.assertNull(fields.put(f.getAttribute("name"),f.getTextContent())); } org.junit.jupiter.api.Assertions.assertNull(bank.put(o.getAttribute("id"),fields)); }
        var cases=golden.getElementsByTagName("case"); for (int i=0;i<cases.getLength();i++) { var c=(org.w3c.dom.Element)cases.item(i); var actual=bank.get(c.getAttribute("id")); assertNotNull(actual); var fields=c.getElementsByTagName("field"); assertEquals(fields.getLength(),actual.size()); for (int k=0;k<fields.getLength();k++) { var f=(org.w3c.dom.Element)fields.item(k); String key=f.getAttribute("name"); assertEquals(f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(f.getTextContent(),"golden"):f.getTextContent(),f.getAttribute("kind").equals("json")?org.ghyura.cf.model.Json.parse(actual.get(key),"report"):actual.get(key),c.getAttribute("id")+" "+key); } }
        assertEquals(org.ghyura.cf.model.Json.parse("{\"raw\":108,\"early_excluded\":0,\"late_excluded\":0,\"unselected\":0,\"executable\":108,\"mandatory_rows\":108,\"optional_multiplier\":1}","expected"),org.ghyura.cf.model.Json.parse(proof.getElementsByTagName("stage-counts").item(0).getTextContent(),"report"));
    }

    /** M10: an addon installed on cf.execution.classpath states its capabilities and shows its plugins, only while installed. */
    @Test
    void anInstalledAddonStatesItsCapabilitiesAndPluginsOnlyWhileInstalled(@TempDir Path work) throws Exception {
        Path fixtures = Path.of(System.getProperty("cf.fixtures.jar", "missing"));
        assertTrue(Files.isRegularFile(fixtures));
        Outcome plain = launch(work, work, List.of("capabilities", "--format", "xml"), "C", "-Xmx256m");
        assertEquals(0, plain.exit(), plain.err());
        assertTrue(plain.out().contains("<capability id=\"C12\" status=\"PARTIAL\""),plain.out());
        assertTrue(plain.out().contains("implemented=\"10\" partial=\"1\" notImplemented=\"6\" incompatible=\"1\""), plain.out());
        assertTrue(plain.out().contains("<capability id=\"C13\" status=\"NOT_IMPLEMENTED\""), "the default jar does not claim C13");
        assertTrue(plain.out().contains("<plugin id=\"inline-metrics\"") && !plain.out().contains("origin=\"addon\""), plain.out());
        Outcome installed = launch(work, work, List.of("capabilities", "--format", "xml", "--set", "cf.execution.classpath=" + fixtures), "C",
                "-Xmx256m");
        assertEquals(0, installed.exit(), installed.err());
        assertTrue(installed.out().contains("implemented=\"11\" partial=\"2\" notImplemented=\"4\" incompatible=\"1\""), installed.out());
        assertTrue(installed.out().contains("<capability id=\"C13\" status=\"PARTIAL\""), installed.out());
        assertTrue(installed.out().contains("<capability id=\"C17\" status=\"IMPLEMENTED\""), installed.out());
        assertTrue(installed.out().contains("<evidence>addon fw-sut-fixtures: typed SUT plugins tutor"), installed.out());
        for (String id : List.of("tutor", "bundle-pricing", "bundle-secure-pipeline", "billing-engine", "order-fulfilment", "telemetry-catalog",
                "tester-pub", "demo-d7-reduction-trees", "demo-d2-stream-boundaries", "demo-d8-ties-pagination", "demo-d11-coalition-value", "demo-d4-configuration-rules", "demo-d8b-dependency-dags", "demo-d9-robust-portfolios", "demo-d12-cyclic-patterns", "demo-d10-diagnostic-sensors", "demo-d13a-tool-call-order", "demo-d6-concurrency-schedules", "demo-d13d-session-memory", "demo-d1-idempotency", "demo-d13b-composed-carriers", "demo-d13c-agent-message-order", "demo-d14c-solver-relations", "demo-d14a-language-processors", "demo-d13e-context-position", "demo-d14b-sql-partitioning", "demo-d15-fault-schedules", "demo-d3c-workflow-coverage", "demo-d3-workflow-order", "demo-d5a-field-pipeline", "demo-d5b-nested-scopes", "local-prompt-guard", "local-redteam-mock-v1", "local-cpu-assistant", "local-redteam-regression-v2", "local-surrogate-forest", "local-structured-assistant", "finance-stack-policy", "finance-stack-ledger", "finance-stack-sanctions", "finance-stack-clearing", "finance-stack-ecosystem", "finance-stack-gateway", "finance-stack-peering", "local-attack-defense")) {
            assertTrue(installed.out().contains("<plugin id=\"" + id + "\""), id);
        }
        assertTrue(installed.out().contains("origin=\"addon\"") && installed.out().contains("<plugin id=\"inline-metrics\""), installed.out());
        Outcome absent = launch(work, work, List.of("capabilities", "--set", "cf.execution.classpath=" + work.resolve("absent.jar")), "C",
                "-Xmx256m");
        assertEquals(3, absent.exit(), absent.err());
        assertTrue(absent.err().contains("CONFIG_INVALID_VALUE"), absent.err());
    }

    /** The control for the rule below: the JVM's probe passes, and each kind of real network use or child process fails it. */
    @Test
    void theSyscallRuleTellsTheJvmsCapabilityProbeFromNetworkUse() {
        List<String> base = new ArrayList<>(List.of("100 execve(\"/jdk/bin/java\", [\"java\"], 0x0 /* 3 vars */) = 0"));
        for (int i = 0; i < 25; i++) {
            base.add("10" + i + " clone3({flags=CLONE_VM|CLONE_FS|CLONE_FILES|CLONE_SIGHAND|CLONE_THREAD|CLONE_SYSVSEM}, 88) = 2" + i);
        }
        base.add("100 socket(AF_UNIX, SOCK_STREAM|SOCK_CLOEXEC|SOCK_NONBLOCK, 0) = 5");
        base.add("100 connect(5, {sa_family=AF_UNIX, sun_path=\"/var/run/nscd/socket\"}, 110) = -1 ENOENT (No such file or directory)");
        base.add("100 socket(AF_INET, SOCK_STREAM, IPPROTO_IP) = 7");
        base.add("100 socket(AF_INET6, SOCK_STREAM, IPPROTO_IP) = 7");
        base.add("100 socket(AF_INET6, SOCK_STREAM, IPPROTO_IP) = 7");
        assertNoSubprocessAndNoNetworkUse(base);
        for (String use : List.of(
                "100 connect(7, {sa_family=AF_INET, sin_port=htons(9), sin_addr=inet_addr(\"192.0.2.1\")}, 16) = -1 ENETUNREACH",
                "100 connect(7, {sa_family=AF_INET6, sin6_port=htons(443), sin6_addr=inet_pton(AF_INET6, \"::1\")}, 28) = 0",
                "100 bind(7, {sa_family=AF_INET, sin_port=htons(8080), sin_addr=inet_addr(\"0.0.0.0\")}, 16) = 0",
                "100 sendto(7, \"x\", 1, 0, {sa_family=AF_INET, sin_port=htons(53), sin_addr=inet_addr(\"10.0.0.1\")}, 16) = 1",
                "100 listen(5, 50) = 0",
                "100 accept4(5, NULL, NULL, SOCK_CLOEXEC) = 8",
                "101 execve(\"/bin/sh\", [\"sh\"], 0x0 /* 3 vars */) = 0",
                "100 clone3({flags=CLONE_VM|CLONE_VFORK, exit_signal=SIGCHLD}, 88) = 300",
                "100 socket(AF_INET, SOCK_DGRAM, IPPROTO_IP) = 9", "100 socket(AF_INET, SOCK_DGRAM, IPPROTO_IP) = 9")) {
            List<String> log = new ArrayList<>(base);
            log.add(use);
            if (use.contains("SOCK_DGRAM")) {
                log.add(use);
            }
            assertThrows(AssertionError.class, () -> assertNoSubprocessAndNoNetworkUse(log), use);
        }
    }

    private static void assertNoSubprocessAndNoNetworkUse(List<String> syscalls) {
        assertTrue(syscalls.size() > 20, "strace log looks empty: " + syscalls.size() + " lines");
        long executed = syscalls.stream().filter(l -> (l.contains(" execve(") || l.contains(" execveat(")) && l.endsWith("= 0"))
                .count();
        assertEquals(1, executed, "exactly one program may be executed (the java launcher itself): " + syscalls.stream()
                .filter(l -> l.contains("execve")).toList());
        assertEquals(List.of(), syscalls.stream().filter(l -> l.contains(" fork(") || l.contains(" vfork(")).toList(),
                "no process may be forked");
        assertEquals(List.of(), syscalls.stream().filter(l -> (l.contains(" clone(") || l.contains(" clone3("))
                && !l.contains("CLONE_THREAD")).toList(), "only threads may be cloned, never processes");
        // F29: the JVM's own probe creates and closes an INET and an INET6 socket; it never connects, binds or sends
        List<String> internetUse = syscalls.stream().filter(l -> (l.contains(" connect(") || l.contains(" bind(")
                || l.contains(" sendto(") || l.contains(" sendmsg(")) && (l.contains("AF_INET") || l.contains("AF_NETLINK")
                || l.contains("AF_PACKET"))).toList();
        assertEquals(List.of(), internetUse, "nothing may connect, bind or send to a network address");
        List<String> listeners = syscalls.stream().filter(l -> l.contains(" listen(") || l.contains(" accept(")
                || l.contains(" accept4(")).toList();
        assertEquals(List.of(), listeners, "nothing may listen or accept");
        List<String> probes = syscalls.stream().filter(l -> l.contains(" socket(AF_INET")).toList();
        assertTrue(probes.size() <= 4, "more internet sockets than the JVM's capability probe creates: " + probes);
    }

    @Test
    void theJarValidatesAScenarioInEveryFormatInAReadOnlyDirectoryAndTheyAllMeanTheSame(@TempDir Path work) throws Exception {
        Map<InputFormat, Path> scenarios = writeExampleScenarios(work);
        Path cwd = readOnlyDirectory(work);
        try {
            Set<String> identities = new LinkedHashSet<>();
            for (Map.Entry<InputFormat, Path> scenario : scenarios.entrySet()) {
                Outcome outcome = launch(work, cwd, List.of("validate", "--scenario", scenario.getValue().toString(), "--format", "xml"),
                        "C", "-Xmx64m");
                assertEquals(0, outcome.exit(), scenario.getKey() + ": " + outcome.err());
                Element root = parse(outcome.out());
                Element element = (Element) root.getElementsByTagName("scenario").item(0);
                assertEquals(scenario.getKey().token(), element.getAttribute("format"));
                assertEquals("5", element.getAttribute("dataSheets"), scenario.getKey().token());
                assertEquals("9", element.getAttribute("values"), scenario.getKey().token());
                assertEquals("SIGNED_BYTE", element.getAttribute("keyEncoding"), scenario.getKey().token());
                identities.add(element.getAttribute("scenarioDigest") + " " + element.getAttribute("planDigest"));
                assertFalse(outcome.out().contains("�"), "output was not valid UTF-8");
            }
            assertEquals(1, identities.size(), "the same scenario must have one identity in every format: " + identities);
            Outcome refused = launch(work, cwd, List.of("validate", "--scenario", scenarios.get(InputFormat.JSON).resolveSibling("missing.json").toString()),
                    "C", "-Xmx64m");
            assertEquals(3, refused.exit());
            assertTrue(refused.err().contains("INPUT_UNREADABLE"), refused.err());
        } finally {
            makeWritable(cwd);
        }
        try (Stream<Path> left = Files.list(cwd)) {
            assertEquals(0, left.count(), "the application wrote into its working directory");
        }
    }

    @Test
    void anIsolatedRootWithNoNetworkAndAReadOnlyWorkingDirectoryStillValidatesScenarios(@TempDir Path work) throws Exception {
        Assumptions.assumeTrue(commandWorks(List.of("bwrap", "--unshare-all", "--ro-bind", "/", "/", "true")),
                "bwrap cannot create namespaces here: the isolated-root evidence cannot be produced");
        Map<InputFormat, Path> scenarios = writeExampleScenarios(work);
        Path cwd = Files.createDirectory(work.resolve("empty"));
        Path folder = scenarios.get(InputFormat.JSON).getParent();
        for (InputFormat format : List.of(InputFormat.JSON, InputFormat.XLSX)) {
            Outcome outcome = sandboxed(work, cwd, Map.of("/app/fw-app.jar", jar(), "/app/scenarios", folder),
                    List.of("-jar", "/app/fw-app.jar", "validate", "--scenario", "/app/scenarios/scenario." + format.token()));
            assertEquals(0, outcome.exit(), format + ": " + outcome.err());
            assertTrue(outcome.out().contains("data sheets   5") && outcome.out().contains("9 (labels 6..14)"), outcome.out());
            assertTrue(outcome.err().contains("SPEC_RUNMEFIRSTONCE_SHAPE"), "the one warning of the example: " + outcome.err());
        }
    }

    @Test
    void readingScenariosInEveryFormatLoadsNoWindowingToolkitAndNoNetworkingClass(@TempDir Path work) throws Exception {
        Map<InputFormat, Path> scenarios = writeExampleScenarios(work);
        Path cwd = Files.createDirectory(work.resolve("cwd"));
        for (Map.Entry<InputFormat, Path> scenario : scenarios.entrySet()) {
            Path classLog = work.resolve("classes-" + scenario.getKey().token() + ".log");
            Outcome outcome = launch(work, cwd, List.of("validate", "--scenario", scenario.getValue().toString()), "C", "-Xmx64m",
                    "-Xlog:class+load=info:file=" + classLog);
            assertEquals(0, outcome.exit(), scenario.getKey() + ": " + outcome.err());
            List<String> loaded = Files.readAllLines(classLog, StandardCharsets.UTF_8);
            assertTrue(loaded.size() > 200, "class-load log looks empty: " + loaded.size());
            if (scenario.getKey() == InputFormat.XLSX) {
                assertTrue(loaded.stream().anyMatch(line -> line.contains(" org.apache.poi.")), "the workbook reader of the jar was used");
            }
            assertEquals(List.of(), loaded.stream()
                    .filter(line -> line.contains(" java.awt.") || line.contains(" javax.swing.") || line.contains(" sun.awt."))
                    .toList(), scenario.getKey() + ": reading a scenario must not initialise AWT or Swing");
            assertEquals(List.of(), connecting(loaded), scenario.getKey() + ": reading a scenario must not load a class that "
                    + "connects or listens");
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    /**
     * The class-load lines of classes that a connection, a listener or an HTTP request cannot do without: a socket
     * address, a socket or socket channel implementation, an HTTP or TLS handler. The channel classes the JDK loads
     * for its local capability probe (sun.nio.ch.SocketChannelImpl among them, decision F29) are not among them.
     */
    private static List<String> connecting(List<String> classLoadLines) {
        return classLoadLines.stream()
                .filter(line -> line.contains(" java.net.Socket ") || line.contains(" java.net.ServerSocket ")
                        || line.contains(" java.net.DatagramSocket ") || line.contains(" java.net.MulticastSocket ")
                        || line.contains(" java.net.HttpURLConnection ") || line.contains(" java.net.InetSocketAddress ")
                        || line.contains(" java.net.InetAddress ") || line.contains(" java.net.http.")
                        || line.contains(" sun.nio.ch.NioSocketImpl ") || line.contains(" sun.nio.ch.ServerSocketChannelImpl ")
                        || line.contains(" sun.nio.ch.DatagramChannelImpl ") || line.contains(" javax.net.")
                        || line.contains(" sun.net.www.protocol.http.") || line.contains(" sun.net.www.protocol.https."))
                .toList();
    }

    /** The scenario the legacy builder documents, written once per input format into a fresh directory. */
    private static Map<InputFormat, Path> writeExampleScenarios(Path work) throws IOException {
        WorkbookData example = ScheduleBuilder.newSchedule()
                .sheet("HEAD").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1)).row("scenario=demo")
                .sheet("COST").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1))
                .rows(" id=1 cost=0.10", " id=2 cost=0.30", " id=3 cost=0.50")
                .sheet("LAT").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1))
                .rows(" latency=10ms", " latency=30ms", " latency=80ms")
                .sheet("TAIL").flags(Flag.EXCLUDE, Flag.REUSE).verbs(Verb.combi(1), Verb.combi(1)).row(" status=ok")
                .join("ROW", JoinSpec.from("HEAD").via("COST", "LAT").to("TAIL").as(Multiplicity.M_N)).row("FW_EMPTY_STRING")
                .runMeFirstOnce("class RunMeFirstOnce { public static String FW_ARGS; }")
                .arguments("noargs")
                .customVar(2, "FWCUSTOMVAR=2 nominal")
                .build();
        Path folder = Files.createDirectories(work.resolve("scenarios"));
        Map<InputFormat, Path> files = new EnumMap<>(InputFormat.class);
        for (InputFormat format : InputFormat.values()) {
            Path file = folder.resolve("scenario." + format.token());
            Files.write(file, WorkbookFormats.writer(format).write(example));
            files.put(format, file);
        }
        return files;
    }

    /** Runs the jar under strace and returns the traced process, thread and socket system calls. */
    private static List<String> observe(Path work, String name, List<String> applicationArguments) throws Exception {
        Path log = work.resolve("strace-" + name + ".log");
        List<String> command = new ArrayList<>(List.of("strace", "-f", "-qq", "-e",
                "trace=execve,execveat,fork,vfork,clone,clone3,socket,connect,bind,listen,accept,accept4,sendto,sendmsg",
                "-o", log.toString(),
                javaExecutable().toString(), "-Xmx64m", "-Djava.awt.headless=true", "-jar", jar().toString()));
        command.addAll(applicationArguments);
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(90, TimeUnit.SECONDS), "strace run did not finish");
        assertEquals(0, process.exitValue());
        return Files.readAllLines(log, StandardCharsets.UTF_8);
    }

    /** Runs the distribution tool as the release runs it: java tools/Distribution.java, from the reactor's tools directory. */
    private static String distributionTool(Path work, Path out) throws Exception {
        List<String> command = new ArrayList<>(List.of(javaExecutable().toString(), Reactor.root().resolve("tools/Distribution.java").toString(),
                "--root", Reactor.root().toString(), "--out", out.toString(), "--app", jar().toString(),
                "--addon", Path.of(System.getProperty("cf.fixtures.jar", "missing")).toAbsolutePath().toString(),
                "--python-addon", Path.of(System.getProperty("cf.python.jar", "missing")).toAbsolutePath().toString()));
        ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true);
        builder.environment().remove("JAVA_TOOL_OPTIONS");   // the JVM would announce it on the output the test reads
        Process process = builder.start();
        String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "the distribution tool finished");
        assertEquals(0, process.exitValue(), text);
        return text;
    }

    /**
     * OPEN-08 and OPEN-09: the local distribution is the declared set of files, the application jar and the fixtures addon both
     * carrying the project terms unchanged, the terms beside them, the addon configuration and the samples, each with its size
     * and SHA-256 in distribution.xml and nothing undeclared; the same build makes the same zip, byte for byte, and a
     * distribution is never written over another.
     */
    @Test
    void theLocalDistributionDeclaresEveryFileCarriesTheTermsAndZipsReproducibly(@TempDir Path work) throws Exception {
        String first = distributionTool(work, work.resolve("one"));
        distributionTool(work, work.resolve("two"));
        String folder = "combinatorics-framework-0.1.0-SNAPSHOT";
        Path dist = work.resolve("one").resolve(folder);
        assertTrue(first.startsWith("distribution " + dist), first);
        Element root = parse(Files.readString(dist.resolve("distribution.xml")));
        assertEquals(List.of("cf.distribution/v1", "0.1.0-SNAPSHOT", "25"), List.of(root.getAttribute("schema"), root.getAttribute("version"),
                root.getAttribute("java")));
        NodeList declared = root.getElementsByTagName("file");
        List<String> paths = new ArrayList<>();
        for (int i = 0; i < declared.getLength(); i++) {
            Element f = (Element) declared.item(i);
            byte[] bytes = Files.readAllBytes(dist.resolve(f.getAttribute("path")));
            assertEquals(f.getAttribute("bytes"), Integer.toString(bytes.length), f.getAttribute("path"));
            assertEquals(f.getAttribute("sha256"), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)), f.getAttribute("path"));
            paths.add(f.getAttribute("path"));
        }
        assertEquals(Integer.toString(paths.size()), root.getAttribute("files"));
        List<String> present;
        try (Stream<Path> walk = Files.walk(dist)) {
            present = walk.filter(Files::isRegularFile).map(p -> dist.relativize(p).toString()).sorted().toList();
        }
        List<String> expected = new ArrayList<>(paths);
        expected.add("distribution.xml");
        assertEquals(expected.stream().sorted().toList(), present, "nothing undeclared");
        assertTrue(paths.containsAll(List.of("fw-app.jar", "addons/fw-sut-fixtures.jar", "addons/fw-python.jar", "LICENSE", "NOTICE.md",
                "cf-addons.properties", "cf-python.properties",
                "scenarios/showcase/showcase.xlsx", "scenarios/showcase/showcase.properties", "scenarios/tutor/tutor-typed.json",
                "scenarios/tutor/tutor-typed.constraints.json")), paths.toString());
        assertTrue(paths.stream().noneMatch(p -> p.contains("/.")), "no hidden or lock file travels: " + paths);
        for (String terms : List.of("LICENSE", "NOTICE.md")) {
            byte[] project = Files.readAllBytes(Reactor.root().resolve(terms));
            assertArrayEquals(project, Files.readAllBytes(dist.resolve(terms)), terms);
            for (String jarName : List.of("fw-app.jar", "addons/fw-sut-fixtures.jar", "addons/fw-python.jar")) {
                try (JarFile jar = new JarFile(dist.resolve(jarName).toFile())) {
                    try (var in = jar.getInputStream(jar.getJarEntry("META-INF/" + terms))) {
                        assertArrayEquals(project, in.readAllBytes(), jarName + " carries " + terms + " unchanged");
                    }
                }
            }
        }
        Path zip = work.resolve("one").resolve(folder + ".zip");
        assertArrayEquals(Files.readAllBytes(zip), Files.readAllBytes(work.resolve("two").resolve(folder + ".zip")), "the same build makes the same archive");
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            assertEquals(expected.size(), archive.size());
            for (String p : expected) {
                try (var in = archive.getInputStream(archive.getEntry(folder + "/" + p))) {
                    assertArrayEquals(Files.readAllBytes(dist.resolve(p)), in.readAllBytes(), p);
                }
            }
        }
        assertTrue(Files.readString(dist.resolve("cf-addons.properties")).endsWith("\ncf.execution.classpath=addons/fw-sut-fixtures.jar\n"));
        List<String> again = new ArrayList<>(List.of(javaExecutable().toString(), Reactor.root().resolve("tools/Distribution.java").toString(),
                "--root", Reactor.root().toString(), "--out", work.resolve("one").toString(), "--app", jar().toString()));
        Process process = new ProcessBuilder(again).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS));
        assertTrue(process.exitValue() != 0, "a distribution is written once, into a new place");
    }

    /**
     * OPEN-09: started from its own read-only folder in an isolated root (loopback-only network, no /usr/bin and so no Python,
     * no home and no legacy checkout), the distribution finds its addon only through cf-addons.properties and runs its samples
     * in one JVM: the tutor lesson as a typed plugin of the addon with its exact distribution, and the showcase workbook.
     */
    @Test
    void theDistributionFindsItsAddonAndRunsItsSamplesFromItsOwnFolderInIsolation(@TempDir Path work) throws Exception {
        Assumptions.assumeTrue(commandWorks(List.of("bwrap", "--unshare-all", "--ro-bind", "/", "/", "true")),
                "bwrap cannot create namespaces here: the isolated-root evidence cannot be produced");
        distributionTool(work, work.resolve("out"));
        Path dist = work.resolve("out").resolve("combinatorics-framework-0.1.0-SNAPSHOT");
        Outcome plain = sandboxed(work, dist, Map.of(), List.of("-jar", "/work/fw-app.jar", "capabilities", "--format", "xml"));
        assertEquals(0, plain.exit(), plain.err());
        assertTrue(plain.out().contains("<capability id=\"C13\" status=\"NOT_IMPLEMENTED\"") && !plain.out().contains("origin=\"addon\""), plain.out());
        Outcome addon = sandboxed(work, dist, Map.of(), List.of("-jar", "/work/fw-app.jar", "capabilities", "--format", "xml", "--config",
                "cf-addons.properties"));
        assertEquals(0, addon.exit(), addon.err());
        assertTrue(addon.out().contains("<capability id=\"C13\" status=\"PARTIAL\"") && addon.out().contains("origin=\"addon\""), addon.out());
        Outcome tutor = sandboxed(work, dist, Map.of(), List.of("-Xmx256m", "-jar", "/work/fw-app.jar", "run", "--scenario",
                "scenarios/tutor/tutor-typed.json", "--sut", "tutor", "--config", "cf-addons.properties"));
        assertEquals(0, tutor.exit(), tutor.err());
        assertTrue(tutor.out().contains("generated 192, excluded 48, left out by covering selection 0, executable 144"), tutor.out());
        assertTrue(tutor.out().contains("0 PASS: 18; 20 ORDER_VIOLATION: 120; 30 DOUBLE_CHARGE: 2; 31 DISPATCH_LOST: 4"), tutor.out());
        Outcome showcase = sandboxed(work, dist, Map.of(), List.of("-Xmx256m", "-jar", "/work/fw-app.jar", "run", "--scenario",
                "scenarios/showcase/showcase.xlsx", "--config", "scenarios/showcase/showcase.properties"));
        assertEquals(0, showcase.exit(), showcase.err());
        assertTrue(showcase.out().contains("showcase"), showcase.out());
    }

    /**
     * C07 through the Python add-on (author decision 2026-10-12): the packaged application with addons/fw-python.jar runs the
     * unchanged D10 scenario's own Python, every case PASS as the Java port proves it, and while it does, the only programs it
     * starts are the java launcher, the JDK's spawn helper and the configured Python interpreter, once per case, with nothing
     * connecting, binding or sending to a network address. Without the add-on the plain jar still claims no Python.
     */
    @Test
    void theUnchangedD10RunsItsOwnPythonFromThePackagedJarsAndStartsOnlyThatInterpreter(@TempDir Path work) throws Exception {
        Path python = pythonExecutable();
        Path addon = Path.of(System.getProperty("cf.python.jar", "missing")).toAbsolutePath();
        assertTrue(Files.isRegularFile(addon), "the Python add-on jar: " + addon);
        Path scenario = work.resolve("d10.toml");
        try (var in = getClass().getResourceAsStream("/org/ghyura/cf/fixtures/demos/d10/spec.toml")) {
            assertNotNull(in);
            Files.write(scenario, in.readAllBytes());
        }
        Outcome capabilities = launch(work, work, List.of("capabilities", "--format", "xml", "--set", "cf.execution.classpath=" + addon), "C",
                "-Xmx256m");
        assertEquals(0, capabilities.exit(), capabilities.err());
        assertTrue(capabilities.out().contains("<capability id=\"C07\" status=\"IMPLEMENTED\""), capabilities.out());
        assertTrue(capabilities.out().contains("<evidence>addon fw-python: cf.execution.sut=python runs"), capabilities.out());
        assertTrue(capabilities.out().contains("<capability id=\"C14\" status=\"PARTIAL\""), capabilities.out());
        assertTrue(capabilities.out().contains("<capability id=\"C15\" status=\"PARTIAL\""), capabilities.out());
        assertTrue(capabilities.out().contains("implemented=\"11\" partial=\"3\" notImplemented=\"3\" incompatible=\"1\""), capabilities.out());
        assertTrue(capabilities.out().contains("<plugin id=\"python\""), capabilities.out());
        List<String> run = List.of("run", "--scenario", scenario.toString(), "--sut", "python", "--set", "cf.execution.classpath=" + addon,
                "--set", "cf.python.executable=" + python, "--set", "cf.generation.distinctTables=true", "--workers", "8");
        // 3072 interpreter starts: about 40 s on an idle four-core machine, far longer while the rest of verify loads it
        Outcome result = launchWithin(300, work, work, run, "C", "-Xmx128m");
        assertEquals(0, result.exit(), result.err());
        assertTrue(result.out().contains("verdicts    0: 3072"), result.out());
        assertTrue(result.out().contains("PASS 3072, DOMAIN_FAIL 0, BROKEN 0, TIMEOUT 0, INFRA_FAIL 0, SKIPPED 0, CANCELLED 0"), result.out());
        List<String> traced = new ArrayList<>(run);
        traced.addAll(List.of("--set", "cf.run.stop.candidates=24"));
        List<String> syscalls = observe(work, "run-d10-python", traced);
        Map<String, Long> programs = new TreeMap<>();
        Map<String, String> unfinished = new java.util.HashMap<>();   // strace -f splits a call another process interrupted
        for (String line : syscalls) {
            String pid = line.split(" ", 2)[0];
            int at = line.indexOf(" execve(\"");
            if (at >= 0) {
                int from = at + " execve(\"".length();
                String program = line.substring(from, line.indexOf('"', from));
                if (line.endsWith("<unfinished ...>")) {
                    unfinished.put(pid, program);
                } else if (line.endsWith("= 0")) {
                    programs.merge(program, 1L, Long::sum);
                }
            } else if (line.contains("<... execve resumed>") && line.endsWith("= 0") && unfinished.containsKey(pid)) {
                programs.merge(unfinished.remove(pid), 1L, Long::sum);
            }
            assertTrue(!line.contains(" execveat("), "no program is executed by descriptor: " + line);
        }
        Path spawnHelper = Path.of(System.getProperty("java.home"), "lib", "jspawnhelper");
        assertEquals(Set.of(javaExecutable().toString(), spawnHelper.toString(), python.toString()), programs.keySet(),
                "only java, its spawn helper and the configured interpreter run: " + programs);
        assertEquals(1L, programs.get(javaExecutable().toString()));
        assertTrue(programs.get(python.toString()) >= 24, "the interpreter ran once per admitted case: " + programs);
        List<String> internetUse = syscalls.stream().filter(l -> (l.contains(" connect(") || l.contains(" bind(") || l.contains(" sendto(")
                || l.contains(" sendmsg(")) && (l.contains("AF_INET") || l.contains("AF_NETLINK") || l.contains("AF_PACKET"))).toList();
        assertEquals(List.of(), internetUse, "nothing may connect, bind or send to a network address");
        assertEquals(List.of(), syscalls.stream().filter(l -> l.contains(" listen(") || l.contains(" accept(") || l.contains(" accept4(")).toList(),
                "nothing may listen or accept");
    }

    /**
     * The legacy generator_trunk usage examples run their own Python from the packaged jars as the legacy bundle ran them (0121):
     * a scenario inside the generator tree (an ancestor with sut_paths.py) runs with the tree as working directory, the tree and
     * its repository on PYTHONPATH and BUNDLE_REPO_ROOT and BUNDLE_SUT_ROOT set, every sheet ending in a line break. A copy of
     * the QA checkout's tree: a self-contained AI_combi smoke, an engine demo that imports the generator_trunk package and a
     * billing proof whose SUT path is relative to the tree, four cases each, every one with a verdict. With a copy of the SUT
     * checkout beside it, as the legacy layout had it, three original SUT applications run unchanged (0125): the automation
     * constructor simulating each candidate's circuit (C14), the telemetry catalog's lookups and the tester-pub adjudicator.
     */
    @Test
    void generatorTrunkExamplesRunTheirOwnPythonAsTheLegacyBundleRanThem(@TempDir Path work) throws Exception {
        Path trunk = Reactor.root().getParent().resolve("Combinatorics-Framework-QA-edition/generator_trunk");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(trunk), "the QA edition checkout is not beside the project");
        Path sut = Reactor.root().getParent().resolve("SUT");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(sut.resolve("automation-scheme-studio")), "the SUT checkout is not beside the project");
        Path python = pythonExecutable();
        Path addon = Path.of(System.getProperty("cf.python.jar", "missing")).toAbsolutePath();
        Path copy = work.resolve("QA-edition/generator_trunk");
        copyTree(trunk, copy);
        copyTree(sut, work.resolve("SUT"));   // the legacy sibling checkout BUNDLE_SUT_ROOT names (sut_paths.discovered_sut_root)
        Map<String, String> expected = Map.of("AI_combi_testing_platform/scenarios/00_smoke/scenario.toml", "PASS 4, DOMAIN_FAIL 0, BROKEN 0",
                // 0129: the bundle Core configuration gives engine_demo the legacy 8 candidates (cross-checked case by case)
                "engine_demo/direct_engine_smoke/scenario.toml", "PASS 3, DOMAIN_FAIL 1, BROKEN 0",
                "proof_billing/c0_smoke/smoke.toml", "PASS 2, DOMAIN_FAIL 2, BROKEN 0",
                // original SUT applications, unchanged: the automation constructor (C14), the telemetry catalog and tester-pub
                "scenarios/automation_scheme_studio/00_smoke/scenario.toml", "PASS 2, DOMAIN_FAIL 2, BROKEN 0",
                "usecases/telemetry_catalog_e2e/telemetry_lookup.toml", "PASS 4, DOMAIN_FAIL 0, BROKEN 0",
                "usecases/qa_pub_vs_bar/qa_pub_vs_bar.toml", "PASS 4, DOMAIN_FAIL 0, BROKEN 0");
        for (var e : expected.entrySet()) {
            Outcome result = launch(work, work, List.of("run", "--scenario", copy.resolve(e.getKey()).toString(), "--sut", "python",
                    "--set", "cf.execution.classpath=" + addon, "--set", "cf.python.executable=" + python, "--set", "cf.run.stop.candidates=4",
                    "--workers", "4"), "C", "-Xmx128m");
            assertEquals(0, result.exit(), e.getKey() + ": " + result.err());
            assertTrue(result.out().contains("cf.python.workdir                 = " + copy + "  [SCENARIO"), result.out());
            assertTrue(result.out().contains("outcomes    " + e.getValue()), e.getKey() + ": " + result.out());
        }
    }

    /** Copies a reference checkout without its git metadata or bytecode caches; originals run only on copies. */
    private static void copyTree(Path checkout, Path to) throws IOException {
        Path from = checkout.toRealPath();   // a checkout may be reached through a symbolic link
        try (var walk = Files.walk(from)) {
            for (Path f : walk.filter(f -> !f.toString().contains("__pycache__") && !from.relativize(f).toString().startsWith(".git")).toList()) {
                Path target = to.resolve(from.relativize(f).toString());
                if (Files.isDirectory(f)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(f, target);
                }
            }
        }
    }

    /** The absolute python3 found on this JVM's PATH; the test is a release-blocking skip without one. */
    private static Path pythonExecutable() {
        for (String dir : System.getenv().getOrDefault("PATH", "").split(java.io.File.pathSeparator)) {
            if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, "python3"))) {
                return Path.of(dir, "python3").toAbsolutePath();
            }
        }
        org.junit.jupiter.api.Assumptions.abort("MISSING_AUTHORIZED_BACKEND: no python3 on PATH, so the Python add-on the author requires "
                + "(decision 2026-10-12) cannot be verified here");
        return null;
    }

    private static Path javaExecutable() {
        return Path.of(System.getProperty("java.home"), "bin", "java");
    }

    /** Runs the jar with an empty environment whose PATH is only the JDK's bin directory. */
    private static Outcome launch(Path scratch, Path cwd, List<String> args, String locale, String... jvmOptions)
            throws IOException, InterruptedException {
        return launchWithin(90, scratch, cwd, args, locale, jvmOptions);
    }

    private static Outcome launchWithin(long seconds, Path scratch, Path cwd, List<String> args, String locale, String... jvmOptions)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(javaExecutable().toString()));
        command.addAll(List.of(jvmOptions));
        command.addAll(List.of("-Djava.awt.headless=true", "-jar", jar().toString()));
        command.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(cwd.toFile());
        Map<String, String> environment = builder.environment();
        environment.clear();
        environment.put("PATH", javaExecutable().getParent().toString());
        environment.put("HOME", scratch.toString());
        environment.put("LC_ALL", locale);
        Path out = Files.createTempFile(scratch, "stdout", ".txt");
        Path err = Files.createTempFile(scratch, "stderr", ".txt");
        builder.redirectOutput(out.toFile());
        builder.redirectError(err.toFile());
        Process process = builder.start();
        if (!process.waitFor(seconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("the application did not finish within " + seconds + " seconds: " + args);
        }
        return new Outcome(process.exitValue(), Files.readString(out, StandardCharsets.UTF_8),
                Files.readString(err, StandardCharsets.UTF_8));
    }

    /** True when the command starts and exits 0. */
    private static boolean commandWorks(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
            return process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Runs the JDK's java inside a bubblewrap root that has a loopback-only network and holds only the system
     * libraries, the JDK, a few explicit read-only binds and an empty read-only {@code /work}: no
     * {@code /usr/bin}, no {@code /bin}, no {@code /home}.
     */
    private static Outcome sandboxed(Path scratch, Path cwd, Map<String, Path> binds, List<String> javaArguments)
            throws IOException, InterruptedException {
        Path javaHome = Path.of(System.getProperty("java.home")).toRealPath();
        List<String> command = new ArrayList<>(List.of("bwrap", "--unshare-all", "--die-with-parent", "--clearenv",
                "--setenv", "PATH", javaHome.resolve("bin").toString(), "--setenv", "LC_ALL", "C"));
        for (String dir : List.of("/usr/lib", "/usr/lib64", "/lib", "/lib64")) {
            Path path = Path.of(dir);
            if (Files.isSymbolicLink(path)) {
                command.addAll(List.of("--symlink", Files.readSymbolicLink(path).toString(), dir));
            } else if (Files.isDirectory(path)) {
                command.addAll(List.of("--ro-bind", dir, dir));
            }
        }
        command.addAll(List.of("--ro-bind", "/etc", "/etc", "--proc", "/proc", "--dev", "/dev", "--tmpfs", "/tmp",
                "--ro-bind", javaHome.toString(), javaHome.toString()));
        binds.forEach((inside, host) -> command.addAll(List.of("--ro-bind", host.toString(), inside)));
        command.addAll(List.of("--ro-bind", cwd.toString(), "/work", "--chdir", "/work",
                javaHome.resolve("bin").resolve("java").toString(), "-Xmx64m", "-Djava.awt.headless=true"));
        command.addAll(javaArguments);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().clear();
        builder.environment().put("PATH", System.getenv("PATH") == null ? "/usr/bin:/bin" : System.getenv("PATH"));
        Path out = Files.createTempFile(scratch, "stdout", ".txt");
        Path err = Files.createTempFile(scratch, "stderr", ".txt");
        builder.redirectOutput(out.toFile());
        builder.redirectError(err.toFile());
        Process process = builder.start();
        if (!process.waitFor(90, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("the sandboxed run did not finish within 90 seconds: " + javaArguments);
        }
        return new Outcome(process.exitValue(), Files.readString(out, StandardCharsets.UTF_8),
                Files.readString(err, StandardCharsets.UTF_8));
    }

    private static Path readOnlyDirectory(Path work) throws IOException {
        Path cwd = Files.createDirectory(work.resolve("readonly"));
        Files.setPosixFilePermissions(cwd, PosixFilePermissions.fromString("r-x------"));
        Assumptions.assumeFalse(Files.isWritable(cwd),
                "this account can write to a read-only directory (running as root?); the read-only check cannot be enforced");
        return cwd;
    }

    private static void makeWritable(Path directory) throws IOException {
        Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
    }

    private static Element parse(String xml) throws Exception {
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return document.getDocumentElement();
    }

    /** Machine-readable evidence of what the distribution contains. */
    private static void writeInventory(Path jar, int entries, Set<String> artifacts) throws Exception {
        byte[] bytes = Files.readAllBytes(jar);
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<dependency-inventory jar=\"").append(jar.getFileName()).append("\" bytes=\"").append(bytes.length)
                .append("\" entries=\"").append(entries).append("\" sha256=\"").append(hex).append("\">\n");
        for (String artifact : artifacts) {
            int colon = artifact.indexOf(':');
            xml.append("  <artifact group=\"").append(artifact, 0, colon).append("\" name=\"")
                    .append(artifact.substring(colon + 1)).append("\"/>\n");
        }
        xml.append("</dependency-inventory>\n");
        Path dir = Reactor.root().resolve("fw-acceptance").resolve("target").resolve("acceptance");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("dependency-inventory.xml"), xml, StandardCharsets.UTF_8);
    }
}
