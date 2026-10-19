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

import org.ghyura.cf.generation.RenderedSource;
import org.ghyura.cf.model.SourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CodeFormatterTest {
    static RenderedSource source(String... pieces) {
        StringBuilder text = new StringBuilder();
        var chunks = new ArrayList<RenderedSource.Chunk>();
        for (int i = 0; i < pieces.length; i++) {
            var origin = new RenderedSource.Origin("Step" + i, "Step" + i, 10 + i, i + 1, i == 2,
                    RenderedSource.Kind.VALUE, SourceLocation.ofPath("scenario.xlsx", "Step" + i + "!A1"), "");
            chunks.add(new RenderedSource.Chunk(text.length(), text.length() + pieces[i].length(), List.of(origin)));
            text.append(pieces[i]);
        }
        return new RenderedSource(text.toString(), chunks, false);
    }

    @Test
    void javaFormattingPreservesIdenticalActionsByPositionCommentsAndStringContents() {
        var source = source("class Demo{void run(){", "ping();", "ping();", "String x=\"a  b;🙂\";/* comment  stays */}}\n");
        var result = CodeFormatter.format(source, CodeFormatter.Language.AUTO);
        assertEquals(CodeFormatter.Language.JAVA, result.language());
        assertNotEquals(source.text(), result.source().text());
        assertTrue(result.source().text().contains("\"a  b;🙂\""));
        assertTrue(result.source().text().contains("/* comment  stays */"));
        int first = result.source().text().indexOf("ping()"), second = result.source().text().indexOf("ping()", first + 1);
        assertEquals("Step1", originAt(result.source(), first).sheet());
        assertEquals("Step2", originAt(result.source(), second).sheet());
        assertTrue(originAt(result.source(), second).optional());
        assertEquals("class Demo{void run(){ping();ping();String x=\"a  b;🙂\";/* comment  stays */}}\n", source.text());
    }

    @Test
    void jsonPreservesDuplicateKeysEscapesNumbersAndEveryNonWhitespaceOrigin() {
        var source = source("{\"x\":1e+02,", "\"x\":-0.0,", "\"s\":\"a  b\\t\\u0041\",\"empty\":[]}");
        var result = CodeFormatter.format(source, CodeFormatter.Language.AUTO);
        assertEquals(CodeFormatter.Language.JSON, result.language());
        assertTrue(result.source().text().contains("1e+02"));
        assertTrue(result.source().text().contains("-0.0"));
        assertTrue(result.source().text().contains("\"a  b\\t\\u0041\""));
        assertEquals(2, result.source().text().split("\"x\"", -1).length - 1);
        assertEquals("Step1", originAt(result.source(), result.source().text().indexOf("-0.0")).sheet());
        for (String invalid : List.of("{} {}", "{\"x\":1,}", "{\"x\":NaN}")) {
            assertThrows(IllegalArgumentException.class, () -> CodeFormatter.format(source(invalid), CodeFormatter.Language.JSON));
        }
    }

    @Test
    void xmlKeepsAttributeOrderAndTextAndRefusesExternalEntitiesAndMixedContent() {
        var source = source("<?xml version='1.0'?><root>", "<a z='2' a='1'>a  b &amp; c</a>", "<b/><!-- hello --></root>");
        var result = CodeFormatter.format(source, CodeFormatter.Language.AUTO);
        assertEquals(CodeFormatter.Language.XML, result.language());
        assertTrue(result.source().text().contains("\n  <a z='2' a='1'>a  b &amp; c</a>\n"));
        assertEquals("Step1", originAt(result.source(), result.source().text().indexOf("&amp;")).sheet());
        assertEquals("<leaf>  </leaf>", CodeFormatter.format(source("<leaf>  </leaf>"), CodeFormatter.Language.XML).source().text());
        for (String unsafe : List.of("<!DOCTYPE r [<!ENTITY e SYSTEM 'file:///should-not-be-read'>]><r>&e;</r>",
                "<p>Hello <b>world</b>!</p>", "<p><!-- keep -->text</p>", "<r xml:space='preserve'>  <x/> </r>")) {
            assertThrows(IllegalArgumentException.class, () -> CodeFormatter.format(source(unsafe), CodeFormatter.Language.XML));
        }
    }

    @Test
    void formattingDoesNotGuessFromTheContainerAndNeverSilentlyLosesOrReordersCode() {
        assertEquals(CodeFormatter.Language.JAVASCRIPT, CodeFormatter.detect("function f(){return 1;}"));
        assertEquals(CodeFormatter.Language.PYTHON, CodeFormatter.detect("def f():\n    return 1\n"));
        assertEquals(CodeFormatter.Language.CPP, CodeFormatter.detect("#include <stdio.h>\nint main(){return 0;}"));
        assertEquals(CodeFormatter.Language.TEXT, CodeFormatter.detect("SELECT * FROM events;"));
        var input = source("first();", "second();");
        assertThrows(IllegalArgumentException.class, () -> CodeFormatter.remap(input, "second(); first();"));
        assertThrows(IllegalArgumentException.class, () -> CodeFormatter.remap(input, "first();"));
        assertThrows(IllegalArgumentException.class, () -> CodeFormatter.remap(input, "first(); third();"));
        assertThrows(IllegalArgumentException.class, () -> CodeFormatter.format(new RenderedSource("class X{…", List.of(), true), CodeFormatter.Language.JAVA));
        assertThrows(IllegalArgumentException.class, () -> CodeFormatter.format(source("opaque user DSL"), CodeFormatter.Language.AUTO));
    }

    @Test
    void installedRuffFormatsOnlyStdinAndKeepsOrigins() {
        assumeTrue(ExternalCodeFormatter.executable("ruff") != null, "optional Python formatter is not installed");
        var source = source("def f():\n", "    x=1+2\n", "    return x\n");
        var result = CodeFormatter.format(source, CodeFormatter.Language.PYTHON);
        assertTrue(result.source().text().contains("x = 1 + 2"));
        assertEquals("Step1", originAt(result.source(), result.source().text().indexOf("1 + 2")).sheet());
    }

    static RenderedSource.Origin originAt(RenderedSource source, int index) {
        return source.chunks().stream().filter(c -> c.start() <= index && index < c.end()).findFirst().orElseThrow().origins().getFirst();
    }
}
