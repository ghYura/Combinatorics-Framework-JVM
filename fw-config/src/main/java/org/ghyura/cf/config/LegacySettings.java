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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * The catalog of every setting name the legacy framework read ({@code legacy-settings.xml}): a closed list, one
 * disposition per name, generated once from a read-only scan of the pinned legacy checkout and reviewed name by
 * name. A name that is not in it is refused by the importer, never passed through or dropped.
 */
public final class LegacySettings {

    private static final String RESOURCE = "legacy-settings.xml";
    private static volatile LegacySettings loaded;

    private final String sourceCommit;
    private final List<LegacySetting> settings;

    private LegacySettings(String sourceCommit, List<LegacySetting> settings) {
        this.sourceCommit = sourceCommit;
        this.settings = Collections.unmodifiableList(settings);
    }

    /** The embedded catalog. A defect in it is a build defect, reported as INTERNAL_ERROR. */
    public static LegacySettings catalog() {
        LegacySettings current = loaded;
        if (current == null) {
            current = load();
            loaded = current;
        }
        return current;
    }

    /** A catalog of these entries (tests: a deferred setting, which the embedded catalog no longer has). */
    static LegacySettings of(String sourceCommit, List<LegacySetting> settings) {
        return new LegacySettings(sourceCommit, new ArrayList<>(settings));
    }

    /** The legacy commit the catalog was generated from. */
    public String sourceCommit() {
        return sourceCommit;
    }

    public List<LegacySetting> all() {
        return settings;
    }

    /** The entry for a name: an exact entry first, otherwise the longest prefix entry that covers it. */
    public Optional<LegacySetting> find(String name) {
        LegacySetting best = null;
        for (LegacySetting s : settings) {
            if (!s.prefix() && s.name().equals(name)) {
                return Optional.of(s);
            }
            if (s.prefix() && name.startsWith(s.name()) && (best == null || s.name().length() > best.name().length())) {
                best = s;
            }
        }
        return Optional.ofNullable(best);
    }

    /** The names of every entry, for spelling suggestions. */
    public List<String> names() {
        List<String> names = new ArrayList<>(settings.size());
        for (LegacySetting s : settings) {
            names.add(s.name());
        }
        return names;
    }

    private static LegacySettings load() {
        try (InputStream in = LegacySettings.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw internal("the embedded catalog " + RESOURCE + " is missing");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(in);
            Element root = document.getDocumentElement();
            List<LegacySetting> settings = new ArrayList<>();
            NodeList nodes = root.getElementsByTagName("setting");
            for (int i = 0; i < nodes.getLength(); i++) {
                settings.add(setting((Element) nodes.item(i)));
            }
            int declared = Integer.parseInt(root.getAttribute("entries"));
            if (declared != settings.size()) {
                throw internal("the catalog declares " + declared + " entries but holds " + settings.size());
            }
            return new LegacySettings(root.getAttribute("source-commit"), settings);
        } catch (FrameworkException e) {
            throw e;
        } catch (Exception e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INTERNAL_ERROR, "cannot read the embedded legacy settings "
                    + "catalog: " + e.getMessage(), SourceLocation.of(RESOURCE)), e);
        }
    }

    private static LegacySetting setting(Element e) {
        List<LegacySetting.ValueRule> values = new ArrayList<>();
        List<LegacySetting.Location> foundIn = new ArrayList<>();
        List<LegacySetting.Location> readAt = new ArrayList<>();
        String reason = "";
        for (Node child = e.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (!(child instanceof Element c)) {
                continue;
            }
            switch (c.getTagName()) {
                case "reason" -> reason = c.getTextContent();
                case "value" -> values.add(new LegacySetting.ValueRule(c.getAttribute("text"),
                        LegacySetting.Disposition.ofToken(c.getAttribute("disposition")), c.getTextContent()));
                case "found-in" -> foundIn.add(location(c));
                case "read-at" -> readAt.add(location(c));
                default -> throw internal("unexpected element <" + c.getTagName() + "> in the entry of " + e.getAttribute("name"));
            }
        }
        String transform = optional(e, "transform");
        return new LegacySetting(e.getAttribute("name"), "prefix".equals(e.getAttribute("match")),
                LegacySetting.Kind.ofToken(e.getAttribute("kind")), LegacySetting.Disposition.ofToken(e.getAttribute("disposition")),
                optional(e, "target"), transform == null ? null : LegacySetting.Transform.ofToken(transform),
                optional(e, "default"), "default".equals(e.getAttribute("when-absent")), optional(e, "alias"),
                optional(e, "owner"), optional(e, "category"),
                "true".equals(e.getAttribute("secret")), reason, values, foundIn, readAt);
    }

    private static String optional(Element e, String attribute) {
        return e.hasAttribute(attribute) ? e.getAttribute(attribute) : null;
    }

    private static LegacySetting.Location location(Element e) {
        return new LegacySetting.Location(e.getAttribute("path"), Integer.parseInt(e.getAttribute("line")));
    }

    private static FrameworkException internal(String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INTERNAL_ERROR, message, SourceLocation.of(RESOURCE)));
    }
}
