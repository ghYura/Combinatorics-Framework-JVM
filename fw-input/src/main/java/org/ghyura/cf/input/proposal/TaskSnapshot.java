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

package org.ghyura.cf.input.proposal;

import org.ghyura.cf.input.InputSource;
import org.ghyura.cf.input.InputFormat;
import org.ghyura.cf.input.SpecReader;
import org.ghyura.cf.model.AtomicFiles;
import org.ghyura.cf.model.Canonical;
import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.proposal.FrozenTask;
import org.ghyura.cf.model.proposal.TaskDefinition;
import org.ghyura.cf.model.proposal.ProposalProvider;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.io.IOException;

/** Bounded portable replay; reads saved responses and never has a provider parameter. */
public final class TaskSnapshot {
    private TaskSnapshot() { }
    /** Native reader lowering uses a stable logical name, independent of the saved file's directory. */
    public static SpecReader.Compiled compile(FrozenTask frozen,long maxBytes) {
        requireWithin(frozen,maxBytes);
        return SpecReader.read(InputSource.ofText("local-task.json",frozen.specJson()),InputFormat.JSON);
    }
    public static void save(FrozenTask frozen,Path path,long maxBytes) throws IOException {
        requireWithin(frozen,maxBytes);
        AtomicFiles.write(path,Canonical.utf8(frozen.snapshotJson()));
    }
    public static FrozenTask load(Path path,long maxBytes) throws IOException {
        var input=InputSource.read(path,maxBytes);
        var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        String text=decoder.decode(ByteBuffer.wrap(input.copyOfBytes())).toString();
        return decode(text,maxBytes);
    }
    public static FrozenTask decode(String text,long maxBytes) {
        if(maxBytes<0 || text.length()>maxBytes/16)throw new IllegalArgumentException("snapshot decode allowance exceeded");
        var envelope=object(Json.parse(text,"frozen task"));var body=object(envelope.get("body"));
        allowed(envelope,"body","sha256");allowed(body,"schema","id","version","title","context","seed","args","goals","custom_vars","location","provider","slots");
        if(!string(body,"schema").equals("cf.local-task/v1"))throw new IllegalArgumentException("unsupported frozen task schema");
        var provider=object(body.get("provider"));allowed(provider,"id","version","digest");
        var identity=new ProposalProvider.Identity(string(provider,"id"),string(provider,"version"),string(provider,"digest"));
        var slots=new ArrayList<TaskDefinition.Slot>();var responses=new ArrayList<List<String>>();
        for(var item:list(body.get("slots"))) {
            var slot=object(item);var values=strings(slot.get("responses"));TaskDefinition.Values source;
            switch(string(slot,"kind")) {
                case "fixed" -> { allowed(slot,"sheet","key","verb","flags","directives","raw","location","responses","kind"); source=new TaskDefinition.Fixed(values); }
                case "proposed" -> { allowed(slot,"sheet","key","verb","flags","directives","raw","location","responses","kind","role","context","count","prefix","suffix"); source=new TaskDefinition.Proposed(string(slot,"role"),string(slot,"context"),
                        integer(slot,"count"),string(slot,"prefix"),string(slot,"suffix")); }
                default -> throw new IllegalArgumentException("unknown frozen slot kind");
            }
            slots.add(new TaskDefinition.Slot(string(slot,"sheet"),string(slot,"key"),string(slot,"verb"),
                    strings(slot.get("flags")),strings(slot.get("directives")),bool(slot,"raw"),source,location(slot.get("location"))));
            responses.add(values);
        }
        var words=new ArrayList<TaskDefinition.CustomVerdict>();
        for(var item:list(body.get("custom_vars"))) {
            var word=object(item);allowed(word,"code","msg");words.add(new TaskDefinition.CustomVerdict(integer(word,"code"),string(word,"msg")));
        }
        var task=new TaskDefinition(string(body,"id"),string(body,"version"),string(body,"title"),string(body,"context"),
                number(body,"seed"),slots,strings(body.get("args")),strings(body.get("goals")),words,location(body.get("location")));
        var frozen=new FrozenTask(task,identity,responses);requireWithin(frozen,maxBytes);
        if(!frozen.digest().equals(string(envelope,"sha256")))throw new IllegalArgumentException("frozen task checksum mismatch");
        return frozen;
    }
    private static SourceLocation location(Object value) {
        var p=object(value);allowed(p,"origin","line","column","path");return new SourceLocation(string(p,"origin"),integer(p,"line"),integer(p,"column"),string(p,"path"));
    }
    private static void allowed(Map<?,?> map,String... keys) {
        var permitted=java.util.Set.of(keys);
        if(map.size()!=permitted.size() || !permitted.containsAll(map.keySet()))
            throw new IllegalArgumentException("unknown or missing frozen task fields");
    }
    private static Map<?,?> object(Object value) {
        if(value instanceof Map<?,?> map)return map;throw new IllegalArgumentException("frozen task needs an object");
    }
    private static List<?> list(Object value) {
        if(value instanceof List<?> items)return items;throw new IllegalArgumentException("frozen task needs a list");
    }
    private static List<String> strings(Object value) {
        return list(value).stream().map(v->{if(v instanceof String s)return s;throw new IllegalArgumentException("text required");}).toList();
    }
    private static String string(Map<?,?> map,String key) {
        if(map.get(key) instanceof String s)return s;throw new IllegalArgumentException("text required: "+key);
    }
    private static boolean bool(Map<?,?> map,String key) {
        if(map.get(key) instanceof Boolean b)return b;throw new IllegalArgumentException("boolean required: "+key);
    }
    private static long number(Map<?,?> map,String key) {
        if(map.get(key) instanceof Long n)return n;throw new IllegalArgumentException("signed64bit integer required: "+key);
    }
    private static int integer(Map<?,?> map,String key) { return Math.toIntExact(number(map,key)); }
    /** Conservative simultaneous metadata/JSON/UTF-8 buffers, checked before serialization or native lowering. */
    public static void requireWithin(FrozenTask frozen,long maxBytes) {
        long amount=2048;var task=frozen.task();
        for(String s:List.of(task.id(),task.version(),task.title(),task.context(),task.location().origin(),task.location().path(),
                frozen.provider().id(),frozen.provider().version(),frozen.provider().contentDigest()))amount=admit(amount,s.length(),maxBytes);
        for(String s:task.arguments())amount=admit(amount,s.length(),maxBytes);
        for(String s:task.goals())amount=admit(amount,s.length(),maxBytes);
        for(var word:task.customVerdicts())amount=admit(amount,word.message().length(),maxBytes);
        for(int i=0;i<task.slots().size();i++) {
            var slot=task.slots().get(i);
            for(String s:List.of(slot.sheet(),slot.key(),slot.verb(),slot.location().origin(),slot.location().path()))amount=admit(amount,s.length(),maxBytes);
            for(String s:slot.flags())amount=admit(amount,s.length(),maxBytes);
            for(String s:slot.directives())amount=admit(amount,s.length(),maxBytes);
            int wrapper=0;
            if(slot.values() instanceof TaskDefinition.Proposed p) {
                for(String s:List.of(p.role(),p.context(),p.prefix(),p.suffix()))amount=admit(amount,s.length(),maxBytes);
                wrapper=Math.addExact(p.prefix().length(),p.suffix().length());
            }
            for(String s:frozen.responses().get(i))amount=admit(amount,Math.addExact(s.length(),wrapper),maxBytes);
        }
        if(amount>maxBytes)throw new IllegalArgumentException("snapshot byte allowance exceeded");
    }
    private static long admit(long prior,int length,long limit) {
        long size=Math.addExact(512L,Math.multiplyExact(128L,length));
        if(prior>limit || size>limit-prior)throw new IllegalArgumentException("snapshot byte allowance exceeded");
        return prior+size;
    }
}
