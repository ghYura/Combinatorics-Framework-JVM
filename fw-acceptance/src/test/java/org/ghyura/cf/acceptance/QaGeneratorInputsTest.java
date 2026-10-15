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

import org.ghyura.cf.app.Application;
import org.ghyura.cf.desktop.session.ScenarioLibrary;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Severity;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The QA edition's generator_trunk scenario specs as framework inputs (the author asked for inputs, not ports of their applications). Each pinned
 * spec loads without errors and assembles its recorded counts under the native defaults and under the legacy de-duplication profile; the original
 * bundle's committed plan cardinalities and graph exports are independent evidence, and so are the counts the scenario library documents.
 * The specs are read from the reference checkout beside the project, copied into a temporary directory first.
 */
class QaGeneratorInputsTest {
    private static final Pattern COUNTS = Pattern.compile("<candidates [^>]*generated=\"(\\d+)\"[^>]*executable=\"(\\d+)\"");
    /** The API-ladder spec grew after the library recorded 18; its structural count: C(6,2) cases x 2! actions x 5 routes x 2^2 observers x 2 (optional). */
    private static final Map<String, String> STALE_LIBRARY_COUNTS = Map.of("combinatorial_tests_sieve3d_external_api", "1200");

    private static Path copyTrunk(Path work) throws Exception {
        Path trunk = Reactor.root().getParent().resolve("Combinatorics-Framework-QA-edition/generator_trunk");
        Assumptions.assumeTrue(Files.isDirectory(trunk), "the QA edition checkout is not beside the project: its scenario inputs cannot be read");
        try (var walk = Files.walk(trunk)) {
            for (Path p : walk.toList()) {
                Path target = work.resolve(trunk.relativize(p).toString());
                if (Files.isDirectory(p)) Files.createDirectories(target); else Files.copy(p, target);
            }
        }
        return work;
    }
    private static List<Element> specs() throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = QaGeneratorInputsTest.class.getResourceAsStream("generator-inputs-manifest.xml")) {
            assertNotNull(in); var nodes = factory.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName("spec");
            var out = new ArrayList<Element>(); for (int i = 0; i < nodes.getLength(); i++) out.add((Element) nodes.item(i)); return out;
        }
    }
    private static Element child(Element e, String tag, String profile) {
        var nodes = e.getElementsByTagName(tag);
        for (int i = 0; i < nodes.getLength(); i++) { var c = (Element) nodes.item(i); if (profile == null || profile.equals(c.getAttribute("profile"))) return c; }
        return null;
    }
    private static String sha256(Path p) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p))); }
    private static String[] candidates(Path spec, boolean legacy) {
        var args = new ArrayList<>(List.of("candidates", "--scenario", spec.toString(), "--limit", "0", "--scan", "100000000", "--format", "xml"));
        if (legacy) args.addAll(List.of("--set", "cf.generation.distinctTables=true", "--set", "cf.assembly.distinct=true"));
        var out = new ByteArrayOutputStream(); var err = new ByteArrayOutputStream();
        int code = Application.run(args.toArray(String[]::new), Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        var m = COUNTS.matcher(out.toString(StandardCharsets.UTF_8));
        return m.find() ? new String[]{Integer.toString(code), m.group(1), m.group(2)} : new String[]{Integer.toString(code), out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8)};
    }
    @Test void everyGeneratorSpecLoadsAndAssemblesItsPinnedCountsUnderBothProfiles(@TempDir Path work) throws Exception {
        Path trunk = copyTrunk(work); var session = Session.resolve(null, Map.of(), List.of()); var all = specs(); assertEquals(159, all.size());
        for (Element spec : all) {
            String rel = spec.getAttribute("path"); Path file = trunk.resolve(rel);
            assertEquals(spec.getAttribute("sha256"), sha256(file), rel);
            var summary = session.validate(file);
            assertTrue(summary.findings().stream().noneMatch(d -> d.severity() == Severity.ERROR), rel + ": " + summary.findings());
            for (String profile : List.of("native", "legacy")) {
                // 0129: a spec in the legacy generator tree runs with the bundle Core configuration (distinct tables and final tables) by
                // default, as the legacy bundle ran it, so both profiles give the pinned legacy counts (cross-checked with the legacy).
                var want = child(spec, "counts", "legacy"); var got = candidates(file, profile.equals("legacy"));
                if (want.hasAttribute("refused")) {
                    assertEquals(want.getAttribute("exit"), got[0], rel + " " + profile); assertTrue(got[1].contains(want.getAttribute("refused")), rel + " " + profile);
                } else {
                    assertEquals("0", got[0], rel + " " + profile + " " + (got.length == 2 ? got[1] : ""));
                    assertEquals(List.of(want.getAttribute("generated"), want.getAttribute("executable")), List.of(got[1], got[2]), rel + " " + profile);
                }
            }
        }
    }
    @Test void theOriginalBundlePlanCardinalitiesAndGraphExportsHold(@TempDir Path work) throws Exception {
        Path trunk = copyTrunk(work); var session = Session.resolve(null, Map.of(), List.of()); int plans = 0, graphs = 0;
        for (Element spec : specs()) {
            String rel = spec.getAttribute("path"); Path file = trunk.resolve(rel);
            var plan = child(spec, "plan", null);
            if (plan != null) {
                assertEquals(plan.getAttribute("sha256"), sha256(file.getParent().resolve("plan.json")), rel);
                var got = candidates(file, true); assertEquals("0", got[0], rel); long executable = Long.parseLong(got[2]);
                switch (plan.getAttribute("mode")) {
                    case "EXACT" -> { if (!plan.hasAttribute("explained")) assertEquals(Long.parseLong(plan.getAttribute("value")), executable, rel); }
                    case "BOUNDED" -> assertTrue(Long.parseLong(plan.getAttribute("lower")) <= executable && executable <= Long.parseLong(plan.getAttribute("upper")), rel + " " + executable);
                    default -> assertEquals("UNKNOWN", plan.getAttribute("mode"), rel);
                }
                plans++;
            }
            if (child(spec, "graph", null) != null) {
                Path export = file.getParent().resolve("meta/scenario.graph.json");
                assertEquals(child(spec, "graph", null).getAttribute("sha256"), sha256(export), rel);
                var jvm = (Map<?, ?>) Json.parse(session.validate(file).graph().json(), "jvm graph");
                var stripped = new LinkedHashMap<String, Object>(); jvm.forEach((k, v) -> stripped.put((String) k, v));
                var nodes = new ArrayList<Object>();
                for (Object n : (List<?>) jvm.get("nodes")) {
                    var node = new LinkedHashMap<String, Object>(); ((Map<?, ?>) n).forEach((k, v) -> node.put((String) k, v));
                    var attrs = new LinkedHashMap<String, Object>(); ((Map<?, ?>) node.get("attrs")).forEach((k, v) -> { if (!k.equals("chain") && !k.equals("keeps_rows")) attrs.put((String) k, v); });
                    node.put("attrs", attrs); nodes.add(node);
                }
                stripped.put("nodes", nodes);
                assertEquals(Json.parse(Files.readString(export), "legacy graph"), stripped, rel);
                graphs++;
            }
        }
        assertEquals(List.of(26, 12), List.of(plans, graphs));
    }
    @Test void everyStaticLibraryEntryOpensAndItsDocumentedCountReconciles(@TempDir Path work) throws Exception {
        Path trunk = copyTrunk(work); var entries = ScenarioLibrary.load(trunk); var session = Session.resolve(null, Map.of(), List.of());
        // 75 manifest entries: the May29 one names a spec the repository lacks, and the loader passes it over as the legacy did.
        assertEquals(74, entries.size()); assertTrue(entries.stream().noneMatch(e -> e.id().startsWith("may29_run")));
        // The release-gate ladder is made at run time by the AI platform, an application the port does not carry: listed with its documented ladder.
        var dynamic = entries.stream().filter(ScenarioLibrary.Entry::dynamic).toList();
        assertEquals(List.of("ai_combi_release_gate_breakpoint"), dynamic.stream().map(ScenarioLibrary.Entry::id).toList());
        assertEquals(dynamic.get(0).candidates(), Integer.toString(dynamic.get(0).releaseGateCandidates()));
        int reconciled = 0, runtime = 0;
        for (var entry : entries) {
            if (entry.dynamic()) continue;
            var summary = session.validate(entry.spec());
            assertTrue(summary.findings().stream().noneMatch(d -> d.severity() == Severity.ERROR), entry.id() + ": " + summary.findings());
            if ("runtime-known".equals(entry.candidates())) { runtime++; continue; }
            var counts = new HashSet<String>();
            for (boolean legacy : List.of(false, true)) { var got = candidates(entry.spec(), legacy); if (got[0].equals("0")) { counts.add(got[1]); counts.add(got[2]); } }
            String expected = STALE_LIBRARY_COUNTS.getOrDefault(entry.id(), entry.candidates());
            assertTrue(counts.contains(expected), entry.id() + ": library " + entry.candidates() + ", JVM " + counts);
            reconciled++;
        }
        assertEquals(List.of(71, 2), List.of(reconciled, runtime));
    }
}
