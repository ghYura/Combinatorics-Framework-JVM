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

package org.ghyura.cf.analysis;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Admission covers the input and its parser/retained expansion until the consumer closes it. */
final class AnalysisInput implements AutoCloseable {
    final String text;
    final String sha256;
    private final MemoryBudget.Lease lease;

    private AnalysisInput(String text, String sha256, MemoryBudget.Lease lease) {
        this.text = text;
        this.sha256 = sha256;
        this.lease = lease;
    }

    static AnalysisInput read(Path file, MemoryBudget budget, long expansion) {
        return read(file, budget, expansion, false);
    }

    /** @param replace false refuses a file that is not UTF-8; true reads it as CPython's errors="replace" did */
    static AnalysisInput read(Path file, MemoryBudget budget, long expansion, boolean replace) {
        MemoryBudget.Lease lease = null;
        boolean transferred = false;
        try {
            AnalysisInput result;
            try (SeekableByteChannel channel = Files.newByteChannel(file)) {
                long size = channel.size();
                if (size > Integer.MAX_VALUE - 16L) throw MemorySizes.limit("analysis input exceeds the supported document size");
                lease = budget.reserve(MemorySizes.add(65_536, MemorySizes.multiply(expansion, size)), "analysis input " + file);
                byte[] bytes = new byte[(int) size];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) throw new IOException("input was truncated while reading");
                }
                if (channel.read(ByteBuffer.allocate(1)) != -1) throw MemorySizes.limit("analysis input grew beyond its admitted size");
                String text = replace ? Py.decodeUtf8(bytes) : StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                result = new AnalysisInput(text, hash, lease);
            }
            transferred = true;
            return result;
        } catch (CharacterCodingException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "analysis input is not UTF-8", SourceLocation.of(file.toString())));
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_UNREADABLE, "analysis input cannot be read", SourceLocation.of(file.toString())));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the JVM", e);
        } finally {
            if (!transferred && lease != null) lease.close();
        }
    }

    @Override public void close() { lease.close(); }
}
