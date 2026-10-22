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

package org.ghyura.cf.showcase.cucumber.web;

import org.ghyura.cf.showcase.cucumber.webshop.Browser;

import java.io.IOException;

/**
 * What the Selenium scenario's FW_RunMeFirstOnce does once per run, before any test case: start the dummy web shop on a
 * free loopback port and start one headless browser against it, so that Selenium Manager has resolved the driver before
 * parallel test cases ask for it. The shop lives until the Framework's process ends.
 */
public final class Bootstrap {

    private static DummyWebShop shop;

    private Bootstrap() {
    }

    /** @return the shop's base URL, which FW_ARGS hands to every test case */
    public static synchronized String startShopAndWarmUp() throws IOException {
        if (shop == null) {
            shop = DummyWebShop.start(0);
        }
        Browser.warmUp(shop.baseUrl());
        return shop.baseUrl();
    }
}
