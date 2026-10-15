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
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.store.MemoryBudget;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Writes rendered candidates as a legacy hand-over (audit EX01, capability C03): what the legacy Reader left for the legacy
 * executor. The candidates go either as loose files, one {@code <display id>.java} each holding exactly the rendered bytes
 * (LooseFileSink in FILES_MODE, where nothing follows the payload), or packed into DEFLATE shards in the ShardSink format: a
 * {@code FWSHARD1} header, records in ascending id order ({@code REC\0}, id length, UTF-8 id, raw length, CRC-32 of the raw
 * bytes, compressed length, the bytes deflated alone at the default level), at most 1000 records or about 8 MiB compressed
 * per shard, and a trailer ({@code FIN\0}, record count, XOR of the ids' 64-bit FNV-1a hashes, {@code FWSHEND1}), all
 * big-endian, each shard written beside its name and moved into place. The bundle.handoff/v2 manifest comes last, written
 * atomically and field for field as HandoffManifestWriter wrote it, so an export that failed half way carries no manifest.
 * A source's digest is the SHA-256 of its sorted file names, one per line, as the legacy computed it; the legacy convenience
 * shard-manifest.txt is not written, since the shards are the source of truth. The results database the manifest names is
 * the legacy shipped default; this build writes none.
 */
public final class HandoffExport {

    /** The candidates' id format the legacy recorded. */
    public static final String ID_FORMAT = "<combi_id>_0_0";
    /** ShardSink.DEFAULT_MAX_RECORDS: candidates per shard. */
    public static final int SHARD_RECORDS = 1000;
    /** ShardSink.DEFAULT_MAX_BYTES: approximate compressed bytes per shard. */
    public static final long SHARD_BYTES = 8L * 1024 * 1024;
    /** The manifest's file name. */
    public static final String MANIFEST = "manifest.json";

    // the legacy shipped results database (generator_trunk/config/reader.fw.properties: results.db.host/port/user, db.name)
    static final String RESULT_HOST = "localhost";
    static final int RESULT_PORT = 5432;
    static final String RESULT_DATABASE = "inventory_e2e";
    static final String RESULT_USER = "postgres";

    private static final byte[] FILE_MAGIC = "FWSHARD1".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] FILE_TRAILER = "FWSHEND1".getBytes(StandardCharsets.US_ASCII);
    private static final int REC_MAGIC = 0x52454300;
    private static final int END_MAGIC = 0x46494E00;

    /** How the candidates travel, by their manifest names. */
    public enum Transport {
        LOOSE_FILES("loose-files", "candidates"), SHARDED("sharded", "shards");

        private final String manifestName;
        private final String directory;

        Transport(String manifestName, String directory) {
            this.manifestName = manifestName;
            this.directory = directory;
        }

        /** The candidate_transport value. */
        public String manifestName() {
            return manifestName;
        }

        /** @throws FrameworkException USAGE_INVALID_VALUE for any other name */
        public static Transport of(String name) {
            for (Transport t : values()) {
                if (t.manifestName.equals(name)) {
                    return t;
                }
            }
            throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "--transport takes loose-files or sharded, got '"
                    + name + "' (the gRPC transport is a network hop this build does not make)", SourceLocation.NONE, "option", "--transport"));
        }
    }

    /**
     * What the manifest says besides the candidates.
     *
     * @param runId          the run id (cf.run.id)
     * @param verdict        FW_VAR or FW_CUSTOM_VAR
     * @param placeholders   the legacy results insert template's parameter count (result_schema_mode)
     * @param customVerdicts FW_CUSTOM_VAR codes and messages
     * @param arguments      the candidates' arguments
     * @param preprocess     the source run once before the candidates, if any
     */
    public record Header(String runId, RunConfig.VerdictField verdict, int placeholders, Map<Integer, String> customVerdicts,
                         List<String> arguments, Optional<String> preprocess, LegacyHandoff.Language language) {
        public Header {
            Objects.requireNonNull(runId, "runId");
            Objects.requireNonNull(verdict, "verdict");
            customVerdicts = Collections.unmodifiableMap(new TreeMap<>(customVerdicts));
            arguments = List.copyOf(arguments);
            Objects.requireNonNull(preprocess, "preprocess");
            Objects.requireNonNull(language, "language");
        }

        /** Existing Java callers keep the original constructor and byte format. */
        public Header(String runId, RunConfig.VerdictField verdict, int placeholders, Map<Integer, String> customVerdicts,
                      List<String> arguments, Optional<String> preprocess) {
            this(runId, verdict, placeholders, customVerdicts, arguments, preprocess, LegacyHandoff.Language.JAVA);
        }
    }

    /** A rendered candidate: its legacy display id and source bytes. */
    public record Rendered(String id, byte[] source) {
        public Rendered {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(source, "source");
        }
    }

    /**
     * What was written.
     *
     * @param files the candidate files or shards
     * @param bytes the candidates' raw bytes, as the legacy sink summary counted them
     * @param index the XOR of the ids' FNV-1a hashes, the legacy sink summary's order-independent index
     */
    public record Written(Transport transport, Path manifest, long candidates, long bytes, String index, int files) {
    }

    private HandoffExport() {
    }

    /**
     * Writes the hand-over into {@code directory}, which must not exist or be empty.
     *
     * @throws FrameworkException USAGE_INVALID_VALUE for a directory that holds files, RESOURCE_LIMIT when the export's
     *                            metadata or workspaces do not fit the budget, RESULT_EXPORT_FAILED for a duplicate or
     *                            unusable id or a failed write
     */
    public static Written write(Path directory, Transport transport, Header header, Iterator<Rendered> candidates, MemoryBudget budget) {
        Path dir = directory.toAbsolutePath().normalize();
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> list = Files.list(dir)) {
                    if (list.findAny().isPresent()) {
                        throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "the export directory " + dir
                                + " is not empty: an export never mixes with earlier files (the legacy refused a manifest when stale "
                                + "candidates were found)", SourceLocation.of(dir.toString()), "path", dir.toString()));
                    }
                }
            }
            try (ExportMetadata metadata = new ExportMetadata(header, dir, budget)) {
                Path source = dir.resolve(transport.directory);
                Files.createDirectories(source);
                Written written = transport == Transport.LOOSE_FILES ? looseFiles(dir, source, candidates, metadata, header.language().extension())
                        : shards(dir, source, candidates, budget, metadata);
                String preprocess = null;
                if (header.preprocess().isPresent()) {
                    Path file = dir.resolve("preprocess").resolve("runmefirstonce.first");
                    Files.createDirectories(file.getParent());
                    Files.write(file, header.preprocess().get().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
                    preprocess = file.toString();
                }
                String json = manifest(header, transport, written.candidates(), source.toString(),
                        metadata.digest(), preprocess);
                Path manifest = dir.resolve(MANIFEST);
                AtomicFiles.write(manifest, json.getBytes(StandardCharsets.UTF_8));
                return new Written(transport, manifest, written.candidates(), written.bytes(), written.index(), written.files());
            }
        } catch (IOException e) {
            throw failed(dir, e.getMessage());
        }
    }

    /** Holds the names until their sorted legacy digest is written; accounts for strings, sorting and encoding copies. */
    private static final class ExportMetadata implements AutoCloseable {
        private final MemoryBudget budget;
        private final MemoryBudget.Lease header;
        private final List<MemoryBudget.Lease> namesLeases = new ArrayList<>();
        private final List<String> names = new ArrayList<>();

        ExportMetadata(Header h, Path directory, MemoryBudget budget) {
            this.budget = budget;
            long characters = h.runId().length() + 8L * directory.toString().length();
            for (String argument : h.arguments()) characters += argument.length() + 8L;
            for (String message : h.customVerdicts().values()) characters += message.length() + 64L;
            characters += h.preprocess().map(String::length).orElse(0);
            // Fixed I/O workspaces plus escaped manifest/preprocess strings and their UTF-8 copies.
            header = budget.reserve(32768L + 24L * characters, "candidate export manifest, preprocess and I/O workspaces");
        }

        String add(String stem, String suffix) {
            namesLeases.add(budget.reserve(256L + 24L * (stem.length() + (long) suffix.length()),
                    "candidate export file names and sorted source digest"));
            String name = stem + suffix;
            names.add(name);
            return name;
        }

        String digest() {
            names.sort(String::compareTo);
            StringBuilder lines = new StringBuilder();
            for (String name : names) lines.append(name).append('\n');
            return Canonical.sha256Hex(lines.toString().getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public void close() {
            namesLeases.forEach(MemoryBudget.Lease::close);
            header.close();
        }
    }

    private static Written looseFiles(Path dir, Path source, Iterator<Rendered> candidates, ExportMetadata metadata, String extension) throws IOException {
        long count = 0, bytes = 0, index = 0;
        while (candidates.hasNext()) {
            Rendered c = candidates.next();
            Path file = source.resolve(metadata.add(fileName(dir, c.id()), extension));
            if (Files.exists(file)) {
                throw failed(dir, "two candidates have the id " + c.id());
            }
            Files.write(file, c.source(), StandardOpenOption.CREATE_NEW);
            count++;
            bytes += c.source().length;
            index ^= fnv1a64(c.id());
        }
        return new Written(Transport.LOOSE_FILES, null, count, bytes, String.format("%016x", index), Math.toIntExact(count));
    }

    /** One spooled record: where its compressed bytes are and what the shard header says of it. */
    private record Entry(String id, long hash, long offset, int compressed, long raw, long crc) {
    }

    private static Written shards(Path dir, Path source, Iterator<Rendered> candidates, MemoryBudget budget, ExportMetadata metadata) throws IOException {
        Path spool = dir.resolve(".shard-spool");
        List<Entry> entries = new ArrayList<>();
        List<MemoryBudget.Lease> leases = new ArrayList<>();
        try {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(spool, StandardOpenOption.CREATE_NEW))) {
                long offset = 0;
                while (candidates.hasNext()) {
                    Rendered c = candidates.next();
                    fileName(dir, c.id());
                    leases.add(budget.reserve(192L + 2L * c.id().length(), "the shard index entry of candidate " + c.id()));
                    CRC32 crc = new CRC32();
                    crc.update(c.source());
                    // ByteArrayOutputStream growth, the returned copy and the deflater's staging buffer coexist.
                    var compression = budget.reserve(65536L + 4L * c.source().length, "candidate export compression buffers");
                    try {
                        byte[] comp = deflate(c.source());
                        out.write(comp);
                        entries.add(new Entry(c.id(), fnv1a64(c.id()), offset, comp.length, c.source().length, crc.getValue()));
                        offset += comp.length;
                    } finally {
                        compression.close();
                    }
                }
            }
            entries.sort(Comparator.comparing(Entry::id).thenComparingLong(Entry::crc));
            for (int i = 1; i < entries.size(); i++) {
                if (entries.get(i).id().equals(entries.get(i - 1).id())) {
                    throw failed(dir, "two candidates have the id " + entries.get(i).id());
                }
            }
            long bytes = 0, index = 0;
            int shards = 0;
            try (RandomAccessFile in = new RandomAccessFile(spool.toFile(), "r")) {
                int from = 0;
                while (from < entries.size()) {
                    String name = metadata.add(String.format("shard-%05d", shards), ".fwshard");
                    Path tmp = source.resolve(name + ".tmp");
                    int records = 0;
                    long compressed = 0, xor = 0;
                    try (DataOutputStream shard = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE_NEW)))) {
                        shard.write(FILE_MAGIC);
                        while (from < entries.size() && records < SHARD_RECORDS && compressed < SHARD_BYTES) {
                            Entry e = entries.get(from++);
                            var transfer = budget.reserve(e.compressed() + 3L * e.id().length() + 64L, "candidate export shard transfer buffers");
                            try {
                                byte[] comp = new byte[e.compressed()];
                                in.seek(e.offset());
                                in.readFully(comp);
                                byte[] id = e.id().getBytes(StandardCharsets.UTF_8);
                                shard.writeInt(REC_MAGIC);
                                shard.writeInt(id.length);
                                shard.write(id);
                                shard.writeLong(e.raw());
                                shard.writeLong(e.crc());
                                shard.writeInt(e.compressed());
                                shard.write(comp);
                                records++;
                                compressed += e.compressed();
                                xor ^= e.hash();
                                bytes += e.raw();
                            } finally {
                                transfer.close();
                            }
                        }
                        shard.writeInt(END_MAGIC);
                        shard.writeInt(records);
                        shard.writeLong(xor);
                        shard.write(FILE_TRAILER);
                    }
                    Files.move(tmp, source.resolve(name), StandardCopyOption.ATOMIC_MOVE);
                    index ^= xor;
                    shards++;
                }
            }
            return new Written(Transport.SHARDED, null, entries.size(), bytes, String.format("%016x", index), shards);
        } finally {
            leases.forEach(MemoryBudget.Lease::close);
            Files.deleteIfExists(spool);
        }
    }

    /** The manifest, field for field and in the order HandoffManifestWriter.toJson wrote it. */
    static String manifest(Header header, Transport transport, long count, String sourcePath, String sourceDigest, String preprocess) {
        StringBuilder sb = new StringBuilder(1024);
        sb.append('{');
        field(sb, "protocol", LegacyHandoff.PROTOCOL, true);
        field(sb, "run_id", header.runId(), true);
        field(sb, "language", header.language().token(), true);
        field(sb, "candidate_transport", transport.manifestName(), true);
        raw(sb, "candidate_count", Long.toString(count), true);
        field(sb, "id_format", ID_FORMAT, true);
        sb.append("\"sources\":[{");
        field(sb, "kind", "dir", true);
        field(sb, "path", sourcePath, true);
        field(sb, "sha256", sourceDigest, false);
        sb.append("}],");
        sb.append("\"result_target\":{");
        field(sb, "host", RESULT_HOST, true);
        raw(sb, "port", Integer.toString(RESULT_PORT), true);
        field(sb, "database", RESULT_DATABASE, true);
        field(sb, "user", RESULT_USER, false);
        sb.append("},");
        field(sb, "result_schema_mode", "placeholders=" + header.placeholders(), true);
        field(sb, "verdict_mode", header.verdict().name(), true);
        sb.append("\"custom_verdicts\":[");
        boolean first = true;
        for (Map.Entry<Integer, String> v : header.customVerdicts().entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('{');
            raw(sb, "code", Integer.toString(v.getKey()), true);
            field(sb, "message", v.getValue(), false);
            sb.append('}');
        }
        sb.append("],");
        sb.append("\"arguments\":[");
        for (int i = 0; i < header.arguments().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(header.arguments().get(i)));
        }
        sb.append("],");
        raw(sb, "shift", "1", true);
        field(sb, "preprocess", preprocess, true);
        raw(sb, "execution_policy_ref", "null", false);
        sb.append('}');
        return sb.toString();
    }

    private static void field(StringBuilder sb, String name, String value, boolean comma) {
        sb.append(quote(name)).append(':').append(value == null ? "null" : quote(value));
        if (comma) sb.append(',');
    }

    private static void raw(StringBuilder sb, String name, String value, boolean comma) {
        sb.append(quote(name)).append(':').append(value);
        if (comma) sb.append(',');
    }

    /** HandoffManifestWriter.quote. */
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 2);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append('"').toString();
    }

    /** An id is a file name: never empty, never a path. */
    private static String fileName(Path dir, String id) {
        if (id.isEmpty() || id.contains("/") || id.contains("\\") || id.equals(".") || id.equals("..") || id.indexOf('\0') >= 0) {
            throw failed(dir, "the candidate id '" + id + "' cannot name a file");
        }
        return id;
    }

    /** ShardSink.deflate: one record, the default level, the zlib wrapper. */
    static byte[] deflate(byte[] data) {
        Deflater def = new Deflater(Deflater.DEFAULT_COMPRESSION, false);
        try {
            def.setInput(data);
            def.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16, data.length / 2));
            byte[] buf = new byte[8192];
            while (!def.finished()) {
                out.write(buf, 0, def.deflate(buf));
            }
            return out.toByteArray();
        } finally {
            def.end();
        }
    }

    /** The 64-bit FNV-1a hash of the UTF-8 id, as both legacy sinks computed it. */
    static long fnv1a64(String s) {
        long h = 0xcbf29ce484222325L;
        for (byte x : s.getBytes(StandardCharsets.UTF_8)) {
            h ^= (x & 0xffL);
            h *= 0x100000001b3L;
        }
        return h;
    }

    private static FrameworkException failed(Path dir, String why) {
        return new FrameworkException(Diagnostic.error(ErrorCode.RESULT_EXPORT_FAILED, "the candidate export into " + dir + " failed: " + why
                + "; no manifest was written", SourceLocation.of(dir.toString()), "path", dir.toString()));
    }
}
