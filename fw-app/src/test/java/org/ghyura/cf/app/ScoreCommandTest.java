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

package org.ghyura.cf.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** score: the AN-02 textual scorer through the command line. */
class ScoreCommandTest {

    @Test
    void aTextFileIsScoredWithManualRulesAndRefusalsAreTyped(@TempDir Path dir) throws Exception {
        Path input = Files.writeString(dir.resolve("corpus.log"), "# metrics\nrun=1 latency=12 status=ok\nrun=2 latency=40 status=ok\n"
                + "run=3 latency=9 status=error\necho multi \\\n  line\nrun=4 latency=11 status=ok\n", StandardCharsets.UTF_8);
        Path rules = Files.writeString(dir.resolve("rules.json"), "{\"required_tokens\":[\"status\"],\"forbidden_tokens\":[\"error\"],"
                + "\"optimizations\":[{\"key\":\"latency\",\"mode\":\"minimize\",\"weight\":40}]}", StandardCharsets.UTF_8);
        Result xml = run("score", "--input", input.toString(), "--rules", rules.toString(), "--top-k", "5", "--opt-threshold", "50", "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        assertTrue(xml.out().contains("<text-scoring input=\"" + input + "\" sha256=\""), xml.out());
        assertTrue(xml.out().contains("lines=\"6\""), "the continued command is one logical line: " + xml.out());
        assertTrue(xml.out().contains("<line no=\"5\" score=\""), "it is numbered by its first physical line: " + xml.out());
        assertTrue(xml.out().contains("<contribution name=\"ForbidToken:error\" value=\"-50.0\"/>"), xml.out());
        assertTrue(xml.out().contains("<goal key=\"latency\" mode=\"minimize\" target=\"0.0\" weight=\"40.0\"/>"), xml.out());
        assertTrue(xml.out().contains("<metric key=\"latency\" count=\"4\""), xml.out());
        Result text = run("score", "--input", input.toString());
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().contains("decisions: opt "), text.out());
        assertTrue(text.out().contains("#2    score="), text.out());

        assertEquals(2, run("score").code(), "--input is required");
        Result badTopK = run("score", "--input", input.toString(), "--top-k", "0");
        assertEquals(2, badTopK.code());
        assertTrue(badTopK.err().contains("USAGE_INVALID_VALUE"), badTopK.err());
        assertEquals(2, run("score", "--input", input.toString(), "--min-support", "1.5").code());
        Files.writeString(rules, "{\"required_regexes\":[\"(\"]}", StandardCharsets.UTF_8);
        Result badRegex = run("score", "--input", input.toString(), "--rules", rules.toString());
        assertEquals(3, badRegex.code());
        assertTrue(badRegex.err().contains("INPUT_MALFORMED"), badRegex.err());
        Files.writeString(rules, "{\"optimization\":{\"key\":\"latency\",\"mode\":\"lowest\"}}", StandardCharsets.UTF_8);
        Result badMode = run("score", "--input", input.toString(), "--rules", rules.toString());
        assertEquals(1, badMode.code());
        assertTrue(badMode.err().contains("ANALYSIS_INVALID_GOAL"), badMode.err());
        assertEquals(3, run("score", "--input", dir.resolve("missing.log").toString()).code());
    }

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int code = Application.run(args, Map.of(), new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Result(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    @Test
    void suggestPrintsTheLegacyPlanAsExplicitSettingsAndScoresNothing(@TempDir Path dir) throws Exception {
        StringBuilder corpus = new StringBuilder("echo start\n");
        for (int i = 0; i < 6; i++) corpus.append("run=").append(i).append(" latency=").append(10 + i).append(" accuracy=0.9").append(i).append('\n');
        Path input = Files.writeString(dir.resolve("lines.log"), corpus.toString(), StandardCharsets.UTF_8);
        Result text = run("score", "--input", input.toString(), "--suggest");
        assertEquals(0, text.code(), text.err());
        assertTrue(text.out().contains("cf.analysis.goals=run:min:33.33,latency:min:33.33,accuracy:max:33.33"), text.out());
        assertTrue(text.out().contains("\"optimizations\""), text.out());
        assertTrue(text.out().contains("this build executes nothing outside Java"), text.out());
        assertTrue(!text.out().contains("decisions:"), "nothing is scored");
        Result xml = run("score", "--input", input.toString(), "--suggest", "--format", "xml");
        assertEquals(0, xml.code(), xml.err());
        assertTrue(xml.out().contains("<goal key=\"accuracy\" mode=\"MAXIMIZE\" target=\"0.925\" weight=\"33.33\" support=\"6\""), xml.out());
        assertTrue(xml.out().contains("shellLike=\"1\""), xml.out());
    }
}
