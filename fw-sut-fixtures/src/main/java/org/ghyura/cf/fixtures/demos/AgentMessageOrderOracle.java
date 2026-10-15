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
import java.util.TreeMap;
import java.util.TreeSet;

/** Own policy-blind reference, reply-reconstructed promises and integrity gate before four prefix sensors. */
public final class AgentMessageOrderOracle {
    private AgentMessageOrderOracle() { }
    public static final List<String> CHECKS=List.of("capacity_ok","exclusive_promises_ok","commitments_ok","reference_ok");
    private static Map<String,Object> fields(Object... xs) { var m=new TreeMap<String,Object>();for(int i=0;i<xs.length;i+=2)m.put((String)xs[i],xs[i+1]);return Collections.unmodifiableMap(m); }
    @SuppressWarnings("unchecked") private static Map<String,Object> freeze(Map<String,Object> m) { return (Map<String,Object>)ToolCallOrder.freezeDocument(m); }
    @SuppressWarnings("unchecked") private static Map<String,Object> object(Object m) { return m==null?Map.of():(Map<String,Object>)m; }
    private static Object normalized(Object v) { return Json.parse(Json.write(ToolCallOrder.freezeDocument(v)),"independent D13c comparison"); }
    private static List<String> enabled(Map<String,Byte> counts,String mode) { var agents=new ArrayList<String>();for(String a:List.of("A","B")){if(counts.get(a)>=2)continue;if(a.equals("B")&&counts.get("B")==0&&mode.equals("after_A_offer")&&counts.get("A")==0)continue;agents.add(a);}return List.copyOf(agents); }
    public static List<Map<String,Object>> referenceTrace(String schedule,String mode) {
        if(schedule==null||!schedule.matches("[AB]{4}")||!("independent".equals(mode)||"after_A_offer".equals(mode)))throw new IllegalArgumentException("invalid reference schedule/mode");var counts=new TreeMap<String,Byte>();counts.put("A",(byte)0);counts.put("B",(byte)0);var offers=new TreeMap<String,Map<String,Object>>();var allocations=new TreeMap<String,String>();var promises=new TreeMap<String,String>();byte version=0,index=0;var events=new ArrayList<Map<String,Object>>();
        for(char ch:schedule.toCharArray()) { String a=Character.toString(ch);var before=enabled(counts,mode);if(!before.contains(a))throw new IllegalArgumentException("disabled reference delivery");byte local=(byte)(counts.get(a)+1);counts.put(a,local);String kind=local==1?"inspect":"commit";var request=new TreeMap<>(fields("id",a+":"+kind,"agent",a,"kind",kind,"resource","R"));Map<String,Object> reply;
            if(local==1){reply=fields("available",allocations.isEmpty(),"version",version);offers.put(a,reply);}else{var offer=offers.get(a);request.put("offer_version",offer.get("version"));request.put("offer_available",offer.get("available"));if(Boolean.FALSE.equals(offer.get("available")))reply=fields("status","BUSY","ticket",null);else if(!allocations.isEmpty()||version!=((Number)offer.get("version")).byteValue())reply=fields("status","STALE","ticket",null);else{String ticket="R:"+a;allocations.put(a,ticket);promises.put(a,ticket);version++;reply=fields("status","GRANTED","ticket",ticket);}}
            events.add(freeze(fields("index",++index,"agent",a,"local_step",local,"message",request.get("id"),"enabled_agents_before",before,"request",request,"response",reply,"offers",offers,"version",version,"allocations",allocations,"issued_grants",promises)));
        }return List.copyOf(events);
    }
    public static List<Map<String,Object>> reconstructedLedgers(List<Map<String,Object>> trace) { var ledger=new TreeMap<String,Object>();var out=new ArrayList<Map<String,Object>>();for(var e:trace){var reply=object(e.get("response"));if("GRANTED".equals(reply.get("status")))ledger.put((String)e.get("agent"),reply.get("ticket"));out.add(freeze(ledger));}return List.copyOf(out); }
    public record Judgement(boolean integrityOk,List<Object> integrityFailures,List<Map<String,Object>> checks,List<Object> failingCheckpoints) { }
    public static Judgement judge(List<Map<String,Object>> observed,List<Map<String,Object>> reference) {
        var ledgers=reconstructedLedgers(observed);var broken=new TreeSet<Integer>();
        for(int i=0;i<observed.size();i++){var e=observed.get(i);var response=object(e.get("response"));if(!ledgers.get(i).equals(e.get("issued_grants"))||("GRANTED".equals(response.get("status"))&&!("R:"+e.get("agent")).equals(response.get("ticket"))))broken.add(((Number)e.get("index")).intValue());}
        if(!broken.isEmpty()||observed.size()!=reference.size())return new Judgement(false,broken.isEmpty()?List.of("length"):new ArrayList<Object>(broken),null,null);
        var checks=new ArrayList<Map<String,Object>>();var failing=new ArrayList<Object>();for(int i=0;i<observed.size();i++){var got=observed.get(i);var want=reference.get(i);var led=ledgers.get(i);var allocations=object(got.get("allocations"));var check=fields("index",want.get("index"),"capacity_ok",allocations.size()<=1,"exclusive_promises_ok",led.size()<=1,"commitments_ok",allocations.equals(led),"reference_ok",normalized(got).equals(normalized(want)));checks.add(check);if(CHECKS.stream().anyMatch(k -> !Boolean.TRUE.equals(check.get(k))))failing.add(want.get("index"));}return new Judgement(true,List.of(),List.copyOf(checks),List.copyOf(failing));
    }
}
