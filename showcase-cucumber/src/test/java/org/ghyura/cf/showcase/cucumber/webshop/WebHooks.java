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

import io.cucumber.java.After;
import org.ghyura.cf.showcase.cucumber.CaseBoard;

/** Closes the scenario's browser; the same {@link Browser} instance the steps used, auto-wired by PicoContainer. */
public class WebHooks {

    private final Browser browser;
    private final WebWorld world;

    public WebHooks(Browser browser, WebWorld world) {
        this.browser = browser;
        this.world = world;
    }

    @After
    public void closeTheBrowser() {
        CaseBoard.metric("browser_ms", (System.nanoTime() - world.started) / 1_000_000);
        browser.quit();
    }
}
