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

package org.ghyura.cf.fixtures.catalogpub;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.ghyura.cf.fixtures.catalogpub.PythonValues.*;

/** Faithful ordered validation and query assembly from telemetry_rules.py, including its permissive cases. */
public final class TelemetryRules {
    private TelemetryRules() { }
    static final Map<String, Object> DATA = resource("catalog-data.json");
    public static final Map<String, Object> FAMILIES = object(DATA.get("families"));
    public static final Map<String, Object> RULES = object(DATA.get("rules"));
    public static final List<String> QUERY_PARAMS = strings(DATA.get("queryParams"));
    private static final Set<String> MULTI = Set.of("SignalClassList", "AccessTier", "sortBy");
    private static List<String> strings(Object o) { return ((List<?>) o).stream().map(String.class::cast).toList(); }
    public static boolean present(Map<String, ?> p, String key) {
        Object v = p.get(key); return v != null && !v.equals("") && !(v instanceof List<?> l && l.isEmpty());
    }
    public static List<String> asList(Object value) {
        if (value == null || value.equals("")) return List.of();
        List<String> values = value instanceof List<?> l ? l.stream().map(PythonValues::str).toList() : List.of(str(value).split(",", -1));
        return values.stream().filter(s -> !s.isEmpty()).toList();
    }
    public static BigInteger nullableInt(Object value) {
        try { return integer(value); } catch (PythonError e) { return null; }
    }
    public static List<String> sortSignals(Map<String, ?> p) { return asList(p.get("sortBy")).stream().map(s -> s.split("=", 2)[0]).toList(); }
    public static List<String> sortDirections(Map<String, ?> p) { return asList(p.get("sortBy")).stream().filter(s -> s.contains("=")).map(s -> s.split("=", 2)[1]).toList(); }
    private static int index(Object values, Object value) {
        int at = ((List<?>) values).indexOf(value);
        if (at < 0) throw new PythonError("ValueError", "list.index(x): x not in list"); return at;
    }
    private static boolean eq(Map<String, ?> p, String key, String value) { return value.equals(p.get(key)); }
    private static boolean reversed(Map<String, ?> p, String a, String b) {
        if (!present(p, a) || !present(p, b)) return false;
        BigInteger start = nullableInt(p.get(a)), end = nullableInt(p.get(b));
        return start != null && end != null && end.compareTo(start) < 0;
    }
    public static List<String> validate(Map<String, ?> p) {
        Set<String> broken = new LinkedHashSet<>();
        if (!present(p, "locator")) broken.add("R_OBLIGATORY_LOCATOR");
        if (!present(p, "accessKey")) broken.add("R_OBLIGATORY_ACCESS_KEY");
        Object family = present(p, "DatasetFamily") ? p.get("DatasetFamily") : null;
        List<String> signals = asList(p.get("SignalClassList")), access = asList(p.get("AccessTier")), sort = sortSignals(p);
        if (family != null && !signals.isEmpty()) {
            if (family instanceof List<?> || family instanceof Map<?, ?>) {
                String type = family instanceof List<?> ? "list" : "dict";
                throw new PythonError("TypeError", "cannot use '" + type + "' as a dict key (unhashable type: '" + type + "')");
            }
            if (!strings(FAMILIES.getOrDefault(family, List.of())).containsAll(signals)) broken.add("R_SIGNAL_BY_FAMILY");
        }
        if (!sort.isEmpty() && !signals.isEmpty() && !signals.containsAll(sort)) broken.add("R_SORT_SIGNAL_SUBSET");
        if (present(p, "IndexedTimestamp") && present(p, "provisional")) broken.add("R_INDEXED_XOR_PROVISIONAL");
        if (reversed(p, "CaptureTimeStart", "CaptureTimeEnd")) broken.add("R_CAPTURE_RANGE");
        if (reversed(p, "offsetStart", "offsetEnd")) broken.add("R_OFFSET_RANGE");
        if (eq(p, "encoding", "zarr")) {
            if (present(p, "QualityTier") && index(DATA.get("quality"), p.get("QualityTier")) < index(DATA.get("quality"), "gold")) broken.add("R_ZARR_QUALITY");
            if (!present(p, "geometry")) broken.add("R_ZARR_QUALITY");
        }
        if (eq(p, "geometry", "volumetric") && (!present(p, "encoding") || index(DATA.get("encodings"), p.get("encoding")) < 1)) broken.add("R_VOLUME_MIN_ENCODING");
        if (eq(p, "responseFormat", "markup") && eq(p, "scanScope", "archive")) broken.add("R_MARKUP_NO_ARCHIVE");
        if (eq(p, "exactMatch", "enabled") && (present(p, "contributorTag") || present(p, "notesPattern"))) broken.add("R_EXACT_NO_APPROXIMATE");
        if (eq(p, "provisional", "enabled") && !eq(p, "AssetKind", "preview")) broken.add("R_PROVISIONAL_PREVIEW");
        if (present(p, "QualityTier") && !present(p, "AccessTier")) broken.add("R_QUALITY_NEEDS_ACCESS");
        BigInteger custodian = present(p, "custodianId") ? nullableInt(p.get("custodianId")) : null;
        boolean restricted = custodian != null && custodian.compareTo(BigInteger.valueOf(70000)) >= 0;
        if (restricted && eq(p, "ExcludeRestricted", "enabled")) broken.add("R_RESTRICTED_CUSTODIAN");
        if (eq(p, "complianceLock", "enabled") && (restricted || eq(p, "ExcludeRestricted", "disabled"))) broken.add("R_COMPLIANCE_LOCK");
        if ("OpenData".equals(family) && ((!access.isEmpty() && !Set.of("public", "community").containsAll(access)) || eq(p, "geometry", "volumetric"))) broken.add("R_OPEN_ACCESS");
        return List.copyOf(broken);
    }
    public static String assembleQuery(Map<String, ?> p) {
        List<String> result = new ArrayList<>();
        for (String key : QUERY_PARAMS) if (present(p, key)) result.add(key + "=" + quote(MULTI.contains(key) ? String.join(",", asList(p.get(key))) : str(p.get(key)), ",=*+:-"));
        return String.join("&", result);
    }
    public static String assembleUrl(String base, Object locator, Map<String, ?> params) {
        String query = assembleQuery(params);
        return base.replaceAll("/+$", "") + "/api/catalog/lookup/" + quote(str(locator), "/") + (query.isEmpty() ? "" : "?" + query);
    }
    public static Map<String, Object> parseQuery(Object locator, Map<String, ?> query) {
        Map<String, Object> params = new LinkedHashMap<>(); params.put("locator", locator);
        query.forEach((key, values) -> {
            Object value = values;
            if (values instanceof List<?> list) {
                if (list.isEmpty()) throw new PythonError("IndexError", "list index out of range"); value = list.getFirst();
            }
            params.put(key, MULTI.contains(key) ? asList(value) : value);
        });
        return java.util.Collections.unmodifiableMap(params);
    }
    public static String quote(String value, String safe) {
        int position = 0;
        for (int i = 0; i < value.length(); position++) {
            char c = value.charAt(i);
            if (Character.isHighSurrogate(c) && i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) { i += 2; continue; }
            if (Character.isSurrogate(c)) {
                int end = i + 1;
                while (end < value.length() && Character.isSurrogate(value.charAt(end))
                        && !(Character.isHighSurrogate(value.charAt(end)) && end + 1 < value.length() && Character.isLowSurrogate(value.charAt(end + 1)))) end++;
                String location = end == i + 1 ? "character '" + String.format(java.util.Locale.ROOT, "\\u%04x", (int) c) + "' in position " + position
                        : "characters in position " + position + "-" + (position + end - i - 1);
                throw new PythonError("UnicodeEncodeError", "'utf-8' codec can't encode " + location + ": surrogates not allowed");
            }
            i++;
        }
        StringBuilder result = new StringBuilder();
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 255;
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || "_.-~".indexOf(c) >= 0 || safe.indexOf(c) >= 0) result.append((char) c);
            else result.append('%').append("0123456789ABCDEF".charAt(c >>> 4)).append("0123456789ABCDEF".charAt(c & 15));
        }
        return result.toString();
    }
}
