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


package org.ghyura.cf.generation;

import org.ghyura.cf.algebra.LegacyLibrary;
import org.ghyura.cf.algebra.MultisetPermutations;
import org.ghyura.cf.algebra.Operators;
import org.ghyura.cf.algebra.RankedTuples;
import org.ghyura.cf.algebra.TupleCursor;
import org.ghyura.cf.model.key.KeyEncoding;
import org.ghyura.cf.plan.Directive;
import org.ghyura.cf.plan.SeqPlan;
import org.ghyura.cf.plan.SheetPlan;
import org.ghyura.cf.store.KeyDictionary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An independent transliteration of the legacy stage loop for comparison: plain lists of rows, exactly as the
 * SheetWorker and BraceOperationHandler code reads (fw_ and fw2_ lists, the id counters, the later pass reading fw_
 * into a map by id and walking the ids 1..max, explicit loops for the Cartesian products and joiners, list copies for
 * the swaps). It shares nothing with the production stages but the ranked operators of fw-algebra, whose orders are
 * fixed by the legacy jars themselves (LegacyOrderTest).
 */
final class LegacyModel {

    record R(long id, long parent, int[] labels) {
        R withoutParent() {
            return new R(id, 0, labels);
        }
    }

    private final SeqPlan plan;
    private final KeyDictionary dictionary;
    private final LegacyLibrary library;
    private final boolean distinct;
    private final Map<String, List<R>> finals = new LinkedHashMap<>();
    final Map<String, String> consumedBy = new HashMap<>();
    long unparseable;

    LegacyModel(SeqPlan plan, KeyDictionary dictionary, GenerationOptions options) {
        this.plan = plan;
        this.dictionary = dictionary;
        this.library = options.library();
        this.distinct = options.distinctTables();
    }

    Map<String, List<R>> run() {
        List<SheetPlan> active = new ArrayList<>(plan.activeSheets());
        List<SheetPlan> done = new ArrayList<>();
        while (done.size() < active.size()) {
            SheetPlan next = null;
            for (SheetPlan candidate : active) {
                if (!done.contains(candidate) && operandsDone(candidate, active, done)
                        && (next == null || candidate.key() < next.key())) {
                    next = candidate;
                }
            }
            if (next == null) {
                throw new IllegalStateException("circular joiners");
            }
            done.add(next);
            finals.put(next.name(), sheet(next));
        }
        return finals;
    }

    private static boolean operandsDone(SheetPlan sheet, List<SheetPlan> active, List<SheetPlan> done) {
        for (Directive d : sheet.directives()) {
            if (d instanceof Directive.Joiner j) {
                for (Directive.Operand o : List.of(j.first(), j.second())) {
                    if (o.sheet() == null || o.sheet().equals(sheet.name())) {
                        continue;
                    }
                    for (SheetPlan a : active) {
                        if (a.name().equals(o.sheet()) && !done.contains(a)) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private int[] values(String sheet) {
        SheetPlan p = plan.sheet(sheet).orElse(null);
        return p == null || p.virtual() ? new int[0] : dictionary.labelsOf(p.key());
    }

    private Integer firstValue(String sheet) {
        if (sheet.isEmpty()) {
            return null;
        }
        int[] v = values(sheet);
        return v.length == 0 ? null : v[0];
    }

    private List<R> sheet(SheetPlan sheet) {
        List<R> fw = new ArrayList<>();
        List<R> fw2 = new ArrayList<>();
        long[] fwId = {0};
        long[] fwId2 = {0};
        boolean combi2 = false;
        Map<String, String> group = null;
        Integer separator = null;
        List<Directive> ds = sheet.directives();
        for (int i = 0; i < ds.size(); i++) {
            Directive d = ds.get(i);
            boolean generating = true;
            if (d instanceof Directive.Joiner j) {
                joiner(sheet, j, fw, fwId);
                for (R r : fw) {
                    fw2.add(r.withoutParent());
                }
                fw.clear();
                combi2 = true;
            } else if (d instanceof Directive.Group g) {
                group = new LinkedHashMap<>();
                for (Directive.Replacement r : g.replacements()) {
                    if (r.sheetReference() == null) {
                        group.put(r.pattern(), r.prefix());
                    } else if (firstValue(r.sheetReference()) != null) {
                        group.put(r.pattern(), r.prefix() + firstValue(r.sheetReference()) + r.suffix());
                    }
                }
                generating = false;
            } else if (d instanceof Directive.Separator s) {
                separator = firstValue(s.sheet());
                generating = false;
            } else if (i == 0) {
                first(sheet, d, fw, fwId);
                combi2 = false;
            } else {
                if (group != null) {
                    grouped(d, fw, fw2, fwId2, group, separator);
                    group = null;
                } else {
                    later(d, fw, fw2, fwId2, separator);
                }
                combi2 = true;
            }
            if (generating && distinct) {
                distinctify(combi2 ? fw2 : fw);
            }
            if (combi2 && i > 0 && i < ds.size() - 1) {
                List<R> moved = new ArrayList<>();
                for (R r : fw2) {
                    moved.add(r.withoutParent());
                }
                fw = moved;
                fw2 = new ArrayList<>();
                combi2 = false;
            }
        }
        List<R> result = combi2 ? fw2 : fw;
        if (distinct) {
            distinctify(result);
        }
        return result;
    }

    private void distinctify(List<R> table) {
        Map<List<Integer>, R> first = new LinkedHashMap<>();
        for (R r : table) {
            first.putIfAbsent(Arrays.stream(r.labels()).boxed().toList(), r);
        }
        table.clear();
        table.addAll(first.values());
    }

    private static List<int[]> all(RankedTuples tuples) {
        List<int[]> out = new ArrayList<>();
        try (TupleCursor c = tuples.cursor()) {
            while (c.hasNext()) {
                out.add(c.next());
            }
        }
        return out;
    }

    private static int[] pick(int[] source, int[] tuple) {
        int[] out = new int[tuple.length];
        for (int i = 0; i < tuple.length; i++) {
            out[i] = source[tuple[i]];
        }
        return out;
    }

    private void first(SheetPlan sheet, Directive d, List<R> fw, long[] fwId) {
        int[] v = values(sheet.name());
        int n = v.length;
        List<int[]> rows = new ArrayList<>();
        // SheetWorker.runDirective: the (all) forms loop m = 1..n before anything else; buildDirectiveGenerator: a
        // sheet missing from the values map (a data sheet without cells; a virtual sheet has an empty list) gets
        // CombinatorialGenerator.subsets(emptyList), whatever the verb
        boolean all = d instanceof Directive.Combinatoric c && c.count().kind() == Directive.CountKind.ALL
                && c.verb() != Directive.Verb.PERMUT;
        if (!sheet.virtual() && sheet.valueCount() == 0 && !all) {
            for (int[] tuple : all(Operators.subsets(0))) {
                fw.add(new R(++fwId[0], 0, tuple));
            }
            return;
        }
        switch (d) {
            case Directive.Combinatoric c -> {
                if (c.verb() == Directive.Verb.PERMUT) {
                    int[] classes = MultisetPermutations.classesOf(Arrays.stream(v).boxed().toList());
                    RankedTuples t = c.duplicatesIdentical() ? Operators.distinctArrangements(classes) : Operators.permutations(classes, library);
                    for (int[] tuple : all(t)) {
                        rows.add(pick(v, tuple));
                    }
                } else if (c.count().kind() == Directive.CountKind.ALL) {
                    for (int m = 1; m <= n; m++) {
                        rows.addAll(verbRows(c.verb(), v, m));
                    }
                } else {
                    int m = c.count().kind() == Directive.CountKind.SIZE ? n
                            : c.count().kind() == Directive.CountKind.EXACT ? c.count().value() : 1;
                    rows.addAll(verbRows(c.verb(), v, m));
                }
            }
            case Directive.Subsets s -> {
                if (s.mode() == Directive.SubsetMode.DEFAULT || s.sizes().isEmpty()) {
                    for (int[] tuple : all(Operators.subsets(n))) {
                        rows.add(pick(v, tuple));
                    }
                } else {
                    for (int size = 0; size <= n; size++) {
                        List<Integer> p = s.sizes();
                        boolean match = switch (s.mode()) {
                            case BEFORE -> size < p.get(0);
                            case AFTER -> size > p.get(0);
                            case EXACT -> size == p.get(0);
                            case RANGE -> size >= p.get(0) && size <= p.get(1);
                            case GIVEN -> p.contains(size);
                            case DEFAULT -> true;
                        };
                        if (match) {
                            for (int[] tuple : all(Operators.combinations(n, size, library))) {
                                rows.add(pick(v, tuple));
                            }
                        }
                    }
                }
            }
            case Directive.Cartes c -> {
                int[] other = values(c.sheet());
                if (c.first()) {
                    for (int x : other) {
                        for (int y : v) {
                            rows.add(new int[]{x, y});
                        }
                    }
                } else {
                    for (int y : v) {
                        for (int x : other) {
                            rows.add(new int[]{y, x});
                        }
                    }
                }
            }
            default -> throw new IllegalStateException(d.toString());
        }
        for (int[] row : rows) {
            fw.add(new R(++fwId[0], 0, row));
        }
    }

    /** SheetWorker.buildGenerator: an impossible size returns CombinatorialGenerator.subsets(emptyList). */
    private List<int[]> verbRows(Directive.Verb verb, int[] v, int m) {
        int n = v.length;
        boolean impossible = switch (verb) {
            case COMBI -> m < 0 || m > n;
            case COMBI_R, PERMUT_R -> m < 0;
            case PERMUT -> throw new IllegalStateException();
        };
        RankedTuples t = impossible ? Operators.subsets(0) : switch (verb) {
            case COMBI -> Operators.combinations(n, m, library);
            case COMBI_R -> Operators.combinationsWithRepetition(n, m, library);
            case PERMUT_R -> Operators.permutationsWithRepetition(n, m, library);
            case PERMUT -> throw new IllegalStateException();
        };
        List<int[]> rows = new ArrayList<>();
        for (int[] tuple : all(t)) {
            rows.add(pick(v, tuple));
        }
        return rows;
    }

    private static Map<Long, R> byId(List<R> fw) {
        Map<Long, R> map = new HashMap<>();
        for (R r : fw) {
            map.put(r.id(), r);
        }
        return map;
    }

    private void later(Directive d, List<R> fw, List<R> fw2, long[] fwId2, Integer separator) {
        Map<Long, R> byId = byId(fw);
        long maxId = 0;
        for (R r : fw) {
            maxId = Math.max(maxId, r.id());
        }
        for (long j = 1; j <= maxId; j++) {
            R r = byId.get(j);
            if (r == null || r.labels().length == 0) {
                continue;
            }
            int[] in = r.labels();
            List<int[]> results = new ArrayList<>();
            switch (d) {
                case Directive.Combinatoric c -> {
                    if (c.verb() == Directive.Verb.PERMUT) {
                        for (int[] tuple : all(Operators.permutations(MultisetPermutations.classesOf(Arrays.stream(in).boxed().toList()), library))) {
                            results.add(pick(in, tuple));
                        }
                    } else {
                        List<Integer> ms = new ArrayList<>();
                        switch (c.count().kind()) {
                            case SIZE -> ms.add(in.length);
                            case ALL -> {
                                for (int t = 1; t <= in.length; t++) {
                                    ms.add(t);
                                }
                            }
                            case EXACT -> ms.add(Math.max(1, c.count().value()));
                            case DEFAULT -> ms.add(1);
                        }
                        for (int m : ms) {
                            results.addAll(verbRows(c.verb(), in, m));
                        }
                    }
                }
                case Directive.Subsets s -> {
                    for (int[] tuple : all(Operators.subsets(in.length))) {
                        results.add(pick(in, tuple));
                    }
                }
                case Directive.Cartes c -> {
                    int[] other = values(c.sheet());
                    if (c.first()) {
                        for (int x : other) {
                            for (int y : in) {
                                results.add(new int[]{x, y});
                            }
                        }
                    } else {
                        for (int y : in) {
                            for (int x : other) {
                                results.add(new int[]{y, x});
                            }
                        }
                    }
                }
                default -> throw new IllegalStateException(d.toString());
            }
            for (int[] w : results) {
                int[] combo = w;
                if (separator != null) {
                    if (w.length == 0) {
                        continue;
                    }
                    combo = new int[w.length * 2 - 1];
                    for (int i = 0; i < w.length; i++) {
                        combo[i * 2] = w[i];
                        if (i < w.length - 1) {
                            combo[i * 2 + 1] = separator;
                        }
                    }
                }
                fw2.add(new R(++fwId2[0], j, combo));
            }
        }
    }

    private void grouped(Directive d, List<R> fw, List<R> fw2, long[] fwId2, Map<String, String> group, Integer separator) {
        List<int[]> units = new ArrayList<>();
        for (R r : byId(fw).values()) {
            units.add(r.labels());
        }
        units.sort((a, b) -> {
            for (int i = 0; i < Math.min(a.length, b.length); i++) {
                int c = Integer.compare(a[i], b[i]);
                if (c != 0) {
                    return c;
                }
            }
            return Integer.compare(a.length, b.length);
        });
        if (units.isEmpty()) {
            return;
        }
        int n = units.size();
        List<Integer> ms = new ArrayList<>();
        if (d instanceof Directive.Combinatoric c) {
            switch (c.count().kind()) {
                case SIZE -> ms.add(n);
                case ALL -> {
                    if (c.verb() == Directive.Verb.PERMUT) {
                        ms.add(1);
                    } else {
                        for (int t = 1; t <= n; t++) {
                            ms.add(t);
                        }
                    }
                }
                case EXACT -> ms.add(Math.max(1, c.count().value()));
                case DEFAULT -> ms.add(1);
            }
        } else {
            ms.add(1);
        }
        KeyEncoding encoding = dictionary.encoding();
        for (int m : ms) {
            List<String> texts = new ArrayList<>();
            switch (d) {
                case Directive.Combinatoric c -> {
                    if (c.verb() == Directive.Verb.COMBI && m > n) {
                        continue;
                    }
                    int[] identity = new int[n];
                    for (int i = 0; i < n; i++) {
                        identity[i] = i;
                    }
                    RankedTuples t = switch (c.verb()) {
                        case COMBI -> Operators.combinations(n, m, LegacyLibrary.COMBINATORICSLIB3);
                        case COMBI_R -> Operators.combinationsWithRepetition(n, m, LegacyLibrary.COMBINATORICSLIB3);
                        case PERMUT -> Operators.permutations(identity, LegacyLibrary.COMBINATORICSLIB3);
                        case PERMUT_R -> Operators.permutationsWithRepetition(n, m, LegacyLibrary.COMBINATORICSLIB3);
                    };
                    for (int[] tuple : all(t)) {
                        List<String> parts = new ArrayList<>();
                        for (int index : tuple) {
                            parts.add(Arrays.toString(units.get(index)));
                        }
                        texts.add(parts.toString());
                    }
                }
                case Directive.Subsets s -> {
                    for (int[] tuple : all(Operators.subsets(n))) {
                        List<String> parts = new ArrayList<>();
                        for (int index : tuple) {
                            parts.add(Arrays.toString(units.get(index)));
                        }
                        texts.add(parts.toString());
                    }
                }
                case Directive.Cartes c -> {
                    int[] operand = values(c.sheet());
                    if (operand.length == 0) {
                        continue;
                    }
                    if (c.first()) {
                        for (int x : operand) {
                            for (int[] u : units) {
                                texts.add("[" + x + ", " + Arrays.toString(u) + "]");
                            }
                        }
                    } else {
                        for (int[] u : units) {
                            for (int x : operand) {
                                texts.add("[" + Arrays.toString(u) + ", " + x + "]");
                            }
                        }
                    }
                }
                default -> throw new IllegalStateException(d.toString());
            }
            for (String text : texts) {
                String cur = text;
                for (Map.Entry<String, String> e : group.entrySet()) {
                    cur = cur.replaceAll(e.getKey(), e.getValue());
                }
                if (separator != null) {
                    cur = cur.replaceAll(", ", ", " + separator + ", ");
                }
                List<Integer> labels = new ArrayList<>();
                boolean bad = false;
                for (String part : cur.replaceAll("[{}\\[\\]]", "").split(", ")) {
                    String p = part.trim();
                    if (p.isEmpty()) {
                        continue;
                    }
                    try {
                        int label = Integer.parseInt(p);
                        if (!encoding.accepts(label)) {
                            bad = true;
                            break;
                        }
                        labels.add(label);
                    } catch (NumberFormatException e) {
                        bad = true;
                        break;
                    }
                }
                if (bad) {
                    unparseable++;
                    continue;
                }
                if (labels.isEmpty()) {
                    continue;
                }
                fw2.add(new R(++fwId2[0], 0, labels.stream().mapToInt(Integer::intValue).toArray()));
            }
        }
    }

    private List<int[]> operand(Directive.Operand o, String reader) {
        if (o.sheet() == null) {
            return List.of();
        }
        List<R> table = finals.get(o.sheet());
        if (table == null) {
            return List.of();
        }
        String consumer = consumedBy.get(o.sheet());
        boolean reuse = plan.sheet(o.sheet()).map(SheetPlan::reuse).orElse(false);
        if (consumer != null && !reuse) {
            return List.of();
        }
        List<int[]> rows = new ArrayList<>();
        for (R r : table) {
            rows.add(r.labels());
        }
        if (o.kind() == Directive.OperandKind.NESTED_GROUPED && !rows.isEmpty()) {
            List<Integer> all = new ArrayList<>();
            for (int[] r : rows) {
                for (int x : r) {
                    all.add(x);
                }
            }
            return List.<int[]>of(all.stream().mapToInt(Integer::intValue).toArray());
        }
        return rows;
    }

    private void joiner(SheetPlan sheet, Directive.Joiner j, List<R> fw, long[] fwId) {
        List<int[]> a = operand(j.first(), sheet.name());
        List<int[]> b = operand(j.second(), sheet.name());
        for (Directive.Operand o : List.of(j.first(), j.second())) {
            if (o.sheet() != null) {
                consumedBy.putIfAbsent(o.sheet(), sheet.name());
            }
        }
        Integer rel = firstValue(j.relation());
        Integer sep = firstValue(j.separator());
        Integer start = firstValue(j.start());
        Integer end = firstValue(j.end());
        List<List<Integer>> bodies = new ArrayList<>();
        switch (j.multiplicity()) {
            case ONE_TO_ONE -> {
                for (int[] ra : a) {
                    for (int[] rb : b) {
                        if (ra.length == rb.length) {
                            List<Integer> alt = new ArrayList<>();
                            for (int i = 0; i < Math.max(ra.length, rb.length); i++) {
                                if (i < ra.length) {
                                    alt.add(ra[i]);
                                }
                                if (i < rb.length) {
                                    alt.add(rb[i]);
                                }
                            }
                            List<Integer> out = new ArrayList<>();
                            for (int k = 0; k < alt.size(); k++) {
                                out.add(alt.get(k));
                                if (k % 2 == 0) {
                                    if (rel != null) {
                                        out.add(rel);
                                    }
                                } else if (k != alt.size() - 1 && sep != null) {
                                    out.add(sep);
                                }
                            }
                            bodies.add(out);
                        }
                    }
                }
            }
            case ONE_TO_N -> {
                for (int[] ra : a) {
                    for (int[] rb : b) {
                        List<Integer> out = new ArrayList<>();
                        for (int i = 0; i < ra.length; i++) {
                            out.add(ra[i]);
                            if (rel != null) {
                                out.add(rel);
                            }
                            for (int x : rb) {
                                out.add(x);
                            }
                            if (sep != null && i < ra.length - 1) {
                                out.add(sep);
                            }
                        }
                        bodies.add(out);
                    }
                }
            }
            case M_TO_M, M_TO_N -> {
                for (int[] ra : a) {
                    for (int[] rb : b) {
                        if (j.multiplicity() == Directive.Multiplicity.M_TO_M && ra.length != rb.length) {
                            continue;
                        }
                        List<Integer> out = new ArrayList<>();
                        for (int x : ra) {
                            out.add(x);
                        }
                        if (rel != null) {
                            out.add(rel);
                        }
                        for (int x : rb) {
                            out.add(x);
                        }
                        bodies.add(out);
                    }
                }
            }
            case M_TO_ONE -> {
                for (int[] rb : b) {
                    for (int[] ra : a) {
                        List<Integer> out = new ArrayList<>();
                        for (int i = 0; i < rb.length; i++) {
                            for (int x : ra) {
                                out.add(x);
                            }
                            if (rel != null) {
                                out.add(rel);
                            }
                            out.add(rb[i]);
                            if (sep != null && i < rb.length - 1) {
                                out.add(sep);
                            }
                        }
                        bodies.add(out);
                    }
                }
            }
        }
        for (List<Integer> body : bodies) {
            List<Integer> row = new ArrayList<>();
            if (start != null) {
                row.add(start);
            }
            row.addAll(body);
            if (end != null) {
                row.add(end);
            }
            if (!row.isEmpty()) {
                fw.add(new R(++fwId[0], 0, row.stream().mapToInt(Integer::intValue).toArray()));
            }
        }
    }
}
