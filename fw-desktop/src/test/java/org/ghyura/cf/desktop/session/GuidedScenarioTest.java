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

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.desktop.session.GuidedScenario.Factor;
import org.ghyura.cf.desktop.session.GuidedScenario.Kind;
import org.ghyura.cf.desktop.session.GuidedScenario.Role;
import org.ghyura.cf.desktop.session.GuidedScenario.Rule;
import org.ghyura.cf.desktop.session.GuidedScenario.Selection;
import org.ghyura.cf.desktop.session.GuidedScenario.Use;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.spi.Cancellation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The desktop wizard's design (step 0): each answer counts what it means, and its rules and selection reach the plan. */
class GuidedScenarioTest {

    private static final List<String> FOUR = List.of("a", "b", "c", "d");
    /** The line a factor f adds to a test case's code, whatever its kind: it sets, does or makes happen its values. */
    private static final Pattern PICK = Pattern.compile("(?:set|act|happen)\\(\"f\", \"([^\"]*)\"\\);");

    /** Every answer the wizard offers, on four values, with the number of ways it means. */
    private static Map<Factor, Long> answers() {
        Factor one = Factor.of("f", FOUR);
        Map<Factor, Long> ways = new LinkedHashMap<>();
        ways.put(one, 4L);
        ways.put(one.using(Use.EXACTLY, 1, 0, 4).withOrder(true).withRepeats(true), 4L);
        ways.put(one.using(Use.EXACTLY, 2, 0, 4), 6L);
        ways.put(one.using(Use.EXACTLY, 4, 0, 4), 1L);
        ways.put(one.using(Use.EXACTLY, 2, 0, 4).withOrder(true), 12L);
        ways.put(one.using(Use.EXACTLY, 3, 0, 4).withOrder(true), 24L);
        ways.put(one.using(Use.EXACTLY, 4, 0, 4).withOrder(true), 24L);
        ways.put(one.using(Use.EXACTLY, 2, 0, 4).withRepeats(true), 10L);
        ways.put(one.using(Use.EXACTLY, 3, 0, 4).withRepeats(true), 20L);
        ways.put(one.using(Use.EXACTLY, 2, 0, 4).withOrder(true).withRepeats(true), 16L);
        ways.put(one.using(Use.EXACTLY, 5, 0, 4).withOrder(true).withRepeats(true), 1024L);
        ways.put(one.using(Use.RANGE, 2, 0, 4), 16L);
        ways.put(one.using(Use.RANGE, 2, 1, 4), 15L);
        ways.put(one.using(Use.RANGE, 2, 1, 2), 10L);
        // no order of no values is made: an ordered range starts at one value
        ways.put(one.using(Use.RANGE, 2, 0, 2).withOrder(true), 16L);
        ways.put(one.using(Use.RANGE, 2, 2, 3).withOrder(true), 36L);
        ways.put(one.withOptional(true), 5L);
        ways.put(one.using(Use.EXACTLY, 2, 0, 4).withOrder(true).withOptional(true), 13L);
        ways.put(one.using(Use.EXACTLY, 2, 0, 4).withRepeats(true).withOptional(true), 11L);
        ways.put(one.using(Use.RANGE, 2, 0, 2).withOptional(true), 12L);
        return ways;
    }

    private static BigInteger stage(CandidatePreview preview, String name) {
        return preview.estimates().candidates().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow().count().value();
    }

    /**
     * The plan repeats a lone bracketed verb of a row, or not (cf.plan.autoPromoteSingleVerb): either way the Framework
     * generates exactly the ways each answer means, the very ways the wizard shows as examples, and the labels it counts.
     */
    @ParameterizedTest(name = "auto-promotion {0}")
    @ValueSource(booleans = {true, false})
    void everyAnswerWritesInstructionsThatCountWhatItMeans(boolean promote, @TempDir Path dir) {
        Session session = SessionTest.session("cf.plan.autoPromoteSingleVerb=" + promote, "cf.keys.encoding=SIGNED_BYTE");
        int i = 0;
        for (Map.Entry<Factor, Long> answer : answers().entrySet()) {
            Factor f = answer.getKey();
            String what = f.instructions() + ", " + f.shape();
            assertEquals(List.of(), f.problems(), what);
            assertEquals(BigInteger.valueOf(answer.getValue()), f.rows(), what);
            assertTrue(f.formula().endsWith(GuidedScenario.figure(f.rows())), f.formula());
            GuidedScenario design = GuidedScenario.empty().adding(f).adding(Factor.of("g", List.of("x")));
            Path file = dir.resolve("answer" + i++ + ".json");
            design.save(file);
            CandidatePreview all = session.preview(file, new CandidatePreview.Request(BigInteger.valueOf(1_000_000), BigInteger.ZERO, 500,
                    false, true), Cancellation.NONE);
            assertEquals(design.generated(), stage(all, "generated"), what);
            assertEquals(design.total(), stage(all, "executable"), what);
            assertEquals(design.labels(), session.validate(file).highestKey(), what);
            if (all.generated().compareTo(BigInteger.valueOf(500)) <= 0) {
                List<String> picked = new ArrayList<>();
                for (CandidatePreview.Row row : all.page().rows()) {
                    Matcher m = PICK.matcher(row.source());
                    picked.add(m.find() ? m.group(1).isEmpty() ? "none" : m.group(1) : "(left out)");
                }
                assertEquals(new TreeSet<>(f.examples(10_000)), new TreeSet<>(picked), "the examples are the test cases' own: " + what);
                assertEquals(picked.size(), new HashSet<>(picked).size(), "no way comes twice: " + what);
            }
        }
    }

    /** The values of one way of a factor, as a test case shows it ("a+b"); empty for none or left out. */
    private static Set<String> has(String way) {
        return way.equals("none") || way.equals("(left out)") ? Set.of() : Set.of(way.split("\\+"));
    }

    /** Rules remove exactly the combinations they name, counted by hand; a pairwise selection keeps fewer that still cover. */
    @Test
    void rulesRemoveWhatTheySayAndTheSelectionTravelsAsSettings(@TempDir Path dir) throws Exception {
        GuidedScenario design = GuidedScenario.empty().named("shop")
                .adding(Factor.of("browser", List.of("chrome", "firefox", "safari")))
                .adding(Factor.of("os", List.of("linux", "mac os", "windows")))
                .adding(Factor.of("extras", List.of("wrap", "express", "insurance")).using(Use.RANGE, 2, 0, 3))
                .adding(Factor.of("gift", List.of("card", "box")).withOptional(true))
                .adding(new Rule(Kind.NEVER, "browser", "safari", "os", "linux"))
                .adding(new Rule(Kind.NEEDS, "browser", "safari", "os", "mac os"))
                .adding(new Rule(Kind.NEVER, "extras", "wrap", "extras", "express"))
                .adding(new Rule(Kind.NEEDS, "gift", "box", "extras", "wrap"));
        assertEquals(List.of(), design.problems());
        long expected = 0;
        for (String b : design.factors().get(0).examples(100)) {
            for (String o : design.factors().get(1).examples(100)) {
                for (String x : design.factors().get(2).examples(100)) {
                    for (String g : design.factors().get(3).examples(100)) {
                        boolean removed = b.equals("safari") && o.equals("linux") || b.equals("safari") && !o.equals("mac os")
                                || has(x).contains("wrap") && has(x).contains("express") || g.equals("box") && !has(x).contains("wrap");
                        if (!removed) expected++;
                    }
                }
            }
        }
        Session session = SessionTest.session();
        GuidedScenario.Checked all = design.check(session, Cancellation.NONE);
        assertEquals(BigInteger.valueOf(3 * 3 * 8 * 3), design.total());
        assertEquals(design.total(), all.generated());
        assertEquals(BigInteger.valueOf(expected), all.executable(), all.text());
        assertEquals(4, all.rules().size(), all.text());
        assertNull(all.selected(), "every combination: no selection");
        assertTrue(all.first().contains("public class Shop") && all.first().contains("set(\"browser\", \""), all.first());

        GuidedScenario pairwise = design.selecting(Selection.PAIRS, 0, true);
        GuidedScenario.Checked pairs = pairwise.check(session, Cancellation.NONE);
        assertTrue(pairs.executable().signum() > 0 && pairs.executable().compareTo(all.executable()) < 0, pairs.text());
        assertNotNull(pairs.selected(), pairs.text());
        assertTrue(pairs.selected().contains("t=2"), pairs.selected());
        assertTrue(pairs.text().contains("selection: "), pairs.text());

        GuidedScenario.Saved saved = pairwise.save(dir.resolve("shop.json"));
        assertEquals(dir.resolve("shop.constraints.json"), saved.constraints());
        Object rules = Json.parse(Files.readString(saved.constraints()), "rules");
        assertEquals(4, ((List<?>) ((Map<?, ?>) rules).get("constraints")).size());
        String properties = Files.readString(saved.properties());
        assertTrue(properties.contains("cf.coverage.strength=2\n") && properties.contains("cf.coverage.method=set-cover\n")
                && properties.contains("cf.keys.encoding=SIGNED_BYTE\n") && properties.contains("cf.run.id=shop\n"), properties);
        assertEquals(Map.of("cf.coverage.strength", "2", "cf.coverage.budget", "0", "cf.coverage.method", "set-cover"), pairwise.settings());

        // a later save without rules empties the companion, so the old rules stop applying
        GuidedScenario.Saved again = pairwise.withRules(List.of()).save(saved.scenario());
        assertEquals(saved.constraints(), again.constraints());
        assertEquals(List.of(), ((Map<?, ?>) Json.parse(Files.readString(again.constraints()), "rules")).get("constraints"));
        assertNull(GuidedScenario.empty().adding(Factor.of("f", FOUR)).save(dir.resolve("plain.json")).constraints());
    }

    /**
     * A saved scenario runs as it is: the bootstrap runs once, and first, for the whole run, however many workers run the test
     * cases, and hands every one of them its FW_ARGS; every test case reports its values, and the check names its verdict code.
     */
    @Test
    void aSavedScenarioRunsItsBootstrapOnceFirstAndItsCheckNamesItsVerdict(@TempDir Path dir) throws Exception {
        GuidedScenario design = GuidedScenario.empty().named("first-run")
                .adding(Factor.of("size", List.of("small", "extra large")))
                .adding(Factor.of("steps", List.of("open", "pay")).using(Use.EXACTLY, 2, 0, 2).withOrder(true))
                .withBootstrap(new GuidedScenario.Bootstrap(true, "  EU   0.20 "));
        assertEquals(List.of("EU", "0.20"), design.bootstrap().words());
        Path file = design.save(dir.resolve("first-run.json")).scenario();
        ScenarioDocument document = ScenarioDocument.read(file);
        assertEquals(List.of("FW_Seq", "FW_SheetNames", "FW_CUSTOM_VAR", "FW_RunMeFirstOnce", "FW_Arguments", "HEAD", "size", "steps", "check",
                "TAIL"), document.sheetNames());
        assertEquals("0.20", document.cell(4, 1, 0), "one argument a row, as the showcase writes them");
        Path log = dir.resolve("bootstrap.log");
        // the user writes the bootstrap: it notes each time it runs, and tells the test cases it is ready
        int runme = document.sheetNames().indexOf("FW_RunMeFirstOnce");
        document.setCell(runme, 0, 0, document.cell(runme, 0, 0).replace("FW_ARGS = String.join(\" \", args).trim();",
                "try { java.nio.file.Files.writeString(java.nio.file.Path.of(\"" + log + "\"), \"ran\\n\", "
                + "java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); } catch (java.io.IOException e) { "
                + "throw new IllegalStateException(e); } FW_ARGS = String.join(\" \", args).trim() + \" ready\";"));
        int check = document.sheetNames().indexOf("check");
        // and the check: a large size fails, and so would a test case that ran before the bootstrap was ready
        document.setCell(check, 0, 0, document.cell(check, 0, 0).replace("boolean misbehaved = false;",
                "boolean misbehaved = chosen.get(\"size\").startsWith(\"extra\") || shared.length != 3 || !\"EU\".equals(shared[0]) "
                + "|| !\"ready\".equals(shared[2]);"));
        document.save(file);
        assertTrue(session().validate(file).findings().stream().noneMatch(d -> d.code() == org.ghyura.cf.model.ErrorCode.SPEC_RUNMEFIRSTONCE_SHAPE),
                "the bootstrap has the shape the reader expects");
        Session session = SessionTest.session("cf.execution.workers=4");
        try (Session.PreparedRun run = session.prepareRun(file)) {
            var summary = run.handle().await();
            assertEquals(RunState.COMPLETED, summary.state());
            assertTrue(RunReport.toText(summary).contains(design.checkCode() + " " + GuidedScenario.CHECK_FAILED + ": 2"),
                    "FW_CUSTOM_VAR names the check's code: " + RunReport.toText(summary));
            List<Observation> finals = run.handle().finals();
            assertEquals(4, finals.size());
            Set<String> seen = new TreeSet<>();
            for (Observation o : finals) {
                var domain = o.domain().orElseThrow();
                String size = domain.attributes().get("size");
                seen.add(size + " " + domain.attributes().get("steps") + " " + domain.verdictCode());
            }
            assertEquals(new TreeSet<>(List.of("extra_large open+pay " + design.checkCode(), "extra_large pay+open " + design.checkCode(),
                    "small open+pay 0", "small pay+open 0")), seen);
        }
        assertEquals(List.of("ran"), Files.readAllLines(log), "once for the whole run, on four workers");
        assertFalse(design.withBootstrap(new GuidedScenario.Bootstrap(false, "EU")).document().sheetNames().contains("FW_RunMeFirstOnce"));
        assertTrue(design.withBootstrap(new GuidedScenario.Bootstrap(true, "FW_x")).problems().stream().anyMatch(p -> p.contains("FW_x")));
    }

    private static Session session() {
        return SessionTest.session();
    }

    /** The orders of each sequence a kept test case runs, from the test cases' code: order to how often. */
    private static Map<String, Integer> orders(CandidatePreview preview, String sequence) {
        Pattern act = Pattern.compile("act\\(\"" + sequence + "\", \"([^\"]*)\"\\);");
        Map<String, Integer> seen = new java.util.TreeMap<>();
        for (CandidatePreview.Row row : preview.page().rows()) {
            Matcher m = act.matcher(row.source());
            assertTrue(m.find(), "every kept test case does the sequence: " + row.source());
            seen.merge(m.group(1), 1, Integer::sum);
        }
        return seen;
    }

    /**
     * A covering selection treats the three kinds apart: pairwise covers the static parameters; every order of a sequence of
     * actions runs with each combination it keeps (FW_Optional and the rule that it is always there); each sudden action
     * happens or not with every one of them. Without that, the sequence's orders are chosen like a parameter's values.
     */
    @Test
    void pairwiseCoversTheStaticParametersWhileEveryOrderAndEverySuddenActionRunWithEachKeptCombination(@TempDir Path dir) {
        Factor steps = Factor.of("steps", List.of("login", "search", "pay")).using(Use.EXACTLY, 2, 0, 3).withOrder(true);
        Factor timeout = Factor.of("timeout", List.of("short")).withOptional(true);
        assertEquals(List.of(Role.SEQUENCE, Role.SUDDEN, Role.PARAMETER), List.of(steps.role(), timeout.role(), Factor.of("x", FOUR).role()));
        assertEquals(steps.withOptional(false), steps.as(Role.PARAMETER).as(Role.SEQUENCE).using(Use.EXACTLY, 2, 0, 3),
                "a sequence again: two of its three actions in a row");
        GuidedScenario design = GuidedScenario.empty().named("kinds")
                .adding(Factor.of("browser", List.of("chrome", "firefox", "safari"))).adding(Factor.of("os", List.of("linux", "mac", "windows")))
                .adding(Factor.of("lang", List.of("en", "uk"))).adding(steps).adding(timeout).selecting(Selection.PAIRS, 0, false);
        assertTrue(design.apart());
        assertEquals(List.of("FW_Optional", "FW_Concatenator=+", "FW_Combi(2)", "FW_Permut()"), design.instructions(steps));
        assertEquals(BigInteger.valueOf(18), design.mandatory(), "the selection chooses among the static parameters only");
        assertEquals(BigInteger.valueOf(6 * 2), design.perRow(), "every order, the sudden action or not");
        assertEquals(BigInteger.valueOf(18 * 7 * 2), design.generated(), "FW_Optional also makes the sequence's absent branch");
        assertTrue(design.constraintsJson().contains("\"id\": \"steps_always\""), design.constraintsJson());
        Session session = SessionTest.session();
        Path file = design.save(dir.resolve("kinds.json")).scenario();
        CandidatePreview kept = SessionTest.session("cf.coverage.strength=2").preview(file, new CandidatePreview.Request(
                BigInteger.valueOf(1_000_000), BigInteger.ZERO, 500, true, true), Cancellation.NONE);
        BigInteger rows = stage(kept, "selected rows");
        assertEquals(design.generated(), stage(kept, "generated"));
        assertEquals(rows.multiply(design.perRow()), stage(kept, "executable"), "each kept combination of the parameters runs 12 ways");
        Map<String, Integer> seen = orders(kept, "steps");
        assertEquals(6, seen.size(), "every order runs: " + seen);
        assertEquals(1, new HashSet<>(seen.values()).size(), "and each as often as the others: " + seen);
        assertTrue(kept.page().rows().stream().anyMatch(r -> r.source().contains("happen(\"timeout\", \"short\");"))
                && kept.page().rows().stream().anyMatch(r -> !r.source().contains("happen(\"timeout\"")), "the sudden action happens or not");
        GuidedScenario.Checked checked = design.check(session, Cancellation.NONE);
        assertEquals(stage(kept, "executable"), checked.executable());
        assertTrue(checked.text().contains("steps_always removes " + GuidedScenario.figure(rows.multiply(BigInteger.TWO)))
                && checked.text().contains("without the sequence"), checked.text());

        // the selection may choose orders like values instead: the sequence counts as a parameter, and no rule is needed
        GuidedScenario chosen = design.withAllOrders(false);
        assertFalse(chosen.apart());
        assertEquals(List.of("FW_Concatenator=+", "FW_Combi(2)", "FW_Permut()"), chosen.instructions(steps));
        assertEquals(BigInteger.valueOf(18 * 6), chosen.mandatory());
        assertFalse(chosen.constraintsJson().contains("steps_always"), chosen.constraintsJson());
        GuidedScenario.Checked fewer = chosen.check(session, Cancellation.NONE);
        assertEquals(chosen.generated(), fewer.generated());
        assertTrue(fewer.executable().compareTo(checked.executable()) < 0, fewer.text() + " vs " + checked.text());
        // without a selection the two are the same scenario
        assertEquals(design.selecting(Selection.ALL, 0, false).instructions(steps), chosen.selecting(Selection.ALL, 0, false).instructions(steps));
    }

    /** What cannot be written says what to change, and nothing is written. */
    @Test
    void problemsSayWhatToChangeAndKeepTheDesignFromBeingWritten(@TempDir Path dir) {
        GuidedScenario fine = GuidedScenario.empty().adding(Factor.of("browser", List.of("chrome", "firefox")));
        assertEquals(List.of(), fine.problems());
        List<String> problems = GuidedScenario.empty().named("2nd scenario")
                .adding(Factor.of("my factor", List.of("a", "a")))
                .adding(Factor.of("FW_mine", List.of("FW_x", "say \"hi\"")))
                .adding(Factor.of("TAIL", List.of()))
                .adding(Factor.of("steps", FOUR).using(Use.EXACTLY, 5, 0, 4))
                .adding(Factor.of("Steps", FOUR).using(Use.RANGE, 2, 3, 2))
                .adding(new Rule(Kind.NEVER, "steps", "a", "browser", "chrome"))
                .problems();
        for (String expected : List.of("The scenario's name starts with a letter", "Factor my factor: a name starts with a letter",
                "Factor my factor has the value a twice.", "Factor FW_mine: a name that starts with FW_",
                "the value FW_x starts with FW_", "the value say \"hi\" has a quote", "Factor TAIL: HEAD, check and TAIL",
                "Factor TAIL needs at least one value", "Factor steps has 4 values, so a test case cannot take 5 different ones",
                "Factor Steps: the fewest values (3)", "Two factors are named Steps.", "names the factor browser")) {
            assertTrue(problems.stream().anyMatch(p -> p.contains(expected)), expected + " in " + problems);
        }
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 83; i++) many.add("v" + i);
        GuidedScenario big = GuidedScenario.empty().adding(Factor.of("a", many)).adding(Factor.of("b", many)).adding(Factor.of("c", many));
        assertEquals(3 + 3 + 249 + 3, big.labels());
        assertTrue(big.problems().stream().anyMatch(p -> p.startsWith("The scenario uses 258 labels")), big.problems().toString());
        GuidedScenario fits = big.withCheck(false).removingFactor(2).adding(Factor.of("c", many.subList(0, 82)));
        assertEquals(255, fits.labels());
        assertTrue(fits.problems().isEmpty(), "255 labels fit: " + fits.problems());
        FrameworkException refused = assertThrows(FrameworkException.class, () -> big.save(dir.resolve("big.json")));
        assertTrue(refused.getMessage().contains("258 labels"), refused.getMessage());
        assertFalse(Files.exists(dir.resolve("big.json")));
        assertTrue(GuidedScenario.empty().adding(Factor.of("f", FOUR)).withRules(List.of(new Rule(Kind.NEVER, "f", "a", "f", "b")))
                .problems().getFirst().contains("never together anyway"));
    }

    @Test
    void aFullByteWizardDesignValidatesAndPreviewsWithNegativeLabels(@TempDir Path dir) {
        List<String> values = java.util.stream.IntStream.range(0, 248).mapToObj(i -> "v" + i).toList();
        GuidedScenario design = GuidedScenario.empty().adding(Factor.of("choice", values));
        assertEquals(255, design.labels());
        assertTrue(design.problems().isEmpty(), design.problems().toString());
        Path file = dir.resolve("full-byte.json");
        design.save(file);
        Session session = SessionTest.session("cf.keys.encoding=SIGNED_BYTE");
        var summary = session.validate(file);
        assertEquals(org.ghyura.cf.model.key.KeyEncoding.SIGNED_BYTE, summary.encoding());
        assertTrue(summary.firstLabel() < 0);
        assertEquals(127, summary.highestKey());
        CandidatePreview preview = session.preview(file, new CandidatePreview.Request(BigInteger.valueOf(1000),
                BigInteger.ZERO, 3, false, true), Cancellation.NONE);
        assertEquals(design.generated(), stage(preview, "generated"));
        assertEquals(design.total(), stage(preview, "executable"));
        assertEquals(3, preview.page().rows().size());
    }

    /** The advice follows the numbers: few run whole; many get pairwise; covering cannot help fewer than three factors. */
    @Test
    void theRecommendedSelectionFollowsTheNumbers() {
        GuidedScenario few = GuidedScenario.empty().adding(Factor.of("a", FOUR)).adding(Factor.of("b", FOUR)).adding(Factor.of("c", FOUR));
        assertEquals(Selection.ALL, few.recommended());
        assertTrue(few.advice().startsWith("64 combinations are few enough"), few.advice());
        GuidedScenario many = few.adding(Factor.of("d", FOUR)).adding(Factor.of("e", FOUR).using(Use.EXACTLY, 2, 0, 4).withOrder(true));
        assertEquals(BigInteger.valueOf(4 * 4 * 4 * 4 * 12), many.total());
        assertEquals(Selection.PAIRS, many.recommended());
        assertEquals(BigInteger.valueOf(48), many.floor(2), "every combination: the sequence's orders count like values");
        assertEquals(BigInteger.valueOf(16), many.selecting(Selection.PAIRS, 0, false).floor(2), "pairwise covers the static parameters");
        assertTrue(many.advice().contains("Pairwise is for the static parameters") && many.advice().contains("at least 192 test cases")
                && many.advice().contains("every order of each sequence of actions runs with each combination it keeps (e: 12 orders)"),
                many.advice());
        assertTrue(many.withAllOrders(false).advice().contains("at least 48 test cases"), many.withAllOrders(false).advice());
        GuidedScenario two = GuidedScenario.empty().adding(Factor.of("a", FOUR).using(Use.EXACTLY, 3, 0, 4).withOrder(true))
                .adding(Factor.of("b", FOUR).using(Use.RANGE, 0, 0, 4));
        assertEquals(Selection.ALL, two.recommended());
        assertTrue(two.advice().contains("cannot make them fewer"), two.advice());
        assertEquals("at least 192 test cases, so that every pair of values appears", many.selecting(Selection.PAIRS, 0, false).kept());
    }
}
