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

package org.ghyura.cf.acceptance;

import org.ghyura.cf.input.FileResolver;
import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.input.ScenarioInterpreter;
import org.ghyura.cf.input.XlsxWorkbookReader;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.input.WorkbookData;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.model.key.KeyEncodingPolicy;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.spec.LocatedText;
import org.ghyura.cf.model.spec.ScenarioSpec;
import org.ghyura.cf.model.spec.SeqRow;
import org.ghyura.cf.model.spec.SheetSpec;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.DirectiveParser;
import org.ghyura.cf.plan.PlanOptions;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SeqPlanner;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.plan.SheetRole;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gates A02 and A03 on real input: the 32 workbooks of the reference checkout go through the framework's
 * reader, interpreter, planner and dictionary, and everything is compared with {@link CorpusSurvey}, an
 * independent reading of the same bytes straight through POI.
 */
class CorpusPlanTest {

    private static final Set<ErrorCode> EXPECTED_FINDINGS = Set.of(ErrorCode.SPEC_DUPLICATE_VALUE, ErrorCode.SPEC_RUNMEFIRSTONCE_SHAPE,
            ErrorCode.PLAN_ROW_OVERWRITTEN);

    private static final Set<String> FLAG_TOKENS = Set.of("FW_Exclude", "FW_Optional", "FW_Reuse", "FW_ReuseTableOnly");

    private record Pipeline(WorkbookData workbook, ScenarioSpec spec, SeqPlan plan, List<Diagnostic> findings) {
    }

    private static Pipeline run(String file) throws Exception {
        WorkbookData workbook = new XlsxWorkbookReader().read(InputSource.of(file, CorpusSurvey.bytes(file))).workbook();
        ScenarioInterpreter.Interpretation interpretation = ScenarioInterpreter.interpret(workbook, FileResolver.none());
        SeqPlanner.Planned planned = SeqPlanner.plan(interpretation.spec(), PlanOptions.legacyDefaults());
        List<Diagnostic> findings = new ArrayList<>(interpretation.diagnostics());
        findings.addAll(planned.diagnostics());
        return new Pipeline(workbook, interpretation.spec(), planned.plan(), findings);
    }

    @Test
    void theSurveyTotalsOfTheWholeCorpusAreTheFiguresOfTheOneOffDirectPoiCount() throws Exception {
        int sheets = 0;
        int values = 0;
        int rows = 0;
        int cells = 0;
        Map<String, Integer> flags = new TreeMap<>();
        Map<String, Integer> shapes = new TreeMap<>();
        for (CorpusSurvey.Entry entry : CorpusSurvey.entries()) {
            CorpusSurvey.Book book = CorpusSurvey.survey(entry.file());
            sheets += book.dataSheets();
            values += book.dataCells();
            rows += book.seqRows;
            cells += book.seqCells;
            book.flags.forEach((k, v) -> flags.merge(k, v, Integer::sum));
            book.shapes.forEach((k, v) -> shapes.merge(k, v, Integer::sum));
        }
        assertEquals(429, sheets);
        assertEquals(1093, values);
        assertEquals(580, rows);
        assertEquals(2332, cells);
        assertEquals(Map.of("FW_Exclude", 96, "FW_Optional", 14, "FW_Reuse", 253, "FW_ReuseTableOnly", 16), flags);
        assertEquals(Map.of("Cartes", 51, "Combi", 751, "CombiR", 50, "Group", 230, "Permut", 68, "PermutR", 57, "Separator", 72,
                "Subsets", 74, "joiner", 20), shapes);
    }

    @Test
    void everyRealWorkbookPlansWithoutErrorAndEveryFactAgreesWithTheDirectSurvey() throws Exception {
        long overwritten = 0;
        long nestedTotal = 0;
        for (CorpusSurvey.Entry entry : CorpusSurvey.entries()) {
            String file = entry.file();
            CorpusSurvey.Book survey = CorpusSurvey.survey(file);
            Pipeline pipeline = run(file);
            ScenarioSpec spec = pipeline.spec();
            SeqPlan plan = pipeline.plan();

            assertEquals(survey.dataSheets(), spec.sheetCount(), file + " data sheets");
            assertEquals(survey.dataCells(), spec.valueCount(), file + " values");
            List<String> surveyed = new ArrayList<>();
            survey.values.values().forEach(surveyed::addAll);
            List<String> interpreted = new ArrayList<>();
            spec.sheets().forEach(s -> s.cells().forEach(v -> interpreted.add(v.text())));
            assertEquals(surveyed, interpreted, file + " the text of every value, in dictionary order");
            assertEquals(survey.seqRows, spec.sequence().size(), file + " FW_Seq rows");
            assertEquals(survey.seqCells, spec.sequence().stream().mapToInt(r -> r.cells().size()).sum(), file + " FW_Seq cells");

            assertEquals(0, plan.virtualSheetCount(), file + " generates no virtual sheet: every reference names a real sheet");
            assertEquals(survey.dataSheets(), plan.sheets().size(), file);
            assertTrue(pipeline.findings().stream().allMatch(d -> EXPECTED_FINDINGS.contains(d.code()) && !d.severity().isError()),
                    file + ": " + pipeline.findings());
            long overwrites = pipeline.findings().stream().filter(d -> d.code() == ErrorCode.PLAN_ROW_OVERWRITTEN).count();
            assertEquals(survey.overwritten, overwrites, file + " rows that replace an earlier row of the same sheet");
            assertTrue(pipeline.findings().stream().filter(d -> d.code() == ErrorCode.PLAN_ROW_OVERWRITTEN).allMatch(d -> d.severity() == Severity.INFO));
            overwritten += overwrites;

            for (SheetPlan sheet : plan.sheets()) {
                CorpusSurvey.SheetFacts facts = survey.sheets.get(sheet.name());
                assertEquals(facts.role, sheet.role().name(), file + " / " + sheet.name() + " role");
                assertEquals(facts.reuse, sheet.reuse(), file + " / " + sheet.name() + " reuse");
                assertEquals(facts.reuseTableOnly, sheet.reuseTableOnly(), file + " / " + sheet.name() + " reuse table only");
                assertEquals(facts.hasDirectives, sheet.active(), file + " / " + sheet.name() + " active");
                assertEquals(facts.promoted, sheet.promoted(), file + " / " + sheet.name() + " promoted");
                assertEquals(survey.values.get(sheet.name()).size(), sheet.valueCount(), file + " / " + sheet.name());
            }
            long placeholders = 0;
            for (SeqRow row : spec.sequence()) {
                for (LocatedText cell : row.cells()) {
                    if (cell.text().startsWith("FW_(")) {
                        Directive.Joiner joiner = (Directive.Joiner) DirectiveParser.parse(cell.text(), cell.location());
                        placeholders += (joiner.first().isPlaceholder() ? 1 : 0) + (joiner.second().isPlaceholder() ? 1 : 0);
                    }
                }
            }
            assertEquals(survey.nestedPlaceholders, placeholders, file + " nested placeholders");
            nestedTotal += placeholders;
        }
        assertEquals(4, nestedTotal, "four joiner operands in the real workbooks are the nested placeholders FW_() and FW_()G");
        assertEquals(176, overwritten, "the real workbooks use the FW_Seq overwrite rule 176 times: the first rows give flags and a basic verb, "
                + "later rows restate the final verbs");
    }

    @Test
    void everyDirectiveCellOfTheRealWorkbooksParsesToTheFamilyTheSurveyNamedIt() throws Exception {
        Map<String, Integer> families = new TreeMap<>();
        Map<String, Integer> surveyed = new TreeMap<>();
        for (CorpusSurvey.Entry entry : CorpusSurvey.entries()) {
            Pipeline pipeline = run(entry.file());
            CorpusSurvey.survey(entry.file()).shapes.forEach((k, v) -> surveyed.merge(k, v, Integer::sum));
            for (SeqRow row : pipeline.spec().sequence()) {
                for (LocatedText cell : row.cells()) {
                    if (!cell.text().startsWith("FW_") || FLAG_TOKENS.contains(cell.text())) {
                        continue;
                    }
                    Directive directive = DirectiveParser.parse(cell.text(), cell.location());
                    String family = switch (directive) {
                        case Directive.Combinatoric c -> c.verb().token().substring(3);
                        case Directive.Subsets s -> "Subsets";
                        case Directive.Cartes c -> "Cartes";
                        case Directive.Group g -> "Group";
                        case Directive.Separator s -> "Separator";
                        case Directive.Joiner j -> "joiner";
                    };
                    families.merge(family, 1, Integer::sum);
                    assertEquals(cell.text(), directive.source());
                }
            }
        }
        assertEquals(surveyed, families);
    }

    @Test
    void theRealDirectiveFormsIncludeTheOnesTheStrictGrammarHadToKeep() throws Exception {
        Set<String> seen = new java.util.TreeSet<>();
        for (CorpusSurvey.Entry entry : CorpusSurvey.entries()) {
            for (SeqRow row : run(entry.file()).spec().sequence()) {
                for (LocatedText cell : row.cells()) {
                    if (cell.text().startsWith("FW_") && !cell.text().startsWith("FW_Group") && !cell.text().startsWith("FW_(")
                            && !FLAG_TOKENS.contains(cell.text())) {
                        seen.add(DirectiveParser.parse(cell.text(), cell.location()) == null ? "?" + cell.text() : cell.text());
                    }
                }
            }
        }
        assertFalse(seen.stream().anyMatch(s -> s.startsWith("?")), seen.toString());
        // lower-case subset mode, bare and bracketed FW_Permut, the legacy duplicate marker and a verb argument of "all" all occur for real
        assertTrue(seen.containsAll(Set.of("FW_Subsets_after(1)", "FW_Permut", "FW_Permut()", "FW_PermutR(all)", "FW_Combi(size)", "FW_Subsets",
                "FW_Permut(PermutationGenerator.TreatDuplicatesAs.IDENTICAL)", "FW_Cartes_first(1)")), seen.toString());
    }

    @Test
    void theDictionaryOfEveryRealWorkbookLabelsValuesFromSPlusOneAndPicksTheEncodingAByteCanHold() throws Exception {
        int needTwoBytes = 0;
        for (CorpusSurvey.Entry entry : CorpusSurvey.entries()) {
            String file = entry.file();
            CorpusSurvey.Book survey = CorpusSurvey.survey(file);
            Pipeline pipeline = run(file);
            int s = survey.dataSheets();
            int c = survey.dataCells();
            boolean oneByte = s + c <= 127;
            needTwoBytes += oneByte ? 0 : 1;
            try (KeyDictionary dictionary = KeyDictionary.build(pipeline.spec(), (int) pipeline.plan().virtualSheetCount(),
                    KeyEncodingPolicy.AUTO_PRESERVE, new MemoryBudget(1L << 30))) {
                assertEquals(oneByte ? KeyEncoding.SIGNED_BYTE : KeyEncoding.PACKED_LEGACY16, dictionary.encoding(), file + " " + s + "+" + c);
                assertEquals(s + 1, dictionary.firstLabel(), file);
                assertEquals(s + c, c == 0 ? s + c : dictionary.lastLabel(), file);
                assertEquals(Math.max(s, s + c), dictionary.census().max(), file);
                assertEquals(1, dictionary.census().min(), file);
                assertEquals(s + c, dictionary.census().count(), file + " every sheet key and every value label is counted once");

                int label = s + 1;
                int sheetKey = 0;
                for (Map.Entry<String, List<String>> data : survey.values.entrySet()) {
                    sheetKey++;
                    int[] labels = dictionary.labelsOf(sheetKey);
                    assertEquals(data.getValue().size(), labels.length, file + " / " + data.getKey());
                    for (int i = 0; i < labels.length; i++) {
                        assertEquals(label, labels[i], file + " / " + data.getKey() + " label " + i);
                        ValueCell value = dictionary.value(label);
                        assertEquals(data.getValue().get(i), value.text(), file + " / " + data.getKey() + " value " + i);
                        assertEquals(sheetKey, dictionary.sheetOfLabel(label), file);
                        label++;
                    }
                    KeyRow row = dictionary.rowOf(sheetKey);
                    assertEquals(dictionary.encoding(), row.encoding(), file);
                    assertTrue(java.util.Arrays.equals(labels, row.labels()), file + " / " + data.getKey() + " round trip through the encoding");
                }
                assertEquals(s + c + 1, label, file);
            }
            if (!oneByte) {
                FrameworkException strict = assertThrows(FrameworkException.class, () -> KeyDictionary.build(pipeline.spec(), 0,
                        KeyEncodingPolicy.SIGNED_BYTE, new MemoryBudget(1L << 30)), file);
                assertEquals(ErrorCode.KEY_CENSUS_OVERFLOW, strict.code(), file);
            } else {
                KeyDictionary.build(pipeline.spec(), 0, KeyEncodingPolicy.SIGNED_BYTE, new MemoryBudget(1L << 30)).close();
            }
            KeyDictionary.build(pipeline.spec(), 0, KeyEncodingPolicy.PACKED_LEGACY16, new MemoryBudget(1L << 30)).close();
        }
        assertEquals(2, needTwoBytes, "two of the real workbooks have labels above 127: the generator example (601) and the classes spec (135)");
    }

    @Test
    void planningARealWorkbookTwiceGivesTheSamePlanAndTheSameScenarioIdentity() throws Exception {
        for (CorpusSurvey.Entry entry : CorpusSurvey.entries()) {
            Pipeline first = run(entry.file());
            Pipeline second = run(entry.file());
            assertEquals(first.spec().contentDigest(), second.spec().contentDigest(), entry.file());
            assertEquals(first.plan().contentDigest(), second.plan().contentDigest(), entry.file());
            assertEquals(first.plan().canonicalText(), second.plan().canonicalText(), entry.file());
        }
    }

    @Test
    void theRolesOfTheRealSheetsAddUpToWhatTheDirectSurveyCounted() throws Exception {
        long excluded = 0;
        long optional = 0;
        long mandatory = 0;
        long none = 0;
        for (CorpusSurvey.Entry entry : CorpusSurvey.entries()) {
            CorpusSurvey.Book survey = CorpusSurvey.survey(entry.file());
            excluded += survey.count("EXCLUDED");
            optional += survey.count("OPTIONAL");
            mandatory += survey.count("MANDATORY");
            none += survey.count("NONE");
            SeqPlan plan = run(entry.file()).plan();
            assertEquals(survey.count("EXCLUDED"), plan.sheets().stream().filter(x -> x.role() == SheetRole.EXCLUDED).count(), entry.file());
        }
        assertEquals(429, excluded + optional + mandatory + none);
        assertEquals(List.of(59L, 10L, 359L, 1L), List.of(excluded, optional, mandatory, none));
    }
}
