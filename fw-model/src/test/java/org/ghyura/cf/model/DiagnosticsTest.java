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

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsTest {

    @Test
    void sourceLocationRendersOnlyWhatIsKnown() {
        assertEquals("<unknown>", SourceLocation.NONE.toString());
        assertFalse(SourceLocation.NONE.isKnown());
        assertEquals("a.json", SourceLocation.of("a.json").toString());
        assertEquals("a.json:7", SourceLocation.of("a.json", 7).toString());
        assertEquals("a.json:7:12", SourceLocation.of("a.json", 7, 12).toString());
        assertEquals("book.xlsx (Sheet1!B7)", SourceLocation.ofPath("book.xlsx", "Sheet1!B7").toString());
        assertEquals("(/sheets/2)", SourceLocation.ofPath("", "/sheets/2").toString());
        assertEquals("a.json:7 (cf.keys.encoding)", new SourceLocation("a.json", 7, 0, "cf.keys.encoding").toString());
        assertTrue(SourceLocation.ofPath("", "/x").isKnown());
    }

    @Test
    void sourceLocationRejectsImpossibleCoordinates() {
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation("a", -1, 0, ""));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation("a", 1, -1, ""));
        assertThrows(IllegalArgumentException.class, () -> new SourceLocation("a", 0, 3, ""));
        assertThrows(NullPointerException.class, () -> new SourceLocation(null, 0, 0, ""));
        assertThrows(NullPointerException.class, () -> new SourceLocation("", 0, 0, null));
    }

    @Test
    void diagnosticKeepsAttributeOrderAndIsImmutable() {
        Diagnostic d = Diagnostic.error(ErrorCode.KEY_CENSUS_MISMATCH, "parser needs key 7 but 6 were counted",
                SourceLocation.of("book.xlsx", 3), "expected", "6", "actual", "7");
        assertEquals(List.of("expected", "actual"), List.copyOf(d.attributes().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> d.attributes().put("x", "y"));
        assertEquals("ERROR KEY_CENSUS_MISMATCH [book.xlsx:3]: parser needs key 7 but 6 were counted"
                + " {expected=6, actual=7}", d.toString());
    }

    @Test
    void diagnosticCopiesTheAttributeMapItIsGiven() {
        Map<String, String> source = new LinkedHashMap<>();
        source.put("k", "v");
        Diagnostic d = new Diagnostic(Severity.INFO, ErrorCode.CONFIG_LEGACY_MAPPED, "mapped", SourceLocation.NONE, source);
        source.put("later", "x");
        assertEquals(Map.of("k", "v"), d.attributes());
    }

    @Test
    void diagnosticRejectsMalformedArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> Diagnostic.error(ErrorCode.INTERNAL_ERROR, "odd", SourceLocation.NONE, "name-without-value"));
        assertThrows(IllegalArgumentException.class,
                () -> Diagnostic.error(ErrorCode.INTERNAL_ERROR, "dup", SourceLocation.NONE, "a", "1", "a", "2"));
        assertThrows(IllegalArgumentException.class, () -> Diagnostic.error(ErrorCode.INTERNAL_ERROR, "  ", SourceLocation.NONE));
        assertThrows(NullPointerException.class, () -> Diagnostic.error(ErrorCode.INTERNAL_ERROR, "x", null));
    }

    @Test
    void diagnosticWithoutLocationOrAttributesRendersPlainly() {
        assertEquals("WARNING CONFIG_LEGACY_RETIRED: gone",
                Diagnostic.warning(ErrorCode.CONFIG_LEGACY_RETIRED, "gone", SourceLocation.NONE).toString());
    }

    @Test
    void exceptionReportsTheFirstErrorAsPrimaryAndKeepsEverything() {
        Diagnostic note = Diagnostic.info(ErrorCode.CONFIG_LEGACY_MAPPED, "mapped", SourceLocation.NONE);
        Diagnostic first = Diagnostic.error(ErrorCode.CONFIG_UNKNOWN_KEY, "unknown", SourceLocation.NONE);
        Diagnostic second = Diagnostic.error(ErrorCode.CONFIG_INVALID_VALUE, "invalid", SourceLocation.NONE);
        FrameworkException e = new FrameworkException(List.of(note, first, second));
        assertEquals(first, e.primary());
        assertEquals(ErrorCode.CONFIG_UNKNOWN_KEY, e.code());
        assertEquals(List.of(note, first, second), e.diagnostics());
        assertTrue(e.getMessage().contains("CONFIG_UNKNOWN_KEY"));
        assertThrows(UnsupportedOperationException.class, () -> e.diagnostics().add(second));
    }

    @Test
    void exceptionWithOnlyNonErrorsFallsBackToTheFirst() {
        Diagnostic note = Diagnostic.info(ErrorCode.CONFIG_LEGACY_MAPPED, "mapped", SourceLocation.NONE);
        assertEquals(note, new FrameworkException(List.of(note)).primary());
        assertThrows(IllegalArgumentException.class, () -> new FrameworkException(List.<Diagnostic>of()));
    }

    @Test
    void everyErrorCodeHasADescriptionAndAStableUniqueName() {
        Set<String> names = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(names.add(code.name()));
            assertFalse(code.description().isBlank(), code.name());
        }
        assertEquals(Severity.ERROR, ErrorCode.UNSUPPORTED_CAPABILITY.defaultSeverity());
        assertTrue(Severity.ERROR.isError());
        assertFalse(Severity.WARNING.isError());
    }
}
