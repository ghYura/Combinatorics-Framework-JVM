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

package org.ghyura.cf.fixtures.catalogpub;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.ghyura.cf.fixtures.business.FixtureValues.map;
import static org.ghyura.cf.fixtures.catalogpub.PythonValues.*;

/** Seeded, stateful Java semantic port of tester_vs_pub/pub.py; all deliberate defects remain selectable. */
public final class Pub {
    public static final Map<String, Double> MENU;
    static {
        Map<String, Double> menu = new LinkedHashMap<>();
        menu.put("beer", 5.0); menu.put("water", 1.0); menu.put("wine", 7.0); menu.put("cola", 3.0); menu.put("whiskey", 9.0);
        MENU = Collections.unmodifiableMap(menu);
    }
    private static final List<String> REQUIRED = List.of("enter", "sit", "order");
    private static final List<String> GOOD_QUANTITY = List.of("1", "2", "3", "5");
    private static final List<String> ALL_QUANTITY = List.of("1", "2", "3", "5", "0", "-1", "999999999", "abcd", "");
    private static final List<String> GOOD_TARGET = List.copyOf(MENU.keySet());
    private static final List<String> ALL_TARGET = List.of("beer", "water", "wine", "cola", "whiskey", "lizard", "null", "'; DROP TABLE bar;--", "");
    private static final List<Barman> DEFAULT_BARMEN = List.of(new Barman("Ada", .98), new Barman("Boris", .8), new Barman("Dana", .65), new Barman("Chuck", .4));
    private static final List<Client> DEFAULT_CLIENTS = List.of(new Client("Sober Sam", .9), new Client("Regular Rita", .6), new Client("QA Quinn", .15), new Client("Drunk Dave", .05));
    public record Order(List<String> actions, Object quantity, String target, List<String> garnish, Set<String> events) {
        public Order { actions = actions == null ? List.of() : List.copyOf(actions); garnish = garnish == null ? List.of() : List.copyOf(garnish); events = events == null ? Set.of() : Set.copyOf(events); }
        public static Order standard() { return new Order(REQUIRED, "1", "beer", List.of(), Set.of()); }
        public Map<String, Object> toMap() { return map("actions", actions, "quantity", quantity, "target", target, "garnish", garnish, "events", events.stream().sorted().toList()); }
    }
    public record Verdict(boolean served, String status, String reason, BigInteger units, double price) {
        public Map<String, Object> toMap() { return map("served", served, "status", status, "reason", reason, "units", units, "price", price); }
    }
    public record Response(boolean served, String status, String reason, BigInteger units, double price, String target,
                           String barman, boolean adequate, String deviation, boolean crashed, boolean expectedServed, String expectedReason, String error) {
        public Map<String, Object> toMap() { return map("served", served, "status", status, "reason", reason, "units", units, "price", price,
                "target", target, "barman", barman, "adequate", adequate, "deviation", deviation, "crashed", crashed,
                "expected_served", expectedServed, "expected_reason", expectedReason, "error", error); }
    }
    private record Quantity(BigInteger n, String kind) { }
    private static Quantity quantity(Object raw) {
        String s = str(raw); int at = 0; while (at < s.length() && s.charAt(at) == '-') at++;
        if (at == s.length() || !s.substring(at).codePoints().allMatch(PythonValues::isDigit)) return new Quantity(null, "type");
        BigInteger n = integer(s);
        return new Quantity(n, n.signum() < 0 ? "domain" : n.signum() == 0 ? "zero" : n.compareTo(BigInteger.valueOf(1000)) > 0 ? "huge" : "ok");
    }
    private static Verdict no(String status, String reason) { return new Verdict(false, status, reason, BigInteger.ZERO, 0); }
    private static boolean misordered(Order o) { return o.actions().lastIndexOf("order") < o.actions().lastIndexOf("enter") || o.actions().lastIndexOf("sit") < o.actions().lastIndexOf("enter"); }
    public static Verdict adjudicate(Order o) {
        if (o.events().contains("fire_alarm")) return no("evacuated", "fire alarm — everybody out");
        if (!o.actions().containsAll(REQUIRED)) return no("rejected", "you must enter, sit and order");
        if (misordered(o)) return no("rejected", "you must enter the pub first");
        Quantity q = quantity(o.quantity());
        if (q.kind().equals("type")) return no("rejected", "quantity is not a number");
        if (q.kind().equals("domain")) return no("rejected", "quantity must be positive");
        if (q.kind().equals("huge")) return no("rejected", "we can't pour that many — over capacity");
        if (!MENU.containsKey(o.target())) return no("rejected", repr(o.target()) + " is not on the menu");
        if (o.events().contains("last_call") && q.n().signum() > 0) return no("rejected", "sorry, it's last call — the bar is closing");
        double price = round((MENU.get(o.target()) + .5 * o.garnish().size()) * q.n().doubleValue() * (o.events().contains("happy_hour") ? .5 : 1));
        return new Verdict(true, "served", q.n().signum() == 0 ? "zero order — a no-op, nothing poured" : "enjoy", q.n(), q.n().signum() == 0 ? 0 : price);
    }
    private static double difficulty(Order o) {
        double d = 0; String kind = quantity(o.quantity()).kind();
        if (List.of("type", "domain", "huge").contains(kind)) d += .5;
        if (!MENU.containsKey(o.target())) d += .3;
        if (o.actions().containsAll(REQUIRED) && misordered(o)) d += .2;
        if (o.events().contains("last_call") || o.events().contains("fire_alarm")) d += .15;
        return Math.min(1, d);
    }
    private static double round(double value) { return Double.isFinite(value) ? org.ghyura.cf.fixtures.business.FixtureValues.round2(value) : value; }
    private static double numeric(BigInteger value) {
        double result = value.doubleValue(); if (!Double.isFinite(result)) throw new PythonError("OverflowError", "int too large to convert to float"); return result;
    }
    private static double clamp(double n) { return n < 1 ? Math.max(0, n) : 1; }
    public record Barman(String name, double competence) {
        public Barman { competence = clamp(competence); }
        public Response serve(Order o, PythonRandom rng, boolean strict) {
            Verdict truth = adjudicate(o);
            double probability = (1 - competence) * (.30 + .70 * difficulty(o));
            if (rng.random() >= probability) return new Response(truth.served(), truth.status(), truth.reason(), truth.units(), truth.price(),
                    o.target(), name, true, null, false, truth.served(), truth.reason(), "");
            Quantity q = quantity(o.quantity()); List<String> flaws = new ArrayList<>();
            if (q.kind().equals("type")) flaws.addAll(List.of("crash_on_junk", "type_confusion"));
            if (q.kind().equals("huge")) flaws.addAll(List.of("overflow", "pour_the_impossible"));
            if (q.kind().equals("domain")) flaws.add("negative_accept");
            if (!MENU.containsKey(o.target()) && o.actions().contains("order")) flaws.add("serve_off_menu");
            if (!truth.served() && truth.status().equals("rejected") && o.actions().contains("enter")) flaws.add("silent_accept");
            if (o.events().contains("last_call")) flaws.add("ignore_last_call");
            if (truth.served() && truth.units().signum() > 0) flaws.addAll(List.of("wrong_price", "short_change", "silent_drop"));
            if (flaws.isEmpty()) flaws.addAll(List.of("wrong_price", "silent_drop"));
            String flaw = rng.choice(flaws), status = "served", error = "", reason;
            boolean served = true, crashed = false; BigInteger units = BigInteger.ONE; double price;
            switch (flaw) {
                case "crash_on_junk" -> {
                    if (strict) throw new PythonError("PubError", name + " choked on quantity " + repr(o.quantity()));
                    status = "crashed"; reason = "barman crashed on " + repr(o.quantity()); units = BigInteger.ZERO; price = 0;
                    served = false; crashed = true; error = "ValueError: invalid literal for int(): " + repr(o.quantity());
                }
                case "type_confusion" -> { reason = "served a mystery amount"; price = round(MENU.getOrDefault(o.target(), 4.0)); }
                case "overflow" -> { reason = "poured a truly heroic quantity"; units = integer(o.quantity()); price = round(MENU.getOrDefault(o.target(), 5.0) * numeric(units)); }
                case "pour_the_impossible" -> {
                    if (strict) throw new PythonError("PubError", name + " overflowed on " + str(o.quantity()) + " units");
                    status = "crashed"; reason = "the taps exploded"; units = BigInteger.ZERO; price = 0; served = false; crashed = true; error = "OverflowError: too many beers";
                }
                case "negative_accept" -> { reason = "served a negative order (took beer back?)"; units = integer(o.quantity()); price = round(MENU.getOrDefault(o.target(), 5.0) * numeric(units)); }
                case "serve_off_menu" -> { reason = "sure, one " + str(o.target()) + " coming up"; price = 4; }
                case "silent_accept" -> { reason = "accepted a badly-formed order without checking"; price = 5; }
                case "ignore_last_call" -> { reason = "served after last call (bar should be closed)"; units = q.kind().equals("ok") ? q.n() : BigInteger.ONE; price = round(MENU.getOrDefault(o.target(), 5.0) * numeric(units)); }
                case "wrong_price" -> { reason = "served, but charged the wrong price"; units = truth.units(); price = round(truth.price() + rng.choice(List.of(-2.0, -1.0, 1.5, 3.0))); }
                case "short_change" -> { reason = "served, but pocketed the change"; units = truth.units(); price = round(truth.price() + rng.uniform(.5, 3)); }
                case "silent_drop" -> { reason = "said 'served' but poured nothing"; units = BigInteger.ZERO; price = 0; }
                default -> throw new AssertionError(flaw);
            }
            return new Response(served, status, reason, units, price, o.target(), name, false, flaw, crashed, truth.served(), truth.reason(), error);
        }
    }
    public record Client(String name, double sobriety) {
        public Client { sobriety = clamp(sobriety); }
        public Order order(PythonRandom rng) {
            boolean adequate = rng.random() < sobriety; List<String> actions = new ArrayList<>(REQUIRED);
            if (!adequate) rng.shuffle(actions);
            String quantity = rng.choice(adequate ? GOOD_QUANTITY : ALL_QUANTITY), target = rng.choice(adequate ? GOOD_TARGET : ALL_TARGET);
            List<String> garnish = new ArrayList<>(); Set<String> events = new java.util.LinkedHashSet<>();
            for (String item : List.of("ice", "lime", "umbrella", "salt")) if (rng.random() < (adequate ? .15 : .4)) garnish.add(item);
            for (String item : List.of("happy_hour", "last_call", "fire_alarm")) if (rng.random() < (adequate ? .12 : .25)) events.add(item);
            return new Order(actions, quantity, target, garnish, events);
        }
    }
    private final PythonRandom rng;
    private final List<Barman> barmen;
    private final List<Client> clients;
    private final double reliability;
    public Pub(BigInteger seed) { this(seed, .6, List.of(), List.of()); }
    public Pub(BigInteger seed, double reliability, List<Barman> barmen, List<Client> clients) {
        rng = new PythonRandom(seed); this.reliability = clamp(reliability);
        this.barmen = barmen == null || barmen.isEmpty() ? DEFAULT_BARMEN : List.copyOf(barmen);
        this.clients = clients == null || clients.isEmpty() ? DEFAULT_CLIENTS : List.copyOf(clients);
    }
    public Response serve(Order order, String namedBarman, BigInteger seed, boolean strict) {
        PythonRandom random = seed == null ? rng : new PythonRandom(seed); Barman barman;
        if (namedBarman == null) {
            List<Barman> ranked = barmen.stream().sorted(Comparator.comparingDouble(Barman::competence).reversed()).toList();
            List<Double> weights = new ArrayList<>(); for (int i = 0; i < ranked.size(); i++) weights.add(Math.pow(reliability, i) + .05);
            barman = random.weightedChoice(ranked, weights);
        } else barman = barmen.stream().filter(b -> b.name().equals(namedBarman)).findFirst()
                .orElseThrow(() -> new PythonError("KeyError", repr("no barman named " + repr(namedBarman))));
        return barman.serve(order == null ? Order.standard() : order, random, strict);
    }
    public List<List<Object>> simulate(int rounds, boolean strict) {
        List<List<Object>> transcript = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            Client client = rng.choice(clients); Order order = client.order(rng);
            transcript.add(List.of(client.name(), order.toMap(), serve(order, null, null, strict).toMap()));
        }
        return List.copyOf(transcript);
    }
}
