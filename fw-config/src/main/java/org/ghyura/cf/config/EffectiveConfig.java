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
import org.ghyura.cf.model.DiagnosticXml;
import org.ghyura.cf.model.Xml;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A resolved {@link RunConfig} together with where every value came from, and any warnings found while
 * resolving. Secret values are redacted in both renderings. Produced only by {@link ConfigResolver}.
 */
public final class EffectiveConfig {

    private final RunConfig config;
    private final List<ConfigEntry> entries;
    private final List<Diagnostic> diagnostics;
    private final LegacyImport legacy;
    private final List<ConfigConflict> conflicts;

    EffectiveConfig(RunConfig config, List<ConfigEntry> entries, List<Diagnostic> diagnostics) {
        this(config, entries, diagnostics, null, List.of());
    }

    EffectiveConfig(RunConfig config, List<ConfigEntry> entries, List<Diagnostic> diagnostics, LegacyImport legacy,
                    List<ConfigConflict> conflicts) {
        this.config = Objects.requireNonNull(config, "config");
        this.entries = List.copyOf(entries);
        this.diagnostics = List.copyOf(diagnostics);
        this.legacy = legacy;
        this.conflicts = List.copyOf(conflicts);
    }

    /** The values a higher layer replaced with different ones, in the order the layers were applied. */
    public List<ConfigConflict> conflicts() {
        return conflicts;
    }

    /** The legacy import this configuration was resolved with, if any. */
    public Optional<LegacyImport> legacyImport() {
        return Optional.ofNullable(legacy);
    }

    public RunConfig config() {
        return config;
    }

    /** One entry per declared key, in declaration order. */
    public List<ConfigEntry> entries() {
        return entries;
    }

    /** Non-error findings: warnings and notes. A resolution with errors throws instead. */
    public List<Diagnostic> diagnostics() {
        return diagnostics;
    }

    public ConfigEntry entry(ConfigKey<?> key) {
        for (ConfigEntry e : entries) {
            if (e.key().equals(key.name())) {
                return e;
            }
        }
        throw new IllegalArgumentException("not a declared key: " + key);
    }

    public String toXml() {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        appendXml(xml, "");
        return xml.toString();
    }

    /** The effective-config element, every line indented by {@code indent}, without an XML declaration: for embedding in a report. */
    public void appendXml(StringBuilder xml, String indent) {
        xml.append(indent).append("<effective-config schema=\"1\">\n");
        for (ConfigEntry e : entries) {
            xml.append(indent).append("  <setting key=\"").append(Xml.escape(e.key())).append("\" value=\"")
                    .append(Xml.escape(e.displayValue())).append("\" origin=\"").append(e.origin().name()).append('"');
            if (e.location().isKnown()) {
                xml.append(" location=\"").append(Xml.escape(e.location().toString())).append('"');
            }
            xml.append("/>\n");
        }
        for (ConfigConflict c : conflicts) {
            xml.append(indent).append("  <overridden key=\"").append(Xml.escape(c.key())).append("\" value=\"")
                    .append(Xml.escape(c.overridden().displayValue())).append("\" origin=\"").append(c.overridden().origin().name())
                    .append('"');
            if (c.overridden().location().isKnown()) {
                xml.append(" location=\"").append(Xml.escape(c.overridden().location().toString())).append('"');
            }
            xml.append(" by=\"").append(c.winner().origin().name()).append("\"/>\n");
        }
        if (legacy != null) {
            legacy.appendXml(xml, indent + "  ");
        }
        if (!diagnostics.isEmpty()) {
            xml.append(indent).append("  <diagnostics>\n");
            DiagnosticXml.appendAll(xml, indent + "    ", diagnostics);
            xml.append(indent).append("  </diagnostics>\n");
        }
        xml.append(indent).append("</effective-config>\n");
    }

    public String toText() {
        StringBuilder text = new StringBuilder(2048);
        int width = 0;
        for (ConfigEntry e : entries) {
            width = Math.max(width, e.key().length());
        }
        for (ConfigEntry e : entries) {
            text.append(String.format("%-" + width + "s = %s  [%s", e.key(), e.displayValue(), e.origin()));
            if (e.origin() != ConfigOrigin.DEFAULT && e.location().isKnown()) {
                text.append(' ').append(e.location());
            }
            text.append("]\n");
        }
        for (ConfigConflict c : conflicts) {
            text.append(c.key()).append(": ").append(c.overridden().displayValue()).append(" [").append(c.overridden().origin());
            if (c.overridden().location().isKnown()) {
                text.append(' ').append(c.overridden().location());
            }
            text.append("] overridden by ").append(c.winner().displayValue()).append(" [").append(c.winner().origin()).append("]\n");
        }
        if (legacy != null) {
            text.append(legacy.toText());
        }
        for (Diagnostic d : diagnostics) {
            text.append(d).append('\n');
        }
        return text.toString();
    }
}
