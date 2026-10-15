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

package org.ghyura.cf.fixtures.demos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Closed seven-class D10 offline analysis. Every measured value comes from the complete observed matrix.
 * Public retained inputs/outputs are caller-owned under the 0064 admission contract. */
public final class SensorDiagnosis {
    private SensorDiagnosis() { }
    public static final String HEALTHY_SOURCE="declared healthy reference (DB-free control), not a Framework observation";
    public record Observed(String caseId,String mask,String fault,String verdict,List<Byte> readings) {
        public Observed { readings=List.copyOf(readings); }
    }
    public record DiagnosisClass(int index,int code,List<Byte> signature,List<String> members,String source) {
        public DiagnosisClass { signature=List.copyOf(signature); members=List.copyOf(members); }
    }
    public record Distance(int first,int second,int value) { }
    public record Suite(String mask,int cost,List<String> signatures,List<Distance> distances,int minDistance,boolean detect,boolean separate,boolean erasure,boolean error) {
        public Suite { signatures=List.copyOf(signatures); distances=List.copyOf(distances); }
        boolean meets(String goal) { return switch (goal) { case "detect" -> detect; case "separate" -> separate; case "erasure" -> erasure; case "error" -> error; default -> throw new IllegalArgumentException(); }; }
    }
    public record Winner(String mask,int cost,int feasibleCount,List<String> minimumCostMasks) { public Winner { minimumCostMasks=List.copyOf(minimumCostMasks); } }
    public record Belief(List<Integer> classes,int cost,Integer sensor) { public Belief { classes=List.copyOf(classes); } }
    public record Node(int cost,Integer sensor,Integer diagnosis,Node zero,Node one) { }
    public record Step(int sensor,byte reading) { }
    public record DiagnosisPath(int diagnosis,int cost,List<Step> steps) { public DiagnosisPath { steps=List.copyOf(steps); } }
    public record Adaptive(Node tree,List<Belief> dp,List<DiagnosisPath> paths) { public Adaptive { dp=List.copyOf(dp); paths=List.copyOf(paths); } }
    public record Noise(int diagnosis,int position,String observed,List<Integer> decoded) { public Noise { decoded=List.copyOf(decoded); } }
    public record Identification(int diagnosis,List<String> members) { public Identification { members=List.copyOf(members); } }
    public record Analysis(List<DiagnosisClass> classes,List<Suite> suites,Map<String,Winner> winners,Adaptive adaptive,Map<String,List<Noise>> noise,int unquotientedLabelMinDistance,Map<String,Identification> identifyExamples) {
        public Analysis { classes=List.copyOf(classes); suites=List.copyOf(suites); winners=Map.copyOf(winners); noise=Map.copyOf(noise); identifyExamples=Map.copyOf(identifyExamples); }
    }
    public static List<Byte> parseReadings(String value) {
        if (value==null||!value.matches("\\[(?:[01](?:, [01])*)?\\]")) throw new IllegalArgumentException("binary readings list required");
        if (value.equals("[]")) return List.of(); return java.util.Arrays.stream(value.substring(1,value.length()-1).split(", ")).map(Byte::valueOf).toList();
    }
    private static List<Byte> projection(List<Byte> full,List<Integer> selected) { return selected.stream().map(full::get).toList(); }
    private static int distance(String a,String b) { if (a.length()!=b.length()) throw new IllegalArgumentException("signature length mismatch"); int d=0; for (int i=0;i<a.length();i++) if (a.charAt(i)!=b.charAt(i)) d++; return d; }
    public static Identification identify(List<Byte> signature,List<DiagnosisClass> classes) {
        for (var c : classes) if (c.signature().equals(signature)) return new Identification(c.index(),c.members()); return null;
    }
    public static Analysis analyse(List<Observed> records) {
        var seen=new TreeMap<String,Observed>();
        for (var o : records) {
            if (o.mask()==null||!o.mask().matches("[01]{8}")||!DiagnosticSensors.LABELS.contains(o.fault())||!("S="+o.mask()+"|H="+o.fault()).equals(o.caseId())) throw new IllegalArgumentException("unknown or conflicting identity "+o.caseId());
            if (!"PASS".equals(o.verdict())) throw new IllegalArgumentException("non-PASS observation "+o.caseId());
            if (o.readings().size()!=DiagnosticSensors.selected(o.mask()).size()) throw new IllegalArgumentException("wrong reading count "+o.caseId());
            DiagnosticSensors.signature(o.readings()); if (seen.putIfAbsent(o.caseId(),o)!=null) throw new IllegalArgumentException("duplicate observation "+o.caseId());
        }
        if (seen.size()!=3072) throw new IllegalArgumentException("complete 256 x 12 observations required");
        var full=new LinkedHashMap<String,List<Byte>>(); for (var fault : DiagnosticSensors.LABELS) full.put(fault,seen.get("S=11111111|H="+fault).readings());
        for (var o : seen.values()) if (!o.readings().equals(projection(full.get(o.fault()),DiagnosticSensors.selected(o.mask())))) throw new IllegalArgumentException("readings differ from observed full-signature projection "+o.caseId());
        var groups=new LinkedHashMap<List<Byte>,List<String>>(); for (var fault : DiagnosticSensors.LABELS) groups.computeIfAbsent(full.get(fault),ignored -> new ArrayList<>()).add(fault);
        var zero=java.util.Collections.nCopies(8,(byte)0);
        if (groups.size()!=6||groups.containsKey(zero)) throw new IllegalArgumentException("D10 needs six measured classes distinct from declared healthy reference");
        var classes=new ArrayList<DiagnosisClass>(); classes.add(new DiagnosisClass(0,0,zero,List.of("H0"),HEALTHY_SOURCE));
        for (var g : groups.entrySet()) { var s=g.getKey(); int code=s.get(0)|(s.get(1)<<1)|(s.get(2)<<2)|(s.get(3)<<3); classes.add(new DiagnosisClass(classes.size(),code,s,g.getValue(),"inferred from observed full-mask signatures")); }
        var suites=new ArrayList<Suite>();
        for (int i=0;i<256;i++) {
            String mask="%8s".formatted(Integer.toBinaryString(i)).replace(' ','0'); var selected=DiagnosticSensors.selected(mask); var words=classes.stream().map(c -> DiagnosticSensors.signature(projection(c.signature(),selected))).toList();
            var distances=new ArrayList<Distance>(); for (int a=0;a<7;a++) for (int b=a+1;b<7;b++) distances.add(new Distance(a,b,distance(words.get(a),words.get(b))));
            int min=distances.stream().mapToInt(Distance::value).min().orElseThrow(); suites.add(new Suite(mask,selected.size(),words,distances,min,words.subList(1,7).stream().noneMatch(words.getFirst()::equals),min>=1,min>=2,min>=3));
        }
        var winners=new LinkedHashMap<String,Winner>();
        for (var goal : List.of("detect","separate","erasure","error")) {
            var feasible=suites.stream().filter(s -> s.meets(goal)).sorted(Comparator.comparingInt(Suite::cost).thenComparing(Suite::mask)).toList();
            if (feasible.isEmpty()) throw new IllegalArgumentException("observed signatures do not support "+goal); var best=feasible.getFirst(); winners.put(goal,new Winner(best.mask(),best.cost(),feasible.size(),feasible.stream().filter(s -> s.cost()==best.cost()).map(Suite::mask).toList()));
        }
        var noise=new LinkedHashMap<String,List<Noise>>(); for (var goal : List.of("erasure","error")) noise.put(goal,noise(suites.stream().filter(s -> s.mask().equals(winners.get(goal).mask())).findFirst().orElseThrow().signatures(),goal));
        int labelDistance=8; for (int i=0;i<12;i++) for (int j=i+1;j<12;j++) labelDistance=Math.min(labelDistance,distance(DiagnosticSensors.signature(full.get(DiagnosticSensors.LABELS.get(i))),DiagnosticSensors.signature(full.get(DiagnosticSensors.LABELS.get(j)))));
        var examples=new LinkedHashMap<String,Identification>(); for (var fault : DiagnosticSensors.LABELS) examples.put(fault,identify(full.get(fault),classes));
        return new Analysis(classes,suites,winners,adaptive(classes),noise,labelDistance,examples);
    }
    private static List<Noise> noise(List<String> words,String kind) {
        var rows=new ArrayList<Noise>();
        for (int c=0;c<7;c++) for (int pos=-1;pos<words.get(c).length();pos++) {
            var obs=new StringBuilder(words.get(c)); if (pos>=0) obs.setCharAt(pos,kind.equals("erasure")?'?':obs.charAt(pos)=='0'?'1':'0'); var keep=new ArrayList<Integer>();
            for (int other=0;other<7;other++) {
                boolean match=true; if (kind.equals("erasure")) { for (int k=0;k<obs.length();k++) if (obs.charAt(k)!='?'&&obs.charAt(k)!=words.get(other).charAt(k)) match=false; }
                else match=distance(obs.toString(),words.get(other))<=1; if (match) keep.add(other);
            }
            if (!keep.equals(List.of(c))) throw new IllegalArgumentException("observed suite cannot uniquely decode "+kind); rows.add(new Noise(c,pos,obs.toString(),keep));
        }
        return List.copyOf(rows);
    }
    private static final class Program {
        final List<DiagnosisClass> classes; final int[] costs=new int[128],sensors=new int[128];
        Program(List<DiagnosisClass> classes) { this.classes=classes; java.util.Arrays.fill(costs,-1); java.util.Arrays.fill(sensors,-1); }
        int part(int belief,int sensor,int reading) { int bits=0; for (int c=0;c<7;c++) if ((belief&(1<<(6-c)))!=0&&classes.get(c).signature().get(sensor)==reading) bits|=1<<(6-c); return bits; }
        int best(int belief) {
            if (costs[belief]>=0) return costs[belief]; if (Integer.bitCount(belief)==1) return costs[belief]=0;
            int cost=99,sensor=-1; for (int s=0;s<8;s++) { int zero=part(belief,s,0),one=part(belief,s,1); if (zero==0||one==0) continue; int proposed=1+Math.max(best(zero),best(one)); if (proposed<cost) { cost=proposed; sensor=s; } }
            if (sensor<0) throw new IllegalArgumentException("inseparable observed belief"); sensors[belief]=sensor; return costs[belief]=cost;
        }
        Node tree(int belief) { int cost=best(belief),s=sensors[belief]; if (s<0) return new Node(0,null,6-Integer.numberOfTrailingZeros(belief),null,null); return new Node(cost,s,null,tree(part(belief,s,0)),tree(part(belief,s,1))); }
    }
    private static Adaptive adaptive(List<DiagnosisClass> classes) {
        var program=new Program(classes); var dp=new ArrayList<Belief>();
        for (int belief=1;belief<128;belief++) { int cost=program.best(belief); var members=new ArrayList<Integer>(); for (int c=0;c<7;c++) if ((belief&(1<<(6-c)))!=0) members.add(c); dp.add(new Belief(members,cost,program.sensors[belief]<0?null:program.sensors[belief])); }
        var root=program.tree(127); var paths=new ArrayList<DiagnosisPath>();
        for (int c=0;c<7;c++) { var node=root; var steps=new ArrayList<Step>(); while (node.sensor()!=null) { byte reading=classes.get(c).signature().get(node.sensor()); steps.add(new Step(node.sensor(),reading)); node=reading==0?node.zero():node.one(); } if (node.diagnosis()!=c) throw new IllegalStateException("adaptive leaf mismatch"); paths.add(new DiagnosisPath(c,steps.size(),steps)); }
        return new Adaptive(root,dp,paths);
    }
}
