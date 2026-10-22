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

package org.ghyura.cf.showcase.cucumber.webshop;

import java.util.ArrayList;
import java.util.List;

/**
 * What one web scenario chose and what the customer meant; PicoContainer shares the one instance between the step and
 * hook classes of a scenario. A repeated action changes nothing of what the customer meant.
 */
public final class WebWorld {

    String baseUrl = "";
    boolean stealth;
    String language = "en";
    String account = "guest";
    final List<String> items = new ArrayList<>();
    String coupon = "";
    String shipping = "standard";
    boolean couponApplied;
    boolean giftWrap;
    /** The last action, which a sudden "goes back and repeats the last action" does once more. */
    Runnable lastAction;
    long started = System.nanoTime();
}
