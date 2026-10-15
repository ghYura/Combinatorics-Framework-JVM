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

package org.ghyura.cf.artifacts;

import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SutCandidate;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * One candidate of an imported legacy hand-over: a Java source file of a loose-files bundle. Its id is the digest of the
 * run, its legacy id (the file name without {@code .java}) and its source digest; the source is read again when it is
 * needed and refused if it changed since the import.
 */
public final class HandoffCandidate implements SutCandidate {

    private final CandidateId id;
    private final String legacyId;
    private final BigInteger ordinal;
    private final Path file;
    private final String sourceSha256;
    private final long size;

    HandoffCandidate(String runId, String legacyId, long ordinal, Path file, String sourceSha256, long size) {
        this.legacyId = Objects.requireNonNull(legacyId, "legacyId");
        this.file = Objects.requireNonNull(file, "file");
        this.sourceSha256 = Objects.requireNonNull(sourceSha256, "sourceSha256");
        this.ordinal = BigInteger.valueOf(ordinal);
        this.size = size;
        this.id = new CandidateId(Canonical.sha256Hex("legacy-handoff/1\nrun=" + Canonical.escape(runId) + "\nid="
                + Canonical.escape(legacyId) + "\nsource=" + sourceSha256 + "\n"));
    }

    @Override
    public CandidateId id() {
        return id;
    }

    @Override
    public String displayId() {
        return legacyId;
    }

    @Override
    public BigInteger ordinal() {
        return ordinal;
    }

    /** A hand-over carries rendered sources only, no sheet selections. */
    @Override
    public List<SheetSelection> selections() {
        return List.of();
    }

    /** The file, read again and refused when its digest no longer matches the one taken at import. */
    @Override
    public String source() {
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException("candidate " + legacyId + " cannot be read again from " + file, e);
        }
        if (!Canonical.sha256Hex(bytes).equals(sourceSha256)) {
            throw new IllegalStateException("candidate " + legacyId + " changed on disk since the hand-over was imported: " + file);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** The SHA-256 of the file's bytes at import. */
    public String sourceSha256() {
        return sourceSha256;
    }

    public Path file() {
        return file;
    }

    @Override
    public long footprintBytes() {
        return 512 + 4 * size;
    }
}
