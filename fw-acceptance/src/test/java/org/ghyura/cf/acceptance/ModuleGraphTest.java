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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A01 (rule R06): internal modules have explicit dependencies, no cycles, and the layering of the
 * architect's modules.xml. The reference graph is the architect's file, copied unmodified and pinned by hash.
 */
class ModuleGraphTest {

    /** SHA-256 of handoff/modules.xml (architecture revision 1) as copied into the test resources. */
    private static final String MODULES_XML_SHA256 = "27d5a5be281cef584b2883d9080c0d4849095e2b05d31a97298659405dda7ff7";

    /**
     * Modules the author added after the architect's revision 1, each with the modules it may depend on: the Python add-on
     * (author decision 2026-10-12, handoff/outbox/0118-python-addon), an add-on like fw-sut-fixtures that depends on fw-model
     * alone and that only fw-acceptance uses, in test scope. The architect's file stays as it was copied.
     */
    private static final Map<String, Set<String>> AUTHOR_ADDED = Map.of("fw-python", Set.of("fw-model"));

    /** Add-ons: never a production dependency of any module; fw-acceptance alone may use them, in test scope. */
    private static final Set<String> TEST_ONLY_ADDONS = Set.of("fw-sut-fixtures", "fw-python");

    private static final String INTERNAL_GROUP = "org.ghyura.cf";
    private static final Pattern UNPINNED = Pattern.compile("(?i)(^|[\\[(,])\\s*(latest|release)\\s*([\\])]|$)|[\\[(].*,.*[\\])]");

    private record Dependency(String module, String scope) {
    }

    @Test
    void theCopiedArchitectureGraphIsTheArchitectsFileUnmodified() throws Exception {
        String text = resourceText("modules-handoff.xml");
        String withoutNotice = text.replaceAll("(?s)<!--.*?-->\\s*", "");
        assertEquals(MODULES_XML_SHA256, sha256(withoutNotice),
                "modules-handoff.xml differs from handoff/modules.xml; a new architecture revision needs a deliberate update");
        assertEquals(18, architectGraph().size());
        assertEquals(19, expectedGraph().size(), "the architect's 18 modules and the author's Python add-on");
    }

    @Test
    void reactorModulesAreExactlyTheDirectoriesThatHoldAPom() throws Exception {
        Document root = parse(Reactor.root().resolve("pom.xml"));
        List<String> declared = new ArrayList<>();
        NodeList modules = root.getDocumentElement().getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            declared.add(modules.item(i).getTextContent().trim());
        }
        assertEquals(new TreeSet<>(Reactor.modules()), new TreeSet<>(declared));
        assertEquals(declared.size(), new HashSet<>(declared).size());
    }

    @Test
    void everyReactorModuleExistsInTheArchitecture() throws Exception {
        Set<String> architecture = expectedGraph().keySet();
        for (String module : Reactor.modules()) {
            assertTrue(architecture.contains(module), module + " is not a module of the architecture");
        }
    }

    @Test
    void declaredInternalDependenciesStayInsideTheArchitecturesLayering() throws Exception {
        Map<String, Set<String>> expected = expectedGraph();
        Map<String, Set<String>> closure = transitiveClosure(expected);
        for (String module : Reactor.modules()) {
            for (Dependency dependency : internalDependencies(module)) {
                assertTrue(expected.containsKey(dependency.module()), module + " depends on unknown " + dependency.module());
                assertTrue(closure.get(module).contains(dependency.module()),
                        module + " may not depend on " + dependency.module() + " (allowed: " + closure.get(module) + ")");
            }
        }
    }

    @Test
    void theDeclaredGraphHasNoCycle() throws Exception {
        Map<String, Set<String>> declared = new LinkedHashMap<>();
        for (String module : Reactor.modules()) {
            Set<String> deps = new LinkedHashSet<>();
            for (Dependency d : internalDependencies(module)) {
                deps.add(d.module());
            }
            declared.put(module, deps);
        }
        Set<String> done = new HashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        for (String module : declared.keySet()) {
            visit(module, declared, done, visiting);
        }
        assertEquals(declared.size(), done.size());
    }

    @Test
    void testOnlyModulesNeverReachProductionAndAcceptanceIsTestScopeOnly() throws Exception {
        for (String module : Reactor.modules()) {
            for (Dependency dependency : internalDependencies(module)) {
                if (dependency.module().equals("fw-acceptance") || TEST_ONLY_ADDONS.contains(dependency.module())) {
                    assertTrue(module.equals("fw-acceptance") && dependency.scope().equals("test"),
                            module + " must not depend on " + dependency.module() + " outside test scope");
                }
            }
        }
        for (Dependency dependency : internalDependencies("fw-acceptance")) {
            assertEquals("test", dependency.scope(), "fw-acceptance is a test-only artifact: " + dependency.module());
        }
    }

    @Test
    void everyPluginAndDependencyVersionIsPinnedExactly() throws Exception {
        List<Path> poms = new ArrayList<>(Reactor.files("pom.xml"));
        assertTrue(poms.size() >= 5, "found only " + poms.size() + " poms");
        for (Path pom : poms) {
            Document document = parse(pom);
            for (String tag : List.of("plugin", "dependency", "parent")) {
                NodeList nodes = document.getElementsByTagName(tag);
                for (int i = 0; i < nodes.getLength(); i++) {
                    Element element = (Element) nodes.item(i);
                    String version = childText(element, "version");
                    if (version != null) {
                        assertFalse(version.endsWith("-SNAPSHOT") && !version.equals("0.1.0-SNAPSHOT"),
                                pom + ": snapshot dependency " + version);
                        assertFalse(UNPINNED.matcher(version).find(), pom + ": unpinned version '" + version + "'");
                    }
                }
            }
        }
        // every plugin the build uses is pinned in the parent's pluginManagement
        Document parent = parse(Reactor.root().resolve("pom.xml"));
        NodeList managed = ((Element) parent.getElementsByTagName("pluginManagement").item(0)).getElementsByTagName("plugin");
        assertTrue(managed.getLength() >= 7);
        for (int i = 0; i < managed.getLength(); i++) {
            Element plugin = (Element) managed.item(i);
            assertTrue(childText(plugin, "version") != null, "unpinned managed plugin " + childText(plugin, "artifactId"));
        }
    }

    @Test
    void noPomDeclaresABannedDependencyAndProductionModulesDeclareNoUnapprovedThirdParty() throws Exception {
        assertTrue(BannedDependencies.isBanned("org.postgresql", "postgresql"));
        assertTrue(BannedDependencies.isBanned("org.hibernate.orm", "hibernate-core"));
        assertTrue(BannedDependencies.isBanned("com.zaxxer", "HikariCP"));
        assertFalse(BannedDependencies.isBanned("com.zaxxer", "SparseBitSet"));
        assertFalse(BannedDependencies.isBanned("org.hibernateish", "x"));
        assertFalse(BannedDependencies.isBanned("org.junit.jupiter", "junit-jupiter"));

        // D10 input libraries, the author-requested FlatLaf desktop (0162) and JavaParser for the candidate code preview's
        // Java formatting (2026-10-19; Apache-2.0, in the third-party inventory).
        Set<String> approvedProduction = Set.of("com.fasterxml.jackson.core:jackson-core", "org.apache.poi:poi-ooxml",
                "com.formdev:flatlaf", "com.github.javaparser:javaparser-core");
        for (Path pom : Reactor.files("pom.xml")) {
            Document document = parse(pom);
            NodeList dependencies = document.getElementsByTagName("dependency");
            for (int i = 0; i < dependencies.getLength(); i++) {
                Element dependency = (Element) dependencies.item(i);
                String group = childText(dependency, "groupId");
                String artifact = childText(dependency, "artifactId");
                assertFalse(BannedDependencies.isBanned(group, artifact), pom + ": banned dependency " + group + ":" + artifact);
                String scope = childText(dependency, "scope");
                boolean testOnly = "test".equals(scope);
                boolean internal = INTERNAL_GROUP.equals(group);
                boolean inDependencyManagement = isUnder(dependency, "dependencyManagement");
                if (!internal && !testOnly && !inDependencyManagement) {
                    assertTrue(approvedProduction.contains(group + ":" + artifact),
                            pom + ": production dependency " + group + ":" + artifact + " has not been approved");
                }
            }
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private static boolean isUnder(Element element, String ancestorTag) {
        for (Node n = element.getParentNode(); n != null; n = n.getParentNode()) {
            if (n instanceof Element e && e.getTagName().equals(ancestorTag)) {
                return true;
            }
        }
        return false;
    }

    /** The architect's graph and the modules the author added, which fw-acceptance may use in test scope. */
    private static Map<String, Set<String>> expectedGraph() throws Exception {
        Map<String, Set<String>> graph = architectGraph();
        for (Map.Entry<String, Set<String>> added : AUTHOR_ADDED.entrySet()) {
            graph.put(added.getKey(), new LinkedHashSet<>(added.getValue()));
            graph.get("fw-acceptance").add(added.getKey());
        }
        return graph;
    }

    private static Map<String, Set<String>> architectGraph() throws Exception {
        Document document = parse(resourceBytes("modules-handoff.xml"));
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        NodeList modules = document.getDocumentElement().getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            Element module = (Element) modules.item(i);
            Set<String> deps = new LinkedHashSet<>();
            NodeList dependsOn = module.getElementsByTagName("depends-on");
            for (int j = 0; j < dependsOn.getLength(); j++) {
                deps.add(((Element) dependsOn.item(j)).getAttribute("module"));
            }
            graph.put(module.getAttribute("id"), deps);
        }
        return graph;
    }

    private static Map<String, Set<String>> transitiveClosure(Map<String, Set<String>> graph) {
        Map<String, Set<String>> closure = new LinkedHashMap<>();
        for (String module : graph.keySet()) {
            Set<String> reach = new LinkedHashSet<>();
            collect(module, graph, reach);
            closure.put(module, reach);
        }
        return closure;
    }

    private static void collect(String module, Map<String, Set<String>> graph, Set<String> reach) {
        for (String dep : graph.getOrDefault(module, Set.of())) {
            if (reach.add(dep)) {
                collect(dep, graph, reach);
            }
        }
    }

    private static void visit(String module, Map<String, Set<String>> graph, Set<String> done, Set<String> visiting) {
        if (done.contains(module)) {
            return;
        }
        assertTrue(visiting.add(module), "dependency cycle through " + visiting + " -> " + module);
        for (String dep : graph.getOrDefault(module, Set.of())) {
            visit(dep, graph, done, visiting);
        }
        visiting.remove(module);
        done.add(module);
    }

    private static List<Dependency> internalDependencies(String module) throws Exception {
        Document document = parse(Reactor.root().resolve(module).resolve("pom.xml"));
        List<Dependency> result = new ArrayList<>();
        Element project = document.getDocumentElement();
        Node child = project.getFirstChild();
        while (child != null) {
            if (child instanceof Element section && section.getTagName().equals("dependencies")) {
                NodeList dependencies = section.getElementsByTagName("dependency");
                for (int i = 0; i < dependencies.getLength(); i++) {
                    Element dependency = (Element) dependencies.item(i);
                    if (INTERNAL_GROUP.equals(childText(dependency, "groupId"))) {
                        String scope = childText(dependency, "scope");
                        result.add(new Dependency(childText(dependency, "artifactId"), scope == null ? "compile" : scope));
                    }
                }
            }
            child = child.getNextSibling();
        }
        return result;
    }

    private static String childText(Element parent, String tag) {
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element e && e.getTagName().equals(tag)) {
                return e.getTextContent().trim();
            }
            child = child.getNextSibling();
        }
        return null;
    }

    private static Document parse(Path file) throws Exception {
        return parse(Files.readAllBytes(file));
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
    }

    private static byte[] resourceBytes(String name) throws IOException {
        try (InputStream in = ModuleGraphTest.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IllegalStateException("missing test resource " + name);
            }
            return in.readAllBytes();
        }
    }

    private static String resourceText(String name) throws IOException {
        return new String(resourceBytes(name), StandardCharsets.UTF_8);
    }

    private static String sha256(String text) throws NoSuchAlgorithmException {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b & 0xFF));
        }
        return hex.toString();
    }
}
