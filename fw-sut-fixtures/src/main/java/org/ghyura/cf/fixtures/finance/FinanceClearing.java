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

package org.ghyura.cf.fixtures.finance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;

/** Original two-party coordinator; policy gates and settlement ports are injected. */
public final class FinanceClearing {
    public static final int MAX_TRANSFERS = 256;
    public enum Status {
        INITIATED, BLOCKED, AWAITING_CONFIRMATION, PARTIALLY_CONFIRMED,
        AWAITING_REVIEW, ON_HOLD, SETTLED, REJECTED, EXPIRED, FAILED;
        public boolean terminal() {
            return this == BLOCKED || this == SETTLED || this == REJECTED || this == EXPIRED || this == FAILED;
        }
    }
    public enum Party {
        SENDER, RECEIVER;
        public String label() { return this == SENDER ? "A" : "B"; }
    }
    public record Request(String idempotencyKey, Money amount, String sourceClient,
                          String sourceAccount, String sourceBank, String targetClient,
                          String targetAccount, String targetBank) {
        public Request {
            text(idempotencyKey); Objects.requireNonNull(amount);
            text(sourceClient); text(sourceAccount); text(sourceBank);
            text(targetClient); text(targetAccount); text(targetBank);
        }
    }
    public static final class PeerUnavailable extends ServiceError {
        private static final long serialVersionUID = 1L;
        private final boolean retryable;
        private final String bank;
        public PeerUnavailable(String message, boolean retryable, String bank) {
            super("PEER_UNAVAILABLE", 503, text(message), fields("bank", text(bank)));
            this.retryable = retryable; this.bank = bank;
        }
        public boolean retryable() { return retryable; }
        public String bank() { return bank; }
    }
    private record Confirmation(String decision, Instant at) { }
    public static final class Transfer {
        private final String reference, idempotencyKey;
        private final FinanceRisk.Context context;
        private final Instant createdAt;
        private final double expiresSeconds;
        private final int ttlSeconds;
        private Instant updatedAt;
        private Status status = Status.INITIATED;
        private boolean requiresReview;
        private List<FinanceRisk.Result> gateResults = List.of();
        private final List<String> reasons = new ArrayList<>();
        private final Map<Party, String> tokens = new LinkedHashMap<>();
        private final Map<Party, Confirmation> confirmations = new LinkedHashMap<>();
        private Map<String, Object> settlement;
        private String failure;
        private FinanceSettlement.Executor executor;
        private Transfer(String reference, Request request, Instant created, Instant updated, double expiry, int ttl) {
            this.reference = reference; idempotencyKey = request.idempotencyKey();
            context = new FinanceRisk.Context(reference, request.amount(), request.sourceClient(), request.targetClient(),
                    request.sourceAccount(), request.targetAccount(), request.sourceBank(), request.targetBank());
            createdAt = created; updatedAt = updated; expiresSeconds = expiry; ttlSeconds = ttl;
        }
        public String reference() { return reference; }
        public String idempotencyKey() { return idempotencyKey; }
        public Status status() { return status; }
        public FinanceRisk.Context context() { return context; }
        public boolean requiresReview() { return requiresReview; }
        public String failure() { return failure; }
        public Instant updatedAt() { return updatedAt; }
        public Map<Party, String> tokens() { return Collections.unmodifiableMap(new LinkedHashMap<>(tokens)); }
        private boolean bothConfirmed() {
            return Arrays.stream(Party.values()).allMatch(p -> confirmations.containsKey(p)
                    && confirmations.get(p).decision().equals("CONFIRM"));
        }
        public Map<String, Object> view() {
            var confirmed = new LinkedHashMap<String, String>();
            confirmations.forEach((p, c) -> confirmed.put(p.name(), c.decision()));
            return fields("reference", reference, "status", status.name(), "amount", context.amount().asString(),
                    "currency", context.amount().currency(), "cross_bank", context.crossBank(), "requires_review", requiresReview,
                    "source", fields("client_id", context.sourceClient(), "account_id", context.sourceAccount(), "bank", context.sourceBank()),
                    "target", fields("client_id", context.targetClient(), "account_id", context.targetAccount(), "bank", context.targetBank()),
                    "confirmations", confirmed, "reasons", List.copyOf(reasons),
                    "gate_results", gateResults.stream().map(FinanceRisk.Result::view).toList(),
                    "settlement", settlement == null ? null : new LinkedHashMap<>(settlement), "failure", failure,
                    "created_at", FinanceLedger.iso(createdAt), "ttl_seconds", ttlSeconds,
                    "expires_at", FinanceLedger.iso(secondsInstant(expiresSeconds)));
        }
    }

    private final List<Function<FinanceRisk.Context, FinanceRisk.Result>> gates;
    private final Function<FinanceRisk.Context, FinanceSettlement.Executor> router;
    private final Bus bus;
    private final String name;
    private final FinanceIdentity identity;
    private final FinanceTime time;
    private final List<Consumer<Transfer>> hooks;
    private final DoubleSupplier seconds;
    private final Supplier<String> references, tokenFactory;
    private final Map<String, Transfer> transfers = new LinkedHashMap<>();
    private final Map<String, String> byIdempotency = new LinkedHashMap<>();
    private final Set<String> onHold = new LinkedHashSet<>();

    public FinanceClearing(List<Function<FinanceRisk.Context, FinanceRisk.Result>> gates,
                           Function<FinanceRisk.Context, FinanceSettlement.Executor> router,
                           Bus bus, String name, FinanceIdentity identity, FinanceTime time,
                           List<Consumer<Transfer>> hooks) {
        this(gates, router, bus, name, identity, time, hooks, () -> System.currentTimeMillis() / 1000.0,
                () -> "TRF-" + HexFormat.of().withUpperCase().formatHex(random(4)),
                () -> Base64.getUrlEncoder().withoutPadding().encodeToString(random(18)));
    }
    public FinanceClearing(List<Function<FinanceRisk.Context, FinanceRisk.Result>> gates,
                           Function<FinanceRisk.Context, FinanceSettlement.Executor> router,
                           Bus bus, String name, FinanceIdentity identity, FinanceTime time,
                           List<Consumer<Transfer>> hooks, DoubleSupplier seconds,
                           Supplier<String> references, Supplier<String> tokens) {
        if (gates.size() > 16 || hooks.size() > 16) throw new IllegalArgumentException("clearing gate/hook bound exceeded");
        this.gates = List.copyOf(gates); this.router = Objects.requireNonNull(router);
        this.bus = Objects.requireNonNull(bus); this.name = text(name); this.identity = identity;
        this.time = Objects.requireNonNull(time); this.hooks = List.copyOf(hooks);
        this.seconds = Objects.requireNonNull(seconds); this.references = Objects.requireNonNull(references);
        tokenFactory = Objects.requireNonNull(tokens);
    }
    private static byte[] random(int bytes) {
        var value = new byte[bytes]; new SecureRandom().nextBytes(value); return value;
    }
    private static double boundedSeconds(double value) {
        if (!Double.isFinite(value) || Math.abs(value) > 1e12) throw new IllegalArgumentException("finite bounded clearing seconds required");
        return value;
    }
    private static Instant secondsInstant(double value) {
        long whole = (long) Math.floor(value);
        long micros = (long) Math.rint((value - whole) * 1_000_000);
        return Instant.ofEpochSecond(whole, micros * 1000);
    }
    private void touch(Transfer transfer) { transfer.updatedAt = bus.now(); }
    private void emit(String kind, Object... values) { bus.publish(kind, name, values); }
    private static ServiceError state(String message, Object... details) {
        return new ServiceError("INVALID_STATE", 409, message, fields(details));
    }
    private static ServiceError unauthorized(String message, Object... details) {
        return new ServiceError("UNAUTHORIZED", 401, message, fields(details));
    }
    public Transfer get(String reference) {
        var transfer = transfers.get(reference);
        if (transfer == null) throw notFound("transfer not found", "reference", reference);
        return transfer;
    }
    public List<Transfer> transfers() { return List.copyOf(transfers.values()); }
    public Transfer initiate(Request request) {
        String existing = byIdempotency.get(request.idempotencyKey());
        if (existing != null) return transfers.get(existing);
        if (transfers.size() >= MAX_TRANSFERS) throw new IllegalArgumentException("clearing transfer bound exceeded");
        String reference = text(references.get());
        if (!reference.matches("TRF-[0-9A-F]{8}") || transfers.containsKey(reference))
            throw new IllegalArgumentException("invalid or duplicate injected transfer reference");
        int ttl = time.seconds("confirmation_ttl_seconds");
        double expiry = boundedSeconds(seconds.getAsDouble()) + ttl;
        var transfer = new Transfer(reference, request, bus.now(), bus.now(), expiry, ttl);
        transfers.put(reference, transfer); byIdempotency.put(request.idempotencyKey(), reference);
        emit("transfer.initiated", "reference", reference, "amount", request.amount().toString(),
                "sender", request.sourceClient(), "receiver", request.targetClient(), "cross_bank", transfer.context.crossBank());
        var evaluated = new ArrayList<FinanceRisk.Result>();
        for (var gate : gates) evaluated.add(Objects.requireNonNull(gate.apply(transfer.context)));
        transfer.gateResults = List.copyOf(evaluated);
        for (var result : evaluated) transfer.reasons.addAll(result.reasons());
        var decision = FinanceRisk.combine(evaluated);
        if (decision == FinanceRisk.Decision.BLOCK) {
            transfer.status = Status.BLOCKED; touch(transfer);
            emit("transfer.blocked", "reference", reference, "reasons", transfer.reasons.isEmpty() ? "policy" : String.join(";", transfer.reasons));
            return transfer;
        }
        transfer.executor = router.apply(transfer.context);
        try { transfer.executor.hold(transfer.context); }
        catch (ServiceError error) {
            transfer.status = Status.REJECTED; transfer.failure = error.code(); transfer.reasons.add(error.code()); touch(transfer);
            emit("transfer.rejected", "reference", reference, "reason", error.code()); return transfer;
        }
        transfer.requiresReview = decision == FinanceRisk.Decision.REVIEW;
        transfer.tokens.put(Party.SENDER, asciiToken(tokenFactory.get()));
        transfer.tokens.put(Party.RECEIVER, asciiToken(tokenFactory.get()));
        transfer.status = Status.AWAITING_CONFIRMATION; touch(transfer);
        emit("transfer.awaiting_confirmation", "reference", reference, "requires_review", transfer.requiresReview, "parties", "A+B");
        return transfer;
    }
    private static String asciiToken(String value) {
        text(value);
        if (value.length() > 256 || !value.chars().allMatch(c -> c < 128)) throw new IllegalArgumentException("bounded ASCII token required");
        return value;
    }
    public Transfer confirm(String reference, Party party, String token) { return confirm(reference, party, token, "CONFIRM"); }
    public Transfer confirm(String reference, Party party, String token, String decision) {
        var transfer = get(reference);
        if (transfer.status != Status.AWAITING_CONFIRMATION && transfer.status != Status.PARTIALLY_CONFIRMED)
            throw state("transfer is not awaiting confirmation (status=" + transfer.status.name() + ")", "reference", reference);
        String expected = transfer.tokens.get(party);
        if (expected == null || expected.isEmpty() || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII), asciiToken(token).getBytes(StandardCharsets.US_ASCII)))
            throw unauthorized("invalid confirmation token", "reference", reference, "party", party.name());
        decision = text(decision).toUpperCase(Locale.ROOT);
        if (!decision.equals("CONFIRM") && !decision.equals("REJECT")) throw validation("decision must be CONFIRM or REJECT", "field", "decision");
        var prior = transfer.confirmations.get(party);
        if (prior != null) {
            if (prior.decision().equals(decision)) return transfer;
            throw state("party " + party.label() + " already responded", "reference", reference);
        }
        transfer.confirmations.put(party, new Confirmation(decision, bus.now())); touch(transfer);
        if (decision.equals("REJECT")) {
            transfer.status = Status.REJECTED; safeRelease(transfer);
            emit("transfer.rejected", "reference", reference, "by", party.label(), "reason", "party_rejected"); return transfer;
        }
        emit("transfer.party_confirmed", "reference", reference, "by", party.label());
        if (transfer.bothConfirmed()) {
            if (transfer.requiresReview) {
                transfer.status = Status.AWAITING_REVIEW; emit("transfer.awaiting_review", "reference", reference); return transfer;
            }
            return settle(transfer);
        }
        transfer.status = Status.PARTIALLY_CONFIRMED; return transfer;
    }
    public Transfer review(String reference, boolean approve, String actor) {
        var transfer = get(reference);
        if (transfer.status != Status.AWAITING_REVIEW)
            throw state("transfer is not awaiting review (status=" + transfer.status.name() + ")", "reference", reference);
        if (identity != null && actor != null && !identity.get(actor).hasRole("COMPLIANCE"))
            throw unauthorized("compliance role required", "actor", actor);
        String by = actor == null || actor.isEmpty() ? "system" : actor;
        if (approve) { emit("transfer.review_approved", "reference", reference, "by", by); return settle(transfer); }
        transfer.status = Status.REJECTED; safeRelease(transfer);
        emit("transfer.review_denied", "reference", reference, "by", by); return transfer;
    }
    public List<Transfer> expireDue() { return expireDue(seconds.getAsDouble()); }
    public List<Transfer> expireDue(double now) {
        boundedSeconds(now); var expired = new ArrayList<Transfer>();
        for (var transfer : transfers.values()) if (!transfer.status.terminal() && transfer.expiresSeconds <= now) {
            transfer.status = Status.EXPIRED; touch(transfer); safeRelease(transfer); onHold.remove(transfer.reference);
            emit("transfer.expired", "reference", transfer.reference); expired.add(transfer);
        }
        return List.copyOf(expired);
    }
    private Transfer settle(Transfer transfer) {
        try {
            var result = transfer.executor.settle(transfer.context);
            transfer.settlement = result; transfer.status = Status.SETTLED; transfer.failure = null; touch(transfer);
            onHold.remove(transfer.reference);
            emit("transfer.settled", "reference", transfer.reference, "amount", transfer.context.amount().toString(), "mode", result.getOrDefault("mode", "?"));
            for (var hook : hooks) hook.accept(transfer);
        } catch (PeerUnavailable error) {
            transfer.failure = error.code(); touch(transfer);
            if (error.retryable()) {
                transfer.status = Status.ON_HOLD; onHold.add(transfer.reference);
                emit("transfer.on_hold", "reference", transfer.reference, "peer", error.bank());
            } else {
                transfer.status = Status.FAILED; safeRelease(transfer); onHold.remove(transfer.reference);
                emit("transfer.unavailable", "reference", transfer.reference, "peer", error.bank());
            }
        } catch (ServiceError error) {
            transfer.status = Status.FAILED; transfer.failure = error.code(); touch(transfer);
            safeRelease(transfer); onHold.remove(transfer.reference);
            emit("transfer.failed", "reference", transfer.reference, "reason", error.code());
        }
        return transfer;
    }
    /** Deterministic insertion order replaces the original unordered on-hold set iteration. */
    public List<Transfer> retryOnHold() {
        var resolved = new ArrayList<Transfer>();
        for (String reference : List.copyOf(onHold)) {
            var transfer = transfers.get(reference);
            if (transfer == null || transfer.status != Status.ON_HOLD) { onHold.remove(reference); continue; }
            emit("transfer.retry", "reference", reference); settle(transfer);
            if (transfer.status != Status.ON_HOLD) resolved.add(transfer);
        }
        return List.copyOf(resolved);
    }
    private void safeRelease(Transfer transfer) {
        if (transfer.executor == null) return;
        try { transfer.executor.release(transfer.context); } catch (Exception ignored) { /* original safe release */ }
    }
}
