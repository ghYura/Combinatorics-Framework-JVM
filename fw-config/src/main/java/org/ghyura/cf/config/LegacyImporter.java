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
import org.ghyura.cf.model.Severity;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Suggestions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Translates legacy properties files (fw.properties and its siblings) into this build's configuration, key by
 * key, with nothing passed through and nothing dropped silently (architect decisions F6-F31, review HG05).
 *
 * <p>Every key is looked up in the closed {@link LegacySettings} catalog. A mapped key becomes a cf.* value
 * through the exact parsing rule the legacy code applied; a retired, inert, superseded or honoured key is
 * reported and changes nothing; a deferred key (retained behaviour of a later work package) is reported as
 * pending, a warning now, and a run must refuse it until that package lands; an unsupported value and a key the
 * catalog does not know are errors. Secret values are redacted before anything is stored.</p>
 *
 * <p>Files are read as ISO-8859-1 and parsed with {@link java.util.Properties} rules, as the legacy
 * {@code Properties.load(InputStream)} did. A key repeated in one file keeps its last value, as there, with a
 * warning; later files win over earlier ones. Keys the legacy executor read as JVM system properties
 * ({@code fw.exec.threads}) may be given in an import file too. The environment is reported, never applied: a
 * legacy variable that is set (such as CORE_DB_PASSWORD) appears in the report with its disposition.</p>
 */
public final class LegacyImporter {

    private static final Pattern SECRET_NAME = Pattern.compile("(?i)password|passwd|secret|token|credential");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final Pattern INTEGER = Pattern.compile("[+-]?\\d+");

    private LegacyImporter() {
    }

    /** One legacy text to import and the name its locations use. */
    public record Source(String origin, String text) {
        public Source {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(text, "text");
        }
    }

    /** Reads the files (ISO-8859-1, as the legacy Properties.load did) and imports them in order. */
    public static LegacyImport importFiles(List<Path> files, Map<String, String> environment) {
        List<Source> sources = new ArrayList<>();
        List<Diagnostic> unreadable = new ArrayList<>();
        for (Path file : files) {
            try {
                sources.add(new Source(file.toString(), new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1)));
            } catch (IOException e) {
                unreadable.add(Diagnostic.error(ErrorCode.CONFIG_UNREADABLE, "cannot read the legacy configuration file ("
                        + e.getClass().getSimpleName() + ": " + e.getMessage() + ")", SourceLocation.of(file.toString())));
            }
        }
        LegacyImport imported = importSources(sources, environment);
        if (unreadable.isEmpty()) {
            return imported;
        }
        List<Diagnostic> all = new ArrayList<>(unreadable);
        all.addAll(imported.diagnostics());
        List<String> names = new ArrayList<>();
        for (Path file : files) {
            names.add(file.toString());
        }
        return new LegacyImport(imported.catalogCommit(), names, imported.entries(), imported.environment(),
                imported.overrides(), imported.pending(), all);
    }

    private record Occurrence(String key, String value, SourceLocation where) {
    }

    /** Imports legacy texts already in memory, in order. */
    public static LegacyImport importSources(List<Source> sources, Map<String, String> environment) {
        return importSources(sources, environment, LegacySettings.catalog());
    }

    /** Imports legacy texts against this catalog. */
    static LegacyImport importSources(List<Source> sources, Map<String, String> environment, LegacySettings catalog) {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(environment, "environment");
        List<Diagnostic> diagnostics = new ArrayList<>();
        List<String> files = new ArrayList<>();

        // the winning occurrence of every key: the last in a file (Properties), a later file over an earlier one
        Map<String, Occurrence> winners = new LinkedHashMap<>();
        for (Source source : sources) {
            files.add(source.origin());
            Map<String, Occurrence> inThisFile = new LinkedHashMap<>();
            for (PropertiesText.Line line : PropertiesText.parseAll(source.origin(), source.text(), diagnostics)) {
                SourceLocation where = SourceLocation.of(source.origin(), line.line());
                Occurrence earlier = inThisFile.get(line.key());
                if (earlier != null) {
                    diagnostics.add(new Diagnostic(Severity.WARNING, ErrorCode.CONFIG_DUPLICATE_KEY, "'" + line.key()
                            + "' is set again; the legacy Properties.load kept the last value, so does the import (first set on "
                            + "line " + earlier.where().line() + ")", where, Map.of("key", line.key())));
                }
                Occurrence occurrence = new Occurrence(line.key(), line.value(), where);
                inThisFile.put(line.key(), occurrence);
                winners.put(line.key(), occurrence);
            }
        }

        List<LegacyImport.Entry> entries = new ArrayList<>();
        List<LegacyImport.Pending> pending = new ArrayList<>();
        Map<String, List<LegacyImport.TargetValue>> byTarget = new LinkedHashMap<>();
        for (Occurrence o : winners.values()) {
            LegacySetting setting = catalog.find(o.key()).orElse(null);
            boolean secret = SECRET_NAME.matcher(o.key()).find() || (setting != null && setting.secret());
            String shown = secret ? LegacyImport.REDACTED : o.value();
            if (setting == null || !setting.kind().importable()) {
                String why = setting == null
                        ? "'" + o.key() + "' is not a setting the legacy framework read (catalog of " + catalog.sourceCommit()
                        + ")" + hint(catalog, o.key())
                        : "'" + o.key() + "' is " + article(setting.kind()) + " of the legacy framework, not a key of its "
                        + "properties files: " + setting.reason();
                diagnostics.add(Diagnostic.error(ErrorCode.CONFIG_LEGACY_UNCLASSIFIED, why, o.where(), "key", o.key()));
                entries.add(new LegacyImport.Entry(o.key(), o.where(), shown, setting == null ? null : setting.kind(), null,
                        null, null, null, why));
                continue;
            }
            if (setting.aliasOf() != null && winners.containsKey(setting.aliasOf())) {
                entries.add(new LegacyImport.Entry(o.key(), o.where(), shown, setting.kind(), setting.disposition(),
                        setting.target(), null, setting.owner(), "Shadowed: " + setting.aliasOf()
                        + " is set as well and wins, as the legacy code read it first."));
                continue;
            }
            LegacySetting.Disposition disposition = setting.dispositionFor(o.value());
            String note = setting.ruleFor(o.value()).map(LegacySetting.ValueRule::reason).orElse(setting.reason());
            switch (disposition) {
                case MAPPED -> {
                    Mapped mapped = map(setting, o, diagnostics);
                    if (mapped.value() != null) {
                        byTarget.computeIfAbsent(setting.target(), k -> new ArrayList<>())
                                .add(new LegacyImport.TargetValue(setting.target(), mapped.value(), o.where(), o.key()));
                    }
                    entries.add(new LegacyImport.Entry(o.key(), o.where(), shown, setting.kind(), disposition, setting.target(),
                            mapped.value(), setting.owner(), mapped.note() == null ? note : note + " " + mapped.note()));
                }
                case DEFERRED -> {
                    String owner = setting.owner() == null ? "a later work package" : setting.owner();
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_PENDING, "'" + o.key() + "' asks for retained "
                            + "legacy behaviour that " + owner + " implements; this build reports it and a run refuses it until "
                            + "then: " + note, o.where(), "key", o.key(), "owner", owner));
                    pending.add(new LegacyImport.Pending(o.key(), o.where(), owner, note));
                    entries.add(new LegacyImport.Entry(o.key(), o.where(), shown, setting.kind(), disposition, setting.target(),
                            null, setting.owner(), note));
                }
                case UNSUPPORTED -> {
                    diagnostics.add(Diagnostic.error(ErrorCode.CONFIG_LEGACY_UNSUPPORTED, "'" + o.key() + "' = '" + shown
                            + "' asks for behaviour this architecture rules out: " + note, o.where(), "key", o.key()));
                    entries.add(new LegacyImport.Entry(o.key(), o.where(), shown, setting.kind(), disposition, setting.target(),
                            null, setting.owner(), note));
                }
                default -> entries.add(new LegacyImport.Entry(o.key(), o.where(), shown, setting.kind(), disposition,
                        setting.target(), null, setting.owner(), note));
            }
        }

        // a switch whose legacy default does something else than this build's default is reproduced when an imported
        // configuration leaves it out, as the legacy process would have run without it (decision G6)
        if (!sources.isEmpty()) {
            for (LegacySetting setting : catalog.all()) {
                if (!setting.defaultWhenAbsent() || winners.containsKey(setting.name())) {
                    continue;
                }
                Mapped mapped = map(setting, new Occurrence(setting.name(), setting.legacyDefault(), SourceLocation.NONE), diagnostics);
                byTarget.computeIfAbsent(setting.target(), k -> new ArrayList<>())
                        .add(new LegacyImport.TargetValue(setting.target(), mapped.value(), SourceLocation.NONE, setting.name()));
                entries.add(new LegacyImport.Entry(setting.name(), SourceLocation.NONE, "", setting.kind(),
                        LegacySetting.Disposition.MAPPED, setting.target(), mapped.value(), setting.owner(), "Absent from every "
                        + "imported file: the legacy default " + setting.legacyDefault() + " applies, because it does something "
                        + "else than this build's default (decision G6); a native setting overrides it. " + setting.reason()));
            }
        }

        List<LegacyImport.TargetValue> overrides = new ArrayList<>();
        for (Map.Entry<String, List<LegacyImport.TargetValue>> target : byTarget.entrySet()) {
            List<LegacyImport.TargetValue> values = target.getValue();
            LegacyImport.TargetValue first = values.getFirst();
            for (LegacyImport.TargetValue other : values.subList(1, values.size())) {
                if (!other.value().equals(first.value())) {
                    diagnostics.add(Diagnostic.error(ErrorCode.CONFIG_INCONSISTENT, "'" + first.legacyKey() + "' (" + first.where()
                            + ") and '" + other.legacyKey() + "' both set " + target.getKey() + ", to " + first.value() + " and "
                            + other.value() + "; the legacy processes had separate values, this build has one: keep one of "
                            + "the two or set " + target.getKey() + " explicitly", other.where(), "key", target.getKey()));
                }
            }
            overrides.add(first);
        }

        List<LegacyImport.EnvironmentEntry> reported = new ArrayList<>();
        for (LegacySetting setting : catalog.all()) {
            if (setting.kind() == LegacySetting.Kind.ENVIRONMENT && setting.disposition() != LegacySetting.Disposition.NOT_A_SETTING
                    && environment.containsKey(setting.name())) {
                boolean secret = setting.secret() || SECRET_NAME.matcher(setting.name()).find();
                reported.add(new LegacyImport.EnvironmentEntry(setting.name(),
                        secret ? LegacyImport.REDACTED : environment.get(setting.name()), setting.disposition(),
                        "Reported, never applied: " + setting.reason()));
            }
        }
        return new LegacyImport(catalog.sourceCommit(), files, entries, reported, overrides, pending, diagnostics);
    }

    /** The cf.* value of a mapped key (null when it produces none) and what the import noticed. */
    private record Mapped(String value, String note) {
    }

    private static Mapped map(LegacySetting setting, Occurrence o, List<Diagnostic> diagnostics) {
        String text = o.value().trim();
        String lower = text.toLowerCase(Locale.ROOT);
        return switch (setting.transform()) {
            case LEGACY_BOOLEAN -> {
                if (text.isEmpty()) {
                    yield new Mapped(null, "Blank: the legacy default " + setting.legacyDefault() + " applies, as it does here.");
                }
                boolean value = lower.equals("true");
                if (!value && !lower.equals("false")) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + text
                            + "': the legacy Boolean.parseBoolean read every text but 'true' as false, so the import does",
                            o.where(), "key", o.key()));
                }
                yield new Mapped(Boolean.toString(value), null);
            }
            case LEGACY_STRING -> text.isEmpty()
                    ? new Mapped(null, "Blank: the legacy default " + setting.legacyDefault() + " applies, as it does here.")
                    : new Mapped(text, null);
            case KEYS_DISPATCH -> switch (lower) {
                case "" -> new Mapped(null, "Blank: the legacy default short applies; this build picks the width with "
                        + "cf.keys.encoding and numbers labels identically.");
                case "short" -> new Mapped("PACKED_LEGACY16", null);
                case "auto" -> new Mapped("AUTO_PRESERVE", null);
                default -> invalid(o, "the legacy code refused it as well; allowed: short, auto", diagnostics);
            };
            case KEYS_STRATEGY -> switch (lower) {
                case "", "identity" -> new Mapped(RunConfig.LogicalLabels.LEGACY_WINDOW.token(), text.isEmpty()
                        ? "Blank: the legacy default identity applies: the legacy numbering, moved to the cheapest window only "
                        + "when it does not fit 16 bits (cf.keys.logical.labels=legacy-window)." : null);
                case "anchor", "optimal" -> {
                    diagnostics.add(Diagnostic.error(ErrorCode.CONFIG_LEGACY_UNSUPPORTED, "'" + o.key() + "' = '" + text
                            + "' relabels the keys; relabelling needs its own parity evidence and is not part of this build "
                            + "(decision F2)", o.where(), "key", o.key()));
                    yield new Mapped(null, "Refused: relabelling strategy.");
                }
                default -> invalid(o, "the legacy code refused it as well; allowed: identity, anchor, optimal", diagnostics);
            };
            case LEGACY_WORKER_COUNT -> {
                if (text.isEmpty()) {
                    yield new Mapped(null, "Blank: the legacy pool used every processor; cf.execution.workers keeps its value.");
                }
                if (!DIGITS.matcher(text).matches()) {
                    yield invalid(o, "the legacy validation refused it as well: a positive integer, or empty for every "
                            + "processor", diagnostics);
                }
                if (text.replaceFirst("^0+", "").isEmpty()) {
                    yield new Mapped(null, "0: the legacy pool used every processor; cf.execution.workers keeps its value.");
                }
                yield new Mapped(text.replaceFirst("^0+", ""), null);
            }
            case POSITIVE_INT -> {
                if (!INTEGER.matcher(text).matches() || text.startsWith("-") || text.replaceFirst("^\\+?0+", "").isEmpty()) {
                    yield invalid(o, "the legacy code required a positive integer as well", diagnostics);
                }
                yield new Mapped(text.replaceFirst("^\\+?0*", ""), null);
            }
            case PARSE_BOOLEAN, LEGACY_LIBRARY, LEGACY_VERDICT -> {
                // Boolean.parseBoolean(p.getProperty(key)) on the untrimmed value: "true " was false there, and is here
                String raw = o.value();
                boolean value = raw.equalsIgnoreCase("true");
                String note = null;
                if (raw.isEmpty()) {
                    note = "Blank: the legacy Boolean.parseBoolean read it as false, as the import does.";
                } else if (!value && !raw.equalsIgnoreCase("false")) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + raw
                            + "': the legacy Boolean.parseBoolean read every value but 'true' (any case, nothing around it) "
                            + "as false, so the import does", o.where(), "key", o.key()));
                }
                String mapped = switch (setting.transform()) {
                    case PARSE_BOOLEAN -> Boolean.toString(value);
                    case LEGACY_VERDICT -> value ? RunConfig.VerdictField.FW_CUSTOM_VAR.token() : RunConfig.VerdictField.FW_VAR.token();
                    default -> value ? RunConfig.GenerationLibrary.PARALLEL.token() : RunConfig.GenerationLibrary.COMBINATORICSLIB3.token();
                };
                yield new Mapped(mapped, note);
            }
            case LEGACY_ENUM -> {
                if (text.isEmpty()) {
                    yield new Mapped(null, "Blank: the legacy default " + setting.legacyDefault() + " applies, as it does here.");
                }
                ConfigKey<?> target = ConfigKeys.byName().get(setting.target());
                try {
                    target.parse(lower);
                } catch (ConfigValueException e) {
                    yield invalid(o, "the legacy parseEnum refused it as well (" + e.getMessage() + ")", diagnostics);
                }
                yield new Mapped(lower, null);
            }
            case LEGACY_LONG -> {
                if (!INTEGER.matcher(text).matches()) {
                    yield invalid(o, "the legacy validation required an integer from 1 to " + Long.MAX_VALUE + " as well", diagnostics);
                }
                try {
                    long value = Long.parseLong(text);
                    if (value < 1) {
                        yield invalid(o, "the legacy validation required an integer from 1 to " + Long.MAX_VALUE + " as well", diagnostics);
                    }
                    yield new Mapped(Long.toString(value), null);
                } catch (NumberFormatException e) {
                    yield invalid(o, "the legacy validation required an integer from 1 to " + Long.MAX_VALUE + " as well", diagnostics);
                }
            }
            case LEGACY_INT -> {
                if (!INTEGER.matcher(text).matches()) {
                    yield invalid(o, "the legacy validation required an integer from 0 to " + Integer.MAX_VALUE + " as well", diagnostics);
                }
                try {
                    int value = Integer.parseInt(text);
                    if (value < 0) {
                        yield invalid(o, "the legacy validation required an integer from 0 to " + Integer.MAX_VALUE + " as well", diagnostics);
                    }
                    yield new Mapped(Integer.toString(value), null);
                } catch (NumberFormatException e) {
                    yield invalid(o, "the legacy validation required an integer from 0 to " + Integer.MAX_VALUE + " as well", diagnostics);
                }
            }
            case LEGACY_INT_LIST -> {
                if (text.isEmpty()) {
                    if (ConfigKeys.OPTIONAL_PRODUCED_SIZES.name().equals(setting.target())) {
                        yield new Mapped("none", "Blank: the legacy Core wrote no optional table, so no optional size is produced.");
                    }
                    yield invalid(o, "the legacy Reader failed on a blank list (Integer.parseInt of an empty text)", diagnostics);
                }
                java.util.TreeSet<Integer> sizes = new java.util.TreeSet<>();
                for (String piece : text.split("\\s{0,},\\s{0,}", -1)) {
                    if (!INTEGER.matcher(piece).matches()) {
                        yield invalid(o, "'" + piece + "' is not an integer; the legacy Integer.parseInt refused it as well", diagnostics);
                    }
                    long size;
                    try {
                        size = Long.parseLong(piece);
                    } catch (NumberFormatException e) {
                        yield invalid(o, "'" + piece + "' is not an int; the legacy Integer.parseInt refused it as well", diagnostics);
                    }
                    if (size < 1 || size > 64) {
                        yield invalid(o, "an optional combination size is from 1 to 64; the legacy optional contract "
                                + "(bundle/optional_contract.py) refused sizes below 1 and the absent branch is cf.optional.includeAbsent",
                                diagnostics);
                    }
                    sizes.add((int) size);
                }
                StringBuilder joined = new StringBuilder();
                for (int size : sizes) {
                    joined.append(joined.isEmpty() ? "" : ",").append(size);
                }
                yield new Mapped(joined.toString(), null);
            }
            case LEGACY_TEXT -> new Mapped(o.value(), null);
            case LEGACY_RUN_ID -> text.isEmpty()
                    ? new Mapped(null, "Blank: the legacy minted a new run id for every run (HandoffManifestWriter.mintRunId); "
                    + "this build keeps cf.run.id, 'run' unless set, so sample keys are reproducible.")
                    : new Mapped(text, null);
            case LEGACY_PATH -> text.isEmpty()
                    ? new Mapped(null, "Blank: no file, as the legacy loadOrNull treated it.")
                    : new Mapped(text, null);
            case LEGACY_IS_OPT -> {
                String raw = o.value();
                boolean value = raw.equalsIgnoreCase("true");
                if (!value && !raw.isEmpty() && !raw.equalsIgnoreCase("false")) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + raw
                            + "': the legacy Boolean.parseBoolean read every value but 'true' as false, so the import does",
                            o.where(), "key", o.key()));
                }
                yield new Mapped(value ? RunConfig.OptionalMode.ON.token() : RunConfig.OptionalMode.OFF.token(),
                        raw.isEmpty() ? "Blank: the legacy Boolean.parseBoolean read it as false, so optional tables are off." : null);
            }
            case LEGACY_MARKER_SWITCH -> {
                boolean on = o.value().equalsIgnoreCase("FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE");
                yield new Mapped(Boolean.toString(on), on ? null
                        : "Not the marker FW_REPLACE_ME_WITH_CURRENT_COMBO_SEQUENCE: the legacy Reader replaced nothing, so neither does this build.");
            }
            case LEGACY_IN_FLIGHT -> {
                int value;
                try {
                    value = Integer.parseInt(text);
                } catch (NumberFormatException e) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + text
                            + "' is not an integer; the legacy control plane read it as 64, so the import does", o.where(), "key", o.key()));
                    value = 64;
                }
                if (value < 1 || value > 1024) {
                    int bounded = value < 1 ? 1 : 1024;
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = " + value + ": "
                            + (value < 1 ? "the legacy control plane raised it to 1" : "this build runs at most 1024 samples at once")
                            + ", so the import sets cf.execution.workers to " + bounded, o.where(), "key", o.key()));
                    value = bounded;
                }
                yield new Mapped(Integer.toString(value), null);
            }
            case LEGACY_SERIALIZE -> {
                if (o.value().isBlank()) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' is blank: the legacy "
                            + "control plane then derived it from fw.controlplane.metricSensitivity, which the import reads only "
                            + "when this key is absent; cf.execution.serialize keeps its value", o.where(), "key", o.key()));
                    yield new Mapped(null, "Blank: derived from fw.controlplane.metricSensitivity by the legacy code.");
                }
                yield new Mapped(Boolean.toString(o.value().equalsIgnoreCase("true")), null);
            }
            case LEGACY_METRIC_SENSITIVITY -> switch (lower) {
                case "noisy", "perf", "latency", "throughput", "continuous" -> new Mapped("true", null);
                case "deterministic", "verdict", "fw_var", "" -> new Mapped("false", null);
                default -> invalid(o, "the legacy control plane refused it as well; allowed: noisy, perf, latency, throughput, "
                        + "continuous, deterministic, verdict, fw_var", diagnostics);
            };
            case LEGACY_BUDGET_MILLIS -> {
                long value;
                try {
                    value = Long.parseLong(text);
                } catch (NumberFormatException e) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + text
                            + "' is not an integer; the legacy control plane read it as 0 (no budget), so the import does",
                            o.where(), "key", o.key()));
                    value = 0;
                }
                if (value > (1L << 40)) {
                    yield invalid(o, "this build bounds the run budget to 2^40 milliseconds", diagnostics);
                }
                yield new Mapped(Long.toString(Math.max(0, value)), value < 0 ? "Negative: the legacy control plane read it as 0 (no budget)." : null);
            }
            case LEGACY_EXEC_MODE -> o.value().isEmpty()
                    ? new Mapped(null, "Blank: the legacy fail-only default applies, as it does here.")
                    : new Mapped(o.value().equalsIgnoreCase("passonly") ? RunConfig.Attach.PASSED.token() : RunConfig.Attach.FAILED.token(), null);
            case LEGACY_GOALS -> legacyGoals(o, diagnostics);
            case LEGACY_KEY_LIST -> {
                List<String> keys = new ArrayList<>();
                for (String piece : o.value().split(",")) {
                    String key = piece.trim();
                    if (key.isEmpty() || keys.contains(key)) {
                        continue;
                    }
                    if (!Parsers.isMetricKey(key)) {
                        yield invalid(o, "'" + key + "' is not a metric key this build can name", diagnostics);
                    }
                    keys.add(key);
                }
                yield new Mapped(String.join(",", keys), null);
            }
            case LEGACY_DISCOVER -> new Mapped(Boolean.toString(!(lower.equals("false") || text.equals("0"))), null);
            case LEGACY_DISCOVER_MODE -> switch (lower) {
                case "min", "minimize" -> new Mapped("min", null);
                case "max", "maximize" -> new Mapped("max", null);
                case "auto", "" -> new Mapped("auto", null);
                default -> {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + text + "': the "
                            + "legacy analyzer read every value but min, minimize, max and maximize as auto, so the import does", o.where(),
                            "key", o.key()));
                    yield new Mapped("auto", null);
                }
            };
            case LEGACY_DISCOVER_WEIGHT -> {
                double weight;
                try {
                    weight = text.isEmpty() ? 1.0 : Double.parseDouble(text);
                } catch (NumberFormatException e) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + text + "' is not a "
                            + "number; the legacy analyzer read it as 1.0, so the import does", o.where(), "key", o.key()));
                    weight = 1.0;
                }
                if (!Double.isFinite(weight) || weight < 0) {
                    yield invalid(o, "the legacy analyzer refused a negative or nonfinite weight as well", diagnostics);
                }
                yield new Mapped(Double.toString(weight), null);
            }
            case LEGACY_TOP_K -> {
                int k;
                try {
                    k = Integer.parseInt(o.value());
                } catch (NumberFormatException e) {
                    yield invalid(o, "the legacy Integer.parseInt refused it as well, and the analyzer did not start", diagnostics);
                }
                if (k < 1 || k > 100_000) {
                    int bounded = k < 1 ? 1 : 100_000;
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = " + k + ": "
                            + (k < 1 ? "the legacy analyzer kept at least 1" : "this build lists at most 100000") + ", so the import "
                            + "sets cf.analysis.topK to " + bounded, o.where(), "key", o.key()));
                    k = bounded;
                }
                yield new Mapped(Integer.toString(k), null);
            }
            case LEGACY_INT_DEFAULT_1 -> {
                if (INTEGER.matcher(text).matches()) {
                    yield new Mapped(text.replaceFirst("^\\+", ""), null);
                }
                diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "' = '" + text
                        + "' is not an integer; the legacy parseInt(value, 1) read it as 1, so the import does", o.where(),
                        "key", o.key()));
                yield new Mapped("1", null);
            }
        };
    }

    /** fw.analyzer.goals as Reader AnalyzerWiring parsed it, written as cf.analysis.goals. */
    private static Mapped legacyGoals(Occurrence o, List<Diagnostic> diagnostics) {
        java.util.LinkedHashMap<String, String> goals = new java.util.LinkedHashMap<>();
        for (String token : o.value().split(",")) {
            String[] parts = token.trim().split(":");
            if (parts.length < 2) {
                if (!token.isBlank()) {
                    diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "': goal '" + token.trim()
                            + "' has no mode; the legacy analyzer skipped it, so the import does", o.where(), "key", o.key()));
                }
                continue;
            }
            String key = parts[0].trim();
            if (!Parsers.isMetricKey(key)) {
                return invalid(o, "goal key '" + key + "' is not a metric key this build can name", diagnostics);
            }
            String word = parts[1].trim().toLowerCase(Locale.ROOT);
            String mode = switch (word) {
                case "max", "maximize" -> "max";
                case "target" -> "target";
                default -> "min";
            };
            if (!word.equals("min") && !word.equals("minimize") && mode.equals("min")) {
                diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "': goal '" + token.trim()
                        + "' has mode '" + word + "'; the legacy analyzer minimised every unknown mode, so the import does", o.where(),
                        "key", o.key()));
            }
            double target;
            double weight;
            try {
                target = parts.length >= 3 ? Double.parseDouble(parts[2].trim()) : 0.0;
                weight = parts.length >= 4 ? Double.parseDouble(parts[3].trim()) : 1.0;
            } catch (NumberFormatException e) {
                return invalid(o, "goal '" + token.trim() + "' has a target or weight that is not a number; the legacy analyzer "
                        + "did not start then", diagnostics);
            }
            if (!Double.isFinite(target) || !Double.isFinite(weight) || weight < 0) {
                return invalid(o, "goal '" + token.trim() + "' needs a finite target and a finite weight at least 0", diagnostics);
            }
            if (goals.containsKey(key)) {
                diagnostics.add(Diagnostic.warning(ErrorCode.CONFIG_LEGACY_COERCED, "'" + o.key() + "': goal key '" + key
                        + "' appears twice; the legacy front used the last one, so the import keeps it", o.where(), "key", o.key()));
                goals.remove(key);
            }
            String w = weight == 1.0 ? "" : ":" + weight;
            goals.put(key, mode.equals("target") ? key + ":target:" + target + w : key + ":" + mode + w);
        }
        return new Mapped(String.join(",", goals.values()), null);
    }

    private static Mapped invalid(Occurrence o, String why, List<Diagnostic> diagnostics) {
        diagnostics.add(Diagnostic.error(ErrorCode.CONFIG_INVALID_VALUE, "'" + o.key() + "' = '" + o.value().trim() + "': " + why,
                o.where(), "key", o.key()));
        return new Mapped(null, "Refused: " + why + ".");
    }

    private static String hint(LegacySettings catalog, String key) {
        String closest = Suggestions.closest(key, catalog.names(), 3);
        return closest == null ? "" : "; did you mean '" + closest + "'?";
    }

    private static String article(LegacySetting.Kind kind) {
        return switch (kind) {
            case EXECUTOR_ARGUMENT -> "an executor command-line argument";
            case ENVIRONMENT -> "an environment variable";
            case NOT_A_SETTING -> "a name the legacy code reads that is not a setting";
            case PROPERTY -> "a properties key";
            case SYSTEM_PROPERTY -> "a system property";
        };
    }
}
