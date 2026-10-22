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
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The oracle: the business rules written down once more, independently of {@link CheckoutEngine}. Items cost what they
 * cost when they were put in the cart; coupon codes ignore case and surrounding spaces, and each counts once however
 * often it is entered; SAVE10 takes 10% off the items; FREESHIP makes the shipping free, whenever it is applied; gift
 * wrap costs {@link Catalog#GIFT_WRAP}; cents round half up.
 */
public final class ReferencePricing {

    private ReferencePricing() {
    }

    /** The coupons as the rules read them: trimmed, upper-cased, each once. */
    public static Set<String> codes(Collection<String> entered) {
        return entered.stream().map(c -> c.strip().toUpperCase(Locale.ROOT)).collect(Collectors.toSet());
    }

    public static BigDecimal expectedTotal(List<BigDecimal> pricesWhenAdded, Collection<String> entered, BigDecimal shippingCost,
                                           boolean giftWrap) {
        Set<String> coupons = codes(entered);
        BigDecimal subtotal = pricesWhenAdded.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = coupons.contains("SAVE10")
                ? subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
        BigDecimal shipping = coupons.contains("FREESHIP") ? BigDecimal.ZERO : shippingCost;
        BigDecimal wrap = giftWrap ? Catalog.GIFT_WRAP : BigDecimal.ZERO;
        return subtotal.subtract(discount).add(shipping).add(wrap).setScale(2, RoundingMode.HALF_UP);
    }
}
