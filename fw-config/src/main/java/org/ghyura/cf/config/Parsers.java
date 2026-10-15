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

package org.ghyura.cf.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Strict value parsers for {@link ConfigKey}. Raw text is trimmed first; nothing else is forgiven. */
final class Parsers {

    private Parsers() {
    }

    @FunctionalInterface
    interface Parser<T> {
        T parse(String raw) throws ConfigValueException;
    }

    static Parser<Integer> intRange(int min, int max) {
        return raw -> {
            long value = integral(raw);
            if (value < min || value > max) {
                throw new ConfigValueException("expected an integer from " + min + " to " + max + ", got " + value, false);
            }
            return (int) value;
        };
    }

    static Parser<Long> longRange(long min, long max) {
        return raw -> {
            long value = integral(raw);
            if (value < min || value > max) {
                throw new ConfigValueException("expected an integer from " + min + " to " + max + ", got " + value, false);
            }
            return value;
        };
    }

    /** Any 64-bit integer; used for seeds, where negative values are legitimate. */
    static Parser<Long> anyLong() {
        return Parsers::integral;
    }

    /** cf.run.stop.target: empty, or a metric key, {@code >=} or {@code <=}, and a finite number. */
    static Parser<RunConfig.StopTarget> stopTarget() {
        return raw -> {
            String text = raw.trim();
            if (text.isEmpty()) {
                return RunConfig.StopTarget.NONE;
            }
            int ge = text.indexOf(">=");
            int le = text.indexOf("<=");
            int at = ge >= 0 ? ge : le;
            if (at <= 0 || (ge >= 0 && le >= 0)) {
                throw new ConfigValueException("a stop target is KEY>=VALUE or KEY<=VALUE, got '" + text + "'", false);
            }
            String key = text.substring(0, at).trim();
            if (!isMetricKey(key)) {
                throw new ConfigValueException("'" + key + "' is not a metric key (a letter or '_', then letters, digits, '_', '.' or '-')",
                        false);
            }
            double value;
            try {
                value = Double.parseDouble(text.substring(at + 2).trim());
            } catch (NumberFormatException e) {
                throw new ConfigValueException("the value of stop target '" + text + "' is not a number", false);
            }
            if (!Double.isFinite(value)) {
                throw new ConfigValueException("the value of stop target '" + text + "' is not finite", false);
            }
            return new RunConfig.StopTarget(key, ge >= 0, value);
        };
    }

    static Parser<Boolean> bool() {
        return raw -> switch (raw.trim()) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw new ConfigValueException("expected exactly 'true' or 'false', got '" + raw.trim() + "'", false);
        };
    }

    /**
     * The name prefix of generated virtual sheets: 1 to 64 characters with no white space and none of the
     * characters that structure FW_Seq cells (comma, brackets, double quote), so a generated name is always a
     * single sheet-name token. Refused at configuration time, before any scenario is read.
     */
    static Parser<String> virtualSheetPrefix() {
        return raw -> {
            String text = raw.trim();
            if (text.isEmpty()) {
                throw new ConfigValueException("expected a sheet-name prefix, got an empty value", false);
            }
            if (text.length() > 64) {
                throw new ConfigValueException("expected at most 64 characters, got " + text.length(), false);
            }
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c) || Character.isISOControl(c) || c == ',' || c == '(' || c == ')' || c == '"') {
                    throw new ConfigValueException("a sheet-name prefix holds no white space, control character, comma, "
                            + "bracket or double quote, got '" + text + "'", false);
                }
            }
            return text;
        };
    }

    /** Any text, exactly as given: nothing is trimmed, so white space can be the value (a separator, for instance). */
    static Parser<String> verbatim() {
        return raw -> raw;
    }

    /**
     * The Python add-on's candidate sandbox, trimmed: empty for none, bwrap or bwrap-network (bubblewrap), docker:IMAGE or
     * docker:IMAGE@NETWORK (a container; a final '@' part without ':' names the network, so a digest reference stays whole).
     */
    static Parser<String> pythonSandbox() {
        return raw -> {
            String text = raw.trim();
            if (text.isEmpty() || text.equals("bwrap") || text.equals("bwrap-network")) {
                return text;
            }
            if (text.startsWith("docker:")) {
                String rest = text.substring("docker:".length());
                int at = rest.lastIndexOf('@');
                String image = at >= 0 && rest.indexOf(':', at) < 0 ? rest.substring(0, at) : rest;
                String network = image.length() < rest.length() ? rest.substring(at + 1) : "none";
                if (image.matches("[A-Za-z0-9][A-Za-z0-9._/:@-]*") && network.matches("[A-Za-z0-9][A-Za-z0-9_.-]*")) {
                    return text;
                }
            }
            throw new ConfigValueException("a Python sandbox is empty, bwrap, bwrap-network, docker:IMAGE or docker:IMAGE@NETWORK, got '"
                    + text + "'", false);
        };
    }

    /** A file path, trimmed; empty means none. Whether the file exists is checked where it is read. */
    static Parser<String> path() {
        return raw -> {
            String text = raw.trim();
            for (int i = 0; i < text.length(); i++) {
                if (Character.isISOControl(text.charAt(i))) {
                    throw new ConfigValueException("a path holds no control character", false);
                }
            }
            return text;
        };
    }

    /** A run id: 1..64 characters of {@code [A-Za-z0-9._-]}, trimmed. */
    static Parser<String> runId() {
        return raw -> {
            String text = raw.trim();
            if (!text.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new ConfigValueException("a run id is 1..64 characters of [A-Za-z0-9._-], got '" + raw + "'", false);
            }
            return text;
        };
    }

    /** {@code generated-java} or a SUT plugin id: 1..64 characters of {@code [A-Za-z0-9._-]}, trimmed. */
    static Parser<String> sutName() {
        return raw -> {
            String text = raw.trim();
            if (!text.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new ConfigValueException("generated-java or a plugin id of 1..64 characters of [A-Za-z0-9._-], got '"
                        + raw + "'", false);
            }
            return text;
        };
    }

    /** NAME=VALUE environment assignments separated by ';', each name an environment variable name, none twice. */
    static Parser<List<String>> envAssignments() {
        return raw -> {
            List<String> entries = new java.util.ArrayList<>();
            List<String> names = new java.util.ArrayList<>();
            for (String piece : raw.split(";", -1)) {
                String text = piece.trim();
                if (text.isEmpty()) {
                    continue;
                }
                int eq = text.indexOf('=');
                String name = eq < 0 ? text : text.substring(0, eq).trim();
                if (eq < 0 || !name.matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
                    throw new ConfigValueException("an environment assignment is NAME=VALUE with NAME of [A-Za-z_][A-Za-z0-9_]*, got '" + text + "'", false);
                }
                for (int i = 0; i < text.length(); i++) {
                    if (Character.isISOControl(text.charAt(i))) {
                        throw new ConfigValueException("an environment assignment holds no control character", false);
                    }
                }
                if (names.contains(name)) {
                    throw new ConfigValueException("environment variable '" + name + "' is assigned twice", false);
                }
                names.add(name);
                entries.add(name + "=" + text.substring(eq + 1).trim());
            }
            return List.copyOf(entries);
        };
    }

    /**
     * Environment ids separated by commas, each trimmed and 1..64 characters of {@code [A-Za-z0-9._:-]}; empty pieces are
     * dropped as the legacy control plane dropped them, empty text is none, and a repeated id is refused.
     */
    static Parser<List<String>> environmentList() {
        return raw -> {
            List<String> ids = new java.util.ArrayList<>();
            for (String piece : raw.split(",", -1)) {
                String text = piece.trim();
                if (text.isEmpty()) {
                    continue;
                }
                if (!text.matches("[A-Za-z0-9._:-]{1,64}")) {
                    throw new ConfigValueException("an environment id is 1..64 characters of [A-Za-z0-9._:-], got '" + text + "'", false);
                }
                if (ids.contains(text)) {
                    throw new ConfigValueException("environment '" + text + "' is listed twice", false);
                }
                ids.add(text);
            }
            return List.copyOf(ids);
        };
    }

    /** A finite number above 0 and at most 1, trimmed. */
    static Parser<Double> unitInterval() {
        return raw -> {
            double value = nonNegativeDouble().parse(raw);
            if (!(value > 0) || value > 1) {
                throw new ConfigValueException("expected a number above 0 and at most 1, got '" + raw.trim() + "'", false);
            }
            return value;
        };
    }

    /** A finite number at least 0, trimmed. */
    static Parser<Double> nonNegativeDouble() {
        return raw -> {
            String text = raw.trim();
            double value;
            try {
                value = Double.parseDouble(text);
            } catch (NumberFormatException e) {
                throw new ConfigValueException("expected a finite number at least 0, got '" + text + "'", false);
            }
            if (!Double.isFinite(value) || value < 0 || text.endsWith("d") || text.endsWith("D") || text.endsWith("f")
                    || text.endsWith("F")) {
                throw new ConfigValueException("expected a finite number at least 0, got '" + text + "'", false);
            }
            return value;
        };
    }

    /** A metric key: a letter or '_' first, then letters, digits, '_', '.' or '-'. */
    static boolean isMetricKey(String key) {
        return key.matches("[\\p{L}_][\\p{L}\\p{N}_.\\-]*");
    }

    /** Metric keys separated by commas, each trimmed; empty pieces dropped; a repeated key refused. */
    static Parser<List<String>> metricKeyList() {
        return raw -> {
            List<String> keys = new java.util.ArrayList<>();
            for (String piece : raw.split(",", -1)) {
                String key = piece.trim();
                if (key.isEmpty()) {
                    continue;
                }
                if (!isMetricKey(key)) {
                    throw new ConfigValueException("'" + key + "' is not a metric key (a letter or '_', then letters, digits, "
                            + "'_', '.' or '-')", false);
                }
                if (keys.contains(key)) {
                    throw new ConfigValueException("metric key '" + key + "' is listed twice", false);
                }
                keys.add(key);
            }
            return List.copyOf(keys);
        };
    }

    /**
     * Goals separated by commas: {@code key:min}, {@code key:max} or {@code key:target:VALUE}, each optionally followed by
     * {@code :WEIGHT}; a key appears once.
     */
    static Parser<List<RunConfig.AnalysisGoal>> goalList() {
        return raw -> {
            List<RunConfig.AnalysisGoal> goals = new java.util.ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (String piece : raw.split(",", -1)) {
                String text = piece.trim();
                if (text.isEmpty()) {
                    continue;
                }
                String[] parts = text.split(":", -1);
                String key = parts[0].trim();
                if (parts.length < 2 || !isMetricKey(key)) {
                    throw new ConfigValueException("goal '" + text + "' is not key:min, key:max or key:target:VALUE[:WEIGHT]", false);
                }
                RunConfig.GoalMode mode;
                switch (parts[1].trim()) {
                    case "min" -> mode = RunConfig.GoalMode.MIN;
                    case "max" -> mode = RunConfig.GoalMode.MAX;
                    case "target" -> mode = RunConfig.GoalMode.TARGET;
                    default -> throw new ConfigValueException("goal '" + text + "': the mode is min, max or target, got '"
                            + parts[1].trim() + "'", false);
                }
                int next = 2;
                double target = 0;
                if (mode == RunConfig.GoalMode.TARGET) {
                    if (parts.length < 3) {
                        throw new ConfigValueException("goal '" + text + "': a target goal needs its value, key:target:VALUE", false);
                    }
                    target = finite(parts[2], text);
                    next = 3;
                }
                double weight = 1;
                if (parts.length > next) {
                    weight = finite(parts[next], text);
                    if (weight < 0) {
                        throw new ConfigValueException("goal '" + text + "': the weight must be at least 0", false);
                    }
                    next++;
                }
                if (parts.length > next) {
                    throw new ConfigValueException("goal '" + text + "' has more parts than key:mode[:target]:weight", false);
                }
                if (!seen.add(key)) {
                    throw new ConfigValueException("goal key '" + key + "' appears twice", false);
                }
                goals.add(new RunConfig.AnalysisGoal(key, mode, target, weight));
            }
            return List.copyOf(goals);
        };
    }

    private static double finite(String raw, String goal) throws ConfigValueException {
        try {
            double value = Double.parseDouble(raw.trim());
            if (Double.isFinite(value) && !raw.trim().matches(".*[dDfF]$")) {
                return value;
            }
        } catch (NumberFormatException e) {
            // reported below
        }
        throw new ConfigValueException("goal '" + goal + "': '" + raw.trim() + "' is not a finite number", false);
    }

    /** Paths separated by the platform path separator, each trimmed; empty entries are dropped and empty text is none. */
    static Parser<List<String>> pathList() {
        return raw -> {
            List<String> paths = new java.util.ArrayList<>();
            for (String piece : raw.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator), -1)) {
                String text = path().parse(piece);
                if (!text.isEmpty()) {
                    paths.add(text);
                }
            }
            return List.copyOf(paths);
        };
    }

    /**
     * {@code all}, {@code none}, or sizes from 1 to 64 separated by commas with optional white space around them (the legacy
     * {@code trim().split("\\s{0,},\\s{0,}")}); a repeated size is refused.
     */
    static Parser<RunConfig.SizeSet> sizeSet() {
        return raw -> {
            String text = raw.trim();
            if (text.equals("all")) {
                return RunConfig.SizeSet.all();
            }
            if (text.equals("none")) {
                return RunConfig.SizeSet.of(List.of());
            }
            if (text.isEmpty()) {
                throw new ConfigValueException("expected 'all' or sizes such as 1,2,3, got an empty value", false);
            }
            java.util.TreeSet<Integer> sizes = new java.util.TreeSet<>();
            for (String part : text.split("\\s{0,},\\s{0,}", -1)) {
                if (!part.matches("[0-9]{1,2}")) {
                    throw new ConfigValueException("expected 'all' or sizes from 1 to 64 separated by commas, got '" + text + "'", false);
                }
                int size = Integer.parseInt(part);
                if (size < 1 || size > 64) {
                    throw new ConfigValueException("an optional combination size is from 1 to 64 (the absent branch is "
                            + "cf.optional.includeAbsent), got " + size, false);
                }
                if (!sizes.add(size)) {
                    throw new ConfigValueException("the size " + size + " is listed twice in '" + text + "'", false);
                }
            }
            return RunConfig.SizeSet.of(sizes);
        };
    }

    /**
     * Regular expressions separated as the legacy Reader separated them: the text is trimmed and split by
     * {@code separator}; empty pieces are dropped; each piece must compile. Empty text is the empty list.
     */
    static Parser<List<String>> regexList(String separator) {
        return raw -> {
            String text = raw.trim();
            if (text.isEmpty()) {
                return List.of();
            }
            List<String> items = new ArrayList<>();
            for (String piece : text.split(separator)) {
                if (piece.isEmpty()) {
                    continue;
                }
                try {
                    java.util.regex.Pattern.compile(piece);
                } catch (java.util.regex.PatternSyntaxException e) {
                    throw new ConfigValueException("'" + piece + "' is not a regular expression: " + e.getDescription(), false);
                }
                items.add(piece);
            }
            return List.copyOf(items);
        };
    }

    /**
     * An enumerated token matched exactly (case-sensitive). {@code unsupported} lists well-known tokens that
     * name capabilities the architecture excludes; they are refused as UNSUPPORTED_CAPABILITY, not as typos.
     */
    static <E extends Enum<E>> Parser<E> choice(Class<E> type, Function<E, String> token, Set<String> unsupported) {
        return raw -> {
            String text = raw.trim();
            List<String> allowed = new ArrayList<>();
            for (E constant : type.getEnumConstants()) {
                String t = token.apply(constant);
                if (t.equals(text)) {
                    return constant;
                }
                allowed.add(t);
            }
            if (unsupported.contains(text)) {
                throw new ConfigValueException("'" + text + "' is not available in this architecture (supported: "
                        + String.join(", ", allowed) + ")", true);
            }
            throw new ConfigValueException("expected one of " + String.join(", ", allowed) + ", got '" + text + "'", false);
        };
    }

    private static long integral(String raw) throws ConfigValueException {
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new ConfigValueException("expected an integer, got an empty value", false);
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw new ConfigValueException("expected an integer, got '" + text + "'", false);
        }
    }
}
