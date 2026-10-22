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

package org.ghyura.cf.showcase.cucumber.shop;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** The bookshop's price list: item prices, which may change while a checkout is open, and shipping prices. */
public final class Catalog {

    /** What gift wrap costs, once per order. */
    public static final BigDecimal GIFT_WRAP = new BigDecimal("2.00");

    private final Map<String, BigDecimal> items = new ConcurrentHashMap<>();
    private final Map<String, BigDecimal> shipping = new ConcurrentHashMap<>();

    /** The standard price list the web shop uses and the Gherkin Background of the checkout scenarios states. */
    public static Catalog standard() {
        Catalog c = new Catalog();
        c.price("book", new BigDecimal("12.00"));
        c.price("pen", new BigDecimal("2.50"));
        c.price("mug", new BigDecimal("8.00"));
        c.price("bookmark", new BigDecimal("0.05"));
        c.shippingCost("standard", new BigDecimal("4.90"));
        c.shippingCost("express", new BigDecimal("9.90"));
        c.shippingCost("pickup", new BigDecimal("0.00"));
        return c;
    }

    public void price(String item, BigDecimal price) {
        items.put(item, price);
    }

    public BigDecimal price(String item) {
        BigDecimal p = items.get(item);
        if (p == null) {
            throw new IllegalArgumentException("the bookshop does not sell '" + item + "'");
        }
        return p;
    }

    /** A price change, such as one published while a customer's checkout is open. */
    public void raise(String item, BigDecimal by) {
        price(item, price(item).add(by));
    }

    public void shippingCost(String option, BigDecimal price) {
        shipping.put(option, price);
    }

    public BigDecimal shippingCost(String option) {
        BigDecimal p = shipping.get(option);
        if (p == null) {
            throw new IllegalArgumentException("there is no shipping option '" + option + "'");
        }
        return p;
    }
}
