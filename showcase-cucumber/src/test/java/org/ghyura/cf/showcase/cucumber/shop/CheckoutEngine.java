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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The system under test of the showcase: the bookshop's checkout and the payment provider it charges. Every usual path
 * works. Its nine <b>planted defects</b> hide in the <i>order</i> of the customer's actions, in <i>when</i> a sudden event
 * strikes, and in who is watching; a matrix of parameter values alone finds almost none of them:
 *
 * <ol>
 *   <li><b>D1, stage order:</b> FREESHIP applied before the shipping is chosen is silently dropped;</li>
 *   <li><b>D2, stacking order:</b> for a VIP, SAVE10 applied <i>before</i> FREESHIP is counted twice;</li>
 *   <li><b>D3, retried payment:</b> an invoice order delivered twice is placed twice (card and wallet are idempotent);</li>
 *   <li><b>D4, price lock:</b> an order is priced at the price list of the moment it is placed, so a price change that
 *       strikes <i>after</i> an item was added leaks into the order (one that strikes before is the item's real price);</li>
 *   <li><b>D5, retried coupon:</b> a coupon request delivered twice counts twice;</li>
 *   <li><b>D6, rounding:</b> the discount is rounded half-even, where the rules say half up; it shows only when 10% of the
 *       items ends in exactly half a cent (a 0.05 bookmark beside a book);</li>
 *   <li><b>D7, defeat device:</b> when the engine finds a test runner on its call stack it prices by the book, which hides
 *       D4 and D6 from every test that calls it directly from a step;</li>
 *   <li><b>D8, stale quote:</b> a wallet is charged the amount quoted when the shipping was chosen, never updated by the
 *       actions that follow; the order itself shows the right total, only the payment provider's ledger tells;</li>
 *   <li><b>D9, retried item:</b> an add-to-cart request delivered twice adds the item twice.</li>
 * </ol>
 */
public final class CheckoutEngine {

    /** One charge the payment provider recorded. */
    public record Charge(int orderId, String payment, BigDecimal amount) {
    }

    private record Price(BigDecimal subtotal, BigDecimal discount, BigDecimal shipping, BigDecimal wrap, String option) {
        BigDecimal total() {
            return subtotal.subtract(discount).add(shipping).add(wrap).setScale(2, RoundingMode.HALF_UP);
        }
    }

    private final Catalog catalog;
    private final List<Order> orders = new ArrayList<>();
    private final List<Charge> charges = new ArrayList<>();

    public CheckoutEngine(Catalog catalog) {
        this.catalog = catalog;
    }

    public Checkout open(String customer) {
        if (!List.of("guest", "member", "vip").contains(customer)) {
            throw new IllegalArgumentException("unknown kind of customer '" + customer + "'");
        }
        return new Checkout(customer);
    }

    /** Every order placed so far. */
    public synchronized List<Order> orders() {
        return List.copyOf(orders);
    }

    /** The payment provider's ledger: every charge made so far. */
    public synchronized List<Charge> charges() {
        return List.copyOf(charges);
    }

    private synchronized Order record(Checkout c, String payment, Price p) {
        Order o = new Order(orders.size() + 1, c.customer, c.items, c.coupons, p.option(), c.giftWrap, payment, p.subtotal(),
                p.discount(), p.shipping(), p.wrap(), p.total());
        orders.add(o);
        // planted defect D8: the wallet gateway keeps the pre-authorisation made when the shipping was chosen
        BigDecimal charged = payment.equals("wallet") && c.quote != null ? c.quote : p.total();
        charges.add(new Charge(o.id(), payment, charged));
        return o;
    }

    /** Planted defect D7: whether a test runner is calling, judged from the call stack. */
    private static boolean underTest() {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(f -> f.getClassName().startsWith("io.cucumber.")
                || f.getClassName().startsWith("org.junit.") || f.getClassName().startsWith("org.testng.")));
    }

    /** One customer's open checkout. */
    public final class Checkout {
        private final String customer;
        private final List<String> items = new ArrayList<>();
        private final List<BigDecimal> pricesWhenAdded = new ArrayList<>();
        private final List<String> coupons = new ArrayList<>();
        private String shipping;
        private boolean freeShipping;
        private boolean giftWrap;
        private BigDecimal quote;
        private Order placed;

        private Checkout(String customer) {
            this.customer = customer;
        }

        /** Planted defect D9: no request identity, so a retried add adds the item again. */
        public void add(String item) {
            pricesWhenAdded.add(catalog.price(item));
            items.add(item);
        }

        public void applyCoupon(String entered) {
            String code = entered.strip().toUpperCase(Locale.ROOT);
            if (!code.equals("SAVE10") && !code.equals("FREESHIP")) {
                throw new IllegalArgumentException("unknown coupon '" + entered + "'");
            }
            coupons.add(code);                                  // planted defect D5: no check for a coupon already applied
            // planted defect D1: without a shipping option yet, the free shipping is silently dropped
            if (code.equals("FREESHIP") && shipping != null) {
                freeShipping = true;
            }
        }

        public void chooseShipping(String option) {
            catalog.shippingCost(option);
            shipping = option;
            quote = price(underTest()).total();                 // the wallet's pre-authorisation (see D8)
        }

        public void addGiftWrap() {
            giftWrap = true;
        }

        private Price price(boolean audited) {
            String option = shipping == null ? "standard" : shipping;
            BigDecimal subtotal = BigDecimal.ZERO;
            for (int i = 0; i < items.size(); i++) {
                // planted defect D4: today's price, not the price when added (unless D7 sees a test runner)
                subtotal = subtotal.add(audited ? pricesWhenAdded.get(i) : catalog.price(items.get(i)));
            }
            // planted defect D6: banker's rounding, where the rules round half up (unless D7 sees a test runner)
            BigDecimal tenth = subtotal.multiply(new BigDecimal("0.10")).setScale(2, audited ? RoundingMode.HALF_UP : RoundingMode.HALF_EVEN);
            BigDecimal discount = BigDecimal.ZERO;
            for (String c : coupons) {
                discount = c.equals("SAVE10") ? discount.add(tenth) : discount;
            }
            int save = coupons.indexOf("SAVE10");
            int free = coupons.indexOf("FREESHIP");
            if (customer.equals("vip") && save >= 0 && free > save) {
                discount = discount.add(tenth);                 // planted defect D2: the VIP bonus counts an earlier SAVE10 again
            }
            BigDecimal ship = freeShipping ? BigDecimal.ZERO.setScale(2) : catalog.shippingCost(option);
            BigDecimal wrap = giftWrap ? Catalog.GIFT_WRAP : BigDecimal.ZERO.setScale(2);
            return new Price(subtotal, discount, ship, wrap, option);
        }

        /** Places the order, paid by card, wallet or invoice. */
        public Order place(String payment) {
            if (!List.of("card", "wallet", "invoice").contains(payment)) {
                throw new IllegalArgumentException("unknown payment method '" + payment + "'");
            }
            if (items.isEmpty()) {
                throw new IllegalStateException("the cart is empty");
            }
            if (placed != null && !payment.equals("invoice")) {
                return placed;                                  // card and wallet payments carry an idempotency key
            }
            // planted defect D3: an invoice submitted again is not recognised and goes on to place a second order
            Order o = record(this, payment, price(underTest()));
            if (placed == null) {
                placed = o;
            }
            return o;
        }
    }
}
