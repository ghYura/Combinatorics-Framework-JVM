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

import org.ghyura.cf.fixtures.demos.CoalitionAllocation.Fraction;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Original D12 offline catalogue and population analysis; costs come exclusively from observations. */
public final class CyclicAnalysis {
    public record Observed(String caseId,String word,String campaign,String verdict,Map<String,String> fields) {
        public Observed { fields=Map.copyOf(fields); }
    }
    public record Orbit(String representative,List<String> members,int size,int period,int stabilizerSize,String reflectedRepresentative,BigInteger totalCost) {
        public Orbit { members=List.copyOf(members); }
    }
    public record Population(String label,Map<BigInteger,Integer> histogram,Fraction mean) {
        public Population { histogram=Collections.unmodifiableMap(new TreeMap<>(histogram)); }
    }
    public record Analysis(List<Orbit> orbits,List<Integer> burnside,Map<Integer,Integer> orbitSizeHistogram,boolean disjointComplete,
                           int selfReflectionClasses,int mirrorPairs,Map<String,Population> populations,boolean hasClasses,
                           int representativesMatched,List<String> mismatches,List<String> ranking,List<String> bestClasses,
                           List<String> bestLabelledWords,Map<String,Fraction> masses,boolean weightedHistogramEqualsWords) {
        public Analysis {
            orbits=List.copyOf(orbits); burnside=List.copyOf(burnside); orbitSizeHistogram=Collections.unmodifiableMap(new TreeMap<>(orbitSizeHistogram));
            populations=Collections.unmodifiableMap(new LinkedHashMap<>(populations)); mismatches=List.copyOf(mismatches); ranking=List.copyOf(ranking);
            bestClasses=List.copyOf(bestClasses); bestLabelledWords=List.copyOf(bestLabelledWords); masses=Map.copyOf(masses);
        }
    }
    private CyclicAnalysis() { }
    public static String word(int n) {
        if (n<0||n>=729) throw new IllegalArgumentException("original word index required"); char[] letters=new char[6];
        for (int k=5;k>=0;k--) { letters[k]="ABC".charAt(n%3); n/=3; } return new String(letters);
    }
    private static String rotate(String word,int k) { return word.substring(k)+word.substring(0,k); }
    public static List<String> rotations(String word) {
        CyclicPatterns.validate(word); var values=new TreeSet<String>(); for (int k=0;k<6;k++) values.add(rotate(word,k)); return List.copyOf(values);
    }
    public static List<String> words() { return java.util.stream.IntStream.range(0,729).mapToObj(CyclicAnalysis::word).toList(); }
    public static List<String> representatives() { return words().stream().map(w -> rotations(w).getFirst()).distinct().sorted().toList(); }
    private static Map<String,Observed> load(List<Observed> observations,String campaign,List<String> expected) {
        var expectedSet=new HashSet<>(expected); var rows=new TreeMap<String,Observed>();
        if (observations.size()>expected.size()) throw new IllegalArgumentException("extra or duplicate "+campaign+" observations");
        for (var o : observations) {
            if (!expectedSet.contains(o.word())||!("W="+o.word()).equals(o.caseId())||!campaign.equals(o.campaign())) throw new IllegalArgumentException("unknown or mismatched "+campaign+" identity");
            if (!"PASS".equals(o.verdict())) throw new IllegalArgumentException("non-PASS "+campaign+" observation");
            for (String field : CyclicPatterns.OBSERVED_FIELDS) if (!o.fields().containsKey(field)) throw new IllegalArgumentException("missing observed field "+field);
            if (rows.putIfAbsent(o.word(),o)!=null) throw new IllegalArgumentException("duplicate "+campaign+" observation");
        }
        if (!rows.keySet().equals(expectedSet)) throw new IllegalArgumentException("incomplete "+campaign+" support"); return rows;
    }
    private static BigInteger cost(Observed o) { return new BigInteger(o.fields().get("total_cost")); }
    private static Population population(String label,Map<String,Observed> rows,Map<String,Integer> weights) {
        var histogram=new TreeMap<BigInteger,Integer>(); BigInteger sum=BigInteger.ZERO; int denominator=0;
        for (var entry : weights.entrySet()) {
            BigInteger cost=cost(rows.get(entry.getKey())); int weight=entry.getValue(); histogram.merge(cost,weight,Integer::sum);
            sum=sum.add(cost.multiply(BigInteger.valueOf(weight))); denominator+=weight;
        }
        return new Population(label,histogram,new Fraction(sum,BigInteger.valueOf(denominator)));
    }
    public static Analysis analyse(List<Observed> wordObservations) { return analyse(wordObservations,null); }
    /** Optional classes campaign is independently guarded and cross-checked, without replacing mismatched fields. */
    public static Analysis analyse(List<Observed> wordObservations,List<Observed> classObservations) {
        var allWords=words(); var words=load(wordObservations,"words",allWords); var reps=representatives(); var orbits=new ArrayList<Orbit>();
        var covered=new ArrayList<String>(); var sizeHistogram=new TreeMap<Integer,Integer>(); var mirrors=new HashSet<String>(); int self=0;
        for (String rep : reps) {
            var members=rotations(rep); var costs=new HashSet<BigInteger>(); for (String member : members) costs.add(cost(words.get(member)));
            if (costs.size()!=1) throw new IllegalArgumentException("non-constant observed orbit cost "+rep);
            int period=6; for (int k=1;k<6;k++) if (rotate(rep,k).equals(rep)) { period=k; break; }
            String reflected=rotations(new StringBuilder(rep).reverse().toString()).getFirst();
            orbits.add(new Orbit(rep,members,members.size(),period,6/members.size(),reflected,costs.iterator().next())); covered.addAll(members); sizeHistogram.merge(members.size(),1,Integer::sum);
            if (rep.equals(reflected)) self++; else mirrors.add(rep.compareTo(reflected)<0?rep+"|"+reflected:reflected+"|"+rep);
        }
        var burnside=new ArrayList<Integer>(); for (int k=0;k<6;k++) { final int shift=k; burnside.add((int)allWords.stream().filter(w -> rotate(w,shift).equals(w)).count()); }
        covered.sort(Comparator.naturalOrder()); boolean complete=covered.equals(allWords); var populations=new LinkedHashMap<String,Population>();
        var wordWeights=new TreeMap<String,Integer>(); allWords.forEach(w -> wordWeights.put(w,1)); populations.put("words",population("uniform labelled words (729)",words,wordWeights));
        var mismatches=new ArrayList<String>(); List<String> ranking=List.of(),best=List.of(),bestWords=List.of(); var masses=new LinkedHashMap<String,Fraction>(); boolean weightedEquals=false;
        if (classObservations!=null) {
            var classes=load(classObservations,"classes",reps); var uniform=new TreeMap<String,Integer>(); var weighted=new TreeMap<String,Integer>();
            for (var orbit : orbits) {
                String rep=orbit.representative(); uniform.put(rep,1); weighted.put(rep,orbit.size());
                if (CyclicPatterns.OBSERVED_FIELDS.stream().anyMatch(k -> !classes.get(rep).fields().get(k).equals(words.get(rep).fields().get(k)))) mismatches.add(rep);
            }
            populations.put("classes_uniform",population("uniform rotation classes (130)",classes,uniform));
            populations.put("classes_weighted",population("orbit-weighted classes (weight = distinct orbit size)",classes,weighted));
            ranking=reps.stream().sorted(Comparator.<String,BigInteger>comparing(r -> cost(classes.get(r))).thenComparing(Comparator.naturalOrder())).toList();
            BigInteger minimum=cost(classes.get(ranking.getFirst())); best=ranking.stream().filter(r -> cost(classes.get(r)).equals(minimum)).toList();
            bestWords=best.stream().flatMap(r -> rotations(r).stream()).sorted().toList();
            masses.put("best_class_of_classes",Fraction.of(best.size(),reps.size())); masses.put("best_words_of_words",Fraction.of(bestWords.size(),allWords.size()));
            weightedEquals=populations.get("classes_weighted").histogram().equals(populations.get("words").histogram());
        }
        return new Analysis(orbits,burnside,sizeHistogram,complete,self,mirrors.size(),populations,classObservations!=null,
                classObservations==null?0:reps.size()-mismatches.size(),mismatches,ranking,best,bestWords,masses,weightedEquals);
    }
}
