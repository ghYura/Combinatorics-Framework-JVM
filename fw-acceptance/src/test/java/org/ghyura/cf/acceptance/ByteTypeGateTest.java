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

import java.lang.classfile.ClassFile;
import java.lang.classfile.TypeKind;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gate A02 (rule R04): no owned Java source or bytecode, production or test, uses the 16-bit integer type, its
 * wrapper, its buffers or its readers and writers. The scanners are proven on positive controls first, so a
 * pass means something.
 */
class ByteTypeGateTest {

    /**
     * Rule R04 wraps an incompatible third-party API at an explicit adapter boundary. POI's number-format methods
     * are declared with the 16-bit type; the one test class that calls them is approved here for exactly those
     * calls (the value goes from one POI call straight into the next) and for nothing else. Every approval must
     * still be needed, so the list cannot go stale and quietly widen the rule.
     */
    private static final Map<String, Set<String>> THIRD_PARTY_ADAPTERS = Map.of(
            "org/ghyura/cf/input/PoiStyles", Set.of(
                    "call org/apache/poi/xssf/usermodel/XSSFDataFormat.getFormat involves a 16-bit type",
                    "call org/apache/poi/ss/usermodel/CellStyle.setDataFormat involves a 16-bit type"));

    private static boolean approved(BytecodeScanner.Finding finding) {
        return THIRD_PARTY_ADAPTERS.getOrDefault(finding.owner(), Set.of()).contains(finding.what());
    }

    private static final String BAD_SOURCE = """
            import java.nio.Sh%1$sortBuffer;
            class Bad {
                sh%1$sort field;
                Sh%1$sort boxed;
                java.util.List<Sh%1$sort> generic;
                sh%1$sort[] array;
                int cast(int v) { return (sh%1$sort) v; }
                void io(java.io.DataInputStream in) throws Exception { in.read%1$sShort(); }
                int max() { return Sh%1$sort.MAX_VALUE; }
            }
            """.replace("%1$s", "");

    private static final String CLEAN_SOURCE = """
            /** talks about sh%1$sort and Sh%1$sort and read%1$sShort in a comment */
            class Good {
                String s = "sh%1$sort Sh%1$sort read%1$sShort";
                byte[] octets = new byte[4];
                int widened(byte b) { return b; }
                long big = 1L << 40;
                char c = 'x';
                int fromOctets(byte hi, byte lo) { return ((hi & 0xFF) << 8) | (lo & 0xFF); }
            }
            """.replace("%1$s", "");

    @Test
    void ownedSourceNeverUsesA16BitType() {
        List<Path> sources = Reactor.javaSources();
        assertTrue(sources.size() >= 60, "the scan would be vacuous: only " + sources.size() + " sources found");
        List<ShortTypeSourceScanner.Finding> findings = ShortTypeSourceScanner.scan(sources);
        assertEquals(List.of(), findings, "16-bit types in owned source");
    }

    @Test
    void ownedBytecodeNeverUsesA16BitType() {
        List<Path> main = Reactor.classFiles(true);
        List<Path> test = Reactor.classFiles(false);
        assertTrue(main.size() >= 40, "the scan would be vacuous: only " + main.size() + " main classes");
        assertTrue(test.size() >= 30, "the scan would be vacuous: only " + test.size() + " test classes");
        List<BytecodeScanner.Finding> findings = new ArrayList<>();
        for (Path file : main) {
            findings.addAll(BytecodeScanner.scanShortTypes(file));
        }
        for (Path file : test) {
            findings.addAll(BytecodeScanner.scanShortTypes(file));
        }
        assertEquals(List.of(), findings.stream().filter(f -> !approved(f)).toList(), "16-bit types in owned bytecode");
        for (Map.Entry<String, Set<String>> adapter : THIRD_PARTY_ADAPTERS.entrySet()) {
            for (String what : adapter.getValue()) {
                assertTrue(findings.stream().anyMatch(f -> f.owner().equals(adapter.getKey()) && f.what().equals(what)),
                        "the approval of " + adapter.getKey() + " for '" + what + "' is not needed any more: remove it");
            }
        }
    }

    @Test
    void anAdapterApprovalCoversExactlyTheNamedCallsOfTheNamedClassAndNothingElse() {
        String adapter = "org/ghyura/cf/input/PoiStyles";
        String call = "call org/apache/poi/ss/usermodel/CellStyle.setDataFormat involves a 16-bit type";
        assertTrue(approved(new BytecodeScanner.Finding(adapter, "withFormat", call)));
        assertFalse(approved(new BytecodeScanner.Finding("org/ghyura/cf/input/SomeOtherTest", "m", call)),
                "the same call from another class is a finding");
        assertFalse(approved(new BytecodeScanner.Finding(adapter, "m",
                "call org/apache/poi/ss/usermodel/CellStyle.setFontIndex involves a 16-bit type")), "another POI call");
        assertFalse(approved(new BytecodeScanner.Finding(adapter, "m",
                "call java/io/DataInputStream.read" + "Short involves a 16-bit type")), "a non-POI call");

        // an approved class cannot smuggle in its own 16-bit field, local, cast or array
        byte[] smuggled = build("org.ghyura.cf.input.PoiStyles", cb -> {
            cb.withField("f", ConstantDescs.CD_short, ClassFile.ACC_PUBLIC);
            cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int), ClassFile.ACC_STATIC,
                    mb -> mb.withCode(c -> c.iload(0).i2s().pop().iconst_1().newarray(TypeKind.SHORT).pop().return_()));
        });
        List<BytecodeScanner.Finding> found = BytecodeScanner.scanShortTypes(smuggled);
        assertTrue(found.size() >= 3, found.toString());
        assertTrue(found.stream().noneMatch(ByteTypeGateTest::approved), found.toString());
    }

    @Test
    void theSourceScannerCatchesEveryFormAndIgnoresCommentsAndStrings() {
        List<ShortTypeSourceScanner.Finding> bad = ShortTypeSourceScanner.scanText("Bad.java", BAD_SOURCE);
        assertEquals(8, bad.size(), bad.toString());
        // line numbers are real, not -1
        assertTrue(bad.stream().allMatch(f -> f.line() >= 1), bad.toString());
        assertEquals(List.of(), ShortTypeSourceScanner.scanText("Good.java", CLEAN_SOURCE));
    }

    @Test
    void theBytecodeScannerCatchesEveryFormAndAcceptsCleanClasses() {
        ClassDesc wrapper = ClassDesc.of("java.lang.Sh" + "ort");
        assertFinding(build("probe.Field", cb -> cb.withField("f", ConstantDescs.CD_short, ClassFile.ACC_PUBLIC)),
                "field of a 16-bit type");
        assertFinding(build("probe.ArrayField", cb -> cb.withField("a", ConstantDescs.CD_short.arrayType(),
                ClassFile.ACC_PUBLIC)), "field of a 16-bit type");
        assertFinding(build("probe.Wrapper", cb -> cb.withField("w", wrapper, ClassFile.ACC_PUBLIC)),
                "field of a 16-bit type");
        assertFinding(build("probe.Descriptor", cb -> cb.withMethod("m",
                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_short), ClassFile.ACC_STATIC,
                mb -> mb.withCode(c -> c.return_()))), "method descriptor mentions a 16-bit type");
        assertFinding(build("probe.Cast", cb -> cb.withMethod("m",
                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_int), ClassFile.ACC_STATIC,
                mb -> mb.withCode(c -> c.iload(0).i2s().pop().return_()))), "conversion to or from a 16-bit integer");
        assertFinding(build("probe.NewArray", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void),
                ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.iconst_1().newarray(TypeKind.SHORT).pop().return_()))),
                "allocation of a 16-bit array");
        assertFinding(build("probe.ArrayStore", cb -> cb.withMethod("m",
                MethodTypeDesc.of(ConstantDescs.CD_void, ConstantDescs.CD_short.arrayType()), ClassFile.ACC_STATIC,
                mb -> mb.withCode(c -> c.aload(0).iconst_0().iconst_1().sastore().return_()))), "store into a 16-bit array");
        assertFinding(build("probe.Reader", cb -> cb.withMethod("m", MethodTypeDesc.of(ConstantDescs.CD_void),
                ClassFile.ACC_STATIC, mb -> mb.withCode(c -> c.aconst_null().invokevirtual(
                        ClassDesc.of("java.io.DataInputStream"), "read" + "Short",
                        MethodTypeDesc.of(ConstantDescs.CD_int)).pop().return_()))), "involves a 16-bit type");

        // a class that uses only byte, int, long and char is clean for both scans
        byte[] clean = build("probe.Clean", cb -> {
            cb.withField("octets", ConstantDescs.CD_byte.arrayType(), ClassFile.ACC_PUBLIC);
            cb.withField("count", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC);
            cb.withField("id", ConstantDescs.CD_long, ClassFile.ACC_PUBLIC);
            cb.withMethod("narrow", MethodTypeDesc.of(ConstantDescs.CD_byte, ConstantDescs.CD_int), ClassFile.ACC_STATIC,
                    mb -> mb.withCode(c -> c.iload(0).i2b().ireturn()));
        });
        assertEquals(List.of(), BytecodeScanner.scanShortTypes(clean));
    }

    private static byte[] build(String className, java.util.function.Consumer<java.lang.classfile.ClassBuilder> body) {
        return ClassFile.of().build(ClassDesc.of(className), body);
    }

    private static void assertFinding(byte[] classBytes, String expectedFragment) {
        List<BytecodeScanner.Finding> findings = BytecodeScanner.scanShortTypes(classBytes);
        assertTrue(findings.stream().anyMatch(f -> f.what().contains(expectedFragment)),
                "expected a finding containing '" + expectedFragment + "' but got " + findings);
    }
}
