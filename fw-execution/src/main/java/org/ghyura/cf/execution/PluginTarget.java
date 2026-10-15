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

package org.ghyura.cf.execution;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.Oracle;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A typed SUT plugin (architecture D06). Every attempt opens a fresh session and closes it afterwards, so repeats never
 * share session state; the plugin's oracle judges each observation.
 */
public final class PluginTarget implements SutTarget {

    private final SutPlugin plugin;
    private final String digest;
    private final AtomicLong closeFailures = new AtomicLong();

    public PluginTarget(SutPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.digest = Canonical.sha256Hex("plugin/1\n" + Canonical.escape(plugin.id()) + "\n" + Canonical.escape(plugin.version()) + "\n");
    }

    @Override
    public String id() {
        return plugin.id();
    }

    @Override
    public String version() {
        return plugin.version();
    }

    @Override
    public Map<Integer, String> verdictNames() {
        return Map.copyOf(plugin.verdictNames());
    }

    @Override
    public String sourceDigest(SutCandidate candidate) {
        return plugin.runsCandidateSource() ? Canonical.sha256Hex(candidate.source().getBytes(StandardCharsets.UTF_8)) : digest;
    }

    @Override
    public boolean harvestsMetricLines() {
        return plugin.harvestsMetricLines();
    }

    /** Sessions that threw while closing. */
    public long closeFailures() {
        return closeFailures.get();
    }

    @Override
    public AttemptResult attempt(SutCandidate candidate, Prepared prepared, SampleContext context) {
        SutSession session;
        try {
            session = plugin.open(context);
        } catch (Throwable t) {
            AttemptResult.Failure f = Failures.classify(t);
            if (f.outcome() == Outcome.SKIPPED || f.code() == ErrorCode.RESOURCE_LIMIT) {
                return f;
            }
            return new AttemptResult.Failure(Outcome.INFRA_FAIL, ErrorCode.EXEC_SESSION_FAILED, "the plugin " + plugin.id()
                    + " could not open a session: " + Failures.describe(Failures.unwrap(t)));
        }
        if (session == null) {
            return new AttemptResult.Failure(Outcome.INFRA_FAIL, ErrorCode.EXEC_SESSION_FAILED, "the plugin " + plugin.id()
                    + " returned no session");
        }
        try {
            DomainObservation observation = session.evaluate(candidate, context);
            if (observation == null) {
                return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_NO_VERDICT, "the plugin " + plugin.id()
                        + " returned no observation");
            }
            return judge(plugin.oracle(), candidate, observation);
        } catch (Throwable t) {
            return Failures.classify(t);
        } finally {
            try {
                session.close();
            } catch (Throwable t) {
                closeFailures.incrementAndGet();
            }
        }
    }

    static AttemptResult judge(Oracle oracle, SutCandidate candidate, DomainObservation observation) {
        Outcome outcome;
        try {
            outcome = oracle.classify(candidate, observation);
        } catch (Throwable t) {
            return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_ORACLE_REFUSED, "the oracle failed on verdict "
                    + observation.verdictCode() + ": " + Failures.describe(Failures.unwrap(t)));
        }
        if (outcome == Outcome.PASS || outcome == Outcome.DOMAIN_FAIL) {
            return new AttemptResult.Verdict(observation, outcome);
        }
        return new AttemptResult.Failure(Outcome.BROKEN, ErrorCode.EXEC_ORACLE_REFUSED, "the oracle answered " + outcome
                + " for verdict " + observation.verdictCode() + "; only PASS and DOMAIN_FAIL judge an observation");
    }

    @Override
    public void close() {
        // a plugin holds no run state of its own here; sessions are closed per attempt
    }
}
