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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Deterministic in-JVM port of SUT/order_fulfilment/fulfilment.py, including its delivery faults. */
public final class OrderFulfilmentSut {
    private static final Map<String, BigInteger> STOCK = stock(false);
    private static final Set<String> TERMINAL = Set.of("SHIPPED", "FAILED", "CANCELLED");
    private final int timeout;
    private final String compensateOrder;
    private final boolean retry;
    private long now;
    private long nextMessage = 1;
    private long nextAuth = 1;
    private long nextShipment = 1;
    private final Map<String, BigInteger> onHand = new LinkedHashMap<>(STOCK);
    private final Map<String, BigInteger> reserved = stock(true);
    private final Map<String, Reservation> reservations = new LinkedHashMap<>();
    private final Set<String> inventorySeen = new HashSet<>();
    private final Set<String> paymentSeen = new HashSet<>();
    private final Set<String> shippingSeen = new HashSet<>();
    private final Map<String, Map<String, Object>> auths = new LinkedHashMap<>();
    private final Map<String, String> byKey = new LinkedHashMap<>();
    private final List<BigInteger> captured = new ArrayList<>();
    private final List<BigInteger> voided = new ArrayList<>();
    private final Map<String, String> shipments = new LinkedHashMap<>();
    private final List<String> shipmentLog = new ArrayList<>();
    private final List<Map<String, Object>> ledger = new ArrayList<>();
    private final List<Message> bus = new ArrayList<>();
    private final List<String> delivered = new ArrayList<>();
    private final Map<String, Map<String, Object>> orders = new LinkedHashMap<>();

    private record Reservation(String sku, BigInteger quantity) { }

    private static Map<String, BigInteger> stock(boolean zero) {
        Map<String, BigInteger> stock = new LinkedHashMap<>();
        stock.put("widget", zero ? BigInteger.ZERO : BigInteger.valueOf(50));
        stock.put("gadget", zero ? BigInteger.ZERO : BigInteger.valueOf(8));
        stock.put("doohickey", zero ? BigInteger.ZERO : BigInteger.ONE);
        return stock;
    }

    public record Message(String id, String to, String kind, Map<String, Object> body, long sentAt) {
        public Message { body = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(body)); }
        public Map<String, Object> snapshot() {
            return FixtureValues.map("id", id, "to", to, "kind", kind, "body", body, "sent_at", sentAt);
        }
    }

    public OrderFulfilmentSut(int timeout, String compensateOrder, boolean retry) {
        this.timeout = timeout; this.compensateOrder = compensateOrder; this.retry = retry;
    }
    public OrderFulfilmentSut() { this(3, "release_first", true); }

    private Message send(String to, String kind, Map<String, Object> body) {
        Message m = new Message("m" + nextMessage, to, kind, body, now);
        nextMessage = Math.incrementExact(nextMessage); bus.add(m); return m;
    }

    private int pick(int k) {
        if (bus.isEmpty()) return -1;
        long index = k < 0 ? (long) bus.size() + k : k;
        return (int) Math.max(0, Math.min(index, bus.size() - 1L));
    }
    public Message deliver(int k) {
        int i = pick(k); if (i == -1) return null;
        Message m = bus.remove(i); handle(m); return m;
    }
    public Message duplicate(int k) {
        int i = pick(k); if (i == -1) return null;
        Message m = bus.get(i); handle(m); return m;
    }
    public Message drop(int k) { int i = pick(k); return i == -1 ? null : bus.remove(i); }
    public int drain(int limit) {
        int n = 0; while (!bus.isEmpty() && n < limit) { deliver(0); n++; } return n;
    }
    public int inflight() { return bus.size(); }
    public void restart(String service) {
        switch (service) {
            case "inventory" -> inventorySeen.clear();
            case "payment" -> paymentSeen.clear();
            case "shipping" -> shippingSeen.clear();
            default -> throw new IllegalArgumentException("unknown service " + service);
        }
    }
    public void tick(int count) {
        for (int i = 0; i < count; i++) {
            now = Math.incrementExact(now);
            if (!retry) continue;
            for (Map<String, Object> order : new ArrayList<>(orders.values())) {
                if (TERMINAL.contains(text(order, "state")) || order.get("awaiting") == null) continue;
                if (now - (Long) order.get("sent_at") >= timeout) {
                    order.put("attempt", Math.incrementExact((Long) order.get("attempt")));
                    go(text(order, "id"), text(order, "state"));
                }
            }
        }
    }
    public void submit(String oid, String sku, BigInteger quantity, BigInteger amount) {
        orders.put(oid, FixtureValues.map("id", oid, "sku", sku, "qty", quantity, "amount", amount,
                "state", "NEW", "auth", null, "attempt", 0L, "awaiting", null, "sent_at", now, "moves", 0L));
        go(oid, "RESERVING");
    }
    public void submit(String oid) { submit(oid, "widget", BigInteger.ONE, BigInteger.valueOf(100)); }

    private void go(String oid, String state) {
        Map<String, Object> o = orders.get(oid);
        o.put("state", state); o.put("moves", Math.incrementExact((Long) o.get("moves"))); o.put("sent_at", now);
        switch (state) {
            case "RESERVING" -> {
                o.put("awaiting", "reserved");
                send("inventory", "reserve", FixtureValues.map("oid", oid, "sku", o.get("sku"), "qty", o.get("qty")));
            }
            case "AUTHORIZING" -> {
                o.put("awaiting", "authorized");
                send("payment", "authorize", FixtureValues.map("oid", oid, "amount", o.get("amount"), "key", oid + ":" + o.get("attempt")));
            }
            case "CAPTURING" -> {
                o.put("awaiting", "captured"); send("payment", "capture", FixtureValues.map("oid", oid, "auth", o.get("auth")));
            }
            case "SHIPPING" -> {
                o.put("awaiting", "shipped"); send("shipping", "ship", FixtureValues.map("oid", oid));
            }
            case "COMPENSATING" -> {
                o.put("awaiting", "compensated");
                if (compensateOrder.equals("release_first")) send("inventory", "release", FixtureValues.map("oid", oid));
                if (o.get("auth") != null && !o.get("auth").equals(""))
                    send("payment", "void", FixtureValues.map("oid", oid, "auth", o.get("auth")));
                if (!compensateOrder.equals("release_first")) send("inventory", "release", FixtureValues.map("oid", oid));
            }
            default -> o.put("awaiting", null);
        }
    }

    private void handle(Message m) {
        delivered.add(m.kind());
        switch (m.to()) {
            case "inventory" -> inventory(m);
            case "payment" -> payment(m);
            case "shipping" -> shipping(m);
            case "gateway" -> gateway(m);
            default -> { }
        }
    }
    private static String text(Map<String, Object> map, String key) { return (String) map.get(key); }
    private void inventory(Message m) {
        if (!inventorySeen.add(m.id())) return;
        String oid = text(m.body(), "oid");
        switch (m.kind()) {
            case "reserve" -> {
                String sku = text(m.body(), "sku"); BigInteger qty = (BigInteger) m.body().get("qty");
                boolean ok = reservations.containsKey(oid) || onHand.getOrDefault(sku, BigInteger.ZERO).compareTo(qty) >= 0;
                if (ok && !reservations.containsKey(oid)) {
                    if (!onHand.containsKey(sku)) throw new IllegalArgumentException("unknown sku " + sku);
                    onHand.put(sku, onHand.get(sku).subtract(qty)); reserved.put(sku, reserved.get(sku).add(qty));
                    reservations.put(oid, new Reservation(sku, qty));
                }
                send("gateway", "reserved", FixtureValues.map("oid", oid, "ok", ok));
            }
            case "release", "consume" -> {
                Reservation r = reservations.remove(oid);
                if (r != null) {
                    reserved.put(r.sku(), reserved.get(r.sku()).subtract(r.quantity()));
                    if (m.kind().equals("release")) onHand.put(r.sku(), onHand.get(r.sku()).add(r.quantity()));
                }
            }
            default -> { }
        }
    }
    private void payment(Message m) {
        if (!paymentSeen.add(m.id())) return;
        String oid = text(m.body(), "oid");
        switch (m.kind()) {
            case "authorize" -> {
                String key = text(m.body(), "key"); String aid = byKey.get(key);
                if (aid == null) {
                    aid = "auth-" + nextAuth; nextAuth = Math.incrementExact(nextAuth);
                    auths.put(aid, FixtureValues.map("oid", oid, "amount", m.body().get("amount"), "state", "AUTHORIZED")); byKey.put(key, aid);
                }
                send("gateway", "authorized", FixtureValues.map("oid", oid, "auth", aid));
            }
            case "capture" -> {
                Map<String, Object> a = auths.get(text(m.body(), "auth"));
                if (a != null) {
                    a.put("state", "CAPTURED"); BigInteger amount = (BigInteger) a.get("amount"); captured.add(amount);
                    ledger.add(FixtureValues.map("kind", "capture", "oid", oid, "amount", amount));
                }
                send("gateway", "captured", FixtureValues.map("oid", oid, "ok", a != null));
            }
            case "void" -> {
                Map<String, Object> a = auths.get(text(m.body(), "auth"));
                if (a != null && a.get("state").equals("CAPTURED")) {
                    BigInteger amount = (BigInteger) a.get("amount"); voided.add(amount); a.put("state", "REFUNDED");
                    if (amount.signum() != 0) ledger.add(FixtureValues.map("kind", "refund", "oid", oid, "amount", amount));
                } else if (a != null) a.put("state", "VOIDED");
            }
            default -> { }
        }
    }
    private void shipping(Message m) {
        if (!shippingSeen.add(m.id())) return;
        if (m.kind().equals("ship")) {
            String oid = text(m.body(), "oid"); String sid = "shp-" + nextShipment; nextShipment = Math.incrementExact(nextShipment);
            shipments.put(oid, sid); shipmentLog.add(oid);
            send("gateway", "shipped", FixtureValues.map("oid", oid, "shipment", sid));
        }
    }
    private void gateway(Message m) {
        String oid = text(m.body(), "oid"); Map<String, Object> o = orders.get(oid); if (o == null) return;
        switch (m.kind()) {
            case "reserved" -> {
                if (Boolean.TRUE.equals(m.body().get("ok"))) go(oid, "AUTHORIZING");
                else { o.put("state", "FAILED"); o.put("awaiting", null); }
            }
            case "authorized" -> { o.put("auth", m.body().get("auth")); go(oid, "CAPTURING"); }
            case "captured" -> go(oid, Boolean.TRUE.equals(m.body().get("ok")) ? "SHIPPING" : "COMPENSATING");
            case "shipped" -> {
                send("inventory", "consume", FixtureValues.map("oid", oid)); o.put("state", "SHIPPED"); o.put("awaiting", null);
            }
            default -> { }
        }
    }
    private BigInteger sum(String kind) {
        return ledger.stream().filter(e -> e.get("kind").equals(kind)).map(e -> (BigInteger) e.get("amount"))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
    public BigInteger balance() {
        return ledger.stream().map(e -> e.get("kind").equals("capture") ? (BigInteger) e.get("amount") : ((BigInteger) e.get("amount")).negate())
                .reduce(BigInteger.ZERO, BigInteger::add);
    }
    public List<String> audit() {
        Set<String> bad = new TreeSet<>();
        if (!balance().equals(sum("capture").subtract(sum("refund")))) bad.add("J1");
        Map<String, Integer> captureCounts = new LinkedHashMap<>();
        for (Map<String, Object> e : ledger) if (e.get("kind").equals("capture")) captureCounts.merge(text(e, "oid"), 1, Math::addExact);
        if (captureCounts.values().stream().anyMatch(n -> n > 1)) bad.add("J5");
        if (new HashSet<>(shipmentLog).size() != shipmentLog.size()) bad.add("J4");
        for (Map<String, Object> o : orders.values()) {
            String oid = text(o, "id"), state = text(o, "state");
            if (TERMINAL.contains(state) && !state.equals("SHIPPED") && reservations.containsKey(oid)) bad.add("J2");
            if (state.equals("FAILED")) {
                BigInteger net = ledger.stream().filter(e -> e.get("oid").equals(oid))
                        .map(e -> e.get("kind").equals("capture") ? (BigInteger) e.get("amount") : ((BigInteger) e.get("amount")).negate())
                        .reduce(BigInteger.ZERO, BigInteger::add);
                if (net.signum() != 0) bad.add("J6");
            }
            if (TERMINAL.contains(state) && Boolean.TRUE.equals(o.get("moved_after_terminal"))) bad.add("J8");
        }
        for (String oid : shipments.keySet()) {
            Map<String, Object> o = orders.get(oid); Map<String, Object> a = o == null ? null : auths.get(o.get("auth"));
            if (a == null || !Set.of("CAPTURED", "REFUNDED").contains(a.get("state"))) bad.add("J3");
        }
        for (Map.Entry<String, BigInteger> entry : STOCK.entrySet()) {
            String sku = entry.getKey();
            BigInteger shipped = orders.values().stream().filter(o -> o.get("state").equals("SHIPPED") && o.get("sku").equals(sku))
                    .map(o -> (BigInteger) o.get("qty")).reduce(BigInteger.ZERO, BigInteger::add);
            if (reserved.get(sku).signum() < 0 || onHand.get(sku).signum() < 0
                    || !onHand.get(sku).add(reserved.get(sku)).add(shipped).equals(entry.getValue())) bad.add("J7");
        }
        return List.copyOf(bad);
    }
    public Map<String, Object> summary() {
        Map<String, Object> states = new LinkedHashMap<>(); orders.forEach((oid, o) -> states.put(oid, o.get("state")));
        return FixtureValues.map("orders", states, "inflight", bus.size(), "balance", balance(), "shipped", shipmentLog.size(),
                "reserved", new LinkedHashMap<>(reserved), "on_hand", new LinkedHashMap<>(onHand));
    }
    public Map<String, Object> snapshot() {
        Map<String, Object> byOrder = new LinkedHashMap<>();
        reservations.forEach((oid, r) -> byOrder.put(oid, List.of(r.sku(), r.quantity())));
        Map<String, Object> orderCopies = new LinkedHashMap<>(); orders.forEach((oid, o) -> orderCopies.put(oid, new LinkedHashMap<>(o)));
        Map<String, Object> authCopies = new LinkedHashMap<>(); auths.forEach((aid, a) -> authCopies.put(aid, new LinkedHashMap<>(a)));
        return FixtureValues.map("now", now, "summary", summary(), "audit", audit(), "orders", orderCopies,
                "bus", bus.stream().map(Message::snapshot).toList(), "delivered", List.copyOf(delivered), "by_order", byOrder,
                "auths", authCopies, "by_key", new LinkedHashMap<>(byKey), "captured", List.copyOf(captured), "voided", List.copyOf(voided),
                "shipments", new LinkedHashMap<>(shipments), "shipment_log", List.copyOf(shipmentLog),
                "ledger", ledger.stream().map(e -> new LinkedHashMap<>(e)).toList());
    }
}
