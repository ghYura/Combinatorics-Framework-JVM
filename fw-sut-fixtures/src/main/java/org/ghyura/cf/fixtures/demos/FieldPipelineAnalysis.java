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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;

/** Complete recorded support and witnesses; does not rerun or rejudge the adapter. */
public final class FieldPipelineAnalysis {
    private FieldPipelineAnalysis() { }
    public record Observed(Map<String,String> fields) { public Observed { fields=Map.copyOf(fields); } public String id() { return fields.get("case_id"); } }
    public static List<FieldPipeline.Config> configurations() { var out=new ArrayList<FieldPipeline.Config>();for(String p:FieldPipeline.POLICIES)for(String a:FieldPipeline.OPS)for(String b:FieldPipeline.OPS)if(!a.equals(b))for(String f:FieldPipeline.FIELDS)out.add(new FieldPipeline.Config(p,new FieldPipeline.Tree(f,List.of(a,b))));return List.copyOf(out); }
    public static Set<String> expectedIds() { return Collections.unmodifiableSet(new TreeSet<>(configurations().stream().map(FieldPipeline.Config::caseId).toList())); }
    public static Object value(Observed row,String key) { String v=row.fields().get(key);if(v==null)throw new IllegalArgumentException("missing D5 A "+key);return Json.parse(v,"observed D5 A "+key); }
    public static FieldPipeline.Config configuration(Observed row) { Object t=value(row,"tree");if(!(t instanceof Map<?,?> tree)||!tree.keySet().equals(Set.of("field","ops"))||!(tree.get("field")instanceof String field)||!(tree.get("ops")instanceof List<?> ops)||ops.stream().anyMatch(v->!(v instanceof String)))throw new IllegalArgumentException("recorded two-operation tree required");return new FieldPipeline.Config(row.fields().get("policy"),new FieldPipeline.Tree(field,ops.stream().map(v->(String)v).toList())); }
    public static Map<String,String> witnesses() { var m=new LinkedHashMap<String,String>();m.put("correct_AM_x","A|correct|OPS=AM|FIELD=x");m.put("reverse_pair_AM_x","A|reverse_pair|OPS=AM|FIELD=x");m.put("wrong_field_AM_x","A|wrong_field|OPS=AM|FIELD=x");m.put("reverse_pair_AS_x","A|reverse_pair|OPS=AS|FIELD=x");return Collections.unmodifiableMap(m); }
    public static Map<String,Object> analyse(List<Observed> rows) {
        var expected=expectedIds();var bank=new TreeMap<String,Observed>();var totals=new TreeMap<String,Integer>();var byPolicy=new TreeMap<String,Map<String,Integer>>();FieldPipeline.POLICIES.forEach(p->byPolicy.put(p,new TreeMap<>()));var commute=new TreeSet<String>();
        for(var row:rows){var c=configuration(row);String verdict=row.fields().get("verdict");if(!"A".equals(row.fields().get("phase"))||!"A".equals(row.fields().get("contract"))||!"d5a.observation/v1".equals(row.fields().get("schema"))||!c.caseId().equals(row.id())||!expected.contains(row.id())||bank.putIfAbsent(row.id(),row)!=null)throw new IllegalArgumentException("unknown/duplicate/conflicting D5 A record");if(!List.of("PASS","DOMAIN_FAIL").contains(verdict))throw new IllegalArgumentException("domain verdict required");if(!(value(row,"traces_equal")instanceof Boolean))throw new IllegalArgumentException("Boolean trace diagnostic required");totals.merge(verdict,1,Integer::sum);byPolicy.get(c.policy()).merge(verdict,1,Integer::sum);if(c.policy().equals("reverse_pair")&&verdict.equals("PASS"))commute.add(row.id());}
        if(!bank.keySet().equals(expected))throw new IllegalArgumentException("complete original 72-case support required");var witnesses=new ArrayList<Map<String,Object>>();for(var e:witnesses().entrySet()){var row=bank.get(e.getValue());witnesses.add(FieldPipeline.fields("label",e.getKey(),"case_id",row.id(),"tree",value(row,"tree"),"expected",value(row,"expected"),"observed",value(row,"observed"),"sut_trace",value(row,"sut_trace"),"verdict",row.fields().get("verdict")));}return FieldPipeline.freeze(FieldPipeline.fields("totals",totals,"by_policy",byPolicy,"reverse_pair_passes",new ArrayList<>(commute),"witnesses",witnesses));
    }
}
