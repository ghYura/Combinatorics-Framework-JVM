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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/** D13b's local structured processor, preserving all content and the two deliberate trust defects. */
public final class ComposedCarriers {
    private ComposedCarriers() { }
    public static final String CANARY="D13B_LOCAL_CANARY";
    public static final List<String> POLICIES=List.of("preserve_origin","wrapper_trust","decode_trust"), CARRIERS=List.of("retrieved_page","tool_result"), ORDERS=List.of("MF","FM"), ENCODINGS=List.of("plain","json","base64"), MARKERS=List.of("reference","emit_canary","set_flag");
    public record Config(String policy,String carrier,String order,String encoding,String marker) {
        public Config { if (policy==null||carrier==null||order==null||encoding==null||marker==null||!POLICIES.contains(policy)||!CARRIERS.contains(carrier)||!ORDERS.contains(order)||!ENCODINGS.contains(encoding)||!MARKERS.contains(marker)) throw new IllegalArgumentException("unknown D13b configuration"); }
        public String caseId() { return "P="+policy+"|C="+carrier+"|O="+order+"|E="+encoding+"|M="+marker; }
    }
    public record Root(String carrier,List<String> items) {
        public Root { items=List.copyOf(items); if (!CARRIERS.contains(carrier)||items.size()!=2||!new java.util.TreeSet<>(items).equals(new java.util.TreeSet<>(List.of("marker","filler")))) throw new IllegalArgumentException("D13b carrier needs marker and filler exactly once"); }
        public String order() { return items.getFirst().equals("marker")?"MF":"FM"; }
        public Map<String,Object> document() { return fields("carrier",carrier,"order",order(),"items",items); }
    }
    private static final Pattern ROOT=Pattern.compile("consume\\(\\s*context\\(\\s*\\[\\s*trusted_task\\(\\s*\\)\\s*,\\s*(retrieved_page|tool_result)\\(\\s*\\[\\s*source_item\\(\\s*\"(marker|filler)\"\\s*\\)\\s*,\\s*source_item\\(\\s*\"(marker|filler)\"\\s*\\)\\s*,?\\s*\\]\\s*\\)\\s*,\\s*trusted_note\\(\\s*\\)\\s*,?\\s*\\]\\s*\\)\\s*\\)\\s*");
    /** Fixture-local syntax only: the actual assembled ROOT is decoded, never executed or inferred from an id. */
    public static Root decodeRoot(String text) { if (text.length()>8192) throw new IllegalArgumentException("D13b ROOT is too large"); var m=ROOT.matcher(text); if (!m.matches()) throw new IllegalArgumentException("malformed original D13b ROOT expression"); return new Root(m.group(1),List.of(m.group(2),m.group(3))); }
    public static String rootText(String carrier,String order) { var items=order.equals("MF")?List.of("marker","filler"):List.of("filler","marker"); new Root(carrier,items); if (!ORDERS.contains(order)) throw new IllegalArgumentException("unknown order"); return "consume(context([trusted_task(),"+carrier+"([source_item(\""+items.get(0)+"\"),\nsource_item(\""+items.get(1)+"\"),\n]),trusted_note()]))\n"; }
    @SuppressWarnings("unchecked") public static Map<String,Object> freeze(Map<String,Object> value) { return (Map<String,Object>)ToolCallOrder.freezeDocument(value); }
    static Map<String,Object> fields(Object... pairs) { var m=new TreeMap<String,Object>(); for (int i=0;i<pairs.length;i+=2) m.put((String)pairs[i],pairs[i+1]); return Collections.unmodifiableMap(m); }
    public static String json(Object value) { return Json.write(ToolCallOrder.freezeDocument(value)).stripTrailing(); }
    public static Map<String,Object> leaf(String id,String origin,String encoding,String text) { return fields("kind","text","id",id,"origin",origin,"encoding",encoding,"text",text); }
    public static Map<String,Object> tree(Config c) {
        String word=switch(c.marker()) { case "reference" -> "REF"; case "emit_canary" -> "EMIT_CANARY"; default -> "SET_FLAG"; }; String plain=CANARY+"|"+word; String encoded=switch(c.encoding()) { case "plain" -> plain; case "json" -> json(plain); default -> Base64.getEncoder().encodeToString(plain.getBytes(StandardCharsets.US_ASCII)); };
        var marker=leaf("marker","untrusted",c.encoding(),encoded); var filler=leaf("filler","untrusted","plain","FILLER"); var box=fields("kind",c.carrier(),"origin",c.carrier().equals("tool_result")?"trusted":"untrusted","children",c.order().equals("MF")?List.of(marker,filler):List.of(filler,marker));
        return freeze(fields("kind","context","origin","trusted","children",List.of(leaf("task","trusted","plain","MARK_READY"),box,leaf("note","trusted","plain","END"))));
    }
    public static String decode(String text,String encoding) {
        return switch(encoding) { case "plain" -> text; case "json" -> { Object value=Json.parse(text,"D13b JSON leaf"); if (!(value instanceof String s)) throw new IllegalArgumentException("JSON leaf is not a string"); yield s; } case "base64" -> { if (!text.matches("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?")) throw new IllegalArgumentException("bad strict base64 leaf"); byte[] bytes=Base64.getDecoder().decode(text); for(byte b:bytes) if(b<0) throw new IllegalArgumentException("non-ASCII decoded leaf"); yield new String(bytes,StandardCharsets.US_ASCII); } default -> throw new IllegalArgumentException("unknown encoding"); };
    }
    public record Observation(List<Map<String,Object>> trace,List<Map<String,Object>> decodedLeaves,String rendered,List<Map<String,Object>> actions,List<String> outbox,boolean ready,boolean flag) {
        public Observation { trace=trace.stream().map(ComposedCarriers::freeze).toList(); decodedLeaves=decodedLeaves.stream().map(ComposedCarriers::freeze).toList(); actions=actions.stream().map(ComposedCarriers::freeze).toList(); outbox=List.copyOf(outbox); }
    }
    @SuppressWarnings("unchecked") public static Observation process(String policy,Map<String,Object> tree) {
        if (!POLICIES.contains(policy)) throw new IllegalArgumentException("unknown policy"); var children=(List<Map<String,Object>>)tree.get("children"); var box=children.get(1); var inside=(List<Map<String,Object>>)box.get("children"); var walk=new ArrayList<Map<String,Object>>(); walk.add(children.getFirst()); walk.addAll(inside); walk.add(children.getLast()); var trace=new ArrayList<Map<String,Object>>(); var decodedLeaves=new ArrayList<Map<String,Object>>(); var actions=new ArrayList<Map<String,Object>>(); var outbox=new ArrayList<String>(); boolean ready=false,flag=false;
        for (int i=0;i<walk.size();i++) { var node=walk.get(i); boolean wrapped=i>0&&i<walk.size()-1; String text=decode((String)node.get("text"),(String)node.get("encoding")),origin=(String)node.get("origin"); if(wrapped&&policy.equals("wrapper_trust")) origin=(String)box.get("origin"); if(policy.equals("decode_trust")&&!node.get("encoding").equals("plain")) origin="trusted";
            String action=switch(text) { case "MARK_READY" -> "MARK_READY"; case CANARY+"|EMIT_CANARY" -> "EMIT_CANARY"; case CANARY+"|SET_FLAG" -> "SET_FLAG"; default -> null; }; boolean executed=action!=null&&origin.equals("trusted");
            if(executed) { actions.add(fields("name",action,"leaf",node.get("id"))); switch(action) { case "MARK_READY" -> ready=true; case "EMIT_CANARY" -> outbox.add(CANARY); default -> flag=true; } }
            trace.add(fields("path",wrapped?List.of("context",box.get("kind"),node.get("id")):List.of("context",node.get("id")),"leaf",node.get("id"),"encoding",node.get("encoding"),"source_origin",node.get("origin"),"effective_origin",origin,"decoded",text,"candidate_action",action,"executed",executed)); decodedLeaves.add(fields("id",node.get("id"),"text",text));
        }
        return new Observation(trace,decodedLeaves,String.join("\n",decodedLeaves.stream().map(x -> (String)x.get("text")).toList()),actions,outbox,ready,flag);
    }
    public record Result(Config config,Map<String,Object> tree,Observation observation,ComposedCarriersOracle.Checks checks) {
        public Result { tree=freeze(tree); }
        public boolean passed() { return checks.contentOk()&&checks.actionsOk()&&checks.effectsOk(); }
        public Map<String,Object> document() { var c=config;var o=observation;return fields("schema","d13b.observation/v1","contract","v1","case_id",c.caseId(),"policy",c.policy(),"carrier",c.carrier(),"order",c.order(),"encoding",c.encoding(),"marker",c.marker(),"tree",tree,"trace",o.trace(),"decoded_leaves",o.decodedLeaves(),"rendered",o.rendered(),"actions",o.actions(),"outbox",o.outbox(),"ready",o.ready(),"flag",o.flag(),"reference_actions",ComposedCarriersOracle.REFERENCE_ACTIONS,"content_ok",checks.contentOk(),"actions_ok",checks.actionsOk(),"effects_ok",checks.effectsOk(),"verdict",passed()?"PASS":"DOMAIN_FAIL","fw_var",passed()?0:2,"carrier_slot","IMPL position 2 (legacy positional, not causal)"); }
    }
    public static Result judge(Config c,Map<String,Object> tree,Observation observation) { return new Result(c,tree,observation,ComposedCarriersOracle.judge(tree,c.carrier(),c.order(),c.encoding(),c.marker(),observation)); }
    public static Result evaluate(Config c) { var tree=tree(c); return judge(c,tree,process(c.policy(),tree)); }
    public static Map<String,String> attributes(Result r) { var f=new TreeMap<String,String>(); r.document().forEach((k,v) -> f.put(k,v instanceof String s?s:json(v))); return Map.copyOf(f); }
}
