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

/**
 * Renders the {@link ErrorCode} taxonomy as XML. The checked-in {@code error-taxonomy.xml} is the reviewed,
 * frozen contract; a test compares this rendering with it, so changing a code, its category, default
 * severity or description fails the build until the contract file is regenerated on purpose.
 */
public final class ErrorTaxonomy {

    /** Classpath location of the frozen contract, next to this class. */
    public static final String RESOURCE = "error-taxonomy.xml";

    private ErrorTaxonomy() {
    }

    public static String toXml() {
        StringBuilder xml = new StringBuilder(4096);
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<error-taxonomy schema=\"1\">\n");
        for (ErrorCode.Category category : ErrorCode.Category.values()) {
            xml.append("  <category name=\"").append(category.name()).append("\">\n");
            for (ErrorCode code : ErrorCode.values()) {
                if (code.category() == category) {
                    xml.append("    <code name=\"").append(code.name())
                            .append("\" severity=\"").append(code.defaultSeverity().name()).append("\">")
                            .append(escape(code.description())).append("</code>\n");
                }
            }
            xml.append("  </category>\n");
        }
        xml.append("</error-taxonomy>\n");
        return xml.toString();
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
