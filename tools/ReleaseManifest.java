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
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * The release manifest of a finished build (M10; the legacy bundle release): what this release is, assembled only from
 * what the build left behind, asserting nothing it did not observe. It records the source tree (this tree has no version
 * control, so the hash of its file listing stands for a revision), the toolchain, the hashes of the release inputs, the
 * capability counts the jar itself reports, the bundled third-party components (also written as a CycloneDX 1.5 SBOM), the
 * test totals of every surefire and failsafe report, and every skip classified by the legacy rule: unrecognised reasons are
 * BLOCKING_UNEXPECTED, so a new skip stops the gate instead of being tolerated, and a missing authorised backend blocks a
     * release too. Status: FAILED with any failure or error, BLOCKED with missing required evidence or a release-blocking
     * skip, otherwise READY. Generate after a frozen-source clean verify; existing reports do not attest source freshness.
 *
 * <pre>
 * java tools/ReleaseManifest.java --root . --out MANIFEST.xml --sbom SBOM.xml [--maven VERSION]
 * java tools/ReleaseManifest.java --classify REASON...      (prints the class of each skip reason, one per line)
 * </pre>
 */
public final class ReleaseManifest {

    static final String SCHEMA = "cf.release-manifest/v1";
    static final String EXPECTED_OPTIONAL = "EXPECTED_OPTIONAL";
    static final String UNSUPPORTED_PLATFORM = "UNSUPPORTED_PLATFORM";
    static final String MISSING_AUTHORIZED_BACKEND = "MISSING_AUTHORIZED_BACKEND";
    static final String BLOCKING_UNEXPECTED = "BLOCKING_UNEXPECTED";
    static final List<String> CLASSES = List.of(EXPECTED_OPTIONAL, UNSUPPORTED_PLATFORM, MISSING_AUTHORIZED_BACKEND, BLOCKING_UNEXPECTED);
    static final List<String> REQUIRED_INPUTS = List.of("fw-app/target/fw-app.jar", "fw-app/src/main/resources/org/ghyura/cf/app/cli-contract.xml",
            "fw-model/src/main/resources/org/ghyura/cf/model/error-taxonomy.xml", "fw-config/src/main/resources/org/ghyura/cf/config/compatibility-deltas.xml",
            "fw-acceptance/src/test/resources/feature-mapping.xml", "fw-app/src/main/resources/META-INF/cf-third-party.xml");

    /**
     * Reason patterns, first match wins: the legacy release.py list unchanged, then this build's own reasons (the isolation
     * tools, the reference and SUT checkouts beside the project, an account that defeats a read-only check). An all-capitals
     * pattern matches the reason as written, any other one its lower case.
     */
    static final List<String[]> PATTERNS = List.of(
            new String[]{"EXPECTED_OPTIONAL", EXPECTED_OPTIONAL},
            new String[]{"UNSUPPORTED_PLATFORM", UNSUPPORTED_PLATFORM},
            new String[]{"MISSING_AUTHORIZED_BACKEND", MISSING_AUTHORIZED_BACKEND},
            new String[]{"could not import ['\"]?grpc", EXPECTED_OPTIONAL},
            new String[]{"analyzer\\b.*not present", EXPECTED_OPTIONAL},
            new String[]{"opt-in|explicitly enabled|live e2e|set [a-z0-9_]+=1", EXPECTED_OPTIONAL},
            new String[]{"refus\\w+ to delete|would delete a real", EXPECTED_OPTIONAL},
            new String[]{"requires? linux|not supported on (macos|windows)|platform", UNSUPPORTED_PLATFORM},
            new String[]{"docker|podman|container runtime|bubblewrap|rootless", MISSING_AUTHORIZED_BACKEND},
            new String[]{"postgres|database not reachable|no database", MISSING_AUTHORIZED_BACKEND},
            new String[]{"jdk|java (not )?available|maven|jar (missing|not present)", MISSING_AUTHORIZED_BACKEND},
            new String[]{"BUNDLE_SUT_ROOT|sibling (sut )?checkout|automation_constructor", MISSING_AUTHORIZED_BACKEND},
            new String[]{"\\bbwrap\\b|\\bstrace\\b", MISSING_AUTHORIZED_BACKEND},
            new String[]{"(legacy|sut) checkout is not beside the project", MISSING_AUTHORIZED_BACKEND},
            new String[]{"running as root", UNSUPPORTED_PLATFORM});

    /** The class of one skip reason; an empty or unrecognised one is BLOCKING_UNEXPECTED. */
    static String classify(String reason) {
        String text = reason == null ? "" : reason.strip();
        if (text.isEmpty()) {
            return BLOCKING_UNEXPECTED;
        }
        for (String[] p : PATTERNS) {
            boolean upper = p[0].equals(p[0].toUpperCase(java.util.Locale.ROOT)) && !p[0].equals(p[0].toLowerCase(java.util.Locale.ROOT));
            Pattern pattern = upper ? Pattern.compile(p[0]) : Pattern.compile(p[0], Pattern.CASE_INSENSITIVE);
            if (pattern.matcher(upper ? text : text.toLowerCase(java.util.Locale.ROOT)).find()) {
                return p[1];
            }
        }
        return BLOCKING_UNEXPECTED;
    }

    private record Skip(String test, String reason, String klass) {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--classify")) {
            for (int i = 1; i < args.length; i++) {
                System.out.println(classify(args[i]));
            }
            return;
        }
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        Path root = Path.of(options.getOrDefault("--root", ".")).toAbsolutePath().normalize();
        Path out = Path.of(options.get("--out"));
        Path sbom = Path.of(options.get("--sbom"));
        Path jar = root.resolve("fw-app/target/fw-app.jar");

        int tests = 0;
        int failures = 0;
        int errors = 0;
        int skipped = 0;
        int suites = 0;
        List<Skip> skips = new ArrayList<>();
        List<String> missingEvidence = new ArrayList<>();
        for (String input : REQUIRED_INPUTS) {
            if (!Files.isRegularFile(root.resolve(input))) missingEvidence.add("Missing required input: " + input);
        }
        List<Path> reports = expectedReports(root, missingEvidence);
        for (Path report : reports) {
            if (!Files.isRegularFile(report)) {
                missingEvidence.add("Missing test report: " + root.relativize(report));
                continue;
            }
            Element suite = parse(Files.readAllBytes(report)).getDocumentElement();
            suites++;
            tests += Integer.parseInt(suite.getAttribute("tests"));
            if (Integer.parseInt(suite.getAttribute("tests")) == 0) {
                missingEvidence.add("Empty test report: " + root.relativize(report));
            }
            failures += Integer.parseInt(suite.getAttribute("failures"));
            errors += Integer.parseInt(suite.getAttribute("errors"));
            skipped += Integer.parseInt(suite.getAttribute("skipped"));
            NodeList cases = suite.getElementsByTagName("testcase");
            for (int i = 0; i < cases.getLength(); i++) {
                Element c = (Element) cases.item(i);
                NodeList s = c.getElementsByTagName("skipped");
                if (s.getLength() > 0) {
                    String reason = ((Element) s.item(0)).getAttribute("message");
                    skips.add(new Skip(c.getAttribute("classname") + "." + c.getAttribute("name"), reason, classify(reason)));
                }
            }
        }
        if (tests == 0) missingEvidence.add("No tests were reported");
        if (skipped != skips.size()) missingEvidence.add("Skipped-test totals do not match the classified cases");
        Map<String, Integer> byClass = new LinkedHashMap<>();
        CLASSES.forEach(k -> byClass.put(k, 0));
        skips.forEach(s -> byClass.merge(s.klass(), 1, Integer::sum));
        long blocking = skips.stream().filter(s -> s.klass().equals(BLOCKING_UNEXPECTED) || s.klass().equals(MISSING_AUTHORIZED_BACKEND)).count();
        StringBuilder capabilityXml = new StringBuilder();
        if (!appendCapabilities(capabilityXml, jar)) missingEvidence.add("The packaged application's capabilities were not observed");
        Document thirdParty = thirdParty(jar);
        if (thirdParty == null || thirdParty.getElementsByTagName("component").getLength() == 0) {
            missingEvidence.add("The packaged third-party inventory is missing or empty");
        }
        String status = failures + errors > 0 ? "FAILED" : blocking > 0 || !missingEvidence.isEmpty() ? "BLOCKED" : "READY";

        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<release-manifest schema=\"").append(SCHEMA).append("\" status=\"").append(status).append("\">\n");
        appendSource(xml, root);
        xml.append("  <toolchain java=\"").append(escape(System.getProperty("java.version"))).append("\" vendor=\"")
                .append(escape(System.getProperty("java.vendor"))).append("\" maven=\"").append(escape(options.getOrDefault("--maven", "unknown")))
                .append("\" os=\"").append(escape(System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch")))
                .append("\"/>\n");
        xml.append("  <inputs>\n");
        for (String input : REQUIRED_INPUTS) {
            Path p = root.resolve(input);
            if (Files.isRegularFile(p)) {
                byte[] bytes = Files.readAllBytes(p);
                xml.append("    <input path=\"").append(input).append("\" bytes=\"").append(bytes.length).append("\" sha256=\"").append(sha256(bytes)).append("\"/>\n");
            } else {
                xml.append("    <input path=\"").append(input).append("\" missing=\"true\"/>\n");
            }
        }
        xml.append("  </inputs>\n");
        xml.append(capabilityXml);
        xml.append("  <evidence expectedSuites=\"").append(reports.size()).append("\" blockers=\"").append(missingEvidence.size()).append("\">\n");
        for (String problem : missingEvidence) {
            xml.append("    <blocker>").append(escape(problem)).append("</blocker>\n");
        }
        xml.append("  </evidence>\n");
        byte[] cyclone = cycloneDx(thirdParty, root).getBytes(StandardCharsets.UTF_8);
        Files.write(sbom, cyclone);
        NodeList components = thirdParty == null ? null : thirdParty.getElementsByTagName("component");
        xml.append("  <sbom format=\"CycloneDX/1.5\" path=\"").append(escape(sbom.getFileName().toString())).append("\" sha256=\"").append(sha256(cyclone))
                .append("\" components=\"").append(components == null ? 0 : components.getLength()).append("\"/>\n");
        xml.append("  <tests suites=\"").append(suites).append("\" tests=\"").append(tests).append("\" failures=\"").append(failures)
                .append("\" errors=\"").append(errors).append("\" skipped=\"").append(skipped).append("\"/>\n");
        xml.append("  <skips total=\"").append(skips.size()).append("\" releaseBlocking=\"").append(blocking).append('"');
        byClass.forEach((k, v) -> xml.append(' ').append(k).append("=\"").append(v).append('"'));
        xml.append(skips.isEmpty() ? "/>\n" : ">\n");
        for (Skip s : skips) {
            xml.append("    <skip test=\"").append(escape(s.test())).append("\" class=\"").append(s.klass()).append("\" reason=\"").append(escape(s.reason()))
                    .append("\"/>\n");
        }
        if (!skips.isEmpty()) {
            xml.append("  </skips>\n");
        }
        xml.append("  <residuals>\n");
        if (components != null) {
            for (int i = 0; i < components.getLength(); i++) {
                Element c = (Element) components.item(i);
                if (!c.getAttribute("residual").isBlank()) {
                    xml.append("    <residual component=\"").append(escape(c.getAttribute("artifact"))).append("\">").append(escape(c.getAttribute("residual")))
                            .append("</residual>\n");
                }
            }
        }
        String terms = projectTerms(root, jar);
        if (terms != null) {
            xml.append("    <residual>").append(escape(terms)).append("</residual>\n");
        }
        xml.append("  </residuals>\n</release-manifest>\n");
        Files.writeString(out, xml.toString(), StandardCharsets.UTF_8);
        System.out.println("release manifest " + status + ": " + tests + " tests, " + failures + " failures, " + errors + " errors, " + skipped + " skipped ("
                + blocking + " release-blocking) -> " + out);
    }

    /** This reactor uses Maven's standard test names; require each suite, including integration tests. */
    private static List<Path> expectedReports(Path root, List<String> missing) throws Exception {
        List<Path> reports = new ArrayList<>();
        NodeList modules = parse(Files.readAllBytes(root.resolve("pom.xml"))).getElementsByTagName("module");
        for (int i = 0; i < modules.getLength(); i++) {
            Path module = root.resolve(modules.item(i).getTextContent().strip());
            if (!Files.isRegularFile(module.resolve("pom.xml"))) missing.add("Missing module: " + root.relativize(module));
            Path source = module.resolve("src/test/java");
            if (!Files.isDirectory(source)) continue;
            try (Stream<Path> walk = Files.walk(source)) {
                for (Path file : walk.filter(Files::isRegularFile).sorted().toList()) {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".java")) continue;
                    name = name.substring(0, name.length() - 5);
                    boolean integration = name.startsWith("IT") || name.endsWith("IT") || name.endsWith("ITCase");
                    if (!integration && !name.startsWith("Test") && !name.endsWith("Test") && !name.endsWith("Tests") && !name.endsWith("TestCase")) continue;
                    // TestSupport and similar helpers match Maven's names but contain no executable test methods.
                    if (!Pattern.compile("@(?:org\\.[\\w.]+\\.)?(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b")
                            .matcher(Files.readString(file)).find()) continue;
                    String type = source.relativize(file).toString().replace(java.io.File.separatorChar, '.');
                    type = type.substring(0, type.length() - 5);
                    reports.add(module.resolve("target").resolve(integration ? "failsafe-reports" : "surefire-reports").resolve("TEST-" + type + ".xml"));
                }
            }
        }
        if (reports.isEmpty()) missing.add("No test suites were found in the reactor sources");
        return reports;
    }

    /** Where the project's own LICENSE and NOTICE.md stand: null when the jar carries both (META-INF/LICENSE, META-INF/NOTICE.md). */
    private static String projectTerms(Path root, Path jar) throws IOException {
        boolean inTree = Files.isRegularFile(root.resolve("LICENSE")) && Files.isRegularFile(root.resolve("NOTICE.md"));
        boolean inJar = false;
        if (Files.isRegularFile(jar)) {
            try (ZipFile zip = new ZipFile(jar.toFile())) {
                inJar = zip.getEntry("META-INF/LICENSE") != null && zip.getEntry("META-INF/NOTICE.md") != null;
            }
        }
        if (inJar) {
            return null;
        }
        return inTree ? "The project's LICENSE and NOTICE.md are in the source tree but not bundled in the jar."
                : "The project has no LICENSE and NOTICE.md in its source tree.";
    }

    /** The tree has no version control: the hash of its sorted file listing (sha256 and path, build output left out) identifies it. */
    private static void appendSource(StringBuilder xml, Path root) throws Exception {
        List<Path> files;
        try (Stream<Path> walk = Files.walk(root)) {
            files = walk.filter(Files::isRegularFile).filter(p -> {
                for (Path part : root.relativize(p)) {
                    String name = part.toString();
                    if (name.equals("target") || name.equals(".git") || name.equals(".mvn")) {
                        return false;
                    }
                }
                return true;
            }).sorted().toList();
        }
        StringBuilder listing = new StringBuilder();
        for (Path file : files) {
            listing.append(sha256(Files.readAllBytes(file))).append("  ").append(root.relativize(file)).append('\n');
        }
        boolean vcs = Files.isDirectory(root.resolve(".git"));
        xml.append("  <source versionControl=\"").append(vcs ? "git" : "none").append("\" files=\"").append(files.size()).append("\" treeSha256=\"")
                .append(sha256(listing.toString().getBytes(StandardCharsets.UTF_8))).append("\"/>\n");
    }

    /** What the jar itself reports through capabilities --format xml, counted by status. */
    private static boolean appendCapabilities(StringBuilder xml, Path jar) throws Exception {
        if (!Files.isRegularFile(jar)) {
            xml.append("  <capabilities observed=\"false\" reason=\"the jar was not built\"/>\n");
            return false;
        }
        Path launcher = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(launcher.toString(), "-Djava.awt.headless=true", "-jar", jar.toString(), "capabilities", "--format", "xml")
                .redirectErrorStream(true).start();
        byte[] output;
        try (InputStream in = process.getInputStream()) {
            output = in.readAllBytes();
        }
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            process.destroyForcibly();
            xml.append("  <capabilities observed=\"false\" reason=\"capabilities did not finish cleanly\"/>\n");
            return false;
        }
        Map<String, Integer> counts = new TreeMap<>();
        NodeList entries = parse(output).getElementsByTagName("capability");
        for (int i = 0; i < entries.getLength(); i++) {
            counts.merge(((Element) entries.item(i)).getAttribute("status"), 1, Integer::sum);
        }
        xml.append("  <capabilities observed=\"true\" sha256=\"").append(sha256(output)).append("\" total=\"").append(entries.getLength()).append('"');
        counts.forEach((k, v) -> xml.append(' ').append(k.toLowerCase(java.util.Locale.ROOT).replace('_', '-')).append("=\"").append(v).append('"'));
        xml.append("/>\n");
        return entries.getLength() > 0;
    }

    private static Document thirdParty(Path jar) throws Exception {
        if (!Files.isRegularFile(jar)) {
            return null;
        }
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var entry = zip.getEntry("META-INF/cf-third-party.xml");
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return parse(in.readAllBytes());
            }
        }
    }

    /** A CycloneDX 1.5 bill of materials: the application and every bundled component with its declared license. */
    private static String cycloneDx(Document thirdParty, Path root) throws IOException {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<bom xmlns=\"http://cyclonedx.org/schema/bom/1.5\" version=\"1\">\n  <metadata>\n");
        String version = projectVersion(root);
        x.append("    <component type=\"application\" bom-ref=\"fw-app\"><group>org.ghyura.cf</group><name>fw-app</name><version>").append(escape(version))
                .append("</version><licenses>").append(license("LicenseRef-BUSL-1.1")).append("</licenses></component>\n  </metadata>\n  <components>\n");
        if (thirdParty != null) {
            NodeList components = thirdParty.getElementsByTagName("component");
            for (int i = 0; i < components.getLength(); i++) {
                Element c = (Element) components.item(i);
                String purl = "pkg:maven/" + c.getAttribute("group") + "/" + c.getAttribute("artifact") + "@" + c.getAttribute("version");
                x.append("    <component type=\"library\" bom-ref=\"").append(escape(purl)).append("\"><group>").append(escape(c.getAttribute("group")))
                        .append("</group><name>").append(escape(c.getAttribute("artifact"))).append("</name><version>").append(escape(c.getAttribute("version")))
                        .append("</version><licenses>").append(license(c.getAttribute("license"))).append("</licenses><purl>")
                        .append(escape(purl)).append("</purl></component>\n");
            }
            NodeList ported = thirdParty.getElementsByTagName("ported");
            for (int i = 0; i < ported.getLength(); i++) {
                Element p = (Element) ported.item(i);
                x.append("    <component type=\"library\" bom-ref=\"ported-").append(i).append("\"><name>").append(escape(p.getAttribute("name")))
                        .append("</name><licenses>").append(license(p.getAttribute("license"))).append("</licenses>")
                        .append("<description>ported into ").append(escape(p.getAttribute("into"))).append("</description></component>\n");
            }
        }
        return x.append("  </components>\n</bom>\n").toString();
    }

    /** An SPDX list identifier goes in id; a LicenseRef (not on the list) in name, as CycloneDX requires. */
    private static String license(String spdx) {
        return spdx.startsWith("LicenseRef-") ? "<license><name>" + escape(spdx) + "</name></license>" : "<license><id>" + escape(spdx) + "</id></license>";
    }

    private static String projectVersion(Path root) throws IOException {
        var m = Pattern.compile("<version>([^<]+)</version>").matcher(Files.readString(root.resolve("pom.xml")));
        while (m.find()) {
            if (m.group(1).contains("SNAPSHOT") || Character.isDigit(m.group(1).charAt(0))) {
                return m.group(1);
            }
        }
        return "unknown";
    }

    private static Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
