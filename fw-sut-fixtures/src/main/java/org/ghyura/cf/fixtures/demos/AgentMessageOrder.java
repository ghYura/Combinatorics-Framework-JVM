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

/** D13c's atomic local coordinator and two agent stubs; defective policies remain observable. */
public final class AgentMessageOrder {
    private AgentMessageOrder() { }
    public static final List<String> POLICIES=List.of("compare_version","trust_offer","overwrite_owner"), MODES=List.of("independent","after_A_offer");
    public record Config(String policy,String mode,byte cap,String schedule) {
        public Config { if(policy==null||mode==null||schedule==null||!POLICIES.contains(policy)||!MODES.contains(mode)||cap<0||cap>2||!schedule.matches("[AB]{4}"))throw new IllegalArgumentException("unknown D13c configuration"); }
        public String caseId() { return "P="+policy+"|M="+mode+"|K="+cap+"|S="+schedule; }
    }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>();for(int i=0;i<pairs.length;i+=2)m.put((String)pairs[i],pairs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> m) { return (Map<String,Object>)ToolCallOrder.freezeDocument(m); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public static final class Coordinator {
        private final String policy;
        private byte version;
        private final Map<String,String> allocations=new TreeMap<>(), issuedGrants=new TreeMap<>();
        public Coordinator(String policy) { if(policy==null||!POLICIES.contains(policy))throw new IllegalArgumentException("unknown policy");this.policy=policy; }
        public Map<String,Object> snapshot() { return freeze(fields("version",version,"allocations",allocations,"issued_grants",issuedGrants)); }
        public Map<String,Object> deliver(Map<String,Object> request) {
            Object agent=request.get("agent"),kind=request.get("kind");
            if(!("A".equals(agent)||"B".equals(agent))||!"R".equals(request.get("resource"))||!(agent+":"+kind).equals(request.get("id")))throw new IllegalArgumentException("malformed coordinator message");
            if("inspect".equals(kind)) { if(!request.keySet().equals(Set.of("id","agent","kind","resource")))throw new IllegalArgumentException("malformed inspect");return fields("available",allocations.isEmpty(),"version",version); }
            Object offered=request.get("offer_version");
            if(!"commit".equals(kind)||!request.keySet().equals(Set.of("id","agent","kind","resource","offer_version","offer_available"))||!(offered instanceof Byte||offered instanceof Integer||offered instanceof Long)||((Number)offered).longValue()<Byte.MIN_VALUE||((Number)offered).longValue()>Byte.MAX_VALUE||!(request.get("offer_available") instanceof Boolean))throw new IllegalArgumentException("malformed commit");
            if(!((Boolean)request.get("offer_available")))return fields("status","BUSY","ticket",null);
            if(policy.equals("compare_version")&&(!allocations.isEmpty()||version!=((Number)offered).byteValue()))return fields("status","STALE","ticket",null);
            if(version==Byte.MAX_VALUE)throw new IllegalStateException("coordinator version overflow");String ticket="R:"+agent;
            if(policy.equals("overwrite_owner"))allocations.clear();allocations.put((String)agent,ticket);issuedGrants.put((String)agent,ticket);version++;
            return fields("status","GRANTED","ticket",ticket);
        }
    }
    public static byte contextSwitches(String word) { byte n=0;for(int i=0;i<word.length()-1;i++)if(word.charAt(i)!=word.charAt(i+1))n++;return n; }
    public static byte preemptions(String word) { byte n=0;for(int i=0;i<word.length()-1;i++){char outgoing=word.charAt(i);if(outgoing!=word.charAt(i+1)&&word.substring(0,i+1).chars().filter(c -> c==outgoing).count()<2)n++;}return n; }
    private static List<String> enabled(Map<String,Byte> delivered,String mode) { var out=new ArrayList<String>();for(String a:List.of("A","B"))if(delivered.get(a)<2&&!(a.equals("B")&&mode.equals("after_A_offer")&&delivered.get("B")==0&&delivered.get("A")==0))out.add(a);return List.copyOf(out); }
    public static List<Map<String,Object>> observe(Config c) {
        if(c.schedule().chars().filter(x -> x=='A').count()!=2||preemptions(c.schedule())>c.cap())throw new IllegalArgumentException("malformed local order or exceeded preemption cap");
        var coordinator=new Coordinator(c.policy());var delivered=new TreeMap<String,Byte>();delivered.put("A",(byte)0);delivered.put("B",(byte)0);var offers=new TreeMap<String,Map<String,Object>>();var trace=new ArrayList<Map<String,Object>>();byte index=0;
        for(char ch:c.schedule().toCharArray()) { String agent=Character.toString(ch);var before=enabled(delivered,c.mode());if(!before.contains(agent))throw new IllegalArgumentException("disabled local delivery");byte step=(byte)(delivered.get(agent)+1);delivered.put(agent,step);String kind=step==1?"inspect":"commit";var request=new TreeMap<>(fields("id",agent+":"+kind,"agent",agent,"kind",kind,"resource","R"));
            if(step==2){request.put("offer_version",offers.get(agent).get("version"));request.put("offer_available",offers.get(agent).get("available"));}var reply=coordinator.deliver(request);if(step==1)offers.put(agent,reply);var snap=coordinator.snapshot();trace.add(freeze(fields("index",++index,"agent",agent,"local_step",step,"message",request.get("id"),"enabled_agents_before",before,"request",request,"response",reply,"offers",offers,"version",snap.get("version"),"allocations",snap.get("allocations"),"issued_grants",snap.get("issued_grants"))));
        }
        return List.copyOf(trace);
    }
    public record Result(Config config,List<Map<String,Object>> observedTrace,List<Map<String,Object>> referenceTrace,AgentMessageOrderOracle.Judgement judgement) {
        public Result { observedTrace=observedTrace.stream().map(AgentMessageOrder::freeze).toList();referenceTrace=referenceTrace.stream().map(AgentMessageOrder::freeze).toList();if(!judgement.integrityOk())throw new IllegalStateException("D13c observation integrity failure: "+judgement.integrityFailures()); }
        public boolean passed() { return judgement.failingCheckpoints().isEmpty(); }
        public Map<String,Object> document() { var all=new TreeMap<String,Boolean>();for(String k:AgentMessageOrderOracle.CHECKS)all.put(k,judgement.checks().stream().allMatch(x -> Boolean.TRUE.equals(x.get(k))));return fields("schema","d13c.observation/v1","contract","v1","case_id",config.caseId(),"policy",config.policy(),"mode",config.mode(),"cap",config.cap(),"schedule",config.schedule(),"context_switches",contextSwitches(config.schedule()),"preemptions",preemptions(config.schedule()),"observed_trace",observedTrace,"reference_trace",referenceTrace,"reconstructed_ledgers",AgentMessageOrderOracle.reconstructedLedgers(observedTrace),"integrity_ok",true,"checks",judgement.checks(),"failing_checkpoints",judgement.failingCheckpoints(),"all_checks",all,"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier_slot","IMPL position 2 (legacy positional, not causal)"); }
    }
    public static Result judge(Config c,List<Map<String,Object>> observed) { var reference=AgentMessageOrderOracle.referenceTrace(c.schedule(),c.mode());return new Result(c,observed,reference,AgentMessageOrderOracle.judge(observed,reference)); }
    public static Result evaluate(Config c) { return judge(c,observe(c)); }
    public static Map<String,String> attributes(Result r) { var out=new TreeMap<String,String>();r.document().forEach((k,v) -> out.put(k,v instanceof String s?s:json(v)));return Map.copyOf(out); }
}
