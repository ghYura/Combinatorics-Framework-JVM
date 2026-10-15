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

package org.ghyura.cf.compiler;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.Trees;
import org.ghyura.cf.model.Diagnostic;
import org.ghyura.cf.model.ErrorCode;
import org.ghyura.cf.model.FrameworkException;
import org.ghyura.cf.model.SourceLocation;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Source decomposition (GEN:source-decomposition; the legacy code_decompose.py): splits a program into logically whole,
 * byte-preserving pieces (their concatenation is the source) and writes the fwgen spec the legacy tool wrote, one raw
 * slot per piece with its original text as the first value, interaction notes from shared identifiers, ready for
 * rewrites to be combined. Nothing is executed.
 *
 * <ul>
 *   <li>JAVA splits the first top-level type into its header (package, imports, declaration and the line of its opening
 *       brace), one piece per member and its footer, at member boundaries the JDK's own parser finds (javac's tree,
 *       parse only), so text blocks, braces inside annotations, lambdas and nested types split correctly; the members
 *       keep the legacy line conventions, names, kinds and identifier coupling.</li>
 *   <li>JAVA_HEURISTIC is the legacy character scanner unchanged, for exact parity; like the legacy tool it refuses a
 *       split that is not byte-preserving (text blocks break it).</li>
 *   <li>GENERIC splits at blank-line gaps, as the legacy fallback did.</li>
 *   <li>PYTHON is the legacy ast decomposition: one piece per top-level function or class, runs of other statements merged,
 *       leading lines with the piece after them, names as the legacy {@code _py_names} gathered them; the source is read
 *       by {@link PythonSyntax}, which parses as CPython 3.14's ast.parse does (no Python runs).</li>
 *   <li>AUTO chooses as the legacy tool chose: Python when the text parses as Python, else Java when it looks like Java,
 *       else blank-line blocks.</li>
 * </ul>
 */
public final class SourceDecomposition {

    /** How to split. */
    public enum Language {
        AUTO, JAVA, JAVA_HEURISTIC, GENERIC, PYTHON;

        /** auto, java, java-heuristic, generic or py. */
        public static Language ofToken(String token) {
            return switch (token) {
                case "auto" -> AUTO;
                case "java" -> JAVA;
                case "java-heuristic" -> JAVA_HEURISTIC;
                case "generic" -> GENERIC;
                case "py" -> PYTHON;
                default -> throw new FrameworkException(Diagnostic.error(ErrorCode.USAGE_INVALID_VALUE, "--lang takes auto, java, "
                        + "java-heuristic, generic or py, not '" + token + "'", SourceLocation.NONE, "option", "--lang"));
            };
        }
    }

    /**
     * One piece.
     *
     * @param start the offset of the piece in the source (UTF-16 units), for its location
     */
    public record Piece(String kind, String name, String code, SortedSet<String> defines, SortedSet<String> uses, String verb, String note,
                        int start) {
        public Piece {
            defines = Collections.unmodifiableSortedSet(new TreeSet<>(defines));
            uses = Collections.unmodifiableSortedSet(new TreeSet<>(uses));
        }
    }

    /** The spreadsheet cell ceiling (POI): a longer piece is split for workbook use. */
    public static final int XLSX_CELL_LIMIT = 32767;
    static final String DEFAULT_VERB = "FW_Combi(1)";
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern BLANK_GAP = Pattern.compile("\n[ \t]*\n");
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\n]*");
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern CALL_NAME = Pattern.compile("(?<![.\\w])([A-Za-z_]\\w*)\\s*\\(", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern FIELD_NAME = Pattern.compile("\\b([A-Za-z_]\\w*)\\s*[=;]", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern TYPE_WORD = Pattern.compile("\\b(class|interface|enum|record)\\b", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern LOOKS_JAVA = Pattern.compile("\\b(class|interface|enum|record)\\s+\\w+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final String HEADER = String.join("\n", "# ============================================================================",
            "# fwgen DECOMPOSITION spec  \u2014  QUICK START (auto-generated by code_decompose.py)",
            "# ----------------------------------------------------------------------------",
            "# Each [[slots]] below is ONE logical PIECE of the source. values[0] is the",
            "# ORIGINAL piece. To explore the space, ADD REWRITES as more '''...''' entries",
            "# to a piece's `values`. The Core then takes the CARTESIAN of every piece's",
            "# chosen rewrite (verb FW_Combi(1)) \u2014 i.e. every combination of rewrites \u2014 which",
            "# is what surfaces INDIRECT cross-piece interactions (see the per-piece notes).",
            "#",
            "# Run it through the Bundle:",
            "#   python3 fwgen_cli.py list --specs <this-dir>      # predicts the Core row count",
            "#   python3 fwgen_cli.py gen  --specs <this-dir> --out out",
            "#   # \u2192 Core fills the DB, Reader reassembles each combination into a program.",
            "#   # raw=true \u21d2 pieces emitted VERBATIM; run the Reader with",
            "#   #   reader.core.concatenator=''  so the pieces concatenate back cleanly.",
            "#",
            "# Tuning the rule per piece (the verb): FW_Combi(1)=cartesian leaf (default);",
            "#   FW_Permut if a piece is an ORDER-sensitive step sequence; FW_Subsets if a",
            "#   piece is OPTIONAL (present/absent). Keep value sets small to avoid explosion.",
            "# ============================================================================",
            "");
    private static final Set<String> JAVA_KEYWORDS = Set.of("abstract", "boolean", "break", "byte", "case", "catch", "char", "class", "continue", "default", "do", "double", "else", "enum", "extends", "false", "final", "finally", "float", "for", "if", "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "null", "package", "private", "protected", "public", "record", "return", "short", "static", "super", "switch", "synchronized", "this", "throw", "throws", "transient", "true", "try", "void", "volatile", "while");

    private SourceDecomposition() {
    }

    /** A piece being built: its text grows as whitespace-only blocks join it. */
    private static final class Draft {
        final String kind;
        final String name;
        final StringBuilder code;
        final Set<String> defines;
        final Set<String> uses;
        final int start;
        String note = "";

        Draft(String kind, String name, String code, Set<String> defines, Set<String> uses, int start) {
            this.kind = kind;
            this.name = name;
            this.code = new StringBuilder(code);
            this.defines = defines;
            this.uses = uses;
            this.start = start;
        }

        Piece piece() {
            return new Piece(kind, name, code.toString(), new TreeSet<>(defines), new TreeSet<>(uses), DEFAULT_VERB, note, start);
        }
    }

    /**
     * Splits a source.
     *
     * @throws FrameworkException INPUT_MALFORMED when PYTHON is asked of a text that does not parse as Python, or when the
     *                            legacy heuristic's split is not byte-preserving
     */
    public static List<Piece> decompose(String src, Language language) {
        Language chosen = language == Language.AUTO
                ? (looksPython(src) ? Language.PYTHON : looksJava(src) ? Language.JAVA : Language.GENERIC) : language;
        List<Draft> drafts = switch (chosen) {
            case PYTHON -> python(src);
            case JAVA -> orGeneric(javaSyntax(src), src);
            case JAVA_HEURISTIC -> orGeneric(javaHeuristic(src), src);
            default -> generic(src);
        };
        annotate(drafts);
        List<Piece> pieces = drafts.stream().map(Draft::piece).toList();
        if (!join(pieces).equals(src)) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "the " + (chosen == Language.JAVA_HEURISTIC
                    ? "legacy heuristic's" : chosen.name().toLowerCase(Locale.ROOT)) + " split is not byte-preserving (the legacy tool "
                    + "refused it too: its scanner does not know Java text blocks); use --lang java", SourceLocation.NONE));
        }
        return pieces;
    }

    private static List<Draft> orGeneric(List<Draft> pieces, String src) {
        return pieces != null ? pieces : generic(src);
    }

    /** The pieces' text, joined: the source when the split is byte-preserving. */
    public static String join(List<Piece> pieces) {
        StringBuilder all = new StringBuilder();
        for (Piece p : pieces) {
            all.append(p.code());
        }
        return all.toString();
    }

    // ---- python --------------------------------------------------------------------------------------------------

    /**
     * decompose_python: one piece per top-level def or class, consecutive other statements merged; a statement's piece
     * runs from the end of the one before to its own last line, so leading blanks, comments and decorators go with the piece
     * after them and trailing lines with the last one. Lines are str.splitlines(keepends=True), the statement ends ast's
     * line numbers, as the legacy tool sliced them.
     */
    private static List<Draft> python(String src) {
        PythonSyntax.Module module;
        try {
            module = PythonSyntax.parse(src);
        } catch (PythonSyntax.Failure e) {
            throw new FrameworkException(Diagnostic.error(ErrorCode.INPUT_MALFORMED, "the source does not parse as Python (as CPython 3.14's "
                    + "ast.parse reads it): " + e.getMessage(), SourceLocation.of("source", e.line)));
        }
        List<String> lines = splitLinesKeepEnds(src);
        List<String[]> raw = new ArrayList<>();
        int prev = 0;
        for (PythonSyntax.Statement st : module.body()) {
            int end = st.endLine();
            raw.add(new String[]{st.kind(), st.name(), slice(lines, prev, end)});
            prev = end;
        }
        if (prev < lines.size()) {
            String tail = slice(lines, prev, lines.size());
            if (raw.isEmpty()) {
                raw.add(new String[]{"stmt", "", tail});
            } else {
                raw.getLast()[2] += tail;
            }
        }
        List<String[]> merged = new ArrayList<>();
        for (String[] r : raw) {
            if (r[0].equals("stmt") && !merged.isEmpty() && merged.getLast()[0].equals("stmt")) {
                merged.getLast()[2] += r[2];
            } else {
                merged.add(r.clone());
            }
        }
        List<Draft> drafts = new ArrayList<>();
        int start = 0;
        for (String[] m : merged) {
            Set<String> defines = new TreeSet<>();
            Set<String> uses = new TreeSet<>();
            try {
                PythonSyntax.Module piece = PythonSyntax.parse(m[2]);
                defines.addAll(piece.defined());
                uses.addAll(piece.used());
            } catch (PythonSyntax.Failure e) {
                // _py_names: a piece that does not parse on its own defines and uses nothing
            }
            drafts.add(new Draft(m[0].equals("stmt") ? "statements" : m[0], m[1], m[2], defines, uses, start));
            start += m[2].length();
        }
        return drafts;
    }

    /** lines[from:to] joined, with Python's clipping of out-of-range bounds. */
    private static String slice(List<String> lines, int from, int to) {
        int a = Math.max(0, Math.min(from, lines.size()));
        int b = Math.max(a, Math.min(to, lines.size()));
        return String.join("", lines.subList(a, b));
    }

    /** str.splitlines(keepends=True): every line boundary CPython's str knows, CR LF as one. */
    static List<String> splitLinesKeepEnds(String s) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        int k = 0;
        while (k < s.length()) {
            char c = s.charAt(k);
            if (c == '\n' || c == '\r' || c == 0x0B || c == 0x0C || c == 0x1C || c == 0x1D || c == 0x1E || c == 0x85 || c == 0x2028
                    || c == 0x2029) {
                k += c == '\r' && k + 1 < s.length() && s.charAt(k + 1) == '\n' ? 2 : 1;
                lines.add(s.substring(start, k));
                start = k;
            } else {
                k++;
            }
        }
        if (start < s.length()) {
            lines.add(s.substring(start));
        }
        return lines;
    }

    /** _looks_python: whether the text parses as Python, as CPython 3.14's ast.parse would decide. */
    public static boolean looksPython(String src) {
        return PythonSyntax.parses(src);
    }

    /** _looks_java: a type declaration and a brace. */
    static boolean looksJava(String src) {
        return LOOKS_JAVA.matcher(src).find() && src.contains("{");
    }

    // ---- generic ---------------------------------------------------------------------------------------------------

    /** decompose_generic: blank-line gaps; whitespace-only chunks join the piece before them. */
    private static List<Draft> generic(String src) {
        List<String> chunks = new ArrayList<>();
        Matcher m = BLANK_GAP.matcher(src);
        int last = 0;
        while (m.find()) {
            chunks.add(src.substring(last, m.start()));
            chunks.add(m.group());
            last = m.end();
        }
        chunks.add(src.substring(last));
        List<Draft> pieces = new ArrayList<>();
        int at = 0;
        for (int i = 0; i < chunks.size(); i += 2) {
            String code = chunks.get(i) + (i + 1 < chunks.size() ? chunks.get(i + 1) : "");
            if (!pyBlank(code)) {
                pieces.add(new Draft("block", "", code, Set.of(), identifiers(code), at));
            } else if (!pieces.isEmpty()) {
                pieces.getLast().code.append(code);
            } else if (!code.isEmpty()) {
                pieces.add(new Draft("block", "", code, Set.of(), Set.of(), at));
            }
            at += code.length();
        }
        return pieces;
    }

    // ---- Java: the legacy heuristic ---------------------------------------------------------------------------------

    private static int eol(String src, int index) {
        int j = src.indexOf('\n', index);
        return j != -1 ? j + 1 : src.length();
    }

    /** decompose_java, unchanged: a comment-, string- and char-aware brace scanner over the first top-level type. */
    private static List<Draft> javaHeuristic(String src) {
        int n = src.length();
        int i = 0;
        int depth = 0;
        boolean lc = false;
        boolean bc = false;
        boolean dq = false;
        boolean sq = false;
        int bodyOpen = -1;
        int headEnd = -1;
        int memberStart = -1;
        boolean depth1IsMember = false;
        List<int[]> spans = new ArrayList<>();
        int tailStart = n;
        while (i < n) {
            char c = src.charAt(i);
            char d = i + 1 < n ? src.charAt(i + 1) : '\0';
            if (lc) {
                if (c == '\n') {
                    lc = false;
                }
            } else if (bc) {
                if (c == '*' && d == '/') {
                    bc = false;
                    i += 2;
                    continue;
                }
            } else if (dq) {
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    dq = false;
                }
            } else if (sq) {
                if (c == '\\') {
                    i += 2;
                    continue;
                }
                if (c == '\'') {
                    sq = false;
                }
            } else {
                if (c == '/' && d == '/') {
                    lc = true;
                    i += 2;
                    continue;
                }
                if (c == '/' && d == '*') {
                    bc = true;
                    i += 2;
                    continue;
                }
                if (c == '"') {
                    dq = true;
                } else if (c == '\'') {
                    sq = true;
                } else if (c == '{') {
                    depth++;
                    if (depth == 1 && bodyOpen < 0) {
                        bodyOpen = i;
                        headEnd = eol(src, i);
                        memberStart = headEnd;
                    } else if (depth == 2 && bodyOpen >= 0) {
                        String pre = src.substring(memberStart, i);
                        depth1IsMember = pre.contains(")") || TYPE_WORD.matcher(pre).find() || pre.stripTrailing().endsWith("static");
                    }
                } else if (c == '}') {
                    depth--;
                    if (bodyOpen >= 0 && depth == 0) {
                        tailStart = memberStart;
                        break;
                    }
                    if (bodyOpen >= 0 && depth == 1 && depth1IsMember) {
                        int end = eol(src, i);
                        spans.add(new int[]{memberStart, end});
                        memberStart = end;
                    }
                } else if (c == ';' && depth == 1 && bodyOpen >= 0) {
                    int end = eol(src, i);
                    spans.add(new int[]{memberStart, end});
                    memberStart = end;
                }
            }
            i++;
        }
        if (bodyOpen < 0) {
            return null;
        }
        return framed(src, headEnd, spans, tailStart);
    }

    /** Header, members (named and coupled as the legacy tool named them) and footer of a type split at these offsets. */
    private static List<Draft> framed(String src, int headEnd, List<int[]> spans, int tailStart) {
        List<Draft> pieces = new ArrayList<>();
        pieces.add(new Draft("header", "", src.substring(0, headEnd), Set.of(), Set.of(), 0));
        for (int[] span : spans) {
            String code = src.substring(span[0], Math.max(span[0], span[1]));
            String name = memberName(code);
            boolean method = !name.isEmpty() && Pattern.compile("\\b" + Pattern.quote(name) + "\\s*\\(", Pattern.UNICODE_CHARACTER_CLASS)
                    .matcher(code).find();
            Set<String> uses = new TreeSet<>(identifiers(stripComments(code)));
            uses.removeAll(JAVA_KEYWORDS);
            pieces.add(new Draft(method ? "method" : !name.isEmpty() ? "field" : "member", name, code, name.isEmpty() ? Set.of() : Set.of(name),
                    uses, span[0]));
        }
        pieces.add(new Draft("footer", "", src.substring(tailStart), Set.of(), Set.of(), tailStart));
        return pieces;
    }

    /** _java_member_name: a method's name before '(' (not a call after '.'), else a field's name before '=' or ';'. */
    private static String memberName(String code) {
        String s = stripComments(code);
        Matcher m = CALL_NAME.matcher(s);
        if (m.find() && !List.of("if", "for", "while", "switch", "catch", "return", "new").contains(m.group(1))) {
            return m.group(1);
        }
        Matcher f = FIELD_NAME.matcher(s);
        return f.find() ? f.group(1) : "";
    }

    /** The legacy order: line comments first (even inside strings), then block comments. */
    private static String stripComments(String code) {
        return BLOCK_COMMENT.matcher(LINE_COMMENT.matcher(code).replaceAll("")).replaceAll("");
    }

    private static Set<String> identifiers(String code) {
        Set<String> out = new TreeSet<>();
        Matcher m = IDENT.matcher(code);
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    // ---- Java: syntax-aware ---------------------------------------------------------------------------------------

    private static final class Source extends SimpleJavaFileObject {
        private final String text;

        Source(String text) {
            super(URI.create("string:///Decomposed.java"), Kind.SOURCE);
            this.text = text;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return text;
        }
    }

    /**
     * The first top-level type split where javac's parse tree puts its body and members (nothing is compiled or run);
     * the legacy heuristic when the source does not parse, null when it declares no type.
     */
    private static List<Draft> javaSyntax(String src) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return javaHeuristic(src);
        }
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavacTask task = (JavacTask) compiler.getTask(null, null, diagnostics, List.of("-proc:none", "-Xlint:none", "-nowarn"), null,
                List.of(new Source(src)));
        CompilationUnitTree unit;
        try {
            unit = task.parse().iterator().next();
        } catch (IOException | RuntimeException e) {
            return javaHeuristic(src);
        }
        if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == javax.tools.Diagnostic.Kind.ERROR)) {
            return javaHeuristic(src);
        }
        SourcePositions positions = Trees.instance(task).getSourcePositions();
        ClassTree type = null;
        for (Tree t : unit.getTypeDecls()) {
            if (t instanceof ClassTree c) {
                type = c;
                break;
            }
        }
        if (type == null) {
            return null;
        }
        int bodyOpen = bodyBrace(src, unit, type, positions);
        if (bodyOpen < 0) {
            return javaHeuristic(src);
        }
        int headEnd = eol(src, bodyOpen);
        int memberStart = headEnd;
        List<int[]> spans = new ArrayList<>();
        // an enum's constants run to the first ';' (or the closing brace) at depth 0: one piece, as the legacy scanner made
        int constantsEnd = type.getKind() == Tree.Kind.ENUM ? enumConstantsEnd(src, bodyOpen) : -1;
        boolean constantsDone = constantsEnd < 0;
        for (Tree member : type.getMembers()) {
            long from = positions.getStartPosition(unit, member);
            if (from < bodyOpen) {
                continue; // a record component: part of the header
            }
            int end;
            if (!constantsDone && from < constantsEnd) {
                end = constantsEnd;
            } else {
                constantsDone = true;
                end = (int) positions.getEndPosition(unit, member);
            }
            int lineEnd = eol(src, Math.max(0, end - 1));
            if (lineEnd <= memberStart) {
                continue; // on a line already taken: the member joins the piece before it
            }
            spans.add(new int[]{memberStart, lineEnd});
            memberStart = lineEnd;
        }
        return framed(src, headEnd, spans, memberStart);
    }

    /** After the ';' that ends an enum's constants (depth 0, outside literals and comments), or at its closing brace. */
    private static int enumConstantsEnd(String src, int bodyOpen) {
        int depth = 0;
        int i = bodyOpen + 1;
        while (i < src.length()) {
            int skipped = skipLiteralOrComment(src, i);
            if (skipped != i) {
                i = skipped;
                continue;
            }
            char c = src.charAt(i);
            if (c == '(' || c == '{' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}' && depth > 0) {
                depth--;
            } else if (c == '}') {
                return i;
            } else if (c == ';' && depth == 0) {
                return i + 1;
            }
            i++;
        }
        return src.length();
    }

    /** The offset of the type body's '{': the first one after the declaration's last header part, outside comments. */
    private static int bodyBrace(String src, CompilationUnitTree unit, ClassTree type, SourcePositions positions) {
        long from = positions.getEndPosition(unit, type.getModifiers());
        from = Math.max(from, positions.getStartPosition(unit, type));
        for (Tree t : type.getTypeParameters()) {
            from = Math.max(from, positions.getEndPosition(unit, t));
        }
        if (type.getExtendsClause() != null) {
            from = Math.max(from, positions.getEndPosition(unit, type.getExtendsClause()));
        }
        for (Tree t : type.getImplementsClause()) {
            from = Math.max(from, positions.getEndPosition(unit, t));
        }
        for (Tree t : type.getPermitsClause()) {
            from = Math.max(from, positions.getEndPosition(unit, t));
        }
        for (Tree member : type.getMembers()) {
            long start = positions.getStartPosition(unit, member);
            long end = positions.getEndPosition(unit, member);
            if (type.getKind() == Tree.Kind.RECORD && member instanceof VariableTree && end >= 0 && end <= firstBraceFrom(src, (int) from)) {
                from = Math.max(from, end); // the components of a record header
            } else if (start >= 0) {
                break;
            }
        }
        if (from < 0) {
            return -1;
        }
        return firstBraceFrom(src, (int) from);
    }

    /** The first '{' at or after an offset that is not in a comment, string, text block or character literal. */
    private static int firstBraceFrom(String src, int from) {
        int i = from;
        int n = src.length();
        while (i < n) {
            int skipped = skipLiteralOrComment(src, i);
            if (skipped != i) {
                i = skipped;
                continue;
            }
            if (src.charAt(i) == '{') {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int skipSpaceAndComments(String src, int from) {
        int i = from;
        while (i < src.length()) {
            if (Character.isWhitespace(src.charAt(i))) {
                i++;
            } else if (src.startsWith("//", i) || src.startsWith("/*", i)) {
                i = skipLiteralOrComment(src, i);
            } else {
                break;
            }
        }
        return i;
    }

    /** The offset after a comment, string, text block or character literal starting at {@code i}, or {@code i}. */
    private static int skipLiteralOrComment(String src, int i) {
        int n = src.length();
        if (src.startsWith("//", i)) {
            int nl = src.indexOf('\n', i);
            return nl < 0 ? n : nl;
        }
        if (src.startsWith("/*", i)) {
            int end = src.indexOf("*/", i + 2);
            return end < 0 ? n : end + 2;
        }
        if (src.startsWith("\"\"\"", i)) {
            int j = i + 3;
            while (j < n) {
                if (src.charAt(j) == '\\') {
                    j += 2;
                } else if (src.startsWith("\"\"\"", j)) {
                    return j + 3;
                } else {
                    j++;
                }
            }
            return n;
        }
        char c = src.charAt(i);
        if (c == '"' || c == '\'') {
            int j = i + 1;
            while (j < n && src.charAt(j) != c && src.charAt(j) != '\n') {
                j += src.charAt(j) == '\\' ? 2 : 1;
            }
            return Math.min(n, j + 1);
        }
        return i;
    }

    // ---- oversize pieces ------------------------------------------------------------------------------------------

    /** _split_text_on_newlines: chunks of at most {@code limit} characters, cut after the last newline that fits. */
    static List<String> splitOnNewlines(String code, int limit) {
        List<String> chunks = new ArrayList<>();
        int i = 0;
        int n = code.length();
        while (n - i > limit) {
            int windowEnd = i + limit;
            int nl = code.lastIndexOf('\n', windowEnd - 1);
            int cut = nl > i ? nl + 1 : windowEnd;
            chunks.add(code.substring(i, cut));
            i = cut;
        }
        chunks.add(code.substring(i));
        return chunks;
    }

    /** subsplit_oversize: a piece longer than the limit becomes consecutive parts {@code <name>__partK}; coupling stays on part 1. */
    public static List<Piece> subsplitOversize(List<Piece> pieces, int limit) {
        List<Piece> out = new ArrayList<>();
        for (Piece p : pieces) {
            if (p.code().length() <= limit) {
                out.add(p);
                continue;
            }
            List<String> parts = splitOnNewlines(p.code(), limit);
            String base = p.name().isEmpty() ? p.kind() : p.name();
            int at = p.start();
            for (int j = 1; j <= parts.size(); j++) {
                String note = "role: sub-piece " + j + "/" + parts.size() + " of OVERSIZE " + p.kind() + " `" + p.name() + "` (" + p.code().length()
                        + " chars > " + limit + " cell limit).\n# byte-preserving split; reassembles via the Reader's concatenator='' in "
                        + "sheet order — keep these consecutive and single-valued.";
                out.add(new Piece(p.kind(), base + "__part" + j, parts.get(j - 1), j == 1 ? p.defines() : new TreeSet<>(),
                        j == 1 ? p.uses() : new TreeSet<>(), p.verb(), note, at));
                at += parts.get(j - 1).length();
            }
        }
        return out;
    }

    // ---- notes ----------------------------------------------------------------------------------------------------

    /** _annotate: each piece's role and the earlier or later pieces defining names it uses. */
    private static void annotate(List<Draft> pieces) {
        for (int i = 0; i < pieces.size(); i++) {
            Draft p = pieces.get(i);
            List<String> links = new ArrayList<>();
            for (int j = 0; j < pieces.size(); j++) {
                Draft q = pieces.get(j);
                if (j == i || q.defines.isEmpty()) {
                    continue;
                }
                TreeSet<String> shared = new TreeSet<>(p.uses);
                shared.retainAll(q.defines);
                if (!shared.isEmpty()) {
                    links.add(String.format(Locale.ROOT, "P%02d(%s)", j + 1, String.join(", ", shared.stream().limit(4).toList())));
                }
            }
            String role = switch (p.kind) {
                case "statements" -> "module-level statements (imports / constants / __main__ block)";
                case "function" -> "function `" + p.name + "`";
                case "class" -> "class `" + p.name + "`";
                case "block" -> "code block";
                case "header" -> "type header (package / imports / class decl + open brace)";
                case "method" -> "method `" + p.name + "`";
                case "field" -> "field `" + p.name + "`";
                case "footer" -> "type footer (closing brace(s) / trailing)";
                case "member" -> "type member";
                default -> p.kind;
            };
            String hint = p.kind.equals("header") || p.kind.equals("footer") || i == 0 && p.kind.equals("statements")
                    ? "  · structural frame — usually kept as a single CONSTANT value (FW_Combi(1))." : "";
            String note = "role: " + role + "." + hint;
            if (!links.isEmpty()) {
                note += "\n# interacts (uses names defined in): " + String.join(", ", links) + " → indirect coupling; the FW_Combi(1) "
                        + "cartesian covers every rewrite-pair, so a rewrite here is tried against each rewrite there.";
            } else {
                note += "\n# no static identifier coupling detected (still combined via the cartesian).";
            }
            p.note = note;
        }
    }

    // ---- the fwgen spec ---------------------------------------------------------------------------------------------

    private static String slug(String s, int index) {
        String base = s.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("^_+|_+$", "");
        String full = String.format(Locale.ROOT, "P%02d_%s", index, base.isEmpty() ? "piece" : base);
        return full.length() > 40 ? full.substring(0, 40) : full;
    }

    /**
     * A piece as a TOML multi-line string that reads back to exactly the piece (the legacy _toml_value lost a piece's
     * leading newline, which TOML trims right after the opening delimiter, and added a final one; CR became LF when read):
     * a literal '...' block opened with its own newline and closed right after the text, or an escaped basic "..."
     * block when the text holds three single quotes, a carriage return or another control character.
     */
    static String tomlValue(String code) {
        boolean literal = !code.contains("\'\'\'");
        for (int k = 0; k < code.length() && literal; k++) {
            char c = code.charAt(k);
            literal = c >= 0x20 && c != 0x7f || c == '\t' || c == '\n';
        }
        if (literal) {
            return "\'\'\'\n" + code + "\'\'\'";
        }
        StringBuilder out = new StringBuilder("\"\"\"\n");
        for (int k = 0; k < code.length(); k++) {
            char c = code.charAt(k);
            if (c == '\\') {
                out.append("\\\\");
            } else if (c == '"' && code.startsWith("\"\"\"", k)) {
                out.append("\\\"");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c < 0x20 && c != '\t' && c != '\n' || c == 0x7f) {
                out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        return out.append("\"\"\"").toString();
    }

    /** dump_toml: the decomposition spec, goals correctness and latency_ms, argument mode=decompose_rewrite. */
    public static String toml(List<Piece> pieces, String name) {
        List<String> out = new ArrayList<>();
        out.add(HEADER);
        out.add("title = \"decomposition of " + name + " — " + pieces.size() + " pieces, rewrite & combine\"");
        out.add("note  = \"Each slot = one code piece; add rewrites to values; the cartesian of rewrites surfaces indirect interactions. "
                + "raw=true → verbatim; Reader concatenator=''.\"");
        out.add("goals = [\"correctness\", \"latency_ms\"]");
        out.add("args  = [\"mode=decompose_rewrite\"]");
        out.add("");
        for (int i = 1; i <= pieces.size(); i++) {
            Piece p = pieces.get(i - 1);
            String sheet = slug(p.name().isEmpty() ? p.kind() : p.name(), i);
            out.add(("# --- PIECE " + i + "/" + pieces.size() + ": " + p.kind() + " " + p.name()).stripTrailing() + " ---");
            for (String line : p.note().split("\n", -1)) {
                if (line.isEmpty() && p.note().endsWith("\n")) {
                    continue;
                }
                out.add(line.startsWith("#") ? line : "# " + line);
            }
            out.add("[[slots]]");
            out.add("sheet  = \"" + sheet + "\"");
            out.add(String.format(Locale.ROOT, "key    = \"p%02d\"", i));
            out.add("verb   = \"" + p.verb() + "\"   # cartesian leaf; alt: FW_Permut (order) / FW_Subsets (optional)");
            out.add("raw    = true");
            out.add("values = [");
            out.add(tomlValue(p.code()) + ",");
            out.add("  # ↑ baseline (original). Add rewrites of THIS piece as more '''...''' entries.");
            out.add("]");
            out.add("");
        }
        return String.join("\n", out) + "\n";
    }

    // ---- Python str helpers ----------------------------------------------------------------------------------------

    /** Python's str.strip() leaves nothing: every character is Python whitespace. */
    private static boolean pyBlank(String s) {
        for (int k = 0; k < s.length(); k++) {
            char c = s.charAt(k);
            boolean space = c == ' ' || c >= '\t' && c <= '\r' || c >= '\u001c' && c <= '\u001f' || c == '\u0085' || c == ' '
                    || c == ' ' || c >= ' ' && c <= ' ' || c == ' ' || c == ' ' || c == ' ' || c == ' '
                    || c == '　';
            if (!space) {
                return false;
            }
        }
        return true;
    }

    /** The 1-based line and column of an offset, for a piece's location. */
    public static Map.Entry<Integer, Integer> lineAndColumn(String src, int offset) {
        int line = 1;
        int lineStart = 0;
        for (int k = 0; k < Math.min(offset, src.length()); k++) {
            if (src.charAt(k) == '\n') {
                line++;
                lineStart = k + 1;
            }
        }
        return Map.entry(line, offset - lineStart + 1);
    }
}
