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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stateful domain port of SUT/billing_engine/billing_core.py. Money uses arbitrary precision minor units.
 * Original proration, replay, refund and coupon-reapplication defects are intentionally preserved.
 * An account belongs to one sample; no ambient clock or shared subscription state exists.
 */
public final class BillingSut {
    private static final Map<String, Integer> PRICES = Map.of("free", 0, "basic", 1000, "pro", 3000, "team", 12000);
    private static final Map<String, Integer> PERCENT = Map.of("none", 0, "welcome", 20, "loyal10", 10, "half", 50);
    private static final Map<String, Integer> MAX_USES = Map.of("none", 0, "welcome", 1, "loyal10", 3, "half", 1);
    private String plan;
    private final int cycleDays;
    private final String proration;
    private final String rounding;
    private final long trialEnds;
    private long now;
    private long cycleStart;
    private Long pausedAt;
    private String state = "active";
    private String coupon;
    private int couponUses;
    private BigInteger credit = BigInteger.ZERO;
    private BigInteger cash = BigInteger.ZERO;
    private final List<Invoice> invoices = new ArrayList<>();
    private final List<Map<String, Object>> ledger = new ArrayList<>();
    private final Map<String, Map<String, Object>> events = new LinkedHashMap<>();
    private long nextInvoice = 1;
    private long nextEvent = 1;

    private record Line(String description, BigInteger amount) {
        Map<String, Object> snapshot() { return FixtureValues.map("desc", description, "amount", amount); }
    }

    private static final class Invoice {
        private final long id;
        private final long at;
        private final String reason;
        private final String plan;
        private final List<Line> lines;
        private final BigInteger total;
        private BigInteger refunded = BigInteger.ZERO;
        private String event;
        private Invoice(long id, long at, String reason, String plan, List<Line> lines, BigInteger total) {
            this.id = id; this.at = at; this.reason = reason; this.plan = plan; this.lines = List.copyOf(lines); this.total = total;
        }
        Map<String, Object> snapshot() {
            return FixtureValues.map("id", id, "at", at, "reason", reason, "plan", plan,
                    "lines", lines.stream().map(Line::snapshot).toList(), "total", total, "refunded", refunded,
                    "settled", true, "raised_while", "active", "event", event);
        }
    }

    public BillingSut(String plan, int cycleDays, String proration, String rounding, long trialDays, boolean dunning) {
        checkPlan(plan);
        this.plan = plan; this.cycleDays = cycleDays; this.proration = proration; this.rounding = rounding; this.trialEnds = trialDays;
        // dunning is accepted but unused in the original engine too.
        charge(0, "signup", List.of());
    }

    public BillingSut() { this("basic", 30, "credit", "half_up", 0, false); }

    private static void checkPlan(String plan) {
        if (!PRICES.containsKey(plan)) throw new IllegalArgumentException("unknown plan " + plan);
    }

    private BigInteger price(String name) { return BigInteger.valueOf(PRICES.get(name)); }

    private BigInteger round(double amount) {
        long base = (long) amount;
        if (rounding.equals("half_up")) return BigInteger.valueOf((long) (amount + 0.5));
        if (rounding.equals("bankers")) {
            double rem = amount - base;
            if (rem > 0.5 || (rem == 0.5 && base % 2 != 0)) base++;
        }
        return BigInteger.valueOf(base);
    }

    private String event(String kind) {
        String id = kind + "-" + nextEvent; nextEvent = Math.incrementExact(nextEvent); return id;
    }

    private void log(String kind, BigInteger amount, Long invoice, String eventId, long at) {
        ledger.add(FixtureValues.map("kind", kind, "amount", amount, "at", at, "invoice", invoice, "event", eventId));
    }

    private Invoice charge(long at, String reason, List<Line> extra) {
        if (!state.equals("active") || at < trialEnds) return null;
        BigInteger base = price(plan);
        List<Line> lines = new ArrayList<>(); lines.add(new Line("plan", base));
        if (coupon != null && couponUses < MAX_USES.get(coupon)) {
            BigInteger discount = round(base.doubleValue() * PERCENT.get(coupon) / 100.0);
            lines.add(new Line("coupon:" + coupon, discount.negate())); couponUses++;
        }
        lines.addAll(extra);
        BigInteger total = lines.stream().map(Line::amount).reduce(BigInteger.ZERO, BigInteger::add);
        if (credit.signum() > 0 && total.signum() > 0) {
            BigInteger applied = credit.min(total);
            lines.add(new Line("credit", applied.negate())); credit = credit.subtract(applied); total = total.subtract(applied);
        }
        if (total.signum() < 0) { credit = credit.subtract(total); total = BigInteger.ZERO; }
        Invoice invoice = new Invoice(nextInvoice, at, reason, plan, lines, total);
        nextInvoice = Math.incrementExact(nextInvoice); invoices.add(invoice);
        invoice.event = event("charge"); events.put(invoice.event, FixtureValues.map("kind", "charge", "invoice", invoice.id));
        cash = cash.add(total); log("charge", total, invoice.id, invoice.event, at);
        return invoice;
    }

    public void subscribe(String newPlan) {
        checkPlan(newPlan);
        if (state.equals("cancelled")) throw new IllegalArgumentException("cannot subscribe on a cancelled account");
        plan = newPlan; cycleStart = now; charge(now, "subscribe", List.of());
    }

    public void changePlan(String newPlan) {
        checkPlan(newPlan);
        if (!state.equals("active")) throw new IllegalArgumentException("cannot change plan while " + state);
        String old = plan;
        long remaining = Math.max(0, cycleDays - (now - cycleStart)); plan = newPlan;
        if (proration.equals("none")) return;
        if (cycleDays == 0) throw new ArithmeticException("division by zero");
        double fraction = (double) remaining / cycleDays;
        // Preserve the source's use of the already changed plan to calculate unused credit.
        BigInteger unused = round(price(plan).doubleValue() * fraction);
        BigInteger newPart = round(price(newPlan).doubleValue() * fraction);
        if (proration.equals("credit") && unused.signum() > 0) {
            credit = credit.add(unused); log("credit", unused, null, event("credit"), now);
        } else if (proration.equals("immediate")) {
            BigInteger delta = newPart.subtract(unused);
            if (delta.signum() > 0) charge(now, "proration", List.of(new Line("prorated " + old + "->" + newPlan, delta.subtract(price(newPlan)))));
            else if (delta.signum() < 0) { credit = credit.subtract(delta); log("credit", delta.negate(), null, event("credit"), now); }
        }
    }

    public void pause() {
        if (!state.equals("active")) throw new IllegalArgumentException("cannot pause while " + state);
        state = "paused"; pausedAt = now;
    }
    public void resume() {
        if (!state.equals("paused")) throw new IllegalArgumentException("cannot resume while " + state);
        state = "active"; cycleStart = Math.addExact(cycleStart, now - pausedAt); pausedAt = null;
    }
    public void cancel() {
        if (state.equals("cancelled")) throw new IllegalArgumentException("already cancelled"); state = "cancelled";
    }
    public void reactivate() {
        if (!state.equals("cancelled")) throw new IllegalArgumentException("not cancelled");
        state = "active"; cycleStart = now; charge(now, "reactivate", List.of());
    }

    public void tick(int days) {
        for (int i = 0; i < days; i++) {
            now = Math.incrementExact(now);
            if (state.equals("active") && now - cycleStart >= cycleDays) {
                cycleStart = Math.addExact(cycleStart, cycleDays); charge(now, "renewal", List.of());
            }
        }
    }

    public void applyCoupon(String code) {
        if (!PERCENT.containsKey(code)) throw new IllegalArgumentException("unknown coupon " + code);
        if (code.equals("none")) return;
        coupon = code; couponUses = 0;
    }

    private Invoice invoice(Long id, String emptyMessage) {
        if (id == null) {
            if (invoices.isEmpty()) throw new IllegalArgumentException(emptyMessage);
            return invoices.getLast();
        }
        return invoices.stream().filter(i -> i.id == id).findFirst().orElseThrow(() -> new IllegalArgumentException("no such invoice " + id));
    }

    public BigInteger refund(Long id) {
        Invoice invoice = invoice(id, "nothing to refund"); BigInteger amount = price(plan).min(invoice.total);
        invoice.refunded = invoice.refunded.add(amount); cash = cash.subtract(amount);
        log("refund", amount, invoice.id, event("refund"), now); return amount;
    }

    public BigInteger chargeback(Long id) {
        Invoice invoice = invoice(id, "nothing to charge back"); BigInteger amount = invoice.total;
        invoice.refunded = invoice.refunded.add(amount); cash = cash.subtract(amount);
        log("chargeback", amount, invoice.id, event("chargeback"), now); return amount;
    }

    public void replay(String id) {
        if (id == null) {
            if (ledger.isEmpty()) return;
            id = (String) ledger.getLast().get("event");
        }
        if (events.containsKey(id)) return;
        switch (id.split("-", 2)[0]) {
            case "charge" -> charge(now, "replay", List.of());
            case "refund" -> refund(null);
            case "chargeback" -> chargeback(null);
            default -> { }
        }
    }

    private BigInteger sum(String kind) {
        return ledger.stream().filter(e -> e.get("kind").equals(kind)).map(e -> (BigInteger) e.get("amount"))
                .reduce(BigInteger.ZERO, BigInteger::add);
    }

    /** Independent invariant observations: the engine never repairs itself from this audit. */
    public List<String> audit() {
        List<String> bad = new ArrayList<>();
        BigInteger charges = sum("charge"), refunds = sum("refund"), chargebacks = sum("chargeback"), credits = sum("credit");
        if (!cash.equals(charges.subtract(refunds).subtract(chargebacks))) bad.add("I1");
        if (credit.signum() < 0) bad.add("I2");
        // I3 cannot arise through the source public API: charge refuses a non-active account.
        if (invoices.stream().anyMatch(i -> i.refunded.compareTo(i.total) > 0)) bad.add("I4");
        Map<String, Integer> used = new HashMap<>();
        for (Invoice i : invoices) for (Line line : i.lines) {
            if (line.description().startsWith("coupon:")) used.merge(line.description().substring(7), 1, Integer::sum);
        }
        if (used.entrySet().stream().anyMatch(e -> e.getValue() > MAX_USES.get(e.getKey()))) bad.add("I5");
        if (invoices.stream().anyMatch(i -> !i.total.equals(i.lines.stream().map(Line::amount).reduce(BigInteger.ZERO, BigInteger::add)))) bad.add("I6");
        if (invoices.stream().anyMatch(i -> i.at < trialEnds && i.total.signum() > 0)) bad.add("I7");
        if (credits.add(refunds).add(chargebacks).compareTo(charges) > 0) bad.add("I8");
        if (cycleStart > now) bad.add("I9");
        return List.copyOf(bad);
    }

    public Map<String, Object> snapshot() {
        return FixtureValues.map("plan", plan, "now", now, "state", state, "cycle_start", cycleStart, "trial_ends", trialEnds,
                "paused_at", pausedAt, "coupon", coupon, "coupon_uses", couponUses, "credit_balance", credit, "cash_collected", cash,
                "invoices", invoices.stream().map(Invoice::snapshot).toList(), "ledger", ledger.stream().map(LinkedHashMap::new).toList(), "audit", audit());
    }
}
