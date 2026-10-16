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

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.CodeModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.ArrayLoadInstruction;
import java.lang.classfile.instruction.ArrayStoreInstruction;
import java.lang.classfile.instruction.ConvertInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeDynamicInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LocalVariable;
import java.lang.classfile.instruction.NewMultiArrayInstruction;
import java.lang.classfile.instruction.NewObjectInstruction;
import java.lang.classfile.instruction.NewPrimitiveArrayInstruction;
import java.lang.classfile.instruction.NewReferenceArrayInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reads compiled classes with the JDK's own class-file API and reports what the source rules forbid:
 * 16-bit integer types anywhere (fields, descriptors, locals, array and conversion instructions, wrappers,
 * buffers, reader and writer methods) and the framework-excluded APIs (subprocesses, sockets, scripting, SQL,
 * native code, process exit). Bytecode is the second line of defence after the source scan: it sees generated
 * code and anything the parse tree cannot.
 *
 * <p>Forbidden names are spelled as concatenations so this file stays clean under its own source scan.</p>
 */
final class BytecodeScanner {

    record Finding(String owner, String member, String what) {
        @Override
        public String toString() {
            return owner + (member.isEmpty() ? "" : "#" + member) + " " + what;
        }
    }

    private static final String WRAPPER = "java/lang/Sh" + "ort";
    private static final String BUFFER = "java/nio/Sh" + "ortBuffer";
    private static final Set<String> TYPE_DESCRIPTORS = Set.of("S", "L" + WRAPPER + ";", "L" + BUFFER + ";");
    private static final Set<String> TYPE_INTERNAL_NAMES = Set.of("S", WRAPPER, BUFFER);
    private static final Set<String> METHOD_NAMES = Set.of("read" + "Short", "write" + "Short", "get" + "Short",
            "put" + "Short", "asSh" + "ortBuffer", "sh" + "ortValue", "parseSh" + "ort");

    /** Owners that may not appear at all, matched exactly. */
    private static final Set<String> BANNED_OWNERS = Set.of(
            "java/lang/ProcessBuilder", "java/lang/Process", "java/net/Socket", "java/net/ServerSocket",
            "java/net/DatagramSocket", "java/net/MulticastSocket", "java/net/HttpURLConnection",
            "java/net/URLConnection", "java/nio/channels/SocketChannel", "java/nio/channels/ServerSocketChannel",
            "java/nio/channels/DatagramChannel", "java/nio/channels/AsynchronousSocketChannel",
            "java/nio/channels/AsynchronousServerSocketChannel", "sun/misc/Unsafe");

    /** Owner prefixes (packages) that may not appear at all. */
    private static final List<String> BANNED_PREFIXES = List.of("java/net/http/", "javax/script/", "java/sql/",
            "javax/sql/", "javax/naming/", "java/rmi/", "javax/net/", "jdk/jshell/", "jdk/internal/", "jdk/nashorn/",
            "com/sun/net/httpserver/");

    /** Individual methods, as owner#name. */
    private static final Set<String> BANNED_METHODS = Set.of(
            "java/lang/Runtime#exec", "java/lang/Runtime#halt", "java/lang/Runtime#load",
            "java/lang/Runtime#loadLibrary", "java/lang/System#load", "java/lang/System#loadLibrary",
            "java/net/URL#openConnection", "java/net/URL#openStream",
            "java/lang/Thread#stop", "java/lang/Thread#suspend", "java/lang/Thread#resume");

    /** Methods that end the JVM: allowed only in the classes the caller names (the application entry point). */
    private static final Set<String> EXIT_METHODS = Set.of("java/lang/System#exit", "java/lang/Runtime#exit");

    private BytecodeScanner() {
    }

    static List<Finding> scanShortTypes(Path classFile) {
        return scanShortTypes(read(classFile));
    }

    static List<Finding> scanShortTypes(byte[] bytes) {
        ClassModel model = ClassFile.of().parse(bytes);
        String owner = model.thisClass().asInternalName();
        List<Finding> findings = new ArrayList<>();

        signature(owner, "", model.findAttribute(Attributes.signature()).map(a -> a.signature().stringValue()).orElse(""),
                findings);
        for (FieldModel field : model.fields()) {
            String name = field.fieldName().stringValue();
            if (shortish(field.fieldTypeSymbol())) {
                findings.add(new Finding(owner, name, "field of a 16-bit type"));
            }
            signature(owner, name, field.findAttribute(Attributes.signature()).map(a -> a.signature().stringValue())
                    .orElse(""), findings);
        }
        for (MethodModel method : model.methods()) {
            String name = method.methodName().stringValue();
            if (shortish(method.methodTypeSymbol())) {
                findings.add(new Finding(owner, name, "method descriptor mentions a 16-bit type"));
            }
            signature(owner, name, method.findAttribute(Attributes.signature()).map(a -> a.signature().stringValue())
                    .orElse(""), findings);
            method.code().ifPresent(code -> scanCode(owner, name, code, findings));
        }
        return findings;
    }

    private static void scanCode(String owner, String method, CodeModel code, List<Finding> findings) {
        for (CodeElement element : code) {
            switch (element) {
                case ConvertInstruction c when c.toType() == TypeKind.SHORT || c.fromType() == TypeKind.SHORT ->
                        findings.add(new Finding(owner, method, "conversion to or from a 16-bit integer (a cast)"));
                case ArrayLoadInstruction a when a.typeKind() == TypeKind.SHORT ->
                        findings.add(new Finding(owner, method, "load from a 16-bit array"));
                case ArrayStoreInstruction a when a.typeKind() == TypeKind.SHORT ->
                        findings.add(new Finding(owner, method, "store into a 16-bit array"));
                case NewPrimitiveArrayInstruction a when a.typeKind() == TypeKind.SHORT ->
                        findings.add(new Finding(owner, method, "allocation of a 16-bit array"));
                case LocalVariable v when shortish(v.typeSymbol()) ->
                        findings.add(new Finding(owner, method, "local variable " + v.name().stringValue()
                                + " of a 16-bit type"));
                case InvokeInstruction i -> {
                    if (TYPE_INTERNAL_NAMES.contains(i.owner().asInternalName()) || shortish(i.typeSymbol())
                            || METHOD_NAMES.contains(i.name().stringValue())) {
                        findings.add(new Finding(owner, method, "call " + i.owner().asInternalName() + "."
                                + i.name().stringValue() + " involves a 16-bit type"));
                    }
                }
                case FieldInstruction f when shortish(f.typeSymbol()) || TYPE_INTERNAL_NAMES.contains(f.owner().asInternalName()) ->
                        findings.add(new Finding(owner, method, "field access " + f.name().stringValue()
                                + " involves a 16-bit type"));
                case InvokeDynamicInstruction d when shortish(d.typeSymbol()) ->
                        findings.add(new Finding(owner, method, "dynamic call site with a 16-bit type"));
                case NewObjectInstruction n when TYPE_INTERNAL_NAMES.contains(n.className().asInternalName()) ->
                        findings.add(new Finding(owner, method, "instantiates a 16-bit wrapper"));
                case TypeCheckInstruction t when internalShortish(t.type().asInternalName()) ->
                        findings.add(new Finding(owner, method, "type check against a 16-bit type"));
                case NewReferenceArrayInstruction a when internalShortish(a.componentType().asInternalName()) ->
                        findings.add(new Finding(owner, method, "array of a 16-bit wrapper"));
                case NewMultiArrayInstruction a when internalShortish(a.arrayType().asInternalName()) ->
                        findings.add(new Finding(owner, method, "multi-dimensional 16-bit array"));
                default -> {
                    // everything else cannot carry a 16-bit type
                }
            }
        }
    }

    private static void signature(String owner, String member, String signature, List<Finding> findings) {
        if (signature.contains("L" + WRAPPER + ";")) {
            findings.add(new Finding(owner, member, "generic signature mentions the 16-bit wrapper"));
        }
    }

    private static boolean shortish(ClassDesc desc) {
        ClassDesc d = desc;
        while (d.isArray()) {
            d = d.componentType();
        }
        return TYPE_DESCRIPTORS.contains(d.descriptorString());
    }

    private static boolean shortish(MethodTypeDesc desc) {
        if (shortish(desc.returnType())) {
            return true;
        }
        for (ClassDesc parameter : desc.parameterList()) {
            if (shortish(parameter)) {
                return true;
            }
        }
        return false;
    }

    private static boolean internalShortish(String internalName) {
        String name = internalName;
        while (name.startsWith("[")) {
            name = name.substring(1);
        }
        return TYPE_INTERNAL_NAMES.contains(name) || TYPE_DESCRIPTORS.contains(name);
    }

    /**
     * Forbidden APIs in one class.
     *
     * @param mayExit internal names of the classes that may end the JVM (the application entry point)
     */
    static List<Finding> scanForbiddenApis(Path classFile, Set<String> mayExit) {
        return scanForbiddenApis(read(classFile), mayExit);
    }

    static List<Finding> scanForbiddenApis(byte[] bytes, Set<String> mayExit) {
        ClassModel model = ClassFile.of().parse(bytes);
        String owner = model.thisClass().asInternalName();
        List<Finding> findings = new ArrayList<>();
        for (FieldModel field : model.fields()) {
            bannedDescriptor(owner, field.fieldName().stringValue(), field.fieldTypeSymbol().descriptorString(), findings);
        }
        for (MethodModel method : model.methods()) {
            String name = method.methodName().stringValue();
            if (method.flags().has(AccessFlag.NATIVE)) {
                findings.add(new Finding(owner, name, "native method"));
            }
            bannedDescriptor(owner, name, method.methodTypeSymbol().descriptorString(), findings);
            method.code().ifPresent(code -> {
                for (CodeElement element : code) {
                    switch (element) {
                        case InvokeInstruction i -> {
                            String target = i.owner().asInternalName();
                            String call = target + "#" + i.name().stringValue();
                            bannedOwner(owner, name, target, findings);
                            bannedDescriptor(owner, name, i.typeSymbol().descriptorString(), findings);
                            if (BANNED_METHODS.contains(call)) {
                                findings.add(new Finding(owner, name, "calls " + call));
                            }
                            if (EXIT_METHODS.contains(call) && !mayExit.contains(owner)) {
                                findings.add(new Finding(owner, name, "calls " + call + " outside the entry point"));
                            }
                        }
                        case FieldInstruction f -> bannedOwner(owner, name, f.owner().asInternalName(), findings);
                        case NewObjectInstruction n -> bannedOwner(owner, name, n.className().asInternalName(), findings);
                        case TypeCheckInstruction t -> bannedOwner(owner, name, t.type().asInternalName(), findings);
                        default -> {
                            // no other instruction names a type or a method
                        }
                    }
                }
            });
        }
        return findings;
    }

    private static void bannedOwner(String owner, String method, String target, List<Finding> findings) {
        if (BANNED_OWNERS.contains(target)) {
            findings.add(new Finding(owner, method, "uses " + target));
            return;
        }
        for (String prefix : BANNED_PREFIXES) {
            if (target.startsWith(prefix)) {
                findings.add(new Finding(owner, method, "uses " + target));
                return;
            }
        }
    }

    /** Checks the class names inside a descriptor such as {@code (Ljava/net/Socket;I)V}. */
    private static void bannedDescriptor(String owner, String member, String descriptor, List<Finding> findings) {
        int from = 0;
        while (true) {
            int start = descriptor.indexOf('L', from);
            if (start < 0) {
                return;
            }
            int end = descriptor.indexOf(';', start);
            if (end < 0) {
                return;
            }
            bannedOwner(owner, member, descriptor.substring(start + 1, end), findings);
            from = end + 1;
        }
    }

    private static byte[] read(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + file, e);
        }
    }
}
