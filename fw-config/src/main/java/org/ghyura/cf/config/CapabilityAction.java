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

package org.ghyura.cf.config;

/** How the architecture treats a legacy capability (compatibility.xml, mapping action). */
public enum CapabilityAction {
    /** The mechanism is replaced by a Java equivalent with the same observable behavior. */
    REPLACE,
    /** Source in another language is ported to Java. */
    PORT,
    /** Existing Java is adapted to the single-JVM architecture. */
    ADAPT,
    /** Each family needs its own Java port; an unsupported diagnostic alone is not a port. */
    PORT_REQUIRED,
    /** The guarantee cannot exist in one JVM; it is reported, never emulated. */
    INCOMPATIBLE
}
