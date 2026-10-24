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

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/** The opt-in guif entry point. The original desktop launcher and its look and feel are unchanged. */
public final class FlatDesktopLauncher {
    private FlatDesktopLauncher() { }

    /** Opens the FlatLaf GUI and waits for its window and background work to close. */
    public static void launch(Session session, Path scenario) {
        if (GraphicsEnvironment.isHeadless()) {
            throw unavailable("the FlatLaf desktop needs a display and this JVM is headless (java.awt.headless=true or no display)");
        }
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("launch waits for the window; call it outside the Swing event thread");
        }
        CompletableFuture<Void> closed = new CompletableFuture<>();
        CompletableFuture<JFrame> opened = new CompletableFuture<>();
        SwingUtilities.invokeLater(() -> {
            try {
                opened.complete(open(session, scenario, closed));
            } catch (Throwable failure) {
                opened.completeExceptionally(failure);
            }
        });
        try {
            opened.get();
            closed.get();
        } catch (InterruptedException interrupted) {
            opened.thenAccept(frame -> SwingUtilities.invokeLater(frame::dispose));
            Thread.currentThread().interrupt();
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof FrameworkException diagnostic) throw diagnostic;
            throw unavailable("the FlatLaf desktop failed: " + failure.getCause());
        }
    }

    /** The tint of every second table row, which sets odd rows apart from even ones. */
    static final String ALTERNATE_ROW = "#e4ebf5";

    /**
     * The grey of the example shown inside an empty field. FlatLaf draws it in its disabled grey (#808080, 3.9:1 on white);
     * guif's examples are instructions, so they get text contrast: 5.3:1 on a field, 4.8:1 on a disabled one, still lighter
     * than what the user types.
     */
    static final String PLACEHOLDER = "#6b6b6b";

    /**
     * Install before constructing any widgets; no optional native integration or animation work. Every table of the FlatLaf
     * desktop tints its odd rows (checkbox and number columns too), every output (read-only text and tables) shows on a
     * grayscale gradient ({@link GrayGradient}), and the examples inside empty fields read as text ({@link #PLACEHOLDER});
     * the settings belong to FlatLaf, so the original desktop's look is untouched.
     */
    static void installLookAndFeel() {
        System.setProperty("flatlaf.useNativeLibrary", "false");
        System.setProperty("flatlaf.animation", "false");
        Map<String, String> extra = new HashMap<>(Objects.requireNonNullElse(FlatLaf.getGlobalExtraDefaults(), Map.of()));
        extra.put("Table.alternateRowColor", ALTERNATE_ROW);
        extra.putAll(GrayGradient.uiClasses());
        for (String field : new String[]{"TextField", "FormattedTextField", "PasswordField"}) {
            extra.put(field + ".placeholderForeground", PLACEHOLDER);
        }
        FlatLaf.setGlobalExtraDefaults(extra);
        if (!FlatLightLaf.setup()) throw unavailable("could not initialize FlatLaf");
    }

    private static JFrame open(Session session, Path scenario, CompletableFuture<Void> closed) {
        installLookAndFeel();
        Desktop desktop = new Desktop(session, scenario, FileTrail.remembered(FileTrail.defaultStore(System.getenv())), true);
        JFrame frame = null;
        try {
            frame = new JFrame("Combinatorics Framework · FlatLaf");
            frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            frame.setContentPane(desktop.component());
            frame.addWindowListener(new WindowAdapter() {
                @Override public void windowClosing(WindowEvent event) {
                    if (desktop.allowClose()) event.getWindow().dispose();
                }
                @Override public void windowClosed(WindowEvent event) {
                    desktop.close().whenComplete((ignored, failure) -> {
                        if (failure == null) closed.complete(null);
                        else closed.completeExceptionally(failure);
                    });
                }
            });
            var screen = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
            frame.setSize(Math.min(1200, screen.width), Math.min(820, screen.height));
            frame.setLocation(screen.x + (screen.width - frame.getWidth()) / 2,
                    screen.y + (screen.height - frame.getHeight()) / 2);
            frame.setVisible(true);
            return frame;
        } catch (RuntimeException | Error failure) {
            if (frame != null) frame.dispose();
            desktop.close();
            throw failure;
        }
    }

    private static FrameworkException unavailable(String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.UNSUPPORTED_CAPABILITY, message,
                SourceLocation.NONE, "command", "guif"));
    }
}
