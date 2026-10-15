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

package org.ghyura.cf.model;

import java.util.List;
import java.util.Map;

/**
 * The machine-readable form of diagnostics, shared by every report:
 *
 * <pre>
 * &lt;diagnostic severity="ERROR" code="KEY_LABEL_RESERVED"&gt;
 *   &lt;message&gt;...&lt;/message&gt;
 *   &lt;location origin="book.xlsx" line="3" column="0" path="Sheet1!B7"/&gt;   (only when known)
 *   &lt;attribute name="label" value="-128"/&gt;
 * &lt;/diagnostic&gt;
 * </pre>
 */
public final class DiagnosticXml {

    private DiagnosticXml() {
    }

    public static void append(StringBuilder out, String indent, Diagnostic d) {
        out.append(indent).append("<diagnostic severity=\"").append(d.severity().name())
                .append("\" code=\"").append(d.code().name()).append("\">\n");
        out.append(indent).append("  <message>").append(Xml.escape(d.message())).append("</message>\n");
        SourceLocation where = d.location();
        if (where.isKnown()) {
            out.append(indent).append("  <location origin=\"").append(Xml.escape(where.origin()))
                    .append("\" line=\"").append(where.line())
                    .append("\" column=\"").append(where.column())
                    .append("\" path=\"").append(Xml.escape(where.path())).append("\"/>\n");
        }
        for (Map.Entry<String, String> attribute : d.attributes().entrySet()) {
            out.append(indent).append("  <attribute name=\"").append(Xml.escape(attribute.getKey()))
                    .append("\" value=\"").append(Xml.escape(attribute.getValue())).append("\"/>\n");
        }
        out.append(indent).append("</diagnostic>\n");
    }

    public static void appendAll(StringBuilder out, String indent, List<Diagnostic> diagnostics) {
        for (Diagnostic d : diagnostics) {
            append(out, indent, d);
        }
    }
}
