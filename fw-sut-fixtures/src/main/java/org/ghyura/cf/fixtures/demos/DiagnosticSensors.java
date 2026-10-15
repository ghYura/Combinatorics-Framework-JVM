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

/** D10 fresh named probes and an independent literal reference table. */
public final class DiagnosticSensors {
    private DiagnosticSensors() { }
    public static final List<String> LABELS=java.util.stream.IntStream.rangeClosed(1,12).mapToObj(i -> "F%02d".formatted(i)).toList();
    public record Reading(List<Byte> latentBits,List<Byte> readings) {
        public Reading { latentBits=List.copyOf(latentBits); readings=List.copyOf(readings); }
    }
    public record Result(String mask,String fault,List<Integer> selected,Reading observed,List<Byte> reference,boolean passed) {
        public Result { selected=List.copyOf(selected); reference=List.copyOf(reference); }
        public String caseId() { return "S="+mask+"|H="+fault; }
    }
    public static List<Integer> selected(String mask) {
        if (mask==null||!mask.matches("[01]{8}")) throw new IllegalArgumentException("eight binary mask positions required");
        var out=new ArrayList<Integer>(); for (int i=0;i<8;i++) if (mask.charAt(i)=='1') out.add(i); return List.copyOf(out);
    }
    public static String mask(List<Integer> selected) {
        validate(selected); var s=new StringBuilder(); for (int i=0;i<8;i++) s.append(selected.contains(i)?'1':'0'); return s.toString();
    }
    private static void validate(List<Integer> selected) {
        int previous=-1; for (Integer sensor : selected) {
            if (sensor==null||sensor<0||sensor>7||sensor<=previous) throw new IllegalArgumentException("sensor unknown, repeated or out of order"); previous=sensor;
        }
    }
    private static int group(String label) {
        if ("H0".equals(label)) return 0; int index=LABELS.indexOf(label);
        if (index<0) throw new IllegalArgumentException("unknown fault label"); return index/2+1;
    }
    private static final class Plant {
        private final byte a,b,c,d;
        Plant(int group) {
            int code=switch (group) { case 0 -> 0; case 1 -> 1; case 2 -> 2; case 3 -> 4; case 4 -> 8; case 5 -> 7; case 6 -> 15; default -> throw new IllegalArgumentException(); };
            a=(byte)(code&1); b=(byte)((code>>1)&1); c=(byte)((code>>2)&1); d=(byte)((code>>3)&1);
        }
        List<Byte> latent() { return List.of(a,b,c,d); }
        byte read(int sensor) {
            return switch (sensor) { case 0 -> a; case 1 -> b; case 2 -> c; case 3 -> d; case 4 -> (byte)(a^b^d); case 5 -> (byte)(a^c^d); case 6 -> (byte)(b^c^d); case 7 -> (byte)(a^b^c); default -> throw new IllegalArgumentException("unknown sensor"); };
        }
    }
    public static Reading observe(String label,List<Integer> selected) {
        validate(selected); var plant=new Plant(group(label)); return new Reading(plant.latent(),selected.stream().map(plant::read).toList());
    }
    public static List<Byte> reference(String label,List<Integer> selected) {
        validate(selected); String signature=switch (group(label)) {
            case 0 -> "00000000"; case 1 -> "10001101"; case 2 -> "01001011"; case 3 -> "00100111";
            case 4 -> "00011110"; case 5 -> "11100001"; case 6 -> "11111111"; default -> throw new IllegalArgumentException();
        };
        return selected.stream().map(i -> (byte)(signature.charAt(i)-'0')).toList();
    }
    public static Result judge(String mask,String fault,Reading observed) {
        if (!LABELS.contains(fault)) throw new IllegalArgumentException("H0 is a declared reference, not a campaign fault");
        var selected=selected(mask); var expected=reference(fault,selected); return new Result(mask,fault,selected,observed,expected,observed.readings().equals(expected));
    }
    public static Result evaluate(String mask,String fault) { return judge(mask,fault,observe(fault,selected(mask))); }
    public static String signature(List<Byte> readings) { var s=new StringBuilder(); for (byte b : readings) { if (b!=0&&b!=1) throw new IllegalArgumentException("non-binary reading"); s.append(b); } return s.toString(); }
    public static Map<String,String> attributes(Result r) {
        var a=new TreeMap<String,String>(); a.put("schema","d10.observation/v1"); a.put("contract","v1"); a.put("case_id",r.caseId()); a.put("mask",r.mask()); a.put("selected",r.selected().toString()); a.put("sensor_cost",Integer.toString(r.selected().size())); a.put("fault",r.fault());
        a.put("latent_bits",r.observed().latentBits().toString()); a.put("readings",r.observed().readings().toString()); a.put("signature",signature(r.observed().readings())); a.put("reference_readings",r.reference().toString()); a.put("oracle_agrees",Boolean.toString(r.passed())); a.put("verdict",r.passed()?"PASS":"DOMAIN_FAIL"); a.put("fw_var",r.passed()?"0":"2"); a.put("carrier","SENSORS position 2 (legacy positional, not causal)"); return Map.copyOf(a);
    }
}
