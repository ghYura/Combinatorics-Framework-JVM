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

import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The verified-scenario library of the legacy Face 1 desktops (intake/scenario_library.py), read as the legacy read it: the
 * entries of a generator tree's intake manifests (automation_scenarios.json, then verified_examples.json), each with an id
 * and either a spec file inside the tree or, for a scenario its application makes fresh, the release-gate materializer with
 * its run profile, a doubling ladder of difficulty units and a source inside the tree; an entry without a suite belongs to
 * "Verified examples". A static entry opens as the spec document it names. A dynamic one needs the application that makes
 * it, which the port does not carry (author scope), and is listed with the candidate count of its ladder.
 */
public final class ScenarioLibrary {

    public static final String DEFAULT_SUITE_ID = "verified_examples";
    public static final String DEFAULT_SUITE_TITLE = "Verified examples";
    static final String RELEASE_GATE_MATERIALIZER = "release_gate_breakpoint";
    static final String AI_RELEASE_GATE_PROFILE = "ai_combi_release_gate_breakpoint";
    static final List<Long> RELEASE_GATE_DEFAULT_DIFFICULTY_UNITS = List.of(2L, 4L, 8L, 16L, 32L);

    /**
     * A catalog entry: what the library says of the scenario (its expected counts are the documented ones) and where it is.
     *
     * @param spec   the spec document of a static entry, null for a dynamic one
     * @param source the materializer's source of a dynamic entry, null for a static one
     */
    public record Entry(String id, String title, String blurb, String candidates, String pass, String analyzer, String suite,
                        String suiteTitle, Path spec, String materializer, Path source, String sourceLabel, int releaseGateCandidates) {

        public Entry {
            Objects.requireNonNull(id, "id");
        }

        /** Made fresh by its application, not read from a spec document. */
        public boolean dynamic() {
            return !materializer.isEmpty();
        }
    }

    /** A suite: its id and title, in manifest order. */
    public record Suite(String id, String title) {
    }

    private ScenarioLibrary() {
    }

    /** load_scenarios of a generator tree: its intake/automation_scenarios.json, then its intake/verified_examples.json. */
    public static List<Entry> load(Path generatorRoot) {
        return load(generatorRoot, List.of(generatorRoot.resolve("intake").resolve("automation_scenarios.json"),
                generatorRoot.resolve("intake").resolve("verified_examples.json")));
    }

    /** load_scenarios with explicit manifests: a manifest that cannot be read or is not a list is passed over. */
    public static List<Entry> load(Path generatorRoot, List<Path> manifests) {
        Path root = real(generatorRoot);
        List<Object> raw = new ArrayList<>();
        for (Path manifest : manifests) {
            Object document;
            try {
                document = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8), manifest.toString());
            } catch (IOException | FrameworkException e) {
                continue;
            }
            if (document instanceof List<?> list) {
                raw.addAll(list);
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (Object o : raw) {
            if (!(o instanceof Map<?, ?> item) || !truthy(item.get("id"))) {
                continue;
            }
            String materializer = str(or(item.get("spec_materializer"), ""));
            Path spec = null;
            Path source = null;
            int ladder = 0;
            if (!materializer.isEmpty()) {
                if (!materializer.equals(RELEASE_GATE_MATERIALIZER) || !str(or(item.get("run_profile"), "")).equals(AI_RELEASE_GATE_PROFILE)) {
                    continue;
                }
                List<Long> units = difficultyUnits(item);
                if (units == null || !truthy(item.get("source"))) {
                    continue;
                }
                source = inside(root, str(item.get("source")));
                if (source == null) {
                    continue;
                }
                ladder = units.size() * 512;
            } else {
                if (!truthy(item.get("spec"))) {
                    continue;
                }
                spec = inside(root, str(item.get("spec")));
                if (spec == null) {
                    continue;
                }
            }
            Object suite = item.containsKey("suite") ? item.get("suite") : DEFAULT_SUITE_ID;
            Object suiteTitle = item.containsKey("suite_title") ? item.get("suite_title") : DEFAULT_SUITE_TITLE;
            String label = !materializer.isEmpty() ? "fresh " + materializer + " from " + str(or(item.get("source"), "?"))
                    : str(or(item.get("spec"), "?"));
            entries.add(new Entry(str(item.get("id")), text(item.get("title")), text(item.get("blurb")), text(item.get("candidates")),
                    text(item.get("pass")), text(item.get("analyzer")), str(or(suite, DEFAULT_SUITE_ID)), str(or(suiteTitle, DEFAULT_SUITE_TITLE)),
                    spec, materializer, source, label, ladder));
        }
        return Collections.unmodifiableList(entries);
    }

    /** suite_catalog: the suites in manifest order, each with the title its first entry gives it. */
    public static List<Suite> suites(List<Entry> entries) {
        List<Suite> suites = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Entry e : entries) {
            if (seen.add(e.suite())) {
                suites.add(new Suite(e.suite(), e.suiteTitle()));
            }
        }
        return suites;
    }

    /** scenarios_in_suite. */
    public static List<Entry> inSuite(List<Entry> entries, String suiteId) {
        return entries.stream().filter(e -> e.suite().equals(suiteId)).toList();
    }

    /** _release_gate_difficulty_units: one to six powers of two from 2 to 64, each double the one before; null when refused. */
    private static List<Long> difficultyUnits(Map<?, ?> item) {
        Object raw = item.containsKey("difficulty_units") ? item.get("difficulty_units") : RELEASE_GATE_DEFAULT_DIFFICULTY_UNITS;
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<Long> units = new ArrayList<>();
        for (Object v : list) {
            if (!(v instanceof Long n)) {
                return null;
            }
            units.add(n);
        }
        if (units.isEmpty() || units.size() > 6) {
            return null;
        }
        for (long v : units) {
            if (v < 2 || v > 64 || (v & (v - 1)) != 0) {
                return null;
            }
        }
        for (int i = 1; i < units.size(); i++) {
            if (units.get(i) != units.get(i - 1) * 2) {
                return null;
            }
        }
        return units;
    }

    /** The file a manifest path names when it is a regular file inside the tree, symbolic links followed; otherwise null. */
    private static Path inside(Path root, String relative) {
        Path path = root.resolve(relative).normalize();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        Path resolved = real(path);
        return resolved.startsWith(root) ? resolved : null;
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path.toAbsolutePath().normalize();
        }
    }

    private static Object or(Object value, Object otherwise) {
        return truthy(value) ? value : otherwise;
    }

    /** Python truthiness of a JSON value. */
    private static boolean truthy(Object v) {
        return switch (v) {
            case null -> false;
            case Boolean b -> b;
            case String s -> !s.isEmpty();
            case Long n -> n != 0;
            case BigDecimal d -> d.signum() != 0;
            case List<?> l -> !l.isEmpty();
            case Map<?, ?> m -> !m.isEmpty();
            default -> true;
        };
    }

    /** Python str() of a JSON value, as far as a catalog entry's ids and paths need it. */
    private static String str(Object v) {
        return switch (v) {
            case null -> "None";
            case Boolean b -> b ? "True" : "False";
            case String s -> s;
            default -> String.valueOf(v);
        };
    }

    /** A descriptive field as shown: absent or null is empty. */
    private static String text(Object v) {
        return v == null ? "" : str(v);
    }
}
