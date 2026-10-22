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

import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.picocontainer.Disposable;

import java.time.Duration;

/**
 * One scenario's browser. Nothing configures a driver path: {@code new FirefoxDriver(options)} asks Selenium Manager, which
 * finds geckodriver in its cache or downloads it (and a browser, were none installed): the auto-loader. PicoContainer
 * creates one {@code Browser} per scenario and auto-wires it into every step and hook class that takes one, and disposes
 * of it at the scenario's end; {@link WebHooks} quits it explicitly as well. The browser is headless, so nothing opens on
 * a display.
 */
public final class Browser implements Disposable {

    private static final Duration WAIT = Duration.ofSeconds(20);

    private WebDriver driver;
    private String window = "desktop";

    /** The driver, started on first use. */
    public WebDriver driver() {
        if (driver == null) {
            driver = start();
            size();
        }
        return driver;
    }

    static WebDriver start() {
        FirefoxOptions options = new FirefoxOptions();
        options.addArguments("-headless");
        options.addPreference("browser.sessionstore.resume_from_crash", false);
        options.addPreference("datareporting.policy.dataSubmissionEnabled", false);
        return new FirefoxDriver(options);
    }

    /** A desktop (1280×900) or a mobile (412×915) window; the shop's CSS reacts to the width. */
    public void window(String kind) {
        if (!kind.equals("desktop") && !kind.equals("mobile")) {
            throw new IllegalArgumentException("unknown window '" + kind + "'");
        }
        window = kind;
        if (driver != null) {
            size();
        }
    }

    public String window() {
        return window;
    }

    private void size() {
        driver.manage().window().setSize(window.equals("mobile") ? new Dimension(412, 915) : new Dimension(1280, 900));
    }

    public void open(String url) {
        driver().get(url);
        ready();
    }

    /** Clicks an element that loads another page, and waits until the new page has replaced the current one. */
    public void clickAndWait(By by) {
        WebElement body = driver().findElement(By.tagName("body"));
        driver().findElement(by).click();
        new WebDriverWait(driver(), WAIT).until(ExpectedConditions.stalenessOf(body));
        ready();
    }

    /** The current page's name ({@code data-page} of its body), or "" when it has none. */
    public String page() {
        String name = driver().findElement(By.tagName("body")).getDomAttribute("data-page");
        return name == null ? "" : name;
    }

    public void ready() {
        new WebDriverWait(driver(), WAIT).until(d -> "complete".equals(((JavascriptExecutor) d).executeScript("return document.readyState"))
                && !d.findElements(By.cssSelector("body[data-page]")).isEmpty());
    }

    /**
     * Hides from the current page that the browser is automated: {@code navigator.webdriver} reads false from now on, for
     * this page. A system that behaves differently for automated browsers (a defeat device) can then no longer tell.
     *
     * @return whether the page now believes no automation is present
     */
    public boolean hideAutomation() {
        Object hidden = ((JavascriptExecutor) driver()).executeScript("""
                try { Object.defineProperty(navigator, 'webdriver', { get: () => false, configurable: true }); } catch (e) {}
                if (navigator.webdriver) {
                  try { Object.defineProperty(Navigator.prototype, 'webdriver', { get: () => false, configurable: true }); } catch (e) {}
                }
                return navigator.webdriver === false;""");
        return Boolean.TRUE.equals(hidden);
    }

    public WebDriverWait waiting() {
        return new WebDriverWait(driver(), WAIT);
    }

    public void quit() {
        if (driver != null) {
            try {
                driver.quit();
            } finally {
                driver = null;
            }
        }
    }

    @Override
    public void dispose() {
        quit();
    }

    /** Starts a browser once and loads the shop: resolves the driver before the test cases need it. */
    public static void warmUp(String url) {
        WebDriver d = start();
        try {
            d.get(url + "/");
        } finally {
            d.quit();
        }
    }
}
