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

package org.ghyura.cf.showcase.cucumber.checkout;

import io.cucumber.java.After;
import org.ghyura.cf.showcase.cucumber.CaseBoard;

/**
 * After every scenario, even one stopped by its first failing Then, evaluates every check and puts each on the metrics
 * line ({@code chk_*=1} holds, {@code 0} fails). The verdict names the most serious failure; these metrics show the ones
 * it masks, which is how a defect hiding behind another one is still seen.
 */
public class CheckoutHooks {

    private final CheckoutWorld world;

    public CheckoutHooks(CheckoutWorld world) {
        this.world = world;
    }

    @After
    public void recordEveryCheck() {
        if (world.order == null) {
            return;
        }
        CaseBoard.metric("chk_one_order", world.oneOrderProblem() == null ? 1 : 0);
        CaseBoard.metric("chk_charge", world.chargeProblem() == null ? 1 : 0);
        CaseBoard.metric("chk_free_shipping", world.freeShippingProblem() == null ? 1 : 0);
        CaseBoard.metric("chk_total", world.totalProblem() == null ? 1 : 0);
        CaseBoard.metric("expected_eur", world.expectedTotal());
    }
}
