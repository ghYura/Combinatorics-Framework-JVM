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

package org.ghyura.cf.spi;

import org.ghyura.cf.model.CandidateId;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * An assembled candidate as a system under test sees it: its identity, every sheet's selection with the value texts
 * of its row, and, on demand, the rendered source.
 */
public interface SutCandidate {

    /** The candidate's identity (a digest of plan, dictionary, policies and coordinates). */
    CandidateId id();

    /** The legacy display id: finalId_0_0, or finalId_optId_size. */
    String displayId();

    /** The candidate's position in the generated space. */
    BigInteger ordinal();

    /** Every sheet of the candidate, in column order (ascending sheet key). */
    List<SheetSelection> selections();

    /** The selection of the named sheet, if the candidate has that column. */
    default Optional<SheetSelection> selection(String sheet) {
        return selections().stream().filter(s -> s.sheet().equals(sheet)).findFirst();
    }

    /** The rendered source of the candidate (the text the legacy Reader wrote), rendered when first asked for. */
    String source();

    /**
     * An estimate of the bytes this candidate holds while it waits and runs, reserved from the run's budget when it is
     * admitted ({@code cf.pipeline.queue.maxBytes}). The default counts its selections, twice over for the source it may
     * render: object headers and two bytes per character.
     */
    default long footprintBytes() {
        long bytes = 256;
        for (SheetSelection s : selections()) {
            bytes += 64 + 2L * s.sheet().length();
            for (String value : s.values()) {
                bytes += 48 + 2L * value.length();
            }
        }
        return 2 * bytes;
    }
}
