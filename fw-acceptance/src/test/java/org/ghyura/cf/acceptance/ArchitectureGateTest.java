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

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A01 (rules R01-R03): the owned code is Java only, runs in one JVM, and touches no subprocess, socket,
 * script engine, SQL, native code or process exit, except the explicit add-on/browser/formatter allowances below.
 * Evidence is bytecode plus the file tree; the packaged jar
 * and its dependencies are checked in {@code PackagedApplicationIT}.
 */
class ArchitectureGateTest {

    /** The only class that may end the JVM (D07: plugins and framework code must not terminate the VM). */
    private static final Set<String> MAY_EXIT = Set.of("org/ghyura/cf/app/Main");

    /**
     * The only test classes allowed a forbidden API, and exactly which: the packaged-jar smoke test launches the
     * distribution (a subprocess is its job), and the isolation probe deliberately tries to open a socket to
     * prove the sandbox denies it.
     */
    private static final Map<String, Set<String>> TEST_ALLOW_LIST = Map.of(
            "org/ghyura/cf/acceptance/PackagedApplicationIT",
            Set.of("uses java/lang/ProcessBuilder", "uses java/lang/Process"),
            "org/ghyura/cf/acceptance/IsolationProbe", Set.of("uses java/net/Socket"),
            "org/ghyura/cf/desktop/session/Face2BridgeTest", Set.of("uses com/sun/net/httpserver/Headers",
                    "uses com/sun/net/httpserver/HttpExchange", "uses java/net/http/HttpClient", "uses java/net/http/HttpRequest",
                    "uses java/net/http/HttpRequest$Builder", "uses java/net/http/HttpResponse", "uses java/net/http/HttpResponse$BodyHandler",
                    "uses java/net/http/HttpResponse$BodyHandlers"),
            "org/ghyura/cf/desktop/session/Face2TestClient", Set.of("uses com/sun/net/httpserver/HttpExchange"),
            "org/ghyura/cf/desktop/session/Face2TestClient$Exchange", Set.of("uses com/sun/net/httpserver/Headers",
                    "uses com/sun/net/httpserver/HttpExchange", "uses com/sun/net/httpserver/HttpContext", "uses com/sun/net/httpserver/HttpPrincipal"));

    /**
     * Production classes allowed otherwise forbidden APIs, and exactly which: the Python add-on's PythonProcess starts the
     * configured Python interpreter for each candidate (C07, author decision 2026-10-12: Python runs as the legacy executor
     * ran it). Face 2's loopback bridge serves the preserved QA HTML/CSS/JS by the author's explicit request
     * on 2026-10-16. The candidate code preview's ExternalCodeFormatter starts an optional local formatter already on the
     * PATH (ruff, prettier, clang-format, gofmt, rustfmt or shfmt) only when the user asks Auto-format for such a language:
     * fixed arguments, standard input, no shell, a temporary directory, bounded output and a ten-second limit (author
     * decision 2026-10-19: finish it as Codex intended). The core still starts no process or network connection.
     */
    private static final Map<String, Set<String>> PRODUCTION_ALLOW_LIST = Map.of(
            "org/ghyura/cf/python/PythonProcess", Set.of("uses java/lang/ProcessBuilder", "uses java/lang/Process"),
            "org/ghyura/cf/desktop/ExternalCodeFormatter", Set.of("uses java/lang/ProcessBuilder", "uses java/lang/Process"),
            "org/ghyura/cf/desktop/session/Face2Bridge", Set.of("uses com/sun/net/httpserver/HttpServer",
                    "uses com/sun/net/httpserver/HttpHandler", "uses com/sun/net/httpserver/HttpExchange",
                    "uses com/sun/net/httpserver/HttpContext", "uses com/sun/net/httpserver/Headers"));

    /** The author's required browser UI and its dependency-free JS verification; no general foreign-runtime exception. */
    private static final Set<String> FACE2_WEB_FILES = Set.of(
            "fw-desktop/src/main/resources/org/ghyura/cf/desktop/face2/bridge.js", "tools/verify-face2-js.cjs");

    /**
     * The guif dog-food suites: the author asked on 2026-10-18 for the first and its Python and shell infrastructure to be
     * committed, and on 2026-10-20 for the second (button availability). They drive the desktop through the Framework from
     * outside; no module builds them and the distribution does not carry them.
     */
    private static final Set<String> DOGFOOD_SUITES = Set.of("guif_dogfood/", "guif_dogfood_v2/");

    private static final List<String> FOREIGN_SUFFIXES = List.of(".py", ".pyw", ".sh", ".bash", ".zsh", ".bat", ".cmd",
            ".ps1", ".js", ".mjs", ".cjs", ".ts", ".rb", ".pl", ".go", ".rs", ".c", ".cc", ".cpp", ".h", ".hpp", ".cs", ".sql",
            ".groovy", ".kts", ".gradle");

    @Test
    void ownedProductionBytecodeUsesNoForbiddenApi() {
        List<Path> classes = Reactor.classFiles(true);
        assertTrue(classes.size() >= 40, "the scan would be vacuous: only " + classes.size() + " classes");
        List<BytecodeScanner.Finding> findings = new ArrayList<>();
        for (Path file : classes) {
            for (BytecodeScanner.Finding f : BytecodeScanner.scanForbiddenApis(file, MAY_EXIT)) {
                if (!PRODUCTION_ALLOW_LIST.getOrDefault(f.owner(), Set.of()).contains(f.what())) {
                    findings.add(f);
                }
            }
        }
        assertEquals(List.of(), findings, "forbidden APIs in production bytecode");
    }

    @Test
    void theOneAllowedProcessStarterIsThePythonAddonsPythonProcessAndUsesWhatItIsAllowed() {
        Path file = Reactor.root().resolve("fw-python/target/classes/org/ghyura/cf/python/PythonProcess.class");
        assertTrue(Files.isRegularFile(file), "the allowance names a class that exists: " + file);
        Set<String> used = new java.util.TreeSet<>();
        for (BytecodeScanner.Finding f : BytecodeScanner.scanForbiddenApis(file, Set.of())) {
            used.add(f.what());
        }
        assertEquals(new java.util.TreeSet<>(PRODUCTION_ALLOW_LIST.get("org/ghyura/cf/python/PythonProcess")), used,
                "the allowance is exactly what the class uses, no more");
    }

    @Test
    void ownedTestBytecodeUsesNoForbiddenApiExceptTheAllowListedHarnessClasses() {
        List<Path> classes = Reactor.classFiles(false);
        assertTrue(classes.size() >= 30, "the scan would be vacuous: only " + classes.size() + " classes");
        List<BytecodeScanner.Finding> findings = new ArrayList<>();
        for (Path file : classes) {
            for (BytecodeScanner.Finding f : BytecodeScanner.scanForbiddenApis(file, Set.of())) {
                Set<String> allowed = TEST_ALLOW_LIST.getOrDefault(f.owner(), Set.of());
                if (!allowed.contains(f.what())) {
                    findings.add(f);
                }
            }
        }
        assertEquals(List.of(), findings, "forbidden APIs in test bytecode");
    }

    @Test
    void theApiScannerCatchesEveryCategoryAndHonoursTheExitAllowList() {
        ClassDesc string = ConstantDescs.CD_String;
        ClassDesc builder = ClassDesc.of("java.lang.ProcessBuilder");
        assertApiFinding(build("probe/Subprocess", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void),
                ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.new_(builder).dup().iconst_0().anewarray(string)
                        .invokespecial(builder, "<init>", MethodTypeDesc.of(ConstantDescs.CD_void, string.arrayType()))
                        .pop().return_()))), Set.of(), "uses java/lang/ProcessBuilder");
        assertApiFinding(build("probe/Exec", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void,
                ClassDesc.of("java.lang.Runtime")), ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.aload(0)
                        .aconst_null().invokevirtual(ClassDesc.of("java.lang.Runtime"), "exec",
                                MethodTypeDesc.of(ClassDesc.of("java.lang.Process"), string)).pop().return_()))),
                Set.of(), "calls java/lang/Runtime#exec");
        assertApiFinding(build("probe/SocketField", cb -> cb.withField("s", ClassDesc.of("java.net.Socket"),
                ClassFile.ACC_PUBLIC)), Set.of(), "uses java/net/Socket");
        assertApiFinding(build("probe/Sql", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void,
                ClassDesc.of("java.sql.Connection")), ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.return_()))),
                Set.of(), "uses java/sql/Connection");
        assertApiFinding(build("probe/Script", cb -> cb.withField("e", ClassDesc.of("javax.script.ScriptEngine"),
                ClassFile.ACC_PUBLIC)), Set.of(), "uses javax/script/ScriptEngine");
        assertApiFinding(build("probe/Native", cb -> cb.withMethod("n", MethodTypeDesc.of(ConstantDescs.CD_void),
                ClassFile.ACC_PUBLIC | ClassFile.ACC_NATIVE, mb -> { })), Set.of(), "native method");
        assertApiFinding(build("probe/Library", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void),
                ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.aconst_null().invokestatic(ClassDesc.of("java.lang.System"),
                        "loadLibrary", MethodTypeDesc.of(ConstantDescs.CD_void, string)).return_()))), Set.of(),
                "calls java/lang/System#loadLibrary");

        byte[] exits = build("probe/Exits", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void),
                ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.iconst_0().invokestatic(ClassDesc.of("java.lang.System"),
                        "exit", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int)).return_())));
        assertApiFinding(exits, Set.of(), "outside the entry point");
        assertEquals(List.of(), BytecodeScanner.scanForbiddenApis(exits, Set.of("probe/Exits")));
    }

    @Test
    void ordinaryJdkUseIsNotFlagged() {
        byte[] ordinary = build("probe/Ordinary", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void),
                ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.new_(ClassDesc.of("java.util.ArrayList")).dup()
                        .invokespecial(ClassDesc.of("java.util.ArrayList"), "<init>",
                                MethodTypeDesc.of(ConstantDescs.CD_void)).pop().return_())));
        assertEquals(List.of(), BytecodeScanner.scanForbiddenApis(ordinary, Set.of()));
    }

    @Test
    void theProjectHoldsOnlyJavaSourceDataAndTheExplicitFace2WebFiles() {
        List<String> foreign = new ArrayList<>();
        Path root = Reactor.root();
        for (Path file : Reactor.files(FOREIGN_SUFFIXES.toArray(new String[0]))) {
            String name = root.relativize(file).toString().replace(java.io.File.separatorChar, '/');
            if (!FACE2_WEB_FILES.contains(name) && DOGFOOD_SUITES.stream().noneMatch(name::startsWith)) foreign.add(name);
        }
        for (Path file : Reactor.files("Dockerfile", "Makefile", "Jenkinsfile")) {
            foreign.add(root.relativize(file).toString());
        }
        assertEquals(List.of(), foreign, "R01: Java source, with only the author-requested Face 2 browser assets and JS check, and the "
                + "guif dog-food suite");
        for (String file : FACE2_WEB_FILES) assertTrue(Files.isRegularFile(root.resolve(file)), "explicit web allowance must exist: " + file);
        for (String suite : DOGFOOD_SUITES) assertTrue(Files.isDirectory(root.resolve(suite)), "the dog-food allowance must name an existing suite: " + suite);
        assertTrue(Reactor.javaSources().stream().anyMatch(p -> p.endsWith("tools/AddLicenseHeaders.java")),
                "the header utility is Java and lives in tools/");
    }

    private static byte[] build(String internalName, Consumer<ClassBuilder> body) {
        return ClassFile.of().build(ClassDesc.ofInternalName(internalName), body);
    }

    private static void assertApiFinding(byte[] classBytes, Set<String> mayExit, String expectedFragment) {
        List<BytecodeScanner.Finding> findings = BytecodeScanner.scanForbiddenApis(classBytes, mayExit);
        assertTrue(findings.stream().anyMatch(f -> f.what().contains(expectedFragment)),
                "expected a finding containing '" + expectedFragment + "' but got " + findings);
    }
}
