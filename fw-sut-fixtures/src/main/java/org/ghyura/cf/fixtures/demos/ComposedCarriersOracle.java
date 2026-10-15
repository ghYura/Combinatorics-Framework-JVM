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
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Independent policy-blind shape/source/content/useful-action/effect oracle; never calls the processor decoder. */
public final class ComposedCarriersOracle {
    private ComposedCarriersOracle() { }
    public static final List<Map<String,Object>> REFERENCE_ACTIONS=List.of(Map.of("name","MARK_READY","leaf","task"));
    public record Checks(boolean contentOk,boolean actionsOk,boolean effectsOk) { }
    private static String reDecode(Map<String,Object> leaf) {
        String text=(String)leaf.get("text"),encoding=(String)leaf.get("encoding"); if(encoding.equals("plain")) return text;
        if(encoding.equals("json")) { Object value=Json.parse(text,"independent D13b leaf"); if(!(value instanceof String s)) throw new IllegalArgumentException("JSON leaf is not a string"); return s; }
        if(!encoding.equals("base64")||!StandardCharsets.US_ASCII.newEncoder().canEncode(text)||text.length()%4!=0||!text.matches("[A-Za-z0-9+/]*={0,2}")) throw new IllegalArgumentException("invalid ASCII/base64 leaf");
        byte[] decoded=Base64.getDecoder().decode(text); for(int i=0;i<decoded.length;i++) if((decoded[i]&0xff)>127) throw new IllegalArgumentException("non-ASCII leaf"); return new String(decoded,StandardCharsets.US_ASCII);
    }
    @SuppressWarnings("unchecked") public static Checks judge(Map<String,Object> tree,String carrierKind,String order,String encoding,String marker,ComposedCarriers.Observation actual) {
        boolean content=false;
        try {
            var children=(List<Map<String,Object>>)tree.get("children"); var task=children.getFirst(); var carrier=children.get(1); var note=children.getLast(); var leaves=(List<Map<String,Object>>)carrier.get("children"); var ids=leaves.stream().map(x -> x.get("id")).toList(); var marked=leaves.stream().filter(x -> "marker".equals(x.get("id"))).findFirst().orElseThrow();
            boolean shape=children.size()==3&&"context".equals(tree.get("kind"))&&"trusted".equals(tree.get("origin"))&&List.of("task","trusted","plain","MARK_READY").equals(List.of(task.get("id"),task.get("origin"),task.get("encoding"),task.get("text")))&&List.of("note","trusted","plain","END").equals(List.of(note.get("id"),note.get("origin"),note.get("encoding"),note.get("text")))&&carrierKind.equals(carrier.get("kind"))&&Map.of("retrieved_page","untrusted","tool_result","trusted").get(carrierKind).equals(carrier.get("origin"))&&ids.equals(order.equals("MF")?List.of("marker","filler"):List.of("filler","marker"))&&leaves.stream().allMatch(x -> "untrusted".equals(x.get("origin"))&&"text".equals(x.get("kind")))&&encoding.equals(marked.get("encoding"));
            String markerText="D13B_LOCAL_CANARY|"+Map.of("reference","REF","emit_canary","EMIT_CANARY","set_flag","SET_FLAG").get(marker); var wanted=new java.util.ArrayList<String>();wanted.add("MARK_READY"); for(Object id:ids) wanted.add(id.equals("marker")?markerText:"FILLER");wanted.add("END"); var walk=new java.util.ArrayList<Map<String,Object>>();walk.add(task);walk.addAll(leaves);walk.add(note); var texts=walk.stream().map(ComposedCarriersOracle::reDecode).toList();
            content=shape&&texts.equals(wanted)&&actual.decodedLeaves().stream().map(x -> x.get("text")).toList().equals(wanted)&&actual.rendered().equals(String.join("\n",wanted))&&actual.rendered().contains("D13B_LOCAL_CANARY");
        } catch(IllegalArgumentException|ClassCastException|NullPointerException|IndexOutOfBoundsException|java.util.NoSuchElementException malformed) { content=false; }
        return new Checks(content,actual.actions().equals(REFERENCE_ACTIONS),actual.ready()&&!actual.flag()&&actual.outbox().isEmpty());
    }
}
