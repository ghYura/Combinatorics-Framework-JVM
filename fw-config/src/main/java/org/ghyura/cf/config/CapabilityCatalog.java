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
import org.ghyura.cf.model.Xml;
import org.ghyura.cf.spi.AddonCapabilities;
import org.ghyura.cf.spi.SutPlugin;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * The honest answer to "what can this build do": the architecture's compatibility mapping (C01-C18) joined
 * with the claims modules publish. A capability nobody claims is NOT_IMPLEMENTED; one the architecture rules
 * out is INCOMPATIBLE and stays so. A scaffold is never advertised as a completed analog (R09).
 */
public final class CapabilityCatalog {

    private static final String MAPPING_RESOURCE = "compatibility.xml";

    /** One capability with its resolved status and the evidence (or, for INCOMPATIBLE, the reason). */
    public record Entry(Capability capability, CapabilityStatus status, String evidence) {
    }

    /** A SUT plugin that {@code cf.execution.sut} can name: from the application class path or from an installed addon. */
    public record Plugin(String id, String version, String origin) {
    }

    private final List<Entry> entries;
    private final List<Plugin> plugins;

    private CapabilityCatalog(List<Entry> entries, List<Plugin> plugins) {
        this.entries = List.copyOf(entries);
        this.plugins = List.copyOf(plugins);
    }

    /** The mapping shipped with this build plus every claim found through {@link ServiceLoader}. */
    public static CapabilityCatalog load() {
        return load(List.of());
    }

    /**
     * The mapping shipped with this build, every claim found through {@link ServiceLoader}, and the statements of the addons
     * installed on {@code addonClasspath} ({@code cf.execution.classpath}): only {@link AddonCapabilities} that the addon
     * class path itself defines count, reported as the addon's. The SUT plugins of both class paths are listed. The loader
     * over the addon class path belongs to this call and is closed before it returns.
     *
     * @throws FrameworkException CONFIG_INVALID_VALUE for a class path entry that does not exist; INTERNAL_ERROR for an
     *                            addon statement with another status than implemented or partial, or a conflicting claim
     */
    public static CapabilityCatalog load(List<String> addonClasspath) {
        List<CapabilityClaim> claims = new ArrayList<>();
        for (CapabilityProvider provider : ServiceLoader.load(CapabilityProvider.class)) {
            claims.addAll(provider.claims());
        }
        List<Plugin> plugins = new ArrayList<>();
        URLClassLoader addons = addonClasspath.isEmpty() ? null : addonLoader(addonClasspath);
        try {
            ClassLoader all = addons != null ? addons : CapabilityCatalog.class.getClassLoader();
            for (ServiceLoader.Provider<SutPlugin> p : ServiceLoader.load(SutPlugin.class, all).stream().toList()) {
                SutPlugin plugin = p.get();
                plugins.add(new Plugin(plugin.id(), plugin.version(), addons != null && p.type().getClassLoader() == addons ? "addon" : "application"));
            }
            if (addons != null) {
                for (ServiceLoader.Provider<AddonCapabilities> p : ServiceLoader.load(AddonCapabilities.class, addons).stream().toList()) {
                    if (p.type().getClassLoader() != addons) {
                        continue;
                    }
                    AddonCapabilities addon = p.get();
                    for (AddonCapabilities.Statement statement : addon.statements()) {
                        CapabilityStatus status = switch (statement.status()) {
                            case "implemented" -> CapabilityStatus.IMPLEMENTED;
                            case "partial" -> CapabilityStatus.PARTIAL;
                            default -> throw internal("addon " + addon.addon() + " states '" + statement.status() + "' for "
                                    + statement.capabilityId() + "; an addon states implemented or partial");
                        };
                        claims.add(new CapabilityClaim(statement.capabilityId(), status, "addon " + addon.addon() + ": " + statement.evidence()));
                    }
                }
            }
        } finally {
            if (addons != null) {
                try {
                    addons.close();
                } catch (IOException ignored) {
                    // nothing of the loader is used after this call
                }
            }
        }
        plugins.sort(Comparator.comparing(Plugin::id).thenComparing(Plugin::origin));
        return new CapabilityCatalog(of(readMapping(), claims).entries, plugins);
    }

    private static URLClassLoader addonLoader(List<String> classpath) {
        List<URL> urls = new ArrayList<>();
        for (String entry : classpath) {
            Path p = Path.of(entry).toAbsolutePath().normalize();
            if (!Files.exists(p)) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INVALID_VALUE, "cf.execution.classpath names '" + entry
                        + "', which does not exist", SourceLocation.NONE, "key", "cf.execution.classpath", "entry", entry));
            }
            try {
                urls.add(p.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new FrameworkException(Diagnostic.error(ErrorCode.CONFIG_INVALID_VALUE, "cf.execution.classpath entry '" + entry
                        + "' is not a usable path", SourceLocation.NONE, "key", "cf.execution.classpath"));
            }
        }
        return new URLClassLoader("cf-addons", urls.toArray(URL[]::new), CapabilityCatalog.class.getClassLoader());
    }

    /** The SUT plugins found, by id. */
    public List<Plugin> plugins() {
        return plugins;
    }

    /**
     * @throws FrameworkException INTERNAL_ERROR when a claim names an unknown capability, a capability is
     *                            claimed twice, or a claim contradicts an INCOMPATIBLE decision
     */
    public static CapabilityCatalog of(List<Capability> mapping, List<CapabilityClaim> claims) {
        Map<String, Capability> byId = new LinkedHashMap<>();
        for (Capability capability : mapping) {
            if (byId.put(capability.id(), capability) != null) {
                throw internal("capability " + capability.id() + " is declared twice");
            }
        }
        Map<String, CapabilityClaim> claimed = new LinkedHashMap<>();
        for (CapabilityClaim claim : claims) {
            Capability capability = byId.get(claim.capabilityId());
            if (capability == null) {
                throw internal("a module claims unknown capability " + claim.capabilityId());
            }
            if (claimed.put(claim.capabilityId(), claim) != null) {
                throw internal("capability " + claim.capabilityId() + " is claimed more than once");
            }
            if (capability.action() == CapabilityAction.INCOMPATIBLE && claim.status() != CapabilityStatus.INCOMPATIBLE) {
                throw internal("a module claims " + claim.status() + " for " + claim.capabilityId()
                        + ", which the architecture rules out");
            }
        }
        List<Entry> entries = new ArrayList<>();
        for (Capability capability : mapping) {
            CapabilityClaim claim = claimed.get(capability.id());
            if (capability.action() == CapabilityAction.INCOMPATIBLE) {
                entries.add(new Entry(capability, CapabilityStatus.INCOMPATIBLE, capability.note()));
            } else if (claim == null) {
                entries.add(new Entry(capability, CapabilityStatus.NOT_IMPLEMENTED, ""));
            } else {
                entries.add(new Entry(capability, claim.status(), claim.evidence()));
            }
        }
        return new CapabilityCatalog(entries, List.of());
    }

    public List<Entry> entries() {
        return entries;
    }

    public Entry entry(String id) {
        for (Entry e : entries) {
            if (e.capability().id().equals(id)) {
                return e;
            }
        }
        throw new IllegalArgumentException("unknown capability " + id);
    }

    public Map<CapabilityStatus, Integer> counts() {
        Map<CapabilityStatus, Integer> counts = new EnumMap<>(CapabilityStatus.class);
        for (CapabilityStatus s : CapabilityStatus.values()) {
            counts.put(s, 0);
        }
        for (Entry e : entries) {
            counts.merge(e.status(), 1, Integer::sum);
        }
        return counts;
    }

    public String toXml() {
        Map<CapabilityStatus, Integer> counts = counts();
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<capabilities schema=\"1\" implemented=\"").append(counts.get(CapabilityStatus.IMPLEMENTED))
                .append("\" partial=\"").append(counts.get(CapabilityStatus.PARTIAL))
                .append("\" notImplemented=\"").append(counts.get(CapabilityStatus.NOT_IMPLEMENTED))
                .append("\" incompatible=\"").append(counts.get(CapabilityStatus.INCOMPATIBLE)).append("\">\n");
        for (Entry e : entries) {
            Capability c = e.capability();
            xml.append("  <capability id=\"").append(Xml.escape(c.id())).append("\" status=\"").append(e.status())
                    .append("\" action=\"").append(c.action()).append("\" targets=\"")
                    .append(Xml.escape(String.join(" ", c.targetModules()))).append("\">\n");
            xml.append("    <legacy>").append(Xml.escape(c.legacy())).append("</legacy>\n");
            xml.append("    <note>").append(Xml.escape(c.note())).append("</note>\n");
            if (!e.evidence().isEmpty() && e.status() != CapabilityStatus.INCOMPATIBLE) {
                xml.append("    <evidence>").append(Xml.escape(e.evidence())).append("</evidence>\n");
            }
            xml.append("  </capability>\n");
        }
        if (!plugins.isEmpty()) {
            xml.append("  <sut-plugins>\n");
            for (Plugin p : plugins) {
                xml.append("    <plugin id=\"").append(Xml.escape(p.id())).append("\" version=\"").append(Xml.escape(p.version()))
                        .append("\" origin=\"").append(p.origin()).append("\"/>\n");
            }
            xml.append("  </sut-plugins>\n");
        }
        xml.append("</capabilities>\n");
        return xml.toString();
    }

    public String toText() {
        StringBuilder text = new StringBuilder(2048);
        for (Entry e : entries) {
            Capability c = e.capability();
            text.append(String.format("%-4s %-16s %-14s %s  [%s]%n", c.id(), e.status(), c.action(), c.legacy(),
                    String.join(" ", c.targetModules())));
            if (e.status() == CapabilityStatus.INCOMPATIBLE) {
                text.append("       reason: ").append(c.note()).append('\n');
            } else if (!e.evidence().isEmpty()) {
                text.append("       evidence: ").append(e.evidence()).append('\n');
            }
        }
        if (!plugins.isEmpty()) {
            text.append("SUT plugins: ").append(String.join(", ", plugins.stream().map(p -> p.id() + " " + p.version() + " (" + p.origin() + ")")
                    .toList())).append('\n');
        }
        Map<CapabilityStatus, Integer> counts = counts();
        text.append(String.format("%d implemented, %d partial, %d not implemented, %d incompatible by design%n",
                counts.get(CapabilityStatus.IMPLEMENTED), counts.get(CapabilityStatus.PARTIAL),
                counts.get(CapabilityStatus.NOT_IMPLEMENTED), counts.get(CapabilityStatus.INCOMPATIBLE)));
        return text.toString();
    }

    private static List<Capability> readMapping() {
        try (InputStream in = CapabilityCatalog.class.getResourceAsStream(MAPPING_RESOURCE)) {
            if (in == null) {
                throw internal("the compatibility mapping resource " + MAPPING_RESOURCE + " is missing");
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(in);
            NodeList nodes = document.getDocumentElement().getChildNodes();
            List<Capability> mapping = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node instanceof Element element && element.getTagName().equals("mapping")) {
                    mapping.add(new Capability(element.getAttribute("id"), element.getAttribute("legacy"),
                            targets(element.getAttribute("target")), action(element),
                            element.getTextContent().strip()));
                }
            }
            if (mapping.isEmpty()) {
                throw internal("the compatibility mapping contains no <mapping> entries");
            }
            return mapping;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INTERNAL_ERROR,
                    "cannot read the compatibility mapping: " + e.getMessage(), SourceLocation.of(MAPPING_RESOURCE)), e);
        }
    }

    private static List<String> targets(String text) {
        return text.isBlank() ? List.of() : Arrays.asList(text.trim().split("\\s+"));
    }

    private static CapabilityAction action(Element element) {
        try {
            return CapabilityAction.valueOf(element.getAttribute("action"));
        } catch (IllegalArgumentException e) {
            throw internal("mapping " + element.getAttribute("id") + " has unknown action '"
                    + element.getAttribute("action") + "'");
        }
    }

    private static FrameworkException internal(String message) {
        return new FrameworkException(Diagnostic.error(ErrorCode.INTERNAL_ERROR, message,
                SourceLocation.of(MAPPING_RESOURCE)));
    }
}
