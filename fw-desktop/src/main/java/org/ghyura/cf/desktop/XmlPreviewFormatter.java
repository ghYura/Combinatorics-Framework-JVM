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

package org.ghyura.cf.desktop;

import org.w3c.dom.Comment;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** XML layout without serializing a DOM: retain attribute order, entities, quotes, comments and text verbatim. */
final class XmlPreviewFormatter {
    static String format(String source) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler() {
                @Override public void error(SAXParseException e) throws SAXException { throw e; }
                @Override public void fatalError(SAXParseException e) throws SAXException { throw e; }
            });
            var document = builder.parse(new InputSource(new StringReader(source)));
            checkMixedContent(document.getDocumentElement());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("XML could not be formatted (DTDs and external entities are disabled): " + e.getMessage(), e);
        }
        List<String> parts = parts(source);
        StringBuilder out = new StringBuilder();
        int depth = 0;
        String previous = "";
        for (int index = 0; index < parts.size(); index++) {
            String part = parts.get(index);
            if (part.isBlank()) {
                // Whitespace-only leaf content is data; only spacing between sibling/parent elements is layout.
                if (index > 0 && index + 1 < parts.size() && parts.get(index - 1).startsWith("<")
                        && !parts.get(index - 1).startsWith("</") && !parts.get(index - 1).endsWith("/>")
                        && parts.get(index + 1).startsWith("</")) out.append(part);
                continue;
            }
            boolean tag = part.startsWith("<") && !part.startsWith("<![CDATA[");
            boolean close = part.startsWith("</");
            boolean open = tag && !close && !part.startsWith("<?") && !part.startsWith("<!") && !part.endsWith("/>");
            if (close) depth--;
            boolean previousOpen = previous.startsWith("<") && !previous.startsWith("</") && !previous.startsWith("<?")
                    && !previous.startsWith("<!") && !previous.endsWith("/>");
            if (tag && !previous.isEmpty() && previous.endsWith(">") && !previous.startsWith("<![CDATA[")
                    && !(close && previousOpen)) {
                if (depth > 128) throw new IllegalArgumentException("XML nesting is too deep to format in the preview.");
                out.append('\n').append("  ".repeat(Math.max(0, depth)));
            }
            out.append(part);
            if (open) depth++;
            previous = part;
            if (out.length() > CodeFormatter.MAX_FORMATTED_CHARS) throw new IllegalArgumentException("Formatted XML is too large.");
        }
        return out.toString();
    }

    private static void checkMixedContent(Element element) {
        if (element.hasAttribute("xml:space")) {
            throw new IllegalArgumentException("XML with xml:space retains its original spacing.");
        }
        boolean text = false, child = false;
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element e) { child = true; checkMixedContent(e); }
            // node kinds by interface: getNodeType() returns a 16-bit short, which owned bytecode never uses
            else if (node instanceof Comment || node instanceof ProcessingInstruction) child = true;
            else if (node instanceof Text t && !t.getData().isBlank()) text = true; // CDATA sections are Text too
        }
        if (text && child) throw new IllegalArgumentException("XML with mixed text and elements retains its original spacing.");
    }

    private static List<String> parts(String source) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < source.length();) {
            int end;
            if (source.startsWith("<!--", i)) end = source.indexOf("-->", i) + 3;
            else if (source.startsWith("<![CDATA[", i)) end = source.indexOf("]]>", i) + 3;
            else if (source.startsWith("<?", i)) end = source.indexOf("?>", i) + 2;
            else if (source.charAt(i) == '<') {
                char quote = 0;
                end = i + 1;
                while (end < source.length()) {
                    char c = source.charAt(end++);
                    if (quote != 0) { if (c == quote) quote = 0; }
                    else if (c == '\'' || c == '"') quote = c;
                    else if (c == '>') break;
                }
            } else {
                end = source.indexOf('<', i);
                if (end < 0) end = source.length();
            }
            if (end <= i) throw new IllegalArgumentException("Incomplete XML.");
            result.add(source.substring(i, end));
            i = end;
        }
        return result;
    }
}
