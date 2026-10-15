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

package org.ghyura.cf.desktop.session;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** GEN:operator-authoring-ui: editing the companion sidecar as bonds, its unsaved impact through the session, and saving it. */
class ConstraintEditingTest {

    private static BigInteger executable(Session session, Path scenario, String sidecar) {
        CandidatePreview p = session.preview(scenario, sidecar, new CandidatePreview.Request(BigInteger.valueOf(1_000_000), BigInteger.ZERO, 1,
                true, true), Cancellation.NONE);
        return p.estimates().candidates().stream().filter(s -> s.name().equals("executable")).findFirst().orElseThrow().count().value();
    }

    @Test
    void pairsAreDrawnBetweenTwoSheetsCheckedBeforeSavingAndSavedBesideTheScenario(@TempDir Path dir) throws IOException {
        Path scenario = CandidatePreviewTest.tutor(dir);
        Session session = SessionTest.session("cf.plan.autoPromoteSingleVerb=false");
        ConstraintEditing editing = ConstraintEditing.open(scenario);
        assertEquals(1, editing.bonds().size());
        var any = ConstraintEditing.gate("any", 0);
        assertEquals("forbid", editing.pair("payment", "wallet", "account", "guest", any));
        assertEquals("forbid", editing.pair("account", "guest", "payment", "wallet", any), "a pair is the same both ways");
        assertNull(editing.pair("payment", "wallet", "account", "guest", ConstraintEditing.gate("adjacent", 0)), "another gate is another bond");
        assertEquals(BigInteger.valueOf(144), executable(session, scenario, editing.json()));

        editing.setPair("payment", "card", "account", "guest", any, "forbid");
        assertTrue(editing.json().contains("\"id\": \"bond0_account_payment\"") && editing.json().contains("\"desc\": \"2 forbid account×payment bond(s)\""),
                editing.json());
        assertEquals(BigInteger.valueOf(96), executable(session, scenario, editing.json()), "each forbidden pair removes 12 rows of 4 candidates");
        editing.setPair("payment", "card", "account", "guest", any, null);
        assertEquals(BigInteger.valueOf(144), executable(session, scenario, editing.json()));

        editing.setPair("channel", "web", "payment", "card", ConstraintEditing.gate("within", 2), "require");
        Path written = editing.save(scenario);
        assertEquals(dir.resolve("tutor.constraints.json"), written);
        String saved = Files.readString(written, StandardCharsets.UTF_8);
        assertEquals(editing.json(), saved);
        assertEquals(saved, ConstraintEditing.open(scenario).json(), "the saved sidecar opens to the same bonds");
        assertTrue(saved.contains("\"within\": 2"), saved);
        session.validate(scenario);

        ConstraintEditing contradicting = ConstraintEditing.of("{\"version\": 1, \"constraints\": [{\"id\": \"f\", \"polarity\": \"forbid\", "
                + "\"pairs\": [{\"A\": \"a\", \"B\": \"b\"}]}, {\"id\": \"r\", \"polarity\": \"require\", \"pairs\": [{\"A\": \"a\", \"B\": \"b\"}]}]}",
                "inline");
        assertFalse(contradicting.contradictions().isEmpty());
        FrameworkException refused = assertThrows(FrameworkException.class, () -> contradicting.save(scenario));
        assertEquals(ErrorCode.CONSTRAINT_INVALID, refused.diagnostics().getFirst().code());
        assertEquals(saved, Files.readString(written, StandardCharsets.UTF_8), "a refused save leaves the file as it was");
        assertThrows(IllegalArgumentException.class, () -> editing.setPair("payment", "card", "payment", "wallet", any, "forbid"));
    }

    private static org.ghyura.cf.model.rule.RuleValue json(String text) {
        return org.ghyura.cf.input.ConstraintBonds.parse(text, "test");
    }

    @Test
    void rulesAreEditedAsWrittenWithNestedConditionsAndCheckedAsARunReadsThem(@TempDir Path dir) throws IOException {
        Path scenario = CandidatePreviewTest.tutor(dir);
        Session session = SessionTest.session("cf.plan.autoPromoteSingleVerb=false");
        ConstraintEditing editing = ConstraintEditing.open(scenario);
        var any = ConstraintEditing.gate("any", 0);
        editing.setPair("payment", "card", "account", "guest", any, "forbid");
        assertEquals(java.util.List.of(new ConstraintEditing.Rule("bond0_account_payment", "pairs", java.util.List.of(0, 1))), editing.rules());
        assertEquals(BigInteger.valueOf(96), executable(session, scenario, editing.json()));

        // the legacy params rows: the parameters a formula reads as sheet.param
        editing.setParams("payment", "card", "risk=2");
        editing.setParams("payment", "wallet", "risk=5");
        editing.setParams("account", "guest", "trust=1");
        editing.setParams("account", "member", "trust=3, label=vip, 10=x");
        assertEquals("10=x, trust=3, label=vip", editing.paramsText("account", "member"), "the page showed array-index keys first");
        assertEquals(java.util.List.of("value", "pos", "trust", "10", "label"), FormulaBlocks.paramsOf("account", editing.params()));

        // a formula rule, as the legacy formula dialog added it
        assertEquals("Pick ≥2 sheets", assertThrows(IllegalArgumentException.class,
                () -> editing.addWhen(java.util.List.of("payment"), "forbid", any, "payment.risk > 1")).getMessage());
        assertEquals("Unsafe formula", assertThrows(IllegalArgumentException.class,
                () -> editing.addWhen(java.util.List.of("payment", "account"), "forbid", any, "payment.risk > 1; x")).getMessage());
        int when = editing.addWhen(java.util.List.of("payment", "account"), "forbid", any, " payment.risk > account.trust ");
        assertEquals(new ConstraintEditing.Rule("when0_account_payment", "when", java.util.List.of(2)), editing.rules().get(when));
        assertTrue(editing.json().contains("\"when\": \"payment.risk > account.trust\""), editing.json());
        assertEquals(java.util.List.of(), editing.problems());
        assertEquals(BigInteger.valueOf(48), executable(session, scenario, editing.json()), "only card with member is left");

        // a nested "only when" condition: the formula applies on the web without a duplicate submit
        ConstraintEditing.Draft d = editing.draft(when);
        var nested = json("{\"all\": [{\"sheet\": \"channel\", \"eq\": \"web\"}, {\"not\": {\"sheet\": \"duplicate_submit\", \"present\": true}}]}");
        assertEquals(when, editing.apply(when, new ConstraintEditing.Draft(d.polarity(), d.gate(), nested, d.sheets(), d.when(), null)));
        assertEquals(BigInteger.valueOf(84), executable(session, scenario, editing.json()), "wallet with member goes only where the condition holds");

        // what a run would refuse, reported as it reports it, the sidecar left as it is
        String before = editing.json();
        java.util.List<String> broken = editing.problems(when, new ConstraintEditing.Draft("forbid", any, null, d.sheets(), "payment.risk >", null));
        assertTrue(broken.size() == 1 && broken.getFirst().contains("'when' \"payment.risk >\" is not a supported expression"), broken.toString());
        java.util.List<String> stranger = editing.problems(when, new ConstraintEditing.Draft("forbid", any, null, d.sheets(), "channel.value == 1", null));
        assertTrue(stranger.getFirst().contains("uses the name 'channel', which is neither one of its sheets"), stranger.toString());
        assertEquals(before, editing.json());

        // a condition on a group of pairs reaches both bonds, which stay one rule
        assertEquals(0, editing.apply(0, new ConstraintEditing.Draft("forbid", any, json("{\"sheet\": \"channel\", \"eq\": \"mobile\"}"), null, null, null)));
        assertEquals(java.util.List.of(0, 1), editing.rules().getFirst().bonds());
        assertTrue(editing.bonds().get(0).condition() != null && editing.bonds().get(1).condition() != null);
        assertEquals(BigInteger.valueOf(108), executable(session, scenario, editing.json()));

        // an assert rule, as the legacy relation dialog added it
        assertEquals("Add at least one condition", assertThrows(IllegalArgumentException.class, () -> editing.addAssert("require", null)).getMessage());
        int asserted = editing.addAssert("require", json("{\"any\": [{\"sheet\": \"channel\", \"eq\": \"web\"}, {\"sheet\": \"payment\", \"eq\": \"card\"}]}"));
        assertEquals(new ConstraintEditing.Rule("assert0_channel_payment", "assert", java.util.List.of(3)), editing.rules().get(asserted));
        assertEquals(BigInteger.valueOf(84), executable(session, scenario, editing.json()), "mobile with wallet goes");

        // saved and opened again, the rules are the same; a removed rule takes its bonds with it
        Path written = editing.save(scenario);
        ConstraintEditing reopened = ConstraintEditing.open(scenario);
        assertEquals(org.ghyura.cf.input.ConstraintBonds.pythonJson(editing.sidecar(), true, false),
                org.ghyura.cf.input.ConstraintBonds.pythonJson(reopened.sidecar(), true, false),
                "the same sidecar (a drawn pair keeps the order it was drawn in until it is read back, as in the legacy editor)");
        assertEquals(editing.rules(), reopened.rules());
        assertTrue(Files.readString(written, StandardCharsets.UTF_8).contains("\"params\": {\n    \"payment\""), editing.json());
        reopened.removeRule(0);
        assertEquals(java.util.List.of("when", "assert"), reopened.rules().stream().map(ConstraintEditing.Rule::kind).toList());
        assertEquals(2, reopened.bonds().size());
    }
}
