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

package org.ghyura.cf.input.builder;

/**
 * Factories for the verb tokens of columns 4 to 6 of an {@code FW_Seq} row. Each returns exactly the text the
 * engine recognises, so the builder stays a typed mirror of what an author would type into a cell. Adapted from
 * the legacy {@code com.company.excel.Verb}.
 */
public final class Verb {

    private Verb() {
    }

    /** k-combinations without repetition. */
    public static String combi(int k) {
        return "FW_Combi(" + k + ")";
    }

    /** k-combinations with repetition. */
    public static String combiR(int k) {
        return "FW_CombiR(" + k + ")";
    }

    /** Permutations; in the legacy engine the argument is accepted but inert (permute(k) lowers to combi then permut). */
    public static String permut(int k) {
        return "FW_Permut(" + k + ")";
    }

    /** Permutations with repetition. */
    public static String permutR(int k) {
        return "FW_PermutR(" + k + ")";
    }

    /** All subsets. */
    public static String subsets() {
        return "FW_Subsets";
    }

    /** Cartesian product. */
    public static String cartes() {
        return "FW_Cartes";
    }

    /** Cartesian product, first-only variant. */
    public static String cartesFirst() {
        return "FW_Cartes_first";
    }

    /** Group rows into one. */
    public static String group() {
        return "FW_Group";
    }

    /** A separator key between rows. */
    public static String separator(String separator) {
        return "FW_Separator(" + separator + ")";
    }

    /** A regular-expression replacement over the numeric key text of grouped rows. */
    public static String replaceRe(String pattern, String replacement) {
        return "FW_ReplaceRE(" + pattern + "," + replacement + ")";
    }
}
