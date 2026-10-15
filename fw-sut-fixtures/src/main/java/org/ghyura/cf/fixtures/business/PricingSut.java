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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Java domain port of SUT/bundle_pricing_tryout/app.py; seeded order and replay faults remain present. */
public final class PricingSut {
    private static final Map<String, Double> TAX = Map.of("us", 0.0825, "eu", 0.20);
    private static final Set<String> COUPONS = Set.of("SUMMER", "LOYALTY");
    private static final List<String> STAGES = List.of("discount", "round", "tax");
    private final Map<String, State> states = new HashMap<>();

    private static final class State {
        private final Set<String> seen = new HashSet<>();
        private double ledger;
    }

    public record Request(String scenario, String key, String mode, String region, List<String> coupons, List<String> stages) {
        public Request {
            if (scenario == null || scenario.isEmpty() || scenario.codePointCount(0, scenario.length()) > 120
                    || key == null || key.isEmpty() || key.codePointCount(0, key.length()) > 120) {
                throw new IllegalArgumentException("Scenario and idempotency key must contain 1..120 characters");
            }
            coupons = List.copyOf(coupons); stages = List.copyOf(stages);
        }
    }

    private static double discount(double price, List<String> coupons) {
        long fixed = coupons.stream().filter("LOYALTY"::equals).count();
        price -= fixed * 10.0;
        for (String coupon : coupons) if (coupon.equals("SUMMER")) price *= 0.8;
        return price;
    }

    public static double canonicalPrice(List<String> coupons, String region) {
        return FixtureValues.round2(discount(100, coupons) * (1 + TAX.get(region)));
    }

    public static double literalPrice(List<String> coupons, String region, List<String> stages, String mode) {
        double price = 100;
        double tax = mode.equals("legacy") ? 0.0825 : TAX.get(region);
        for (String stage : stages) {
            price = switch (stage) {
                case "discount" -> discount(price, coupons);
                case "tax" -> price * (1 + tax);
                case "round" -> FixtureValues.round2(price);
                default -> price;
            };
        }
        return FixtureValues.round2(price);
    }

    public synchronized Map<String, Object> checkout(Request r) {
        if (!Set.of("strict", "fast", "legacy").contains(r.mode())) return error("unknown mode");
        if (!TAX.containsKey(r.region())) return error("unknown region");
        if (!r.stages().stream().sorted().toList().equals(STAGES)) return error("stages must be a permutation of discount/tax/round");
        if (!COUPONS.containsAll(r.coupons())) return error("unknown coupon");
        double price = r.mode().equals("strict") ? canonicalPrice(r.coupons(), r.region())
                : literalPrice(r.coupons(), r.region(), r.stages(), r.mode());
        State state = states.computeIfAbsent(r.scenario(), ignored -> new State());
        boolean duplicate = state.seen.contains(r.key());
        if (!duplicate || r.mode().equals("legacy")) state.ledger = FixtureValues.round2(state.ledger + price);
        state.seen.add(r.key());
        return FixtureValues.map("ok", true, "price", price, "ledger_total", state.ledger, "duplicate", duplicate,
                "mode", r.mode(), "region", r.region(), "coupons", r.coupons().stream().sorted().toList(), "stages", new ArrayList<>(r.stages()));
    }

    private static Map<String, Object> error(String error) { return FixtureValues.map("ok", false, "error", error); }
}
