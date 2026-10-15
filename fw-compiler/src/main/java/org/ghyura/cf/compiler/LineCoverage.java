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

package org.ghyura.cf.compiler;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassHierarchyResolver;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.Instruction;
import java.lang.classfile.MethodModel;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.LineNumber;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.reflect.AccessFlag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Optional line instrumentation of generated Java candidates (AN-12, decision R-INSTRUMENTATION), in memory with the
 * JDK's class-file API: no agent, no subprocess. The executable lines are the unique source lines the bytecode's line
 * tables give for the candidate's own methods; compiler-generated members are left out (synthetic and bridge methods but
 * lambda bodies, an enum's values and valueOf, a record's generated toString, hashCode and equals, empty no-argument
 * constructors such as javac's implicit one, every method of a synthetic class), and nothing outside the compilation is
 * touched. Every line gets one probe, set where the line's code starts.
 *
 * <p>The probes are a boolean array in a holder class added to the compilation. Each sample defines the compilation's
 * classes in a loader of its own, so every attempt has its own probes; they are read once, when the attempt completes,
 * which seals them: whatever threads the candidate leaves running can only write to that attempt's probes, which nothing
 * reads again, and never to another sample's.</p>
 */
public final class LineCoverage {

    /** Part of the cache digest of every instrumented compilation and of each instrumented sample's source identity. */
    public static final String VERSION = "cf-line-coverage/2";
    /** The probe array's field in the holder. */
    static final String FIELD = "HITS";

    /**
     * What instrumentation added to a compilation.
     *
     * @param holder the probe holder's binary name, or null when nothing was instrumented
     * @param lines  the executable source lines, ascending; the probe of {@code lines[i]} is element {@code i}
     * @param note   why no line is executable, or empty
     */
    public record Probes(String holder, int[] lines, String note) {
        public Probes {
            lines = lines.clone();
        }

        @Override
        public int[] lines() {
            return lines.clone();
        }

        public int total() {
            return lines.length;
        }
    }

    /** The lines one attempt ran, read when it completed. */
    public record Hits(int covered, int total) {
        public double fraction() {
            return (double) covered / total;
        }
    }

    private LineCoverage() {
    }

    /**
     * The compilation with a probe at the start of every executable line, under {@code digest}; the plain compilation
     * itself when it has no executable line or its stack maps cannot be rebuilt (the note says why).
     *
     * @param dependencies resolves the types of the class path when stack maps are rebuilt
     */
    static CompiledClasses instrument(CompiledClasses plain, String digest, ClassLoader dependencies) {
        Map<String, ClassModel> models = new TreeMap<>();
        for (String name : plain.names()) {
            models.put(name, ClassFile.of().parse(plain.bytecode(name)));
        }
        TreeSet<Integer> lines = new TreeSet<>();
        for (ClassModel model : models.values()) {
            for (MethodModel method : model.methods()) {
                if (counted(model, method)) {
                    method.code().ifPresent(code -> code.forEach(e -> {
                        if (e instanceof LineNumber line) {
                            lines.add(line.line());
                        }
                    }));
                }
            }
        }
        int[] executable = lines.stream().mapToInt(Integer::intValue).toArray();
        if (executable.length == 0) {
            return plain.as(digest, new Probes(null, executable, "the candidate's own code has no executable line"));
        }
        Map<Integer, Integer> probeOf = new HashMap<>();
        for (int i = 0; i < executable.length; i++) {
            probeOf.put(executable[i], i);
        }
        String holder = holderName(plain, dependencies);
        ClassDesc holderType = ClassDesc.of(holder);
        ClassHierarchyResolver resolver = ClassHierarchyResolver.ofResourceParsing(desc -> {
            String binary = desc.descriptorString().substring(1, desc.descriptorString().length() - 1).replace('/', '.');
            byte[] bytes = plain.bytecode(binary);
            return bytes == null ? null : new java.io.ByteArrayInputStream(bytes);
        }).orElse(ClassHierarchyResolver.ofClassLoading(dependencies)).orElse(ClassHierarchyResolver.defaultResolver());
        ClassFile files = ClassFile.of(ClassFile.ClassHierarchyResolverOption.of(resolver));
        Map<String, byte[]> classes = new TreeMap<>();
        try {
            for (Map.Entry<String, ClassModel> e : models.entrySet()) {
                ClassModel model = e.getValue();
                classes.put(e.getKey(), files.transformClass(model, ClassTransform.transformingMethodBodies(m -> counted(model, m),
                        CodeTransform.ofStateful(() -> probes(holderType, probeOf)))));
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            return plain.as(digest, new Probes(null, new int[0], "the stack maps of the instrumented bytecode could not be rebuilt: "
                    + e.getMessage()));
        }
        classes.put(holder, files.build(holderType, cb -> cb.withFlags(AccessFlag.PUBLIC, AccessFlag.FINAL, AccessFlag.SYNTHETIC)
                .withField(FIELD, ConstantDescs.CD_boolean.arrayType(), fb -> fb.withFlags(AccessFlag.PUBLIC, AccessFlag.STATIC, AccessFlag.FINAL))
                .withMethodBody(ConstantDescs.CLASS_INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_STATIC, code -> code
                        .loadConstant(executable.length).newarray(TypeKind.BOOLEAN).putstatic(holderType, FIELD, ConstantDescs.CD_boolean.arrayType())
                        .return_())));
        return new CompiledClasses(plain.mainType(), classes, digest, new Probes(holder, executable, ""));
    }

    /** Sets a line's probe before the first instruction of the line, after the labels there, so branches reach it too. */
    private static CodeTransform probes(ClassDesc holder, Map<Integer, Integer> probeOf) {
        return new CodeTransform() {
            private int pending = -1;

            @Override
            public void accept(java.lang.classfile.CodeBuilder code, CodeElement e) {
                if (e instanceof LineNumber line) {
                    pending = probeOf.getOrDefault(line.line(), -1);
                } else if (e instanceof Instruction && pending >= 0) {
                    code.getstatic(holder, FIELD, ConstantDescs.CD_boolean.arrayType()).loadConstant(pending).iconst_1().bastore();
                    pending = -1;
                }
                code.with(e);
            }
        };
    }

    /** Whether a method is the candidate's own code rather than a member the compiler generated. */
    static boolean counted(ClassModel type, MethodModel method) {
        if (type.flags().has(AccessFlag.SYNTHETIC) || method.code().isEmpty()) {
            return false;
        }
        String name = method.methodName().stringValue();
        String descriptor = method.methodType().stringValue();
        if (method.flags().has(AccessFlag.BRIDGE) || method.flags().has(AccessFlag.SYNTHETIC)) {
            return name.startsWith("lambda$");
        }
        String self = "L" + type.thisClass().asInternalName() + ";";
        if (type.flags().has(AccessFlag.ENUM) && (name.equals("values") && descriptor.equals("()[" + self)
                || name.equals("valueOf") && descriptor.equals("(Ljava/lang/String;)" + self))) {
            return false;
        }
        CodeModel code = method.code().get();
        if (type.findAttribute(Attributes.record()).isPresent() && List.of("toString", "hashCode", "equals").contains(name)
                && code.elementStream().anyMatch(e -> e instanceof InvokeDynamicInstruction indy
                && indy.bootstrapMethod().owner().descriptorString().equals("Ljava/lang/runtime/ObjectMethods;"))) {
            return false;
        }
        if (name.equals(ConstantDescs.INIT_NAME) && descriptor.equals("()V")) {
            List<Opcode> ops = new ArrayList<>();
            code.forEach(e -> {
                if (e instanceof Instruction i) {
                    ops.add(i.opcode());
                }
            });
            return !ops.equals(List.of(Opcode.ALOAD_0, Opcode.INVOKESPECIAL, Opcode.RETURN));
        }
        return true;
    }

    /** Keep both candidate classes and dependency classes visible under their original binary names. */
    private static String holderName(CompiledClasses plain, ClassLoader dependencies) {
        String main = plain.mainType();
        String prefix = main.contains(".") ? main.substring(0, main.lastIndexOf('.') + 1) : "";
        String name = prefix + "CfLineProbes";
        for (int i = 2; plain.names().contains(name) || dependencyDefines(name, dependencies); i++) {
            name = prefix + "CfLineProbes" + i;
        }
        return name;
    }

    private static boolean dependencyDefines(String name, ClassLoader dependencies) {
        try {
            Class.forName(name, false, dependencies);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (LinkageError e) {
            // A present but unlinkable dependency still owns its name; do not replace it with a probe holder.
            return true;
        }
    }

    /**
     * Reads the probes of the attempt whose classes {@code loader} defined, once, when it completed; null when the
     * compilation has no executable line.
     */
    public static Hits read(ClassLoader loader, Probes probes) throws ReflectiveOperationException {
        if (probes.holder() == null) {
            return null;
        }
        return new Hits(covered(loader, probes).length, probes.total());
    }

    /** The source lines the attempt ran, ascending. */
    static int[] covered(ClassLoader loader, Probes probes) throws ReflectiveOperationException {
        boolean[] hits = (boolean[]) Class.forName(probes.holder(), true, loader).getField(FIELD).get(null);
        boolean[] sealed = Arrays.copyOf(hits, hits.length);
        int[] lines = probes.lines();
        return java.util.stream.IntStream.range(0, lines.length).filter(i -> sealed[i]).map(i -> lines[i]).toArray();
    }
}
