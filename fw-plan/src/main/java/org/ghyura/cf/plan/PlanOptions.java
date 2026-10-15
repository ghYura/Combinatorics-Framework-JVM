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

package org.ghyura.cf.plan;

import org.ghyura.cf.config.RunConfig;

import java.util.Objects;

/**
 * The switches the legacy {@code workbook.*} and {@code seq.*} settings gave the FW_Seq parser, as one typed
 * value. {@link #legacyDefaults()} is what the legacy Core did when nothing was configured; every default is on.
 *
 * @param autoGenerateMissingSheets  create an empty virtual sheet for a FW_Seq reference that names no data sheet
 *                                   (legacy {@code workbook.autoGenerateMissingSheetsFromFwSeq})
 * @param virtualSheetPrefix         the name prefix of the target generated for a headless row
 *                                   (legacy {@code workbook.virtualSheetNamePrefix})
 * @param headlessRowsGetTarget      give a row that starts with an FW_ token a generated target of its own
 *                                   (legacy {@code seq.headlessRowsAutoSynthesizeTarget})
 * @param nestedJoinersEnabled       resolve the placeholders FW_() and FW_()G to earlier joiners
 *                                   (legacy {@code seq.nestedFwBraceEnabled})
 * @param autoPromoteSingleVerb      repeat a lone combination verb so that both table generations are filled
 *                                   (legacy {@code seq.autoPromoteSingleVerb})
 * @param autoGenerateSheetNames     give every sheet that FW_SheetNames does not list an empty prefix and ending
 *                                   (legacy {@code workbook.autoGenerateMissingFwSheetNames})
 */
public record PlanOptions(boolean autoGenerateMissingSheets, String virtualSheetPrefix, boolean headlessRowsGetTarget,
                          boolean nestedJoinersEnabled, boolean autoPromoteSingleVerb, boolean autoGenerateSheetNames) {

    public PlanOptions {
        Objects.requireNonNull(virtualSheetPrefix, "virtualSheetPrefix");
        if (virtualSheetPrefix.isBlank()) {
            throw new IllegalArgumentException("the virtual sheet prefix must not be blank");
        }
    }

    public static PlanOptions legacyDefaults() {
        return new PlanOptions(true, "FW_VIRTUAL_", true, true, true, true);
    }

    /**
     * The options a resolved configuration asks for ({@code cf.plan.*}, architect decision G4-F25), so that the
     * command line, the environment, a properties file and the embedding API all reach the planner the same way.
     */
    public static PlanOptions of(RunConfig config) {
        Objects.requireNonNull(config, "config");
        return new PlanOptions(config.planAutoGenerateMissingSheets(), config.planVirtualSheetPrefix(),
                config.planHeadlessRowsGetTarget(), config.planNestedJoinersEnabled(), config.planAutoPromoteSingleVerb(),
                config.planAutoGenerateSheetNames());
    }
}
