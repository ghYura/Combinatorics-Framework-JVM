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

import org.ghyura.cf.campaign.RunState;
import org.ghyura.cf.config.ConfigResolver;
import org.ghyura.cf.desktop.session.Session;
import org.ghyura.cf.fixtures.demos.CyclicAnalysis;
import org.ghyura.cf.fixtures.demos.CyclicPatternPlugin;
import org.ghyura.cf.store.MemoryBudget;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** D12 full observed analysis through shared runs. --classes is optional, as in the original analysis.
 * java --class-path fw-app/target/fw-app.jar:fw-sut-fixtures/target/fw-sut-fixtures-0.1.0-SNAPSHOT.jar
 * tools/CyclicPatternReport.java --words PATH/words/spec.toml [--classes PATH/classes/spec.toml] --out proof.xml [--workers 4]
 */
public final class CyclicPatternReport {
    private static String escape(String s) { return s.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;"); }
    private static String join(List<?> values) { return values.stream().map(Object::toString).collect(java.util.stream.Collectors.joining(" ")); }
    private static List<CyclicAnalysis.Observed> run(Session session,Path spec,String campaign) throws Exception {
        var rows=new ArrayList<CyclicAnalysis.Observed>();
        try (var prepared=session.prepareRun(spec)) {
            var run=prepared.handle(); var summary=run.await(); if (summary.state()!=RunState.COMPLETED) throw new IllegalStateException("D12 run failed: "+summary.diagnostics());
            for (var o : run.finals()) {
                var a=o.domain().orElseThrow(() -> new IllegalArgumentException("non-domain D12 observation")).attributes();
                if (!campaign.equals(a.get("campaign"))||!o.outcome().name().equals(a.get("verdict"))) throw new IllegalArgumentException("conflicting D12 campaign/verdict");
                rows.add(new CyclicAnalysis.Observed(a.get("case_id"),a.get("word"),a.get("campaign"),o.outcome().name(),a));
            }
        }
        return rows;
    }
    public static void main(String[] args) throws Exception {
        Path words=null,classes=null,out=null; String workers="1";
        for (int i=0;i<args.length;i+=2) {
            if (i+1==args.length) throw new IllegalArgumentException("option value required");
            switch (args[i]) { case "--words" -> words=Path.of(args[i+1]); case "--classes" -> classes=Path.of(args[i+1]); case "--out" -> out=Path.of(args[i+1]); case "--workers" -> workers=args[i+1]; default -> throw new IllegalArgumentException("unknown option "+args[i]); }
        }
        if (words==null||out==null) throw new IllegalArgumentException("--words and --out required");
        var session=Session.resolve(null,Map.of(),List.of(new ConfigResolver.Override("cf.execution.sut",CyclicPatternPlugin.ID,"D12 report"),new ConfigResolver.Override("cf.execution.workers",workers,"D12 report")));
        var budget=MemoryBudget.of(session.configuration().config());
        try (var owned=budget.reserve(16L*1024*1024,"D12 observed banks, orbit/population analysis and XML")) {
            if (owned.bytes()!=16L*1024*1024) throw new IllegalStateException("invalid D12 report reservation");
            var wordRows=run(session,words,"words"); var classRows=classes==null?null:run(session,classes,"classes"); var r=CyclicAnalysis.analyse(wordRows,classRows);
            var xml=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<cyclic-report schema=\"d12.analysis/v1\" source=\"observed costs of complete campaigns\" word-observations=\"729\" class-observations=\"")
                    .append(classRows==null?0:130).append("\">\n");
            var rows=new ArrayList<>(wordRows); if (classRows!=null) rows.addAll(classRows); rows.sort(java.util.Comparator.comparing(CyclicAnalysis.Observed::campaign).thenComparing(CyclicAnalysis.Observed::word));
            for (var o : rows) {
                xml.append("  <observation id=\"").append(o.caseId()).append("\" campaign=\"").append(o.campaign()).append("\">\n");
                for (var f : new TreeMap<>(o.fields()).entrySet()) xml.append("    <field name=\"").append(escape(f.getKey())).append("\">").append(escape(f.getValue())).append("</field>\n");
                xml.append("  </observation>\n");
            }
            xml.append("  <partition classes=\"").append(r.orbits().size()).append("\" covered-words=\"").append(r.orbits().stream().mapToInt(CyclicAnalysis.Orbit::size).sum())
                    .append("\" disjoint-complete=\"").append(r.disjointComplete()).append("\" burnside-fixed-counts=\"").append(join(r.burnside())).append("\" burnside-classes=\"")
                    .append(r.burnside().stream().mapToInt(Integer::intValue).sum()/6).append("\" self-reflection-classes=\"").append(r.selfReflectionClasses()).append("\" mirror-pairs=\"").append(r.mirrorPairs())
                    .append("\" orbit-size-histogram=\"").append(r.orbitSizeHistogram().entrySet().stream().map(e -> e.getKey()+":"+e.getValue()).collect(java.util.stream.Collectors.joining(" "))).append("\">\n");
            for (var o : r.orbits()) xml.append("    <orbit representative=\"").append(o.representative()).append("\" members=\"").append(join(o.members())).append("\" size=\"").append(o.size())
                    .append("\" period=\"").append(o.period()).append("\" stabilizer-size=\"").append(o.stabilizerSize()).append("\" reflected-representative=\"").append(o.reflectedRepresentative()).append("\" total-cost=\"").append(o.totalCost()).append("\"/>\n");
            xml.append("  </partition>\n");
            for (var p : r.populations().entrySet()) {
                xml.append("  <population name=\"").append(p.getKey()).append("\" label=\"").append(escape(p.getValue().label())).append("\" mean=\"").append(p.getValue().mean()).append("\">\n");
                for (var bin : p.getValue().histogram().entrySet()) xml.append("    <bin cost=\"").append(bin.getKey()).append("\" count=\"").append(bin.getValue()).append("\"/>\n");
                xml.append("  </population>\n");
            }
            if (r.hasClasses()) {
                xml.append("  <cross-campaign representatives-matched=\"").append(r.representativesMatched()).append("\" mismatches=\"").append(join(r.mismatches())).append("\" weighted-histogram-equals-words=\"").append(r.weightedHistogramEqualsWords()).append("\"/>\n");
                xml.append("  <ranking order=\"").append(join(r.ranking())).append("\" best-classes=\"").append(join(r.bestClasses())).append("\" best-labelled-words=\"").append(join(r.bestLabelledWords()))
                        .append("\" best-class-mass=\"").append(r.masses().get("best_class_of_classes")).append("\" best-word-mass=\"").append(r.masses().get("best_words_of_words")).append("\"/>\n");
            }
            xml.append("</cyclic-report>\n"); Files.writeString(out,xml,StandardCharsets.UTF_8);
            System.out.println("D12: 729 words; 130 orbits; "+r.selfReflectionClasses()+" self-reflection classes; "+r.mirrorPairs()+" mirror pairs; means "+r.populations().entrySet().stream().map(e -> e.getKey()+"="+e.getValue().mean()).toList());
        }
    }
}
