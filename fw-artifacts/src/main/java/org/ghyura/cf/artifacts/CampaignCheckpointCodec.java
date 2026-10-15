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

package org.ghyura.cf.artifacts;

import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.results.CampaignCheckpoint;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.store.MemoryBudget;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Schema 1: a strict XML envelope, a SHA-256 of its base64 payload and exact UTF-16 code units of a JSON state document. */
public final class CampaignCheckpointCodec {
    private static final Pattern DOCUMENT = Pattern.compile("\\A<\\?xml version=\"1.0\" encoding=\"UTF-8\"\\?>\\n"
            + "<cf-campaign-checkpoint schema=\"([0-9]+)\" payloadSha256=\"([0-9a-f]{64})\">\\n"
            + "<payload>([A-Za-z0-9+/=]+)</payload>\\n</cf-campaign-checkpoint>\\n\\z");

    private CampaignCheckpointCodec() { }

    /** Root dispatch only; a recognized campaign still must pass decode's complete envelope and checksum checks. */
    public static boolean isCampaign(Path path) {
        try (var input = Files.newInputStream(path)) {
            String prefix = new String(input.readNBytes(512), StandardCharsets.US_ASCII);
            return Pattern.compile("\\A\\s*(?:<\\?xml[^?]*\\?>\\s*)?<cf-campaign-checkpoint\\b").matcher(prefix).find();
        } catch (IOException | RuntimeException e) { throw corrupt(path.toString(), "it cannot be read: " + e.getMessage()); }
    }

    public static void write(Path path, CampaignCheckpoint checkpoint) {
        write(path, checkpoint, CheckpointMemory.defaults());
    }

    @SuppressWarnings("try")
    public static void write(Path path, CampaignCheckpoint checkpoint, MemoryBudget budget) {
        try (MemoryBudget.Lease lease = budget.reserve(CheckpointMemory.campaignEncodeBytes(checkpoint), "campaign checkpoint output")) {
            AtomicFiles.write(path, encodeBody(checkpoint));
        }
        catch (IOException e) { throw new UncheckedIOException("cannot write campaign checkpoint " + path, e); }
    }

    public static CampaignCheckpoint read(Path path) {
        return read(path, CheckpointMemory.defaults());
    }

    public static CampaignCheckpoint read(Path path, MemoryBudget budget) {
        try { return CheckpointMemory.read(path, true, budget, bytes -> decodeBody(bytes, path.toString())); }
        catch (IOException e) { throw corrupt(path.toString(), "it cannot be read: " + e.getMessage()); }
    }

    public static byte[] encode(CampaignCheckpoint c) {
        return encode(c, CheckpointMemory.defaults());
    }

    @SuppressWarnings("try")
    public static byte[] encode(CampaignCheckpoint c, MemoryBudget budget) {
        try (MemoryBudget.Lease lease = budget.reserve(CheckpointMemory.campaignEncodeBytes(c), "campaign checkpoint encoding")) {
            return encodeBody(c);
        }
    }

    private static byte[] encodeBody(CampaignCheckpoint c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scenarioPath", c.scenarioPath()); m.put("baseScenarioDigest", c.baseScenarioDigest());
        m.put("configuration", c.configuration()); m.put("configDigest", c.configDigest()); m.put("strategy", c.strategy());
        m.put("iterationsRequested", (long) c.iterationsRequested()); m.put("stopWhenStable", (long) c.stopWhenStable());
        m.put("explorationFloor", Double.toHexString(c.explorationFloor())); m.put("minWinnerSupport", (long) c.minWinnerSupport());
        m.put("completedIterations", c.completedIterations()); m.put("inputSeedDigest", c.inputSeedDigest());
        m.put("nextSeedJson", c.nextSeedJson()); m.put("fullSpace", c.fullSpace()); m.put("previousFront", c.previousFront());
        m.put("stableTransitions", (long) c.stableTransitions()); m.put("iteration", (long) c.iteration());
        m.put("childScenarioDigest", c.childScenarioDigest());
        m.put("currentRun", c.currentRun().map(cp -> Base64.getEncoder().encodeToString(CheckpointCodec.encodeBody(cp))).orElse(""));
        m.put("status", c.status()); m.put("reason", c.reason()); m.put("diagnostics", c.diagnostics());
        String json = Json.write(m);
        byte[] head = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cf-campaign-checkpoint schema=\"1\" payloadSha256=\""
                .getBytes(StandardCharsets.US_ASCII);
        byte[] middle = "\">\n<payload>".getBytes(StandardCharsets.US_ASCII);
        byte[] tail = "</payload>\n</cf-campaign-checkpoint>\n".getBytes(StandardCharsets.US_ASCII);
        int length = Math.toIntExact(CheckpointCodec.base64Length(2L * json.length()));
        int start = head.length + 64 + middle.length;
        byte[] out = new byte[Math.addExact(Math.addExact(start, length), tail.length)];
        java.nio.ByteBuffer target = java.nio.ByteBuffer.wrap(out, start, length);
        CheckpointCodec.base64Utf16(json, b -> target.put((byte) b));
        try {
            java.security.MessageDigest hash = java.security.MessageDigest.getInstance("SHA-256");
            hash.update(out, start, length);
            System.arraycopy(head, 0, out, 0, head.length);
            byte[] digest = java.util.HexFormat.of().formatHex(hash.digest()).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(digest, 0, out, head.length, 64);
            System.arraycopy(middle, 0, out, head.length + 64, middle.length);
            System.arraycopy(tail, 0, out, start + length, tail.length);
            return out;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static CampaignCheckpoint decode(byte[] bytes, String origin) {
        return decode(bytes, origin, CheckpointMemory.defaults());
    }

    @SuppressWarnings("try")
    public static CampaignCheckpoint decode(byte[] bytes, String origin, MemoryBudget budget) {
        try (MemoryBudget.Lease lease = budget.reserve(CheckpointMemory.decodeBytes(bytes, true), "campaign checkpoint decoding")) {
            return decodeBody(bytes, origin);
        }
    }

    private static CampaignCheckpoint decodeBody(byte[] bytes, String origin) {
        try {
            Matcher envelope = DOCUMENT.matcher(new String(bytes, StandardCharsets.ISO_8859_1));
            if (!envelope.matches() || !envelope.group(1).equals("1")) throw new IllegalArgumentException("invalid envelope or schema");
            String payload = envelope.group(3);
            if (!Canonical.sha256Hex(payload).equals(envelope.group(2))) throw new IllegalArgumentException("payload SHA-256 mismatch");
            byte[] units = Base64.getDecoder().decode(payload);
            if (units.length % 2 != 0) throw new IllegalArgumentException("incomplete UTF-16 code unit");
            char[] chars = new char[units.length / 2];
            for (int i = 0; i < chars.length; i++) chars[i] = (char) (((units[2 * i] & 255) << 8) | (units[2 * i + 1] & 255));
            if (!(Json.parse(new String(chars), origin) instanceof Map<?, ?> m) || m.size() != 21)
                throw new IllegalArgumentException("invalid state document");
            if (!(m.get("configuration") instanceof Map<?, ?> config)) throw new IllegalArgumentException("missing configuration");
            Map<String, String> configuration = new TreeMap<>();
            config.forEach((k, v) -> configuration.put((String) k, (String) v));
            String run = text(m, "currentRun");
            Optional<Checkpoint> current = run.isEmpty() ? Optional.empty()
                    : Optional.of(CheckpointCodec.decodeBody(Base64.getDecoder().decode(run), origin + " current run"));
            CampaignCheckpoint c = new CampaignCheckpoint(text(m, "scenarioPath"), text(m, "baseScenarioDigest"), configuration,
                    text(m, "strategy"), integer(m, "iterationsRequested"), integer(m, "stopWhenStable"),
                    Double.parseDouble(text(m, "explorationFloor")), integer(m, "minWinnerSupport"), strings(m, "completedIterations"),
                    text(m, "inputSeedDigest"), text(m, "nextSeedJson"), text(m, "fullSpace"), strings(m, "previousFront"),
                    integer(m, "stableTransitions"), integer(m, "iteration"), text(m, "childScenarioDigest"), current,
                    text(m, "status"), text(m, "reason"), strings(m, "diagnostics"));
            if (!c.configDigest().equals(text(m, "configDigest"))) throw new IllegalArgumentException("configuration digest mismatch");
            return c;
        } catch (FrameworkException e) {
            if (e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)) throw e;
            throw corrupt(origin, e.getMessage());
        } catch (RuntimeException e) { throw corrupt(origin, e.getMessage()); }
    }

    private static String text(Map<?, ?> m, String key) {
        if (m.get(key) instanceof String text) return text;
        throw new IllegalArgumentException("missing text " + key);
    }

    private static int integer(Map<?, ?> m, String key) {
        if (m.get(key) instanceof Long value) return Math.toIntExact(value);
        throw new IllegalArgumentException("missing integer " + key);
    }

    private static List<String> strings(Map<?, ?> m, String key) {
        if (m.get(key) instanceof List<?> list) return list.stream().map(String.class::cast).toList();
        throw new IllegalArgumentException("missing list " + key);
    }

    private static FrameworkException corrupt(String origin, String why) {
        return new FrameworkException(Diagnostic.error(ErrorCode.CHECKPOINT_CORRUPT,
                "the campaign checkpoint " + origin + " cannot be resumed: " + why + "; nothing was resumed", SourceLocation.of(origin)));
    }
}
