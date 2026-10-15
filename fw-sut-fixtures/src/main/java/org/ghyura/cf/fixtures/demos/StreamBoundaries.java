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


package org.ghyura.cf.fixtures.demos;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/** The full frozen D2 catalogue; decoder state is carried per stream, never shared between candidates. */
public final class StreamBoundaries {
    public record Payload(String encoding, String hex, String literal, boolean truncated) { }
    public record Outcome(boolean accepted, String text, String error) { }
    public record Result(List<String> chunksHex, Outcome reference, Outcome observed, String failureClass) {
        public Result { chunksHex = List.copyOf(chunksHex); }
        public boolean passed() { return failureClass.equals("none"); }
    }
    private static final Map<String, Payload> PAYLOADS = Map.of(
            "ascii", new Payload("utf-8", "414243", "ABC", false),
            "euro", new Payload("utf-8", "41e282ac42", "A€B", false),
            "emoji", new Payload("utf-8", "f09f9982", "🙂", false),
            "truncated_utf8", new Payload("utf-8", "41e282", "A�", true),
            "bom_utf16", new Payload("utf-16", "fffe4100a903", "AΩ", false),
            "truncated_utf16", new Payload("utf-16", "fffe410042", "A�", true));

    private StreamBoundaries() { }

    public static Payload payload(String id) {
        Payload p = PAYLOADS.get(id);
        if (p == null) throw new IllegalArgumentException("unknown D2 payload");
        return p;
    }

    /** Five bits, with bit zero denoting the cut after byte one, as in the original scenario. */
    public static Result evaluate(String adapter, String payloadId, String errors, int mask) {
        if (!List.of("incremental", "per_chunk", "no_final").contains(adapter))
            throw new IllegalArgumentException("unknown D2 adapter");
        if (!List.of("strict", "replace").contains(errors)) throw new IllegalArgumentException("unknown D2 errors policy");
        if (mask < 0 || mask > 31) throw new IllegalArgumentException("five cut bits required");
        Payload p = payload(payloadId);
        byte[] data = HexFormat.of().parseHex(p.hex());
        List<byte[]> chunks = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < 5; i++) {
            if ((mask & (1 << i)) == 0) continue;
            int end = i + 1;
            if (end >= data.length) throw new IllegalArgumentException("cut at or beyond payload end");
            chunks.add(java.util.Arrays.copyOfRange(data, start, end));
            start = end;
        }
        chunks.add(java.util.Arrays.copyOfRange(data, start, data.length));
        Outcome ref = reference(p, errors);
        Outcome observed = decode(adapter, chunks, p.encoding(), errors);
        return new Result(chunks.stream().map(HexFormat.of()::formatHex).toList(), ref, observed, classify(ref, observed));
    }

    /** The oracle never reads the adapter or cuts and checks whole-stream decoding against the frozen literal. */
    public static Outcome reference(Payload p, String errors) {
        if (!List.of("strict", "replace").contains(errors)) throw new IllegalArgumentException("unknown D2 errors policy");
        Outcome ref = decode("per_chunk", List.of(HexFormat.of().parseHex(p.hex())), p.encoding(), errors);
        boolean rejects = p.truncated() && errors.equals("strict");
        if (rejects ? ref.accepted() : !ref.accepted() || !p.literal().equals(ref.text()))
            throw new IllegalStateException("whole-stream decode disagrees with frozen literal");
        return ref;
    }

    public static String classify(Outcome reference, Outcome observed) {
        if (!reference.accepted()) return observed.accepted() ? "unexpected_acceptance" : "none";
        if (!observed.accepted()) return "unexpected_rejection";
        return reference.text().equals(observed.text()) ? "none" : "wrong_text";
    }

    private static Outcome decode(String adapter, List<byte[]> chunks, String encoding, String errors) {
        try {
            StringBuilder text = new StringBuilder();
            if (adapter.equals("per_chunk")) {
                for (byte[] chunk : chunks) {
                    Decoder decoder = new Decoder(encoding, errors, false);
                    text.append(decoder.feed(chunk, true));
                }
            } else {
                Decoder decoder = new Decoder(encoding, errors, true);
                for (byte[] chunk : chunks) text.append(decoder.feed(chunk, false));
                if (adapter.equals("incremental")) text.append(decoder.feed(new byte[0], true));
            }
            return new Outcome(true, text.toString(), null);
        } catch (CharacterCodingException e) {
            // Stable source-domain error identity, rather than exposing JDK exception names.
            return new Outcome(false, null, "UnicodeDecodeError");
        }
    }

    /** Bounded by the six-byte catalogue. Retains unfinished code units between calls, including a split BOM. */
    private static final class Decoder {
        private final boolean utf16;
        private final boolean requireBom;
        private final CodingErrorAction action;
        private final ByteBuffer pending = ByteBuffer.allocate(16);
        private CharsetDecoder decoder;

        private Decoder(String encoding, String errors, boolean requireBom) {
            if (!List.of("utf-8", "utf-16").contains(encoding)) throw new IllegalArgumentException("unsupported D2 encoding");
            utf16 = encoding.equals("utf-16");
            this.requireBom = requireBom;
            action = errors.equals("strict") ? CodingErrorAction.REPORT : CodingErrorAction.REPLACE;
            if (!utf16) decoder = configured(StandardCharsets.UTF_8.newDecoder());
        }

        private CharsetDecoder configured(CharsetDecoder value) {
            return value.onMalformedInput(action).onUnmappableCharacter(action);
        }

        private String feed(byte[] chunk, boolean finalInput) throws CharacterCodingException {
            pending.put(chunk).flip();
            if (decoder == null) {
                if (pending.remaining() < 2 && !finalInput) { pending.compact(); return ""; }
                boolean little = true; // Original CPython bytes.decode baseline uses native little endian without a BOM.
                boolean hasBom = false;
                if (pending.remaining() >= 2) {
                    int first = Byte.toUnsignedInt(pending.get(0)), second = Byte.toUnsignedInt(pending.get(1));
                    hasBom = first == 255 && second == 254 || first == 254 && second == 255;
                    if (hasBom) { little = first == 255; pending.position(2); }
                }
                if (requireBom && !hasBom) throw new CharacterCodingException();
                decoder = configured((little ? StandardCharsets.UTF_16LE : StandardCharsets.UTF_16BE).newDecoder());
            }
            CharBuffer output = CharBuffer.allocate(16);
            var result = decoder.decode(pending, output, finalInput);
            if (result.isError()) result.throwException();
            if (result.isOverflow()) throw new IllegalStateException("D2 catalogue output exceeds bound");
            if (finalInput) {
                var flushed = decoder.flush(output);
                if (flushed.isError()) flushed.throwException();
                if (flushed.isOverflow()) throw new IllegalStateException("D2 catalogue output exceeds bound");
            }
            pending.compact();
            return output.flip().toString();
        }
    }
}
