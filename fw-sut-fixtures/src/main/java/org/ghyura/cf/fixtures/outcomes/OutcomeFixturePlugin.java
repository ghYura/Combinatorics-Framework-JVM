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

package org.ghyura.cf.fixtures.outcomes;

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.MemoryReservation;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SkipSample;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A plugin whose behaviour is the candidate's value of the sheet {@code behaviour}, to drive every execution outcome
 * ({@code cf.execution.sut=outcome-fixture}):
 *
 * <ul>
 *   <li>{@code pass}, {@code fail:N} (domain verdict N, any int), {@code print} (writes to System.out and the capture, passes);</li>
 *   <li>{@code none} (no observation), {@code throw} (an exception), {@code nonfinite} (a NaN metric), {@code oracle}
 *       (the oracle refuses verdict 99);</li>
 *   <li>{@code sleep} (polls its cancellation and stops when asked), {@code spin:MS} (ignores cancellation and interrupts for
 *       MS milliseconds, then returns a pass that must be ignored);</li>
 *   <li>{@code reserve} (asks for more memory than any budget), {@code open-fail} (the session cannot open),
 *       {@code skip} (declines to run), {@code flaky} (INFRA_FAIL on the first attempt of a sample, then a pass).</li>
 * </ul>
 */
public final class OutcomeFixturePlugin implements SutPlugin {

    public static final String ID = "outcome-fixture";

    private final Map<String, AtomicInteger> flakyAttempts = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return "1";
    }

    @Override
    public Map<Integer, String> verdictNames() {
        return Map.of(0, "OK", 7, "WRONG", 300, "CUSTOM_ABOVE_127");
    }

    @Override
    public org.ghyura.cf.spi.Oracle oracle() {
        return (candidate, observation) -> observation.verdictCode() == 99 ? org.ghyura.cf.model.Outcome.BROKEN
                : org.ghyura.cf.spi.Oracle.VERDICT_CODE.classify(candidate, observation);
    }

    @Override
    public SutSession open(ExecutionContext context) throws Exception {
        return new SutSession() {
            @Override
            public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) throws Exception {
                String behaviour = candidate.selection("behaviour").map(SheetSelection::values)
                        .filter(v -> !v.isEmpty()).map(v -> v.getFirst()).orElse("pass");
                String verb = behaviour.contains(":") ? behaviour.substring(0, behaviour.indexOf(':')) : behaviour;
                String arg = behaviour.contains(":") ? behaviour.substring(behaviour.indexOf(':') + 1) : "";
                switch (verb) {
                    case "pass":
                        return DomainObservation.of(0);
                    case "fail":
                        return DomainObservation.of(Integer.parseInt(arg));
                    case "print":
                        System.out.println("printed by " + candidate.displayId());
                        ctx.output().stderr("to the capture");
                        return DomainObservation.of(0);
                    case "none":
                        return null;
                    case "throw":
                        throw new IllegalStateException("the fixture fails on purpose");
                    case "nonfinite":
                        return new DomainObservation(0, Map.of("latency", Double.NaN), Map.of());
                    case "oracle":
                        return DomainObservation.of(99);
                    case "sleep":
                        while (!ctx.cancellation().isCancelled()) {
                            try {
                                Thread.sleep(5);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                        ctx.cancellation().throwIfCancelled();
                        return DomainObservation.of(0);
                    case "spin": {
                        long end = System.nanoTime() + Long.parseLong(arg) * 1_000_000L;
                        long sink = 0;
                        while (System.nanoTime() < end) {
                            sink += System.nanoTime() & 1;
                        }
                        return DomainObservation.of(sink >= 0 ? 0 : 1);
                    }
                    case "reserve":
                        try (MemoryReservation r = ctx.reserve(Long.MAX_VALUE / 2, "fixture")) {
                            return DomainObservation.of(r.bytes() > 0 ? 0 : 1);
                        }
                    case "skip":
                        throw new SkipSample("the fixture's environment lacks what this candidate needs");
                    case "flaky": {
                        String slot = ctx.sample().candidateId().hex() + "/" + ctx.sample().repeatIndex();
                        if (flakyAttempts.computeIfAbsent(slot, s -> new AtomicInteger()).getAndIncrement() == 0) {
                            ctx.reserve(Long.MAX_VALUE / 2, "flaky first attempt");
                        }
                        return DomainObservation.of(0);
                    }
                    default:
                        throw new IllegalArgumentException("unknown fixture behaviour '" + behaviour + "'");
                }
            }

            @Override
            public void close() {
            }
        };
    }

    /** A plugin whose sessions cannot open (behaviour open-fail, as a plugin of its own). */
    public static final class Unopenable implements SutPlugin {
        public static final String ID = "outcome-fixture-unopenable";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public String version() {
            return "1";
        }

        @Override
        public SutSession open(ExecutionContext context) throws Exception {
            throw new java.io.IOException("the fixture's service is not reachable");
        }
    }
}
