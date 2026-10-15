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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * The local distribution of a finished build (OPEN-09): one folder, and a reproducible zip of it, holding the application
 * jar, the reference fixture plugins as an explicit addon, the project terms (LICENSE and NOTICE.md, unchanged, which both
 * jars also carry under META-INF), sample scenarios as portable assets, the configuration that installs the addon, and
 * distribution.xml, which declares the launch path and every file with its size and SHA-256. Nothing in it needs Python, a
 * database, a network service or a legacy checkout. The tool writes into a new place on this machine and publishes nothing.
 *
 * <pre>
 * java tools/Distribution.java --root . --out DIR [--app JAR] [--addon JAR]
 * </pre>
 */
public final class Distribution {

    static final String SCHEMA = "cf.distribution/v1";
    static final String NAME = "combinatorics-framework";
    static final String ADDON_CONFIG = "cf-addons.properties";
    static final String PYTHON_CONFIG = "cf-python.properties";
    static final List<String> TUTOR = List.of("tutor-typed.json", "tutor-typed.constraints.json", "tutor-generated.json", "tutor-generated.constraints.json");
    /** Every zip entry carries this time, so the same files always make the same archive. */
    static final LocalDateTime ENTRY_TIME = LocalDateTime.of(2026, 1, 1, 0, 0);

    public static void main(String[] args) throws Exception {
        Map<String, String> options = new LinkedHashMap<>();
        for (int i = 0; i + 1 < args.length; i += 2) {
            options.put(args[i], args[i + 1]);
        }
        if (!options.containsKey("--out")) {
            throw new IllegalArgumentException("usage: java tools/Distribution.java --root . --out DIR [--app JAR] [--addon JAR] [--python-addon JAR]");
        }
        Path root = Path.of(options.getOrDefault("--root", ".")).toAbsolutePath().normalize();
        Path out = Path.of(options.get("--out")).toAbsolutePath().normalize();
        Path app = Path.of(options.getOrDefault("--app", root.resolve("fw-app/target/fw-app.jar").toString())).toAbsolutePath().normalize();
        String version = version(app);
        Path addon = Path.of(options.getOrDefault("--addon", root.resolve("fw-sut-fixtures/target/fw-sut-fixtures-" + version + ".jar").toString()))
                .toAbsolutePath().normalize();
        Path pythonAddon = Path.of(options.getOrDefault("--python-addon", root.resolve("fw-python/target/fw-python-" + version + ".jar").toString()))
                .toAbsolutePath().normalize();
        String folder = NAME + "-" + version;
        Path dist = out.resolve(folder);
        Path zip = out.resolve(folder + ".zip");
        if (Files.exists(dist) || Files.exists(zip)) {
            throw new IllegalStateException(dist + " or " + zip + " already exists: a distribution is written once, into a new place");
        }
        Map<String, Path> sources = new TreeMap<>();
        sources.put("fw-app.jar", app);
        sources.put("addons/fw-sut-fixtures.jar", addon);
        sources.put("addons/fw-python.jar", pythonAddon);
        sources.put("LICENSE", root.resolve("LICENSE"));
        sources.put("NOTICE.md", root.resolve("NOTICE.md"));
        try (Stream<Path> showcase = Files.list(root.resolve("scenarios/showcase"))) {
            for (Path p : showcase.sorted().toList()) {
                String name = p.getFileName().toString();
                if (Files.isRegularFile(p) && !name.startsWith(".")) {
                    sources.put("scenarios/showcase/" + name, p);
                }
            }
        }
        for (String name : TUTOR) {
            sources.put("scenarios/tutor/" + name, root.resolve("fw-sut-fixtures/src/main/resources/org/ghyura/cf/fixtures/tutor/" + name));
        }
        for (var e : sources.entrySet()) {
            if (!Files.isRegularFile(e.getValue())) {
                throw new IllegalStateException("missing for " + e.getKey() + ": " + e.getValue());
            }
        }
        for (Path jar : List.of(app, addon, pythonAddon)) {
            for (String terms : List.of("LICENSE", "NOTICE.md")) {
                if (!Arrays.equals(entry(jar, "META-INF/" + terms), Files.readAllBytes(root.resolve(terms)))) {
                    throw new IllegalStateException(jar + " does not carry the project's " + terms + " unchanged at META-INF/" + terms);
                }
            }
        }
        Map<String, byte[]> files = new TreeMap<>();
        for (var e : sources.entrySet()) {
            files.put(e.getKey(), Files.readAllBytes(e.getValue()));
        }
        files.put(ADDON_CONFIG, addonConfig(root).getBytes(StandardCharsets.UTF_8));
        files.put(PYTHON_CONFIG, pythonConfig(root).getBytes(StandardCharsets.UTF_8));
        files.put("distribution.xml", declaration(root, version, files).getBytes(StandardCharsets.UTF_8));
        Files.createDirectories(dist);
        for (var e : files.entrySet()) {
            Path target = dist.resolve(e.getKey());
            Files.createDirectories(target.getParent());
            Files.write(target, e.getValue(), StandardOpenOption.CREATE_NEW);
        }
        try (ZipOutputStream archive = new ZipOutputStream(Files.newOutputStream(zip, StandardOpenOption.CREATE_NEW))) {
            for (var e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(folder + "/" + e.getKey());
                entry.setTimeLocal(ENTRY_TIME);
                archive.putNextEntry(entry);
                archive.write(e.getValue());
                archive.closeEntry();
            }
        }
        System.out.println("distribution " + dist + " (" + files.size() + " files) and " + zip + " sha256 " + sha256(Files.readAllBytes(zip)));
    }

    /** The version the application jar reports (org/ghyura/cf/app/version.properties). */
    static String version(Path app) throws IOException {
        Properties p = new Properties();
        p.load(new java.io.ByteArrayInputStream(entry(app, "org/ghyura/cf/app/version.properties")));
        String version = p.getProperty("app.version", "");
        if (version.isBlank() || version.contains("${")) {
            throw new IllegalStateException(app + " reports no version");
        }
        return version;
    }

    static byte[] entry(Path jar, String name) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) {
                return null;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return in.readAllBytes();
            }
        }
    }

    /** The license comment every project properties file carries. */
    static StringBuilder licenseComment(Path root) throws IOException {
        StringBuilder text = new StringBuilder();
        for (String line : Files.readAllLines(root.resolve("fw-config/src/main/resources/org/ghyura/cf/config/runtime-defaults.properties"))) {
            if (!line.startsWith("#")) {
                break;
            }
            text.append(line).append('\n');
        }
        return text;
    }

    /** The Python add-on's configuration: every candidate runs as Python with the installed python3 (author decision 2026-10-12). */
    static String pythonConfig(Path root) throws IOException {
        return licenseComment(root).append("\n# Runs every candidate as Python with the Python add-on and the installed python3, as the legacy\n")
                .append("# executor did (author decision 2026-10-12), for commands started from this folder:\n")
                .append("#   java -jar fw-app.jar run --scenario SCENARIO --config ").append(PYTHON_CONFIG).append('\n')
                .append("# cf.python.executable names another interpreter, cf.python.path what the candidates import, cf.python.workdir\n")
                .append("# where they run (by default the scenario's directory).\n")
                .append("cf.execution.classpath=addons/fw-python.jar\n")
                .append("cf.execution.sut=python\n").toString();
    }

    /** The addon configuration, under the license comment every project properties file carries. */
    static String addonConfig(Path root) throws IOException {
        return licenseComment(root).append("\n# Installs the reference fixture plugins (tutor, domain slices, demos) for commands started from this folder:\n")
                .append("#   java -jar fw-app.jar COMMAND ... --config ").append(ADDON_CONFIG).append('\n')
                .append("cf.execution.classpath=addons/fw-sut-fixtures.jar\n").toString();
    }

    /** distribution.xml: the launch path and every other file of the folder with its size and SHA-256. */
    static String declaration(Path root, String version, Map<String, byte[]> files) throws Exception {
        String deltas = Files.readString(root.resolve("fw-config/src/main/resources/org/ghyura/cf/config/compatibility-deltas.xml"));
        String notice = deltas.substring(deltas.indexOf("<!--"), deltas.indexOf("-->") + 3);
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n").append(notice).append('\n');
        xml.append("<distribution schema=\"").append(SCHEMA).append("\" name=\"").append(NAME).append("\" version=\"").append(escape(version))
                .append("\" java=\"25\" files=\"").append(files.size()).append("\">\n");
        xml.append("  <launch workingDirectory=\"this folder\">\n");
        xml.append("    <command purpose=\"framework\">java -jar fw-app.jar COMMAND [OPTIONS]</command>\n");
        xml.append("    <command purpose=\"with the reference fixture plugins\">java -jar fw-app.jar COMMAND [OPTIONS] --config ").append(ADDON_CONFIG)
                .append("</command>\n");
        xml.append("    <sample>java -jar fw-app.jar run --scenario scenarios/showcase/showcase.xlsx --config scenarios/showcase/showcase.properties</sample>\n");
        xml.append("    <command purpose=\"with the Python add-on: every candidate runs as Python\">java -jar fw-app.jar COMMAND [OPTIONS] --config ")
                .append(PYTHON_CONFIG).append("</command>\n");
        xml.append("    <sample>java -jar fw-app.jar run --scenario scenarios/tutor/tutor-typed.json --sut tutor --config ").append(ADDON_CONFIG)
                .append("</sample>\n");
        xml.append("  </launch>\n");
        xml.append("  <requires>A Java 25 runtime; Python 3 only for the Python add-on (").append(PYTHON_CONFIG).append("). No database, network service, ")
                .append("container or legacy checkout.</requires>\n");
        xml.append("  <terms>LICENSE and NOTICE.md, unchanged; every jar carries them too, at META-INF/LICENSE and META-INF/NOTICE.md.</terms>\n");
        for (var e : files.entrySet()) {
            String path = e.getKey();
            String role = path.equals("fw-app.jar") ? "application" : path.startsWith("addons/") ? "addon" : path.equals(ADDON_CONFIG) || path.equals(PYTHON_CONFIG) ? "configuration"
                    : path.startsWith("scenarios/") ? "sample" : "terms";
            xml.append("  <file path=\"").append(escape(path)).append("\" role=\"").append(role).append("\" bytes=\"").append(e.getValue().length)
                    .append("\" sha256=\"").append(sha256(e.getValue())).append("\"/>\n");
        }
        return xml.append("</distribution>\n").toString();
    }

    static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
