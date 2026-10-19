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

import com.fasterxml.jackson.core.JsonFactory;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import org.ghyura.cf.generation.RenderedSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Preview-only formatting. No candidate is compiled, loaded, executed or written back. */
final class CodeFormatter {
    static final int MAX_FORMATTED_CHARS = 128_000;

    enum Language {
        AUTO("Auto-detect"), JAVA("Java"), JSON("JSON"), XML("XML"), PYTHON("Python"),
        JAVASCRIPT("JavaScript"), TYPESCRIPT("TypeScript"), HTML("HTML"), CSS("CSS"), YAML("YAML"),
        C("C"), CPP("C++"), CSHARP("C#"), GO("Go"), RUST("Rust"), SHELL("Shell"), TEXT("Other / plain text");
        private final String title;
        Language(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    record Result(RenderedSource source, Language language) { }

    static Result format(RenderedSource source, Language requested) {
        if (source.truncated()) throw new IllegalArgumentException("This preview is truncated; formatting needs complete code.");
        if (source.text().isBlank()) throw new IllegalArgumentException("Select a test case with code first.");
        Language language = requested == Language.AUTO ? detect(source.text()) : requested;
        String formatted = switch (language) {
            case JAVA -> java(source.text());
            case JSON -> json(source.text());
            case XML -> XmlPreviewFormatter.format(source.text());
            case AUTO, TEXT -> throw new IllegalArgumentException("Language not recognized. Choose a language; origins remain available for any text.");
            default -> ExternalCodeFormatter.format(source.text(), language);
        };
        return new Result(remap(source, formatted), language);
    }

    /** Conservative hints, independent of the scenario container's .xlsx/.json/.toml/.yaml extension and the runner. */
    static Language detect(String code) {
        String s = code.stripLeading();
        String lower = s.toLowerCase(Locale.ROOT);
        if (s.startsWith("{") || s.startsWith("[")) {
            try { validateJson(s); return Language.JSON; } catch (IllegalArgumentException ignored) { }
        }
        if (lower.startsWith("<!doctype html") || lower.startsWith("<html")) return Language.HTML;
        if (s.startsWith("<?xml") || s.matches("(?s)<[A-Za-z_][\\w:.-]*(?:\\s|>|/).*")) return Language.XML;
        if (s.startsWith("#!") && lower.lines().findFirst().orElse("").contains("python")
                || s.matches("(?s)(?:from \\w.* import .*|import \\w+\\s*\\n.*|(?:async )?def \\w+\\(.*|class \\w+.*:.*)")) return Language.PYTHON;
        if (s.startsWith("#!") && lower.lines().findFirst().orElse("").matches(".*(?:bash|/sh|zsh).*")) return Language.SHELL;
        if (s.startsWith("#include") || s.startsWith("#define")) return Language.CPP;
        if (s.matches("(?s)(?:pub(?:\\([^)]*\\))?\\s+)?(?:fn |use |mod |extern crate ).*")) return Language.RUST;
        if (s.matches("(?s)package \\w+\\s*\\n.*\\bfunc\\b.*")) return Language.GO;
        if (s.matches("(?s)(?:(?:export |async )?function |(?:const|let|var) \\w+\\s*=|console\\.).*")) return Language.JAVASCRIPT;
        try { javaNode(s); return Language.JAVA; } catch (IllegalArgumentException ignored) { }
        return Language.TEXT;
    }

    private static Node javaNode(String source) {
        var parser = new JavaParser(new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE)
                .setPreprocessUnicodeEscapes(false).setStoreTokens(true));
        var unit = parser.parse(source);
        if (unit.isSuccessful() && unit.getResult().isPresent() && !unit.getResult().orElseThrow().getTypes().isEmpty()) {
            return unit.getResult().orElseThrow();
        }
        var block = parser.parseBlock(source);
        if (block.isSuccessful() && block.getResult().isPresent()) return block.getResult().orElseThrow();
        var statement = parser.parseStatement(source);
        if (statement.isSuccessful() && statement.getResult().isPresent()) return statement.getResult().orElseThrow();
        throw new IllegalArgumentException("Not a complete Java source, block or statement. Choose its language, or view the original.");
    }

    private record Token(int start, int end, String text, boolean comment) { }

    private static List<Token> tokens(Node node, String source) {
        var result = new ArrayList<Token>();
        var token = node.getTokenRange().orElseThrow().getBegin().findFirstToken();
        int at = 0;
        while (true) {
            String text = token.getText();
            if (!source.startsWith(text, at)) throw new IllegalArgumentException("The Java parser changed source spelling; original retained.");
            if (!token.getCategory().isWhitespace() && !text.isEmpty()) {
                result.add(new Token(at, at + text.length(), text, token.getCategory().isComment()));
            }
            at += text.length();
            if (token.getNextToken().isEmpty()) break;
            token = token.getNextToken().orElseThrow();
        }
        if (at != source.length()) throw new IllegalArgumentException("The Java parser did not consume all source text; original retained.");
        return result;
    }

    private static String java(String source) {
        Node node = javaNode(source);
        List<Token> original = tokens(node, source);
        String pretty = new DefaultPrettyPrinter().print(node);
        List<Token> output = tokens(javaNode(pretty), pretty);
        if (original.size() != output.size()) throw new IllegalArgumentException("Formatting would change Java tokens; original retained.");
        // The printer may reflow comments or text blocks. Keep their exact original spelling and content.
        StringBuilder restored = new StringBuilder();
        int from = 0;
        for (int i = 0; i < original.size(); i++) {
            Token before = original.get(i), after = output.get(i);
            if (!before.text().equals(after.text()) && !(before.comment() && after.comment())
                    && !(before.text().startsWith("\"\"\"") && after.text().startsWith("\"\"\""))) {
                throw new IllegalArgumentException("Formatting would change or reorder Java tokens; original retained.");
            }
            restored.append(pretty, from, after.start()).append(before.text());
            from = after.end();
        }
        return restored.append(pretty, from, pretty.length()).toString();
    }

    private static void validateJson(String source) {
        try (var parser = new JsonFactory().createParser(source)) {
            if (parser.nextToken() == null) throw new IllegalArgumentException("Empty JSON.");
            parser.skipChildren();
            if (parser.nextToken() != null) throw new IllegalArgumentException("JSON must contain one complete value.");
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Not valid JSON: " + e.getMessage(), e);
        }
    }

    /** Streaming validation plus whitespace edits: duplicate keys, escapes and number spelling are preserved. */
    private static String json(String source) {
        validateJson(source);
        StringBuilder out = new StringBuilder();
        boolean quoted = false, escaped = false;
        int depth = 0;
        char previous = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quoted) {
                out.append(c);
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
                continue;
            }
            if (Character.isWhitespace(c)) continue;
            if (c == '"') { quoted = true; out.append(c); }
            else if (c == '{' || c == '[') {
                out.append(c); depth++;
                int next = i + 1;
                while (next < source.length() && Character.isWhitespace(source.charAt(next))) next++;
                if (next < source.length() && source.charAt(next) != '}' && source.charAt(next) != ']') newline(out, depth);
            } else if (c == '}' || c == ']') {
                depth--;
                if (previous != '{' && previous != '[') newline(out, depth);
                out.append(c);
            } else if (c == ',') { out.append(c); newline(out, depth); }
            else if (c == ':') out.append(": ");
            else out.append(c);
            previous = c;
            if (out.length() > MAX_FORMATTED_CHARS) throw new IllegalArgumentException("Formatted preview is too large; original retained.");
        }
        return out.toString();
    }

    private static void newline(StringBuilder out, int depth) {
        if (depth > 128) throw new IllegalArgumentException("Source nesting is too deep to format in the preview.");
        out.append('\n').append("  ".repeat(Math.max(0, depth)));
    }

    /**
     * Monotone character mapping, never a substring search (identical actions must keep their separate occurrences).
     * Reject additions, deletions and reordering of non-whitespace. New layout whitespace between different origins
     * stays neutral; whitespace inside the same origin receives its background. The original source is immutable.
     */
    static RenderedSource remap(RenderedSource source, String formatted) {
        if (formatted.length() > MAX_FORMATTED_CHARS) throw new IllegalArgumentException("Formatted preview is too large; original retained.");
        int[] positions = new int[formatted.length()];
        Arrays.fill(positions, -1);
        String original = source.text();
        int at = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (Character.isWhitespace(formatted.charAt(i))) continue;
            while (at < original.length() && Character.isWhitespace(original.charAt(at))) at++;
            if (at == original.length() || original.charAt(at) != formatted.charAt(i)) {
                throw new IllegalArgumentException("The formatter rewrote content; kept the original to preserve exact chunk origins.");
            }
            positions[i] = at++;
        }
        while (at < original.length() && Character.isWhitespace(original.charAt(at))) at++;
        if (at != original.length()) throw new IllegalArgumentException("The formatter removed content; original retained.");
        int[] owners = new int[original.length()];
        Arrays.fill(owners, -1);
        for (int i = 0; i < source.chunks().size(); i++) {
            var chunk = source.chunks().get(i);
            Arrays.fill(owners, chunk.start(), chunk.end(), i);
        }
        for (int i = 0; i < positions.length; i++) if (positions[i] >= 0) positions[i] = owners[positions[i]];
        int previous = -1;
        for (int i = 0; i < positions.length;) {
            if (!Character.isWhitespace(formatted.charAt(i))) { previous = positions[i++]; continue; }
            int start = i;
            while (i < positions.length && Character.isWhitespace(formatted.charAt(i))) i++;
            int following = i < positions.length ? positions[i] : -1;
            if (previous == following && previous >= 0) Arrays.fill(positions, start, i, previous);
        }
        List<RenderedSource.Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < positions.length;) {
            int start = i, owner = positions[i++];
            while (i < positions.length && positions[i] == owner) i++;
            if (owner >= 0) chunks.add(new RenderedSource.Chunk(start, i, source.chunks().get(owner).origins()));
        }
        return new RenderedSource(formatted, chunks, false);
    }
}
