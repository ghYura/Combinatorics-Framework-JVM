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

package org.ghyura.cf.spi;

import java.util.Map;

/**
 * A system under test packaged as a typed Java plugin (architecture D06), found with {@link java.util.ServiceLoader}
 * on the application class path or on {@code cf.execution.classpath}; {@code cf.execution.sut} names it by {@link #id()}.
 *
 * <p>The contract is trusted, cooperative Java (D07): a plugin polls {@link ExecutionContext#cancellation()} in every long
 * loop and never terminates the virtual machine. Nothing here isolates or forcibly stops plugin code.</p>
 */
public interface SutPlugin {

    /** The plugin's stable name: 1..64 characters of {@code [A-Za-z0-9._-]}. */
    String id();

    /** The plugin's version; it is part of every observation's policy digest. */
    String version();

    /** Names of the domain verdict codes the plugin reports, such as 20 = ORDER_VIOLATION; empty when it names none. */
    default Map<Integer, String> verdictNames() {
        return Map.of();
    }

    /** How a domain observation becomes PASS or DOMAIN_FAIL; by default its verdict code (0 passes). */
    default Oracle oracle() {
        return Oracle.VERDICT_CODE;
    }

    /**
     * Whether the sample's standard output carries the legacy metrics line ({@code app=... FW_VAR=...}) whose
     * {@code key=value} tokens become the observation's metrics, as for generated candidates; a typed plugin reports
     * its metrics itself, so by default no.
     */
    default boolean harvestsMetricLines() {
        return false;
    }

    /**
     * Whether a candidate's rendered source is itself what runs (the Python add-on writes it to a file and runs it): then
     * the results record the SHA-256 of that source as its source hash, as the legacy executors recorded the candidate
     * file's. A typed plugin runs its own code on the selections, so by default its identity digest is recorded instead.
     */
    default boolean runsCandidateSource() {
        return false;
    }

    /**
     * The plugin that runs this run's samples, told the run's inputs once before the first sample. By default the plugin
     * itself: a typed Java plugin needs nothing more. A plugin that runs rendered sources (the Python add-on) returns one
     * that holds the inputs; a failure here stops the run before any sample.
     */
    default SutPlugin forRun(RunInputs inputs) throws Exception {
        return this;
    }

    /**
     * A fresh session for one sample: one repeat of one candidate in one environment. Sessions are never shared
     * between samples, so no state survives from one repeat to the next unless the plugin keeps it on purpose.
     */
    SutSession open(ExecutionContext context) throws Exception;
}
