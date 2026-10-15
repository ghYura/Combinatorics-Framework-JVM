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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Original D13d offline aggregation, from complete recorded observations only. */
public final class SessionMemoryAnalysis {
    private SessionMemoryAnalysis() { }
    public record Observed(Map<String,String> fields) {
        public Observed { fields=Map.copyOf(fields); }
        public String id() { return fields.get("case_id"); }
    }
    public static SessionMemory.Config configuration(Observed row) {
        var f=row.fields();
        try { return new SessionMemory.Config(f.get("policy"),f.get("users"),f.get("sessions"),f.get("cuts"),Byte.parseByte(f.get("write_at"))); }
        catch (RuntimeException e) { throw new IllegalArgumentException("incomplete/invalid D13d coordinates",e); }
    }
    public static Set<String> expectedIds() {
        var ids=new TreeSet<String>();
        for (String p : SessionMemory.POLICIES) for (String u : SessionMemory.PARTITIONS) for (String s : SessionMemory.PARTITIONS) for (String x : SessionMemory.CUTS) for (byte w=1;w<=2;w++) ids.add(new SessionMemory.Config(p,u,s,x,w).caseId());
        return Collections.unmodifiableSet(ids);
    }
    public static Map<String,String> witnesses() {
        var m=new LinkedHashMap<String,String>();
        m.put("session_only_cross_user","P=session_only|U=010|S=000|X=00|W=1");
        m.put("user_only_cross_session","P=user_only|U=000|S=010|X=00|W=1");
        m.put("ignores_reset_expired","P=ignores_reset|U=000|S=000|X=10|W=1");
        m.put("scoped_reset_before_write_retains","P=scoped|U=000|S=000|X=10|W=2");
        m.put("scoped_reset_after_write_clears","P=scoped|U=000|S=000|X=01|W=1");
        return Collections.unmodifiableMap(m);
    }
    private static void tally(Map<String,Integer> m,String value) { m.merge(value,1,Integer::sum); }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> records(Map<String,String> f,String key) {
        Object parsed=Json.parse(f.get(key),"observed D13d "+key);
        if (!(parsed instanceof List<?> list)||list.stream().anyMatch(v -> !(v instanceof Map<?,?>))) throw new IllegalArgumentException("D13d record list required: "+key);
        return (List<Map<String,Object>>)parsed;
    }
    /** The verifier/tests assess field integrity; this preserves the original observed-value aggregation. */
    @SuppressWarnings("unchecked") public static Map<String,Object> analyse(List<Observed> observations) {
        var expected=expectedIds(); var bank=new TreeMap<String,Observed>();
        var totals=new TreeMap<String,Integer>(); var policy=new TreeMap<String,Map<String,Integer>>(); var cuts=new TreeMap<String,Map<String,Integer>>(); var writes=new TreeMap<String,Map<String,Integer>>(); var cross=new TreeMap<String,Map<String,Integer>>(); var kinds=new TreeMap<String,Integer>(); var occurrences=new TreeMap<String,Integer>(); var mechanisms=new TreeMap<String,Set<String>>(); var retainedPolicy=new TreeMap<String,Integer>(); int retained=0,twoLeaks=0;
        for (String p : SessionMemory.POLICIES) { policy.put(p,new TreeMap<>()); mechanisms.put(p,new TreeSet<>()); for (String x : SessionMemory.CUTS) for (byte w=1;w<=2;w++) cross.put(p+"|X="+x+"|W="+w,new TreeMap<>()); }
        for (String x : SessionMemory.CUTS) cuts.put(x,new TreeMap<>()); for (byte w=1;w<=2;w++) writes.put(Byte.toString(w),new TreeMap<>());
        for (Observed row : observations) {
            var f=row.fields(); var c=configuration(row); String verdict=f.get("verdict");
            if (!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null||!("PASS".equals(verdict)||"DOMAIN_FAIL".equals(verdict))) throw new IllegalArgumentException("unknown/duplicate/conflicting/non-domain D13d support");
            tally(totals,verdict); tally(policy.get(c.policy()),verdict); tally(cuts.get(c.cuts()),verdict); tally(writes.get(Byte.toString(c.writeAt())),verdict); tally(cross.get(c.policy()+"|X="+c.cuts()+"|W="+c.writeAt()),verdict);
            var leaks=records(f,"leaks"); if (leaks.size()==2) twoLeaks++;
            for (var leak : leaks) { String kind=(String)leak.get("kind"); tally(kinds,kind); mechanisms.get(c.policy()).add(kind); }
            for (var occurrence : records(f,"leak_occurrences")) tally(occurrences,(String)occurrence.get("channel"));
            if (records(f,"reference_trace").stream().anyMatch(e -> "read".equals(e.get("operation"))&&e.get("reply")!=null)) { retained++; tally(retainedPolicy,c.policy()); }
        }
        if (!bank.keySet().equals(expected)) throw new IllegalArgumentException("complete original 800-case D13d support required");
        var mechanismLists=new TreeMap<String,Object>(); mechanisms.forEach((k,v) -> mechanismLists.put(k,List.copyOf(v)));
        var witnessRows=new ArrayList<Map<String,Object>>();
        for (var e : witnesses().entrySet()) { var f=bank.get(e.getValue()).fields(); var witness=new TreeMap<String,Object>(); witness.put("label",e.getKey()); for (String k : List.of("case_id","verdict")) witness.put(k,f.get(k)); for (String k : List.of("observed_trace","checks","leaks","failing_checkpoints")) witness.put(k,Json.parse(f.get(k),"observed D13d "+k)); witnessRows.add(witness); }
        return (Map<String,Object>)ToolCallOrder.freezeDocument(SessionMemory.fields("totals",totals,"by_policy",policy,"leak_checkpoints",kinds,"occurrences",occurrences,"failed_cases",totals.getOrDefault("DOMAIN_FAIL",0),"cases_with_two_leaks",twoLeaks,"strata",Map.of("policy",policy,"cuts",cuts,"write_at",writes),"cross",cross,"leak_kinds_by_policy",mechanismLists,"useful_retention",Map.of("records_whose_reference_retains_a_value",retained,"per_policy",retainedPolicy),"witnesses",witnessRows));
    }
}
