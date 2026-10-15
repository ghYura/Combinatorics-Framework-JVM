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
import org.ghyura.cf.model.CandidateId;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Observation;
import org.ghyura.cf.model.Outcome;
import org.ghyura.cf.model.RunId;
import org.ghyura.cf.model.SampleKey;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.results.Checkpoint;
import org.ghyura.cf.results.ResultRepository;
import org.ghyura.cf.spi.DomainObservation;
import org.ghyura.cf.store.MemoryBudget;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The checkpoint file (XML {@code cf-checkpoint}, schema 1). Its payload element is covered by a SHA-256 written in the root
 * element, computed over the payload's exact bytes, so a truncated or altered file is refused before anything in it is
 * believed. Every text is kept exactly: a value with a character XML 1.0 cannot carry is stored as its UTF-16 code units in
 * base64 (a lone surrogate included), and line breaks
 * and tabs as character references. A file is replaced only once its new content is complete, so a failed write keeps the
 * last good checkpoint.
 */
public final class CheckpointCodec {

    private static final int SCHEMA = 1;
    private static final String OPEN = "<payload>";
    private static final String CLOSE = "</payload>";
    private static final Pattern HEADER = Pattern.compile("<cf-checkpoint schema=\"([0-9]+)\" payloadSha256=\"([0-9a-f]{64})\">");

    private CheckpointCodec() {
    }

    /** Writes a checkpoint to {@code path}, replacing the previous one only when the new file is complete. */
    public static void write(Path path, Checkpoint checkpoint) {
        write(path, checkpoint, CheckpointMemory.defaults());
    }

    /** The reservation covers encoding and atomic replacement, including all simultaneously live buffers. */
    @SuppressWarnings("try")
    public static void write(Path path, Checkpoint checkpoint, MemoryBudget budget) {
        Size size = measure(checkpoint);
        try (MemoryBudget.Lease lease = budget.reserve(size.reservation(), "checkpoint output")) {
            AtomicFiles.write(path, encodeBody(checkpoint, size));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the checkpoint " + path + ": " + e.getMessage(), e);
        }
    }

    /**
     * @throws FrameworkException CHECKPOINT_CORRUPT when the file cannot be read, is truncated, has another schema or fails
     *                            its payload checksum
     */
    public static Checkpoint read(Path path) {
        return read(path, CheckpointMemory.defaults());
    }

    /** On return, the immutable checkpoint belongs to the caller; a resumed run admits its retained state separately. */
    public static Checkpoint read(Path path, MemoryBudget budget) {
        try {
            return CheckpointMemory.read(path, false, budget, bytes -> decodeBody(bytes, path.toString()));
        } catch (FrameworkException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw corrupt(path.toString(), "it cannot be read: " + e.getMessage());
        }
    }

    public static byte[] encode(Checkpoint c) {
        return encode(c, CheckpointMemory.defaults());
    }

    /** Returned bytes belong to the caller; their allocation and all temporary buffers are admitted before encoding. */
    @SuppressWarnings("try")
    public static byte[] encode(Checkpoint c, MemoryBudget budget) {
        Size size = measure(c);
        try (MemoryBudget.Lease lease = budget.reserve(size.reservation(), "checkpoint encoding")) {
            return encodeBody(c, size);
        }
    }

    /** The payload's exact size, measured by the same writer without building it. */
    record Size(long chars, long utf8Bytes, boolean wide, long workspace) {
        long reservation() {
            return org.ghyura.cf.store.MemorySizes.add(CheckpointMemory.encodeBytes(chars, utf8Bytes, wide), workspace);
        }
    }

    /** Writes the payload once into a counter, allocating nothing that grows with it. */
    static Size measure(Checkpoint c) {
        Out counter = new Out(null);
        payload(c, counter);
        return new Size(counter.chars, counter.utf8, counter.wide, counter.workspace);
    }

    /** Only called inside a reservation, including by the campaign codec's enclosing reservation. */
    static byte[] encodeBody(Checkpoint c) {
        return encodeBody(c, measure(c));
    }

    /**
     * Header, payload and trailer in one array of the measured size: the payload built in a builder of exactly its length,
     * encoded straight into the array and hashed in place, so nothing is copied again.
     */
    static byte[] encodeBody(Checkpoint c, Size size) {
        StringBuilder text = new StringBuilder(Math.toIntExact(size.chars()));
        payload(c, new Out(text));
        if (text.length() != size.chars()) {
            throw new IllegalStateException("the checkpoint changed while it was written");
        }
        byte[] head = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cf-checkpoint schema=\"" + SCHEMA + "\" payloadSha256=\"")
                .getBytes(StandardCharsets.UTF_8);
        byte[] headEnd = "\">\n".getBytes(StandardCharsets.UTF_8);
        byte[] tail = "\n</cf-checkpoint>\n".getBytes(StandardCharsets.UTF_8);
        int at = head.length + 64 + headEnd.length;
        int length = Math.toIntExact(size.utf8Bytes());
        byte[] out = new byte[Math.addExact(Math.addExact(at, length), tail.length)];
        ByteBuffer target = ByteBuffer.wrap(out, at, length);
        CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
        try {
            CoderResult result = encoder.encode(CharBuffer.wrap(text), target, true);
            if (result.isError() || result.isOverflow()) {
                result.throwException();
            }
            result = encoder.flush(target);
            if (result.isError() || result.isOverflow()) {
                result.throwException();
            }
        } catch (CharacterCodingException e) {
            throw new IllegalStateException("the checkpoint payload is not UTF-8 encodable: " + e.getMessage(), e);
        }
        if (target.position() != at + length) {
            throw new IllegalStateException("the checkpoint payload is not the measured size");
        }
        byte[] digest;
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(out, at, length);
            digest = sha.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(HexFormat.of().formatHex(digest).getBytes(StandardCharsets.US_ASCII), 0, out, head.length, 64);
        System.arraycopy(headEnd, 0, out, head.length + 64, headEnd.length);
        System.arraycopy(tail, 0, out, at + length, tail.length);
        return out;
    }

    /** The payload text, written into a counter or a builder. */
    private static void payload(Checkpoint c, Out p) {
        p.append(OPEN).append('\n');
        p.append("<identity");
        attr(p, "runId", c.runId());
        attr(p, "state", c.state());
        attr(p, "scenarioPath", c.scenarioPath());
        attr(p, "scenarioDigest", c.scenarioDigest());
        attr(p, "planDigest", c.planDigest());
        attr(p, "dictionaryDigest", c.dictionaryDigest());
        attr(p, "policyDigest", c.policyDigest());
        attr(p, "plugin", c.plugin());
        attr(p, "pluginVersion", c.pluginVersion());
        p.workspace = Math.max(p.workspace, CheckpointMemory.configurationWorkspace(c.configuration()));
        attr(p, "configDigest", p.text == null ? "0".repeat(64) : c.configDigest());
        p.append("/>\n<config>\n");
        c.configuration().forEach((k, v) -> {
            p.append("<entry");
            attr(p, "key", k);
            attr(p, "value", v);
            p.append("/>\n");
        });
        p.append("</config>\n<observations count=\"").append(c.observations().size()).append("\">\n");
        for (Observation o : c.observations()) {
            p.append("<o");
            key(p, o.key());
            attr(p, "display", o.display());
            attr(p, "outcome", o.outcome().name());
            attr(p, "elapsedNanos", Long.toString(o.elapsedNanos()));
            attr(p, "sourceDigest", o.sourceDigest());
            attr(p, "policyDigest", o.policyDigest());
            o.failure().ifPresent(f -> attr(p, "failure", f.name()));
            attr(p, "failureMessage", o.failureMessage());
            attr(p, "stdout", o.stdout());
            attr(p, "stderr", o.stderr());
            attr(p, "truncated", Boolean.toString(o.truncated()));
            if (o.domain().isEmpty()) {
                p.append("/>\n");
                continue;
            }
            DomainObservation d = o.domain().get();
            attr(p, "verdict", Integer.toString(d.verdictCode()));
            p.append(">\n");
            p.ordered(d.metrics()).forEach((k, v) -> {
                p.append("<metric");
                attr(p, "key", k);
                attr(p, "value", Double.toString(v));
                p.append("/>\n");
            });
            p.ordered(d.attributes()).forEach((k, v) -> {
                p.append("<attribute");
                attr(p, "key", k);
                attr(p, "value", v);
                p.append("/>\n");
            });
            p.append("</o>\n");
        }
        p.append("</observations>\n<attachments count=\"").append(c.attachments().size()).append("\">\n");
        for (ResultRepository.Attachment a : c.attachments()) {
            p.append("<a");
            key(p, a.key());
            attr(p, "name", a.name());
            attr(p, "text", a.text());
            p.append("/>\n");
        }
        p.append("</attachments>\n").append(CLOSE);
    }

    /**
     * Where the payload goes: counted (characters, UTF-8 bytes, whether any is above Latin-1) or built. The payload holds
     * no lone surrogate (such a value is stored in base64), so a surrogate pair is four UTF-8 bytes.
     */
    private static final class Out {
        private final StringBuilder text;
        private long chars;
        private long utf8;
        private boolean wide;
        private long workspace;

        Out(StringBuilder text) {
            this.text = text;
        }

        <V> Map<String, V> ordered(Map<String, V> values) {
            workspace = Math.max(workspace, 128L * values.size());
            return text == null ? values : new TreeMap<>(values);
        }

        Out base64(String value) {
            if (text == null) {
                long length = base64Length(2L * value.length());
                chars += length;
                utf8 += length;
            } else {
                base64Utf16(value, c -> text.append((char) c));
            }
            return this;
        }

        Out append(String s) {
            if (text != null) {
                text.append(s);
                return this;
            }
            for (int i = 0; i < s.length(); i++) {
                count(s.charAt(i));
            }
            return this;
        }

        Out append(char c) {
            if (text != null) {
                text.append(c);
            } else {
                count(c);
            }
            return this;
        }

        Out append(int n) {
            return append(Integer.toString(n));
        }

        private void count(char c) {
            chars++;
            if (c > 0xFF) {
                wide = true;
            }
            if (c < 0x80) {
                utf8 += 1;
            } else if (c < 0x800) {
                utf8 += 2;
            } else if (Character.isHighSurrogate(c)) {
                utf8 += 4;   // with its low surrogate, which adds nothing
            } else if (Character.isLowSurrogate(c)) {
                utf8 += 0;
            } else {
                utf8 += 3;
            }
        }
    }

    /** @throws FrameworkException CHECKPOINT_CORRUPT */
    public static Checkpoint decode(byte[] bytes, String origin) {
        return decode(bytes, origin, CheckpointMemory.defaults());
    }

    @SuppressWarnings("try")
    public static Checkpoint decode(byte[] bytes, String origin, MemoryBudget budget) {
        try (MemoryBudget.Lease lease = budget.reserve(CheckpointMemory.decodeBytes(bytes, false), "checkpoint decoding")) {
            return decodeBody(bytes, origin);
        }
    }

    static Checkpoint decodeBody(byte[] bytes, String origin) {
        String text = new String(bytes, StandardCharsets.ISO_8859_1);
        Matcher header = HEADER.matcher(text.substring(0, Math.min(text.length(), 512)));
        if (!header.find()) {
            throw corrupt(origin, "it has no cf-checkpoint header with a payload checksum");
        }
        if (!header.group(1).equals(Integer.toString(SCHEMA))) {
            throw corrupt(origin, "its schema is " + header.group(1) + "; this build reads schema " + SCHEMA);
        }
        int open = text.indexOf(OPEN);
        int close = text.lastIndexOf(CLOSE);
        if (open < 0 || close < open) {
            throw corrupt(origin, "it is truncated (its payload does not end)");
        }
        byte[] payload = java.util.Arrays.copyOfRange(bytes, open, close + CLOSE.length());
        if (!Canonical.sha256Hex(payload).equals(header.group(2))) {
            throw corrupt(origin, "its payload does not match its SHA-256 (altered or truncated)");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setExpandEntityReferences(false);
            factory.setAttribute("jdk.xml.maxElementDepth", "64");
            // A checksummed payload with DTDs refused and its parser workspace admitted: the JDK's secure-processing
            // size limits (100,000 characters per value, 50,000,000 in all) would refuse a valid checkpoint whose captured
            // output or attached source is larger, so they are lifted here; nesting stays bounded.
            factory.setAttribute("jdk.xml.maxGeneralEntitySizeLimit", "0");
            factory.setAttribute("jdk.xml.totalEntitySizeLimit", "0");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
            Element root = doc.getDocumentElement();
            Element identity = only(root, "identity");
            Map<String, String> configuration = new LinkedHashMap<>();
            for (Element e : all(only(root, "config"), "entry")) {
                configuration.put(text(e, "key"), text(e, "value"));
            }
            List<Observation> observations = new ArrayList<>();
            Element obs = only(root, "observations");
            for (Element e : all(obs, "o")) {
                observations.add(observation(e));
            }
            if (observations.size() != Integer.parseInt(obs.getAttribute("count"))) {
                throw new IllegalArgumentException("it lists " + observations.size() + " observations but declares " + obs.getAttribute("count"));
            }
            List<ResultRepository.Attachment> attachments = new ArrayList<>();
            for (Element e : all(only(root, "attachments"), "a")) {
                attachments.add(new ResultRepository.Attachment(key(e), text(e, "name"), text(e, "text")));
            }
            Checkpoint c = new Checkpoint(text(identity, "runId"), text(identity, "state"), text(identity, "scenarioPath"),
                    text(identity, "scenarioDigest"), text(identity, "planDigest"), text(identity, "dictionaryDigest"),
                    text(identity, "policyDigest"), text(identity, "plugin"), text(identity, "pluginVersion"), configuration, observations,
                    attachments);
            if (!c.configDigest().equals(text(identity, "configDigest"))) {
                throw new IllegalArgumentException("its configuration does not match its configuration digest");
            }
            return c;
        } catch (FrameworkException e) {
            throw e;
        } catch (Exception e) {
            throw corrupt(origin, "its payload is not a checkpoint: " + e.getMessage());
        }
    }

    private static Observation observation(Element e) {
        Outcome outcome = Outcome.valueOf(text(e, "outcome"));
        Optional<DomainObservation> domain = Optional.empty();
        if (e.hasAttribute("verdict")) {
            Map<String, Double> metrics = new LinkedHashMap<>();
            for (Element m : all(e, "metric")) {
                metrics.put(text(m, "key"), Double.parseDouble(text(m, "value")));
            }
            Map<String, String> attributes = new LinkedHashMap<>();
            for (Element a : all(e, "attribute")) {
                attributes.put(text(a, "key"), text(a, "value"));
            }
            domain = Optional.of(new DomainObservation(Integer.parseInt(text(e, "verdict")), metrics, attributes));
        }
        Optional<ErrorCode> failure = e.hasAttribute("failure") ? Optional.of(ErrorCode.valueOf(text(e, "failure"))) : Optional.empty();
        return new Observation(key(e), text(e, "display"), outcome, domain, Long.parseLong(text(e, "elapsedNanos")), text(e, "sourceDigest"),
                text(e, "policyDigest"), failure, text(e, "failureMessage"), text(e, "stdout"), text(e, "stderr"),
                Boolean.parseBoolean(text(e, "truncated")));
    }

    private static void key(Out p, SampleKey k) {
        attr(p, "run", k.runId().value());
        attr(p, "candidate", k.candidateId().hex());
        attr(p, "attempt", Integer.toString(k.attempt()));
        attr(p, "repeat", Integer.toString(k.repeatIndex()));
        attr(p, "env", k.environmentId());
    }

    private static SampleKey key(Element e) {
        return new SampleKey(new RunId(text(e, "run")), new CandidateId(text(e, "candidate")), Integer.parseInt(text(e, "attempt")),
                Integer.parseInt(text(e, "repeat")), text(e, "env"));
    }

    /** An attribute kept exactly: escaped, line breaks and tabs as references, or base64 when XML 1.0 cannot carry it. */
    private static void attr(Out out, String name, String value) {
        boolean representable = value.codePoints().allMatch(CheckpointCodec::isXmlChar);
        if (!representable) {
            out.append(' ').append(name).append("-b64=\"").base64(value).append('"');
            return;
        }
        out.append(' ').append(name).append("=\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\n' -> out.append("&#10;");
                case '\r' -> out.append("&#13;");
                case '\t' -> out.append("&#9;");
                default -> out.append(c);
            }
        }
        out.append('"');
    }

    private static String text(Element e, String name) {
        if (e.hasAttribute(name)) {
            return e.getAttribute(name);
        }
        if (e.hasAttribute(name + "-b64")) {
            byte[] b = Base64.getDecoder().decode(e.getAttribute(name + "-b64"));
            if (b.length % 2 != 0) {
                throw new IllegalArgumentException("<" + e.getTagName() + "> " + name + " is not UTF-16");
            }
            char[] chars = new char[b.length / 2];
            for (int i = 0; i < chars.length; i++) {
                chars[i] = (char) (((b[2 * i] & 0xFF) << 8) | (b[2 * i + 1] & 0xFF));
            }
            return new String(chars);
        }
        throw new IllegalArgumentException("<" + e.getTagName() + "> has no " + name);
    }

    static long base64Length(long bytes) {
        return Math.multiplyExact(Math.floorDiv(Math.addExact(bytes, 2), 3), 4);
    }

    /** Base64 of exact big-endian UTF-16 code units, without a whole-value temporary array or string. */
    static void base64Utf16(String value, java.util.function.IntConsumer out) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        int bits = 0;
        int available = 0;
        for (int i = 0; i < value.length(); i++) {
            bits = (bits << 16) | value.charAt(i);
            available += 16;
            while (available >= 6) {
                available -= 6;
                out.accept(alphabet.charAt((bits >>> available) & 63));
            }
        }
        if (available > 0) out.accept(alphabet.charAt((bits << (6 - available)) & 63));
        int remainder = (int) ((2L * value.length()) % 3);
        if (remainder != 0) for (int i = remainder; i < 3; i++) out.accept('=');
    }

    private static boolean isXmlChar(int cp) {
        return cp == 0x9 || cp == 0xA || cp == 0xD || (cp >= 0x20 && cp <= 0xD7FF) || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF);
    }

    private static Element only(Element parent, String tag) {
        List<Element> found = all(parent, tag);
        if (found.size() != 1) {
            throw new IllegalArgumentException("expected one <" + tag + ">, found " + found.size());
        }
        return found.getFirst();
    }

    /** The descendants of {@code parent} with this tag, in document order. */
    private static List<Element> all(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        List<Element> out = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            out.add((Element) nodes.item(i));
        }
        return out;
    }

    private static FrameworkException corrupt(String origin, String why) {
        return new FrameworkException(Diagnostic.error(ErrorCode.CHECKPOINT_CORRUPT, "the checkpoint " + origin + " cannot be resumed: "
                + why + "; nothing was resumed", SourceLocation.of(origin)));
    }
}
