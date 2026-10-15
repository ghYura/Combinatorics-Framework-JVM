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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Turns the build's own test reports into machine-readable gate evidence, and refuses to write evidence that
 * the reports do not support. Java utility (R01).
 *
 * <pre>
 * java tools/EvidenceReport.java --root . --mapping GATE_MAPPING.xml --out EVIDENCE.xml
 *        --command "mvn -B clean verify" --baseline "text" [--maven 3.9.15] [--handoff ../handoff]
 * </pre>
 *
 * Provenance (review HG10): the JDK, operating system, Maven version and exact command; with {@code --handoff}
 * the architecture revision named by CLAUDE_HANDOFF.xml and the SHA-256 of every architect-owned handoff file
 * (the outbox is the developer's and is left out); and a source manifest, the SHA-256 over the sorted list of
 * every project source file with its own SHA-256, which identifies the source state without version control.
 *
 * The mapping names, per gate case, the tests that evidence it (class and method). Every cited test must exist
 * in a Surefire or Failsafe report and have passed, otherwise the tool exits 1 and writes nothing. A case may
 * be declared {@code status="open"} with a reason; it then cites no test and is reported as open, never as met.
 * Parameterized tests count as passed only when every invocation passed.
 */
public final class EvidenceReport {

    private record Result(String className, String name, String outcome) {
    }

    private record Suite(String name, int tests, int failures, int errors, int skipped, String report) {
    }

    private EvidenceReport() {
    }

    public static void main(String[] args) throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        for (String required : List.of("--root", "--mapping", "--out", "--command")) {
            if (!options.containsKey(required)) {
                System.err.println("missing " + required);
                System.exit(2);
            }
        }
        Path root = Paths.get(options.get("--root")).toAbsolutePath().normalize();
        List<Suite> suites = new ArrayList<>();
        List<Result> results = new ArrayList<>();
        for (Path report : reports(root)) {
            readReport(root, report, suites, results);
        }
        if (suites.isEmpty()) {
            System.err.println("no Surefire or Failsafe reports under " + root + "; run the build first");
            System.exit(1);
        }

        Document mapping = parse(Files.readAllBytes(Paths.get(options.get("--mapping"))));
        StringBuilder gates = new StringBuilder();
        List<String> problems = new ArrayList<>();
        NodeList gateNodes = mapping.getDocumentElement().getElementsByTagName("gate");
        for (int g = 0; g < gateNodes.getLength(); g++) {
            Element gate = (Element) gateNodes.item(g);
            gates.append("    <gate id=\"").append(escape(gate.getAttribute("id"))).append("\" name=\"")
                    .append(escape(gate.getAttribute("name"))).append("\">\n");
            NodeList caseNodes = gate.getElementsByTagName("case");
            for (int c = 0; c < caseNodes.getLength(); c++) {
                Element testCase = (Element) caseNodes.item(c);
                appendCase(gates, testCase, gate.getAttribute("id"), results, problems);
            }
            gates.append("    </gate>\n");
        }
        if (!problems.isEmpty()) {
            System.err.println("the evidence mapping is not supported by the test reports:");
            problems.forEach(p -> System.err.println("  - " + p));
            System.exit(1);
        }

        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        for (Suite s : suites) {
            tests += s.tests();
            failures += s.failures();
            errors += s.errors();
            skipped += s.skipped();
        }
        StringBuilder xml = new StringBuilder(16384);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<evidence package=\"").append(escape(mapping.getDocumentElement().getAttribute("package")))
                .append("\" architecture-revision=\"")
                .append(escape(mapping.getDocumentElement().getAttribute("architecture-revision")))
                .append("\" generated=\"").append(LocalDate.now()).append("\">\n");
        xml.append("  <environment jdk=\"").append(escape(System.getProperty("java.runtime.version")))
                .append("\" os=\"").append(escape(System.getProperty("os.name") + " " + System.getProperty("os.version")))
                .append("\" arch=\"").append(escape(System.getProperty("os.arch")))
                .append("\" maven=\"").append(escape(options.getOrDefault("--maven", "unrecorded"))).append("\"/>\n");
        xml.append("  <toolchain java-vendor=\"").append(escape(System.getProperty("java.vendor"))).append("\" java-vm=\"")
                .append(escape(System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version"))).append("\"/>\n");
        xml.append("  <command>").append(escape(options.get("--command"))).append("</command>\n");
        if (options.containsKey("--handoff")) {
            appendHandoff(xml, Paths.get(options.get("--handoff")).toAbsolutePath().normalize());
        }
        appendSourceManifest(xml, root);
        xml.append("  <baseline>").append(escape(options.getOrDefault("--baseline", "unrecorded"))).append("</baseline>\n");
        xml.append("  <summary suites=\"").append(suites.size()).append("\" tests=\"").append(tests)
                .append("\" failures=\"").append(failures).append("\" errors=\"").append(errors)
                .append("\" skipped=\"").append(skipped).append("\"/>\n");
        xml.append("  <gates>\n").append(gates).append("  </gates>\n");
        xml.append("  <suites>\n");
        for (Suite s : suites) {
            xml.append("    <suite name=\"").append(escape(s.name())).append("\" tests=\"").append(s.tests())
                    .append("\" failures=\"").append(s.failures()).append("\" errors=\"").append(s.errors())
                    .append("\" skipped=\"").append(s.skipped()).append("\" report=\"").append(escape(s.report()))
                    .append("\"/>\n");
        }
        xml.append("  </suites>\n");
        xml.append("</evidence>\n");

        Path out = Paths.get(options.get("--out")).toAbsolutePath();
        Path temporary = out.resolveSibling(out.getFileName() + ".tmp");
        Files.writeString(temporary, xml, StandardCharsets.UTF_8);
        Files.move(temporary, out, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        System.out.println("wrote " + out + ": " + suites.size() + " suites, " + tests + " tests, " + failures
                + " failures, " + errors + " errors, " + skipped + " skipped");
        if (failures + errors > 0) {
            System.exit(1);
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static void appendHandoff(StringBuilder xml, Path handoff) throws Exception {
        Path entry = handoff.resolveSibling("CLAUDE_HANDOFF.xml");
        String revision = Files.isRegularFile(entry)
                ? parse(Files.readAllBytes(entry)).getDocumentElement().getAttribute("revision") : "unrecorded";
        List<Path> files = new ArrayList<>();
        if (Files.isRegularFile(entry)) {
            files.add(entry);
        }
        try (Stream<Path> walk = Files.walk(handoff)) {
            walk.filter(Files::isRegularFile).filter(p -> !handoff.relativize(p).startsWith("outbox"))
                    .filter(p -> !p.getFileName().toString().startsWith(".")).sorted().forEach(files::add);
        }
        xml.append("  <handoff revision=\"").append(escape(revision)).append("\" files=\"").append(files.size()).append("\">\n");
        for (Path file : files) {
            xml.append("    <file path=\"").append(escape(handoff.getParent().relativize(file).toString())).append("\" sha256=\"")
                    .append(sha256(Files.readAllBytes(file))).append("\"/>\n");
        }
        xml.append("  </handoff>\n");
    }

    private static void appendSourceManifest(StringBuilder xml, Path root) throws Exception {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !root.relativize(p).toString().contains("target/") && !root.relativize(p).startsWith(".mvn"))
                    .filter(p -> root.relativize(p).toString().contains("src/") || p.getFileName().toString().equals("pom.xml")
                            || root.relativize(p).startsWith("tools"))
                    .sorted().toList();
        }
        StringBuilder listing = new StringBuilder();
        for (Path file : files) {
            listing.append(sha256(Files.readAllBytes(file))).append("  ").append(root.relativize(file)).append('\n');
        }
        xml.append("  <source-manifest files=\"").append(files.size()).append("\" sha256=\"")
                .append(sha256(listing.toString().getBytes(StandardCharsets.UTF_8))).append("\"/>\n");
    }

    private static void appendCase(StringBuilder gates, Element testCase, String gateId, List<Result> results,
                                   List<String> problems) {
        String id = testCase.getAttribute("id");
        String status = testCase.getAttribute("status");
        String where = gateId + "/" + id;
        gates.append("      <case id=\"").append(escape(id)).append("\" status=\"").append(escape(status))
                .append("\" description=\"").append(escape(testCase.getAttribute("description"))).append("\">\n");
        NodeList tests = testCase.getElementsByTagName("test");
        if (status.equals("open")) {
            if (tests.getLength() > 0) {
                problems.add(where + " is open but cites tests");
            }
            NodeList reasons = testCase.getElementsByTagName("blocked-by");
            if (reasons.getLength() == 0) {
                problems.add(where + " is open without a blocked-by reason");
            }
            for (int i = 0; i < reasons.getLength(); i++) {
                Element reason = (Element) reasons.item(i);
                gates.append("        <blocked-by package=\"").append(escape(reason.getAttribute("package"))).append("\">")
                        .append(escape(reason.getTextContent().strip())).append("</blocked-by>\n");
            }
        } else if (!status.equals("verified")) {
            problems.add(where + " has unknown status '" + status + "'");
        } else if (tests.getLength() == 0) {
            problems.add(where + " is verified but cites no test");
        }
        for (int i = 0; i < tests.getLength(); i++) {
            Element test = (Element) tests.item(i);
            String className = test.getAttribute("class");
            String method = test.getAttribute("method");
            int invocations = 0;
            int passed = 0;
            for (Result r : results) {
                if (r.className().equals(className) && (r.name().equals(method) || r.name().startsWith(method + "("))) {
                    invocations++;
                    if (r.outcome().equals("passed")) {
                        passed++;
                    }
                }
            }
            String outcome = invocations == 0 ? "missing" : passed == invocations ? "passed" : "not-passed";
            if (!outcome.equals("passed") && status.equals("verified")) {
                problems.add(where + " cites " + className + "#" + method + " which is " + outcome);
            }
            gates.append("        <test class=\"").append(escape(className)).append("\" method=\"")
                    .append(escape(method)).append("\" result=\"").append(outcome).append("\" invocations=\"")
                    .append(invocations).append("\"/>\n");
        }
        NodeList notes = testCase.getElementsByTagName("note");
        for (int i = 0; i < notes.getLength(); i++) {
            gates.append("        <note>").append(escape(notes.item(i).getTextContent().strip())).append("</note>\n");
        }
        gates.append("      </case>\n");
    }

    private static List<Path> reports(Path root) throws IOException {
        List<Path> reports = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.getFileName().toString().startsWith("TEST-") && p.getFileName().toString().endsWith(".xml"))
                    .filter(p -> p.toString().contains("surefire-reports") || p.toString().contains("failsafe-reports"))
                    .sorted().forEach(reports::add);
        }
        return reports;
    }

    private static void readReport(Path root, Path report, List<Suite> suites, List<Result> results) throws Exception {
        Document document = parse(Files.readAllBytes(report));
        Element suite = document.getDocumentElement();
        suites.add(new Suite(suite.getAttribute("name"), Integer.parseInt(suite.getAttribute("tests")),
                Integer.parseInt(suite.getAttribute("failures")), Integer.parseInt(suite.getAttribute("errors")),
                Integer.parseInt(suite.getAttribute("skipped")), root.relativize(report).toString()));
        NodeList cases = suite.getElementsByTagName("testcase");
        for (int i = 0; i < cases.getLength(); i++) {
            Element testCase = (Element) cases.item(i);
            String outcome = "passed";
            if (testCase.getElementsByTagName("failure").getLength() > 0) {
                outcome = "failed";
            } else if (testCase.getElementsByTagName("error").getLength() > 0) {
                outcome = "error";
            } else if (testCase.getElementsByTagName("skipped").getLength() > 0) {
                outcome = "skipped";
            }
            results.add(new Result(testCase.getAttribute("classname"), testCase.getAttribute("name"), outcome));
        }
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml));
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
