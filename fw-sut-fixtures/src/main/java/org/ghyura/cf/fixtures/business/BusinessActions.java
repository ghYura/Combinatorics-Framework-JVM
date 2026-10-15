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

package org.ghyura.cf.fixtures.business;

import java.util.Map;

import static org.ghyura.cf.fixtures.business.FixtureValues.integer;
import static org.ghyura.cf.fixtures.business.FixtureValues.text;

/** Explicit action mapping shared by fixtures and plugins; no code evaluation or transport layer. */
public final class BusinessActions {
    private BusinessActions() { }

    public static Object billing(BillingSut sut, Map<String, Object> action) {
        String operation = text(action, "op", "");
        Long invoice = action.get("invoice_id") == null ? null : integer(action, "invoice_id", 0).longValueExact();
        return switch (operation) {
            case "subscribe" -> { sut.subscribe(text(action, "plan", "basic")); yield null; }
            case "change_plan" -> { sut.changePlan(text(action, "plan", "basic")); yield null; }
            case "pause" -> { sut.pause(); yield null; }
            case "resume" -> { sut.resume(); yield null; }
            case "cancel" -> { sut.cancel(); yield null; }
            case "reactivate" -> { sut.reactivate(); yield null; }
            case "tick" -> { sut.tick(integer(action, "days", 1).intValueExact()); yield null; }
            case "apply_coupon" -> { sut.applyCoupon(text(action, "code", "none")); yield null; }
            case "refund" -> sut.refund(invoice);
            case "chargeback" -> sut.chargeback(invoice);
            case "replay" -> { sut.replay(text(action, "event_id", null)); yield null; }
            default -> throw new IllegalArgumentException("unknown billing action " + operation);
        };
    }

    public static Object fulfilment(OrderFulfilmentSut sut, Map<String, Object> action) {
        String operation = text(action, "op", "");
        int k = integer(action, "k", 0).intValueExact();
        return switch (operation) {
            case "submit" -> {
                sut.submit(text(action, "oid", "order-1"), text(action, "sku", "widget"),
                        integer(action, "qty", 1), integer(action, "amount", 100)); yield null;
            }
            case "deliver" -> snapshot(sut.deliver(k));
            case "duplicate" -> snapshot(sut.duplicate(k));
            case "drop" -> snapshot(sut.drop(k));
            case "drain" -> sut.drain(integer(action, "limit", 200).intValueExact());
            case "tick" -> { sut.tick(integer(action, "n", 1).intValueExact()); yield null; }
            case "restart" -> { sut.restart(text(action, "service", "payment")); yield null; }
            default -> throw new IllegalArgumentException("unknown fulfilment action " + operation);
        };
    }

    private static Object snapshot(OrderFulfilmentSut.Message message) {
        return message == null ? null : message.snapshot();
    }
}
