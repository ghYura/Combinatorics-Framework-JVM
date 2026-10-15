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

import org.ghyura.cf.model.proposal.FrozenTask;
import org.ghyura.cf.model.proposal.TaskDefinition;
import org.ghyura.cf.model.proposal.ProposalProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Admit and freeze local responses once, before any native plan or run is created. */
public final class TaskFreezer {
    private TaskFreezer() { }
    public static FrozenTask freeze(TaskDefinition task,ProposalProvider provider,long maxBytes) {
        Objects.requireNonNull(task);Objects.requireNonNull(provider);
        if(maxBytes<1024)throw new IllegalArgumentException("task freeze byte allowance too small");
        long estimate=1024;
        for(String text:List.of(task.id(),task.version(),task.title(),task.context()))estimate=admit(estimate,text,maxBytes);
        for(String text:task.arguments())estimate=admit(estimate,text,maxBytes);
        for(String text:task.goals())estimate=admit(estimate,text,maxBytes);
        for(var word:task.customVerdicts())estimate=admit(estimate,word.message(),maxBytes);
        var identity=Objects.requireNonNull(provider.identity());
        for(String text:List.of(identity.id(),identity.version(),identity.contentDigest(),task.location().origin(),task.location().path()))
            estimate=admit(estimate,text,maxBytes);
        var responses=new ArrayList<List<String>>();
        for(var slot:task.slots()) {
            for(String text:List.of(slot.sheet(),slot.key(),slot.verb(),slot.location().origin(),slot.location().path()))
                estimate=admit(estimate,text,maxBytes);
            for(String text:slot.flags())estimate=admit(estimate,text,maxBytes);
            for(String text:slot.directives())estimate=admit(estimate,text,maxBytes);
            List<String> values;
            if(slot.values() instanceof TaskDefinition.Fixed fixed)values=fixed.values();
            else {
                var proposed=(TaskDefinition.Proposed)slot.values();
                for(String text:List.of(proposed.role(),proposed.context(),proposed.prefix(),proposed.suffix()))
                    estimate=admit(estimate,text,maxBytes);
                values=Objects.requireNonNull(provider.propose(new ProposalProvider.Request(slot.sheet(),proposed.role(),
                        task.context()+" "+proposed.context(),proposed.count(),task.seed())));
                if(values.size()>proposed.count())throw new IllegalArgumentException("provider exceeded cardinality: "+slot.sheet());
            }
            for(String text:values) {
                estimate=admit(estimate,Objects.requireNonNull(text),maxBytes);
                if(slot.values() instanceof TaskDefinition.Proposed p)
                    estimate=admitLength(estimate,Math.addExact(text.length(),Math.addExact(p.prefix().length(),p.suffix().length())),maxBytes);
            }
            responses.add(List.copyOf(values));
        }
        if(!identity.equals(provider.identity()))throw new IllegalArgumentException("provider identity changed during freeze");
        return new FrozenTask(task,identity,responses);
    }
    private static long admit(long prior,String text,long maxBytes) {
        return admitLength(prior,text.length(),maxBytes);
    }
    private static long admitLength(long prior,int length,long maxBytes) {
        long bytes=Math.addExact(256L,Math.multiplyExact(64L,length));
        if(bytes>maxBytes-prior)throw new IllegalArgumentException("task freeze byte allowance exceeded");
        return prior+bytes;
    }
}
