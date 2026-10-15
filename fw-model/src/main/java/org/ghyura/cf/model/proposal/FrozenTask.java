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

package org.ghyura.cf.model.proposal;

import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.RunId;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.Objects;

/** Saved proposals and their authoring contract; no provider is retained or called during replay. */
public record FrozenTask(TaskDefinition task, ProposalProvider.Identity provider, List<List<String>> responses) {
    public FrozenTask {
        Objects.requireNonNull(task); Objects.requireNonNull(provider);
        responses=responses.stream().map(List::copyOf).toList();
        if(responses.size()!=task.slots().size())throw new IllegalArgumentException("response/slot count mismatch");
        for(int i=0;i<responses.size();i++) {
            var slot=task.slots().get(i);
            if(slot.values() instanceof TaskDefinition.Fixed fixed && !responses.get(i).equals(fixed.values()))
                throw new IllegalArgumentException("frozen fixed slot differs: "+slot.sheet());
            if(slot.values() instanceof TaskDefinition.Proposed proposed && responses.get(i).size()>proposed.count())
                throw new IllegalArgumentException("provider exceeded requested cardinality: "+slot.sheet());
        }
    }
    /** Entire typed contract and ordered responses; physical location is retained as provenance. */
    public Map<String,Object> body() {
        var root=new TreeMap<String,Object>(); root.put("schema","cf.local-task/v1");
        root.put("id",task.id()); root.put("version",task.version()); root.put("title",task.title());
        root.put("context",task.context()); root.put("seed",task.seed());
        root.put("args",task.arguments()); root.put("goals",task.goals());
        root.put("custom_vars",task.customVerdicts().stream().map(v->Map.of("code",v.code(),"msg",v.message())).toList());
        root.put("location",location(task.location()));
        root.put("provider",Map.of("id",provider.id(),"version",provider.version(),"digest",provider.contentDigest()));
        var slots=new ArrayList<Map<String,Object>>();
        for(int i=0;i<task.slots().size();i++) {
            var slot=task.slots().get(i); var value=new TreeMap<String,Object>();
            value.put("sheet",slot.sheet()); value.put("key",slot.key()); value.put("verb",slot.verb());
            value.put("flags",slot.flags()); value.put("directives",slot.directives()); value.put("raw",slot.raw());
            value.put("location",location(slot.location())); value.put("responses",responses.get(i));
            if(slot.values() instanceof TaskDefinition.Proposed p) {
                value.put("kind","proposed"); value.put("role",p.role()); value.put("context",p.context());
                value.put("count",p.count()); value.put("prefix",p.prefix()); value.put("suffix",p.suffix());
            } else value.put("kind","fixed");
            slots.add(Map.copyOf(value));
        }
        root.put("slots",List.copyOf(slots)); return Map.copyOf(root);
    }
    private static Map<String,Object> location(org.ghyura.cf.model.SourceLocation p) {
        return Map.of("origin",p.origin(),"line",p.line(),"column",p.column(),"path",p.path());
    }
    private static Object canonical(Object value) {
        if(value instanceof Map<?,?> map) {
            var sorted=new TreeMap<String,Object>();
            map.forEach((key,item)->sorted.put((String)key,canonical(item)));
            return java.util.Collections.unmodifiableMap(sorted);
        }
        if(value instanceof List<?> list)return list.stream().map(FrozenTask::canonical).toList();
        return value;
    }
    public String digest() { return Canonical.sha256Hex(Json.write(canonical(body()))); }
    /** Explicit task-run namespace binds provenance even when two providers return identical values. */
    public RunId runId(String prefix) {
        new RunId(prefix);
        return new RunId(Canonical.sha256Hex("cf.task-run/1\n"+prefix+"\n"+digest()));
    }
    /** Portable snapshot envelope; the checksum binds ordered responses and metadata, not a provider session. */
    public String snapshotJson() { return Json.write(canonical(Map.of("body",body(),"sha256",digest()))); }
    /** Native fwgen JSON document; all generation remains with the existing reader/planner. */
    public String specJson() {
        var root=new TreeMap<String,Object>(); root.put("title",task.title()); root.put("args",task.arguments());
        root.put("goals",task.goals());
        root.put("custom_vars",task.customVerdicts().stream().map(v->Map.of("code",v.code(),"msg",v.message())).toList());
        var slots=new ArrayList<Map<String,Object>>();
        var extra=new ArrayList<List<String>>();
        for(int i=0;i<task.slots().size();i++) {
            var slot=task.slots().get(i);var value=new TreeMap<String,Object>();
            value.put("sheet",slot.sheet());value.put("key",slot.key());value.put("verb",slot.verb());
            value.put("flags",slot.flags());value.put("raw",slot.raw());
            if(!slot.directives().isEmpty()) {
                var row=new ArrayList<String>();row.add(slot.sheet());row.add(slot.verb());row.addAll(slot.directives());extra.add(List.copyOf(row));
            }
            var texts=responses.get(i);
            if(slot.values() instanceof TaskDefinition.Proposed proposed)
                texts=texts.stream().map(v->proposed.prefix()+v+proposed.suffix()).toList();
            value.put("values",texts);slots.add(Map.copyOf(value));
        }
        root.put("slots",List.copyOf(slots));
        if(!extra.isEmpty())root.put("seq_extra",List.copyOf(extra));
        return Json.write(canonical(root));
    }
}
