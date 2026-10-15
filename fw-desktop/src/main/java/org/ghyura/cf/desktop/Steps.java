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

package org.ghyura.cf.desktop;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;

/**
 * The desktop's tabs carry their step in the order a user works through them: "3 · Scenario", and a tab inside a step
 * "2.1 · Control sheets". Each tab also carries a hint, kept on its content, that the hint layer shows over the tab.
 */
final class Steps {

    private Steps() {
    }

    /** A tab title that starts with its step. */
    static String title(String step, String name) {
        return step + " · " + name;
    }

    /** Adds a tab titled with its step and keeps its hint on its content. */
    static void add(JTabbedPane tabs, String step, String name, JComponent content, String hint) {
        tabs.addTab(title(step, name), content);
        content.putClientProperty(Hints.TAB, hint);
    }
}
