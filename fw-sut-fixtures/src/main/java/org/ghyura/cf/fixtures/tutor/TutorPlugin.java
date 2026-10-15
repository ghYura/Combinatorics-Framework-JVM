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

package org.ghyura.cf.fixtures.tutor;

import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.spi.ExecutionContext;
import org.ghyura.cf.spi.SheetSelection;
import org.ghyura.cf.spi.SutCandidate;
import org.ghyura.cf.spi.SutPlugin;
import org.ghyura.cf.spi.SutSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The tutor as a typed SUT plugin ({@code cf.execution.sut=tutor}). A candidate's sheets are read by name: channel,
 * account and payment give one value each, steps gives the workflow order, and every present optional sheet named after
 * a stress action (duplicate_submit, carrier_timeout) adds that action. The tutor's verdict code is the domain verdict;
 * its outcome name and reason are attributes, its metrics are metrics.
 */
public final class TutorPlugin implements SutPlugin {

    public static final String ID = "tutor";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String version() {
        return "tutor_sut.py@8d5aa2d/java-1";
    }

    @Override
    public Map<Integer, String> verdictNames() {
        Map<Integer, String> names = new TreeMap<>();
        TutorSut.VERDICTS.forEach((name, code) -> names.put(code, name));
        return names;
    }

    @Override
    public SutSession open(ExecutionContext context) {
        return new SutSession() {
            @Override
            public DomainObservation evaluate(SutCandidate candidate, ExecutionContext ctx) {
                List<String> stress = new ArrayList<>();
                for (String action : TutorSut.STRESS_ACTIONS) {
                    candidate.selection(action).filter(SheetSelection::inCandidate).ifPresent(s -> stress.add(action));
                }
                TutorSut.Result r = TutorSut.evaluate(one(candidate, "channel"), one(candidate, "account"), one(candidate, "payment"),
                        candidate.selection("steps").map(SheetSelection::values).orElse(List.of()), stress);
                Map<String, String> attributes = new TreeMap<>();
                attributes.put("outcome", r.outcome());
                attributes.put("reason", r.reason());
                attributes.put("interaction", String.join(" ", r.interaction()));
                return new DomainObservation(r.verdictCode(), r.metrics(), attributes);
            }

            @Override
            public void close() {
            }
        };
    }

    private static String one(SutCandidate candidate, String sheet) {
        return candidate.selection(sheet).filter(SheetSelection::inCandidate).map(s -> s.values().isEmpty() ? "" : s.values().getFirst())
                .orElse("");
    }
}
