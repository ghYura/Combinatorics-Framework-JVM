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

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.ghyura.cf.showcase.cucumber.CaseBoard;
import org.ghyura.cf.showcase.cucumber.HarnessProblem;
import org.ghyura.cf.showcase.cucumber.shop.Catalog;
import org.ghyura.cf.showcase.cucumber.shop.ReferencePricing;
import org.openqa.selenium.By;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.HasCapabilities;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The glue of the generated web scenarios (02-selenium-webshop): every step drives the dummy web shop through a real,
 * headless Firefox. The Givens set the few static parameters; the Whens are the customer's actions in the scenario's order,
 * with sudden events woven between them (a reload, going back and repeating the last action); the Thens read the
 * shop's own order list and compare it with the business rules. The browser and the scenario's world arrive through the
 * constructor: PicoContainer auto-wires them.
 */
public class WebSteps {

    private final Browser browser;
    private final WebWorld world;

    public WebSteps(Browser browser, WebWorld world) {
        this.browser = browser;
        this.world = world;
    }

    // ---- Background ---------------------------------------------------------------------------------------------------

    @Given("the web shop runs at {string}")
    public void theWebShopRunsAt(String url) {
        world.baseUrl = url.replaceAll("/+$", "");
    }

    @Given("the browser is a headless Firefox resolved by Selenium Manager")
    public void theBrowserIsAHeadlessFirefox() {
        Capabilities c = ((HasCapabilities) browser.driver()).getCapabilities();
        CaseBoard.attribute("browser", c.getBrowserName() + "-" + c.getBrowserVersion());
    }

    @Given("the customer puts a {word} in the cart")
    public void theCustomerPutsInTheCart(String item) {
        browser.open(world.baseUrl + "/");
        browser.clickAndWait(By.id("add-" + item));
        world.items.add(item);
    }

    // ---- static parameters --------------------------------------------------------------------------------------------

    /** "hides" outsmarts the shop's defeat device (W7): before paying, the page is told no automation is present. */
    @Given("the browser {word} that it is automated")
    public void theBrowserHidesOrShowsAutomation(String how) {
        world.stealth = switch (how) {
            case "hides" -> true;
            case "shows" -> false;
            default -> throw new HarnessProblem("a browser hides or shows its automation, not '" + how + "'");
        };
    }

    @Given("the browser window is {word}")
    public void theBrowserWindowIs(String kind) {
        browser.window(kind);
    }

    @Given("the shop speaks {word}")
    public void theShopSpeaks(String language) {
        world.language = language;
        browser.open(world.baseUrl + "/?lang=" + language);
    }

    @Given("the customer browses as a {word}")
    public void theCustomerBrowsesAs(String account) {
        world.account = account;
        if (account.equals("member")) {
            browser.open(world.baseUrl + "/login");
            browser.driver().findElement(By.id("username")).sendKeys("member");
            browser.driver().findElement(By.id("password")).sendKeys("secret");
            browser.clickAndWait(By.id("sign-in"));
            if (!"true".equals(browser.driver().findElement(By.id("who")).getDomAttribute("data-member"))) {
                throw new AssertionError("signing in as a member did not succeed");
            }
        }
    }

    /** The coupon exactly as the customer will type it, spaces and case included. */
    @Given("the customer has the coupon {string}")
    public void theCustomerHasTheCoupon(String coupon) {
        world.coupon = coupon.equals("none") ? "" : coupon;
    }

    @Given("the shipping option is {word}")
    public void theShippingOptionIs(String option) {
        world.shipping = option;
    }

    // ---- the customer's actions, in the scenario's order --------------------------------------------------------------

    @When("the customer applies the coupon")
    public void theCustomerAppliesTheCoupon() {
        act(this::applyCoupon);
    }

    @When("the customer chooses the shipping")
    public void theCustomerChoosesTheShipping() {
        act(this::chooseShipping);
    }

    @When("the customer asks for gift wrap")
    public void theCustomerAsksForGiftWrap() {
        act(this::askForGiftWrap);
    }

    @When("the customer pays")
    public void theCustomerPays() {
        onTheCart();
        stealthIfAsked();
        browser.clickAndWait(By.id("pay"));
        if (!browser.page().equals("confirmation")) {
            throw new AssertionError("paying led to the " + browser.page() + " page, not to a confirmation");
        }
        CaseBoard.metric("total_eur", new BigDecimal(browser.driver().findElement(By.id("order-total")).getDomAttribute("data-amount")));
        world.lastAction = this::payAgainIfPossible;
    }

    /** Runs an action and remembers it, so that a sudden "repeat the last action" can do it once more. */
    private void act(Runnable action) {
        action.run();
        world.lastAction = action;
    }

    private void applyCoupon() {
        if (world.coupon.isEmpty()) {
            return;
        }
        onTheCart();
        WebElement field = browser.driver().findElement(By.id("coupon"));
        if (!field.isDisplayed()) {                             // the narrow layout folds the coupon form away
            browser.driver().findElement(By.id("coupon-summary")).click();
            browser.waiting().until(ExpectedConditions.visibilityOf(field));
        }
        field.clear();
        field.sendKeys(world.coupon);
        browser.clickAndWait(By.id("apply-coupon"));
        world.couponApplied = true;
    }

    private void chooseShipping() {
        onTheCart();
        browser.driver().findElement(By.cssSelector("#shipping option[data-code='" + world.shipping + "']")).click();
        browser.clickAndWait(By.id("update-shipping"));
    }

    private void askForGiftWrap() {
        onTheCart();
        WebElement box = browser.driver().findElement(By.id("giftwrap"));
        if (!box.isDisplayed()) {
            throw new AssertionError("the gift wrap option is not visible (" + browser.window() + " window, " + world.language + ")");
        }
        if (!box.isSelected()) {
            box.click();
        }
        browser.clickAndWait(By.id("update-giftwrap"));
        world.giftWrap = true;
    }

    private void payAgainIfPossible() {
        List<WebElement> pay = browser.driver().findElements(By.id("pay"));
        if (!pay.isEmpty() && pay.getFirst().isEnabled()) {
            stealthIfAsked();
            browser.clickAndWait(By.id("pay"));
        }
    }

    private void onTheCart() {
        if (!browser.page().equals("cart")) {
            browser.open(world.baseUrl + "/cart");
        }
    }

    private void stealthIfAsked() {
        if (world.stealth && !browser.hideAutomation()) {
            throw new HarnessProblem("the browser could not hide navigator.webdriver from the page");
        }
    }

    // ---- sudden events ------------------------------------------------------------------------------------------------

    @When("the customer reloads the page")
    public void theCustomerReloadsThePage() {
        browser.driver().navigate().refresh();
        browser.ready();
    }

    /** Back one page, then the last action once more, as on that page: a customer who thinks it did not work. */
    @When("the customer goes back and repeats the last action")
    public void theCustomerGoesBackAndRepeatsTheLastAction() {
        if (world.lastAction == null) {
            throw new HarnessProblem("'goes back and repeats the last action' struck before any action");
        }
        browser.driver().navigate().back();
        browser.ready();
        world.lastAction.run();
    }

    // ---- checks -------------------------------------------------------------------------------------------------------

    private List<WebElement> orders() {
        browser.open(world.baseUrl + "/orders");
        List<WebElement> rows = new ArrayList<>(browser.driver().findElements(By.cssSelector("#orders tr.order")));
        CaseBoard.metric("orders", rows.size());
        return rows;
    }

    @Then("exactly one order exists")
    public void exactlyOneOrderExists() {
        List<WebElement> rows = orders();
        if (!rows.isEmpty()) {
            CaseBoard.attribute("automation_reported", rows.getFirst().getDomAttribute("data-automated"));
        }
        if (rows.size() != 1) {
            throw new AssertionError("expected exactly one order, the shop lists " + rows.size());
        }
    }

    @Then("the order ships with the chosen shipping")
    public void theOrderShipsWithTheChosenShipping() {
        String shipped = orders().getFirst().getDomAttribute("data-shipping");
        if (!world.shipping.equals(shipped)) {
            throw new AssertionError("the customer chose " + world.shipping + ", the order ships " + shipped);
        }
    }

    @Then("the total matches the reference price")
    public void theTotalMatchesTheReferencePrice() {
        Catalog prices = Catalog.standard();
        List<BigDecimal> itemPrices = world.items.stream().map(prices::price).toList();
        List<String> coupons = world.couponApplied ? List.of(world.coupon) : List.of();   // the rules trim, upper-case, count once
        BigDecimal expected = ReferencePricing.expectedTotal(itemPrices, coupons, prices.shippingCost(world.shipping), world.giftWrap);
        BigDecimal total = new BigDecimal(orders().getFirst().getDomAttribute("data-total"));
        CaseBoard.metric("expected_eur", expected);
        if (expected.compareTo(total) != 0) {
            throw new AssertionError("the order totals " + total + ", the business rules say " + expected);
        }
    }
}
