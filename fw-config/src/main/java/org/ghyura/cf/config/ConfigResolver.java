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

import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Suggestions;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves settings once, in this precedence (architecture.xml): embedded defaults, the values translated from
 * legacy configuration files ({@link LegacyImporter}, origin LEGACY), an explicit properties file, declared
 * environment aliases, explicit overrides (command line or embedding). Later layers win per key. Every problem is collected and reported together; a resolution with any error throws one
 * {@link FrameworkException} carrying all diagnostics, so a config either fully validates or does not
 * exist. No file, environment or system property is read again after this returns.
 */
public final class ConfigResolver {

    private static final String DEFAULTS_RESOURCE = "runtime-defaults.properties";
    private static final int SUGGESTION_DISTANCE = 3;

    /**
     * One explicit override.
     *
     * @param origin how the caller names the source in locations, for example {@code --set} or {@code embedding}
     */
    public record Override(String key, String value, String origin) {
        public Override {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(origin, "origin");
        }
    }

    /**
     * A setting a scenario document chose, with the place in the document that chose it.
     */
    public record ScenarioValue(String key, String value, SourceLocation where) {
        public ScenarioValue {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
        }
    }

    private ConfigResolver() {
    }

    /**
     * @param file        an explicit properties file, or {@code null} for none; a missing or undecodable file is an error
     * @param environment the process environment (passed in so resolution never reads global state)
     * @param overrides   applied last, in order
     */
    public static EffectiveConfig resolve(Path file, Map<String, String> environment, List<Override> overrides) {
        return resolve(file, environment, overrides, null);
    }

    /**
     * Like {@link #resolve(Path, Map, List)} with a legacy import below every cf.* source. Its findings are
     * reported with the others: its errors fail the resolution, its warnings stay in the effective configuration,
     * and the import itself is kept there for reports and for the run gate.
     *
     * @param legacy what {@link LegacyImporter} made of legacy files, or null for none
     */
    public static EffectiveConfig resolve(Path file, Map<String, String> environment, List<Override> overrides,
                                          LegacyImport legacy) {
        if (file == null) {
            return resolveLayers(null, null, environment, overrides, Runtime.getRuntime().maxMemory(), legacy);
        }
        String origin = file.toString();
        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(Files.readAllBytes(file))).toString();
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_UNREADABLE,
                    "cannot read the configuration file as UTF-8 (" + e.getClass().getSimpleName() + ": "
                            + e.getMessage() + ")", SourceLocation.of(origin)), e);
        }
        return resolveLayers(origin, text, environment, overrides, Runtime.getRuntime().maxMemory(), legacy);
    }

    /** Like {@link #resolve} for properties text already in memory (embedding, tests). */
    public static EffectiveConfig resolveText(String origin, String propertiesText, Map<String, String> environment,
                                              List<Override> overrides) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(propertiesText, "propertiesText");
        return resolveLayers(origin, propertiesText, environment, overrides, Runtime.getRuntime().maxMemory(), null);
    }

    static EffectiveConfig resolveLayers(String fileOrigin, String fileText, Map<String, String> environment,
                                         List<Override> overrides, long maxHeap) {
        return resolveLayers(fileOrigin, fileText, environment, overrides, maxHeap, null);
    }

    static EffectiveConfig resolveLayers(String fileOrigin, String fileText, Map<String, String> environment,
                                         List<Override> overrides, long maxHeap, LegacyImport legacy) {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(overrides, "overrides");
        Map<String, ConfigKey<?>> registry = ConfigKeys.byName();
        List<Diagnostic> problems = new ArrayList<>();
        Map<String, ConfigEntry> winners = new LinkedHashMap<>();
        List<ConfigConflict> conflicts = new ArrayList<>();

        for (PropertiesText.Line line : defaultLines(registry)) {
            winners.put(line.key(), new ConfigEntry(line.key(), line.value(), ConfigOrigin.DEFAULT,
                    SourceLocation.of("<defaults>", line.line()), registry.get(line.key()).secret()));
        }

        if (legacy != null) {
            problems.addAll(legacy.diagnostics());
            for (LegacyImport.TargetValue value : legacy.overrides()) {
                ConfigKey<?> key = registry.get(value.key());
                if (key == null) {
                    throw internal("the legacy settings catalog maps " + value.legacyKey() + " to the undeclared key "
                            + value.key());
                }
                place(winners, conflicts, new ConfigEntry(value.key(), value.value(), ConfigOrigin.LEGACY, value.where(),
                        key.secret()));
            }
        }

        if (fileText != null) {
            for (PropertiesText.Line line : PropertiesText.parse(fileOrigin, fileText, problems)) {
                SourceLocation where = SourceLocation.of(fileOrigin, line.line());
                ConfigKey<?> key = registry.get(line.key());
                if (key == null) {
                    problems.add(unknownKey(registry, line.key(), where));
                } else {
                    place(winners, conflicts, new ConfigEntry(line.key(), line.value(), ConfigOrigin.FILE, where,
                            key.secret()));
                }
            }
        }

        for (ConfigKey<?> key : registry.values()) {
            String value = environment.get(key.environmentAlias());
            if (value != null) {
                place(winners, conflicts, new ConfigEntry(key.name(), value, ConfigOrigin.ENVIRONMENT,
                        SourceLocation.ofPath("environment", key.environmentAlias()), key.secret()));
            }
        }

        applyOverrides(registry, winners, conflicts, problems, overrides);
        return finish(registry, winners, conflicts, problems, maxHeap, legacy);
    }

    /**
     * A configuration derived from a resolved one with further explicit overrides on top (a campaign iteration's run id,
     * for instance); every other value keeps its origin, and the derived configuration is validated as a whole again.
     */
    public static EffectiveConfig derive(EffectiveConfig base, List<Override> overrides) {
        Map<String, ConfigKey<?>> registry = ConfigKeys.byName();
        Map<String, ConfigEntry> winners = new LinkedHashMap<>();
        for (ConfigEntry e : base.entries()) {
            winners.put(e.key(), e);
        }
        List<ConfigConflict> conflicts = new ArrayList<>(base.conflicts());
        List<Diagnostic> problems = new ArrayList<>(base.diagnostics());
        applyOverrides(registry, winners, conflicts, problems, overrides);
        return finish(registry, winners, conflicts, problems, Runtime.getRuntime().maxMemory(), base.legacyImport().orElse(null));
    }

    /**
     * The configuration with the settings a scenario document chose (an fwgen spec's goals and coverage choices), as the
     * layer between a legacy import and the properties file: a scenario value takes a key only from the defaults or a
     * legacy import; a key an explicit source set keeps that value, and the scenario's differing value is recorded as
     * overridden. The result is validated as a whole again.
     */
    public static EffectiveConfig withScenario(EffectiveConfig base, List<ScenarioValue> values) {
        Objects.requireNonNull(base, "base");
        if (values.isEmpty()) {
            return base;
        }
        Map<String, ConfigKey<?>> registry = ConfigKeys.byName();
        Map<String, ConfigEntry> winners = new LinkedHashMap<>();
        for (ConfigEntry e : base.entries()) {
            winners.put(e.key(), e);
        }
        List<ConfigConflict> conflicts = new ArrayList<>(base.conflicts());
        List<Diagnostic> problems = new ArrayList<>();
        for (Diagnostic d : base.diagnostics()) {
            if (d.code() != ErrorCode.CONFIG_INCONSISTENT) {
                problems.add(d);
            }
        }
        for (ScenarioValue value : values) {
            ConfigKey<?> key = registry.get(value.key());
            if (key == null) {
                throw internal("a scenario chose the undeclared setting " + value.key());
            }
            ConfigEntry entry = new ConfigEntry(value.key(), value.value(), ConfigOrigin.SCENARIO, value.where(), key.secret());
            ConfigEntry current = winners.get(value.key());
            if (current.origin() == ConfigOrigin.DEFAULT || current.origin() == ConfigOrigin.LEGACY) {
                place(winners, conflicts, entry);
            } else if (!current.rawValue().trim().equals(entry.rawValue().trim())) {
                conflicts.add(new ConfigConflict(value.key(), entry, current));
            }
        }
        return finish(registry, winners, conflicts, problems, Runtime.getRuntime().maxMemory(), base.legacyImport().orElse(null));
    }

    private static void applyOverrides(Map<String, ConfigKey<?>> registry, Map<String, ConfigEntry> winners, List<ConfigConflict> conflicts,
                                       List<Diagnostic> problems, List<Override> overrides) {
        for (Override override : overrides) {
            SourceLocation where = SourceLocation.ofPath(override.origin(), override.key());
            ConfigKey<?> key = registry.get(override.key());
            if (key == null) {
                problems.add(unknownKey(registry, override.key(), where));
            } else {
                place(winners, conflicts, new ConfigEntry(override.key(), override.value(), ConfigOrigin.OVERRIDE,
                        where, key.secret()));
            }
        }
    }

    private static EffectiveConfig finish(Map<String, ConfigKey<?>> registry, Map<String, ConfigEntry> winners,
                                          List<ConfigConflict> conflicts, List<Diagnostic> problems, long maxHeap, LegacyImport legacy) {
        Map<String, Object> parsed = new LinkedHashMap<>();
        boolean allParsed = true;
        for (ConfigKey<?> key : registry.values()) {
            ConfigEntry entry = winners.get(key.name());
            try {
                parsed.put(key.name(), key.parse(entry.rawValue()));
            } catch (ConfigValueException e) {
                allParsed = false;
                problems.add(Diagnostic.error(e.unsupportedCapability() ? ErrorCode.UNSUPPORTED_CAPABILITY
                                : ErrorCode.CONFIG_INVALID_VALUE, key.name() + ": " + e.getMessage(), entry.location(),
                        "key", key.name(), "value", entry.displayValue(), "origin", entry.origin().name()));
            }
        }

        RunConfig config = null;
        if (allParsed) {
            config = new RunConfig(parsed);
            ConfigConsistency.check(config, winners, maxHeap, problems);
        }

        boolean failed = false;
        for (Diagnostic d : problems) {
            failed |= d.severity().isError();
        }
        if (failed) {
            throw new FrameworkException(problems);
        }
        List<ConfigEntry> ordered = new ArrayList<>();
        for (ConfigKey<?> key : registry.values()) {
            ordered.add(winners.get(key.name()));
        }
        return new EffectiveConfig(Objects.requireNonNull(config), ordered, problems, legacy, conflicts);
    }

    /**
     * Puts {@code entry} over whatever a lower layer set, and records a conflict when that was not a default and
     * said something else (the layers are applied lowest first, so the later entry always wins).
     */
    private static void place(Map<String, ConfigEntry> winners, List<ConfigConflict> conflicts, ConfigEntry entry) {
        ConfigEntry previous = winners.put(entry.key(), entry);
        if (previous != null && previous.origin() != ConfigOrigin.DEFAULT && !previous.rawValue().trim().equals(entry.rawValue().trim())) {
            conflicts.add(new ConfigConflict(entry.key(), previous, entry));
        }
    }

    /** The embedded defaults. A defect in this file is a build defect, reported as INTERNAL_ERROR. */
    private static List<PropertiesText.Line> defaultLines(Map<String, ConfigKey<?>> registry) {
        String text;
        try (InputStream in = ConfigResolver.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (in == null) {
                throw internal("the embedded defaults resource " + DEFAULTS_RESOURCE + " is missing");
            }
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INTERNAL_ERROR,
                    "cannot read the embedded defaults: " + e.getMessage(), SourceLocation.of("<defaults>")), e);
        }
        List<Diagnostic> problems = new ArrayList<>();
        List<PropertiesText.Line> lines = PropertiesText.parse("<defaults>", text, problems);
        if (!problems.isEmpty()) {
            throw internal("the embedded defaults are malformed: " + problems.getFirst());
        }
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (PropertiesText.Line line : lines) {
            if (!registry.containsKey(line.key())) {
                throw internal("the embedded defaults define undeclared key " + line.key());
            }
            seen.put(line.key(), Boolean.TRUE);
        }
        for (String declared : registry.keySet()) {
            if (!seen.containsKey(declared)) {
                throw internal("the embedded defaults do not define " + declared);
            }
        }
        return lines;
    }

    private static FrameworkException internal(String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INTERNAL_ERROR, message, SourceLocation.of("<defaults>")));
    }

    private static Diagnostic unknownKey(Map<String, ConfigKey<?>> registry, String key, SourceLocation where) {
        String suggestion = Suggestions.closest(key, registry.keySet(), SUGGESTION_DISTANCE);
        StringBuilder message = new StringBuilder("'").append(key).append("' is not a setting of this build");
        if (!key.startsWith("cf.")) {
            message.append(" (target settings live under cf.*; legacy fw.properties keys are not read as configuration)");
        } else if (suggestion != null) {
            message.append("; did you mean '").append(suggestion).append("'?");
        }
        return suggestion == null
                ? Diagnostic.error(ErrorCode.CONFIG_UNKNOWN_KEY, message.toString(), where, "key", key)
                : Diagnostic.error(ErrorCode.CONFIG_UNKNOWN_KEY, message.toString(), where, "key", key,
                "suggestion", suggestion);
    }

}
