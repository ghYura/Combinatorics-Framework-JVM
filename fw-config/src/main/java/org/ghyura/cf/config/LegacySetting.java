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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One setting name the legacy framework read, and what this build does with it (architect decisions F6-F31,
 * review HG05). Loaded from the catalog {@code legacy-settings.xml} by {@link LegacySettings}.
 *
 * @param name        the key, variable or argument name; with {@code prefix} the start of a family of names
 * @param prefix      whether {@code name} matches every name that starts with it
 * @param kind        where the legacy framework read it from
 * @param disposition what this build does with it, unless one of {@code values} says otherwise for a value
 * @param target      the cf.* key (or surface) a mapped or superseded setting goes to, or null
 * @param transform   how a mapped value is converted, or null
 * @param legacyDefault the value the legacy code used when the setting was absent or blank, or null
 * @param defaultWhenAbsent whether an import that does not set it reproduces {@code legacyDefault}, because the
 *                    legacy default differs from this build's default in what it does (decision G6)
 * @param aliasOf     the setting this one is an older name of (read only when that one is absent), or null
 * @param owner       the work package that owns its semantics, or null
 * @param category    a grouping (storage, transport, process, ...), or null
 * @param secret      whether a value is a credential that no report may show
 * @param reason      what the legacy setting did and why this build does what it does
 * @param values      dispositions that hold for particular values only
 * @param foundIn     the legacy properties files that set it (no values are recorded)
 * @param readAt      the legacy code that reads it
 */
public record LegacySetting(String name, boolean prefix, Kind kind, Disposition disposition, String target,
                            Transform transform, String legacyDefault, boolean defaultWhenAbsent, String aliasOf, String owner,
                            String category, boolean secret, String reason, List<ValueRule> values, List<Location> foundIn,
                            List<Location> readAt) {

    /** Where the legacy framework read a setting from. */
    public enum Kind {
        /** A key of a legacy properties file (fw.properties and its siblings). */
        PROPERTY("property"),
        /** A JVM system property of a legacy process ({@code -Dfw.exec.threads}). */
        SYSTEM_PROPERTY("system-property"),
        /** An argument of the legacy executor's command line. */
        EXECUTOR_ARGUMENT("executor-argument"),
        /** An environment variable of a legacy process or tool. */
        ENVIRONMENT("environment"),
        /** A name the legacy code reads that is not a framework setting (JDK properties, derived properties). */
        NOT_A_SETTING("not-a-setting");

        private final String token;

        Kind(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        /** Whether an import file may name it: properties keys and, as an import convenience, system properties. */
        public boolean importable() {
            return this == PROPERTY || this == SYSTEM_PROPERTY;
        }

        static Kind ofToken(String token) {
            for (Kind k : values()) {
                if (k.token.equals(token)) {
                    return k;
                }
            }
            throw new IllegalArgumentException("unknown kind: " + token);
        }
    }

    /** What this build does with a legacy setting (or with one value of it). */
    public enum Disposition {
        /** Converted into a cf.* setting with the legacy parsing rule. */
        MAPPED("mapped"),
        /** This build already behaves as the value asks; nothing to convert. */
        HONORED("honored"),
        /** Belongs to a mechanism the architecture removed (database, files, processes, polling, console); reported, not applied. */
        RETIRED("retired"),
        /** Set in legacy files but read by no legacy code; it had no effect there either. */
        INERT("inert"),
        /** Retained semantics owned by a later work package; reported as pending and refused by a run until then. */
        DEFERRED("deferred"),
        /** Requests behaviour the architecture rules out; refused. */
        UNSUPPORTED("unsupported"),
        /** Replaced by another surface of this build (a command-line option, the file extension, an API). */
        SUPERSEDED("superseded"),
        /** Not a framework setting at all. */
        NOT_A_SETTING("not-a-setting");

        private final String token;

        Disposition(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        static Disposition ofToken(String token) {
            for (Disposition d : values()) {
                if (d.token.equals(token)) {
                    return d;
                }
            }
            throw new IllegalArgumentException("unknown disposition: " + token);
        }
    }

    /** How a mapped legacy value becomes a target value; each follows the legacy parsing code exactly. */
    public enum Transform {
        /** AppConfig.getBoolOrDefault: blank gives the default; only "true", any case, is true. */
        LEGACY_BOOLEAN("legacy-boolean"),
        /** AppConfig.getStringOrDefault: blank gives the default; otherwise the trimmed text. */
        LEGACY_STRING("legacy-string"),
        /** core.keys.dispatch: short or auto, any case; blank is short. */
        KEYS_DISPATCH("keys-dispatch"),
        /** core.keys.strategy: identity, anchor or optimal, any case; blank is identity. */
        KEYS_STRATEGY("keys-strategy"),
        /** AppConfig.resolveWorkerCount: digits, blank or 0 meaning all processors; anything else is an error. */
        LEGACY_WORKER_COUNT("legacy-worker-count"),
        /** MainWatch.parsePositiveInt: a positive integer or an error. */
        POSITIVE_INT("positive-int"),
        /** BundleControlPlane.parseInt(value, 1): an integer, and 1 when the text is not one. */
        LEGACY_INT_DEFAULT_1("legacy-int-default-1"),
        /** Boolean.parseBoolean of the untrimmed value, as AppConfig read it: only "true", any case, is true. */
        PARSE_BOOLEAN("parse-boolean"),
        /**
         * core.threading.parallelizeSubCombosIfPossible, read with Boolean.parseBoolean of the untrimmed value:
         * true chose the parallel library (PARALLEL), anything else combinatoricslib3 (COMBINATORICSLIB3).
         */
        LEGACY_LIBRARY("legacy-library"),
        /** AppConfig.parseEnum: case-insensitive name of the target's choices; blank gives the default; other text is refused. */
        LEGACY_ENUM("legacy-enum"),
        /** Long.parseLong, validated 1..Long.MAX_VALUE as AppConfig.requireLong did. */
        LEGACY_LONG("legacy-long"),
        /** Integer.parseInt, validated 0..Integer.MAX_VALUE as AppConfig.requireInt did. */
        LEGACY_INT("legacy-int"),
        /** trim().split("\\s{0,},\\s{0,}") with Integer.parseInt per piece (AppConfig.parseIntList, ReaderConfig). */
        LEGACY_INT_LIST("legacy-int-list"),
        /** The value as java.util.Properties read it (leading white space dropped, the rest kept), empty included. */
        LEGACY_TEXT("legacy-text"),
        /** A path, trimmed; blank is none (OptionalBondFilter.loadOrNull). */
        LEGACY_PATH("legacy-path"),
        /** reader.core.processIsOpt with Boolean.parseBoolean: true is on, anything else off. */
        LEGACY_IS_OPT("legacy-is-opt"),
        /** reader.out.replaceWithCurrentComboSequence: on when it equals FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE, any case. */
        LEGACY_MARKER_SWITCH("legacy-marker-switch"),
        /**
         * results.db.onlyFW_CUSTOM_VAR with Boolean.parseBoolean of the untrimmed value: true chose the FW_CUSTOM_VAR verdict
         * field, anything else FW_VAR.
         */
        LEGACY_VERDICT("legacy-verdict"),
        /** reader.handoff.runId, read with getProperty(key, ""): blank minted a new id per run; otherwise the trimmed text. */
        LEGACY_RUN_ID("legacy-run-id"),
        /**
         * fw.controlplane.maxInFlight: parseInt(value.trim(), 64) and below 1 raised to 1, as BundleControlPlane did; above
         * 1024 lowered to cf.execution.workers' bound with a warning.
         */
        LEGACY_IN_FLIGHT("legacy-in-flight"),
        /** fw.controlplane.serializePerHost: Boolean.parseBoolean of the untrimmed value; blank left it to metricSensitivity. */
        LEGACY_SERIALIZE("legacy-serialize"),
        /** fw.controlplane.metricSensitivity: noisy, perf, latency, throughput, continuous serialize; deterministic, verdict, fw_var, blank do not. */
        LEGACY_METRIC_SENSITIVITY("legacy-metric-sensitivity"),
        /** fw.controlplane.budgetMillis: parseLong(value.trim(), 0) and below 0 raised to 0 (no budget), as BundleControlPlane did. */
        LEGACY_BUDGET_MILLIS("legacy-budget-millis"),
        /** -Dfw.exec.mode: passonly (any case, nothing around it) kept the winners' sources; anything else the fail-only default. */
        LEGACY_EXEC_MODE("legacy-exec-mode"),
        /**
         * fw.analyzer.goals as AnalyzerWiring read it: key:mode[:target[:weight]], max/maximize and target, anything else
         * min, the third part a target even for min and max (ignored there), fewer than two parts skipped.
         */
        LEGACY_GOALS("legacy-goals"),
        /** A comma-separated list of metric keys, each trimmed, blanks dropped (fw.analyzer.minimizeKeys/maximizeKeys). */
        LEGACY_KEY_LIST("legacy-key-list"),
        /** fw.analyzer.autoDiscover: anything but false (any case) and 0 meant yes. */
        LEGACY_DISCOVER("legacy-discover"),
        /** fw.analyzer.autoDiscoverMode: min/minimize, max/maximize, anything else auto. */
        LEGACY_DISCOVER_MODE("legacy-discover-mode"),
        /** fw.analyzer.autoDiscoverWeight: parseDouble, 1.0 when unparseable; negative or nonfinite refused. */
        LEGACY_DISCOVER_WEIGHT("legacy-discover-weight"),
        /** fw.analyzer.topK: Integer.parseInt of the untrimmed value, below 1 read as 1. */
        LEGACY_TOP_K("legacy-top-k");

        private final String token;

        Transform(String token) {
            this.token = token;
        }

        public String token() {
            return token;
        }

        static Transform ofToken(String token) {
            for (Transform t : values()) {
                if (t.token.equals(token)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("unknown transform: " + token);
        }
    }

    /** A disposition that holds for one value, compared trimmed and ignoring case. */
    public record ValueRule(String text, Disposition disposition, String reason) {
        public ValueRule {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** A place in the legacy checkout, relative to its root. */
    public record Location(String path, int line) {
        public Location {
            Objects.requireNonNull(path, "path");
        }

        @Override
        public String toString() {
            return path + ":" + line;
        }
    }

    public LegacySetting {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(reason, "reason");
        values = List.copyOf(values);
        foundIn = List.copyOf(foundIn);
        readAt = List.copyOf(readAt);
        if (disposition == Disposition.MAPPED && kind.importable() && (target == null || transform == null)) {
            throw new IllegalArgumentException(name + ": an importable mapped setting needs a target and a transform");
        }
        if (disposition != Disposition.MAPPED && transform != null) {
            throw new IllegalArgumentException(name + ": only a mapped setting has a transform");
        }
        if (defaultWhenAbsent && (disposition != Disposition.MAPPED || !kind.importable() || transform == null
                || legacyDefault == null || prefix)) {
            throw new IllegalArgumentException(name + ": only a mapped key with a transform and a legacy default can "
                    + "reproduce that default when it is absent");
        }
    }

    /** Whether this entry covers {@code key}. */
    public boolean matches(String key) {
        return prefix ? key.startsWith(name) : key.equals(name);
    }

    /** The disposition for one value: a value rule when one matches, otherwise the setting's own. */
    public Disposition dispositionFor(String value) {
        return ruleFor(value).map(ValueRule::disposition).orElse(disposition);
    }

    /** The value rule that matches {@code value} (trimmed, ignoring case), if any. */
    public Optional<ValueRule> ruleFor(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        for (ValueRule rule : values) {
            if (rule.text().toLowerCase(Locale.ROOT).equals(text)) {
                return Optional.of(rule);
            }
        }
        return Optional.empty();
    }
}
