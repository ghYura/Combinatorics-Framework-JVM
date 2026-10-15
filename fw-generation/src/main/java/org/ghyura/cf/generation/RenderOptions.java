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

package org.ghyura.cf.generation;

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.Canonical;

import java.util.List;
import java.util.Objects;

/**
 * How a candidate's source text is written (the legacy Reader's rendering settings, work package M03).
 *
 * @param concatenator       written after every sheet instead of its ending when not empty (cf.render.concatenator)
 * @param sheetConcatenator  whether FW_Concatenator= joins the values of a sheet's row (cf.render.sheetConcatenator)
 * @param preserveWhitespace render values as written; false refines every value not refined at parse
 * @param spaceBefore        regular expressions a refined value gains a space before (outside quotes)
 * @param spaceAfter         regular expressions a refined value gains a space after
 * @param newlineBefore      regular expressions a refined value gains a line break before
 * @param newlineAfter       regular expressions a refined value gains a line break after
 * @param markerReplacement  replace FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE by the candidate's display id
 */
public record RenderOptions(String concatenator, RunConfig.SheetConcatenator sheetConcatenator, boolean preserveWhitespace,
                            List<String> spaceBefore, List<String> spaceAfter, List<String> newlineBefore,
                            List<String> newlineAfter, boolean markerReplacement) {

    public RenderOptions {
        Objects.requireNonNull(concatenator, "concatenator");
        Objects.requireNonNull(sheetConcatenator, "sheetConcatenator");
        spaceBefore = List.copyOf(spaceBefore);
        spaceAfter = List.copyOf(spaceAfter);
        newlineBefore = List.copyOf(newlineBefore);
        newlineAfter = List.copyOf(newlineAfter);
    }

    public static RenderOptions defaults() {
        return of(RunConfig.defaults());
    }

    public static RenderOptions of(RunConfig config) {
        Objects.requireNonNull(config, "config");
        return new RenderOptions(config.renderConcatenator(), config.renderSheetConcatenator(), config.renderPreserveWhitespace(),
                config.renderSpaceBefore(), config.renderSpaceAfter(), config.renderNewlineBefore(), config.renderNewlineAfter(),
                config.renderMarkerReplacement());
    }

    /** A canonical text of every option, for identities. */
    public String canonical() {
        return "concatenator=" + Canonical.escape(concatenator) + ";sheetConcatenator=" + sheetConcatenator.token()
                + ";preserveWhitespace=" + preserveWhitespace + ";spaceBefore=" + Canonical.escape(String.join("\n", spaceBefore))
                + ";spaceAfter=" + Canonical.escape(String.join("\n", spaceAfter)) + ";newlineBefore="
                + Canonical.escape(String.join("\n", newlineBefore)) + ";newlineAfter=" + Canonical.escape(String.join("\n", newlineAfter))
                + ";marker=" + markerReplacement;
    }
}
