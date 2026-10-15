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

import org.ghyura.cf.input.ConstraintSidecarReader;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.model.AtomicFiles;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Bounded UTF-8 files and optimistic overwrite protection for the Sieve workbench. Call off the Swing thread. */
public final class SieveFiles {
    private SieveFiles() { }
    public record Opened(SieveDocument document, String fingerprint) { }
    public record Text(String content, String fingerprint) { }

    public static Opened open(Path scenario, InputSource.Reader inputs) {
        Path companion = ConstraintSidecarReader.companionOf(scenario);
        Text text = read(companion, inputs, SieveDocument.MAX_BYTES);
        return new Opened(text.fingerprint().isEmpty() ? SieveDocument.empty()
                : SieveDocument.parse(text.content(), companion.toString()), text.fingerprint());
    }

    public static Text read(Path file, InputSource.Reader inputs, long limit) {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return new Text("", "");
        byte[] bytes = inputs.read(file, limit).copyOfBytes();
        try {
            return new Text(StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString(), fingerprint(bytes));
        } catch (IOException e) { throw new UncheckedIOException(e); }
    }

    public static String fingerprint(Path file) {
        return !Files.exists(file, LinkOption.NOFOLLOW_LINKS) ? ""
                : fingerprint(InputSource.read(file, SieveDocument.MAX_BYTES).copyOfBytes());
    }

    public static Path layoutOf(Path sidecar) { return sidecar.resolveSibling(sidecar.getFileName() + ".layout.json"); }

    /** The caller validates semantics before writing. An externally changed file requires reopening or exporting elsewhere. */
    public static String write(Path target, String content, String expected) {
        if (Files.isSymbolicLink(target)) throw new IllegalStateException("Refusing to replace a symbolic link: " + target);
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > SieveDocument.MAX_BYTES) throw new IllegalArgumentException("The interactive Sieve file limit is 16 MiB.");
        if (!fingerprint(target).equals(expected)) throw new IllegalStateException("File changed outside this editor: " + target
                + ". Reopen it, or export your edits to a different file.");
        try { AtomicFiles.write(target, bytes); }
        catch (IOException e) { throw new UncheckedIOException(e); }
        return fingerprint(bytes);
    }

    private static String fingerprint(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
