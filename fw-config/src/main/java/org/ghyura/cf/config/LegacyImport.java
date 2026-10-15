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
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.Xml;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What {@link LegacyImporter} made of legacy configuration: one entry per key found, with its disposition and
 * source location; the cf.* values it produced (configuration layer LEGACY); the retained behaviours that a later
 * work package implements (a run must refuse while any is pending); the legacy environment variables present;
 * and every finding. Secret values never appear in it: they are replaced by {@link #REDACTED} on the way in.
 *
 * @param catalogCommit the legacy commit the settings catalog was generated from
 * @param files         the imported files, in import order
 * @param entries       one per key, in order of first appearance; the value is the one that won
 * @param environment   the legacy environment variables present, in catalog order
 * @param overrides     the cf.* values the import produced
 * @param pending       the deferred settings: retained behaviour owned by a later work package
 * @param diagnostics   errors, warnings and notes, in order
 */
public record LegacyImport(String catalogCommit, List<String> files, List<Entry> entries, List<EnvironmentEntry> environment,
                           List<TargetValue> overrides, List<Pending> pending, List<Diagnostic> diagnostics) {

    /** What a secret value is shown as. */
    public static final String REDACTED = "<redacted>";

    public LegacyImport {
        Objects.requireNonNull(catalogCommit, "catalogCommit");
        files = List.copyOf(files);
        entries = List.copyOf(entries);
        environment = List.copyOf(environment);
        overrides = List.copyOf(overrides);
        pending = List.copyOf(pending);
        diagnostics = List.copyOf(diagnostics);
    }

    /**
     * One legacy key as imported.
     *
     * @param key         the key as written
     * @param where       where its winning value was written
     * @param shownValue  the value, or {@link #REDACTED} for a secret
     * @param kind        the catalog kind, or null when the key is not in the catalog
     * @param disposition what this build does with it, or null when it is refused as unclassified
     * @param target      the cf.* key or surface it went to, or null
     * @param targetValue the cf.* value it produced, or null when it produced none
     * @param owner       the work package that owns its semantics, or null
     * @param note        why: the catalog reason, a value rule, a transform note or the refusal
     */
    public record Entry(String key, SourceLocation where, String shownValue, LegacySetting.Kind kind,
                        LegacySetting.Disposition disposition, String target, String targetValue, String owner, String note) {
        public Entry {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(shownValue, "shownValue");
            Objects.requireNonNull(note, "note");
        }
    }

    /** A legacy environment variable that is set: reported, never applied. */
    public record EnvironmentEntry(String name, String shownValue, LegacySetting.Disposition disposition, String note) {
        public EnvironmentEntry {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(shownValue, "shownValue");
            Objects.requireNonNull(disposition, "disposition");
            Objects.requireNonNull(note, "note");
        }
    }

    /** A cf.* value the import produced, with the legacy key and place it came from. */
    public record TargetValue(String key, String value, SourceLocation where, String legacyKey) {
        public TargetValue {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(legacyKey, "legacyKey");
        }
    }

    /** Retained legacy behaviour a later work package implements; a run must refuse while it is pending. */
    public record Pending(String legacyKey, SourceLocation where, String owner, String reason) {
        public Pending {
            Objects.requireNonNull(legacyKey, "legacyKey");
            Objects.requireNonNull(where, "where");
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(reason, "reason");
        }
    }

    public boolean hasErrors() {
        return diagnostics.stream().anyMatch(d -> d.severity().isError());
    }

    /** How many entries have each disposition; refused keys are not counted. */
    public Map<LegacySetting.Disposition, Integer> counts() {
        Map<LegacySetting.Disposition, Integer> counts = new EnumMap<>(LegacySetting.Disposition.class);
        for (Entry e : entries) {
            if (e.disposition() != null) {
                counts.merge(e.disposition(), 1, Integer::sum);
            }
        }
        return counts;
    }

    /** The legacy-import element, every line indented by {@code indent}. */
    public void appendXml(StringBuilder xml, String indent) {
        xml.append(indent).append("<legacy-import catalogCommit=\"").append(Xml.escape(catalogCommit)).append("\" keys=\"")
                .append(entries.size() - reproducedDefaults()).append("\" reproducedDefaults=\"").append(reproducedDefaults()).append('"');
        for (Map.Entry<LegacySetting.Disposition, Integer> c : counts().entrySet()) {
            xml.append(' ').append(camel(c.getKey().token())).append("=\"").append(c.getValue()).append('"');
        }
        long refused = entries.stream().filter(e -> e.disposition() == null).count();
        xml.append(" unclassified=\"").append(refused).append("\" pending=\"").append(pending.size()).append("\">\n");
        for (String file : files) {
            xml.append(indent).append("  <file path=\"").append(Xml.escape(file)).append("\"/>\n");
        }
        for (Entry e : entries) {
            xml.append(indent).append("  <setting key=\"").append(Xml.escape(e.key())).append("\" value=\"")
                    .append(Xml.escape(e.shownValue())).append("\" disposition=\"")
                    .append(e.disposition() == null ? "unclassified" : e.disposition().token()).append('"');
            if (e.kind() != null) {
                xml.append(" kind=\"").append(e.kind().token()).append('"');
            }
            if (e.target() != null) {
                xml.append(" target=\"").append(Xml.escape(e.target())).append('"');
            }
            if (e.targetValue() != null) {
                xml.append(" targetValue=\"").append(Xml.escape(e.targetValue())).append('"');
            }
            if (e.owner() != null) {
                xml.append(" owner=\"").append(Xml.escape(e.owner())).append('"');
            }
            xml.append(" location=\"").append(Xml.escape(e.where().toString())).append("\">")
                    .append(Xml.escape(e.note())).append("</setting>\n");
        }
        for (EnvironmentEntry e : environment) {
            xml.append(indent).append("  <environment name=\"").append(Xml.escape(e.name())).append("\" value=\"")
                    .append(Xml.escape(e.shownValue())).append("\" disposition=\"").append(e.disposition().token()).append("\">")
                    .append(Xml.escape(e.note())).append("</environment>\n");
        }
        for (Pending p : pending) {
            xml.append(indent).append("  <pending key=\"").append(Xml.escape(p.legacyKey())).append("\" owner=\"")
                    .append(Xml.escape(p.owner())).append("\" location=\"").append(Xml.escape(p.where().toString())).append("\"/>\n");
        }
        xml.append(indent).append("</legacy-import>\n");
    }

    /** How many entries reproduce a legacy default for a key the files leave out (decision G6); they have no location. */
    public long reproducedDefaults() {
        return entries.stream().filter(e -> !e.where().isKnown()).count();
    }

    /** A compact human-readable report: one line per key, then the environment and what is pending. */
    public String toText() {
        StringBuilder text = new StringBuilder(2048);
        long reproduced = reproducedDefaults();
        text.append("legacy import (settings catalog of ").append(catalogCommit, 0, Math.min(12, catalogCommit.length()))
                .append("): ").append(entries.size() - reproduced).append(" keys from ").append(String.join(", ", files));
        if (reproduced > 0) {
            text.append("; ").append(reproduced).append(reproduced == 1 ? " legacy default" : " legacy defaults")
                    .append(" reproduced for keys they leave out");
        }
        text.append('\n');
        int width = 0;
        for (Entry e : entries) {
            width = Math.max(width, e.key().length());
        }
        for (Entry e : entries) {
            text.append("  ").append(e.key()).append(" ".repeat(width - e.key().length())).append("  ")
                    .append(e.disposition() == null ? "unclassified" : e.disposition().token());
            if (e.target() != null) {
                text.append(" -> ").append(e.target());
                if (e.targetValue() != null) {
                    text.append('=').append(e.targetValue());
                }
            }
            if (e.owner() != null && (e.disposition() == LegacySetting.Disposition.DEFERRED
                    || e.disposition() == LegacySetting.Disposition.UNSUPPORTED)) {
                text.append(" (").append(e.owner()).append(')');
            }
            text.append("  [").append(e.where().isKnown() ? e.where().toString() : "absent: legacy default").append("]\n");
        }
        for (EnvironmentEntry e : environment) {
            text.append("  environment ").append(e.name()).append("  ").append(e.disposition().token()).append('\n');
        }
        if (!pending.isEmpty()) {
            text.append("  pending until their work package lands (a run refuses them): ").append(pending.size()).append('\n');
        }
        return text.toString();
    }

    private static String camel(String token) {
        StringBuilder out = new StringBuilder();
        boolean upper = false;
        for (char c : token.toCharArray()) {
            if (c == '-') {
                upper = true;
            } else {
                out.append(upper ? Character.toUpperCase(c) : c);
                upper = false;
            }
        }
        return out.toString();
    }
}
