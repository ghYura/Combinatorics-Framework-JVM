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

package org.ghyura.cf.fixtures.proposal;

import org.ghyura.cf.model.Json;
import org.ghyura.cf.model.SourceLocation;
import org.ghyura.cf.model.proposal.TaskDefinition;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.io.IOException;

/** Original three local task recipes; values are proposed before the normal framework generates candidates. */
public final class LocalProposalScenarios {
    private LocalProposalScenarios(){ }
    public static TaskDefinition of(String kind,long seed) {
        if(!List.of("quote","parser","guard").contains(kind))throw new IllegalArgumentException("unknown local task: "+kind);
        try(var in=LocalProposalScenarios.class.getResourceAsStream(kind+".json")) {
            if(in==null)throw new IllegalStateException("missing local task recipe");
            byte[] bytes=in.readNBytes(65537);if(bytes.length>65536)throw new IllegalArgumentException("task recipe exceeds64KiB");
            var data=map(Json.parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8),"local task recipe"));
            var location=SourceLocation.of(text(data,"source"),Math.toIntExact((Long)data.get("source_line")));
            var slots=new ArrayList<TaskDefinition.Slot>();
            for(var item:list(data.get("slots"))) {
                var slot=map(item);TaskDefinition.Values values;
                if(slot.containsKey("values"))values=new TaskDefinition.Fixed(strings(slot.get("values")));
                else values=new TaskDefinition.Proposed(text(slot,"role"),text(slot,"context"),Math.toIntExact((Long)slot.get("count")),text(slot,"prefix"),text(slot,"suffix"));
                slots.add(new TaskDefinition.Slot(text(slot,"sheet"),text(slot,"key"),text(slot,"verb"),strings(slot.get("flags")),List.of(),true,values,location));
            }
            var words=new ArrayList<TaskDefinition.CustomVerdict>();
            for(var item:list(data.get("custom_vars"))) {var word=map(item);words.add(new TaskDefinition.CustomVerdict(Math.toIntExact((Long)word.get("code")),text(word,"msg")));}
            return new TaskDefinition(text(data,"id"),text(data,"version"),text(data,"title"),text(data,"context"),seed,slots,strings(data.get("args")),strings(data.get("goals")),words,location);
        }catch(IOException e){throw new IllegalStateException("local task recipe unreadable",e);}
    }
    static Map<?,?> map(Object value){return (Map<?,?>)value;}
    static List<?> list(Object value){return (List<?>)value;}
    static String text(Map<?,?> data,String key){return (String)data.get(key);}
    static List<String> strings(Object value){return list(value).stream().map(String.class::cast).toList();}
}
