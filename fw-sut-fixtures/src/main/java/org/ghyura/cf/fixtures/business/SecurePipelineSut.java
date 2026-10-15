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

package org.ghyura.cf.fixtures.business;

import org.ghyura.cf.model.Canonical;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Domain port of bundle_secure_pipeline_tryout/app.py, including Unicode/order, stale-key and poisoned-cache faults. */
public final class SecurePipelineSut {
    private static final Pattern SECRET = Pattern.compile("SECRET-[A-Za-z0-9]+");
    private final Map<String, State> states = new HashMap<>();
    private static final class State { private int key = 1; private boolean poisoned; }

    public record Request(String scenario, String mode, List<String> order, List<String> features, String text) {
        public Request {
            if (scenario == null || scenario.isEmpty() || scenario.codePointCount(0, scenario.length()) > 120)
                throw new IllegalArgumentException("Invalid scenario name");
            java.util.Objects.requireNonNull(text);
            order = List.copyOf(order); features = List.copyOf(features);
        }
    }

    public synchronized Map<String, Object> rotateKey(String scenario) {
        states.computeIfAbsent(scenario, ignored -> new State()).key = 2;
        return FixtureValues.map("ok", true, "key_version", 2);
    }

    public synchronized Map<String, Object> poisonCache(String scenario) {
        states.computeIfAbsent(scenario, ignored -> new State()).poisoned = true;
        return FixtureValues.map("ok", true, "poisoned", true);
    }

    public synchronized Map<String, Object> process(Request r) {
        if (!Set.of("strict", "legacy").contains(r.mode())) return FixtureValues.map("ok", false, "error", "unknown mode");
        if (!r.order().stream().sorted().toList().equals(List.of("normalize", "redact", "tag"))) {
            return FixtureValues.map("ok", false, "error", "invalid operation order");
        }
        State state = states.computeIfAbsent(r.scenario(), ignored -> new State());
        String text = r.text();
        for (String operation : r.order()) {
            text = switch (operation) {
                case "normalize" -> Normalizer.normalize(text, Normalizer.Form.NFKC);
                case "redact" -> SECRET.matcher(r.mode().equals("strict") ? Normalizer.normalize(text, Normalizer.Form.NFKC) : text)
                        .replaceAll("[REDACTED]");
                case "tag" -> text + "|TAG";
                default -> throw new IllegalStateException("Validated operation changed");
            };
        }
        int key = r.mode().equals("strict") ? state.key : 1;
        String digest = digest(text, key);
        if (r.mode().equals("legacy") && state.poisoned && r.features().contains("cache")) digest = "0".repeat(64);
        return FixtureValues.map("ok", true, "text", text, "digest", digest, "key_version", key,
                "audit", r.features().contains("audit"), "mode", r.mode(), "order", r.order(),
                "features", FixtureValues.unicodeSorted(r.features()));
    }

    public static String digest(String text, int key) {
        if (key != 1 && key != 2) throw new IllegalArgumentException("Unknown key version");
        return Canonical.sha256Hex("bundle-key-v" + key + "|" + text);
    }
}
