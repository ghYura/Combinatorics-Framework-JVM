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

package org.ghyura.cf.input;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.rule.RuleValue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A parsed JSON or TOML document with the place of every part, in document order: the constraint sidecar and the
 * fwgen spec readers check their shapes on it and point at the offending part. Paths are JSON pointers.
 */
public sealed interface DocTree {

    SourceLocation where();

    /** A table or object; its keys keep their document order. */
    record Obj(Map<String, DocTree> fields, SourceLocation where) implements DocTree {
        public Obj {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            Objects.requireNonNull(where, "where");
        }
    }

    record Arr(List<DocTree> items, SourceLocation where) implements DocTree {
        public Arr {
            items = List.copyOf(items);
            Objects.requireNonNull(where, "where");
        }
    }

    /**
     * A scalar: a text, integer, real, boolean or (JSON only) null. A TOML date or time is a text holding what Python
     * prints for it ({@code str()} of the tomllib value) and says which kind it was.
     */
    record Leaf(RuleValue value, Temporal temporal, SourceLocation where) implements DocTree {
        public Leaf {
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(temporal, "temporal");
            Objects.requireNonNull(where, "where");
        }

        public Leaf(RuleValue value, SourceLocation where) {
            this(value, Temporal.NONE, where);
        }
    }

    /** What a TOML date or time leaf was; {@link #NONE} for every other leaf. */
    enum Temporal { NONE, DATE_TIME, DATE, TIME }

    /** The JSON pointer segment of a key. */
    static String pointer(String key) {
        return key.replace("~", "~0").replace("/", "~1");
    }

    /**
     * Reads the JSON value at the parser's current token. A key repeated in one object is reported to
     * {@code duplicateKey} (the legacy json.loads kept the last silently); the later value is kept.
     */
    static DocTree readJson(JsonParser parser, String origin, String path, BiConsumer<String, SourceLocation> duplicateKey)
            throws IOException {
        JsonToken token = parser.currentToken();
        SourceLocation where = at(origin, parser.currentTokenLocation(), path);
        return switch (token) {
            case START_OBJECT -> {
                Map<String, DocTree> fields = new LinkedHashMap<>();
                while (parser.nextToken() == JsonToken.FIELD_NAME) {
                    String key = parser.currentName();
                    String keyPath = path + "/" + pointer(key);
                    SourceLocation keyAt = at(origin, parser.currentTokenLocation(), keyPath);
                    parser.nextToken();
                    DocTree value = readJson(parser, origin, keyPath, duplicateKey);
                    if (fields.containsKey(key)) {
                        duplicateKey.accept(key, keyAt);
                        fields.remove(key);
                    }
                    fields.put(key, value);
                }
                yield new Obj(fields, where);
            }
            case START_ARRAY -> {
                List<DocTree> items = new ArrayList<>();
                int i = 0;
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    items.add(readJson(parser, origin, path + "/" + i++, duplicateKey));
                }
                yield new Arr(items, where);
            }
            case VALUE_STRING -> new Leaf(new RuleValue.Text(parser.getText()), where);
            case VALUE_NUMBER_INT -> new Leaf(new RuleValue.Int(parser.getBigIntegerValue()), where);
            case VALUE_NUMBER_FLOAT -> new Leaf(new RuleValue.Real(parser.getDoubleValue()), where);
            case VALUE_TRUE -> new Leaf(new RuleValue.Bool(true), where);
            case VALUE_FALSE -> new Leaf(new RuleValue.Bool(false), where);
            case VALUE_NULL -> new Leaf(RuleValue.Null.NULL, where);
            default -> throw new IOException("unexpected JSON token " + token);
        };
    }

    /** A location from a Jackson location: line and column when known. */
    static SourceLocation at(String origin, JsonLocation l, String path) {
        return new SourceLocation(origin, Math.max(0, l.getLineNr()), l.getLineNr() > 0 ? Math.max(0, l.getColumnNr()) : 0, path);
    }

    /** The plain value of a part: lists and maps of its leaves' values. */
    static RuleValue value(DocTree n) {
        return switch (n) {
            case Leaf l -> l.value();
            case Arr a -> new RuleValue.ListValue(a.items().stream().map(DocTree::value).toList());
            case Obj o -> {
                Map<String, RuleValue> m = new LinkedHashMap<>();
                o.fields().forEach((k, v) -> m.put(k, value(v)));
                yield new RuleValue.MapValue(m);
            }
        };
    }

    /**
     * Compact JSON of a part, as Python's {@code json.dumps(value, ensure_ascii=True, separators=(',', ':'))} writes the
     * corresponding value: reals as Python's repr, non-ASCII as backslash-u escapes. Deterministic, so that two documents
     * compare by text.
     */
    static String toJson(DocTree n) {
        StringBuilder out = new StringBuilder();
        appendJson(out, value(n));
        return out.toString();
    }

    /** Compact Python-style JSON of a plain value (see {@link #toJson}). */
    static void appendJson(StringBuilder out, RuleValue v) {
        switch (v) {
            case RuleValue.Text t -> appendJsonString(out, t.value());
            case RuleValue.Int i -> out.append(i.value());
            case RuleValue.Real r -> out.append(Double.isNaN(r.value()) ? "NaN" : Double.isInfinite(r.value())
                    ? (r.value() > 0 ? "Infinity" : "-Infinity") : PyText.floatRepr(r.value()));
            case RuleValue.Bool b -> out.append(b.value() ? "true" : "false");
            case RuleValue.ListValue l -> {
                out.append('[');
                for (int k = 0; k < l.items().size(); k++) {
                    if (k > 0) {
                        out.append(',');
                    }
                    appendJson(out, l.items().get(k));
                }
                out.append(']');
            }
            case RuleValue.MapValue m -> {
                out.append('{');
                boolean first = true;
                for (Map.Entry<String, RuleValue> e : m.entries().entrySet()) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    appendJsonString(out, e.getKey());
                    out.append(':');
                    appendJson(out, e.getValue());
                }
                out.append('}');
            }
            default -> out.append("null");
        }
    }

    /** A JSON string as Python's ensure_ascii encoder writes it. */
    static void appendJsonString(StringBuilder out, String s) {
        out.append('"');
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (c < 0x20 || c > 0x7E) {
                        out.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
    }
}
