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
import java.util.Objects;

/** D13d reference and isolation checks use case inputs, never policy, adapter keys or stored provenance. */
public final class SessionMemoryOracle {
    private SessionMemoryOracle() { }
    public static final String CANARY_PREFIX="D13D_CANARY_R";
    public static byte epochAt(String cuts,byte index) { byte epoch=0; for (byte i=1;i<index;i++) if (cuts.charAt(i-1)=='1') epoch++; return epoch; }
    /** A case has one write, so the independent scoped reference retains at most one entry. */
    public static List<SessionMemory.Event> reference(String users,String sessions,String cuts,byte writeAt) {
        var out=new ArrayList<SessionMemory.Event>(); SessionMemory.MemoryEntry retained=null;
        for (byte i=1;i<=3;i++) {
            byte user=(byte)(users.charAt(i-1)-'0'),session=(byte)(sessions.charAt(i-1)-'0'),epoch=epochAt(cuts,i); boolean reset=i>1&&cuts.charAt(i-2)=='1'; var key=List.of(user,session);
            if (reset) retained=null;
            String reply,op; List<SessionMemory.ToolCall> calls;
            if (i==writeAt) { retained=new SessionMemory.MemoryEntry(key,CANARY_PREFIX+i,user,session,epoch,i); reply="STORED"; calls=List.of(); op="write"; }
            else { reply=retained!=null&&retained.key().equals(key)?retained.value():null; calls=reply==null?List.of():List.of(new SessionMemory.ToolCall("draft",reply)); op="read"; }
            out.add(new SessionMemory.Event(i,user,session,op,reset,epoch,key,reply,calls,retained==null?List.of():List.of(retained)));
        }
        return List.copyOf(out);
    }
    public record Owner(byte user,byte session,byte epoch,String canary) { public Map<String,Object> document() { return SessionMemory.fields("user",user,"session",session,"epoch",epoch,"canary",canary); } }
    public record Check(byte requestIndex,boolean responseOk,boolean toolsOk,boolean isolationOk) { public boolean passed() { return responseOk&&toolsOk&&isolationOk; } public Map<String,Object> document() { return SessionMemory.fields("request_index",requestIndex,"response_ok",responseOk,"tools_ok",toolsOk,"isolation_ok",isolationOk); } }
    public record Leak(byte requestIndex,String kind) { public Map<String,Object> document() { return SessionMemory.fields("request_index",requestIndex,"kind",kind); } }
    public record Occurrence(byte requestIndex,String channel) { public Map<String,Object> document() { return SessionMemory.fields("request_index",requestIndex,"channel",channel); } }
    public record Judgment(Owner owner,List<Check> checks,List<Leak> leaks,List<Occurrence> occurrences,List<Byte> failingCheckpoints) { public Judgment { checks=List.copyOf(checks); leaks=List.copyOf(leaks); occurrences=List.copyOf(occurrences); failingCheckpoints=List.copyOf(failingCheckpoints); } }
    private record Exposure(String channel,String value) { }
    private static List<Exposure> canaries(SessionMemory.Event e) {
        var out=new ArrayList<Exposure>(); if (e.reply()!=null&&e.reply().contains(CANARY_PREFIX)) out.add(new Exposure("reply",e.reply()));
        for (var call : e.toolCalls()) if (call.argument()!=null&&call.argument().contains(CANARY_PREFIX)) out.add(new Exposure("tool_argument",call.argument())); return out;
    }
    public static Judgment judge(List<SessionMemory.Event> observed,List<SessionMemory.Event> reference,String users,String sessions,String cuts,byte writeAt) {
        if (observed.size()!=3||!observed.stream().map(SessionMemory.Event::requestIndex).toList().equals(List.of((byte)1,(byte)2,(byte)3))) throw new IllegalArgumentException("observed requests 1..3 required");
        var owner=new Owner((byte)(users.charAt(writeAt-1)-'0'),(byte)(sessions.charAt(writeAt-1)-'0'),epochAt(cuts,writeAt),CANARY_PREFIX+writeAt); var checks=new ArrayList<Check>(); var leaks=new ArrayList<Leak>(); var occurrences=new ArrayList<Occurrence>(); var failing=new ArrayList<Byte>();
        for (byte i=1;i<=3;i++) {
            var got=observed.get(i-1); var want=reference.get(i-1); byte user=(byte)(users.charAt(i-1)-'0'),session=(byte)(sessions.charAt(i-1)-'0'),epoch=epochAt(cuts,i);
            var bad=new ArrayList<Exposure>(); if (i!=writeAt) for (var exposure : canaries(got)) if (!exposure.value().equals(owner.canary())||user!=owner.user()||session!=owner.session()||epoch!=owner.epoch()||i<writeAt) bad.add(exposure);
            var check=new Check(i,Objects.equals(got.reply(),want.reply()),got.toolCalls().equals(want.toolCalls()),bad.isEmpty()); checks.add(check); if (!check.passed()) failing.add(i);
            if (!bad.isEmpty()) { String kind=owner.user()!=user?"cross_user":owner.session()!=session?"cross_session":"expired"; leaks.add(new Leak(i,kind)); for (var exposure : bad) occurrences.add(new Occurrence(i,exposure.channel())); }
        }
        return new Judgment(owner,checks,leaks,occurrences,failing);
    }
}
