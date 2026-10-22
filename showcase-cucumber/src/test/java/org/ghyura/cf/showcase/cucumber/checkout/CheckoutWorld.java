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

import org.ghyura.cf.showcase.cucumber.shop.Catalog;
import org.ghyura.cf.showcase.cucumber.shop.CheckoutEngine;
import org.ghyura.cf.showcase.cucumber.shop.Order;
import org.ghyura.cf.showcase.cucumber.shop.ReferencePricing;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The state of one scenario. PicoContainer creates it once per scenario and auto-wires that one instance into every step
 * and hook class that asks for it in its constructor, so they share it without statics.
 *
 * <p>It keeps two accounts of the scenario: what the engine was sent (retries included) and what the customer
 * <i>meant</i> (each request once, each item at the price it had when it was added). The checks compare the engine with
 * the second. They live here, not in the Then steps, so that a hook can evaluate all of them even after the first failing
 * Then has stopped the scenario.</p>
 */
public final class CheckoutWorld {

    final Catalog catalog = new Catalog();
    final CheckoutEngine engine = new CheckoutEngine(catalog);
    String channel = "test";
    String customer = "member";
    String payment = "card";
    boolean startsWithBookmark;
    /** What the customer meant: each item at its price when added, each coupon once, the shipping last chosen. */
    final List<BigDecimal> pricesWhenAdded = new ArrayList<>();
    final List<String> couponsMeant = new ArrayList<>();
    String shippingMeant;
    /** The last request sent, which a sudden duplicate delivery sends once more. */
    Runnable lastRequest;
    Order order;
    private CheckoutEngine.Checkout checkout;

    /** The open checkout: opened with the first action, with the cart's starting items at that moment's prices. */
    CheckoutEngine.Checkout checkout() {
        if (checkout == null) {
            checkout = engine.open(customer);
            if (startsWithBookmark) {
                pricesWhenAdded.add(catalog.price("bookmark"));
                checkout.add("bookmark");
            }
        }
        return checkout;
    }

    /** Sends a request and remembers it as the one a duplicate delivery would repeat. */
    void send(Runnable request) {
        request.run();
        lastRequest = request;
    }

    /**
     * Places (or places again) the order through the scenario's channel. The "test" channel calls the engine from the
     * step, with Cucumber on the call stack. The "terminal" channel places it from a plain thread, as the shop's own till
     * would: the engine cannot tell it is being tested, which outsmarts its defeat device (D7).
     */
    Order place() {
        CheckoutEngine.Checkout c = checkout();
        if (!channel.equals("terminal")) {
            return c.place(payment);
        }
        AtomicReference<Order> placed = new AtomicReference<>();
        AtomicReference<RuntimeException> failed = new AtomicReference<>();
        Thread till = Thread.ofPlatform().name("pos-terminal").start(() -> {
            try {
                placed.set(c.place(payment));
            } catch (RuntimeException e) {
                failed.set(e);
            }
        });
        try {
            till.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while the terminal placed the order", e);
        }
        if (failed.get() != null) {
            throw failed.get();
        }
        return placed.get();
    }

    // ---- the checks: null when the rule holds, else what is wrong ---------------------------------------------------

    String oneOrderProblem() {
        int placed = engine.orders().size();
        return placed == 1 ? null : "expected exactly one order, the shop placed " + placed;
    }

    /** The money, read from the payment provider's ledger rather than from the order the engine hands back. */
    String chargeProblem() {
        List<CheckoutEngine.Charge> charged = engine.charges().stream().filter(c -> c.orderId() == order.id()).toList();
        if (charged.size() != 1) {
            return "order " + order.id() + " was charged " + charged.size() + " times";
        }
        BigDecimal amount = charged.getFirst().amount();
        return amount.compareTo(order.total()) == 0 ? null
                : "the order totals " + order.total() + ", the " + payment + " was charged " + amount;
    }

    String freeShippingProblem() {
        boolean freeShip = ReferencePricing.codes(couponsMeant).contains("FREESHIP");
        return !freeShip || order.shippingCost().signum() == 0 ? null
                : "FREESHIP was applied, yet the order charges " + order.shippingCost() + " for shipping";
    }

    BigDecimal expectedTotal() {
        BigDecimal ship = catalog.shippingCost(shippingMeant == null ? "standard" : shippingMeant);
        return ReferencePricing.expectedTotal(pricesWhenAdded, couponsMeant, ship, false);
    }

    String totalProblem() {
        BigDecimal expected = expectedTotal();
        return expected.compareTo(order.total()) == 0 ? null : "the order totals " + order.total() + ", the business rules say " + expected;
    }
}
