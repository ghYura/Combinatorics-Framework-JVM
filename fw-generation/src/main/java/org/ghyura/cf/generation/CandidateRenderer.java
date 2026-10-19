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

package org.ghyura.cf.generation;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.key.KeyRow;
import org.ghyura.cf.model.key.SelectionState;
import org.ghyura.cf.model.spec.SheetDecoration;
import org.ghyura.cf.model.spec.ValueCell;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.store.KeyDictionary;
import org.ghyura.cf.store.MemoryBudget;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Writes a candidate's source the way the legacy Reader wrote it (ComboGenerationPipeline.buildSegment, Assembler,
 * emitCandidate): for every column in order whose sheet is present, the sheet's FW_SheetNames prefix, the bytes of the
 * row's values (joined by the sheet's FW_Concatenator= text unless cf.render.sheetConcatenator=legacy-ignore), and then
 * cf.render.concatenator when it is not empty, otherwise the sheet's ending; an absent sheet writes nothing, a present
 * empty row writes its prefix and separator. FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE is then replaced by the
 * candidate's display id (when enabled), and the last written separator is cut off, in the order of the legacy loose-file
 * writer.
 *
 * <p>Value bytes are prepared once per value, UTF-8, reserved from the budget: as written when
 * cf.render.preserveWhitespace is true, or when the value was already refined at parse (FW_RefineCodeExceptQuoted) or
 * declares FW_PATH_FILES_TO (which the legacy marked refined); otherwise refined by {@link ReaderRefiner}.</p>
 */
public final class CandidateRenderer implements AutoCloseable {

    /** The legacy marker, replaced by the candidate's display id. */
    public static final String MARKER = "FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE";
    private static final byte[] MARKER_BYTES = MARKER.getBytes(StandardCharsets.UTF_8);

    private final Assembly assembly;
    private final RenderOptions options;
    private final int firstLabel;
    private final byte[][] values;
    private final byte[][] prefix;
    private final byte[][] separator;
    private final byte[][] glue;
    private final MemoryBudget.Lease lease;
    private final SeqPlan plan;
    private final KeyDictionary dictionary;

    private CandidateRenderer(Assembly assembly, RenderOptions options, int firstLabel, byte[][] values, byte[][] prefix,
                              byte[][] separator, byte[][] glue, MemoryBudget.Lease lease, SeqPlan plan, KeyDictionary dictionary) {
        this.assembly = assembly;
        this.options = options;
        this.firstLabel = firstLabel;
        this.values = values;
        this.prefix = prefix;
        this.separator = separator;
        this.glue = glue;
        this.lease = lease;
        this.plan = plan;
        this.dictionary = dictionary;
    }

    /**
     * @throws FrameworkException ASSEMBLY_RENDER_FAILED when a column's sheet has no FW_SheetNames row or a value cannot be
     *                            refined; RESOURCE_LIMIT when the prepared values do not fit the budget
     */
    public static CandidateRenderer of(Assembly assembly, SeqPlan plan, KeyDictionary dictionary, RenderOptions options,
                                       MemoryBudget budget) {
        Objects.requireNonNull(assembly, "assembly");
        Objects.requireNonNull(options, "options");
        List<String> sheets = assembly.columnSheets();
        byte[][] prefix = new byte[sheets.size()][];
        byte[][] separator = new byte[sheets.size()][];
        byte[][] glue = new byte[sheets.size()][];
        byte[] global = options.concatenator().getBytes(StandardCharsets.UTF_8);
        for (int c = 0; c < sheets.size(); c++) {
            String sheet = sheets.get(c);
            SheetDecoration decoration = plan.decorations().stream().filter(d -> d.sheet().equals(sheet)).findFirst().orElse(null);
            if (decoration == null) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.ASSEMBLY_RENDER_FAILED, "the sheet " + sheet + " has no "
                        + "FW_SheetNames row, so its candidates cannot be written (the legacy Reader dropped them); add the row or "
                        + "set cf.plan.autoGenerateSheetNames=true", plan.sheet(sheet).map(s -> s.location()).orElse(SourceLocation.NONE),
                        "sheet", sheet));
            }
            prefix[c] = decoration.prefix().getBytes(StandardCharsets.UTF_8);
            separator[c] = global.length > 0 ? global : decoration.ending().getBytes(StandardCharsets.UTF_8);
            String concatenator = assembly.concatenator(c);
            glue[c] = concatenator == null ? new byte[0] : concatenator.getBytes(StandardCharsets.UTF_8);
        }
        int first = dictionary.firstLabel();
        int count = dictionary.valueCount();
        ReaderRefiner refiner = new ReaderRefiner(options);
        long bytes = 64 + 24L * count;
        byte[][] values = new byte[count][];
        for (int i = 0; i < count; i++) {
            ValueCell cell = dictionary.value(first + i);
            String text = cell.text();
            if (!options.preserveWhitespace() && !cell.refined() && !text.contains("FW_PATH_FILES_TO")) {
                try {
                    text = refiner.refine(text);
                } catch (ReaderRefiner.TooComplexException | IllegalArgumentException | IndexOutOfBoundsException e) {
                    throw new FrameworkException(Diagnostic.error(ErrorCode.ASSEMBLY_RENDER_FAILED, "the value of label " + (first + i)
                            + " cannot be refined for rendering: " + e.getMessage(), cell.location(), "label", Integer.toString(first + i)), e);
                }
            }
            values[i] = text.getBytes(StandardCharsets.UTF_8);
            bytes += values[i].length;
        }
        MemoryBudget.Lease lease = budget.reserve(bytes, "the rendered bytes of every value");
        return new CandidateRenderer(assembly, options, first, values, prefix, separator, glue, lease, plan, dictionary);
    }

    /** The candidate's source bytes. */
    public byte[] render(Candidate candidate) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        int lastSeparator = write(candidate, (bytes, column, occurrence, label, kind) -> out.writeBytes(bytes));
        byte[] payload = out.toByteArray();
        if (options.markerReplacement()) {
            payload = replaceMarkers(payload, candidate.display().getBytes(StandardCharsets.UTF_8));
        }
        return java.util.Arrays.copyOf(payload, Math.max(0, payload.length - lastSeparator));
    }

    @FunctionalInterface
    private interface SegmentWriter {
        void write(byte[] bytes, int column, int occurrence, int label, RenderedSource.Kind kind);
    }

    /** One emission path for execution and provenance; no substring searching to guess a value's origin. */
    private int write(Candidate candidate, SegmentWriter out) {
        int lastSeparator = 0;
        for (int c = 0; c < prefix.length; c++) {
            if (candidate.states().get(c) == SelectionState.ABSENT) {
                continue;
            }
            KeyRow keys = candidate.keys(c);
            out.write(prefix[c], c, 0, 0, RenderedSource.Kind.PREFIX);
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) {
                    out.write(glue[c], c, i + 1, 0, RenderedSource.Kind.VALUE_SEPARATOR);
                }
                out.write(values[keys.label(i) - firstLabel], c, i + 1, keys.label(i), RenderedSource.Kind.VALUE);
            }
            out.write(separator[c], c, 0, 0, RenderedSource.Kind.SHEET_SEPARATOR);
            lastSeparator = separator[c].length;
        }
        return lastSeparator;
    }

    /**
     * The same bytes as {@link #render}, with their origins. Only the bounded preview retains these spans; execution
     * does not allocate them. Refinement, optional absence, repeated labels, decorations, markers (including markers
     * split across cells) and the legacy final-separator trimming all follow the execution renderer.
     */
    public RenderedSource renderWithOrigins(Candidate candidate, int maxChars) {
        if (maxChars < 0) throw new IllegalArgumentException("negative source limit");
        List<String> sheets = assembly.columnSheets();
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        List<RenderedSource.Chunk> spans = new ArrayList<>(); // byte offsets until decoded below
        int lastSeparator = write(candidate, (bytes, column, occurrence, label, kind) -> {
            if (bytes.length == 0) return;
            String assembled = sheets.get(column);
            String sheet = assembled;
            SourceLocation location;
            String detail = "";
            if (kind == RenderedSource.Kind.VALUE) {
                ValueCell cell = dictionary.value(label);
                sheet = plan.sheetByKey(dictionary.sheetOfLabel(label)).orElseThrow().name();
                location = cell.location();
                if (cell.refined() || !options.preserveWhitespace() && !cell.text().contains("FW_PATH_FILES_TO")) {
                    detail = "code refinement applied";
                }
                if (cell.optional()) detail += (detail.isEmpty() ? "" : "; ") + "FW_Optional value";
            } else if (kind == RenderedSource.Kind.VALUE_SEPARATOR) {
                location = plan.sheet(assembled).orElseThrow().location();
                detail = "FW_Concatenator of " + assembled;
            } else if (kind == RenderedSource.Kind.SHEET_SEPARATOR && !options.concatenator().isEmpty()) {
                location = SourceLocation.ofPath("<render settings>", "cf.render.concatenator");
            } else {
                location = plan.decorations().stream().filter(d -> d.sheet().equals(assembled)).findFirst().orElseThrow().location();
                detail = "FW_SheetNames " + (kind == RenderedSource.Kind.PREFIX ? "prefix" : "ending");
            }
            var origin = new RenderedSource.Origin(sheet, assembled, kind == RenderedSource.Kind.VALUE ? label : null,
                    occurrence, assembly.optionalColumn(column), kind, location, detail);
            spans.add(new RenderedSource.Chunk(raw.size(), raw.size() + bytes.length, List.of(origin)));
            raw.writeBytes(bytes);
        });
        byte[] bytes = raw.toByteArray();
        ByteArrayOutputStream replaced = new ByteArrayOutputStream();
        List<RenderedSource.Chunk> mapped = new ArrayList<>();
        int from = 0;
        if (options.markerReplacement()) {
            byte[] replacement = candidate.display().getBytes(StandardCharsets.UTF_8);
            for (int i = 0; i <= bytes.length - MARKER_BYTES.length;) {
                if (!matches(bytes, i)) { i++; continue; }
                copyRange(bytes, spans, from, i, replaced, mapped);
                var contributors = new LinkedHashSet<RenderedSource.Origin>();
                for (int j = firstSpan(spans, i); j < spans.size() && spans.get(j).start() < i + MARKER_BYTES.length; j++) {
                    spans.get(j).origins().forEach(origin -> contributors.add(origin.marker()));
                }
                mapped.add(new RenderedSource.Chunk(replaced.size(), replaced.size() + replacement.length, List.copyOf(contributors)));
                replaced.writeBytes(replacement);
                i += MARKER_BYTES.length;
                from = i;
            }
        }
        copyRange(bytes, spans, from, bytes.length, replaced, mapped);
        byte[] payload = replaced.toByteArray();
        int end = Math.max(0, payload.length - lastSeparator);
        StringBuilder text = new StringBuilder();
        List<RenderedSource.Chunk> chunks = new ArrayList<>();
        boolean truncated = false;
        for (var span : mapped) {
            if (span.start() >= end) break;
            String part = new String(payload, span.start(), Math.min(end, span.end()) - span.start(), StandardCharsets.UTF_8);
            int keep = Math.min(part.length(), maxChars - text.length());
            if (keep < part.length()) {
                truncated = true;
                if (keep > 0 && Character.isHighSurrogate(part.charAt(keep - 1))) keep--;
            }
            if (keep > 0) {
                chunks.add(new RenderedSource.Chunk(text.length(), text.length() + keep, span.origins()));
                text.append(part, 0, keep);
            }
            if (truncated) break;
        }
        if (truncated) text.append('…');
        return new RenderedSource(text.toString(), chunks, truncated);
    }

    private static int firstSpan(List<RenderedSource.Chunk> spans, int offset) {
        int low = 0, high = spans.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (spans.get(middle).end() <= offset) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static void copyRange(byte[] bytes, List<RenderedSource.Chunk> spans, int from, int to,
                                  ByteArrayOutputStream out, List<RenderedSource.Chunk> mapped) {
        for (int i = firstSpan(spans, from); i < spans.size() && spans.get(i).start() < to; i++) {
            var span = spans.get(i);
            int start = Math.max(from, span.start()), end = Math.min(to, span.end());
            if (end <= start) continue;
            mapped.add(new RenderedSource.Chunk(out.size(), out.size() + end - start, span.origins()));
            out.write(bytes, start, end - start);
        }
    }

    /** The source as text. */
    public String renderText(Candidate candidate) {
        return new String(render(candidate), StandardCharsets.UTF_8);
    }

    /** Left-to-right, non-overlapping replacement of the marker, as Main.fwSpliceMarkers did. */
    static byte[] replaceMarkers(byte[] payload, byte[] replacement) {
        ByteArrayOutputStream out = null;
        int from = 0;
        int i = 0;
        while (i <= payload.length - MARKER_BYTES.length) {
            if (matches(payload, i)) {
                if (out == null) {
                    out = new ByteArrayOutputStream(payload.length + 16);
                }
                out.write(payload, from, i - from);
                out.writeBytes(replacement);
                i += MARKER_BYTES.length;
                from = i;
            } else {
                i++;
            }
        }
        if (out == null) {
            return payload;
        }
        out.write(payload, from, payload.length - from);
        return out.toByteArray();
    }

    private static boolean matches(byte[] payload, int at) {
        for (int j = 0; j < MARKER_BYTES.length; j++) {
            if (payload[at + j] != MARKER_BYTES[j]) {
                return false;
            }
        }
        return true;
    }

    /** The assembly this renderer writes the candidates of. */
    public Assembly assembly() {
        return assembly;
    }

    @Override
    public void close() {
        lease.close();
    }
}
