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

import org.ghyura.cf.model.Json;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.ghyura.cf.fixtures.finance.FinanceKernel.*;
import static org.junit.jupiter.api.Assertions.*;

class FinancePeeringTest {
    private Map<String, Map<?, ?>> controls(String section) throws Exception {
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance(); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        try (var in = getClass().getResourceAsStream("peering-golden.xml")) {
            assertNotNull(in); var text = factory.newDocumentBuilder().parse(in).getDocumentElement().getElementsByTagName(section).item(0).getTextContent();
            var out = new LinkedHashMap<String, Map<?, ?>>(); for (Object row : (List<?>) Json.parse(text, section)) out.put((String) ((Map<?, ?>) row).get("id"), (Map<?, ?>) row); return out;
        }
    }
    private static final class Clock { double now; }
    private static FinancePeering.PeerLink.Builder link(Clock clock) { return FinancePeering.PeerLink.builder("toBankX", "NORD", "http://peer", "secret").seconds(() -> clock.now); }
    private static void steps(FinancePeering.PeerLink link, Object steps) {
        for (Object item : (List<?>) steps) {
            var step = (List<?>) item;
            if (step.get(0).equals("ok")) link.recordOk(); else link.recordFailure(((Number) step.get(1)).doubleValue());
            assertEquals(step.get(2), link.state().name(), step.toString());
        }
    }
    @Test void theSourcePeeringTestsAndDerivedLinkControlsHold() throws Exception {
        var c = controls("peer-link"); var clock = new Clock(); clock.now = 5000;
        for (String id : List.of("source-ping-healthy", "source-ping-unhealthy")) {
            boolean healthy = (Boolean) c.get(id).get("health");
            assertEquals(c.get(id).get("expected"), link(clock).health(() -> healthy).unavailableAfter(30).build().ping().name(), id);
        }
        steps(link(clock).health(() -> false).unavailableAfter(30).build(), c.get("source-escalation").get("steps"));
        steps(link(clock).health(() -> false).unavailableAfter(30).build(), c.get("reset-and-inclusive-deadline").get("steps"));
        // source test_ensure_available_maps_state_to_retryable
        var ensure = (Map<?, ?>) c.get("source-ensure-available").get("expected");
        var available = link(clock).health(() -> true).reconnectDelay(10_000).unavailableAfter(30).build(); available.lastPing(clock.now);
        for (var state : FinancePeering.PeerState.values()) {
            available.state(state); var want = (Map<?, ?>) ensure.get(state.name());
            if (want == null) { available.ensureAvailable(); continue; }
            var error = assertThrows(FinanceClearing.PeerUnavailable.class, available::ensureAvailable);
            assertEquals(want, fields("code", error.code(), "status", (long) error.status(), "message", error.getMessage(), "details", error.view().get("details"), "retryable", error.retryable()), state.name());
        }
        // stateNow pings once the last probe is the reconnect delay old
        var timeline = c.get("state-now-reconnect-delay"); var health = new boolean[1]; var probes = new int[1];
        var link = link(clock).health(() -> { probes[0]++; return health[0]; }).reconnectDelay(2).unavailableAfter(30).build();
        assertEquals(timeline.get("initial"), link.state().name());
        for (Object item : (List<?>) timeline.get("steps")) {
            var step = (List<?>) item; clock.now = ((Number) step.get(0)).doubleValue(); health[0] = (Boolean) step.get(1); int before = probes[0];
            assertEquals(step.get(2), link.stateNow().name(), step.toString()); assertEquals(step.get(3).equals("pinged"), probes[0] > before, step.toString());
        }
        // live timing: an override wins, else the live time config, else the defaults
        var timing = c.get("live-timing"); var time = new FinanceTime(); time.update(Map.of("peer_unavailable_after_seconds", timing.get("unavailable_after_live")));
        steps(link(clock).health(() -> false).time(time).build(), timing.get("steps_live"));
        steps(link(clock).health(() -> false).time(time).unavailableAfter(((Number) timing.get("override")).intValue()).build(), timing.get("steps_override"));
        assertEquals(((Number) timing.get("default_without_config")).intValue(), link(clock).health(() -> false).build().unavailableAfter());
        // call outcomes through an injected caller
        var calls = c.get("call-outcomes");
        for (Object item : (List<?>) calls.get("cases")) {
            var row = (Map<?, ?>) item; var reply = (List<?>) row.get("reply"); var sent = new byte[1][];
            var caller = link(clock).health(() -> true).caller((method, path, body) -> { sent[0] = body; return new FinancePeering.Reply(((Number) reply.get(0)).intValue(), reply.get(1)); }).build();
            if (row.containsKey("returns")) assertEquals(row.get("returns"), caller.call("POST", "/interbank/time-config", fields("values", fields("risk_window_seconds", 12))), row.toString());
            else {
                var error = assertThrows(ServiceError.class, () -> caller.call("POST", "/interbank/time-config", fields("values", fields("risk_window_seconds", 12))));
                assertEquals(row.get("error"), fields("code", error.code(), "status", (long) error.status(), "message", error.getMessage()), row.toString());
            }
            assertEquals(calls.get("request_body"), new String(sent[0], StandardCharsets.UTF_8)); assertEquals(row.get("state"), caller.state().name(), row.toString());
        }
        var port = c.get("inbound-port-body"); var sent = new byte[1][];
        var peer = link(clock).health(() -> true).reconnectDelay(10_000).caller((method, path, body) -> { sent[0] = body; return new FinancePeering.Reply(200, Map.of("ok", true)); }).build();
        peer.state(FinancePeering.PeerState.AVAILABLE); peer.lastPing(clock.now);
        new FinancePeering.PeerBankPort(peer).inbound("acc_x", Money.of("50.00", "USD"), "TRF-1");
        assertEquals(port.get("body"), new String(sent[0], StandardCharsets.UTF_8));
    }
    @Test void theRegistryKeepsOneFilePerNodeAndSkipsWhatTheSourceSkips(@TempDir Path work) throws Exception {
        var c = controls("registry"); var dir = work.resolve("registry"); var registry = new FinancePeering.Registry(dir, () -> 1000.5, 4242);
        assertEquals(c.get("missing-directory").get("nodes"), registry.read());
        for (Object item : (List<?>) c.get("two-nodes").get("register")) { var r = (List<?>) item; registry.register((String) r.get(0), (String) r.get(1), (String) r.get(2)); }
        var nodes = registry.read(); assertEquals(c.get("two-nodes").get("aliases"), List.copyOf(nodes.keySet()));
        for (var entry : nodes.values()) assertEquals(c.get("two-nodes").get("entry_keys"), List.copyOf(entry.keySet()));
        assertEquals(fields("alias", "toBank1", "bank_code", "AURUM", "base_url", "http://127.0.0.1:8001", "pid", 4242L, "ts", 1000.5), nodes.get("toBank1"));
        for (var e : ((Map<?, ?>) c.get("skipped-files").get("files")).entrySet()) Files.writeString(dir.resolve((String) e.getKey()), (String) e.getValue());
        assertEquals(c.get("skipped-files").get("aliases"), List.copyOf(registry.read().keySet()));
        Files.writeString(dir.resolve("list.json"), "[1, 2]");
        assertThrows(IllegalArgumentException.class, registry::read);
        Files.delete(dir.resolve("list.json"));
        registry.deregister((String) c.get("deregister").get("remove")); assertEquals(c.get("deregister").get("aliases"), List.copyOf(registry.read().keySet()));
        registry.deregister((String) c.get("deregister").get("remove"));
    }
    @Test void theSourceTimeConfigTestsAndPropertiesControlsHold(@TempDir Path work) throws Exception {
        var c = controls("time-persistence");
        // source test_properties_file_round_trip
        var path = work.resolve("nested/time_toBank1.properties"); var created = FinanceTime.load(path);
        assertTrue(Files.exists(path)); assertEquals(c.get("created-defaults").get("text"), Files.readString(path, StandardCharsets.UTF_8));
        assertEquals(path.toString(), created.view().get("path"));
        created.update(Map.of("peer_ping_interval_seconds", 9)); created.acceptSync(false); created.save();
        var reloaded = FinanceTime.load(path); assertEquals(9, reloaded.seconds("peer_ping_interval_seconds")); assertFalse(reloaded.acceptSync());
        var parse = c.get("parse"); var parsed = work.resolve("parsed.properties"); Files.writeString(parsed, (String) parse.get("text"), StandardCharsets.UTF_8);
        var time = FinanceTime.load(parsed);
        for (var e : ((Map<?, ?>) parse.get("values")).entrySet()) assertEquals(((Number) e.getValue()).intValue(), time.seconds((String) e.getKey()), (String) e.getKey());
        assertEquals(parse.get("accept_time_sync"), time.acceptSync());
        for (Object item : (List<?>) c.get("invalid").get("cases")) {
            var row = (List<?>) item; var file = work.resolve("invalid.properties"); Files.writeString(file, (String) row.get(0));
            assertEquals(row.get(1), assertThrows(ServiceError.class, () -> FinanceTime.load(file)).getMessage(), (String) row.get(0));
        }
        var flags = c.get("accept-flag");
        for (String key : List.of("true", "false")) for (Object value : (List<?>) flags.get(key)) {
            var file = work.resolve("flag.properties"); Files.writeString(file, "accept_time_sync = " + value);
            assertEquals(Boolean.parseBoolean(key), FinanceTime.load(file).acceptSync(), String.valueOf(value));
        }
        new FinanceTime().save();
        // source test_timeconfig.py: live, incoming flag, validation and propagation through the ecosystem
        var eco = new FinanceEcosystem(); eco.addBank("AURUM", "Aurum", "XX", false, null);
        var a = eco.identity().register("A", "a@x.com", FinanceIdentity.Kyc.VERIFIED, Set.of("CUSTOMER"), "RETAIL", false);
        var b = eco.identity().register("B", "b@x.com", FinanceIdentity.Kyc.VERIFIED, Set.of("CUSTOMER"), "RETAIL", false);
        String src = eco.open("AURUM", a.id(), "USD", "100.00").id(), dst = eco.open("AURUM", b.id(), "USD", "0.00").id();
        eco.updateTime(Map.of("confirmation_ttl_seconds", 7), null, "self", false);
        assertEquals(7, eco.initiate(src, dst, "10", "k").view().get("ttl_seconds"));
        var sync = new FinanceEcosystem();
        assertEquals(true, sync.updateTime(Map.of("confirmation_ttl_seconds", 11), null, "self", true).get("applied")); assertEquals(11, sync.time().seconds("confirmation_ttl_seconds"));
        sync.updateTime(null, false, "self", false); var declined = sync.updateTime(Map.of("confirmation_ttl_seconds", 22), null, "self", true);
        assertEquals(false, declined.get("applied")); assertEquals("sync_declined", declined.get("reason")); assertEquals(11, sync.time().seconds("confirmation_ttl_seconds"));
        assertEquals(true, sync.updateTime(Map.of("confirmation_ttl_seconds", 33), null, "self", false).get("applied")); assertEquals(33, sync.time().seconds("confirmation_ttl_seconds"));
        for (var bad : List.of(Map.of("bogus_seconds", 5), Map.of("risk_window_seconds", 0), Map.of("risk_window_seconds", -3)))
            assertThrows(ServiceError.class, () -> new FinanceEcosystem().updateTime(bad, null, "self", false));
        var propagating = new FinanceEcosystem(); propagating.propagator(values -> fields("toBank2", "applied"));
        assertEquals(Map.of("toBank2", "applied"), propagating.updateTime(Map.of("risk_window_seconds", 12), null, "connected", false).get("propagated"));
        // R99-06: the source int() syntax, and an explicit refusal past Java int
        var units = new FinanceTime(); units.update(Map.of("risk_window_seconds", " 1_000 ", "replay_window_seconds", "٣"));
        assertEquals(1000, units.seconds("risk_window_seconds")); assertEquals(3, units.seconds("replay_window_seconds"));
        assertThrows(IllegalArgumentException.class, () -> units.update(Map.of("risk_window_seconds", "2147483648")));
        assertEquals("risk_window_seconds must be an integer number of seconds", assertThrows(ServiceError.class, () -> units.update(Map.of("risk_window_seconds", true))).getMessage());
    }
}
