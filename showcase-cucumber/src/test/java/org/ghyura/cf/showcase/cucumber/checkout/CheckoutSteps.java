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

import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.ghyura.cf.showcase.cucumber.CaseBoard;
import org.ghyura.cf.showcase.cucumber.HarnessProblem;
import org.ghyura.cf.showcase.cucumber.shop.Order;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The glue of the generated checkout scenarios (01-gherkin-checkout). The Givens set the few static parameters; the
 * Whens are the customer's actions, in whatever order the scenario gives, and the sudden events woven between them; the
 * Thens compare the engine with what the customer meant and with the payment provider's ledger. A failing Then throws an
 * {@link AssertionError}; the generated code turns it into the verdict that Then's sheet names.
 */
public class CheckoutSteps {

    private final CheckoutWorld world;

    /** PicoContainer calls this with the scenario's world: constructor auto-wiring, no wiring code. */
    public CheckoutSteps(CheckoutWorld world) {
        this.world = world;
    }

    // ---- Background and static parameters ---------------------------------------------------------------------------

    @Given("the bookshop sells")
    public void theBookshopSells(DataTable prices) {
        for (Map<String, String> row : prices.asMaps()) {
            world.catalog.price(row.get("item"), new BigDecimal(row.get("price")));
        }
    }

    @Given("the shipping options cost")
    public void theShippingOptionsCost(DataTable prices) {
        for (Map<String, String> row : prices.asMaps()) {
            world.catalog.shippingCost(row.get("option"), new BigDecimal(row.get("price")));
        }
    }

    @Given("the checkout runs on the {word} thread")
    public void theCheckoutRunsOn(String channel) {
        if (!channel.equals("test") && !channel.equals("terminal")) {
            throw new HarnessProblem("unknown channel '" + channel + "'");
        }
        world.channel = channel;
    }

    @Given("the customer is a {word}")
    public void theCustomerIs(String customer) {
        world.customer = customer;
    }

    @Given("the payment method is {word}")
    public void thePaymentMethodIs(String payment) {
        world.payment = payment;
    }

    @Given("the cart starts with a bookmark")
    public void theCartStartsWithABookmark() {
        world.startsWithBookmark = true;
    }

    @Given("the cart starts with nothing")
    public void theCartStartsWithNothing() {
        world.startsWithBookmark = false;
    }

    // ---- the customer's actions, in the scenario's order --------------------------------------------------------------

    @When("the customer adds a {word}")
    public void theCustomerAdds(String item) {
        world.checkout();
        world.pricesWhenAdded.add(world.catalog.price(item));
        world.send(() -> world.checkout().add(item));
    }

    @When("the customer applies {word}")
    public void theCustomerApplies(String coupon) {
        world.checkout();
        world.couponsMeant.add(coupon);
        world.send(() -> world.checkout().applyCoupon(coupon));
    }

    @When("the customer chooses {word} shipping")
    public void theCustomerChoosesShipping(String option) {
        world.checkout();
        world.shippingMeant = option;
        world.send(() -> world.checkout().chooseShipping(option));
    }

    @When("the customer places the order")
    public void theCustomerPlacesTheOrder() {
        world.send(() -> {
            Order placed = world.place();
            if (world.order == null) {
                world.order = placed;                           // a retried order request leaves the first order the order
            }
        });
        CaseBoard.metric("total_eur", world.order.total());
        CaseBoard.metric("orders", world.engine.orders().size());
    }

    // ---- sudden events ------------------------------------------------------------------------------------------------

    @When("the price of {word} rises by {bigdecimal} during checkout")
    public void thePriceRises(String item, BigDecimal by) {
        world.checkout();
        world.catalog.raise(item, by);
        CaseBoard.metric("price_rise_eur", by);
    }

    /** The network delivers the last request again; the customer did nothing, so what they meant does not change. */
    @When("the previous request is delivered twice")
    public void thePreviousRequestIsDeliveredTwice() {
        if (world.lastRequest == null) {
            throw new HarnessProblem("a duplicate delivery struck before any request was sent");
        }
        world.lastRequest.run();
        CaseBoard.metric("orders", world.engine.orders().size());
    }

    // ---- checks -------------------------------------------------------------------------------------------------------

    @Then("exactly one order is placed")
    public void exactlyOneOrderIsPlaced() {
        fail(world.oneOrderProblem());
    }

    @Then("the customer is charged exactly the order total")
    public void theCustomerIsChargedExactlyTheOrderTotal() {
        fail(world.chargeProblem());
    }

    @Then("free shipping is honoured")
    public void freeShippingIsHonoured() {
        fail(world.freeShippingProblem());
    }

    @Then("the total matches the reference price")
    public void theTotalMatchesTheReferencePrice() {
        fail(world.totalProblem());
    }

    private static void fail(String problem) {
        if (problem != null) {
            throw new AssertionError(problem);
        }
    }
}
