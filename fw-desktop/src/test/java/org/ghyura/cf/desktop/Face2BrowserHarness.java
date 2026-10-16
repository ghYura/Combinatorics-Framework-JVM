// SPDX-License-Identifier: LicenseRef-BUSL-1.1
// (c) Yurii Baranov. See LICENSE and NOTICE.md for the binding terms.
package org.ghyura.cf.desktop;

import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.CandidatePreview;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.spi.Cancellation;

import javax.swing.SwingUtilities;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Browser acceptance fixture using the actual workbench connection, save, undo and Session preview. */
public final class Face2BrowserHarness {
    private Face2BrowserHarness() { }

    public static void main(String[] args) throws Exception {
        Path dir = Path.of(args[0]); Files.createDirectories(dir);
        Path file = Files.writeString(dir.resolve("browser.json"), """
                {"sheets":{"FW_Seq":[["A","FW_Combi(1)"],["B","FW_Combi(1)"],["C","FW_Combi(1)"]],
                 "A":[["a"],["b"]],"B":[["1"],["2"]],"C":[["x"],["y"]]}}
                """);
        Session session = Session.resolve(null, Map.of(), List.of(new ConfigResolver.Override("cf.plan.autoPromoteSingleVerb", "false", "browser test")));
        FileTrail trail = FileTrail.inMemory();
        SieveWorkbench view = edt(() -> new SieveWorkbench(() -> session, () -> file, new Navigator(trail, () -> file)));
        try {
            // Also allows exporting the real packaged HTML without starting any sockets.
            if (args.length > 1 && args[1].equals("--html")) {
                var opened = session.openSieve(file);
                var html = new org.ghyura.cf.desktop.session.Face2Document(opened.document()).html(
                        Map.of("A", List.of("a", "b"), "B", List.of("1", "2"), "C", List.of("x", "y")), java.util.Set.of(), "./");
                Path page = Files.writeString(dir.resolve("face2.html"), html);
                System.out.println(page); return;
            }
            System.out.println("URL " + edt(view::openFace2).get(30, TimeUnit.SECONDS)); System.out.flush();
            try (Scanner input = new Scanner(System.in)) {
                while (input.hasNextLine()) {
                    String command = input.nextLine();
                    if (command.equals("exit")) break;
                    if (command.equals("edit")) {
                        edt(() -> { view.pair(new SieveCanvas.Value("A", "b"), new SieveCanvas.Value("B", "2")); return null; });
                    } else if (command.equals("undo")) {
                        edt(() -> { view.undo(); return null; });
                    }
                    var preview = session.preview(file, new CandidatePreview.Request(BigInteger.valueOf(100), BigInteger.ZERO, 20, true, true), Cancellation.NONE);
                    var kept = preview.estimates().candidates().stream().filter(s -> s.name().equals("executable")).findFirst().orElseThrow().count().value();
                    System.out.println("STATE {\"savedRules\":" + session.openSieve(file).document().rules().size()
                            + ",\"workbenchRules\":" + edt(() -> view.document().rules().size()) + ",\"kept\":" + kept
                            + ",\"dirty\":" + edt(view::dirty) + "}"); System.out.flush();
                }
            }
        } finally { edt(view::close).get(30, TimeUnit.SECONDS); trail.close(); }
    }

    private static <T> T edt(Callable<T> action) throws Exception {
        FutureTask<T> task = new FutureTask<>(action); SwingUtilities.invokeAndWait(task); return task.get(30, TimeUnit.SECONDS);
    }
}
