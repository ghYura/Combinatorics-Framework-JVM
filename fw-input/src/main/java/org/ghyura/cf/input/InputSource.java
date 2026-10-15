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

package org.ghyura.cf.input;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * The bytes of one input document and the name that locations refer to. The input is read once and held in
 * memory; readers never touch the file again, never write to it, and never keep a handle on it. (The legacy
 * Core opened workbooks through POI's path-based factory, which saves the file back on close and so rewrites
 * the user's input; reading from bytes rules that out by construction.)
 */
public final class InputSource {

    /** File access supplied by an owner that can admit each input before reading it. */
    @FunctionalInterface
    public interface Reader {
        InputSource read(Path file, long maxBytes);
    }

    /** Default cap on one input document: 256 MiB. */
    public static final long DEFAULT_MAX_BYTES = 256L * 1024 * 1024;

    private final String name;
    private final byte[] bytes;

    private InputSource(String name, byte[] bytes) {
        this.name = Objects.requireNonNull(name, "name");
        this.bytes = bytes;
    }

    /** Takes a copy of {@code bytes}. */
    public static InputSource of(String name, byte[] bytes) {
        return new InputSource(name, Objects.requireNonNull(bytes, "bytes").clone());
    }

    public static InputSource ofText(String name, String text) {
        return new InputSource(name, Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8));
    }

    /** Reads a regular file completely, read-only. */
    public static InputSource read(Path file) {
        return read(file, DEFAULT_MAX_BYTES);
    }

    /**
     * @throws FrameworkException INPUT_UNREADABLE when the file cannot be opened or read, INPUT_LIMIT when it is
     *                            larger than {@code maxBytes}
     */
    public static InputSource read(Path file, long maxBytes) {
        String name = file.toString();
        checkedSize(file, maxBytes);
        try (var input = Files.newInputStream(file)) {
            return read(name, input, maxBytes);
        } catch (IOException e) {
            throw unreadable(name, e);
        }
    }

    /** Checks a regular file's size without reading it, for admission before allocation. Reading must still enforce the cap. */
    public static long checkedSize(Path file, long maxBytes) {
        String name = file.toString();
        if (maxBytes < 0) {
            throw new IllegalArgumentException("a byte limit cannot be negative: " + maxBytes);
        }
        long ceiling = Math.min(maxBytes, Integer.MAX_VALUE - 8L);
        try {
            if (!Files.isRegularFile(file)) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE,
                        "not a readable regular file", SourceLocation.of(name)));
            }
            long size = Files.size(file);
            if (size > ceiling) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_LIMIT,
                        "the input has " + size + " bytes; the limit is " + ceiling, SourceLocation.of(name),
                        "bytes", Long.toString(size), "limit", Long.toString(ceiling)));
            }
            return size;
        } catch (IOException e) {
            throw unreadable(name, e);
        }
    }

    private static FrameworkException unreadable(String name, IOException e) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE,
                "cannot read the input (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")",
                SourceLocation.of(name)), e);
    }

    /** Enforces the byte ceiling on the stream as well as its initial file size; never owns the stream. */
    static InputSource read(String name, java.io.InputStream input, long maxBytes) throws IOException {
        if (maxBytes < 0) throw new IllegalArgumentException("a byte limit cannot be negative: " + maxBytes);
        int ceiling = (int) Math.min(maxBytes, Integer.MAX_VALUE - 8L);
        byte[] bytes = input.readNBytes(ceiling);
        if (input.read() != -1) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_LIMIT,
                    "the input exceeds the limit of " + ceiling + " bytes while reading", SourceLocation.of(name),
                    "limit", Integer.toString(ceiling)));
        }
        return new InputSource(name, bytes);
    }

    /** The name locations refer to: a file path as given, or a logical name. */
    public String name() {
        return name;
    }

    public int size() {
        return bytes.length;
    }

    /** The document's bytes; callers must not modify them. Package-private to keep the copy discipline. */
    byte[] view() {
        return bytes;
    }

    /** A copy of the document's bytes. */
    public byte[] copyOfBytes() {
        return bytes.clone();
    }

    @Override
    public String toString() {
        return "InputSource[" + name + ", " + bytes.length + " bytes]";
    }
}
