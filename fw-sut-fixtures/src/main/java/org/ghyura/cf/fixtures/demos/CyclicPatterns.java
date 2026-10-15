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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Original D12 oriented six-letter ring, with a separate pair-count/doubled-string reference. */
public final class CyclicPatterns {
    public static final List<String> COMPARED = List.of("counts", "edge_counts", "transition_cost", "balance_penalty", "total_cost", "representative", "orbit_size", "period", "stabilizer_size");
    public static final List<String> OBSERVED_FIELDS = List.of("counts", "edge_counts", "edge_costs", "transition_cost", "balance_penalty", "total_cost", "representative", "orbit_size", "period", "stabilizer_size");
    public record Core(List<Integer> counts, List<List<Integer>> edgeCounts, int transitionCost, int balancePenalty,
                       int totalCost, String representative, int orbitSize, int period, int stabilizerSize) {
        public Core { counts = List.copyOf(counts); edgeCounts = edgeCounts.stream().map(List::copyOf).toList(); }
        public Map<String,String> fields() {
            return Map.of("counts",counts.toString(),"edge_counts",edgeCounts.toString(),"transition_cost",Integer.toString(transitionCost),
                    "balance_penalty",Integer.toString(balancePenalty),"total_cost",Integer.toString(totalCost),"representative",representative,
                    "orbit_size",Integer.toString(orbitSize),"period",Integer.toString(period),"stabilizer_size",Integer.toString(stabilizerSize));
        }
    }
    public record Score(Core core, List<Integer> edgeCosts) { public Score { edgeCosts = List.copyOf(edgeCosts); } }
    public record Result(String campaign, String word, Score observed, Core reference, List<String> mismatched) {
        public Result { mismatched = List.copyOf(mismatched); }
        public boolean passed() { return mismatched.isEmpty(); }
        public String caseId() { return "W="+word; }
    }
    private CyclicPatterns() { }
    public static void validate(String word) {
        if (word == null || !word.matches("[ABC]{6}")) throw new IllegalArgumentException("six letters over A,B,C required");
    }
    private static String rotate(String word, int k) { return word.substring(k)+word.substring(0,k); }
    public static String representative(String word) {
        validate(word); String best = word; for (int k=1;k<6;k++) if (rotate(word,k).compareTo(best)<0) best=rotate(word,k); return best;
    }
    private static void validate(String campaign, String word) {
        validate(word); if (!List.of("words","classes").contains(campaign)) throw new IllegalArgumentException("unknown D12 campaign");
        if (campaign.equals("classes") && !representative(word).equals(word)) throw new IllegalArgumentException("canonical rotation representative required");
    }
    private static int cost(char from, char to) {
        if (from==to) return 2;
        return from=='A'&&to=='B' || from=='B'&&to=='C' || from=='C'&&to=='A' ? 0 : 3;
    }
    private static List<List<Integer>> matrix(int[][] values) {
        return java.util.Arrays.stream(values).map(row -> java.util.Arrays.stream(row).boxed().toList()).toList();
    }
    public static Score sut(String word) {
        validate(word); var counts = new ArrayList<Integer>();
        for (char letter : "ABC".toCharArray()) counts.add((int)word.chars().filter(c -> c==letter).count());
        int[][] pairs = new int[3][3]; var edgeCosts = new ArrayList<Integer>(); int transition=0;
        for (int k=0;k<6;k++) {
            char from=word.charAt(k),to=word.charAt((k+1)%6); pairs[from-'A'][to-'A']++;
            int value=cost(from,to); edgeCosts.add(value); transition+=value;
        }
        int balance=counts.stream().mapToInt(n -> (n-2)*(n-2)).sum(); var rotations=new TreeSet<String>();
        for (int k=0;k<6;k++) rotations.add(rotate(word,k)); int period=6;
        for (int k=1;k<6;k++) if (rotate(word,k).equals(word)) { period=k; break; }
        return new Score(new Core(counts,matrix(pairs),transition,balance,transition+balance,rotations.first(),rotations.size(),period,6/rotations.size()),edgeCosts);
    }
    public static Core reference(String word) {
        validate(word); int[][] weights={{2,0,3},{3,2,0},{0,3,2}},pairs=new int[3][3]; int[] counts=new int[3];
        String seam=word+word.charAt(0); for (int k=0;k<6;k++) { pairs[seam.charAt(k)-'A'][seam.charAt(k+1)-'A']++; counts[word.charAt(k)-'A']++; }
        int transition=0,balance=0; for (int i=0;i<3;i++) { balance+=(counts[i]-2)*(counts[i]-2); for (int j=0;j<3;j++) transition+=pairs[i][j]*weights[i][j]; }
        String ring=word+word; var slices=new TreeSet<String>(); int period=6;
        for (int k=0;k<6;k++) { String slice=ring.substring(k,k+6); slices.add(slice); if (k>0&&slice.equals(word)&&period==6) period=k; }
        return new Core(java.util.Arrays.stream(counts).boxed().toList(),matrix(pairs),transition,balance,transition+balance,slices.first(),slices.size(),period,6/slices.size());
    }
    public static Result judge(String campaign, String word, Score observed) {
        validate(campaign,word); Core reference=reference(word); var got=observed.core().fields(); var expected=reference.fields();
        return new Result(campaign,word,observed,reference,COMPARED.stream().filter(k -> !got.get(k).equals(expected.get(k))).toList());
    }
    public static Result evaluate(String campaign, String word) { return judge(campaign,word,sut(word)); }
    public static Map<String,String> attributes(Result r) {
        var a=new TreeMap<String,String>(); a.putAll(r.observed().core().fields()); a.put("edge_costs",r.observed().edgeCosts().toString());
        r.reference().fields().forEach((k,v) -> a.put("reference_"+k,v));
        a.put("schema","d12.observation/v1"); a.put("contract","v1"); a.put("campaign",r.campaign()); a.put("case_id",r.caseId()); a.put("word",r.word());
        a.put("mismatched_fields",r.mismatched().toString()); a.put("oracle_agrees",Boolean.toString(r.passed())); a.put("verdict",r.passed()?"PASS":"DOMAIN_FAIL");
        a.put("fw_var",r.passed()?"0":"2"); a.put("carrier","second mandatory slot (legacy positional, not causal)"); return Map.copyOf(a);
    }
}
