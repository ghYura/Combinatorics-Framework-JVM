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

import org.ghyura.cf.fixtures.demos.ToolCallOrder.Delivery;
import org.ghyura.cf.fixtures.demos.ToolCallOrder.Event;
import org.ghyura.cf.fixtures.demos.ToolCallOrder.PublicSend;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Independent guarded-plan reference and event-prefix obligations. Never reads local state snapshots. */
public final class ToolCallOrderOracle {
    private ToolCallOrderOracle() { }
    private static final String MESSAGE_ID="notice-1";
    public record Reference(List<PublicSend> sends,List<Delivery> outbox) { public Reference { sends=List.copyOf(sends); outbox=List.copyOf(outbox); } }
    public record Violation(byte request,String kind) { public Map<String,Object> document() { return ToolCallOrder.fields("request",request,"kind",kind); } }
    public static List<Violation> violations(List<Event> trace) {
        boolean approved=false,tainted=false; var emitted=new HashSet<String>(); var out=new ArrayList<Violation>();
        for (var e : trace) {
            switch (e.name()) {
                case "approve" -> approved=true;
                case "revoke" -> approved=false;
                case "read_chunk" -> tainted=true;
                case "send_result" -> { if (e.action().equals("emit")) {
                    if (!approved) out.add(new Violation(e.request(),"unapproved_emit"));
                    if (tainted) out.add(new Violation(e.request(),"tainted_emit"));
                    if (emitted.contains(e.messageId())) out.add(new Violation(e.request(),"duplicate_emit")); emitted.add(e.messageId());
                } }
                default -> { }
            }
        }
        return List.copyOf(out);
    }
    public static Reference reference(String order,byte repetitions,Byte revokeCut) {
        if (!List.of("ARS","ASR","RAS","RSA","SAR","SRA").contains(order)||repetitions<1||repetitions>2||revokeCut!=null&&(revokeCut<0||revokeCut>3)) throw new IllegalArgumentException("invalid guarded plan");
        var steps=new ArrayList<Character>(); if (revokeCut!=null&&revokeCut==0) steps.add('X');
        for (int k=0;k<3;k++) { steps.add(order.charAt(k)); if (revokeCut!=null&&revokeCut==k+1) steps.add('X'); }
        boolean eligible=false,chunkReceived=false,delivered=false; var sends=new ArrayList<PublicSend>(); var outbox=new ArrayList<Delivery>();
        for (char step : steps) {
            switch (step) {
                case 'X' -> eligible=false;
                case 'A' -> eligible=true;
                case 'R' -> chunkReceived=true;
                case 'S' -> { for (byte q=1;q<=repetitions;q++) {
                    String action; String payload=null;
                    if (eligible&&!chunkReceived&&!delivered) { action="emit"; payload="PUBLIC"; outbox.add(new Delivery(MESSAGE_ID,payload)); delivered=true; }
                    else if (eligible&&!chunkReceived) action="dedup"; else action="block";
                    sends.add(new PublicSend(q,action,payload));
                } }
                default -> throw new IllegalArgumentException();
            }
        }
        return new Reference(sends,outbox);
    }
}
