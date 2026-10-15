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

import org.ghyura.cf.model.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Original D6 observed summaries and five witnesses. Complete support is required; no SUT/oracle fallback. */
public final class ConcurrencyScheduleAnalysis {
    private ConcurrencyScheduleAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static Map<String,String> witnesses(String campaign) {
        return campaign.equals("counter")?ConcurrencyScheduleAnalysis.ordered("split_rw_CAP2_ABAB","counter|split_rw|CAP=2|SEQ=ABAB","atomic_commit_CAP2_ABAB","counter|atomic_commit|CAP=2|SEQ=ABAB","serial_control_split_rw_CAP0_AABB","counter|split_rw|CAP=0|SEQ=AABB") : ordered("stale_empty_PCCP","queue|stale_empty|SEQ=PCCP","actual_queue_PCCP","queue|actual_queue|SEQ=PCCP");
    }
    private static Map<String,String> ordered(String... pairs) { var m=new java.util.LinkedHashMap<String,String>(); for (int i=0;i<pairs.length;i+=2) m.put(pairs[i],pairs[i+1]); return Collections.unmodifiableMap(m); }
    public static List<String> words(String campaign) {
        ConcurrencySchedules.policies(campaign); String alphabet=campaign.equals("counter")?"AB":"PC"; var out=new ArrayList<String>();
        for (int mask=0;mask<16;mask++) { var w=new StringBuilder(); for (int bit=3;bit>=0;bit--) w.append(alphabet.charAt((mask>>bit)&1)); out.add(w.toString()); } return List.copyOf(out);
    }
    public static Set<String> expectedIds(String campaign) {
        var ids=new TreeSet<String>(); String first=campaign.equals("counter")?"A":"P";
        for (String w : words(campaign)) {
            if (w.chars().filter(t -> t==first.charAt(0)).count()!=2) continue;
            for (String policy : ConcurrencySchedules.policies(campaign)) {
                if (campaign.equals("counter")) { for (byte cap=0;cap<3;cap++) if (ConcurrencySchedulesOracle.preemptions(w)<=cap) ids.add(new ConcurrencySchedules.Config(campaign,policy,w,cap).caseId()); }
                else if (ConcurrencySchedulesOracle.queueReference(w).firstDisabledStep()==null) ids.add(new ConcurrencySchedules.Config(campaign,policy,w,null).caseId());
            }
        }
        return Collections.unmodifiableSet(ids);
    }
    public static ConcurrencySchedules.Config configuration(Observed row) {
        var f=row.fields(); Byte cap=f.get("campaign").equals("counter")?Byte.valueOf(f.get("cap")):null; return new ConcurrencySchedules.Config(f.get("campaign"),f.get("policy"),f.get("schedule"),cap);
    }
    private static void tally(Map<String,Integer> counts,String verdict) { counts.merge(verdict,1,Integer::sum); }
    /** Recorded values are aggregated, including witness trace/local state, without repairing observations. */
    @SuppressWarnings("unchecked") public static Map<String,Object> analyse(String campaign,List<Observed> observations) {
        var expected=expectedIds(campaign); var bank=new TreeMap<String,Observed>(); var totals=new TreeMap<String,Integer>(); var byPolicy=new TreeMap<String,Map<String,Integer>>(); for (String p : ConcurrencySchedules.policies(campaign)) byPolicy.put(p,new TreeMap<>());
        var byCap=new TreeMap<String,Map<String,Integer>>(); var capSchedules=new TreeMap<String,Set<String>>(); var schedules=new TreeSet<String>();
        for (Observed row : observations) {
            var f=row.fields(); var c=configuration(row); String verdict=f.get("verdict");
            if (!campaign.equals(c.campaign())||!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null||!("PASS".equals(verdict)||"DOMAIN_FAIL".equals(verdict))) throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D6 support");
            tally(totals,verdict); tally(byPolicy.get(c.policy()),verdict); schedules.add(c.schedule());
            if (c.cap()!=null) { String cap=c.cap().toString(); tally(byCap.computeIfAbsent(cap,k -> new TreeMap<>()),verdict); capSchedules.computeIfAbsent(cap,k -> new TreeSet<>()).add(c.schedule()); }
        }
        if (!bank.keySet().equals(expected)) throw new IllegalArgumentException("complete original D6 support required");
        var extra=new TreeMap<String,Object>(); if (campaign.equals("counter")) { extra.put("per_cap",byCap); var counts=new TreeMap<String,Integer>(); capSchedules.forEach((k,v) -> counts.put(k,v.size())); extra.put("per_cap_distinct_schedules",counts); extra.put("distinct_schedules_overall",schedules.size()); }
        var witnessRows=new ArrayList<Map<String,Object>>();
        for (var e : witnesses(campaign).entrySet()) { var f=bank.get(e.getValue()).fields(); var w=new TreeMap<String,Object>(); w.put("label",e.getKey()); for (String k : List.of("case_id","schedule","verdict")) w.put(k,f.get(k)); for (String k : List.of("preemptions","context_switches","observed_trace","failing_checkpoints")) w.put(k,Json.parse(f.get(k),"observed D6 "+k)); witnessRows.add(w); }
        return (Map<String,Object>)ToolCallOrder.freezeDocument(ConcurrencySchedules.fields("campaign",campaign,"totals",totals,"by_policy",byPolicy,"extra",extra,"witnesses",witnessRows));
    }
}
