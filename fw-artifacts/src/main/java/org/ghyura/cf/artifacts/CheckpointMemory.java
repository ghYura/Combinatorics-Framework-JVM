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

import org.ghyura.cf.store.MemoryBudget;
import org.ghyura.cf.store.MemorySizes;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;
import java.util.Map;
import org.ghyura.cf.results.CampaignCheckpoint;

/** Admission before any whole-document buffer, parser tree, base64 expansion or nested child codec is allocated. */
final class CheckpointMemory {
    private CheckpointMemory() { }

    /** Convenience APIs have a bounded workspace even when no live run supplies a budget. */
    static MemoryBudget defaults() {
        return new MemoryBudget(Math.max(1, Math.min(128L << 20, Runtime.getRuntime().maxMemory() / 4)));
    }

    /** Count serialized occurrences, including escaping and a nested run, without constructing JSON or Base64. */
    static long campaignEncodeBytes(CampaignCheckpoint c) {
        long chars = 4096; // fixed keys, numeric fields, indentation and envelope
        for (String s : new String[] {c.scenarioPath(), c.baseScenarioDigest(), c.strategy(), c.inputSeedDigest(),
                c.nextSeedJson(), c.fullSpace(), c.childScenarioDigest(), c.status(), c.reason()}) chars += jsonChars(s);
        for (var e : c.configuration().entrySet()) chars += 32 + jsonChars(e.getKey()) + jsonChars(e.getValue());
        for (var list : java.util.List.of(c.completedIterations(), c.previousFront(), c.diagnostics()))
            for (String s : list) chars += 16 + jsonChars(s);
        long child = 0;
        if (c.currentRun().isPresent()) {
            CheckpointCodec.Size size = CheckpointCodec.measure(c.currentRun().get());
            long bytes = size.utf8Bytes() + 1024;
            chars += CheckpointCodec.base64Length(bytes);
            child = MemorySizes.add(size.reservation(), MemorySizes.multiply(4, bytes));
        }
        if (CheckpointCodec.base64Length(2 * chars) > Integer.MAX_VALUE - 1024L)
            throw MemorySizes.limit("checkpoint exceeds the supported encoding size");
        // JSON builder growth/widening, immutable JSON, final Base64 envelope, nested encoding and digest workspace.
        // Base64 is emitted straight into the envelope; there is no full UTF-16 byte array or payload String.
        return MemorySizes.add(65_536, MemorySizes.add(MemorySizes.multiply(12, chars),
                MemorySizes.add(child, configurationWorkspace(c.configuration()))));
    }

    private static long jsonChars(String s) {
        long length = 2;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            length += switch (ch) {
                case '"', '\\', '\n', '\r', '\t', '\b', '\f' -> 2;
                default -> ch < 0x20 ? 6 : 1;
            };
        }
        return length;
    }

    /** Canonical escaping, sorting, builder growth and digest copies for the configuration alone. */
    static long configurationWorkspace(Map<String, String> config) {
        long chars = 0;
        for (var e : config.entrySet()) chars += (long) e.getKey().length() + e.getValue().length() + 2;
        return MemorySizes.add(MemorySizes.multiply(80, chars), MemorySizes.multiply(128, config.size()));
    }

    /**
     * A run checkpoint's encoding from its measured payload (CheckpointCodec writes it twice, counting first), so no
     * worst-case guess: the builder of exactly {@code chars}, one byte each, or up to three while a builder that met a
     * character above Latin-1 widens to UTF-16, and the one output array of header, UTF-8 payload and trailer.
     */
    static long encodeBytes(long chars, long utf8Bytes, boolean wide) {
        if (chars > Integer.MAX_VALUE - 1024L || utf8Bytes > Integer.MAX_VALUE - 1024L)
            throw MemorySizes.limit("checkpoint exceeds the supported encoding size");
        return MemorySizes.add(MemorySizes.add(65_536, MemorySizes.multiply(wide ? 3 : 1, chars)), MemorySizes.add(1024, utf8Bytes));
    }

    /** Buffer/text expansion plus explicit DOM/attribute overhead; output-heavy and node-heavy files differ. */
    static long decodeBytes(byte[] bytes, boolean campaign) {
        if (campaign) {
            // Outer Base64/UTF-16, JSON maps/lists/strings and inner Base64/XML are simultaneously live.
            // The inner XML occupies at most 9/32 of the outer file. Include dense nodes and config digest copies.
            return MemorySizes.add(65_536, MemorySizes.multiply(32, bytes.length));
        }
        long nodes = 0, attributes = 0, configBytes = 0;
        boolean quoted = false, config = false, tag = false;
        byte quote = 0;
        for (int i = 0; i < bytes.length; i++) {
            byte b = bytes[i];
            if (!quoted && b == '<') {
                tag = true;
                nodes++;
                if (starts(bytes, i, "<config>")) config = true;
                if (starts(bytes, i, "</config>")) config = false;
            }
            if (config) configBytes++;
            if (quoted) {
                if (b == quote) quoted = false;
            } else if (tag && (b == '"' || b == '\'')) {
                quote = b;
                quoted = true;
            } else if (tag && b == '=') attributes++;
            else if (b == '>') tag = false;
        }
        return MemorySizes.add(65_536, MemorySizes.add(MemorySizes.multiply(12, bytes.length),
                MemorySizes.add(MemorySizes.multiply(1024, nodes), MemorySizes.add(MemorySizes.multiply(256, attributes),
                        MemorySizes.multiply(32, configBytes)))));
    }

    private static boolean starts(byte[] bytes, int offset, String literal) {
        if (offset + literal.length() > bytes.length) return false;
        for (int i = 0; i < literal.length(); i++) if (bytes[offset + i] != literal.charAt(i)) return false;
        return true;
    }

    @SuppressWarnings("try")
    static <T> T read(Path path, boolean campaign, MemoryBudget budget, Function<byte[], T> decoder) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(path)) {
            long length = channel.size();
            if (length > Integer.MAX_VALUE - 16L) throw MemorySizes.limit("checkpoint exceeds the supported document size");
            try (MemoryBudget.Lease lease = budget.reserve(length, "checkpoint input")) {
                byte[] bytes = new byte[(int) length];
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    if (channel.read(buffer) < 0) throw new IOException("checkpoint was truncated while reading");
                }
                if (channel.read(ByteBuffer.allocate(1)) != -1)
                    throw MemorySizes.limit("checkpoint grew beyond its admitted size while reading");
                try (MemoryBudget.Lease parsing = budget.reserve(decodeBytes(bytes, campaign) - length, "checkpoint decoding")) {
                    return decoder.apply(bytes);
                }
            }
        }
    }
}
