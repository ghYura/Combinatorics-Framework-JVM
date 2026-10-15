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

import org.ghyura.cf.config.RunConfig;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.store.MemoryBudget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.Inflater;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The candidate export (C03) against the legacy writers it replaces: LooseFileSink's files, ShardSink's container format read
 * back by an independent decoder written from the format's description, and HandoffManifestWriter's manifest, which the
 * hand-over import reads again.
 */
class HandoffExportTest {

    @Test
    void pythonFilesKeepTheirLanguagePayloadAndMetadataThroughTheHandoff(@TempDir Path dir) throws Exception {
        var header = new HandoffExport.Header("python-export", HEADER.verdict(), HEADER.placeholders(), HEADER.customVerdicts(),
                HEADER.arguments(), Optional.of("print('preprocess')\n"), LegacyHandoff.Language.PYTHON);
        var source = new LinkedHashMap<String, String>();
        source.put("2_0_0", "if True:\n    FW_CUSTOM_VAR = 2\n");
        source.put("10_0_0", "FW_CUSTOM_VAR = 0\n");
        var written = HandoffExport.write(dir, HandoffExport.Transport.LOOSE_FILES, header, rendered(source).iterator(), BUDGET);
        var imported = LegacyHandoff.read(written.manifest());
        assertEquals(LegacyHandoff.Language.PYTHON, imported.language());
        assertEquals(List.of("10_0_0", "2_0_0"), imported.candidates().stream().map(HandoffCandidate::displayId).toList());
        assertEquals(source, imported.candidates().stream().collect(java.util.stream.Collectors.toMap(HandoffCandidate::displayId,
                HandoffCandidate::source)));
        assertEquals(HEADER.arguments(), imported.arguments());
        assertEquals(HEADER.customVerdicts(), imported.verdictNames());
        assertEquals(header.preprocess(), imported.runFirstOnce());
        assertTrue(Files.readString(written.manifest()).contains(sha256("10_0_0.py\n2_0_0.py\n")));
        assertEquals(0, BUDGET.reservedBytes());
        Files.move(dir.resolve("candidates/10_0_0.py"), dir.resolve("candidates/11_0_0.py"));
        assertEquals(ErrorCode.INPUT_SCHEMA, assertThrows(FrameworkException.class,
                () -> LegacyHandoff.read(written.manifest())).diagnostics().getFirst().code(),
                "Python verifies the source names even when the file count is unchanged");
    }

    @Test
    void everyTransportAdmitsItsManifestAndFileMetadataBeforeWritingIt(@TempDir Path dir) {
        for (var transport : HandoffExport.Transport.values()) {
            var budget = new MemoryBudget(1); Path out = dir.resolve(transport.manifestName());
            var failure = assertThrows(FrameworkException.class, () -> HandoffExport.write(out, transport, HEADER,
                    rendered(Map.of("1_0_0", "class A {}" )).iterator(), budget));
            assertEquals(ErrorCode.RESOURCE_LIMIT, failure.diagnostics().getFirst().code());
            assertFalse(Files.exists(out.resolve(HandoffExport.MANIFEST)));
            assertEquals(0, budget.reservedBytes(), "all failed-export reservations release");
        }
    }

    @Test
    void compressionBuffersMustFitTheBudgetEvenWhenThePayloadCompressesWell(@TempDir Path dir) {
        var budget = new MemoryBudget(100 * 1024); // room for the index/header, not the current in-memory deflate buffers
        var payload = new HandoffExport.Rendered("1_0_0", new byte[1024 * 1024]);
        var failure = assertThrows(FrameworkException.class, () -> HandoffExport.write(dir, HandoffExport.Transport.SHARDED,
                HEADER, List.of(payload).iterator(), budget));
        assertEquals(ErrorCode.RESOURCE_LIMIT, failure.diagnostics().getFirst().code());
        assertFalse(Files.exists(dir.resolve(HandoffExport.MANIFEST)));
        assertFalse(Files.exists(dir.resolve(".shard-spool")));
        assertEquals(0, budget.reservedBytes());
    }

    private static final MemoryBudget BUDGET = new MemoryBudget(64L << 20);
    private static final HandoffExport.Header HEADER = new HandoffExport.Header("exported", RunConfig.VerdictField.FW_CUSTOM_VAR, 6,
            Map.of(2, "LOST"), List.of("x", "y z"), Optional.of("public class P { static { } }"));

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    /** 64-bit FNV-1a over the UTF-8 id, written here from its definition. */
    private static long fnv(String id) {
        long h = 0xcbf29ce484222325L;
        for (byte b : id.getBytes(StandardCharsets.UTF_8)) {
            h = (h ^ (b & 0xff)) * 0x100000001b3L;
        }
        return h;
    }

    private static List<HandoffExport.Rendered> rendered(Map<String, String> sources) {
        List<HandoffExport.Rendered> out = new ArrayList<>();
        sources.forEach((id, text) -> out.add(new HandoffExport.Rendered(id, text.getBytes(StandardCharsets.UTF_8))));
        return out;
    }

    @Test
    void looseFilesAreTheLegacyFilesAndTheManifestImportsThemBack(@TempDir Path dir) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        for (String id : List.of("2_0_0", "10_0_0", "1_0_0")) {
            sources.put(id, "public class C" + id + " { public static int FW_CUSTOM_VAR = -1; public static void main(String[] a) { } }\n");
        }
        Path out = dir.resolve("export");
        HandoffExport.Written w = HandoffExport.write(out, HandoffExport.Transport.LOOSE_FILES, HEADER, rendered(sources).iterator(), BUDGET);
        Path files = out.resolve("candidates").toAbsolutePath().normalize();
        for (var e : sources.entrySet()) {
            assertEquals(e.getValue(), Files.readString(files.resolve(e.getKey() + ".java")), "the rendered bytes and nothing after them");
        }
        long bytes = sources.values().stream().mapToLong(s -> s.getBytes(StandardCharsets.UTF_8).length).sum();
        long index = fnv("2_0_0") ^ fnv("10_0_0") ^ fnv("1_0_0");
        assertEquals(List.of(3L, bytes, String.format("%016x", index), 3), List.of(w.candidates(), w.bytes(), w.index(), w.files()));
        Path once = out.resolve("preprocess").resolve("runmefirstonce.first").toAbsolutePath().normalize();
        String expected = "{\"protocol\":\"bundle.handoff/v2\",\"run_id\":\"exported\",\"language\":\"java\",\"candidate_transport\":\"loose-files\","
                + "\"candidate_count\":3,\"id_format\":\"<combi_id>_0_0\",\"sources\":[{\"kind\":\"dir\",\"path\":\"" + files + "\",\"sha256\":\""
                + sha256("10_0_0.java\n1_0_0.java\n2_0_0.java\n") + "\"}],\"result_target\":{\"host\":\"localhost\",\"port\":5432,"
                + "\"database\":\"inventory_e2e\",\"user\":\"postgres\"},\"result_schema_mode\":\"placeholders=6\",\"verdict_mode\":\"FW_CUSTOM_VAR\","
                + "\"custom_verdicts\":[{\"code\":2,\"message\":\"LOST\"}],\"arguments\":[\"x\",\"y z\"],\"shift\":1,\"preprocess\":\"" + once + "\","
                + "\"execution_policy_ref\":null}";
        assertEquals(expected, Files.readString(w.manifest()), "HandoffManifestWriter's fields, order and compact form");
        LegacyHandoff.Bundle back = LegacyHandoff.read(w.manifest());
        assertEquals(List.of("10_0_0", "1_0_0", "2_0_0"), back.candidates().stream().map(HandoffCandidate::displayId).toList());
        assertEquals(sources.get("10_0_0"), back.candidates().getFirst().source());
        assertEquals("exported", back.runId());
        assertEquals(Map.of(2, "LOST"), back.verdictNames());
        assertEquals(List.of("x", "y z"), back.arguments());
        assertEquals(Optional.of("public class P { static { } }"), back.runFirstOnce());
    }

    /** One record as the shard's reader sees it. */
    private record Record(String id, byte[] raw) {
    }

    /** Reads one shard from the format's description: header, records, trailer, end of file; checks every length and CRC. */
    private static List<Record> readShard(Path shard, long[] trailer) throws Exception {
        List<Record> records = new ArrayList<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Files.readAllBytes(shard)))) {
            byte[] magic = new byte[8];
            in.readFully(magic);
            assertEquals("FWSHARD1", new String(magic, StandardCharsets.US_ASCII));
            while (true) {
                int tag = in.readInt();
                if (tag == 0x46494E00) {
                    trailer[0] = in.readInt();
                    trailer[1] = in.readLong();
                    in.readFully(magic);
                    assertEquals("FWSHEND1", new String(magic, StandardCharsets.US_ASCII));
                    assertThrows(EOFException.class, in::readByte, "nothing after the trailer");
                    return records;
                }
                assertEquals(0x52454300, tag, "REC\\0");
                byte[] id = new byte[in.readInt()];
                in.readFully(id);
                long rawLength = in.readLong();
                long crc = in.readLong();
                byte[] comp = new byte[in.readInt()];
                in.readFully(comp);
                Inflater inflater = new Inflater();
                inflater.setInput(comp);
                byte[] raw = new byte[Math.toIntExact(rawLength)];
                int n = inflater.inflate(raw);
                assertTrue(inflater.finished() && n == raw.length, "the record inflates alone to its raw length");
                inflater.end();
                CRC32 check = new CRC32();
                check.update(raw);
                assertEquals(crc, check.getValue());
                records.add(new Record(new String(id, StandardCharsets.UTF_8), raw));
            }
        }
    }

    @Test
    void shardsAreTheShardSinkFormatInIdOrderAThousandRecordsEach(@TempDir Path dir) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        for (int i = 0; i < 2500; i++) {
            sources.put(i + "_0_0", ("class C" + i + " { }\n").repeat(1 + i % 3));
        }
        HandoffExport.Written w = HandoffExport.write(dir.resolve("export"), HandoffExport.Transport.SHARDED, HEADER,
                rendered(sources).iterator(), BUDGET);
        Path shards = dir.resolve("export").resolve("shards");
        assertEquals(List.of("shard-00000.fwshard", "shard-00001.fwshard", "shard-00002.fwshard"),
                Files.list(shards).map(p -> p.getFileName().toString()).sorted().toList(), "no temporary file or spool is left");
        assertFalse(Files.exists(dir.resolve("export").resolve(".shard-spool")));
        List<String> ids = new ArrayList<>();
        Map<String, String> read = new TreeMap<>();
        List<Long> counts = new ArrayList<>();
        long index = 0;
        for (int s = 0; s < 3; s++) {
            long[] trailer = new long[2];
            List<Record> records = readShard(shards.resolve(String.format("shard-%05d.fwshard", s)), trailer);
            long xor = 0;
            for (Record r : records) {
                ids.add(r.id());
                read.put(r.id(), new String(r.raw(), StandardCharsets.UTF_8));
                xor ^= fnv(r.id());
            }
            assertEquals(records.size(), trailer[0]);
            assertEquals(xor, trailer[1], "the trailer's index is the XOR of its ids' hashes");
            counts.add(trailer[0]);
            index ^= xor;
        }
        assertEquals(List.of(1000L, 1000L, 500L), counts);
        assertEquals(ids.stream().sorted().toList(), ids, "ascending id order across the shards");
        assertEquals(new TreeMap<>(sources), read, "every candidate once, its bytes intact");
        assertEquals(List.of(2500L, String.format("%016x", index), 3), List.of(w.candidates(), w.index(), w.files()));
        String manifest = Files.readString(w.manifest());
        assertTrue(manifest.contains("\"candidate_transport\":\"sharded\",\"candidate_count\":2500,"), manifest);
        assertTrue(manifest.contains("\"sha256\":\"" + sha256("shard-00000.fwshard\nshard-00001.fwshard\nshard-00002.fwshard\n") + "\""), manifest);
        FrameworkException e = assertThrows(FrameworkException.class, () -> LegacyHandoff.read(w.manifest()));
        assertEquals(ErrorCode.UNSUPPORTED_CAPABILITY, e.diagnostics().getFirst().code(), "this build reads no shards back, by name");
        byte[] first = HandoffExport.deflate(sources.get("0_0_0").getBytes(StandardCharsets.UTF_8));
        assertArrayEquals(first, java.util.Arrays.copyOfRange(Files.readAllBytes(shards.resolve("shard-00000.fwshard")), 8 + 4 + 4 + 5 + 8 + 8 + 4,
                8 + 4 + 4 + 5 + 8 + 8 + 4 + first.length), "the first record's deflated bytes follow its header");
    }

    @Test
    void aDirectoryWithFilesOrTwoCandidatesWithOneIdLeaveNoManifest(@TempDir Path dir) throws Exception {
        Files.writeString(Files.createDirectories(dir.resolve("used")).resolve("old.java"), "class Old { }", StandardCharsets.UTF_8);
        FrameworkException used = assertThrows(FrameworkException.class, () -> HandoffExport.write(dir.resolve("used"),
                HandoffExport.Transport.LOOSE_FILES, HEADER, rendered(Map.of("1_0_0", "x")).iterator(), BUDGET));
        assertEquals(ErrorCode.USAGE_INVALID_VALUE, used.diagnostics().getFirst().code());
        for (HandoffExport.Transport t : HandoffExport.Transport.values()) {
            List<HandoffExport.Rendered> twice = List.of(new HandoffExport.Rendered("1_0_0", new byte[] {1}),
                    new HandoffExport.Rendered("1_0_0", new byte[] {2}));
            Path out = dir.resolve(t.manifestName());
            FrameworkException e = assertThrows(FrameworkException.class, () -> HandoffExport.write(out, t, HEADER, twice.iterator(), BUDGET));
            assertEquals(ErrorCode.RESULT_EXPORT_FAILED, e.diagnostics().getFirst().code(), t.name());
            assertFalse(Files.exists(out.resolve(HandoffExport.MANIFEST)), "a failed export carries no manifest");
        }
        FrameworkException path = assertThrows(FrameworkException.class, () -> HandoffExport.write(dir.resolve("path"),
                HandoffExport.Transport.LOOSE_FILES, HEADER, rendered(Map.of("../escape", "x")).iterator(), BUDGET));
        assertEquals(ErrorCode.RESULT_EXPORT_FAILED, path.diagnostics().getFirst().code());
        assertEquals(ErrorCode.USAGE_INVALID_VALUE, assertThrows(FrameworkException.class, () -> HandoffExport.Transport.of("grpc"))
                .diagnostics().getFirst().code(), "the gRPC transport is a network hop");
    }
}
