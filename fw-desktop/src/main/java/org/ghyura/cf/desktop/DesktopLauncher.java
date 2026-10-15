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

import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

/**
 * Opens the Swing desktop over a session and blocks until its window is closed. On a headless JVM it refuses with
 * UNSUPPORTED_CAPABILITY before any Swing class is initialized; the commands never load this class.
 */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    /**
     * @param scenario the scenario to open, or null
     * @throws FrameworkException UNSUPPORTED_CAPABILITY on a headless JVM
     */
    public static void launch(Session session, Path scenario) {
        if (GraphicsEnvironment.isHeadless()) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.UNSUPPORTED_CAPABILITY, "the desktop needs a display and this JVM is "
                    + "headless (java.awt.headless=true or no display); every other command works without one", SourceLocation.NONE,
                    "command", "desktop"));
        }
        CountDownLatch closed = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            Desktop desktop = new Desktop(session, scenario, FileTrail.remembered(FileTrail.defaultStore(System.getenv())));
            JFrame frame = new JFrame("Combinatorics Framework");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(desktop.component());
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosed(WindowEvent e) {
                    desktop.close().whenComplete((ignored, failure) -> closed.countDown());
                }
            });
            frame.setSize(1200, 820);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
        try {
            closed.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
