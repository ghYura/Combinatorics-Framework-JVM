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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The Markdown the framework's own manuals use, as the simple HTML a Swing text pane shows: headings, paragraphs, bulleted
 * and numbered lists (nested, with continued lines), quotes, tables, fenced code, rules, and inline bold, italics, code
 * and links (their text). Everything else is shown as text; nothing is fetched.
 */
final class MarkdownLite {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*?)\\s*#*\\s*$");
    private static final Pattern ITEM = Pattern.compile("^(\\s*)(\\d{1,9}[.)]|[-*+])\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern TABLE_LINE = Pattern.compile("^\\s*\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)*\\|?\\s*$");
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC = Pattern.compile("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");

    private MarkdownLite() {
    }

    /** The markdown as the body of an HTML page. */
    static String html(String markdown) {
        return blocks(markdown.replace("\t", "    ").lines().toList());
    }

    private static String blocks(List<String> lines) {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                i++;
            } else if (trimmed.startsWith("```")) {
                StringBuilder code = new StringBuilder();
                for (i++; i < lines.size() && !lines.get(i).strip().startsWith("```"); i++) {
                    code.append(escape(lines.get(i))).append('\n');
                }
                out.append("<pre>").append(code).append("</pre>");
                i++;
            } else if (HEADING.matcher(line).matches()) {
                Matcher h = HEADING.matcher(line);
                h.matches();
                int level = h.group(1).length();
                out.append("<h").append(level).append('>').append(inline(h.group(2))).append("</h").append(level).append('>');
                i++;
            } else if (RULE.matcher(line).matches()) {
                out.append("<hr>");
                i++;
            } else if (trimmed.startsWith(">")) {
                List<String> quoted = new ArrayList<>();
                for (; i < lines.size() && lines.get(i).strip().startsWith(">"); i++) {
                    String q = lines.get(i).strip().substring(1);
                    quoted.add(q.startsWith(" ") ? q.substring(1) : q);
                }
                out.append("<blockquote>").append(blocks(quoted)).append("</blockquote>");
            } else if (trimmed.startsWith("|")) {
                i = table(lines, i, out);
            } else if (ITEM.matcher(line).matches()) {
                i = list(lines, i, out);
            } else {
                StringBuilder text = new StringBuilder(trimmed);
                for (i++; i < lines.size() && !lines.get(i).isBlank() && !startsBlock(lines.get(i)); i++) {
                    text.append(' ').append(lines.get(i).strip());
                }
                out.append("<p>").append(inline(text.toString())).append("</p>");
            }
        }
        return out.toString();
    }

    private static boolean startsBlock(String line) {
        String trimmed = line.strip();
        return trimmed.startsWith("```") || trimmed.startsWith(">") || trimmed.startsWith("|") || HEADING.matcher(line).matches()
                || RULE.matcher(line).matches() || ITEM.matcher(line).matches();
    }

    /** A list from line i: its items at one indent, each with the lines indented under it; returns the line after it. */
    private static int list(List<String> lines, int i, StringBuilder out) {
        Matcher first = ITEM.matcher(lines.get(i));
        first.matches();
        int indent = first.group(1).length();
        boolean ordered = Character.isDigit(first.group(2).charAt(0));
        String start = ordered ? first.group(2).substring(0, first.group(2).length() - 1) : "1";
        out.append(ordered ? start.equals("1") ? "<ol>" : "<ol start=\"" + Integer.parseInt(start) + "\">" : "<ul>");
        while (i < lines.size()) {
            int next = i;
            while (next < lines.size() && lines.get(next).isBlank()) {
                next++;
            }
            Matcher m = next < lines.size() ? ITEM.matcher(lines.get(next)) : null;
            if (m == null || !m.matches() || m.group(1).length() != indent || Character.isDigit(m.group(2).charAt(0)) != ordered) {
                break;
            }
            int column = m.start(3);
            List<String> body = new ArrayList<>();
            body.add(m.group(3));
            for (i = next + 1; i < lines.size(); i++) {
                String line = lines.get(i);
                if (line.isBlank()) {
                    // a blank line belongs to the item only when a line indented under it follows
                    if (i + 1 < lines.size() && !lines.get(i + 1).isBlank() && indentOf(lines.get(i + 1)) > indent) {
                        body.add("");
                        continue;
                    }
                    break;
                }
                int at = indentOf(line);
                if (at > indent) {
                    body.add(line.substring(Math.min(at, column)));
                } else if (!startsBlock(line)) {
                    body.add(line.strip());   // the item's sentence goes on without an indent
                } else {
                    break;
                }
            }
            out.append("<li>").append(tight(blocks(body))).append("</li>");
        }
        out.append(ordered ? "</ol>" : "</ul>");
        return i;
    }

    /** An item's first paragraph without its paragraph margins, as a list without blank lines between items shows it. */
    private static String tight(String html) {
        if (!html.startsWith("<p>")) {
            return html;
        }
        int end = html.indexOf("</p>");
        return html.substring(3, end) + html.substring(end + 4);
    }

    private static int indentOf(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    /** A table from line i: the first row its header, a row of dashes after it skipped; returns the line after it. */
    private static int table(List<String> lines, int i, StringBuilder out) {
        out.append("<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">");
        boolean header = true;
        for (; i < lines.size() && lines.get(i).strip().startsWith("|"); i++) {
            String row = lines.get(i).strip();
            if (TABLE_LINE.matcher(row).matches()) {
                continue;
            }
            row = row.substring(1);
            if (row.endsWith("|")) {
                row = row.substring(0, row.length() - 1);
            }
            String cell = header && i + 1 < lines.size() && TABLE_LINE.matcher(lines.get(i + 1).strip()).matches() ? "th" : "td";
            out.append("<tr>");
            for (String text : row.split("\\|", -1)) {
                out.append('<').append(cell).append(" valign=\"top\">").append(inline(text.strip())).append("</").append(cell).append('>');
            }
            out.append("</tr>");
            header = false;
        }
        out.append("</table>");
        return i;
    }

    /** Bold, italics, code and links within a line; code is shown as written. */
    static String inline(String text) {
        StringBuilder out = new StringBuilder();
        String[] parts = text.split("`", -1);
        for (int k = 0; k < parts.length; k++) {
            boolean code = k % 2 == 1 && k < parts.length - 1;
            if (code) {
                out.append("<code>").append(escape(parts[k])).append("</code>");
            } else {
                String plain = escape(k % 2 == 1 ? "`" + parts[k] : parts[k]);
                plain = LINK.matcher(plain).replaceAll("$1");
                plain = BOLD.matcher(plain).replaceAll("<b>$1</b>");
                plain = ITALIC.matcher(plain).replaceAll("<i>$1</i>");
                out.append(plain);
            }
        }
        return out.toString();
    }

    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
