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

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.desktop.session.FileTrail;
import org.ghyura.cf.desktop.session.GuidedScenario;
import org.ghyura.cf.desktop.session.GuidedScenario.Kind;
import org.ghyura.cf.desktop.session.GuidedScenario.Role;
import org.ghyura.cf.desktop.session.GuidedScenario.Selection;
import org.ghyura.cf.desktop.session.GuidedScenario.Situation;
import org.ghyura.cf.desktop.session.GuidedScenario.Use;
import org.ghyura.cf.desktop.session.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.SwingUtilities;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Step 0 of the FlatLaf desktop: a newcomer composes a first scenario, checks it, saves it, and its test cases run. */
class WizardViewTest {

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);
        return task.get(30, TimeUnit.SECONDS);
    }

    private static String page(WizardView w) {
        return w.pages().getTitleAt(w.pages().getSelectedIndex());
    }

    @Test
    void aNewcomerComposesAFirstScenarioChecksItSavesItAndItsTestCasesRun(@TempDir Path dir) throws Exception {
        Session session = Session.resolve(null, Map.of(), List.of());
        Desktop desktop = edt(() -> new Desktop(session, null, FileTrail.inMemory(), true));
        try {
            WizardView w = desktop.wizard();
            assertEquals(List.of("0.1 · Situation", "0.2 · Factors", "0.3 · How each varies", "0.4 · Rules (sieve)", "0.5 · How many",
                    "0.6 · Bootstrap", "0.7 · Finish"), edt(() -> {
                        List<String> titles = new java.util.ArrayList<>();
                        for (int i = 0; i < w.pages().getTabCount(); i++) titles.add(w.pages().getTitleAt(i));
                        return titles;
                    }));
            assertTrue(edt(w::problems).startsWith("To fix: Add at least one factor"), edt(w::problems));

            // 0.1: start from the workflow's example: three static parameters, a sequence of actions and a sudden action
            edt(() -> {
                w.fill(Situation.WORKFLOW);
                return null;
            });
            assertEquals("0.2 · Factors", edt(() -> page(w)));
            GuidedScenario example = edt(w::design);
            assertEquals(List.of(Role.PARAMETER, Role.PARAMETER, Role.PARAMETER, Role.SEQUENCE, Role.SUDDEN),
                    example.factors().stream().map(GuidedScenario.Factor::role).toList());
            assertEquals(BigInteger.valueOf(2 * 3 * 3 * 24 * 2), example.total());
            assertEquals(Selection.PAIRS, example.selection(), "pairwise for the three static parameters");

            // 0.2: a factor of one's own, of the kind that may or may not happen
            edt(() -> {
                w.addFactorForTest();
                w.type("coupon", "SAVE10");
                w.kind(Role.SUDDEN);
                return null;
            });
            GuidedScenario.Factor coupon = edt(() -> w.design().factors().get(5));
            assertEquals("coupon", coupon.name());
            assertEquals(List.of("SAVE10"), coupon.values());
            assertEquals(Role.SUDDEN, coupon.role());
            assertTrue(edt(w::meaning).contains("coupon, a sudden action") && edt(w::meaning).contains("FW_Seq row: coupon  |  FW_Optional  |  "
                    + "FW_Combi(1)"), edt(w::meaning));

            // 0.3: the steps are a sequence in a row, kept apart from the pairwise selection
            edt(() -> {
                w.select(3);
                return null;
            });
            String steps = edt(w::meaning);
            assertTrue(steps.contains("steps, a sequence of actions") && steps.contains("FW_Seq row: steps  |  FW_Optional  |  "
                    + "FW_Concatenator=+  |  FW_Combi(3)  |  FW_Permut()") && steps.contains("P(4,3) = 4·3·2 = 24")
                    && steps.contains("a sequence in a row") && steps.contains("steps_always"), steps);
            // answered otherwise, the same factor gets the instructions of its new freedom
            edt(() -> {
                w.answer(Use.EXACTLY, 2, 0, 4, true, true, false);
                return null;
            });
            assertTrue(edt(w::meaning).contains("FW_PermutR(2)  |  FW_Combi(2)") && edt(w::meaning).contains("4^2 = 16"), edt(w::meaning));
            // two of the four steps in a row, and no session expiring, keep this first run short
            edt(() -> {
                w.answer(Use.EXACTLY, 2, 0, 4, true, false, false);
                w.select(4);
                w.removeSelected();
                return null;
            });
            assertEquals(List.of("user", "device", "browser", "steps", "coupon"), edt(() -> w.design().factors().stream()
                    .map(GuidedScenario.Factor::name).toList()));
            assertEquals(BigInteger.valueOf(12), edt(() -> w.design().factors().get(3).ways()));

            // 0.4: rules, refused with the reason when they cannot work
            String refused = edt(() -> w.rule(Kind.NEVER, "user", "guest", "user", "member"));
            assertTrue(refused.contains("never together anyway"), refused);
            String added = edt(() -> w.rule(Kind.NEVER, "user", "guest", "coupon", "SAVE10"));
            assertEquals("Added: user = guest is never together with coupon = SAVE10.", added);
            assertEquals(2, edt(() -> w.design().rules().size()), "the example's rule and this one");

            // 0.5: pairwise for the static parameters; every order and the coupon or not with each combination it keeps
            edt(() -> {
                w.choose(Selection.PAIRS);
                return null;
            });
            assertEquals(Selection.PAIRS, edt(() -> w.design().selection()));
            String counts = edt(w::counts);
            assertTrue(counts.contains("Static parameters: user 2 ways, device 3 ways, browser 3 ways.") && counts.contains("Sequences of "
                    + "actions: steps 12 orders.") && counts.contains("coupon 2 (with not happening)") && counts.contains("keeps at least "
                    + GuidedScenario.figure(BigInteger.valueOf(3 * 3 * 12 * 2)) + " test cases"), counts);

            // 0.6: the bootstrap runs once, first, for all the test cases, with the arguments typed here
            edt(() -> {
                w.bootstrapArguments("EU 0.20");
                return null;
            });
            assertEquals(List.of("EU", "0.20"), edt(() -> w.design().bootstrap().words()));

            // 0.7: the Framework's own count, then save, then step 3 with the selection
            GuidedScenario.Checked checked = edt(w::check).get(60, TimeUnit.SECONDS);
            assertEquals(edt(() -> w.design().generated()), checked.generated());
            assertTrue(checked.executable().signum() > 0 && checked.executable().compareTo(checked.generated()) < 0, checked.text());
            assertTrue(edt(w::output).startsWith("The Framework generates " + GuidedScenario.figure(checked.generated()) + " combinations"),
                    edt(w::output));
            GuidedScenario.Saved saved = edt(() -> w.save(dir.resolve("first"))).get(30, TimeUnit.SECONDS);
            assertEquals(dir.resolve("first.json").toAbsolutePath(), saved.scenario());
            assertTrue(Files.exists(dir.resolve("first.constraints.json")) && Files.exists(dir.resolve("first.properties")));
            assertTrue(Files.readString(saved.scenario()).contains("class RunMeFirstOnce"), "the bootstrap is saved with the scenario");
            assertTrue(edt(w::output).contains("Saved the scenario: " + saved.scenario()), edt(w::output));
            assertTrue(edt(w::useInStep3));
            assertEquals("3 · Scenario", edt(() -> desktop.tabs().getTitleAt(desktop.tabs().getSelectedIndex())));
            assertEquals(saved.scenario(), edt(() -> desktop.scenario().scenario()));
            assertEquals(2, desktop.runners().session().configuration().config().coverageStrength(), "the selection reached step 3");
            assertTrue(edt(w::output).contains("covering t = 2"), edt(w::output));

            // step 6 runs exactly the test cases the wizard's check counted, and every one passes until the check is written
            edt(() -> desktop.run().start()).get(30, TimeUnit.SECONDS);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
            while (desktop.run().controller().view().state() != RunState.COMPLETED && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(RunState.COMPLETED, desktop.run().controller().view().state());
            assertEquals(checked.executable().longValueExact(), desktop.run().controller().view().committed());

            // a change after saving is saved again before the scenario is handed on
            edt(() -> {
                w.choose(Selection.ALL);
                return null;
            });
            assertTrue(edt(w::useInStep3));
            assertTrue(edt(w::output).startsWith("Saved your changes again in " + saved.scenario()), edt(w::output));
            assertEquals(0, desktop.runners().session().configuration().config().coverageStrength());
        } finally {
            edt(desktop::close).get(30, TimeUnit.SECONDS);
        }
    }

    @Test
    void aDesignWithProblemsSaysWhatToFixAndIsNeitherCheckedNorSaved(@TempDir Path dir) throws Exception {
        Desktop desktop = edt(() -> new Desktop(Session.resolve(null, Map.of(), List.of()), null, FileTrail.inMemory(), true));
        try {
            WizardView w = desktop.wizard();
            edt(() -> {
                w.fill(Situation.SETTINGS);
                w.addFactorForTest();
                w.type("FW_x", "a, a");
                return null;
            });
            assertTrue(edt(w::problems).startsWith("To fix: Factor FW_x: a name that starts with FW_"), edt(w::problems));
            assertNull(edt(w::check).get(30, TimeUnit.SECONDS));
            assertTrue(edt(w::output).contains("Fix these first:") && edt(w::output).contains("has the value a twice"), edt(w::output));
            assertFalse(edt(w::useInStep3), "nothing saved, nothing handed on");
            edt(() -> {
                w.type("tier", "free, paid");
                return null;
            });
            assertTrue(edt(w::problems).startsWith("Ready"), edt(w::problems));
            assertEquals(Selection.PAIRS, edt(() -> w.design().selection()), "324 combinations: the example chose pairwise");
        } finally {
            edt(desktop::close).get(30, TimeUnit.SECONDS);
        }
    }
}
