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

package org.ghyura.cf.model;

import java.util.Objects;

/**
 * The frozen error taxonomy (work package M00). Names are stable identifiers: a code is never renamed,
 * repurposed or removed, only added. Later work packages append codes under their own category; the
 * checked-in {@code error-taxonomy.xml} is the reviewed contract and a test renders this enum against it.
 *
 * <p>Outcomes of executed samples (PASS, DOMAIN_FAIL, BROKEN, TIMEOUT, INFRA_FAIL, SKIPPED, CANCELLED) are
 * not error codes; see {@link Outcome}. An error code describes why a request, input or run could not
 * proceed, never what a SUT answered.</p>
 */
public enum ErrorCode {

    // ---- cross-cutting -------------------------------------------------------------------------------------
    INTERNAL_ERROR(Category.INTERNAL, Severity.ERROR,
            "A framework invariant was violated; this is a defect, not a user input problem."),
    UNSUPPORTED_CAPABILITY(Category.CAPABILITY, Severity.ERROR,
            "The request needs a capability this build does not provide, or that the architecture rules out."),
    RESOURCE_LIMIT(Category.RESOURCE, Severity.ERROR,
            "A configured memory, size or count budget would be exceeded; no work was dropped silently."),

    // ---- command line --------------------------------------------------------------------------------------
    USAGE_UNKNOWN_COMMAND(Category.USAGE, Severity.ERROR, "The first argument is not a known command."),
    USAGE_UNKNOWN_OPTION(Category.USAGE, Severity.ERROR, "The option is not defined for this command."),
    USAGE_MISSING_OPTION(Category.USAGE, Severity.ERROR, "A required option was not given."),
    USAGE_MISSING_VALUE(Category.USAGE, Severity.ERROR, "An option that takes a value was last or followed by another option."),
    USAGE_INVALID_VALUE(Category.USAGE, Severity.ERROR, "An option value does not have the required form."),
    USAGE_DUPLICATE_OPTION(Category.USAGE, Severity.ERROR, "A non-repeatable option was given more than once."),
    USAGE_UNEXPECTED_ARGUMENT(Category.USAGE, Severity.ERROR, "A positional argument was given where none is accepted."),

    // ---- configuration -------------------------------------------------------------------------------------
    CONFIG_UNREADABLE(Category.CONFIG, Severity.ERROR, "The configuration file cannot be opened or decoded as UTF-8."),
    CONFIG_MALFORMED(Category.CONFIG, Severity.ERROR, "A configuration line is not valid properties syntax."),
    CONFIG_DUPLICATE_KEY(Category.CONFIG, Severity.ERROR, "A key is defined twice in one source; there is no silent last-wins."),
    CONFIG_UNKNOWN_KEY(Category.CONFIG, Severity.ERROR, "A cf.* key is not defined by this build."),
    CONFIG_INVALID_VALUE(Category.CONFIG, Severity.ERROR, "A value cannot be parsed or is outside its allowed range."),
    CONFIG_INCONSISTENT(Category.CONFIG, Severity.ERROR, "Individually valid values contradict each other."),
    CONFIG_LEGACY_MAPPED(Category.CONFIG, Severity.INFO, "A legacy key was recognised and mapped to a target key."),
    CONFIG_LEGACY_RETIRED(Category.CONFIG, Severity.WARNING,
            "A legacy key belongs to a mechanism the architecture removed; it is deliberately not acted on."),
    CONFIG_LEGACY_UNSUPPORTED(Category.CONFIG, Severity.ERROR,
            "A legacy key requests behaviour the architecture rules out (database, process, sandbox)."),
    CONFIG_LEGACY_UNCLASSIFIED(Category.CONFIG, Severity.ERROR,
            "A legacy key has no disposition for where it was found (not in the legacy settings catalog, or not a "
                    + "properties key there); it is refused, never passed through or dropped."),
    CONFIG_LEGACY_PENDING(Category.CONFIG, Severity.WARNING,
            "A legacy key requests retained behaviour that a later work package implements; a run refuses it until then."),
    CONFIG_LEGACY_COERCED(Category.CONFIG, Severity.WARNING,
            "Under the legacy parsing rules a value means something other than it says (for example 'yes' read as "
                    + "false); the legacy meaning is imported."),
    CONFIG_SUT_UNKNOWN(Category.CONFIG, Severity.ERROR,
            "cf.execution.sut names no SUT plugin found on the application class path or on cf.execution.classpath, "
                    + "and is not generated-java."),
    CONFIG_SUT_AMBIGUOUS(Category.CONFIG, Severity.ERROR,
            "Two SUT plugins on the class path have the id cf.execution.sut names; neither is chosen silently."),

    // ---- key encoding --------------------------------------------------------------------------------------
    KEY_LABEL_OUT_OF_RANGE(Category.KEY, Severity.ERROR, "A key label does not fit the selected encoding."),
    KEY_LABEL_RESERVED(Category.KEY, Severity.ERROR, "A key label equals the encoding's reserved sentinel value."),
    KEY_ENCODING_MISMATCH(Category.KEY, Severity.ERROR, "Rows of different key encodings were combined."),
    KEY_CENSUS_OVERFLOW(Category.KEY, Severity.ERROR, "The plan's labels do not fit the requested encoding policy."),
    KEY_WINDOW_SHIFTED(Category.KEY, Severity.INFO,
            "cf.keys.logical.labels=legacy-window numbered the values in the legacy cheapest window because S+C exceeds "
                    + "32767, as the legacy identity strategy did."),
    KEY_CENSUS_MISMATCH(Category.KEY, Severity.ERROR, "The parse consumed a different number of keys than the census counted."),
    KEY_PARSE_INVALID(Category.KEY, Severity.ERROR, "Text is not a well-formed key list such as [1, -2, 3]."),
    KEY_WIRE_MALFORMED(Category.KEY, Severity.ERROR, "A key frame header carries impossible values."),
    KEY_WIRE_TRUNCATED(Category.KEY, Severity.ERROR, "A key frame ends before its header or declared payload does."),
    KEY_WIRE_TRUNCATED_KEY(Category.KEY, Severity.ERROR, "A key frame payload ends in the middle of a key."),
    KEY_WIRE_COUNT_MISMATCH(Category.KEY, Severity.ERROR, "A key frame's declared key count disagrees with its payload byte count."),
    KEY_WIRE_VERSION_UNSUPPORTED(Category.KEY, Severity.ERROR, "A key frame has a schema version this build does not read."),
    KEY_WIRE_ENCODING_UNSUPPORTED(Category.KEY, Severity.ERROR, "A key frame names an encoding this build does not know."),
    KEY_WIRE_TRAILING_BYTES(Category.KEY, Severity.ERROR, "Bytes follow the end of a key frame that should have been consumed exactly."),
    KEY_WRAPAROUND_INPUT(Category.KEY, Severity.ERROR,
            "A legacy input only worked because a counter wrapped around; there is no equivalence promise for it."),

    // ---- scenario input files ------------------------------------------------------------------------------
    INPUT_UNREADABLE(Category.INPUT, Severity.ERROR, "The input file or stream cannot be opened or read."),
    INPUT_UNRECOGNIZED_FORMAT(Category.INPUT, Severity.ERROR,
            "The input's format cannot be told from its file name; there is no silent fallback to another format."),
    INPUT_MALFORMED(Category.INPUT, Severity.ERROR, "The input is not well formed in its own format."),
    INPUT_SCHEMA(Category.INPUT, Severity.ERROR, "The input is well formed but does not have the shape of a schedule."),
    INPUT_UNSUPPORTED_SYNTAX(Category.INPUT, Severity.ERROR,
            "The input uses YAML or TOML syntax outside the supported schedule subset."),
    INPUT_DUPLICATE_SHEET(Category.INPUT, Severity.ERROR, "Two sheets share a name, ignoring case."),
    INPUT_LIMIT(Category.INPUT, Severity.ERROR, "The input exceeds a format limit or a configured size bound."),
    INPUT_VERSION_UNSUPPORTED(Category.INPUT, Severity.ERROR,
            "The document declares a protocol or format version this build does not read; nothing of it is used (fail "
                    + "closed)."),
    INPUT_AUTHORING_WARNING(Category.INPUT, Severity.WARNING,
            "An fwgen spec has something the legacy compiler's compatibility mode warned about and went on with: an "
                    + "unknown field is ignored, or a slot will not produce what its author probably meant."),

    // ---- scenario content (what the cells and control sheets say, after reading) ---------------------------
    SPEC_UNKNOWN_DIRECTIVE(Category.SPEC, Severity.ERROR,
            "A data cell starts with FW_ but is not a cell directive this framework knows."),
    SPEC_DIRECTIVE_SYNTAX(Category.SPEC, Severity.ERROR, "A cell directive has a missing or malformed argument."),
    SPEC_DIRECTIVE_WITHOUT_TARGET(Category.SPEC, Severity.ERROR,
            "FW_Optional or FW_RefineCodeExceptQuoted has no earlier value in its own sheet to apply to."),
    SPEC_CONTROL_ROW(Category.SPEC, Severity.ERROR,
            "A row of a control sheet is incomplete or holds a value of the wrong kind."),
    SPEC_CONTROL_SHEET_UNKNOWN(Category.SPEC, Severity.WARNING,
            "A sheet named FW_... is not a control sheet this framework knows; it is ignored, as the legacy parser did."),
    SPEC_FORMULA_AS_TEXT(Category.SPEC, Severity.WARNING,
            "A formula cell is kept as its formula text and is not evaluated."),
    SPEC_RUNMEFIRSTONCE_SHAPE(Category.SPEC, Severity.WARNING,
            "The FW_RunMeFirstOnce cell is not a class RunMeFirstOnce with a public static String FW_ARGS."),
    SPEC_DUPLICATE_VALUE(Category.SPEC, Severity.INFO,
            "A sheet repeats a value; duplicates stay distinct keys, as the legacy parser kept them."),

    // ---- plan (the FW_Seq sheet: flags, directives, references) --------------------------------------------
    PLAN_NO_SEQUENCE(Category.PLAN, Severity.ERROR,
            "The scenario has no FW_Seq sheet, or it holds no row with content; there is nothing to generate."),
    PLAN_UNKNOWN_SHEET(Category.PLAN, Severity.ERROR,
            "A FW_Seq row or directive refers to a sheet that does not exist and was not generated."),
    PLAN_UNKNOWN_DIRECTIVE(Category.PLAN, Severity.ERROR,
            "A FW_Seq cell starts with FW_ but is not a flag or directive this framework knows."),
    PLAN_DIRECTIVE_SYNTAX(Category.PLAN, Severity.ERROR, "A FW_Seq directive does not match its grammar."),
    PLAN_CYCLE(Category.PLAN, Severity.ERROR,
            "Joiners wait for each other's tables in a circle, so no order of evaluation exists."),
    PLAN_ROW_OVERWRITTEN(Category.PLAN, Severity.INFO,
            "A later FW_Seq row for the same sheet replaces the directives of an earlier one; the flags of the earlier row stay "
                    + "(the documented overwrite rule, which real scenarios use routinely)."),
    PLAN_AMBIGUOUS_TARGET(Category.PLAN, Severity.WARNING,
            "A FW_Seq row names no sheet of its own and takes over the target of an earlier row, as the legacy parser did."),
    PLAN_IGNORED_CELL(Category.PLAN, Severity.WARNING,
            "A FW_Seq cell is neither a sheet name nor an FW_ token and is ignored."),
    PLAN_VIRTUAL_SHEET(Category.PLAN, Severity.WARNING,
            "A reference named no sheet, so an empty virtual sheet was generated for it, as the legacy parser did."),
    PLAN_CONFLICTING_FLAGS(Category.PLAN, Severity.WARNING,
            "A sheet carries flags that cannot both apply; only the first one counts, as in the legacy parser."),

    // ---- generation ----------------------------------------------------------------------------------------
    GEN_EMPTY_TABLE(Category.GENERATION, Severity.WARNING,
            "A sheet produced no rows. A mandatory sheet without rows leaves no candidates under the default empty-stage policy; "
                    + "the legacy Core dropped such a sheet instead."),
    GEN_NO_SOURCE_ROWS(Category.GENERATION, Severity.WARNING,
            "A later pass found no rows to work on because no earlier directive of the sheet generated any."),
    GEN_UNPARSEABLE_ROW(Category.GENERATION, Severity.WARNING,
            "A grouped row rewritten by FW_ReplaceRE is no longer a list of key labels and is dropped (legacy policy warn); "
                    + "the diagnostic counts the dropped rows and names the first one."),
    GEN_REPLACEMENT_INVALID(Category.GENERATION, Severity.ERROR,
            "A FW_ReplaceRE replacement cannot be applied to a row's key text (for example a reference to a missing group)."),
    GEN_REPLACEMENT_UNRESOLVED(Category.GENERATION, Severity.WARNING,
            "A joined FW_ReplaceRE names a sheet without values; the legacy Core left that replacement out."),
    GEN_OPERAND_CONSUMED(Category.GENERATION, Severity.WARNING,
            "A joiner reads a table that an earlier joiner consumed (no FW_Reuse), so it reads no rows."),
    GEN_REPLACEMENT_PATTERN(Category.GENERATION, Severity.WARNING,
            "A FW_ReplaceRE pattern needs text that a key-label list never contains, so it can never match "
                    + "(cf.generation.replace.pattern warn; strict refuses the plan)."),
    GEN_REPLACEMENT_UNMATCHED(Category.GENERATION, Severity.WARNING,
            "A FW_ReplaceRE pattern changed no row of its sheet (cf.generation.replace.unmatched warn; fail stops the run)."),
    GEN_REPLACEMENT_IDENTITY(Category.GENERATION, Severity.WARNING,
            "A FW_ReplaceRE pattern equals its replacement: it changes nothing but the plan's identity "
                    + "(cf.generation.replace.identity warn)."),
    GEN_REPLACEMENT_SUMMARY(Category.GENERATION, Severity.INFO,
            "Per grouped sheet: rows seen, rewritten and dropped, and the rows each FW_ReplaceRE pattern changed "
                    + "(cf.generation.replace.diagnostics summary)."),

    // ---- constraints (work package M03) --------------------------------------------------------------------
    CONSTRAINT_UNREADABLE(Category.CONSTRAINT, Severity.ERROR,
            "A constraint sidecar or legacy bonds file is missing, unreadable, not UTF-8 or not in its format; nothing of it is applied."),
    CONSTRAINT_INVALID(Category.CONSTRAINT, Severity.ERROR,
            "A constraint sidecar entry has an invalid shape, type or key; an authored rule is never dropped silently."),
    CONSTRAINT_UNKNOWN_SHEET(Category.CONSTRAINT, Severity.ERROR, "A constraint, parameter or order names a sheet the scenario does not have."),
    CONSTRAINT_UNMATERIALIZED_SHEET(Category.CONSTRAINT, Severity.ERROR,
            "A constraint names a sheet that is no column of the candidates (excluded, heading, consumed, empty and dropped, "
                    + "or without directives), so it could never fire."),
    CONSTRAINT_EXPRESSION(Category.CONSTRAINT, Severity.ERROR,
            "A 'when' predicate is outside the supported expression grammar or uses a name it may not use."),
    CONSTRAINT_AMBIGUOUS(Category.CONSTRAINT, Severity.ERROR,
            "A scenario has a companion constraint sidecar and a different constraint file is configured."),
    CONSTRAINT_LOADED(Category.CONSTRAINT, Severity.INFO,
            "A scenario's companion constraint sidecar (<stem>.constraints.json beside it) was read, as the legacy loader read it."),
    CONSTRAINT_INERT(Category.CONSTRAINT, Severity.WARNING,
            "A rule or part of one can never match: a value no cell of its sheet has, or a pairs entry without every sheet."),

    // ---- candidate assembly (work package M03) ------------------------------------------------------------
    ASSEMBLY_EMPTY_FACTOR(Category.ASSEMBLY, Severity.WARNING,
            "A mandatory sheet has no rows, so no candidate exists under the default empty-stage policy; zero candidates "
                    + "are executable and nothing passed."),
    ASSEMBLY_SHEET_DROPPED(Category.ASSEMBLY, Severity.WARNING,
            "cf.generation.emptyStagePolicy=LEGACY_DROP_WITH_DIAGNOSTIC dropped a mandatory sheet without rows from the "
                    + "candidates, as the legacy Core did; the candidates no longer contain that column."),
    ASSEMBLY_BASELINE_UNAVAILABLE(Category.ASSEMBLY, Severity.WARNING,
            "A mandatory sheet has no first row, so a baseline reference to it cannot be resolved; no value is invented."),
    ASSEMBLY_TRUNCATED(Category.ASSEMBLY, Severity.WARNING,
            "cf.assembly.mandatoryLimit kept only the first rows of the mandatory product or of an optional subset's product."),
    ASSEMBLY_SUBSET_SKIPPED(Category.ASSEMBLY, Severity.WARNING,
            "An optional subset whose product of rows exceeds cf.optional.subsetRowLimit was left out, as the legacy Core did."),
    ASSEMBLY_SIZE_UNUSED(Category.ASSEMBLY, Severity.WARNING,
            "An optional combination size is larger than the number of optional sheets, so it forms no combination."),
    ASSEMBLY_RULE_ERROR(Category.ASSEMBLY, Severity.ERROR,
            "A rule could not be evaluated on a candidate (a type error, a value outside a declared order); the run stops."),
    ASSEMBLY_COVERAGE_REFUSED(Category.ASSEMBLY, Severity.ERROR,
            "Covering selection cannot run on this plan (product above cf.coverage.hardCap, no mandatory sheet to cover)."),
    ASSEMBLY_COVERAGE_INFEASIBLE(Category.ASSEMBLY, Severity.INFO,
            "Some t-tuples of the mandatory sheets' rows appear in no row the rules allow; they are reported as infeasible, "
                    + "not as uncovered."),
    ASSEMBLY_RENDER_FAILED(Category.ASSEMBLY, Severity.ERROR,
            "A candidate's source could not be rendered (a value too complex to refine, a label without a value)."),

    // ---- execution (sample failure codes and run-level execution errors) -----------------------------------
    EXEC_COMPILE_ERROR(Category.EXECUTION, Severity.WARNING,
            "The candidate's generated Java source does not compile; the sample is BROKEN and the compiler's messages "
                    + "are kept with it."),
    EXEC_COMPILER_UNAVAILABLE(Category.EXECUTION, Severity.ERROR,
            "This Java runtime has no system Java compiler (javax.tools), so generated sources cannot be compiled; the "
                    + "samples are INFRA_FAIL. Run on a JDK, not a bare JRE image."),
    EXEC_NO_ENTRY_POINT(Category.EXECUTION, Severity.WARNING,
            "The generated class has no public static void main(String[]) to run; the sample is BROKEN."),
    EXEC_NO_VERDICT(Category.EXECUTION, Severity.WARNING,
            "The sample produced no verdict: the plugin returned none, or the generated class has no static int field "
                    + "FW_VAR or FW_CUSTOM_VAR (the one cf.execution.verdict selects); the sample is BROKEN."),
    EXEC_SUT_EXCEPTION(Category.EXECUTION, Severity.WARNING,
            "The system under test threw; the sample is BROKEN, never a domain failure, unless the plugin itself turned "
                    + "the exception into a domain observation."),
    EXEC_FORBIDDEN_CALL(Category.EXECUTION, Severity.WARNING,
            "The generated class calls System.exit, Runtime.exit or Runtime.halt, which would stop the whole virtual "
                    + "machine; it is not run and the sample is BROKEN."),
    EXEC_ORACLE_REFUSED(Category.EXECUTION, Severity.WARNING,
            "The plugin's oracle judged the observation unusable (BROKEN or an outcome an oracle may not give)."),
    EXEC_DEADLINE(Category.EXECUTION, Severity.WARNING,
            "The cooperative deadline cf.execution.timeout.millis passed; the sample was asked to stop and is TIMEOUT."),
    EXEC_UNCOOPERATIVE(Category.EXECUTION, Severity.WARNING,
            "The sample did not stop within cf.execution.cancel.grace.millis after its deadline. Its thread was "
                    + "interrupted and left running: one JVM cannot stop it. The sample is TIMEOUT; the run reports the thread."),
    EXEC_SESSION_FAILED(Category.EXECUTION, Severity.WARNING,
            "The SUT plugin could not open a session for the sample; the sample is INFRA_FAIL."),
    EXEC_SESSION_CLOSE_FAILED(Category.EXECUTION, Severity.WARNING,
            "A SUT session threw while closing; the sample keeps its outcome and the run reports the failure."),
    EXEC_OUT_OF_MEMORY(Category.EXECUTION, Severity.WARNING,
            "The virtual machine ran out of memory while the sample ran; the sample is INFRA_FAIL."),
    EXEC_NOT_ATTEMPTED(Category.EXECUTION, Severity.INFO,
            "The sample was deliberately not attempted (a stop policy or an earlier failure ended the run first); it "
                    + "is SKIPPED."),
    EXEC_CANCELLED(Category.EXECUTION, Severity.INFO,
            "The run was cancelled before or while the sample ran; the sample is CANCELLED."),
    EXEC_PREPARE_FAILED(Category.EXECUTION, Severity.ERROR,
            "FW_RunMeFirstOnce did not compile, run or leave a String FW_ARGS, so no candidate can run; the legacy "
                    + "executor stopped too."),
    EXEC_ABANDONED_THREADS(Category.EXECUTION, Severity.WARNING,
            "Some samples ignored their deadline and their threads were still running when the run ended; they were "
                    + "not stopped, because one JVM cannot stop them."),
    EXEC_COVERAGE_OMITTED(Category.EXECUTION, Severity.WARNING,
            "cf.execution.coverage=lines could not measure some samples (the candidate has no executable line of its own, or "
                    + "its instrumented bytecode could not be built); their coverage metrics are omitted, never invented."),

    // ---- results -------------------------------------------------------------------------------------------
    RESULT_CONFLICT(Category.RESULT, Severity.ERROR,
            "A second observation for an existing sample key differs from the committed one; the committed one stays "
                    + "and the conflict is an error, never an overwrite."),
    RESULT_LISTENER_FAILED(Category.RESULT, Severity.WARNING,
            "A listener kept failing on a committed observation: it was offered the same observation again, with its "
                    + "sample key, then skipped. Counters come from the committed results, never from deliveries."),
    RESULT_ATTACHMENT_OMITTED(Category.RESULT, Severity.WARNING,
            "Attachments did not fit cf.results.maxAttachmentBytes and were not kept; the observations they belong to "
                    + "are unchanged and the omissions are counted."),
    RESULT_EXPORT_FAILED(Category.RESULT, Severity.ERROR,
            "Writing an optional report or export failed; the committed observations and their verdicts are unchanged."),
    CHECKPOINT_WRITE_FAILED(Category.RESULT, Severity.ERROR,
            "Writing a checkpoint failed; the last good checkpoint was kept and the committed observations are unchanged."),

    // ---- analysis ------------------------------------------------------------------------------------------
    ANALYSIS_INVALID_GOAL(Category.ANALYSIS, Severity.ERROR,
            "An analysis goal is malformed: its key, its mode (min, max or target), its target or its weight (finite, at "
                    + "least 0)."),
    ANALYSIS_INVALID_EXPRESSION(Category.ANALYSIS, Severity.ERROR,
            "An objective expression does not parse or names an unknown variable or function; nothing is evaluated."),
    ANALYSIS_NONFINITE(Category.ANALYSIS, Severity.ERROR,
            "An objective or metric value is NaN or infinite; it is refused, never coerced into a number."),
    ANALYSIS_FORMAL_INCOMPLETE(Category.ANALYSIS, Severity.ERROR,
            "Formal analysis needs declared goals and at least one eligible sample; it does not discover axes."),
    ANALYSIS_FINDINGS(Category.ANALYSIS, Severity.ERROR,
            "Triage was asked to fail on findings and the run has at least one distinct finding; the report was still written."),

    // ---- campaigns -----------------------------------------------------------------------------------------
    CAMPAIGN_SEED_UNJOINABLE(Category.CAMPAIGN, Severity.ERROR,
            "A seed cannot drive a bias: none of its winners carries sheet selections (a legacy seed was joined to database "
                    + "rows, which this build does not keep), so nothing was run."),
    CAMPAIGN_NO_WINNERS(Category.CAMPAIGN, Severity.ERROR,
            "An iteration produced no seed with front winners (no analysis, or no eligible sample with metrics), so the "
                    + "next iteration cannot be derived; the iterations before it stand."),
    CAMPAIGN_NO_SIGNAL(Category.CAMPAIGN, Severity.WARNING,
            "The seed carried no narrowing signal (CONVERGED_NO_SIGNAL): the iteration ran without bias and the campaign "
                    + "stopped after it, as the legacy iterate loop did."),
    CAMPAIGN_EXHAUSTED(Category.CAMPAIGN, Severity.WARNING,
            "The next iteration would have no candidate to run (the bias would keep fewer than one, or no candidate was "
                    + "executable); the campaign stopped and the iterations before it stand."),

    // ---- checkpoints ---------------------------------------------------------------------------------------
    CHECKPOINT_CORRUPT(Category.CHECKPOINT, Severity.ERROR,
            "A checkpoint is unreadable, truncated, of an unknown schema or fails its payload checksum; nothing was resumed."),
    CHECKPOINT_MISMATCH(Category.CHECKPOINT, Severity.ERROR,
            "A checkpoint does not belong to what would be resumed: scenario, plan, dictionary, policy, plugin or "
                    + "configuration differ; nothing was resumed."),

    // ---- run lifecycle -------------------------------------------------------------------------------------
    RUN_CANCELLED(Category.RUN, Severity.ERROR,
            "The work was cancelled cooperatively before it finished; nothing it had not completed was published."),
    RUN_BUDGET_EXHAUSTED(Category.RUN, Severity.WARNING,
            "The run's time budget (cf.run.budget.millis) passed: admission stopped and running samples were cancelled "
                    + "cooperatively; what was committed before stays."),
    RUN_RECONCILIATION_FAILED(Category.RUN, Severity.ERROR,
            "A run's totals do not reconcile: a stage invariant failed, which is a defect of the framework, never a property of "
                    + "the scenario; the committed observations are kept."),
    RUN_STOPPED(Category.RUN, Severity.INFO,
            "The run's stop policy ended admission (cf.run.stop.candidates or cf.run.stop.target); the candidates admitted "
                    + "before the decision finished, and the decision does not depend on workers or timing."),
    RUN_ERROR_BUDGET_EXHAUSTED(Category.RUN, Severity.ERROR,
            "The run's error budget (cf.run.stop.errors) was used up by BROKEN, TIMEOUT or INFRA_FAIL verdict samples: admission "
                    + "ended and the command fails; what was committed stays."),
    RUN_ENVIRONMENT_BLOCKED(Category.RUN, Severity.ERROR,
            "The doctor found a blocking check: a run would fail on this environment as configured; the report names it.");

    /** Coarse grouping; later work packages append categories (EXEC, ...). */
    public enum Category {
        INTERNAL,
        CAPABILITY,
        RESOURCE,
        USAGE,
        CONFIG,
        KEY,
        INPUT,
        SPEC,
        PLAN,
        GENERATION,
        CONSTRAINT,
        ASSEMBLY,
        EXECUTION,
        RESULT,
        ANALYSIS,
        CAMPAIGN,
        CHECKPOINT,
        RUN
    }

    private final Category category;
    private final Severity defaultSeverity;
    private final String description;

    ErrorCode(Category category, Severity defaultSeverity, String description) {
        this.category = Objects.requireNonNull(category);
        this.defaultSeverity = Objects.requireNonNull(defaultSeverity);
        this.description = Objects.requireNonNull(description);
    }

    public Category category() {
        return category;
    }

    /** The severity this code normally carries; a {@link Diagnostic} may override it (strict vs lenient import). */
    public Severity defaultSeverity() {
        return defaultSeverity;
    }

    public String description() {
        return description;
    }
}
