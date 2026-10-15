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

package org.ghyura.cf.analysis;

import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AN-02: the textual scorer against goldens of the unchanged legacy analyzer (handoff/outbox/devtools/TextScoringGoldens.java). */
class TextScoringTest {

    @Test
    void everyFeatureProfileRuleAndScoreMatchesTheOriginalAnalyzer() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        NodeList cases;
        try (var in = getClass().getResourceAsStream("text-scoring-goldens.xml")) {
            assertNotNull(in);
            cases = factory.newDocumentBuilder().parse(in).getElementsByTagName("case");
        }
        assertEquals(8, cases.getLength());
        int trailing = 0;
        MemoryBudget budget = new MemoryBudget(64L << 20);
        for (int i = 0; i < cases.getLength(); i++) {
            Element c = (Element) cases.item(i);
            String id = c.getAttribute("id");
            RuleSet rules = RuleSet.parse(text(child(c, "config")), id);
            TextScoring.Settings settings = new TextScoring.Settings(Integer.parseInt(c.getAttribute("minSupport")),
                    Integer.parseInt(c.getAttribute("topK")), num(c, "optThreshold"), num(c, "watchThreshold"));
            TextScoring.Report report = TextScoring.score(text(child(c, "corpus")).lines().toList(), rules, settings, budget);
            assertEquals(0, budget.reservedBytes(), id);

            List<Element> goals = children(c, "goal");
            assertEquals(goals.size(), rules.goals().size(), id);
            for (int g = 0; g < goals.size(); g++) {
                Element e = goals.get(g);
                RuleSet.Goal goal = rules.goals().get(g);
                String at = id + " goal " + g;
                assertEquals(e.getAttribute("key"), goal.key(), at);
                assertEquals(e.getAttribute("mode"), goal.mode(), at);
                assertEquals(num(e, "target"), goal.target(), at);
                assertEquals(num(e, "weight"), goal.weight(), at);
                assertEquals(Boolean.parseBoolean(e.getAttribute("valid")), goal.optimumValid(), at);
                assertEquals(num(e, "argmin"), goal.argmin(), at);
                assertEquals(num(e, "optimum"), goal.optimum(), at);
            }

            Element p = child(c, "profile");
            CorpusProfile profile = report.profile();
            assertEquals(Integer.parseInt(p.getAttribute("totalLines")), profile.totalLines(), id);
            assertEquals(Integer.parseInt(p.getAttribute("nonemptyLines")), profile.nonemptyLines(), id);
            assertEquals(Integer.parseInt(p.getAttribute("emptyLines")), profile.emptyLines(), id);
            double[] expected = {num(p, "lengthMean"), num(p, "lengthMedian"), num(p, "lengthP10"), num(p, "lengthP90"), num(p, "lengthStdev"),
                    num(p, "entropyMean"), num(p, "entropyMedian"), num(p, "entropyP10"), num(p, "entropyP90"), num(p, "entropyStdev")};
            double[] actual = {profile.lengthMean(), profile.lengthMedian(), profile.lengthP10(), profile.lengthP90(), profile.lengthStdev(),
                    profile.entropyMean(), profile.entropyMedian(), profile.entropyP10(), profile.entropyP90(), profile.entropyStdev()};
            for (int k = 0; k < expected.length; k++) assertEquals(expected[k], actual[k], id + " profile statistic " + k);
            assertEquals(p.getAttribute("dominantLineType"), profile.dominantLineType(), id);
            assertEquals(p.getAttribute("dominantSignature"), profile.dominantSignature(), id);
            List<String> frequencies = new ArrayList<>();
            for (Element e : children(p, "frequency")) frequencies.add(e.getAttribute("kind") + ":" + e.getAttribute("value") + "=" + e.getAttribute("count"));
            List<String> ours = new ArrayList<>();
            addAll(ours, "token", profile.tokenFrequency());
            addAll(ours, "key", profile.keyFrequency());
            addAll(ours, "number", profile.numberFrequency());
            addAll(ours, "signature", profile.signatureFrequency());
            addAll(ours, "lineType", profile.lineTypeFrequency());
            assertEquals(frequencies, ours, id + ": frequencies with the legacy tie order");
            assertEquals(values(p, "common-token"), profile.commonTokens(), id);
            assertEquals(values(p, "common-key"), profile.commonKeys(), id);
            assertEquals(values(p, "common-number"), profile.commonNumbers(), id);
            List<String> bands = new ArrayList<>();
            for (Element e : children(p, "metric")) {
                bands.add(e.getAttribute("key") + " " + e.getAttribute("count") + " " + num(e, "min") + " " + num(e, "max") + " " + num(e, "low") + " "
                        + num(e, "high"));
            }
            List<String> ourBands = new ArrayList<>();
            profile.metrics().forEach((k, b) -> ourBands.add(k + " " + b.count() + " " + b.min() + " " + b.max() + " " + b.low() + " " + b.high()));
            assertEquals(bands, ourBands, id);

            Element r = child(c, "rules");
            assertEquals(num(r, "confidence"), report.synthesized().confidence(), id);
            assertEquals(Integer.parseInt(r.getAttribute("tokenDfCutoff")), report.synthesized().tokenDfCutoff(), id);
            assertEquals(Integer.parseInt(r.getAttribute("numberDfCutoff")), report.synthesized().numberDfCutoff(), id);

            List<Element> lines = children(c, "line");
            assertEquals(lines.size(), report.lines().size(), id);
            StringBuilder csv = new StringBuilder("LineNo,Score,Decision,OriginalLine,Type,Length,Entropy,Tokens,Keys,Numbers,Signature,Notes,MatchedRules\n");
            for (Element e : lines) {
                String row = text(child(e, "csv"));
                assertTrue(row.startsWith(e.getAttribute("no") + ","), row);
                csv.append(e.getAttribute("first")).append(row.substring(e.getAttribute("no").length()));
            }
            assertEquals(csv.toString(), report.toCsv(), id + ": the legacy CSV export, line numbers as D56");
            for (int l = 0; l < lines.size(); l++) {
                Element e = lines.get(l);
                ScoreBreakdown s = report.lines().get(l);
                TextFeatures f = s.features();
                String at = id + " line " + e.getAttribute("no");
                // delta D56: a logical line starts at its first physical line, also when the input ends inside it
                assertEquals(Integer.parseInt(e.getAttribute("first")), s.lineNo(), at);
                if (!e.getAttribute("no").equals(e.getAttribute("first"))) {
                    trailing++;
                    assertEquals(l, lines.size() - 1, at + ": only an unterminated last command was numbered by its last line");
                }
                assertEquals(num(e, "score"), s.score(), at);
                assertEquals(e.getAttribute("decision"), s.decision(), at);
                assertEquals(e.getAttribute("type"), f.lineType(), at);
                assertEquals(e.getAttribute("signature"), f.signature(), at);
                assertEquals(Integer.parseInt(e.getAttribute("length")), f.length(), at);
                assertEquals(Integer.parseInt(e.getAttribute("placeholders")), f.placeholderCount(), at);
                double[] ratios = {num(e, "entropy"), num(e, "alpha"), num(e, "digit"), num(e, "space"), num(e, "punct"), num(e, "upper"), num(e, "unique")};
                double[] ourRatios = {f.entropy(), f.alphaRatio(), f.digitRatio(), f.spaceRatio(), f.punctRatio(), f.uppercaseRatio(), f.uniqueTokenRatio()};
                for (int k = 0; k < ratios.length; k++) assertEquals(ratios[k], ourRatios[k], at + " ratio " + k);
                assertEquals(text(child(e, "text")), s.raw(), at);
                assertEquals(text(child(e, "prefix")), f.prefix(), at);
                assertEquals(text(child(e, "suffix")), f.suffix(), at);
                assertEquals(values(e, "token"), f.tokens(), at);
                List<Double> numbers = new ArrayList<>();
                for (Element n : children(e, "number")) numbers.add(Double.parseDouble(n.getTextContent()));
                assertEquals(numbers, f.numbers(), at);
                List<String> kv = new ArrayList<>();
                for (Element n : children(e, "kv")) kv.add(n.getAttribute("key") + "=" + n.getAttribute("value"));
                List<String> ourKv = new ArrayList<>();
                f.keyValues().forEach((k, v) -> ourKv.add(k + "=" + v));
                assertEquals(kv, ourKv, at);
                List<String> delimiters = new ArrayList<>();
                for (Element n : children(e, "delimiter")) delimiters.add(n.getAttribute("char") + "=" + n.getAttribute("count"));
                List<String> ourDelimiters = new ArrayList<>();
                f.delimiters().forEach((k, v) -> ourDelimiters.add(k + "=" + v));
                assertEquals(delimiters, ourDelimiters, at);
                List<String> contributions = new ArrayList<>();
                for (Element n : children(e, "contribution")) contributions.add(n.getAttribute("name") + "=" + num(n, "value"));
                List<String> ourContributions = new ArrayList<>();
                s.contributions().forEach((k, v) -> ourContributions.add(k + "=" + v));
                assertEquals(contributions, ourContributions, at);
                assertEquals(values(e, "note"), s.notes(), at);
                assertEquals(values(e, "matched"), s.matchedRules(), at);
            }
        }
        assertEquals(1, trailing, "the shell case ends inside a for block");
    }

    @Test
    void ruleTypesTheLegacyReaderCoercedSilentlyAreRefusedAndNullBoundsAreOpen() {
        RuleSet open = RuleSet.parse("{\"numeric_ranges\":{\"x\":[null,2]},\"line_length\":{\"min\":null,\"max\":\"7.9\"},"
                + "\"optimizations\":[{\"key\":\"\"},{\"key\":\"Lat\",\"weight\":\" 2.5 \"}]}", "rules.json");
        assertEquals(new RuleSet.Range(Double.NEGATIVE_INFINITY, 2.0), open.numericRanges().get("x"));
        assertEquals(null, open.minLength());
        assertEquals(7, open.maxLength());
        assertEquals(1, open.goals().size(), "a goal without a key is skipped, as before");
        assertEquals("lat", open.goals().getFirst().key());
        assertEquals(2.5, open.goals().getFirst().weight());
        Map<String, ErrorCode> refused = Map.ofEntries(
                Map.entry("[]", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"required_tokens\":\"status\"}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"required_tokens\":[true]}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"token_weights\":{\"fast\":\"quick\"}}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"required_regexes\":[\"(\"]}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"numeric_ranges\":{\"x\":[\"0\",1]}}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"numeric_ranges\":{\"x\":[0,1,2]}}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"line_length\":{\"min\":\"short\"}}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"optimizations\":{\"key\":\"a\"}}", ErrorCode.INPUT_MALFORMED),
                Map.entry("{\"optimization\":{\"key\":\"a\",\"mode\":\"max\"}}", ErrorCode.ANALYSIS_INVALID_GOAL),
                Map.entry("{\"optimization\":{\"key\":\"a\",\"objective\":\"x +\",\"search_lo\":0,\"search_hi\":1}}", ErrorCode.ANALYSIS_INVALID_EXPRESSION),
                Map.entry("{\"notes\":[\"a\"],\"notes\":[\"b\"]}", ErrorCode.INPUT_MALFORMED));
        refused.forEach((json, code) -> {
            FrameworkException e = assertThrows(FrameworkException.class, () -> RuleSet.parse(json, "rules.json"), json);
            assertEquals(code, e.code(), json);
        });
    }

    @Test
    void scoringIsAdmittedBeforeItAllocatesAndAFileIsReadStrictly(@TempDir Path dir) throws Exception {
        List<String> corpus = new ArrayList<>();
        for (int i = 0; i < 2000; i++) corpus.add("run=" + i + " latency=" + (i % 17) + " status=ok tokens of a typical metrics line");
        MemoryBudget tight = new MemoryBudget(512 * 1024);
        FrameworkException e = assertThrows(FrameworkException.class, () -> TextScoring.score(corpus, RuleSet.NONE, TextScoring.Settings.LEGACY, tight));
        assertEquals(ErrorCode.RESOURCE_LIMIT, e.code());
        assertEquals(0, tight.reservedBytes(), "every reservation is returned");

        Path file = dir.resolve("corpus.log");
        Files.write(file, String.join("\r\n", corpus.subList(0, 5)).getBytes(StandardCharsets.UTF_8));
        MemoryBudget budget = new MemoryBudget(16L << 20);
        TextScoring.Report report = TextScoring.score(file, RuleSet.NONE, TextScoring.Settings.LEGACY, budget);
        assertEquals(5, report.lines().size());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))), report.sha256());
        assertEquals(report.lines().size(), report.count(ScoreBreakdown.OPT) + report.count(ScoreBreakdown.WATCH) + report.count(ScoreBreakdown.REJECT));
        assertEquals(0, budget.reservedBytes());
        Files.write(file, new byte[]{'a', (byte) 0xC3, '\n'});
        assertEquals(ErrorCode.INPUT_MALFORMED, assertThrows(FrameworkException.class,
                () -> TextScoring.score(file, RuleSet.NONE, TextScoring.Settings.LEGACY, budget)).code());
        assertEquals(0, budget.reservedBytes());
    }

    private static void addAll(List<String> out, String kind, Map<String, Integer> map) {
        map.forEach((k, v) -> out.add(kind + ":" + k + "=" + v));
    }

    private static double num(Element e, String attribute) {
        return Double.parseDouble(e.getAttribute(attribute));
    }

    private static String text(Element e) {
        return new String(Base64.getDecoder().decode(e.getTextContent().strip()), StandardCharsets.UTF_8);
    }

    private static Element child(Element parent, String tag) {
        List<Element> all = children(parent, tag);
        assertEquals(1, all.size(), tag);
        return all.getFirst();
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> out = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && e.getTagName().equals(tag)) out.add(e);
        }
        return out;
    }

    private static List<String> values(Element parent, String tag) {
        return children(parent, tag).stream().map(e -> e.getAttribute("value")).toList();
    }
}
