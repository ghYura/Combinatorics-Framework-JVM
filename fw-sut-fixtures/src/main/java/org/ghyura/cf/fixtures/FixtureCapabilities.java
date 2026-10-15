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

package org.ghyura.cf.fixtures;

import org.ghyura.cf.spi.AddonCapabilities;

import java.util.List;

/**
 * This addon's statement about the capability mapping, reported only while the addon is installed on
 * {@code cf.execution.classpath}: C13 is partial, as bounded deterministic ports of the legacy SUT pipelines.
 */
public final class FixtureCapabilities implements AddonCapabilities {

    static final String C13_EVIDENCE = "typed SUT plugins tutor (exact, gate A15), bundle-pricing, bundle-secure-pipeline, "
            + "billing-engine, order-fulfilment, telemetry-catalog and tester-pub, each against goldens of the unchanged originals "
            + "(TutorSutTest, BusinessSutTest, CatalogPubTest) and run through the packaged application with this addon installed "
            + "(PackagedApplicationIT). Not ported: the originals' complete tryout programs and the remaining SUT families.";

    @Override
    public String addon() {
        return "fw-sut-fixtures";
    }

    @Override
    public List<Statement> statements() {
        return List.of(new Statement("C13", "partial", C13_EVIDENCE),
                new Statement("C17", "implemented", "D7 reduction trees: unchanged original scenario, all 168 cases (156 PASS, 12 DOMAIN_FAIL). D2 stream boundaries: unchanged original scenario, 1152 raw candidates, 672 filtered and all 480 executed (332 PASS, 148 DOMAIN_FAIL). D8 ties/pagination: all 450 cases (330 PASS, 120 DOMAIN_FAIL), plus 150 supplemental tie-order proofs. D11 coalition value: all 64 coalitions PASS and exact allocation from observed values (192 weighted marginals, 720 permutations and 64 dividends). All have independent original predictions and serial/parallel/package checks. D4 configuration rules: original main (48 PASS, 18 DOMAIN_FAIL) and invalid controls (17 PASS, 1 DOMAIN_FAIL), exact phased sieve counts, 60-pair/nine-row proof and wrong-sieve masking certificate from observed values. D8b dependency DAGs: all 768 cases (604 PASS, 164 DOMAIN_FAIL), complete cache/parent-read/path-count traces and 64-graph topology proof (315 graph/order pairs, 31 redundant-edge additions). D9 robust portfolios: all 1024 evaluations PASS, full observed minimax/profile rankings, exact primary ties, 64 world summaries and eleven joint-mixture sensitivity rankings. D12 cyclic patterns: original 729-word and 130-class campaigns PASS, all directed ring/rotation fields, complete observed orbit/Burnside/reflection certificates, exact three-population histograms/means, 130-class ranking and population masses. D10 diagnostic sensors: all 3072 evaluations PASS, declared healthy control and measured alias classes, complete 256-suite/5376-distance analysis, all minimum-cost ties, 127-belief adaptive tree/paths and 49 erasure/56 error decoding controls from observed readings. D13a tool-call order: all 480 original traces (454 PASS, 26 DOMAIN_FAIL), fresh inert local read/send tools, independent guarded reference and policy-blind prefix obligations, complete observed axis/violation/request/effect/attempt counts and five witness timelines. D6 concurrency schedules: both original counter (96 raw, 72 excluded, 18 PASS/6 DOMAIN_FAIL) and queue (32 raw, 26 excluded, 5 PASS/1 DOMAIN_FAIL), every four-step local/reference trace and full live rule truth/overlap/sequential-survivor certificates, prefix completion versus context switches, reference enabledness and five observed witnesses. D13d session memory: unchanged 1152 raw/352 excluded/800 observed cases (718 PASS/82 DOMAIN_FAIL), both restricted-growth bonds with all raw truth/overlap/survivors, all reply/tool/memory/epoch/owner/reference fields and policy-blind isolation checks; complete observed strata/cross/leak counts, useful retention and five witnesses. D1 idempotency: unchanged 600 raw/480 excluded/120 observed cases (77 PASS/43 DOMAIN_FAIL), all delivery/ledger/receipt/peer/sensor fields, restart lifetime and full-payload cache semantics, independent operation-key oracle, all raw bond truths, recorded policy/control tables, B/E effect histograms and every 44-entry witness/contrast group. D13b composed carriers: unchanged 108 composed contexts (80 PASS/28 DOMAIN_FAIL), actual whole-pair grouping/rewrite/permutation and both braces, every tree/trace/decoded/rendered/action/effect field, independent policy-blind shape/source/content/useful-work oracle and recorded five axes/30 cross groups/promotion effects/14 benign controls/five witnesses. Original config distinctification and rewrite summaries retained. D13c agent message order: unchanged 54 cases (36 PASS/18 DOMAIN_FAIL), all 288 raw rule truths and 288/108/81/54 phase counts, independent enabledness/preemption exploration, distinct allocation and outstanding-promise ledgers, policy-blind useful reference and integrity-first four-prefix sensors, complete observed strata/denominators/mechanisms/five witnesses. D14c solver relations: unchanged144cases(72PASS/72DOMAIN_FAIL),six native goods permutations,432solver calls/8064independent exhaustive subset checks,three variants/four relations/all optimum ties/feasible counts/gaps,24input relabel/dominance proofs and complete recorded tallies/controls/five witnesses. D14a language processors: unchanged432cases(316PASS/116DOMAIN_FAIL),two native declaration orders,864original/transformed programs with all ASTs/bytecodes/9504VM transitions and independent reference/four-number oracle,three sensors/12strata/grouping/dead-code/declaration restoration/cancellation controls and five recorded witnesses. D13e context position: unchanged54cases(49PASS/5DOMAIN_FAIL),1080trials/2160paired judge readings/6480context steps,mechanical task oracle and exact unsigned64-bit shared noise thresholds,full recorded calibration/confusion/Wilson/strata/paired tables/mechanisms/five witnesses,offline120triple cover and separate4320case universe model. D14b SQL partitioning:72cases36PASS/36DOMAIN_FAIL,local immutable nullable relations/joins/288branch evaluations,independent policy-blind query-bag/union oracle,allSQL/row/multiplicity/check fields,Kleene tables/24families/strata/23set-masked failures/controls/five witnesses. Original PostgreSQL/container transaction/network provenance explicitly replaced by DB-less local semantics. D15 fault schedules:336raw/24native partition exclusions/312cases216PASS96DOMAIN_FAIL,three in-JVM replica/journal state machines,volatile loss/full double replay/fresh retry faults,seven policy-blind observed checks,all1062attempts/checkpoints/fullpayloads/canonicaljournals/local lifecycle,three useful controls/two all-node restart cases/allstrata/mechanisms/five witnesses. OS worker/SIGKILL/fsync/IPC/PID provenance explicitly replaced by local transitions. D3 phase C workflow coverage:1440native six-event permutations,1410PASS30DOMAIN_FAIL,six failures hidden by final-state-only checks;all per-event snapshots and accounting invariants,five independently checked coverage certificates and observed detection measurements. D3 A/B workflow order:72 A cases48PASS24FAIL/four hidden finals,81 B cases66PASS15FAIL;native optional restart/reconciliation tails,repeated-operation words and equivalent CombiR-to-identical-Permut B-alt chain,all531 checkpoints/four-field independent reference/five witnesses. B-alt originally planned-only,additionally executed in Java as equivalence proof. All D3 phases delivered;22 of22whole demos complete,D5 phase A delivered:72 cases32PASS40FAIL,original grouped operation/operation/field fragments,independent exact-record reference,all16fields/traces/four witnesses/eight benign reversed passes. D5 B1/B2 delivered:24 single-tree cases8PASS16FAIL and3 all-eight-tree bundles1PASS2FAIL. All27records/276nativefields/48fresh per-tree results/480trace steps,ordered native rewrites/subsets/ZIP/CAT/nested references and107-fragment bundle checked against independent original predictions. All D5 phases and all22demo families delivered;C17implemented with addon. Other application/domain/provider/inventory families remain outside this demo claim."));
    }
}
