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

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Any-language code origins, with optional preview formatting. All methods except the formatter run on the EDT. */
final class CandidateCodeView {
    enum Mode {
        BACKGROUNDS("Backgrounds + origin list"), LIST("Origin list only"), CODE("Code only");
        private final String title;
        Mode(String title) { this.title = title; }
        @Override public String toString() { return title; }
    }

    private record Entry(int number, List<RenderedSource.Origin> origins, List<RenderedSource.Chunk> spans) {
        int start() { return spans.getFirst().start(); }
        String name() { return origins.stream().map(CandidateCodeView::name).distinct().collect(java.util.stream.Collectors.joining(" + ")); }
    }

    private final JPanel panel = new JPanel(new BorderLayout(4, 4));
    private final JTextArea code = Hints.of(new JTextArea() {
        @Override public String getToolTipText(MouseEvent event) {
            int index = entryAt(viewToModel2D(event.getPoint()));
            return index < 0 ? "Layout whitespace or source without origin information."
                    : "Chunk " + entries.get(index).number() + ": " + describe(entries.get(index)).replace('\n', ' ');
        }
    }, "Filled when you select a test case in the candidate table. Select code to see its input origin. Formatting changes this view only.");
    private final JComboBox<CodeFormatter.Language> language = Hints.of(new JComboBox<>(CodeFormatter.Language.values()),
            "The language of the generated code, independent of the XLSX, JSON, TOML or YAML scenario file. Auto-detect uses the code itself.");
    private final JComboBox<Mode> mode = Hints.of(new JComboBox<>(Mode.values()),
            "Show muted origin backgrounds, use a text-only origin list, or give all space to code. Colors may repeat; the numbered list identifies each origin.");
    private final JButton format = Hints.of(new JButton("Auto-format"), "Format this preview with the selected language. The executed source and scenario file stay unchanged.");
    private final JButton original = Hints.of(new JButton("Original"), "Restore the exact generated source and its original chunk boundaries.");
    private final JLabel status = new JLabel("Select a test case to inspect its code and origins.");
    private final JTextArea details = Hints.of(new JTextArea(4, 24), "Filled when you select code or a chunk origin row: its input file, cell or document path, sheet, value label, position and optional status.");
    private final ListTableModel<Entry> model = new ListTableModel<>(List.of("Chunk", "Lines", "Origin", "Part"),
            List.<Function<Entry, Object>>of(Entry::number, this::lines, Entry::name,
                    e -> e.origins().stream().map(o -> part(o.kind())).distinct().collect(java.util.stream.Collectors.joining(" + "))));
    private final JTable origins = Hints.of(new JTable(model), "Chunks in generated order. Select a row to jump to its code and read its complete source location below.");
    private final JPanel originPanel = new JPanel(new BorderLayout(2, 2));
    private final JSplitPane split;
    private RenderedSource raw = new RenderedSource("", List.of(), false);
    private RenderedSource displayed = raw;
    private List<Entry> entries = List.of();
    private List<Integer> colors = List.of();
    private boolean synchronizing, working, closing;
    private long revision;
    private CompletableFuture<CodeFormatter.Result> pending = CompletableFuture.completedFuture(null);

    CandidateCodeView() {
        code.setEditable(false);
        code.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        code.setTabSize(4);
        details.setEditable(false);
        details.setLineWrap(true);
        details.setWrapStyleWord(true);
        origins.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        origins.setAutoCreateRowSorter(false); // the sequence is meaningful, including repeated and optional actions
        origins.setFillsViewportHeight(true);
        origins.getColumnModel().getColumn(0).setPreferredWidth(45);
        origins.getColumnModel().getColumn(0).setMaxWidth(60);
        origins.getColumnModel().getColumn(1).setPreferredWidth(55);
        origins.getColumnModel().getColumn(1).setMaxWidth(80);
        origins.getColumnModel().getColumn(2).setPreferredWidth(250);
        origins.getColumnModel().getColumn(3).setPreferredWidth(110);
        origins.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public java.awt.Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                                              boolean focused, int row, int column) {
                // forget the last row's swatch: each row starts from the table's own background, odd rows set apart
                setBackground(null);
                super.getTableCellRendererComponent(table, value, selected, focused, row, column);
                if (!selected && mode.getSelectedItem() == Mode.BACKGROUNDS && row < colors.size()) {
                    setBackground(background(colors.get(row), getForeground()));
                }
                return this;
            }
        });
        // the origin list and the selected origin side by side, so the list keeps several rows in step 5's quarter of the window
        JSplitPane beside = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, TableTitles.titled(origins, "Chunk origins in generated order"),
                FieldNames.titled(details, "Selected chunk origin"));
        beside.setResizeWeight(0.6);
        originPanel.add(beside, BorderLayout.CENTER);
        split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, FieldNames.titled(code, "Code of the selected test case"), originPanel);
        split.setResizeWeight(0.6);
        // the first divider follows these sizes, not the code's length; extra height then goes 60:40
        code.getParent().getParent().setPreferredSize(new Dimension(420, 230));
        originPanel.setPreferredSize(new Dimension(420, 160));
        code.getParent().getParent().setMinimumSize(new Dimension(120, 80));
        originPanel.setMinimumSize(new Dimension(120, 80));
        JPanel toolbar = WrapLayout.row();
        toolbar.add(FieldNames.label("Code language:", language)); toolbar.add(language);
        toolbar.add(format); toolbar.add(original);
        toolbar.add(javax.swing.Box.createHorizontalStrut(12));
        toolbar.add(FieldNames.label("Origins:", mode)); toolbar.add(mode);
        panel.add(toolbar, BorderLayout.NORTH); panel.add(split, BorderLayout.CENTER); panel.add(status, BorderLayout.SOUTH);
        panel.setMinimumSize(new Dimension(260, 180));
        status.setMinimumSize(new Dimension(0, 0));
        format.addActionListener(e -> format());
        original.addActionListener(e -> original());
        mode.addActionListener(e -> paintOrigins());
        origins.getSelectionModel().addListSelectionListener(e -> {
            if (synchronizing || e.getValueIsAdjusting()) return;
            int selected = origins.getSelectedRow();
            if (selected < 0 || selected >= entries.size()) return;
            synchronizing = true;
            try {
                code.setCaretPosition(entries.get(selected).start());
                details.setText(describe(entries.get(selected)));
                details.setCaretPosition(0);
            } finally { synchronizing = false; }
        });
        code.addCaretListener(e -> selectOrigin(e.getDot()));
        code.addPropertyChangeListener("UI", e -> paintOrigins());
        buttons();
    }

    JComponent component() { return panel; }

    void show(RenderedSource source) {
        revision++;
        raw = source == null ? new RenderedSource("", List.of(), false) : source;
        display(raw);
        message(raw.truncated() ? "Original · preview truncated; formatting is unavailable."
                : raw.text().isEmpty() ? "Select a test case to inspect its code and origins."
                : "Original · " + entries.size() + " chunks. Select code or an origin row for its source.");
        buttons();
    }

    CompletableFuture<CodeFormatter.Result> format() {
        if (closing || working || raw.text().isBlank() || raw.truncated()) return pending;
        long request = ++revision;
        RenderedSource input = raw;
        CodeFormatter.Language selected = (CodeFormatter.Language) language.getSelectedItem();
        working = true;
        message("Formatting preview…"); buttons();
        pending = Background.run("code-format", () -> CodeFormatter.format(input, selected), result -> {
            working = false;
            if (!closing && request == revision) {
                display(result.source());
                message("Formatted " + result.language() + " preview · execution uses the original source.");
            }
            buttons();
        }, failure -> {
            working = false;
            if (!closing && request == revision) {
                message(failure instanceof IllegalArgumentException ? failure.getMessage() : Background.describe(failure).strip());
                details.setText(status.getText());
                details.setCaretPosition(0);
            }
            buttons();
        });
        return pending;
    }

    void original() {
        revision++;
        display(raw);
        message(raw.truncated() ? "Original · preview truncated; formatting is unavailable." : "Original generated source.");
        buttons();
    }

    CompletableFuture<Void> close() {
        closing = true; revision++; buttons();
        return pending.handle((result, failure) -> null);
    }

    private void display(RenderedSource source) {
        synchronizing = true;
        try {
            displayed = source;
            var grouped = new LinkedHashMap<List<RenderedSource.Origin>, List<RenderedSource.Chunk>>();
            for (var chunk : source.chunks()) {
                if (!chunk.origins().isEmpty()) grouped.computeIfAbsent(chunk.origins(), ignored -> new ArrayList<>()).add(chunk);
            }
            List<Entry> next = new ArrayList<>();
            grouped.forEach((key, spans) -> next.add(new Entry(next.size() + 1, key, List.copyOf(spans))));
            entries = List.copyOf(next);
            model.show(entries);
            code.setText(source.text());
            code.setCaretPosition(0);
            paintOrigins();
        } finally { synchronizing = false; }
        selectOrigin(0);
    }

    private void paintOrigins() {
        code.getHighlighter().removeAllHighlights();
        boolean list = mode.getSelectedItem() != Mode.CODE;
        if (originPanel.isVisible() != list) {
            originPanel.setVisible(list);
            split.setDividerSize(list ? 8 : 0);
            split.setDividerLocation(list ? 0.6 : 1.0);
        }
        origins.repaint();
        if (mode.getSelectedItem() != Mode.BACKGROUNDS) return;
        var palette = new LinkedHashMap<String, Integer>();
        var indices = new ArrayList<Integer>();
        for (Entry entry : entries) {
            // Same input cell has the same color even when the action is repeated at another position.
            String key = entry.origins().stream().map(o -> o.sheet() + "|" + o.label() + "|" + o.kind() + "|" + o.location())
                    .collect(java.util.stream.Collectors.joining(";"));
            int color = palette.computeIfAbsent(key, ignored -> palette.size());
            indices.add(color);
            for (var span : entry.spans()) {
                try { code.getHighlighter().addHighlight(span.start(), span.end(),
                        new DefaultHighlighter.DefaultHighlightPainter(background(color, code.getForeground()))); }
                catch (BadLocationException e) { throw new IllegalStateException("invalid code origin span", e); }
            }
        }
        colors = List.copyOf(indices);
    }

    /** Muted colors with at least 4.5:1 text contrast, including dark/high-contrast Swing themes. */
    static Color background(int index, Color foreground) {
        int[] light = {0xe5eaf0, 0xeee7df, 0xe3ece5, 0xebe5ef, 0xe0ebec, 0xeee2e3};
        int[] dark = {0x303941, 0x403a32, 0x303e38, 0x3c3543, 0x2d3e42, 0x433536};
        boolean lightBackground = contrast(foreground, Color.WHITE) >= contrast(foreground, Color.BLACK);
        Color color = new Color((lightBackground ? light : dark)[Math.floorMod(index, light.length)]);
        while (contrast(foreground, color) < 4.5) {
            int step = lightBackground ? 8 : -8;
            color = new Color(Math.clamp(color.getRed() + step, 0, 255), Math.clamp(color.getGreen() + step, 0, 255),
                    Math.clamp(color.getBlue() + step, 0, 255));
        }
        return color;
    }

    static double contrast(Color a, Color b) {
        double x = luminance(a), y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    private static double luminance(Color c) {
        double[] rgb = {c.getRed() / 255.0, c.getGreen() / 255.0, c.getBlue() / 255.0};
        for (int i = 0; i < rgb.length; i++) rgb[i] = rgb[i] <= 0.04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + 0.055) / 1.055, 2.4);
        return .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2];
    }

    private int entryAt(int offset) {
        for (int i = 0; i < entries.size(); i++) {
            for (var span : entries.get(i).spans()) if (span.start() <= offset && offset < span.end()) return i;
        }
        return -1;
    }

    private void selectOrigin(int offset) {
        if (synchronizing) return;
        synchronizing = true;
        try {
            int index = entryAt(offset);
            if (index < 0) {
                origins.clearSelection();
                details.setText(raw.truncated() && offset >= raw.text().length() - 1 ? "Preview limit reached. The remaining code is not displayed."
                        : displayed == raw ? "No source location at this position." : "Layout whitespace introduced by the formatter.");
            } else {
                origins.setRowSelectionInterval(index, index);
                origins.scrollRectToVisible(origins.getCellRect(index, 0, true));
                details.setText(describe(entries.get(index)));
            }
            details.setCaretPosition(0);
        } finally { synchronizing = false; }
    }

    private String lines(Entry entry) {
        int from = line(entry.start()), to = line(entry.spans().getLast().end() - 1);
        return from == to ? Integer.toString(from) : from + "–" + to;
    }

    private int line(int at) {
        int line = 1;
        for (int i = 0; i < at; i++) if (displayed.text().charAt(i) == '\n') line++;
        return line;
    }

    private static String name(RenderedSource.Origin origin) {
        return origin.sheet() + (origin.label() == null ? "" : " · value " + origin.label())
                + (origin.occurrence() == 0 ? "" : " · position " + origin.occurrence()) + (origin.optional() ? " · FW_Optional" : "");
    }

    private static String describe(Entry entry) {
        return entry.origins().stream().map(o -> "Chunk " + entry.number() + " · " + name(o) + " · " + part(o.kind())
                + (!o.sheet().equals(o.assembledSheet()) ? " · assembled in " + o.assembledSheet() : "")
                + "\n" + o.location() + (o.detail().isEmpty() ? "" : "\n" + o.detail()))
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }

    private static String part(RenderedSource.Kind kind) {
        return switch (kind) { case VALUE -> "value"; case PREFIX -> "sheet prefix";
            case SHEET_SEPARATOR -> "sheet ending / separator"; case VALUE_SEPARATOR -> "between values"; };
    }

    private void message(String text) { status.setText(text); status.setToolTipText(text); }
    private void buttons() {
        format.setEnabled(!closing && !working && !raw.truncated() && !raw.text().isBlank());
        original.setEnabled(!closing && (working || displayed != raw));
        language.setEnabled(!closing && !working);
    }

    // Small observable surface for headless interaction tests.
    String source() { return code.getText(); }
    String status() { return status.getText(); }
    String detail() { return details.getText(); }
    int originCount() { return entries.size(); }
    void language(CodeFormatter.Language value) { language.setSelectedItem(value); }
    void mode(Mode value) { mode.setSelectedItem(value); }
    void selectOriginRow(int row) { origins.setRowSelectionInterval(row, row); }
    JTable originTable() { return origins; }
    JTextArea code() { return code; }
}
