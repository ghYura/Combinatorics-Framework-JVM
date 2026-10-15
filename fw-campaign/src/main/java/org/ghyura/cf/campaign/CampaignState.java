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

package org.ghyura.cf.campaign;

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.analysis.BundleSeed;
import org.ghyura.cf.store.MemorySizes;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Exact JSON for the fixed checkpoint roots and their closed record graphs. No class names come from the file;
 * decoding follows only the declared components of Iteration, Diagnostic or BundleSeed. Hexadecimal doubles retain infinities,
 * signed zero and every finite bit pattern; arbitrary integers are decimal strings. No Java object serialization.
 */
final class CampaignState {
    private CampaignState() { }

    static String iteration(Campaign.Iteration value) { return write(value); }
    static String diagnostic(Diagnostic value) { return write(value); }
    static Campaign.Iteration iteration(String text) { return read(text, Campaign.Iteration.class); }
    static Diagnostic diagnostic(String text) { return read(text, Diagnostic.class); }
    static String seed(BundleSeed value) { return write(value); }
    static BundleSeed seed(String text) { return read(text, BundleSeed.class); }

    /** Tree conversion plus simultaneous UTF-16 builder growth and the returned JSON string, before either is allocated. */
    static long encodingBytes(Object value) {
        try {
            long characters = MemorySizes.add(1, jsonCharacters(value, 0));
            if (characters > Integer.MAX_VALUE - 16L) throw MemorySizes.limit("campaign state exceeds the JSON encoding size limit");
            return MemorySizes.add(65_536, MemorySizes.add(MemorySizes.multiply(4, MemorySizes.retained(value)),
                    MemorySizes.multiply(8, characters)));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot size campaign state", e);
        }
    }

    /** Upper bound for Json.write(tree(value)); visits the record graph without building a second graph or large text. */
    private static long jsonCharacters(Object value, int depth) throws ReflectiveOperationException {
        if (depth > 64) throw MemorySizes.limit("campaign state is nested deeper than 64 levels");
        if (value == null) return 4;
        if (value instanceof Optional<?> optional) return jsonCharacters(optional.orElse(null), depth);
        if (value instanceof String text) return quotedCharacters(text);
        if (value instanceof Boolean) return 5;
        if (value instanceof Integer || value instanceof Long) return 20;
        if (value instanceof Double number) return quotedCharacters(Double.toHexString(number));
        if (value instanceof BigInteger number) return 4 + MemorySizes.multiply(number.bitLength(), 30_103) / 100_000;
        if (value instanceof Enum<?> entry) return quotedCharacters(entry.name());
        long characters = 3 + 2L * depth; // delimiters, opening newline and closing indentation
        if (value instanceof List<?> list) {
            if (list.isEmpty()) return 2;
            for (Object entry : list) characters = MemorySizes.add(characters,
                    MemorySizes.add(4 + 2L * depth, jsonCharacters(entry, depth + 1)));
        } else if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) return 2;
            for (var entry : map.entrySet()) characters = MemorySizes.add(characters,
                    MemorySizes.add(6 + 2L * depth, MemorySizes.add(quotedCharacters(entry.getKey() instanceof Enum<?> e
                            ? e.name() : entry.getKey().toString()), jsonCharacters(entry.getValue(), depth + 1))));
        } else if (value.getClass().isRecord()) {
            for (RecordComponent component : value.getClass().getRecordComponents()) characters = MemorySizes.add(characters,
                    MemorySizes.add(6 + 2L * depth, MemorySizes.add(quotedCharacters(component.getName()),
                            jsonCharacters(component.getAccessor().invoke(value), depth + 1))));
        } else throw new IllegalArgumentException("unsupported checkpoint component " + value.getClass().getName());
        return characters;
    }

    private static long quotedCharacters(String text) {
        long characters = 2;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            characters = MemorySizes.add(characters, switch (c) {
                case '"', '\\', '\n', '\r', '\t', '\b', '\f' -> 2;
                default -> c < 0x20 ? 6 : 1;
            });
        }
        return characters;
    }

    private static String write(Object value) {
        try { return Json.write(tree(value)); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("cannot encode campaign state", e); }
    }

    private static Object tree(Object value) throws ReflectiveOperationException {
        if (value == null) return null;
        if (value instanceof Optional<?> optional) return tree(optional.orElse(null));
        if (value instanceof String || value instanceof Boolean || value instanceof Integer || value instanceof Long) return value;
        if (value instanceof Double number) return Double.toHexString(number);
        if (value instanceof BigInteger number) return number.toString();
        if (value instanceof Enum<?> entry) return entry.name();
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object entry : list) out.add(tree(entry));
            return out;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new TreeMap<>();
            for (var entry : map.entrySet()) out.put(entry.getKey() instanceof Enum<?> e ? e.name() : entry.getKey().toString(),
                    tree(entry.getValue()));
            return out;
        }
        if (value.getClass().isRecord()) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (RecordComponent component : value.getClass().getRecordComponents())
                out.put(component.getName(), tree(component.getAccessor().invoke(value)));
            return out;
        }
        throw new IllegalArgumentException("unsupported checkpoint component " + value.getClass().getName());
    }

    private static <T> T read(String text, Class<T> root) {
        try { return root.cast(value(Json.parse(text, "campaign checkpoint state"), root)); }
        catch (FrameworkException e) {
            if (e.diagnostics().stream().anyMatch(d -> d.code() == ErrorCode.RESOURCE_LIMIT)) throw e;
            throw new FrameworkException(Diagnostic.error(ErrorCode.CHECKPOINT_CORRUPT,
                    "invalid completed campaign state: " + e.getMessage(), SourceLocation.NONE));
        }
        catch (ReflectiveOperationException | RuntimeException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CHECKPOINT_CORRUPT,
                    "invalid completed campaign state: " + e.getMessage(), SourceLocation.NONE));
        }
    }

    private static Object value(Object json, Type type) throws ReflectiveOperationException {
        if (type instanceof ParameterizedType generic) {
            Type[] arguments = generic.getActualTypeArguments();
            if (generic.getRawType() == Optional.class) return Optional.ofNullable(value(json, arguments[0]));
            if (generic.getRawType() == List.class && json instanceof List<?> list) {
                List<Object> out = new ArrayList<>();
                for (Object entry : list) out.add(value(entry, arguments[0]));
                return List.copyOf(out);
            }
            if (generic.getRawType() == Map.class && json instanceof Map<?, ?> map) {
                Map<Object, Object> out = new LinkedHashMap<>();
                for (var entry : map.entrySet()) {
                    Object key = mapKey((String) entry.getKey(), arguments[0]);
                    if (out.containsKey(key)) throw new IllegalArgumentException("duplicate decoded map key");
                    out.put(key, value(entry.getValue(), arguments[1]));
                }
                return out;
            }
            throw new IllegalArgumentException("invalid collection for " + type);
        }
        if (!(type instanceof Class<?> c)) throw new IllegalArgumentException("unsupported component type " + type);
        if (json == null && !c.isPrimitive()) return null;
        if (c == String.class) return String.class.cast(json);
        if (c == boolean.class || c == Boolean.class) return Boolean.class.cast(json);
        if (c == int.class || c == Integer.class) return Math.toIntExact((Long) json);
        if (c == long.class || c == Long.class) return Long.class.cast(json);
        if (c == double.class || c == Double.class) return Double.parseDouble((String) json);
        if (c == BigInteger.class) return new BigInteger((String) json);
        if (c.isEnum()) {
            for (Object constant : c.getEnumConstants()) if (((Enum<?>) constant).name().equals(json)) return constant;
            throw new IllegalArgumentException("unknown " + c.getSimpleName() + " " + json);
        }
        if (c.isRecord() && json instanceof Map<?, ?> map) {
            RecordComponent[] components = c.getRecordComponents();
            if (map.size() != components.length) throw new IllegalArgumentException("wrong fields for " + c.getSimpleName());
            Class<?>[] types = new Class<?>[components.length];
            Object[] values = new Object[components.length];
            for (int i = 0; i < components.length; i++) {
                RecordComponent component = components[i];
                if (!map.containsKey(component.getName())) throw new IllegalArgumentException("missing " + component.getName());
                types[i] = component.getType(); values[i] = value(map.get(component.getName()), component.getGenericType());
            }
            return c.getDeclaredConstructor(types).newInstance(values);
        }
        throw new IllegalArgumentException("invalid value for " + type);
    }

    private static Object mapKey(String key, Type type) throws ReflectiveOperationException {
        if (type == Integer.class) return Integer.valueOf(key);
        return value(key, type);
    }
}
