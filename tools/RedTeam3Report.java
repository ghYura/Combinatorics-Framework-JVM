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

import org.ghyura.cf.fixtures.redteam.RedTeam3;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Json;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Offline scoring of captured redteam3 endpoint observations; never invokes or trains a victim.
 * Usage: java -cp fw-app.jar:fw-sut-fixtures.jar tools/RedTeam3Report.java INPUT.json OUTPUT.json
 * Input schema cf.redteam3-observations/v1 has records of variant (ENDPOINT/CANDIDATE),
 * combo_id (0..63), pid and replies in original request order. A reply has body (JSON value),
 * elapsed_ms (finite nonnegative number), failed (boolean). Supply actual observations;
 * a missing implementation must not be presented as an observed request failure. */
public final class RedTeam3Report {
    private RedTeam3Report() { }
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("input observations JSON and output report JSON required");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (input.toAbsolutePath().normalize().equals(output.toAbsolutePath().normalize())
                || Files.exists(output) && Files.isSameFile(input, output)) throw new IllegalArgumentException("report must not replace input observations");
        byte[] bytes;
        try (var in = Files.newInputStream(input)) { bytes = in.readNBytes(2 * 1024 * 1024 + 1); }
        if (bytes.length > 2 * 1024 * 1024) throw new IllegalArgumentException("observations exceed 2 MiB");
        String text = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        var root = object(Json.parseBinary64(text, input.toString()), Set.of("schema", "records"));
        if (!"cf.redteam3-observations/v1".equals(root.get("schema"))) throw new IllegalArgumentException("unknown observations schema");
        if (!(root.get("records") instanceof List<?> records) || records.isEmpty() || records.size() > 128)
            throw new IllegalArgumentException("1..128 observed source scenarios required");
        var seen = new HashSet<String>(); var rows = new ArrayList<Map<String,Object>>(); var endpoint = new ArrayList<RedTeam3.Result>();
        for (var value : records) {
            var record = object(value, Set.of("variant", "combo_id", "pid", "replies"));
            var variant = RedTeam3.Variant.valueOf((String) record.get("variant"));
            var coordinate = new RedTeam3.Coordinate(integer(record.get("combo_id")));
            int pid = integer(record.get("pid"));
            if (!seen.add(variant + "/" + coordinate.comboId())) throw new IllegalArgumentException("repeated variant/source coordinate");
            if (!(record.get("replies") instanceof List<?> replies) || replies.size() > 4) throw new IllegalArgumentException("at most four ordered replies required");
            var observations = new ArrayList<RedTeam3.Reply>();
            for (var reply : replies) {
                var r = object(reply, Set.of("body", "elapsed_ms", "failed"));
                if (!(r.get("elapsed_ms") instanceof Number latency) || !(r.get("failed") instanceof Boolean failed))
                    throw new IllegalArgumentException("numeric elapsed_ms and boolean failed required");
                observations.add(new RedTeam3.Reply(r.get("body"), latency.doubleValue(), failed));
            }
            var result = RedTeam3.replay(variant, coordinate, pid, observations);
            rows.add(new TreeMap<>(Map.of("variant", variant.name(), "combo_id", coordinate.comboId(), "pid", pid,
                    "uid", result.plan().uid(), "marker", result.plan().marker(), "request_count", result.plan().calls().size(),
                    "response", result.response(), "metrics", result.metrics(), "attributes", result.attributes())));
            if (variant == RedTeam3.Variant.ENDPOINT) endpoint.add(result);
        }
        var summary = new LinkedHashMap<String,Object>();
        RedTeam3.summarize(endpoint).forEach((k,v) -> summary.put(k, Map.of("breaches", v.breaches(), "applicable", v.applicable(), "rate_percent", v.ratePercent())));
        var report = new TreeMap<String,Object>(); report.put("schema", "cf.redteam3-report/v1");
        report.put("scope", "captured endpoint observations; no victim execution or learned-model claim");
        report.put("input_sha256", Canonical.sha256Hex(text)); report.put("records", rows); report.put("endpoint_families", summary);
        AtomicFiles.write(output, Json.write(report).getBytes(StandardCharsets.UTF_8));
        System.out.println(rows.size() + " captured source observations scored");
    }
    private static int integer(Object value) {
        if (!(value instanceof Long n)) throw new IllegalArgumentException("source id and PID must be integers");
        return Math.toIntExact(n);
    }
    private static Map<?,?> object(Object value, Set<String> keys) {
        if (!(value instanceof Map<?,?> map) || !map.keySet().equals(keys)) throw new IllegalArgumentException("exact observation fields required: " + keys);
        return map;
    }
}
